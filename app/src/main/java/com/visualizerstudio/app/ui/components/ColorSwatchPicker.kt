package com.visualizerstudio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 11 warna preset (section 5.1c) dipakai konsisten di font, garis spectrum, background box, stroke. */
val PRESET_COLOR_NAMES = listOf(
    "red", "yellow", "green", "blue", "gold", "black", "gray", "turquoise", "orange", "pink", "white"
)

private fun namedColorToCompose(name: String): Color = when (name.lowercase()) {
    "red" -> Color(0xFFE53935)
    "yellow" -> Color(0xFFFDD835)
    "green" -> Color(0xFF43A047)
    "blue" -> Color(0xFF1E88E5)
    "gold" -> Color(0xFFFFD700)
    "black" -> Color(0xFF000000)
    "gray" -> Color(0xFF9E9E9E)
    "turquoise" -> Color(0xFF40E0D0)
    "orange" -> Color(0xFFFB8C00)
    "pink" -> Color(0xFFEC407A)
    "white" -> Color(0xFFFFFFFF)
    else -> runCatching { Color(android.graphics.Color.parseColor(name)) }.getOrDefault(Color.Gray)
}

/**
 * Palet 11 warna preset + input hex custom. [value] & [onValueChange] pakai representasi
 * string yang sama seperti field warna di domain model (mis. "white", "#FF0000").
 */
@Composable
fun ColorSwatchPicker(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        FlowRow {
            PRESET_COLOR_NAMES.forEach { name ->
                val selected = value.equals(name, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(32.dp)
                        .clickable { onValueChange(name) }
                        .background(namedColorToCompose(name), CircleShape)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) Color(0xFF6750A4) else Color(0xFFBDBDBD),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {}
            }
        }
        OutlinedTextField(
            value = if (value.startsWith("#")) value else "",
            onValueChange = { hex -> onValueChange(hex) },
            label = { Text("Hex custom (mis. #FF00AA)") },
            singleLine = true,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
