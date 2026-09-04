package com.visualizerstudio.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * `ui/settings/HardwareDiagnosticsScreen.kt` — dimiliki Fase 6 (folder `ui/settings/` belum
 * dimiliki fase manapun di Appendix A.2, sesuai catatan "file belum tercantum di tabel ini...
 * buat file baru bebas selama tidak menabrak path yang sudah dimiliki fase lain").
 *
 * Menampilkan: chipset terdeteksi, status termal & baterai real-time, dan hasil benchmark
 * ranking encoder hardware (skor + alasan per kandidat) dari `HardwareEncoderDetectorExtended`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareDiagnosticsScreen(
    viewModel: HardwareDiagnosticsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostik Hardware") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Chipset: ${state.chipsetVendor}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Thermal: ${state.thermalLevel}")
                    Text("Baterai: ${state.batteryPercent}% ${if (state.isCharging) "(mengisi daya)" else ""}")
                    Text("Rekomendasi: ${state.recommendation}")
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { viewModel.runBenchmark() },
                enabled = !state.isBenchmarking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isBenchmarking) "Menjalankan benchmark..." else "Jalankan Benchmark Encoder")
            }

            Spacer(Modifier.height(16.dp))

            if (state.rankedEncoders.isEmpty()) {
                Text("Belum ada hasil benchmark. Tekan tombol di atas.")
            } else {
                LazyColumn {
                    items(state.rankedEncoders) { enc ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.padding(end = 8.dp)) {
                                    Text(enc.codecName, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        enc.reasons.joinToString(" | "),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    if (enc.mustAvoid) "HINDARI" else "${enc.score}",
                                    color = if (enc.mustAvoid) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
