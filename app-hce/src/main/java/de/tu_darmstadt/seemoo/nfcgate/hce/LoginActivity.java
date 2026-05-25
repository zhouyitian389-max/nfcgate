package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.tu_darmstadt.seemoo.nfcgate.hce.auth.CloudSessionManager;
import de.tu_darmstadt.seemoo.nfcgate.hce.network.CloudApiClient;

public class LoginActivity extends AppCompatActivity {
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private EditText etPassword;
    private Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        if (CloudSessionManager.hasToken(this)) {
            openMain();
            return;
        }

        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnLogin.setOnClickListener(v -> performLogin());
    }

    private void performLogin() {
        String password = etPassword.getText() == null ? "" : etPassword.getText().toString().trim();
        if (password.length() < 4 || password.length() > 32) {
            Toast.makeText(this, R.string.login_password_invalid, Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);
        ioExecutor.execute(() -> {
            try {
                CloudApiClient.LoginResult result = new CloudApiClient(this).login(password);
                CloudSessionManager.saveSession(this, result.token, result.accountId);
                runOnUiThread(this::openMain);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnLogin.setEnabled(true);
                    String msg = e.getMessage() == null ? getString(R.string.login_failed_generic) : e.getMessage();
                    Toast.makeText(this, getString(R.string.login_failed, msg), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void openMain() {
        Intent intent = new Intent(this, SplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }
}
