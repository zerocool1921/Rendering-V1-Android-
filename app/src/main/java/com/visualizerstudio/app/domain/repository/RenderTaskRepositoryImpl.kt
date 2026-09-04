package com.visualizerstudio.app.domain.repository

import com.visualizerstudio.app.data.local.RenderTaskDao
import com.visualizerstudio.app.data.local.RenderTaskMapper
import com.visualizerstudio.app.domain.model.RenderTask
import com.visualizerstudio.app.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementasi konkret `RenderTaskRepository` (kontrak beku Appendix A.4.1) di atas Room.
 * Method di luar interface (insert/observeAll/reorder) sengaja ditambahkan sebagai method
 * publik tambahan pada class ini — BUKAN menambah ke interface yang beku — supaya Fase 4
 * (Antrian Render, Wizard) bisa langsung memakainya lewat injeksi `RenderTaskRepositoryImpl`
 * atau lewat perluasan interface pada revisi Appendix A berikutnya, tanpa mengedit file ini.
 */
class RenderTaskRepositoryImpl @Inject constructor(
    private val dao: RenderTaskDao
) : RenderTaskRepository {

    override suspend fun getTaskById(id: String): RenderTask? =
        dao.getById(id)?.let { RenderTaskMapper.toDomain(it) }

    override suspend fun updateStatus(id: String, status: TaskStatus, errorMessage: String?) {
        dao.updateStatus(id, status.name, errorMessage)
    }

    override suspend fun getNextQueuedTask(): RenderTask? =
        dao.getNextQueued()?.let { RenderTaskMapper.toDomain(it) }

    // --- Method tambahan pragmatis (di luar kontrak beku A.4.1) untuk mendukung
    //     penyimpanan & antrian penuh sejak Fase 1, dipakai UI Fase 4 nanti. ---

    suspend fun upsertTask(task: RenderTask) {
        dao.insert(RenderTaskMapper.toEntity(task))
    }

    suspend fun deleteTask(task: RenderTask) {
        dao.delete(RenderTaskMapper.toEntity(task))
    }

    suspend fun reorderTask(id: String, newOrder: Long) {
        dao.updateQueueOrder(id, newOrder)
    }

    fun observeAllTasks(): Flow<List<RenderTask>> =
        dao.observeAll().map { list -> list.map { RenderTaskMapper.toDomain(it) } }
}
