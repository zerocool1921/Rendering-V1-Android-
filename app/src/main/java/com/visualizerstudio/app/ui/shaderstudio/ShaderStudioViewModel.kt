package com.visualizerstudio.app.ui.shaderstudio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visualizerstudio.app.data.local.ShaderTemplateDao
import com.visualizerstudio.app.domain.model.ShaderCategory
import com.visualizerstudio.app.domain.model.ShaderTemplateEntity
import com.visualizerstudio.app.render.gl.ShaderCategory as ValidatorCategory
import com.visualizerstudio.app.render.gl.ShaderSkeletons
import com.visualizerstudio.app.render.gl.ShaderValidationResult
import com.visualizerstudio.app.render.gl.ShaderValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Catatan kepemilikan: [ShaderTemplateDao] & [ShaderTemplateEntity] diasumsikan sudah ada dari
 * Fase 1 (data/local & domain/model, lihat Appendix A.2 baris "Room DAO: ... ShaderTemplateDao"
 * di section 3) — file ini HANYA meng-`import` & memakainya, tidak membuat ulang.
 */

sealed class GenerateOutcome {
    data class Success(val entity: ShaderTemplateEntity) : GenerateOutcome()
    data class Rejected(val result: ShaderValidationResult) : GenerateOutcome()
}

@HiltViewModel
class ShaderStudioViewModel @Inject constructor(
    private val shaderTemplateDao: ShaderTemplateDao,
    private val validator: ShaderValidator,
    private val thumbnailRenderer: com.visualizerstudio.app.ui.shaderstudio.ShaderThumbnailRenderer
) : ViewModel() {

    val gallery: StateFlow<List<ShaderTemplateEntity>> =
        shaderTemplateDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _lastOutcome = MutableStateFlow<GenerateOutcome?>(null)
    val lastOutcome: StateFlow<GenerateOutcome?> = _lastOutcome

    private val _thumbnails = MutableStateFlow<Map<String, android.net.Uri>>(emptyMap())
    val thumbnails: StateFlow<Map<String, android.net.Uri>> = _thumbnails

    /** Render thumbnail nyata sekali per shader (dipanggil galeri saat entry pertama tampil). */
    fun ensureThumbnail(entity: ShaderTemplateEntity) {
        if (entity.thumbnailUri != null || _thumbnails.value.containsKey(entity.id)) return
        viewModelScope.launch {
            val uri = thumbnailRenderer.renderThumbnail(entity) ?: return@launch
            _thumbnails.value = _thumbnails.value + (entity.id to uri)
            shaderTemplateDao.updateThumbnail(entity.id, uri)
        }
    }

    // ---------------- Timer 3D ----------------
    fun generateTimer3D(name: String, colorHex: String, phaseTexts: List<Pair<String, String>>) {
        val phases = phaseTexts.map { ShaderSkeletons.TimerPhase(it.first, it.second) }
        val raw = ShaderSkeletons.timer3D(colorHex, phases)
        validateAndSave(name, ShaderCategory.TIMER_3D, ValidatorCategory.TIMER_3D, raw, paramsJson = buildParamsJson(
            "colorHex" to colorHex,
            "phaseCount" to phases.size.toString()
        ))
    }

    // ---------------- Spectrum Custom ----------------
    fun generateSpectrum(
        name: String,
        colorHex: String,
        style: ShaderSkeletons.SpectrumStyle,
        barCount: Int,
        beatZoomSensitivity: Float,
        glow: Boolean
    ) {
        val raw = ShaderSkeletons.spectrumCustom(colorHex, style, barCount, beatZoomSensitivity, glow)
        validateAndSave(name, ShaderCategory.SPECTRUM, ValidatorCategory.SPECTRUM, raw, paramsJson = buildParamsJson(
            "colorHex" to colorHex,
            "style" to style.name,
            "barCount" to barCount.toString(),
            "beatZoomSensitivity" to beatZoomSensitivity.toString(),
            "glow" to glow.toString()
        ))
    }

    // ---------------- Overlay Effect Loop ----------------
    fun generateOverlayLoop(
        name: String,
        colorHex: String,
        kind: ShaderSkeletons.OverlayEffectKind,
        density: Float,
        loopDurationSec: Float
    ) {
        val raw = ShaderSkeletons.overlayEffectLoop(colorHex, kind, density, loopDurationSec)
        validateAndSave(name, ShaderCategory.OVERLAY_LOOP, ValidatorCategory.OVERLAY_LOOP, raw, paramsJson = buildParamsJson(
            "colorHex" to colorHex,
            "kind" to kind.name,
            "density" to density.toString(),
            "loopDurationSec" to loopDurationSec.toString()
        ))
    }

    /** Mode Advanced/Impor (section 6.4) — user tempel .glsl sendiri, tetap wajib divalidasi. */
    fun importCustomGlsl(name: String, category: ShaderCategory, rawSource: String) {
        val validatorCategory = when (category) {
            ShaderCategory.TIMER_3D -> ValidatorCategory.TIMER_3D
            ShaderCategory.SPECTRUM -> ValidatorCategory.SPECTRUM
            ShaderCategory.OVERLAY_LOOP -> ValidatorCategory.OVERLAY_LOOP
            ShaderCategory.BUILTIN -> ValidatorCategory.BUILTIN
        }
        validateAndSave(name, category, validatorCategory, rawSource, paramsJson = "{}")
    }

    private fun validateAndSave(
        name: String,
        category: ShaderCategory,
        validatorCategory: ValidatorCategory,
        rawSource: String,
        paramsJson: String
    ) {
        val result = validator.validateAndConvert(rawSource, validatorCategory)
        if (!result.isValid || result.glslEs == null) {
            _lastOutcome.value = GenerateOutcome.Rejected(result)
            return
        }
        val entity = ShaderTemplateEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            category = category,
            glslCode = result.glslEs,
            thumbnailUri = null, // diisi belakangan oleh ShaderThumbnailRenderer setelah render pertama
            paramsJson = paramsJson
        )
        viewModelScope.launch {
            shaderTemplateDao.insert(entity)
            _lastOutcome.value = GenerateOutcome.Success(entity)
        }
    }

    private fun buildParamsJson(vararg pairs: Pair<String, String>): String =
        pairs.joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) -> "\"$k\":\"$v\"" }
}
