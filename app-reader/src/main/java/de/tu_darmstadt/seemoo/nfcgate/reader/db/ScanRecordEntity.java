package de.tu_darmstadt.seemoo.nfcgate.reader.db;

import androidx.annotation.NonNull;
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

    @NonNull
    public String deviceName;

    @NonNull
    public String sourceType;

    @NonNull
    public String rawData;

    @NonNull
    public String cardBrand;

    public long timestamp;
    public boolean uploaded;

    public static ScanRecordEntity fromRecord(ScanRecord record) {
        ScanRecordEntity entity = new ScanRecordEntity();
        entity.id = record.getId();
        entity.deviceName = record.getDeviceName();
        entity.sourceType = record.getSourceType();
        entity.rawData = record.getRawData();
        entity.cardBrand = record.getCardBrand().name();
        entity.timestamp = record.getTimestamp();
        entity.uploaded = record.isUploaded();
        return entity;
    }

    public ScanRecord toRecord() {
        CardBrandDetector.CardBrand brand;
        try {
            brand = CardBrandDetector.CardBrand.valueOf(cardBrand);
        } catch (Exception ignored) {
            brand = CardBrandDetector.CardBrand.UNKNOWN;
        }
        return new ScanRecord(id, deviceName, sourceType, rawData, timestamp, uploaded, extractPan(rawData), brand);
    }

    private static String extractPan(String rawData) {
        if (rawData == null) return null;
        Matcher matcher = Pattern.compile("(?<!\\d)(\\d{13,19})(?!\\d)").matcher(rawData);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
