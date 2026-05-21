package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers;

import android.hardware.usb.UsbDevice;

public class PN532Device {
    private final UsbDevice usbDevice;

    public PN532Device(UsbDevice usbDevice) {
        this.usbDevice = usbDevice;
    }

    public UsbDevice getUsbDevice() {
        return usbDevice;
    }
}
