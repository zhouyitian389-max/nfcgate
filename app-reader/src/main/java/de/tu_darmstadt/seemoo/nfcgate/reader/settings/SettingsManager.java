package de.tu_darmstadt.seemoo.nfcgate.reader.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

public final class SettingsManager {
    public static final String KEY_SERVER_URL   = "server_url";
    public static final String KEY_YITIAN_HOST  = "yitian_host";
    public static final String KEY_YITIAN_PORT  = "yitian_port";
    public static final String KEY_AUTO_SCAN    = "auto_scan";
    public static final String KEY_SCAN_TIMEOUT = "scan_timeout";
    public static final String KEY_THEME        = "theme";
    public static final String KEY_HAPTIC       = "haptic_feedback";

    public static final String THEME_DARK   = "dark";
    public static final String THEME_LIGHT  = "light";
    public static final String THEME_SYSTEM = "system";

    /** Legacy relay URL kept for backward compatibility. */
    public static final String DEFAULT_SERVER_URL  = "https://relay.example.com/api/upload";
    public static final String DEFAULT_YITIAN_HOST = "192.168.1.100";
    public static final int    DEFAULT_YITIAN_PORT = 8080;

    private SettingsManager() {}

    public static SharedPreferences prefs(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    // ── YitianNFC direct delivery ──────────────────────────────────────────
    public static String getYitianHost(Context ctx) {
        return prefs(ctx).getString(KEY_YITIAN_HOST, DEFAULT_YITIAN_HOST);
    }

    public static int getYitianPort(Context ctx) {
        try {
            return Integer.parseInt(
                    prefs(ctx).getString(KEY_YITIAN_PORT, String.valueOf(DEFAULT_YITIAN_PORT)));
        } catch (NumberFormatException e) {
            return DEFAULT_YITIAN_PORT;
        }
    }

    // ── Legacy relay URL ───────────────────────────────────────────────────
    public static String getServerUrl(Context ctx) {
        return prefs(ctx).getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
    }

    // ── Feature flags ──────────────────────────────────────────────────────
    public static boolean isAutoScanEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_AUTO_SCAN, false);
    }

    public static int getScanTimeoutSeconds(Context ctx) {
        return prefs(ctx).getInt(KEY_SCAN_TIMEOUT, 15);
    }

    public static boolean isHapticFeedbackEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_HAPTIC, true);
    }

    public static String getTheme(Context ctx) {
        return prefs(ctx).getString(KEY_THEME, THEME_DARK);
    }

    public static void applySavedTheme(Context ctx) {
        switch (getTheme(ctx)) {
            case THEME_LIGHT:  AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);            break;
            case THEME_SYSTEM: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); break;
            default:           AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);           break;
        }
    }
}
