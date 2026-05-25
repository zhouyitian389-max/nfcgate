package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;

import java.util.concurrent.Executor;

public class PinVerifyActivity extends FragmentActivity {
    private static final int MAX_ATTEMPTS = 3;

    private int attemptsLeft = MAX_ATTEMPTS;
    private EditText etPinCode;
    private MaterialButton btnVerify;
    private TextView tvAttempts;
    private String expectedPin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_verify);

        expectedPin = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(SplashActivity.PREF_PIN_CODE, "");
        if (expectedPin == null || expectedPin.trim().isEmpty()) {
            openMainAndFinish();
            return;
        }

        etPinCode = findViewById(R.id.et_pin_code);
        btnVerify = findViewById(R.id.btn_verify_pin);
        tvAttempts = findViewById(R.id.tv_attempts_left);

        updateAttemptsLabel();
        btnVerify.setOnClickListener(v -> verifyPin());

        // Try biometric first if enabled
        boolean biometricEnabled = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("pref_biometric_enabled", true);
        if (biometricEnabled) {
            tryBiometric();
        }
    }

    private void tryBiometric() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuth = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            return; // fallback to PIN
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        openMainAndFinish();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        // User cancelled or not available, fallback to PIN
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(PinVerifyActivity.this,
                                R.string.pin_verify_failed, Toast.LENGTH_SHORT).show();
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.pin_verify_title))
                .setSubtitle(getString(R.string.biometric_subtitle))
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
                                | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void verifyPin() {
        String enteredPin = etPinCode.getText() == null ? "" : etPinCode.getText().toString().trim();
        if (!enteredPin.matches("\\d{4,6}")) {
            Toast.makeText(this, R.string.pin_invalid_format, Toast.LENGTH_SHORT).show();
            return;
        }
        if (enteredPin.equals(expectedPin)) {
            openMainAndFinish();
            return;
        }

        attemptsLeft--;
        updateAttemptsLabel();
        etPinCode.setText("");
        Toast.makeText(this, R.string.pin_verify_failed, Toast.LENGTH_SHORT).show();
        if (attemptsLeft <= 0) {
            etPinCode.setEnabled(false);
            btnVerify.setEnabled(false);
            Toast.makeText(this, R.string.pin_verify_locked, Toast.LENGTH_LONG).show();
        }
    }

    private void updateAttemptsLabel() {
        tvAttempts.setText(getString(R.string.pin_attempts_left, attemptsLeft));
    }

    private void openMainAndFinish() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    public void onBackPressed() {
        finishAffinity();
    }
}
