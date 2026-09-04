package com.visualizerstudio.app.ui.canvaseditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visualizerstudio.app.domain.model.CropConfig
import com.visualizerstudio.app.domain.model.RenderTask
import com.visualizerstudio.app.domain.repository.RenderTaskRepository
import com.visualizerstudio.app.render.ffmpeg.FfmpegSessionRunner
import com.visualizerstudio.app.render.ffmpeg.RenderProgress
import com.visualizerstudio.app.render.ffmpeg.RenderResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * NOTE PENTING (dicatat jujur, bukan disembunyikan): Appendix A.4.1 versi saat ini HANYA
 * mendeklarasikan `getTaskById` / `updateStatus` / `getNextQueuedTask` di
 * [RenderTaskRepository] — tidak ada method untuk menyimpan PERUBAHAN penuh sebuah [RenderTask]
 * (posisi/crop/keyframe hasil edit Canvas Editor). Canvas Editor MEMBUTUHKAN ini untuk tombol
 * "Simpan Posisi". File ini mengasumsikan tambahan method berikut sudah/akan ada di interface
 * tsb (idealnya ditambahkan sebagai revisi Appendix A.4.1 v3, dibroadcast ke semua sesi sesuai
 * section 0.1 poin 2 — BUKAN diedit sepihak oleh sesi Fase 5 ini):
 *
 *   suspend fun updateTask(task: RenderTask)
 *
 * Kalau interface asli Fase 1 belum punya method ini saat file ini digabung, tambahkan sebagai
 * revisi Appendix A resmi dulu sebelum compile, JANGAN diakali dengan mengedit repository Fase 1
 * langsung dari sesi Fase 5.
 */
interface RenderTaskRepositoryUpdateExtension {
    suspend fun updateTask(task: RenderTask)
}

data class CanvasEditorUiState(
    val taskName: String = "",
    val canvasWidth: Int = 1920,
    val canvasHeight: Int = 1080,
    val elements: List<CanvasElementUi> = emptyList(),
    val selectedElementId: String? = null,
    val keyframesByElement: Map<String, List<KeyframeUi>> = emptyMap(),
    val playheadSec: Float = 0f,
    val timelineDurationSec: Float = 30f,
    val snap: SnapSettings = SnapSettings(),
    val isRenderingPreview: Boolean = false,
    val previewProgress: RenderProgress? = null,
    val previewResultPath: String? = null,
    val previewError: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class CanvasEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val renderTaskRepository: RenderTaskRepository,
    private val ffmpegSessionRunner: FfmpegSessionRunner
) : ViewModel() {

    private val taskId: String = checkNotNull(savedStateHandle["taskId"])

    private val _uiState = MutableStateFlow(CanvasEditorUiState())
    val uiState: StateFlow<CanvasEditorUiState> = _uiState.asStateFlow()

    private var loadedTask: RenderTask? = null

    init {
        viewModelScope.launch {
            val task = renderTaskRepository.getTaskById(taskId) ?: return@launch
            loadedTask = task
            _uiState.value = _uiState.value.copy(
                taskName = task.name,
                canvasWidth = task.resolution.width,
                canvasHeight = task.resolution.height,
                elements = buildElementsFromTask(task),
                keyframesByElement = buildKeyframesFromTask(task),
                timelineDurationSec = max(10f, task.audioFiles.size * 30f)
            )
        }
    }

    private fun buildElementsFromTask(task: RenderTask): List<CanvasElementUi> {
        val list = mutableListOf<CanvasElementUi>()
        task.spectrums.forEachIndexed { i, s ->
            list += CanvasElementUi(
                id = "spectrum_$i",
                kind = ElementKind.SPECTRUM,
                label = "Spectrum ${i + 1}",
                x = resolveExprToPixel(s.posX, task.resolution.width, s.width),
                y = resolveExprToPixel(s.posY, task.resolution.height, s.height),
                width = s.width.toFloat(),
                height = s.height.toFloat(),
                shearX = s.shearX, shearY = s.shearY, opacity = s.opacity,
                crop = task.cropConfig, supportsCrop = true
            )
        }
        task.introConfig?.let {
            list += CanvasElementUi(
                id = "intro_text", kind = ElementKind.INTRO_TEXT, label = "Intro",
                x = task.resolution.width / 2f - 200f, y = task.resolution.height / 2f - 40f,
                width = 400f, height = 80f, opacity = it.opacity
            )
        }
        task.titleConfig?.let {
            list += CanvasElementUi(
                id = "title_text", kind = ElementKind.TITLE_TEXT, label = "Title",
                x = task.resolution.width / 2f - 200f, y = task.resolution.height - 160f,
                width = 400f, height = 80f, opacity = it.opacity
            )
        }
        task.bgVideoOverlays.forEachIndexed { i, o ->
            list += CanvasElementUi(
                id = "overlay_$i", kind = ElementKind.OVERLAY_ASSET, label = "Overlay ${i + 1}",
                x = 0f, y = 0f, width = task.resolution.width.toFloat(), height = task.resolution.height.toFloat(),
                shearX = o.shearX, shearY = o.shearY, opacity = o.opacity
            )
        }
        return list
    }

    private fun buildKeyframesFromTask(task: RenderTask): Map<String, List<KeyframeUi>> {
        val map = mutableMapOf<String, List<KeyframeUi>>()
        if (task.keyframes.spectrum.isNotEmpty()) {
            map["spectrum_0"] = task.keyframes.spectrum.mapIndexed { i, k -> KeyframeUi.fromDomain(k, "kf_spec_$i") }
        }
        if (task.keyframes.intro.isNotEmpty()) {
            map["intro_text"] = task.keyframes.intro.mapIndexed { i, k -> KeyframeUi.fromDomain(k, "kf_intro_$i") }
        }
        if (task.keyframes.title.isNotEmpty()) {
            map["title_text"] = task.keyframes.title.mapIndexed { i, k -> KeyframeUi.fromDomain(k, "kf_title_$i") }
        }
        task.keyframes.overlays.forEach { (key, list) ->
            map[key] = list.mapIndexed { i, k -> KeyframeUi.fromDomain(k, "kf_${key}_$i") }
        }
        return map
    }

    /** Ekspresi posisi sederhana (5.1d) — kalau bukan angka murni, kembalikan tengah sebagai default awal editor. */
    private fun resolveExprToPixel(expr: String, canvasSize: Int, elementSize: Int): Float =
        expr.toFloatOrNull() ?: ((canvasSize - elementSize) / 2f)

    fun selectElement(id: String?) {
        _uiState.value = _uiState.value.copy(selectedElementId = id)
    }

    // ---------------- Drag / resize / shear / crop (dua arah nyata dgn panel properti) ----------------

    fun onGizmoDrag(id: String, deltaX: Float, deltaY: Float) {
        updateElement(id) { el ->
            val (nx, ny) = applySnap(el.x + deltaX, el.y + deltaY)
            el.copy(x = nx, y = ny)
        }
    }

    fun onGizmoResize(id: String, deltaW: Float, deltaH: Float) {
        updateElement(id) { el ->
            el.copy(
                width = (el.width + deltaW).coerceAtLeast(20f),
                height = (el.height + deltaH).coerceAtLeast(20f)
            )
        }
    }

    fun onGizmoShear(id: String, deltaShearX: Float, deltaShearY: Float) {
        updateElement(id) { el ->
            el.copy(
                shearX = (el.shearX + deltaShearX).coerceIn(-1f, 1f),
                shearY = (el.shearY + deltaShearY).coerceIn(-1f, 1f)
            )
        }
    }

    fun onCropHandleDrag(id: String, side: GizmoInteraction, deltaPx: Int) {
        updateElement(id) { el ->
            val c = el.crop ?: CropConfig()
            val newCrop = when (side) {
                GizmoInteraction.CROP_LEFT -> c.copy(left = max(0, c.left + deltaPx))
                GizmoInteraction.CROP_RIGHT -> c.copy(right = max(0, c.right + deltaPx))
                GizmoInteraction.CROP_TOP -> c.copy(top = max(0, c.top + deltaPx))
                GizmoInteraction.CROP_BOTTOM -> c.copy(bottom = max(0, c.bottom + deltaPx))
                else -> c
            }
            el.copy(crop = newCrop)
        }
    }

    /** Dipanggil dari panel properti (ketik angka manual) — arah B dari live sync dua arah. */
    fun onPropertyPanelEdit(id: String, x: Float? = null, y: Float? = null, width: Float? = null, height: Float? = null, shearX: Float? = null, shearY: Float? = null) {
        updateElement(id) { el ->
            el.copy(
                x = x ?: el.x, y = y ?: el.y,
                width = width ?: el.width, height = height ?: el.height,
                shearX = shearX ?: el.shearX, shearY = shearY ?: el.shearY
            )
        }
    }

    private fun applySnap(x: Float, y: Float): Pair<Float, Float> {
        val snap = _uiState.value.snap
        var nx = x; var ny = y
        if (snap.snapToGrid) {
            nx = (nx / snap.gridSizePx).let { kotlin.math.round(it) } * snap.gridSizePx
            ny = (ny / snap.gridSizePx).let { kotlin.math.round(it) } * snap.gridSizePx
        }
        if (snap.snapToSafeArea) {
            val m = snap.safeAreaMarginPx
            val w = _uiState.value.canvasWidth.toFloat()
            val h = _uiState.value.canvasHeight.toFloat()
            if (kotlin.math.abs(nx - m) < 10f) nx = m
            if (kotlin.math.abs(nx - (w - m)) < 10f) nx = w - m
            if (kotlin.math.abs(ny - m) < 10f) ny = m
            if (kotlin.math.abs(ny - (h - m)) < 10f) ny = h - m
        }
        return nx to ny
    }

    private fun updateElement(id: String, transform: (CanvasElementUi) -> CanvasElementUi) {
        _uiState.value = _uiState.value.copy(
            elements = _uiState.value.elements.map { if (it.id == id) transform(it) else it }
        )
    }

    // ---------------- Keyframe timeline ----------------

    fun addKeyframeAtPlayhead(elementId: String) {
        val el = _uiState.value.elements.find { it.id == elementId } ?: return
        val newKf = KeyframeUi(
            id = UUID.randomUUID().toString(),
            timeSec = _uiState.value.playheadSec,
            x = el.x, y = el.y, opacity = el.opacity
        )
        val updated = (_uiState.value.keyframesByElement[elementId].orEmpty() + newKf).sortedBy { it.timeSec }
        _uiState.value = _uiState.value.copy(
            keyframesByElement = _uiState.value.keyframesByElement + (elementId to updated)
        )
    }

    fun moveKeyframe(elementId: String, keyframeId: String, newTimeSec: Float) {
        val list = _uiState.value.keyframesByElement[elementId].orEmpty()
        val updated = list.map { if (it.id == keyframeId) it.copy(timeSec = newTimeSec.coerceIn(0f, _uiState.value.timelineDurationSec)) else it }
            .sortedBy { it.timeSec }
        _uiState.value = _uiState.value.copy(keyframesByElement = _uiState.value.keyframesByElement + (elementId to updated))
    }

    fun deleteKeyframe(elementId: String, keyframeId: String) {
        val list = _uiState.value.keyframesByElement[elementId].orEmpty().filterNot { it.id == keyframeId }
        _uiState.value = _uiState.value.copy(keyframesByElement = _uiState.value.keyframesByElement + (elementId to list))
    }

    fun movePlayhead(sec: Float) {
        _uiState.value = _uiState.value.copy(playheadSec = sec.coerceIn(0f, _uiState.value.timelineDurationSec))
    }

    fun toggleSnapToGrid(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(snap = _uiState.value.snap.copy(snapToGrid = enabled))
    }

    fun toggleSnapToSafeArea(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(snap = _uiState.value.snap.copy(snapToSafeArea = enabled))
    }

    // ---------------- Simpan & Render Preview ----------------

    fun savePositions() {
        val task = loadedTask ?: return
        val state = _uiState.value
        val updatedSpectrums = task.spectrums.mapIndexed { i, s ->
            val el = state.elements.find { it.id == "spectrum_$i" } ?: return@mapIndexed s
            s.copy(
                posX = el.x.toInt().toString(), posY = el.y.toInt().toString(),
                width = el.width.toInt(), height = el.height.toInt(),
                shearX = el.shearX, shearY = el.shearY, opacity = el.opacity
            )
        }
        val newCrop = state.elements.firstOrNull { it.supportsCrop }?.crop ?: task.cropConfig
        val newKeyframes = task.keyframes.copy(
            spectrum = state.keyframesByElement["spectrum_0"]?.map { it.toDomain() } ?: task.keyframes.spectrum,
            intro = state.keyframesByElement["intro_text"]?.map { it.toDomain() } ?: task.keyframes.intro,
            title = state.keyframesByElement["title_text"]?.map { it.toDomain() } ?: task.keyframes.title
        )
        val updatedTask = task.copy(spectrums = updatedSpectrums, cropConfig = newCrop, keyframes = newKeyframes)
        loadedTask = updatedTask
        viewModelScope.launch {
            (renderTaskRepository as? RenderTaskRepositoryUpdateExtension)?.updateTask(updatedTask)
            _uiState.value = _uiState.value.copy(saved = true)
        }
    }

    /** Render Preview NYATA (bukan simulasi) lewat FfmpegSessionRunner milik Fase 2, 30 detik pertama. */
    fun renderPreview() {
        val task = loadedTask ?: return
        _uiState.value = _uiState.value.copy(isRenderingPreview = true, previewError = null, previewResultPath = null)
        viewModelScope.launch {
            val state = _uiState.value
            val spectrum = task.spectrums.firstOrNull()
            val crop = state.elements.firstOrNull { it.supportsCrop }?.crop ?: task.cropConfig

            val filters = mutableListOf<String>()
            var lastLabel = "0:v"
            if (crop.isActive) {
                filters += "[$lastLabel]crop=iw-${crop.left + crop.right}:ih-${crop.top + crop.bottom}:${crop.left}:${crop.top}[cropped]"
                lastLabel = "cropped"
            }
            spectrum?.let { s ->
                val el = state.elements.firstOrNull { it.kind == ElementKind.SPECTRUM }
                val px = el?.x?.toInt() ?: 0
                val py = el?.y?.toInt() ?: 0
                filters += "[$lastLabel]drawbox=x=$px:y=$py:w=${s.width}:h=${s.height}:color=${s.color}@${s.opacity}:t=fill[spec]"
                lastLabel = "spec"
            }
            val args = mutableListOf(
                "-y", "-t", "30",
                "-i", task.mediaFiles.firstOrNull()?.toString() ?: "",
                "-i", task.audioFiles.firstOrNull()?.toString() ?: "",
                "-filter_complex", filters.joinToString(";").ifBlank { "[0:v]null[$lastLabel]" },
                "-map", "[$lastLabel]", "-map", "1:a",
                "-preset", "ultrafast", "-t", "30",
                task.outputFolder.toString() + "/preview_${task.id}.mp4"
            )

            val result = ffmpegSessionRunner.run(args) { progress ->
                _uiState.value = _uiState.value.copy(previewProgress = progress)
            }
            _uiState.value = when (result) {
                is RenderResult.Success -> _uiState.value.copy(isRenderingPreview = false, previewResultPath = result.outputPath)
                is RenderResult.Failure -> _uiState.value.copy(isRenderingPreview = false, previewError = result.message)
                RenderResult.Cancelled -> _uiState.value.copy(isRenderingPreview = false, previewError = "Dibatalkan")
            }
        }
    }
}
