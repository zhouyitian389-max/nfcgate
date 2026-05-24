package de.tu_darmstadt.seemoo.nfcgate.reader.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import de.tu_darmstadt.seemoo.nfcgate.reader.R;
import de.tu_darmstadt.seemoo.nfcgate.reader.model.ScanRecord;
import de.tu_darmstadt.seemoo.nfcgate.reader.util.CardBrandDetector;

public class CardBrandStatsAdapter extends RecyclerView.Adapter<CardBrandStatsAdapter.ViewHolder> {

    public static class BrandSection {
        public final CardBrandDetector.CardBrand brand;
        public final int count;
        public final List<ScanRecord> records;
        public boolean expanded;

        public BrandSection(CardBrandDetector.CardBrand brand, int count, List<ScanRecord> records) {
            this.brand = brand;
            this.count = count;
            this.records = records;
        }
    }

    private final List<BrandSection> sections = new ArrayList<>();

    public void setSections(List<BrandSection> list) {
        sections.clear();
        sections.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card_brand_stat, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BrandSection section = sections.get(position);
        holder.logo.setImageResource(CardBrandDetector.getDrawableRes(section.brand));
        holder.brandName.setText(CardBrandDetector.getDisplayNameRes(section.brand));
        holder.binRange.setText(holder.itemView.getContext().getString(R.string.brand_bin_range_template,
                CardBrandDetector.getBinRange(section.brand)));
        holder.count.setText(holder.itemView.getContext().getString(R.string.brand_count_template, section.count));

        holder.records.setVisibility(section.expanded ? View.VISIBLE : View.GONE);
        if (section.expanded) {
            StringBuilder builder = new StringBuilder();
            for (ScanRecord record : section.records) {
                if (builder.length() > 0) builder.append('\n');
                builder.append(record.getFormattedDate()).append(" · ").append(record.getMaskedPan());
            }
            holder.records.setText(builder.toString());
        } else {
            holder.records.setText("");
        }

        holder.itemView.setOnClickListener(v -> {
            section.expanded = !section.expanded;
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return sections.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView logo;
        final TextView brandName;
        final TextView binRange;
        final TextView count;
        final TextView records;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            logo = itemView.findViewById(R.id.iv_brand_logo);
            brandName = itemView.findViewById(R.id.tv_brand_name);
            binRange = itemView.findViewById(R.id.tv_bin_range);
            count = itemView.findViewById(R.id.tv_count);
            records = itemView.findViewById(R.id.tv_records);
        }
    }
}
