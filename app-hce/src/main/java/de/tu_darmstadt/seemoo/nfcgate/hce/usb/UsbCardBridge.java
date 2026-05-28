package de.tu_darmstadt.seemoo.nfcgate.hce.usb;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;

public class UsbCardBridge {
    private static final String TAG = "UsbCardBridge";
    private static final int ACR_VENDOR_ID = 0x072F;
    private static final int PID_NF = 0x8234;
    private static final int PID_UF = 0x8235;
    private static final int CCID_INTERFACE_CLASS = 0x0B;
    private static final int TIMEOUT_MS = 5000;
    private static final int SLOT_STATUS_TIMEOUT_MS = 500;
    private static final int MSG_POWER_ON = 0x62;
    private static final int MSG_POWER_OFF = 0x63;
    private static final int MSG_GET_SLOT_STATUS = 0x65;
    private static final int MSG_XFR_BLOCK = 0x6F;
    private static final int MSG_DATA_BLOCK = 0x80;
    private static final int MSG_SLOT_STATUS = 0x81;
    private static final int SLOT = 0x00;

    private final Context context;
    private final UsbManager usbManager;
    private final Object lock = new Object();

    @Nullable
    private UsbDevice device;
    @Nullable
    private UsbDeviceConnection connection;
    @Nullable
    private UsbInterface ccidInterface;
    @Nullable
    private UsbEndpoint outEndpoint;
    @Nullable
    private UsbEndpoint inEndpoint;
    private int sequence;
    private boolean poweredOn;

    public UsbCardBridge(Context context) {
        this.context = context.getApplicationContext();
        this.usbManager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);
    }

    public void refreshConnection() {
        synchronized (lock) {
            ensureConnectedLocked();
        }
    }

    public boolean isConnected() {
        synchronized (lock) {
            return ensureConnectedLocked();
        }
    }

    @Nullable
    public byte[] transceive(byte[] apdu) {
        synchronized (lock) {
            if (apdu == null || !ensureConnectedLocked()) {
                return null;
            }
            if (!isCardPresentLocked()) {
                poweredOn = false;
                return null;
            }
            if (!poweredOn) {
                byte[] atr = sendPowerOnLocked();
                if (atr.length == 0) {
                    return null;
                }
                poweredOn = true;
            }
            byte[] response = sendXfrBlockLocked(apdu);
            return response.length > 0 ? response : null;
        }
    }

    public boolean powerOff() {
        synchronized (lock) {
            if (!ensureConnectedLocked()) {
                return false;
            }
            boolean ok = sendPowerOffLocked();
            if (ok) {
                poweredOn = false;
            }
            return ok;
        }
    }

    public boolean isCardPresent() {
        synchronized (lock) {
            if (!ensureConnectedLocked()) {
                return false;
            }
            return isCardPresentLocked();
        }
    }

    public void close() {
        synchronized (lock) {
            disconnectLocked();
        }
    }

    private boolean ensureConnectedLocked() {
        if (usbManager == null) {
            return false;
        }
        if (connection != null && device != null && usbManager.getDeviceList().containsKey(device.getDeviceName())) {
            return true;
        }
        disconnectLocked();

        UsbDevice target = null;
        for (UsbDevice candidate : usbManager.getDeviceList().values()) {
            if (isAcr39u(candidate)) {
                target = candidate;
                break;
            }
        }
        if (target == null || !usbManager.hasPermission(target)) {
            return false;
        }

        UsbDeviceConnection opened = usbManager.openDevice(target);
        if (opened == null) {
            return false;
        }
        UsbInterface selectedInterface = null;
        UsbEndpoint selectedOut = null;
        UsbEndpoint selectedIn = null;

        for (int i = 0; i < target.getInterfaceCount(); i++) {
            UsbInterface intf = target.getInterface(i);
            if (intf.getInterfaceClass() != CCID_INTERFACE_CLASS) {
                continue;
            }
            UsbEndpoint out = null;
            UsbEndpoint in = null;
            for (int j = 0; j < intf.getEndpointCount(); j++) {
                UsbEndpoint ep = intf.getEndpoint(j);
                if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    continue;
                }
                if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    out = ep;
                } else if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                    in = ep;
                }
            }
            if (out != null && in != null && opened.claimInterface(intf, true)) {
                selectedInterface = intf;
                selectedOut = out;
                selectedIn = in;
                break;
            }
        }

        if (selectedInterface == null || selectedOut == null || selectedIn == null) {
            opened.close();
            return false;
        }

        device = target;
        connection = opened;
        ccidInterface = selectedInterface;
        outEndpoint = selectedOut;
        inEndpoint = selectedIn;
        sequence = 0;
        poweredOn = false;
        Log.d(TAG, "Connected to ACR39U " + target.getDeviceName());
        return true;
    }

    private void disconnectLocked() {
        if (connection != null && ccidInterface != null) {
            try {
                connection.releaseInterface(ccidInterface);
            } catch (Exception ignored) {
            }
        }
        if (connection != null) {
            connection.close();
        }
        device = null;
        connection = null;
        ccidInterface = null;
        outEndpoint = null;
        inEndpoint = null;
        sequence = 0;
        poweredOn = false;
    }

    private byte[] sendPowerOnLocked() {
        int seq = nextSequenceLocked();
        writeCommandLocked(buildPowerOnCommand(seq));
        return readDataBlockLocked(seq);
    }

    private byte[] sendXfrBlockLocked(byte[] apdu) {
        int seq = nextSequenceLocked();
        writeCommandLocked(buildXfrBlockCommand(seq, apdu));
        return readDataBlockLocked(seq);
    }

    private boolean sendPowerOffLocked() {
        int seq = nextSequenceLocked();
        writeCommandLocked(buildPowerOffCommand(seq));
        return readSlotStatusLocked(seq).length > 0;
    }

    private boolean isCardPresentLocked() {
        int seq = nextSequenceLocked();
        writeCommandLocked(buildGetSlotStatusCommand(seq));
        byte[] response = readSlotStatusLocked(seq);
        if (response.length == 0) {
            return false;
        }
        int status = response[0] & 0x03;
        return status <= 1;
    }

    private void writeCommandLocked(byte[] command) {
        if (connection == null || outEndpoint == null) {
            return;
        }
        connection.bulkTransfer(outEndpoint, command, command.length, TIMEOUT_MS);
    }

    private byte[] readDataBlockLocked(int expectedSequence) {
        if (connection == null || inEndpoint == null) {
            return new byte[0];
        }
        byte[] firstChunk = readChunkLocked();
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
            byte[] chunk = readChunkLocked();
            if (chunk.length == 0) {
                break;
            }
            int toCopy = Math.min(remaining, chunk.length);
            out.write(chunk, 0, toCopy);
            remaining -= toCopy;
        }
        return out.toByteArray();
    }

    private byte[] readChunkLocked() {
        return readChunkLocked(TIMEOUT_MS);
    }

    private byte[] readChunkLocked(int timeoutMs) {
        if (connection == null || inEndpoint == null) {
            return new byte[0];
        }
        byte[] buffer = new byte[Math.max(inEndpoint.getMaxPacketSize(), 512)];
        int read = connection.bulkTransfer(inEndpoint, buffer, buffer.length, timeoutMs);
        if (read <= 0) {
            return new byte[0];
        }
        byte[] out = new byte[read];
        System.arraycopy(buffer, 0, out, 0, read);
        return out;
    }

    private byte[] readSlotStatusLocked(int expectedSequence) {
        byte[] response = readChunkLocked(SLOT_STATUS_TIMEOUT_MS);
        if (response.length < 10 || (response[0] & 0xFF) != MSG_SLOT_STATUS) {
            return new byte[0];
        }
        int responseSeq = response[6] & 0xFF;
        if (responseSeq != expectedSequence) {
            Log.w(TAG, "CCID slot status sequence mismatch exp=" + expectedSequence + " got=" + responseSeq);
        }
        return new byte[]{response[7]};
    }

    private int nextSequenceLocked() {
        int current = sequence & 0xFF;
        sequence = (sequence + 1) & 0xFF;
        return current;
    }

    private static boolean isAcr39u(UsbDevice device) {
        if (device.getVendorId() != ACR_VENDOR_ID) {
            return false;
        }
        int pid = device.getProductId() & 0xFFFF;
        return pid == PID_NF || pid == PID_UF;
    }

    static byte[] buildPowerOnCommand(int sequence) {
        byte[] command = new byte[10];
        command[0] = (byte) MSG_POWER_ON;
        command[5] = (byte) SLOT;
        command[6] = (byte) (sequence & 0xFF);
        command[7] = 0x00;
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
        command[7] = 0x00;
        command[8] = 0x00;
        command[9] = 0x00;
        if (payload.length > 0) {
            System.arraycopy(payload, 0, command, 10, payload.length);
        }
        return command;
    }

    static byte[] buildPowerOffCommand(int sequence) {
        byte[] command = new byte[10];
        command[0] = (byte) MSG_POWER_OFF;
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

    private static int readUInt32LE(byte[] data, int offset) {
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
