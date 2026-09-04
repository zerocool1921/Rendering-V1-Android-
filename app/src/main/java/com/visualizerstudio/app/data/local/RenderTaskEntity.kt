package com.visualizerstudio.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Representasi Room dari `domain.model.RenderTask` (Appendix A.3). Field list/objek
 * kompleks disimpan sebagai kolom TEXT berisi JSON (lihat `Converters.kt` &
 * `RenderTaskMapper.kt`) supaya skema tabel tetap flat & stabil walau model domain
 * bertambah sub-field di fase lanjutan (mis. Fase 5 menambah properti Canvas Editor).
 */
@Entity(tableName = "render_task")
data class RenderTaskEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mediaFilesJson: String,       // List<String> (Uri.toString()) di-encode JSON
    val audioFilesJson: String,       // List<String> (Uri.toString()) di-encode JSON
    val mediaMode: String,            // enum MediaMode.name
    val resolution: String,           // enum Resolution.name
    val fps: Int,
    val loops: Int,
    val spectrumsJson: String,        // List<SpectrumConfigDto>
    val bgVideoOverlaysJson: String,  // List<OverlayAssetConfigDto>
    val introConfigJson: String?,     // TextOverlayConfigDto?
    val titleConfigJson: String?,     // TextOverlayConfigDto?
    val slowMotionConfigJson: String, // SlowMotionConfigDto
    val reversePlayConfigJson: String,// ReversePlayConfigDto
    val overlaySpeedConfigJson: String, // OverlaySpeedConfigDto
    val cropConfigJson: String,       // CropConfigDto
    val bgVisualEffect: String,       // enum BgVisualEffect.name
    val useBeatZoom: Boolean,
    val keyframesJson: String,        // KeyframeSetDto
    val outputFolder: String,         // Uri.toString()
    val status: String,               // enum TaskStatus.name
    val errorMessage: String? = null, // diisi RenderTaskRepository.updateStatus()
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val queueOrder: Long = System.currentTimeMillis() // dasar urutan FIFO getNextQueuedTask()
)
