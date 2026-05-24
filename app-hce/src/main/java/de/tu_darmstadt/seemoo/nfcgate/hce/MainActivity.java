package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardDatabase;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardEntity;
import de.tu_darmstadt.seemoo.nfcgate.hce.service.HttpReceiverService;

public class MainActivity extends AppCompatActivity {
    private TextView tvHttpStatus, tvCardCount, tvSelected, tvAuthor;
    private MaterialButton btnStart, btnStop, btnReceived, btnSettings, btnAbout;
    private boolean running = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isRunning = intent.getBooleanExtra(HttpReceiverService.EXTRA_RUNNING, false);
            int port = intent.getIntExtra(HttpReceiverService.EXTRA_PORT, 8080);
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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvHttpStatus = findViewById(R.id.tv_http_status);
        tvCardCount = findViewById(R.id.tv_card_count);
        tvSelected = findViewById(R.id.tv_selected_card);
        tvAuthor = findViewById(R.id.tv_author);
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        btnReceived = findViewById(R.id.btn_received_cards);
        btnSettings = findViewById(R.id.btn_settings);
        btnAbout = findViewById(R.id.btn_about);

        btnStart.setOnClickListener(v -> startReceiver());
        btnStop.setOnClickListener(v -> stopReceiver());
        btnReceived.setOnClickListener(v -> startActivity(new Intent(this, ReceivedCardsActivity.class)));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnAbout.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));

        tvAuthor.setText(R.string.author_credit);
        tvHttpStatus.setText(R.string.status_http_stopped);
        refreshFromDb();
        updateButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(HttpReceiverService.BROADCAST_STATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, f);
        }
        refreshFromDb();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(stateReceiver); } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    /** Reads user-configured HTTP port from preferences, falling back to 8080. */
    private int getConfiguredPort() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String raw = prefs.getString("server_port", "8080");
        try {
            int p = Integer.parseInt(raw == null ? "8080" : raw.trim());
            if (p < 1 || p > 65535) return 8080;
            return p;
        } catch (NumberFormatException e) {
            return 8080;
        }
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

    /** Runs Room queries off the main thread to avoid ANRs. */
    private void refreshFromDb() {
        ioExecutor.execute(() -> {
            int count = CardDatabase.getInstance(this).cardDao().count();
            CardEntity sel = CardDatabase.getInstance(this).cardDao().getSelected();
            mainHandler.post(() -> {
                tvCardCount.setText(getString(R.string.status_cards, count));
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
    }
}
