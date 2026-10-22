package de.eedesign.fahrtenbuch;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Laufende Eingaben je Fahrzeug, inkl. offener Fahrt; wird sofort in den App-Einstellungen gesichert. */
public class Draft {
    public static final int NEW = 0, OPEN = 1, ENDING = 2;

    public int state = NEW;
    public boolean wasOpen;
    public boolean touched;
    public String start = "", kmStart = "", startOrt = "";
    public String end = "", kmEnd = "", ziel = "", zuordnung = "", zwInput = "";
    public List<String> zwischen = new ArrayList<>();

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("state", state);
            o.put("wasOpen", wasOpen);
            o.put("touched", touched);
            o.put("start", start);
            o.put("kmStart", kmStart);
            o.put("startOrt", startOrt);
            o.put("end", end);
            o.put("kmEnd", kmEnd);
            o.put("ziel", ziel);
            o.put("zuordnung", zuordnung);
            o.put("zwInput", zwInput);
            JSONArray a = new JSONArray();
            for (String s : zwischen) a.put(s);
            o.put("zwischen", a);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public static Draft fromJson(JSONObject o) {
        Draft d = new Draft();
        if (o == null) return d;
        d.state = o.optInt("state", NEW);
        d.wasOpen = o.optBoolean("wasOpen", false);
        d.touched = o.optBoolean("touched", false);
        d.start = o.optString("start", "");
        d.kmStart = o.optString("kmStart", "");
        d.startOrt = o.optString("startOrt", "");
        d.end = o.optString("end", "");
        d.kmEnd = o.optString("kmEnd", "");
        d.ziel = o.optString("ziel", "");
        d.zuordnung = o.optString("zuordnung", "");
        d.zwInput = o.optString("zwInput", "");
        JSONArray a = o.optJSONArray("zwischen");
        if (a != null) for (int i = 0; i < a.length(); i++) d.zwischen.add(a.optString(i, ""));
        return d;
    }

    public boolean isOpen() {
        return state == OPEN || state == ENDING;
    }
}
