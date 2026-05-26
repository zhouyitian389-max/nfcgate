package de.tu_darmstadt.seemoo.nfcgate.reader.relay;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketRelayClient {
    public interface Listener {
        void onApduCommand(String sessionId, byte[] command, int seq);
        void onSessionEnded();
    }

    private static final String TAG = "ReaderRelayWsClient";
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();
    private final String url;
    private final String jwt;
    private final String sessionId;
    private final Listener listener;
    private WebSocket webSocket;
    private int reconnectAttempt;

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
        Request.Builder builder = new Request.Builder().url(url);
        if (jwt != null && !jwt.isEmpty()) {
            builder.header("Authorization", "Bearer " + jwt);
        }
        webSocket = httpClient.newWebSocket(builder.build(), new RelayWebSocketListener());
    }

    public synchronized void close() {
        if (webSocket != null) {
            webSocket.close(1000, "closed");
            webSocket = null;
        }
    }

    public synchronized boolean sendApduResponse(String currentSessionId, byte[] apduResponse, int seq) {
        if (webSocket == null) {
            return false;
        }
        try {
            JSONObject payload = new JSONObject()
                    .put("type", "apdu_response")
                    .put("sessionId", currentSessionId)
                    .put("seq", seq)
                    .put("data", bytesToHex(apduResponse));
            return webSocket.send(payload.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Failed to send apdu_response", e);
            return false;
        }
    }

    private synchronized void scheduleReconnect() {
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
        }, "reader-relay-reconnect").start();
    }

    private class RelayWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            reconnectAttempt = 0;
            try {
                JSONObject join = new JSONObject()
                        .put("type", "session_join")
                        .put("sessionId", sessionId)
                        .put("role", "reader");
                webSocket.send(join.toString());
            } catch (JSONException e) {
                Log.e(TAG, "Failed to send session_join", e);
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                JSONObject payload = new JSONObject(text);
                String type = payload.optString("type");
                if ("apdu_command".equals(type)) {
                    listener.onApduCommand(payload.optString("sessionId", sessionId), hexToBytes(payload.optString("data")), payload.optInt("seq", 0));
                } else if ("session_end".equals(type)) {
                    listener.onSessionEnded();
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

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
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
