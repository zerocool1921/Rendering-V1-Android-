package com.visualizerstudio.app.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.hardwarePrefsDataStore by preferencesDataStore(name = "hardware_prefs")

/**
 * Skema key DataStore untuk preferensi encoder hardware (section 5.3). Nilai
 * `HardwareEncoderMode`/`EncoderSelection` sesungguhnya (Appendix A.4.3) dideklarasikan
 * di Fase 2 (`render/ffmpeg/EncoderParamsProvider.kt`) — file ini hanya skema key
 * penyimpanan preferensinya, disimpan sebagai String mentah (nama enum/mediaCodecName)
 * supaya tidak perlu import tipe milik Fase 2 dari Fase 1.
 */
object HardwarePrefsKeys {
    val ENCODER_MODE = stringPreferencesKey("encoder_mode")             // "AUTO" | "MANUAL" | "CPU_SOFTWARE"
    val MANUAL_MEDIA_CODEC_NAME = stringPreferencesKey("manual_media_codec_name")
    val LAST_DETECTED_CHIPSET = stringPreferencesKey("last_detected_chipset")
    val PREFER_HEVC_OVER_H264 = booleanPreferencesKey("prefer_hevc_over_h264")
}

@Singleton
class HardwarePrefs @Inject constructor(
    private val context: Context
) {
    val encoderMode: Flow<String> =
        context.hardwarePrefsDataStore.data.map { it[HardwarePrefsKeys.ENCODER_MODE] ?: "AUTO" }

    val manualMediaCodecName: Flow<String?> =
        context.hardwarePrefsDataStore.data.map { it[HardwarePrefsKeys.MANUAL_MEDIA_CODEC_NAME] }

    val lastDetectedChipset: Flow<String?> =
        context.hardwarePrefsDataStore.data.map { it[HardwarePrefsKeys.LAST_DETECTED_CHIPSET] }

    val preferHevcOverH264: Flow<Boolean> =
        context.hardwarePrefsDataStore.data.map { it[HardwarePrefsKeys.PREFER_HEVC_OVER_H264] ?: false }

    suspend fun setEncoderMode(mode: String) {
        context.hardwarePrefsDataStore.edit { it[HardwarePrefsKeys.ENCODER_MODE] = mode }
    }

    suspend fun setManualMediaCodecName(name: String) {
        context.hardwarePrefsDataStore.edit { it[HardwarePrefsKeys.MANUAL_MEDIA_CODEC_NAME] = name }
    }

    suspend fun setLastDetectedChipset(chipset: String) {
        context.hardwarePrefsDataStore.edit { it[HardwarePrefsKeys.LAST_DETECTED_CHIPSET] = chipset }
    }

    suspend fun setPreferHevcOverH264(prefer: Boolean) {
        context.hardwarePrefsDataStore.edit { it[HardwarePrefsKeys.PREFER_HEVC_OVER_H264] = prefer }
    }
}
