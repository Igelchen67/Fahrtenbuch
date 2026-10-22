package de.eedesign.fahrtenbuch;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Eine Fahrt; entspricht einer Zeile im Tabellenblatt des Fahrzeugs. */
public class Trip {
    public String vehicle = "";
    public LocalDateTime start;
    public LocalDateTime end;
    public Long kmStart;
    public Long kmEnd;
    public String startOrt = "";
    public List<String> zwischen = new ArrayList<>();
    public String ziel = "";
    public String zuordnung = "";

    public Trip copy() {
        Trip t = new Trip();
        t.vehicle = vehicle;
        t.start = start;
        t.end = end;
        t.kmStart = kmStart;
        t.kmEnd = kmEnd;
        t.startOrt = startOrt;
        t.zwischen = new ArrayList<>(zwischen);
        t.ziel = ziel;
        t.zuordnung = zuordnung;
        return t;
    }

    /** Schlüssel zum Wiederfinden einer Fahrt nach erneutem Einlesen der Datei. */
    public String key() {
        return Dt.format(start) + "|" + (kmStart == null ? "" : kmStart) + "|" + startOrt
                + "|" + Dt.format(end) + "|" + (kmEnd == null ? "" : kmEnd) + "|" + ziel;
    }
}
