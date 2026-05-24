package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.title_settings);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            EditTextPreference port = findPreference("server_port");
            if (port != null) {
                port.setOnBindEditTextListener(et -> et.setInputType(
                        android.text.InputType.TYPE_CLASS_NUMBER));
            }
            EditTextPreference pinCode = findPreference(SplashActivity.PREF_PIN_CODE);
            if (pinCode != null) {
                pinCode.setOnBindEditTextListener(et -> et.setInputType(
                        android.text.InputType.TYPE_CLASS_NUMBER
                                | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD));
                pinCode.setOnPreferenceChangeListener((preference, newValue) -> {
                    String pin = newValue == null ? "" : String.valueOf(newValue).trim();
                    if (pin.isEmpty() || pin.matches("\\d{4,6}")) {
                        return true;
                    }
                    Toast.makeText(requireContext(), R.string.pin_invalid_format, Toast.LENGTH_SHORT).show();
                    return false;
                });
            }
        }
    }
}
