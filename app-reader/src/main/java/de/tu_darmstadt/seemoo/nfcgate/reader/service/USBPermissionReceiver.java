package de.tu_darmstadt.seemoo.nfcgate.reader.service;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import androidx.annotation.Nullable;

public class USBPermissionReceiver extends BroadcastReceiver {
    public static final String ACTION_USB_PERMISSION = "de.tu_darmstadt.seemoo.nfcgate.reader.USB_PERMISSION";

    public interface Callback {
        void onUsbAttached(UsbDevice device);
        void onUsbDetached(UsbDevice device);
        void onPermissionGranted(UsbDevice device);
    }

    @Nullable
    private final Callback callback;

    public USBPermissionReceiver() {
        this(null);
    }

    public USBPermissionReceiver(@Nullable Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (device == null) {
            return;
        }
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            requestPermission(context, device);
            if (callback != null) {
                callback.onUsbAttached(device);
            }
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            if (callback != null) {
                callback.onUsbDetached(device);
            }
        } else if (ACTION_USB_PERMISSION.equals(action)) {
            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            if (granted && callback != null) {
                callback.onPermissionGranted(device);
            }
        }
    }

    public static void requestPermission(Context context, UsbDevice device) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            return;
        }
        PendingIntent intent = PendingIntent.getBroadcast(
                context,
                0,
                new Intent(ACTION_USB_PERMISSION).setPackage(context.getPackageName()),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        usbManager.requestPermission(device, intent);
    }
}
