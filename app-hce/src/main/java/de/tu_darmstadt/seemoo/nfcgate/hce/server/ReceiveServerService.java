package de.tu_darmstadt.seemoo.nfcgate.hce.server;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class ReceiveServerService extends Service {
    private ReceiveServer server;

    @Override
    public void onCreate() {
        super.onCreate();
        server = new ReceiveServer(this);
    }

    public ReceiveServer getServer() {
        return server;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
