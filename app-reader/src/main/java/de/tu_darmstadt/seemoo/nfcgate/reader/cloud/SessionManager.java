package de.tu_darmstadt.seemoo.nfcgate.reader.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public final class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREF_CLOUD_SECRETS = "cloud_secrets";
    private static final String KEY_TOKEN = "cloud_token";
    private static final String KEY_TOKEN_EXPIRY = "cloud_token_expiry";
    private static final String KEY_ACCOUNT_ID = "cloud_account_id";
    private static final String KEY_PASSWORD = "cloud_password";
    private static final String KEY_SALT = "cloud_salt";
    private static final String KEY_COOLDOWN_UNTIL = "cloud_cooldown_until";

    private SessionManager() {}

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    private static SharedPreferences secretPrefs(Context context) {
        Context appContext = context.getApplicationContext();
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    appContext,
                    PREF_CLOUD_SECRETS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            Log.w(TAG, "Falling back to plain SharedPreferences for cloud secrets", e);
            return appContext.getSharedPreferences(PREF_CLOUD_SECRETS, Context.MODE_PRIVATE);
        }
    }

    public static void saveLogin(Context context, String token, long expiresAtMillis, String accountId, String password) {
        prefs(context).edit()
                .putString(KEY_TOKEN, token)
                .putLong(KEY_TOKEN_EXPIRY, expiresAtMillis)
                .putString(KEY_ACCOUNT_ID, accountId)
                .remove(KEY_PASSWORD)
                .remove(KEY_SALT)
                .apply();
        secretPrefs(context).edit().putString(KEY_PASSWORD, password).apply();
    }

    public static String getToken(Context context) {
        return prefs(context).getString(KEY_TOKEN, null);
    }

    public static String getPassword(Context context) {
        SharedPreferences secrets = secretPrefs(context);
        String password = secrets.getString(KEY_PASSWORD, "");
        if (!password.isEmpty()) return password;
        String legacy = prefs(context).getString(KEY_PASSWORD, "");
        if (!legacy.isEmpty()) {
            secrets.edit().putString(KEY_PASSWORD, legacy).apply();
            prefs(context).edit().remove(KEY_PASSWORD).apply();
            return legacy;
        }
        return "";
    }

    public static String getAccountId(Context context) {
        return prefs(context).getString(KEY_ACCOUNT_ID, "");
    }

    public static long getTokenExpiryMillis(Context context) {
        return prefs(context).getLong(KEY_TOKEN_EXPIRY, 0L);
    }

    public static void updateToken(Context context, String token, long expiresAtMillis) {
        prefs(context).edit().putString(KEY_TOKEN, token).putLong(KEY_TOKEN_EXPIRY, expiresAtMillis).apply();
    }

    public static boolean isLoggedIn(Context context) {
        String token = getToken(context);
        return token != null && !token.isEmpty();
    }

    public static boolean isTokenExpiringSoon(Context context) {
        return getTokenExpiryMillis(context) - System.currentTimeMillis() <= 5 * 60_000L;
    }

    public static void clear(Context context) {
        prefs(context).edit()
                .remove(KEY_TOKEN)
                .remove(KEY_TOKEN_EXPIRY)
                .remove(KEY_ACCOUNT_ID)
                .remove(KEY_PASSWORD)
                .remove(KEY_SALT)
                .remove(KEY_COOLDOWN_UNTIL)
                .apply();
        secretPrefs(context).edit().remove(KEY_PASSWORD).remove(KEY_SALT).apply();
    }

    public static void setSalt(Context context, String salt) {
        secretPrefs(context).edit().putString(KEY_SALT, salt).apply();
        prefs(context).edit().remove(KEY_SALT).apply();
    }

    public static String getSalt(Context context) {
        SharedPreferences secrets = secretPrefs(context);
        String salt = secrets.getString(KEY_SALT, "");
        if (!salt.isEmpty()) return salt;
        String legacy = prefs(context).getString(KEY_SALT, "");
        if (!legacy.isEmpty()) {
            secrets.edit().putString(KEY_SALT, legacy).apply();
            prefs(context).edit().remove(KEY_SALT).apply();
            return legacy;
        }
        return "";
    }

    public static void setCooldown(Context context, long cooldownUntilMillis) {
        prefs(context).edit().putLong(KEY_COOLDOWN_UNTIL, cooldownUntilMillis).apply();
    }

    public static long getCooldownRemainingSeconds(Context context) {
        long remain = prefs(context).getLong(KEY_COOLDOWN_UNTIL, 0L) - System.currentTimeMillis();
        return remain <= 0 ? 0 : (long) Math.ceil(remain / 1000.0);
    }
}
