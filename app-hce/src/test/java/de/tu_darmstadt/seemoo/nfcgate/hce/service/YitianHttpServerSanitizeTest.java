package de.tu_darmstadt.seemoo.nfcgate.hce.service;

import org.junit.Test;

import de.tu_darmstadt.seemoo.nfcgate.hce.util.CardSanitizer;

import static org.junit.Assert.assertEquals;

public class YitianHttpServerSanitizeTest {

    // ─── sanitizePan ────────────────────────────────────────────────────────

    @Test
    public void sanitizePanAccepts8DigitPan() {
        assertEquals("12345678", CardSanitizer.sanitizePan("12345678"));
    }

    @Test
    public void sanitizePanAccepts32DigitPan() {
        String pan32 = "12345678901234567890123456789012";
        assertEquals(pan32, CardSanitizer.sanitizePan(pan32));
    }

    @Test
    public void sanitizePanTrimsWhitespace() {
        assertEquals("12345678901234", CardSanitizer.sanitizePan("  12345678901234  "));
    }

    @Test
    public void sanitizePanReturnsEmptyForNullOrEmpty() {
        assertEquals("", CardSanitizer.sanitizePan(null));
        assertEquals("", CardSanitizer.sanitizePan(""));
        assertEquals("", CardSanitizer.sanitizePan("   "));
    }

    @Test(expected = IllegalArgumentException.class)
    public void sanitizePanRejectsNonNumeric() {
        CardSanitizer.sanitizePan("123abc789");
    }

    @Test(expected = IllegalArgumentException.class)
    public void sanitizePanRejectsTooShort() {
        CardSanitizer.sanitizePan("1234567"); // only 7 digits, minimum is 8
    }

    @Test(expected = IllegalArgumentException.class)
    public void sanitizePanRejectsTooLong() {
        CardSanitizer.sanitizePan("123456789012345678901234567890123"); // 33 digits
    }

    // ─── clamp ──────────────────────────────────────────────────────────────

    @Test
    public void clampReturnsValueWhenWithinLimit() {
        assertEquals("hello", CardSanitizer.clamp("hello", 10));
        assertEquals("hello", CardSanitizer.clamp("hello", 5));
    }

    @Test
    public void clampTruncatesWhenExceedsLimit() {
        assertEquals("hel", CardSanitizer.clamp("hello", 3));
        assertEquals("", CardSanitizer.clamp("hello", 0));
    }

    @Test
    public void clampHandlesNullAsEmpty() {
        assertEquals("", CardSanitizer.clamp(null, 10));
    }
}
