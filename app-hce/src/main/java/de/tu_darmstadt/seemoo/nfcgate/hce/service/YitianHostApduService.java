package de.tu_darmstadt.seemoo.nfcgate.hce.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.cardemulation.HostApduService;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.nio.charset.StandardCharsets;

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
    public static final String ACTION_SELECTION_CHANGED = "de.tu_darmstadt.seemoo.nfcgate.hce.ACTION_SELECTION_CHANGED";
    private static final byte[] SW_OK = {(byte) 0x90, (byte) 0x00};
    private static final byte[] SW_NOT_FOUND = {(byte) 0x6A, (byte) 0x82};
    private static final byte[] SELECT_HEADER = {(byte) 0x00, (byte) 0xA4, (byte) 0x04, (byte) 0x00};
    private static final byte[] AID = hex("F0010203040506");
    private final Object cacheLock = new Object();
    private volatile CardEntity cachedSelectedCard;
    private volatile boolean isSelectionCacheLoaded;
    private final BroadcastReceiver selectionChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            invalidateSelectedCardCache();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter(ACTION_SELECTION_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(selectionChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(selectionChangedReceiver, filter);
        }
    }

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        if (commandApdu == null || commandApdu.length < 4) return SW_NOT_FOUND;
        if (isSelectAid(commandApdu)) {
            CardEntity selected = getSelectedCard();
            if (selected == null) {
                Log.w(TAG, "SELECT AID received but no card selected");
                return concat("YITIAN-NFC".getBytes(StandardCharsets.US_ASCII), SW_OK);
            }
            String payload = "YITIAN|" + (selected.brand == null ? "" : selected.brand)
                    + "|" + (selected.last4());
            return concat(payload.getBytes(StandardCharsets.US_ASCII), SW_OK);
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
        super.onDestroy();
    }

    private CardEntity getSelectedCard() {
        if (!isSelectionCacheLoaded) {
            synchronized (cacheLock) {
                if (!isSelectionCacheLoaded) {
                    cachedSelectedCard = CardDatabase.getInstance(this).cardDao().getSelected();
                    isSelectionCacheLoaded = true;
                }
            }
        }
        return cachedSelectedCard;
    }

    private void invalidateSelectedCardCache() {
        synchronized (cacheLock) {
            isSelectionCacheLoaded = false;
            cachedSelectedCard = null;
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

    private static byte[] hex(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
