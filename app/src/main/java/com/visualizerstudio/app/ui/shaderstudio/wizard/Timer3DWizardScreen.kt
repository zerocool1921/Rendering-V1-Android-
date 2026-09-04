package com.visualizerstudio.app.ui.shaderstudio.wizard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.visualizerstudio.app.render.gl.ShaderCategory
import com.visualizerstudio.app.render.gl.ShaderSkeletons
import com.visualizerstudio.app.ui.shaderstudio.GenerateOutcome
import com.visualizerstudio.app.ui.shaderstudio.ShaderLivePreview
import com.visualizerstudio.app.ui.shaderstudio.ShaderStudioViewModel

internal val COLOR_PRESETS = listOf(
    "Merah" to "#E53935", "Kuning" to "#FDD835", "Hijau" to "#43A047", "Biru" to "#1E88E5",
    "Emas" to "#D4AF37", "Hitam" to "#000000", "Abu" to "#9E9E9E", "Toska" to "#00ACC1",
    "Oranye" to "#FB8C00", "Pink" to "#EC407A", "Putih" to "#FFFFFF"
)

@Composable
fun Timer3DWizardScreen(viewModel: ShaderStudioViewModel, onDone: () -> Unit) {
    var name by remember { mutableStateOf("Timer 3D Baru") }
    var colorHex by remember { mutableStateOf(COLOR_PRESETS[0].second) }
    var phases by remember { mutableStateOf(listOf("Ready" to "", "3" to "", "2" to "", "1" to "", "GO" to "")) }
    val outcome by viewModel.lastOutcome.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Wizard: Timer Countdown 3D") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama shader") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Text("Warna angka", style = MaterialTheme.typography.titleSmall)
            ColorPresetRow(selected = colorHex, onSelect = { colorHex = it })
            Spacer(Modifier.height(16.dp))
            Text("Fase teks intro bergantian (opsional, maks 8)", style = MaterialTheme.typography.titleSmall)
            Text(
                "Durasi tiap fase dihitung otomatis: kata pendek/angka = 1.0 detik, kalimat panjang = 1.5–4.0 detik.",
                style = MaterialTheme.typography.bodySmall
            )
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 260.dp)) {
                items(phases.size) { idx ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = phases[idx].first,
                            onValueChange = { v -> phases = phases.toMutableList().also { it[idx] = v to phases[idx].second } },
                            label = { Text("Baris 1") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(4.dp))
                        OutlinedTextField(
                            value = phases[idx].second,
                            onValueChange = { v -> phases = phases.toMutableList().also { it[idx] = phases[idx].first to v } },
                            label = { Text("Baris 2 (opsional)") },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { phases = phases.toMutableList().also { it.removeAt(idx) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Hapus fase")
                        }
                    }
                }
            }
            if (phases.size < 8) {
                TextButton(onClick = { phases = phases + ("" to "") }) {
                    Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Tambah fase")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Preview langsung", style = MaterialTheme.typography.titleSmall)
            val rawGlsl by remember(colorHex, phases) {
                mutableStateOf(
                    ShaderSkeletons.timer3D(
                        colorHex,
                        phases.filter { it.first.isNotBlank() }.map { ShaderSkeletons.TimerPhase(it.first, it.second) }
                    )
                )
            }
            ShaderLivePreview(rawGlsl330 = rawGlsl, category = ShaderCategory.TIMER_3D)
            Spacer(Modifier.height(12.dp))
            if (outcome is GenerateOutcome.Rejected) {
                val r = (outcome as GenerateOutcome.Rejected).result
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Validasi gagal:", style = MaterialTheme.typography.titleSmall)
                        r.errors.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = { viewModel.generateTimer3D(name, colorHex, phases.filter { it.first.isNotBlank() }) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Generate & Validasi") }
            LaunchedEffect(outcome) {
                if (outcome is GenerateOutcome.Success) onDone()
            }
        }
    }
}

@Composable
internal fun ColorPresetRow(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        COLOR_PRESETS.forEach { (label, hex) ->
            FilterChip(selected = selected == hex, onClick = { onSelect(hex) }, label = { Text(label) })
        }
    }
}
