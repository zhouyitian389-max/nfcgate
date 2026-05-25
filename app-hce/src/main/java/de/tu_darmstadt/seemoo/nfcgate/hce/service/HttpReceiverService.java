package de.tu_darmstadt.seemoo.nfcgate.hce.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import de.tu_darmstadt.seemoo.nfcgate.hce.R;

public class HttpReceiverService extends Service {
    private static final String TAG = "HttpReceiverService";
    private static final String CHANNEL_ID = "yitian_nfc_receiver";
    private static final int NOTIF_ID = 1001;
    public static final String ACTION_START = "yitian.nfc.START";
    public static final String ACTION_STOP = "yitian.nfc.STOP";

    public static final String BROADCAST_STATE = "yitian.nfc.STATE";
    public static final String EXTRA_RUNNING = "running";
    public static final String EXTRA_PORT = "port";
    public static final String EXTRA_COUNT = "count";
    public static final String PREF_SERVER_PORT = "server_port";
    public static final String PREF_HTTP_AUTH_ENABLED = "pref_http_auth_enabled";
    public static final String PREF_HTTP_AUTH_TOKEN_DISPLAY = "pref_http_auth_token_display";

    private YitianHttpServer server;
    private int port = 8080;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = ACTION_START;
        if (intent != null && intent.getAction() != null) {
            action = intent.getAction();
        }
        if (ACTION_STOP.equals(action)) {
            stopServer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            stopSelf();
            broadcast(false, port, 0);
            return START_NOT_STICKY;
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            port = Integer.parseInt(sp.getString(HttpReceiverService.PREF_SERVER_PORT, "8080"));
        } catch (NumberFormatException e) { port = 8080; }
        boolean started = startServer();
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
        if (started) {
            broadcast(true, port, 0);
        } else {
            broadcast(false, port, 0);
            stopForeground(true);
            stopSelf();
        }
        return START_STICKY;
    }

    private boolean startServer() {
        stopServer();
        try {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            boolean authEnabled = sp.getBoolean(PREF_HTTP_AUTH_ENABLED, true);
            String authToken = authEnabled ? HttpAuthTokenProvider.getOrCreateToken(this) : null;
            server = new YitianHttpServer(this, port, authEnabled, authToken);
            server.setListener((total, newOnes) -> broadcast(true, port, total));
            server.start(5000, false);
            Log.i(TAG, "YitianHttpServer started on " + port);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "start failed", e);
            server = null;
            return false;
        }
    }

    private void stopServer() {
        if (server != null) {
            try { server.stop(); } catch (Exception ignored) {}
            server = null;
        }
    }

    private void broadcast(boolean running, int port, int count) {
        Intent i = new Intent(BROADCAST_STATE);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_RUNNING, running);
        i.putExtra(EXTRA_PORT, port);
        i.putExtra(EXTRA_COUNT, count);
        sendBroadcast(i);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text, port))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopServer();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
