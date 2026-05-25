package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.tu_darmstadt.seemoo.nfcgate.hce.cloud.CloudApiClient;
import de.tu_darmstadt.seemoo.nfcgate.hce.cloud.SessionManager;
import de.tu_darmstadt.seemoo.nfcgate.hce.cloud.SyncStatusTracker;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardDatabase;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardEntity;
import de.tu_darmstadt.seemoo.nfcgate.hce.service.CloudSyncService;
import de.tu_darmstadt.seemoo.nfcgate.hce.service.HttpReceiverService;

public class MainActivity extends AppCompatActivity {
    private TextView tvHttpStatus, tvCardCount, tvSelected, tvAuthor;
    private TextView tvLastSync;
    private View viewSyncDot;
    private MaterialButton btnStart, btnStop, btnReceived, btnSettings, btnAbout;
    private boolean running = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private static final int REQUEST_POST_NOTIFICATIONS = 1001;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isRunning = intent.getBooleanExtra(HttpReceiverService.EXTRA_RUNNING, false);
            int port = intent.getIntExtra(HttpReceiverService.EXTRA_PORT, 8080);
            int count = intent.getIntExtra(HttpReceiverService.EXTRA_COUNT, 0);
            running = isRunning;
            tvHttpStatus.setText(isRunning
                    ? getString(R.string.status_http_running, port)
                    : getString(R.string.status_http_stopped));
            refreshFromDb();
            updateButtons();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SettingsManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvHttpStatus = findViewById(R.id.tv_http_status);
        tvCardCount = findViewById(R.id.tv_card_count);
        tvSelected = findViewById(R.id.tv_selected_card);
        tvAuthor = findViewById(R.id.tv_author);
        tvLastSync = findViewById(R.id.tv_last_sync);
        viewSyncDot = findViewById(R.id.view_sync_dot);
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        btnReceived = findViewById(R.id.btn_received_cards);
        btnSettings = findViewById(R.id.btn_settings);
        btnAbout = findViewById(R.id.btn_about);

        btnStart.setOnClickListener(v -> startReceiver());
        btnStop.setOnClickListener(v -> stopReceiver());
        btnReceived.setOnClickListener(v -> startActivity(new Intent(this, ReceivedCardsActivity.class)));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnSettings.setOnLongClickListener(v -> {
            startActivity(new Intent(this, DeviceListActivity.class));
            return true;
        });
        btnAbout.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        tvCardCount.setOnClickListener(v -> startActivity(new Intent(this, OperationLogActivity.class)));

        tvAuthor.setText(R.string.author_credit);
        tvHttpStatus.setText(R.string.status_http_stopped);
        requestNotificationPermissionIfNeeded();
        refreshFromDb();
        startCloudSyncIfEnabled();
        refreshCloudStats();
        updateButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!SessionManager.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        if (SettingsManager.isAutoSyncEnabled(this)) {
            startCloudSyncIfEnabled();
        } else {
            Intent stopIntent = new Intent(this, CloudSyncService.class);
            stopIntent.setAction(CloudSyncService.ACTION_STOP);
            startService(stopIntent);
        }
        IntentFilter f = new IntentFilter(HttpReceiverService.BROADCAST_STATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, f);
        }
        refreshFromDb();
        refreshCloudStats();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(stateReceiver); } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdownNow();
    }

    private void startReceiver() {
        Intent i = new Intent(this, HttpReceiverService.class);
        i.setAction(HttpReceiverService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i); else startService(i);
        Toast.makeText(this, getString(R.string.toast_started, getConfiguredPort()), Toast.LENGTH_SHORT).show();
    }

    private void stopReceiver() {
        Intent i = new Intent(this, HttpReceiverService.class);
        i.setAction(HttpReceiverService.ACTION_STOP);
        startService(i);
        Toast.makeText(this, R.string.toast_stopped, Toast.LENGTH_SHORT).show();
    }

    private void refreshFromDb() {
        dbExecutor.execute(() -> {
            CardDatabase database = CardDatabase.getInstance(this);
            int count = database.cardDao().count();
            CardEntity sel = database.cardDao().getSelected();
            long dayStart = startOfDay();
            int today = database.cardDao().countSince(dayStart);
            mainHandler.post(() -> {
                tvCardCount.setText(getString(R.string.status_cards, count));
                tvCardCount.append("\n" + getString(R.string.dashboard_today_cards, today));
                if (sel != null) {
                    tvSelected.setText(getString(R.string.status_selected,
                            (sel.brand == null ? "" : sel.brand) + " **** " + sel.last4()));
                } else {
                    tvSelected.setText(R.string.status_none_selected);
                }
            });
        });
    }

    private void updateButtons() {
        btnStart.setVisibility(running ? View.GONE : View.VISIBLE);
        btnStop.setVisibility(running ? View.VISIBLE : View.GONE);
        SyncStatusTracker.State state = SyncStatusTracker.getState();
        if (state == SyncStatusTracker.State.CONNECTED) {
            tvHttpStatus.setText(getString(R.string.sync_status_connected));
            if (viewSyncDot != null) viewSyncDot.setBackgroundColor(0xFF2E7D32);
        } else if (state == SyncStatusTracker.State.SYNCING) {
            tvHttpStatus.setText(getString(R.string.sync_status_syncing));
            if (viewSyncDot != null) viewSyncDot.setBackgroundColor(0xFFF9A825);
        } else if (!running) {
            tvHttpStatus.setText(getString(R.string.sync_status_offline));
            if (viewSyncDot != null) viewSyncDot.setBackgroundColor(0xFFB00020);
        }
        if (tvLastSync != null) {
            long last = SyncStatusTracker.getLastSuccessAt();
            String value = last <= 0 ? "--" : android.text.format.DateFormat.format("HH:mm:ss", last).toString();
            tvLastSync.setText(getString(R.string.dashboard_last_sync, value));
        }
    }

    private int getConfiguredPort() {
        String rawPort = PreferenceManager.getDefaultSharedPreferences(this).getString(HttpReceiverService.PREF_SERVER_PORT, "8080");
        int resolvedPort;
        try {
            resolvedPort = Integer.parseInt(rawPort);
        } catch (NumberFormatException e) {
            return 8080;
        }
        return resolvedPort >= 1 && resolvedPort <= 65535 ? resolvedPort : 8080;
    }

    private void startCloudSyncIfEnabled() {
        if (!SettingsManager.isAutoSyncEnabled(this) || !SessionManager.isLoggedIn(this)) return;
        Intent intent = new Intent(this, CloudSyncService.class);
        intent.setAction(CloudSyncService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void refreshCloudStats() {
        dbExecutor.execute(() -> {
            try {
                CloudApiClient.Stats stats = new CloudApiClient(this).fetchStats();
                mainHandler.post(() -> tvAuthor.setText(getString(R.string.author_credit) + "\n"
                        + getString(R.string.stats_summary, stats.totalUsers, stats.yourCards, stats.todayActive)));
            } catch (Exception ignored) {
            }
        });
    }

    private long startOfDay() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_POST_NOTIFICATIONS);
        }
    }
}
