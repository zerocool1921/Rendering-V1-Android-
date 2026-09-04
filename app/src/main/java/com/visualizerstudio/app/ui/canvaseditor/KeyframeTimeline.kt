package com.visualizerstudio.app.ui.canvaseditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

private const val TRACK_HEIGHT_DP = 28
private const val RULER_HEIGHT_DP = 20

@Composable
fun KeyframeTimeline(state: CanvasEditorUiState, viewModel: CanvasEditorViewModel) {
    val tracks = state.keyframesByElement.keys.toList()
    Column(
        Modifier
            .fillMaxWidth()
            .height((RULER_HEIGHT_DP + tracks.size * (TRACK_HEIGHT_DP + 4)).dp.coerceAtLeast(60.dp))
            .background(Color(0xFF0B0B0B))
            .padding(8.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val widthPx = constraints.maxWidth.toFloat()
            val pxPerSec = widthPx / state.timelineDurationSec

            // ruler + playhead scrub
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(RULER_HEIGHT_DP.dp)
                    .pointerInput(state.timelineDurationSec) {
                        detectTapGestures { offset -> viewModel.movePlayhead(offset.x / pxPerSec) }
                    }
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val playheadX = state.playheadSec * pxPerSec
                    drawLine(Color.Red, Offset(playheadX, 0f), Offset(playheadX, size.height + tracks.size * (TRACK_HEIGHT_DP + 4) * 3f), strokeWidth = 3f)
                }
            }

            Column(Modifier.padding(top = RULER_HEIGHT_DP.dp)) {
                tracks.forEach { elementId ->
                    val keyframes = state.keyframesByElement[elementId].orEmpty()
                    val label = state.elements.find { it.id == elementId }?.label ?: elementId
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(70.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(TRACK_HEIGHT_DP.dp)
                                .background(Color(0xFF1B1B1B))
                        ) {
                            Canvas(Modifier.matchParentSize()) {
                                // interpolasi antar-keyframe (preview pergerakan, garis penghubung)
                                val sorted = keyframes.sortedBy { it.timeSec }
                                for (i in 0 until sorted.size - 1) {
                                    val x1 = sorted[i].timeSec * pxPerSec
                                    val x2 = sorted[i + 1].timeSec * pxPerSec
                                    drawLine(Color(0xFF00E5FF), Offset(x1, size.height / 2), Offset(x2, size.height / 2), strokeWidth = 2f)
                                }
                            }
                            keyframes.forEach { kf ->
                                KeyframeDot(
                                    xSec = kf.timeSec,
                                    pxPerSec = pxPerSec,
                                    onDrag = { deltaPx -> viewModel.moveKeyframe(elementId, kf.id, kf.timeSec + deltaPx / pxPerSec) },
                                    onLongDelete = { viewModel.deleteKeyframe(elementId, kf.id) }
                                )
                            }
                            // indikator posisi playhead pada track ini (preview interpolasi bergerak)
                            interpolateKeyframes(keyframes, state.playheadSec)?.let { interp ->
                                Box(
                                    Modifier
                                        .offset { androidx.compose.ui.unit.IntOffset((interp.timeSec * pxPerSec).toInt() - 4, TRACK_HEIGHT_DP / 2 - 4) }
                                        .size(8.dp)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun KeyframeDot(xSec: Float, pxPerSec: Float, onDrag: (Float) -> Unit, onLongDelete: () -> Unit) {
    Box(
        Modifier
            .offset { androidx.compose.ui.unit.IntOffset((xSec * pxPerSec).toInt() - 6, TRACK_HEIGHT_DP / 2 - 6) }
            .size(12.dp)
            .background(Color(0xFFFFAB00), shape = androidx.compose.foundation.shape.CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, amount ->
                    change.consume()
                    onDrag(amount.x)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongDelete() })
            }
    )
}
