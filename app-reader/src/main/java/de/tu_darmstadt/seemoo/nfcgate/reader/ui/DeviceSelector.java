package de.tu_darmstadt.seemoo.nfcgate.reader.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.List;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCDevice;

public final class DeviceSelector {
    public interface DeviceSelectedCallback {
        void onDeviceSelected(NFCDevice device);
    }

    private DeviceSelector() {
    }

    public static void show(Context context, List<NFCDevice> devices, DeviceSelectedCallback callback) {
        List<String> names = new ArrayList<>();
        for (NFCDevice device : devices) {
            names.add(device.getName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, names);
        new AlertDialog.Builder(context)
                .setTitle("Select NFC Device")
                .setAdapter(adapter, (dialog, which) -> callback.onDeviceSelected(devices.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
