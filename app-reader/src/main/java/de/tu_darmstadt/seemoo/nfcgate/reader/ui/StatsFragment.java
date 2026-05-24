package de.tu_darmstadt.seemoo.nfcgate.reader.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.tu_darmstadt.seemoo.nfcgate.reader.R;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.AppDatabase;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.CardBrandCount;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.ScanRecordEntity;
import de.tu_darmstadt.seemoo.nfcgate.reader.model.ScanRecord;
import de.tu_darmstadt.seemoo.nfcgate.reader.util.CardBrandDetector;

public class StatsFragment extends Fragment {
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private AppDatabase database;
    private CardBrandStatsAdapter adapter;
    private TextView tvTotal;
    private TextView tvUploadRate;
    private TextView tvTrend;
    private TextView tvEmpty;
    private RecyclerView rvCards;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        database = AppDatabase.getInstance(requireContext());
        tvTotal = view.findViewById(R.id.tv_total_count);
        tvUploadRate = view.findViewById(R.id.tv_upload_rate);
        tvTrend = view.findViewById(R.id.tv_recent_trend);
        tvEmpty = view.findViewById(R.id.tv_empty);
        rvCards = view.findViewById(R.id.rv_cards);
        adapter = new CardBrandStatsAdapter();
        rvCards.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvCards.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCardStats();
    }

    private void loadCardStats() {
        ioExecutor.execute(() -> {
            List<ScanRecordEntity> all = database.scanRecordDao().getAll();
            int total = all.size();
            int uploaded = 0;
            for (ScanRecordEntity entity : all) {
                if (entity.uploaded) {
                    uploaded++;
                }
            }

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

            String uploadRateText = total == 0
                    ? getString(R.string.upload_rate_empty)
                    : String.format(Locale.US, "%d%% (%d/%d)",
                    Math.round((uploaded * 100f) / total), uploaded, total);
            String trendText = buildRecentTrend(all);

            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                tvTotal.setText(getString(R.string.total_scan_count, total));
                tvUploadRate.setText(getString(R.string.upload_success_rate, uploadRateText));
                tvTrend.setText(trendText);
                adapter.setSections(sections);
                boolean empty = sections.isEmpty();
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                rvCards.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        });
    }

    private String buildRecentTrend(List<ScanRecordEntity> records) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        SimpleDateFormat format = new SimpleDateFormat("MM-dd", Locale.US);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            Calendar day = (Calendar) calendar.clone();
            day.add(Calendar.DAY_OF_YEAR, -i);
            counts.put(format.format(day.getTime()), 0);
        }
        for (ScanRecordEntity entity : records) {
            String day = format.format(entity.timestamp);
            if (counts.containsKey(day)) {
                counts.put(day, counts.get(day) + 1);
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add(getString(R.string.recent_trend_title));
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            lines.add(getString(R.string.recent_trend_format, entry.getKey(), entry.getValue()));
        }
        return TextUtils.join("\n", lines);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }
}
