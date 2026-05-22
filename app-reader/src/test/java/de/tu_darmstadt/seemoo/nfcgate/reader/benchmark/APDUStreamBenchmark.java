package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Fork(1)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
public class APDUStreamBenchmark {
    @Param({"64", "128", "256", "512", "1024"})
    public int chunkSize;

    private List<byte[]> responses;

    @Setup
    public void setup() {
        responses = BenchmarkSamples.apduResponses();
    }

    @Benchmark
    public void processApduStream() {
        List<byte[]> stream = new ArrayList<>();
        for (byte[] response : responses) {
            stream.addAll(APDUStreamProcessor.chunk(response, chunkSize));
        }
        new APDUStreamProcessor(chunkSize).processStream(stream);
    }
}
