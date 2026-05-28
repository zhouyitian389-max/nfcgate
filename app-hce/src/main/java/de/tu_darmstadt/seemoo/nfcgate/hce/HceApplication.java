package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import com.google.android.material.color.DynamicColors;

import java.util.Locale;

public class HceApplication extends Application {
    private static final String TAG = "CrashHandler";

    @Override
    public void onCreate() {
        super.onCreate();
        installCrashHandler();
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

    private void installCrashHandler() {
        Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "Uncaught exception in " + thread.getName(), throwable);
            try {
                java.io.File crashDir = new java.io.File(getFilesDir(), "crashes");
                if (!crashDir.exists()) {
                    crashDir.mkdirs();
                }
                java.io.File crashFile = new java.io.File(crashDir, "crash_" + System.currentTimeMillis() + ".txt");
                try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(crashFile))) {
                    pw.println("Time: " + new java.util.Date());
                    pw.println("Thread: " + thread.getName());
                    throwable.printStackTrace(pw);
                }
            } catch (Exception ignored) {
                // Ignore crash logging failures and continue with default termination flow.
            }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable);
            } else {
                System.exit(1);
            }
        });
    }
}
