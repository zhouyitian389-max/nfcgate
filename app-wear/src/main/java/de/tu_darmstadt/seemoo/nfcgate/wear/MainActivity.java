package de.tu_darmstadt.seemoo.nfcgate.wear;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import de.tu_darmstadt.seemoo.nfcgate.wear.model.CaptureState;

public class MainActivity extends AppCompatActivity {
    private CaptureController controller;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        controller = new CaptureController(this);
        statusView = findViewById(R.id.capture_status);
        Button startButton = findViewById(R.id.start_button);
        Button stopButton = findViewById(R.id.stop_button);
        Button pauseButton = findViewById(R.id.pause_button);
        startButton.setOnClickListener(v -> {
            controller.start();
            render(controller.getState());
        });
        stopButton.setOnClickListener(v -> {
            controller.stop();
            render(controller.getState());
        });
        pauseButton.setOnClickListener(v -> {
            controller.pause();
            render(controller.getState());
        });
        render(controller.getState());
    }

    private void render(CaptureState state) {
        String status = state.isActive() ? (state.isPaused() ? getString(R.string.status_paused) : getString(R.string.status_active)) : getString(R.string.status_idle);
        statusView.setText(getString(R.string.capture_status_template, status, state.getSessionCount(), state.getBytesCaptured()));
    }
}
