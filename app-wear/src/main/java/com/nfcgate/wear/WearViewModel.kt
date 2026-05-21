package com.nfcgate.wear

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nfcgate.wear.model.CaptureState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WearViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = CaptureController(
        phoneConnection = PhoneConnection(application),
        batteryProvider = {
            val intent = application.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        }
    )

    val captureState: StateFlow<CaptureState> = controller.state

    init {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                controller.tickDuration()
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(2000)
                if (captureState.value.active) controller.requestStatus()
            }
        }
    }

    fun toggleCapture() = controller.toggleCapture()
    fun pauseOrResume() {
        if (captureState.value.paused) controller.resumeCapture() else controller.pauseCapture()
    }
}
