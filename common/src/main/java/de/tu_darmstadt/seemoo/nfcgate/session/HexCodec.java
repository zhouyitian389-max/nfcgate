package de.tu_darmstadt.seemoo.nfcgate.session;

public final class HexCodec {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private HexCodec() {
    }

    public static String toHex(byte[] data) {
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int value = data[i] & 0xFF;
            out[i * 2] = HEX[value >>> 4];
            out[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(out);
    }

    public static byte[] fromHex(String hex) {
        String normalized = hex == null ? "" : hex.replaceAll("[^0-9A-Fa-f]", "");
        if ((normalized.length() & 1) == 1) {
            normalized = "0" + normalized;
        }
        byte[] out = new byte[normalized.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(normalized.charAt(i * 2), 16);
            int lo = Character.digit(normalized.charAt(i * 2 + 1), 16);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
