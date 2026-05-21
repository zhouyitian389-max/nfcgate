package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers;

import android.content.Context;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCEvent;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCHandler;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCSource;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.WearDataLayerBridge;

public class WearOSNFCHandler implements NFCHandler {
    @SuppressWarnings("unused")
    private final Context context;
    private final WearDataLayerBridge bridge;

    public WearOSNFCHandler(Context context, WearDataLayerBridge bridge) {
        this.context = context.getApplicationContext();
        this.bridge = bridge;
    }

    @Override
    public void startCapture(EventCallback callback) {
        bridge.setTapListener((type, uid, timestamp) ->
                callback.onEvent(new NFCEvent.CardDetected(type, uid, NFCSource.WEAR_OS, timestamp))
        );
    }

    @Override
    public void stopCapture() {
        bridge.setTapListener(null);
    }
}
