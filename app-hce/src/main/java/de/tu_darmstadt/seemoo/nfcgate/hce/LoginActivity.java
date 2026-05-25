package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.tu_darmstadt.seemoo.nfcgate.hce.cloud.CloudApiClient;
import de.tu_darmstadt.seemoo.nfcgate.hce.cloud.SessionManager;

public class LoginActivity extends AppCompatActivity {
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SettingsManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText etPassword = findViewById(R.id.et_password);
        TextView tvCooldown = findViewById(R.id.tv_cooldown);
        findViewById(R.id.btn_login).setOnClickListener(v -> {
            long remain = SessionManager.getCooldownRemainingSeconds(this);
            if (remain > 0) {
                tvCooldown.setText(getString(R.string.too_many_attempts, remain));
                return;
            }
            String password = etPassword.getText().toString().trim();
            if (password.isEmpty()) {
                Toast.makeText(this, R.string.hint_password, Toast.LENGTH_SHORT).show();
                return;
            }
            ioExecutor.execute(() -> login(password));
        });
    }

    private void login(String password) {
        try {
            CloudApiClient api = new CloudApiClient(this);
            CloudApiClient.LoginResult result = api.login(password);
            SessionManager.saveLogin(this, result.token, result.expiresAt, result.accountId, password);
            SessionManager.setSalt(this, result.salt);
            api.registerDevice();
            try { api.registerFcmToken("no_fcm"); } catch (Exception ignored) {}
            mainHandler.post(() -> {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            });
        } catch (CloudApiClient.ApiException ex) {
            if (ex.code == 429) {
                SessionManager.setCooldown(this, System.currentTimeMillis() + Math.max(1, ex.retryAfterSeconds) * 1000L);
            }
            mainHandler.post(() -> Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show());
        } catch (Exception ex) {
            mainHandler.post(() -> Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }
}
