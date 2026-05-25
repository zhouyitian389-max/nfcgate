package de.tu_darmstadt.seemoo.nfcgate.reader.cloud;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.tu_darmstadt.seemoo.nfcgate.reader.settings.SettingsManager;

public class CloudEventSource {
    public interface EventListener {
        void onNewCards();
        void onLogout();
        void onError(Exception e);
    }

    private final Context appContext;
    private final EventListener listener;
    private final Object lock = new Object();

    private volatile ExecutorService executor;
    private volatile HttpURLConnection connection;
    private volatile boolean running;
    private volatile boolean connected;

    public CloudEventSource(Context context, EventListener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        synchronized (lock) {
            if (running) return;
            running = true;
            connected = false;
            executor = Executors.newSingleThreadExecutor();
            executor.execute(this::runLoop);
        }
    }

    public void stop() {
        ExecutorService toShutdown;
        synchronized (lock) {
            running = false;
            connected = false;
            disconnect();
            toShutdown = executor;
            executor = null;
        }
        if (toShutdown != null) toShutdown.shutdownNow();
    }

    public boolean isConnected() {
        return connected;
    }

    static String extractEventType(String payload) throws Exception {
        JSONObject json = new JSONObject(payload);
        String eventType = json.optString("type");
        if (eventType.isEmpty()) eventType = json.optString("event");
        if (eventType.isEmpty()) eventType = json.optString("name");
        if (eventType.isEmpty() && json.optBoolean("new_cards", false)) eventType = "new_cards";
        if (eventType.isEmpty() && json.optBoolean("logout", false)) eventType = "logout";
        return eventType;
    }

    private void runLoop() {
        long backoffMs = 1000L;
        while (running) {
            try {
                streamEvents();
                if (!running) break;
                backoffMs = 1000L;
            } catch (UnauthorizedException ex) {
                connected = false;
                running = false;
                SyncStatusTracker.markOffline();
                SessionManager.clear(appContext);
                listener.onLogout();
            } catch (SocketTimeoutException ex) {
                connected = false;
                SyncStatusTracker.markOffline();
                backoffMs = 1000L;
                if (running) listener.onError(ex);
            } catch (IOException ex) {
                connected = false;
                SyncStatusTracker.markOffline();
                backoffMs = Math.min(backoffMs * 2L, 30_000L);
                if (running) listener.onError(ex);
            } catch (Exception ex) {
                connected = false;
                SyncStatusTracker.markOffline();
                backoffMs = Math.min(backoffMs * 2L, 30_000L);
                if (running) listener.onError(ex);
            } finally {
                disconnect();
            }
            if (!running) break;
            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        connected = false;
    }

    private void streamEvents() throws Exception {
        HttpURLConnection conn = openConnection();
        connection = conn;
        int code = conn.getResponseCode();
        if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            throw new UnauthorizedException();
        }
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("SSE HTTP " + code);
        }

        connected = true;
        SyncStatusTracker.markSuccess();

        StringBuilder payload = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    dispatchPayload(payload);
                    payload.setLength(0);
                    continue;
                }
                if (line.startsWith("data:")) {
                    String value = line.substring(5).trim();
                    if (payload.length() > 0) payload.append('\n');
                    payload.append(value);
                }
            }
            dispatchPayload(payload);
        } finally {
            connected = false;
        }
    }

    private void dispatchPayload(StringBuilder payload) throws Exception {
        if (payload.length() == 0) return;
        SyncStatusTracker.markSuccess();
        String eventType = extractEventType(payload.toString());
        if ("new_cards".equals(eventType)) {
            listener.onNewCards();
        } else if ("logout".equals(eventType)) {
            running = false;
            connected = false;
            SessionManager.clear(appContext);
            listener.onLogout();
            disconnect();
        }
    }

    private HttpURLConnection openConnection() throws Exception {
        String token = SessionManager.getToken(appContext);
        if (token == null || token.isEmpty()) throw new IllegalStateException("Missing cloud token");
        URL url = new URL(baseUrl() + "/api/events/stream");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(90_000);
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        return conn;
    }

    private String baseUrl() {
        String raw = SettingsManager.getCloudBaseUrl(appContext);
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }

    private void disconnect() {
        HttpURLConnection conn = connection;
        connection = null;
        if (conn != null) conn.disconnect();
    }

    private static final class UnauthorizedException extends Exception {
    }
}
