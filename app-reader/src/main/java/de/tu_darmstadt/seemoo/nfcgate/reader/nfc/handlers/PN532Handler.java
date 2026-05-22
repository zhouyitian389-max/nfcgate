package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers;

import android.content.Context;

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
    private final USBConnection usbConnection;
    private final PN532Protocol protocol;
    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public PN532Handler(Context context, PN532Protocol protocol) {
        this.usbConnection = new USBConnection(context);
        this.protocol = protocol;
    }

    @Override
    public void startCapture(EventCallback callback) {
        capturing.set(true);
        worker.execute(() -> {
            try {
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
            }
        });
    }

    @Override
    public void stopCapture() {
        capturing.set(false);
        usbConnection.disconnect();
    }
}
