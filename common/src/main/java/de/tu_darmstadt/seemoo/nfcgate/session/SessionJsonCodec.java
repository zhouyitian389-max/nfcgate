package de.tu_darmstadt.seemoo.nfcgate.session;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public final class SessionJsonCodec {
    private SessionJsonCodec() {
    }

    public static byte[] encode(SessionDocument document) {
        try {
            return document.toJson().toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Failed to encode session", e);
        }
    }

    public static String encodeToString(SessionDocument document) {
        return new String(encode(document), StandardCharsets.UTF_8);
    }

    public static SessionDocument decode(byte[] data) {
        return decode(new String(data, StandardCharsets.UTF_8));
    }

    public static SessionDocument decode(String json) {
        try {
            return SessionDocument.fromJson(new JSONObject(json));
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid session JSON", e);
        }
    }
}
