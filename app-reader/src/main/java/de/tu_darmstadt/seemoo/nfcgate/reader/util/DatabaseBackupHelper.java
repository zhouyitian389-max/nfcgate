package de.tu_darmstadt.seemoo.nfcgate.reader.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import de.tu_darmstadt.seemoo.nfcgate.reader.db.AppDatabase;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.ScanRecordEntity;

public final class DatabaseBackupHelper {
    private DatabaseBackupHelper() {
    }

    public static File exportToJson(AppDatabase database, File directory) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create export directory");
        }
        JSONArray records = new JSONArray();
        for (ScanRecordEntity entity : database.scanRecordDao().getAll()) {
            JSONObject item = new JSONObject();
            item.put("id", entity.id);
            item.put("deviceName", entity.deviceName);
            item.put("sourceType", entity.sourceType);
            item.put("rawData", entity.rawData);
            item.put("cardBrand", entity.cardBrand);
            item.put("pan", entity.pan);
            item.put("timestamp", entity.timestamp);
            item.put("uploaded", entity.uploaded);
            records.put(item);
        }
        JSONObject backup = new JSONObject();
        backup.put("version", 1);
        backup.put("exportedAt", System.currentTimeMillis());
        backup.put("records", records);

        File backupFile = new File(directory, "yitian_wallet_backup.json");
        try (FileOutputStream outputStream = new FileOutputStream(backupFile)) {
            outputStream.write(backup.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return backupFile;
    }

    public static int restoreFromJson(AppDatabase database, File backupFile) throws Exception {
        String raw;
        try (FileInputStream inputStream = new FileInputStream(backupFile);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            raw = outputStream.toString(StandardCharsets.UTF_8.name());
        }
        JSONObject backup = new JSONObject(raw);
        JSONArray records = backup.optJSONArray("records");
        if (records == null) {
            return 0;
        }
        List<ScanRecordEntity> restored = new ArrayList<>(records.length());
        for (int i = 0; i < records.length(); i++) {
            JSONObject item = records.getJSONObject(i);
            ScanRecordEntity entity = new ScanRecordEntity();
            entity.id = item.optLong("id", 0L);
            entity.deviceName = item.optString("deviceName", "");
            entity.sourceType = item.optString("sourceType", "");
            entity.rawData = item.optString("rawData", "");
            entity.cardBrand = item.optString("cardBrand", "UNKNOWN");
            entity.pan = item.isNull("pan") ? null : item.optString("pan", null);
            entity.timestamp = item.optLong("timestamp", System.currentTimeMillis());
            entity.uploaded = item.optBoolean("uploaded", false);
            restored.add(entity);
        }
        database.runInTransaction(() -> {
            database.scanRecordDao().deleteAll();
            database.scanRecordDao().insertAll(restored);
        });
        return restored.size();
    }
}
