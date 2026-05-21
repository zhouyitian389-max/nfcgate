package de.tu_darmstadt.seemoo.nfcgate.reader.wear;

import android.util.Log;

import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class ReaderWearListenerService extends WearableListenerService {
    private static final String TAG = "ReaderWearListener";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (!ReaderWearProtocol.PATH_COMMAND.equals(messageEvent.getPath())) {
            return;
        }

        String payload = new String(messageEvent.getData(), StandardCharsets.UTF_8);
        String type = "query";
        try {
            type = new JSONObject(payload).optString("type", "query");
        } catch (Exception ignored) {
        }

        if (!"query".equals(type)) {
            ReaderWearStateStore.updateFromCommand(this, type);
        }
        sendSessionToWear(messageEvent.getSourceNodeId());
    }

    private void sendSessionToWear(String nodeId) {
        String sessionJson = ReaderWearStateStore.toSessionJson(this);
        Wearable.getMessageClient(this)
                .sendMessage(nodeId, ReaderWearProtocol.PATH_STATUS, sessionJson.getBytes(StandardCharsets.UTF_8));

        PutDataMapRequest dataRequest = PutDataMapRequest.create(ReaderWearProtocol.PATH_DATA_SESSION);
        DataMap map = dataRequest.getDataMap();
        try {
            JSONObject json = new JSONObject(sessionJson);
            map.putInt("count", json.optInt("count", 0));
            map.putLong("bytes", json.optLong("bytes", 0L));
            map.putBoolean("active", json.optBoolean("active", false));
            map.putBoolean("paused", json.optBoolean("paused", false));
            map.putLong("updatedAt", System.currentTimeMillis());
        } catch (Exception e) {
            Log.w(TAG, "Failed to build DataMap", e);
        }

        Wearable.getDataClient(this).putDataItem(dataRequest.asPutDataRequest());
    }
}
