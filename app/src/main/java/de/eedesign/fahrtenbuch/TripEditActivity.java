package de.eedesign.fahrtenbuch;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/** Ändern oder Löschen einer gespeicherten Fahrt. */
public class TripEditActivity extends Activity {
    private String vehicle, key;
    private TripForm form;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        vehicle = getIntent().getStringExtra("vehicle");
        key = getIntent().getStringExtra("key");
        if (Repo.book == null) Repo.load(this);
        Trip t = Repo.ready() ? Repo.book.findByKey(vehicle, key) : null;
        if (t == null) {
            Toast.makeText(this, "Fahrt nicht gefunden.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        int pad = Ui.dp(this, 16);
        LinearLayout root = Ui.vbox(this);
        root.setPadding(pad, pad / 2, pad, pad * 2);
        root.addView(Ui.label(this, "Fahrzeug"));
        TextView name = new TextView(this);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        Vehicle v = Repo.book.vehicle(vehicle);
        if (v != null) Ui.badge(name, v);
        else name.setText(vehicle);
        root.addView(Ui.row(this, name));

        form = new TripForm(this);
        form.setLists(Repo.book.places(null), Repo.book.assignments(null));
        form.showZw(true);
        form.showEnd(true);
        root.addView(form.root);

        if (b != null && b.getString("form") != null) {
            try {
                form.fromDraft(Draft.fromJson(new JSONObject(b.getString("form"))));
            } catch (JSONException e) {
                form.fromTrip(t);
            }
        } else {
            form.fromTrip(t);
        }

        View save = Ui.button(this, "Speichern", x -> save());
        View del = Ui.button(this, "Löschen", x -> delete());
        View cancel = Ui.button(this, "Abbrechen", x -> finish());
        for (View x : new View[]{save, del, cancel}) x.setLayoutParams(Ui.weight(1));
        LinearLayout r = Ui.row(this, save, del, cancel);
        r.setPadding(0, pad, 0, 0);
        root.addView(r);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (form != null) {
            Draft d = new Draft();
            form.toDraft(d);
            out.putString("form", d.toJson().toString());
        }
    }

    private Trip current() {
        Repo.load(this);
        Trip t = Repo.ready() ? Repo.book.findByKey(vehicle, key) : null;
        if (t == null) Ui.alert(this, "Die Fahrt wurde in der Datei nicht mehr gefunden (zwischenzeitlich am PC geändert?).");
        return t;
    }

    private void save() {
        Trip check = new Trip();
        String err = form.toTrip(check, true);
        if (err != null) {
            Ui.alert(this, err);
            return;
        }
        Trip t = current();
        if (t == null) return;
        form.toTrip(t, true);
        try {
            Repo.save(this);
        } catch (IOException e) {
            Ui.alert(this, "Speichern fehlgeschlagen: " + e.getMessage());
            return;
        }
        Toast.makeText(this, "Fahrt gespeichert", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void delete() {
        Ui.confirm(this, "Diese Fahrt löschen?", "Löschen", () -> {
            Trip t = current();
            if (t == null) return;
            Repo.book.trips.remove(t);
            try {
                Repo.save(this);
            } catch (IOException e) {
                Ui.alert(this, "Löschen fehlgeschlagen: " + e.getMessage());
                return;
            }
            Toast.makeText(this, "Fahrt gelöscht", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
