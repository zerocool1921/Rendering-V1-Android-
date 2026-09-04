package com.visualizerstudio.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.visualizerstudio.app.domain.model.RenderTask
import com.visualizerstudio.app.domain.model.TaskStatus
import com.visualizerstudio.app.domain.repository.RenderTaskRepositoryImpl
import com.visualizerstudio.app.ui.theme.VisualizerStudioTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Entry point skeleton Fase 1 — layar dashboard sesungguhnya (`ui/home/**`) adalah milik
 * Fase 4 (lihat Appendix A.2). Composable di sini HANYA pembuktian bahwa fondasi (Room +
 * Hilt + DataStore) tersambung end-to-end, BUKAN implementasi UI final.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var renderTaskRepository: RenderTaskRepositoryImpl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VisualizerStudioTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Fase1FoundationCheckScreen(
                        onInsertSample = { onDone ->
                            lifecycleScope.launch {
                                val sample = RenderTask(
                                    id = UUID.randomUUID().toString(),
                                    name = "Contoh Tugas Fase 1",
                                    mediaFiles = emptyList(),
                                    audioFiles = emptyList(),
                                    outputFolder = Uri.EMPTY,
                                    status = TaskStatus.QUEUED
                                )
                                renderTaskRepository.upsertTask(sample)
                                val readBack = renderTaskRepository.getTaskById(sample.id)
                                onDone(readBack != null)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Fase1FoundationCheckScreen(
    onInsertSample: ((Boolean) -> Unit) -> Unit
) {
    var resultText by remember { mutableStateOf("Belum diuji") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Music Visualizer Studio — Fase 1: Fondasi",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Room DB, Hilt DI, dan DataStore sudah tersambung. " +
                "Tombol di bawah menulis satu RenderTask contoh lalu membacanya kembali " +
                "untuk membuktikan alur data domain -> Room -> domain berjalan.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = {
            onInsertSample { success ->
                resultText = if (success) {
                    "OK — RenderTask berhasil ditulis & dibaca kembali dari Room."
                } else {
                    "GAGAL — RenderTask tidak ditemukan setelah ditulis."
                }
            }
        }) {
            Text("Uji Simpan RenderTask ke Room")
        }
        Text(text = "Status: $resultText", style = MaterialTheme.typography.bodyMedium)
    }
}
