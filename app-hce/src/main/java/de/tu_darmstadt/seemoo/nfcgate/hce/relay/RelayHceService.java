package de.tu_darmstadt.seemoo.nfcgate.hce.relay;

import android.content.SharedPreferences;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class RelayHceService extends HostApduService implements WebSocketRelayClient.Listener {
    private static final String TAG = "RelayHceService";
    private static final byte[] STATUS_TIMEOUT = new byte[]{(byte) 0x64, 0x00};
    private static final byte[] STATUS_ERROR = new byte[]{(byte) 0x6F, 0x00};
    private static final long RESPONSE_TIMEOUT_SECONDS = 30L;

    private final AtomicReference<CountDownLatch> pendingLatch = new AtomicReference<>();
    private final AtomicReference<byte[]> pendingResponse = new AtomicReference<>();
    private WebSocketRelayClient relayClient;

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String wsUrl = preferences.getString("relay_ws_url", "");
        String jwt = preferences.getString("relay_jwt", "");
        String sessionId = preferences.getString("relay_session_id", "");
        relayClient = new WebSocketRelayClient(wsUrl, jwt, sessionId, this);
        relayClient.connect();
    }

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        if (relayClient == null) {
            return STATUS_ERROR;
        }

        CountDownLatch latch = new CountDownLatch(1);
        pendingLatch.set(latch);
        pendingResponse.set(null);
        boolean sent = relayClient.sendApduCommand(bytesToHex(commandApdu));
        if (!sent) {
            pendingLatch.set(null);
            return STATUS_ERROR;
        }

        try {
            boolean complete = latch.await(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!complete) {
                return STATUS_TIMEOUT;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return STATUS_ERROR;
        } finally {
            pendingLatch.set(null);
        }

        byte[] response = pendingResponse.getAndSet(null);
        return response != null ? response : STATUS_ERROR;
    }

    @Override
    public void onApduResponse(byte[] response) {
        pendingResponse.set(response);
        CountDownLatch latch = pendingLatch.get();
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    public void onDeactivated(int reason) {
        Log.i(TAG, "HCE deactivated, reason=" + reason);
    }

    @Override
    public void onDestroy() {
        if (relayClient != null) {
            relayClient.close();
        }
        super.onDestroy();
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02X", b));
        }
        return sb.toString();
    }
}
