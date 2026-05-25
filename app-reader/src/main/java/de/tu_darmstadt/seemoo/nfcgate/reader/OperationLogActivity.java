package de.tu_darmstadt.seemoo.nfcgate.reader;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.tu_darmstadt.seemoo.nfcgate.reader.cloud.CloudApiClient;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.AppDatabase;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.OperationLogEntity;

public class OperationLogActivity extends AppCompatActivity {
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final List<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_list);
        setTitle("Operation Logs");
        ListView listView = findViewById(R.id.list_simple);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        listView.setAdapter(adapter);
        ioExecutor.execute(this::loadLogs);
    }

    private void loadLogs() {
        AppDatabase db = AppDatabase.getInstance(this);
        try {
            List<CloudApiClient.LogItem> remote = new CloudApiClient(this).fetchLogs();
            for (CloudApiClient.LogItem item : remote) {
                OperationLogEntity e = new OperationLogEntity();
                e.timestamp = item.timestamp;
                e.action = item.action;
                e.details = item.details;
                db.operationLogDao().insert(e);
            }
        } catch (Exception ignored) {
        }
        List<OperationLogEntity> logs = db.operationLogDao().getRecent(200);
        rows.clear();
        SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.getDefault());
        for (OperationLogEntity log : logs) {
            rows.add(log.action + " at " + format.format(new Date(log.timestamp)) + "\n" + log.details);
        }
        runOnUiThread(() -> adapter.notifyDataSetChanged());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }
}
