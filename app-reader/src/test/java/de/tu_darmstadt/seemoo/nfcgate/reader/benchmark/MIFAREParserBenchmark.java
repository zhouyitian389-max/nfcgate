package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Fork(1)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
public class MIFAREParserBenchmark {
    private MIFARECacheManager cache;
    private MIFAREParser parser;
    private Map<Integer, byte[]> blocks;

    @Setup
    public void setup() {
        cache = new MIFARECacheManager();
        parser = new MIFAREParser(cache);
        blocks = BenchmarkSamples.mifareBlocks(1000);
        for (int block = 0; block < 64; block++) {
            cache.cacheBlock(block, blocks.get(block));
        }
    }

    @Benchmark
    public void readAllBlocksWithCache() {
        parser.readAllBlocksParallel(blocks::get, 64);
    }

    @Benchmark
    public void readAllBlocksNoCacheFirst() {
        cache.clear();
        parser.readAllBlocksParallel(blocks::get, 64);
    }

    @Benchmark
    public void parseBlocks() {
        parser.parseWithCache(blocks);
    }
}
