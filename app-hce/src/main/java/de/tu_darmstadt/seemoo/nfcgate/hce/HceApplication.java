package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.app.Application;
import android.os.Build;

import com.google.android.material.color.DynamicColors;

import java.util.Locale;

public class HceApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(this);
        }
        String lang = SettingsManager.getLanguage(this);
        if (!"system".equals(lang)) {
            Locale locale = "zh-CN".equals(lang) ? Locale.SIMPLIFIED_CHINESE : Locale.ENGLISH;
            Locale.setDefault(locale);
            android.content.res.Configuration config = getResources().getConfiguration();
            config.setLocale(locale);
            getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        }
    }
}
