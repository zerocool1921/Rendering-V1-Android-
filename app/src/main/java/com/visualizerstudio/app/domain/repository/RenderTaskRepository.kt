package com.visualizerstudio.app.domain.repository

import com.visualizerstudio.app.domain.model.RenderTask
import com.visualizerstudio.app.domain.model.TaskStatus

interface RenderTaskRepository {
    suspend fun getTaskById(id: String): RenderTask?
    suspend fun updateStatus(id: String, status: TaskStatus, errorMessage: String? = null)
    suspend fun getNextQueuedTask(): RenderTask?
}
