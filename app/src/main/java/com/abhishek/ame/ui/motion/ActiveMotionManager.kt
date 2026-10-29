package com.abhishek.ame.ui.motion

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Single, long-lived video engine for motion artwork, reverse-engineered from
 * Apple Music's ActiveMotionManager and LocalActiveHlsMotionManagerRegistry.
 *
 * Keeps exactly one ExoPlayer instance across the entire app lifecycle, backed
 * by a persistent LRU disk cache and proactive HLS segment preloader for
 * instantaneous zero-latency track transitions without hardware decoder thrashing.
 */
@OptIn(UnstableApi::class)
object ActiveMotionManager {

    private const val TAG = "ActiveMotionManager"
    private var playerInstance: ExoPlayer? = null
    private var currentUrl: String? = null

    private var simpleCache: SimpleCache? = null
    private var cacheDataSourceFactory: CacheDataSource.Factory? = null
    private val preloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val preloadedUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        return simpleCache ?: run {
            val cacheDirectory = File(context.applicationContext.cacheDir, "motion_artwork_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(100L * 1024L * 1024L) // 100MB disk cache
            val db = StandaloneDatabaseProvider(context.applicationContext)
            SimpleCache(cacheDirectory, evictor, db).also { simpleCache = it }
        }
    }

    @Synchronized
    fun getCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        return cacheDataSourceFactory ?: run {
            val cache = getCache(context)
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Ame/1.2")
                .setConnectTimeoutMs(8000)
                .setReadTimeoutMs(10000)
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setCacheReadDataSourceFactory(FileDataSource.Factory())
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .also { cacheDataSourceFactory = it }
        }
    }

    @Synchronized
    fun getPlayer(context: Context): ExoPlayer {
        return playerInstance ?: ExoPlayer.Builder(context.applicationContext)
            .build()
            .apply {
                setAudioAttributes(AudioAttributes.DEFAULT, false)
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ALL
                // Apple's idiom: disable user seek tracking overhead
                setSeekBackIncrementMs(Long.MAX_VALUE)
                setSeekForwardIncrementMs(Long.MAX_VALUE)
                playerInstance = this
            }
    }

    fun isPowerSave(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isPowerSaveMode == true
    }

    fun setMedia(context: Context, url: String, playing: Boolean = true) {
        val player = getPlayer(context)
        if (currentUrl != url) {
            currentUrl = url
            val factory = getCacheDataSourceFactory(context)
            val mediaItem = MediaItem.Builder().setUri(url).setMediaId(url).build()
            val mediaSource = HlsMediaSource.Factory(factory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)
            player.setMediaSource(mediaSource)
            player.prepare()
        }
        player.playWhenReady = playing && !isPowerSave(context)
    }

    /**
     * Proactively prefetches the initial segments of upcoming motion video streams
     * into the local disk cache so that skipping to this track starts playing instantly.
     */
    fun preload(context: Context, url: String?) {
        if (url.isNullOrBlank() || !url.startsWith("http")) return
        if (!preloadedUrls.add(url)) return // Already requested

        preloadScope.launch {
            runCatching {
                val factory = getCacheDataSourceFactory(context)
                val mediaItem = MediaItem.fromUri(Uri.parse(url))
                val downloader = HlsDownloader(mediaItem, factory)
                downloader.download { _, bytesDownloaded, _ ->
                    // Download first 1.5MB for instantaneous local start
                    if (bytesDownloaded >= 1_500_000L) {
                        downloader.cancel()
                    }
                }
            }.onFailure {
                android.util.Log.w(TAG, "Preload failed for $url: ${it.message}")
            }
        }
    }

    fun pause() {
        playerInstance?.playWhenReady = false
    }

    fun resume(context: Context) {
        if (!isPowerSave(context)) {
            playerInstance?.playWhenReady = true
        }
    }
}
