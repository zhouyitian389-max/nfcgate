package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

final class ValidationUtils {
    private static final int MAX_APDU_LENGTH = 65536;
    private static final int MAX_BLOCK_DATA_LENGTH = 1024;

    private ValidationUtils() {
    }

    static void validateApdu(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("APDU cannot be null or empty");
        }
        if (data.length > MAX_APDU_LENGTH) {
            throw new IllegalArgumentException("APDU exceeds max length: " + data.length);
        }
    }

    static void validateBlockData(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Block data cannot be null");
        }
        if (data.length > MAX_BLOCK_DATA_LENGTH) {
            throw new IllegalArgumentException("Block data exceeds max length: " + data.length);
        }
    }
}
