package com.nfcgate.wear.ui.components

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.CircularProgressIndicator

@Composable
fun CaptureCircularProgress(progress: Float) {
    CircularProgressIndicator(progress = progress.coerceIn(0f, 1f))
}
