package com.visualizerstudio.app.data.local

import kotlinx.serialization.Serializable

/**
 * DTO serializable yang menjadi cermin (mirror) dari data class di
 * `domain/model/RenderTask.kt` (Appendix A.3, kontrak beku).
 *
 * Kenapa tidak langsung anotasi `@Serializable` di file domain: file domain adalah
 * kontrak beku yang WAJIB direproduksi persis oleh fase manapun (aturan besi 0.1 & A.3),
 * dan memakai `android.net.Uri` yang bukan tipe primitif buat kotlinx.serialization.
 * Jadi field kompleks `RenderTask` disimpan sebagai kolom TEXT berisi JSON dari DTO ini,
 * lalu di-mapping bolak-balik ke domain model murni lewat `RenderTaskMapper.kt`.
 */

@Serializable
enum class SpectrumTypeDto { SHOWFREQS, SHOWWAVES, CANDLES, SEGMENTED_FREQ, SHOWFREQS_LOG, NONE, CUSTOM_SHADER }

@Serializable
data class SpectrumConfigDto(
    val shaderRef: String = "", val specType: SpectrumTypeDto = SpectrumTypeDto.SHOWFREQS,
    val width: Int = 800, val height: Int = 300,
    val posX: String = "(W-w)/2", val posY: String = "H-h-50",
    val color: String = "white", val opacity: Float = 0.8f,
    val bgColor: String? = null, val bgOpacity: Float = 0.5f,
    val useBeatZoom: Boolean = false, val shearX: Float = 0f, val shearY: Float = 0f
)

@Serializable
data class OverlayAssetConfigDto(
    val fileUri: String, val opacity: Float = 1.0f, val fadeInSeconds: Float = 2.0f,
    val loop: Boolean = false, val delaySeconds: Float = 0f,
    val shearX: Float = 0f, val shearY: Float = 0f, val autoLumaKey: Boolean = true
)

@Serializable
data class OverlaySpeedConfigDto(
    val active: Boolean = false, val targetOverlayIndices: List<Int> = emptyList(),
    val speedMode: String = "auto_beat"
)

@Serializable
data class CropConfigDto(val left: Int = 0, val right: Int = 0, val top: Int = 0, val bottom: Int = 0)

@Serializable
enum class TextPositionDto { CENTER_CENTER, CENTER_BOTTOM, CENTER_TOP, TOP_LEFT, TOP_RIGHT, MID_LEFT, MID_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

@Serializable
enum class TextAnimationDto { FADE_IN_OUT, SLIDE_UP, SLIDE_LEFT, ZOOM_IN }

@Serializable
data class TextLineDto(val text: String, val fontSizePx: Int)

@Serializable
data class TextOverlayConfigDto(
    val lines: List<TextLineDto>, val delaySeconds: Float = 0f, val displayDurationSeconds: Float = 5.0f,
    val fontFamily: String = "Arial", val bold: Boolean = false, val italic: Boolean = false,
    val fontColor: String = "white", val opacity: Float = 1.0f,
    val positionPreset: TextPositionDto = TextPositionDto.CENTER_CENTER,
    val animationStyle: TextAnimationDto = TextAnimationDto.FADE_IN_OUT,
    val animationDurationSeconds: Float = 1.0f,
    val useStroke: Boolean = false, val strokeColor: String = "black", val strokeWidthPx: Int = 2,
    val xExpr: String? = null, val yExpr: String? = null
)

@Serializable
enum class EasingTypeDto { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }

@Serializable
data class KeyframeDto(
    val timeSec: Float, val x: Float, val y: Float,
    val scaleX: Float = 1f, val scaleY: Float = 1f, val rotation: Float = 0f,
    val opacity: Float = 1f, val easing: EasingTypeDto = EasingTypeDto.LINEAR
)

@Serializable
data class KeyframeSetDto(
    val bg: List<KeyframeDto> = emptyList(), val spectrum: List<KeyframeDto> = emptyList(),
    val intro: List<KeyframeDto> = emptyList(), val title: List<KeyframeDto> = emptyList(),
    val overlays: Map<String, List<KeyframeDto>> = emptyMap()
)

@Serializable
data class SlowMotionGroupConfigDto(val active: Boolean = false, val speed: Float = 1.0f)

@Serializable
data class SlowMotionConfigDto(
    val visualGroup: SlowMotionGroupConfigDto? = null, val overlayGroup: SlowMotionGroupConfigDto? = null,
    val perFile: Map<String, SlowMotionGroupConfigDto> = emptyMap()
)

@Serializable
data class ReversePlayConfigDto(val active: Boolean = false)
