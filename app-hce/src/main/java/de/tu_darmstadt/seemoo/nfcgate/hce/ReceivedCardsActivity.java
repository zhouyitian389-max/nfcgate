package de.tu_darmstadt.seemoo.nfcgate.hce;

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

public class ReceivedCardsActivity extends AppCompatActivity {
    private RecyclerView rv;
    private TextView tvEmpty;
    private CardDao dao;
    private Adapter adapter;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

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
        ioExecutor.shutdownNow();
    }

    class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        // Initialize to empty list to avoid NPE before the first reload completes.
        private List<CardEntity> data = new ArrayList<>();

        void reload() {
            ioExecutor.execute(() -> {
                final List<CardEntity> fresh = dao.getAll();
                mainHandler.post(() -> {
                    data = fresh != null ? fresh : new ArrayList<>();
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
            final CardEntity c = data.get(position);
            h.tvBrand.setText(c.brand == null ? "UNKNOWN" : c.brand);
            h.tvPan.setText(getString(R.string.masked_pan, c.last4()));
            h.tvHolder.setText(c.holder == null || c.holder.isEmpty()
                    ? getString(R.string.card_holder) : c.holder);
            h.tvSelected.setVisibility(c.isSelected ? View.VISIBLE : View.GONE);

            h.itemView.setOnClickListener(v -> ioExecutor.execute(() -> {
                dao.clearSelection();
                dao.select(c.id);
                mainHandler.post(() -> {
                    Toast.makeText(ReceivedCardsActivity.this,
                            getString(R.string.toast_card_selected, c.last4()),
                            Toast.LENGTH_SHORT).show();
                    reload();
                });
            }));

            h.btnDelete.setOnClickListener(v -> ioExecutor.execute(() -> {
                dao.deleteById(c.id);
                mainHandler.post(() -> {
                    Toast.makeText(ReceivedCardsActivity.this, R.string.toast_card_deleted,
                            Toast.LENGTH_SHORT).show();
                    reload();
                });
            }));
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
}
