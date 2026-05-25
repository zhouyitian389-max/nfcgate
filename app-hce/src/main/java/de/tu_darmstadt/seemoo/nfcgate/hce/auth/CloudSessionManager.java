package de.tu_darmstadt.seemoo.nfcgate.hce.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public final class CloudSessionManager {
    private static final String TAG = "CloudSessionManager";
    private static final String PREF_FILE = "yitian_cloud_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_ACCOUNT_ID = "account_id";

    private CloudSessionManager() {}

    private static SharedPreferences prefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    PREF_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            Log.w(TAG, "Encrypted prefs unavailable, using plain prefs fallback", e);
            return context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        }
    }

    public static void saveSession(Context context, String token, String accountId) {
        prefs(context).edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_ACCOUNT_ID, accountId)
                .apply();
    }

    public static String getToken(Context context) {
        return prefs(context).getString(KEY_TOKEN, null);
    }

    public static boolean hasToken(Context context) {
        String token = getToken(context);
        return token != null && !token.trim().isEmpty();
    }

    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }
}
