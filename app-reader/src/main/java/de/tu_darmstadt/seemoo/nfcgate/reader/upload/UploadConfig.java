package de.tu_darmstadt.seemoo.nfcgate.reader.upload;

import android.content.Context;
import android.content.SharedPreferences;

public class UploadConfig {
    private static final String PREFS = "reader_upload";
    private static final String KEY_URL = "base_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_ALLOW_CLEARTEXT = "allow_cleartext";
    private static final String KEY_E2EE = "e2ee_enabled";

    private final SharedPreferences preferences;

    public UploadConfig(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getBaseUrl() {
        return preferences.getString(KEY_URL, "https://127.0.0.1:8443");
    }

    public void setBaseUrl(String url) {
        preferences.edit().putString(KEY_URL, url).apply();
    }

    public String getApiKey() {
        return preferences.getString(KEY_API_KEY, "");
    }

    public void setApiKey(String apiKey) {
        preferences.edit().putString(KEY_API_KEY, apiKey).apply();
    }

    public boolean isAllowCleartext() {
        return preferences.getBoolean(KEY_ALLOW_CLEARTEXT, false);
    }

    public void setAllowCleartext(boolean allowCleartext) {
        preferences.edit().putBoolean(KEY_ALLOW_CLEARTEXT, allowCleartext).apply();
    }

    public boolean isE2eeEnabled() {
        return preferences.getBoolean(KEY_E2EE, false);
    }

    public void setE2eeEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_E2EE, enabled).apply();
    }
}
