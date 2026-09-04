package com.visualizerstudio.app.render.ffmpeg.scorer

import android.media.MediaCodecInfo
import javax.inject.Inject

/** Scorer untuk chipset Unisoc — umumnya segmen entry-level, diprioritaskan ke stabilitas. */
class UnisocEncoderScorer @Inject constructor() : EncoderCompatibilityScorer {

    override fun appliesTo(chipset: ChipsetInfo) = chipset.vendor == ChipsetVendor.UNISOC

    override fun score(
        chipset: ChipsetInfo,
        codecInfo: MediaCodecInfo,
        targetWidth: Int,
        targetHeight: Int,
        targetFps: Int
    ): EncoderScoreResult {
        var score = 55
        val notes = mutableListOf("Chipset Unisoc segmen entry-level, prioritaskan stabilitas di atas kecepatan")
        var mustAvoid = false

        if (targetHeight >= 2160) {
            mustAvoid = true
            notes += "4K pada Unisoc WAJIB dihindari, disarankan fallback CPU/libx264"
        } else if (targetHeight > 1080) {
            score -= 30
            notes += "Di atas 1080p pada Unisoc sering tidak stabil"
        }
        if (targetFps > 30) {
            score -= 15
            notes += "Target fps>30 berisiko drop frame di Unisoc"
        }
        return EncoderScoreResult(score.coerceIn(0, 100), mustAvoid = mustAvoid, reason = notes.joinToString("; "))
    }
}
