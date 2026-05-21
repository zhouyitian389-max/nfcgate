package de.tu_darmstadt.seemoo.nfcgate.reader.ui;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCDevice;

public class DeviceStatus {
    private DeviceStatus() {
    }

    public static String toStatusText(NFCDevice device) {
        if (device == null) {
            return "No device selected";
        }
        return device.getName() + (device.isConnected() ? " (Connected)" : " (Disconnected)");
    }
}
