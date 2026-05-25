package de.tu_darmstadt.seemoo.nfcgate.reader;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.widget.ViewPager2;

import java.util.Arrays;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {
    public static final String PREF_ONBOARDING_DONE = "onboarding_done";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        ViewPager2 pager = findViewById(R.id.vp_onboarding);
        TextView btnNext = findViewById(R.id.btn_next);
        List<String> pages = Arrays.asList(
                "Welcome to YiTian",
                "Scan NFC cards easily",
                "Sync across devices"
        );
        pager.setAdapter(new SimpleTextPagerAdapter(pages));
        btnNext.setOnClickListener(v -> {
            int cur = pager.getCurrentItem();
            if (cur < pages.size() - 1) {
                pager.setCurrentItem(cur + 1, true);
            } else {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                prefs.edit().putBoolean(PREF_ONBOARDING_DONE, true).apply();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            }
        });
    }
}
