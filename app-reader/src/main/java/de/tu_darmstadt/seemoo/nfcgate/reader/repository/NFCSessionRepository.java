package de.tu_darmstadt.seemoo.nfcgate.reader.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCEvent;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers.ChunkSizeOptimizer;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers.EMVCache;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers.EMVData;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers.EMVParser;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers.MIFARECacheManager;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers.MIFARECard;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers.MIFAREParser;

public class NFCSessionRepository {
    private final List<NFCEvent> events = new ArrayList<>();
    private final MIFARECacheManager mifareCacheManager = new MIFARECacheManager();
    private final MIFAREParser mifareParser = new MIFAREParser(mifareCacheManager);

    public synchronized void append(NFCEvent event) {
        events.add(event);
    }

    public synchronized List<NFCEvent> getEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public synchronized EMVData getLastEmvData() {
        for (int i = events.size() - 1; i >= 0; i--) {
            NFCEvent event = events.get(i);
            if (event instanceof NFCEvent.APDUResponse) {
                return EMVParser.parseWithCache(((NFCEvent.APDUResponse) event).getResponse());
            }
        }
        return null;
    }

    public synchronized MIFARECard getMifareCard() {
        Map<Integer, byte[]> blocks = new LinkedHashMap<>();
        for (NFCEvent event : events) {
            if (event instanceof NFCEvent.MIFAREBlock) {
                NFCEvent.MIFAREBlock block = (NFCEvent.MIFAREBlock) event;
                blocks.put(block.getBlockNum(), block.getData());
            }
        }
        return mifareParser.parseWithCache(blocks);
    }

    public synchronized int getRecommendedApduChunkSize() {
        List<byte[]> responses = new ArrayList<>();
        for (NFCEvent event : events) {
            if (event instanceof NFCEvent.APDUResponse) {
                responses.add(((NFCEvent.APDUResponse) event).getResponse());
            }
        }
        return ChunkSizeOptimizer.findOptimalChunkSize(responses);
    }

    public synchronized void clear() {
        events.clear();
        EMVCache.clear();
        mifareCacheManager.clear();
    }
}
