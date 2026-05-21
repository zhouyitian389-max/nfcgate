package de.tu_darmstadt.seemoo.nfcgate.reader.wear;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

public final class ReaderWearStateStore {
    private static final String PREFS = "wear_state";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_PAUSED = "paused";
    private static final String KEY_COUNT = "count";
    private static final String KEY_BYTES = "bytes";

    private ReaderWearStateStore() {}

    public static void updateFromCommand(Context context, String type) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        switch (type) {
            case "start":
                editor.putBoolean(KEY_ACTIVE, true).putBoolean(KEY_PAUSED, false);
                break;
            case "stop":
                editor.putBoolean(KEY_ACTIVE, false).putBoolean(KEY_PAUSED, false);
                break;
            case "pause":
                editor.putBoolean(KEY_ACTIVE, false).putBoolean(KEY_PAUSED, true);
                break;
            case "resume":
                editor.putBoolean(KEY_ACTIVE, true).putBoolean(KEY_PAUSED, false);
                break;
            default:
                break;
        }
        editor.apply();
    }

    public static String toSessionJson(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONObject json = new JSONObject();
        try {
            json.put("type", "session");
            json.put("count", prefs.getInt(KEY_COUNT, 0));
            json.put("bytes", prefs.getLong(KEY_BYTES, 0));
            json.put("active", prefs.getBoolean(KEY_ACTIVE, false));
            json.put("paused", prefs.getBoolean(KEY_PAUSED, false));
        } catch (JSONException ignored) {
        }
        return json.toString();
    }
}
