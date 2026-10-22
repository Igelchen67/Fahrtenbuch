package de.eedesign.fahrtenbuch;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Fahrzeugauswahl; jedes Fahrzeugnamensfeld trägt die Fahrzeugfarbe. Eintrag null = alle Fahrzeuge. */
public class VehicleAdapter extends BaseAdapter {
    private final Context c;
    public final List<Vehicle> items = new ArrayList<>();

    public VehicleAdapter(Context c, List<Vehicle> vehicles, boolean withAll) {
        this.c = c;
        if (withAll) items.add(null);
        items.addAll(vehicles);
    }

    public int indexOf(String name) {
        for (int i = 0; i < items.size(); i++) {
            Vehicle v = items.get(i);
            if (v != null && v.name.equals(name)) return i;
        }
        return -1;
    }

    @Override public int getCount() { return items.size(); }
    @Override public Vehicle getItem(int p) { return items.get(p); }
    @Override public long getItemId(int p) { return p; }

    @Override
    public View getView(int p, View convert, ViewGroup parent) {
        return make(p);
    }

    @Override
    public View getDropDownView(int p, View convert, ViewGroup parent) {
        return make(p);
    }

    private View make(int p) {
        LinearLayout box = new LinearLayout(c);
        int pad = Ui.dp(c, 4);
        box.setPadding(pad, pad, pad, pad);
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        Vehicle v = items.get(p);
        if (v == null) {
            t.setText("Alle Fahrzeuge");
            t.setTextColor(0xFF000000);
            t.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
        } else {
            Ui.badge(t, v);
        }
        box.addView(t);
        return box;
    }
}
