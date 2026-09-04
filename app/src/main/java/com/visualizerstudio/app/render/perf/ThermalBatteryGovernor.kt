package com.visualizerstudio.app.render.perf

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThermalLevel { NONE, LIGHT, MODERATE, SEVERE, CRITICAL }

/**
 * Rekomendasi kualitas render saat ini, hasil kombinasi status termal + baterai perangkat.
 * Bersifat murni ADVISORY (rekomendasi) — konsumsi nyata di `RenderWorker`/`GlEsSpectrumRenderer`
 * (file milik Fase 2 & 3) BELUM diwire otomatis, karena Fase 6 dilarang mengedit file fase lain
 * (aturan besi 0.1). Lihat catatan integrasi di akhir laporan fase.
 */
data class RenderQualityPolicy(
    val thermalLevel: ThermalLevel,
    val batteryLevelPercent: Int,
    val isCharging: Boolean,
    /** null = tidak perlu membatasi fps preview/render. */
    val recommendedFpsCap: Int?,
    val recommendPauseQueue: Boolean,
    val reason: String
)

@Singleton
class ThermalBatteryGovernor @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _policy = MutableStateFlow(defaultPolicy())
    val policy: StateFlow<RenderQualityPolicy> = _policy.asStateFlow()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                powerManager.addThermalStatusListener { status ->
                    recompute(thermalLevelFromStatus(status))
                }
            }
        }
        refreshNow()
    }

    /** Panggil manual (mis. sebelum enqueue render baru) untuk memaksa hitung ulang status terkini. */
    fun refreshNow() {
        val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalLevelFromStatus(powerManager.currentThermalStatus)
        } else {
            ThermalLevel.NONE
        }
        recompute(level)
    }

    private fun recompute(thermal: ThermalLevel) {
        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val levelPercent = runCatching {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrDefault(100)
        val charging = runCatching { batteryManager.isCharging }.getOrDefault(true)

        val (fpsCap, pauseQueue, reason) = when {
            thermal == ThermalLevel.CRITICAL ->
                Triple(15, true, "Perangkat overheat kritis — antrian render dijeda sementara")
            thermal == ThermalLevel.SEVERE ->
                Triple(20, false, "Thermal severe — turunkan target fps preview/render")
            thermal == ThermalLevel.MODERATE ->
                Triple(24, false, "Thermal moderate — batasi fps render preview")
            levelPercent <= 10 && !charging ->
                Triple(null, true, "Baterai <=10% dan tidak mengisi daya — antrian render dijeda")
            levelPercent <= 20 && !charging ->
                Triple(24, false, "Baterai rendah dan tidak mengisi daya — turunkan beban render")
            else ->
                Triple(null, false, "Normal")
        }

        _policy.value = RenderQualityPolicy(
            thermalLevel = thermal,
            batteryLevelPercent = levelPercent,
            isCharging = charging,
            recommendedFpsCap = fpsCap,
            recommendPauseQueue = pauseQueue,
            reason = reason
        )
    }

    private fun thermalLevelFromStatus(status: Int): ThermalLevel = when (status) {
        PowerManager.THERMAL_STATUS_NONE, PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
        PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
        PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
        PowerManager.THERMAL_STATUS_CRITICAL,
        PowerManager.THERMAL_STATUS_EMERGENCY,
        PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLevel.CRITICAL
        else -> ThermalLevel.NONE
    }

    private fun defaultPolicy() = RenderQualityPolicy(
        thermalLevel = ThermalLevel.NONE,
        batteryLevelPercent = 100,
        isCharging = false,
        recommendedFpsCap = null,
        recommendPauseQueue = false,
        reason = "Belum diinisialisasi"
    )
}
