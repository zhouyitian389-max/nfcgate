package de.tu_darmstadt.seemoo.nfcgate.hce.cloud;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public final class SessionManager {
    private static final String KEY_TOKEN = "cloud_token";
    private static final String KEY_TOKEN_EXPIRY = "cloud_token_expiry";
    private static final String KEY_PASSWORD = "cloud_password";
    private static final String KEY_SALT = "cloud_salt";
    private static final String KEY_ACCOUNT_ID = "cloud_account_id";
    private static final String KEY_COOLDOWN_UNTIL = "cloud_cooldown_until";

    private SessionManager() {}

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public static void saveLogin(Context context, String token, long expiresAtMillis, String accountId, String password) {
        prefs(context).edit().putString(KEY_TOKEN, token).putLong(KEY_TOKEN_EXPIRY, expiresAtMillis)
                .putString(KEY_ACCOUNT_ID, accountId).putString(KEY_PASSWORD, password).apply();
    }

    public static String getToken(Context context) { return prefs(context).getString(KEY_TOKEN, null); }
    public static String getPassword(Context context) { return prefs(context).getString(KEY_PASSWORD, ""); }
    public static String getSalt(Context context) { return prefs(context).getString(KEY_SALT, ""); }
    public static void setSalt(Context context, String salt) { prefs(context).edit().putString(KEY_SALT, salt).apply(); }
    public static boolean isLoggedIn(Context context) { String t = getToken(context); return t != null && !t.isEmpty(); }
    public static long getTokenExpiryMillis(Context context) { return prefs(context).getLong(KEY_TOKEN_EXPIRY, 0L); }
    public static boolean isTokenExpiringSoon(Context context) { return getTokenExpiryMillis(context) - System.currentTimeMillis() <= 5 * 60_000L; }
    public static void updateToken(Context context, String token, long expiresAtMillis) {
        prefs(context).edit().putString(KEY_TOKEN, token).putLong(KEY_TOKEN_EXPIRY, expiresAtMillis).apply();
    }
    public static void clear(Context context) {
        prefs(context).edit().remove(KEY_TOKEN).remove(KEY_TOKEN_EXPIRY).remove(KEY_PASSWORD).remove(KEY_SALT).remove(KEY_ACCOUNT_ID).remove(KEY_COOLDOWN_UNTIL).apply();
    }
    public static void setCooldown(Context context, long until) { prefs(context).edit().putLong(KEY_COOLDOWN_UNTIL, until).apply(); }
    public static long getCooldownRemainingSeconds(Context context) {
        long remain = prefs(context).getLong(KEY_COOLDOWN_UNTIL, 0L) - System.currentTimeMillis();
        return remain <= 0 ? 0 : (long) Math.ceil(remain / 1000.0);
    }
}
