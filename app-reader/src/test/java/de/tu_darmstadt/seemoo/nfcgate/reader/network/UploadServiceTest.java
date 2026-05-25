package de.tu_darmstadt.seemoo.nfcgate.reader.network;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UploadServiceTest {
    @Test
    public void shouldRetryIoErrors() {
        assertTrue(UploadService.shouldRetry(YitianNfcSender.SendResult.ioError(new IOException("boom"))));
    }

    @Test
    public void shouldRetryHttp5xxOnly() {
        assertTrue(UploadService.shouldRetry(YitianNfcSender.SendResult.httpError(500)));
        assertTrue(UploadService.shouldRetry(YitianNfcSender.SendResult.httpError(503)));
        assertFalse(UploadService.shouldRetry(YitianNfcSender.SendResult.httpError(400)));
        assertFalse(UploadService.shouldRetry(YitianNfcSender.SendResult.httpError(404)));
    }

    @Test
    public void shouldRetryNullResultReturnsFalse() {
        assertFalse("null result should not be retried", UploadService.shouldRetry(null));
    }

    @Test
    public void shouldNotRetryHttp4xx() {
        assertFalse(UploadService.shouldRetry(YitianNfcSender.SendResult.httpError(400)));
        assertFalse(UploadService.shouldRetry(YitianNfcSender.SendResult.httpError(401)));
        assertFalse(UploadService.shouldRetry(YitianNfcSender.SendResult.httpError(403)));
        assertFalse(UploadService.shouldRetry(YitianNfcSender.SendResult.httpError(422)));
    }

    @Test
    public void retryDelayDoublesEachAttempt() {
        assertEquals(2000L, UploadService.retryDelayMillis(0));
        assertEquals(4000L, UploadService.retryDelayMillis(1));
        assertEquals(8000L, UploadService.retryDelayMillis(2));
    }

    @Test
    public void retryDelayExponentialBackoff() {
        long prev = UploadService.retryDelayMillis(0);
        for (int i = 1; i <= 5; i++) {
            long current = UploadService.retryDelayMillis(i);
            assertEquals("retry " + i + " delay should double",
                    prev * 2, current);
            prev = current;
        }
    }
}
