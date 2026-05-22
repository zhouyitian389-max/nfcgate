package de.tu_darmstadt.seemoo.nfcgate;

import android.app.Application;

public class NfcGateApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppStartupManager.initialize(this);
    }
}
