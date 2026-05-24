package de.tu_darmstadt.seemoo.nfcgate.reader;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.tu_darmstadt.seemoo.nfcgate.reader.db.AppDatabase;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.CardBrandCount;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.ScanRecordEntity;
import de.tu_darmstadt.seemoo.nfcgate.reader.model.ScanRecord;
import de.tu_darmstadt.seemoo.nfcgate.reader.settings.SettingsManager;
import de.tu_darmstadt.seemoo.nfcgate.reader.ui.CardBrandStatsAdapter;
import de.tu_darmstadt.seemoo.nfcgate.reader.util.CardBrandDetector;

public class CardListActivity extends AppCompatActivity {

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private AppDatabase database;
    private CardBrandStatsAdapter adapter;
    private TextView tvTotal;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SettingsManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });

        tvTotal = findViewById(R.id.tv_total_count);
        tvEmpty = findViewById(R.id.tv_empty);
        RecyclerView rvCards = findViewById(R.id.rv_cards);

        adapter = new CardBrandStatsAdapter();
        rvCards.setLayoutManager(new LinearLayoutManager(this));
        rvCards.setAdapter(adapter);

        database = AppDatabase.getInstance(this);
        loadCardStats();
    }

    private void loadCardStats() {
        ioExecutor.execute(() -> {
            int total = database.scanRecordDao().count();
            List<CardBrandCount> brandCounts = database.scanRecordDao().getBrandCounts();
            List<CardBrandStatsAdapter.BrandSection> sections = new ArrayList<>();

            for (CardBrandCount count : brandCounts) {
                CardBrandDetector.CardBrand brand;
                try {
                    brand = CardBrandDetector.CardBrand.valueOf(count.cardBrand);
                } catch (Exception e) {
                    brand = CardBrandDetector.CardBrand.UNKNOWN;
                }

                List<ScanRecordEntity> entities = database.scanRecordDao().getByBrand(count.cardBrand);
                List<ScanRecord> records = new ArrayList<>(entities.size());
                for (ScanRecordEntity entity : entities) {
                    records.add(entity.toRecord());
                }

                sections.add(new CardBrandStatsAdapter.BrandSection(brand, count.count, records));
            }

            runOnUiThread(() -> {
                tvTotal.setText(getString(R.string.total_scan_count, total));
                adapter.setSections(sections);
                tvEmpty.setVisibility(sections.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }
}
