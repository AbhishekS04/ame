package com.abhishek.ame.backend.fidelity

import android.util.Log
import com.abhishek.ame.data.PreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * The playback fidelity tier currently active.
 */
enum class AudioFidelity {
    /** Standard YouTube Music audio (Opus ~160kbps / AAC ~128kbps / Low ~48kbps). No badge shown. */
    STANDARD,

    /** Bit-perfect Lossless (FLAC up to 1411 kbps, 16-bit/44.1kHz or 320kbps Studio Master). Shows "LOSSLESS" pill. */
    LOSSLESS,

    /** Dolby Atmos & 3D Spatial Audio (Multichannel / Binaural HRTF staging). Shows "DOLBY ATMOS" pill. */
    DOLBY_ATMOS,
}

/**
 * Global audio fidelity coordinator. Tracks what stream tier is actively sounding.
 */
object FidelityManager {
    private const val TAG = "AmeFidelityMgr"

    private val _activeFidelity = MutableStateFlow(AudioFidelity.STANDARD)
    val activeFidelity: StateFlow<AudioFidelity> = _activeFidelity.asStateFlow()

    private val trackFidelities = ConcurrentHashMap<String, AudioFidelity>()

    /** The media item whose decoded stream is allowed to drive the badge. */
    @Volatile
    private var activeMediaId: String? = null

    data class TrackMetadata(
        val title: String,
        val artist: String,
        val durationSec: Long = 0L,
    )

    private val trackRegistry = ConcurrentHashMap<String, TrackMetadata>()

    fun registerTrack(uri: String, title: String, artist: String, durationSec: Long = 0L) {
        if (uri.isBlank() || (title.isBlank() && artist.isBlank())) return
        val meta = TrackMetadata(title = title.trim(), artist = artist.trim(), durationSec = durationSec)
        trackRegistry[uri] = meta
        val norm = normalizeId(uri)
        if (norm.isNotBlank()) {
            trackRegistry[norm] = meta
            trackRegistry["ytmusic:track:$norm"] = meta
            trackRegistry["ytmusic:video:$norm"] = meta
        }
    }

    fun getTrackMetadata(uri: String?): TrackMetadata? {
        if (uri.isNullOrBlank()) return null
        val norm = normalizeId(uri)
        return trackRegistry[uri] ?: trackRegistry[norm] ?: trackRegistry["ytmusic:track:$norm"]
    }

    fun normalizeId(id: String?): String {
        if (id.isNullOrBlank()) return ""
        return id.substringBefore('#')
            .removePrefix("ytmusic:track:")
            .removePrefix("ytmusic:video:")
            .removePrefix("spotify:track:")
            .trim()
    }

    fun recordTrackFidelity(mediaId: String, fidelity: AudioFidelity) {
        val norm = normalizeId(mediaId)
        trackFidelities[mediaId] = fidelity
        if (norm.isNotBlank()) {
            trackFidelities[norm] = fidelity
            trackFidelities["ytmusic:track:$norm"] = fidelity
        }
        // Recording a result is deliberately not activation. Resolver work for
        // the next item happens while the current item is audible; changing the
        // process-wide state here made the Lossless badge appear on the wrong
        // song, sometimes minutes after it started.
        Log.i(TAG, "Recorded fidelity: $fidelity for mediaId=$mediaId (norm=$norm)")
    }

    /**
     * Links two aliases of the same playing track (e.g. videoId and songId from retitle).
     * Ensures replacing a media item in the playlist does not wipe the active fidelity badge.
     */
    fun linkAliases(fromId: String, toId: String) {
        val normFrom = normalizeId(fromId)
        val normTo = normalizeId(toId)
        val fidelity = trackFidelities[fromId]
            ?: trackFidelities[normFrom]
            ?: trackFidelities["ytmusic:track:$normFrom"]
            ?: _activeFidelity.value.takeIf { it != AudioFidelity.STANDARD }

        if (fidelity != null) {
            recordTrackFidelity(toId, fidelity)
            if (normTo.isNotBlank()) {
                recordTrackFidelity(normTo, fidelity)
                recordTrackFidelity("ytmusic:track:$normTo", fidelity)
            }
            Log.i(TAG, "Linked alias $fromId -> $toId with fidelity $fidelity")
        }
    }

    fun onTrackTransition(
        mediaId: String?,
        preferredQuality: PreferencesStore.YouTubeQuality? = null,
    ) {
        if (mediaId.isNullOrBlank()) {
            activeMediaId = null
            _activeFidelity.value = AudioFidelity.STANDARD
            return
        }
        activeMediaId = mediaId
        // If the track already has recorded fidelity from prefetch/resolution, smoothly carry it over
        // instead of dropping to STANDARD for a few frames and popping back in.
        val norm = normalizeId(mediaId)
        val known = trackFidelities[mediaId] ?: trackFidelities[norm] ?: trackFidelities["ytmusic:track:$norm"]
        _activeFidelity.value = known ?: AudioFidelity.STANDARD
        Log.i(TAG, "onTrackTransition mediaId=$mediaId -> fidelity=${_activeFidelity.value}")
    }

    /**
     * Activates a fidelity only after the decoder reports the stream format.
     * This is intentionally media-bound so a prefetched next item cannot affect
     * the track currently shown by the player.
     */
    fun confirmDecoded(mediaId: String?, fidelity: AudioFidelity) {
        if (mediaId.isNullOrBlank()) return
        val expected = activeMediaId ?: return
        if (normalizeId(expected) != normalizeId(mediaId)) return
        recordTrackFidelity(mediaId, fidelity)
        _activeFidelity.value = fidelity
        Log.i(TAG, "Activated decoded fidelity: $fidelity for mediaId=$mediaId")
    }

    fun setFidelity(fidelity: AudioFidelity) {
        // Kept for callers outside the YouTube resolver. YouTube playback uses
        // confirmDecoded(), never this unbound setter.
        _activeFidelity.value = fidelity
    }

    fun reset() {
        activeMediaId = null
        _activeFidelity.value = AudioFidelity.STANDARD
        trackFidelities.clear()
    }
}
