package de.tu_darmstadt.seemoo.nfcgate.hce.service;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class YitianHttpServerAuthTest {
    @Test
    public void isAuthorizedAcceptsMatchingBearerToken() {
        assertTrue(YitianHttpServer.isAuthorized("Bearer secret-token", "secret-token"));
    }

    @Test
    public void isAuthorizedRejectsMissingOrWrongToken() {
        assertFalse(YitianHttpServer.isAuthorized(null, "secret-token"));
        assertFalse(YitianHttpServer.isAuthorized("Bearer wrong-token", "secret-token"));
        assertFalse(YitianHttpServer.isAuthorized("Basic secret-token", "secret-token"));
        assertFalse(YitianHttpServer.isAuthorized("Bearer secret-token", null));
    }
}
