package com.visualizerstudio.app.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LiveDataAdapter
import androidx.lifecycle.compose.observeAsState
import androidx.work.WorkInfo
import com.visualizerstudio.app.domain.model.RenderTask
import com.visualizerstudio.app.domain.model.TaskStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onAddNewTask: () -> Unit,
    viewModel: QueueViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    var orderedIds by remember(tasks) { mutableStateOf(tasks.map { it.id }) }
    val orderedTasks = remember(orderedIds, tasks) {
        val byId = tasks.associateBy { it.id }
        orderedIds.mapNotNull { byId[it] }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Antrian Render") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNewTask) { Text("+") }
        }
    ) { padding ->
        if (orderedTasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Belum ada tugas di antrian. Tekan + untuk membuat video baru.")
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(orderedTasks, key = { it.id }) { task ->
                QueueItemRow(
                    task = task,
                    viewModel = viewModel,
                    onMove = { fromId, toIndex ->
                        val current = orderedIds.toMutableList()
                        val fromIndex = current.indexOf(fromId)
                        if (fromIndex != -1 && toIndex in current.indices) {
                            current.add(toIndex, current.removeAt(fromIndex))
                            orderedIds = current
                        }
                    },
                    onDragEnd = { viewModel.commitReorder(orderedIds) },
                    onDelete = { viewModel.deleteTask(task.id) },
                    onRetry = { viewModel.retryTask(task.id) },
                    indexOf = { id -> orderedIds.indexOf(id) }
                )
            }
        }
    }
}

@Composable
private fun QueueItemRow(
    task: RenderTask,
    viewModel: QueueViewModel,
    onMove: (fromId: String, toIndex: Int) -> Unit,
    onDragEnd: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    indexOf: (String) -> Int
) {
    val workInfos by viewModel.workInfoLiveDataFor(task.id).observeAsState(emptyList())
    val activeWorkInfo = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING } ?: workInfos.firstOrNull()

    var dragOffsetY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 96.dp.toPx() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "Seret untuk urutkan ulang",
                modifier = Modifier
                    .padding(end = 8.dp)
                    .pointerInput(task.id) {
                        detectDragGesturesAfterLongPress(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y
                                val steps = (dragOffsetY / rowHeightPx).toInt()
                                if (steps != 0) {
                                    val newIndex = (indexOf(task.id) + steps).coerceAtLeast(0)
                                    onMove(task.id, newIndex)
                                    dragOffsetY = 0f
                                }
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { dragOffsetY = 0f }
                        )
                    }
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(task.name, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)

                StatusChip(task.status)

                if (task.status == TaskStatus.RENDERING) {
                    val percent = activeWorkInfo.readPercent()
                    LinearProgressIndicator(
                        progress = { (percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                    Text(
                        "${percent.toInt()}% — ${activeWorkInfo.readCurrentTimeSec().toInt()}s / ${activeWorkInfo.readTotalTimeSec().toInt()}s",
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            if (task.status == TaskStatus.FAILED) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Coba Lagi")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Hapus")
            }
        }
    }
}

@Composable
private fun StatusChip(status: TaskStatus) {
    val (label, color) = when (status) {
        TaskStatus.QUEUED -> "Menunggu" to Color(0xFF9E9E9E)
        TaskStatus.RENDERING -> "Merender" to Color(0xFF1E88E5)
        TaskStatus.DONE -> "Selesai" to Color(0xFF43A047)
        TaskStatus.FAILED -> "Gagal" to Color(0xFFB3261E)
    }
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(label, color = color)
    }
}
