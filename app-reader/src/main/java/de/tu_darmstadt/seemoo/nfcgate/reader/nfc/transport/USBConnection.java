package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Arrays;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCDevice;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCSource;

public class USBConnection {
    private static final String TAG = "USBConnection";
    private final UsbManager usbManager;
    @Nullable
    private UsbDeviceConnection connection;
    @Nullable
    private UsbDevice device;
    @Nullable
    private UsbInterface claimedInterface;

    public USBConnection(Context context) {
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    public boolean connect(UsbDevice device) {
        disconnect();
        this.device = device;
        if (!usbManager.hasPermission(device)) {
            Log.w(TAG, "No USB permission for device " + device.getDeviceName());
            return false;
        }
        this.connection = usbManager.openDevice(device);
        return connection != null;
    }

    public void disconnect() {
        if (connection != null && claimedInterface != null) {
            connection.releaseInterface(claimedInterface);
        }
        claimedInterface = null;
        if (connection != null) {
            connection.close();
        }
        connection = null;
        device = null;
    }

    public boolean claimInterface(UsbInterface usbInterface) {
        if (connection == null || usbInterface == null) {
            return false;
        }
        if (claimedInterface != null && claimedInterface.getId() != usbInterface.getId()) {
            connection.releaseInterface(claimedInterface);
            claimedInterface = null;
        }
        if (claimedInterface != null && claimedInterface.getId() == usbInterface.getId()) {
            return true;
        }
        boolean claimed = connection.claimInterface(usbInterface, true);
        if (claimed) {
            claimedInterface = usbInterface;
        } else {
            Log.w(TAG, "Failed to claim USB interface " + usbInterface.getId());
        }
        return claimed;
    }

    public byte[] bulkTransfer(UsbEndpoint endpoint, byte[] data, int timeout) {
        if (connection == null || endpoint == null) {
            return new byte[0];
        }
        byte[] out = new byte[Math.max(endpoint.getMaxPacketSize(), data.length)];
        if (endpoint.getDirection() == android.hardware.usb.UsbConstants.USB_DIR_OUT) {
            int sent = connection.bulkTransfer(endpoint, data, data.length, timeout);
            return sent >= 0 ? Arrays.copyOf(data, sent) : new byte[0];
        }
        int read = connection.bulkTransfer(endpoint, out, out.length, timeout);
        if (read <= 0) {
            return new byte[0];
        }
        byte[] result = new byte[read];
        System.arraycopy(out, 0, result, 0, read);
        return result;
    }

    public byte[] controlTransfer(ControlRequest request) {
        if (connection == null) {
            return new byte[0];
        }
        byte[] buffer = request.data != null ? request.data : new byte[Math.max(0, request.length)];
        int result = connection.controlTransfer(
                request.requestType,
                request.request,
                request.value,
                request.index,
                buffer,
                buffer.length,
                request.timeout
        );
        if (result <= 0) {
            return new byte[0];
        }
        byte[] out = new byte[result];
        System.arraycopy(buffer, 0, out, 0, result);
        return out;
    }

    public boolean isConnected() {
        return connection != null;
    }

    @Nullable
    public UsbDevice getDevice() {
        return device;
    }

    public NFCDevice getDeviceInfo() {
        if (device == null) {
            return new NFCDevice("usb-none", "USB Device", NFCSource.PN532, false);
        }
        return new NFCDevice(
                device.getDeviceName(),
                "USB " + device.getVendorId() + ":" + device.getProductId(),
                NFCSource.PN532,
                isConnected()
        );
    }

    public static class ControlRequest {
        public final int requestType;
        public final int request;
        public final int value;
        public final int index;
        public final int length;
        public final int timeout;
        @Nullable
        public final byte[] data;

        public ControlRequest(int requestType, int request, int value, int index, int length, int timeout, @Nullable byte[] data) {
            this.requestType = requestType;
            this.request = request;
            this.value = value;
            this.index = index;
            this.length = length;
            this.timeout = timeout;
            this.data = data;
        }
    }
}
