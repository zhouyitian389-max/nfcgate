package de.tu_darmstadt.seemoo.nfcgate.wear;

import android.content.Context;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class PhoneConnection {
    private final MessageClient messageClient;
    private final DataClient dataClient;
    private final Context context;

    public PhoneConnection(Context context) {
        this.context = context.getApplicationContext();
        this.messageClient = Wearable.getMessageClient(context);
        this.dataClient = Wearable.getDataClient(context);
    }

    public void sendControl(String command) {
        try {
            List<Node> nodes = Tasks.await(Wearable.getNodeClient(context).getConnectedNodes(), 10, TimeUnit.SECONDS);
            for (Node node : nodes) {
                Tasks.await(messageClient.sendMessage(node.getId(), "/nfcgate/control", command.getBytes()), 10, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {
        }
    }

    public void publishStatus(int count, long bytes, boolean active) {
        PutDataMapRequest request = PutDataMapRequest.create("/nfcgate/status");
        DataMap map = request.getDataMap();
        map.putInt("count", count);
        map.putLong("bytes", bytes);
        map.putBoolean("active", active);
        request.setUrgent();
        dataClient.putDataItem(request.asPutDataRequest());
    }
}
