package com.abhishek.ame.recognition

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Frequency peak detected in the time-frequency spectrogram.
 */
data class FrequencyPeak(
    val fftPassNumber: Int,
    val peakMagnitude: Int,
    val correctedPeakFrequencyBin: Int,
)

/**
 * Pure Kotlin in-place Radix-2 Cooley-Tukey Fast Fourier Transform (size 2048).
 */
internal class FastFourierTransform(private val n: Int = 2048) {
    private val bitRev = IntArray(n)
    private val cosTable = FloatArray(n / 2)
    private val sinTable = FloatArray(n / 2)

    init {
        val bits = (ln(n.toDouble()) / ln(2.0)).toInt()
        for (i in 0 until n) {
            var rev = 0
            var temp = i
            for (j in 0 until bits) {
                rev = (rev shl 1) or (temp and 1)
                temp = temp shr 1
            }
            bitRev[i] = rev
        }
        for (i in 0 until n / 2) {
            val angle = -2.0 * Math.PI * i / n
            cosTable[i] = cos(angle).toFloat()
            sinTable[i] = Math.sin(angle).toFloat()
        }
    }

    fun forward(real: FloatArray, imag: FloatArray) {
        for (i in 0 until n) {
            val j = bitRev[i]
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR
                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
        }
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val step = n / len
            var i = 0
            while (i < n) {
                var k = 0
                for (j in 0 until halfLen) {
                    val c = cosTable[k]
                    val s = sinTable[k]
                    val tr = real[i + j + halfLen] * c - imag[i + j + halfLen] * s
                    val ti = real[i + j + halfLen] * s + imag[i + j + halfLen] * c
                    real[i + j + halfLen] = real[i + j] - tr
                    imag[i + j + halfLen] = imag[i + j] - ti
                    real[i + j] += tr
                    imag[i + j] += ti
                    k += step
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/**
 * Generates official Shazam-compatible audio fingerprint signatures (`data:audio/vnd.shazam.sig;base64,...`)
 * from 16kHz 16-bit mono PCM audio.
 */
class ShazamSignatureGenerator {

    private val fft = FastFourierTransform(2048)

    // Precomputed Hanning window of size 2048
    private val hanning = FloatArray(2048) { i ->
        (0.5f * (1.0 - cos(2.0 * Math.PI * (i + 1) / 2049.0))).toFloat()
    }

    private val ringBuffer = ShortArray(2048)
    private var ringBufferIndex = 0

    private val reorderedRingBuffer = FloatArray(2048)
    private val imagBuffer = FloatArray(2048)

    private val fftOutputs = Array(256) { FloatArray(1025) }
    private var fftOutputsIndex = 0

    private val spreadFftOutputs = Array(256) { FloatArray(1025) }
    private var spreadOutputsIndex = 0

    private var numSpreadFftsDone = 0

    // Frequency bands: 0: 250..520Hz, 1: 520..1450Hz, 2: 1450..3500Hz, 3: 3500..5500Hz
    private val bandPeaks = Array(4) { mutableListOf<FrequencyPeak>() }

    fun processAudio(samples: ShortArray): String {
        ringBufferIndex = 0
        fftOutputsIndex = 0
        spreadOutputsIndex = 0
        numSpreadFftsDone = 0
        for (b in 0..3) bandPeaks[b].clear()

        var sampleIndex = 0
        while (sampleIndex + 128 <= samples.size) {
            doFft(samples, sampleIndex)
            doPeakSpreading()
            numSpreadFftsDone++

            if (numSpreadFftsDone >= 46) {
                doPeakRecognition()
            }
            sampleIndex += 128
        }

        return encodeToUri(samples.size)
    }

    private fun doFft(samples: ShortArray, offset: Int) {
        // Copy 128 samples to circular ring buffer
        for (i in 0 until 128) {
            ringBuffer[(ringBufferIndex + i) and 2047] = samples[offset + i]
        }
        ringBufferIndex = (ringBufferIndex + 128) and 2047

        // Reorder ring buffer and apply Hanning window
        for (i in 0 until 2048) {
            val sample = ringBuffer[(ringBufferIndex + i) and 2047].toFloat()
            reorderedRingBuffer[i] = sample * hanning[i]
            imagBuffer[i] = 0f
        }

        // Forward FFT
        fft.forward(reorderedRingBuffer, imagBuffer)

        // Calculate power spectrum for 1025 positive frequency bins
        val out = fftOutputs[fftOutputsIndex]
        for (bin in 0..1024) {
            val r = reorderedRingBuffer[bin]
            val im = imagBuffer[bin]
            out[bin] = max(1e-10f, (r * r + im * im) / 131072f) // 131072 = 1 shl 17
        }

        fftOutputsIndex = (fftOutputsIndex + 1) and 255
    }

    private fun doPeakSpreading() {
        val realFft = fftOutputs[(fftOutputsIndex - 1) and 255]
        val spread = spreadFftOutputs[spreadOutputsIndex]

        // Frequency-domain spreading
        for (pos in 0..1022) {
            spread[pos] = max(realFft[pos], max(realFft[pos + 1], realFft[pos + 2]))
        }
        spread[1023] = realFft[1023]
        spread[1024] = realFft[1024]

        // Time-domain spreading across former frames [1, 3, 6]
        for (f in intArrayOf(1, 3, 6)) {
            val prevIdx = (spreadOutputsIndex - f) and 255
            val prevSpread = spreadFftOutputs[prevIdx]
            for (pos in 0..1024) {
                prevSpread[pos] = max(prevSpread[pos], spread[pos])
            }
        }

        spreadOutputsIndex = (spreadOutputsIndex + 1) and 255
    }

    private fun doPeakRecognition() {
        val fftMinus46 = fftOutputs[(fftOutputsIndex - 46) and 255]
        val spreadMinus49 = spreadFftOutputs[(spreadOutputsIndex - 49) and 255]

        for (binPos in 10..1014) {
            if (fftMinus46[binPos] >= 1.0f / 64.0f && fftMinus46[binPos] >= spreadMinus49[binPos - 1]) {
                var maxNeighborInFftMinus49 = 0.0f
                for (neighborOffset in intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)) {
                    val nVal = spreadMinus49[binPos + neighborOffset]
                    if (nVal > maxNeighborInFftMinus49) maxNeighborInFftMinus49 = nVal
                }

                if (fftMinus46[binPos] > maxNeighborInFftMinus49) {
                    var maxNeighborInOther = maxNeighborInFftMinus49
                    for (otherOffset in intArrayOf(-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249)) {
                        val otherFft = spreadFftOutputs[(spreadOutputsIndex + otherOffset) and 255]
                        val oVal = otherFft[binPos - 1]
                        if (oVal > maxNeighborInOther) maxNeighborInOther = oVal
                    }

                    if (fftMinus46[binPos] > maxNeighborInOther) {
                        val fftPassNumber = numSpreadFftsDone - 46
                        val peakMag = (ln(max(fftMinus46[binPos], 1.0f / 64.0f).toDouble()) * 1477.3 + 6144.0).toFloat()
                        val peakMagBefore = (ln(max(fftMinus46[binPos - 1], 1.0f / 64.0f).toDouble()) * 1477.3 + 6144.0).toFloat()
                        val peakMagAfter = (ln(max(fftMinus46[binPos + 1], 1.0f / 64.0f).toDouble()) * 1477.3 + 6144.0).toFloat()

                        val peakVar1 = peakMag * 2.0f - peakMagBefore - peakMagAfter
                        val peakVar2 = if (peakVar1 > 0f) (peakMagAfter - peakMagBefore) * 32.0f / peakVar1 else 0.0f
                        val correctedPeakFreqBin = ((binPos * 64) + peakVar2.toInt()).coerceIn(0, 65535)

                        val freqHz = correctedPeakFreqBin.toFloat() * (16000.0f / 2.0f / 1024.0f / 64.0f)
                        val band = when (freqHz.toInt()) {
                            in 250..519 -> 0
                            in 520..1449 -> 1
                            in 1450..3499 -> 2
                            in 3500..5500 -> 3
                            else -> -1
                        }

                        if (band >= 0) {
                            bandPeaks[band].add(
                                FrequencyPeak(
                                    fftPassNumber = fftPassNumber,
                                    peakMagnitude = peakMag.toInt().coerceIn(0, 65535),
                                    correctedPeakFrequencyBin = correctedPeakFreqBin,
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun encodeToUri(numberSamples: Int): String {
        val header = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0xcafe2580.toInt()) // magic1
        header.putInt(0) // crc32 placeholder at 4
        header.putInt(0) // size_minus_header at 8
        header.putInt(0x94119c00.toInt()) // magic2 at 12
        header.putInt(0); header.putInt(0); header.putInt(0) // void1
        header.putInt(3 shl 27) // sample_rate_id: 3 for 16000 Hz, left shifted by 27
        header.putInt(0); header.putInt(0) // void2
        header.putInt((numberSamples + (16000f * 0.24f).toInt())) // number_samples_plus_divided_sample_rate
        header.putInt((15 shl 19) + 0x40000) // fixed_value: 0x7c0000

        val bodyStream = ByteArrayOutputStream()
        val bodyBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        bodyBuffer.putInt(0x40000000)
        bodyBuffer.putInt(0) // will fill size_minus_header
        bodyStream.write(bodyBuffer.array())

        for (band in 0..3) {
            val peaks = bandPeaks[band]
            if (peaks.isEmpty()) continue
            peaks.sortBy { it.fftPassNumber }

            val peaksStream = ByteArrayOutputStream()
            var lastFftPass = 0
            for (peak in peaks) {
                val delta = peak.fftPassNumber - lastFftPass
                if (delta >= 255) {
                    peaksStream.write(0xff)
                    val passBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(peak.fftPassNumber).array()
                    peaksStream.write(passBuf)
                    lastFftPass = peak.fftPassNumber
                }
                val step = (peak.fftPassNumber - lastFftPass).coerceIn(0, 254)
                peaksStream.write(step)
                val pBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                pBuf.putShort(peak.peakMagnitude.toShort())
                pBuf.putShort(peak.correctedPeakFrequencyBin.toShort())
                peaksStream.write(pBuf.array())
                lastFftPass = peak.fftPassNumber
            }

            val peaksBytes = peaksStream.toByteArray()
            val bandHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            bandHeader.putInt(0x60030040.toInt() + band)
            bandHeader.putInt(peaksBytes.size)
            bodyStream.write(bandHeader.array())
            bodyStream.write(peaksBytes)

            val padding = (4 - (peaksBytes.size % 4)) % 4
            for (p in 0 until padding) {
                bodyStream.write(0)
            }
        }

        val bodyBytes = bodyStream.toByteArray()
        val totalSize = 48 + bodyBytes.size
        val sizeMinusHeader = totalSize - 48

        // Write size_minus_header in header at 8
        header.putInt(8, sizeMinusHeader)

        // Write size_minus_header in body at 4
        val bodyBuf = ByteBuffer.wrap(bodyBytes).order(ByteOrder.LITTLE_ENDIAN)
        bodyBuf.putInt(4, sizeMinusHeader)

        // Combine header and body
        val combined = ByteArray(totalSize)
        System.arraycopy(header.array(), 0, combined, 0, 48)
        System.arraycopy(bodyBytes, 0, combined, 48, bodyBytes.size)

        // Compute CRC32 over all bytes starting at index 8 (excluding first 8 bytes: magic1 + crc32)
        val crc = CRC32()
        crc.update(combined, 8, totalSize - 8)
        val crcValue = crc.value.toInt()

        val finalHeader = ByteBuffer.wrap(combined).order(ByteOrder.LITTLE_ENDIAN)
        finalHeader.putInt(4, crcValue)

        val b64 = android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
        return "data:audio/vnd.shazam.sig;base64,$b64"
    }
}
