package com.visualizerstudio.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visualizerstudio.app.domain.model.TaskStatus
import com.visualizerstudio.app.domain.repository.RenderQueueRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class QueueSummary(
    val queuedCount: Int,
    val renderingCount: Int,
    val doneCount: Int,
    val failedCount: Int
) {
    val total get() = queuedCount + renderingCount + doneCount + failedCount
}

/**
 * Info encoder hardware. `HardwareEncoderDetector` sesungguhnya milik Fase 2
 * (`render/ffmpeg/EncoderParamsProvider.kt`, A.4.3) — Home hanya MENGONSUMSI hasilnya lewat
 * Hilt injection, tidak membuat ulang deteksinya sendiri.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    queueRepository: RenderQueueRepository
) : ViewModel() {

    val queueSummary: StateFlow<QueueSummary> = queueRepository.observeAllTasks()
        .map { tasks ->
            QueueSummary(
                queuedCount = tasks.count { it.status == TaskStatus.QUEUED },
                renderingCount = tasks.count { it.status == TaskStatus.RENDERING },
                doneCount = tasks.count { it.status == TaskStatus.DONE },
                failedCount = tasks.count { it.status == TaskStatus.FAILED }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QueueSummary(0, 0, 0, 0))
}
