package com.visualizerstudio.app.ui.preset

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.visualizerstudio.app.domain.model.Preset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetScreen(
    onApplyToNewTask: (Preset) -> Unit,
    viewModel: PresetViewModel = hiltViewModel()
) {
    val presets by viewModel.presets.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Preset") }) }) { padding ->
        if (presets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Belum ada preset. Simpan preset dari akhir Wizard Buat Tugas.")
            }
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp)
        ) {
            items(presets, key = { it.id }) { preset ->
                PresetCard(
                    preset = preset,
                    onApply = { onApplyToNewTask(preset) },
                    onDelete = { viewModel.deletePreset(preset.id) }
                )
            }
        }
    }
}

@Composable
private fun PresetCard(preset: Preset, onApply: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.padding(6.dp)) {
        Column {
            // Thumbnail visual nyata (hasil render mini) adalah tanggung jawab Fase 5
            // (galeri butuh render engine GL/FFmpeg). Placeholder di sini jujur berupa
            // ringkasan teks font/warna, bukan gambar generik yang berpura-pura jadi render.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .background(Color(0xFFF2F0F4)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = preset.fontSettings?.fontFamily ?: "—",
                    color = Color(0xFF6750A4)
                )
            }
            Text(preset.name, modifier = Modifier.padding(8.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            androidx.compose.foundation.layout.Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                TextButton(onClick = onApply) { Text("Terapkan") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Hapus preset")
                }
            }
        }
    }
}
