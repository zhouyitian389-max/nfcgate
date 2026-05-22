package de.tu_darmstadt.seemoo.nfcgate.reader.nfc;

public abstract class NFCEvent {
    private final NFCSource source;

    protected NFCEvent(NFCSource source) {
        this.source = source;
    }

    public NFCSource getSource() {
        return source;
    }

    public static class CardDetected extends NFCEvent {
        private final String type;
        private final byte[] uid;
        private final long timestamp;

        public CardDetected(String type, byte[] uid, NFCSource source, long timestamp) {
            super(source);
            this.type = type;
            this.uid = uid;
            this.timestamp = timestamp;
        }

        public String getType() {
            return type;
        }

        public byte[] getUid() {
            return uid;
        }

        public long getTimestamp() {
            return timestamp;
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

        public byte[] getApdu() {
            return apdu;
        }

        public byte[] getResponse() {
            return response;
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

        public int getBlockNum() {
            return blockNum;
        }

        public byte[] getData() {
            return data;
        }
    }

    public static class Error extends NFCEvent {
        private final String message;

        public Error(String message, NFCSource source) {
            super(source);
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
