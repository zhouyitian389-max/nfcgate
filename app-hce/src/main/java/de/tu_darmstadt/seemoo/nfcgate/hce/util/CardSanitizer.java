package de.tu_darmstadt.seemoo.nfcgate.hce.util;

import java.util.regex.Pattern;

/**
 * Input-sanitization helpers extracted from YitianHttpServer to allow unit testing
 * without an Android runtime.
 */
public final class CardSanitizer {
    private static final Pattern PAN_PATTERN = Pattern.compile("^\\d{8,32}$");

    private CardSanitizer() {}

    /**
     * Validates and returns a trimmed PAN string.
     *
     * @throws IllegalArgumentException if the PAN is null, empty, non-numeric, or not 8–32 digits
     */
    public static String sanitizePan(String pan) {
        String normalized = pan == null ? "" : pan.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.length() > 32 || !PAN_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("invalid pan");
        }
        return normalized;
    }

    /**
     * Returns the value truncated to {@code maxLength} characters (or an empty string if null).
     */
    public static String clamp(String value, int maxLength) {
        String safe = value == null ? "" : value;
        if (safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, maxLength);
    }
}
