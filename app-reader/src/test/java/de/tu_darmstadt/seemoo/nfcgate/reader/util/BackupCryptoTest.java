package de.tu_darmstadt.seemoo.nfcgate.reader.util;

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

    private static final String PASSWORD = "MyStr0ngP@ssword";
    private static final String PLAINTEXT = "[{\"pan\":\"4111111111111111\",\"brand\":\"VISA\",\"timestamp\":1234567890}]";

    @Test
    public void encryptedDataStartsWithYbakMagic() throws Exception {
        byte[] encrypted = BackupCrypto.encryptString(PLAINTEXT, PASSWORD);
        assertNotNull(encrypted);
        assertTrue("encrypted data must start with YBAK magic",
                encrypted[0] == 'Y' && encrypted[1] == 'B'
                && encrypted[2] == 'A' && encrypted[3] == 'K');
    }

    @Test
    public void encryptDecryptRoundTrip() throws Exception {
        byte[] encrypted = BackupCrypto.encryptString(PLAINTEXT, PASSWORD);
        String decrypted = BackupCrypto.decryptString(encrypted, PASSWORD);
        assertEquals(PLAINTEXT, decrypted);
    }

    @Test
    public void encryptDecryptBinaryRoundTrip() throws Exception {
        byte[] original = new byte[128];
        for (int i = 0; i < original.length; i++) original[i] = (byte) i;
        byte[] encrypted = BackupCrypto.encrypt(original, PASSWORD);
        byte[] decrypted = BackupCrypto.decrypt(encrypted, PASSWORD);
        assertArrayEquals(original, decrypted);
    }

    @Test
    public void twoEncryptionsProduceDifferentCiphertext() throws Exception {
        byte[] e1 = BackupCrypto.encryptString(PLAINTEXT, PASSWORD);
        byte[] e2 = BackupCrypto.encryptString(PLAINTEXT, PASSWORD);
        assertNotNull(e1);
        assertNotNull(e2);
        assertTrue("random IV must produce different ciphertext each time", !Arrays.equals(e1, e2));
    }

    @Test
    public void wrongPasswordThrowsException() throws Exception {
        byte[] encrypted = BackupCrypto.encryptString(PLAINTEXT, PASSWORD);
        try {
            BackupCrypto.decryptString(encrypted, "wrongPassword");
            fail("Expected decryption with wrong password to throw");
        } catch (GeneralSecurityException | IOException e) {
            // expected
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
    public void nullDataThrowsIOException() {
        try {
            BackupCrypto.decrypt(null, PASSWORD);
            fail("Expected IOException for null data");
        } catch (IOException e) {
            // expected
        } catch (Exception e) {
            fail("Expected IOException, got: " + e.getClass().getName());
        }
    }

    @Test
    public void badMagicThrowsIOException() throws Exception {
        byte[] encrypted = BackupCrypto.encryptString(PLAINTEXT, PASSWORD);
        encrypted[0] = 'X'; // corrupt magic
        try {
            BackupCrypto.decryptString(encrypted, PASSWORD);
            fail("Expected IOException for corrupted magic bytes");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("magic"));
        }
    }
}
