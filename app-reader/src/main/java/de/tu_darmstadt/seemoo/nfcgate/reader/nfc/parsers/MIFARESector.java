package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MIFARESector {
    private final int sectorNum;
    private final Map<Integer, byte[]> blocks;

    public MIFARESector(int sectorNum, Map<Integer, byte[]> blocks) {
        this.sectorNum = sectorNum;
        Map<Integer, byte[]> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, byte[]> entry : blocks.entrySet()) {
            copy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        this.blocks = Collections.unmodifiableMap(copy);
    }

    public int getSectorNum() {
        return sectorNum;
    }

    public Map<Integer, byte[]> getBlocks() {
        Map<Integer, byte[]> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, byte[]> entry : blocks.entrySet()) {
            copy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        return Collections.unmodifiableMap(copy);
    }
}
