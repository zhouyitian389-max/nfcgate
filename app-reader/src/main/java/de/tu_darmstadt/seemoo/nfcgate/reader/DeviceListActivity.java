package de.tu_darmstadt.seemoo.nfcgate.reader;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.tu_darmstadt.seemoo.nfcgate.reader.cloud.CloudApiClient;

public class DeviceListActivity extends AppCompatActivity {
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final List<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_list);
        setTitle("Devices");
        ListView listView = findViewById(R.id.list_simple);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        listView.setAdapter(adapter);
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            String row = rows.get(position);
            String deviceId = row.contains("(") && row.endsWith(")")
                    ? row.substring(row.lastIndexOf('(') + 1, row.length() - 1)
                    : "";
            ioExecutor.execute(() -> logoutDevice(deviceId));
            return true;
        });
        ioExecutor.execute(this::loadDevices);
    }

    private void loadDevices() {
        try {
            List<CloudApiClient.DeviceItem> devices = new CloudApiClient(this).listDevices();
            rows.clear();
            for (CloudApiClient.DeviceItem d : devices) {
                rows.add(d.name + " · " + d.lastActive + " (" + d.id + ")");
            }
            runOnUiThread(() -> adapter.notifyDataSetChanged());
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void logoutDevice(String deviceId) {
        try {
            new CloudApiClient(this).logoutDevice(deviceId);
            runOnUiThread(() -> Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show());
            loadDevices();
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }
}
