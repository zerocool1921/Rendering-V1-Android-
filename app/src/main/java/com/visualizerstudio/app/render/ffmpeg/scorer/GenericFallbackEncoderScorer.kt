package com.visualizerstudio.app.render.ffmpeg.scorer

import android.media.MediaCodecInfo
import javax.inject.Inject

/** Fallback aman untuk chipset yang tidak dikenali — hanya mengandalkan cek capabilities dasar. */
class GenericFallbackEncoderScorer @Inject constructor() : EncoderCompatibilityScorer {

    override fun appliesTo(chipset: ChipsetInfo) = chipset.vendor == ChipsetVendor.UNKNOWN

    override fun score(
        chipset: ChipsetInfo,
        codecInfo: MediaCodecInfo,
        targetWidth: Int,
        targetHeight: Int,
        targetFps: Int
    ): EncoderScoreResult {
        val caps = runCatching { codecInfo.getCapabilitiesForType("video/avc").videoCapabilities }.getOrNull()
        return when {
            caps == null -> EncoderScoreResult(40, reason = "Tidak bisa membaca video capabilities, skor konservatif")
            !caps.areSizeAndRateSupported(targetWidth, targetHeight, targetFps.toDouble()) ->
                EncoderScoreResult(10, reason = "Capabilities tidak mendukung target resolusi/fps")
            else -> EncoderScoreResult(60, reason = "Chipset tidak dikenali, lolos cek capabilities dasar")
        }
    }
}
