package de.tu_darmstadt.seemoo.nfcgate.reader.nfc;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers.ACR122UHandler;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers.PhoneNFCHandler;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers.PN532Handler;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers.WearOSNFCHandler;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.ACR122UProtocol;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.PN532Protocol;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.USBConnection;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.WearDataLayerBridge;

public class NFCManager {
    public interface EventListener {
        void onNfcEvent(NFCEvent event);
    }

    private final Context context;
    private final UsbManager usbManager;
    private final PhoneNFCHandler phoneHandler;
    private final WearOSNFCHandler wearHandler;
    private final PN532Handler pn532Handler;
    private final ACR122UHandler acr122uHandler;

    public NFCManager(Context context) {
        this.context = context.getApplicationContext();
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        this.phoneHandler = new PhoneNFCHandler(context);
        this.wearHandler = new WearOSNFCHandler(context, new WearDataLayerBridge());
        this.pn532Handler = new PN532Handler(context, new PN532Protocol(new USBConnection(context), null, null));
        this.acr122uHandler = new ACR122UHandler(context, new ACR122UProtocol(new USBConnection(context), null, null));
    }

    public List<NFCDevice> scanUSBDevices() {
        List<NFCDevice> devices = new ArrayList<>();
        if (usbManager == null) {
            return devices;
        }
        for (Map.Entry<String, UsbDevice> entry : usbManager.getDeviceList().entrySet()) {
            UsbDevice device = entry.getValue();
            if (isPN532(device)) {
                devices.add(new NFCDevice(
                        device.getDeviceName(),
                        "PN532 (" + toHex(device.getVendorId()) + ":" + toHex(device.getProductId()) + ")",
                        NFCSource.PN532,
                        false
                ));
            } else if (isACR122U(device)) {
                devices.add(new NFCDevice(
                        device.getDeviceName(),
                        "ACR122U (" + toHex(device.getVendorId()) + ":" + toHex(device.getProductId()) + ")",
                        NFCSource.ACR122U,
                        false
                ));
            }
        }
        return devices;
    }

    public List<NFCDevice> getAvailableDevices() {
        List<NFCDevice> devices = new ArrayList<>();
        devices.add(new NFCDevice("phone", "Phone NFC", NFCSource.PHONE, true));
        devices.add(new NFCDevice("wear", "Wear OS NFC", NFCSource.WEAR_OS, false));
        devices.addAll(scanUSBDevices());
        return devices;
    }

    public void startCapture(NFCDevice device, EventListener listener) {
        NFCSource source = device != null ? device.getSource() : NFCSource.PHONE;
        NFCHandler.EventCallback callback = listener::onNfcEvent;
        switch (source) {
            case WEAR_OS:
                wearHandler.startCapture(callback);
                break;
            case PN532:
                pn532Handler.startCapture(callback);
                break;
            case ACR122U:
                acr122uHandler.startCapture(callback);
                break;
            case PHONE:
            default:
                phoneHandler.startCapture(callback);
                break;
        }
    }

    public void stopCapture() {
        phoneHandler.stopCapture();
        wearHandler.stopCapture();
        pn532Handler.stopCapture();
        acr122uHandler.stopCapture();
    }

    private boolean isPN532(UsbDevice device) {
        return (device.getVendorId() == 0x067B && device.getProductId() == 0x2303)
                || (device.getVendorId() == 0x10C4 && device.getProductId() == 0xEA60);
    }

    private boolean isACR122U(UsbDevice device) {
        return device.getVendorId() == 0x072F;
    }

    private String toHex(int value) {
        return String.format("%04X", value & 0xFFFF);
    }
}
