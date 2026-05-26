package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.emv;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;

import java.util.Locale;

/**
 * Utility class for reading data from Mifare Classic, Mifare Ultralight, and NDEF tags.
 * Each method connects to the tag, reads data, and closes the connection.
 */
public class MifareReader {

    private MifareReader() {
    }

    /**
     * Reads sector 0 / block 0 from a Mifare Classic tag using the default key.
     *
     * @param tag the NFC tag
     * @return hex representation of block 0 data
     * @throws Exception if the tag is not Mifare Classic or authentication fails
     */
    public static String readMifareClassic(Tag tag) throws Exception {
        MifareClassic mfc = MifareClassic.get(tag);
        if (mfc == null) throw new Exception("Not a Mifare Classic tag");

        mfc.connect();
        try {
            // Try default key A first
            boolean auth = mfc.authenticateSectorWithKeyA(0, MifareClassic.KEY_DEFAULT);
            if (!auth) {
                // Try default key B
                auth = mfc.authenticateSectorWithKeyB(0, MifareClassic.KEY_DEFAULT);
            }
            if (!auth) {
                throw new Exception("Mifare Classic authentication failed");
            }
            int block = mfc.sectorToBlock(0);
            byte[] data = mfc.readBlock(block);
            return bytesToHex(data);
        } finally {
            try { mfc.close(); } catch (Exception ignored) { /* ignored */ }
        }
    }

    /**
     * Reads pages 0-3 from a Mifare Ultralight tag.
     *
     * @param tag the NFC tag
     * @return hex representation of pages 0-3 (16 bytes)
     * @throws Exception if the tag is not Mifare Ultralight or read fails
     */
    public static String readMifareUltralight(Tag tag) throws Exception {
        MifareUltralight mul = MifareUltralight.get(tag);
        if (mul == null) throw new Exception("Not a Mifare Ultralight tag");

        mul.connect();
        try {
            byte[] pages = mul.readPages(0); // reads pages 0-3
            return bytesToHex(pages);
        } finally {
            try { mul.close(); } catch (Exception ignored) { /* ignored */ }
        }
    }

    /**
     * Reads the NDEF message from an NDEF-capable tag.
     *
     * @param tag the NFC tag
     * @return human-readable representation of NDEF records
     * @throws Exception if no NDEF support or read fails
     */
    public static String readNDEF(Tag tag) throws Exception {
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) throw new Exception("Tag does not have NDEF support");

        ndef.connect();
        try {
            NdefMessage msg = ndef.getNdefMessage();
            if (msg == null) return "Empty NDEF tag";
            return parseNdefRecords(msg.getRecords());
        } finally {
            try { ndef.close(); } catch (Exception ignored) { /* ignored */ }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String parseNdefRecords(NdefRecord[] records) {
        if (records == null || records.length == 0) return "No NDEF records";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < records.length; i++) {
            NdefRecord record = records[i];
            if (i > 0) sb.append('\n');
            sb.append("Record[").append(i).append("]: ");
            short tnf = record.getTnf();
            if (tnf == NdefRecord.TNF_WELL_KNOWN) {
                byte[] type = record.getType();
                if (type != null && type.length == 1 && type[0] == 'T') {
                    // Text record
                    sb.append(parseTextRecord(record.getPayload()));
                } else if (type != null && type.length == 1 && type[0] == 'U') {
                    // URI record
                    sb.append(parseUriRecord(record.getPayload()));
                } else {
                    sb.append(bytesToHex(record.getPayload()));
                }
            } else {
                sb.append(bytesToHex(record.getPayload()));
            }
        }
        return sb.toString();
    }

    private static String parseTextRecord(byte[] payload) {
        if (payload == null || payload.length == 0) return "";
        int langLen = payload[0] & 0x3F;
        int textStart = 1 + langLen;
        if (textStart >= payload.length) return "";
        return new String(payload, textStart, payload.length - textStart);
    }

    private static String parseUriRecord(byte[] payload) {
        if (payload == null || payload.length == 0) return "";
        // First byte is URI identifier code
        String prefix = uriPrefix(payload[0] & 0xFF);
        return prefix + new String(payload, 1, payload.length - 1);
    }

    private static String uriPrefix(int code) {
        switch (code) {
            case 0x01: return "http://www.";
            case 0x02: return "https://www.";
            case 0x03: return "http://";
            case 0x04: return "https://";
            case 0x05: return "tel:";
            case 0x06: return "mailto:";
            default:   return "";
        }
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02X", b & 0xFF));
        }
        return sb.toString();
    }
}
