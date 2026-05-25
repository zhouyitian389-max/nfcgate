package de.tu_darmstadt.seemoo.nfcgate.hce.security;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PinHasherTest {

    @Test
    public void hashOutputHasTwoParts() {
        String stored = PinHasher.hash("1234");
        assertNotNull(stored);
        String[] parts = stored.split(":", 2);
        assertEquals("hash output must have exactly two colon-separated parts", 2, parts.length);
        assertFalse("salt part must not be empty", parts[0].isEmpty());
        assertFalse("hash part must not be empty", parts[1].isEmpty());
    }

    @Test
    public void hashPartsAreValidBase64() {
        String stored = PinHasher.hash("5678");
        String[] parts = stored.split(":", 2);
        // If not Base64, java.util.Base64 would throw – test indirectly via verify()
        assertTrue("verify should succeed immediately after hash",
                PinHasher.verify("5678", stored));
    }

    @Test
    public void twoHashesOfSamePinDifferentSalts() {
        String h1 = PinHasher.hash("9999");
        String h2 = PinHasher.hash("9999");
        String salt1 = h1.split(":", 2)[0];
        String salt2 = h2.split(":", 2)[0];
        assertNotEquals("different hashes must use different salts (random salt)", salt1, salt2);
    }

    @Test
    public void verifyCorrectPinReturnsTrue() {
        String stored = PinHasher.hash("1234");
        assertTrue(PinHasher.verify("1234", stored));
    }

    @Test
    public void verifyWrongPinReturnsFalse() {
        String stored = PinHasher.hash("1234");
        assertFalse(PinHasher.verify("9999", stored));
        assertFalse(PinHasher.verify("12345", stored));
        assertFalse(PinHasher.verify("", stored));
    }

    @Test
    public void verifyNullPinReturnsFalseWithoutCrash() {
        String stored = PinHasher.hash("1234");
        assertFalse("verify(null, stored) must return false, not throw",
                PinHasher.verify(null, stored));
    }

    @Test
    public void verifyNullStoredReturnsFalseWithoutCrash() {
        assertFalse("verify(pin, null) must return false, not throw",
                PinHasher.verify("1234", null));
    }

    @Test
    public void verifyBothNullReturnsFalse() {
        assertFalse(PinHasher.verify(null, null));
    }

    @Test
    public void verifyMalformedStoredReturnsFalse() {
        assertFalse(PinHasher.verify("1234", "not-a-valid-hash"));
        assertFalse(PinHasher.verify("1234", ""));
    }
}
