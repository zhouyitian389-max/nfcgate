package de.tu_darmstadt.seemoo.nfcgate.hce.security;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;

import de.tu_darmstadt.seemoo.nfcgate.hce.MainActivity;
import de.tu_darmstadt.seemoo.nfcgate.hce.R;

public class PinLockActivity extends AppCompatActivity {
    private static final String KEY_PIN = "pin_lock_value";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_lock);
        setTitle(R.string.pin_lock_title);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String savedPin = preferences.getString(KEY_PIN, "");
        boolean hasPin = savedPin != null && !savedPin.isEmpty();

        TextView tvMessage = findViewById(R.id.tv_pin_message);
        EditText etPin = findViewById(R.id.et_pin);
        MaterialButton btnContinue = findViewById(R.id.btn_pin_continue);

        tvMessage.setText(hasPin ? R.string.pin_lock_enter_message : R.string.pin_lock_set_message);
        etPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etPin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});

        btnContinue.setOnClickListener(v -> {
            String candidate = etPin.getText() == null ? "" : etPin.getText().toString().trim();
            if (!candidate.matches("\\d{4,6}")) {
                Toast.makeText(this, R.string.pin_lock_error_length, Toast.LENGTH_SHORT).show();
                return;
            }
            if (hasPin && !candidate.equals(savedPin)) {
                Toast.makeText(this, R.string.pin_lock_error_mismatch, Toast.LENGTH_SHORT).show();
                etPin.setText("");
                return;
            }
            if (!hasPin) {
                preferences.edit().putString(KEY_PIN, candidate).apply();
            }
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
