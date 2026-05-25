package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import de.tu_darmstadt.seemoo.nfcgate.hce.security.PinHasher;
import de.tu_darmstadt.seemoo.nfcgate.hce.service.HttpReceiverService;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
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
            EditTextPreference port = findPreference(HttpReceiverService.PREF_SERVER_PORT);
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
                    if (!pin.isEmpty() && !pin.matches("\\d{4,6}")) {
                        Toast.makeText(requireContext(), R.string.pin_invalid_format, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    // Store as PBKDF2 hash in EncryptedSharedPreferences; clear plaintext
                    try {
                        SharedPreferences encPrefs = getPinPrefs();
                        if (pin.isEmpty()) {
                            encPrefs.edit().remove(SplashActivity.PREF_PIN_HASH).apply();
                        } else {
                            encPrefs.edit()
                                    .putString(SplashActivity.PREF_PIN_HASH, PinHasher.hash(pin))
                                    .apply();
                        }
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), R.string.pin_invalid_format, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    // Return false so the plaintext value is NOT saved to default SharedPreferences
                    return false;
                });
            }
        }

        private SharedPreferences getPinPrefs() {
            try {
                MasterKey masterKey = new MasterKey.Builder(requireContext())
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();
                return EncryptedSharedPreferences.create(
                        requireContext(),
                        SplashActivity.PREF_FILE,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            } catch (Exception e) {
                Log.w(TAG, "Falling back to plain SharedPreferences for PIN storage", e);
                return requireContext().getSharedPreferences(SplashActivity.PREF_FILE, Context.MODE_PRIVATE);
            }
        }
    }
}
