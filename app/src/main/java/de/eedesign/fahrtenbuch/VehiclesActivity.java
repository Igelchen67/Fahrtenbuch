package de.eedesign.fahrtenbuch;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

/** Fahrzeuge anlegen, umbenennen, Farbe ändern, löschen. */
public class VehiclesActivity extends Activity {
    private ListView list;
    private Adapter adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        int pad = Ui.dp(this, 16);
        LinearLayout root = Ui.vbox(this);
        root.setPadding(pad, pad / 2, pad, pad);
        root.addView(Ui.button(this, "Fahrzeug anlegen", v -> add()));
        empty = Ui.text(this, "Noch kein Fahrzeug angelegt.", 15);
        empty.setPadding(0, pad, 0, 0);
        root.addView(empty);
        list = new ListView(this);
        list.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> actions(Repo.book.vehicles.get(pos)));
        root.addView(list);
        root.addView(Ui.label(this, "Antippen: umbenennen, Farbe ändern oder löschen"));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Repo.load(this);
        if (!Repo.ready()) {
            Toast.makeText(this, Repo.error != null ? Repo.error : "Bitte zuerst eine Excel-Datei anlegen oder öffnen.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        refresh();
    }

    private void refresh() {
        adapter.notifyDataSetChanged();
        empty.setVisibility(Repo.ready() && Repo.book.vehicles.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean saveOrReport(String failMsg) {
        try {
            Repo.save(this);
            return true;
        } catch (IOException e) {
            Ui.alert(this, failMsg + ": " + e.getMessage());
            return false;
        } finally {
            if (!Repo.ready()) finish();
            else refresh();
        }
    }

    private void add() {
        Ui.inputDialog(this, "Neues Fahrzeug", "", name -> {
            Repo.load(this);
            if (!Repo.ready()) return;
            String err = Repo.book.checkVehicleName(name, null);
            if (err != null) {
                Ui.alert(this, err);
                return;
            }
            int used = Repo.book.vehicles.size();
            int def = Vehicle.PALETTE[(used * 5) % Vehicle.PALETTE.length];
            Ui.pickColor(this, "Farbe für „" + name + "“", def, col -> {
                Repo.load(this);
                if (!Repo.ready()) return;
                String err2 = Repo.book.checkVehicleName(name, null);
                if (err2 != null) {
                    Ui.alert(this, err2);
                    return;
                }
                Repo.book.vehicles.add(new Vehicle(name, col));
                if (saveOrReport("Anlegen fehlgeschlagen")) {
                    Store.setLastVehicle(this, name);
                    Toast.makeText(this, "Fahrzeug angelegt", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void actions(Vehicle v) {
        String[] items = {"Umbenennen", "Farbe ändern", "Löschen"};
        new AlertDialog.Builder(this).setTitle(v.name).setItems(items, (d, which) -> {
            if (which == 0) rename(v.name);
            else if (which == 1) recolor(v.name);
            else delete(v.name);
        }).show();
    }

    private void rename(String oldName) {
        Ui.inputDialog(this, "Fahrzeug umbenennen", oldName, name -> {
            if (name.equals(oldName)) return;
            Repo.load(this);
            if (!Repo.ready()) return;
            Vehicle v = Repo.book.vehicle(oldName);
            if (v == null) {
                Ui.alert(this, "Fahrzeug nicht mehr vorhanden.");
                refresh();
                return;
            }
            String err = Repo.book.checkVehicleName(name, v);
            if (err != null) {
                Ui.alert(this, err);
                return;
            }
            v.name = name;
            for (Trip t : Repo.book.trips) if (t.vehicle.equals(oldName)) t.vehicle = name;
            if (saveOrReport("Umbenennen fehlgeschlagen")) {
                Store.renameDraft(this, oldName, name);
                Toast.makeText(this, "Fahrzeug umbenannt", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void recolor(String name) {
        Vehicle v0 = Repo.book.vehicle(name);
        if (v0 == null) return;
        Ui.pickColor(this, "Farbe für „" + name + "“", v0.color, col -> {
            Repo.load(this);
            if (!Repo.ready()) return;
            Vehicle v = Repo.book.vehicle(name);
            if (v == null) {
                refresh();
                return;
            }
            v.color = col;
            if (saveOrReport("Farbänderung fehlgeschlagen")) {
                Toast.makeText(this, "Farbe geändert", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void delete(String name) {
        int n = Repo.book.tripsOf(name).size();
        if (n > 0) {
            Ui.alert(this, "Löschen nicht möglich: Für „" + name + "“ sind " + n
                    + (n == 1 ? " Fahrt" : " Fahrten") + " gespeichert.");
            return;
        }
        Draft d = Store.draft(this, name);
        if (d != null && d.isOpen()) {
            Ui.alert(this, "Löschen nicht möglich: Für „" + name + "“ ist eine Fahrt begonnen. Bitte zuerst speichern oder verwerfen.");
            return;
        }
        Ui.confirm(this, "Fahrzeug „" + name + "“ löschen?", "Löschen", () -> {
            Repo.load(this);
            if (!Repo.ready()) return;
            Vehicle v = Repo.book.vehicle(name);
            if (v == null) {
                refresh();
                return;
            }
            if (!Repo.book.tripsOf(name).isEmpty()) {
                Ui.alert(this, "Löschen nicht möglich: In der Datei sind inzwischen Fahrten gespeichert.");
                return;
            }
            Repo.book.vehicles.remove(v);
            if (saveOrReport("Löschen fehlgeschlagen")) {
                Store.removeDraft(this, name);
                Toast.makeText(this, "Fahrzeug gelöscht", Toast.LENGTH_SHORT).show();
            }
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

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return Repo.ready() ? Repo.book.vehicles.size() : 0; }
        @Override public Object getItem(int p) { return Repo.book.vehicles.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int p, View convert, ViewGroup parent) {
            Vehicle v = Repo.book.vehicles.get(p);
            int pad = Ui.dp(VehiclesActivity.this, 8);
            TextView name = new TextView(VehiclesActivity.this);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            Ui.badge(name, v);
            int n = Repo.book.tripsOf(v.name).size();
            TextView info = Ui.text(VehiclesActivity.this, n + (n == 1 ? " Fahrt" : " Fahrten"), 14);
            info.setPadding(pad * 2, 0, 0, 0);
            LinearLayout row = Ui.row(VehiclesActivity.this, name, info);
            row.setPadding(0, pad, 0, pad);
            return row;
        }
    }
}
