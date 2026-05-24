package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;

public class PinVerifyActivity extends AppCompatActivity {
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
}
