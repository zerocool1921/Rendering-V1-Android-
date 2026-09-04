package com.visualizerstudio.app.render.ffmpeg

import android.media.MediaCodecList
import com.visualizerstudio.app.render.ffmpeg.scorer.ChipsetIdentifier
import com.visualizerstudio.app.render.ffmpeg.scorer.EncoderCompatibilityScorer
import javax.inject.Inject

/**
 * `render/ffmpeg/HardwareEncoderDetectorExtended.kt` — dimiliki Fase 6 (Appendix A.2).
 *
 * Perluasan deteksi hardware encoder lintas chipset di ATAS `HardwareEncoderDetector` dasar
 * milik Fase 2 (`EncoderParamsProvider.kt`). Sesuai A.4.3, class ini TIDAK mewarisi maupun
 * mengedit `HardwareEncoderDetector` Fase 2 — hanya memanggil tipe data `HardwareEncoderMode`
 * dan `EncoderSelection` milik Fase 2 sebagai kontrak, dan berdiri sebagai lapisan skor
 * kompatibilitas per-chipset terpisah yang dipasok lewat Extension Point
 * `Set<EncoderCompatibilityScorer>`.
 *
 * Cara pakai yang direkomendasikan di `DetectHardwareEncoderUseCase` (Fase 2, domain layer):
 * 1. Panggil `HardwareEncoderDetector` (Fase 2) dulu untuk mendapat kandidat dasar & mode
 *    (AUTO/MANUAL/CPU_SOFTWARE).
 * 2. Kalau mode == AUTO, panggil `bestEncoderNameOrNull()` di sini untuk mem-verifikasi/
 *    merangking ulang kandidat itu dengan skor per-chipset sebelum dibungkus jadi
 *    `EncoderSelection` final.
 * 3. Kalau hasilnya null (semua kandidat `mustAvoid`), fallback ke
 *    `EncoderSelection(mediaCodecName = null, ffmpegVideoCodec = "libx264")` — konsisten
 *    dengan perilaku anti-crash Fase 2 yang sudah ada.
 */
class HardwareEncoderDetectorExtended @Inject constructor(
    private val scorers: Set<@JvmSuppressWildcards EncoderCompatibilityScorer>
) {

    data class RankedEncoder(
        val codecName: String,
        val score: Int,
        val mustAvoid: Boolean,
        val reasons: List<String>
    )

    /**
     * Merangking semua kandidat encoder H.264 hardware yang terdeteksi `MediaCodecList`,
     * dari yang paling direkomendasikan ke paling tidak direkomendasikan untuk target
     * resolusi/fps yang diminta.
     */
    fun rankAvailableEncoders(
        targetWidth: Int,
        targetHeight: Int,
        targetFps: Int,
        mimeType: String = "video/avc"
    ): List<RankedEncoder> {
        val chipset = ChipsetIdentifier.identify()
        val applicableScorers = scorers.filter { it.appliesTo(chipset) }

        val codecList = runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS) }.getOrNull()
            ?: return emptyList()

        val hardwareCandidates = codecList.codecInfos.filter { info ->
            info.isEncoder &&
                info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) } &&
                isLikelyHardwareImplementation(info.name)
        }

        return hardwareCandidates.map { info ->
            if (applicableScorers.isEmpty()) {
                // Belum ada scorer spesifik untuk vendor chipset ini (mis. GenericFallbackEncoderScorer
                // hanya menyala untuk UNKNOWN) → skor netral, anti-crash, tidak pernah mustAvoid paksa.
                RankedEncoder(
                    codecName = info.name,
                    score = 50,
                    mustAvoid = false,
                    reasons = listOf("Tidak ada scorer chipset spesifik yang cocok, dipakai skor netral")
                )
            } else {
                val results = applicableScorers.map {
                    it.score(chipset, info, targetWidth, targetHeight, targetFps)
                }
                val avoided = results.any { it.mustAvoid }
                val avgScore = results.map { it.score.coerceIn(0, 100) }.average().toInt()
                RankedEncoder(
                    codecName = info.name,
                    score = if (avoided) 0 else avgScore,
                    mustAvoid = avoided,
                    reasons = results.map { it.reason }
                )
            }
        }.sortedByDescending { it.score }
    }

    /** Nama encoder terbaik, atau null kalau SEMUA kandidat harus dihindari (caller fallback CPU). */
    fun bestEncoderNameOrNull(targetWidth: Int, targetHeight: Int, targetFps: Int): String? =
        rankAvailableEncoders(targetWidth, targetHeight, targetFps).firstOrNull { !it.mustAvoid }?.codecName

    private fun isLikelyHardwareImplementation(codecName: String): Boolean {
        val n = codecName.lowercase()
        // Implementasi software Android (OMX.google.*, c2.android.*) sengaja dikecualikan —
        // deteksi ini fokus ke encoder HARDWARE vendor chipset saja.
        return !n.startsWith("omx.google.") && !n.startsWith("c2.android.")
    }
}
