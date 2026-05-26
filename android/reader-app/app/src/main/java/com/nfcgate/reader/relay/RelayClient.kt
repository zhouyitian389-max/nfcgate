package com.nfcgate.reader.relay

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RelayClient(
    private val serverUrl: String,
    private val token: String,
    private val role: String = "reader"
) : WebSocketListener() {

    interface RelayListener {
        fun onConnected(sessionId: String)
        fun onDisconnected(reason: String)
        fun onApduCommand(apduHex: String, seq: Int)
    }

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val heartbeatExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val reconnectExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val manuallyClosed = AtomicBoolean(false)

    @Volatile
    private var ws: WebSocket? = null

    @Volatile
    private var listener: RelayListener? = null

    fun setListener(listener: RelayListener?) {
        this.listener = listener
    }

    fun connect() {
        manuallyClosed.set(false)
        val request = Request.Builder()
            .url(serverUrl)
            .header("Authorization", "Bearer " + token)
            .build()
        ws = httpClient.newWebSocket(request, this)
    }

    fun disconnect(reason: String = "reader_close") {
        manuallyClosed.set(true)
        ws?.close(1000, reason)
        heartbeatExecutor.shutdownNow()
        reconnectExecutor.shutdownNow()
    }

    fun sendApduResponse(respHex: String, seq: Int) {
        ws?.send(JSONObject().put("type", "apdu_response").put("seq", seq).put("data", respHex).toString())
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        startHeartbeat()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            val msg = JSONObject(text)
            when (msg.optString("type")) {
                "session_joined" -> listener?.onConnected(msg.optString("sessionId"))
                "apdu_command" -> listener?.onApduCommand(msg.optString("data"), msg.optInt("seq", 0))
                "session_end" -> listener?.onDisconnected(msg.optString("reason", "session_end"))
                "ping" -> ws?.send(JSONObject().put("type", "pong").toString())
            }
        } catch (e: Exception) {
            Log.e("ReaderRelayClient", "Invalid message", e)
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        listener?.onDisconnected(reason)
        scheduleReconnect()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        listener?.onDisconnected(t.message ?: "failure")
        scheduleReconnect()
    }

    private fun startHeartbeat() {
        if (heartbeatExecutor.isShutdown) return
        heartbeatExecutor.scheduleAtFixedRate({
            ws?.send(JSONObject().put("type", "ping").toString())
        }, 15, 15, TimeUnit.SECONDS)
    }

    private fun scheduleReconnect() {
        if (manuallyClosed.get() || reconnectExecutor.isShutdown) return
        reconnectExecutor.schedule({ connect() }, 3, TimeUnit.SECONDS)
    }
}
