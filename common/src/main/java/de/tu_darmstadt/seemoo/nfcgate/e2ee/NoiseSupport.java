package de.tu_darmstadt.seemoo.nfcgate.e2ee;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class NoiseSupport {
    private NoiseSupport() {
    }

    static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    static byte[] derivePublicKey(byte[] privateKey) {
        return Arrays.copyOf(sha256(concat("pub".getBytes(StandardCharsets.UTF_8), privateKey)), 32);
    }

    static byte[] pseudoDh(byte[] privateKey, byte[] publicKey) {
        byte[] localPublic = derivePublicKey(privateKey);
        byte[][] ordered = Arrays.compareUnsigned(localPublic, publicKey) <= 0
                ? new byte[][] {localPublic, publicKey}
                : new byte[][] {publicKey, localPublic};
        return sha256(concat(ordered[0], ordered[1]));
    }

    static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] hkdf(byte[] keyMaterial, String info, int size) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            byte[] salt = new byte[32];
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            byte[] prk = mac.doFinal(keyMaterial);
            byte[] out = new byte[size];
            byte[] previous = new byte[0];
            int offset = 0;
            byte counter = 1;
            while (offset < size) {
                mac.init(new SecretKeySpec(prk, "HmacSHA256"));
                mac.update(previous);
                mac.update(info.getBytes(StandardCharsets.UTF_8));
                mac.update(counter);
                previous = mac.doFinal();
                int copy = Math.min(previous.length, size - offset);
                System.arraycopy(previous, 0, out, offset, copy);
                offset += copy;
                counter++;
            }
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] encrypt(byte[] key, long counter, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] nonce = nonce(counter);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Arrays.copyOf(key, 16), "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return concat(ByteBuffer.allocate(8).putLong(counter).array(), ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] decrypt(byte[] key, byte[] payload) {
        if (payload.length < 8) {
            throw new IllegalArgumentException("Ciphertext too short");
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            long counter = buffer.getLong();
            byte[] ciphertext = new byte[payload.length - 8];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Arrays.copyOf(key, 16), "AES"), new GCMParameterSpec(128, nonce(counter)));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ciphertext integrity check failed", e);
        }
    }

    static byte[] hmac(byte[] key, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean constantTimeEquals(byte[] left, byte[] right) {
        if (left.length != right.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < left.length; i++) {
            diff |= left[i] ^ right[i];
        }
        return diff == 0;
    }

    static byte[] concat(byte[]... arrays) {
        int size = 0;
        for (byte[] array : arrays) {
            size += array.length;
        }
        byte[] out = new byte[size];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, out, offset, array.length);
            offset += array.length;
        }
        return out;
    }

    private static byte[] nonce(long counter) {
        byte[] nonce = new byte[12];
        ByteBuffer.wrap(nonce, 4, 8).putLong(counter);
        return nonce;
    }
}
