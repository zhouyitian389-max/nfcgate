package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SimpleTextPagerAdapter extends RecyclerView.Adapter<SimpleTextPagerAdapter.VH> {
    private final List<String> pages;

    public SimpleTextPagerAdapter(List<String> pages) {
        this.pages = pages;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboarding_page, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.textView.setText(pages.get(position));
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView textView;

        VH(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.tv_page_text);
        }
    }
}
