package com.visualizerstudio.app.ui.canvaseditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.min

private const val HANDLE_SIZE = 18f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasEditorScreen(
    viewModel: CanvasEditorViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Canvas Editor — ${state.taskName}") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Tutup") } },
                actions = {
                    TextButton(onClick = { viewModel.savePositions() }) { Text("Simpan Posisi") }
                    Button(onClick = { viewModel.renderPreview() }, enabled = !state.isRenderingPreview) {
                        Text(if (state.isRenderingPreview) "Rendering…" else "Render Preview")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.weight(1f)) {
                Box(Modifier.weight(2f).fillMaxHeight().padding(8.dp)) {
                    CanvasArea(state, viewModel)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PropertyPanel(state, viewModel)
                }
            }
            SnapControlsRow(state, viewModel)
            KeyframeTimeline(state, viewModel)
            RenderPreviewStatus(state)
        }
    }
}

@Composable
private fun CanvasArea(state: CanvasEditorUiState, viewModel: CanvasEditorViewModel) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF111111))
    ) {
        val canvasPxW = constraints.maxWidth.toFloat()
        val canvasPxH = constraints.maxHeight.toFloat()
        val scale = min(canvasPxW / state.canvasWidth, canvasPxH / state.canvasHeight)
        val offsetX = (canvasPxW - state.canvasWidth * scale) / 2f
        val offsetY = (canvasPxH - state.canvasHeight * scale) / 2f

        // background frame video (di fase nyata: thumbnail media asli via ExoPlayer/Media3 frame grab)
        Box(
            Modifier
                .align(Alignment.Center)
                .size((state.canvasWidth * scale).dp, (state.canvasHeight * scale).dp)
                .background(Color(0xFF1E1E1E))
        )

        // guide safe-area
        if (state.snap.snapToSafeArea) {
            val m = state.snap.safeAreaMarginPx * scale
            Canvas(Modifier.matchParentSize()) {
                drawRect(
                    color = Color.Yellow.copy(alpha = 0.25f),
                    topLeft = Offset(offsetX + m, offsetY + m),
                    size = androidx.compose.ui.geometry.Size(
                        state.canvasWidth * scale - 2 * m,
                        state.canvasHeight * scale - 2 * m
                    ),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                )
            }
        }

        state.elements.forEach { el ->
            ElementGizmo(
                el = el,
                selected = el.id == state.selectedElementId,
                scale = scale,
                canvasOffset = Offset(offsetX, offsetY),
                onSelect = { viewModel.selectElement(el.id) },
                onDrag = { dx, dy -> viewModel.onGizmoDrag(el.id, dx / scale, dy / scale) },
                onResize = { dw, dh -> viewModel.onGizmoResize(el.id, dw / scale, dh / scale) },
                onShear = { dsx, dsy -> viewModel.onGizmoShear(el.id, dsx, dsy) },
                onCropDrag = { side, deltaPx -> viewModel.onCropHandleDrag(el.id, side, (deltaPx / scale).toInt()) }
            )
        }
    }
}

@Composable
private fun ElementGizmo(
    el: CanvasElementUi,
    selected: Boolean,
    scale: Float,
    canvasOffset: Offset,
    onSelect: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    onShear: (Float, Float) -> Unit,
    onCropDrag: (GizmoInteraction, Float) -> Unit
) {
    val left = canvasOffset.x + el.x * scale
    val top = canvasOffset.y + el.y * scale
    val w = el.width * scale
    val h = el.height * scale
    val borderColor = if (selected) Color(0xFF00E5FF) else Color(0xFFFFAB00)

    Box(
        Modifier
            .offset { androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()) }
            .size((w / androidx.compose.ui.platform.LocalDensity.current.density).dp, (h / androidx.compose.ui.platform.LocalDensity.current.density).dp)
    ) {
        // badan gizmo — drag posisi real-time (update x/y tiap delta gesture, bukan cuma di akhir)
        Canvas(
            Modifier
                .matchParentSize()
                .pointerInput(el.id) {
                    detectDragGestures(onDragStart = { onSelect() }) { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
        ) {
            rotate(el.rotationDeg) {
                drawRect(
                    color = borderColor.copy(alpha = 0.15f),
                    size = size
                )
                drawRect(color = borderColor, size = size, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                // representasi visual shear: gambar jajar-genjang mengikuti shearX/shearY (bukan cuma angka)
                if (el.shearX != 0f || el.shearY != 0f) {
                    val shearOffsetX = el.shearY * size.width * 0.3f
                    val shearOffsetY = el.shearX * size.height * 0.3f
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(shearOffsetX, 0f)
                        lineTo(size.width + shearOffsetX, 0f)
                        lineTo(size.width - shearOffsetX, size.height)
                        lineTo(-shearOffsetX, size.height)
                        close()
                    }
                    drawPath(path, color = borderColor.copy(alpha = 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                }
            }
        }

        if (selected) {
            // handle resize (sudut kanan-bawah)
            GizmoHandle(
                modifier = Modifier.align(Alignment.BottomEnd),
                color = Color(0xFF00E5FF)
            ) { dx, dy -> onResize(dx, dy) }

            // handle shear (atas-tengah menggeser shearX, kiri-tengah menggeser shearY)
            GizmoHandle(modifier = Modifier.align(Alignment.TopCenter), color = Color(0xFFFF4081)) { dx, _ ->
                onShear(dx / 200f, 0f)
            }
            GizmoHandle(modifier = Modifier.align(Alignment.CenterStart), color = Color(0xFFFF4081)) { _, dy ->
                onShear(0f, dy / 200f)
            }

            if (el.supportsCrop) {
                CropHandle(Modifier.align(Alignment.TopStart), GizmoInteraction.CROP_TOP, onCropDrag)
                CropHandle(Modifier.align(Alignment.BottomStart), GizmoInteraction.CROP_LEFT, onCropDrag)
                CropHandle(Modifier.align(Alignment.TopEnd), GizmoInteraction.CROP_RIGHT, onCropDrag)
                CropHandle(Modifier.align(Alignment.BottomEnd), GizmoInteraction.CROP_BOTTOM, onCropDrag)
            }
        }
    }
}

@Composable
private fun GizmoHandle(modifier: Modifier, color: Color, onDrag: (Float, Float) -> Unit) {
    Box(
        modifier
            .size(HANDLE_SIZE.dp)
            .background(color)
            .pointerInput(Unit) {
                detectDragGestures { change, amount ->
                    change.consume()
                    onDrag(amount.x, amount.y)
                }
            }
    )
}

@Composable
private fun CropHandle(modifier: Modifier, side: GizmoInteraction, onCropDrag: (GizmoInteraction, Float) -> Unit) {
    Box(
        modifier
            .size((HANDLE_SIZE * 0.7f).dp)
            .background(Color(0xFFFFD600))
            .pointerInput(side) {
                detectDragGestures { change, amount ->
                    change.consume()
                    val delta = if (side == GizmoInteraction.CROP_LEFT || side == GizmoInteraction.CROP_RIGHT) amount.x else amount.y
                    onCropDrag(side, delta)
                }
            }
    )
}

@Composable
private fun SnapControlsRow(state: CanvasEditorUiState, viewModel: CanvasEditorViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Snap grid", style = MaterialTheme.typography.labelMedium)
        Switch(checked = state.snap.snapToGrid, onCheckedChange = { viewModel.toggleSnapToGrid(it) })
        Spacer(Modifier.width(12.dp))
        Text("Snap safe-area", style = MaterialTheme.typography.labelMedium)
        Switch(checked = state.snap.snapToSafeArea, onCheckedChange = { viewModel.toggleSnapToSafeArea(it) })
    }
}

@Composable
private fun RenderPreviewStatus(state: CanvasEditorUiState) {
    if (state.isRenderingPreview) {
        val pct = state.previewProgress?.percent ?: 0f
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
            Text("Render preview 30 detik… ${pct.toInt()}%", style = MaterialTheme.typography.bodySmall)
        }
    }
    state.previewResultPath?.let {
        Text("Preview selesai: $it", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
    state.previewError?.let {
        Text("Render gagal: $it", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
    }
}
