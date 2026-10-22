package de.eedesign.fahrtenbuch;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Inhalt der Excel-Datei: Fahrzeuge (Tabellenblätter) und Fahrten. */
public class Book {
    public final List<Vehicle> vehicles = new ArrayList<>();
    public final List<Trip> trips = new ArrayList<>();

    public static final Comparator<Trip> BY_START = (a, b) -> {
        if (a.start == null && b.start == null) return 0;
        if (a.start == null) return -1;
        if (b.start == null) return 1;
        return a.start.compareTo(b.start);
    };

    public Vehicle vehicle(String name) {
        for (Vehicle v : vehicles) if (v.name.equals(name)) return v;
        return null;
    }

    public List<Trip> tripsOf(String vehicle) {
        List<Trip> l = new ArrayList<>();
        for (Trip t : trips) if (vehicle == null || t.vehicle.equals(vehicle)) l.add(t);
        l.sort(BY_START);
        return l;
    }

    public Trip lastTrip(String vehicle) {
        List<Trip> l = tripsOf(vehicle);
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }

    public Trip findByKey(String vehicle, String key) {
        for (Trip t : trips) if (t.vehicle.equals(vehicle) && t.key().equals(key)) return t;
        return null;
    }

    public void sort() {
        trips.sort(BY_START);
    }

    /** Alle bisherigen Orte, alphabetisch, ohne Dubletten (Groß-/Kleinschreibung egal). */
    public List<String> places(List<String> extra) {
        List<String> raw = new ArrayList<>();
        for (Trip t : trips) {
            raw.add(t.startOrt);
            raw.addAll(t.zwischen);
            raw.add(t.ziel);
        }
        if (extra != null) raw.addAll(extra);
        return uniqueSorted(raw);
    }

    public List<String> assignments(List<String> extra) {
        List<String> raw = new ArrayList<>();
        for (Trip t : trips) raw.add(t.zuordnung);
        if (extra != null) raw.addAll(extra);
        return uniqueSorted(raw);
    }

    public static List<String> uniqueSorted(List<String> raw) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String s : raw) {
            if (s == null) continue;
            s = s.trim();
            if (s.isEmpty()) continue;
            String k = s.toLowerCase(Locale.GERMAN);
            if (!m.containsKey(k)) m.put(k, s);
        }
        List<String> l = new ArrayList<>(m.values());
        Collator c = Collator.getInstance(Locale.GERMAN);
        c.setStrength(Collator.SECONDARY);
        l.sort(c);
        return l;
    }

    /** Prüft einen Fahrzeugnamen; liefert Fehlertext oder null. */
    public String checkVehicleName(String name, Vehicle self) {
        if (name == null || name.trim().isEmpty()) return "Bitte einen Namen eingeben.";
        if (!name.equals(name.trim())) return "Der Name darf nicht mit Leerzeichen beginnen oder enden.";
        if (name.length() > 31) return "Der Name darf höchstens 31 Zeichen lang sein (Excel-Grenze).";
        if (name.matches(".*[\\[\\]:*?/\\\\].*")) return "Die Zeichen [ ] : * ? / \\ sind in Excel-Blattnamen nicht erlaubt.";
        if (name.startsWith("'") || name.endsWith("'")) return "Der Name darf nicht mit ' beginnen oder enden.";
        for (Vehicle v : vehicles) {
            if (v != self && v.name.equalsIgnoreCase(name)) return "Ein Fahrzeug mit diesem Namen existiert bereits.";
        }
        return null;
    }
}
