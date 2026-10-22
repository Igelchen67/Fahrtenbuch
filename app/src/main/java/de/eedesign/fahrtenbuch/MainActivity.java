package de.eedesign.fahrtenbuch;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Startseite: Datei, Fahrzeugauswahl und Erfassung der aktuellen Fahrt. */
public class MainActivity extends Activity {
    private static final int REQ_NEW = 1, REQ_OPEN = 2;
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private TextView fileText, errorText, hint, status;
    private LinearLayout vehicleBox, tripBox;
    private Spinner spinner;
    private VehicleAdapter adapter;
    private TripForm form;
    private Button bBegin, bEnd, bSave, bBack, bDiscard, bReset;
    private String current;
    private Draft draft;
    private boolean ignoreSelection;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int pad = Ui.dp(this, 16);
        LinearLayout root = Ui.vbox(this);
        root.setPadding(pad, pad / 2, pad, pad * 2);

        // Datei
        fileText = Ui.text(this, "", 14);
        fileText.setLayoutParams(Ui.weight(1));
        root.addView(Ui.label(this, "Excel-Datei"));
        root.addView(Ui.row(this, fileText,
                Ui.small(this, "Neu", v -> createFile()),
                Ui.small(this, "Öffnen", v -> openFile())));
        errorText = Ui.text(this, "", 14);
        errorText.setTextColor(0xFFC62828);
        root.addView(errorText);
        hint = Ui.text(this, "", 15);
        hint.setPadding(0, pad / 2, 0, 0);
        root.addView(hint);

        // Fahrzeug
        vehicleBox = Ui.vbox(this);
        vehicleBox.addView(Ui.label(this, "Fahrzeug"));
        spinner = new Spinner(this);
        spinner.setLayoutParams(Ui.weight(1));
        vehicleBox.addView(Ui.row(this, spinner));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (ignoreSelection || adapter == null) return;
                Vehicle veh = adapter.getItem(pos);
                if (veh != null && !veh.name.equals(current)) {
                    Store.setLastVehicle(MainActivity.this, veh.name);
                    showVehicle(veh.name);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {}
        });
        root.addView(vehicleBox);

        root.addView(Ui.row(this,
                withWeight(Ui.button(this, "Fahrtenübersicht", v -> startActivity(new Intent(this, OverviewActivity.class)))),
                withWeight(Ui.button(this, "Fahrzeuge", v -> startActivity(new Intent(this, VehiclesActivity.class))))));

        // Fahrt
        tripBox = Ui.vbox(this);
        status = Ui.text(this, "", 18);
        status.setPadding(0, pad, 0, 0);
        tripBox.addView(status);
        form = new TripForm(this);
        form.onChange = this::formChanged;
        tripBox.addView(form.root);

        bBegin = Ui.button(this, "Fahrt beginnen", v -> begin());
        bEnd = Ui.button(this, "Fahrt beenden", v -> endTrip());
        bSave = Ui.button(this, "Fahrt speichern", v -> saveTrip());
        bBack = Ui.button(this, "Zurück", v -> back());
        bDiscard = Ui.button(this, "Fahrt verwerfen", v -> discard());
        bReset = Ui.button(this, "Eingaben zurücksetzen", v -> reset());
        LinearLayout r1 = Ui.row(this, withWeight(bBegin), withWeight(bEnd), withWeight(bSave));
        LinearLayout r2 = Ui.row(this, withWeight(bBack), withWeight(bDiscard), withWeight(bReset));
        r1.setPadding(0, pad, 0, 0);
        tripBox.addView(r1);
        tripBox.addView(r2);
        root.addView(tripBox);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    private static View withWeight(View v) {
        v.setLayoutParams(Ui.weight(1));
        return v;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Repo.load(this);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        storeForm();
    }

    private void refresh() {
        fileText.setText(Repo.uri == null ? "keine Datei gewählt" : Repo.fileName);
        errorText.setText(Repo.error == null ? "" : Repo.error);
        errorText.setVisibility(Repo.error == null ? View.GONE : View.VISIBLE);
        if (!Repo.ready()) {
            hint.setText("Bitte eine Excel-Datei neu anlegen oder öffnen.");
            hint.setVisibility(View.VISIBLE);
            vehicleBox.setVisibility(View.GONE);
            tripBox.setVisibility(View.GONE);
            current = null;
            return;
        }
        if (Repo.book.vehicles.isEmpty()) {
            hint.setText("Bitte unter „Fahrzeuge“ ein Fahrzeug anlegen.");
            hint.setVisibility(View.VISIBLE);
            vehicleBox.setVisibility(View.GONE);
            tripBox.setVisibility(View.GONE);
            current = null;
            return;
        }
        hint.setVisibility(View.GONE);
        vehicleBox.setVisibility(View.VISIBLE);
        tripBox.setVisibility(View.VISIBLE);

        adapter = new VehicleAdapter(this, Repo.book.vehicles, false);
        String last = Store.lastVehicle(this);
        int idx = last == null ? -1 : adapter.indexOf(last);
        if (idx < 0) idx = 0;
        String name = adapter.getItem(idx).name;
        ignoreSelection = true;
        spinner.setAdapter(adapter);
        spinner.setSelection(idx, false);
        spinner.post(() -> ignoreSelection = false);
        Store.setLastVehicle(this, name);
        current = null;
        showVehicle(name);
    }

    private void showVehicle(String name) {
        storeForm();
        current = name;
        List<String> extra = new ArrayList<>();
        Draft d = Store.draft(this, name);
        if (d == null) d = new Draft();
        if (d.state == Draft.NEW && !d.touched) suggest(d);
        draft = d;
        form.setLists(Repo.book.places(extra), Repo.book.assignments(null));
        form.fromDraft(d);
        Store.putDraft(this, name, d);
        showState();
    }

    /** Vorschläge für eine neue Fahrt: aktuelle Zeit, letzter End-km und letzter Zielort. */
    private void suggest(Draft d) {
        Trip last = Repo.book.lastTrip(current);
        Draft n = new Draft();
        n.start = Dt.format(Dt.now());
        if (last != null) {
            n.kmStart = last.kmEnd != null ? String.valueOf(last.kmEnd) : "";
            n.startOrt = last.ziel;
        }
        d.state = Draft.NEW;
        d.touched = false;
        d.wasOpen = false;
        d.start = n.start;
        d.kmStart = n.kmStart;
        d.startOrt = n.startOrt;
        d.end = "";
        d.kmEnd = "";
        d.ziel = "";
        d.zuordnung = "";
        d.zwInput = "";
        d.zwischen.clear();
    }

    private void showState() {
        int s = draft.state;
        switch (s) {
            case Draft.OPEN:
                status.setText("Offene Fahrt seit " + draft.start);
                break;
            case Draft.ENDING:
                status.setText("Fahrt beenden");
                break;
            default:
                status.setText("Neue Fahrt");
        }
        form.showZw(s != Draft.NEW);
        form.showEnd(s == Draft.ENDING);
        bBegin.setVisibility(s == Draft.NEW ? View.VISIBLE : View.GONE);
        bEnd.setVisibility(s != Draft.ENDING ? View.VISIBLE : View.GONE);
        bSave.setVisibility(s == Draft.ENDING ? View.VISIBLE : View.GONE);
        bBack.setVisibility(s == Draft.ENDING ? View.VISIBLE : View.GONE);
        bDiscard.setVisibility(s != Draft.NEW ? View.VISIBLE : View.GONE);
        bReset.setVisibility(s == Draft.NEW ? View.VISIBLE : View.GONE);
    }

    private void formChanged() {
        if (current == null || draft == null) return;
        form.toDraft(draft);
        draft.touched = true;
        Store.putDraft(this, current, draft);
    }

    private void storeForm() {
        if (current == null || draft == null) return;
        form.toDraft(draft);
        Store.putDraft(this, current, draft);
    }

    // ------------------------------------------------------------- Aktionen

    private void begin() {
        String err = form.toTrip(new Trip(), false);
        if (err != null) {
            Ui.alert(this, err);
            return;
        }
        form.toDraft(draft);
        draft.state = Draft.OPEN;
        draft.touched = true;
        Store.putDraft(this, current, draft);
        showState();
        Toast.makeText(this, "Fahrt begonnen", Toast.LENGTH_SHORT).show();
    }

    private void endTrip() {
        form.toDraft(draft);
        draft.wasOpen = draft.state == Draft.OPEN;
        draft.state = Draft.ENDING;
        draft.touched = true;
        draft.end = Dt.format(Dt.now());
        form.end.setText(draft.end);
        Store.putDraft(this, current, draft);
        showState();
    }

    private void back() {
        form.toDraft(draft);
        draft.state = draft.wasOpen ? Draft.OPEN : Draft.NEW;
        Store.putDraft(this, current, draft);
        showState();
    }

    private void saveTrip() {
        Trip t = new Trip();
        t.vehicle = current;
        String err = form.toTrip(t, true);
        if (err != null) {
            Ui.alert(this, err);
            return;
        }
        Repo.load(this);
        if (!Repo.ready() || Repo.book.vehicle(current) == null) {
            Ui.alert(this, "Die Datei oder das Fahrzeug ist nicht mehr verfügbar.");
            refresh();
            return;
        }
        Repo.book.trips.add(t);
        try {
            Repo.save(this);
        } catch (IOException e) {
            Ui.alert(this, "Speichern fehlgeschlagen: " + e.getMessage());
            return;
        }
        Store.removeDraft(this, current);
        draft = null;
        Toast.makeText(this, "Fahrt gespeichert", Toast.LENGTH_SHORT).show();
        String name = current;
        current = null;
        showVehicle(name);
    }

    private void discard() {
        Ui.confirm(this, "Die begonnene Fahrt verwerfen? Die Eingaben gehen verloren.", "Verwerfen", () -> {
            Store.removeDraft(this, current);
            draft = null;
            String name = current;
            current = null;
            showVehicle(name);
        });
    }

    private void reset() {
        Store.removeDraft(this, current);
        draft = null;
        String name = current;
        current = null;
        showVehicle(name);
    }

    // ---------------------------------------------------------------- Datei

    private void createFile() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(XLSX);
        i.putExtra(Intent.EXTRA_TITLE, "Fahrtenbuch.xlsx");
        startActivityForResult(i, REQ_NEW);
    }

    private void openFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{XLSX, "application/octet-stream", "application/vnd.ms-excel"});
        startActivityForResult(i, REQ_OPEN);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(u,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException e) {
            Ui.alert(this, "Für diese Datei wurde kein dauerhafter Schreibzugriff gewährt.");
            return;
        }
        if (req == REQ_NEW) {
            try {
                Repo.createNew(this, u);
            } catch (IOException e) {
                Ui.alert(this, "Datei konnte nicht angelegt werden: " + e.getMessage());
                return;
            }
        }
        storeForm();
        current = null;
        Store.setLastUri(this, u.toString());
        Repo.load(this);
        refresh();
        if (Repo.error != null) Ui.alert(this, Repo.error);
    }
}
