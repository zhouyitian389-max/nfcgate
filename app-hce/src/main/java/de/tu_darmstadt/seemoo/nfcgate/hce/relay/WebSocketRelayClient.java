package de.tu_darmstadt.seemoo.nfcgate.hce.relay;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketRelayClient {
    public interface Listener {
        void onApduResponse(byte[] response);
    }

    private static final String TAG = "HceRelayWsClient";
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();
    private final String url;
    private final String jwt;
    private final String sessionId;
    private final Listener listener;
    private WebSocket webSocket;
    private int reconnectAttempt;
    private final AtomicInteger nextCommandSeq = new AtomicInteger(1);
    private final AtomicInteger pendingSeq = new AtomicInteger(0);

    public WebSocketRelayClient(String url, String jwt, String sessionId, Listener listener) {
        this.url = url;
        this.jwt = jwt;
        this.sessionId = sessionId;
        this.listener = listener;
    }

    public synchronized void connect() {
        if (webSocket != null) {
            return;
        }
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (jwt != null && !jwt.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + jwt);
        }
        webSocket = httpClient.newWebSocket(requestBuilder.build(), new RelayWebSocketListener());
    }

    public synchronized void close() {
        if (webSocket != null) {
            webSocket.close(1000, "closed");
            webSocket = null;
        }
    }

    public synchronized boolean sendApduCommand(String apduHex) {
        if (webSocket == null) {
            return false;
        }
        try {
            int seq = nextCommandSeq.getAndIncrement();
            pendingSeq.set(seq);
            JSONObject payload = new JSONObject()
                    .put("type", "apdu_command")
                    .put("sessionId", sessionId)
                    .put("seq", seq)
                    .put("data", apduHex);
            return webSocket.send(payload.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Failed to encode apdu_command payload", e);
            return false;
        }
    }

    private synchronized void scheduleReconnect() {
        if (url == null || url.isEmpty()) {
            return;
        }
        reconnectAttempt++;
        long backoffMs = Math.min(30_000L, (long) Math.pow(2, Math.min(reconnectAttempt, 6)) * 1000L);
        new Thread(() -> {
            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            synchronized (WebSocketRelayClient.this) {
                webSocket = null;
            }
            connect();
        }, "hce-relay-reconnect").start();
    }

    private class RelayWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            reconnectAttempt = 0;
            try {
                JSONObject join = new JSONObject()
                        .put("type", "session_join")
                        .put("sessionId", sessionId)
                        .put("role", "hce");
                webSocket.send(join.toString());
            } catch (JSONException e) {
                Log.e(TAG, "Failed to send session_join", e);
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                JSONObject message = new JSONObject(text);
                if ("apdu_response".equals(message.optString("type"))) {
                    int seq = message.optInt("seq", 0);
                    if (seq != 0 && seq != pendingSeq.get()) {
                        Log.w(TAG, "Ignoring mismatched apdu_response seq=" + seq + " expected=" + pendingSeq.get());
                        return;
                    }
                    listener.onApduResponse(hexToBytes(message.optString("data")));
                    pendingSeq.set(0);
                }
            } catch (JSONException e) {
                Log.w(TAG, "Invalid relay message: " + text, e);
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            scheduleReconnect();
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Log.e(TAG, "WebSocket failure", t);
            scheduleReconnect();
        }
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null) {
            return new byte[0];
        }
        String normalized = hex.replaceAll("\\s+", "");
        if (normalized.length() % 2 != 0) {
            normalized = "0" + normalized;
        }
        byte[] out = new byte[normalized.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int idx = i * 2;
            out[i] = (byte) Integer.parseInt(normalized.substring(idx, idx + 2), 16);
        }
        return out;
    }
}
