package de.tu_darmstadt.seemoo.nfcgate.reader.nfc;

import androidx.annotation.Nullable;

public class NFCDevice {
    private final String id;
    private final String name;
    private final NFCSource source;
    private final boolean connected;
    @Nullable
    private final Integer batteryLevel;

    public NFCDevice(String id, String name, NFCSource source, boolean connected, @Nullable Integer batteryLevel) {
        this.id = id;
        this.name = name;
        this.source = source;
        this.connected = connected;
        this.batteryLevel = batteryLevel;
    }

    public NFCDevice(String id, String name, NFCSource source, boolean connected) {
        this(id, name, source, connected, null);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public NFCSource getSource() {
        return source;
    }

    public boolean isConnected() {
        return connected;
    }

    @Nullable
    public Integer getBatteryLevel() {
        return batteryLevel;
    }
}
