package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

public class SplashActivity extends AppCompatActivity {
    public static final String PREF_PIN_CODE = "pref_pin_code";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            String pinCode = PreferenceManager.getDefaultSharedPreferences(this)
                    .getString(PREF_PIN_CODE, "");
            Class<?> target = pinCode != null && !pinCode.trim().isEmpty()
                    ? PinVerifyActivity.class
                    : MainActivity.class;
            startActivity(new Intent(this, target));
            finish();
        }, 1200);
    }
}
