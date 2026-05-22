package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MIFARECacheManager {
    private static final int MAX_SECTORS = 16;

    private final ConcurrentHashMap<Integer, MIFAREBlockCache> blockCache = new ConcurrentHashMap<>();
    private final Map<Integer, MIFARESector> sectorCache = Collections.synchronizedMap(
            new LinkedHashMap<Integer, MIFARESector>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, MIFARESector> eldest) {
                    return size() > MAX_SECTORS;
                }
            }
    );

    public byte[] getBlock(int blockNum) {
        MIFAREBlockCache cached = blockCache.get(blockNum);
        if (cached == null) {
            return null;
        }
        if (cached.isExpired()) {
            blockCache.remove(blockNum, cached);
            return null;
        }
        return cached.getData();
    }

    public void cacheBlock(int blockNum, byte[] data) {
        if (data == null) {
            return;
        }
        blockCache.put(blockNum, new MIFAREBlockCache(blockNum, data));
    }

    public MIFARESector getSector(int sectorNum) {
        synchronized (sectorCache) {
            return sectorCache.get(sectorNum);
        }
    }

    public void cacheSector(int sectorNum, MIFARESector sector) {
        synchronized (sectorCache) {
            sectorCache.put(sectorNum, sector);
        }
    }

    public void invalidateSector(int sectorNum) {
        int startBlock = sectorNum * 4;
        for (int block = startBlock; block < startBlock + 4; block++) {
            blockCache.remove(block);
        }
        synchronized (sectorCache) {
            sectorCache.remove(sectorNum);
        }
    }

    public void clearExpired() {
        blockCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public void clear() {
        blockCache.clear();
        synchronized (sectorCache) {
            sectorCache.clear();
        }
    }
}
