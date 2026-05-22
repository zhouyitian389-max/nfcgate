package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import java.io.ByteArrayOutputStream;
import java.util.List;

public class StreamingEMVParser {
    private final int maxChunkSize;

    public StreamingEMVParser() {
        this(1024);
    }

    public StreamingEMVParser(int maxChunkSize) {
        this.maxChunkSize = Math.max(1, maxChunkSize);
    }

    public EMVData parseChained(List<byte[]> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return null;
        }

        int totalSize = 0;
        for (byte[] chunk : chunks) {
            if (chunk != null) {
                totalSize += chunk.length;
            }
        }

        ByteArrayOutputStream merged = new ByteArrayOutputStream(Math.max(totalSize, maxChunkSize));
        for (byte[] chunk : chunks) {
            if (chunk == null || chunk.length == 0) {
                continue;
            }
            int offset = 0;
            while (offset < chunk.length) {
                int length = Math.min(maxChunkSize, chunk.length - offset);
                merged.write(chunk, offset, length);
                offset += length;
            }
        }
        return EMVParser.parse(merged.toByteArray());
    }
}
