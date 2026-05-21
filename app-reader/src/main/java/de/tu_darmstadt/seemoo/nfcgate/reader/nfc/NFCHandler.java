package de.tu_darmstadt.seemoo.nfcgate.reader.nfc;

public interface NFCHandler {
    interface EventCallback {
        void onEvent(NFCEvent event);
    }

    void startCapture(EventCallback callback);

    void stopCapture();
}
