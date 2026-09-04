package com.visualizerstudio.app.ui.wizard

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visualizerstudio.app.domain.model.BgVisualEffect
import com.visualizerstudio.app.domain.model.CropConfig
import com.visualizerstudio.app.domain.model.MediaMode
import com.visualizerstudio.app.domain.model.OverlayAssetConfig
import com.visualizerstudio.app.domain.model.OverlaySpeedConfig
import com.visualizerstudio.app.domain.model.Preset
import com.visualizerstudio.app.domain.model.RenderTask
import com.visualizerstudio.app.domain.model.ReversePlayConfig
import com.visualizerstudio.app.domain.model.Resolution
import com.visualizerstudio.app.domain.model.SlowMotionConfig
import com.visualizerstudio.app.domain.model.SpectrumConfig
import com.visualizerstudio.app.domain.model.TextOverlayConfig
import com.visualizerstudio.app.domain.repository.PresetRepository
import com.visualizerstudio.app.domain.repository.RenderQueueRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class WizardViewModel @Inject constructor(
    private val queueRepository: RenderQueueRepository,
    private val presetRepository: PresetRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WizardUiState())
    val state: StateFlow<WizardUiState> = _state.asStateFlow()

    // ---- Navigasi antar section (Stepper) ----

    fun goToSection(section: WizardSection) {
        _state.value = _state.value.copy(currentSection = section)
    }

    fun goNext() {
        val s = _state.value
        if (!s.isSectionValid(s.currentSection)) return // Next tetap tidak berefek kalau invalid
        val entries = WizardSection.entries
        val idx = entries.indexOf(s.currentSection)
        if (idx < entries.lastIndex) {
            _state.value = s.copy(currentSection = entries[idx + 1])
        }
    }

    fun goBack() {
        val s = _state.value
        val entries = WizardSection.entries
        val idx = entries.indexOf(s.currentSection)
        if (idx > 0) {
            _state.value = s.copy(currentSection = entries[idx - 1])
        }
    }

    // ---- Section Dasar (1-7) ----

    fun setTaskName(v: String) = update { it.copy(taskName = v) }
    fun setMediaFiles(uris: List<Uri>) = update { it.copy(mediaFiles = uris) }
    fun setAudioFiles(uris: List<Uri>) = update { it.copy(audioFiles = uris) }
    fun setOutputFolder(uri: Uri) = update { it.copy(outputFolder = uri) }
    fun setMediaMode(mode: MediaMode) = update { it.copy(mediaMode = mode) }
    fun setResolution(r: Resolution) = update { it.copy(resolution = r) }
    fun setFps(fps: Int) = update { it.copy(fps = fps) }

    // ---- Section Overlay & Asset (8) ----

    fun addOverlay(config: OverlayAssetConfig) = update { it.copy(overlays = it.overlays + config) }
    fun updateOverlayAt(index: Int, config: OverlayAssetConfig) = update {
        it.copy(overlays = it.overlays.toMutableList().also { list -> list[index] = config })
    }
    fun removeOverlayAt(index: Int) = update {
        it.copy(overlays = it.overlays.toMutableList().also { list -> list.removeAt(index) })
    }

    // ---- Section Spectrum (9-17) ----

    fun setSpectrumEnabled(enabled: Boolean) = update {
        it.copy(spectrumEnabled = enabled, spectrums = if (enabled && it.spectrums.isEmpty()) listOf(SpectrumConfig()) else it.spectrums)
    }

    fun setMultiSpectrumMode(enabled: Boolean) = update { it.copy(multiSpectrumMode = enabled) }

    fun setActiveSpectrumIndex(index: Int) = update { it.copy(activeSpectrumIndex = index) }

    /** Tambah spectrum baru (Manajer Multi-Spectrum) — offset Y otomatis biar tidak numpuk. */
    fun addSpectrum() = update {
        val offsetCount = it.spectrums.size
        val newSpec = SpectrumConfig(posY = "H-h-${50 + offsetCount * 80}")
        it.copy(spectrums = it.spectrums + newSpec, activeSpectrumIndex = it.spectrums.size)
    }

    /** Duplikat spectrum aktif dengan auto-offset posisi Y (section 5.1b). */
    fun duplicateActiveSpectrum() = update {
        val active = it.spectrums.getOrNull(it.activeSpectrumIndex) ?: return@update it
        val duplicated = active.copy(posY = "H-h-${50 + it.spectrums.size * 80}")
        it.copy(spectrums = it.spectrums + duplicated, activeSpectrumIndex = it.spectrums.size)
    }

    fun removeSpectrumAt(index: Int) = update {
        val newList = it.spectrums.toMutableList().also { l -> l.removeAt(index) }
        it.copy(
            spectrums = newList,
            activeSpectrumIndex = it.activeSpectrumIndex.coerceIn(0, (newList.size - 1).coerceAtLeast(0))
        )
    }

    fun updateActiveSpectrum(transform: (SpectrumConfig) -> SpectrumConfig) = update { s ->
        val idx = s.activeSpectrumIndex
        if (idx !in s.spectrums.indices) return@update s
        s.copy(spectrums = s.spectrums.toMutableList().also { it[idx] = transform(it[idx]) })
    }

    fun setBeatZoom(enabled: Boolean) = update { it.copy(useBeatZoom = enabled) }
    fun setOverlaySpeedBeatSync(config: OverlaySpeedConfig) = update { it.copy(overlaySpeedConfig = config) }
    fun setBgVisualEffect(effect: BgVisualEffect) = update { it.copy(bgVisualEffect = effect) }
    fun setCropConfig(crop: CropConfig) = update { it.copy(cropConfig = crop) }

    // ---- Section Teks & Identitas (18-20) ----

    fun setLoops(loops: Int) = update { it.copy(loops = loops.coerceAtLeast(1)) }

    fun setIntroEnabled(enabled: Boolean) = update {
        it.copy(introEnabled = enabled, introConfig = if (enabled) it.introConfig ?: TextOverlayConfig(lines = emptyList()) else null)
    }
    fun updateIntroConfig(transform: (TextOverlayConfig) -> TextOverlayConfig) = update {
        val current = it.introConfig ?: TextOverlayConfig(lines = emptyList())
        it.copy(introConfig = transform(current))
    }

    fun setTitleEnabled(enabled: Boolean) = update {
        it.copy(titleEnabled = enabled, titleConfig = if (enabled) it.titleConfig ?: TextOverlayConfig(lines = emptyList()) else null)
    }
    fun setTitleAutoFromFilename(auto: Boolean) = update { it.copy(titleAutoFromFilename = auto) }
    fun updateTitleConfig(transform: (TextOverlayConfig) -> TextOverlayConfig) = update {
        val current = it.titleConfig ?: TextOverlayConfig(lines = emptyList())
        it.copy(titleConfig = transform(current))
    }

    /** Parsing nama file audio jadi judul otomatis (langkah 20: "parsing underscore"). */
    fun deriveTitleFromFirstAudioFilename(displayName: String) {
        val parsed = displayName.substringBeforeLast('.').replace('_', ' ').trim()
        updateTitleConfig { it.copy(lines = listOf(com.visualizerstudio.app.domain.model.TextLine(parsed, 48))) }
    }

    // ---- Section Gerak Lanjutan (21-22) ----

    fun setSlowMotionConfig(config: SlowMotionConfig) = update { it.copy(slowMotionConfig = config) }
    fun setReversePlayConfig(config: ReversePlayConfig) = update { it.copy(reversePlayConfig = config) }

    // ---- Preset opsional setelah langkah 22 ----

    fun setSaveAsPreset(checked: Boolean) = update { it.copy(saveAsPresetChecked = checked) }
    fun setPresetName(name: String) = update { it.copy(presetName = name) }

    // ---- Submit ----

    fun submit() {
        val s = _state.value
        if (!s.isAllValid) {
            _state.value = s.copy(submitError = "Masih ada langkah yang belum valid.")
            return
        }
        val output = s.outputFolder ?: return
        _state.value = s.copy(isSubmitting = true, submitError = null)

        viewModelScope.launch {
            try {
                val task = RenderTask(
                    id = UUID.randomUUID().toString(),
                    name = s.taskName,
                    mediaFiles = s.mediaFiles,
                    audioFiles = s.audioFiles,
                    mediaMode = s.mediaMode,
                    resolution = s.resolution,
                    fps = s.fps,
                    loops = s.loops,
                    spectrums = if (s.spectrumEnabled) s.spectrums else emptyList(),
                    bgVideoOverlays = s.overlays,
                    introConfig = if (s.introEnabled) s.introConfig else null,
                    titleConfig = if (s.titleEnabled && !s.titleAutoFromFilename) s.titleConfig else s.titleConfig,
                    slowMotionConfig = s.slowMotionConfig,
                    reversePlayConfig = s.reversePlayConfig,
                    overlaySpeedConfig = s.overlaySpeedConfig,
                    cropConfig = s.cropConfig,
                    bgVisualEffect = s.bgVisualEffect,
                    useBeatZoom = s.useBeatZoom,
                    outputFolder = output
                )
                queueRepository.insertTask(task)

                if (s.saveAsPresetChecked && s.presetName.isNotBlank()) {
                    presetRepository.savePreset(
                        Preset(
                            id = UUID.randomUUID().toString(),
                            name = s.presetName,
                            fontSettings = s.introConfig ?: s.titleConfig,
                            keyframesTemplate = com.visualizerstudio.app.domain.model.KeyframeSet(),
                            createdAtEpochMillis = System.currentTimeMillis()
                        )
                    )
                }

                _state.value = _state.value.copy(isSubmitting = false, submittedTaskId = task.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSubmitting = false, submitError = e.message ?: "Gagal menyimpan tugas.")
            }
        }
    }

    /** Terapkan preset tersimpan ke wizard yang sedang dibuka (dipanggil dari Preset Manager). */
    fun applyPreset(preset: Preset) = update {
        it.copy(
            introConfig = preset.fontSettings ?: it.introConfig,
            introEnabled = preset.fontSettings != null || it.introEnabled
        )
    }

    fun resetSubmitError() = update { it.copy(submitError = null) }

    private inline fun update(transform: (WizardUiState) -> WizardUiState) {
        _state.value = transform(_state.value)
    }
}
