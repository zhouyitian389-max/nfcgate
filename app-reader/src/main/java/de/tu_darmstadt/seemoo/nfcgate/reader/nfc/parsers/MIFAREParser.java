package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import android.util.Log;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public class MIFAREParser {
    private static final String TAG = "MIFAREParser";

    public interface BlockReader {
        byte[] readBlock(int blockNum) throws Exception;
    }

    private final MIFARECacheManager cache;
    private final Executor executor;

    public MIFAREParser(MIFARECacheManager cache) {
        this(cache, ForkJoinPool.commonPool());
    }

    public MIFAREParser(MIFARECacheManager cache, Executor executor) {
        this.cache = cache;
        this.executor = executor;
    }

    public Map<Integer, byte[]> readAllBlocksParallel(BlockReader reader, int blockCount) {
        List<CompletableFuture<Map.Entry<Integer, byte[]>>> futures = new ArrayList<>();
        for (int blockNum = 0; blockNum < blockCount; blockNum++) {
            final int currentBlock = blockNum;
            futures.add(CompletableFuture.supplyAsync(() -> new AbstractMap.SimpleImmutableEntry<>(
                    currentBlock,
                    readBlock(reader, currentBlock)
            ), executor));
        }

        Map<Integer, byte[]> results = new LinkedHashMap<>();
        for (CompletableFuture<Map.Entry<Integer, byte[]>> future : futures) {
            Map.Entry<Integer, byte[]> entry = future.join();
            results.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(results);
    }

    public MIFARECard parseWithCache(Map<Integer, byte[]> blockMap) {
        if (blockMap == null || blockMap.isEmpty()) {
            return new MIFARECard(Collections.emptyMap(), Collections.emptyList());
        }

        Map<Integer, byte[]> normalized = new LinkedHashMap<>();
        List<Integer> sortedBlocks = new ArrayList<>(blockMap.keySet());
        sortedBlocks.sort(Comparator.naturalOrder());
        for (Integer blockNum : sortedBlocks) {
            byte[] data = blockMap.get(blockNum);
            if (data != null) {
                cache.cacheBlock(blockNum, data);
                normalized.put(blockNum, data);
            }
        }

        List<MIFARESector> sectors = buildSectors(normalized);
        return new MIFARECard(normalized, sectors);
    }

    private byte[] readBlock(BlockReader reader, int blockNum) {
        byte[] cached = cache.getBlock(blockNum);
        if (cached != null) {
            return cached;
        }
        try {
            byte[] data = reader.readBlock(blockNum);
            if (data != null) {
                cache.cacheBlock(blockNum, data);
            }
            return data;
        } catch (Exception e) {
            Log.w(TAG, "Failed to read block " + blockNum, e);
            return null;
        }
    }

    private List<MIFARESector> buildSectors(Map<Integer, byte[]> blockMap) {
        Map<Integer, Map<Integer, byte[]>> grouped = new LinkedHashMap<>();
        for (Map.Entry<Integer, byte[]> entry : blockMap.entrySet()) {
            int sectorNum = entry.getKey() / 4;
            grouped.computeIfAbsent(sectorNum, ignored -> new LinkedHashMap<>())
                    .put(entry.getKey(), entry.getValue());
        }

        List<MIFARESector> sectors = new ArrayList<>();
        for (Map.Entry<Integer, Map<Integer, byte[]>> entry : grouped.entrySet()) {
            int sectorNum = entry.getKey();
            MIFARESector sector = cache.getSector(sectorNum);
            if (sector == null || sector.getBlocks().size() != entry.getValue().size()) {
                sector = new MIFARESector(sectorNum, entry.getValue());
                cache.cacheSector(sectorNum, sector);
            }
            sectors.add(sector);
        }
        return sectors;
    }
}
