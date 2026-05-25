package de.tu_darmstadt.seemoo.nfcgate.reader.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-GCM encrypted backup file format (.ybak):
 * <pre>
 *   MAGIC(4 bytes, "YBAK") | VERSION(1 byte, 0x01)
 *   | SALT(16 bytes) | IV(12 bytes)
 *   | CIPHERTEXT | GCM_TAG(16 bytes, appended by AES/GCM)
 * </pre>
 * Key derivation: PBKDF2-HMAC-SHA256, 100 000 iterations, 256-bit key.
 */
public final class BackupCrypto {
    static final byte[] MAGIC = {'Y', 'B', 'A', 'K'};
    static final byte VERSION = 0x01;

    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";

    private BackupCrypto() {}

    /**
     * Encrypt {@code plaintext} with the given {@code password}.
     *
     * @return the encrypted .ybak byte array
     * @throws GeneralSecurityException on crypto errors
     * @throws IOException              on byte-assembly errors
     */
    public static byte[] encrypt(byte[] plaintext, String password)
            throws GeneralSecurityException, IOException {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("password must not be empty");
        }
        SecureRandom rng = new SecureRandom();
        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        rng.nextBytes(salt);
        rng.nextBytes(iv);

        SecretKey key = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ciphertextWithTag = cipher.doFinal(plaintext);

        ByteArrayOutputStream out = new ByteArrayOutputStream(
                MAGIC.length + 1 + SALT_BYTES + IV_BYTES + ciphertextWithTag.length);
        out.write(MAGIC);
        out.write(VERSION);
        out.write(salt);
        out.write(iv);
        out.write(ciphertextWithTag);
        return out.toByteArray();
    }

    /**
     * Decrypt a .ybak byte array with the given {@code password}.
     *
     * @return the original plaintext bytes
     * @throws GeneralSecurityException on bad password or corrupted data
     * @throws IOException              on format errors
     */
    public static byte[] decrypt(byte[] data, String password)
            throws GeneralSecurityException, IOException {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("password must not be empty");
        }
        int headerSize = MAGIC.length + 1 + SALT_BYTES + IV_BYTES;
        if (data == null || data.length < headerSize + GCM_TAG_BITS / 8) {
            throw new IOException("backup file too short or null");
        }
        ByteBuffer buf = ByteBuffer.wrap(data);

        // Verify magic
        byte[] magic = new byte[MAGIC.length];
        buf.get(magic);
        for (int i = 0; i < MAGIC.length; i++) {
            if (magic[i] != MAGIC[i]) {
                throw new IOException("invalid backup file: bad magic");
            }
        }
        // Verify version
        byte version = buf.get();
        if (version != VERSION) {
            throw new IOException("unsupported backup version: " + version);
        }

        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        buf.get(salt);
        buf.get(iv);

        byte[] ciphertextWithTag = new byte[buf.remaining()];
        buf.get(ciphertextWithTag);

        SecretKey key = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(ciphertextWithTag);
    }

    /** Encrypt a UTF-8 string and return the .ybak bytes. */
    public static byte[] encryptString(String plaintext, String password)
            throws GeneralSecurityException, IOException {
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8), password);
    }

    /** Decrypt .ybak bytes and return the UTF-8 string. */
    public static String decryptString(byte[] data, String password)
            throws GeneralSecurityException, IOException {
        return new String(decrypt(data, password), StandardCharsets.UTF_8);
    }

    private static SecretKey deriveKey(String password, byte[] salt) throws GeneralSecurityException {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
}
