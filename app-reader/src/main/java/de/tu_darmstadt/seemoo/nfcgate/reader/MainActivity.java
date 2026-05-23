package de.tu_darmstadt.seemoo.nfcgate.reader;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;

import java.util.List;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCDevice;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCManager;
import de.tu_darmstadt.seemoo.nfcgate.reader.ui.DeviceSelector;

public class MainActivity extends AppCompatActivity {
    private NFCManager nfcManager;
    private NFCDevice selectedDevice;

    // Card Views
    private MaterialCardView cardNfcStatus;
    private MaterialCardView cardScanHistory;
    private MaterialCardView cardQuickActions;

    // Text Views
    private TextView tvNfcStatus;
    private TextView tvLastCard;
    private TextView tvHistoryEmpty;
    private TextView tvAuthor;

    // Buttons
    private MaterialButton btnStartScan;
    private MaterialButton btnExport;
    private MaterialButton btnUpload;
    private MaterialButton btnSettings;

    // Other Views
    private RecyclerView rvScanHistory;
    private BottomNavigationView bottomNav;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Initialize NFC Manager
        nfcManager = new NFCManager(this);

        // Initialize Card Views
        cardNfcStatus = findViewById(R.id.card_nfc_status);
        cardScanHistory = findViewById(R.id.card_scan_history);
        cardQuickActions = findViewById(R.id.card_quick_actions);

        // Initialize Text Views
        tvNfcStatus = findViewById(R.id.tv_nfc_status);
        tvLastCard = findViewById(R.id.tv_last_card);
        tvHistoryEmpty = findViewById(R.id.tv_history_empty);
        tvAuthor = findViewById(R.id.tv_author);

        // Initialize Buttons
        btnStartScan = findViewById(R.id.btn_start_scan);
        btnExport = findViewById(R.id.btn_export);
        btnUpload = findViewById(R.id.btn_upload);
        btnSettings = findViewById(R.id.btn_settings);

        // Initialize RecyclerView
        rvScanHistory = findViewById(R.id.rv_scan_history);

        // Initialize Bottom Navigation
        bottomNav = findViewById(R.id.bottom_nav);

        // Setup UI
        setupCardViews();
        setupButtons();
        setupBottomNavigation();
        updateNfcStatus();
    }

    private void setupCardViews() {
        // NFC Status Card
        if (cardNfcStatus != null) {
            cardNfcStatus.setCardElevation(4);
            cardNfcStatus.setRadius(12);
        }

        // Scan History Card
        if (cardScanHistory != null) {
            cardScanHistory.setCardElevation(4);
            cardScanHistory.setRadius(12);
        }

        // Quick Actions Card
        if (cardQuickActions != null) {
            cardQuickActions.setCardElevation(4);
            cardQuickActions.setRadius(12);
        }
    }

    private void setupButtons() {
        // Start Scan Button
        btnStartScan.setOnClickListener(v -> {
            if (nfcManager != null) {
                List<NFCDevice> devices = nfcManager.getAvailableDevices();
                if (devices.isEmpty()) {
                    Toast.makeText(this, "No NFC devices available", Toast.LENGTH_SHORT).show();
                    return;
                }
                DeviceSelector.show(this, devices, device -> {
                    selectedDevice = device;
                    startNfcCapture();
                });
            }
        });

        // Export Button
        btnExport.setOnClickListener(v -> {
            Toast.makeText(this, "Export feature coming soon", Toast.LENGTH_SHORT).show();
        });

        // Upload Button
        btnUpload.setOnClickListener(v -> {
            Toast.makeText(this, "Upload feature coming soon", Toast.LENGTH_SHORT).show();
        });

        // Settings Button
        btnSettings.setOnClickListener(v -> {
            Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            switch (item.getItemId()) {
                case R.id.nav_home:
                    Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show();
                    return true;
                case R.id.nav_data:
                    Toast.makeText(this, "Data", Toast.LENGTH_SHORT).show();
                    return true;
                case R.id.nav_settings:
                    Toast.makeText(this, "Settings", Toast.LENGTH_SHORT).show();
                    return true;
            }
            return false;
        });
    }

    private void startNfcCapture() {
        if (selectedDevice != null && nfcManager != null) {
            nfcManager.startCapture(selectedDevice, event -> runOnUiThread(() -> {
                tvNfcStatus.setText("Status: Scanning");
                Toast.makeText(this, "NFC Capture Started", Toast.LENGTH_SHORT).show();
            }));
        }
    }

    private void updateNfcStatus() {
        if (tvNfcStatus != null) {
            tvNfcStatus.setText("Status: Ready");
        }
        if (tvLastCard != null) {
            tvLastCard.setText("Last Card: None");
        }
        if (tvHistoryEmpty != null) {
            tvHistoryEmpty.setText("No scan history yet");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (nfcManager != null) {
            nfcManager.stopCapture();
        }
    }
}
