package de.tu_darmstadt.seemoo.nfcgate.wear;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

public class DataLayerListener extends WearableListenerService {
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED && "/nfcgate/status".equals(event.getDataItem().getUri().getPath())) {
                DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
            }
        }
    }
}
