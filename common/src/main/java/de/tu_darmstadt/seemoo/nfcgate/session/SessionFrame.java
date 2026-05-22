package de.tu_darmstadt.seemoo.nfcgate.session;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

import de.tu_darmstadt.seemoo.nfcgate.util.NfcComm;

public class SessionFrame {
    public enum Direction {
        READER,
        CARD;

        public static Direction fromBoolean(boolean fromCard) {
            return fromCard ? CARD : READER;
        }
    }

    private final Direction direction;
    private final boolean initial;
    private final long timestamp;
    private final byte[] data;

    public SessionFrame(Direction direction, boolean initial, long timestamp, byte[] data) {
        this.direction = direction;
        this.initial = initial;
        this.timestamp = timestamp;
        this.data = data != null ? data.clone() : new byte[0];
    }

    public static SessionFrame fromNfcComm(NfcComm comm) {
        return new SessionFrame(Direction.fromBoolean(comm.isCard()), comm.isInitial(), comm.getTimestamp(), comm.getData());
    }

    public NfcComm toNfcComm() {
        return new NfcComm(direction == Direction.CARD, initial, data, timestamp);
    }

    public Direction getDirection() {
        return direction;
    }

    public boolean isInitial() {
        return initial;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public byte[] getData() {
        return data.clone();
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("direction", direction.name().toLowerCase())
                .put("initial", initial)
                .put("timestamp", timestamp)
                .put("data", HexCodec.toHex(data));
    }

    public static SessionFrame fromJson(JSONObject object) throws JSONException {
        return new SessionFrame(
                Direction.valueOf(object.optString("direction", "reader").toUpperCase()),
                object.optBoolean("initial", false),
                object.optLong("timestamp", 0L),
                HexCodec.fromHex(object.optString("data", ""))
        );
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SessionFrame)) {
            return false;
        }
        SessionFrame frame = (SessionFrame) other;
        return direction == frame.direction
                && initial == frame.initial
                && timestamp == frame.timestamp
                && Arrays.equals(data, frame.data);
    }

    @Override
    public int hashCode() {
        return direction.hashCode() * 31 + Arrays.hashCode(data);
    }
}
