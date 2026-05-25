package de.tu_darmstadt.seemoo.nfcgate.hce.security;

import android.util.Base64;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hashes and verifies PINs using PBKDF2-SHA256.
 * Storage format: {@code "saltBase64:hashBase64"}
 */
public final class PinHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 100_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;

    private PinHasher() {}

    /**
     * Hash a plaintext PIN. Returns {@code "saltBase64:hashBase64"}.
     */
    public static String hash(String pin) {
        if (pin == null) {
            return null;
        }
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] derived = pbkdf2(pin.toCharArray(), salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP)
                + ":"
                + Base64.encodeToString(derived, Base64.NO_WRAP);
    }

    /**
     * Verify a plaintext PIN against a stored {@code "saltBase64:hashBase64"} value.
     * Returns {@code false} for any null input, malformed stored value, or wrong PIN.
     */
    public static boolean verify(String pin, String stored) {
        if (pin == null || stored == null || !stored.contains(":")) return false;
        String[] parts = stored.split(":", 2);
        if (parts.length != 2) return false;
        try {
            byte[] salt = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] expected = Base64.decode(parts[1], Base64.NO_WRAP);
            byte[] actual = pbkdf2(pin.toCharArray(), salt);
            return constantTimeEquals(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] result = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return result;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("PBKDF2 unavailable", e);
        }
    }

    /** Constant-time byte array comparison to prevent timing attacks. */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) return false;
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= (a[i] ^ b[i]);
        }
        return diff == 0;
    }
}
