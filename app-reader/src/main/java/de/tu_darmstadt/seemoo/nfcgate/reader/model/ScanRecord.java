package de.tu_darmstadt.seemoo.nfcgate.reader.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.tu_darmstadt.seemoo.nfcgate.reader.util.CardBrandDetector;

public class ScanRecord {
    private final String deviceName;
    private final String sourceType;
    private final long timestamp;
    private final String rawData;
    private final String pan;
    private final CardBrandDetector.CardBrand cardBrand;

    public ScanRecord(String deviceName, String sourceType, String rawData,
                      String pan, CardBrandDetector.CardBrand cardBrand) {
        this.deviceName = deviceName;
        this.sourceType = sourceType;
        this.timestamp = System.currentTimeMillis();
        this.rawData = rawData;
        this.pan = pan;
        this.cardBrand = cardBrand;
    }

    public String getDeviceName() { return deviceName; }
    public String getSourceType() { return sourceType; }
    public String getRawData()    { return rawData; }
    public long getTimestamp()    { return timestamp; }
    public String getPan() { return pan; }
    public CardBrandDetector.CardBrand getCardBrand() { return cardBrand; }

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
