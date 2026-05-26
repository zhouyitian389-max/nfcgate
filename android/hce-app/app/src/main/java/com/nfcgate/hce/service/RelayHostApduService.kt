package com.nfcgate.hce.service

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.nfcgate.hce.relay.RelayManager
import java.util.Locale

class RelayHostApduService : HostApduService() {
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.isEmpty()) return SW_INTERNAL_ERROR

        val relayClient = RelayManager.activeClient ?: return SW_INTERNAL_ERROR
        val commandHex = commandApdu.toHex()

        val responseHex = try {
            relayClient.sendApduAndWait(commandHex)
        } catch (e: Exception) {
            Log.e("RelayHostApduService", "relay failed", e)
            null
        }

        return responseHex?.hexToBytes() ?: SW_TIMEOUT
    }

    override fun onDeactivated(reason: Int) {
        RelayManager.activeClient?.disconnect("hce_deactivated_$reason")
    }

    private fun ByteArray.toHex(): String = joinToString("") { String.format(Locale.US, "%02X", it) }

    private fun String.hexToBytes(): ByteArray {
        val clean = trim().replace(" ", "")
        if (clean.length % 2 != 0) return SW_INTERNAL_ERROR
        return try {
            clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (_: NumberFormatException) {
            SW_INTERNAL_ERROR
        }
    }

    companion object {
        private val SW_TIMEOUT = byteArrayOf(0x6F.toByte(), 0x00)
        private val SW_INTERNAL_ERROR = byteArrayOf(0x6F.toByte(), 0x00)
    }
}
