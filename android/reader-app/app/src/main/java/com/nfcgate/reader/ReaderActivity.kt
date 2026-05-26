package com.nfcgate.reader

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nfcgate.hce.relay.RelayClient
import com.nfcgate.hce.relay.RelayManager

/**
 * Reader App: reads a real NFC card and relays APDUs via WebSocket.
 *
 * Flow:
 * 1. User taps real card on this phone
 * 2. IsoDep connection established
 * 3. WebSocket receives apdu_command from HCE side
 * 4. We transceive the command to the real card
 * 5. Send apdu_response back via WebSocket
 */
class ReaderActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "NFCGateReader"
    }

    private var nfcAdapter: NfcAdapter? = null
    private var isoDep: IsoDep? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC not available", Toast.LENGTH_LONG).show()
            finish()
            return
        }
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
        handleTag(tag)
    }

    private fun handleTag(tag: Tag) {
        val iso = IsoDep.get(tag)
        if (iso == null) {
            Toast.makeText(this, "Card does not support IsoDep", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            iso.connect()
            iso.timeout = 5000
            isoDep = iso

            Log.d(TAG, "Card connected - UID: ${tag.id.toHex()}")
            Log.d(TAG, "Historical bytes: ${iso.historicalBytes?.toHex() ?: "none"}")

            // Notify server about card info
            sendCardInfo(tag, iso)

            // Start listening for APDU commands from HCE side
            startRelayLoop(iso)

        } catch (e: Exception) {
            Log.e(TAG, "Error communicating with card", e)
            Toast.makeText(this, "Card error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendCardInfo(tag: Tag, iso: IsoDep) {
        val client = RelayManager.activeClient ?: return
        // Card info is sent as a JSON message
        val info = org.json.JSONObject().apply {
            put("type", "card_info")
            put("uid", tag.id.toHex())
            put("historicalBytes", iso.historicalBytes?.toHex() ?: "")
            put("hiLayerResponse", iso.hiLayerResponse?.toHex() ?: "")
            put("maxTransceiveLength", iso.maxTransceiveLength)
        }
        // Send via internal method (add to RelayClient if needed)
    }

    private fun startRelayLoop(iso: IsoDep) {
        val client = RelayManager.activeClient ?: return

        // Set up listener to handle incoming APDU commands
        client.setListener(object : RelayClient.RelayListener {
            override fun onConnected(sessionId: String) {
                Log.d(TAG, "Reader connected to session: $sessionId")
            }

            override fun onDisconnected(reason: String) {
                Log.d(TAG, "Reader disconnected: $reason")
                runOnUiThread {
                    Toast.makeText(this@ReaderActivity, "Relay ended", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onApduCommand(apdu: ByteArray) {
                // Received command from HCE side → transceive to real card
                Thread {
                    try {
                        Log.d(TAG, "→ Card APDU: ${apdu.toHex()}")
                        val response = iso.transceive(apdu)
                        Log.d(TAG, "← Card Response: ${response.toHex()}")
                        client.sendApduResponse(response)
                    } catch (e: Exception) {
                        Log.e(TAG, "Transceive failed", e)
                        // Send error SW
                        client.sendApduResponse(byteArrayOf(0x6F.toByte(), 0x00.toByte()))
                    }
                }.start()
            }

            override fun onError(error: String) {
                Log.e(TAG, "Relay error: $error")
            }
        })
    }

    /**
     * Called from UI to start relay connection as reader role.
     */
    fun connectAsReader(serverUrl: String, token: String) {
        RelayManager.startRelay(token = token, server = serverUrl, role = "reader")
        Toast.makeText(this, "Reader connecting...", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        isoDep?.close()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}
