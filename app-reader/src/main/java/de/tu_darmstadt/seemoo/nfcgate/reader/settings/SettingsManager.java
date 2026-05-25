package de.tu_darmstadt.seemoo.nfcgate.reader.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

public final class SettingsManager {
    public static final String KEY_SERVER_URL      = "server_url";
    public static final String KEY_YITIAN_HOST     = "yitian_host";
    public static final String KEY_YITIAN_PORT     = "yitian_port";
    public static final String KEY_AUTO_SCAN       = "auto_scan";
    public static final String KEY_SCAN_TIMEOUT    = "scan_timeout";
    public static final String KEY_THEME           = "theme";
    public static final String KEY_LANGUAGE        = "language";
    public static final String KEY_AUTO_SYNC       = "auto_sync";
    public static final String KEY_CLOUD_BASE      = "cloud_api_base";
    public static final String KEY_HAPTIC          = "haptic_feedback";
    public static final String KEY_UPLOAD_MODE     = "upload_mode";
    public static final String KEY_API_SERVER_URL  = "api_server_url";

    public static final String UPLOAD_MODE_CLOUD   = "cloud";

    public static final String THEME_DARK   = "dark";
    public static final String THEME_LIGHT  = "light";
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_DYNAMIC = "dynamic";

    /** Legacy relay URL kept for backward compatibility. */
    public static final String DEFAULT_SERVER_URL     = "https://relay.example.com/api/upload";
    public static final String DEFAULT_YITIAN_HOST    = "192.168.1.100";
    public static final int    DEFAULT_YITIAN_PORT    = 8080;
    public static final String DEFAULT_CLOUD_API_BASE = "https://api.yitian.shop";
    /** Default cloud API server URL (used by CloudApiClient). */
    public static final String DEFAULT_API_SERVER_URL = DEFAULT_CLOUD_API_BASE;

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

    public static boolean isAutoSyncEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_AUTO_SYNC, true);
    }

    public static String getTheme(Context ctx) {
        return prefs(ctx).getString(KEY_THEME, THEME_DARK);
    }

    public static String getLanguage(Context ctx) {
        return prefs(ctx).getString(KEY_LANGUAGE, "system");
    }

    public static String getCloudBaseUrl(Context ctx) {
        return prefs(ctx).getString(KEY_CLOUD_BASE, DEFAULT_CLOUD_API_BASE);
    }

    /** Returns the cloud API server URL configured via the api_server_url preference. */
    public static String getApiServerUrl(Context ctx) {
        return prefs(ctx).getString(KEY_API_SERVER_URL, DEFAULT_API_SERVER_URL);
    }

    /** Returns true when the user has selected cloud-upload mode. */
    public static boolean isCloudUploadMode(Context ctx) {
        return UPLOAD_MODE_CLOUD.equals(prefs(ctx).getString(KEY_UPLOAD_MODE, UPLOAD_MODE_CLOUD));
    }

    public static void applySavedTheme(Context ctx) {
        switch (getTheme(ctx)) {
            case THEME_LIGHT:  AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);            break;
            case THEME_SYSTEM: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); break;
            case THEME_DYNAMIC: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); break;
            default:           AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);           break;
        }
    }
}
