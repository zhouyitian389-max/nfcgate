package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class APDUStreamProcessor {
    private final int chunkSize;

    public APDUStreamProcessor() {
        this(256);
    }

    public APDUStreamProcessor(int chunkSize) {
        this.chunkSize = Math.max(1, chunkSize);
    }

    public List<APDUResponseFrame> processStream(Iterable<byte[]> apduStream) {
        if (apduStream == null) {
            return Collections.emptyList();
        }

        List<APDUResponseFrame> responses = new ArrayList<>();
        APDUAccumulator accumulator = new APDUAccumulator();
        for (byte[] chunk : apduStream) {
            for (byte[] splitChunk : chunk(chunk, chunkSize)) {
                accumulator.append(splitChunk);
                if (accumulator.isComplete()) {
                    APDUResponseFrame response = accumulator.build();
                    if (response != null) {
                        responses.add(response);
                    }
                    accumulator.reset();
                }
            }
        }
        return responses;
    }

    public static List<byte[]> chunk(byte[] response, int chunkSize) {
        if (response == null || response.length == 0) {
            return Collections.emptyList();
        }

        int normalizedChunkSize = Math.max(1, chunkSize);
        List<byte[]> chunks = new ArrayList<>((response.length + normalizedChunkSize - 1) / normalizedChunkSize);
        for (int offset = 0; offset < response.length; offset += normalizedChunkSize) {
            int length = Math.min(normalizedChunkSize, response.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(response, offset, chunk, 0, length);
            chunks.add(chunk);
        }
        return chunks;
    }
}
