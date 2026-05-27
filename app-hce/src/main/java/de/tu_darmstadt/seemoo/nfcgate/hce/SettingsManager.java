package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

public final class SettingsManager {
    public static final String KEY_THEME = "theme";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_AUTO_SYNC = "auto_sync";
    public static final String KEY_USB_OUTPUT_ENABLED = "pref_usb_output_enabled";
    public static final String KEY_CLOUD_BASE = "cloud_api_base";
    public static final String KEY_CLOUD_EMAIL = "cloud_email";

    private SettingsManager() {}

    public static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public static String getTheme(Context context) {
        return prefs(context).getString(KEY_THEME, "dark");
    }

    public static void applySavedTheme(Context context) {
        switch (getTheme(context)) {
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "system":
            case "dynamic":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
        }
    }

    public static String getCloudBaseUrl(Context context) {
        return prefs(context).getString(KEY_CLOUD_BASE, "https://api.yitian.shop");
    }

    public static String getCloudEmail(Context context) {
        return prefs(context).getString(KEY_CLOUD_EMAIL, "");
    }

    public static boolean isAutoSyncEnabled(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_SYNC, true);
    }

    public static boolean isUsbOutputEnabled(Context context) {
        return prefs(context).getBoolean(KEY_USB_OUTPUT_ENABLED, false);
    }

    public static String getLanguage(Context context) {
        return prefs(context).getString(KEY_LANGUAGE, "system");
    }
}
