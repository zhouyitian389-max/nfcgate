package de.tu_darmstadt.seemoo.nfcgate.reader.upload;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

import de.tu_darmstadt.seemoo.nfcgate.session.SessionDocument;
import de.tu_darmstadt.seemoo.nfcgate.session.SessionJsonCodec;

public class UploadClient {
    public interface Callback {
        void onSuccess(int statusCode, String responseBody);
        void onError(String message, Exception error);
    }

    public void upload(SessionDocument session, UploadConfig config, Callback callback) {
        upload(session, config.getBaseUrl(), config.getApiKey(), config.isAllowCleartext(), callback);
    }

    public void upload(SessionDocument session, String baseUrl, String apiKey, boolean allowCleartext, Callback callback) {
        new Thread(() -> doUpload(session, baseUrl, apiKey, allowCleartext, callback)).start();
    }

    protected void doUpload(SessionDocument session, String baseUrl, String apiKey, boolean allowCleartext, Callback callback) {
        try {
            URL url = new URL(normalize(baseUrl) + "/api/v1/sessions");
            if (!allowCleartext && !"https".equalsIgnoreCase(url.getProtocol())) {
                throw new IOException("Cleartext uploads are disabled");
            }
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            if (connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setHostnameVerifier((host, sslSession) -> true);
            }
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-NFCGate-Client", "reader/0.3.0");
            byte[] body = SessionJsonCodec.encode(session);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body);
            }
            int status = connection.getResponseCode();
            String response = readFully(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            postSuccess(callback, status, response);
        } catch (Exception e) {
            postError(callback, e.getMessage(), e);
        }
    }

    private void postSuccess(Callback callback, int statusCode, String responseBody) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(statusCode, responseBody));
    }

    private void postError(Callback callback, String message, Exception error) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onError(message, error));
    }

    protected String readFully(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    protected String normalize(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
