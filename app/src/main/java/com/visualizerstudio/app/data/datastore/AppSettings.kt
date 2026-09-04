package com.visualizerstudio.app.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

/**
 * Skema key DataStore pengaturan umum aplikasi (bukan hardware encoder — lihat
 * `HardwarePrefs.kt` untuk itu). Fase 1 hanya menetapkan skema key + akses baca/tulis
 * dasar; UI yang mengisinya (mis. layar Pengaturan) ditambahkan Fase 4/6.
 */
object AppSettingsKeys {
    val DEFAULT_OUTPUT_FOLDER_URI = stringPreferencesKey("default_output_folder_uri")
    val DEFAULT_RESOLUTION = stringPreferencesKey("default_resolution")       // Resolution.name
    val DEFAULT_FPS = intPreferencesKey("default_fps")
    val DEFAULT_LOOPS = intPreferencesKey("default_loops")
    val LAST_SELECTED_PRESET_ID = stringPreferencesKey("last_selected_preset_id")
    val FIRST_LAUNCH_DONE = booleanPreferencesKey("first_launch_done")
    val AI_SHADER_GENERATION_ENABLED = booleanPreferencesKey("ai_shader_generation_enabled") // section 6.4
}

@Singleton
class AppSettings @Inject constructor(
    private val context: Context
) {
    val defaultOutputFolderUri: Flow<String?> =
        context.appSettingsDataStore.data.map { it[AppSettingsKeys.DEFAULT_OUTPUT_FOLDER_URI] }

    val defaultResolution: Flow<String?> =
        context.appSettingsDataStore.data.map { it[AppSettingsKeys.DEFAULT_RESOLUTION] }

    val defaultFps: Flow<Int?> =
        context.appSettingsDataStore.data.map { it[AppSettingsKeys.DEFAULT_FPS] }

    val defaultLoops: Flow<Int?> =
        context.appSettingsDataStore.data.map { it[AppSettingsKeys.DEFAULT_LOOPS] }

    val firstLaunchDone: Flow<Boolean> =
        context.appSettingsDataStore.data.map { it[AppSettingsKeys.FIRST_LAUNCH_DONE] ?: false }

    val aiShaderGenerationEnabled: Flow<Boolean> =
        context.appSettingsDataStore.data.map { it[AppSettingsKeys.AI_SHADER_GENERATION_ENABLED] ?: false }

    suspend fun setDefaultOutputFolderUri(uri: String) {
        context.appSettingsDataStore.edit { it[AppSettingsKeys.DEFAULT_OUTPUT_FOLDER_URI] = uri }
    }

    suspend fun setDefaultResolution(resolutionName: String) {
        context.appSettingsDataStore.edit { it[AppSettingsKeys.DEFAULT_RESOLUTION] = resolutionName }
    }

    suspend fun setDefaultFps(fps: Int) {
        context.appSettingsDataStore.edit { it[AppSettingsKeys.DEFAULT_FPS] = fps }
    }

    suspend fun setDefaultLoops(loops: Int) {
        context.appSettingsDataStore.edit { it[AppSettingsKeys.DEFAULT_LOOPS] = loops }
    }

    suspend fun setFirstLaunchDone(done: Boolean) {
        context.appSettingsDataStore.edit { it[AppSettingsKeys.FIRST_LAUNCH_DONE] = done }
    }

    suspend fun setAiShaderGenerationEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[AppSettingsKeys.AI_SHADER_GENERATION_ENABLED] = enabled }
    }
}
