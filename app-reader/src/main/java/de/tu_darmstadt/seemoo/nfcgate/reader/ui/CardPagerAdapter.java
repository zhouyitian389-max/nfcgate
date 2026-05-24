package de.tu_darmstadt.seemoo.nfcgate.reader.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import de.tu_darmstadt.seemoo.nfcgate.reader.R;
import de.tu_darmstadt.seemoo.nfcgate.reader.util.CardBrandDetector;

public class CardPagerAdapter extends RecyclerView.Adapter<CardPagerAdapter.ViewHolder> {

    public static class CardItem {
        public final CardBrandDetector.CardBrand brand;
        public final String last4;

        public CardItem(CardBrandDetector.CardBrand brand, String last4) {
            this.brand = brand;
            this.last4 = last4;
        }
    }

    private final List<CardItem> cards = new ArrayList<>();

    public void setCards(List<CardItem> items) {
        cards.clear();
        cards.addAll(items);
        notifyDataSetChanged();
    }

    public List<CardItem> getCards() {
        return new ArrayList<>(cards);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card_pager, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CardItem card = cards.get(position);
        holder.cardBackground.setBackgroundResource(CardBrandDetector.getCardBackgroundRes(card.brand));
        holder.logo.setImageResource(CardBrandDetector.getDrawableRes(card.brand));
        holder.number.setText(holder.itemView.getContext().getString(R.string.masked_card_template, card.last4));
        holder.holder.setText(R.string.card_holder);
        holder.expiry.setText(R.string.card_expiry);
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final FrameLayout cardBackground;
        final ImageView logo;
        final TextView number;
        final TextView holder;
        final TextView expiry;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardBackground = itemView.findViewById(R.id.card_background);
            logo = itemView.findViewById(R.id.iv_brand_logo);
            number = itemView.findViewById(R.id.tv_card_number);
            holder = itemView.findViewById(R.id.tv_card_holder);
            expiry = itemView.findViewById(R.id.tv_card_expiry);
        }
    }
}
