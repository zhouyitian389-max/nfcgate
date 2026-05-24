package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardDao;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardDatabase;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardEntity;
import de.tu_darmstadt.seemoo.nfcgate.hce.service.YitianHostApduService;

public class ReceivedCardsActivity extends AppCompatActivity {
    private RecyclerView rv;
    private TextView tvEmpty;
    private CardDao dao;
    private Adapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_received);
        setTitle(R.string.title_received_cards);

        rv = findViewById(R.id.rv_cards);
        tvEmpty = findViewById(R.id.tv_empty);
        dao = CardDatabase.getInstance(this).cardDao();
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.reload();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdownNow();
    }

    class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private List<CardEntity> data = new ArrayList<>();

        void reload() {
            dbExecutor.execute(() -> {
                List<CardEntity> newData = dao.getAll();
                mainHandler.post(() -> {
                    data = newData;
                    notifyDataSetChanged();
                    boolean empty = data.isEmpty();
                    tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                    rv.setVisibility(empty ? View.GONE : View.VISIBLE);
                });
            });
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            CardEntity c = data.get(position);
            h.tvBrand.setText(c.brand == null ? "UNKNOWN" : c.brand);
            h.tvPan.setText(getString(R.string.masked_pan, c.last4()));
            h.tvHolder.setText(c.holder == null || c.holder.isEmpty()
                    ? getString(R.string.card_holder) : c.holder);
            h.tvSelected.setVisibility(c.isSelected ? View.VISIBLE : View.GONE);
            h.itemView.setOnClickListener(v -> {
                dbExecutor.execute(() -> {
                    dao.clearSelection();
                    dao.select(c.id);
                    mainHandler.post(() -> {
                        sendSelectionChangedBroadcast();
                        reload();
                        Toast.makeText(ReceivedCardsActivity.this,
                                getString(R.string.toast_card_selected, c.last4()),
                                Toast.LENGTH_SHORT).show();
                    });
                });
            });
            h.btnDelete.setOnClickListener(v -> {
                dbExecutor.execute(() -> {
                    dao.deleteById(c.id);
                    mainHandler.post(() -> {
                        sendSelectionChangedBroadcast();
                        reload();
                        Toast.makeText(ReceivedCardsActivity.this, R.string.toast_card_deleted, Toast.LENGTH_SHORT).show();
                    });
                });
            });
        }

        @Override
        public int getItemCount() { return data == null ? 0 : data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvBrand, tvPan, tvHolder, tvSelected;
            View btnDelete;
            VH(View v) {
                super(v);
                tvBrand = v.findViewById(R.id.tv_brand);
                tvPan = v.findViewById(R.id.tv_pan);
                tvHolder = v.findViewById(R.id.tv_holder);
                tvSelected = v.findViewById(R.id.tv_selected);
                btnDelete = v.findViewById(R.id.btn_delete);
            }
        }
    }

    private void sendSelectionChangedBroadcast() {
        Intent intent = new Intent(YitianHostApduService.ACTION_SELECTION_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
}
