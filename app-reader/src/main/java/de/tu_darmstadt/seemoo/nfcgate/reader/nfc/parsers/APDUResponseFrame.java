package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers;

import java.util.Arrays;

public class APDUResponseFrame {
    private final byte[] data;
    private final int sw1;
    private final int sw2;

    public APDUResponseFrame(byte[] data, int sw1, int sw2) {
        this.data = Arrays.copyOf(data, data.length);
        this.sw1 = sw1;
        this.sw2 = sw2;
    }

    public byte[] getData() {
        return Arrays.copyOf(data, data.length);
    }

    public int getSw1() {
        return sw1;
    }

    public int getSw2() {
        return sw2;
    }

    public byte[] toByteArray() {
        byte[] response = Arrays.copyOf(data, data.length + 2);
        response[data.length] = (byte) sw1;
        response[data.length + 1] = (byte) sw2;
        return response;
    }
}
