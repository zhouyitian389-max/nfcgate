package com.nfcgate.wear

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.nfcgate.wear.model.WearMessage
import com.nfcgate.wear.model.WearMessageBus
import java.nio.charset.StandardCharsets

class DataLayerListener : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.dataItem.uri.path == PhoneConnection.PATH_DATA_SESSION) {
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                WearMessageBus.emit(
                    WearMessage.SessionUpdate(
                        count = map.getInt("count", 0),
                        bytes = map.getLong("bytes", 0L),
                        active = map.getBoolean("active", false),
                        paused = map.getBoolean("paused", false)
                    )
                )
            }
        }
    }

    override fun onMessageReceived(messageEvent: com.google.android.gms.wearable.MessageEvent) {
        val parsed = WearMessage.fromJson(String(messageEvent.data, StandardCharsets.UTF_8)) ?: return
        WearMessageBus.emit(parsed)
    }
}
