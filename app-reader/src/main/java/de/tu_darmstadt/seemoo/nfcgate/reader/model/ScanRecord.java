package de.tu_darmstadt.seemoo.nfcgate.reader.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScanRecord {
    private final String deviceName;
    private final String sourceType;
    private final long timestamp;
    private final String rawData;

    public ScanRecord(String deviceName, String sourceType, String rawData) {
        this.deviceName = deviceName;
        this.sourceType = sourceType;
        this.timestamp = System.currentTimeMillis();
        this.rawData = rawData;
    }

    public String getDeviceName() { return deviceName; }
    public String getSourceType() { return sourceType; }
    public String getRawData()    { return rawData; }
    public long getTimestamp()    { return timestamp; }

    public String getFormattedTime() {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
    }

    public String getFormattedDate() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
    }
}
