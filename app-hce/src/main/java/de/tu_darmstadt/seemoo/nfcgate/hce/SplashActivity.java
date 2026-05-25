package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import de.tu_darmstadt.seemoo.nfcgate.hce.cloud.SessionManager;
import de.tu_darmstadt.seemoo.nfcgate.hce.security.PinHasher;

public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";
    public static final String PREF_PIN_CODE = "pref_pin_code";
    public static final String PREF_PIN_HASH = "pref_pin_hash";
    public static final String PREF_FILE = "yitian_pin_secrets";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SettingsManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            if (!prefs.getBoolean(OnboardingActivity.PREF_ONBOARDING_DONE, false)) {
                startActivity(new Intent(this, OnboardingActivity.class));
                finish();
                return;
            }
            if (!SessionManager.isLoggedIn(this)) {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                return;
            }
            boolean hasPinHash = false;
            try {
                SharedPreferences encPrefs = getPinPrefs();

                String storedHash = encPrefs.getString(PREF_PIN_HASH, null);
                if (storedHash == null || storedHash.isEmpty()) {
                    // Attempt migration from legacy plaintext pref_pin_code
                    android.content.SharedPreferences defPrefs =
                            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
                    String legacyPin = defPrefs.getString(PREF_PIN_CODE, "");
                    if (legacyPin != null && !legacyPin.trim().isEmpty()) {
                        // Hash the legacy PIN and store it; remove the plaintext key
                        String hashed = PinHasher.hash(legacyPin.trim());
                        encPrefs.edit().putString(PREF_PIN_HASH, hashed).apply();
                        defPrefs.edit().remove(PREF_PIN_CODE).apply();
                        hasPinHash = true;
                    }
                } else {
                    hasPinHash = true;
                }
            } catch (Exception e) {
                Log.w(TAG, "PIN storage init failed, attempting plain-prefs fallback", e);
                // Fail-closed: check whether a PIN hash exists in plain SharedPreferences
                // (the fallback store). If one is found, route to PinVerifyActivity so the
                // PIN gate is never silently bypassed on an error.
                try {
                    String fallbackHash = getSharedPreferences(PREF_FILE, MODE_PRIVATE)
                            .getString(PREF_PIN_HASH, null);
                    hasPinHash = (fallbackHash != null && !fallbackHash.isEmpty());
                } catch (Exception ex) {
                    Log.w(TAG, "Plain-prefs fallback also failed; defaulting to no PIN gate", ex);
                    hasPinHash = false;
                }
            }

            Class<?> target = hasPinHash ? PinVerifyActivity.class : MainActivity.class;
            startActivity(new Intent(this, target));
            finish();
        }, 1200);
    }

    private SharedPreferences getPinPrefs() {
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    this,
                    PREF_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            Log.w(TAG, "Falling back to plain SharedPreferences for PIN storage", e);
            return getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        }
    }
}
