package de.tu_darmstadt.seemoo.nfcgate.reader.db;

import android.content.Context;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;

/**
 * Thread-safe provider for database passphrase.
 * Generates once and caches in memory + EncryptedSharedPreferences.
 */
public final class DatabasePassphraseProvider {
    private static final String TAG = "DatabasePassphraseProvider";
    private static final String PREF_FILE = "yitian_db_secrets";
    private static final String KEY_PASSPHRASE = "db_passphrase";
    private static final int PASSPHRASE_LENGTH = 32;
    private static final String OBFUSCATED_PREFIX = "obf:";

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
                Log.e(TAG, "EncryptedSharedPreferences unavailable for DB passphrase storage. " +
                        "Falling back to obfuscated plain SharedPreferences weakens protection " +
                        "and must only reuse an existing fallback value or create a single " +
                        "obfuscated value when no encrypted store exists.", e);
                Context appContext = context.getApplicationContext();
                boolean prefsFileExists = encryptedPrefsFileExists(appContext);
                android.content.SharedPreferences prefs = appContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
                String existing = prefs.getString(KEY_PASSPHRASE, null);
                if (existing != null && !existing.isEmpty()) {
                    String decoded = decodeFallbackPassphrase(appContext, existing);
                    if (decoded != null && !decoded.isEmpty()) {
                        cachedPassphrase = decoded;
                        return decoded;
                    }
                }
                if (prefsFileExists) {
                    throw new IllegalStateException("Encrypted DB passphrase store exists but cannot be read; refusing to create a second fallback copy.");
                }
                byte[] raw = new byte[PASSPHRASE_LENGTH];
                new SecureRandom().nextBytes(raw);
                String passphrase = Base64.encodeToString(raw, Base64.NO_WRAP);
                // This is obfuscation only; it is not equivalent to encrypted storage.
                prefs.edit().putString(KEY_PASSPHRASE, encodeFallbackPassphrase(appContext, passphrase)).apply();
                cachedPassphrase = passphrase;
                return passphrase;
            }
        }
    }

    private static boolean encryptedPrefsFileExists(Context context) {
        return new java.io.File(context.getApplicationInfo().dataDir + "/shared_prefs/" + PREF_FILE + ".xml").exists();
    }

    private static String encodeFallbackPassphrase(Context context, String passphrase) {
        String prefixed = getFallbackPrefix(context) + passphrase;
        return OBFUSCATED_PREFIX + Base64.encodeToString(
                prefixed.getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP);
    }

    private static String decodeFallbackPassphrase(Context context, String storedValue) {
        if (!storedValue.startsWith(OBFUSCATED_PREFIX)) {
            String legacyPassphrase = storedValue.trim();
            if (!legacyPassphrase.isEmpty()) {
                context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_PASSPHRASE, encodeFallbackPassphrase(context, legacyPassphrase))
                        .apply();
            }
            return legacyPassphrase;
        }
        try {
            byte[] decoded = Base64.decode(
                    storedValue.substring(OBFUSCATED_PREFIX.length()),
                    Base64.NO_WRAP);
            String prefixed = new String(decoded, StandardCharsets.UTF_8);
            String expectedPrefix = getFallbackPrefix(context);
            if (!prefixed.startsWith(expectedPrefix)) {
                throw new IllegalStateException("Stored fallback passphrase prefix mismatch");
            }
            return prefixed.substring(expectedPrefix.length());
        } catch (Exception e) {
            Log.e(TAG, "Failed to decode fallback DB passphrase", e);
            return null;
        }
    }

    private static String getFallbackPrefix(Context context) {
        String androidId = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        if (androidId == null || androidId.isEmpty()) {
            androidId = "unknown-device";
        }
        return context.getPackageName() + ":" + androidId + ":";
    }
}
