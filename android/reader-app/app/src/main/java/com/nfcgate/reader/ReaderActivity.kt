package com.nfcgate.reader

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nfcgate.reader.relay.RelayClient
import java.util.Locale

class ReaderActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var isoDep: IsoDep? = null
    private var relayClient: RelayClient? = null

    private lateinit var statusView: TextView
    private lateinit var urlInput: EditText
    private lateinit var tokenInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        urlInput = EditText(this).apply {
            hint = "ws://server:8080/ws/relay"
            setText("ws://10.0.2.2:8080/ws/relay")
        }
        tokenInput = EditText(this).apply { hint = "Relay token" }
        val connectButton = Button(this).apply {
            text = "Connect as Reader"
            setOnClickListener {
                connectRelay(urlInput.text.toString().trim(), tokenInput.text.toString().trim())
            }
        }
        statusView = TextView(this).apply { text = "Tap card after connecting" }

        root.addView(urlInput)
        root.addView(tokenInput)
        root.addView(connectButton)
        root.addView(statusView)
        setContentView(root)

        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        enableForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun enableForegroundDispatch() {
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    private fun handleIntent(intent: Intent) {
        val tag: Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return
        val iso = IsoDep.get(tag) ?: return
        try {
            iso.connect()
            iso.timeout = 5000
            isoDep = iso
            statusView.text = "Card connected: ${tag.id.toHex()}"
        } catch (e: Exception) {
            statusView.text = "Card connect failed: ${e.message}"
        }
    }

    private fun connectRelay(server: String, token: String) {
        if (server.isBlank() || token.isBlank()) {
            Toast.makeText(this, "URL + token required", Toast.LENGTH_SHORT).show()
            return
        }

        relayClient?.disconnect("replace")
        relayClient = RelayClient(server, token, "reader").also { client ->
            client.setListener(object : RelayClient.RelayListener {
                override fun onConnected(sessionId: String) {
                    runOnUiThread { statusView.text = "Relay connected: $sessionId" }
                }

                override fun onDisconnected(reason: String) {
                    runOnUiThread { statusView.text = "Relay disconnected: $reason" }
                }

                override fun onApduCommand(apduHex: String, seq: Int) {
                    val response = transceive(apduHex)
                    client.sendApduResponse(response, seq)
                }
            })
            client.connect()
        }
    }

    private fun transceive(apduHex: String): String {
        val iso = isoDep ?: return "6F00"
        return try {
            val response = iso.transceive(apduHex.hexToBytes())
            response.toHex()
        } catch (_: TagLostException) {
            "6F00"
        } catch (_: Exception) {
            "6F00"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        relayClient?.disconnect("activity_destroy")
        isoDep?.close()
    }

    private fun ByteArray.toHex(): String = joinToString("") { String.format(Locale.US, "%02X", it) }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
