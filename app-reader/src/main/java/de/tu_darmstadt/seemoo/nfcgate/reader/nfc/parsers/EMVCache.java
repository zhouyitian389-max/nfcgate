package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EMVCache {
    private static final int MAX_ENTRIES = 100;
    private static final Map<ByteArrayKey, EMVData> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<ByteArrayKey, EMVData>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ByteArrayKey, EMVData> eldest) {
                    return size() > MAX_ENTRIES;
                }
            }
    );

    private EMVCache() {
    }

    public static EMVData getOrParse(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }

        ByteArrayKey lookupKey = new ByteArrayKey(data, false);

        synchronized (CACHE) {
            EMVData cached = CACHE.get(lookupKey);
            if (cached != null) {
                return cached;
            }

            EMVData parsed = EMVParser.parseInternal(data);
            if (parsed != null) {
                CACHE.put(new ByteArrayKey(data, true), parsed);
            }
            return parsed;
        }
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private static final class ByteArrayKey {
        private final byte[] data;
        private final int hashCode;

        private ByteArrayKey(byte[] source, boolean copy) {
            this.data = copy ? Arrays.copyOf(source, source.length) : source;
            this.hashCode = Arrays.hashCode(this.data);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ByteArrayKey)) {
                return false;
            }
            ByteArrayKey that = (ByteArrayKey) other;
            return Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
