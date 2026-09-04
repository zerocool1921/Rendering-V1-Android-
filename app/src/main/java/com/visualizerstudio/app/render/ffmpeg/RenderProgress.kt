package com.visualizerstudio.app.render.ffmpeg

/**
 * KONTRAK BEKU — dimiliki Fase 2 (Appendix A.4.2 blueprint).
 * Fase lain (4, 5, 6) HANYA boleh `import` tipe ini, dilarang mengedit atau membuat ulang.
 */
data class RenderProgress(
    val percent: Float,
    val currentTimeSec: Float,
    val totalTimeSec: Float,
    val fps: Float?,
    val speed: String?
)

sealed class RenderResult {
    data class Success(val outputPath: String) : RenderResult()
    data class Failure(val message: String, val ffmpegLogTail: String? = null) : RenderResult()
    object Cancelled : RenderResult()
}
