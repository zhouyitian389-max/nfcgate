package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChunkSizeOptimizer {
    private static final List<Integer> CHUNK_SIZES = Arrays.asList(64, 128, 256, 512, 1024);

    private ChunkSizeOptimizer() {
    }

    public static int findOptimalChunkSize(List<byte[]> responses) {
        return findOptimalChunkSize(responses, 100);
    }

    static int findOptimalChunkSize(List<byte[]> responses, int iterations) {
        if (responses == null || responses.isEmpty()) {
            return 256;
        }

        Map<Integer, Double> results = benchmarkChunkSizes(responses, iterations);
        double fastest = results.values().stream().min(Double::compareTo).orElse(Double.MAX_VALUE);
        Double preferred = results.get(512);
        if (preferred != null && preferred <= fastest * 1.25d) {
            return 512;
        }

        return results.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(256);
    }

    public static Map<Integer, Double> benchmarkChunkSizes(List<byte[]> responses, int iterations) {
        if (responses == null || responses.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Integer, Double> results = new LinkedHashMap<>();
        for (int chunkSize : CHUNK_SIZES) {
            APDUStreamProcessor processor = new APDUStreamProcessor(chunkSize);
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                List<byte[]> stream = new java.util.ArrayList<>();
                for (byte[] response : responses) {
                    stream.addAll(APDUStreamProcessor.chunk(response, chunkSize));
                }
                processor.processStream(stream);
            }
            double averageMs = (System.nanoTime() - start) / 1_000_000.0 / iterations;
            results.put(chunkSize, averageMs);
        }
        return Collections.unmodifiableMap(results);
    }
}
