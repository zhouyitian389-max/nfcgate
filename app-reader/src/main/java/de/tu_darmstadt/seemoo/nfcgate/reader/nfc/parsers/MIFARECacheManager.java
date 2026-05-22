package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MIFARECacheManager {
    private static final int MAX_SECTORS = 16;
    private static final long CLEANUP_INTERVAL_MS = 60_000;

    final ConcurrentHashMap<Integer, MIFAREBlockCache> blockCache = new ConcurrentHashMap<>();
    private final Map<Integer, MIFARESector> sectorCache = Collections.synchronizedMap(
            new LinkedHashMap<Integer, MIFARESector>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, MIFARESector> eldest) {
                    return size() > MAX_SECTORS;
                }
            }
    );
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MIFARE-Cache-Cleanup");
        t.setDaemon(true);
        return t;
    });

    public MIFARECacheManager() {
        cleanupExecutor.scheduleAtFixedRate(
                this::clearExpired,
                CLEANUP_INTERVAL_MS,
                CLEANUP_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    public byte[] getBlock(int blockNum) {
        MIFAREBlockCache cached = blockCache.get(blockNum);
        if (cached != null && !cached.isExpired()) {
            return cached.getData();
        }
        if (cached != null) {
            blockCache.remove(blockNum, cached);
        }
        return null;
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

    public void shutdown() {
        cleanupExecutor.shutdownNow();
    }

    public void clear() {
        blockCache.clear();
        synchronized (sectorCache) {
            sectorCache.clear();
        }
    }
}
