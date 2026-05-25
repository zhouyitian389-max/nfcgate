package de.tu_darmstadt.seemoo.nfcgate.reader.util;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import de.tu_darmstadt.seemoo.nfcgate.reader.db.AppDatabase;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.ScanRecordEntity;

public final class DatabaseBackupHelper {
    private DatabaseBackupHelper() {
    }

    public static File exportToJson(Context context, AppDatabase database) throws Exception {
        JSONArray array = new JSONArray();
        List<ScanRecordEntity> records = database.scanRecordDao().getAll();
        for (ScanRecordEntity record : records) {
            JSONObject item = new JSONObject();
            item.put("deviceName", record.deviceName);
            item.put("sourceType", record.sourceType);
            item.put("rawData", record.rawData);
            item.put("cardBrand", record.cardBrand);
            item.put("pan", record.pan);
            item.put("timestamp", record.timestamp);
            item.put("uploaded", record.uploaded);
            array.put(item);
        }

        File backupDir = new File(context.getCacheDir(), "backup");
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw new IllegalStateException("Unable to create backup directory");
        }
        File backupFile = new File(backupDir, "yitian_backup.json");
        try (FileOutputStream outputStream = new FileOutputStream(backupFile, false)) {
            outputStream.write(array.toString().getBytes(StandardCharsets.UTF_8));
        }
        return backupFile;
    }

    public static int importFromJson(Context context, AppDatabase database, String json) throws Exception {
        if (json == null || json.trim().isEmpty()) {
            return 0;
        }
        JSONArray array = new JSONArray(json);
        if (array.length() == 0) {
            return 0;
        }

        List<ScanRecordEntity> entities = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            ScanRecordEntity record = new ScanRecordEntity();
            record.deviceName = item.optString("deviceName", "");
            record.sourceType = item.optString("sourceType", "");
            record.rawData = item.optString("rawData", "");
            record.cardBrand = item.optString("cardBrand", "UNKNOWN");
            String pan = item.optString("pan", "");
            record.pan = pan.isEmpty() ? null : pan;
            record.timestamp = item.optLong("timestamp", System.currentTimeMillis());
            record.uploaded = item.optBoolean("uploaded", false);
            entities.add(record);
        }

        database.runInTransaction(() -> database.scanRecordDao().insertAll(entities));
        return entities.size();
    }
}
