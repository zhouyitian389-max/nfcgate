package de.tu_darmstadt.seemoo.nfcgate.wear.model;

public class CaptureState {
    private final int sessionCount;
    private final long bytesCaptured;
    private final boolean active;
    private final boolean paused;

    public CaptureState(int sessionCount, long bytesCaptured, boolean active, boolean paused) {
        this.sessionCount = sessionCount;
        this.bytesCaptured = bytesCaptured;
        this.active = active;
        this.paused = paused;
    }

    public int getSessionCount() {
        return sessionCount;
    }

    public long getBytesCaptured() {
        return bytesCaptured;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isPaused() {
        return paused;
    }
}
