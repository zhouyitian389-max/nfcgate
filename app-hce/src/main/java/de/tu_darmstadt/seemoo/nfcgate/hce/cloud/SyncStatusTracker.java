package de.tu_darmstadt.seemoo.nfcgate.hce.cloud;

public final class SyncStatusTracker {
    public enum State { CONNECTED, SYNCING, OFFLINE }

    private static volatile long lastSuccessAt;
    private static volatile boolean syncing;

    private SyncStatusTracker() {}

    public static void setSyncing(boolean value) { syncing = value; }
    public static void markSuccess() { lastSuccessAt = System.currentTimeMillis(); syncing = false; }
    public static void markOffline() { syncing = false; }
    public static State getState() {
        if (syncing) return State.SYNCING;
        return (System.currentTimeMillis() - lastSuccessAt) < 60_000L ? State.CONNECTED : State.OFFLINE;
    }
    public static long getLastSuccessAt() { return lastSuccessAt; }
}
