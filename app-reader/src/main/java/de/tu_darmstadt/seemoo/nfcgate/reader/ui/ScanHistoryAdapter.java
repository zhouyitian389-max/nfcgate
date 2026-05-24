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

public class ScanHistoryAdapter extends RecyclerView.Adapter<ScanHistoryAdapter.ViewHolder> {

    private final List<ScanRecord> records = new ArrayList<>();

    public void addRecord(ScanRecord record) {
        records.add(0, record);
        notifyItemInserted(0);
    }

    public void setRecords(List<ScanRecord> newRecords) {
        records.clear();
        records.addAll(newRecords);
        notifyDataSetChanged();
    }

    public List<ScanRecord> getRecords() {
        return new ArrayList<>(records);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scan_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScanRecord record = records.get(position);
        holder.ivBrand.setImageResource(CardBrandDetector.getDrawableRes(record.getCardBrand()));
        holder.tvTitle.setText(record.getMaskedPan());
        holder.tvSubtitle.setText(record.getFormattedTime() + " · " + record.getDeviceName() + " [" + record.getSourceType() + "]");
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivBrand;
        final TextView tvTitle;
        final TextView tvSubtitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivBrand = itemView.findViewById(R.id.iv_brand);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvSubtitle = itemView.findViewById(R.id.tv_subtitle);
        }
    }
}
