package de.tu_darmstadt.seemoo.nfcgate.reader.wear;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.WearDataLayerBridge;

public class WearBridgeService extends WearableListenerService {
    @Override
    public void onMessageReceived(MessageEvent event) {
        if (WearDataLayerBridge.PATH_TAP.equals(event.getPath())) {
            WearDataLayerBridge.getInstance().dispatchTap(
                    new String(event.getData()),
                    event.getData(),
                    System.currentTimeMillis()
            );
        } else if (WearDataLayerBridge.PATH_CONTROL.equals(event.getPath())) {
            WearDataLayerBridge.getInstance().dispatchCommand(new String(event.getData()));
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED && WearDataLayerBridge.PATH_STATUS.equals(event.getDataItem().getUri().getPath())) {
                WearDataLayerBridge.getInstance().dispatchStatus(
                        DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getInt("count"),
                        DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getLong("bytes"),
                        DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getBoolean("active")
                );
            }
        }
    }
}
