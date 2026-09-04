package com.visualizerstudio.app.render.ffmpeg

import javax.inject.Inject

/**
 * Parser regex fallback untuk baris log FFmpeg (`time=`, `fps=`, `speed=`), dipakai
 * [FfmpegSessionRunner] saat statistics callback resmi FFmpegKit tidak tersedia/kosong.
 * Porting dari pola regex `run_ffmpeg_with_progress` versi Python sumber.
 */
class FfmpegProgressParser @Inject constructor() {

    private val timeRegex = Regex("""time=(\d{2}):(\d{2}):(\d{2})\.(\d{2})""")
    private val fpsRegex = Regex("""fps=\s*([\d.]+)""")
    private val speedRegex = Regex("""speed=\s*([\d.]+x|N/A)""")

    /**
     * @return null kalau baris tidak mengandung token `time=` (bukan baris progres), supaya
     * caller tidak mengirim update palsu ke notifikasi.
     */
    fun parseLine(line: String, totalTimeSec: Float): RenderProgress? {
        val timeMatch = timeRegex.find(line) ?: return null
        val (h, m, s, cs) = timeMatch.destructured
        val currentTimeSec = h.toInt() * 3600f + m.toInt() * 60f + s.toInt() + cs.toInt() / 100f

        var percent = if (totalTimeSec > 0f) (currentTimeSec / totalTimeSec) * 100f else 0f
        percent = percent.coerceIn(0f, 100f)

        val fps = fpsRegex.find(line)?.groupValues?.get(1)?.toFloatOrNull()
        val speed = speedRegex.find(line)?.groupValues?.get(1)

        return RenderProgress(
            percent = percent,
            currentTimeSec = currentTimeSec,
            totalTimeSec = totalTimeSec,
            fps = fps,
            speed = speed
        )
    }
}
