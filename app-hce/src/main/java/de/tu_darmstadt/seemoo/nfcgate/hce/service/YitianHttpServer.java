package de.tu_darmstadt.seemoo.nfcgate.hce.service;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardDatabase;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardEntity;
import de.tu_darmstadt.seemoo.nfcgate.hce.util.CardSanitizer;
import fi.iki.elonen.NanoHTTPD;

/**
 * Simple HTTP server that accepts POST /api/cards with a JSON array of card objects
 * and stores them in the local Room database.
 */
public class YitianHttpServer extends NanoHTTPD {
    private static final String TAG = "YitianHttpServer";
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
        long now = System.currentTimeMillis();
        List<CardEntity> entities = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            CardEntity e = new CardEntity();
            e.pan = CardSanitizer.sanitizePan(o.optString("pan", ""));
            e.brand = CardSanitizer.clamp(o.optString("brand", "UNKNOWN"), 32);
            e.holder = CardSanitizer.clamp(o.optString("holder", ""), 64);
            e.expiry = CardSanitizer.clamp(o.optString("expiry", ""), 16);
            e.track2 = CardSanitizer.clamp(o.optString("track2", ""), 256);
            e.receivedAt = now + i;
            e.isSelected = false;
            entities.add(e);
        }
        if (entities.isEmpty()) {
            return 0;
        }
        db.runInTransaction(() -> db.cardDao().insertAll(entities));
        return entities.size();
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
