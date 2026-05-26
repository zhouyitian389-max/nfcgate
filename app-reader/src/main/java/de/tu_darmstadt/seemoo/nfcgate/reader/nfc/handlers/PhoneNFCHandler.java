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
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.emv.MifareReader;

/**
 * Built-in phone NFC handler. Uses NfcAdapter ReaderMode if a hosting Activity is provided.
 * If no Activity is available, this handler will emit an Error event explaining the situation
 * (so the UI shows something meaningful instead of silently doing nothing).
 */
public class PhoneNFCHandler implements NFCHandler {
    private static final String TAG = "PhoneNFCHandler";

    private enum ReaderState {
        IDLE,
        READER_MODE_ENABLED,
        CAPTURING
    }

    private Activity activity;
    private final NfcAdapter adapter;
    private EventCallback callback;
    private ReaderState state = ReaderState.IDLE;

    public PhoneNFCHandler(Context context) {
        this.activity = context instanceof Activity ? (Activity) context : null;
        this.adapter = NfcAdapter.getDefaultAdapter(context);
    }

    public void bindActivity(Activity activity) {
        this.activity = activity;
    }

    @Override
    public void startCapture(EventCallback callback) {
        this.callback = callback;
        transitionTo(ReaderState.IDLE);
        if (adapter == null) {
            callback.onEvent(new NFCEvent.Error("Phone has no NFC adapter", NFCSource.PHONE));
            return;
        }
        if (!adapter.isEnabled()) {
            callback.onEvent(new NFCEvent.Error("NFC is disabled in system settings", NFCSource.PHONE));
            return;
        }
        if (activity == null) {
            callback.onEvent(new NFCEvent.Error(
                    "Phone NFC requires a foreground Activity.",
                    NFCSource.PHONE));
            return;
        }
        enableReaderMode(activity);
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
        transitionTo(ReaderState.READER_MODE_ENABLED);
    }

    private void onTag(Tag tag) {
        if (callback == null) return;
        transitionTo(ReaderState.CAPTURING);

        // Attempt a full EMV read first; fall back to UID-only if the card
        // does not support ISO-DEP or any APDU step fails.
        try {
            EMVReader.EMVCard emvCard = EMVReader.readCard(tag);
            String type = emvCard.brand != null ? emvCard.brand : "EMV";
            callback.onEvent(new NFCEvent.CardDetected(
                    type, tag.getId(), NFCSource.PHONE, System.currentTimeMillis(), emvCard));
        } catch (Exception e) {
            Log.d(TAG, "EMV read failed, trying Mifare/NDEF fallback: " + e.getMessage());
            String type = detectFallbackType(tag);
            callback.onEvent(new NFCEvent.CardDetected(
                    type, tag.getId(), NFCSource.PHONE, System.currentTimeMillis()));
        }
        transitionTo(ReaderState.READER_MODE_ENABLED);
    }

    private String detectFallbackType(Tag tag) {
        try {
            MifareReader.readMifareClassic(tag);
            return "MifareClassic";
        } catch (Exception ignored) { }
        try {
            MifareReader.readMifareUltralight(tag);
            return "MifareUltralight";
        } catch (Exception ignored) { }
        try {
            MifareReader.readNDEF(tag);
            return "NDEF";
        } catch (Exception ignored) { }
        String type = tag.getTechList().length > 0 ? tag.getTechList()[0] : "UNKNOWN";
        int dot = type.lastIndexOf('.');
        if (dot >= 0) type = type.substring(dot + 1);
        return type;
    }

    @Override
    public void stopCapture() {
        Activity currentActivity = activity;
        if (adapter != null && currentActivity != null) {
            try {
                adapter.disableReaderMode(currentActivity);
            } catch (Exception ignored) { /* activity may already be destroyed */ }
        }
        transitionTo(ReaderState.IDLE);
        callback = null;
    }

    private void transitionTo(ReaderState nextState) {
        if (state == nextState) {
            return;
        }
        Log.d(TAG, "State transition: " + state + " -> " + nextState);
        state = nextState;
    }
}
