package de.tu_darmstadt.seemoo.nfcgate.hce.service;

import android.content.Context;
import android.util.Base64;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.security.SecureRandom;

public final class HttpAuthTokenProvider {
    private static final String PREF_FILE = "yitian_http_auth";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final int TOKEN_BYTES = 24;

    private HttpAuthTokenProvider() {}

    public static String getOrCreateToken(Context context) {
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
            String existing = prefs.getString(KEY_AUTH_TOKEN, null);
            if (existing != null && !existing.isEmpty()) {
                return existing;
            }
            byte[] raw = new byte[TOKEN_BYTES];
            new SecureRandom().nextBytes(raw);
            String token = Base64.encodeToString(raw, Base64.NO_WRAP);
            prefs.edit().putString(KEY_AUTH_TOKEN, token).apply();
            return token;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to access encrypted HTTP auth token storage", e);
        }
    }
}
