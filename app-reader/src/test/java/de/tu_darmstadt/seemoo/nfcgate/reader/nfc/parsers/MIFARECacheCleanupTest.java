package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import org.junit.Assert;
import org.junit.Test;

public class MIFARECacheCleanupTest {
    @Test
    public void getBlockReturnsNullForExpiredEntry() throws Exception {
        MIFARECacheManager cache = new MIFARECacheManager();
        byte[] data = new byte[]{0x01, 0x02, 0x03};
        // Store with a TTL of 1ms so it expires immediately
        cache.blockCache.put(0, new MIFAREBlockCache(0, data, System.currentTimeMillis() - 1000, 1));

        Assert.assertNull(cache.getBlock(0));
        // Expired entry should be removed
        Assert.assertNull(cache.blockCache.get(0));
        cache.shutdown();
    }

    @Test
    public void getBlockReturnsDataForFreshEntry() {
        MIFARECacheManager cache = new MIFARECacheManager();
        byte[] data = new byte[]{0x0A, 0x0B, 0x0C};
        cache.cacheBlock(1, data);

        byte[] result = cache.getBlock(1);
        Assert.assertNotNull(result);
        Assert.assertArrayEquals(data, result);
        cache.shutdown();
    }

    @Test
    public void clearExpiredRemovesOnlyExpiredEntries() {
        MIFARECacheManager cache = new MIFARECacheManager();
        byte[] data = new byte[16];

        // Fresh entry
        cache.cacheBlock(0, data);
        // Expired entry
        cache.blockCache.put(1, new MIFAREBlockCache(1, data, System.currentTimeMillis() - 1000, 1));

        cache.clearExpired();

        Assert.assertNotNull(cache.getBlock(0));
        Assert.assertNull(cache.blockCache.get(1));
        cache.shutdown();
    }

    @Test
    public void shutdownStopsCleanupExecutor() {
        MIFARECacheManager cache = new MIFARECacheManager();
        cache.shutdown();
        // Calling shutdown again should not throw
        cache.shutdown();
    }
}
