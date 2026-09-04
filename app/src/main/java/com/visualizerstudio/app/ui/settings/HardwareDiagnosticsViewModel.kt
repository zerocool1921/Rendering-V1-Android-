package com.visualizerstudio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visualizerstudio.app.render.ffmpeg.HardwareEncoderDetectorExtended
import com.visualizerstudio.app.render.ffmpeg.scorer.ChipsetIdentifier
import com.visualizerstudio.app.render.perf.ThermalBatteryGovernor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HardwareDiagnosticsUiState(
    val chipsetVendor: String = "Mendeteksi...",
    val rankedEncoders: List<HardwareEncoderDetectorExtended.RankedEncoder> = emptyList(),
    val thermalLevel: String = "-",
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val recommendation: String = "-",
    val isBenchmarking: Boolean = false
)

@HiltViewModel
class HardwareDiagnosticsViewModel @Inject constructor(
    private val detector: HardwareEncoderDetectorExtended,
    private val governor: ThermalBatteryGovernor
) : ViewModel() {

    private val _uiState = MutableStateFlow(HardwareDiagnosticsUiState())
    val uiState: StateFlow<HardwareDiagnosticsUiState> = _uiState.asStateFlow()

    init {
        val chipset = ChipsetIdentifier.identify()
        _uiState.value = _uiState.value.copy(chipsetVendor = chipset.vendor.name)

        viewModelScope.launch {
            governor.refreshNow()
            governor.policy.collect { policy ->
                _uiState.value = _uiState.value.copy(
                    thermalLevel = policy.thermalLevel.name,
                    batteryPercent = policy.batteryLevelPercent,
                    isCharging = policy.isCharging,
                    recommendation = policy.reason
                )
            }
        }
    }

    fun runBenchmark(targetWidth: Int = 1920, targetHeight: Int = 1080, targetFps: Int = 30) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBenchmarking = true)
            val ranked = detector.rankAvailableEncoders(targetWidth, targetHeight, targetFps)
            _uiState.value = _uiState.value.copy(rankedEncoders = ranked, isBenchmarking = false)
        }
    }
}
