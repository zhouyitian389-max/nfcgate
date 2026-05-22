package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport;

import androidx.annotation.Nullable;

public class WearDataLayerBridge {
    public static final String PATH_TAP = "/nfcgate/tap";
    public static final String PATH_CONTROL = "/nfcgate/control";
    public static final String PATH_STATUS = "/nfcgate/status";

    public interface TapListener {
        void onTap(String type, byte[] uid, long timestamp);
    }

    public interface CommandListener {
        void onCommand(String command);
    }

    public interface StatusListener {
        void onStatus(int count, long bytes, boolean active);
    }

    private static final WearDataLayerBridge INSTANCE = new WearDataLayerBridge();

    @Nullable
    private TapListener listener;
    @Nullable
    private CommandListener commandListener;
    @Nullable
    private StatusListener statusListener;

    public static WearDataLayerBridge getInstance() {
        return INSTANCE;
    }

    public void setTapListener(@Nullable TapListener listener) {
        this.listener = listener;
    }

    public void setCommandListener(@Nullable CommandListener commandListener) {
        this.commandListener = commandListener;
    }

    public void setStatusListener(@Nullable StatusListener statusListener) {
        this.statusListener = statusListener;
    }

    public void dispatchTap(String type, byte[] uid, long timestamp) {
        if (listener != null) {
            listener.onTap(type, uid, timestamp);
        }
    }

    public void dispatchCommand(String command) {
        if (commandListener != null) {
            commandListener.onCommand(command);
        }
    }

    public void dispatchStatus(int count, long bytes, boolean active) {
        if (statusListener != null) {
            statusListener.onStatus(count, bytes, active);
        }
    }
}
