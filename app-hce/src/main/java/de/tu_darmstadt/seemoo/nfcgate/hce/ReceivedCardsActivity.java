package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardDao;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardDatabase;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardEntity;
import de.tu_darmstadt.seemoo.nfcgate.hce.service.YitianHostApduService;
import de.tu_darmstadt.seemoo.nfcgate.hce.util.CardBackupHelper;

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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_received_cards, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_encrypted_backup) {
            showEncryptedBackupDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
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

    private void showEncryptedBackupDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        EditText etPassword = new EditText(this);
        etPassword.setHint(getString(R.string.backup_password_prompt));
        etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etPassword);

        EditText etConfirm = new EditText(this);
        etConfirm.setHint(getString(R.string.backup_password_confirm));
        etConfirm.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etConfirm);

        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_encrypted_backup)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String pw1 = etPassword.getText().toString();
                    String pw2 = etConfirm.getText().toString();
                    if (pw1.isEmpty() || !pw1.equals(pw2)) {
                        Toast.makeText(this, R.string.backup_password_mismatch,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    exportEncryptedBackup(pw1);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void exportEncryptedBackup(String password) {
        dbExecutor.execute(() -> {
            try {
                File ybakFile = CardBackupHelper.exportToEncryptedBackup(
                        this, CardDatabase.getInstance(this), password);
                Uri uri = FileProvider.getUriForFile(this,
                        getPackageName() + ".provider", ybakFile);
                mainHandler.post(() -> {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("application/octet-stream");
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent,
                            getString(R.string.menu_encrypted_backup)));
                    Toast.makeText(this, R.string.backup_success, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this,
                        getString(R.string.backup_failed, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        });
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
