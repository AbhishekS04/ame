package com.abhishek.ame.recognition

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.sqrt

data class RecognizedTrack(
    val title: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

sealed interface RecognitionState {
    object Idle : RecognitionState
    object Listening : RecognitionState
    object Processing : RecognitionState
    data class Matched(val track: RecognizedTrack) : RecognitionState
    data class NoMatch(val reason: String = "No match found") : RecognitionState
    data class Error(val message: String) : RecognitionState
}

class ShazamRecognizer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val historyStore: RecognitionHistoryStore = RecognitionHistoryStore(context),
) {
    private val _state = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val state: StateFlow<RecognitionState> = _state.asStateFlow()

    private val _amplitudeRms = MutableStateFlow(0f)
    val amplitudeRms: StateFlow<Float> = _amplitudeRms.asStateFlow()

    val history: StateFlow<List<RecognizedTrack>> = historyStore.history

    private var recognitionJob: Job? = null
    private val sigGen = ShazamSignatureGenerator()

    fun reset() {
        cancel()
        _state.value = RecognitionState.Idle
        _amplitudeRms.value = 0f
    }

    fun cancel() {
        recognitionJob?.cancel()
        recognitionJob = null
        _amplitudeRms.value = 0f
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        cancel()
        _state.value = RecognitionState.Listening

        recognitionJob = scope.launch(Dispatchers.IO) {
            var audioRecord: AudioRecord? = null
            try {
                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                val bufferSize = minBufferSize.coerceAtLeast(sampleRate * 2)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize,
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    _state.value = RecognitionState.Error("Microphone not available")
                    return@launch
                }

                audioRecord.startRecording()

                // Target: 3.5 seconds of recording at 16kHz (56,000 samples)
                val totalTargetSamples = (sampleRate * 3.5).toInt()
                val recordedSamples = ShortArray(totalTargetSamples)
                var samplesRead = 0
                val chunk = ShortArray(1024)

                while (isActive && samplesRead < totalTargetSamples) {
                    val count = audioRecord.read(chunk, 0, minOf(chunk.size, totalTargetSamples - samplesRead))
                    if (count > 0) {
                        System.arraycopy(chunk, 0, recordedSamples, samplesRead, count)
                        samplesRead += count

                        // Calculate live RMS amplitude for radar / waveform animation
                        var sumSquares = 0.0
                        for (i in 0 until count) {
                            val v = chunk[i].toDouble()
                            sumSquares += v * v
                        }
                        val rms = sqrt(sumSquares / count) / 32768.0
                        _amplitudeRms.value = (rms * 3.8).toFloat().coerceIn(0f, 1f)
                    } else if (count < 0) {
                        break
                    }
                }

                audioRecord.stop()
                audioRecord.release()
                audioRecord = null
                _amplitudeRms.value = 0f

                if (!isActive) return@launch

                _state.value = RecognitionState.Processing

                // Generate signature uri
                val signatureUri = sigGen.processAudio(recordedSamples)
                Log.d(TAG, "Generated Shazam signature URI length: ${signatureUri.length}")

                // Dispatch to Shazam API
                val match = queryShazam(signatureUri, 3500)
                if (match != null) {
                    historyStore.addTrack(match)
                    _state.value = RecognitionState.Matched(match)
                } else {
                    _state.value = RecognitionState.NoMatch()
                }

            } catch (e: CancellationException) {
                _amplitudeRms.value = 0f
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Recognition failed", e)
                _state.value = RecognitionState.Error(e.message ?: "Recognition error")
            } finally {
                runCatching {
                    audioRecord?.stop()
                    audioRecord?.release()
                }
                _amplitudeRms.value = 0f
            }
        }
    }

    private suspend fun queryShazam(sigUri: String, sampleMs: Int): RecognizedTrack? =
        withContext(Dispatchers.IO) {
            val uuid1 = UUID.randomUUID().toString().uppercase()
            val uuid2 = UUID.randomUUID().toString().lowercase()
            val urlStr = "https://amp.shazam.com/discovery/v5/en/US/android/-/tag/$uuid1/$uuid2" +
                    "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3"

            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Content-Language", "en_US")
                conn.setRequestProperty("User-Agent", "Shazam/14.28.0 (Android; 34; Pixel 8)")

                val ts = System.currentTimeMillis()
                val body = JSONObject().apply {
                    put("geolocation", JSONObject().apply {
                        put("altitude", 300)
                        put("latitude", 45)
                        put("longitude", 2)
                    })
                    put("signature", JSONObject().apply {
                        put("samplems", sampleMs)
                        put("timestamp", ts)
                        put("uri", sigUri)
                    })
                    put("timestamp", ts)
                    put("timezone", "Europe/Paris")
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val respStr = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use {
                        it.readText()
                    }
                    val json = JSONObject(respStr)
                    if (json.has("track")) {
                        val track = json.getJSONObject("track")
                        val title = track.optString("title", "").trim()
                        val artist = track.optString("subtitle", "").trim()
                        val images = track.optJSONObject("images")
                        val coverUrl = images?.optString("coverarthq")?.takeIf { it.isNotBlank() }
                            ?: images?.optString("coverart")?.takeIf { it.isNotBlank() }

                        if (title.isNotBlank()) {
                            return@withContext RecognizedTrack(
                                title = title,
                                artist = artist,
                                album = "",
                                coverUrl = coverUrl,
                                timestamp = System.currentTimeMillis(),
                            )
                        }
                    }
                } else {
                    Log.w(TAG, "Shazam responded with HTTP $responseCode")
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to Shazam", e)
                null
            } finally {
                conn.disconnect()
            }
        }

    fun removeHistoryItem(track: RecognizedTrack) {
        historyStore.removeTrack(track)
    }

    fun clearHistory() {
        historyStore.clear()
    }

    companion object {
        private const val TAG = "AmeShazam"
    }
}
