package de.tu_darmstadt.seemoo.nfcgate.e2ee;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;
import java.util.Base64;

public class NoiseIdentity {
    private static final String PREFS_NAME = "noise_identity";
    private static final String KEY_PUBLIC = "static_public";
    private static final String KEY_PRIVATE = "static_private";

    public interface Store {
        String get(String key);
        void put(String key, String value);
    }

    private final Store store;
    private byte[] staticPublicKey;
    private byte[] staticPrivateKey;

    public NoiseIdentity(Context context) {
        this(new SharedPreferencesStore(context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)));
    }

    public NoiseIdentity(Store store) {
        this.store = store;
        loadOrCreate();
    }

    public byte[] getStaticPublicKey() {
        return staticPublicKey.clone();
    }

    public byte[] getStaticPrivateKey() {
        return staticPrivateKey.clone();
    }

    public String getFingerprint() {
        return formatFingerprint(staticPublicKey);
    }

    public static String formatFingerprint(byte[] publicKey) {
        byte[] digest = NoiseSupport.sha256(publicKey);
        StringBuilder builder = new StringBuilder(digest.length * 3 - 1);
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) {
                builder.append(':');
            }
            int value = digest[i] & 0xFF;
            builder.append(Character.forDigit(value >>> 4, 16));
            builder.append(Character.forDigit(value & 0x0F, 16));
        }
        return builder.toString().toUpperCase();
    }

    private void loadOrCreate() {
        String publicValue = store.get(KEY_PUBLIC);
        String privateValue = store.get(KEY_PRIVATE);
        if (publicValue != null && privateValue != null) {
            staticPublicKey = Base64.getDecoder().decode(publicValue);
            staticPrivateKey = Base64.getDecoder().decode(privateValue);
            return;
        }

        byte[] seed = new byte[32];
        new SecureRandom().nextBytes(seed);
        staticPrivateKey = seed;
        staticPublicKey = NoiseSupport.derivePublicKey(seed);
        store.put(KEY_PUBLIC, Base64.getEncoder().encodeToString(staticPublicKey));
        store.put(KEY_PRIVATE, Base64.getEncoder().encodeToString(staticPrivateKey));
    }

    private static final class SharedPreferencesStore implements Store {
        private final SharedPreferences preferences;

        private SharedPreferencesStore(SharedPreferences preferences) {
            this.preferences = preferences;
        }

        @Override
        public String get(String key) {
            return preferences.getString(key, null);
        }

        @Override
        public void put(String key, String value) {
            preferences.edit().putString(key, value).apply();
        }
    }
}
