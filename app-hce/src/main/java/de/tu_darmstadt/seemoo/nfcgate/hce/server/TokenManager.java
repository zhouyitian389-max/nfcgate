package de.tu_darmstadt.seemoo.nfcgate.hce.server;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;

public class TokenManager {
    private static final String PREFS = "hce_receive";
    private static final String KEY_TOKEN = "token";

    private final SharedPreferences preferences;

    public TokenManager(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getToken() {
        String token = preferences.getString(KEY_TOKEN, null);
        if (token == null) {
            token = regenerate();
        }
        return token;
    }

    public String regenerate() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        String token = builder.toString();
        preferences.edit().putString(KEY_TOKEN, token).apply();
        return token;
    }
}
