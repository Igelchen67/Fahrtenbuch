package de.eedesign.fahrtenbuch;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;

/** Datums- und Zeitfunktionen; Format JJJJ-MM-TT hh:mm. */
public final class Dt {
    private Dt() {}

    public static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter FMT_SEC =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter FMT_DE =
            DateTimeFormatter.ofPattern("d.M.uuuu H:mm").withResolverStyle(ResolverStyle.STRICT);
    private static final LocalDateTime EXCEL_EPOCH = LocalDateTime.of(1899, 12, 30, 0, 0);

    public static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
    }

    public static String format(LocalDateTime t) {
        return t == null ? "" : FMT.format(t);
    }

    /** Strenge Prüfung der Eingabe im Format JJJJ-MM-TT hh:mm; null bei ungültigem Wert. */
    public static LocalDateTime parseStrict(String s) {
        if (s == null) return null;
        s = s.trim();
        if (!s.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")) return null;
        try {
            return LocalDateTime.parse(s, FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Tolerantes Einlesen von Texten aus Excel. */
    public static LocalDateTime parseLenient(String s) {
        if (s == null) return null;
        s = s.trim().replace('T', ' ');
        LocalDateTime t = parseStrict(s);
        if (t != null) return t;
        for (DateTimeFormatter f : new DateTimeFormatter[]{FMT_SEC, FMT_DE}) {
            try {
                return LocalDateTime.parse(s, f).truncatedTo(ChronoUnit.MINUTES);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    public static double toSerial(LocalDateTime t) {
        long minutes = Duration.between(EXCEL_EPOCH, t).toMinutes();
        return minutes / 1440.0;
    }

    public static LocalDateTime fromSerial(double serial) {
        long minutes = Math.round(serial * 1440.0);
        return EXCEL_EPOCH.plusMinutes(minutes);
    }
}
