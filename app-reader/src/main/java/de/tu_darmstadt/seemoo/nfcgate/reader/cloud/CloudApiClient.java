package de.tu_darmstadt.seemoo.nfcgate.reader.cloud;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import de.tu_darmstadt.seemoo.nfcgate.reader.BuildConfig;
import de.tu_darmstadt.seemoo.nfcgate.reader.settings.SettingsManager;

public class CloudApiClient {
    private static final Object REFRESH_LOCK = new Object();
    public static class ApiException extends Exception {
        public final int code;
        public final long retryAfterSeconds;

        public ApiException(int code, String message, long retryAfterSeconds) {
            super(message);
            this.code = code;
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    public static class LoginResult {
        public final String token;
        public final long expiresAt;
        public final String accountId;
        public final String salt;

        public LoginResult(String token, long expiresAt, String accountId, String salt) {
            this.token = token;
            this.expiresAt = expiresAt;
            this.accountId = accountId;
            this.salt = salt;
        }
    }

    public static class DeviceItem {
        public final String name;
        public final String id;
        public final String lastActive;

        public DeviceItem(String name, String id, String lastActive) {
            this.name = name;
            this.id = id;
            this.lastActive = lastActive;
        }
    }

    public static class LogItem {
        public final long timestamp;
        public final String action;
        public final String details;

        public LogItem(long timestamp, String action, String details) {
            this.timestamp = timestamp;
            this.action = action;
            this.details = details;
        }
    }

    public static class Stats {
        public final int totalUsers;
        public final int yourCards;
        public final int todayActive;

        public Stats(int totalUsers, int yourCards, int todayActive) {
            this.totalUsers = totalUsers;
            this.yourCards = yourCards;
            this.todayActive = todayActive;
        }
    }

    private final Context appContext;

    public CloudApiClient(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public LoginResult login(String password) throws Exception {
        JSONObject req = new JSONObject().put("password", password);
        JSONObject json = request("POST", "/api/auth/login", req, null, true);
        String token = json.optString("token", "");
        long expiresIn = json.optLong("expires_in", 3600L);
        long expiresAt = System.currentTimeMillis() + expiresIn * 1000L;
        String salt = json.optString("salt", "");
        return new LoginResult(token, expiresAt, json.optString("account_id", ""), salt);
    }

    public void refreshIfNeeded() throws Exception {
        if (!SessionManager.isLoggedIn(appContext) || !SessionManager.isTokenExpiringSoon(appContext)) return;
        synchronized (REFRESH_LOCK) {
            if (!SessionManager.isLoggedIn(appContext) || !SessionManager.isTokenExpiringSoon(appContext)) return;
            JSONObject req = new JSONObject().put("token", SessionManager.getToken(appContext));
            JSONObject json = request("POST", "/api/auth/refresh", req, SessionManager.getToken(appContext), false);
            String token = json.optString("token", SessionManager.getToken(appContext));
            long expiresIn = json.optLong("expires_in", 3600L);
            SessionManager.updateToken(appContext, token, System.currentTimeMillis() + expiresIn * 1000L);
        }
    }

    public void registerDevice() throws Exception {
        ContentResolver resolver = appContext.getContentResolver();
        String androidId = Settings.Secure.getString(resolver, Settings.Secure.ANDROID_ID);
        JSONObject req = new JSONObject()
                .put("device_name", Build.MODEL)
                .put("device_id", androidId == null ? "unknown" : androidId);
        request("POST", "/api/devices/register", req, SessionManager.getToken(appContext), false);
    }

    public List<DeviceItem> listDevices() throws Exception {
        JSONObject json = request("GET", "/api/devices/list", null, SessionManager.getToken(appContext), false);
        JSONArray array = json.optJSONArray("devices");
        List<DeviceItem> items = new ArrayList<>();
        if (array == null) return items;
        for (int i = 0; i < array.length(); i++) {
            JSONObject d = array.optJSONObject(i);
            if (d == null) continue;
            items.add(new DeviceItem(d.optString("device_name"), d.optString("device_id"), d.optString("last_active")));
        }
        return items;
    }

    public void logoutDevice(String deviceId) throws Exception {
        JSONObject req = new JSONObject().put("device_id", deviceId);
        request("POST", "/api/devices/logout", req, SessionManager.getToken(appContext), false);
    }

    public List<LogItem> fetchLogs() throws Exception {
        JSONObject json = request("GET", "/api/logs", null, SessionManager.getToken(appContext), false);
        JSONArray arr = json.optJSONArray("logs");
        List<LogItem> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject l = arr.optJSONObject(i);
            if (l == null) continue;
            out.add(new LogItem(l.optLong("timestamp", System.currentTimeMillis()), l.optString("action"), l.optString("details")));
        }
        return out;
    }

    public Stats fetchStats() throws Exception {
        JSONObject json = request("GET", "/api/stats", null, SessionManager.getToken(appContext), false);
        return new Stats(json.optInt("total_users"), json.optInt("your_cards"), json.optInt("today_active"));
    }

    public JSONObject uploadCards(JSONArray cards) throws Exception {
        JSONObject body = new JSONObject().put("cards", cards);
        return request("POST", "/api/cards/upload", body, SessionManager.getToken(appContext), false);
    }

    public void deleteCard(String cardId) throws Exception {
        String encoded = java.net.URLEncoder.encode(cardId, StandardCharsets.UTF_8.name());
        request("DELETE", "/api/cards/" + encoded, null, SessionManager.getToken(appContext), false);
    }

    public void registerFcmToken(String fcmToken) throws Exception {
        JSONObject body = new JSONObject().put("fcm_token", fcmToken);
        request("POST", "/api/devices/fcm", body, SessionManager.getToken(appContext), false);
    }

    private JSONObject request(String method, String path, JSONObject requestJson, String bearerToken, boolean allowAuthFailure) throws Exception {
        refreshIfNeededIfRequired(path);
        SyncStatusTracker.setSyncing(true);
        int retries = 0;
        long backoffMs = 1000L;
        while (true) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(getBaseUrl() + path);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(12000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                if (bearerToken != null && !bearerToken.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
                }

                if (requestJson != null && !"GET".equals(method) && !"DELETE".equals(method)) {
                    conn.setDoOutput(true);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(requestJson.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }

                int code = conn.getResponseCode();
                String body = readBody(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());

                if (code == 401 && !allowAuthFailure) {
                    SessionManager.clear(appContext);
                    throw new ApiException(401, "unauthorized", 0);
                }

                if (code == 429) {
                    long retryAfter = parseRetryAfter(conn);
                    long cooldownUntil = System.currentTimeMillis() + retryAfter * 1000L;
                    SessionManager.setCooldown(appContext, cooldownUntil);
                    if (retries < 4) {
                        Thread.sleep(Math.max(backoffMs, retryAfter * 1000L));
                        retries++;
                        backoffMs *= 2L;
                        continue;
                    }
                    throw new ApiException(429, "too many attempts", retryAfter);
                }

                if (code < 200 || code >= 300) {
                    throw new ApiException(code, body == null || body.isEmpty() ? ("HTTP " + code) : body, 0);
                }

                SyncStatusTracker.markSuccess();
                if (body == null || body.trim().isEmpty()) return new JSONObject();
                return new JSONObject(body);
            } catch (ApiException ex) {
                SyncStatusTracker.markOffline();
                throw ex;
            } catch (Exception ex) {
                SyncStatusTracker.markOffline();
                if (retries < 2) {
                    Thread.sleep(backoffMs);
                    retries++;
                    backoffMs *= 2L;
                    continue;
                }
                throw ex;
            } finally {
                SyncStatusTracker.setSyncing(false);
                if (conn != null) conn.disconnect();
            }
        }
    }

    private void refreshIfNeededIfRequired(String path) throws Exception {
        if ("/api/auth/login".equals(path) || "/api/auth/refresh".equals(path)) {
            return;
        }
        if (SessionManager.isLoggedIn(appContext) && SessionManager.isTokenExpiringSoon(appContext)) {
            refreshIfNeeded();
        }
    }

    private String readBody(InputStream inputStream) throws Exception {
        if (inputStream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private long parseRetryAfter(HttpURLConnection conn) {
        String header = conn.getHeaderField("Retry-After");
        if (header == null) return 10L;
        try {
            return Math.max(1L, Long.parseLong(header.trim()));
        } catch (Exception ignored) {
            return 10L;
        }
    }

    private String getBaseUrl() {
        String raw = SettingsManager.getCloudBaseUrl(appContext);
        String normalized = raw == null ? "" : raw.trim();
        if (normalized.isEmpty()) {
            normalized = SettingsManager.DEFAULT_CLOUD_API_BASE;
        }
        if (!BuildConfig.DEBUG && normalized.startsWith("http://")) {
            normalized = "https://" + normalized.substring("http://".length());
        }
        if (normalized.endsWith("/")) return normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
