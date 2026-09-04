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

private val KIND_LABELS = mapOf(
    ShaderSkeletons.OverlayEffectKind.RAIN_SNOW_DUST to "Hujan / Salju / Debu (arah turun tetap — Overlay)",
    ShaderSkeletons.OverlayEffectKind.FIRE to "Api (arah naik — Effect)",
    ShaderSkeletons.OverlayEffectKind.FIREFLY to "Kunang-kunang (melayang bebas — Effect)",
    ShaderSkeletons.OverlayEffectKind.EXPLOSION_PARTICLE to "Ledakan / Partikel radial — Effect"
)

@Composable
fun OverlayLoopWizardScreen(viewModel: ShaderStudioViewModel, onDone: () -> Unit) {
    var name by remember { mutableStateOf("Overlay Loop Baru") }
    var colorHex by remember { mutableStateOf(COLOR_PRESETS[0].second) }
    var kind by remember { mutableStateOf(ShaderSkeletons.OverlayEffectKind.RAIN_SNOW_DUST) }
    var density by remember { mutableStateOf(40f) }
    var loopDuration by remember { mutableStateOf(120f) }
    val outcome by viewModel.lastOutcome.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Wizard: Overlay Effect Loop") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama shader") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Text("Warna", style = MaterialTheme.typography.titleSmall)
            ColorPresetRow(selected = colorHex, onSelect = { colorHex = it })
            Spacer(Modifier.height(16.dp))
            Text("Jenis efek & arah gerak", style = MaterialTheme.typography.titleSmall)
            Column {
                KIND_LABELS.forEach { (k, label) ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = kind == k, onClick = { kind = k })
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Kepadatan partikel: ${density.toInt()}", style = MaterialTheme.typography.titleSmall)
            Slider(value = density, onValueChange = { density = it }, valueRange = 10f..120f)
            Spacer(Modifier.height(8.dp))
            Text("Durasi loop: ${loopDuration.toInt()} detik", style = MaterialTheme.typography.titleSmall)
            Slider(value = loopDuration, onValueChange = { loopDuration = it }, valueRange = 30f..240f)
            Spacer(Modifier.height(12.dp))
            Text("Preview langsung", style = MaterialTheme.typography.titleSmall)
            val rawGlsl by remember(colorHex, kind, density, loopDuration) {
                mutableStateOf(ShaderSkeletons.overlayEffectLoop(colorHex, kind, density, loopDuration))
            }
            ShaderLivePreview(rawGlsl330 = rawGlsl, category = ShaderCategory.OVERLAY_LOOP)
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
                onClick = { viewModel.generateOverlayLoop(name, colorHex, kind, density, loopDuration) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Generate & Validasi") }
            LaunchedEffect(outcome) { if (outcome is GenerateOutcome.Success) onDone() }
        }
    }
}
