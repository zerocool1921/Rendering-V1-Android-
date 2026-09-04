package com.visualizerstudio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.visualizerstudio.app.domain.model.TextPosition

/** 9 preset posisi teks (section 5.1c) — dipakai untuk Intro & Title. */
data class TextPositionOption(val position: TextPosition, val label: String)

val TEXT_POSITION_OPTIONS = listOf(
    TextPositionOption(TextPosition.TOP_LEFT, "Kiri-Atas"),
    TextPositionOption(TextPosition.CENTER_TOP, "Tengah-Atas"),
    TextPositionOption(TextPosition.TOP_RIGHT, "Kanan-Atas"),
    TextPositionOption(TextPosition.MID_LEFT, "Kiri-Tengah"),
    TextPositionOption(TextPosition.CENTER_CENTER, "Tengah-Tengah"),
    TextPositionOption(TextPosition.MID_RIGHT, "Kanan-Tengah"),
    TextPositionOption(TextPosition.BOTTOM_LEFT, "Bawah-Kiri"),
    TextPositionOption(TextPosition.CENTER_BOTTOM, "Tengah-Bawah"),
    TextPositionOption(TextPosition.BOTTOM_RIGHT, "Bawah-Kanan")
)

@Composable
fun TextPositionPresetGrid(
    selected: TextPosition,
    onSelect: (TextPosition) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = modifier) {
        items(TEXT_POSITION_OPTIONS) { option ->
            val isSelected = option.position == selected
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .aspectRatio(1.6f)
                    .background(
                        color = if (isSelected) Color(0xFF6750A4) else Color(0xFFF2F0F4),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(option.position) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option.label,
                    color = if (isSelected) Color.White else Color.Black
                )
            }
        }
    }
}

/** 6 preset posisi spectrum (section 5.1d) — hasil klik langsung set ekspresi posX/posY. */
data class SpectrumPositionPreset(val label: String, val posX: String, val posY: String)

val SPECTRUM_POSITION_PRESETS = listOf(
    SpectrumPositionPreset("Tengah-Bawah", "(W-w)/2", "H-h-50"),
    SpectrumPositionPreset("Tengah-Tengah", "(W-w)/2", "(H-h)/2"),
    SpectrumPositionPreset("Tengah-Atas", "(W-w)/2", "50"),
    SpectrumPositionPreset("Kiri-Bawah", "50", "H-h-50"),
    SpectrumPositionPreset("Kanan-Bawah", "W-w-50", "H-h-50")
)

@Composable
fun SpectrumPositionPresetRow(
    onSelect: (SpectrumPositionPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Row(modifier = modifier) {
        SPECTRUM_POSITION_PRESETS.forEach { preset ->
            androidx.compose.material3.AssistChip(
                onClick = { onSelect(preset) },
                label = { Text(preset.label) },
                modifier = Modifier.padding(end = 6.dp)
            )
        }
    }
}
