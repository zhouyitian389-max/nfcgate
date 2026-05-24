package de.tu_darmstadt.seemoo.nfcgate.reader.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import de.tu_darmstadt.seemoo.nfcgate.reader.model.ScanRecord;

public class ScanHistoryAdapter extends RecyclerView.Adapter<ScanHistoryAdapter.ViewHolder> {

    private final List<ScanRecord> records = new ArrayList<>();

    public void addRecord(ScanRecord record) {
        records.add(0, record); // newest first
        notifyItemInserted(0);
    }

    public List<ScanRecord> getRecords() {
        return new ArrayList<>(records);
    }

    public void clear() {
        int size = records.size();
        records.clear();
        notifyItemRangeRemoved(0, size);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.two_line_list_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScanRecord record = records.get(position);
        holder.tvTitle.setText(record.getDeviceName() + " [" + record.getSourceType() + "]");
        holder.tvSubtitle.setText(record.getFormattedTime() + "  " + record.getRawData());
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvSubtitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle    = itemView.findViewById(android.R.id.text1);
            tvSubtitle = itemView.findViewById(android.R.id.text2);
        }
    }
}
