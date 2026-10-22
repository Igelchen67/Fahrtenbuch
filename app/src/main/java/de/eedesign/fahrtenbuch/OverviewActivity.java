package de.eedesign.fahrtenbuch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Fahrtenübersicht aller Fahrzeuge, filterbar; älteste oben, neueste unten. */
public class OverviewActivity extends Activity {
    private Spinner filter;
    private VehicleAdapter filterAdapter;
    private ListView list;
    private TextView empty;
    private final List<Trip> shown = new ArrayList<>();
    private TripAdapter tripAdapter;
    private String filterName; // null = alle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        if (b != null) filterName = b.getString("filter");
        int pad = Ui.dp(this, 12);
        LinearLayout root = Ui.vbox(this);
        root.setPadding(pad, pad / 2, pad, 0);
        filter = new Spinner(this);
        root.addView(filter);
        empty = Ui.text(this, "Keine Fahrten vorhanden.", 15);
        empty.setPadding(0, pad, 0, 0);
        root.addView(empty);
        list = new ListView(this);
        list.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        tripAdapter = new TripAdapter();
        list.setAdapter(tripAdapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            Trip t = shown.get(pos);
            Intent i = new Intent(this, TripEditActivity.class);
            i.putExtra("vehicle", t.vehicle);
            i.putExtra("key", t.key());
            startActivity(i);
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            confirmDelete(shown.get(pos));
            return true;
        });
        root.addView(list);
        TextView tip = Ui.label(this, "Antippen: bearbeiten; lange drücken: löschen");
        tip.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        root.addView(tip);
        setContentView(root);

        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                Vehicle veh = filterAdapter.getItem(pos);
                String n = veh == null ? null : veh.name;
                boolean changed = n == null ? filterName != null : !n.equals(filterName);
                filterName = n;
                if (changed) fill();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("filter", filterName);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Repo.load(this);
        if (!Repo.ready()) {
            Toast.makeText(this, Repo.error != null ? Repo.error : "Keine Datei geöffnet.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        filterAdapter = new VehicleAdapter(this, Repo.book.vehicles, true);
        filter.setAdapter(filterAdapter);
        int idx = filterName == null ? 0 : filterAdapter.indexOf(filterName);
        if (idx < 0) {
            idx = 0;
            filterName = null;
        }
        filter.setSelection(idx, false);
        fill();
    }

    private void fill() {
        shown.clear();
        shown.addAll(Repo.book.tripsOf(filterName));
        tripAdapter.notifyDataSetChanged();
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        if (!shown.isEmpty()) list.post(() -> list.setSelection(shown.size() - 1));
    }

    private void confirmDelete(Trip t) {
        Ui.confirm(this, "Diese Fahrt löschen?\n\n" + t.vehicle + "\n" + Dt.format(t.start) + "\n"
                + route(t), "Löschen", () -> {
            Repo.load(this);
            Trip cur = Repo.ready() ? Repo.book.findByKey(t.vehicle, t.key()) : null;
            if (cur == null) {
                Ui.alert(this, "Die Fahrt wurde in der Datei nicht mehr gefunden.");
                onResume();
                return;
            }
            Repo.book.trips.remove(cur);
            try {
                Repo.save(this);
                Toast.makeText(this, "Fahrt gelöscht", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Ui.alert(this, "Löschen fehlgeschlagen: " + e.getMessage());
            }
            if (Repo.ready()) fill();
        });
    }

    static String route(Trip t) {
        StringBuilder sb = new StringBuilder(t.startOrt.isEmpty() ? "?" : t.startOrt);
        for (String z : t.zwischen) sb.append(" → ").append(z);
        sb.append(" → ").append(t.ziel.isEmpty() ? "?" : t.ziel);
        return sb.toString();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class TripAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int p) { return shown.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int p, View convert, ViewGroup parent) {
            Trip t = shown.get(p);
            LinearLayout box = Ui.vbox(OverviewActivity.this);
            int pad = Ui.dp(OverviewActivity.this, 6);
            box.setPadding(0, pad, 0, pad);
            TextView name = new TextView(OverviewActivity.this);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            Vehicle v = Repo.book != null ? Repo.book.vehicle(t.vehicle) : null;
            if (v != null) Ui.badge(name, v);
            else name.setText(t.vehicle);
            LinearLayout head = Ui.row(OverviewActivity.this, name);
            box.addView(head);

            String km;
            if (t.kmStart != null && t.kmEnd != null) {
                km = t.kmStart + " – " + t.kmEnd + " km (" + (t.kmEnd - t.kmStart) + " km)";
            } else {
                km = (t.kmStart == null ? "?" : t.kmStart) + " – " + (t.kmEnd == null ? "?" : t.kmEnd) + " km";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(Dt.format(t.start)).append(" – ").append(Dt.format(t.end)).append('\n')
                    .append(km).append('\n')
                    .append(route(t));
            if (!t.zuordnung.isEmpty()) sb.append('\n').append(t.zuordnung);
            TextView body = Ui.text(OverviewActivity.this, sb.toString(), 15);
            body.setPadding(0, Ui.dp(OverviewActivity.this, 4), 0, 0);
            box.addView(body);
            return box;
        }
    }
}
