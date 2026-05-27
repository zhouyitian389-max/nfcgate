package de.tu_darmstadt.seemoo.nfcgate.reader;

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

import de.tu_darmstadt.seemoo.nfcgate.reader.cloud.CloudApiClient;
import de.tu_darmstadt.seemoo.nfcgate.reader.cloud.SessionManager;

public class LoginActivity extends AppCompatActivity {
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText etEmail = findViewById(R.id.et_email);
        EditText etPassword = findViewById(R.id.et_password);
        TextView tvCooldown = findViewById(R.id.tv_cooldown);
        findViewById(R.id.btn_login).setOnClickListener(v -> {
            long remain = SessionManager.getCooldownRemainingSeconds(this);
            if (remain > 0) {
                tvCooldown.setText(getString(R.string.too_many_attempts, remain));
                return;
            }
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(this, R.string.hint_email, Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.isEmpty()) {
                Toast.makeText(this, R.string.hint_password, Toast.LENGTH_SHORT).show();
                return;
            }
            ioExecutor.execute(() -> doLogin(email, password));
        });
    }

    private void doLogin(String email, String password) {
        try {
            CloudApiClient client = new CloudApiClient(this);
            CloudApiClient.LoginResult result = client.login(email, password);
            SessionManager.saveLogin(this, result.token, result.refreshToken, result.expiresAt, result.accountId, password);
            SessionManager.setSalt(this, result.salt);
            client.registerDevice();
            try { client.registerFcmToken("no_fcm"); } catch (Exception ignored) {}
            mainHandler.post(() -> {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            });
        } catch (CloudApiClient.ApiException e) {
            if (e.code == 429) {
                SessionManager.setCooldown(this, System.currentTimeMillis() + (Math.max(1, e.retryAfterSeconds) * 1000L));
            }
            mainHandler.post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            mainHandler.post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }
}
