package com.visualizerstudio.app.data.repository

import com.visualizerstudio.app.domain.model.Preset
import com.visualizerstudio.app.domain.repository.PresetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Sama pola dengan RenderQueueRepositoryImpl — lihat catatan di file itu & README #1. */
@Singleton
class PresetRepositoryImpl @Inject constructor(
    private val dao: PresetDaoContract
) : PresetRepository {

    override fun observeAllPresets(): Flow<List<Preset>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getPresetById(id: String): Preset? =
        dao.getById(id)?.toDomain()

    override suspend fun savePreset(preset: Preset) {
        dao.insert(preset.toEntityContract())
    }

    override suspend fun deletePreset(id: String) {
        dao.deleteById(id)
    }
}

interface PresetDaoContract {
    fun observeAll(): Flow<List<PresetEntityContract>>
    suspend fun getById(id: String): PresetEntityContract?
    suspend fun insert(entity: PresetEntityContract)
    suspend fun deleteById(id: String)
}

interface PresetEntityContract {
    fun toDomain(): Preset
}

private fun Preset.toEntityContract(): PresetEntityContract {
    throw NotImplementedError(
        "Hubungkan ke PresetEntity asli (Room, dibuat siapa pun yang mem-bootstrap DAO ini " +
            "dari section 3) saat file ini digabung ke project — lihat README_FASE4.md #1."
    )
}
