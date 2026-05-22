package de.tu_darmstadt.seemoo.nfcgate.e2ee;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

public class PeerTrustStore {
    public static class Entry {
        private final byte[] publicKey;
        private final String label;

        public Entry(byte[] publicKey, String label) {
            this.publicKey = publicKey.clone();
            this.label = label;
        }

        public byte[] getPublicKey() {
            return publicKey.clone();
        }

        public String getLabel() {
            return label;
        }
    }

    private final Path storePath;

    public PeerTrustStore(Context context) {
        this(context.getApplicationContext().getFilesDir().toPath().resolve("peers.json"));
    }

    public PeerTrustStore(Path storePath) {
        this.storePath = storePath;
    }

    public synchronized boolean isTrusted(byte[] peerPubKey) {
        for (Entry entry : list()) {
            if (NoiseSupport.constantTimeEquals(entry.publicKey, peerPubKey)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void trust(byte[] peerPubKey, String label) {
        List<Entry> entries = new ArrayList<>(list());
        revoke(peerPubKey);
        entries.add(new Entry(peerPubKey, label));
        write(entries);
    }

    public synchronized void revoke(byte[] peerPubKey) {
        List<Entry> retained = new ArrayList<>();
        for (Entry entry : list()) {
            if (!NoiseSupport.constantTimeEquals(entry.publicKey, peerPubKey)) {
                retained.add(entry);
            }
        }
        write(retained);
    }

    public synchronized List<Entry> list() {
        if (!Files.exists(storePath)) {
            return Collections.emptyList();
        }
        try {
            JSONArray array = new JSONArray(Files.readString(storePath, StandardCharsets.UTF_8));
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                entries.add(new Entry(
                        Base64.getDecoder().decode(object.getString("public_key")),
                        object.optString("label", "peer")
                ));
            }
            return entries;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private void write(List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            array.put(new JSONObject()
                    .put("public_key", Base64.getEncoder().encodeToString(entry.publicKey))
                    .put("label", entry.label));
        }
        try {
            if (storePath.getParent() != null) {
                Files.createDirectories(storePath.getParent());
            }
            Files.writeString(storePath, array.toString(2), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist trusted peers", e);
        }
    }
}
