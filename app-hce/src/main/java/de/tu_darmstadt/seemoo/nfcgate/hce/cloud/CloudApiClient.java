package de.tu_darmstadt.seemoo.nfcgate.hce.cloud;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

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

import de.tu_darmstadt.seemoo.nfcgate.hce.SettingsManager;
import de.tu_darmstadt.seemoo.nfcgate.hce.BuildConfig;

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

    public static class CardItem {
        public final String pan;
        public final String brand;
        public final String holder;
        public final String expiry;
        public final String track2;
        public final String note;
        public final String serverId;
        public final boolean expired;

        public CardItem(String pan, String brand, String holder, String expiry, String track2, String note, String serverId, boolean expired) {
            this.pan = pan;
            this.brand = brand;
            this.holder = holder;
            this.expiry = expiry;
            this.track2 = track2;
            this.note = note;
            this.serverId = serverId;
            this.expired = expired;
        }
    }

    public static class DeviceItem {
        public final String name;
        public final String id;
        public final String lastActive;
        public DeviceItem(String name, String id, String lastActive) { this.name = name; this.id = id; this.lastActive = lastActive; }
    }

    public static class LogItem {
        public final long timestamp;
        public final String action;
        public final String details;
        public LogItem(long timestamp, String action, String details) { this.timestamp = timestamp; this.action = action; this.details = details; }
    }

    public static class Stats {
        public final int totalUsers;
        public final int yourCards;
        public final int todayActive;
        public Stats(int totalUsers, int yourCards, int todayActive) { this.totalUsers = totalUsers; this.yourCards = yourCards; this.todayActive = todayActive; }
    }

    private final Context appContext;

    public CloudApiClient(Context context) { this.appContext = context.getApplicationContext(); }

    public LoginResult login(String password) throws Exception {
        JSONObject req = new JSONObject().put("password", password);
        JSONObject json = request("POST", "/api/auth/login", req, null, true);
        long expiresIn = json.optLong("expires_in", 3600L);
        return new LoginResult(json.optString("token", ""), System.currentTimeMillis() + expiresIn * 1000L, json.optString("account_id", ""), json.optString("salt", ""));
    }

    public void refreshIfNeeded() throws Exception {
        if (!SessionManager.isLoggedIn(appContext) || !SessionManager.isTokenExpiringSoon(appContext)) return;
        synchronized (REFRESH_LOCK) {
            if (!SessionManager.isLoggedIn(appContext) || !SessionManager.isTokenExpiringSoon(appContext)) return;
            JSONObject req = new JSONObject().put("token", SessionManager.getToken(appContext));
            JSONObject json = request("POST", "/api/auth/refresh", req, SessionManager.getToken(appContext), false);
            long expiresIn = json.optLong("expires_in", 3600L);
            SessionManager.updateToken(appContext, json.optString("token", SessionManager.getToken(appContext)), System.currentTimeMillis() + expiresIn * 1000L);
        }
    }

    public void registerDevice() throws Exception {
        ContentResolver resolver = appContext.getContentResolver();
        String androidId = Settings.Secure.getString(resolver, Settings.Secure.ANDROID_ID);
        JSONObject req = new JSONObject().put("device_name", Build.MODEL).put("device_id", androidId == null ? "unknown" : androidId);
        request("POST", "/api/devices/register", req, SessionManager.getToken(appContext), false);
    }

    public List<CardItem> pullCards() throws Exception {
        JSONObject json = request("GET", "/api/cards/pull", null, SessionManager.getToken(appContext), false);
        JSONArray cards = json.optJSONArray("cards");
        List<CardItem> out = new ArrayList<>();
        if (cards == null) return out;
        String password = SessionManager.getPassword(appContext);
        String salt = SessionManager.getSalt(appContext);
        boolean hasPassword = password != null && !password.trim().isEmpty();
        String normalizedSalt = salt == null ? "" : salt.trim();
        if (hasPassword && (normalizedSalt.isEmpty() || "default".equals(normalizedSalt))) {
            throw new java.io.IOException("Salt missing - please re-login");
        }
        for (int i = 0; i < cards.length(); i++) {
            JSONObject c = cards.optJSONObject(i);
            if (c == null) continue;
            JSONObject card = c;
            String blob = c.optString("blob", "");
            if (!blob.isEmpty() && hasPassword) {
                try { card = new JSONObject(E2EEncryption.decrypt(blob, password, normalizedSalt)); }
                catch (Exception ignored) {
                    Log.w("CloudApiClient", "Failed to decrypt card blob, skipping", ignored);
                    continue;
                }
            }
            out.add(new CardItem(
                    card.optString("pan", ""),
                    card.optString("brand", "UNKNOWN"),
                    card.optString("holder", ""),
                    card.optString("expiry", ""),
                    card.optString("track2", ""),
                    card.optString("note", ""),
                    c.optString("id", c.optString("card_id", "")),
                    c.optBoolean("expired", false)
            ));
        }
        return out;
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
        request("POST", "/api/devices/logout", new JSONObject().put("device_id", deviceId), SessionManager.getToken(appContext), false);
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

    public void registerFcmToken(String fcmToken) throws Exception {
        request("POST", "/api/devices/fcm", new JSONObject().put("fcm_token", fcmToken), SessionManager.getToken(appContext), false);
    }

    public void requestDeleteCard(String serverCardId) throws Exception {
        String encoded = java.net.URLEncoder.encode(serverCardId, StandardCharsets.UTF_8.name());
        request("DELETE", "/api/cards/" + encoded, null, SessionManager.getToken(appContext), false);
    }

    private JSONObject request(String method, String path, JSONObject requestJson, String bearerToken, boolean allowAuthFailure) throws Exception {
        if (!"/api/auth/login".equals(path) && !"/api/auth/refresh".equals(path)) refreshIfNeeded();
        SyncStatusTracker.setSyncing(true);
        int retries = 0;
        long backoffMs = 1000L;
        while (true) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(baseUrl() + path);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(12000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                if (bearerToken != null && !bearerToken.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
                if (requestJson != null && !"GET".equals(method) && !"DELETE".equals(method)) {
                    conn.setDoOutput(true);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(requestJson.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }
                int code = conn.getResponseCode();
                String body = readBody(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
                if (code == 401 && !allowAuthFailure) {
                    SessionManager.logout(appContext);
                    throw new ApiException(401, "unauthorized", 0);
                }
                if (code == 429) {
                    long retryAfter = parseRetryAfter(conn);
                    SessionManager.setCooldown(appContext, System.currentTimeMillis() + retryAfter * 1000L);
                    if (retries < 4) {
                        Thread.sleep(Math.max(backoffMs, retryAfter * 1000L));
                        retries++;
                        backoffMs *= 2;
                        continue;
                    }
                    throw new ApiException(429, "too many attempts", retryAfter);
                }
                if (code < 200 || code >= 300) throw new ApiException(code, body == null || body.isEmpty() ? ("HTTP " + code) : body, 0);
                SyncStatusTracker.markSuccess();
                return body == null || body.trim().isEmpty() ? new JSONObject() : new JSONObject(body);
            } catch (ApiException ex) {
                SyncStatusTracker.markOffline();
                throw ex;
            } catch (Exception ex) {
                SyncStatusTracker.markOffline();
                if (retries < 2) {
                    Thread.sleep(backoffMs);
                    retries++;
                    backoffMs *= 2;
                    continue;
                }
                throw ex;
            } finally {
                SyncStatusTracker.setSyncing(false);
                if (conn != null) conn.disconnect();
            }
        }
    }

    private String baseUrl() {
        String raw = SettingsManager.getCloudBaseUrl(appContext);
        String normalized = raw == null ? "" : raw.trim();
        if (normalized.isEmpty()) {
            normalized = "https://api.yitian.shop";
        }
        if (!BuildConfig.DEBUG && normalized.startsWith("http://")) {
            normalized = "https://" + normalized.substring("http://".length());
        }
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
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
        try { return Math.max(1L, Long.parseLong(String.valueOf(conn.getHeaderField("Retry-After")))); }
        catch (Exception ignored) { return 10L; }
    }
}
