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

import de.tu_darmstadt.seemoo.nfcgate.hce.SplashActivity;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardDatabase;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardEntity;

/**
 * Minimal HCE emulator that responds to SELECT AID for F0010203040506 and
 * returns track2 data of the currently selected card as a custom payload.
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
    private static final byte[] SELECT_HEADER = {(byte) 0x00, (byte) 0xA4, (byte) 0x04, (byte) 0x00};
    private static final byte[] AID = hex("F0010203040506");
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private volatile CardEntity cachedCard;
    private volatile boolean cachedPinLocked;
    private volatile long pinLockCacheExpiryElapsed;
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
        refreshCachedCardAsync();
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
        CardEntity selected = cachedCard;
        if (selected == null) {
            Log.w(TAG, "APDU received but no card selected");
            return SW_NOT_FOUND;
        }
        if (isSelectAid(commandApdu)) {
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
        dbExecutor.shutdownNow();
        super.onDestroy();
    }

    private void refreshCachedCardAsync() {
        if (dbExecutor.isShutdown()) {
            return;
        }
        dbExecutor.execute(() -> cachedCard = CardDatabase.getInstance(this).cardDao().getSelected());
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

    private boolean isSelectAid(byte[] apdu) {
        if (apdu.length < 4 + AID.length) return false;
        for (int i = 0; i < SELECT_HEADER.length; i++) if (apdu[i] != SELECT_HEADER[i]) return false;
        int lc = apdu[4] & 0xFF;
        if (lc < AID.length || apdu.length < 5 + lc) return false;
        for (int i = 0; i < AID.length; i++) if (apdu[5 + i] != AID[i]) return false;
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
}
