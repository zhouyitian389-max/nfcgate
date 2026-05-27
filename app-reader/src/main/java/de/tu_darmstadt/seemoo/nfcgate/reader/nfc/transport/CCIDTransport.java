package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;

public class CCIDTransport {
    private static final String TAG = "CCIDTransport";
    private static final int CCID_INTERFACE_CLASS = 0x0B;
    private static final int MSG_ICC_POWER_ON = 0x62;
    private static final int MSG_ICC_POWER_OFF = 0x63;
    private static final int MSG_GET_SLOT_STATUS = 0x65;
    private static final int MSG_XFR_BLOCK = 0x6F;
    private static final int MSG_DATA_BLOCK = 0x80;
    private static final int MSG_SLOT_STATUS = 0x81;
    private static final int SLOT = 0x00;
    private static final int TIMEOUT_MS = 5000;
    private static final int DEFAULT_BAUD_RATE = 9600; // Use reader default.

    private final USBConnection usb;
    @Nullable
    private UsbEndpoint outEndpoint;
    @Nullable
    private UsbEndpoint inEndpoint;
    private int sequence;

    public CCIDTransport(USBConnection usb) {
        this.usb = usb;
    }

    public boolean configureForDevice(UsbDevice device) {
        outEndpoint = null;
        inEndpoint = null;
        sequence = 0;

        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            if (intf.getInterfaceClass() != CCID_INTERFACE_CLASS) {
                continue;
            }
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
                Log.d(TAG, "CCID interface " + intf.getId() + " selected, default baud=" + DEFAULT_BAUD_RATE);
                return true;
            }
        }
        Log.w(TAG, "No CCID bulk endpoints found");
        return false;
    }

    public byte[] powerOn() {
        int seq = nextSequence();
        sendCommand(buildPowerOnCommand(seq));
        return readDataBlock(seq);
    }

    public byte[] xfrBlock(byte[] apdu) {
        int seq = nextSequence();
        sendCommand(buildXfrBlockCommand(seq, apdu));
        return readDataBlock(seq);
    }

    public boolean powerOff() {
        int seq = nextSequence();
        sendCommand(buildPowerOffCommand(seq));
        return readSlotStatus(seq).length > 0;
    }

    public int getSlotStatus() {
        int seq = nextSequence();
        sendCommand(buildGetSlotStatusCommand(seq));
        byte[] response = readSlotStatus(seq);
        if (response.length == 0) {
            return -1;
        }
        return response[0] & 0x03;
    }

    public boolean isCardPresent() {
        int status = getSlotStatus();
        return status >= 0 && status <= 1;
    }

    private int nextSequence() {
        int current = sequence & 0xFF;
        sequence = (sequence + 1) & 0xFF;
        return current;
    }

    private void sendCommand(byte[] command) {
        if (outEndpoint == null) {
            return;
        }
        usb.bulkTransfer(outEndpoint, command, TIMEOUT_MS);
    }

    private byte[] readDataBlock(int expectedSequence) {
        if (inEndpoint == null) {
            return new byte[0];
        }
        byte[] firstChunk = usb.bulkTransfer(inEndpoint, new byte[Math.max(inEndpoint.getMaxPacketSize(), 512)], TIMEOUT_MS);
        if (firstChunk.length < 10 || (firstChunk[0] & 0xFF) != MSG_DATA_BLOCK) {
            return new byte[0];
        }

        int dataLength = readUInt32LE(firstChunk, 1);
        int responseSeq = firstChunk[6] & 0xFF;
        int status = firstChunk[7] & 0xFF;
        if (responseSeq != expectedSequence) {
            Log.w(TAG, "CCID sequence mismatch exp=" + expectedSequence + " got=" + responseSeq);
        }
        if ((status & 0x03) != 0x00) {
            return new byte[0];
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(dataLength, 0));
        int payloadInFirst = Math.max(0, firstChunk.length - 10);
        int copied = Math.min(payloadInFirst, dataLength);
        if (copied > 0) {
            out.write(firstChunk, 10, copied);
        }

        int remaining = dataLength - copied;
        while (remaining > 0) {
            byte[] nextChunk = usb.bulkTransfer(inEndpoint, new byte[Math.max(inEndpoint.getMaxPacketSize(), 512)], TIMEOUT_MS);
            if (nextChunk.length == 0) {
                break;
            }
            int toCopy = Math.min(remaining, nextChunk.length);
            out.write(nextChunk, 0, toCopy);
            remaining -= toCopy;
        }
        return out.toByteArray();
    }

    private byte[] readSlotStatus(int expectedSequence) {
        if (inEndpoint == null) {
            return new byte[0];
        }
        byte[] response = usb.bulkTransfer(inEndpoint, new byte[Math.max(inEndpoint.getMaxPacketSize(), 512)], TIMEOUT_MS);
        if (response.length < 10 || (response[0] & 0xFF) != MSG_SLOT_STATUS) {
            return new byte[0];
        }
        int responseSeq = response[6] & 0xFF;
        if (responseSeq != expectedSequence) {
            Log.w(TAG, "CCID slot status sequence mismatch exp=" + expectedSequence + " got=" + responseSeq);
        }
        return new byte[]{response[7]};
    }

    static byte[] buildPowerOnCommand(int sequence) {
        byte[] command = new byte[10];
        command[0] = (byte) MSG_ICC_POWER_ON;
        // dwLength = 0
        command[5] = (byte) SLOT;
        command[6] = (byte) (sequence & 0xFF);
        command[7] = 0x00; // automatic voltage selection
        command[8] = 0x00;
        command[9] = 0x00;
        return command;
    }

    static byte[] buildXfrBlockCommand(int sequence, byte[] apdu) {
        byte[] payload = apdu != null ? apdu : new byte[0];
        byte[] command = new byte[10 + payload.length];
        command[0] = (byte) MSG_XFR_BLOCK;
        writeUInt32LE(command, 1, payload.length);
        command[5] = (byte) SLOT;
        command[6] = (byte) (sequence & 0xFF);
        command[7] = 0x00; // bBWI
        command[8] = 0x00; // wLevelParameter low
        command[9] = 0x00; // wLevelParameter high
        if (payload.length > 0) {
            System.arraycopy(payload, 0, command, 10, payload.length);
        }
        return command;
    }

    static byte[] buildPowerOffCommand(int sequence) {
        byte[] command = new byte[10];
        command[0] = (byte) MSG_ICC_POWER_OFF;
        command[5] = (byte) SLOT;
        command[6] = (byte) (sequence & 0xFF);
        return command;
    }

    static byte[] buildGetSlotStatusCommand(int sequence) {
        byte[] command = new byte[10];
        command[0] = (byte) MSG_GET_SLOT_STATUS;
        command[5] = (byte) SLOT;
        command[6] = (byte) (sequence & 0xFF);
        return command;
    }

    static int readUInt32LE(byte[] data, int offset) {
        if (data == null || data.length < offset + 4) {
            return 0;
        }
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private static void writeUInt32LE(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }
}
