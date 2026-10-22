package de.eedesign.fahrtenbuch;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Eingabefelder einer Fahrt; verwendet auf der Startseite und beim Bearbeiten. */
public class TripForm {
    private final Context c;
    public final LinearLayout root;
    public final EditText start, end, kmStart, kmEnd;
    public final Combo startOrt, ziel, zuordnung, zwInput;
    private final TextView zwList;
    private final LinearLayout endSection, zwSection;
    private final List<String> zw = new ArrayList<>();
    private boolean silent;
    public Runnable onChange;

    public TripForm(Context c) {
        this.c = c;
        root = Ui.vbox(c);

        start = Ui.timeEdit(c);
        root.addView(Ui.label(c, "Startzeit"));
        root.addView(timeRow(start));

        kmStart = Ui.edit(c, InputType.TYPE_CLASS_NUMBER);
        root.addView(Ui.label(c, "Anfangs km."));
        root.addView(kmStart);

        startOrt = new Combo(c);
        root.addView(Ui.label(c, "Startort"));
        root.addView(startOrt);

        zwSection = Ui.vbox(c);
        zwSection.addView(Ui.label(c, "Zwischenziele"));
        zwList = Ui.text(c, "", 16);
        zwList.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), 0, Ui.dp(c, 4));
        zwSection.addView(zwList);
        zwInput = new Combo(c);
        zwInput.setHint("Zwischenziel");
        zwInput.setLayoutParams(Ui.weight(1));
        zwSection.addView(Ui.row(c, zwInput,
                Ui.small(c, "Hinzufügen", v -> addZw()),
                Ui.small(c, "Leeren", v -> {
                    if (zw.isEmpty()) return;
                    Ui.confirm(c, "Alle Zwischenziele dieser Fahrt entfernen?", "Entfernen", () -> {
                        zw.clear();
                        showZwList();
                        changed();
                    });
                })));
        root.addView(zwSection);

        endSection = Ui.vbox(c);
        end = Ui.timeEdit(c);
        endSection.addView(Ui.label(c, "Ankunftszeit"));
        endSection.addView(timeRow(end));
        kmEnd = Ui.edit(c, InputType.TYPE_CLASS_NUMBER);
        endSection.addView(Ui.label(c, "End km."));
        endSection.addView(kmEnd);
        ziel = new Combo(c);
        endSection.addView(Ui.label(c, "Zielort"));
        endSection.addView(ziel);
        zuordnung = new Combo(c);
        endSection.addView(Ui.label(c, "Zuordnung der Fahrt"));
        endSection.addView(zuordnung);
        root.addView(endSection);

        TextWatcher w = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            public void onTextChanged(CharSequence s, int a, int b, int d) {}
            public void afterTextChanged(Editable s) {
                changed();
            }
        };
        for (EditText e : new EditText[]{start, end, kmStart, kmEnd, startOrt, ziel, zuordnung, zwInput}) {
            e.addTextChangedListener(w);
        }
        showZwList();
    }

    private LinearLayout timeRow(EditText e) {
        e.setLayoutParams(Ui.weight(1));
        return Ui.row(c, e,
                Ui.small(c, "Jetzt", v -> e.setText(Dt.format(Dt.now()))),
                Ui.small(c, "Wählen", v -> Ui.pickDateTime(c, e)));
    }

    private void changed() {
        if (!silent && onChange != null) onChange.run();
    }

    private void addZw() {
        String s = zwInput.value();
        if (s.isEmpty()) return;
        zw.add(s);
        silent = true;
        zwInput.setValue("");
        silent = false;
        zwInput.dismissDropDown();
        showZwList();
        changed();
    }

    private void showZwList() {
        if (zw.isEmpty()) {
            zwList.setText("keine");
            zwList.setTextColor(0xFF888888);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < zw.size(); i++) {
                if (i > 0) sb.append('\n');
                sb.append(i + 1).append(". ").append(zw.get(i));
            }
            zwList.setText(sb);
            zwList.setTextColor(0xFF000000);
        }
    }

    public void setLists(List<String> places, List<String> assignments) {
        startOrt.setItems(places);
        ziel.setItems(places);
        zwInput.setItems(places);
        zuordnung.setItems(assignments);
    }

    public void showEnd(boolean b) {
        endSection.setVisibility(b ? View.VISIBLE : View.GONE);
    }

    public void showZw(boolean b) {
        zwSection.setVisibility(b ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------- Datenübergabe

    public void fromDraft(Draft d) {
        silent = true;
        start.setText(d.start);
        kmStart.setText(d.kmStart);
        startOrt.setValue(d.startOrt);
        end.setText(d.end);
        kmEnd.setText(d.kmEnd);
        ziel.setValue(d.ziel);
        zuordnung.setValue(d.zuordnung);
        zwInput.setValue(d.zwInput);
        zw.clear();
        zw.addAll(d.zwischen);
        showZwList();
        silent = false;
    }

    public void toDraft(Draft d) {
        d.start = start.getText().toString();
        d.kmStart = kmStart.getText().toString();
        d.startOrt = startOrt.getText().toString();
        d.end = end.getText().toString();
        d.kmEnd = kmEnd.getText().toString();
        d.ziel = ziel.getText().toString();
        d.zuordnung = zuordnung.getText().toString();
        d.zwInput = zwInput.getText().toString();
        d.zwischen = new ArrayList<>(zw);
    }

    public void fromTrip(Trip t) {
        Draft d = new Draft();
        d.start = Dt.format(t.start);
        d.end = Dt.format(t.end);
        d.kmStart = t.kmStart == null ? "" : String.valueOf(t.kmStart);
        d.kmEnd = t.kmEnd == null ? "" : String.valueOf(t.kmEnd);
        d.startOrt = t.startOrt;
        d.ziel = t.ziel;
        d.zuordnung = t.zuordnung;
        d.zwischen = new ArrayList<>(t.zwischen);
        fromDraft(d);
    }

    /**
     * Prüft die Eingaben und überträgt sie in die Fahrt.
     * full=false: nur Startangaben (Fahrt beginnen). Liefert Fehlertext oder null.
     */
    public String toTrip(Trip t, boolean full) {
        LocalDateTime now = Dt.now();
        LocalDateTime s = Dt.parseStrict(start.getText().toString());
        if (s == null) return "Startzeit ungültig. Format: JJJJ-MM-TT hh:mm, z. B. " + Dt.format(now);
        if (s.isAfter(now)) return "Die Startzeit liegt in der Zukunft.";
        Long ks = km(kmStart);
        if (ks == null) return "Anfangs km. fehlt oder ist keine ganze Zahl.";
        if (startOrt.value().isEmpty()) return "Bitte den Startort eingeben.";
        t.start = s;
        t.kmStart = ks;
        t.startOrt = startOrt.value();
        List<String> z = new ArrayList<>(zw);
        if (!zwInput.value().isEmpty()) z.add(zwInput.value());
        t.zwischen = z;
        if (!full) return null;

        LocalDateTime e = Dt.parseStrict(end.getText().toString());
        if (e == null) return "Ankunftszeit ungültig. Format: JJJJ-MM-TT hh:mm, z. B. " + Dt.format(now);
        if (e.isAfter(now)) return "Die Ankunftszeit liegt in der Zukunft.";
        if (e.isBefore(s)) return "Die Ankunftszeit liegt vor der Startzeit.";
        Long ke = km(kmEnd);
        if (ke == null) return "End km. fehlt oder ist keine ganze Zahl.";
        if (ke < ks) return "End km. ist kleiner als Anfangs km.";
        if (ziel.value().isEmpty()) return "Bitte den Zielort eingeben.";
        t.end = e;
        t.kmEnd = ke;
        t.ziel = ziel.value();
        t.zuordnung = zuordnung.value();
        return null;
    }

    private static Long km(EditText e) {
        String s = e.getText().toString().trim();
        if (!s.matches("\\d{1,9}")) return null;
        return Long.parseLong(s);
    }
}
