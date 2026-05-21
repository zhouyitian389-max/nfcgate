package com.nfcgate.wear.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text
import com.nfcgate.wear.model.CaptureState
import com.nfcgate.wear.ui.components.StatusCard
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
fun StatusScreen(state: CaptureState, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        StatusCard("Total taps", state.tapCount.toString())
        StatusCard("Data size", state.bytes.toString())
        StatusCard("Duration", formatDuration(state.durationSeconds))
        StatusCard("Phone", if (state.phoneConnected) "connected ✓" else "disconnected ✗")

        Text("Recent taps")
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.recentTaps) { tap ->
                StatusCard(
                    title = "${Date(tap.timestamp)} • ${tap.type}",
                    value = tap.data.take(20)
                )
            }
        }

        Button(onClick = onBack) { Text("Back") }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = TimeUnit.SECONDS.toHours(seconds)
    val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
    val s = seconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}
