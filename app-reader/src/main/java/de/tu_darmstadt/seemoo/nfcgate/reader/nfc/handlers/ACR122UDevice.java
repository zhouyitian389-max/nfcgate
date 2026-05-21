package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers;

import android.hardware.usb.UsbDevice;

public class ACR122UDevice {
    private final UsbDevice usbDevice;

    public ACR122UDevice(UsbDevice usbDevice) {
        this.usbDevice = usbDevice;
    }

    public UsbDevice getUsbDevice() {
        return usbDevice;
    }
}
