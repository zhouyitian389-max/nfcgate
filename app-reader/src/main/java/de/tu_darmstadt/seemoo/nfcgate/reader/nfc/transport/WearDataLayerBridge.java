package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport;

import androidx.annotation.Nullable;

public class WearDataLayerBridge {
    public interface TapListener {
        void onTap(String type, byte[] uid, long timestamp);
    }

    @Nullable
    private TapListener listener;

    public void setTapListener(@Nullable TapListener listener) {
        this.listener = listener;
    }

    public void dispatchTap(String type, byte[] uid, long timestamp) {
        if (listener != null) {
            listener.onTap(type, uid, timestamp);
        }
    }
}
