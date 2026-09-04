package com.visualizerstudio.app.render.audio

import android.util.Log
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Hasil decode: sample mono float32 (-1.0..1.0) + sample rate asli file. */
data class DecodedAudio(val samples: FloatArray, val sampleRateHz: Int)

/**
 * Pembaca WAV robust multi-format (PCM 8/16/24/32-bit & IEEE float 32-bit), port dari
 * `safe_read_audio_data` di `spectrum_generator.py`. Dipakai [GlEsSpectrumRendererAdapter]
 * (lewat [com.visualizerstudio.app.render.gl.GlEsSpectrumRendererAdapter]) untuk membaca
 * `audioSamplePath` — file WAV hasil concat-audio yang sudah disiapkan RenderWorker Fase 2 —
 * sebelum dianalisis FFT per-frame oleh [AudioFftAnalyzer].
 */
object WavDecoder {

    private const val TAG = "WavDecoder"
    private const val FALLBACK_SAMPLE_RATE = 44100
    private const val FALLBACK_SECONDS = 5

    /**
     * @return audio mono float32 + sample rate. Kalau file gagal dibaca/rusak, kembalikan
     * silence [FALLBACK_SECONDS] detik (anti-crash, sama seperti fallback versi Python).
     */
    fun read(path: String): DecodedAudio {
        return try {
            RandomAccessFile(path, "r").use { raf -> parseWav(raf) }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membaca audio WAV $path: ${e.message}", e)
            DecodedAudio(FloatArray(FALLBACK_SAMPLE_RATE * FALLBACK_SECONDS), FALLBACK_SAMPLE_RATE)
        }
    }

    private fun parseWav(raf: RandomAccessFile): DecodedAudio {
        val header = ByteArray(12)
        raf.readFully(header)
        val riff = String(header, 0, 4, Charsets.US_ASCII)
        val waveTag = String(header, 8, 4, Charsets.US_ASCII)
        require(riff == "RIFF" && waveTag == "WAVE") {
            "Bukan file WAV valid (header RIFF/WAVE tidak ditemukan)"
        }

        var channels = 1
        var sampleRate = FALLBACK_SAMPLE_RATE
        var bitsPerSample = 16
        var audioFormat = 1 // 1 = PCM, 3 = IEEE float
        var dataBytes: ByteArray? = null

        val chunkHeader = ByteArray(8)
        while (raf.filePointer <= raf.length() - 8) {
            raf.readFully(chunkHeader)
            val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (chunkSize < 0) break

            when (chunkId) {
                "fmt " -> {
                    val fmt = ByteArray(chunkSize)
                    raf.readFully(fmt)
                    val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                    audioFormat = bb.short.toInt() and 0xFFFF
                    channels = bb.short.toInt() and 0xFFFF
                    sampleRate = bb.int
                    bb.int   // byte rate, tidak dipakai
                    bb.short // block align, tidak dipakai
                    bitsPerSample = bb.short.toInt() and 0xFFFF
                }
                "data" -> {
                    dataBytes = ByteArray(chunkSize)
                    raf.readFully(dataBytes)
                }
                else -> {
                    raf.seek(raf.filePointer + chunkSize)
                }
            }
            if (chunkSize % 2 == 1 && raf.filePointer < raf.length()) {
                raf.seek(raf.filePointer + 1) // padding byte untuk chunk berukuran ganjil
            }
        }

        val raw = dataBytes ?: throw IllegalStateException("Chunk 'data' tidak ditemukan di file WAV")
        val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)

        val samples: FloatArray = when {
            audioFormat == 3 && bitsPerSample == 32 -> {
                val n = raw.size / 4
                FloatArray(n) { bb.getFloat(it * 4) }
            }
            bitsPerSample == 16 -> {
                val n = raw.size / 2
                FloatArray(n) { bb.getShort(it * 2) / 32768.0f }
            }
            bitsPerSample == 8 -> {
                FloatArray(raw.size) { ((raw[it].toInt() and 0xFF) - 128) / 128.0f }
            }
            bitsPerSample == 24 -> {
                val n = raw.size / 3
                FloatArray(n) { i ->
                    val idx = i * 3
                    val b0 = raw[idx].toInt() and 0xFF
                    val b1 = raw[idx + 1].toInt() and 0xFF
                    val b2 = raw[idx + 2].toInt() // sign-extended, byte paling signifikan
                    val value = (b2 shl 16) or (b1 shl 8) or b0
                    value / 8388608.0f
                }
            }
            bitsPerSample == 32 -> {
                val n = raw.size / 4
                FloatArray(n) { bb.getInt(it * 4) / 2147483648.0f }
            }
            else -> {
                Log.w(TAG, "Bit depth $bitsPerSample tidak dikenal, fallback asumsi PCM 16-bit")
                val n = raw.size / 2
                FloatArray(n) { bb.getShort(it * 2) / 32768.0f }
            }
        }

        val mono = if (channels > 1) {
            val frames = samples.size / channels
            FloatArray(frames) { f ->
                var sum = 0f
                for (c in 0 until channels) sum += samples[f * channels + c]
                sum / channels
            }
        } else samples

        return DecodedAudio(mono, sampleRate)
    }
}
