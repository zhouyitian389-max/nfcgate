package com.nfcgate.wear

import com.nfcgate.wear.model.CaptureState
import com.nfcgate.wear.model.WearMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureControllerTest {
    @Test
    fun toggleCapture_sendsStartThenStop() {
        val fake = FakePhoneConnection()
        val controller = CaptureController(fake)

        controller.toggleCapture()
        controller.toggleCapture()

        assertEquals(listOf(WearMessage.StartCapture, WearMessage.StopCapture), fake.sent)
    }

    @Test
    fun pauseResume_sendsExpectedCommands() {
        val fake = FakePhoneConnection()
        val controller = CaptureController(fake)

        controller.pauseCapture()
        controller.resumeCapture()

        assertEquals(listOf(WearMessage.PauseCapture, WearMessage.ResumeCapture), fake.sent)
    }

    @Test
    fun tickDuration_incrementsOnlyWhenActive() {
        val fake = FakePhoneConnection()
        val controller = CaptureController(fake)
        controller.startCapture()
        controller.tickDuration()

        assertTrue(controller.state.value.durationSeconds >= 1)
    }
}

private class FakePhoneConnection : PhoneConnectionClient {
    val sent = mutableListOf<WearMessage>()
    private var callback: ((CaptureState) -> Unit)? = null

    override fun sendMessage(message: WearMessage) {
        sent += message
    }

    override fun listenForUpdates(callback: (CaptureState) -> Unit) {
        this.callback = callback
    }

    override fun isPhoneConnected(): Boolean = true

    override fun requestStatus() = Unit
}
