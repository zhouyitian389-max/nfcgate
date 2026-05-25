package de.tu_darmstadt.seemoo.nfcgate.reader.util;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
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

    /**
     * Encrypts the database records as AES-GCM .ybak and writes the file to the backup directory.
     *
     * @param context  application context
     * @param database Room database
     * @param password backup password (non-empty)
     * @return the written .ybak {@link File}
     */
    public static File exportToEncryptedBackup(Context context, AppDatabase database,
                                               String password) throws Exception {
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

        byte[] encrypted = BackupCrypto.encryptString(array.toString(), password);

        File backupDir = new File(context.getCacheDir(), "backup");
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw new IllegalStateException("Unable to create backup directory");
        }
        File backupFile = new File(backupDir, "yitian_backup.ybak");
        try (FileOutputStream outputStream = new FileOutputStream(backupFile, false)) {
            outputStream.write(encrypted);
        }
        return backupFile;
    }

    /**
     * Decrypts a .ybak backup file and imports its records into the database.
     *
     * @param database Room database
     * @param ybakFile the encrypted backup file
     * @param password backup password
     * @return number of records imported
     */
    public static int restoreFromBackup(AppDatabase database, File ybakFile,
                                        String password) throws Exception {
        byte[] data = new byte[(int) ybakFile.length()];
        int offset = 0;
        int remaining = data.length;
        try (FileInputStream fis = new FileInputStream(ybakFile)) {
            while (remaining > 0) {
                int read = fis.read(data, offset, remaining);
                if (read < 0) break;
                offset += read;
                remaining -= read;
            }
            if (offset != data.length) {
                throw new IllegalStateException("Incomplete read of backup file");
            }
        }
        String json = BackupCrypto.decryptString(data, password);
        return importFromJson(null, database, json);
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

