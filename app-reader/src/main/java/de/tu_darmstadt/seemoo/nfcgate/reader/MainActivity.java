package de.tu_darmstadt.seemoo.nfcgate.reader;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCDevice;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCManager;
import de.tu_darmstadt.seemoo.nfcgate.reader.ui.DeviceSelector;

public class MainActivity extends AppCompatActivity {

    private NFCManager nfcManager;
    private NFCDevice selectedDevice;

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

        // List + Navigation
        rvScanHistory = findViewById(R.id.rv_scan_history);
        bottomNav     = findViewById(R.id.bottom_nav);

        setupButtons();
        setupBottomNavigation();
        updateNfcStatus();
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

        btnExport.setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.export_coming_soon), Toast.LENGTH_SHORT).show());

        btnUpload.setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.upload_coming_soon), Toast.LENGTH_SHORT).show());

        btnSettings.setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.settings_coming_soon), Toast.LENGTH_SHORT).show());
    }

    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_data) {
                Toast.makeText(this, getString(R.string.nav_data), Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.nav_settings) {
                Toast.makeText(this, getString(R.string.nav_settings), Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
    }

    private void startNfcCapture() {
        if (selectedDevice == null || nfcManager == null) return;
        nfcManager.startCapture(selectedDevice, event -> runOnUiThread(() -> {
            tvNfcStatus.setText(getString(R.string.nfc_status_scanning));
            tvLastCard.setText("Last Device: " + selectedDevice.getName());
            Toast.makeText(this, getString(R.string.capture_started), Toast.LENGTH_SHORT).show();
        }));
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
