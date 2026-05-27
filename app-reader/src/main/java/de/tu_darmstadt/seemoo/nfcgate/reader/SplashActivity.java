package de.tu_darmstadt.seemoo.nfcgate.reader;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import de.tu_darmstadt.seemoo.nfcgate.reader.cloud.SessionManager;
import de.tu_darmstadt.seemoo.nfcgate.reader.settings.SettingsManager;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SettingsManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.iv_wallet_logo);
        logo.setAlpha(0f);
        logo.setScaleX(0.5f);
        logo.setScaleY(0.5f);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f).setDuration(500),
                ObjectAnimator.ofFloat(logo, "scaleX", 0.5f, 1f).setDuration(800),
                ObjectAnimator.ofFloat(logo, "scaleY", 0.5f, 1f).setDuration(800)
        );
        animatorSet.start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Class<?> next = SessionManager.isLoggedIn(this) ? MainActivity.class : LoginActivity.class;
            startActivity(new Intent(this, next));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        }, 1800);
    }
}
