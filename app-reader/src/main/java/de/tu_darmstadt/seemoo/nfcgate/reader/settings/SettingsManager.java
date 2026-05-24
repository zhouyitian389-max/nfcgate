package de.tu_darmstadt.seemoo.nfcgate.reader.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

public final class SettingsManager {
    public static final String KEY_SERVER_URL = "server_url";
    public static final String KEY_AUTO_SCAN = "auto_scan";
    public static final String KEY_SCAN_TIMEOUT = "scan_timeout";
    public static final String KEY_THEME = "theme";
    public static final String KEY_HAPTIC_FEEDBACK = "haptic_feedback";

    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_SYSTEM = "system";

    public static final String DEFAULT_SERVER_URL = "https://relay.example.com/api/upload";

    private SettingsManager() {
    }

    public static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static String getServerUrl(Context context) {
        return prefs(context).getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
    }

    public static boolean isAutoScanEnabled(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_SCAN, false);
    }

    public static int getScanTimeoutSeconds(Context context) {
        return prefs(context).getInt(KEY_SCAN_TIMEOUT, 15);
    }

    public static boolean isHapticFeedbackEnabled(Context context) {
        return prefs(context).getBoolean(KEY_HAPTIC_FEEDBACK, true);
    }

    public static String getTheme(Context context) {
        return prefs(context).getString(KEY_THEME, THEME_DARK);
    }

    public static void applySavedTheme(Context context) {
        String mode = getTheme(context);
        if (THEME_LIGHT.equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (THEME_SYSTEM.equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
    }
}
