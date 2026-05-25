package de.tu_darmstadt.seemoo.nfcgate.hce.service;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class YitianHttpServerAuthTest {
    @Test
    public void bearerTokenAcceptsExactMatch() {
        assertTrue(YitianHttpServer.isBearerTokenValid("Bearer abc123", "abc123"));
    }

    @Test
    public void bearerTokenRejectsMissingPrefix() {
        assertFalse(YitianHttpServer.isBearerTokenValid("abc123", "abc123"));
    }

    @Test
    public void bearerTokenRejectsWrongToken() {
        assertFalse(YitianHttpServer.isBearerTokenValid("Bearer xyz", "abc123"));
    }

    @Test
    public void bearerTokenRejectsNullOrEmptyToken() {
        assertFalse(YitianHttpServer.isBearerTokenValid("Bearer abc123", null));
        assertFalse(YitianHttpServer.isBearerTokenValid("Bearer abc123", ""));
    }
}
