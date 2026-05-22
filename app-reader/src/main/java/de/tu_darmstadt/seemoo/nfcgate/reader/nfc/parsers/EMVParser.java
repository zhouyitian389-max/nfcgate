package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EMVParser {
    private static final int MAX_DEPTH = 8;

    private EMVParser() {
    }

    public static EMVData parse(byte[] data) {
        try {
            return EMVCache.getOrParse(data);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static EMVData parseWithCache(byte[] data) {
        return parse(data);
    }

    static EMVData parseInternal(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }

        Map<String, byte[]> tlvs = parseTLV(data);
        if (tlvs.isEmpty()) {
            return null;
        }
        return buildEMVData(tlvs);
    }

    private static Map<String, byte[]> parseTLV(byte[] data) {
        Map<String, byte[]> tlvs = new LinkedHashMap<>();
        parseRange(data, 0, data.length, tlvs, 0);
        return Collections.unmodifiableMap(tlvs);
    }

    private static void parseRange(byte[] data, int start, int end, Map<String, byte[]> tlvs, int depth) {
        if (depth > MAX_DEPTH) {
            return;
        }

        int offset = start;
        while (offset < end) {
            int tagStart = offset;
            int firstTagByte = data[offset++] & 0xFF;
            if ((firstTagByte & 0x1F) == 0x1F) {
                while (offset < end) {
                    int current = data[offset++] & 0xFF;
                    if ((current & 0x80) == 0) {
                        break;
                    }
                }
            }
            if (offset >= end) {
                return;
            }

            int tagEnd = offset;
            int lengthByte = data[offset++] & 0xFF;
            int length;
            if ((lengthByte & 0x80) == 0) {
                length = lengthByte;
            } else {
                int byteCount = lengthByte & 0x7F;
                if (byteCount == 0 || byteCount > 3 || offset + byteCount > end) {
                    return;
                }
                length = 0;
                for (int i = 0; i < byteCount; i++) {
                    length = (length << 8) | (data[offset++] & 0xFF);
                }
            }

            if (length < 0 || offset + length > end) {
                return;
            }

            String tag = HexUtils.toHex(data, tagStart, tagEnd - tagStart);
            byte[] value = Arrays.copyOfRange(data, offset, offset + length);
            tlvs.put(tag, value);

            if ((firstTagByte & 0x20) == 0x20) {
                parseRange(value, 0, value.length, tlvs, depth + 1);
            }
            offset += length;
        }
    }


    private static EMVData buildEMVData(Map<String, byte[]> tlvs) {
        byte[] templateOne = tlvs.get("80");
        String aip = null;
        String afl = null;
        if (templateOne != null && templateOne.length >= 2) {
            aip = HexUtils.toHex(templateOne, 0, 2);
            if (templateOne.length > 2) {
                afl = HexUtils.toHex(templateOne, 2, templateOne.length - 2);
            }
        }

        return new EMVData(
                decodeText(firstNonNull(tlvs.get("84"))),
                decodeText(firstNonNull(tlvs.get("50"), tlvs.get("9F12"))),
                decodeText(tlvs.get("5F20")),
                HexUtils.toHex(firstNonNull(tlvs.get("9F06"), tlvs.get("84"))),
                decodePan(firstNonNull(tlvs.get("5A"), tlvs.get("57"))),
                aip,
                afl,
                tlvs
        );
    }

    private static byte[] firstNonNull(byte[]... values) {
        for (byte[] value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String decodeText(byte[] value) {
        if (value == null || value.length == 0) {
            return null;
        }
        String decoded = new String(value, StandardCharsets.US_ASCII).trim();
        return decoded.isEmpty() ? null : decoded;
    }

    private static String decodePan(byte[] value) {
        if (value == null || value.length == 0) {
            return null;
        }
        String hex = HexUtils.toHex(value);
        int fieldSeparator = hex.indexOf('D');
        if (fieldSeparator >= 0) {
            hex = hex.substring(0, fieldSeparator);
        }
        int padding = hex.indexOf('F');
        if (padding >= 0) {
            hex = hex.substring(0, padding);
        }
        return hex;
    }
}
