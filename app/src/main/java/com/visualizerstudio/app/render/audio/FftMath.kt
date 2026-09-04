package com.visualizerstudio.app.render.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Implementasi FFT (Fast Fourier Transform) radix-2 Cooley-Tukey murni Kotlin — dipakai
 * [AudioFftAnalyzer] & [BeatEnergyExtractor]. `size` WAJIB pangkat 2 (konstanta dipakai 2048,
 * sama seperti `fft_size` di source Python `spectrum_generator.py`/`utils.py`).
 *
 * Catatan performa (lihat section 9 blueprint — "pertimbangkan implementasi native C++ NDK"):
 * implementasi ini murni Kotlin, BUKAN native NDK. Ini pilihan sadar untuk Fase 3 supaya baseline
 * fungsional selesai tanpa menambah kompleksitas toolchain NDK C++ terpisah; blueprint hanya
 * menyebutnya "pertimbangkan" (opsional), bukan syarat wajib. Kandidat kuat dioptimasi native di
 * Fase 6 (Polish) kalau profiling menunjukkan FFT jadi bottleneck baterai/CPU saat render lama.
 */
internal object FftMath {

    /** Hann window, panjang [size]. */
    fun hannWindow(size: Int): FloatArray = FloatArray(size) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / (size - 1))).toFloat()
    }

    /**
     * FFT in-place radix-2 Cooley-Tukey. [re]/[im] harus berukuran pangkat 2 dan sama panjang.
     * Untuk sinyal real, isi [im] dengan nol sebelum memanggil fungsi ini.
     */
    fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        require(n and (n - 1) == 0) { "FftMath.fft: ukuran harus pangkat 2, dapat $n" }
        if (n <= 1) return

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var tmp = re[i]; re[i] = re[j]; re[j] = tmp
                tmp = im[i]; im[i] = im[j]; im[j] = tmp
            }
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wRe = cos(ang).toFloat()
            val wIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val idxLo = i + k
                    val idxHi = i + k + len / 2
                    val uRe = re[idxLo]
                    val uIm = im[idxLo]
                    val vRe = re[idxHi] * curRe - im[idxHi] * curIm
                    val vIm = re[idxHi] * curIm + im[idxHi] * curRe
                    re[idxLo] = uRe + vRe
                    im[idxLo] = uIm + vIm
                    re[idxHi] = uRe - vRe
                    im[idxHi] = uIm - vIm
                    val nextCurRe = curRe * wRe - curIm * wIm
                    val nextCurIm = curRe * wIm + curIm * wRe
                    curRe = nextCurRe
                    curIm = nextCurIm
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Magnitude spectrum |FFT| untuk bin 0..n/2 (setara `numpy.fft.rfft`), panjang n/2+1. */
    fun rfftMagnitude(signal: FloatArray): FloatArray {
        val n = signal.size
        val re = signal.copyOf()
        val im = FloatArray(n)
        fft(re, im)
        val half = n / 2 + 1
        return FloatArray(half) { i -> hypot(re[i].toDouble(), im[i].toDouble()).toFloat() }
    }
}
