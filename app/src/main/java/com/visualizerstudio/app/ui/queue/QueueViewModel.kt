package com.visualizerstudio.app.ui.queue

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.visualizerstudio.app.domain.model.RenderTask
import com.visualizerstudio.app.domain.repository.RenderQueueRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val queueRepository: RenderQueueRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val workManager by lazy { WorkManager.getInstance(context) }

    val tasks: StateFlow<List<RenderTask>> = queueRepository.observeAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Progress real per tugas -- bukan animasi palsu (DoD C.4). Diasumsikan RenderWorker
     * (Fase 2) mendaftarkan pekerjaan lewat enqueueUniqueWork(taskId, ...) dan memanggil
     * setProgressAsync(workDataOf("percent" to ..., "currentTimeSec" to ..., "totalTimeSec" to ...))
     * -- lihat README_FASE4.md asumsi #2. Dipanggil per-item dari Compose (observeAsState)
     * supaya tiap card hanya recompose saat WorkInfo tugasnya sendiri berubah.
     */
    fun workInfoLiveDataFor(taskId: String): LiveData<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkLiveData(taskId)

    fun deleteTask(id: String) {
        viewModelScope.launch { queueRepository.deleteTask(id) }
    }

    fun retryTask(id: String) {
        viewModelScope.launch { queueRepository.retryTask(id) }
    }

    /** Dipanggil setelah drag-reorder selesai di UI (list Compose sudah dalam urutan baru). */
    fun commitReorder(orderedIds: List<String>) {
        viewModelScope.launch { queueRepository.reorderTasks(orderedIds) }
    }
}

fun WorkInfo?.readPercent(): Float = this?.progress?.getFloat("percent", 0f) ?: 0f
fun WorkInfo?.readCurrentTimeSec(): Float = this?.progress?.getFloat("currentTimeSec", 0f) ?: 0f
fun WorkInfo?.readTotalTimeSec(): Float = this?.progress?.getFloat("totalTimeSec", 0f) ?: 0f
