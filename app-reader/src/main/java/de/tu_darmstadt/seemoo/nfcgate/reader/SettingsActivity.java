package de.tu_darmstadt.seemoo.nfcgate.reader;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

import de.tu_darmstadt.seemoo.nfcgate.reader.settings.SettingsManager;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SettingsManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.settings_preferences, rootKey);

            EditTextPreference server = findPreference(SettingsManager.KEY_SERVER_URL);
            if (server != null) {
                server.setOnBindEditTextListener(editText -> editText.setSingleLine(true));
                server.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            }
            EditTextPreference cloud = findPreference(SettingsManager.KEY_CLOUD_BASE);
            if (cloud != null) {
                cloud.setOnBindEditTextListener(editText -> editText.setSingleLine(true));
                cloud.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            }

            SeekBarPreference timeout = findPreference(SettingsManager.KEY_SCAN_TIMEOUT);
            if (timeout != null) {
                timeout.setSummaryProvider((Preference.SummaryProvider<SeekBarPreference>) preference ->
                        getString(R.string.settings_timeout_summary, preference.getValue()));
            }

            ListPreference theme = findPreference(SettingsManager.KEY_THEME);
            if (theme != null) {
                theme.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
                theme.setOnPreferenceChangeListener((preference, newValue) -> {
                    preference.getSharedPreferences().edit()
                            .putString(SettingsManager.KEY_THEME, String.valueOf(newValue)).apply();
                    SettingsManager.applySavedTheme(requireContext());
                    requireActivity().recreate();
                    return true;
                });
            }
            ListPreference language = findPreference(SettingsManager.KEY_LANGUAGE);
            if (language != null) {
                language.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
                language.setOnPreferenceChangeListener((preference, newValue) -> {
                    preference.getSharedPreferences().edit()
                            .putString(SettingsManager.KEY_LANGUAGE, String.valueOf(newValue)).apply();
                    requireActivity().recreate();
                    return true;
                });
            }

            Preference admin = new Preference(requireContext());
            admin.setKey("admin_entry");
            admin.setTitle("Admin");
            admin.setSummary("Total synced cards, account ID, server status");
            admin.setVisible(false);
            admin.setOnPreferenceClickListener(preference -> {
                android.widget.Toast.makeText(requireContext(), "Admin: " + de.tu_darmstadt.seemoo.nfcgate.reader.cloud.SessionManager.getAccountId(requireContext()), android.widget.Toast.LENGTH_SHORT).show();
                return true;
            });
            getPreferenceScreen().addPreference(admin);

            Preference versionPref = new Preference(requireContext());
            versionPref.setTitle("Version");
            versionPref.setSummary(BuildConfig.VERSION_NAME);
            versionPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                int taps = 0;
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    taps++;
                    if (taps >= 7) {
                        admin.setVisible(true);
                    }
                    return true;
                }
            });
            getPreferenceScreen().addPreference(versionPref);
        }
    }
}
