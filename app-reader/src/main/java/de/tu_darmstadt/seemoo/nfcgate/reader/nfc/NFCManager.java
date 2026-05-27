package de.tu_darmstadt.seemoo.nfcgate.reader.nfc;

import android.app.Activity;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers.ACR122UHandler;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers.ACR39UHandler;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers.PhoneNFCHandler;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers.PN532Handler;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers.WearOSNFCHandler;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.ACR122UProtocol;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.CCIDTransport;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.PN532Protocol;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.USBConnection;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.WearDataLayerBridge;
import de.tu_darmstadt.seemoo.nfcgate.reader.settings.SettingsManager;

public class NFCManager {
    public interface EventListener {
        void onNfcEvent(NFCEvent event);
    }

    private final Context context;
    private final UsbManager usbManager;
    private final PhoneNFCHandler phoneHandler;
    private final WearOSNFCHandler wearHandler;
    private final PN532Handler pn532Handler;
    private final ACR39UHandler acr39uHandler;
    private final ACR122UHandler acr122uHandler;

    public NFCManager(Context context) {
        this.context = context;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        this.phoneHandler = new PhoneNFCHandler(context);
        this.wearHandler = new WearOSNFCHandler(context, new WearDataLayerBridge());
        USBConnection pn532Connection = new USBConnection(context);
        USBConnection acr39uConnection = new USBConnection(context);
        USBConnection acr122uConnection = new USBConnection(context);
        this.pn532Handler = new PN532Handler(pn532Connection, new PN532Protocol(pn532Connection));
        this.acr39uHandler = new ACR39UHandler(acr39uConnection, new CCIDTransport(acr39uConnection));
        this.acr122uHandler = new ACR122UHandler(acr122uConnection, new ACR122UProtocol(acr122uConnection));
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
            } else if (isACR39U(device) && SettingsManager.isUsbReaderEnabled(context)) {
                devices.add(new NFCDevice(
                        device.getDeviceName(),
                        "ACR39U (" + toHex(device.getVendorId()) + ":" + toHex(device.getProductId()) + ")",
                        NFCSource.ACR39U,
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
                pn532Handler.setTargetDevice(resolveUsbDevice(device));
                pn532Handler.startCapture(callback);
                break;
            case ACR39U:
                acr39uHandler.setTargetDevice(resolveUsbDevice(device));
                acr39uHandler.startCapture(callback);
                break;
            case ACR122U:
                acr122uHandler.setTargetDevice(resolveUsbDevice(device));
                acr122uHandler.startCapture(callback);
                break;
            case PHONE:
            default:
                if (context instanceof Activity) {
                    phoneHandler.bindActivity((Activity) context);
                }
                phoneHandler.startCapture(callback);
                break;
        }
    }

    public void stopCapture() {
        phoneHandler.stopCapture();
        wearHandler.stopCapture();
        pn532Handler.stopCapture();
        acr39uHandler.stopCapture();
        acr122uHandler.stopCapture();
    }

    private boolean isPN532(UsbDevice device) {
        int vid = device.getVendorId();
        int pid = device.getProductId();
        return (vid == 0x067B && pid == 0x2303)   // Prolific PL2303
                || (vid == 0x10C4 && pid == 0xEA60) // Silicon Labs CP210x
                || (vid == 0x0403 && pid == 0x6001) // FTDI FT232R
                || (vid == 0x0403 && pid == 0x6015) // FTDI FT230X
                || (vid == 0x1A86 && pid == 0x7523) // CH340
                || (vid == 0x1A86 && pid == 0x55D4); // CH9102
    }

    private boolean isACR122U(UsbDevice device) {
        return device.getVendorId() == 0x072F && !isACR39U(device);
    }

    private boolean isACR39U(UsbDevice device) {
        if (device.getVendorId() != 0x072F) {
            return false;
        }
        int pid = device.getProductId() & 0xFFFF;
        return pid == 0x8234 || pid == 0x8235;
    }

    private String toHex(int value) {
        return String.format(Locale.ROOT, "%04X", value & 0xFFFF);
    }

    private UsbDevice resolveUsbDevice(NFCDevice device) {
        if (device == null || usbManager == null) {
            return null;
        }
        for (UsbDevice usbDevice : usbManager.getDeviceList().values()) {
            if (device.getId().equals(usbDevice.getDeviceName())) {
                return usbDevice;
            }
        }
        return null;
    }
}
