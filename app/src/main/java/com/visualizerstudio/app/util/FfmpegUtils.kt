package com.visualizerstudio.app.util

import kotlin.math.floor
import kotlin.math.round

/**
 * Kumpulan helper murni (tanpa side-effect) dipakai lintas builder filter graph Fase 2.
 * Porting dari `utils.py` versi sumber (`ensure_even_int`, `sanitize_ffmpeg_coord`, dsb).
 */
object FfmpegUtils {

    /**
     * Memastikan dimensi (lebar/tinggi) selalu bilangan bulat genap absolut — wajib untuk
     * banyak encoder H.264/yuv420p yang butuh dimensi genap.
     */
    fun ensureEvenInt(value: Number, minVal: Int = 2): Int {
        var num = floor(value.toDouble()).toInt()
        num = maxOf(num, minVal)
        if (num % 2 != 0) num += 1
        return num
    }

    /**
     * Memastikan koordinat X/Y berupa integer bersih ATAU ekspresi FFmpeg valid (mis.
     * "(W-w)/2"). Angka float berlebih (mis. "342.1293") dibulatkan supaya parser FFmpeg
     * tidak rewel; string ekspresi dibiarkan apa adanya.
     */
    fun sanitizeCoord(value: String?): String {
        if (value.isNullOrBlank()) return "0"
        val trimmed = value.trim()
        val asFloat = trimmed.toDoubleOrNull()
        return if (asFloat != null) round(asFloat).toInt().toString() else trimmed
    }

    /**
     * Escape teks user (intro/title/watermark) supaya aman dimasukkan ke dalam ekspresi
     * `drawtext=text='...'` FFmpeg — escape backslash, single quote, dan colon.
     */
    fun escapeDrawtext(text: String): String =
        text
            .replace("\\", "\\\\")
            .replace(":", "\\:")
            .replace("'", "\\'")
            .replace("%", "\\%")

    /** Escape path file untuk dipakai di dalam filter (mis. movie='...', concat list). */
    fun escapeFilterPath(path: String): String =
        path.replace("\\", "/").replace(":", "\\:").replace("'", "\\'")

    /** Format detik → "HH:MM:SS" untuk label progres notifikasi. */
    fun formatDuration(totalSeconds: Float): String {
        val total = totalSeconds.toInt().coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }
}
