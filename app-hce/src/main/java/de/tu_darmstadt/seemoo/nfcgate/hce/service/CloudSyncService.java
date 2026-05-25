package de.tu_darmstadt.seemoo.nfcgate.hce.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.tu_darmstadt.seemoo.nfcgate.hce.MainActivity;
import de.tu_darmstadt.seemoo.nfcgate.hce.R;
import de.tu_darmstadt.seemoo.nfcgate.hce.ReceivedCardsActivity;
import de.tu_darmstadt.seemoo.nfcgate.hce.cloud.CloudApiClient;
import de.tu_darmstadt.seemoo.nfcgate.hce.cloud.CloudEventSource;
import de.tu_darmstadt.seemoo.nfcgate.hce.cloud.SessionManager;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardDatabase;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardEntity;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.OperationLogEntity;

public class CloudSyncService extends Service {
    public static final String ACTION_START = "de.tu_darmstadt.seemoo.nfcgate.hce.action.START_SYNC";
    public static final String ACTION_STOP = "de.tu_darmstadt.seemoo.nfcgate.hce.action.STOP_SYNC";
    private static final String CHANNEL_SERVICE = "cloud_sync_service";
    private static final String CHANNEL_NEW_CARD = "new_card_received";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private CloudEventSource eventSource;
    private final Runnable pullRunnable = new Runnable() {
        @Override
        public void run() {
            ioExecutor.execute(CloudSyncService.this::pullOnce);
            handler.postDelayed(this, 30_000L);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!SessionManager.isLoggedIn(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        createChannels();
        startForeground(9011, buildServiceNotification());
        if (eventSource == null) {
            eventSource = new CloudEventSource(this, new CloudEventSource.EventListener() {
                @Override
                public void onNewCards() {
                    ioExecutor.execute(CloudSyncService.this::pullOnce);
                }

                @Override
                public void onLogout() {
                    SessionManager.logout(CloudSyncService.this);
                    stopSelf();
                }

                @Override
                public void onError(Exception e) {
                    // polling fallback remains active
                }
            });
        }
        eventSource.start();
        handler.removeCallbacks(pullRunnable);
        handler.post(pullRunnable);
        return START_STICKY;
    }

    private void pullOnce() {
        try {
            CloudApiClient api = new CloudApiClient(this);
            List<CloudApiClient.CardItem> cards = api.pullCards();
            if (cards.isEmpty()) return;
            CardDatabase db = CardDatabase.getInstance(this);
            db.runInTransaction(() -> {
                for (CloudApiClient.CardItem item : cards) {
                    CardEntity existing = db.cardDao().findByPan(item.pan);
                    CardEntity e = new CardEntity();
                    if (existing != null) e.id = existing.id;
                    e.pan = item.pan;
                    e.brand = item.brand;
                    e.holder = item.holder;
                    e.expiry = item.expiry;
                    e.track2 = item.track2;
                    e.note = item.note;
                    e.serverCardId = item.serverId;
                    e.expired = item.expired;
                    e.receivedAt = System.currentTimeMillis();
                    e.isSelected = existing != null && existing.isSelected;
                    db.cardDao().insert(e);
                    if (existing == null) {
                        showNewCardNotification(e);
                    }
                }
            });
            OperationLogEntity log = new OperationLogEntity();
            log.timestamp = System.currentTimeMillis();
            log.action = "Synced cards";
            log.details = "Synced " + cards.size() + " cards";
            db.operationLogDao().insert(log);
            Intent i = new Intent("de.tu_darmstadt.seemoo.nfcgate.hce.SYNC_UPDATE");
            i.putExtra("last_sync", System.currentTimeMillis());
            i.setPackage(getPackageName());
            sendBroadcast(i);
        } catch (Exception e) {
            // ignore and retry on next cycle
        }
    }

    private Notification buildServiceNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_SERVICE)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.notif_cloud_sync_title))
                .setContentText(getString(R.string.notif_cloud_sync_text))
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void showNewCardNotification(CardEntity card) {
        Intent intent = new Intent(this, ReceivedCardsActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, (int) (System.currentTimeMillis() % Integer.MAX_VALUE), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String last4 = card.last4();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_NEW_CARD)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.new_card_received_title))
                .setContentText(getString(R.string.new_card_received_text, card.brand == null ? "UNKNOWN" : card.brand, last4))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_SOUND)
                .build();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), notification);
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel serviceChannel = new NotificationChannel(CHANNEL_SERVICE, getString(R.string.channel_cloud_sync), NotificationManager.IMPORTANCE_LOW);
        NotificationChannel newCardChannel = new NotificationChannel(CHANNEL_NEW_CARD, getString(R.string.channel_new_card), NotificationManager.IMPORTANCE_DEFAULT);
        newCardChannel.enableVibration(true);
        newCardChannel.enableLights(true);
        nm.createNotificationChannel(serviceChannel);
        nm.createNotificationChannel(newCardChannel);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(pullRunnable);
        if (eventSource != null) eventSource.stop();
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
