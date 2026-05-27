package de.tu_darmstadt.seemoo.nfcgate.hce.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.nfc.cardemulation.HostApduService;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import de.tu_darmstadt.seemoo.nfcgate.hce.BuildConfig;
import de.tu_darmstadt.seemoo.nfcgate.hce.SplashActivity;
import de.tu_darmstadt.seemoo.nfcgate.hce.cloud.SessionManager;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardDatabase;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardEntity;
import de.tu_darmstadt.seemoo.nfcgate.hce.relay.WebSocketRelayClient;

/**
 * Minimal HCE emulator that returns track2 data of the currently selected card
 * when relay is unavailable and a SELECT command is received.
 *
 * NOTE: This is for demonstration / development use. Real EMV emulation
 * requires a full APDU state machine and contactless kernel logic which
 * is outside the scope of this offline build.
 */
public class YitianHostApduService extends HostApduService {
    private static final String TAG = "YitianHCE";
    private static final String PREF_LOCK_UNTIL_ELAPSED = "pin_lock_until_elapsed";
    private static final String PREF_LOCK_UNTIL_WALL = "pin_lock_until_wall";
    private static final long PIN_LOCK_CACHE_MS = 1000L;
    private static final byte[] SW_SECURITY_NOT_SATISFIED = {(byte) 0x69, (byte) 0x83};
    public static final String ACTION_SELECTION_CHANGED = "de.tu_darmstadt.seemoo.nfcgate.hce.ACTION_SELECTION_CHANGED";
    public static final String ACTION_PIN_LOCK_CHANGED = "de.tu_darmstadt.seemoo.nfcgate.hce.ACTION_PIN_LOCK_CHANGED";
    private static final byte[] SW_OK = {(byte) 0x90, (byte) 0x00};
    private static final byte[] SW_NOT_FOUND = {(byte) 0x6A, (byte) 0x82};
    private static final byte[] SW_TIMEOUT = {(byte) 0x64, (byte) 0x00};
    private static final byte[] SELECT_HEADER_PREFIX = {(byte) 0x00, (byte) 0xA4, (byte) 0x04};
    private static final long RELAY_TIMEOUT_SECONDS = 4L;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final AtomicReference<CountDownLatch> relayPendingLatch = new AtomicReference<>();
    private final AtomicReference<byte[]> relayPendingResponse = new AtomicReference<>();
    private volatile CardEntity cachedCard;
    private volatile boolean cachedPinLocked;
    private volatile long pinLockCacheExpiryElapsed;
    private volatile WebSocketRelayClient relayClient;
    private final BroadcastReceiver selectionChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent != null ? intent.getAction() : null;
            if (ACTION_SELECTION_CHANGED.equals(action)) {
                refreshCachedCardAsync();
            } else if (ACTION_PIN_LOCK_CHANGED.equals(action)) {
                refreshPinLockCache();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        refreshPinLockCache();
        refreshCachedCardSync(500);
        initRelayClient();
        IntentFilter filter = new IntentFilter(HttpReceiverService.BROADCAST_STATE);
        filter.addAction(ACTION_SELECTION_CHANGED);
        filter.addAction(ACTION_PIN_LOCK_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(selectionChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(selectionChangedReceiver, filter);
        }
    }

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        if (commandApdu == null || commandApdu.length < 4) return SW_NOT_FOUND;
        if (isPinLocked()) {
            Log.w(TAG, "Rejecting APDU while PIN is locked");
            return SW_SECURITY_NOT_SATISFIED;
        }
        byte[] relayResponse = relayTransceive(commandApdu);
        if (relayResponse != null) {
            return relayResponse;
        }
        CardEntity selected = cachedCard;
        if (selected == null) {
            selected = refreshCachedCardSync(250);
        }
        if (selected == null) {
            Log.w(TAG, "APDU received but no card selected");
            return SW_NOT_FOUND;
        }
        if (isSelectCommand(commandApdu)) {
            String payload = selected.track2 == null ? "" : selected.track2.trim();
            byte[] track2Bytes = hexToBytes(payload);
            if (track2Bytes.length == 0) return SW_NOT_FOUND;
            return concat(track2Bytes, SW_OK);
        }
        return SW_OK;
    }

    @Override
    public void onDeactivated(int reason) {
        Log.d(TAG, "HCE deactivated: " + reason);
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(selectionChangedReceiver);
        } catch (Exception ignored) {
        }
        if (relayClient != null) {
            relayClient.close();
            relayClient = null;
        }
        dbExecutor.shutdownNow();
        super.onDestroy();
    }

    private void initRelayClient() {
        String jwt = SessionManager.getToken(this);
        if (jwt == null || jwt.trim().isEmpty()) {
            return;
        }
        relayClient = new WebSocketRelayClient(BuildConfig.WS_URL, jwt.trim(), "", response -> {
            relayPendingResponse.set(response);
            CountDownLatch latch = relayPendingLatch.get();
            if (latch != null) {
                latch.countDown();
            }
        });
        relayClient.connect();
    }

    private byte[] relayTransceive(byte[] commandApdu) {
        WebSocketRelayClient client = relayClient;
        if (client == null) {
            return null;
        }
        CountDownLatch latch = new CountDownLatch(1);
        relayPendingResponse.set(null);
        relayPendingLatch.set(latch);
        boolean sent = client.sendApduCommand(bytesToHex(commandApdu));
        if (!sent) {
            relayPendingLatch.set(null);
            return null;
        }
        try {
            boolean done = latch.await(RELAY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!done) {
                return SW_TIMEOUT;
            }
            byte[] response = relayPendingResponse.getAndSet(null);
            return response != null ? response : SW_NOT_FOUND;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SW_NOT_FOUND;
        } finally {
            relayPendingLatch.set(null);
        }
    }

    private void refreshCachedCardAsync() {
        if (dbExecutor.isShutdown()) {
            return;
        }
        dbExecutor.execute(() -> cachedCard = CardDatabase.getInstance(this).cardDao().getSelected());
    }

    private CardEntity refreshCachedCardSync(long timeoutMs) {
        if (dbExecutor.isShutdown()) {
            return cachedCard;
        }
        try {
            Future<CardEntity> future = dbExecutor.submit(() -> CardDatabase.getInstance(this).cardDao().getSelected());
            CardEntity selected = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            cachedCard = selected;
            return selected;
        } catch (Exception e) {
            Log.w(TAG, "Failed to refresh selected card synchronously", e);
            refreshCachedCardAsync();
            return cachedCard;
        }
    }

    private boolean isPinLocked() {
        if (SystemClock.elapsedRealtime() < pinLockCacheExpiryElapsed) {
            return cachedPinLocked;
        }
        refreshPinLockCache();
        return cachedPinLocked;
    }

    private void refreshPinLockCache() {
        if (dbExecutor.isShutdown()) {
            return;
        }
        dbExecutor.execute(() -> {
            SharedPreferences prefs = getPinPrefs();
            if (prefs == null) return; // fail-closed already set in getPinPrefs()
            long lockUntilElapsed = prefs.getLong(PREF_LOCK_UNTIL_ELAPSED, 0L);
            long lockUntilWall = prefs.getLong(PREF_LOCK_UNTIL_WALL, 0L);
            cachedPinLocked = SystemClock.elapsedRealtime() < lockUntilElapsed
                    || System.currentTimeMillis() < lockUntilWall;
            pinLockCacheExpiryElapsed = SystemClock.elapsedRealtime() + PIN_LOCK_CACHE_MS;
        });
    }

    private SharedPreferences getPinPrefs() {
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    this,
                    SplashActivity.PREF_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable, treating as PIN-locked (fail-closed)", e);
            cachedPinLocked = true;
            pinLockCacheExpiryElapsed = SystemClock.elapsedRealtime() + PIN_LOCK_CACHE_MS;
            return null;
        }
    }

    private boolean isSelectCommand(byte[] apdu) {
        if (apdu.length < SELECT_HEADER_PREFIX.length) return false;
        for (int i = 0; i < SELECT_HEADER_PREFIX.length; i++) if (apdu[i] != SELECT_HEADER_PREFIX[i]) return false;
        return true;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        hex = hex.replaceAll("[^0-9A-Fa-f]", "");
        int len = hex.length();
        if (len == 0) return new byte[0];
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len - 1; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static byte[] hex(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
