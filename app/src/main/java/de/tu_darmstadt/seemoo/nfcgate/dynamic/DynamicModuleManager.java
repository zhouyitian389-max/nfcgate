package de.tu_darmstadt.seemoo.nfcgate.dynamic;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Stub DynamicModuleManager - dynamic feature delivery is disabled until
 * AGP compatibility issues are resolved. All module loads are no-ops that
 * directly invoke the success callback.
 */
public class DynamicModuleManager {
    private static final String TAG = "DynamicModuleManager";
    private final Context context;

    public DynamicModuleManager(Context context) {
        this.context = context;
    }

    public void loadModule(String moduleName, Runnable onSuccess) {
        // Dynamic features disabled; treat all modules as already installed
        Log.d(TAG, "Dynamic features disabled, directly running: " + moduleName);
        onSuccess.run();
    }

    public void launchModuleActivity(String moduleName, String activityClassName) {
        loadModule(moduleName, () -> {
            Intent intent = new Intent();
            intent.setClassName(context.getPackageName(), activityClassName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        });
    }
}
