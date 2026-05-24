package de.tu_darmstadt.seemoo.nfcgate.reader;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.CompositePageTransformer;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tu_darmstadt.seemoo.nfcgate.reader.db.AppDatabase;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.ScanRecordEntity;
import de.tu_darmstadt.seemoo.nfcgate.reader.model.ScanRecord;
import de.tu_darmstadt.seemoo.nfcgate.reader.network.UploadService;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCDevice;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCManager;
import de.tu_darmstadt.seemoo.nfcgate.reader.settings.SettingsManager;
import de.tu_darmstadt.seemoo.nfcgate.reader.ui.CardPagerAdapter;
import de.tu_darmstadt.seemoo.nfcgate.reader.ui.DeviceSelector;
import de.tu_darmstadt.seemoo.nfcgate.reader.ui.ScanHistoryAdapter;
import de.tu_darmstadt.seemoo.nfcgate.reader.util.CardBrandDetector;

public class MainActivity extends AppCompatActivity {
    private static final Pattern PAN_PATTERN = Pattern.compile("(?<!\\d)(\\d{13,19})(?!\\d)");

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private NFCManager nfcManager;
    private NFCDevice selectedDevice;
    private AppDatabase appDatabase;
    private ScanHistoryAdapter scanHistoryAdapter;
    private CardPagerAdapter cardPagerAdapter;

    private TextView tvNfcStatus;
    private TextView tvLastCard;
    private TextView tvHistoryEmpty;
    private MaterialButton btnStartScan;
    private MaterialButton btnExport;
    private MaterialButton btnUpload;
    private MaterialButton btnSettings;
    private RecyclerView rvScanHistory;
    private BottomNavigationView bottomNav;
    private ViewPager2 cardPager;
    private TabLayout cardDots;
    private MaterialCardView cardNfcStatus;

    private Runnable autoScrollRunnable;
    private Runnable stopCaptureRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SettingsManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        nfcManager = new NFCManager(this);
        appDatabase = AppDatabase.getInstance(this);

        tvNfcStatus = findViewById(R.id.tv_nfc_status);
        tvLastCard = findViewById(R.id.tv_last_card);
        tvHistoryEmpty = findViewById(R.id.tv_history_empty);

        btnStartScan = findViewById(R.id.btn_start_scan);
        btnExport = findViewById(R.id.btn_export);
        btnUpload = findViewById(R.id.btn_upload);
        btnSettings = findViewById(R.id.btn_settings);

        rvScanHistory = findViewById(R.id.rv_scan_history);
        cardPager = findViewById(R.id.vp_wallet_cards);
        cardDots = findViewById(R.id.tab_card_indicator);
        cardNfcStatus = findViewById(R.id.card_nfc_status);

        setupRecyclerView();
        setupCardPager();

        bottomNav = findViewById(R.id.bottom_nav);

        setupButtons();
        setupBottomNavigation();
        loadHistoryFromDatabase();
        updateNfcStatus();

        if (SettingsManager.isAutoScanEnabled(this)) {
            autoStartCapture();
        }
    }

    private void setupRecyclerView() {
        scanHistoryAdapter = new ScanHistoryAdapter();
        rvScanHistory.setLayoutManager(new LinearLayoutManager(this));
        rvScanHistory.setAdapter(scanHistoryAdapter);
    }

    private void setupCardPager() {
        cardPagerAdapter = new CardPagerAdapter();
        cardPager.setAdapter(cardPagerAdapter);
        cardPagerAdapter.setCards(defaultCards());

        CompositePageTransformer transformer = new CompositePageTransformer();
        transformer.addTransformer(new MarginPageTransformer(24));
        transformer.addTransformer((page, position) -> {
            float scale = 0.9f + (1 - Math.abs(position)) * 0.1f;
            page.setScaleY(scale);
            page.setScaleX(scale);
            page.setAlpha(0.7f + (1 - Math.abs(position)) * 0.3f);
        });
        cardPager.setPageTransformer(transformer);

        new TabLayoutMediator(cardDots, cardPager, (tab, position) -> { }).attach();

        autoScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (cardPagerAdapter.getItemCount() > 1) {
                    int next = (cardPager.getCurrentItem() + 1) % cardPagerAdapter.getItemCount();
                    cardPager.setCurrentItem(next, true);
                }
                mainHandler.postDelayed(this, 3000L);
            }
        };
    }

    private List<CardPagerAdapter.CardItem> defaultCards() {
        return new ArrayList<>(Arrays.asList(
                new CardPagerAdapter.CardItem(CardBrandDetector.CardBrand.UNIONPAY, "1024"),
                new CardPagerAdapter.CardItem(CardBrandDetector.CardBrand.MASTERCARD, "8899"),
                new CardPagerAdapter.CardItem(CardBrandDetector.CardBrand.VISA, "7788"),
                new CardPagerAdapter.CardItem(CardBrandDetector.CardBrand.AMEX, "0099"),
                new CardPagerAdapter.CardItem(CardBrandDetector.CardBrand.JCB, "5566"),
                new CardPagerAdapter.CardItem(CardBrandDetector.CardBrand.DISCOVER, "4321")
        ));
    }

    private void setupButtons() {
        btnStartScan.setOnClickListener(v -> {
            performButtonHaptic(v);
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

        btnExport.setOnClickListener(v -> {
            performButtonHaptic(v);
            exportHistory();
        });
        btnUpload.setOnClickListener(v -> {
            performButtonHaptic(v);
            uploadHistory();
        });
        btnSettings.setOnClickListener(v -> {
            performButtonHaptic(v);
            openSettings();
        });
    }

    private void performButtonHaptic(View view) {
        if (SettingsManager.isHapticFeedbackEnabled(this)) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        }
    }

    private void setupBottomNavigation() {
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_wallet) {
                startActivity(new Intent(this, CardListActivity.class));
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                return true;
            } else if (id == R.id.nav_settings) {
                openSettings();
                return true;
            }
            return false;
        });
    }

    private void autoStartCapture() {
        List<NFCDevice> devices = nfcManager.getAvailableDevices();
        if (!devices.isEmpty()) {
            selectedDevice = devices.get(0);
            startNfcCapture();
        }
    }

    private void startNfcCapture() {
        if (selectedDevice == null || nfcManager == null) return;
        tvNfcStatus.setText(getString(R.string.nfc_status_scanning));

        if (stopCaptureRunnable != null) {
            mainHandler.removeCallbacks(stopCaptureRunnable);
        }

        int timeoutSeconds = Math.max(5, Math.min(60, SettingsManager.getScanTimeoutSeconds(this)));
        stopCaptureRunnable = () -> {
            if (nfcManager != null) {
                nfcManager.stopCapture();
            }
            tvNfcStatus.setText(getString(R.string.nfc_status_ready));
            Toast.makeText(this, R.string.toast_scan_timeout, Toast.LENGTH_SHORT).show();
        };
        mainHandler.postDelayed(stopCaptureRunnable, timeoutSeconds * 1000L);

        nfcManager.startCapture(selectedDevice, event -> runOnUiThread(() -> {
            String deviceName = selectedDevice.getName();
            String source = selectedDevice.getSource().name();
            String rawData = event != null ? event.toString() : "No data";
            String pan = extractPan(rawData);
            CardBrandDetector.CardBrand brand = CardBrandDetector.detect(pan);

            ScanRecord record = new ScanRecord(deviceName, source, rawData, pan, brand);
            scanHistoryAdapter.addRecord(record);
            persistRecord(record);

            String last4 = pan != null && pan.length() >= 4 ? pan.substring(pan.length() - 4) : getString(R.string.unknown_pan);
            addDetectedCardToPager(brand, last4);

            tvLastCard.setText(getString(R.string.last_card_template, getString(CardBrandDetector.getDisplayNameRes(brand))));
            tvNfcStatus.setText(getString(R.string.nfc_status_ready));
            updateHistoryVisibility();
            animateScanSuccess();
            performScanSuccessVibration();

            Toast.makeText(this, R.string.toast_scan_success, Toast.LENGTH_SHORT).show();
        }));
    }

    private void performScanSuccessVibration() {
        if (!SettingsManager.isHapticFeedbackEnabled(this)) {
            return;
        }
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(100L);
        }
    }

    private void animateScanSuccess() {
        ValueAnimator animator = ValueAnimator.ofArgb(
                ContextCompat.getColor(this, R.color.dark_surface),
                ContextCompat.getColor(this, R.color.gold),
                ContextCompat.getColor(this, R.color.dark_surface));
        animator.setDuration(500L);
        animator.addUpdateListener(a -> cardNfcStatus.setCardBackgroundColor((Integer) a.getAnimatedValue()));
        animator.start();
    }

    private void addDetectedCardToPager(CardBrandDetector.CardBrand brand, String last4) {
        List<CardPagerAdapter.CardItem> cards = cardPagerAdapter.getCards();
        cards.add(0, new CardPagerAdapter.CardItem(brand, last4));
        cardPagerAdapter.setCards(cards);
    }

    private void loadHistoryFromDatabase() {
        ioExecutor.execute(() -> {
            List<ScanRecordEntity> entities = appDatabase.scanRecordDao().getAll();
            List<ScanRecord> records = new ArrayList<>(entities.size());
            List<CardPagerAdapter.CardItem> cards = defaultCards();
            for (ScanRecordEntity entity : entities) {
                ScanRecord record = entity.toRecord();
                records.add(record);
                String pan = record.getPan();
                String last4 = pan != null && pan.length() >= 4
                        ? pan.substring(pan.length() - 4)
                        : getString(R.string.unknown_pan);
                cards.add(new CardPagerAdapter.CardItem(record.getCardBrand(), last4));
            }
            mainHandler.post(() -> {
                scanHistoryAdapter.setRecords(records);
                cardPagerAdapter.setCards(cards);
                updateHistoryVisibility();
            });
        });
    }

    private void persistRecord(ScanRecord record) {
        ioExecutor.execute(() -> {
            long id = appDatabase.scanRecordDao().insert(ScanRecordEntity.fromRecord(record));
            record.setId(id);
        });
    }

    private String extractPan(String rawData) {
        if (rawData == null) {
            return null;
        }
        Matcher matcher = PAN_PATTERN.matcher(rawData);
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
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(exportFile))) {
                writer.write("Time,Device,Source,Brand,PAN,Data\n");
                for (ScanRecord r : records) {
                    writer.write(csvEscape(r.getFormattedDate()));
                    writer.write(",");
                    writer.write(csvEscape(r.getDeviceName()));
                    writer.write(",");
                    writer.write(csvEscape(r.getSourceType()));
                    writer.write(",");
                    writer.write(csvEscape(r.getCardBrand().name()));
                    writer.write(",");
                    writer.write(csvEscape(r.getMaskedPan()));
                    writer.write(",");
                    writer.write(csvEscape(r.getRawData()));
                    writer.write("\n");
                }
            }

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

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void uploadHistory() {
        UploadService.uploadPending(this, appDatabase, ioExecutor, mainHandler);
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void updateNfcStatus() {
        if (tvNfcStatus != null) tvNfcStatus.setText(getString(R.string.nfc_status_ready));
        if (tvLastCard != null) tvLastCard.setText(getString(R.string.last_card_none));
        updateHistoryVisibility();
    }

    private void updateHistoryVisibility() {
        boolean hasRecords = scanHistoryAdapter != null && !scanHistoryAdapter.getRecords().isEmpty();
        if (tvHistoryEmpty != null) {
            tvHistoryEmpty.setVisibility(hasRecords ? View.GONE : View.VISIBLE);
            tvHistoryEmpty.setText(getString(R.string.no_history));
        }
        if (rvScanHistory != null) {
            rvScanHistory.setVisibility(hasRecords ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (autoScrollRunnable != null) {
            mainHandler.postDelayed(autoScrollRunnable, 3000L);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (autoScrollRunnable != null) {
            mainHandler.removeCallbacks(autoScrollRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (nfcManager != null) {
            nfcManager.stopCapture();
        }
        if (stopCaptureRunnable != null) {
            mainHandler.removeCallbacks(stopCaptureRunnable);
        }
        ioExecutor.shutdownNow();
    }
}
