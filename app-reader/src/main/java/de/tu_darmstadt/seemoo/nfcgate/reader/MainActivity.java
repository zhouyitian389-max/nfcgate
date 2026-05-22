package de.tu_darmstadt.seemoo.nfcgate.reader;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCDevice;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCManager;
import de.tu_darmstadt.seemoo.nfcgate.reader.ui.DeviceSelector;
import de.tu_darmstadt.seemoo.nfcgate.reader.ui.DeviceStatus;

public class MainActivity extends AppCompatActivity {
    private NFCManager nfcManager;
    private NFCDevice selectedDevice;
    private TextView selectedDeviceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.app_name);

        nfcManager = new NFCManager(this);
        selectedDeviceView = findViewById(R.id.selected_device);
        Button selectDeviceButton = findViewById(R.id.select_device_button);
        Button startCaptureButton = findViewById(R.id.start_capture_button);

        selectedDeviceView.setText(DeviceStatus.toStatusText(selectedDevice));

        selectDeviceButton.setOnClickListener(v -> {
            List<NFCDevice> devices = nfcManager.getAvailableDevices();
            DeviceSelector.show(this, devices, device -> {
                selectedDevice = device;
                selectedDeviceView.setText(getString(R.string.using_device, device.getName()));
            });
        });

        startCaptureButton.setOnClickListener(v -> {
            nfcManager.startCapture(selectedDevice, event -> runOnUiThread(() ->
                    Toast.makeText(this, R.string.capture_started, Toast.LENGTH_SHORT).show()));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (nfcManager != null) {
            nfcManager.stopCapture();
        }
    }
}
