package com.visualizerstudio.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.visualizerstudio.app.domain.repository.RenderTaskRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mengorkestrasi Antrian Render (section 5.4 blueprint) di atas WorkManager: memastikan HANYA
 * SATU tugas render yang berjalan pada satu waktu (lewat `ExistingWorkPolicy.KEEP` pada nama
 * unique work yang sama), dan otomatis melanjutkan ke tugas `QUEUED` berikutnya begitu tugas
 * saat ini selesai/gagal/dibatalkan — reorder/hapus/retry (UI, Fase 4) cukup mengubah status
 * baris di Room lewat [RenderTaskRepository], scheduler ini yang menariknya kembali secara FIFO.
 */
@Singleton
class RenderQueueScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: RenderTaskRepository
) {

    companion object {
        const val UNIQUE_WORK_NAME = "render_queue_active_task"
        const val KEY_TASK_ID = "task_id"
    }

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** Enqueue tugas [taskId] secara eksplisit (dipanggil UI Fase 4 saat submit wizard/retry manual). */
    fun enqueueTask(taskId: String) {
        val request = OneTimeWorkRequestBuilder<RenderWorker>()
            .setInputData(workDataOf(KEY_TASK_ID to taskId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /**
     * Dipanggil [RenderWorker] setelah menyelesaikan satu tugas (sukses/gagal/cancel) — cari
     * tugas `QUEUED` berikutnya di Room lalu enqueue otomatis. Kalau tidak ada, antrian berhenti
     * dengan tenang (tidak ada foreground service menggantung).
     */
    suspend fun scheduleNextQueuedTaskIfAny() {
        val next = repository.getNextQueuedTask() ?: return
        enqueueTask(next.id)
    }

    /** Batalkan seluruh antrian (dipakai tombol "Batalkan Semua" di UI antrian, Fase 4). */
    fun cancelActiveQueue() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
