package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import org.junit.Assert;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MIFAREParserTest {
    @Test
    public void readAllBlocksParallelUsesCacheWhenAvailable() {
        MIFARECacheManager cache = new MIFARECacheManager();
        AtomicInteger reads = new AtomicInteger();
        for (int block = 0; block < 8; block++) {
            cache.cacheBlock(block, BenchmarkSamples.mifareBlocks(64).get(block));
        }

        MIFAREParser parser = new MIFAREParser(cache);
        Map<Integer, byte[]> blocks = parser.readAllBlocksParallel(blockNum -> {
            reads.incrementAndGet();
            return BenchmarkSamples.mifareBlocks(64).get(blockNum);
        }, 16);

        Assert.assertEquals(16, blocks.size());
        Assert.assertEquals(8, reads.get());
    }

    @Test
    public void parseWithCacheBuildsSectors() {
        MIFARECacheManager cache = new MIFARECacheManager();
        MIFAREParser parser = new MIFAREParser(cache);

        MIFARECard card = parser.parseWithCache(BenchmarkSamples.mifareBlocks(16));

        Assert.assertEquals(16, card.getBlocks().size());
        Assert.assertEquals(4, card.getSectors().size());
        Assert.assertEquals(4, card.getSectors().get(0).getBlocks().size());
    }

    @Test
    public void invalidateSectorRemovesCachedBlocks() {
        MIFARECacheManager cache = new MIFARECacheManager();
        cache.cacheBlock(4, BenchmarkSamples.mifareBlocks(8).get(4));
        cache.cacheBlock(5, BenchmarkSamples.mifareBlocks(8).get(5));

        cache.invalidateSector(1);

        Assert.assertNull(cache.getBlock(4));
        Assert.assertNull(cache.getBlock(5));
    }
}
