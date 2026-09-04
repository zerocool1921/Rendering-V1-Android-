package com.visualizerstudio.app.render.ffmpeg.spectrum

import com.visualizerstudio.app.domain.model.SpectrumConfig

/**
 * KONTRAK BEKU — dimiliki Fase 2 (Appendix A.4.4 blueprint).
 *
 * Kontrak render 1 spectrum kustom (shader GLSL, bukan filter FFmpeg native) jadi video
 * alpha siap di-overlay oleh [FfmpegFilterGraphBuilder]. Fase 2 HANYA mendeklarasikan
 * interface ini (tanpa implementasi nyata — implementasi butuh EGL/GLES yang baru dibangun
 * di Fase 3). Fase 3 WAJIB membuat implementasi baru yang meng-implement interface ini
 * persis (nama method, parameter, return type), lalu di-"colok" lewat Dagger Multibindings
 * (lihat [com.visualizerstudio.app.di.CustomSpectrumRendererMultibindModule] — Appendix A.5)
 * — TANPA PERNAH mengedit file ini maupun [com.visualizerstudio.app.worker.RenderWorker].
 */
interface CustomSpectrumRenderer {
    /** @return true kalau berhasil, file video alpha tertulis di [outputPath]. */
    suspend fun renderToAlphaVideo(
        spec: SpectrumConfig,
        durationSec: Float,
        fps: Int,
        audioSamplePath: String,
        outputPath: String
    ): Boolean
}
