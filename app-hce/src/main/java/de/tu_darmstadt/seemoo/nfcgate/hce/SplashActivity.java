package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import de.tu_darmstadt.seemoo.nfcgate.hce.security.PinHasher;

public class SplashActivity extends AppCompatActivity {
    public static final String PREF_PIN_CODE = "pref_pin_code";
    public static final String PREF_PIN_HASH = "pref_pin_hash";
    public static final String PREF_FILE = "yitian_pin_secrets";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            boolean hasPinHash = false;
            try {
                MasterKey masterKey = new MasterKey.Builder(this)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();
                SharedPreferences encPrefs = EncryptedSharedPreferences.create(
                        this,
                        PREF_FILE,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);

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
                // If EncryptedSharedPreferences fails, allow entry
                hasPinHash = false;
            }

            Class<?> target = hasPinHash ? PinVerifyActivity.class : MainActivity.class;
            startActivity(new Intent(this, target));
            finish();
        }, 1200);
    }
}
