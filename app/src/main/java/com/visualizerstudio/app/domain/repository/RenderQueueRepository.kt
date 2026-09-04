package com.visualizerstudio.app.domain.repository

import com.visualizerstudio.app.domain.model.RenderTask
import kotlinx.coroutines.flow.Flow

/**
 * Ekstensi baru milik Fase 4. `RenderTaskRepository` (Appendix A.4.1, milik Fase 1) beku
 * dan hanya berisi 3 method (getTaskById, updateStatus, getNextQueuedTask) — tidak cukup
 * untuk kebutuhan UI Wizard (insert tugas baru) & UI Queue (observe semua tugas, hapus,
 * reorder). Interface ini MELENGKAPI, bukan menggantikan — kedua interface bisa
 * diimplementasikan oleh class Room repository yang sama di layer data.
 *
 * Lihat README_FASE4.md #1 untuk asumsi implementasi konkretnya.
 */
interface RenderQueueRepository {

    /** Semua tugas di antrian, urut berdasarkan sortIndex, reaktif (live update). */
    fun observeAllTasks(): Flow<List<RenderTask>>

    /** Dipanggil di akhir Wizard (langkah 22 selesai) untuk memasukkan tugas baru ke antrian. */
    suspend fun insertTask(task: RenderTask)

    /** Hapus satu tugas (dipakai swipe-delete di layar Antrian). */
    suspend fun deleteTask(id: String)

    /**
     * Simpan ulang urutan eksekusi setelah drag-reorder di layar Antrian.
     * [orderedIds] adalah daftar id RenderTask dari urutan pertama (akan dirender duluan)
     * sampai terakhir. Implementasi WAJIB menulis ulang kolom sortIndex di Room sesuai
     * index posisi di list ini, supaya RenderQueueScheduler (Fase 2) yang query
     * `ORDER BY sortIndex` otomatis ikut urutan baru tanpa perlu disentuh.
     */
    suspend fun reorderTasks(orderedIds: List<String>)

    /** Set ulang status tugas gagal jadi QUEUED lagi (tombol Retry). */
    suspend fun retryTask(id: String)
}
