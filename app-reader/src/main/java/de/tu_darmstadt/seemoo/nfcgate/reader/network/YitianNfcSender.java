package de.tu_darmstadt.seemoo.nfcgate.reader.network;

import android.util.Log;

import de.tu_darmstadt.seemoo.nfcgate.reader.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Sends scanned card records to a remote YitianNFC instance over HTTP/HTTPS.
 * Endpoint: POST <host>/api/cards
 * Body: JSON array of {pan, brand, holder, expiry, track2}
 */
public class YitianNfcSender {
    private static final String TAG = "YitianNfcSender";
    public static final String DEFAULT_HOST = "192.168.1.100";
    public static final int DEFAULT_PORT = 8080;
    static final String USER_AGENT = "YitianRead/" + BuildConfig.VERSION_NAME;

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

    static final class SendResult {
        private final int responseCode;
        private final String errorMessage;
        private final IOException ioException;

        private SendResult(int responseCode, String errorMessage, IOException ioException) {
            this.responseCode = responseCode;
            this.errorMessage = errorMessage;
            this.ioException = ioException;
        }

        static SendResult success() {
            return new SendResult(HttpURLConnection.HTTP_OK, null, null);
        }

        static SendResult httpError(int responseCode) {
            return new SendResult(responseCode, "HTTP " + responseCode, null);
        }

        static SendResult ioError(IOException ioException) {
            String message = ioException != null && ioException.getMessage() != null
                    ? ioException.getMessage()
                    : "I/O error";
            return new SendResult(-1, message, ioException);
        }

        boolean isSuccess() {
            return responseCode >= 200 && responseCode < 300;
        }

        int getResponseCode() {
            return responseCode;
        }

        String getErrorMessage() {
            return errorMessage;
        }

        IOException getIoException() {
            return ioException;
        }
    }

    public static void send(final String host, final int port,
                            final List<CardData> cards, final Callback cb) {
        new Thread(() -> {
            SendResult result = sendOnce(host, port, cards);
            if (cb == null) {
                return;
            }
            if (result.isSuccess()) {
                cb.onSuccess(cards.size());
            } else {
                cb.onError(result.getErrorMessage());
            }
        }, "YitianNfcSender").start();
    }

    static SendResult sendOnce(final String host, final int port, final List<CardData> cards) {
        HttpURLConnection conn = null;
        try {
            URL url = buildUploadUrl(host, port);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent", USER_AGENT);
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
            byte[] body = arr.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                return SendResult.success();
            }
            return SendResult.httpError(code);
        } catch (IOException e) {
            Log.w(TAG, "send failed", e);
            return SendResult.ioError(e);
        } catch (Exception e) {
            Log.w(TAG, "send failed", e);
            return new SendResult(-1, e.getMessage() != null ? e.getMessage() : "Unknown error", null);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    static URL buildUploadUrl(String host, int port) throws IOException {
        String normalizedHost = host == null ? "" : host.trim();
        if (normalizedHost.isEmpty()) {
            throw new IllegalArgumentException("host must not be empty");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be in range 1-65535, got: " + port);
        }
        String baseUrl;
        if (normalizedHost.startsWith("http://") || normalizedHost.startsWith("https://")) {
            baseUrl = normalizedHost;
        } else {
            baseUrl = "http://" + normalizedHost + ":" + port;
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return new URL(baseUrl + "/api/cards");
    }
}
