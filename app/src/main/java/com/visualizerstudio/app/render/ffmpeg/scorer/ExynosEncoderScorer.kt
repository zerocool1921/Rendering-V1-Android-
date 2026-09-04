package com.visualizerstudio.app.render.ffmpeg.scorer

import android.media.MediaCodecInfo
import javax.inject.Inject

/**
 * Scorer untuk chipset Samsung Exynos. Beberapa board Exynos generasi tertentu pernah dilaporkan
 * bermasalah pada encoding H.264 panjang (artefak color-format / B-frame) — diberi penalti skor,
 * bukan diblokir total, supaya user tetap bisa override manual di layar Pengaturan (mode MANUAL,
 * A.4.3).
 */
class ExynosEncoderScorer @Inject constructor() : EncoderCompatibilityScorer {

    override fun appliesTo(chipset: ChipsetInfo) = chipset.vendor == ChipsetVendor.SAMSUNG_EXYNOS

    private val knownProblematicBoards = setOf("exynos990", "exynos850", "exynos9610")

    override fun score(
        chipset: ChipsetInfo,
        codecInfo: MediaCodecInfo,
        targetWidth: Int,
        targetHeight: Int,
        targetFps: Int
    ): EncoderScoreResult {
        val name = codecInfo.name.lowercase()
        if (!name.contains("exynos") && !name.contains("sec")) {
            return EncoderScoreResult(55, reason = "Bukan implementasi vendor Samsung native, skor netral")
        }

        var score = 75
        val notes = mutableListOf("Encoder Exynos terdeteksi")

        val problematic = knownProblematicBoards.any {
            chipset.hardwareString.contains(it) || chipset.boardString.contains(it)
        }
        var mustAvoid = false
        if (problematic) {
            score -= 35
            notes += "Board ini pernah dilaporkan punya isu B-frame/color-format pada render panjang"
            if (targetHeight >= 2160) {
                mustAvoid = true
                notes += "4K pada board bermasalah ini WAJIB dihindari, disarankan fallback CPU/libx264"
            }
        }
        if (targetFps > 30 && targetHeight >= 1080) {
            score -= 15
            notes += "1080p60+ pada sebagian Exynos mid-range rawan throttle/drop frame"
        }
        return EncoderScoreResult(score.coerceIn(0, 100), mustAvoid = mustAvoid, reason = notes.joinToString("; "))
    }
}
