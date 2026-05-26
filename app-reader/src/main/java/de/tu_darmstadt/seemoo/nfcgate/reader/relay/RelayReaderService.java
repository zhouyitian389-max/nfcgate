package de.tu_darmstadt.seemoo.nfcgate.reader.relay;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class RelayReaderService implements NfcAdapter.ReaderCallback, WebSocketRelayClient.Listener {
    private static final String TAG = "RelayReaderService";
    private final NfcAdapter nfcAdapter;
    private final WebSocketRelayClient relayClient;
    private final AtomicReference<IsoDep> activeIsoDep = new AtomicReference<>();
    private volatile boolean active = false;

    public RelayReaderService(NfcAdapter nfcAdapter, String relayUrl, String jwt, String sessionId) {
        this.nfcAdapter = nfcAdapter;
        this.relayClient = new WebSocketRelayClient(relayUrl, jwt, sessionId, this);
    }

    public void start(Activity activity) {
        if (nfcAdapter == null || activity == null) {
            Log.w(TAG, "Unable to start relay mode: missing adapter/activity");
            return;
        }
        active = true;
        Bundle options = new Bundle();
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);
        nfcAdapter.enableReaderMode(
                activity,
                this,
                NfcAdapter.FLAG_READER_NFC_A
                        | NfcAdapter.FLAG_READER_NFC_B
                        | NfcAdapter.FLAG_READER_NFC_F
                        | NfcAdapter.FLAG_READER_NFC_V
                        | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                options
        );
        relayClient.connect();
    }

    public void stop(Activity activity) {
        active = false;
        if (nfcAdapter != null && activity != null) {
            nfcAdapter.disableReaderMode(activity);
        }
        closeIsoDep();
        relayClient.close();
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            return;
        }
        try {
            isoDep.connect();
            activeIsoDep.set(isoDep);
            Log.i(TAG, "IsoDep connected for relay");
        } catch (IOException e) {
            Log.e(TAG, "Failed to connect IsoDep", e);
            closeIsoDep();
        }
    }

    @Override
    public void onApduCommand(String sessionId, byte[] command) {
        if (!active) {
            return;
        }
        IsoDep isoDep = activeIsoDep.get();
        if (isoDep == null || !isoDep.isConnected()) {
            relayClient.sendApduResponse(sessionId, new byte[]{(byte) 0x6A, (byte) 0x82});
            return;
        }

        try {
            byte[] response = isoDep.transceive(command);
            relayClient.sendApduResponse(sessionId, response);
        } catch (IOException e) {
            Log.e(TAG, "IsoDep transceive failed", e);
            relayClient.sendApduResponse(sessionId, new byte[]{(byte) 0x6F, 0x00});
            closeIsoDep();
        }
    }

    @Override
    public void onSessionEnded() {
        closeIsoDep();
    }

    private void closeIsoDep() {
        IsoDep isoDep = activeIsoDep.getAndSet(null);
        if (isoDep != null) {
            try {
                isoDep.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed closing IsoDep", e);
            }
        }
    }
}
