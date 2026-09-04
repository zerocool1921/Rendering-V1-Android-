package com.visualizerstudio.app.domain.model

/**
 * Domain model Preset. BUKAN bagian dari Appendix A.3 (yang beku hanya RenderTask.kt),
 * jadi didefinisikan di sini oleh Fase 4 sebagai ekstensi baru. Field mengikuti ringkasan
 * konseptual di section 4 blueprint (PresetEntity).
 *
 * Fase 5 (galeri preset dengan thumbnail render) WAJIB memakai struktur ini apa adanya —
 * kalau perlu field tambahan (mis. thumbnailUri hasil render), tambahkan lewat data class
 * baru yang membungkus ini (mis. `PresetWithThumbnail`), jangan edit file ini.
 */
data class Preset(
    val id: String,
    val name: String,
    val fontSettings: TextOverlayConfig?,
    val keyframesTemplate: KeyframeSet,
    val createdAtEpochMillis: Long
)
