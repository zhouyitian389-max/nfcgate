package de.tu_darmstadt.seemoo.nfcgate.hce.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.security.SecureRandom;

public final class AuthTokenProvider {
    private static final String PREF_FILE = "yitian_hce_auth";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final int TOKEN_LENGTH = 32;

    private static final Object LOCK = new Object();
    private static volatile String cachedToken;

    private AuthTokenProvider() {}

    public static String getToken(Context context) {
        if (cachedToken != null) {
            return cachedToken;
        }

        synchronized (LOCK) {
            if (cachedToken != null) {
                return cachedToken;
            }

            try {
                MasterKey masterKey = new MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();

                SharedPreferences prefs = EncryptedSharedPreferences.create(
                        context,
                        PREF_FILE,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);

                String existing = prefs.getString(KEY_AUTH_TOKEN, null);
                if (existing != null && !existing.isEmpty()) {
                    cachedToken = existing;
                    return existing;
                }

                byte[] raw = new byte[TOKEN_LENGTH];
                new SecureRandom().nextBytes(raw);
                String token = Base64.encodeToString(raw, Base64.NO_WRAP);
                prefs.edit().putString(KEY_AUTH_TOKEN, token).apply();
                cachedToken = token;
                return token;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize auth token storage", e);
            }
        }
    }
}
