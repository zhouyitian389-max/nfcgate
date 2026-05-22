package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MIFARECard {
    private final Map<Integer, byte[]> blocks;
    private final List<MIFARESector> sectors;

    public MIFARECard(Map<Integer, byte[]> blocks, List<MIFARESector> sectors) {
        Map<Integer, byte[]> blockCopy = new LinkedHashMap<>();
        for (Map.Entry<Integer, byte[]> entry : blocks.entrySet()) {
            blockCopy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        this.blocks = Collections.unmodifiableMap(blockCopy);
        this.sectors = Collections.unmodifiableList(new ArrayList<>(sectors));
    }

    public Map<Integer, byte[]> getBlocks() {
        Map<Integer, byte[]> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, byte[]> entry : blocks.entrySet()) {
            copy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        return Collections.unmodifiableMap(copy);
    }

    public List<MIFARESector> getSectors() {
        return sectors;
    }
}
