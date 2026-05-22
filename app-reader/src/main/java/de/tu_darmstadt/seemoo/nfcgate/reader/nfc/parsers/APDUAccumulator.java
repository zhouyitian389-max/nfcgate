package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public class APDUAccumulator {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public void append(byte[] chunk) {
        if (chunk == null || chunk.length == 0) {
            return;
        }
        buffer.write(chunk, 0, chunk.length);
    }

    public boolean isComplete() {
        byte[] data = buffer.toByteArray();
        if (data.length < 2) {
            return false;
        }

        int sw1 = data[data.length - 2] & 0xFF;
        if (sw1 == 0x61) {
            return false;
        }

        int payloadLength = data.length - 2;
        return payloadLength == 0 || isCompleteTlvPayload(data, payloadLength);
    }

    public APDUResponseFrame build() {
        byte[] response = buffer.toByteArray();
        if (response.length < 2) {
            return null;
        }
        int dataLength = response.length - 2;
        return new APDUResponseFrame(
                Arrays.copyOf(response, dataLength),
                response[dataLength] & 0xFF,
                response[dataLength + 1] & 0xFF
        );
    }

    public void reset() {
        buffer.reset();
    }

    private boolean isCompleteTlvPayload(byte[] response, int payloadLength) {
        int offset = 0;
        while (offset < payloadLength) {
            int firstTagByte = response[offset++] & 0xFF;
            if ((firstTagByte & 0x1F) == 0x1F) {
                while (offset < payloadLength) {
                    int current = response[offset++] & 0xFF;
                    if ((current & 0x80) == 0) {
                        break;
                    }
                }
                if (offset > payloadLength) {
                    return false;
                }
            }
            if (offset >= payloadLength) {
                return false;
            }

            int lengthByte = response[offset++] & 0xFF;
            int length;
            if ((lengthByte & 0x80) == 0) {
                length = lengthByte;
            } else {
                int byteCount = lengthByte & 0x7F;
                if (byteCount == 0 || byteCount > 3 || offset + byteCount > payloadLength) {
                    return false;
                }
                length = 0;
                for (int i = 0; i < byteCount; i++) {
                    length = (length << 8) | (response[offset++] & 0xFF);
                }
            }

            if (offset + length > payloadLength) {
                return false;
            }
            offset += length;
        }
        return offset == payloadLength;
    }
}
