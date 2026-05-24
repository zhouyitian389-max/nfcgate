package de.tu_darmstadt.seemoo.nfcgate.reader.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.tu_darmstadt.seemoo.nfcgate.reader.util.CardBrandDetector;

public class ScanRecord {
    private long id;
    private final String deviceName;
    private final String sourceType;
    private final long timestamp;
    private final String rawData;
    private final String pan;
    private final CardBrandDetector.CardBrand cardBrand;
    private boolean uploaded;

    public ScanRecord(String deviceName, String sourceType, String rawData,
                      String pan, CardBrandDetector.CardBrand cardBrand) {
        this(0L, deviceName, sourceType, rawData, System.currentTimeMillis(), false, pan, cardBrand);
    }

    public ScanRecord(long id, String deviceName, String sourceType, String rawData,
                      long timestamp, boolean uploaded, String pan, CardBrandDetector.CardBrand cardBrand) {
        this.id = id;
        this.deviceName = deviceName;
        this.sourceType = sourceType;
        this.timestamp = timestamp;
        this.rawData = rawData;
        this.pan = pan;
        this.cardBrand = cardBrand;
        this.uploaded = uploaded;
    }

    public long getId() { return id; }
    public String getDeviceName() { return deviceName; }
    public String getSourceType() { return sourceType; }
    public String getRawData() { return rawData; }
    public long getTimestamp() { return timestamp; }
    public String getPan() { return pan; }
    public CardBrandDetector.CardBrand getCardBrand() { return cardBrand; }
    public boolean isUploaded() { return uploaded; }

    public void setId(long id) { this.id = id; }
    public void setUploaded(boolean uploaded) { this.uploaded = uploaded; }

    public String getFormattedTime() {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
    }

    public String getFormattedDate() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
    }

    public String getMaskedPan() {
        if (pan == null || pan.length() < 4) {
            return "**** **** **** 0000";
        }
        String last4 = pan.substring(pan.length() - 4);
        return "**** **** **** " + last4;
    }
}
