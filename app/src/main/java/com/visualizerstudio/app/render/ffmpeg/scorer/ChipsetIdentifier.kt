package com.visualizerstudio.app.render.ffmpeg.scorer

import android.os.Build

/**
 * Deteksi vendor chipset perangkat dari properti `Build` bawaan Android — tidak butuh
 * dependency native/NDK tambahan. Dipakai `HardwareEncoderDetectorExtended` (Fase 6) untuk
 * memilih `EncoderCompatibilityScorer` mana saja yang relevan.
 */
object ChipsetIdentifier {

    fun identify(): ChipsetInfo {
        val hw = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { Build.SOC_MODEL }.getOrNull()
        } else null

        val vendor = when {
            hw.contains("qcom") || hw.contains("kona") || hw.contains("lahaina") ||
                hw.contains("taro") || hw.contains("msm") ||
                socModel?.startsWith("SM", ignoreCase = true) == true -> ChipsetVendor.QUALCOMM

            hw.contains("exynos") || board.contains("exynos") ||
                socModel?.contains("exynos", ignoreCase = true) == true -> ChipsetVendor.SAMSUNG_EXYNOS

            hw.contains("mt6") || hw.contains("mt8") || hw.contains("mtk") ||
                board.startsWith("mt") -> ChipsetVendor.MEDIATEK

            hw.contains("sc9") || hw.contains("ums") || hw.contains("unisoc") -> ChipsetVendor.UNISOC

            manufacturer == "google" &&
                (hw.contains("gs1") || hw.contains("gs2") || hw.contains("tensor")) -> ChipsetVendor.GOOGLE_TENSOR

            else -> ChipsetVendor.UNKNOWN
        }

        return ChipsetInfo(
            vendor = vendor,
            hardwareString = hw,
            boardString = board,
            socModel = socModel,
            sdkInt = Build.VERSION.SDK_INT
        )
    }
}
