package com.visualizerstudio.app.render.audio

import kotlin.math.max

/**
 * Ekstraksi energi beat/bass untuk efek `useBeatZoom` (zoom seluruh frame mengikuti bass —
 * lihat `SpectrumConfig.useBeatZoom` di domain model Fase 1, dipetakan ke uniform
 * `u_zoom_factor` section 6.0). Port dari `analyze_audio_beats` di `spectrum_generator.py`.
 *
 * Catatan porting: versi Python punya jalur GPU CUDA (PyTorch `torch.stft`) DAN fallback CPU
 * (`np.fft.rfft` manual per-frame, band bass 20-250Hz). Jalur CUDA TIDAK diporting (device
 * compute umum di Android tidak sama, lihat catatan performa section 9 blueprint) — Fase 3
 * hanya mem-port jalur fallback CPU-nya, memakai [FftMath] yang sama seperti [AudioFftAnalyzer].
 */
class BeatEnergyExtractor(private val fftSize: Int = 2048) {

    private val window = FftMath.hannWindow(fftSize)

    /**
     * Cara pemakaian utama di [com.visualizerstudio.app.render.gl.GlEsSpectrumRendererAdapter]:
     * dihitung dari [bars] yang SUDAH dihasilkan [AudioFftAnalyzer.computeBars] pada frame yang
     * sama (menghindari FFT ganda per-frame) — persis rumus Python:
     * `bass_power = mean(bars[:num_bars//4]); zoom_factor = 1.0 + bass_power * 0.12`.
     *
     * @return faktor zoom, 1.0 = tanpa zoom.
     */
    fun zoomFactorFromBars(bars: FloatArray): Float {
        val quarter = max(1, bars.size / 4)
        var sum = 0f
        for (i in 0 until quarter) sum += bars[i]
        val bassPower = sum / quarter
        return 1.0f + bassPower * 0.12f
    }

    /**
     * Versi mandiri: hitung energi rata-rata band bass (20-250Hz) langsung dari sample mentah
     * tanpa bergantung pada bar spectrum yang sudah dihitung — dipakai kalau butuh sinyal beat
     * independen dari tampilan bar (mis. debugging/analitik terpisah).
     */
    fun bassEnergyAt(samples: FloatArray, sampleRate: Int, fps: Int, frameIndex: Int): Float {
        val centerSample = (frameIndex * (sampleRate.toDouble() / fps)).toInt()
        val startSample = max(0, centerSample - fftSize / 2)
        val chunk = FloatArray(fftSize)
        for (i in 0 until fftSize) {
            val srcIdx = startSample + i
            chunk[i] = if (srcIdx in samples.indices) samples[srcIdx] * window[i] else 0f
        }
        val magnitude = FftMath.rfftMagnitude(chunk)
        val freqPerBin = sampleRate.toFloat() / fftSize
        var sum = 0f
        var count = 0
        for (i in magnitude.indices) {
            val freq = i * freqPerBin
            if (freq in 20f..250f) {
                sum += magnitude[i]
                count++
            }
        }
        return if (count > 0) sum / count else (magnitude.sum() / max(1, magnitude.size))
    }
}
