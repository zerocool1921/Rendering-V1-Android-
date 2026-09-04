package com.visualizerstudio.app.ui.wizard.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.visualizerstudio.app.domain.model.OverlaySpeedConfig
import com.visualizerstudio.app.domain.model.BgVisualEffect
import com.visualizerstudio.app.ui.components.SpectrumPositionPresetRow
import com.visualizerstudio.app.ui.wizard.WizardUiState
import com.visualizerstudio.app.ui.wizard.WizardViewModel
import com.visualizerstudio.app.ui.wizard.components.SpectrumLivePreview

@Composable
fun SpectrumSection(state: WizardUiState, vm: WizardViewModel) {
    Column {
        StepLabel("Langkah 9 — Spectrum")
        Row {
            Text("Aktifkan Spectrum", modifier = Modifier.padding(end = 8.dp, top = 12.dp))
            Switch(checked = state.spectrumEnabled, onCheckedChange = vm::setSpectrumEnabled)
        }

        if (!state.spectrumEnabled) {
            Text("Spectrum dimatikan — output tanpa elemen spectrum.")
            return
        }

        Row {
            Text("Manajer Multi-Spectrum", modifier = Modifier.padding(end = 8.dp, top = 12.dp))
            Switch(checked = state.multiSpectrumMode, onCheckedChange = vm::setMultiSpectrumMode)
        }

        if (state.multiSpectrumMode) {
            Row(modifier = Modifier.padding(vertical = 6.dp)) {
                state.spectrums.forEachIndexed { index, _ ->
                    FilterChip(
                        selected = state.activeSpectrumIndex == index,
                        onClick = { vm.setActiveSpectrumIndex(index) },
                        label = { Text("Spectrum ${index + 1}") },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            Row {
                Button(onClick = vm::addSpectrum, modifier = Modifier.padding(end = 8.dp)) { Text("+ Tambah") }
                Button(onClick = vm::duplicateActiveSpectrum, modifier = Modifier.padding(end = 8.dp)) { Text("Duplikat") }
                if (state.spectrums.size > 1) {
                    Button(onClick = { vm.removeSpectrumAt(state.activeSpectrumIndex) }) { Text("Hapus") }
                }
            }
        }

        val activeSpec = state.spectrums.getOrNull(state.activeSpectrumIndex) ?: return

        // Live preview (langkah 9-17 — GLSurfaceView, lihat README_FASE4.md asumsi #4)
        SpectrumLivePreview(
            spec = activeSpec,
            sampleAudioFiles = state.audioFiles,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).padding(vertical = 8.dp)
        )

        // Langkah 10 — Opasitas
        StepLabel("Langkah 10 — Opasitas Spectrum")
        Slider(
            value = activeSpec.opacity,
            onValueChange = { v -> vm.updateActiveSpectrum { it.copy(opacity = v) } },
            valueRange = 0.1f..1f
        )
        Text("${"%.2f".format(activeSpec.opacity)}")

        // Langkah 11-12 — Lebar & Tinggi
        Row {
            OutlinedTextField(
                value = activeSpec.width.toString(),
                onValueChange = { v -> v.toIntOrNull()?.let { w -> vm.updateActiveSpectrum { it.copy(width = w) } } },
                label = { Text("Lebar (px) — Langkah 11") },
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            )
            OutlinedTextField(
                value = activeSpec.height.toString(),
                onValueChange = { v -> v.toIntOrNull()?.let { h -> vm.updateActiveSpectrum { it.copy(height = h) } } },
                label = { Text("Tinggi (px) — Langkah 12") },
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
        }

        // Langkah 13 — Posisi
        StepLabel("Langkah 13 — Posisi Spectrum")
        SpectrumPositionPresetRow(onSelect = { preset ->
            vm.updateActiveSpectrum { it.copy(posX = preset.posX, posY = preset.posY) }
        })
        Row {
            OutlinedTextField(
                value = activeSpec.posX,
                onValueChange = { v -> vm.updateActiveSpectrum { it.copy(posX = v) } },
                label = { Text("Posisi X (ekspresi)") },
                modifier = Modifier.weight(1f).padding(end = 4.dp, top = 8.dp)
            )
            OutlinedTextField(
                value = activeSpec.posY,
                onValueChange = { v -> vm.updateActiveSpectrum { it.copy(posY = v) } },
                label = { Text("Posisi Y (ekspresi)") },
                modifier = Modifier.weight(1f).padding(start = 4.dp, top = 8.dp)
            )
        }
        Text("Presisi lewat Canvas Editor (Fase 5) setelah tugas dibuat.", modifier = Modifier.padding(top = 4.dp))

        // Langkah 14 — Efek Visual Latar Belakang
        StepLabel("Langkah 14 — Efek Visual Latar Belakang")
        Row {
            BgVisualEffect.entries.forEach { effect ->
                FilterChip(
                    selected = state.bgVisualEffect == effect,
                    onClick = { vm.setBgVisualEffect(effect) },
                    label = { Text(effect.name) },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }

        // Langkah 15 — Warna Garis Spectrum
        StepLabel("Langkah 15 — Warna Garis Spectrum")
        com.visualizerstudio.app.ui.components.ColorSwatchPicker(
            value = activeSpec.color,
            onValueChange = { v -> vm.updateActiveSpectrum { it.copy(color = v) } }
        )

        // Langkah 16 — Warna Background Box
        StepLabel("Langkah 16 — Warna Background Box Spectrum")
        Row {
            Text("Aktifkan background box", modifier = Modifier.padding(end = 8.dp, top = 12.dp))
            Switch(
                checked = activeSpec.bgColor != null,
                onCheckedChange = { on -> vm.updateActiveSpectrum { it.copy(bgColor = if (on) "black" else null) } }
            )
        }
        if (activeSpec.bgColor != null) {
            com.visualizerstudio.app.ui.components.ColorSwatchPicker(
                value = activeSpec.bgColor ?: "black",
                onValueChange = { v -> vm.updateActiveSpectrum { it.copy(bgColor = v) } }
            )
            // Langkah 17 — Opasitas Background Box (kondisional)
            StepLabel("Langkah 17 — Opasitas Background Box")
            Slider(
                value = activeSpec.bgOpacity,
                onValueChange = { v -> vm.updateActiveSpectrum { it.copy(bgOpacity = v) } },
                valueRange = 0.1f..1f
            )
        }

        // Spectrum Overlay Beat Effect (section 5.1b tahap 2)
        StepLabel("Beat Zoom (zoom seluruh frame mengikuti bass)")
        Row {
            Text("Aktifkan Beat Zoom", modifier = Modifier.padding(end = 8.dp, top = 12.dp))
            Switch(checked = state.useBeatZoom, onCheckedChange = vm::setBeatZoom)
        }

        StepLabel("Overlay Speed Beat Sync")
        Row {
            Text("Kecepatan overlay berdenyut mengikuti beat", modifier = Modifier.padding(end = 8.dp, top = 12.dp))
            Switch(
                checked = state.overlaySpeedConfig.active,
                onCheckedChange = { on -> vm.setOverlaySpeedBeatSync(state.overlaySpeedConfig.copy(active = on)) }
            )
        }
        if (state.overlaySpeedConfig.active) {
            Text("Pilih overlay yang berdenyut (index sesuai urutan Langkah 8):")
            Row {
                state.overlays.forEachIndexed { idx, _ ->
                    val selected = idx in state.overlaySpeedConfig.targetOverlayIndices
                    AssistChip(
                        onClick = {
                            val newIndices = if (selected) {
                                state.overlaySpeedConfig.targetOverlayIndices - idx
                            } else {
                                state.overlaySpeedConfig.targetOverlayIndices + idx
                            }
                            vm.setOverlaySpeedBeatSync(state.overlaySpeedConfig.copy(targetOverlayIndices = newIndices))
                        },
                        label = { Text("Overlay ${idx + 1}${if (selected) " ✓" else ""}") },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
        }

        // Crop (5.1e — sengaja diekspos, walau bukan bagian dari 22 langkah wizard asli;
        // kontrol visual crop handle interaktif adalah tanggung jawab Canvas Editor Fase 5,
        // di sini hanya field angka dasar sebagai fallback non-visual)
        StepLabel("Crop Latar Belakang (opsional — presisi lewat Canvas Editor)")
        Row {
            listOf("left" to state.cropConfig.left, "right" to state.cropConfig.right,
                "top" to state.cropConfig.top, "bottom" to state.cropConfig.bottom).forEach { (label, v) ->
                OutlinedTextField(
                    value = v.toString(),
                    onValueChange = { input ->
                        val n = input.toIntOrNull() ?: return@OutlinedTextField
                        val c = state.cropConfig
                        vm.setCropConfig(
                            when (label) {
                                "left" -> c.copy(left = n)
                                "right" -> c.copy(right = n)
                                "top" -> c.copy(top = n)
                                else -> c.copy(bottom = n)
                            }
                        )
                    },
                    label = { Text(label) },
                    modifier = Modifier.weight(1f).padding(2.dp)
                )
            }
        }
    }
}
