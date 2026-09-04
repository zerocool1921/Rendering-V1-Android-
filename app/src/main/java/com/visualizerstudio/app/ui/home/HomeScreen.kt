package com.visualizerstudio.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCreateNewTask: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenPresets: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val summary by viewModel.queueSummary.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Music Visualizer Studio") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Ringkasan Antrian", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text("Menunggu: ${summary.queuedCount}  •  Merender: ${summary.renderingCount}")
                    Text("Selesai: ${summary.doneCount}  •  Gagal: ${summary.failedCount}")
                    Button(onClick = onOpenQueue, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Lihat Antrian (${summary.total})")
                    }
                }
            }

            Button(
                onClick = onCreateNewTask,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text("+ Buat Video Baru")
            }

            Button(
                onClick = onOpenPresets,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kelola Preset")
            }
        }
    }
}
