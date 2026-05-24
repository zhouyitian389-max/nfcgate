package de.tu_darmstadt.seemoo.nfcgate.reader;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import de.tu_darmstadt.seemoo.nfcgate.reader.model.ScanRecord;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCDevice;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCManager;
import de.tu_darmstadt.seemoo.nfcgate.reader.ui.DeviceSelector;
import de.tu_darmstadt.seemoo.nfcgate.reader.ui.ScanHistoryAdapter;

public class MainActivity extends AppCompatActivity {

    private NFCManager nfcManager;
    private NFCDevice selectedDevice;
    private ScanHistoryAdapter scanHistoryAdapter;

    // Views
    private Toolbar toolbar;
    private MaterialCardView cardNfcStatus;
    private MaterialCardView cardScanHistory;
    private MaterialCardView cardQuickActions;
    private TextView tvNfcStatus;
    private TextView tvLastCard;
    private TextView tvHistoryEmpty;
    private MaterialButton btnStartScan;
    private MaterialButton btnExport;
    private MaterialButton btnUpload;
    private MaterialButton btnSettings;
    private RecyclerView rvScanHistory;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // NFC Manager
        nfcManager = new NFCManager(this);

        // Card Views
        cardNfcStatus    = findViewById(R.id.card_nfc_status);
        cardScanHistory  = findViewById(R.id.card_scan_history);
        cardQuickActions = findViewById(R.id.card_quick_actions);

        // Text Views
        tvNfcStatus    = findViewById(R.id.tv_nfc_status);
        tvLastCard     = findViewById(R.id.tv_last_card);
        tvHistoryEmpty = findViewById(R.id.tv_history_empty);

        // Buttons
        btnStartScan = findViewById(R.id.btn_start_scan);
        btnExport    = findViewById(R.id.btn_export);
        btnUpload    = findViewById(R.id.btn_upload);
        btnSettings  = findViewById(R.id.btn_settings);

        // RecyclerView
        rvScanHistory = findViewById(R.id.rv_scan_history);
        setupRecyclerView();

        // Bottom Navigation
        bottomNav = findViewById(R.id.bottom_nav);

        setupButtons();
        setupBottomNavigation();
        updateNfcStatus();
    }

    private void setupRecyclerView() {
        scanHistoryAdapter = new ScanHistoryAdapter();
        rvScanHistory.setLayoutManager(new LinearLayoutManager(this));
        rvScanHistory.setAdapter(scanHistoryAdapter);
    }

    private void setupButtons() {
        btnStartScan.setOnClickListener(v -> {
            List<NFCDevice> devices = nfcManager.getAvailableDevices();
            if (devices.isEmpty()) {
                Toast.makeText(this, "No NFC devices available", Toast.LENGTH_SHORT).show();
                return;
            }
            DeviceSelector.show(this, devices, device -> {
                selectedDevice = device;
                startNfcCapture();
            });
        });

        btnExport.setOnClickListener(v -> exportHistory());
        btnUpload.setOnClickListener(v -> uploadHistory());
        btnSettings.setOnClickListener(v -> openSettings());
    }

    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_data) {
                showDataTab();
                return true;
            } else if (id == R.id.nav_settings) {
                openSettings();
                return true;
            }
            return false;
        });
    }

    private void startNfcCapture() {
        if (selectedDevice == null || nfcManager == null) return;
        tvNfcStatus.setText(getString(R.string.nfc_status_scanning));
        nfcManager.startCapture(selectedDevice, event -> runOnUiThread(() -> {
            String deviceName = selectedDevice.getName();
            String source     = selectedDevice.getSource().name();
            String rawData    = event != null ? event.toString() : "No data";

            // Add to history
            ScanRecord record = new ScanRecord(deviceName, source, rawData);
            scanHistoryAdapter.addRecord(record);

            // Update UI
            tvLastCard.setText(getString(R.string.last_card_none) + " " + deviceName);
            tvHistoryEmpty.setVisibility(View.GONE);
            rvScanHistory.setVisibility(View.VISIBLE);

            Toast.makeText(this, getString(R.string.capture_started), Toast.LENGTH_SHORT).show();
        }));
    }

    // ─── Export ────────────────────────────────────────────────────────────────
    private void exportHistory() {
        List<ScanRecord> records = scanHistoryAdapter.getRecords();
        if (records.isEmpty()) {
            Toast.makeText(this, "No scan history to export", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File exportFile = new File(getCacheDir(), "nfcgate_export.csv");
            FileWriter writer = new FileWriter(exportFile);
            writer.write("Time,Device,Source,Data\n");
            for (ScanRecord r : records) {
                writer.write(String.format("%s,%s,%s,%s\n",
                        r.getFormattedDate(), r.getDeviceName(), r.getSourceType(), r.getRawData()));
            }
            writer.close();

            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider", exportFile);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Export Scan History"));
        } catch (IOException e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ─── Upload ────────────────────────────────────────────────────────────────
    private void uploadHistory() {
        List<ScanRecord> records = scanHistoryAdapter.getRecords();
        if (records.isEmpty()) {
            Toast.makeText(this, "No scan history to upload", Toast.LENGTH_SHORT).show();
            return;
        }
        // TODO: connect to real server endpoint
        Toast.makeText(this, "Upload: " + records.size() + " records queued (server not configured)",
                Toast.LENGTH_LONG).show();
    }

    // ─── Settings ──────────────────────────────────────────────────────────────
    private void openSettings() {
        Toast.makeText(this, "Settings screen coming soon", Toast.LENGTH_SHORT).show();
    }

    // ─── Data Tab ──────────────────────────────────────────────────────────────
    private void showDataTab() {
        List<ScanRecord> records = scanHistoryAdapter.getRecords();
        Toast.makeText(this, records.size() + " scan records total", Toast.LENGTH_SHORT).show();
    }

    private void updateNfcStatus() {
        if (tvNfcStatus != null)    tvNfcStatus.setText(getString(R.string.nfc_status_ready));
        if (tvLastCard != null)     tvLastCard.setText(getString(R.string.last_card_none));
        if (tvHistoryEmpty != null) tvHistoryEmpty.setText(getString(R.string.no_history));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (nfcManager != null) {
            nfcManager.stopCapture();
        }
    }
}
