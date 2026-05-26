package de.tu_darmstadt.seemoo.nfcgate.reader.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import de.tu_darmstadt.seemoo.nfcgate.reader.model.ScanRecord;
import de.tu_darmstadt.seemoo.nfcgate.reader.util.CardBrandDetector;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Entity(
        tableName = "scan_records",
        indices = {
                @Index(value = {"timestamp"}, name = "idx_timestamp"),
                @Index(value = {"pan"}, name = "idx_pan")
        }
)
public class ScanRecordEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "deviceName")
    @NonNull public String deviceName;
    @ColumnInfo(name = "sourceType")
    @NonNull public String sourceType;
    @ColumnInfo(name = "rawData")
    @NonNull public String rawData;
    @ColumnInfo(name = "cardBrand")
    @NonNull public String cardBrand;

    /** PAN extracted at scan time and stored directly. */
    @Nullable
    @ColumnInfo(name = "pan")
    public String pan;

    @ColumnInfo(name = "timestamp")
    public long    timestamp;
    @ColumnInfo(name = "uploaded")
    public boolean uploaded;
    @ColumnInfo(name = "synced")
    public boolean synced;
    @Nullable
    @ColumnInfo(name = "note")
    public String note;
    @Nullable
    @ColumnInfo(name = "server_card_id")
    public String serverCardId;

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
        e.synced     = false;
        e.note       = null;
        e.serverCardId = null;
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
