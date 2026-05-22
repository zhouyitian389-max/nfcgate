package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BenchmarkSamples {
    private BenchmarkSamples() {
    }

    static byte[] selectAidResponse() {
        return tlv("6F",
                tlv("84", ascii("2PAY.SYS.DDF01")),
                tlv("A5",
                        tlv("88", HexUtils.hexToBytes("02")),
                        tlv("5F2D", ascii("en")),
                        tlv("9F11", HexUtils.hexToBytes("01")),
                        tlv("5F34", HexUtils.hexToBytes("01")),
                        tlv("82", HexUtils.hexToBytes("1C00")),
                        tlv("9F10", HexUtils.hexToBytes("06011003A00000"))
                )
        );
    }

    static byte[] gpoResponse() {
        return tlv("80", HexUtils.hexToBytes("180010080101001000000000"));
    }

    static byte[] readRecordResponse() {
        return tlv("70",
                tlv("5A", HexUtils.hexToBytes("4761739001010010")),
                tlv("5F24", HexUtils.hexToBytes("251231")),
                tlv("5F20", ascii("JOHN DOE")),
                tlv("5F34", HexUtils.hexToBytes("01")),
                tlv("9F07", HexUtils.hexToBytes("FF00")),
                tlv("9F08", HexUtils.hexToBytes("008C")),
                tlv("9F42", HexUtils.hexToBytes("0978")),
                tlv("9F44", HexUtils.hexToBytes("02")),
                tlv("8E", HexUtils.hexToBytes("000000000000000042031E031F03")),
                tlv("9F0D", HexUtils.hexToBytes("B860A80000")),
                tlv("9F0E", HexUtils.hexToBytes("0010000000")),
                tlv("9F0F", HexUtils.hexToBytes("B868BC9800")),
                tlv("9F10", HexUtils.hexToBytes("06010A03A00000")),
                tlv("9F11", HexUtils.hexToBytes("01")),
                tlv("9F12", ascii("VISA CREDIT")),
                tlv("9F1A", HexUtils.hexToBytes("0840")),
                tlv("9F26", HexUtils.hexToBytes("1122334455667788")),
                tlv("9F27", HexUtils.hexToBytes("80")),
                tlv("9F36", HexUtils.hexToBytes("0012")),
                tlv("9F37", HexUtils.hexToBytes("A1B2C3D4")),
                tlv("95", HexUtils.hexToBytes("0000000000")),
                tlv("5F2A", HexUtils.hexToBytes("0840")),
                tlv("82", HexUtils.hexToBytes("5800")),
                tlv("9F33", HexUtils.hexToBytes("E0F8C8")),
                tlv("9F34", HexUtils.hexToBytes("1F0302"))
        );
    }

    static List<byte[]> emvApdus() {
        List<byte[]> apdus = new ArrayList<>();
        apdus.add(selectAidResponse());
        apdus.add(gpoResponse());
        apdus.add(readRecordResponse());
        return apdus;
    }

    static List<byte[]> apduResponses() {
        List<byte[]> responses = new ArrayList<>();
        responses.add(withStatus(selectAidResponse(), 0x90, 0x00));
        responses.add(withStatus(gpoResponse(), 0x90, 0x00));
        responses.add(withStatus(readRecordResponse(), 0x90, 0x00));
        return responses;
    }

    static Map<Integer, byte[]> mifareBlocks(int blockCount) {
        Map<Integer, byte[]> blocks = new LinkedHashMap<>();
        for (int block = 0; block < blockCount; block++) {
            byte[] data = new byte[16];
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) (block + i);
            }
            blocks.put(block, data);
        }
        return blocks;
    }

    static byte[] withStatus(byte[] data, int sw1, int sw2) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(data.length + 2);
        output.write(data, 0, data.length);
        output.write(sw1);
        output.write(sw2);
        return output.toByteArray();
    }

    private static byte[] tlv(String tagHex, byte[]... values) {
        ByteArrayOutputStream value = new ByteArrayOutputStream();
        for (byte[] bytes : values) {
            value.write(bytes, 0, bytes.length);
        }
        byte[] tag = HexUtils.hexToBytes(tagHex);
        byte[] encodedValue = value.toByteArray();

        ByteArrayOutputStream output = new ByteArrayOutputStream(tag.length + encodedValue.length + 3);
        output.write(tag, 0, tag.length);
        writeLength(output, encodedValue.length);
        output.write(encodedValue, 0, encodedValue.length);
        return output.toByteArray();
    }

    private static void writeLength(ByteArrayOutputStream output, int length) {
        if (length < 0x80) {
            output.write(length);
        } else if (length <= 0xFF) {
            output.write(0x81);
            output.write(length);
        } else {
            output.write(0x82);
            output.write((length >>> 8) & 0xFF);
            output.write(length & 0xFF);
        }
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
