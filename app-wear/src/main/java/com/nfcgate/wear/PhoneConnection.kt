package com.nfcgate.wear

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.nfcgate.wear.model.CaptureState
import com.nfcgate.wear.model.TapRecord
import com.nfcgate.wear.model.WearMessage
import com.nfcgate.wear.model.WearMessageBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.nio.charset.StandardCharsets

interface PhoneConnectionClient {
    fun sendMessage(message: WearMessage)
    fun listenForUpdates(callback: (CaptureState) -> Unit)
    fun isPhoneConnected(): Boolean
    fun requestStatus()
}

class PhoneConnection(context: Context) : PhoneConnectionClient,
    MessageClient.OnMessageReceivedListener,
    DataClient.OnDataChangedListener,
    CapabilityClient.OnCapabilityChangedListener {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messageClient = Wearable.getMessageClient(appContext)
    private val dataClient = Wearable.getDataClient(appContext)
    private val capabilityClient = Wearable.getCapabilityClient(appContext)

    @Volatile
    private var connected = false

    private var callback: ((CaptureState) -> Unit)? = null

    init {
        messageClient.addListener(this)
        dataClient.addListener(this)
        capabilityClient.addListener(this, CAPABILITY_READER)
        capabilityClient.getCapability(CAPABILITY_READER, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { connected = it.nodes.isNotEmpty() }
            .addOnFailureListener { connected = false }

        WearMessageBus.messages.onEach { inbound ->
            callback?.invoke(toCaptureState(inbound))
        }.launchIn(scope)
    }

    override fun sendMessage(message: WearMessage) {
        val payload = message.toJson().toByteArray(StandardCharsets.UTF_8)
        capabilityClient.getCapability(CAPABILITY_READER, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { capabilityInfo ->
                connected = capabilityInfo.nodes.isNotEmpty()
                capabilityInfo.nodes.forEach { node ->
                    messageClient.sendMessage(node.id, PATH_COMMAND, payload)
                }
            }
    }

    override fun listenForUpdates(callback: (CaptureState) -> Unit) {
        this.callback = callback
        callback(CaptureState(phoneConnected = connected))
    }

    override fun isPhoneConnected(): Boolean = connected

    override fun requestStatus() {
        sendMessage(WearMessage.QueryStatus)
    }

    override fun onMessageReceived(messageEvent: com.google.android.gms.wearable.MessageEvent) {
        if (messageEvent.path != PATH_STATUS && messageEvent.path != PATH_TAP) return
        val parsed = WearMessage.fromJson(String(messageEvent.data, StandardCharsets.UTF_8)) ?: return
        WearMessageBus.emit(parsed)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            val item = event.dataItem
            if (item.uri.path == PATH_DATA_SESSION) {
                val dataMap = com.google.android.gms.wearable.DataMapItem.fromDataItem(item).dataMap
                WearMessageBus.emit(
                    WearMessage.SessionUpdate(
                        count = dataMap.getInt("count", 0),
                        bytes = dataMap.getLong("bytes", 0L),
                        active = dataMap.getBoolean("active", false),
                        paused = dataMap.getBoolean("paused", false)
                    )
                )
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: com.google.android.gms.wearable.CapabilityInfo) {
        connected = capabilityInfo.nodes.isNotEmpty()
        callback?.invoke(CaptureState(phoneConnected = connected))
    }

    private fun toCaptureState(message: WearMessage): CaptureState {
        return when (message) {
            is WearMessage.SessionUpdate -> CaptureState(
                active = message.active,
                paused = message.paused,
                tapCount = message.count,
                bytes = message.bytes,
                phoneConnected = connected
            )
            is WearMessage.TapEvent -> CaptureState(
                phoneConnected = connected,
                recentTaps = listOf(TapRecord(message.type, message.data, message.timestamp))
            )
            else -> CaptureState(phoneConnected = connected)
        }
    }

    companion object {
        const val CAPABILITY_READER = "nfcgate_reader"
        const val PATH_COMMAND = "/nfcgate/wear/command"
        const val PATH_STATUS = "/nfcgate/phone/status"
        const val PATH_TAP = "/nfcgate/phone/tap"
        const val PATH_DATA_SESSION = "/nfcgate/session"
    }
}
