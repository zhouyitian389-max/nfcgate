package de.tu_darmstadt.seemoo.nfcgate.hce.server;

import android.content.Context;

import org.json.JSONObject;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import de.tu_darmstadt.seemoo.nfcgate.e2ee.NoiseIdentity;
import de.tu_darmstadt.seemoo.nfcgate.e2ee.NoiseSession;
import de.tu_darmstadt.seemoo.nfcgate.hce.session.HceSessionStore;
import de.tu_darmstadt.seemoo.nfcgate.session.SessionDocument;
import de.tu_darmstadt.seemoo.nfcgate.session.SessionJsonCodec;

public class ReceiveServer {
    public static class Request {
        public final String path;
        public final String authorization;
        public final byte[] body;

        public Request(String path, String authorization, byte[] body) {
            this.path = path;
            this.authorization = authorization;
            this.body = body;
        }
    }

    public static class Response {
        public final int status;
        public final String body;

        public Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private final HceSessionStore sessionStore;
    private final TokenManager tokenManager;
    private final NoiseIdentity identity;
    private final Map<String, NoiseSession> noiseSessions = new HashMap<>();
    private boolean requireE2ee;

    public ReceiveServer(Context context) {
        this.sessionStore = new HceSessionStore(context);
        this.tokenManager = new TokenManager(context);
        this.identity = new NoiseIdentity(context);
    }

    public void setRequireE2ee(boolean requireE2ee) {
        this.requireE2ee = requireE2ee;
    }

    public String getFingerprint() {
        return identity.getFingerprint();
    }

    public Response handle(Request request) {
        if (!isAuthorized(request.authorization)) {
            return new Response(401, "Unauthorized");
        }
        try {
            if ("/api/v1/sessions".equals(request.path)) {
                if (requireE2ee) {
                    return new Response(403, "Plaintext upload rejected: E2EE required");
                }
                SessionDocument document = SessionJsonCodec.decode(request.body);
                sessionStore.save(document);
                sessionStore.setActive(document.getSessionId());
                return new Response(201, new JSONObject().put("session_id", document.getSessionId()).toString());
            }
            if ("/api/v1/noise/handshake".equals(request.path)) {
                JSONObject json = new JSONObject(new String(request.body));
                String session = json.getString("session");
                byte[] msg = Base64.getDecoder().decode(json.getString("msg"));
                NoiseSession noise = noiseSessions.get(session);
                if (noise == null) {
                    noise = NoiseSession.responder(identity);
                    noise.readHandshakeMessage1(msg);
                    noiseSessions.put(session, noise);
                    return new Response(200, new JSONObject().put("msg", Base64.getEncoder().encodeToString(noise.handshakeMessage2())).toString());
                }
                noise.readHandshakeMessage3(msg);
                return new Response(200, new JSONObject().put("msg", "").toString());
            }
            if ("/api/v1/noise/sessions".equals(request.path)) {
                JSONObject json = new JSONObject(new String(request.body));
                String session = json.getString("session");
                NoiseSession noise = noiseSessions.remove(session);
                if (noise == null) {
                    return new Response(404, "Noise session not found");
                }
                byte[] plaintext = noise.transportReceive(Base64.getDecoder().decode(json.getString("ciphertext")));
                SessionDocument document = SessionJsonCodec.decode(plaintext);
                sessionStore.save(document);
                sessionStore.setActive(document.getSessionId());
                return new Response(201, new JSONObject().put("session_id", document.getSessionId()).toString());
            }
            return new Response(404, "Not found");
        } catch (Exception e) {
            return new Response(400, e.getMessage());
        }
    }

    private boolean isAuthorized(String authorization) {
        return authorization != null && authorization.equals("Bearer " + tokenManager.getToken());
    }
}
