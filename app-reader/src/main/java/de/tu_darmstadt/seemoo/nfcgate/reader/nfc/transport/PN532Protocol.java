package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Locale;

public class PN532Protocol {
    private static final String TAG = "PN532Protocol";
    private static final byte PREAMBLE = 0x00;
    private static final byte START_CODE_1 = 0x00;
    private static final byte START_CODE_2 = (byte) 0xFF;
    private static final byte POSTAMBLE = 0x00;
    private static final byte HOST_TO_PN532 = (byte) 0xD4;

    private final USBConnection usb;
    @Nullable
    private UsbEndpoint outEndpoint;
    @Nullable
    private UsbEndpoint inEndpoint;

    public PN532Protocol(USBConnection usb) {
        this.usb = usb;
    }

    public boolean configureForDevice(UsbDevice device) {
        outEndpoint = null;
        inEndpoint = null;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            UsbEndpoint outEp = null;
            UsbEndpoint inEp = null;
            for (int j = 0; j < intf.getEndpointCount(); j++) {
                UsbEndpoint ep = intf.getEndpoint(j);
                if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    continue;
                }
                if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    outEp = ep;
                } else if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                    inEp = ep;
                }
            }
            if (outEp != null && inEp != null && usb.claimInterface(intf)) {
                outEndpoint = outEp;
                inEndpoint = inEp;
                Log.d(TAG, "Using interface " + intf.getId() + " endpoints OUT=" + outEp.getAddress() + " IN=" + inEp.getAddress());
                return true;
            }
        }
        Log.w(TAG, "No suitable bulk endpoints found for PN532 device");
        return false;
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
        UsbEndpoint outEp = outEndpoint;
        UsbEndpoint inEp = inEndpoint;
        if (outEp == null || inEp == null) {
            Log.w(TAG, "Endpoints not configured before sendCommand");
            return new byte[0];
        }
        if (outEp.getDirection() == UsbConstants.USB_DIR_OUT) {
            usb.bulkTransfer(outEp, buildFrame(cmd), timeout);
        }
        if (inEp.getDirection() == UsbConstants.USB_DIR_IN) {
            return parseFrame(usb.bulkTransfer(inEp, new byte[Math.max(inEp.getMaxPacketSize(), 64)], timeout));
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
            sb.append(String.format(Locale.ROOT, "%02X", b));
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
