package com.visualizerstudio.app.ui.shaderstudio.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.visualizerstudio.app.render.gl.ShaderCategory
import com.visualizerstudio.app.render.gl.ShaderSkeletons
import com.visualizerstudio.app.ui.shaderstudio.GenerateOutcome
import com.visualizerstudio.app.ui.shaderstudio.ShaderLivePreview
import com.visualizerstudio.app.ui.shaderstudio.ShaderStudioViewModel

@Composable
fun SpectrumWizardScreen(viewModel: ShaderStudioViewModel, onDone: () -> Unit) {
    var name by remember { mutableStateOf("Spectrum Baru") }
    var colorHex by remember { mutableStateOf(COLOR_PRESETS[0].second) }
    var style by remember { mutableStateOf(ShaderSkeletons.SpectrumStyle.BAR) }
    var barCount by remember { mutableStateOf(64f) }
    var beatZoom by remember { mutableStateOf(0.3f) }
    var glow by remember { mutableStateOf(true) }
    val outcome by viewModel.lastOutcome.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Wizard: Spectrum Custom") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama shader") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Text("Warna", style = MaterialTheme.typography.titleSmall)
            ColorPresetRow(selected = colorHex, onSelect = { colorHex = it })
            Spacer(Modifier.height(16.dp))
            Text("Gaya dasar", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShaderSkeletons.SpectrumStyle.values().forEach { s ->
                    FilterChip(selected = style == s, onClick = { style = s }, label = { Text(s.name) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Jumlah bar: ${barCount.toInt()}", style = MaterialTheme.typography.titleSmall)
            Slider(value = barCount, onValueChange = { barCount = it }, valueRange = 16f..120f, steps = 12)
            Spacer(Modifier.height(8.dp))
            Text("Sensitivitas beat-zoom: ${"%.2f".format(beatZoom)}", style = MaterialTheme.typography.titleSmall)
            Slider(value = beatZoom, onValueChange = { beatZoom = it }, valueRange = 0f..1f)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = glow, onCheckedChange = { glow = it })
                Text("Efek glow/reflection")
            }
            Spacer(Modifier.height(12.dp))
            Text("Preview langsung", style = MaterialTheme.typography.titleSmall)
            val rawGlsl by remember(colorHex, style, barCount, beatZoom, glow) {
                mutableStateOf(ShaderSkeletons.spectrumCustom(colorHex, style, barCount.toInt(), beatZoom, glow))
            }
            ShaderLivePreview(rawGlsl330 = rawGlsl, category = ShaderCategory.SPECTRUM)
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
                onClick = { viewModel.generateSpectrum(name, colorHex, style, barCount.toInt(), beatZoom, glow) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Generate & Validasi (preview di galeri)") }
            LaunchedEffect(outcome) { if (outcome is GenerateOutcome.Success) onDone() }
        }
    }
}
