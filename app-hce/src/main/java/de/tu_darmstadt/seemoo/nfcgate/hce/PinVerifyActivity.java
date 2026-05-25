package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.android.material.button.MaterialButton;

import java.util.concurrent.Executor;

import de.tu_darmstadt.seemoo.nfcgate.hce.security.PinHasher;

public class PinVerifyActivity extends FragmentActivity {
    private static final String TAG = "PinVerifyActivity";
    private static final int MAX_ATTEMPTS = 3;
    private static final long LOCK_DURATION_MS = 5 * 60 * 1000L;
    private static final String PREF_LOCK_UNTIL_ELAPSED = "pin_lock_until_elapsed";
    private static final String PREF_LOCK_UNTIL_WALL = "pin_lock_until_wall";
    private static final String PREF_LOCK_UNTIL_LEGACY = "pin_lock_until";

    private int attemptsLeft = MAX_ATTEMPTS;
    private EditText etPinCode;
    private MaterialButton btnVerify;
    private TextView tvAttempts;
    private String storedPinHash;
    private SharedPreferences encPrefs;
    private CountDownTimer lockCountdown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_verify);

        // Replace deprecated onBackPressed() with OnBackPressedDispatcher callback
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
            }
        });

        encPrefs = getPinPrefs();

        storedPinHash = encPrefs.getString(SplashActivity.PREF_PIN_HASH, null);
        if (storedPinHash == null || storedPinHash.isEmpty()) {
            openMainAndFinish();
            return;
        }

        etPinCode = findViewById(R.id.et_pin_code);
        btnVerify = findViewById(R.id.btn_verify_pin);
        tvAttempts = findViewById(R.id.tv_attempts_left);

        btnVerify.setOnClickListener(v -> verifyPin());

        // Check for persistent lockout
        long lockUntilElapsed = encPrefs.getLong(PREF_LOCK_UNTIL_ELAPSED, 0L);
        long lockUntilWall = encPrefs.getLong(PREF_LOCK_UNTIL_WALL, 0L);
        long lockUntilLegacy = encPrefs.getLong(PREF_LOCK_UNTIL_LEGACY, 0L);
        if (lockUntilElapsed == 0L && lockUntilWall == 0L && lockUntilLegacy > 0L) {
            lockUntilElapsed = SystemClock.elapsedRealtime() + Math.max(0L, lockUntilLegacy - System.currentTimeMillis());
            lockUntilWall = lockUntilLegacy;
            encPrefs.edit()
                    .putLong(PREF_LOCK_UNTIL_ELAPSED, lockUntilElapsed)
                    .putLong(PREF_LOCK_UNTIL_WALL, lockUntilWall)
                    .remove(PREF_LOCK_UNTIL_LEGACY)
                    .apply();
        }
        if (isStillLocked(lockUntilElapsed, lockUntilWall)) {
            applyLockout(lockUntilElapsed, lockUntilWall);
            return;
        }

        updateAttemptsLabel();

        // Try biometric first if enabled
        boolean biometricEnabled = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(this)
                .getBoolean("pref_biometric_enabled", true);
        if (biometricEnabled) {
            tryBiometric();
        }
    }

    private SharedPreferences getPinPrefs() {
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    this,
                    SplashActivity.PREF_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            Log.w(TAG, "Falling back to plain SharedPreferences for PIN storage", e);
            return getSharedPreferences(SplashActivity.PREF_FILE, MODE_PRIVATE);
        }
    }

    private void applyLockout(long lockUntilElapsed, long lockUntilWall) {
        long remainingMs = calculateRemainingLockMs(lockUntilElapsed, lockUntilWall);
        if (remainingMs <= 0L) {
            clearLockout();
            return;
        }
        if (lockCountdown != null) {
            lockCountdown.cancel();
            lockCountdown = null;
        }
        etPinCode.setEnabled(false);
        btnVerify.setEnabled(false);
        lockCountdown = new CountDownTimer(remainingMs, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                tvAttempts.setText(getString(R.string.pin_locked_remaining, seconds));
            }

            @Override
            public void onFinish() {
                if (isStillLocked(lockUntilElapsed, lockUntilWall)) {
                    applyLockout(lockUntilElapsed, lockUntilWall);
                    return;
                }
                clearLockout();
            }
        }.start();
    }

    private boolean isStillLocked(long lockUntilElapsed, long lockUntilWall) {
        return SystemClock.elapsedRealtime() < lockUntilElapsed
                || System.currentTimeMillis() < lockUntilWall;
    }

    private long calculateRemainingLockMs(long lockUntilElapsed, long lockUntilWall) {
        long elapsedRemaining = lockUntilElapsed - SystemClock.elapsedRealtime();
        long wallRemaining = lockUntilWall - System.currentTimeMillis();
        return Math.max(0L, Math.max(elapsedRemaining, wallRemaining));
    }

    private void clearLockout() {
        encPrefs.edit()
                .remove(PREF_LOCK_UNTIL_ELAPSED)
                .remove(PREF_LOCK_UNTIL_WALL)
                .remove(PREF_LOCK_UNTIL_LEGACY)
                .apply();
        etPinCode.setEnabled(true);
        btnVerify.setEnabled(true);
        attemptsLeft = MAX_ATTEMPTS;
        updateAttemptsLabel();
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
        String enteredPin = etPinCode.getText() == null ? "" : etPinCode.getText().toString();
        // Validate: 4-8 digits, all numeric
        if (enteredPin.length() < 4 || enteredPin.length() > 8 || !enteredPin.matches("\\d+")) {
            Toast.makeText(this, R.string.pin_invalid_format, Toast.LENGTH_SHORT).show();
            return;
        }
        if (PinHasher.verify(enteredPin, storedPinHash)) {
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
            // Persist lockout timestamp
            long lockUntilElapsed = SystemClock.elapsedRealtime() + LOCK_DURATION_MS;
            long lockUntilWall = System.currentTimeMillis() + LOCK_DURATION_MS;
            encPrefs.edit()
                    .putLong(PREF_LOCK_UNTIL_ELAPSED, lockUntilElapsed)
                    .putLong(PREF_LOCK_UNTIL_WALL, lockUntilWall)
                    .remove(PREF_LOCK_UNTIL_LEGACY)
                    .apply();
            applyLockout(lockUntilElapsed, lockUntilWall);
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
    protected void onDestroy() {
        super.onDestroy();
        if (lockCountdown != null) {
            lockCountdown.cancel();
            lockCountdown = null;
        }
    }
}
