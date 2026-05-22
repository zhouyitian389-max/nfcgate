package de.tu_darmstadt.seemoo.nfcgate.hce.hce;

import java.util.List;

import de.tu_darmstadt.seemoo.nfcgate.session.SessionDocument;
import de.tu_darmstadt.seemoo.nfcgate.session.SessionFrame;

public class ReplayEngine {
    private final SessionDocument session;
    private int position;

    public ReplayEngine(SessionDocument session) {
        this.session = session;
    }

    public synchronized byte[] nextResponse(byte[] commandApdu) {
        if (session == null) {
            return new byte[] {(byte) 0x6A, (byte) 0x82};
        }
        List<SessionFrame> frames = session.getFrames();
        for (int i = position; i < frames.size() - 1; i++) {
            SessionFrame request = frames.get(i);
            SessionFrame response = frames.get(i + 1);
            if (request.getDirection() == SessionFrame.Direction.READER
                    && response.getDirection() == SessionFrame.Direction.CARD
                    && matches(request.getData(), commandApdu)) {
                position = i + 2;
                return response.getData();
            }
        }
        return new byte[] {(byte) 0x6A, (byte) 0x82};
    }

    private boolean matches(byte[] expected, byte[] actual) {
        if (expected.length != actual.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                return false;
            }
        }
        return true;
    }
}
