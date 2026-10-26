package com.abhishek.ame.backend.youtube

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.abhishek.ame.backend.PlaybackHost
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Builds the ExoPlayer the YouTube Music backend plays through.
 *
 * A real ExoPlayer, unlike the Spotify side: there is no engine to hand audio
 * to, the tracks are ordinary HTTPS files, and the queue, gapless transitions
 * and buffering are all things ExoPlayer already does properly.
 */
@UnstableApi
object YouTubePlayerFactory {

    fun create(host: PlaybackHost): Player {
        fun loadControl() = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_500,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val crossfadeStore = (host.context.applicationContext as? com.abhishek.ame.AmeApplication)?.crossfade

        // One speaker for both players; see PcmMixer.
        val mixer = PcmMixer()
        val mainSlot = MixerSlot()
        val tailSlot = MixerSlot(follows = mainSlot, initialFade = 0f).apply { held = true }

        val dataSources = ResolvingDataSource.Factory(
            // Files as well as the network: a kept song resolves to one on the
            // phone, which an HTTP source cannot open.
            ChunkedDataSource.Factory(
                androidx.media3.datasource.DefaultDataSource.Factory(
                    host.context,
                    DefaultHttpDataSource.Factory()
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .setConnectTimeoutMs(15000)
                        .setReadTimeoutMs(15000)
                        .setAllowCrossProtocolRedirects(true),
                ),
            ),
            YouTubeStreamResolver(host.context.applicationContext),
        )

        fun buildPlayer(handleFocus: Boolean, slot: MixerSlot) =
            ExoPlayer.Builder(host.context, renderers(host, MixerOutputProvider(host.context, mixer, slot)))
            .setMediaSourceFactory(VideoAwareSourceFactory(DefaultMediaSourceFactory(dataSources), dataSources))
            .setLoadControl(loadControl())
            .setHandleAudioBecomingNoisy(true)
            // Ducks and pauses for whatever else wants the speaker. The Spotify
            // path gets this from its own output; ExoPlayer will not ask for focus
            // at all unless it is told to handle it.
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ handleFocus,
            )
            .setLooper(host.looper)
            .build()

        return buildPlayer(true, mainSlot).let { exo ->
                exo.addAnalyticsListener(Diagnostics)
                // A song YouTube plays only to an account, with none signed in:
                // said, and passed over, rather than left at --:-- with no word
                // of why. See SignedInStreams.
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        // A signed-in address that failed is not handed out again.
                        SignedInStreams.forget()
                        val causes = generateSequence<Throwable>(error) { it.cause }.toList()
                        val message = when {
                            causes.any { it is SignedInStreams.SignInRequired } ->
                                com.abhishek.ame.R.string.youtube_sign_in_required
                            causes.any { it is SignedInStreams.Refused } ->
                                com.abhishek.ame.R.string.youtube_signed_in_unplayable
                            else -> {
                                recover(exo, error)
                                return
                            }
                        }
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(
                                host.context,
                                host.context.getString(message),
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                        if (exo.hasNextMediaItem()) {
                            exo.seekToNextMediaItem()
                            exo.prepare()
                            exo.play()
                        }
                    }
                })
                // Video for a video, sound for a song, as the queue moves.
                exo.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                        YouTubeVideoMode.onItem(mediaItem?.mediaId)
                        val prefQuality = (host.context.applicationContext as? com.abhishek.ame.AmeApplication)
                            ?.preferences?.youtubeQuality?.value
                        com.abhishek.ame.backend.fidelity.FidelityManager.onTrackTransition(mediaItem?.mediaId, prefQuality)
                        // A video whose song is already known says so at once.
                        mediaItem?.mediaId?.takeIf { it.startsWith(YouTubeBackend.TRACK_PREFIX) }
                            ?.let(YouTubeBackend::videoIdOfUri)
                            ?.takeIf(SongCounterparts::wanted)
                            ?.let { video -> SongCounterparts.known(video)?.let { retitle(exo, video, it) } }

                        // Upgrade playing item artwork to official Apple Studio Master cover
                        mediaItem?.mediaMetadata?.let { meta ->
                            val title = meta.title?.toString().orEmpty()
                            val artist = meta.artist?.toString().orEmpty()
                            val album = meta.albumTitle?.toString().orEmpty()
                            val id = mediaItem.mediaId
                            if (title.isNotBlank() && artist.isNotBlank() && id.isNotBlank()) {
                                upgradeArtwork(exo, title, artist, album, id)
                            }
                        }
                        // The next one asked for now, while this one plays.
                        val next = exo.nextMediaItemIndex
                        if (next >= 0) {
                            exo.getMediaItemAt(next).mediaId
                                .takeIf { it.startsWith(YouTubeBackend.URI_SCHEME) }
                                ?.let(YouTubeStreams::prefetch)
                        }
                    }
                })
                val fades = YouTubeFadeController(exo, crossfadeStore, mixer, mainSlot, tailSlot) {
                    buildPlayer(false, tailSlot)
                }
                val preferences = (host.context.applicationContext as? com.abhishek.ame.AmeApplication)
                    ?.preferences
                // Each song found for a video in the queue renames its entry, so
                // the player shows what it plays; see SongCounterparts.
                CoroutineScope(SupervisorJob() + android.os.Handler(host.looper).asCoroutineDispatcher()).launch {
                    SongCounterparts.resolved.collect { (video, song) ->
                        if (SongCounterparts.wanted(video)) retitle(exo, video, song)
                    }
                }
                // The talking and the sketches around a music video; see SponsorBlock.
                SponsorBlock(exo) { preferences?.sponsorBlock?.value ?: true }
                // Every change of song the listener asks for goes through the
                // fade; see SkipFadePlayer.
                mixer.skipFadeMs = { preferences?.skipFadeMs() ?: 0 }
                SkipFadePlayer(exo, fades) { preferences?.skipFadeMs() ?: 0 }
            }
    }

    /**
     * Shows [song] in place of [video] wherever the queue has it. Same stream
     * address, so nothing reloads: only the name, the picture and the id the
     * rest of the app reads (lyrics, credits, likes) change.
     */
    /** The song last picked up again after an error, and when, and how often. */
    private var recovering: Triple<String, Long, Int>? = null

    /**
     * Picks the song up again where it stopped, after an error that was not
     * about the account.
     *
     * Everything else used to be left as it was: a stream address that had
     * expired, a 403 halfway through, a connection that dropped for a moment,
     * and the player sat stopped in its error with the song half played until
     * the listener came back and chose another. Asked again, with the addresses
     * it had forgotten, almost every one of these plays on. One that fails
     * [RETRIES] times in a row within [RETRY_WINDOW_MS] is passed over, as a
     * song that cannot be played would be.
     */
    private fun recover(exo: ExoPlayer, error: androidx.media3.common.PlaybackException) {
        val item = exo.currentMediaItem ?: return
        val id = item.mediaId
        if (!exo.playWhenReady) return

        // If an external high-fidelity stream failed, blacklist it so recovery instantly falls back to YouTube High
        val causes = generateSequence<Throwable>(error) { it.cause }.toList()
        for (cause in causes) {
            if (cause is androidx.media3.datasource.HttpDataSource.HttpDataSourceException) {
                val failedUri = cause.dataSpec.uri.toString()
                if (!failedUri.contains("googlevideo.com")) {
                    YouTubeStreamResolver.failedLosslessUrls.add(failedUri)
                    android.util.Log.w("AmeYT", "Blacklisted failed external stream: $failedUri")
                }
            }
        }

        val now = android.os.SystemClock.elapsedRealtime()
        val last = recovering?.takeIf { it.first == id && now - it.second < RETRY_WINDOW_MS }
        val attempt = (last?.third ?: 0) + 1
        recovering = Triple(id, now, attempt)
        YouTubeStreams.forget(YouTubeBackend.videoIdOfUri(id))
        val at = exo.currentPosition
        val index = exo.currentMediaItemIndex
        android.util.Log.w("AmeYT", "playback failed (${error.errorCodeName}), try $attempt at ${at}ms")
        android.os.Handler(exo.applicationLooper).postDelayed({
            // Moved on in the meantime: nothing of this to pick up.
            if (exo.currentMediaItem?.mediaId != id || exo.playerError == null) return@postDelayed
            if (attempt > RETRIES) {
                if (exo.hasNextMediaItem()) exo.seekToNextMediaItem() else return@postDelayed
            } else {
                exo.seekTo(index, at)
            }
            exo.prepare()
            exo.play()
        }, RETRY_DELAY_MS * attempt)
    }

    private const val RETRIES = 3
    private const val RETRY_DELAY_MS = 1_500L
    private const val RETRY_WINDOW_MS = 60_000L

    private fun retitle(player: ExoPlayer, video: String, song: com.metrolist.innertube.models.SongItem) {
        val artistsStr = song.artists.joinToString(", ") { it.name }
        com.abhishek.ame.backend.fidelity.FidelityManager.registerTrack(
            uri = "${YouTubeBackend.TRACK_PREFIX}${song.id}",
            title = song.title,
            artist = artistsStr,
            durationSec = song.duration?.toLong() ?: 0L
        )
        com.abhishek.ame.backend.fidelity.FidelityManager.registerTrack(
            uri = "${YouTubeBackend.TRACK_PREFIX}$video",
            title = song.title,
            artist = artistsStr,
            durationSec = song.duration?.toLong() ?: 0L
        )
        com.abhishek.ame.backend.fidelity.FidelityManager.linkAliases(video, song.id)
        val listed = "${YouTubeBackend.TRACK_PREFIX}$video"
        for (index in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            if (item.mediaId != listed) continue
            val metadata = item.mediaMetadata.buildUpon()
                .setTitle(song.title)
                .setArtist(song.artists.joinToString(", ") { it.name })
                .setAlbumTitle(song.album?.name ?: item.mediaMetadata.albumTitle)
                .setArtworkUri(android.net.Uri.parse(song.thumbnail.atSize(COVER_SIZE)))
                .build()
            player.replaceMediaItem(
                index,
                item.buildUpon()
                    .setMediaId("${YouTubeBackend.TRACK_PREFIX}${song.id}")
                    .setMediaMetadata(metadata)
                    .build(),
            )
        }
        upgradeArtwork(
            player,
            song.title,
            song.artists.joinToString(", ") { it.name },
            song.album?.name.orEmpty(),
            "${YouTubeBackend.TRACK_PREFIX}${song.id}",
        )
    }

    private fun upgradeArtwork(player: ExoPlayer, title: String, artist: String, album: String, targetMediaId: String) {
        if (title.isBlank() || artist.isBlank()) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val studioArt = com.abhishek.ame.data.AppleCatalog.song(title, artist, album) ?: return@launch
            withContext(android.os.Handler(player.applicationLooper).asCoroutineDispatcher()) {
                for (index in 0 until player.mediaItemCount) {
                    val item = player.getMediaItemAt(index)
                    if (item.mediaId == targetMediaId) {
                        val currentArt = item.mediaMetadata.artworkUri?.toString()
                        if (currentArt != studioArt) {
                            val updatedMeta = item.mediaMetadata.buildUpon()
                                .setArtworkUri(android.net.Uri.parse(studioArt))
                                .build()
                            player.replaceMediaItem(
                                index,
                                item.buildUpon().setMediaMetadata(updatedMeta).build(),
                            )
                        }
                        break
                    }
                }
            }
        }
    }

    /** Temporary: chasing a stall where the position stops with the sink idle. */
    private object Diagnostics : androidx.media3.exoplayer.analytics.AnalyticsListener {
        override fun onAudioUnderrun(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long,
        ) {
            android.util.Log.w(TAG, "underrun size=$bufferSize sinceFeed=${elapsedSinceLastFeedMs}ms")
        }

        override fun onPlaybackStateChanged(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            state: Int,
        ) {
            android.util.Log.w(TAG, "state=$state pos=${eventTime.currentPlaybackPositionMs}")
        }

        override fun onPlayWhenReadyChanged(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            playWhenReady: Boolean,
            reason: Int,
        ) {
            android.util.Log.w(TAG, "playWhenReady=$playWhenReady reason=$reason")
        }

        override fun onPlaybackParametersChanged(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            parameters: androidx.media3.common.PlaybackParameters,
        ) {
            android.util.Log.w(TAG, "params speed=${parameters.speed} pitch=${parameters.pitch}")
            // Saved here as on the Spotify path (see LibrespotPlayer): never
            // saved for a YouTube song, the effects button went on reading the
            // speed of the last Spotify one, lit after "Original".
            com.abhishek.ame.playback.AudioEffects.rememberSpeedAndPitch(parameters.speed, parameters.pitch)
        }

        override fun onLoadError(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
            mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
            error: java.io.IOException,
            wasCanceled: Boolean,
        ) {
            android.util.Log.w(TAG, "load error ${loadEventInfo.uri.host}: ${error.message}")
        }

        override fun onAudioSinkError(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            audioSinkError: Exception,
        ) {
            android.util.Log.e(TAG, "sink error", audioSinkError)
        }

        private const val TAG = "AmeYT"
    }

    /**
     * Renderers whose audio sink resamples in software.
     *
     * ExoPlayer would otherwise hand a non-unity speed straight to `AudioTrack`,
     * and on this hardware that track stops consuming: the session stays
     * PLAYING and its buffer keeps filling while the position stands still, so
     * the sound goes and the progress bar jumps back to where it stalled.
     * Sonic — the same resampler Media3 uses when the platform cannot oblige —
     * has no such trouble, and it is what the Spotify path already effectively
     * does through its own output.
     */
    private fun renderers(
        host: PlaybackHost,
        output: androidx.media3.exoplayer.audio.AudioOutputProvider,
    ) =
        object : androidx.media3.exoplayer.DefaultRenderersFactory(host.context) {
            // Video stays on the phone's hardware decoders: FFmpeg's are
            // software, and a 1080p picture through them falls behind and stalls.
            override fun buildVideoRenderers(
                context: android.content.Context,
                extensionRendererMode: Int,
                mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>,
            ) = super.buildVideoRenderers(
                context,
                EXTENSION_RENDERER_MODE_OFF,
                mediaCodecSelector,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                allowedVideoJoiningTimeMs,
                out,
            )

            init {
                // FFmpeg ahead of the phone's own decoders. Releasing a platform
                // audio codec while another song is starting aborts the whole
                // app on this hardware, inside MediaCodec's metrics
                // (`ubsan: sub-overflow` in updateMediametrics); a decoder of the
                // app's own has no such code to fall into. The platform ones stay
                // behind it for whatever FFmpeg cannot decode.
                setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
            }

            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(false)
                .setAudioOutputProvider(output)
                // Karaoke, ahead of Sonic. The default chain was all this sink
                // had, so the dial moved and the voice stayed: the removal is a
                // processor of this app's, and a chain that does not list it
                // does not run it. Before the stretcher, because it works on
                // what the two channels share and a stretched pair shares less.
                //
                // Speed and pitch by the app's own vocoder, as on Spotify and
                // for the phone's own files. The default chain left them to
                // Sonic, which was built for speech: on music it smears the
                // attacks and adds a metallic edge, and the same effect sounded
                // plainly worse on a YouTube song. See BungeeAudioProcessor.
                .setAudioProcessorChain(
                    // Then every stream at the mixer's rate, so a 44.1 kHz song
                    // and a 48 kHz one can be summed; see MixerRateProcessor.
                    com.abhishek.ame.playback.BungeeProcessorChain(after = arrayOf(MixerRateProcessor())),
                )
                .build()
        }
}

/**
 * Turns a `ytmusic:track:` URI into the URL the audio really lives at.
 *
 * Resolved at load time rather than when the queue is built, and this is the
 * whole reason for the indirection: a YouTube stream URL is signed and expires
 * within hours, so a queue holding resolved URLs would be a queue that stops
 * working while it sits there. The URI keeps its meaning; only the resolution
 * is perishable.
 *
 * Runs on ExoPlayer's loading thread, so blocking here is correct.
 */
@UnstableApi
private class YouTubeStreamResolver(
    private val context: android.content.Context,
) : ResolvingDataSource.Resolver {

    companion object {
        val failedLosslessUrls: MutableSet<String> = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())
    }

    /**
     * Asked without an account, and again as the account where YouTube will
     * only play the song to one; see SignedInStreams.
     */
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec = try {
        resolveAnonymously(dataSpec)
    } catch (error: Exception) {
        if (!SignedInStreams.needsAccount(error)) throw error
        val base = dataSpec.uri.toString().substringBefore('#')
        val kind = when (dataSpec.uri.fragment) {
            PART_VIDEO -> SignedInStreams.Kind.VIDEO
            PART_AUDIO -> SignedInStreams.Kind.AUDIO
            else -> if (YouTubeVideoMode.wantsVideo(base)) SignedInStreams.Kind.MUXED else SignedInStreams.Kind.AUDIO
        }
        val id = YouTubeBackend.videoIdOfUri(base)
        val url = try {
            SignedInStreams.url(context, id, kind, wantsLowRate())
        } catch (refused: SignedInStreams.SignInRequired) {
            throw refused
        } catch (refused: Exception) {
            android.util.Log.w("AmeYTSignedIn", "$id refused: ${refused.message}")
            throw SignedInStreams.Refused(id, refused)
        }
        dataSpec.withUri(android.net.Uri.parse(url))
    }

    private fun resolveAnonymously(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri.toString()
        if (!uri.startsWith(YouTubeBackend.TRACK_PREFIX) && !uri.startsWith(YouTubeBackend.VIDEO_PREFIX)) {
            return dataSpec
        }
        // "#video" and "#audio" are the two halves of a video played in high
        // quality; see VideoAwareSourceFactory.
        val part = dataSpec.uri.fragment
        val base = uri.substringBefore('#')
        val video = part == null && YouTubeVideoMode.wantsVideo(base)

        // A song kept on the phone plays from the phone. Not while the video is
        // showing and there is a network to fetch it over: the file is the
        // sound alone.
        val kept = com.abhishek.ame.download.YouTubeDownloads.fileFor(context, base)
        // Never for half of a video: the picture is the official video's, and
        // its sound has to be that video's too or the two drift apart.
        if (kept != null && part == null &&
            (!video || com.abhishek.ame.playback.OfflineMode.active.value)
        ) {
            return dataSpec.withUri(android.net.Uri.fromFile(kept))
        }

        // NewPipe holds its downloader in a static, and every extractor reads it
        // at construction: without this the first resolve dies on "downloader is
        // null". Done here rather than at startup so a session that never plays
        // a YouTube track never builds one.
        YouTubeStreams.ensureNewPipe()

        val songId = YouTubeBackend.videoIdOfUri(base)
        // A song watched rather than heard is its official video, sound and
        // all; see YouTubeStreams.watchSource. A video is already itself.
        val watching = part != null || video
        val (videoId, info) = if (watching && base.startsWith(YouTubeBackend.TRACK_PREFIX)) {
            YouTubeStreams.watchSource(songId)
        } else {
            // A music video from a list, heard: its song, found now if the list
            // did not get to it before the row was tapped; see SongCounterparts.
            val heard = if (!watching && base.startsWith(YouTubeBackend.TRACK_PREFIX) &&
                SongCounterparts.wanted(songId)
            ) {
                runCatching { SongCounterparts.findBlocking(songId) }.getOrNull()?.id
            } else {
                null
            } ?: songId
            heard to YouTubeStreams.info(heard)
        }

        // The picture alone, as sharp as is worth it on a phone: H.264 first,
        // which every phone decodes in hardware, then whatever else there is.
        if (part == PART_VIDEO) {
            val picture = info.videoOnlyStreams
                .filter { !it.content.isNullOrEmpty() && it.height in 1..MAX_VIDEO_HEIGHT }
                .sortedWith(
                    compareByDescending<org.schabi.newpipe.extractor.stream.VideoStream> { it.height }
                        .thenByDescending { it.format == org.schabi.newpipe.extractor.MediaFormat.MPEG_4 },
                )
                .firstOrNull()?.content
                ?: info.videoStreams.filter { !it.content.isNullOrEmpty() }.maxByOrNull { it.height }?.content
                ?: error("nessun video per $videoId")
            return dataSpec.withUri(android.net.Uri.parse(picture))
        }

        // With the video showing, one of YouTube's muxed streams: those are the
        // only ones carrying picture and sound in a single file, and a single
        // file is what a progressive source can play. They top out at 720p,
        // which is the price of not building a DASH manifest here.
        // Standard YouTube stream resolution (always resolved as a rock-solid base)
        val youtubeStreamUrl = (if (video && part == null) {
            info.videoStreams
                .filter { !it.content.isNullOrEmpty() }
                .maxByOrNull { it.height }
                ?.content
        } else {
            null
        }) ?: info.audioStreams
            .filter { !it.content.isNullOrEmpty() }
            .sortedWith(
                compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> {
                    it.format == org.schabi.newpipe.extractor.MediaFormat.M4A
                }.thenBy { if (wantsLowRate()) it.averageBitrate else -it.averageBitrate },
            )
            .firstOrNull()
            ?.content
            ?: error("nessuna traccia audio per $videoId")

        // Quality and fidelity preferences
        val qualityPref = (context.applicationContext as? com.abhishek.ame.AmeApplication)
            ?.preferences?.youtubeQuality?.value ?: com.abhishek.ame.data.PreferencesStore.YouTubeQuality.AUTO
        android.util.Log.i("AmeFidelity", "resolveAnonymously: base=$base, qualityPref=$qualityPref, name='${info.name}', uploader='${info.uploaderName}', duration=${info.duration}")

        // User explicit choices:
        // HIGH or LOW: always return standard YouTube stream immediately without any external network delay
        if (qualityPref == com.abhishek.ame.data.PreferencesStore.YouTubeQuality.HIGH ||
            qualityPref == com.abhishek.ame.data.PreferencesStore.YouTubeQuality.LOW
        ) {
            com.abhishek.ame.backend.fidelity.FidelityManager.recordTrackFidelity(base, com.abhishek.ame.backend.fidelity.AudioFidelity.STANDARD)
            com.abhishek.ame.backend.fidelity.FidelityManager.setFidelity(com.abhishek.ame.backend.fidelity.AudioFidelity.STANDARD)
            return dataSpec.withUri(android.net.Uri.parse(youtubeStreamUrl))
        }

        // LOSSLESS, PREMIUM, VERY_HIGH or AUTO: attempt high-bitrate stream resolution
        var resolvedStreamUrl: String? = null
        var resolvedResult: com.abhishek.ame.backend.fidelity.FidelityResolver.ResolvedStream? = null
        val isMetered = context.getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered == true
        // LOSSLESS, PREMIUM, and VERY_HIGH play on both Wi-Fi and mobile cellular data without restriction
        val allowExternalHiFi = (qualityPref == com.abhishek.ame.data.PreferencesStore.YouTubeQuality.LOSSLESS ||
            qualityPref == com.abhishek.ame.data.PreferencesStore.YouTubeQuality.PREMIUM ||
            qualityPref == com.abhishek.ame.data.PreferencesStore.YouTubeQuality.VERY_HIGH ||
            (qualityPref == com.abhishek.ame.data.PreferencesStore.YouTubeQuality.AUTO && !isMetered)) &&
            !video && part != PART_VIDEO

        if (allowExternalHiFi) {
            val targetBitrate = when (qualityPref) {
                com.abhishek.ame.data.PreferencesStore.YouTubeQuality.LOSSLESS -> 1411
                com.abhishek.ame.data.PreferencesStore.YouTubeQuality.VERY_HIGH -> 256
                else -> 320
            }

            // Retrieve clean metadata from FidelityManager or SongCounterparts if available
            val counterpart = SongCounterparts.known(songId) ?: SongCounterparts.known(videoId)
            val registered = com.abhishek.ame.backend.fidelity.FidelityManager.getTrackMetadata(base)
                ?: com.abhishek.ame.backend.fidelity.FidelityManager.getTrackMetadata(songId)
                ?: com.abhishek.ame.backend.fidelity.FidelityManager.getTrackMetadata(videoId)

            val cleanTitle = counterpart?.title
                ?: registered?.title?.takeIf { it.isNotBlank() }
                ?: info.name.orEmpty()

            val cleanArtist = counterpart?.artists?.joinToString(", ") { it.name }
                ?: registered?.artist?.takeIf { it.isNotBlank() }
                ?: info.uploaderName.orEmpty()

            val cleanDuration = counterpart?.duration?.toLong()
                ?: registered?.durationSec?.takeIf { it > 0 }
                ?: info.duration

            android.util.Log.i("AmeFidelity", "resolveAnonymously target: '$cleanTitle' by '$cleanArtist' (${cleanDuration}s)")

            val resolved = runCatching {
                kotlinx.coroutines.runBlocking {
                    com.abhishek.ame.backend.fidelity.FidelityResolver.resolveHiFiStream(
                        songTitle = cleanTitle,
                        artistName = cleanArtist,
                        targetDurationSec = cleanDuration,
                        targetBitrate = targetBitrate,
                    )
                }
            }.getOrNull()

            if (resolved != null && !failedLosslessUrls.contains(resolved.streamUrl)) {
                resolvedStreamUrl = resolved.streamUrl
                resolvedResult = resolved
            }
        }

        val finalUrl: String
        val chosenFidelity: com.abhishek.ame.backend.fidelity.AudioFidelity

        if (!resolvedStreamUrl.isNullOrBlank()) {
            finalUrl = resolvedStreamUrl
            // Strictly assign LOSSLESS only if stream is bit-perfect FLAC from lossless CDN
            chosenFidelity = if (resolvedResult?.isLosslessFlac == true) {
                com.abhishek.ame.backend.fidelity.AudioFidelity.LOSSLESS
            } else {
                com.abhishek.ame.backend.fidelity.AudioFidelity.STANDARD
            }
            android.util.Log.i("AmeFidelity", "Using stream: $finalUrl (chosenFidelity=$chosenFidelity, isLosslessFlac=${resolvedResult?.isLosslessFlac})")
        } else {
            // Standard fallback (High 160 kbps direct from YouTube)
            finalUrl = youtubeStreamUrl
            chosenFidelity = com.abhishek.ame.backend.fidelity.AudioFidelity.STANDARD
        }

        com.abhishek.ame.backend.fidelity.FidelityManager.recordTrackFidelity(base, chosenFidelity)
        com.abhishek.ame.backend.fidelity.FidelityManager.setFidelity(chosenFidelity)

        return dataSpec.withUri(android.net.Uri.parse(finalUrl))
    }

    /** Whether the lower rate is wanted now; see PreferencesStore.youtubeQuality. */
    private fun wantsLowRate(): Boolean {
        val chosen = (context.applicationContext as? com.abhishek.ame.AmeApplication)
            ?.preferences?.youtubeQuality?.value
        return when (chosen) {
            com.abhishek.ame.data.PreferencesStore.YouTubeQuality.LOW -> true
            com.abhishek.ame.data.PreferencesStore.YouTubeQuality.HIGH,
            com.abhishek.ame.data.PreferencesStore.YouTubeQuality.LOSSLESS,
            com.abhishek.ame.data.PreferencesStore.YouTubeQuality.PREMIUM,
            com.abhishek.ame.data.PreferencesStore.YouTubeQuality.VERY_HIGH -> false
            else -> context.getSystemService(android.net.ConnectivityManager::class.java)
                ?.isActiveNetworkMetered == true
        }
    }

}

/**
 * Plays a video as two sources side by side: the picture alone and the sound
 * alone. YouTube serves anything sharper than 360p only that way; the file that
 * carries both is the small one.
 *
 * Decided when the item is added, so it applies to videos (which always want
 * their picture) and to songs added while the video button is on.
 */
@UnstableApi
private class VideoAwareSourceFactory(
    private val base: DefaultMediaSourceFactory,
    dataSources: androidx.media3.datasource.DataSource.Factory,
) : androidx.media3.exoplayer.source.MediaSource.Factory by base {

    private val progressive = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSources)

    override fun createMediaSource(mediaItem: androidx.media3.common.MediaItem): androidx.media3.exoplayer.source.MediaSource {
        val uri = mediaItem.localConfiguration?.uri?.toString() ?: return base.createMediaSource(mediaItem)
        val ours = uri.startsWith(YouTubeBackend.TRACK_PREFIX) || uri.startsWith(YouTubeBackend.VIDEO_PREFIX)
        if (!ours || !YouTubeVideoMode.wantsVideo(uri)) return base.createMediaSource(mediaItem)
        fun half(part: String) = progressive.createMediaSource(
            mediaItem.buildUpon().setUri("$uri#$part").build(),
        )
        return androidx.media3.exoplayer.source.MergingMediaSource(half(PART_VIDEO), half(PART_AUDIO))
    }
}

private const val PART_VIDEO = "video"
private const val PART_AUDIO = "audio"

/** Past this a phone screen shows no difference, and the data plan does. */
private const val MAX_VIDEO_HEIGHT = 1080

/** NewPipe's one-time setup, shared by the player and the downloads. */
object YouTubeStreams {
    const val WATCH_URL = "https://www.youtube.com/watch?v="

    private val infos = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, StreamInfo>>()
    private val watched = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Pair<String, StreamInfo>>>()
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    /** One fetch per key at a time: the two halves of a video ask at once. */
    private fun lockFor(key: String): Any = locks.computeIfAbsent(key) { Any() }

    /**
     * What YouTube says about a video, kept for a while: the two halves of a
     * video ask within milliseconds of each other, and the URLs stay good for
     * hours.
     */
    fun info(videoId: String): StreamInfo = synchronized(lockFor(videoId)) {
        val now = android.os.SystemClock.elapsedRealtime()
        infos[videoId]?.takeIf { now - it.first < INFO_TTL_MS }?.let { return it.second }
        ensureNewPipe()
        val fresh = StreamInfo.getInfo(ServiceList.YouTube, "$WATCH_URL$videoId")
        infos[videoId] = now to fresh
        if (infos.size > 64) infos.entries.minByOrNull { it.value.first }?.let { infos.remove(it.key) }
        fresh
    }

    /**
     * What to show for a song when it is watched: its official video where
     * there is one that can be played, the song's own upload otherwise. Decided
     * once per song and kept, so the picture and the sound — asked for
     * separately — always come from the same upload. A video that answers
     * "age-restricted" cannot be watched without an account, and is passed over.
     */
    fun watchSource(songId: String): Pair<String, StreamInfo> = synchronized(lockFor("watch:$songId")) {
        val now = android.os.SystemClock.elapsedRealtime()
        watched[songId]?.takeIf { now - it.first < INFO_TTL_MS }?.let { return it.second }
        val song = info(songId)
        val chosen = runCatching { OfficialVideos.forSong(songId, song) }.getOrNull()
            ?.let { id ->
                runCatching { id to info(id) }
                    .onFailure {
                        android.util.Log.i("AmeYTVideo", "official video $id unplayable: ${it.message}")
                        // Said under the picture, so the cover standing in for
                        // the video is not mistaken for a bug.
                        YouTubeVideoMode.markUnavailable(
                            songId,
                            if (it is org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException) {
                                YouTubeVideoMode.Unavailable.AGE_RESTRICTED
                            } else {
                                YouTubeVideoMode.Unavailable.OTHER
                            },
                        )
                    }
                    .getOrNull()
            }
            ?: (songId to song)
        watched[songId] = now to chosen
        chosen
    }

    private val prefetcher = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "AmeYTPrefetch").apply { isDaemon = true }
    }

    /** Asks ahead for what [uri] will need, so the next item starts at once. */
    fun prefetch(uri: String) {
        val id = YouTubeBackend.videoIdOfUri(uri)
        prefetcher.execute {
            runCatching {
                if (uri.startsWith(YouTubeBackend.TRACK_PREFIX) && YouTubeVideoMode.wantsVideo(uri)) {
                    watchSource(id)
                } else {
                    info(id)
                }
            }
        }
    }

    /** Drops what is kept about [videoId], so its addresses are asked for again. */
    fun forget(videoId: String) {
        infos.remove(videoId)
        watched.remove(videoId)
    }

    private const val INFO_TTL_MS = 60 * 60 * 1000L

    @Volatile
    private var initialised = false

    /** Idempotent: several tracks resolve on the same thread pool. */
    @Synchronized
    fun ensureNewPipe() {
        if (initialised) return
        NewPipe.init(NewPipeDownloader.create(), Localization.DEFAULT)
        initialised = true
    }
}
