package de.tu_darmstadt.seemoo.nfcgate.reader.ui;

import android.content.Context;
import android.hardware.usb.UsbDevice;

import de.tu_darmstadt.seemoo.nfcgate.reader.service.USBPermissionReceiver;

public final class USBDevicePermission {
    private USBDevicePermission() {
    }

    public static void request(Context context, UsbDevice device) {
        USBPermissionReceiver.requestPermission(context, device);
    }
}
