package android.util;

/**
 * Stub of {@code android.util.Base64} for use in JVM unit tests.
 * Delegates to {@link java.util.Base64} (Java 8+).
 */
public class Base64 {
    /** Flag: no padding, no line breaks (matches android.util.Base64.NO_WRAP). */
    public static final int NO_WRAP = 2;
    /** Flag: default (matches android.util.Base64.DEFAULT). */
    public static final int DEFAULT = 0;

    public static String encodeToString(byte[] input, int flags) {
        // NO_WRAP → standard base64 without line-breaks, without padding
        return java.util.Base64.getEncoder().withoutPadding().encodeToString(input);
    }

    public static byte[] encode(byte[] input, int flags) {
        return java.util.Base64.getEncoder().withoutPadding().encode(input);
    }

    public static byte[] decode(String str, int flags) {
        if (str == null) return new byte[0];
        // Re-pad to a multiple of 4 so standard decoder can handle it
        String padded = str.trim();
        int missing = (4 - padded.length() % 4) % 4;
        for (int i = 0; i < missing; i++) {
            padded += '=';
        }
        return java.util.Base64.getDecoder().decode(padded);
    }
}
