package com.visualizerstudio.app.ui.wizard.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.visualizerstudio.app.domain.model.TextAnimation
import com.visualizerstudio.app.domain.model.TextLine
import com.visualizerstudio.app.domain.model.TextOverlayConfig
import com.visualizerstudio.app.ui.components.TextPositionPresetGrid
import com.visualizerstudio.app.ui.wizard.WizardUiState
import com.visualizerstudio.app.ui.wizard.WizardViewModel

private val FONT_FAMILIES = listOf(
    "Arial", "Courier New", "Georgia", "Impact", "Times New Roman",
    "Verdana", "Comic Sans MS", "Segoe UI", "Century Gothic", "Garamond"
)

@Composable
fun TeksIdentitasSection(state: WizardUiState, vm: WizardViewModel) {
    Column {
        // Langkah 18 — Looping
        StepLabel("Langkah 18 — Looping Video Hasil Akhir")
        OutlinedTextField(
            value = state.loops.toString(),
            onValueChange = { v -> v.toIntOrNull()?.let(vm::setLoops) },
            label = { Text("Jumlah loop") }
        )

        // Langkah 19 — Intro
        StepLabel("Langkah 19 — Overlay Identitas Channel (Intro)")
        Row {
            Text("Aktifkan Intro", modifier = Modifier.padding(end = 8.dp, top = 12.dp))
            Switch(checked = state.introEnabled, onCheckedChange = vm::setIntroEnabled)
        }
        if (state.introEnabled && state.introConfig != null) {
            TextOverlayEditor(
                config = state.introConfig,
                onChange = { vm.updateIntroConfig { _ -> it } },
                update = vm::updateIntroConfig
            )
        }

        // Langkah 20 — Title
        StepLabel("Langkah 20 — Overlay Judul Lagu (Title)")
        Row {
            Text("Aktifkan Title", modifier = Modifier.padding(end = 8.dp, top = 12.dp))
            Switch(checked = state.titleEnabled, onCheckedChange = vm::setTitleEnabled)
        }
        if (state.titleEnabled) {
            Row {
                Text("Otomatis dari nama file audio", modifier = Modifier.padding(end = 8.dp, top = 12.dp))
                Switch(checked = state.titleAutoFromFilename, onCheckedChange = vm::setTitleAutoFromFilename)
            }
            if (!state.titleAutoFromFilename && state.titleConfig != null) {
                TextOverlayEditor(
                    config = state.titleConfig,
                    onChange = {},
                    update = vm::updateTitleConfig
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextOverlayEditor(
    config: TextOverlayConfig,
    onChange: (TextOverlayConfig) -> Unit,
    update: ((TextOverlayConfig) -> TextOverlayConfig) -> Unit
) {
    var fontExpanded by remember { mutableStateOf(false) }
    var lineText by remember { mutableStateOf(config.lines.firstOrNull()?.text ?: "") }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = lineText,
            onValueChange = { v ->
                lineText = v
                update { it.copy(lines = listOf(TextLine(v, it.lines.firstOrNull()?.fontSizePx ?: 48))) }
            },
            label = { Text("Teks (baris 1, maks 3 baris didukung)") }
        )

        ExposedDropdownMenuBox(expanded = fontExpanded, onExpandedChange = { fontExpanded = it }) {
            OutlinedTextField(
                value = config.fontFamily,
                onValueChange = {},
                readOnly = true,
                label = { Text("Font Family") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontExpanded) },
                modifier = Modifier.menuAnchor()
            )
            androidx.compose.material3.ExposedDropdownMenu(expanded = fontExpanded, onDismissRequest = { fontExpanded = false }) {
                FONT_FAMILIES.forEach { family ->
                    DropdownMenuItem(text = { Text(family) }, onClick = {
                        update { it.copy(fontFamily = family) }
                        fontExpanded = false
                    })
                }
            }
        }

        Row {
            Checkbox(checked = config.bold, onCheckedChange = { v -> update { it.copy(bold = v) } })
            Text("Bold", modifier = Modifier.padding(top = 12.dp, end = 12.dp))
            Checkbox(checked = config.italic, onCheckedChange = { v -> update { it.copy(italic = v) } })
            Text("Italic", modifier = Modifier.padding(top = 12.dp))
        }

        Text("Warna Font")
        com.visualizerstudio.app.ui.components.ColorSwatchPicker(
            value = config.fontColor,
            onValueChange = { v -> update { it.copy(fontColor = v) } }
        )

        Text("Opacity: ${"%.2f".format(config.opacity)}")
        Slider(value = config.opacity, onValueChange = { v -> update { it.copy(opacity = v) } }, valueRange = 0f..1f)

        Text("Posisi Teks (9 preset)")
        TextPositionPresetGrid(
            selected = config.positionPreset,
            onSelect = { pos -> update { it.copy(positionPreset = pos) } }
        )

        Text("Gaya Animasi")
        Row {
            TextAnimation.entries.forEach { anim ->
                FilterChip(
                    selected = config.animationStyle == anim,
                    onClick = { update { it.copy(animationStyle = anim) } },
                    label = { Text(anim.name.replace('_', ' ')) },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }

        Text("Durasi animasi: ${config.animationDurationSeconds} detik")
        Slider(
            value = config.animationDurationSeconds,
            onValueChange = { v -> update { it.copy(animationDurationSeconds = v) } },
            valueRange = 0.2f..5f
        )

        Text("Durasi tampil: ${config.displayDurationSeconds} detik")
        Slider(
            value = config.displayDurationSeconds,
            onValueChange = { v -> update { it.copy(displayDurationSeconds = v) } },
            valueRange = 1f..20f
        )

        Row {
            Text("Delay tampil (detik)", modifier = Modifier.padding(top = 12.dp, end = 8.dp))
            OutlinedTextField(
                value = config.delaySeconds.toString(),
                onValueChange = { v -> v.toFloatOrNull()?.let { d -> update { it.copy(delaySeconds = d) } } },
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Row {
            Checkbox(checked = config.useStroke, onCheckedChange = { v -> update { it.copy(useStroke = v) } })
            Text("Stroke/outline", modifier = Modifier.padding(top = 12.dp))
        }
        if (config.useStroke) {
            com.visualizerstudio.app.ui.components.ColorSwatchPicker(
                value = config.strokeColor,
                onValueChange = { v -> update { it.copy(strokeColor = v) } }
            )
            OutlinedTextField(
                value = config.strokeWidthPx.toString(),
                onValueChange = { v -> v.toIntOrNull()?.let { w -> update { it.copy(strokeWidthPx = w) } } },
                label = { Text("Ketebalan stroke (px)") }
            )
        }
    }
}
