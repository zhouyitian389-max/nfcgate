package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class EMVCacheThreadSafetyTest {
    @Before
    public void clearCache() {
        EMVCache.clear();
    }

    @Test
    public void concurrentGetOrParseReturnsSameInstance() throws Exception {
        final byte[] data = BenchmarkSamples.readRecordResponse();
        final int threadCount = 8;
        final CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        List<Future<EMVData>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                return EMVCache.getOrParse(data.clone());
            }));
        }

        EMVData first = futures.get(0).get();
        Assert.assertNotNull(first);
        for (Future<EMVData> future : futures) {
            Assert.assertSame("All threads must return the same cached instance", first, future.get());
        }
        executor.shutdown();
    }

    @Test
    public void getOrParseHandlesNullAndEmpty() {
        Assert.assertNull(EMVCache.getOrParse(null));
        Assert.assertNull(EMVCache.getOrParse(new byte[0]));
    }

    @Test
    public void getOrParseCachesSingleResult() {
        byte[] data = BenchmarkSamples.selectAidResponse();
        EMVData first = EMVCache.getOrParse(data);
        EMVData second = EMVCache.getOrParse(data.clone());
        Assert.assertNotNull(first);
        Assert.assertSame(first, second);
    }
}
