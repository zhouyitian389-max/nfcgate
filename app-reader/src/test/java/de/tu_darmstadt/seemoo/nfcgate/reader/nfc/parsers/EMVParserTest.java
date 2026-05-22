package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class EMVParserTest {
    @Before
    public void clearCache() {
        EMVCache.clear();
    }

    @Test
    public void parseSelectAidResponseExtractsNestedFields() {
        EMVData data = EMVParser.parse(BenchmarkSamples.selectAidResponse());

        Assert.assertNotNull(data);
        Assert.assertEquals("2PAY.SYS.DDF01", data.getDedicatedFileName());
        Assert.assertEquals("325041592E5359532E4444463031", data.getAid());
        Assert.assertEquals("en", new String(data.getTagValue("5F2D")));
    }

    @Test
    public void parseReadRecordResponseExtractsCardData() {
        EMVData data = EMVParser.parse(BenchmarkSamples.readRecordResponse());

        Assert.assertNotNull(data);
        Assert.assertEquals("JOHN DOE", data.getCardholderName());
        Assert.assertEquals("4761739001010010", data.getPan());
        Assert.assertEquals("VISA CREDIT", data.getApplicationLabel());
    }

    @Test
    public void parseWithCacheReturnsSameCachedInstance() {
        byte[] response = BenchmarkSamples.selectAidResponse();

        EMVData first = EMVParser.parseWithCache(response);
        EMVData second = EMVParser.parseWithCache(response.clone());

        Assert.assertSame(first, second);
    }

    @Test
    public void streamingParserHandlesChunkedRecord() {
        byte[] response = BenchmarkSamples.readRecordResponse();
        List<byte[]> chunks = APDUStreamProcessor.chunk(response, 32);

        EMVData data = new StreamingEMVParser(32).parseChained(chunks);

        Assert.assertNotNull(data);
        Assert.assertEquals("JOHN DOE", data.getCardholderName());
    }
}
