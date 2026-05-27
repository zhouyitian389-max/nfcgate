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
    private String expiry;
    private String track2;
    private String aid;
    private String cardholderName;
    private boolean uploaded;

    public ScanRecord(String deviceName, String sourceType, String rawData,
                      String pan, CardBrandDetector.CardBrand cardBrand) {
        this(0L, deviceName, sourceType, rawData, System.currentTimeMillis(), false, pan, cardBrand,
                null, null, null, null);
    }

    public ScanRecord(long id, String deviceName, String sourceType, String rawData,
                      long timestamp, boolean uploaded, String pan, CardBrandDetector.CardBrand cardBrand) {
        this(id, deviceName, sourceType, rawData, timestamp, uploaded, pan, cardBrand,
                null, null, null, null);
    }

    public ScanRecord(long id, String deviceName, String sourceType, String rawData,
                      long timestamp, boolean uploaded, String pan, CardBrandDetector.CardBrand cardBrand,
                      String expiry, String track2, String aid, String cardholderName) {
        this.id = id;
        this.deviceName = deviceName;
        this.sourceType = sourceType;
        this.timestamp = timestamp;
        this.rawData = rawData;
        this.pan = pan;
        this.cardBrand = cardBrand;
        this.expiry = expiry;
        this.track2 = track2;
        this.aid = aid;
        this.cardholderName = cardholderName;
        this.uploaded = uploaded;
    }

    public long getId() { return id; }
    public String getDeviceName() { return deviceName; }
    public String getSourceType() { return sourceType; }
    public String getRawData() { return rawData; }
    public long getTimestamp() { return timestamp; }
    public String getPan() { return pan; }
    public CardBrandDetector.CardBrand getCardBrand() { return cardBrand; }
    public String getExpiry() { return expiry; }
    public String getTrack2() { return track2; }
    public String getAid() { return aid; }
    public String getCardholderName() { return cardholderName; }
    public boolean isUploaded() { return uploaded; }

    public void setId(long id) { this.id = id; }
    public void setUploaded(boolean uploaded) { this.uploaded = uploaded; }
    public void setExpiry(String expiry) { this.expiry = expiry; }
    public void setTrack2(String track2) { this.track2 = track2; }
    public void setAid(String aid) { this.aid = aid; }
    public void setCardholderName(String cardholderName) { this.cardholderName = cardholderName; }

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
