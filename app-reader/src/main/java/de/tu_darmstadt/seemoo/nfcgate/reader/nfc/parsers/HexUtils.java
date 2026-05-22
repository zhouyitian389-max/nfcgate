package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import java.util.Locale;

final class HexUtils {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private HexUtils() {
    }

    static String toHex(byte[] data) {
        if (data == null) {
            return null;
        }
        return toHex(data, 0, data.length);
    }

    static String toHex(byte[] data, int offset, int length) {
        if (data == null) {
            return null;
        }
        char[] chars = new char[length * 2];
        for (int i = 0; i < length; i++) {
            int value = data[offset + i] & 0xFF;
            chars[i * 2] = HEX[value >>> 4];
            chars[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(chars);
    }

    static String toHex(int value) {
        return Integer.toHexString(value).toUpperCase(Locale.ROOT);
    }

    static byte[] hexToBytes(String hex) {
        if (hex == null) {
            return new byte[0];
        }
        String normalized = hex.replaceAll("\\s+", "");
        if ((normalized.length() & 1) == 1) {
            throw new IllegalArgumentException("Hex string must have an even number of characters");
        }
        byte[] result = new byte[normalized.length() / 2];
        for (int i = 0; i < normalized.length(); i += 2) {
            result[i / 2] = (byte) Integer.parseInt(normalized.substring(i, i + 2), 16);
        }
        return result;
    }
}
