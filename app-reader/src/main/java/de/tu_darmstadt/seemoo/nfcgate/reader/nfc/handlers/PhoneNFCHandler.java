package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers;

import android.content.Context;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCHandler;

public class PhoneNFCHandler implements NFCHandler {
    @SuppressWarnings("unused")
    private final Context context;

    public PhoneNFCHandler(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void startCapture(EventCallback callback) {
        // Placeholder: phone built-in NFC capture is integrated in later reader milestones.
    }

    @Override
    public void stopCapture() {
        // no-op
    }
}
