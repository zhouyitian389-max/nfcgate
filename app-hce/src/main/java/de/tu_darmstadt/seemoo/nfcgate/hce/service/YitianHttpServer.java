package de.tu_darmstadt.seemoo.nfcgate.hce.service;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;

import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardDatabase;
import de.tu_darmstadt.seemoo.nfcgate.hce.db.CardEntity;
import fi.iki.elonen.NanoHTTPD;

/**
 * Simple HTTP server that accepts POST /api/cards with a JSON array of card objects
 * and stores them in the local Room database.
 */
public class YitianHttpServer extends NanoHTTPD {
    private static final String TAG = "YitianHttpServer";
    private final Context appContext;
    private volatile int receivedCount = 0;

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
            return newFixedLengthResponse(Response.Status.OK, "application/json",
                    "{\"app\":\"YitianNFC\",\"status\":\"ok\",\"received\":" + receivedCount + "}");
        }

        if (Method.POST.equals(method) && "/api/cards".equals(uri)) {
            try {
                Map<String, String> files = new java.util.HashMap<>();
                session.parseBody(files);
                String body = files.get("postData");
                if (body == null || body.isEmpty()) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST,
                            "application/json", "{\"error\":\"empty body\"}");
                }
                int added = ingest(body);
                receivedCount += added;
                if (listener != null) listener.onCardsReceived(receivedCount, added);
                return newFixedLengthResponse(Response.Status.OK, "application/json",
                        "{\"status\":\"ok\",\"added\":" + added + "}");
            } catch (Exception e) {
                Log.e(TAG, "ingest failed", e);
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                        "application/json", "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND,
                "application/json", "{\"error\":\"not found\"}");
    }

    private int ingest(String body) throws Exception {
        JSONArray arr = new JSONArray(body);
        CardDatabase db = CardDatabase.getInstance(appContext);
        int added = 0;
        long now = System.currentTimeMillis();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            CardEntity e = new CardEntity();
            e.pan = o.optString("pan", "");
            e.brand = o.optString("brand", "UNKNOWN");
            e.holder = o.optString("holder", "");
            e.expiry = o.optString("expiry", "");
            e.track2 = o.optString("track2", "");
            e.receivedAt = now + i;
            e.isSelected = false;
            db.cardDao().insert(e);
            added++;
        }
        return added;
    }
}
