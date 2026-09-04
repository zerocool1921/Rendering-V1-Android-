package com.visualizerstudio.app.ui.wizard.steps

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.visualizerstudio.app.domain.model.MediaMode
import com.visualizerstudio.app.domain.model.Resolution
import com.visualizerstudio.app.ui.wizard.WizardUiState
import com.visualizerstudio.app.ui.wizard.WizardViewModel

@Composable
fun DasarSection(state: WizardUiState, vm: WizardViewModel) {
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.setMediaFiles(uris)
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.setAudioFiles(uris)
    }
    val outputPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(vm::setOutputFolder)
    }

    Column {
        // Langkah 1 — Nama Tugas
        OutlinedTextField(
            value = state.taskName,
            onValueChange = vm::setTaskName,
            label = { Text("Nama Tugas") },
            isError = state.taskName.isBlank(),
            supportingText = { if (state.taskName.isBlank()) Text("Wajib diisi") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )

        // Langkah 2 — File Media
        StepLabel("Langkah 2 — File Media (visual)")
        Button(onClick = { mediaPicker.launch(arrayOf("video/*", "image/*")) }) {
            Text("Pilih Media (${state.mediaFiles.size} terpilih)")
        }
        if (state.mediaFiles.isEmpty()) ErrorHint("Minimal 1 file media")

        // Langkah 3 — File Audio
        StepLabel("Langkah 3 — File Audio")
        Button(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
            Text("Pilih Audio (${state.audioFiles.size} terpilih)")
        }
        if (state.audioFiles.isEmpty()) ErrorHint("Minimal 1 file audio (akan digabung otomatis)")

        // Langkah 4 — Folder Output
        StepLabel("Langkah 4 — Folder Output")
        Button(onClick = { outputPicker.launch(null) }) {
            Text(if (state.outputFolder != null) "Folder dipilih ✓" else "Pilih Folder Output")
        }
        if (state.outputFolder == null) ErrorHint("Wajib pilih folder output")

        // Langkah 5 — Mode Distribusi Media
        StepLabel("Langkah 5 — Mode Distribusi Media")
        Row {
            MediaMode.entries.forEach { mode ->
                Row(modifier = Modifier.padding(end = 16.dp)) {
                    RadioButton(selected = state.mediaMode == mode, onClick = { vm.setMediaMode(mode) })
                    Text(
                        text = if (mode == MediaMode.EQUAL_SPLIT) "Equal Split" else "Per Track",
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp)
                    )
                }
            }
        }

        // Langkah 6 — Resolusi
        StepLabel("Langkah 6 — Resolusi Video")
        Row {
            Resolution.entries.forEach { res ->
                FilterChip(
                    selected = state.resolution == res,
                    onClick = { vm.setResolution(res) },
                    label = { Text(res.name.removePrefix("R")) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        // Langkah 7 — FPS
        StepLabel("Langkah 7 — Frame Rate (FPS)")
        Row {
            listOf(24, 30, 60).forEach { fps ->
                FilterChip(
                    selected = state.fps == fps,
                    onClick = { vm.setFps(fps) },
                    label = { Text("$fps fps") },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}

@Composable
internal fun StepLabel(text: String) {
    Text(text, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
}

@Composable
internal fun ErrorHint(text: String) {
    Text(text, color = androidx.compose.ui.graphics.Color(0xFFB3261E), modifier = Modifier.padding(top = 4.dp))
}
