package com.visualizerstudio.app.data.local

import android.net.Uri
import com.visualizerstudio.app.domain.model.BgVisualEffect
import com.visualizerstudio.app.domain.model.CropConfig
import com.visualizerstudio.app.domain.model.EasingType
import com.visualizerstudio.app.domain.model.Keyframe
import com.visualizerstudio.app.domain.model.KeyframeSet
import com.visualizerstudio.app.domain.model.MediaMode
import com.visualizerstudio.app.domain.model.OverlayAssetConfig
import com.visualizerstudio.app.domain.model.OverlaySpeedConfig
import com.visualizerstudio.app.domain.model.RenderTask
import com.visualizerstudio.app.domain.model.Resolution
import com.visualizerstudio.app.domain.model.ReversePlayConfig
import com.visualizerstudio.app.domain.model.SlowMotionConfig
import com.visualizerstudio.app.domain.model.SlowMotionGroupConfig
import com.visualizerstudio.app.domain.model.SpectrumConfig
import com.visualizerstudio.app.domain.model.SpectrumType
import com.visualizerstudio.app.domain.model.TaskStatus
import com.visualizerstudio.app.domain.model.TextAnimation
import com.visualizerstudio.app.domain.model.TextLine
import com.visualizerstudio.app.domain.model.TextOverlayConfig
import com.visualizerstudio.app.domain.model.TextPosition
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Mapper murni (tanpa side effect) antara `domain.model.RenderTask` (kontrak beku,
 * tidak boleh diubah) dan `RenderTaskEntity`/DTO (milik layer data, boleh berevolusi
 * bebas selama fungsi mapping ini tetap konsisten).
 */
object RenderTaskMapper {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun toEntity(task: RenderTask): RenderTaskEntity = RenderTaskEntity(
        id = task.id,
        name = task.name,
        mediaFilesJson = json.encodeToString(task.mediaFiles.map { it.toString() }),
        audioFilesJson = json.encodeToString(task.audioFiles.map { it.toString() }),
        mediaMode = task.mediaMode.name,
        resolution = task.resolution.name,
        fps = task.fps,
        loops = task.loops,
        spectrumsJson = json.encodeToString(task.spectrums.map { it.toDto() }),
        bgVideoOverlaysJson = json.encodeToString(task.bgVideoOverlays.map { it.toDto() }),
        introConfigJson = task.introConfig?.let { json.encodeToString(it.toDto()) },
        titleConfigJson = task.titleConfig?.let { json.encodeToString(it.toDto()) },
        slowMotionConfigJson = json.encodeToString(task.slowMotionConfig.toDto()),
        reversePlayConfigJson = json.encodeToString(ReversePlayConfigDto(task.reversePlayConfig.active)),
        overlaySpeedConfigJson = json.encodeToString(task.overlaySpeedConfig.toDto()),
        cropConfigJson = json.encodeToString(task.cropConfig.toDto()),
        bgVisualEffect = task.bgVisualEffect.name,
        useBeatZoom = task.useBeatZoom,
        keyframesJson = json.encodeToString(task.keyframes.toDto()),
        outputFolder = task.outputFolder.toString(),
        status = task.status.name,
        errorMessage = null
    )

    fun toDomain(entity: RenderTaskEntity): RenderTask = RenderTask(
        id = entity.id,
        name = entity.name,
        mediaFiles = json.decodeFromString<List<String>>(entity.mediaFilesJson).map { Uri.parse(it) },
        audioFiles = json.decodeFromString<List<String>>(entity.audioFilesJson).map { Uri.parse(it) },
        mediaMode = MediaMode.valueOf(entity.mediaMode),
        resolution = Resolution.valueOf(entity.resolution),
        fps = entity.fps,
        loops = entity.loops,
        spectrums = json.decodeFromString<List<SpectrumConfigDto>>(entity.spectrumsJson).map { it.toDomain() },
        bgVideoOverlays = json.decodeFromString<List<OverlayAssetConfigDto>>(entity.bgVideoOverlaysJson).map { it.toDomain() },
        introConfig = entity.introConfigJson?.let { json.decodeFromString<TextOverlayConfigDto>(it).toDomain() },
        titleConfig = entity.titleConfigJson?.let { json.decodeFromString<TextOverlayConfigDto>(it).toDomain() },
        slowMotionConfig = json.decodeFromString<SlowMotionConfigDto>(entity.slowMotionConfigJson).toDomain(),
        reversePlayConfig = ReversePlayConfig(json.decodeFromString<ReversePlayConfigDto>(entity.reversePlayConfigJson).active),
        overlaySpeedConfig = json.decodeFromString<OverlaySpeedConfigDto>(entity.overlaySpeedConfigJson).toDomain(),
        cropConfig = json.decodeFromString<CropConfigDto>(entity.cropConfigJson).toDomain(),
        bgVisualEffect = BgVisualEffect.valueOf(entity.bgVisualEffect),
        useBeatZoom = entity.useBeatZoom,
        keyframes = json.decodeFromString<KeyframeSetDto>(entity.keyframesJson).toDomain(),
        outputFolder = Uri.parse(entity.outputFolder),
        status = TaskStatus.valueOf(entity.status)
    )

    // ---- Domain -> Dto ----
    private fun SpectrumConfig.toDto() = SpectrumConfigDto(
        shaderRef, SpectrumTypeDto.valueOf(specType.name), width, height, posX, posY,
        color, opacity, bgColor, bgOpacity, useBeatZoom, shearX, shearY
    )

    private fun OverlayAssetConfig.toDto() = OverlayAssetConfigDto(
        fileUri.toString(), opacity, fadeInSeconds, loop, delaySeconds, shearX, shearY, autoLumaKey
    )

    private fun OverlaySpeedConfig.toDto() = OverlaySpeedConfigDto(active, targetOverlayIndices, speedMode)

    private fun CropConfig.toDto() = CropConfigDto(left, right, top, bottom)

    private fun TextOverlayConfig.toDto() = TextOverlayConfigDto(
        lines.map { TextLineDto(it.text, it.fontSizePx) }, delaySeconds, displayDurationSeconds,
        fontFamily, bold, italic, fontColor, opacity,
        TextPositionDto.valueOf(positionPreset.name), TextAnimationDto.valueOf(animationStyle.name),
        animationDurationSeconds, useStroke, strokeColor, strokeWidthPx, xExpr, yExpr
    )

    private fun KeyframeSet.toDto() = KeyframeSetDto(
        bg.map { it.toDto() }, spectrum.map { it.toDto() }, intro.map { it.toDto() }, title.map { it.toDto() },
        overlays.mapValues { (_, v) -> v.map { it.toDto() } }
    )

    private fun Keyframe.toDto() = KeyframeDto(
        timeSec, x, y, scaleX, scaleY, rotation, opacity, EasingTypeDto.valueOf(easing.name)
    )

    private fun SlowMotionConfig.toDto() = SlowMotionConfigDto(
        visualGroup?.toDto(), overlayGroup?.toDto(), perFile.mapValues { (_, v) -> v.toDto() }
    )

    private fun SlowMotionGroupConfig.toDto() = SlowMotionGroupConfigDto(active, speed)

    // ---- Dto -> Domain ----
    private fun SpectrumConfigDto.toDomain() = SpectrumConfig(
        shaderRef, SpectrumType.valueOf(specType.name), width, height, posX, posY,
        color, opacity, bgColor, bgOpacity, useBeatZoom, shearX, shearY
    )

    private fun OverlayAssetConfigDto.toDomain() = OverlayAssetConfig(
        Uri.parse(fileUri), opacity, fadeInSeconds, loop, delaySeconds, shearX, shearY, autoLumaKey
    )

    private fun OverlaySpeedConfigDto.toDomain() = OverlaySpeedConfig(active, targetOverlayIndices, speedMode)

    private fun CropConfigDto.toDomain() = CropConfig(left, right, top, bottom)

    private fun TextOverlayConfigDto.toDomain() = TextOverlayConfig(
        lines.map { TextLine(it.text, it.fontSizePx) }, delaySeconds, displayDurationSeconds,
        fontFamily, bold, italic, fontColor, opacity,
        TextPosition.valueOf(positionPreset.name), TextAnimation.valueOf(animationStyle.name),
        animationDurationSeconds, useStroke, strokeColor, strokeWidthPx, xExpr, yExpr
    )

    private fun KeyframeSetDto.toDomain() = KeyframeSet(
        bg.map { it.toDomain() }, spectrum.map { it.toDomain() }, intro.map { it.toDomain() }, title.map { it.toDomain() },
        overlays.mapValues { (_, v) -> v.map { it.toDomain() } }
    )

    private fun KeyframeDto.toDomain() = Keyframe(
        timeSec, x, y, scaleX, scaleY, rotation, opacity, EasingType.valueOf(easing.name)
    )

    private fun SlowMotionConfigDto.toDomain() = SlowMotionConfig(
        visualGroup?.toDomain(), overlayGroup?.toDomain(), perFile.mapValues { (_, v) -> v.toDomain() }
    )

    private fun SlowMotionGroupConfigDto.toDomain() = SlowMotionGroupConfig(active, speed)
}
