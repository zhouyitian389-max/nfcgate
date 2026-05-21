package com.nfcgate.wear

import com.nfcgate.wear.model.CaptureState
import com.nfcgate.wear.model.TapRecord
import com.nfcgate.wear.model.WearMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CaptureController(
    private val phoneConnection: PhoneConnectionClient,
    private val batteryProvider: () -> Int = { -1 }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(CaptureState())
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    init {
        phoneConnection.listenForUpdates { updateFromRemote(it) }
        _state.update { it.copy(phoneConnected = phoneConnection.isPhoneConnected(), batteryPercent = batteryProvider()) }
    }

    fun startCapture() = send(WearMessage.StartCapture) { it.copy(active = true, paused = false) }

    fun stopCapture() = send(WearMessage.StopCapture) {
        it.copy(active = false, paused = false, durationSeconds = 0)
    }

    fun pauseCapture() = send(WearMessage.PauseCapture) { it.copy(paused = true, active = false) }

    fun resumeCapture() = send(WearMessage.ResumeCapture) { it.copy(paused = false, active = true) }

    fun toggleCapture() {
        if (_state.value.active || _state.value.paused) stopCapture() else startCapture()
    }

    fun requestStatus() {
        phoneConnection.requestStatus()
    }

    fun tickDuration() {
        _state.update {
            if (it.active) it.copy(durationSeconds = it.durationSeconds + 1, batteryPercent = batteryProvider())
            else it.copy(batteryPercent = batteryProvider())
        }
    }

    private fun send(message: WearMessage, localUpdate: (CaptureState) -> CaptureState) {
        scope.launch {
            phoneConnection.sendMessage(message)
            _state.update(localUpdate)
        }
    }

    private fun updateFromRemote(incoming: CaptureState) {
        val connectionOnly = incoming.recentTaps.isEmpty() &&
            incoming.tapCount == 0 &&
            incoming.bytes == 0L &&
            !incoming.active &&
            !incoming.paused

        _state.update { current ->
            if (connectionOnly) {
                current.copy(phoneConnected = incoming.phoneConnected)
            } else {
                current.copy(
                    active = incoming.active,
                    paused = incoming.paused,
                    tapCount = incoming.tapCount,
                    bytes = incoming.bytes,
                    phoneConnected = incoming.phoneConnected,
                    recentTaps = mergeTaps(current.recentTaps, incoming.recentTaps)
                )
            }
        }
    }

    private fun mergeTaps(existing: List<TapRecord>, incoming: List<TapRecord>): List<TapRecord> {
        if (incoming.isEmpty()) return existing
        return (incoming + existing).distinctBy { "${it.timestamp}:${it.type}:${it.data}" }.take(20)
    }
}
