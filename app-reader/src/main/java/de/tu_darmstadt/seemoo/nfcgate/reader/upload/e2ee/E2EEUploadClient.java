package de.tu_darmstadt.seemoo.nfcgate.reader.upload.e2ee;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import de.tu_darmstadt.seemoo.nfcgate.e2ee.NoiseIdentity;
import de.tu_darmstadt.seemoo.nfcgate.e2ee.NoiseSession;
import de.tu_darmstadt.seemoo.nfcgate.e2ee.PeerTrustStore;
import de.tu_darmstadt.seemoo.nfcgate.reader.upload.UploadClient;
import de.tu_darmstadt.seemoo.nfcgate.reader.upload.UploadConfig;
import de.tu_darmstadt.seemoo.nfcgate.session.SessionDocument;
import de.tu_darmstadt.seemoo.nfcgate.session.SessionJsonCodec;

public class E2EEUploadClient extends UploadClient {
    public interface TrustCallback {
        boolean onFirstContact(String fingerprint, byte[] publicKey);
    }

    private final NoiseIdentity identity;
    private final PeerTrustStore trustStore;

    public E2EEUploadClient(Context context) {
        this.identity = new NoiseIdentity(context);
        this.trustStore = new PeerTrustStore(context);
    }

    public void upload(SessionDocument session, UploadConfig config, TrustCallback trustCallback, Callback callback) {
        new Thread(() -> doEncryptedUpload(session, config, trustCallback, callback)).start();
    }

    private void doEncryptedUpload(SessionDocument session, UploadConfig config, TrustCallback trustCallback, Callback callback) {
        try {
            String sessionId = UUID.randomUUID().toString();
            NoiseSession noise = NoiseSession.initiator(identity);
            byte[] msg1 = postNoise(config, sessionId, noise.handshakeMessage1());
            noise.readHandshakeMessage2(msg1);
            byte[] remoteKey = noise.getRemoteStaticPublicKey();
            if (remoteKey == null) {
                throw new IllegalStateException("Missing peer key in Noise handshake");
            }
            if (!trustStore.isTrusted(remoteKey)) {
                String fingerprint = fingerprint(remoteKey);
                if (trustCallback == null || !trustCallback.onFirstContact(fingerprint, remoteKey)) {
                    throw new IllegalStateException("Peer trust was rejected");
                }
                trustStore.trust(remoteKey, fingerprint);
            }
            postNoise(config, sessionId, noise.handshakeMessage3());
            byte[] ciphertext = noise.transportSend(SessionJsonCodec.encode(session));
            URL url = new URL(normalize(config.getBaseUrl()) + "/api/v1/noise/sessions");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            connection.setRequestProperty("Content-Type", "application/json");
            JSONObject body = new JSONObject()
                    .put("session", sessionId)
                    .put("ciphertext", Base64.getEncoder().encodeToString(ciphertext));
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            String response = readAll(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status >= 400) {
                callback.onError("Encrypted upload failed with status " + status, null);
            } else {
                callback.onSuccess(status, response);
            }
        } catch (Exception e) {
            callback.onError(e.getMessage(), e);
        }
    }

    private byte[] postNoise(UploadConfig config, String sessionId, byte[] message) throws Exception {
        URL url = new URL(normalize(config.getBaseUrl()) + "/api/v1/noise/handshake");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
        connection.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject()
                .put("session", sessionId)
                .put("msg", Base64.getEncoder().encodeToString(message));
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        String response = readAll(connection.getInputStream());
        return Base64.getDecoder().decode(new JSONObject(response).optString("msg", ""));
    }

    private String readAll(InputStream stream) throws Exception {
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

    private String fingerprint(byte[] publicKey) {
        return NoiseIdentity.formatFingerprint(publicKey);
    }
}
