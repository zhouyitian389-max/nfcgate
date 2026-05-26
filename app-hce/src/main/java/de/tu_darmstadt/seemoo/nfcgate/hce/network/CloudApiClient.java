package de.tu_darmstadt.seemoo.nfcgate.hce.network;

import android.content.Context;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import de.tu_darmstadt.seemoo.nfcgate.hce.BuildConfig;
import de.tu_darmstadt.seemoo.nfcgate.hce.auth.CloudSessionManager;

public class CloudApiClient {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    public static final String KEY_API_SERVER_URL = "api_server_url";
    public static final String DEFAULT_API_SERVER_URL = "https://api.yitian.shop";

    private final Context appContext;

    public CloudApiClient(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static final class LoginResult {
        public final String token;
        public final String accountId;

        LoginResult(String token, String accountId) {
            this.token = token;
            this.accountId = accountId;
        }
    }

    public static final class PulledCard {
        public final String id;
        public final String pan;
        public final String brand;
        public final String holder;
        public final String expiry;
        public final String track2;

        PulledCard(String id, String pan, String brand, String holder, String expiry, String track2) {
            this.id = id;
            this.pan = pan;
            this.brand = brand;
            this.holder = holder;
            this.expiry = expiry;
            this.track2 = track2;
        }
    }

    public static final class UploadCard {
        public final String pan;
        public final String brand;
        public final String holder;
        public final String expiry;
        public final String track2;

        public UploadCard(String pan, String brand, String holder, String expiry, String track2) {
            this.pan = pan;
            this.brand = brand;
            this.holder = holder;
            this.expiry = expiry;
            this.track2 = track2;
        }
    }

    public LoginResult login(String password) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("password", password);
        } catch (Exception e) {
            throw new IOException("failed to build login payload", e);
        }
        JSONObject response = request("POST", "/api/auth/login", body, false);
        String token = response.optString("token", "");
        String accountId = response.optString("account_id", "");
        if (token.isEmpty()) {
            throw new IOException("login response missing token");
        }
        return new LoginResult(token, accountId);
    }

    public List<PulledCard> pullCards() throws IOException {
        JSONObject response = request("GET", "/api/cards/pull", null, true);
        JSONArray arr = response.optJSONArray("cards");
        if (arr == null || arr.length() == 0) {
            return new ArrayList<>();
        }
        List<PulledCard> cards = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            cards.add(new PulledCard(
                    o.optString("id", ""),
                    o.optString("pan", ""),
                    o.optString("brand", "UNKNOWN"),
                    o.optString("holder", ""),
                    o.optString("expiry", ""),
                    o.optString("track2", "")
            ));
        }
        return cards;
    }

    public int uploadCards(List<UploadCard> cards) throws IOException {
        JSONObject body = new JSONObject();
        JSONArray arr = new JSONArray();
        try {
            for (UploadCard card : cards) {
                JSONObject obj = new JSONObject();
                obj.put("pan", card.pan == null ? "" : card.pan);
                obj.put("brand", card.brand == null ? "UNKNOWN" : card.brand);
                obj.put("holder", card.holder == null ? "" : card.holder);
                obj.put("expiry", card.expiry == null ? "" : card.expiry);
                obj.put("track2", card.track2 == null ? "" : card.track2);
                arr.put(obj);
            }
            body.put("cards", arr);
        } catch (Exception e) {
            throw new IOException("failed to build upload payload", e);
        }
        JSONObject response = request("POST", "/api/cards/upload", body, true);
        return response.optInt("added", cards.size());
    }

    public void ackCards(List<String> ids) throws IOException {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        JSONObject body = new JSONObject();
        JSONArray arr = new JSONArray();
        for (String id : ids) {
            if (id != null && !id.trim().isEmpty()) {
                arr.put(id.trim());
            }
        }
        try {
            body.put("card_ids", arr);
        } catch (Exception e) {
            throw new IOException("failed to build ack payload", e);
        }
        request("POST", "/api/cards/ack", body, true);
    }

    private JSONObject request(String method, String path, JSONObject body, boolean requireAuth)
            throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(baseUrl() + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            if (requireAuth) {
                String token = CloudSessionManager.getToken(appContext);
                if (token == null || token.trim().isEmpty()) {
                    throw new IOException("not logged in");
                }
                conn.setRequestProperty("Authorization", "Bearer " + token.trim());
            }

            if (body != null) {
                conn.setDoOutput(true);
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (BufferedOutputStream os = new BufferedOutputStream(conn.getOutputStream())) {
                    os.write(payload);
                }
            }

            int code = conn.getResponseCode();
            byte[] responseBytes;
            if (code >= 200 && code < 300) {
                responseBytes = readAll(conn.getInputStream());
            } else {
                responseBytes = readAll(conn.getErrorStream());
                String err = responseBytes.length == 0 ? ("HTTP " + code)
                        : new String(responseBytes, StandardCharsets.UTF_8);
                throw new IOException(err);
            }

            if (responseBytes.length == 0) {
                return new JSONObject();
            }
            return new JSONObject(new String(responseBytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("request failed", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static byte[] readAll(java.io.InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return new byte[0];
        }
        try (BufferedInputStream in = new BufferedInputStream(inputStream);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private String baseUrl() {
        String configured = PreferenceManager.getDefaultSharedPreferences(appContext)
                .getString(KEY_API_SERVER_URL, DEFAULT_API_SERVER_URL);
        String normalized = configured == null ? "" : configured.trim();
        if (!BuildConfig.DEBUG && normalized.startsWith("http://")) {
            normalized = "https://" + normalized.substring("http://".length());
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isEmpty() ? DEFAULT_API_SERVER_URL : normalized;
    }
}
