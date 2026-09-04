package com.visualizerstudio.app.ui.wizard.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.visualizerstudio.app.domain.model.ReversePlayConfig
import com.visualizerstudio.app.domain.model.SlowMotionGroupConfig
import com.visualizerstudio.app.ui.wizard.WizardUiState
import com.visualizerstudio.app.ui.wizard.WizardViewModel

@Composable
fun GerakLanjutanSection(state: WizardUiState, vm: WizardViewModel) {
    val slowMotion = state.slowMotionConfig

    Column {
        // Langkah 21 — Slow Motion
        StepLabel("Langkah 21 — Slow Motion (Gerak Lambat GPU)")

        Text("Grup Visual Utama")
        Row {
            Text("Aktif", modifier = Modifier.padding(end = 8.dp, top = 12.dp))
            Switch(
                checked = slowMotion.visualGroup?.active ?: false,
                onCheckedChange = { on ->
                    vm.setSlowMotionConfig(
                        slowMotion.copy(
                            visualGroup = SlowMotionGroupConfig(active = on, speed = slowMotion.visualGroup?.speed ?: 0.5f)
                        )
                    )
                }
            )
        }
        if (slowMotion.visualGroup?.active == true) {
            Text("Kecepatan: ${"%.2f".format(slowMotion.visualGroup.speed)}x")
            Slider(
                value = slowMotion.visualGroup.speed,
                onValueChange = { v -> vm.setSlowMotionConfig(slowMotion.copy(visualGroup = slowMotion.visualGroup.copy(speed = v))) },
                valueRange = 0.1f..1f
            )
        }

        Text("Grup Overlay")
        Row {
            Text("Aktif", modifier = Modifier.padding(end = 8.dp, top = 12.dp))
            Switch(
                checked = slowMotion.overlayGroup?.active ?: false,
                onCheckedChange = { on ->
                    vm.setSlowMotionConfig(
                        slowMotion.copy(
                            overlayGroup = SlowMotionGroupConfig(active = on, speed = slowMotion.overlayGroup?.speed ?: 0.5f)
                        )
                    )
                }
            )
        }
        if (slowMotion.overlayGroup?.active == true) {
            Text("Kecepatan: ${"%.2f".format(slowMotion.overlayGroup.speed)}x")
            Slider(
                value = slowMotion.overlayGroup.speed,
                onValueChange = { v -> vm.setSlowMotionConfig(slowMotion.copy(overlayGroup = slowMotion.overlayGroup.copy(speed = v))) },
                valueRange = 0.1f..1f
            )
        }

        // Langkah 22 — Reverse Play
        StepLabel("Langkah 22 — Reverse Play (Putar Terbalik)")
        Row {
            Text("Aktifkan untuk video utama", modifier = Modifier.padding(end = 8.dp, top = 12.dp))
            Switch(
                checked = state.reversePlayConfig.active,
                onCheckedChange = { vm.setReversePlayConfig(ReversePlayConfig(active = it)) }
            )
        }

        // Simpan sebagai preset (opsional, setelah langkah 22 selesai)
        StepLabel("Simpan sebagai Preset (opsional)")
        Row {
            androidx.compose.material3.Checkbox(checked = state.saveAsPresetChecked, onCheckedChange = vm::setSaveAsPreset)
            Text("Simpan gaya font & keyframe tugas ini sebagai preset baru", modifier = Modifier.padding(top = 12.dp))
        }
        if (state.saveAsPresetChecked) {
            androidx.compose.material3.OutlinedTextField(
                value = state.presetName,
                onValueChange = vm::setPresetName,
                label = { Text("Nama Preset") },
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
