package com.visualizerstudio.app.render.ffmpeg.scorer

import android.media.MediaCodecInfo
import javax.inject.Inject

/** Scorer untuk chipset Qualcomm Snapdragon — umumnya encoder hardware paling matang di Android. */
class SnapdragonEncoderScorer @Inject constructor() : EncoderCompatibilityScorer {

    override fun appliesTo(chipset: ChipsetInfo) = chipset.vendor == ChipsetVendor.QUALCOMM

    override fun score(
        chipset: ChipsetInfo,
        codecInfo: MediaCodecInfo,
        targetWidth: Int,
        targetHeight: Int,
        targetFps: Int
    ): EncoderScoreResult {
        val name = codecInfo.name.lowercase()
        if (!name.contains("qcom") && !name.contains("qti")) {
            return EncoderScoreResult(60, reason = "Bukan implementasi vendor Qualcomm native, skor netral")
        }

        var score = 90
        val notes = mutableListOf("Encoder venus/qcom Qualcomm, umumnya stabil untuk render panjang")

        val caps = runCatching { codecInfo.getCapabilitiesForType("video/avc").videoCapabilities }.getOrNull()
        if (caps != null && !caps.areSizeAndRateSupported(targetWidth, targetHeight, targetFps.toDouble())) {
            score -= 40
            notes += "Kombinasi ${targetWidth}x${targetHeight}@${targetFps}fps di luar batas capabilities encoder"
        }
        if (targetHeight >= 2160 && chipset.sdkInt < 28) {
            score -= 20
            notes += "4K di API<28 pada sebagian Snapdragon generasi lama rawan drop frame"
        }
        return EncoderScoreResult(score.coerceIn(0, 100), reason = notes.joinToString("; "))
    }
}
