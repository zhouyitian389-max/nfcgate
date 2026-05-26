package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.emv;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Performs a real EMV contactless payment card read using the ISO-DEP (ISO 7816) protocol.
 *
 * <p>Flow:
 * <ol>
 *   <li>SELECT PPSE (2PAY.SYS.DDF01)</li>
 *   <li>Parse AID list from PPSE FCI</li>
 *   <li>SELECT the first candidate AID</li>
 *   <li>GET PROCESSING OPTIONS</li>
 *   <li>Parse AFL and issue READ RECORD for each entry</li>
 *   <li>Extract PAN, Track 2, expiry, cardholder name from records</li>
 * </ol>
 *
 * <p>Cards that do not support ISO-DEP, or fail at any APDU step, cause an exception to be thrown
 * so callers can fall back gracefully to UID-only mode.
 */
public class EMVReader {
    private static final String TAG = "EMVReader";

    // SELECT PPSE: 2PAY.SYS.DDF01
    private static final byte[] SELECT_PPSE = {
        0x00, (byte) 0xA4, 0x04, 0x00, 0x0E,
        '2', 'P', 'A', 'Y', '.', 'S', 'Y', 'S', '.', 'D', 'D', 'F', '0', '1',
        0x00
    };

    /** Parsed data returned from a successful EMV card read. */
    public static class EMVCard {
        public String pan;
        public String track2;
        public String expiry;
        public String cardholderName;
        public String aid;
        public String brand;
    }

    /**
     * Reads EMV payment data from the given Android NFC tag.
     *
     * @param tag the tag obtained from {@code NfcAdapter.ReaderMode}
     * @return populated {@link EMVCard}, never null
     * @throws Exception if the card does not support ISO-DEP, PPSE selection fails,
     *                   no AID can be found, or any mandatory APDU step fails
     */
    public static EMVCard readCard(Tag tag) throws Exception {
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            throw new Exception("Card does not support ISO-DEP");
        }

        try {
            isoDep.connect();
            isoDep.setTimeout(3000);

            // Step 1: SELECT PPSE
            byte[] ppseResponse = isoDep.transceive(SELECT_PPSE);
            if (!isSuccess(ppseResponse)) {
                throw new Exception("PPSE selection failed: " + statusWord(ppseResponse));
            }

            // Step 2: Parse candidate AIDs from PPSE FCI and iterate until one succeeds
            List<String> candidateAids = parseAIDsFromPPSE(ppseResponse);
            if (candidateAids.isEmpty()) {
                throw new Exception("No AID found in PPSE response");
            }

            Exception lastError = null;
            for (String aid : candidateAids) {
                try {
                    // Step 3: SELECT AID
                    byte[] aidResponse = isoDep.transceive(buildSelectAID(aid));
                    if (!isSuccess(aidResponse)) {
                        lastError = new Exception("AID selection failed: " + statusWord(aidResponse));
                        continue;
                    }

                    // Step 4: GET PROCESSING OPTIONS (PDOL-aware)
                    byte[] pdol = parsePDOL(aidResponse);
                    byte[] gpoResponse = isoDep.transceive(buildGPO(pdol));
                    if (!isSuccess(gpoResponse)) {
                        lastError = new Exception("GET PROCESSING OPTIONS failed: " + statusWord(gpoResponse));
                        continue;
                    }

                    EMVCard card = new EMVCard();
                    card.aid = aid;
                    card.brand = getBrandFromAID(aid);

                    // Step 5: Parse AFL and READ RECORDs
                    byte[] afl = parseAFL(gpoResponse);
                    if (afl != null && afl.length % 4 == 0) {
                        readRecordsFromAFL(isoDep, afl, card);
                    }

                    if (card.pan != null) {
                        return card;
                    }
                    lastError = new Exception("No PAN found after reading records for AID " + aid);
                } catch (Exception e) {
                    lastError = e;
                    Log.d(TAG, "AID candidate failed: " + aid + " - " + e.getMessage());
                }
            }

            if (lastError != null) {
                throw lastError;
            }
            throw new Exception("No readable EMV application found");

        } finally {
            try {
                isoDep.close();
            } catch (Exception ignored) {
                // ignored
            }
        }
    }

    // -------------------------------------------------------------------------
    // APDU helpers
    // -------------------------------------------------------------------------

    private static boolean isSuccess(byte[] response) {
        if (response == null || response.length < 2) return false;
        return response[response.length - 2] == (byte) 0x90
                && response[response.length - 1] == (byte) 0x00;
    }

    private static String statusWord(byte[] response) {
        if (response == null || response.length < 2) return "NO_RESPONSE";
        return String.format("%02X%02X",
                response[response.length - 2] & 0xFF,
                response[response.length - 1] & 0xFF);
    }

    private static byte[] buildSelectAID(String aidHex) {
        byte[] aidBytes = hexToBytes(aidHex);
        byte[] cmd = new byte[5 + aidBytes.length + 1];
        cmd[0] = 0x00;               // CLA
        cmd[1] = (byte) 0xA4;        // INS SELECT
        cmd[2] = 0x04;               // P1
        cmd[3] = 0x00;               // P2
        cmd[4] = (byte) aidBytes.length; // Lc
        System.arraycopy(aidBytes, 0, cmd, 5, aidBytes.length);
        cmd[cmd.length - 1] = 0x00;  // Le
        return cmd;
    }

    /** GET PROCESSING OPTIONS with PDOL-aware data object list (tag 0x83). */
    private static byte[] buildGPO(byte[] pdol) {
        byte[] pdolData = buildPdolData(pdol);
        int commandDataLength = 2 + pdolData.length; // 83 + len + data
        byte[] cmd = new byte[5 + commandDataLength + 1];
        cmd[0] = (byte) 0x80;
        cmd[1] = (byte) 0xA8;
        cmd[2] = 0x00;
        cmd[3] = 0x00;
        cmd[4] = (byte) commandDataLength;
        cmd[5] = (byte) 0x83;
        cmd[6] = (byte) pdolData.length;
        if (pdolData.length > 0) {
            System.arraycopy(pdolData, 0, cmd, 7, pdolData.length);
        }
        cmd[cmd.length - 1] = 0x00;
        return cmd;
    }

    // -------------------------------------------------------------------------
    // TLV parsing of APDU responses
    // -------------------------------------------------------------------------

    /** Extracts all ADF Names (tag 0x4F) from the PPSE FCI. */
    private static List<String> parseAIDsFromPPSE(byte[] ppseResponse) {
        // Strip 90 00 status bytes before parsing
        byte[] data = stripStatus(ppseResponse);
        TLVParser parser = new TLVParser(data);
        List<byte[]> aidValues = parser.findAll(0x4F);
        java.util.ArrayList<String> out = new java.util.ArrayList<>(aidValues.size());
        for (byte[] aid : aidValues) {
            String hex = bytesToHex(aid);
            if (!hex.isEmpty()) {
                out.add(hex);
            }
        }
        return out;
    }

    /** Extracts the Application File Locator (tag 0x94) from the GPO response. */
    private static byte[] parseAFL(byte[] gpoResponse) {
        byte[] data = stripStatus(gpoResponse);
        TLVParser parser = new TLVParser(data);
        byte[] afl = parser.find(0x94);
        if (afl != null) {
            return afl;
        }
        // Response Message Template Format 1 (tag 0x80): value = AIP(2) || AFL(n)
        if (data.length >= 4 && (data[0] & 0xFF) == 0x80) {
            int len = data[1] & 0xFF;
            if (len >= 2 && data.length >= 2 + len) {
                int aflLen = len - 2;
                if (aflLen > 0) {
                    return Arrays.copyOfRange(data, 4, 4 + aflLen);
                }
            }
        }
        return null;
    }

    /** Extracts PDOL definition (tag 0x9F38) from selected AID FCI. */
    private static byte[] parsePDOL(byte[] aidResponse) {
        byte[] data = stripStatus(aidResponse);
        TLVParser parser = new TLVParser(data);
        return parser.find(0x9F38);
    }

    /** Builds zero-filled PDOL values honoring each DOL entry's requested length. */
    private static byte[] buildPdolData(byte[] pdol) {
        if (pdol == null || pdol.length == 0) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int offset = 0;
        while (offset < pdol.length) {
            int first = pdol[offset++] & 0xFF;
            if ((first & 0x1F) == 0x1F) {
                while (offset < pdol.length) {
                    int next = pdol[offset++] & 0xFF;
                    if ((next & 0x80) == 0) break;
                }
            }
            if (offset >= pdol.length) break;
            int fieldLen = pdol[offset++] & 0xFF;
            for (int i = 0; i < fieldLen; i++) {
                out.write(0x00);
            }
        }
        return out.toByteArray();
    }

    /**
     * Iterates the AFL and issues READ RECORD for every listed record.
     * AFL format: groups of 4 bytes [SFI_byte, first_rec, last_rec, offline_count].
     */
    private static void readRecordsFromAFL(IsoDep isoDep, byte[] afl, EMVCard card)
            throws Exception {
        for (int i = 0; i < afl.length - 3; i += 4) {
            int sfi = (afl[i] >> 3) & 0x1F;
            int firstRecord = afl[i + 1] & 0xFF;
            int lastRecord  = afl[i + 2] & 0xFF;

            for (int rec = firstRecord; rec <= lastRecord; rec++) {
                byte[] readCmd = {
                    0x00, (byte) 0xB2,
                    (byte) rec,
                    (byte) ((sfi << 3) | 0x04),
                    0x00
                };
                byte[] response = isoDep.transceive(readCmd);
                if (isSuccess(response)) {
                    parseEMVRecord(stripStatus(response), card);
                }
            }
        }
    }

    /** Extracts PAN, Track 2, expiry, and cardholder name from a single record. */
    private static void parseEMVRecord(byte[] record, EMVCard card) {
        TLVParser parser = new TLVParser(record);

        // 0x5A – PAN (Primary Account Number)
        if (card.pan == null) {
            byte[] panBytes = parser.find(0x5A);
            if (panBytes != null) {
                card.pan = parsePAN(panBytes);
            }
        }

        // 0x57 – Track 2 Equivalent Data (contains PAN and expiry)
        if (card.track2 == null) {
            byte[] t2Bytes = parser.find(0x57);
            if (t2Bytes != null) {
                String t2Hex = bytesToHex(t2Bytes).replaceAll("F+$", "");
                card.track2 = t2Hex;
                if (card.pan == null) {
                    int sep = t2Hex.indexOf('D');
                    card.pan = sep >= 0 ? t2Hex.substring(0, sep) : t2Hex;
                }
                if (card.expiry == null) {
                    card.expiry = extractExpiryFromTrack2(t2Bytes);
                }
            }
        }

        // 0x5F24 – Application Expiration Date (YYMMDD BCD)
        if (card.expiry == null) {
            byte[] expiryBytes = parser.find(0x5F24);
            if (expiryBytes != null) {
                String hex = bytesToHex(expiryBytes);
                card.expiry = hex.length() >= 4 ? hex.substring(0, 4) : hex; // YYMM
            }
        }

        // 0x5F20 – Cardholder Name
        if (card.cardholderName == null) {
            byte[] nameBytes = parser.find(0x5F20);
            if (nameBytes != null) {
                card.cardholderName = new String(nameBytes).trim();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Data-format helpers
    // -------------------------------------------------------------------------

    /** Removes trailing F-padding and returns the PAN as a digit string. */
    private static String parsePAN(byte[] panBytes) {
        return bytesToHex(panBytes).replaceAll("F+$", "");
    }

    /** Extracts YYMM expiry from the Track 2 field (after the 'D' separator). */
    private static String extractExpiryFromTrack2(byte[] track2Bytes) {
        String hex = bytesToHex(track2Bytes);
        int sep = hex.indexOf('D');
        if (sep >= 0 && sep + 4 < hex.length()) {
            return hex.substring(sep + 1, sep + 5);
        }
        return null;
    }

    /** Returns a human-readable brand name from the AID prefix. */
    private static String getBrandFromAID(String aid) {
        if (aid == null) return "UNKNOWN";
        String upper = aid.toUpperCase();
        if (upper.startsWith("A0000000031010")) return "Visa";
        if (upper.startsWith("A0000000041010")) return "Mastercard";
        if (upper.startsWith("A000000333"))     return "UnionPay";
        if (upper.startsWith("A0000000651010")) return "JCB";
        if (upper.startsWith("A000000152"))     return "Discover";
        return "UNKNOWN";
    }

    /** Strips the trailing 2-byte status word from an APDU response. */
    private static byte[] stripStatus(byte[] response) {
        if (response == null || response.length < 2) return response != null ? response : new byte[0];
        byte[] data = new byte[response.length - 2];
        System.arraycopy(response, 0, data, 0, data.length);
        return data;
    }

    static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len - 1; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
