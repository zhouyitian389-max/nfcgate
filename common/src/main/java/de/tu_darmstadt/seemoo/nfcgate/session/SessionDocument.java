package de.tu_darmstadt.seemoo.nfcgate.session;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SessionDocument {
    private final String sessionId;
    private final long createdAt;
    private final String source;
    private final List<SessionFrame> frames;

    public SessionDocument(String sessionId, long createdAt, String source, List<SessionFrame> frames) {
        this.sessionId = sessionId != null ? sessionId : UUID.randomUUID().toString();
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
        this.source = source != null ? source : "unknown";
        this.frames = Collections.unmodifiableList(new ArrayList<>(frames != null ? frames : Collections.emptyList()));
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getSource() {
        return source;
    }

    public List<SessionFrame> getFrames() {
        return frames;
    }

    public JSONObject toJson() throws JSONException {
        JSONArray array = new JSONArray();
        for (SessionFrame frame : frames) {
            array.put(frame.toJson());
        }
        return new JSONObject()
                .put("session_id", sessionId)
                .put("created_at", createdAt)
                .put("source", source)
                .put("frames", array);
    }

    public static SessionDocument fromJson(JSONObject object) throws JSONException {
        JSONArray framesArray = object.optJSONArray("frames");
        List<SessionFrame> frames = new ArrayList<>();
        if (framesArray != null) {
            for (int i = 0; i < framesArray.length(); i++) {
                frames.add(SessionFrame.fromJson(framesArray.getJSONObject(i)));
            }
        }
        return new SessionDocument(
                object.optString("session_id", null),
                object.optLong("created_at", 0L),
                object.optString("source", "unknown"),
                frames
        );
    }
}
