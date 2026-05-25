package de.tu_darmstadt.seemoo.nfcgate.hce.util;

import org.junit.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BackupCryptoTest {

    private static final String PASSWORD = "s3cr3tB@ckup!";
    private static final String PLAINTEXT = "{\"cards\":[{\"pan\":\"4111111111111111\",\"brand\":\"VISA\"}]}";

    @Test
    public void encryptProducesYbakMagicHeader() throws Exception {
        byte[] encrypted = BackupCrypto.encryptString(PLAINTEXT, PASSWORD);
        assertNotNull(encrypted);
        assertTrue("encrypted data must start with YBAK magic",
                encrypted[0] == 'Y' && encrypted[1] == 'B'
                && encrypted[2] == 'A' && encrypted[3] == 'K');
    }

    @Test
    public void encryptedDataHasVersionByte() throws Exception {
        byte[] encrypted = BackupCrypto.encryptString(PLAINTEXT, PASSWORD);
        // Byte at index 4 is VERSION
        assertEquals(BackupCrypto.VERSION, encrypted[4]);
    }

    @Test
    public void encryptDecryptRoundTrip() throws Exception {
        byte[] encrypted = BackupCrypto.encryptString(PLAINTEXT, PASSWORD);
        String decrypted = BackupCrypto.decryptString(encrypted, PASSWORD);
        assertEquals(PLAINTEXT, decrypted);
    }

    @Test
    public void encryptDecryptBinaryRoundTrip() throws Exception {
        byte[] original = new byte[]{0x00, 0x01, 0x7F, (byte) 0xFF, 0x42};
        byte[] encrypted = BackupCrypto.encrypt(original, PASSWORD);
        byte[] decrypted = BackupCrypto.decrypt(encrypted, PASSWORD);
        assertArrayEquals(original, decrypted);
    }

    @Test
    public void encryptProducesDifferentCiphertextEachTime() throws Exception {
        byte[] e1 = BackupCrypto.encryptString(PLAINTEXT, PASSWORD);
        byte[] e2 = BackupCrypto.encryptString(PLAINTEXT, PASSWORD);
        // Different IVs/salts mean different ciphertexts
        assertNotNull(e1);
        assertNotNull(e2);
        // They should differ (with overwhelmingly high probability due to random IV)
        boolean same = Arrays.equals(e1, e2);
        assertTrue("two encryptions must produce different ciphertext (random IV)", !same);
    }

    @Test
    public void wrongPasswordThrowsException() throws Exception {
        byte[] encrypted = BackupCrypto.encryptString(PLAINTEXT, PASSWORD);
        try {
            BackupCrypto.decryptString(encrypted, "wrongPassword");
            fail("Expected decryption with wrong password to throw");
        } catch (GeneralSecurityException | IOException e) {
            // expected: GCM authentication tag mismatch
        }
    }

    @Test
    public void emptyPasswordThrowsIllegalArgument() {
        try {
            BackupCrypto.encryptString(PLAINTEXT, "");
            fail("Expected empty password to throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (Exception e) {
            fail("Expected IllegalArgumentException, got: " + e.getClass().getName());
        }
    }

    @Test
    public void nullPasswordThrowsIllegalArgument() {
        try {
            BackupCrypto.encryptString(PLAINTEXT, null);
            fail("Expected null password to throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (Exception e) {
            fail("Expected IllegalArgumentException, got: " + e.getClass().getName());
        }
    }

    @Test
    public void badMagicThrowsIOException() throws Exception {
        byte[] encrypted = BackupCrypto.encryptString(PLAINTEXT, PASSWORD);
        // Corrupt the magic bytes
        encrypted[0] = 'X';
        try {
            BackupCrypto.decryptString(encrypted, PASSWORD);
            fail("Expected IOException for bad magic");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("magic"));
        }
    }

    @Test
    public void tooShortDataThrowsIOException() {
        try {
            BackupCrypto.decrypt(new byte[]{1, 2, 3}, PASSWORD);
            fail("Expected IOException for too-short data");
        } catch (IOException e) {
            // expected
        } catch (Exception e) {
            fail("Expected IOException, got: " + e.getClass().getName());
        }
    }
}
