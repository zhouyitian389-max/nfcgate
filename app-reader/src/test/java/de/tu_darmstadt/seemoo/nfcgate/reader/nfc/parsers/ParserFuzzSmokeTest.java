package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class ParserFuzzSmokeTest {
    @Test
    public void parsersDoNotCrashOnRandomInput() {
        Random random = new Random(1337L);
        for (int i = 0; i < 128; i++) {
            byte[] payload = new byte[random.nextInt(128)];
            random.nextBytes(payload);
            EMVParser.parse(payload);
        }
    }

    @Test
    public void mifareParserHandlesRandomBlocks() {
        Random random = new Random(4242L);
        MIFAREParser parser = new MIFAREParser(new MIFARECacheManager());
        for (int iteration = 0; iteration < 32; iteration++) {
            Map<Integer, byte[]> blocks = new LinkedHashMap<>();
            for (int block = 0; block < 8; block++) {
                byte[] payload = new byte[random.nextInt(18)];
                random.nextBytes(payload);
                blocks.put(block, payload);
            }
            parser.parseWithCache(blocks);
        }
    }
}
