package de.tu_darmstadt.seemoo.nfcgate.hce.service;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardDatabase;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardEntity;
import fi.iki.elonen.NanoHTTPD;

/**
 * Simple HTTP server that accepts POST /api/cards with a JSON array of card objects
 * and stores them in the local Room database.
 */
public class YitianHttpServer extends NanoHTTPD {
    private static final String TAG = "YitianHttpServer";
    private static final Pattern PAN_PATTERN = Pattern.compile("^\\d{8,32}$");
    private final Context appContext;
    private final AtomicInteger receivedCount = new AtomicInteger(0);

    public interface ReceiveListener {
        void onCardsReceived(int total, int newOnes);
    }

    private ReceiveListener listener;

    public YitianHttpServer(Context context, int port) {
        super(port);
        this.appContext = context.getApplicationContext();
    }

    public void setListener(ReceiveListener l) {
        this.listener = l;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        if (Method.GET.equals(method) && ("/".equals(uri) || "/health".equals(uri))) {
            return jsonResponse(Response.Status.OK, "app", "YitianNFC", "status", "ok", "received", receivedCount.get());
        }

        if (Method.POST.equals(method) && "/api/cards".equals(uri)) {
            try {
                String contentType = session.getHeaders().get("content-type");
                if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
                    return jsonError(Response.Status.UNSUPPORTED_MEDIA_TYPE, "content-type must be application/json");
                }
                Map<String, String> files = new java.util.HashMap<>();
                session.parseBody(files);
                String body = files.get("postData");
                if (body == null || body.isEmpty()) {
                    return jsonError(Response.Status.BAD_REQUEST, "empty body");
                }
                int added = ingest(body);
                int total = receivedCount.addAndGet(added);
                if (listener != null) listener.onCardsReceived(total, added);
                return jsonResponse(Response.Status.OK, "status", "ok", "added", added);
            } catch (IllegalArgumentException e) {
                return jsonError(Response.Status.BAD_REQUEST, e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "ingest failed", e);
                return jsonError(Response.Status.INTERNAL_ERROR, e.getMessage() != null ? e.getMessage() : "ingest failed");
            }
        }

        return jsonError(Response.Status.NOT_FOUND, "not found");
    }

    private int ingest(String body) throws Exception {
        JSONArray arr = new JSONArray(body);
        CardDatabase db = CardDatabase.getInstance(appContext);
        int added = 0;
        long now = System.currentTimeMillis();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            CardEntity e = new CardEntity();
            e.pan = sanitizePan(o.optString("pan", ""));
            e.brand = clamp(o.optString("brand", "UNKNOWN"), 32);
            e.holder = clamp(o.optString("holder", ""), 64);
            e.expiry = clamp(o.optString("expiry", ""), 16);
            e.track2 = clamp(o.optString("track2", ""), 256);
            e.receivedAt = now + i;
            e.isSelected = false;
            db.cardDao().insert(e);
            added++;
        }
        return added;
    }

    private static String sanitizePan(String pan) {
        String normalized = pan == null ? "" : pan.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.length() > 32 || !PAN_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("invalid pan");
        }
        return normalized;
    }

    private static String clamp(String value, int maxLength) {
        String safe = value == null ? "" : value;
        if (safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, maxLength);
    }

    private Response jsonError(Response.Status status, String message) {
        return jsonResponse(status, "error", message == null ? "unknown error" : message);
    }

    private Response jsonResponse(Response.Status status, Object... kvPairs) {
        try {
            JSONObject response = new JSONObject();
            for (int i = 0; i + 1 < kvPairs.length; i += 2) {
                response.put(String.valueOf(kvPairs[i]), kvPairs[i + 1]);
            }
            return newFixedLengthResponse(status, "application/json", response.toString());
        } catch (Exception e) {
            return newFixedLengthResponse(status, "application/json", "{\"error\":\"json serialization failed\"}");
        }
    }
}
