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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardDao;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardDatabase;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardEntity;
import de.tu_darmstadt.seemoo.nfcgate.hce.cloud.CloudApiClient;
import de.tu_darmstadt.seemoo.nfcgate.hce.service.YitianHostApduService;
import de.tu_darmstadt.seemoo.nfcgate.hce.util.CardBackupHelper;

public class ReceivedCardsActivity extends AppCompatActivity {
    private RecyclerView rv;
    private TextView tvEmpty;
    private CardDao dao;
    private Adapter adapter;
    private ActivityResultLauncher<String[]> restoreFileLauncher;
    private String searchQuery = "";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isActive = new AtomicBoolean(true);

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
        restoreFileLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        showRestorePasswordDialog(uri);
                    }
                });
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position >= 0 && position < adapter.data.size()) {
                    CardEntity card = adapter.data.get(position);
                    showDeleteConfirm(card);
                } else {
                    adapter.reload();
                }
            }
        });
        helper.attachToRecyclerView(rv);
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
        } else if (item.getItemId() == R.id.action_search) {
            return true;
        } else if (item.getItemId() == R.id.action_clear_expired) {
            dbExecutor.execute(() -> {
                int deleted = dao.clearExpired();
                postToMainIfActive(() -> {
                    Toast.makeText(this, getString(R.string.cleared_expired_cards, deleted), Toast.LENGTH_SHORT).show();
                    adapter.reload();
                });
            });
            return true;
        } else if (item.getItemId() == R.id.action_restore_backup) {
            launchRestoreFilePicker();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null && searchItem.getActionView() instanceof SearchView) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            searchView.setQueryHint(getString(R.string.search_cards_hint));
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    searchQuery = query == null ? "" : query.trim();
                    adapter.reload();
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    searchQuery = newText == null ? "" : newText.trim();
                    adapter.reload();
                    return true;
                }
            });
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.reload();
    }

    @Override
    protected void onDestroy() {
        isActive.set(false);
        mainHandler.removeCallbacksAndMessages(null);
        dbExecutor.shutdownNow();
        super.onDestroy();
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
                postToMainIfActive(() -> {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("application/octet-stream");
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent,
                            getString(R.string.menu_encrypted_backup)));
                    Toast.makeText(this, R.string.backup_success, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                postToMainIfActive(() -> Toast.makeText(this,
                        getString(R.string.backup_failed, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void launchRestoreFilePicker() {
        restoreFileLauncher.launch(new String[]{"*/*"});
    }

    private void showRestorePasswordDialog(Uri ybakUri) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        EditText etPassword = new EditText(this);
        etPassword.setHint(getString(R.string.restore_password_prompt));
        etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etPassword);

        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_restore_backup)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String pw = etPassword.getText().toString();
                    if (pw.isEmpty()) {
                        Toast.makeText(this, R.string.backup_password_prompt,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    performRestore(ybakUri, pw);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void performRestore(Uri ybakUri, String password) {
        dbExecutor.execute(() -> {
            File tempFile = null;
            try {
                // Copy URI content to a temp file so CardBackupHelper can read it
                tempFile = File.createTempFile("restore", ".ybak", getCacheDir());
                try (InputStream is = getContentResolver().openInputStream(ybakUri)) {
                    if (is == null) throw new FileNotFoundException("Cannot open backup URI");
                    byte[] buf = new byte[8192];
                    int n;
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                        while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
                    }
                }
                int count = CardBackupHelper.restoreFromBackup(
                        CardDatabase.getInstance(this), tempFile, password);
                postToMainIfActive(() -> {
                    Toast.makeText(this,
                            getString(R.string.restore_success, count),
                            Toast.LENGTH_SHORT).show();
                    adapter.reload();
                });
            } catch (Exception e) {
                postToMainIfActive(() -> Toast.makeText(this,
                        getString(R.string.restore_failed, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    tempFile.delete();
                }
            }
        });
    }

    class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private List<CardEntity> data = new ArrayList<>();

        void reload() {
            dbExecutor.execute(() -> {
                if (!isActive.get()) {
                    return;
                }
                List<CardEntity> newData = searchQuery.isEmpty() ? dao.getAll() : dao.search(searchQuery);
                postToMainIfActive(() -> {
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
            h.tvNote.setText(c.note == null ? "" : c.note);
            bindExpiryBadge(h, c);
            h.itemView.setAlpha(c.expired ? 0.5f : 1f);
            h.tvSelected.setVisibility(c.isSelected ? View.VISIBLE : View.GONE);
            h.itemView.setOnLongClickListener(v -> {
                showEditNoteDialog(c);
                return true;
            });
            h.itemView.setOnClickListener(v -> {
                dbExecutor.execute(() -> {
                    dao.clearSelection();
                    dao.select(c.id);
                    postToMainIfActive(() -> {
                        sendSelectionChangedBroadcast();
                        reload();
                        Toast.makeText(ReceivedCardsActivity.this,
                                getString(R.string.toast_card_selected, c.last4()),
                                Toast.LENGTH_SHORT).show();
                    });
                });
            });
            h.btnDelete.setOnClickListener(v -> {
                showDeleteConfirm(c);
            });
        }

        @Override
        public int getItemCount() { return data == null ? 0 : data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvBrand, tvPan, tvHolder, tvSelected, tvNote, tvExpiryBadge;
            View btnDelete;
            VH(View v) {
                super(v);
                tvBrand = v.findViewById(R.id.tv_brand);
                tvPan = v.findViewById(R.id.tv_pan);
                tvHolder = v.findViewById(R.id.tv_holder);
                tvSelected = v.findViewById(R.id.tv_selected);
                tvNote = v.findViewById(R.id.tv_note);
                tvExpiryBadge = v.findViewById(R.id.tv_expiry_badge);
                btnDelete = v.findViewById(R.id.btn_delete);
            }
        }
    }

    private void showDeleteConfirm(CardEntity c) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_card_title)
                .setMessage(R.string.dialog_delete_card_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    dbExecutor.execute(() -> {
                        dao.deleteById(c.id);
                        if (c.serverCardId != null && !c.serverCardId.isEmpty()) {
                            try {
                                new CloudApiClient(this).requestDeleteCard(c.serverCardId);
                            } catch (Exception ignored) {
                            }
                        }
                        postToMainIfActive(() -> {
                            sendSelectionChangedBroadcast();
                            adapter.reload();
                            Toast.makeText(this, R.string.toast_card_deleted, Toast.LENGTH_SHORT).show();
                        });
                    });
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    int position = adapter.data.indexOf(c);
                    if (position >= 0) {
                        adapter.notifyItemChanged(position);
                    } else {
                        adapter.reload();
                    }
                })
                .show();
    }

    private void showEditNoteDialog(CardEntity c) {
        EditText input = new EditText(this);
        input.setText(c.note == null ? "" : c.note);
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_edit_note_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String note = input.getText().toString();
                    dbExecutor.execute(() -> {
                        c.note = note;
                        dao.insert(c);
                        postToMainIfActive(adapter::reload);
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void postToMainIfActive(Runnable task) {
        if (!isActive.get()) {
            return;
        }
        mainHandler.post(() -> {
            if (!isActive.get() || isFinishing() || isDestroyed()) {
                return;
            }
            task.run();
        });
    }

    private void bindExpiryBadge(Adapter.VH h, CardEntity c) {
        long days = parseExpiryDays(c.expiry);
        if (c.expired || days < 0) {
            h.tvExpiryBadge.setVisibility(View.VISIBLE);
            h.tvExpiryBadge.setText(getString(R.string.label_expired));
            h.tvExpiryBadge.setBackgroundColor(0xFFB00020);
            return;
        }
        if (days <= 30) {
            h.tvExpiryBadge.setVisibility(View.VISIBLE);
            h.tvExpiryBadge.setText(getString(R.string.label_expires_in, days));
            h.tvExpiryBadge.setBackgroundColor(0xFFF9A825);
            return;
        }
        h.tvExpiryBadge.setVisibility(View.GONE);
    }

    private long parseExpiryDays(String expiry) {
        try {
            if (expiry == null) return Long.MAX_VALUE;
            String normalized = expiry.replace("/", "").trim();
            if (normalized.length() != 4) return Long.MAX_VALUE;
            int mm = Integer.parseInt(normalized.substring(0, 2));
            int yy = Integer.parseInt(normalized.substring(2, 4)) + 2000;
            java.util.Calendar now = java.util.Calendar.getInstance();
            java.util.Calendar exp = java.util.Calendar.getInstance();
            exp.set(java.util.Calendar.YEAR, yy);
            exp.set(java.util.Calendar.MONTH, mm - 1);
            exp.set(java.util.Calendar.DAY_OF_MONTH, exp.getActualMaximum(java.util.Calendar.DAY_OF_MONTH));
            long diff = exp.getTimeInMillis() - now.getTimeInMillis();
            return diff / (24L * 60L * 60L * 1000L);
        } catch (Exception ignored) {
            return Long.MAX_VALUE;
        }
    }

    private void sendSelectionChangedBroadcast() {
        Intent intent = new Intent(YitianHostApduService.ACTION_SELECTION_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
}
