package de.tu_darmstadt.seemoo.nfcgate.dynamic;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.play.core.splitinstall.SplitInstallManager;
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory;
import com.google.android.play.core.splitinstall.SplitInstallRequest;

public class DynamicModuleManager {
    private static final String TAG = "DynamicModuleManager";
    private final Context context;
    private final SplitInstallManager splitInstallManager;

    public DynamicModuleManager(Context context) {
        this.context = context;
        this.splitInstallManager = SplitInstallManagerFactory.create(context);
    }

    public void loadModule(String moduleName, Runnable onSuccess) {
        if (splitInstallManager.getInstalledModules().contains(moduleName)) {
            onSuccess.run();
            return;
        }

        SplitInstallRequest request = SplitInstallRequest.newBuilder()
                .addModule(moduleName)
                .build();

        splitInstallManager.startInstall(request)
                .addOnSuccessListener(sessionId -> onSuccess.run())
                .addOnFailureListener(error -> Log.e(TAG, "Failed to load module " + moduleName, error));
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
