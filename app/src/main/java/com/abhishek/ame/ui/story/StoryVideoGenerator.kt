package com.abhishek.ame.ui.story

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import com.abhishek.ame.AmeApplication
import com.abhishek.ame.backend.youtube.YouTubeBackend
import com.abhishek.ame.backend.youtube.YouTubeStreams
import com.abhishek.ame.download.YouTubeDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Generates an MP4 video clip of the Ame story with embedded audio for Instagram Stories & Snapchat.
 * Interleaves H.264 video frames and AAC audio samples for perfectly synced playback.
 */
object StoryVideoGenerator {

    private const val VIDEO_MIME = "video/avc"
    private const val FRAME_RATE = 30
    private const val DURATION_SECONDS = 15

    /**
     * Resolves an audio file for the track, fetching a 15-30s snippet if not cached on disk.
     */
    suspend fun resolveAudioFile(context: Context, info: StoryTrackInfo): File? = withContext(Dispatchers.IO) {
        // 1. Direct local file (if downloaded or local library)
        if (!info.localAudioPath.isNullOrBlank()) {
            val f = File(info.localAudioPath)
            if (f.exists() && f.length() > 5000) return@withContext f
        }

        // 2. Kept YouTube download file
        val kept = YouTubeDownloads.fileFor(context, info.uri)
        if (kept != null && kept.exists() && kept.length() > 5000) return@withContext kept

        // 3. Online stream fetch (first 2 MB of M4A audio)
        runCatching {
            val id = YouTubeBackend.videoIdOfUri(info.uri)
            if (id.isBlank()) return@withContext null

            YouTubeStreams.ensureNewPipe(context)
            val streamInfo = YouTubeStreams.info(id)

            // Prefer AAC/m4a stream which MediaMuxer natively accepts in MP4
            val m4aStream = streamInfo.audioStreams.firstOrNull {
                it.format?.suffix == "m4a" || it.codec?.contains("mp4a", ignoreCase = true) == true
            } ?: streamInfo.audioStreams.firstOrNull() ?: return@withContext null

            val cacheStories = File(context.cacheDir, "stories").apply { if (!exists()) mkdirs() }
            val tempAudio = File(cacheStories, "story_audio_${id}.m4a")

            // Reuse if cached and valid
            if (tempAudio.exists() && tempAudio.length() > 80_000) return@withContext tempAudio

            val client = (context.applicationContext as? AmeApplication)?.sharedHttpClient
                ?: OkHttpClient()
            val request = Request.Builder()
                .url(m4aStream.content)
                .header("Range", "bytes=0-2097151") // 2 MB (approx 90-120 seconds of AAC audio)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 206) {
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(tempAudio).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            if (tempAudio.exists() && tempAudio.length() > 10_000) tempAudio else null
        }.getOrNull()
    }

    suspend fun generateStoryVideo(
        context: Context,
        frameBitmap: Bitmap,
        info: StoryTrackInfo,
        filename: String = "ame_story_clip_${System.currentTimeMillis()}.mp4",
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "stories").apply { if (!exists()) mkdirs() }
        val outputFile = File(dir, filename)

        // 1. Resolve Audio File
        val audioFile = resolveAudioFile(context, info)
        val audioExtractor = if (audioFile != null && audioFile.exists()) {
            runCatching {
                MediaExtractor().apply {
                    setDataSource(audioFile.absolutePath)
                }
            }.getOrNull()
        } else null

        var audioSourceTrack = -1
        var audioFormat: MediaFormat? = null
        if (audioExtractor != null) {
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioSourceTrack = i
                    audioExtractor.selectTrack(i)
                    audioFormat = format
                    break
                }
            }
        }

        val width = 720
        val height = 1280
        val scaledBitmap = Bitmap.createScaledBitmap(frameBitmap, width, height, true)

        val totalFrames = FRAME_RATE * DURATION_SECONDS
        val bitRate = 4_000_000
        val frameDurationUs = 1_000_000L / FRAME_RATE
        val maxDurationUs = DURATION_SECONDS * 1_000_000L

        val videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(VIDEO_MIME)
        encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface: Surface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var muxerStarted = false

        val audioBuffer = ByteBuffer.allocateDirect(128 * 1024)
        val audioBufferInfo = MediaCodec.BufferInfo()
        val videoBufferInfo = MediaCodec.BufferInfo()

        try {
            // Encode and interleave video & audio
            for (frame in 0 until totalFrames) {
                val currentPtsUs = frame * frameDurationUs
                val nextPtsUs = (frame + 1) * frameDurationUs

                // Draw hardware frame
                val canvas: Canvas = inputSurface.lockHardwareCanvas()
                canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
                inputSurface.unlockCanvasAndPost(canvas)

                // Drain video encoder
                val timeoutUs = 10_000L
                while (true) {
                    val status = encoder.dequeueOutputBuffer(videoBufferInfo, timeoutUs)
                    if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        videoTrackIndex = muxer.addTrack(encoder.outputFormat)
                        if (audioExtractor != null && audioFormat != null) {
                            runCatching {
                                audioTrackIndex = muxer.addTrack(audioFormat)
                            }
                        }
                        muxer.start()
                        muxerStarted = true
                    } else if (status >= 0) {
                        val encodedData: ByteBuffer? = encoder.getOutputBuffer(status)
                        if (encodedData != null && muxerStarted) {
                            // Interleave audio up to this video packet's timestamp
                            if (audioExtractor != null && audioTrackIndex >= 0) {
                                while (true) {
                                    val sampleTime = audioExtractor.sampleTime
                                    if (sampleTime < 0 || sampleTime > videoBufferInfo.presentationTimeUs || sampleTime > maxDurationUs) {
                                        break
                                    }
                                    audioBuffer.clear()
                                    val sampleSize = audioExtractor.readSampleData(audioBuffer, 0)
                                    if (sampleSize < 0) break

                                    audioBufferInfo.offset = 0
                                    audioBufferInfo.size = sampleSize
                                    audioBufferInfo.presentationTimeUs = sampleTime
                                    audioBufferInfo.flags = audioExtractor.sampleFlags

                                    muxer.writeSampleData(audioTrackIndex, audioBuffer, audioBufferInfo)
                                    audioExtractor.advance()
                                }
                            }

                            if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && videoBufferInfo.size > 0) {
                                encodedData.position(videoBufferInfo.offset)
                                encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size)
                                muxer.writeSampleData(videoTrackIndex, encodedData, videoBufferInfo)
                            }
                        }
                        encoder.releaseOutputBuffer(status, false)
                    }
                }
            }

            // Signal End of Stream
            encoder.signalEndOfInputStream()

            // Final drain of video & audio
            while (true) {
                val status = encoder.dequeueOutputBuffer(videoBufferInfo, 10_000L)
                if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break
                } else if (status >= 0) {
                    val encodedData: ByteBuffer? = encoder.getOutputBuffer(status)
                    if (encodedData != null && muxerStarted && (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && videoBufferInfo.size > 0) {
                        encodedData.position(videoBufferInfo.offset)
                        encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size)
                        muxer.writeSampleData(videoTrackIndex, encodedData, videoBufferInfo)
                    }
                    encoder.releaseOutputBuffer(status, false)
                    if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }

            // Final audio flush
            if (audioExtractor != null && audioTrackIndex >= 0 && muxerStarted) {
                while (true) {
                    val sampleTime = audioExtractor.sampleTime
                    if (sampleTime < 0 || sampleTime > maxDurationUs) break

                    audioBuffer.clear()
                    val sampleSize = audioExtractor.readSampleData(audioBuffer, 0)
                    if (sampleSize < 0) break

                    audioBufferInfo.offset = 0
                    audioBufferInfo.size = sampleSize
                    audioBufferInfo.presentationTimeUs = sampleTime
                    audioBufferInfo.flags = audioExtractor.sampleFlags

                    muxer.writeSampleData(audioTrackIndex, audioBuffer, audioBufferInfo)
                    audioExtractor.advance()
                }
            }
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { inputSurface.release() }
            runCatching { audioExtractor?.release() }
            runCatching {
                if (muxerStarted) {
                    muxer.stop()
                    muxer.release()
                }
            }
            if (scaledBitmap != frameBitmap) scaledBitmap.recycle()
        }

        outputFile
    }
}
