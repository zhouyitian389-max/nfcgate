package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbEndpoint;

import androidx.annotation.Nullable;

public class PN532Protocol {
    private static final byte PREAMBLE = 0x00;
    private static final byte START_CODE_1 = 0x00;
    private static final byte START_CODE_2 = (byte) 0xFF;
    private static final byte POSTAMBLE = 0x00;
    private static final byte HOST_TO_PN532 = (byte) 0xD4;

    private final USBConnection usb;
    private final UsbEndpoint outEndpoint;
    private final UsbEndpoint inEndpoint;

    public PN532Protocol(USBConnection usb, @Nullable UsbEndpoint outEndpoint, @Nullable UsbEndpoint inEndpoint) {
        this.usb = usb;
        this.outEndpoint = outEndpoint;
        this.inEndpoint = inEndpoint;
    }

    public String getVersion() {
        byte[] response = sendCommand(new byte[]{0x02}, 1000);
        return bytesToHex(response);
    }

    public boolean initRF() {
        byte[] response = sendCommand(new byte[]{0x32, 0x01, 0x00}, 1000);
        return response.length > 0;
    }

    @Nullable
    public CardInfo pollCard(int timeout) {
        byte[] response = sendCommand(new byte[]{0x4A, 0x01, 0x00}, timeout);
        return parseCardInfo(response);
    }

    public byte[] transceiveAPDU(byte[] apdu) {
        byte[] cmd = new byte[apdu.length + 1];
        cmd[0] = 0x40;
        System.arraycopy(apdu, 0, cmd, 1, apdu.length);
        return sendCommand(cmd, 1000);
    }

    @Nullable
    public byte[] readMIFAREBlock(int block) {
        byte[] response = sendCommand(new byte[]{0x40, 0x01, 0x30, (byte) block}, 1000);
        return response.length == 0 ? null : response;
    }

    public byte[] sendCommand(byte[] cmd, int timeout) {
        if (outEndpoint != null && outEndpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
            usb.bulkTransfer(outEndpoint, buildFrame(cmd), timeout);
        }
        if (inEndpoint != null && inEndpoint.getDirection() == UsbConstants.USB_DIR_IN) {
            return parseFrame(usb.bulkTransfer(inEndpoint, new byte[Math.max(inEndpoint.getMaxPacketSize(), 64)], timeout));
        }
        return new byte[0];
    }

    static byte[] buildFrame(byte[] cmd) {
        int dataLength = cmd.length + 1;
        byte len = (byte) dataLength;
        byte lcs = (byte) (0x100 - (len & 0xFF));
        byte[] payload = new byte[dataLength];
        payload[0] = HOST_TO_PN532;
        System.arraycopy(cmd, 0, payload, 1, cmd.length);
        int sum = 0;
        for (byte b : payload) {
            sum += b & 0xFF;
        }
        byte dcs = (byte) (0x100 - (sum & 0xFF));

        byte[] frame = new byte[payload.length + 7];
        int idx = 0;
        frame[idx++] = PREAMBLE;
        frame[idx++] = START_CODE_1;
        frame[idx++] = START_CODE_2;
        frame[idx++] = len;
        frame[idx++] = lcs;
        System.arraycopy(payload, 0, frame, idx, payload.length);
        idx += payload.length;
        frame[idx++] = dcs;
        frame[idx] = POSTAMBLE;
        return frame;
    }

    static byte[] parseFrame(byte[] frame) {
        if (frame.length < 8) {
            return new byte[0];
        }
        int payloadLen = frame[3] & 0xFF;
        if (payloadLen < 1 || frame.length < payloadLen + 7) {
            return new byte[0];
        }
        byte[] payload = new byte[payloadLen - 1];
        System.arraycopy(frame, 6, payload, 0, payload.length);
        return payload;
    }

    @Nullable
    private CardInfo parseCardInfo(byte[] response) {
        if (response.length < 8) {
            return null;
        }
        int uidLen = response[7] & 0xFF;
        if (uidLen <= 0 || response.length < 8 + uidLen) {
            return null;
        }
        byte[] uid = new byte[uidLen];
        System.arraycopy(response, 8, uid, 0, uidLen);
        return new CardInfo(uid, "ISO14443A", null);
    }

    private static String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static class CardInfo {
        private final byte[] uid;
        private final String type;
        @Nullable
        private final byte[] atqa;

        public CardInfo(byte[] uid, String type, @Nullable byte[] atqa) {
            this.uid = uid;
            this.type = type;
            this.atqa = atqa;
        }

        public byte[] getUid() {
            return uid;
        }

        public String getType() {
            return type;
        }

        @Nullable
        public byte[] getAtqa() {
            return atqa;
        }
    }
}
