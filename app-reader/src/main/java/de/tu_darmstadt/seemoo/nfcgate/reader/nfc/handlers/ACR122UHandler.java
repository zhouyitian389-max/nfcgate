package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers;

import android.hardware.usb.UsbDevice;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCEvent;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCHandler;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCSource;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.ACR122UProtocol;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.PN532Protocol.CardInfo;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.USBConnection;

public class ACR122UHandler implements NFCHandler {
    private static final String TAG = "ACR122UHandler";
    private final USBConnection usbConnection;
    private final ACR122UProtocol protocol;
    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile UsbDevice targetDevice;

    public ACR122UHandler(USBConnection usbConnection, ACR122UProtocol protocol) {
        this.usbConnection = usbConnection;
        this.protocol = protocol;
    }

    public void setTargetDevice(UsbDevice targetDevice) {
        this.targetDevice = targetDevice;
    }

    @Override
    public void startCapture(EventCallback callback) {
        UsbDevice device = targetDevice;
        if (device == null) {
            callback.onEvent(new NFCEvent.Error("No ACR122U USB device selected", NFCSource.ACR122U));
            return;
        }
        if (!usbConnection.connect(device)) {
            callback.onEvent(new NFCEvent.Error("Cannot open ACR122U USB connection", NFCSource.ACR122U));
            return;
        }
        if (!protocol.configureForDevice(device)) {
            usbConnection.disconnect();
            callback.onEvent(new NFCEvent.Error("Cannot configure ACR122U USB endpoints", NFCSource.ACR122U));
            return;
        }
        capturing.set(true);
        worker.execute(() -> {
            try {
                Log.d(TAG, "Starting ACR122U capture loop");
                while (capturing.get()) {
                    CardInfo card = protocol.pollCard(1000);
                    if (card == null) {
                        continue;
                    }
                    callback.onEvent(new NFCEvent.CardDetected(
                            card.getType(),
                            card.getUid(),
                            NFCSource.ACR122U,
                            System.currentTimeMillis()
                    ));
                }
            } catch (Exception e) {
                callback.onEvent(new NFCEvent.Error(e.getMessage() != null ? e.getMessage() : "Unknown ACR122U error", NFCSource.ACR122U));
            } finally {
                usbConnection.disconnect();
            }
        });
    }

    @Override
    public void stopCapture() {
        capturing.set(false);
        usbConnection.disconnect();
    }
}
