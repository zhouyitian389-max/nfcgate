package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import de.tu_darmstadt.seemoo.nfcgate.hce.security.PinLockActivity;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(this, PinLockActivity.class));
            finish();
        }, 1200);
    }
}
