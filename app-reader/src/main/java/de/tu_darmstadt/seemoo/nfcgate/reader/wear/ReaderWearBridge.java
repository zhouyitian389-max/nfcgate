package de.tu_darmstadt.seemoo.nfcgate.reader.wear;

import android.content.Context;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeClient;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class ReaderWearBridge {
    private ReaderWearBridge() {}

    public static void sendTapEvent(Context context, String type, String data, long timestamp) {
        try {
            NodeClient nodeClient = Wearable.getNodeClient(context);
            List<Node> nodes = Tasks.await(nodeClient.getConnectedNodes());

            JSONObject json = new JSONObject();
            json.put("type", "tap");
            json.put("tapType", type);
            json.put("data", data);
            json.put("timestamp", timestamp);

            byte[] payload = json.toString().getBytes(StandardCharsets.UTF_8);
            for (Node node : nodes) {
                Wearable.getMessageClient(context).sendMessage(node.getId(), ReaderWearProtocol.PATH_TAP, payload);
            }
        } catch (Exception ignored) {
        }
    }
}
