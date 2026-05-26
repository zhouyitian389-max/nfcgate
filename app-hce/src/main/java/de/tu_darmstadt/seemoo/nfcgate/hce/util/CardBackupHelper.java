package de.tu_darmstadt.seemoo.nfcgate.hce.util;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardDao;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardDatabase;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardEntity;

/**
 * Encrypted backup/restore helper for {@link CardEntity} records.
 * Uses {@link BackupCrypto} (AES-GCM, PBKDF2) to produce .ybak files.
 */
public final class CardBackupHelper {
    private CardBackupHelper() {}

    /**
     * Exports all cards from the database as an AES-GCM encrypted .ybak file.
     *
     * @param context  application context
     * @param database the card Room database
     * @param password non-empty backup password
     * @return the written .ybak {@link File}
     */
    public static File exportToEncryptedBackup(Context context, CardDatabase database,
                                               String password) throws Exception {
        File tempFile = null;
        try {
            CardDao dao = database.cardDao();
            List<CardEntity> cards = dao.getAll();

            JSONArray array = new JSONArray();
            for (CardEntity card : cards) {
                JSONObject item = new JSONObject();
                item.put("pan", card.pan);
                item.put("brand", card.brand);
                item.put("holder", card.holder);
                item.put("expiry", card.expiry);
                item.put("track2", card.track2);
                item.put("receivedAt", card.receivedAt);
                item.put("isSelected", card.isSelected);
                array.put(item);
            }

            tempFile = File.createTempFile("backup_", ".json", context.getCacheDir());
            tempFile.deleteOnExit();
            try (FileOutputStream tempOut = new FileOutputStream(tempFile, false)) {
                tempOut.write(array.toString().getBytes(StandardCharsets.UTF_8));
            }

            byte[] plaintext = new byte[(int) tempFile.length()];
            int offset = 0;
            int remaining = plaintext.length;
            try (FileInputStream fis = new FileInputStream(tempFile)) {
                while (remaining > 0) {
                    int read = fis.read(plaintext, offset, remaining);
                    if (read < 0) break;
                    offset += read;
                    remaining -= read;
                }
                if (offset != plaintext.length) {
                    throw new IllegalStateException("Incomplete read of temp backup data");
                }
            }

            byte[] encrypted = BackupCrypto.encrypt(plaintext, password);

            File backupDir = new File(context.getCacheDir(), "backup");
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                throw new IllegalStateException("Unable to create backup directory");
            }
            File backupFile = new File(backupDir, "yitian_nfc_backup.ybak");
            try (FileOutputStream fos = new FileOutputStream(backupFile, false)) {
                fos.write(encrypted);
            }
            return backupFile;
        } finally {
            if (tempFile != null && tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }
    }

    /**
     * Decrypts a .ybak backup file and inserts its records into the database.
     *
     * @param database the card Room database
     * @param ybakFile the encrypted backup file
     * @param password backup password
     * @return number of card records restored
     */
    public static int restoreFromBackup(CardDatabase database, File ybakFile,
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
        JSONArray array = new JSONArray(json);
        if (array.length() == 0) {
            return 0;
        }

        List<CardEntity> entities = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            CardEntity card = new CardEntity();
            card.pan = item.optString("pan", null);
            card.brand = item.optString("brand", "UNKNOWN");
            card.holder = item.optString("holder", "");
            card.expiry = item.optString("expiry", "");
            card.track2 = item.optString("track2", "");
            card.receivedAt = item.optLong("receivedAt", System.currentTimeMillis());
            card.isSelected = item.optBoolean("isSelected", false);
            entities.add(card);
        }

        database.runInTransaction(() -> {
            CardDao dao = database.cardDao();
            dao.insertAll(entities);
            dao.ensureSingleSelection();
        });
        return entities.size();
    }
}
