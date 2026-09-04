package com.visualizerstudio.app.render.ffmpeg.scorer

import android.media.MediaCodecInfo
import javax.inject.Inject

/** Scorer untuk chipset MediaTek — variasi kualitas encoder cukup lebar antara tier flagship & entry. */
class MediatekEncoderScorer @Inject constructor() : EncoderCompatibilityScorer {

    override fun appliesTo(chipset: ChipsetInfo) = chipset.vendor == ChipsetVendor.MEDIATEK

    private val budgetTierPrefixes = setOf("mt67", "mt65", "mt68")

    override fun score(
        chipset: ChipsetInfo,
        codecInfo: MediaCodecInfo,
        targetWidth: Int,
        targetHeight: Int,
        targetFps: Int
    ): EncoderScoreResult {
        val name = codecInfo.name.lowercase()
        if (!name.contains("mtk") && !name.contains("mediatek")) {
            return EncoderScoreResult(55, reason = "Bukan implementasi vendor MediaTek native, skor netral")
        }

        var score = 70
        val notes = mutableListOf("Encoder MediaTek terdeteksi")

        val isBudgetTier = budgetTierPrefixes.any { chipset.hardwareString.contains(it) }
        if (isBudgetTier) {
            score -= 25
            notes += "Chipset MediaTek kelas entry, rekomendasi maksimal 1080p30 untuk stabilitas"
            if (targetHeight > 1080 || targetFps > 30) {
                score -= 20
                notes += "Target melebihi rekomendasi 1080p30 untuk tier chipset ini"
            }
        }

        val caps = runCatching { codecInfo.getCapabilitiesForType("video/avc").videoCapabilities }.getOrNull()
        if (caps != null && !caps.areSizeAndRateSupported(targetWidth, targetHeight, targetFps.toDouble())) {
            score -= 30
            notes += "Capabilities encoder tidak mendukung kombinasi resolusi/fps target"
        }
        return EncoderScoreResult(score.coerceIn(0, 100), reason = notes.joinToString("; "))
    }
}
