package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers;

import android.hardware.usb.UsbDevice;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCEvent;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCHandler;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCSource;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.PN532Protocol;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.PN532Protocol.CardInfo;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.USBConnection;

public class PN532Handler implements NFCHandler {
    private static final String TAG = "PN532Handler";
    private final USBConnection usbConnection;
    private final PN532Protocol protocol;
    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile UsbDevice targetDevice;

    public PN532Handler(USBConnection usbConnection, PN532Protocol protocol) {
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
            callback.onEvent(new NFCEvent.Error("No PN532 USB device selected", NFCSource.PN532));
            return;
        }
        if (!usbConnection.connect(device)) {
            callback.onEvent(new NFCEvent.Error("Cannot open PN532 USB connection", NFCSource.PN532));
            return;
        }
        if (!protocol.configureForDevice(device)) {
            usbConnection.disconnect();
            callback.onEvent(new NFCEvent.Error("Cannot configure PN532 USB endpoints", NFCSource.PN532));
            return;
        }
        capturing.set(true);
        worker.execute(() -> {
            try {
                Log.d(TAG, "Starting PN532 capture loop");
                protocol.initRF();
                while (capturing.get()) {
                    CardInfo card = protocol.pollCard(1000);
                    if (card == null) {
                        continue;
                    }
                    callback.onEvent(new NFCEvent.CardDetected(
                            card.getType(),
                            card.getUid(),
                            NFCSource.PN532,
                            System.currentTimeMillis()
                    ));
                }
            } catch (Exception e) {
                callback.onEvent(new NFCEvent.Error(e.getMessage() != null ? e.getMessage() : "Unknown PN532 error", NFCSource.PN532));
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
