package de.tu_darmstadt.seemoo.nfcgate.reader.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CardBrandDetectorTest {

    @Test
    public void detectsKnownBrands() {
        assertEquals(CardBrandDetector.CardBrand.VISA, CardBrandDetector.detect("4111111111111111"));
        assertEquals(CardBrandDetector.CardBrand.MASTERCARD, CardBrandDetector.detect("5100000000000000"));
        assertEquals(CardBrandDetector.CardBrand.MASTERCARD, CardBrandDetector.detect("2221000000000000"));
        assertEquals(CardBrandDetector.CardBrand.AMEX, CardBrandDetector.detect("371449635398431"));
        assertEquals(CardBrandDetector.CardBrand.UNIONPAY, CardBrandDetector.detect("6212345678901234"));
        assertEquals(CardBrandDetector.CardBrand.JCB, CardBrandDetector.detect("3528000000000000"));
        assertEquals(CardBrandDetector.CardBrand.DISCOVER, CardBrandDetector.detect("6011123412341234"));
        assertEquals(CardBrandDetector.CardBrand.DISCOVER, CardBrandDetector.detect("6500000000000000"));
        assertEquals(CardBrandDetector.CardBrand.DISCOVER, CardBrandDetector.detect("6440000000000000"));
    }

    @Test
    public void unknownWhenNoPan() {
        assertEquals(CardBrandDetector.CardBrand.UNKNOWN, CardBrandDetector.detect(null));
        assertEquals(CardBrandDetector.CardBrand.UNKNOWN, CardBrandDetector.detect("abc"));
        assertEquals(CardBrandDetector.CardBrand.UNKNOWN, CardBrandDetector.detect("123456"));
    }
}
