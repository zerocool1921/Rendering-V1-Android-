package com.visualizerstudio.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Database Room Fase 1. Entity tambahan (PresetEntity milik Fase 5, ShaderTemplateEntity
 * milik Fase 3) ditambahkan lewat migrasi baru di fase masing-masing — TIDAK menambah
 * entity langsung ke `entities = [...]` di file ini (file ini milik Fase 1, lihat
 * aturan besi 0.1 & Appendix A.2). Fase lanjutan yang butuh entity baru menambah
 * `AppDatabase` versi baru via file migrasi terpisah, bukan mengedit file ini.
 */
@Database(
    entities = [RenderTaskEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun renderTaskDao(): RenderTaskDao

    companion object {
        const val DATABASE_NAME = "visualizer_studio.db"
    }
}
