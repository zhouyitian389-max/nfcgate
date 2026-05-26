package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.util.Log;

import androidx.annotation.Nullable;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.PN532Protocol.CardInfo;

public class ACR122UProtocol {
    private static final String TAG = "ACR122UProtocol";
    private final USBConnection usb;
    @Nullable
    private UsbEndpoint outEndpoint;
    @Nullable
    private UsbEndpoint inEndpoint;

    public ACR122UProtocol(USBConnection usb) {
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
        Log.w(TAG, "No suitable bulk endpoints found for ACR122U device");
        return false;
    }

    public byte[] transmitAPDU(byte[] apdu) {
        if (outEndpoint == null || inEndpoint == null) {
            return new byte[0];
        }
        byte[] command = new byte[apdu.length + 2];
        command[0] = (byte) 0xFF;
        command[1] = 0x00;
        System.arraycopy(apdu, 0, command, 2, apdu.length);
        usb.bulkTransfer(outEndpoint, command, 1000);
        return usb.bulkTransfer(inEndpoint, new byte[Math.max(inEndpoint.getMaxPacketSize(), 64)], 1000);
    }

    @Nullable
    public CardInfo pollCard(int timeout) {
        if (outEndpoint == null || inEndpoint == null) {
            return null;
        }
        if (outEndpoint.getDirection() != UsbConstants.USB_DIR_OUT || inEndpoint.getDirection() != UsbConstants.USB_DIR_IN) {
            return null;
        }
        byte[] command = new byte[]{(byte) 0xFF, 0x00, 0x00, 0x00, 0x04, (byte) 0xD4, 0x4A, 0x01, 0x00};
        usb.bulkTransfer(outEndpoint, command, timeout);
        byte[] response = usb.bulkTransfer(inEndpoint, new byte[Math.max(inEndpoint.getMaxPacketSize(), 64)], timeout);
        if (response.length < 5) {
            return null;
        }
        int uidLen = response[response.length - 1] & 0xFF;
        if (uidLen <= 0 || uidLen > response.length) {
            return null;
        }
        byte[] uid = new byte[uidLen];
        System.arraycopy(response, response.length - uidLen, uid, 0, uidLen);
        return new CardInfo(uid, "ISO14443A", null);
    }
}
