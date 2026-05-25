package de.tu_darmstadt.seemoo.nfcgate.hce.cloud;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardDatabase;
import de.tu_darmstadt.seemoo.nfcgate.hce.service.CloudSyncService;

public final class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String SECURE_PREFS_FILE = "cloud_session_secrets";
    private static final String KEY_TOKEN = "cloud_token";
    private static final String KEY_TOKEN_EXPIRY = "cloud_token_expiry";
    private static final String KEY_PASSWORD = "cloud_password";
    private static final String KEY_SALT = "cloud_salt";
    private static final String KEY_ACCOUNT_ID = "cloud_account_id";
    private static final String KEY_COOLDOWN_UNTIL = "cloud_cooldown_until";
    private static final ExecutorService DB_CLEAR_EXECUTOR = Executors.newSingleThreadExecutor();

    private SessionManager() {}

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    private static SharedPreferences securePrefs(Context context) {
        Context appContext = context.getApplicationContext();
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    appContext,
                    SECURE_PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.w(TAG, "Falling back to plain SharedPreferences for cloud secrets", e);
            return appContext.getSharedPreferences(SECURE_PREFS_FILE, Context.MODE_PRIVATE);
        }
    }

    public static void saveLogin(Context context, String token, long expiresAtMillis, String accountId, String password) {
        prefs(context).edit().putString(KEY_TOKEN, token).putLong(KEY_TOKEN_EXPIRY, expiresAtMillis)
                .putString(KEY_ACCOUNT_ID, accountId).remove(KEY_PASSWORD).remove(KEY_SALT).apply();
        securePrefs(context).edit().putString(KEY_PASSWORD, password).apply();
    }

    public static String getToken(Context context) { return prefs(context).getString(KEY_TOKEN, null); }
    public static String getPassword(Context context) {
        String value = securePrefs(context).getString(KEY_PASSWORD, "");
        if (value == null || value.isEmpty()) {
            String legacy = prefs(context).getString(KEY_PASSWORD, "");
            if (legacy != null && !legacy.isEmpty()) {
                securePrefs(context).edit().putString(KEY_PASSWORD, legacy).apply();
                prefs(context).edit().remove(KEY_PASSWORD).apply();
                return legacy;
            }
            return "";
        }
        return value;
    }
    public static String getSalt(Context context) {
        String value = securePrefs(context).getString(KEY_SALT, "");
        if (value == null || value.isEmpty()) {
            String legacy = prefs(context).getString(KEY_SALT, "");
            if (legacy != null && !legacy.isEmpty()) {
                securePrefs(context).edit().putString(KEY_SALT, legacy).apply();
                prefs(context).edit().remove(KEY_SALT).apply();
                return legacy;
            }
            return "";
        }
        return value;
    }
    public static void setSalt(Context context, String salt) {
        securePrefs(context).edit().putString(KEY_SALT, salt).apply();
        prefs(context).edit().remove(KEY_SALT).apply();
    }
    public static boolean isLoggedIn(Context context) { String t = getToken(context); return t != null && !t.isEmpty(); }
    public static long getTokenExpiryMillis(Context context) { return prefs(context).getLong(KEY_TOKEN_EXPIRY, 0L); }
    public static boolean isTokenExpiringSoon(Context context) { return getTokenExpiryMillis(context) - System.currentTimeMillis() <= 5 * 60_000L; }
    public static void updateToken(Context context, String token, long expiresAtMillis) {
        prefs(context).edit().putString(KEY_TOKEN, token).putLong(KEY_TOKEN_EXPIRY, expiresAtMillis).apply();
    }
    public static void clear(Context context) {
        prefs(context).edit().remove(KEY_TOKEN).remove(KEY_TOKEN_EXPIRY).remove(KEY_PASSWORD).remove(KEY_SALT).remove(KEY_ACCOUNT_ID).remove(KEY_COOLDOWN_UNTIL).apply();
        securePrefs(context).edit().remove(KEY_PASSWORD).remove(KEY_SALT).apply();
    }
    public static void logout(Context context) {
        Context appContext = context.getApplicationContext();
        clear(appContext);
        Intent stopIntent = new Intent(appContext, CloudSyncService.class);
        stopIntent.setAction(CloudSyncService.ACTION_STOP);
        try {
            appContext.startService(stopIntent);
        } catch (Exception ignored) {
        }
        DB_CLEAR_EXECUTOR.execute(() -> {
            CardDatabase db = CardDatabase.getInstance(appContext);
            db.cardDao().deleteAll();
            db.operationLogDao().clearAll();
        });
    }
    public static void setCooldown(Context context, long until) { prefs(context).edit().putLong(KEY_COOLDOWN_UNTIL, until).apply(); }
    public static long getCooldownRemainingSeconds(Context context) {
        long remain = prefs(context).getLong(KEY_COOLDOWN_UNTIL, 0L) - System.currentTimeMillis();
        return remain <= 0 ? 0 : (long) Math.ceil(remain / 1000.0);
    }
}
