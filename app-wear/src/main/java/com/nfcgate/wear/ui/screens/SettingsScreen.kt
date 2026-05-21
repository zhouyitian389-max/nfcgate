package com.nfcgate.wear.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var gestureEnabled by rememberSaveable { mutableStateOf(false) }
    var brightness by rememberSaveable { mutableStateOf("auto") }
    var debugEnabled by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Button(onClick = { gestureEnabled = !gestureEnabled }) {
            Text("Wrist gesture: ${if (gestureEnabled) "on" else "off"}")
        }
        Button(onClick = {
            brightness = when (brightness) {
                "auto" -> "dim"
                "dim" -> "bright"
                else -> "auto"
            }
        }) {
            Text("Brightness: $brightness")
        }
        Button(onClick = { debugEnabled = !debugEnabled }) {
            Text("Debug log: ${if (debugEnabled) "on" else "off"}")
        }
        Button(onClick = onBack) { Text("Back") }
    }
}
