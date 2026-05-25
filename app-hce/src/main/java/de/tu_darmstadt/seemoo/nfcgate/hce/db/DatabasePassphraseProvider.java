package de.tu_darmstadt.seemoo.nfcgate.hce.db;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.security.SecureRandom;

/**
 * Provides a stable database passphrase for SQLCipher encryption.
 * The passphrase is generated once and stored in EncryptedSharedPreferences.
 */
public final class DatabasePassphraseProvider {
    private static final String TAG = "DatabasePassphraseProvider";
    private static final String PREF_FILE = "yitian_hce_db_secrets";
    private static final String KEY_PASSPHRASE = "db_passphrase";
    private static final int PASSPHRASE_LENGTH = 32;

    private DatabasePassphraseProvider() {}

    public static String getPassphrase(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            android.content.SharedPreferences prefs = EncryptedSharedPreferences.create(
                    context,
                    PREF_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);

            String existing = prefs.getString(KEY_PASSPHRASE, null);
            if (existing != null && !existing.isEmpty()) {
                return existing;
            }

            byte[] raw = new byte[PASSPHRASE_LENGTH];
            new SecureRandom().nextBytes(raw);
            String passphrase = Base64.encodeToString(raw, Base64.NO_WRAP);
            prefs.edit().putString(KEY_PASSPHRASE, passphrase).apply();
            return passphrase;
        } catch (Exception e) {
            Log.w(TAG, "Falling back to plain SharedPreferences for DB passphrase storage", e);
            android.content.SharedPreferences prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
            String existing = prefs.getString(KEY_PASSPHRASE, null);
            if (existing != null && !existing.isEmpty()) {
                return existing;
            }
            byte[] raw = new byte[PASSPHRASE_LENGTH];
            new SecureRandom().nextBytes(raw);
            String passphrase = Base64.encodeToString(raw, Base64.NO_WRAP);
            prefs.edit().putString(KEY_PASSPHRASE, passphrase).apply();
            return passphrase;
        }
    }
}
