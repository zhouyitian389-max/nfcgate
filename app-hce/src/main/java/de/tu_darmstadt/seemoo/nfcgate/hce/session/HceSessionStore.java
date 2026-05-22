package de.tu_darmstadt.seemoo.nfcgate.hce.session;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import de.tu_darmstadt.seemoo.nfcgate.session.SessionDocument;
import de.tu_darmstadt.seemoo.nfcgate.session.SessionJsonCodec;

public class HceSessionStore {
    private static final String PREFS = "hce_sessions";
    private static final String KEY_ACTIVE = "active_session_id";

    private final SharedPreferences preferences;
    private final File sessionDir;

    public HceSessionStore(Context context) {
        Context appContext = context.getApplicationContext();
        this.preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.sessionDir = new File(appContext.getFilesDir(), "sessions");
        if (!sessionDir.exists()) {
            sessionDir.mkdirs();
        }
    }

    public synchronized void save(SessionDocument document) {
        File file = new File(sessionDir, document.getSessionId() + ".nfcg.json");
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(SessionJsonCodec.encode(document));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist session", e);
        }
    }

    public synchronized List<SessionDocument> list() {
        List<SessionDocument> sessions = new ArrayList<>();
        File[] files = sessionDir.listFiles((dir, name) -> name.endsWith(".nfcg.json"));
        if (files == null) {
            return sessions;
        }
        for (File file : files) {
            try {
                sessions.add(SessionJsonCodec.decode(Files.readString(file.toPath(), StandardCharsets.UTF_8)));
            } catch (Exception ignored) {
            }
        }
        return sessions;
    }

    public synchronized SessionDocument getActive() {
        String activeId = preferences.getString(KEY_ACTIVE, null);
        if (activeId == null) {
            return null;
        }
        return get(activeId);
    }

    public synchronized SessionDocument get(String sessionId) {
        File file = new File(sessionDir, sessionId + ".nfcg.json");
        if (!file.exists()) {
            return null;
        }
        try {
            return SessionJsonCodec.decode(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized void setActive(String sessionId) {
        preferences.edit().putString(KEY_ACTIVE, sessionId).apply();
    }

    public synchronized void delete(String sessionId) {
        File file = new File(sessionDir, sessionId + ".nfcg.json");
        if (file.exists()) {
            file.delete();
        }
        if (sessionId.equals(preferences.getString(KEY_ACTIVE, null))) {
            preferences.edit().remove(KEY_ACTIVE).apply();
        }
    }
}
