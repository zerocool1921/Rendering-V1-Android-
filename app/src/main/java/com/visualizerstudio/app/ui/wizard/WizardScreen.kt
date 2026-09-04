package com.visualizerstudio.app.ui.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.visualizerstudio.app.ui.wizard.steps.DasarSection
import com.visualizerstudio.app.ui.wizard.steps.GerakLanjutanSection
import com.visualizerstudio.app.ui.wizard.steps.OverlayAssetSection
import com.visualizerstudio.app.ui.wizard.steps.SpectrumSection
import com.visualizerstudio.app.ui.wizard.steps.TeksIdentitasSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScreen(
    onTaskSubmitted: (taskId: String) -> Unit,
    onClose: () -> Unit,
    viewModel: WizardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.submittedTaskId) {
        state.submittedTaskId?.let { onTaskSubmitted(it) }
    }
    LaunchedEffect(state.submitError) {
        state.submitError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.resetSubmitError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buat Tugas Render") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tutup")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            WizardStepIndicator(
                sections = WizardSection.entries,
                current = state.currentSection,
                isValid = { state.isSectionValid(it) },
                onSelect = viewModel::goToSection
            )
            LinearProgressIndicator(
                progress = { (state.currentSectionIndex + 1) / WizardSection.entries.size.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )

            Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                when (state.currentSection) {
                    WizardSection.DASAR -> DasarSection(state, viewModel)
                    WizardSection.OVERLAY_ASSET -> OverlayAssetSection(state, viewModel)
                    WizardSection.SPECTRUM -> SpectrumSection(state, viewModel)
                    WizardSection.TEKS_IDENTITAS -> TeksIdentitasSection(state, viewModel)
                    WizardSection.GERAK_LANJUTAN -> GerakLanjutanSection(state, viewModel)
                }
            }

            WizardBottomBar(
                state = state,
                onBack = viewModel::goBack,
                onNext = viewModel::goNext,
                onSubmit = viewModel::submit
            )
        }
    }
}

@Composable
private fun WizardStepIndicator(
    sections: List<WizardSection>,
    current: WizardSection,
    isValid: (WizardSection) -> Boolean,
    onSelect: (WizardSection) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        sections.forEach { section ->
            val isCurrent = section == current
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = section.title,
                    color = when {
                        isCurrent -> androidx.compose.ui.graphics.Color(0xFF6750A4)
                        isValid(section) -> androidx.compose.ui.graphics.Color(0xFF43A047)
                        else -> androidx.compose.ui.graphics.Color.Gray
                    },
                    modifier = Modifier.padding(2.dp)
                )
            }
        }
    }
}

@Composable
private fun WizardBottomBar(
    state: WizardUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSubmit: () -> Unit
) {
    val isLastSection = state.currentSection == WizardSection.entries.last()
    val currentValid = state.isSectionValid(state.currentSection)

    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        OutlinedButton(onClick = onBack, enabled = state.currentSectionIndex > 0) {
            Text("Kembali")
        }
        if (isLastSection) {
            Button(onClick = onSubmit, enabled = state.isAllValid && !state.isSubmitting) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text(if (state.isSubmitting) "Menyimpan..." else "Simpan & Antre")
            }
        } else {
            Button(onClick = onNext, enabled = currentValid) {
                Text("Lanjut")
            }
        }
    }
}
