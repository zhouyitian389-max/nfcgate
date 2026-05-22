package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

public class APDUAccumulatorEdgeCaseTest {
    /** Build a minimal TLV-encoded APDU response with a 2-byte length field. */
    private static byte[] buildTwoByteLength(int valueLength) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x70); // tag
        out.write(0x82); // two-byte length indicator
        out.write((valueLength >>> 8) & 0xFF);
        out.write(valueLength & 0xFF);
        for (int i = 0; i < valueLength; i++) {
            out.write(i & 0xFF);
        }
        out.write(0x90); // SW1
        out.write(0x00); // SW2
        return out.toByteArray();
    }

    @Test
    public void isCompleteHandlesTwoByteLength() {
        byte[] apdu = buildTwoByteLength(300);
        APDUAccumulator accumulator = new APDUAccumulator();
        accumulator.append(apdu);
        Assert.assertTrue(accumulator.isComplete());
    }

    @Test
    public void buildReturnsTwoByteFrame() {
        byte[] apdu = buildTwoByteLength(256);
        APDUAccumulator accumulator = new APDUAccumulator();
        accumulator.append(apdu);
        APDUResponseFrame frame = accumulator.build();
        Assert.assertNotNull(frame);
        Assert.assertEquals(0x90, frame.getSw1());
        Assert.assertEquals(0x00, frame.getSw2());
        // 1 tag byte + 1 length indicator + 2 length bytes + 256 value bytes
        Assert.assertEquals(260, frame.getData().length);
    }

    @Test
    public void isCompleteReturnsFalseForTruncatedMultiByteLength() {
        // Send only the tag + the 0x82 length indicator byte (truncated)
        APDUAccumulator accumulator = new APDUAccumulator();
        accumulator.append(new byte[]{0x70, (byte) 0x82, 0x01}); // missing second length byte
        accumulator.append(new byte[]{(byte) 0x90, 0x00});
        Assert.assertFalse(accumulator.isComplete());
    }

    @Test
    public void isCompleteHandlesSingleByteLength() {
        // Simple short TLV
        APDUAccumulator accumulator = new APDUAccumulator();
        accumulator.append(new byte[]{0x70, 0x02, 0x01, 0x02, (byte) 0x90, 0x00});
        Assert.assertTrue(accumulator.isComplete());
    }

    @Test
    public void appendIgnoresNullAndEmpty() {
        APDUAccumulator accumulator = new APDUAccumulator();
        accumulator.append(null);
        accumulator.append(new byte[0]);
        Assert.assertFalse(accumulator.isComplete());
    }
}
