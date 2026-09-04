package com.visualizerstudio.app.ui.canvaseditor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun PropertyPanel(state: CanvasEditorUiState, viewModel: CanvasEditorViewModel) {
    val el = state.elements.find { it.id == state.selectedElementId }
    Column(Modifier.fillMaxHeight().padding(12.dp).verticalScroll(rememberScrollState())) {
        Text("Properti", style = MaterialTheme.typography.titleMedium)
        if (el == null) {
            Spacer(Modifier.height(8.dp))
            Text("Pilih elemen di kanvas untuk edit posisi.", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        Spacer(Modifier.height(8.dp))
        Text(el.label, style = MaterialTheme.typography.titleSmall)

        // Live sync arah B: ketik angka manual di sini -> gizmo di kanvas ikut pindah (dua arah nyata)
        NumberField("X", el.x) { v -> viewModel.onPropertyPanelEdit(el.id, x = v) }
        NumberField("Y", el.y) { v -> viewModel.onPropertyPanelEdit(el.id, y = v) }
        NumberField("Lebar", el.width) { v -> viewModel.onPropertyPanelEdit(el.id, width = v) }
        NumberField("Tinggi", el.height) { v -> viewModel.onPropertyPanelEdit(el.id, height = v) }
        NumberField("Shear X", el.shearX) { v -> viewModel.onPropertyPanelEdit(el.id, shearX = v) }
        NumberField("Shear Y", el.shearY) { v -> viewModel.onPropertyPanelEdit(el.id, shearY = v) }

        if (el.supportsCrop) {
            Spacer(Modifier.height(12.dp))
            Text("Crop (5.1e — diaktifkan penuh di Android)", style = MaterialTheme.typography.titleSmall)
            val crop = el.crop ?: com.visualizerstudio.app.domain.model.CropConfig()
            NumberField("Crop Kiri", crop.left.toFloat()) { v -> viewModel.onCropHandleDrag(el.id, GizmoInteraction.CROP_LEFT, (v - crop.left).toInt()) }
            NumberField("Crop Kanan", crop.right.toFloat()) { v -> viewModel.onCropHandleDrag(el.id, GizmoInteraction.CROP_RIGHT, (v - crop.right).toInt()) }
            NumberField("Crop Atas", crop.top.toFloat()) { v -> viewModel.onCropHandleDrag(el.id, GizmoInteraction.CROP_TOP, (v - crop.top).toInt()) }
            NumberField("Crop Bawah", crop.bottom.toFloat()) { v -> viewModel.onCropHandleDrag(el.id, GizmoInteraction.CROP_BOTTOM, (v - crop.bottom).toInt()) }
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = { viewModel.addKeyframeAtPlayhead(el.id) }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Keyframe @ ${"%.1f".format(state.playheadSec)}s")
        }
    }
}

@Composable
private fun NumberField(label: String, value: Float, onChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(formatNum(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toFloatOrNull()?.let(onChange)
        },
        label = { Text(label) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    )
}

private fun formatNum(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.2f".format(v)
