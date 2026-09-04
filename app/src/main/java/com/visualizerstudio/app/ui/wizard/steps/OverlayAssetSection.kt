package com.visualizerstudio.app.ui.wizard.steps

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.visualizerstudio.app.domain.model.OverlayAssetConfig
import com.visualizerstudio.app.ui.wizard.WizardUiState
import com.visualizerstudio.app.ui.wizard.WizardViewModel

@Composable
fun OverlayAssetSection(state: WizardUiState, vm: WizardViewModel) {
    val assetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { vm.addOverlay(OverlayAssetConfig(fileUri = it)) }
    }

    Column {
        StepLabel("Langkah 8 — Efek Overlay Video/Gambar (opsional, bisa lebih dari satu)")
        Text("Overlay ke-2 dst tampil setelah overlay sebelumnya dengan transisi masuk halus.")

        state.overlays.forEachIndexed { index, overlay ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row {
                        Text(if (index == 0) "Overlay utama" else "Overlay #${index + 1}", modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.removeOverlayAt(index) }) {
                            androidx.compose.material3.Icon(Icons.Filled.Delete, contentDescription = "Hapus")
                        }
                    }

                    Text("Opacity: ${"%.2f".format(overlay.opacity)}")
                    Slider(
                        value = overlay.opacity,
                        onValueChange = { vm.updateOverlayAt(index, overlay.copy(opacity = it)) },
                        valueRange = 0f..1f
                    )

                    Text("Fade-in: ${overlay.fadeInSeconds} detik")
                    Slider(
                        value = overlay.fadeInSeconds,
                        onValueChange = { vm.updateOverlayAt(index, overlay.copy(fadeInSeconds = it)) },
                        valueRange = 0f..10f
                    )

                    Row {
                        Text("Loop", modifier = Modifier.padding(end = 8.dp))
                        Switch(checked = overlay.loop, onCheckedChange = { vm.updateOverlayAt(index, overlay.copy(loop = it)) })
                    }

                    OutlinedTextField(
                        value = overlay.delaySeconds.toString(),
                        onValueChange = { v -> v.toFloatOrNull()?.let { vm.updateOverlayAt(index, overlay.copy(delaySeconds = it)) } },
                        label = { Text("Delay tampil (detik)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )

                    Row {
                        Checkbox(
                            checked = overlay.autoLumaKey,
                            onCheckedChange = { vm.updateOverlayAt(index, overlay.copy(autoLumaKey = it)) }
                        )
                        Text("Auto luma-key (area gelap otomatis transparan)", modifier = Modifier.padding(top = 12.dp))
                    }

                    Row {
                        OutlinedTextField(
                            value = overlay.shearX.toString(),
                            onValueChange = { v -> v.toFloatOrNull()?.let { vm.updateOverlayAt(index, overlay.copy(shearX = it)) } },
                            label = { Text("Shear X") },
                            modifier = Modifier.weight(1f).padding(end = 4.dp)
                        )
                        OutlinedTextField(
                            value = overlay.shearY.toString(),
                            onValueChange = { v -> v.toFloatOrNull()?.let { vm.updateOverlayAt(index, overlay.copy(shearY = it)) } },
                            label = { Text("Shear Y") },
                            modifier = Modifier.weight(1f).padding(start = 4.dp)
                        )
                    }
                }
            }
        }

        Button(
            onClick = { assetPicker.launch(arrayOf("video/*", "image/*")) },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(if (state.overlays.isEmpty()) "Tambah Overlay (opsional, lewati kalau tidak perlu)" else "Tambah Overlay Lagi")
        }
    }
}
