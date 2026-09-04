package com.visualizerstudio.app.render.ffmpeg.scorer

import android.media.MediaCodecInfo
import javax.inject.Inject

/** Scorer untuk chipset Google Tensor — flagship, umumnya mendapat update driver rutin. */
class TensorEncoderScorer @Inject constructor() : EncoderCompatibilityScorer {

    override fun appliesTo(chipset: ChipsetInfo) = chipset.vendor == ChipsetVendor.GOOGLE_TENSOR

    override fun score(
        chipset: ChipsetInfo,
        codecInfo: MediaCodecInfo,
        targetWidth: Int,
        targetHeight: Int,
        targetFps: Int
    ): EncoderScoreResult {
        var score = 85
        val notes = mutableListOf("Google Tensor, umumnya stabil & rutin mendapat update driver")

        val caps = runCatching { codecInfo.getCapabilitiesForType("video/avc").videoCapabilities }.getOrNull()
        if (caps != null && !caps.areSizeAndRateSupported(targetWidth, targetHeight, targetFps.toDouble())) {
            score -= 35
            notes += "Kombinasi resolusi/fps di luar capabilities yang dilaporkan encoder"
        }
        return EncoderScoreResult(score.coerceIn(0, 100), reason = notes.joinToString("; "))
    }
}
