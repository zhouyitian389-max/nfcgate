package de.tu_darmstadt.seemoo.nfcgate.reader.network;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class YitianNfcSenderTest {
    @Test
    public void buildUploadUrlDefaultsToHttpWhenSchemeIsMissing() throws Exception {
        assertEquals(
                "http://192.168.1.100:8080/api/cards",
                YitianNfcSender.buildUploadUrl("192.168.1.100", 8080).toString()
        );
    }

    @Test
    public void buildUploadUrlKeepsExistingScheme() throws Exception {
        assertEquals(
                "http://example.com/api/cards",
                YitianNfcSender.buildUploadUrl("http://example.com", 8080).toString()
        );
        assertEquals(
                "https://example.com:8443/api/cards",
                YitianNfcSender.buildUploadUrl("https://example.com:8443", 8080).toString()
        );
    }

    @Test
    public void buildUploadUrlThrowsForEmptyHost() {
        try {
            YitianNfcSender.buildUploadUrl("", 8080);
            fail("Expected IllegalArgumentException for empty host");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (Exception e) {
            fail("Expected IllegalArgumentException, got: " + e.getClass().getName());
        }
    }

    @Test
    public void buildUploadUrlThrowsForNullHost() {
        try {
            YitianNfcSender.buildUploadUrl(null, 8080);
            fail("Expected IllegalArgumentException for null host");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (Exception e) {
            fail("Expected IllegalArgumentException, got: " + e.getClass().getName());
        }
    }

    @Test
    public void buildUploadUrlThrowsForInvalidPort() {
        try {
            YitianNfcSender.buildUploadUrl("192.168.1.1", 0);
            fail("Expected IllegalArgumentException for port 0");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (Exception e) {
            fail("Expected IllegalArgumentException, got: " + e.getClass().getName());
        }

        try {
            YitianNfcSender.buildUploadUrl("192.168.1.1", -1);
            fail("Expected IllegalArgumentException for negative port");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (Exception e) {
            fail("Expected IllegalArgumentException, got: " + e.getClass().getName());
        }

        try {
            YitianNfcSender.buildUploadUrl("192.168.1.1", 65536);
            fail("Expected IllegalArgumentException for port > 65535");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (Exception e) {
            fail("Expected IllegalArgumentException, got: " + e.getClass().getName());
        }
    }

    @Test
    public void userAgentContainsVersionString() {
        // Assert against the production USER_AGENT constant, not a hard-coded duplicate.
        assertTrue("User-Agent must start with 'YitianRead/'",
                YitianNfcSender.USER_AGENT.startsWith("YitianRead/"));
        assertTrue("User-Agent must contain the current version string 'v5.6-YiTian'",
                YitianNfcSender.USER_AGENT.contains("v5.6-YiTian"));
    }

    @Test
    public void buildAuthorizationHeaderValueSkipsBlankTokens() {
        assertEquals(null, YitianNfcSender.buildAuthorizationHeaderValue(null));
        assertEquals(null, YitianNfcSender.buildAuthorizationHeaderValue("   "));
    }

    @Test
    public void buildAuthorizationHeaderValueFormatsBearerToken() {
        assertEquals("Bearer secret-token",
                YitianNfcSender.buildAuthorizationHeaderValue("  secret-token  "));
    }
}
