package de.tu_darmstadt.seemoo.nfcgate.wear;

import android.content.Context;

import de.tu_darmstadt.seemoo.nfcgate.wear.model.CaptureState;
import de.tu_darmstadt.seemoo.nfcgate.wear.model.WearMessage;

public class CaptureController {
    private final PhoneConnection phoneConnection;
    private CaptureState state = new CaptureState(0, 0L, false, false);

    public CaptureController(Context context) {
        this.phoneConnection = new PhoneConnection(context);
    }

    public CaptureState getState() {
        return state;
    }

    public void start() {
        state = new CaptureState(state.getSessionCount(), state.getBytesCaptured(), true, false);
        phoneConnection.sendControl(WearMessage.START);
        phoneConnection.publishStatus(state.getSessionCount(), state.getBytesCaptured(), true);
    }

    public void stop() {
        state = new CaptureState(state.getSessionCount(), state.getBytesCaptured(), false, false);
        phoneConnection.sendControl(WearMessage.STOP);
        phoneConnection.publishStatus(state.getSessionCount(), state.getBytesCaptured(), false);
    }

    public void pause() {
        state = new CaptureState(state.getSessionCount(), state.getBytesCaptured(), true, true);
        phoneConnection.sendControl(WearMessage.PAUSE);
    }
}
