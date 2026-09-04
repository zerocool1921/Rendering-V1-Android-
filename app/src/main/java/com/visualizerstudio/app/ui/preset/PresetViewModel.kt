package com.visualizerstudio.app.ui.preset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visualizerstudio.app.domain.model.Preset
import com.visualizerstudio.app.domain.repository.PresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PresetViewModel @Inject constructor(
    private val presetRepository: PresetRepository
) : ViewModel() {

    val presets: StateFlow<List<Preset>> = presetRepository.observeAllPresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deletePreset(id: String) {
        viewModelScope.launch { presetRepository.deletePreset(id) }
    }
}
