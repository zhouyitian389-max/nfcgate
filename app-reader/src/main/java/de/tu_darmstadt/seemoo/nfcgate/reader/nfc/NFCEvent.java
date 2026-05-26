package de.tu_darmstadt.seemoo.nfcgate.reader.nfc;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.emv.EMVReader;

public abstract class NFCEvent {
    private final NFCSource source;

    protected NFCEvent(NFCSource source) {
        this.source = source;
    }

    public NFCSource getSource() {
        return source;
    }

    private static String hex(byte[] bytes) {
        if (bytes == null) return "<null>";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02X", b));
        return sb.toString();
    }

    public static class CardDetected extends NFCEvent {
        private final String type;
        private final byte[] uid;
        private final long timestamp;
        @Nullable
        private final EMVReader.EMVCard emvCard;

        public CardDetected(String type, byte[] uid, NFCSource source, long timestamp) {
            this(type, uid, source, timestamp, null);
        }

        public CardDetected(String type, byte[] uid, NFCSource source, long timestamp,
                @Nullable EMVReader.EMVCard emvCard) {
            super(source);
            this.type = type;
            this.uid = uid;
            this.timestamp = timestamp;
            this.emvCard = emvCard;
        }

        public String getType()      { return type; }
        public byte[] getUid()       { return uid; }
        public long getTimestamp()   { return timestamp; }
        @Nullable
        public EMVReader.EMVCard getEmvCard() { return emvCard; }

        @NonNull
        @Override
        public String toString() {
            if (emvCard != null && emvCard.pan != null) {
                return "Card[" + (emvCard.brand != null ? emvCard.brand : type) + "]"
                        + " PAN=" + emvCard.pan
                        + (emvCard.expiry != null ? " EXP=" + emvCard.expiry : "")
                        + (emvCard.cardholderName != null ? " NAME=" + emvCard.cardholderName : "")
                        + " UID=" + hex(uid);
            }
            return "Card[" + type + "] UID=" + hex(uid);
        }
    }

    public static class APDUResponse extends NFCEvent {
        private final byte[] apdu;
        private final byte[] response;

        public APDUResponse(byte[] apdu, byte[] response, NFCSource source) {
            super(source);
            this.apdu = apdu;
            this.response = response;
        }

        public byte[] getApdu()     { return apdu; }
        public byte[] getResponse() { return response; }

        @NonNull
        @Override
        public String toString() {
            return "APDU=" + hex(apdu) + " -> " + hex(response);
        }
    }

    public static class MIFAREBlock extends NFCEvent {
        private final int blockNum;
        private final byte[] data;

        public MIFAREBlock(int blockNum, byte[] data, NFCSource source) {
            super(source);
            this.blockNum = blockNum;
            this.data = data;
        }

        public int getBlockNum() { return blockNum; }
        public byte[] getData()  { return data; }

        @NonNull
        @Override
        public String toString() {
            return "MIFARE blk#" + blockNum + "=" + hex(data);
        }
    }

    public static class Error extends NFCEvent {
        private final String message;

        public Error(String message, NFCSource source) {
            super(source);
            this.message = message;
        }

        public String getMessage() { return message; }

        @NonNull
        @Override
        public String toString() {
            return "Error: " + message;
        }
    }
}
