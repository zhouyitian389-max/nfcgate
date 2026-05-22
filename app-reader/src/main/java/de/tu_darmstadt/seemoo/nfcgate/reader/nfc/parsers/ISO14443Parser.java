package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

public class ISO14443Parser {
    public String detectType(byte[] ats) {
        if (ats == null || ats.length == 0) {
            return "UNKNOWN";
        }
        return "ISO14443A";
    }
}
