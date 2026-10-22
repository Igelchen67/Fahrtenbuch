package de.eedesign.fahrtenbuch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

/** App-interne Einstellungen: zuletzt gewählte Datei, zuletzt gewähltes Fahrzeug, Eingaben je Fahrzeug. */
public final class Store {
    private Store() {}

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("fahrtenbuch", Context.MODE_PRIVATE);
    }

    public static String lastUri(Context c) {
        return p(c).getString("uri", null);
    }

    public static void setLastUri(Context c, String uri) {
        p(c).edit().putString("uri", uri).commit();
    }

    public static String lastVehicle(Context c) {
        return p(c).getString("vehicle", null);
    }

    public static void setLastVehicle(Context c, String name) {
        p(c).edit().putString("vehicle", name).commit();
    }

    private static JSONObject drafts(Context c) {
        try {
            return new JSONObject(p(c).getString("drafts", "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static void saveDrafts(Context c, JSONObject o) {
        p(c).edit().putString("drafts", o.toString()).commit();
    }

    public static Draft draft(Context c, String vehicle) {
        JSONObject o = drafts(c).optJSONObject(vehicle);
        return o == null ? null : Draft.fromJson(o);
    }

    public static void putDraft(Context c, String vehicle, Draft d) {
        JSONObject o = drafts(c);
        try {
            o.put(vehicle, d.toJson());
        } catch (JSONException ignored) {
        }
        saveDrafts(c, o);
    }

    public static void removeDraft(Context c, String vehicle) {
        JSONObject o = drafts(c);
        o.remove(vehicle);
        saveDrafts(c, o);
    }

    public static void renameDraft(Context c, String oldName, String newName) {
        JSONObject o = drafts(c);
        JSONObject d = o.optJSONObject(oldName);
        o.remove(oldName);
        if (d != null) {
            try {
                o.put(newName, d);
            } catch (JSONException ignored) {
            }
        }
        saveDrafts(c, o);
        if (oldName.equals(lastVehicle(c))) setLastVehicle(c, newName);
    }
}
