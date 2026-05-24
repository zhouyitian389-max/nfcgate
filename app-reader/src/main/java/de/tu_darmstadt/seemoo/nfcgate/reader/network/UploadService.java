package de.tu_darmstadt.seemoo.nfcgate.reader.network;

import android.content.Context;
import android.os.Handler;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import de.tu_darmstadt.seemoo.nfcgate.reader.R;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.AppDatabase;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.ScanRecordEntity;
import de.tu_darmstadt.seemoo.nfcgate.reader.settings.SettingsManager;

public final class UploadService {

    private UploadService() {
    }

    public static void uploadPending(Context context, AppDatabase database, ExecutorService executor, Handler mainHandler) {
        Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            try {
                List<ScanRecordEntity> pending = database.scanRecordDao().getNotUploaded();
                if (pending.isEmpty()) {
                    mainHandler.post(() -> Toast.makeText(appContext, R.string.toast_no_history_upload, Toast.LENGTH_SHORT).show());
                    return;
                }

                JSONArray payload = new JSONArray();
                List<Long> ids = new ArrayList<>();
                for (ScanRecordEntity record : pending) {
                    JSONObject item = new JSONObject();
                    item.put("deviceName", record.deviceName);
                    item.put("sourceType", record.sourceType);
                    item.put("rawData", record.rawData);
                    item.put("cardBrand", record.cardBrand);
                    item.put("timestamp", record.timestamp);
                    payload.put(item);
                    ids.add(record.id);
                }

                URL endpoint = new URL(SettingsManager.getServerUrl(appContext));
                HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = connection.getResponseCode();
                connection.disconnect();

                if (code >= 200 && code < 300) {
                    database.scanRecordDao().markUploaded(ids);
                    mainHandler.post(() -> Toast.makeText(appContext,
                            appContext.getString(R.string.toast_upload_success, ids.size()),
                            Toast.LENGTH_SHORT).show());
                } else {
                    mainHandler.post(() -> Toast.makeText(appContext,
                            appContext.getString(R.string.toast_upload_failed_code, code),
                            Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(appContext,
                        appContext.getString(R.string.toast_upload_failed, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        });
    }
}
