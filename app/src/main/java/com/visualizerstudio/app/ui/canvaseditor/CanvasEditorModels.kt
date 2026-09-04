package com.visualizerstudio.app.ui.canvaseditor

import com.visualizerstudio.app.domain.model.CropConfig
import com.visualizerstudio.app.domain.model.EasingType
import com.visualizerstudio.app.domain.model.Keyframe

/** Jenis elemen yang bisa punya gizmo drag di Canvas Editor (perluasan Android — 5.6). */
enum class ElementKind { SPECTRUM, INTRO_TEXT, TITLE_TEXT, OVERLAY_ASSET }

/**
 * Representasi UI satu elemen di kanvas. `x`/`y`/`width`/`height` dalam ruang koordinat video
 * (pixel output, bukan pixel layar) — konversi ke/dari koordinat layar dilakukan di
 * [CanvasEditorScreen] lewat skala kanvas saat ini.
 */
data class CanvasElementUi(
    val id: String,
    val kind: ElementKind,
    val label: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val shearX: Float = 0f,
    val shearY: Float = 0f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
    /** Crop hanya berlaku untuk elemen yang punya cropConfig (umumnya spectrum/background). */
    val crop: CropConfig? = null,
    val supportsCrop: Boolean = false
)

data class KeyframeUi(
    val id: String,
    val timeSec: Float,
    val x: Float,
    val y: Float,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val easing: EasingType = EasingType.LINEAR
) {
    fun toDomain() = Keyframe(timeSec, x, y, scaleX, scaleY, rotation, opacity, easing)

    companion object {
        fun fromDomain(k: Keyframe, id: String) =
            KeyframeUi(id, k.timeSec, k.x, k.y, k.scaleX, k.scaleY, k.rotation, k.opacity, k.easing)
    }
}

enum class GizmoInteraction { NONE, DRAG_MOVE, RESIZE, SHEAR, CROP_LEFT, CROP_RIGHT, CROP_TOP, CROP_BOTTOM }

data class SnapSettings(
    val snapToGrid: Boolean = false,
    val gridSizePx: Float = 20f,
    val snapToSafeArea: Boolean = true,
    val safeAreaMarginPx: Float = 40f
)

/** Interpolasi linear/easing antar-2 keyframe terdekat, dipakai preview timeline bergerak. */
fun interpolateKeyframes(keyframes: List<KeyframeUi>, timeSec: Float): KeyframeUi? {
    if (keyframes.isEmpty()) return null
    val sorted = keyframes.sortedBy { it.timeSec }
    if (timeSec <= sorted.first().timeSec) return sorted.first()
    if (timeSec >= sorted.last().timeSec) return sorted.last()
    val idx = sorted.indexOfLast { it.timeSec <= timeSec }
    val a = sorted[idx]
    val b = sorted[idx + 1]
    val span = (b.timeSec - a.timeSec).coerceAtLeast(0.0001f)
    var t = (timeSec - a.timeSec) / span
    t = when (a.easing) {
        EasingType.LINEAR -> t
        EasingType.EASE_IN -> t * t
        EasingType.EASE_OUT -> 1f - (1f - t) * (1f - t)
        EasingType.EASE_IN_OUT -> if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).let { it * it } / 2f
    }
    return KeyframeUi(
        id = "interp",
        timeSec = timeSec,
        x = a.x + (b.x - a.x) * t,
        y = a.y + (b.y - a.y) * t,
        scaleX = a.scaleX + (b.scaleX - a.scaleX) * t,
        scaleY = a.scaleY + (b.scaleY - a.scaleY) * t,
        rotation = a.rotation + (b.rotation - a.rotation) * t,
        opacity = a.opacity + (b.opacity - a.opacity) * t,
        easing = a.easing
    )
}
