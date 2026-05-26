package de.tu_darmstadt.seemoo.nfcgate.reader.db;

import android.content.Context;
import android.util.Base64;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.security.SecureRandom;

/**
 * Thread-safe provider for database passphrase.
 * Generates once and caches in memory + EncryptedSharedPreferences.
 */
public final class DatabasePassphraseProvider {
    private static final String PREF_FILE = "yitian_db_secrets";
    private static final String KEY_PASSPHRASE = "db_passphrase";
    private static final int PASSPHRASE_LENGTH = 32;

    private static final Object LOCK = new Object();
    private static volatile String cachedPassphrase;

    private DatabasePassphraseProvider() {}

    public static String getPassphrase(Context context) {
        // Fast path: already cached
        if (cachedPassphrase != null) {
            return cachedPassphrase;
        }

        synchronized (LOCK) {
            // Double-check after acquiring lock
            if (cachedPassphrase != null) {
                return cachedPassphrase;
            }

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
                    cachedPassphrase = existing;
                    return existing;
                }

                // Generate new passphrase
                byte[] raw = new byte[PASSPHRASE_LENGTH];
                new SecureRandom().nextBytes(raw);
                String passphrase = Base64.encodeToString(raw, Base64.NO_WRAP);
                prefs.edit().putString(KEY_PASSPHRASE, passphrase).apply();
                cachedPassphrase = passphrase;
                return passphrase;
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Secure storage unavailable for DB passphrase; refusing plaintext fallback.", e);
            }
        }
    }
}
