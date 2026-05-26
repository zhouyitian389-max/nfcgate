package de.tu_darmstadt.seemoo.nfcgate.hce.cloud;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class E2EEncryption {
    private static final int ITERATIONS = 100_000;
    private static final int KEY_BITS = 256;

    private E2EEncryption() {}

    public static String encrypt(String plaintext, String password, String accountSalt) throws Exception {
        validateSalt(accountSalt, "encryption");
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        SecretKey key = deriveKey(password, accountSalt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        String ivPart = Base64.encodeToString(iv, Base64.NO_WRAP);
        String dataPart = Base64.encodeToString(encrypted, Base64.NO_WRAP);
        return ivPart + "." + dataPart;
    }

    public static String decrypt(String ciphertext, String password, String accountSalt) throws Exception {
        validateSalt(accountSalt, "decryption");
        String[] parts = ciphertext.split("\\.", 2);
        if (parts.length != 2) throw new IllegalArgumentException("Invalid ciphertext");
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] data = Base64.decode(parts[1], Base64.NO_WRAP);
        SecretKey key = deriveKey(password, accountSalt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
    }

    private static SecretKey deriveKey(String password, String accountSalt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), accountSalt.getBytes(StandardCharsets.UTF_8), ITERATIONS, KEY_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] bytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(bytes, "AES");
    }

    private static void validateSalt(String salt, String operation) {
        if (salt == null || salt.trim().isEmpty() || "default".equals(salt.trim())) {
            throw new IllegalArgumentException(
                    "Salt is required for " + operation + ". Please re-login to fetch salt from server.");
        }
    }
}
