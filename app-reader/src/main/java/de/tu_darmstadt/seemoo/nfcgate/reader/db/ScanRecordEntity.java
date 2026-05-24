package de.tu_darmstadt.seemoo.nfcgate.reader.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import de.tu_darmstadt.seemoo.nfcgate.reader.model.ScanRecord;
import de.tu_darmstadt.seemoo.nfcgate.reader.util.CardBrandDetector;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Entity(tableName = "scan_records")
public class ScanRecordEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull public String deviceName;
    @NonNull public String sourceType;
    @NonNull public String rawData;
    @NonNull public String cardBrand;

    /** PAN extracted at scan time and stored directly. */
    @Nullable
    @ColumnInfo(name = "pan")
    public String pan;

    public long    timestamp;
    public boolean uploaded;

    public static ScanRecordEntity fromRecord(ScanRecord record) {
        ScanRecordEntity e = new ScanRecordEntity();
        e.id         = record.getId();
        e.deviceName = record.getDeviceName();
        e.sourceType = record.getSourceType();
        e.rawData    = record.getRawData();
        e.cardBrand  = record.getCardBrand().name();
        e.pan        = record.getPan();
        e.timestamp  = record.getTimestamp();
        e.uploaded   = record.isUploaded();
        return e;
    }

    public ScanRecord toRecord() {
        CardBrandDetector.CardBrand brand;
        try { brand = CardBrandDetector.CardBrand.valueOf(cardBrand); }
        catch (Exception ignored) { brand = CardBrandDetector.CardBrand.UNKNOWN; }
        // Prefer stored PAN; fall back to regex extraction for legacy rows.
        String resolvedPan = (pan != null && !pan.isEmpty()) ? pan : extractPan(rawData);
        return new ScanRecord(id, deviceName, sourceType, rawData, timestamp, uploaded, resolvedPan, brand);
    }

    private static String extractPan(String raw) {
        if (raw == null) return null;
        Matcher m = Pattern.compile("(?<!\\d)(\\d{13,19})(?!\\d)").matcher(raw);
        return m.find() ? m.group(1) : null;
    }
}
