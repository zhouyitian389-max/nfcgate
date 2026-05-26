package com.nfcgate.hce.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nfcgate.hce.relay.RelayManager

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val urlInput = EditText(this).apply {
            hint = "ws://server:8080/ws/relay"
            setText("ws://10.0.2.2:8080/ws/relay")
        }
        val tokenInput = EditText(this).apply {
            hint = "Relay token"
        }
        val status = TextView(this).apply {
            text = "Not connected"
        }
        val startButton = Button(this).apply {
            text = "Start Relay"
            setOnClickListener {
                val server = urlInput.text.toString().trim()
                val token = tokenInput.text.toString().trim()
                if (server.isEmpty() || token.isEmpty()) {
                    Toast.makeText(context, "URL and token required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val client = RelayManager.startRelay(token, server, "hce")
                client.setListener(object : com.nfcgate.hce.relay.RelayClient.RelayListener {
                    override fun onConnected(sessionId: String) {
                        runOnUiThread { status.text = "Connected: $sessionId" }
                    }

                    override fun onDisconnected(reason: String) {
                        runOnUiThread { status.text = "Disconnected: $reason" }
                    }
                })
            }
        }

        root.addView(urlInput)
        root.addView(tokenInput)
        root.addView(startButton)
        root.addView(status)

        setContentView(root)
    }

    override fun onDestroy() {
        super.onDestroy()
        RelayManager.stopRelay("activity_destroy")
    }
}
