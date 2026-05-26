package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers;

import android.app.Activity;
import android.content.Context;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.util.Log;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCEvent;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCHandler;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCSource;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.emv.EMVReader;

/**
 * Built-in phone NFC handler. Uses NfcAdapter ReaderMode if a hosting Activity is provided.
 * If no Activity is available, this handler will emit an Error event explaining the situation
 * (so the UI shows something meaningful instead of silently doing nothing).
 */
public class PhoneNFCHandler implements NFCHandler {
    private static final String TAG = "PhoneNFCHandler";

    private final Context context;
    private NfcAdapter adapter;
    private EventCallback callback;

    public PhoneNFCHandler(Context context) {
        this.context = context.getApplicationContext();
        this.adapter = NfcAdapter.getDefaultAdapter(this.context);
    }

    @Override
    public void startCapture(EventCallback callback) {
        this.callback = callback;
        if (adapter == null) {
            callback.onEvent(new NFCEvent.Error("Phone has no NFC adapter", NFCSource.PHONE));
            return;
        }
        if (!adapter.isEnabled()) {
            callback.onEvent(new NFCEvent.Error("NFC is disabled in system settings", NFCSource.PHONE));
            return;
        }
        if (!(context instanceof Activity)) {
            // We're holding an application context. Reader mode requires an Activity.
            // Emit a friendly event so user sees feedback instead of silence.
            callback.onEvent(new NFCEvent.Error(
                    "Phone NFC ready. Tap a card while app is foreground.",
                    NFCSource.PHONE));
            return;
        }
        enableReaderMode((Activity) context);
    }

    /** Public so MainActivity can re-bind ReaderMode using a real Activity reference. */
    public void enableReaderMode(Activity activity) {
        if (adapter == null || callback == null) return;
        int flags = NfcAdapter.FLAG_READER_NFC_A
                | NfcAdapter.FLAG_READER_NFC_B
                | NfcAdapter.FLAG_READER_NFC_F
                | NfcAdapter.FLAG_READER_NFC_V
                | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;
        Bundle extras = new Bundle();
        extras.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 500);
        adapter.enableReaderMode(activity, this::onTag, flags, extras);
    }

    private void onTag(Tag tag) {
        if (callback == null) return;

        // Attempt a full EMV read first; fall back to UID-only if the card
        // does not support ISO-DEP or any APDU step fails.
        try {
            EMVReader.EMVCard emvCard = EMVReader.readCard(tag);
            String type = emvCard.brand != null ? emvCard.brand : "EMV";
            callback.onEvent(new NFCEvent.CardDetected(
                    type, tag.getId(), NFCSource.PHONE, System.currentTimeMillis(), emvCard));
        } catch (Exception e) {
            Log.d(TAG, "EMV read failed, falling back to UID-only: " + e.getMessage());
            String type = tag.getTechList().length > 0 ? tag.getTechList()[0] : "UNKNOWN";
            // Strip android.nfc.tech. prefix for readability
            int dot = type.lastIndexOf('.');
            if (dot >= 0) type = type.substring(dot + 1);
            callback.onEvent(new NFCEvent.CardDetected(
                    type, tag.getId(), NFCSource.PHONE, System.currentTimeMillis()));
        }
    }

    @Override
    public void stopCapture() {
        if (adapter != null && context instanceof Activity) {
            try {
                adapter.disableReaderMode((Activity) context);
            } catch (Exception ignored) { /* activity may already be destroyed */ }
        }
        callback = null;
    }
}
