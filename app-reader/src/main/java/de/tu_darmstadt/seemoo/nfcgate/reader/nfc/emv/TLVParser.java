package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.emv;

/**
 * Lightweight TLV (Tag-Length-Value) parser for EMV card data.
 * Supports both single-byte and two-byte tags, as well as constructed (nested) tags.
 */
public class TLVParser {
    private static final int MAX_DEPTH = 8;

    private final byte[] data;

    public TLVParser(byte[] data) {
        this.data = data;
    }

    /**
     * Recursively searches the TLV data for the first value with the given tag.
     *
     * @param targetTag tag integer, e.g. {@code 0x5A} for PAN, {@code 0x5F24} for expiry.
     * @return the value bytes, or {@code null} if not found.
     */
    public byte[] find(int targetTag) {
        if (data == null || data.length == 0) return null;
        return findIn(data, 0, data.length, targetTag, 0);
    }

    private static byte[] findIn(byte[] buf, int start, int end, int targetTag, int depth) {
        if (depth > MAX_DEPTH) return null;
        int offset = start;
        while (offset < end) {
            // Skip padding bytes (0x00 and 0xFF)
            int firstByte = buf[offset] & 0xFF;
            if (firstByte == 0x00 || firstByte == 0xFF) {
                offset++;
                continue;
            }

            boolean constructed = (firstByte & 0x20) != 0;
            int tag = firstByte;
            offset++;

            if ((firstByte & 0x1F) == 0x1F) {
                // Multi-byte tag: read continuation bytes
                if (offset >= end) return null;
                int nextByte = buf[offset++] & 0xFF;
                tag = (tag << 8) | nextByte;
                while ((nextByte & 0x80) != 0) {
                    if (offset >= end) return null;
                    nextByte = buf[offset++] & 0xFF;
                    tag = (tag << 8) | nextByte;
                }
            }

            // Read length
            if (offset >= end) return null;
            int lenByte = buf[offset++] & 0xFF;
            int length;
            if ((lenByte & 0x80) == 0) {
                length = lenByte;
            } else {
                int numBytes = lenByte & 0x7F;
                if (numBytes == 0 || numBytes > 3 || offset + numBytes > end) return null;
                length = 0;
                for (int i = 0; i < numBytes; i++) {
                    length = (length << 8) | (buf[offset++] & 0xFF);
                }
            }
            if (length < 0 || offset + length > end) return null;

            if (tag == targetTag) {
                byte[] value = new byte[length];
                System.arraycopy(buf, offset, value, 0, length);
                return value;
            }

            if (constructed) {
                byte[] nested = findIn(buf, offset, offset + length, targetTag, depth + 1);
                if (nested != null) return nested;
            }

            offset += length;
        }
        return null;
    }
}
