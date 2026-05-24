package de.tu_darmstadt.seemoo.nfcgate.reader.network;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
}
