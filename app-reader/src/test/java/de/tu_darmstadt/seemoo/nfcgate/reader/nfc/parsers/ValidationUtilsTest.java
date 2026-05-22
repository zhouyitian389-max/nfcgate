package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import org.junit.Assert;
import org.junit.Test;

public class ValidationUtilsTest {
    @Test
    public void validateApduThrowsForNull() {
        try {
            ValidationUtils.validateApdu(null);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("null or empty"));
        }
    }

    @Test
    public void validateApduThrowsForEmptyArray() {
        try {
            ValidationUtils.validateApdu(new byte[0]);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("null or empty"));
        }
    }

    @Test
    public void validateApduThrowsWhenExceedsMaxLength() {
        try {
            ValidationUtils.validateApdu(new byte[65537]);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("exceeds max length"));
        }
    }

    @Test
    public void validateApduAcceptsValidData() {
        ValidationUtils.validateApdu(new byte[]{0x00, 0xA4, 0x04, 0x00});
    }

    @Test
    public void validateApduAcceptsMaxLength() {
        ValidationUtils.validateApdu(new byte[65536]);
    }

    @Test
    public void validateBlockDataThrowsForNull() {
        try {
            ValidationUtils.validateBlockData(null);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("null"));
        }
    }

    @Test
    public void validateBlockDataThrowsWhenExceedsMaxLength() {
        try {
            ValidationUtils.validateBlockData(new byte[1025]);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("exceeds max length"));
        }
    }

    @Test
    public void validateBlockDataAcceptsEmptyArray() {
        ValidationUtils.validateBlockData(new byte[0]);
    }

    @Test
    public void validateBlockDataAcceptsMaxLength() {
        ValidationUtils.validateBlockData(new byte[1024]);
    }
}
