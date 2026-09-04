package com.visualizerstudio.app.render.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Analisis FFT audio real-time per-frame untuk mengisi uniform `u_bars[120]` shader spectrum
 * (lihat tabel uniform section 6.0 blueprint). Port dari fallback CPU (`else:` non-Torch) di
 * fungsi `generate_custom_spectrum_gpu` — `spectrum_generator.py`: 120 bar frekuensi skala
 * logaritmik dengan kompresi + kurva EQ + smoothing attack/release + normalisasi rolling-peak,
 * supaya visual tidak "flicker" antar frame.
 *
 * Instance ini STATEFUL (`prevBars`, `rollingPeak` persisten antar-frame) — WAJIB satu instance
 * per sesi render satu spectrum (jangan dipakai bersamaan lintas-tugas). Panggil [reset] kalau
 * instance yang sama mau dipakai ulang untuk sesi render baru.
 */
class AudioFftAnalyzer(private val numBars: Int = 120, private val fftSize: Int = 2048) {

    private val window = FftMath.hannWindow(fftSize)
    private val fftLen = fftSize / 2 + 1

    // log_indices = np.logspace(log10(1), log10(fft_len-1), num_bars)
    private val logIndices = DoubleArray(numBars) { i ->
        val t = if (numBars == 1) 0.0 else i.toDouble() / (numBars - 1)
        val logStart = log10(1.0)
        val logEnd = log10((fftLen - 1).toDouble())
        10.0.pow(logStart + t * (logEnd - logStart))
    }

    // compress_factors = np.linspace(0.55, 0.70, num_bars) ; eq_curve = np.linspace(1.0, 2.5, num_bars)
    private val compressFactors = FloatArray(numBars) { i ->
        0.55f + (0.70f - 0.55f) * i / max(1, numBars - 1)
    }
    private val eqCurve = FloatArray(numBars) { i ->
        1.0f + (2.5f - 1.0f) * i / max(1, numBars - 1)
    }

    private var prevBars = FloatArray(numBars)
    private var rollingPeak = 0.05f

    /** Reset state (`prevBars`, `rollingPeak`) — panggil sebelum mulai sesi render baru. */
    fun reset() {
        prevBars = FloatArray(numBars)
        rollingPeak = 0.05f
    }

    /**
     * Hitung 120 bar untuk frame ke-[frameIndex] dari sinyal [samples] mono @[sampleRate] Hz.
     * @return FloatArray ukuran TETAP 120 (dipadding nol kalau [numBars] < 120), siap dikirim
     * langsung sebagai uniform `u_bars[120]` lewat [com.visualizerstudio.app.render.gl.GlEsSpectrumRenderer].
     */
    fun computeBars(samples: FloatArray, sampleRate: Int, fps: Int, frameIndex: Int): FloatArray {
        val centerSample = (frameIndex * (sampleRate.toDouble() / fps)).toInt()
        val startSample = max(0, centerSample - fftSize / 2)

        val chunk = FloatArray(fftSize)
        for (i in 0 until fftSize) {
            val srcIdx = startSample + i
            chunk[i] = if (srcIdx in samples.indices) samples[srcIdx] * window[i] else 0f
        }

        val magnitude = FftMath.rfftMagnitude(chunk)
        for (i in magnitude.indices) magnitude[i] = magnitude[i] / (fftSize / 2.0f)

        val frameMax = magnitude.maxOrNull() ?: 0f
        rollingPeak = 0.95f * rollingPeak + 0.05f * max(frameMax, 1e-4f)

        val rawBars = FloatArray(numBars)
        for (b in 0 until numBars) {
            val pos = logIndices[b]
            val idxLow = pos.toInt().coerceIn(0, fftLen - 1)
            val idxHigh = min(idxLow + 1, fftLen - 1)
            val frac = (pos - idxLow).toFloat()
            val normLow = magnitude[idxLow] / rollingPeak
            val normHigh = magnitude[idxHigh] / rollingPeak
            val interp = normLow * (1f - frac) + normHigh * frac
            rawBars[b] = interp.pow(compressFactors[b]) * 0.8f * eqCurve[b]
        }

        val bars = FloatArray(numBars)
        for (b in 0 until numBars) {
            bars[b] = if (rawBars[b] > prevBars[b]) {
                0.85f * rawBars[b] + 0.15f * prevBars[b] // attack cepat
            } else {
                0.20f * rawBars[b] + 0.80f * prevBars[b] // release lambat
            }
            bars[b] = bars[b].coerceIn(0f, 1f)
        }
        prevBars = bars.copyOf()

        if (numBars == 120) return bars
        val padded = FloatArray(120)
        bars.copyInto(padded, 0, 0, min(numBars, 120))
        return padded
    }
}
