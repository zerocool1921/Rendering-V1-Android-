package com.visualizerstudio.app.domain.model

import android.net.Uri

enum class MediaMode { EQUAL_SPLIT, PER_TRACK }

enum class Resolution(val width: Int, val height: Int) {
    R720P(1280, 720), R1080P(1920, 1080), R2K(2560, 1440), R4K(3840, 2160)
}

enum class TaskStatus { QUEUED, RENDERING, DONE, FAILED }
enum class BgVisualEffect { NONE, VIGNETTE, BLUR, GRAYSCALE, SEPIA }
enum class SpectrumType { SHOWFREQS, SHOWWAVES, CANDLES, SEGMENTED_FREQ, SHOWFREQS_LOG, NONE, CUSTOM_SHADER }

data class SpectrumConfig(
    val shaderRef: String = "", val specType: SpectrumType = SpectrumType.SHOWFREQS,
    val width: Int = 800, val height: Int = 300,
    val posX: String = "(W-w)/2", val posY: String = "H-h-50",
    val color: String = "white", val opacity: Float = 0.8f,
    val bgColor: String? = null, val bgOpacity: Float = 0.5f,
    val useBeatZoom: Boolean = false, val shearX: Float = 0f, val shearY: Float = 0f
)

data class OverlayAssetConfig(
    val fileUri: Uri, val opacity: Float = 1.0f, val fadeInSeconds: Float = 2.0f,
    val loop: Boolean = false, val delaySeconds: Float = 0f,
    val shearX: Float = 0f, val shearY: Float = 0f, val autoLumaKey: Boolean = true
)

data class OverlaySpeedConfig(
    val active: Boolean = false, val targetOverlayIndices: List<Int> = emptyList(),
    val speedMode: String = "auto_beat"
)

data class CropConfig(val left: Int = 0, val right: Int = 0, val top: Int = 0, val bottom: Int = 0) {
    val isActive: Boolean get() = left > 0 || right > 0 || top > 0 || bottom > 0
}

enum class TextPosition { CENTER_CENTER, CENTER_BOTTOM, CENTER_TOP, TOP_LEFT, TOP_RIGHT, MID_LEFT, MID_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
enum class TextAnimation(val code: Int) { FADE_IN_OUT(1), SLIDE_UP(2), SLIDE_LEFT(3), ZOOM_IN(4) }
data class TextLine(val text: String, val fontSizePx: Int)

data class TextOverlayConfig(
    val lines: List<TextLine>, val delaySeconds: Float = 0f, val displayDurationSeconds: Float = 5.0f,
    val fontFamily: String = "Arial", val bold: Boolean = false, val italic: Boolean = false,
    val fontColor: String = "white", val opacity: Float = 1.0f,
    val positionPreset: TextPosition = TextPosition.CENTER_CENTER,
    val animationStyle: TextAnimation = TextAnimation.FADE_IN_OUT,
    val animationDurationSeconds: Float = 1.0f,
    val useStroke: Boolean = false, val strokeColor: String = "black", val strokeWidthPx: Int = 2,
    val xExpr: String? = null, val yExpr: String? = null
)

enum class EasingType { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }
data class Keyframe(
    val timeSec: Float, val x: Float, val y: Float,
    val scaleX: Float = 1f, val scaleY: Float = 1f, val rotation: Float = 0f,
    val opacity: Float = 1f, val easing: EasingType = EasingType.LINEAR
)
data class KeyframeSet(
    val bg: List<Keyframe> = emptyList(), val spectrum: List<Keyframe> = emptyList(),
    val intro: List<Keyframe> = emptyList(), val title: List<Keyframe> = emptyList(),
    val overlays: Map<String, List<Keyframe>> = emptyMap()
)

data class SlowMotionGroupConfig(val active: Boolean = false, val speed: Float = 1.0f)
data class SlowMotionConfig(
    val visualGroup: SlowMotionGroupConfig? = null, val overlayGroup: SlowMotionGroupConfig? = null,
    val perFile: Map<String, SlowMotionGroupConfig> = emptyMap()
)
data class ReversePlayConfig(val active: Boolean = false)

data class RenderTask(
    val id: String, val name: String,
    val mediaFiles: List<Uri>, val audioFiles: List<Uri>,
    val mediaMode: MediaMode = MediaMode.PER_TRACK,
    val resolution: Resolution = Resolution.R720P,
    val fps: Int = 30, val loops: Int = 1,
    val spectrums: List<SpectrumConfig> = emptyList(),
    val bgVideoOverlays: List<OverlayAssetConfig> = emptyList(),
    val introConfig: TextOverlayConfig? = null, val titleConfig: TextOverlayConfig? = null,
    val slowMotionConfig: SlowMotionConfig = SlowMotionConfig(),
    val reversePlayConfig: ReversePlayConfig = ReversePlayConfig(),
    val overlaySpeedConfig: OverlaySpeedConfig = OverlaySpeedConfig(),
    val cropConfig: CropConfig = CropConfig(),
    val bgVisualEffect: BgVisualEffect = BgVisualEffect.NONE,
    val useBeatZoom: Boolean = false,
    val keyframes: KeyframeSet = KeyframeSet(),
    val outputFolder: Uri, val status: TaskStatus = TaskStatus.QUEUED
)
