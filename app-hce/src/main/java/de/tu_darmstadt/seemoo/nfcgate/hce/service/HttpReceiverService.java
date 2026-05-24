package de.tu_darmstadt.seemoo.nfcgate.hce.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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

    private YitianHttpServer server;
    private int port = 8080;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopServer();
            stopForeground(true);
            stopSelf();
            broadcast(false, port, 0);
            return START_NOT_STICKY;
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            port = Integer.parseInt(sp.getString("server_port", "8080"));
        } catch (NumberFormatException e) { port = 8080; }
        startServer();
        startForeground(NOTIF_ID, buildNotification());
        broadcast(true, port, 0);
        return START_STICKY;
    }

    private void startServer() {
        stopServer();
        try {
            server = new YitianHttpServer(this, port);
            server.setListener((total, newOnes) -> broadcast(true, port, total));
            server.start(5000, false);
            Log.i(TAG, "YitianHttpServer started on " + port);
        } catch (Exception e) {
            Log.e(TAG, "start failed", e);
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
