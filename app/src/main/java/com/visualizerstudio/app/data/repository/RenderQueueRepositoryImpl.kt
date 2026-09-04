package com.visualizerstudio.app.data.repository

import com.visualizerstudio.app.domain.model.RenderTask
import com.visualizerstudio.app.domain.model.TaskStatus
import com.visualizerstudio.app.domain.repository.RenderQueueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementasi referensi. Mengasumsikan `RenderTaskDao` (Room, milik Fase 1, disebut di
 * section 3 folder data/local/) sudah/akan punya method-method berikut — kalau nama beda,
 * cukup sesuaikan panggilan di file ini saja (domain layer tidak perlu berubah):
 *
 *   interface RenderTaskDao {
 *       fun observeAllOrderedBySortIndex(): Flow<List<RenderTaskEntity>>
 *       suspend fun insert(entity: RenderTaskEntity)
 *       suspend fun deleteById(id: String)
 *       suspend fun updateSortIndices(ids: List<String>)
 *       suspend fun updateStatus(id: String, status: TaskStatus, errorMessage: String?)
 *   }
 *
 * `RenderTaskMapper` diasumsikan tersedia (Entity <-> domain RenderTask) — kalau Fase 1
 * belum punya, buat sebagai file baru terpisah `data/local/RenderTaskMapper.kt` (tidak
 * mengedit Entity/DAO Fase 1).
 */
@Singleton
class RenderQueueRepositoryImpl @Inject constructor(
    private val dao: RenderTaskDaoContract
) : RenderQueueRepository {

    override fun observeAllTasks(): Flow<List<RenderTask>> =
        dao.observeAllOrderedBySortIndex().map { entities -> entities.map { it.toDomain() } }

    override suspend fun insertTask(task: RenderTask) {
        dao.insert(task.toEntity())
    }

    override suspend fun deleteTask(id: String) {
        dao.deleteById(id)
    }

    override suspend fun reorderTasks(orderedIds: List<String>) {
        dao.updateSortIndices(orderedIds)
    }

    override suspend fun retryTask(id: String) {
        dao.updateStatus(id, TaskStatus.QUEUED, errorMessage = null)
    }
}

/**
 * Kontrak minimal yang dibutuhkan implementasi di atas dari `RenderTaskDao` milik Fase 1.
 * Ditulis sebagai interface terpisah (bukan asumsi nama class Room langsung) supaya modul
 * `di/QueueRepositoryModule.kt` di bawah bisa mem-bind ke DAO asli Fase 1 dengan adapter
 * tipis kalau nama method persisnya berbeda, tanpa mengedit file ini lagi.
 */
interface RenderTaskDaoContract {
    fun observeAllOrderedBySortIndex(): Flow<List<RenderTaskEntityContract>>
    suspend fun insert(entity: RenderTaskEntityContract)
    suspend fun deleteById(id: String)
    suspend fun updateSortIndices(ids: List<String>)
    suspend fun updateStatus(id: String, status: TaskStatus, errorMessage: String?)
}

/** Placeholder tipe entity — ganti dengan `RenderTaskEntity` asli Fase 1 saat digabung. */
interface RenderTaskEntityContract {
    fun toDomain(): RenderTask
}

private fun RenderTask.toEntity(): RenderTaskEntityContract {
    throw NotImplementedError(
        "Hubungkan ke RenderTaskEntity asli Fase 1 saat file ini digabung ke project " +
            "(lihat README_FASE4.md #1). Placeholder ini sengaja dibiarkan eksplisit error " +
            "daripada silent-fail, supaya tidak lolos sebagai 'selesai' tanpa integrasi nyata."
    )
}
