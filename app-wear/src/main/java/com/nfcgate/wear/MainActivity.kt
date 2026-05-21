package com.nfcgate.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.ambient.AmbientModeSupport
import com.nfcgate.wear.ui.screens.MainScreen
import com.nfcgate.wear.ui.screens.SettingsScreen
import com.nfcgate.wear.ui.screens.StatusScreen
import com.nfcgate.wear.ui.theme.NfcGateWearTheme

class MainActivity : ComponentActivity(), AmbientModeSupport.AmbientCallbackProvider {
    private val viewModel: WearViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AmbientModeSupport.attach(this)

        setContent {
            NfcGateWearTheme {
                var screen by rememberSaveable { mutableStateOf("main") }
                val state by viewModel.captureState.collectAsStateWithLifecycle()

                when (screen) {
                    "main" -> MainScreen(
                        state = state,
                        onToggleCapture = viewModel::toggleCapture,
                        onPauseResume = viewModel::pauseOrResume,
                        onOpenStatus = { screen = "status" },
                        onOpenSettings = { screen = "settings" }
                    )

                    "status" -> StatusScreen(state = state, onBack = { screen = "main" })
                    else -> SettingsScreen(onBack = { screen = "main" })
                }
            }
        }
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback = object : AmbientModeSupport.AmbientCallback() {}
}
