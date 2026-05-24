package de.tu_darmstadt.seemoo.nfcgate.reader.network;

import org.junit.Test;

import java.net.URL;

import static org.junit.Assert.assertEquals;

public class YitianNfcSenderTest {
    @Test
    public void buildEndpointUrlDefaultsToHttp() throws Exception {
        URL url = YitianNfcSender.buildEndpointUrl("192.168.1.2", 8080);
        assertEquals("http://192.168.1.2:8080/api/cards", url.toString());
    }

    @Test
    public void buildEndpointUrlPreservesHttpsScheme() throws Exception {
        URL url = YitianNfcSender.buildEndpointUrl("https://example.com", 8443);
        assertEquals("https://example.com:8443/api/cards", url.toString());
    }

    @Test
    public void buildEndpointUrlKeepsExplicitPort() throws Exception {
        URL url = YitianNfcSender.buildEndpointUrl("https://example.com:9443", 8443);
        assertEquals("https://example.com:9443/api/cards", url.toString());
    }
}
