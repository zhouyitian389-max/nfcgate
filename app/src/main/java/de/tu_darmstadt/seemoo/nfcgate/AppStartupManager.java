package de.tu_darmstadt.seemoo.nfcgate;

import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.preference.PreferenceManager;
import de.tu_darmstadt.seemoo.nfcgate.db.AppDatabase;
import de.tu_darmstadt.seemoo.nfcgate.network.UserTrustManager;

public final class AppStartupManager {
    private AppStartupManager() {
    }

    public static void initialize(Context context) {
        initializeCritical(context);
        initializeAsync(context.getApplicationContext());
    }

    private static void initializeCritical(Context context) {
        AppDatabase.getDatabase(context);
        PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static void initializeAsync(Context context) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> UserTrustManager.init(context));
        executor.shutdown();
    }
}
