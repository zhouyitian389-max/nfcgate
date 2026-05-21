package com.nfcgate.wear

import com.nfcgate.wear.model.WearMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearMessageTest {
    @Test
    fun sessionUpdate_roundTrips() {
        val message = WearMessage.SessionUpdate(count = 5, bytes = 128L, active = true, paused = false)
        val parsed = WearMessage.fromJson(message.toJson())

        assertEquals(message, parsed)
    }

    @Test
    fun tapEvent_roundTrips() {
        val message = WearMessage.TapEvent(type = "EMV", data = "a0b1", timestamp = 1234L)
        val parsed = WearMessage.fromJson(message.toJson())

        assertEquals(message, parsed)
    }

    @Test
    fun unknownType_returnsNull() {
        assertTrue(WearMessage.fromJson("{\"type\":\"x\"}") == null)
    }
}
