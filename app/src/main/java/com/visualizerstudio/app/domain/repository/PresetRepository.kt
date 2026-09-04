package com.visualizerstudio.app.domain.repository

import com.visualizerstudio.app.domain.model.Preset
import kotlinx.coroutines.flow.Flow

/** Ekstensi baru milik Fase 4 — lihat catatan di domain/model/Preset.kt. */
interface PresetRepository {
    fun observeAllPresets(): Flow<List<Preset>>
    suspend fun getPresetById(id: String): Preset?
    suspend fun savePreset(preset: Preset)
    suspend fun deletePreset(id: String)
}
