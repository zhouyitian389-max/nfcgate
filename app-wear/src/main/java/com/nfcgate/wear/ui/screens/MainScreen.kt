package com.nfcgate.wear.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.icons.Icons
import androidx.wear.compose.material.icons.filled.Settings
import com.nfcgate.wear.model.CaptureState
import com.nfcgate.wear.ui.components.ControlButton

@Composable
fun MainScreen(
    state: CaptureState,
    onToggleCapture: () -> Unit,
    onPauseResume: () -> Unit,
    onOpenStatus: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("NFCGate Wear")
            Text(if (state.batteryPercent >= 0) "${state.batteryPercent}%" else "--")
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ControlButton(
                label = if (state.active || state.paused) "Stop" else "Start",
                color = if (state.active || state.paused) Color.Red else Color(0xFF2E7D32),
                onClick = onToggleCapture
            )
            Text("Taps: ${state.tapCount}")
        }

        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onPauseResume) { Text(if (state.paused) "Resume" else "Pause") }
            Button(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        }
        Button(onClick = onOpenStatus) { Text("Status") }
    }
}
