package com.visualizerstudio.app.ui.wizard

import android.net.Uri
import com.visualizerstudio.app.domain.model.BgVisualEffect
import com.visualizerstudio.app.domain.model.CropConfig
import com.visualizerstudio.app.domain.model.MediaMode
import com.visualizerstudio.app.domain.model.OverlayAssetConfig
import com.visualizerstudio.app.domain.model.OverlaySpeedConfig
import com.visualizerstudio.app.domain.model.ReversePlayConfig
import com.visualizerstudio.app.domain.model.Resolution
import com.visualizerstudio.app.domain.model.SlowMotionConfig
import com.visualizerstudio.app.domain.model.SpectrumConfig
import com.visualizerstudio.app.domain.model.TextOverlayConfig

/** 5 section (Stepper) yang membungkus 22 langkah asli — sesuai section 5.1 blueprint. */
enum class WizardSection(val title: String, val stepRange: IntRange) {
    DASAR("Dasar", 1..7),
    OVERLAY_ASSET("Overlay & Asset", 8..8),
    SPECTRUM("Spectrum", 9..17),
    TEKS_IDENTITAS("Teks & Identitas", 18..20),
    GERAK_LANJUTAN("Gerak Lanjutan", 21..22)
}

/**
 * Satu state class menyimpan seluruh 22 langkah (bukan 22 objek state terpisah) supaya
 * submit di akhir wizard bisa langsung dipetakan 1:1 ke `RenderTask` (kontrak A.3).
 * Semua field defaultnya mengikuti default `RenderTask`/sub-model di Appendix A.3.
 */
data class WizardUiState(
    // Section Dasar (langkah 1-7)
    val taskName: String = "",
    val mediaFiles: List<Uri> = emptyList(),
    val audioFiles: List<Uri> = emptyList(),
    val outputFolder: Uri? = null,
    val mediaMode: MediaMode = MediaMode.PER_TRACK,
    val resolution: Resolution = Resolution.R720P,
    val fps: Int = 30,

    // Section Overlay & Asset (langkah 8) — multi overlay, urutan = urutan tampil
    val overlays: List<OverlayAssetConfig> = emptyList(),

    // Section Spectrum (langkah 9-17)
    val spectrumEnabled: Boolean = true,
    val multiSpectrumMode: Boolean = false,
    val spectrums: List<SpectrumConfig> = listOf(SpectrumConfig()),
    val activeSpectrumIndex: Int = 0,
    val useBeatZoom: Boolean = false,
    val overlaySpeedConfig: OverlaySpeedConfig = OverlaySpeedConfig(),
    val bgVisualEffect: BgVisualEffect = BgVisualEffect.NONE,
    val cropConfig: CropConfig = CropConfig(),

    // Section Teks & Identitas (langkah 18-20)
    val loops: Int = 1,
    val introEnabled: Boolean = false,
    val introConfig: TextOverlayConfig? = null,
    val titleEnabled: Boolean = false,
    val titleConfig: TextOverlayConfig? = null,
    val titleAutoFromFilename: Boolean = true,

    // Section Gerak Lanjutan (langkah 21-22)
    val slowMotionConfig: SlowMotionConfig = SlowMotionConfig(),
    val reversePlayConfig: ReversePlayConfig = ReversePlayConfig(),

    // Navigasi & submit
    val currentSection: WizardSection = WizardSection.DASAR,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val submittedTaskId: String? = null,
    val saveAsPresetChecked: Boolean = false,
    val presetName: String = ""
) {
    /** Validasi instan per section — dipakai untuk enable/disable tombol Lanjut (C.3). */
    fun isSectionValid(section: WizardSection): Boolean = when (section) {
        WizardSection.DASAR ->
            taskName.isNotBlank() &&
                mediaFiles.isNotEmpty() &&
                audioFiles.isNotEmpty() &&
                outputFolder != null &&
                fps in setOf(24, 30, 60)

        WizardSection.OVERLAY_ASSET ->
            // opsional (langkah 8 bisa dilewati) — valid selama tiap overlay yang ADA punya file
            overlays.all { it.fadeInSeconds >= 0f && it.opacity in 0f..1f }

        WizardSection.SPECTRUM ->
            if (!spectrumEnabled) true
            else spectrums.isNotEmpty() && spectrums.all { spec ->
                spec.width > 0 && spec.height > 0 &&
                    spec.opacity in 0.1f..1f &&
                    (spec.bgColor == null || spec.bgOpacity in 0.1f..1f)
            }

        WizardSection.TEKS_IDENTITAS ->
            loops >= 1 &&
                (!introEnabled || (introConfig?.lines?.isNotEmpty() == true && introConfig.lines.size <= 3)) &&
                (!titleEnabled || titleAutoFromFilename || (titleConfig?.lines?.isNotEmpty() == true))

        WizardSection.GERAK_LANJUTAN -> true // semua opsional, tidak ada input wajib
    }

    val isAllValid: Boolean
        get() = WizardSection.entries.all { isSectionValid(it) }

    val currentSectionIndex: Int get() = WizardSection.entries.indexOf(currentSection)
}
