package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import java.util.Arrays;

public class MIFAREBlockCache {
    private static final long DEFAULT_TTL_MS = 5 * 60 * 1000L;

    private final int blockNum;
    private final byte[] data;
    private final long timestamp;
    private final long ttl;

    public MIFAREBlockCache(int blockNum, byte[] data) {
        this(blockNum, data, System.currentTimeMillis(), DEFAULT_TTL_MS);
    }

    public MIFAREBlockCache(int blockNum, byte[] data, long timestamp, long ttl) {
        this.blockNum = blockNum;
        this.data = Arrays.copyOf(data, data.length);
        this.timestamp = timestamp;
        this.ttl = ttl;
    }

    public int getBlockNum() {
        return blockNum;
    }

    public byte[] getData() {
        return Arrays.copyOf(data, data.length);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - timestamp > ttl;
    }
}
