package com.nfcgate.wear.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text

@Composable
fun ControlButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        modifier = Modifier.size(120.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = color),
        onClick = onClick
    ) {
        Text(label)
    }
}
