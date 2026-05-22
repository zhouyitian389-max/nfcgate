package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class EMVData {
    private final String dedicatedFileName;
    private final String applicationLabel;
    private final String cardholderName;
    private final String aid;
    private final String pan;
    private final String applicationInterchangeProfile;
    private final String applicationFileLocator;
    private final Map<String, byte[]> tlvs;

    public EMVData(String dedicatedFileName,
                   String applicationLabel,
                   String cardholderName,
                   String aid,
                   String pan,
                   String applicationInterchangeProfile,
                   String applicationFileLocator,
                   Map<String, byte[]> tlvs) {
        this.dedicatedFileName = dedicatedFileName;
        this.applicationLabel = applicationLabel;
        this.cardholderName = cardholderName;
        this.aid = aid;
        this.pan = pan;
        this.applicationInterchangeProfile = applicationInterchangeProfile;
        this.applicationFileLocator = applicationFileLocator;

        Map<String, byte[]> copy = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : tlvs.entrySet()) {
            copy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        this.tlvs = Collections.unmodifiableMap(copy);
    }

    public String getDedicatedFileName() {
        return dedicatedFileName;
    }

    public String getApplicationLabel() {
        return applicationLabel;
    }

    public String getCardholderName() {
        return cardholderName;
    }

    public String getAid() {
        return aid;
    }

    public String getPan() {
        return pan;
    }

    public String getApplicationInterchangeProfile() {
        return applicationInterchangeProfile;
    }

    public String getApplicationFileLocator() {
        return applicationFileLocator;
    }

    public Map<String, byte[]> getTlvs() {
        Map<String, byte[]> copy = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : tlvs.entrySet()) {
            copy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        return Collections.unmodifiableMap(copy);
    }

    public byte[] getTagValue(String tag) {
        byte[] value = tlvs.get(tag);
        return value == null ? null : Arrays.copyOf(value, value.length);
    }
}
