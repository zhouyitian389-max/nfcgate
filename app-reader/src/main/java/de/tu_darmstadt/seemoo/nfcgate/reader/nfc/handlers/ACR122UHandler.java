package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers;

import android.content.Context;

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
    private final USBConnection usbConnection;
    private final ACR122UProtocol protocol;
    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public ACR122UHandler(Context context, ACR122UProtocol protocol) {
        this.usbConnection = new USBConnection(context);
        this.protocol = protocol;
    }

    @Override
    public void startCapture(EventCallback callback) {
        capturing.set(true);
        worker.execute(() -> {
            try {
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
            }
        });
    }

    @Override
    public void stopCapture() {
        capturing.set(false);
        usbConnection.disconnect();
    }
}
