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

import java.util.List;
import java.util.concurrent.TimeUnit;

@Fork(1)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
public class EMVParserBenchmark {
    private List<byte[]> sampleApdus;

    @Setup
    public void setup() {
        EMVCache.clear();
        sampleApdus = BenchmarkSamples.emvApdus();
    }

    @Benchmark
    public void parseSelectAIDResponse() {
        EMVParser.parse(sampleApdus.get(0));
    }

    @Benchmark
    public void parseGpoResponse() {
        EMVParser.parse(sampleApdus.get(1));
    }

    @Benchmark
    public void parseReadRecordResponse() {
        EMVParser.parse(sampleApdus.get(2));
    }

    @Benchmark
    public void parseSequentialApdus() {
        for (byte[] apdu : sampleApdus) {
            EMVParser.parse(apdu);
        }
    }

    @Benchmark
    public void parseWithCaching() {
        for (int i = 0; i < 5; i++) {
            EMVParser.parseWithCache(sampleApdus.get(0));
        }
    }
}
