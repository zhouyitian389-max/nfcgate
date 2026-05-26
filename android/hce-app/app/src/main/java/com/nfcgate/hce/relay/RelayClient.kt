package com.nfcgate.hce.relay

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RelayClient(
    private val serverUrl: String,
    private val token: String,
    private val role: String
) : WebSocketListener() {

    interface RelayListener {
        fun onConnected(sessionId: String)
        fun onDisconnected(reason: String)
        fun onApduCommand(apduHex: String) {}
    }

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val heartbeatExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val reconnectExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    @Volatile
    private var ws: WebSocket? = null

    @Volatile
    private var listener: RelayListener? = null

    @Volatile
    private var lastSessionId: String? = null

    @Volatile
    private var pendingLatch: CountDownLatch? = null

    @Volatile
    private var pendingResponse: String? = null

    private val manuallyClosed = AtomicBoolean(false)

    fun setListener(listener: RelayListener?) {
        this.listener = listener
    }

    fun connect() {
        manuallyClosed.set(false)
        val url = "$serverUrl?token=$token&role=$role"
        ws = httpClient.newWebSocket(Request.Builder().url(url).build(), this)
    }

    fun disconnect(reason: String = "client_close") {
        manuallyClosed.set(true)
        sendRaw(JSONObject().put("type", "session_end").put("reason", reason).toString())
        ws?.close(1000, reason)
        heartbeatExecutor.shutdownNow()
        reconnectExecutor.shutdownNow()
    }

    fun sendApduAndWait(apduHex: String): String? {
        val socket = ws ?: return null
        if (socket.send(JSONObject().put("type", "apdu_command").put("data", apduHex).toString()).not()) {
            return null
        }

        val latch = CountDownLatch(1)
        pendingLatch = latch
        pendingResponse = null
        val ok = latch.await(4500, TimeUnit.MILLISECONDS)
        val response = if (ok) pendingResponse else null
        pendingLatch = null
        pendingResponse = null
        return response
    }

    fun sendApduResponse(respHex: String) {
        sendRaw(JSONObject().put("type", "apdu_response").put("data", respHex).toString())
    }

    private fun sendRaw(payload: String) {
        ws?.send(payload)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        startHeartbeat()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            val msg = JSONObject(text)
            when (msg.optString("type")) {
                "session_joined" -> {
                    lastSessionId = msg.optString("sessionId")
                    listener?.onConnected(lastSessionId ?: "")
                }
                "apdu_response" -> {
                    pendingResponse = msg.optString("data")
                    pendingLatch?.countDown()
                }
                "apdu_command" -> listener?.onApduCommand(msg.optString("data"))
                "session_end" -> listener?.onDisconnected(msg.optString("reason", "session_end"))
                "ping" -> sendRaw(JSONObject().put("type", "pong").toString())
            }
        } catch (e: Exception) {
            Log.e("RelayClient", "Invalid WS message: $text", e)
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        listener?.onDisconnected(reason)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        listener?.onDisconnected(reason)
        scheduleReconnect()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        listener?.onDisconnected(t.message ?: "websocket_failure")
        scheduleReconnect()
    }

    private fun startHeartbeat() {
        if (heartbeatExecutor.isShutdown) return
        heartbeatExecutor.scheduleAtFixedRate({
            sendRaw(JSONObject().put("type", "ping").toString())
        }, 15, 15, TimeUnit.SECONDS)
    }

    private fun scheduleReconnect() {
        if (manuallyClosed.get()) return
        if (reconnectExecutor.isShutdown) return
        reconnectExecutor.schedule({ connect() }, 3, TimeUnit.SECONDS)
    }
}
