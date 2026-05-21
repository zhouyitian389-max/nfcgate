package com.nfcgate.wear.model

data class CaptureState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val tapCount: Int = 0,
    val bytes: Long = 0L,
    val durationSeconds: Long = 0L,
    val phoneConnected: Boolean = false,
    val batteryPercent: Int = -1,
    val recentTaps: List<TapRecord> = emptyList()
)

data class TapRecord(
    val type: String,
    val data: String,
    val timestamp: Long
)
