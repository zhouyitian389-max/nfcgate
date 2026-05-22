package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class APDUStreamProcessorTest {
    @Test
    public void processStreamReassemblesResponses() {
        List<byte[]> stream = new ArrayList<>();
        for (byte[] response : BenchmarkSamples.apduResponses()) {
            stream.addAll(APDUStreamProcessor.chunk(response, 17));
        }

        List<APDUResponseFrame> frames = new APDUStreamProcessor(17).processStream(stream);

        Assert.assertEquals(3, frames.size());
        Assert.assertEquals(0x90, frames.get(0).getSw1());
        Assert.assertEquals(0x00, frames.get(0).getSw2());
    }

    @Test
    public void optimizerPrefersStableMidSizedChunk() {
        int chunkSize = ChunkSizeOptimizer.findOptimalChunkSize(BenchmarkSamples.apduResponses());

        Assert.assertEquals(512, chunkSize);
    }

    @Test
    public void optimizerBenchmarksAllConfiguredChunkSizes() {
        Map<Integer, Double> latencies = ChunkSizeOptimizer.benchmarkChunkSizes(BenchmarkSamples.apduResponses(), 2);

        Assert.assertEquals(5, latencies.size());
        Assert.assertTrue(latencies.containsKey(512));
    }
}
