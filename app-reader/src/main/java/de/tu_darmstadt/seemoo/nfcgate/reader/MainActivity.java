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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tu_darmstadt.seemoo.nfcgate.reader.model.ScanRecord;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCDevice;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCManager;
import de.tu_darmstadt.seemoo.nfcgate.reader.ui.DeviceSelector;
import de.tu_darmstadt.seemoo.nfcgate.reader.ui.ScanHistoryAdapter;
import de.tu_darmstadt.seemoo.nfcgate.reader.ui.WalletCardAdapter;
import de.tu_darmstadt.seemoo.nfcgate.reader.util.CardBrandDetector;

public class MainActivity extends AppCompatActivity {

    private NFCManager nfcManager;
    private NFCDevice selectedDevice;
    private ScanHistoryAdapter scanHistoryAdapter;

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

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        nfcManager = new NFCManager(this);

        tvNfcStatus = findViewById(R.id.tv_nfc_status);
        tvLastCard = findViewById(R.id.tv_last_card);
        tvHistoryEmpty = findViewById(R.id.tv_history_empty);

        btnStartScan = findViewById(R.id.btn_start_scan);
        btnExport = findViewById(R.id.btn_export);
        btnUpload = findViewById(R.id.btn_upload);
        btnSettings = findViewById(R.id.btn_settings);

        rvScanHistory = findViewById(R.id.rv_scan_history);
        setupRecyclerView();
        setupWalletCards();

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

    private void setupWalletCards() {
        RecyclerView walletCards = findViewById(R.id.rv_wallet_cards);
        walletCards.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        List<WalletCardAdapter.WalletCard> cards = Arrays.asList(
                new WalletCardAdapter.WalletCard(CardBrandDetector.CardBrand.UNIONPAY, "1024"),
                new WalletCardAdapter.WalletCard(CardBrandDetector.CardBrand.MASTERCARD, "8899"),
                new WalletCardAdapter.WalletCard(CardBrandDetector.CardBrand.VISA, "7788"),
                new WalletCardAdapter.WalletCard(CardBrandDetector.CardBrand.AMEX, "0099"),
                new WalletCardAdapter.WalletCard(CardBrandDetector.CardBrand.JCB, "5566"),
                new WalletCardAdapter.WalletCard(CardBrandDetector.CardBrand.DISCOVER, "4321")
        );
        walletCards.setAdapter(new WalletCardAdapter(cards));
    }

    private void setupButtons() {
        btnStartScan.setOnClickListener(v -> {
            List<NFCDevice> devices = nfcManager.getAvailableDevices();
            if (devices.isEmpty()) {
                Toast.makeText(this, R.string.toast_no_device, Toast.LENGTH_SHORT).show();
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
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_wallet) {
                startActivity(new Intent(this, CardListActivity.class));
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
            String source = selectedDevice.getSource().name();
            String rawData = event != null ? event.toString() : "No data";
            String pan = extractPan(rawData);
            CardBrandDetector.CardBrand brand = CardBrandDetector.detect(pan);

            ScanRecord record = new ScanRecord(deviceName, source, rawData, pan, brand);
            scanHistoryAdapter.addRecord(record);

            tvLastCard.setText(getString(R.string.last_card_template, brand.name()));
            tvHistoryEmpty.setVisibility(View.GONE);
            rvScanHistory.setVisibility(View.VISIBLE);

            Toast.makeText(this, R.string.capture_started, Toast.LENGTH_SHORT).show();
        }));
    }

    private String extractPan(String rawData) {
        if (rawData == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("(?<!\\d)(\\d{13,19})(?!\\d)").matcher(rawData);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void exportHistory() {
        List<ScanRecord> records = scanHistoryAdapter.getRecords();
        if (records.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_history_export, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File exportDir = new File(getCacheDir(), "exports");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }
            File exportFile = new File(exportDir, "yitian_wallet_export.csv");
            FileWriter writer = new FileWriter(exportFile);
            writer.write("Time,Device,Source,Brand,PAN,Data\n");
            for (ScanRecord r : records) {
                writer.write(String.format("%s,%s,%s,%s,%s,%s\n",
                        r.getFormattedDate(), r.getDeviceName(), r.getSourceType(), r.getCardBrand().name(), r.getMaskedPan(), r.getRawData()));
            }
            writer.close();

            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider", exportFile);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.export_chooser)));
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.toast_export_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void uploadHistory() {
        List<ScanRecord> records = scanHistoryAdapter.getRecords();
        if (records.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_history_upload, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, getString(R.string.toast_upload_queued, records.size()), Toast.LENGTH_LONG).show();
    }

    private void openSettings() {
        startActivity(new Intent(this, AboutActivity.class));
    }

    private void updateNfcStatus() {
        if (tvNfcStatus != null) tvNfcStatus.setText(getString(R.string.nfc_status_ready));
        if (tvLastCard != null) tvLastCard.setText(getString(R.string.last_card_none));
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
