package de.eedesign.fahrtenbuch;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Consumer;

/** Hilfsfunktionen für die programmatisch aufgebaute Oberfläche. */
public final class Ui {
    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c, View... views) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        for (View v : views) l.addView(v);
        return l;
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static TextView label(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(0xFF555555);
        t.setPadding(0, dp(c, 10), 0, 0);
        return t;
    }

    public static TextView text(Context c, String text, float sp) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(0xFF000000);
        return t;
    }

    public static EditText edit(Context c, int inputType) {
        EditText e = new EditText(c);
        e.setInputType(inputType);
        e.setSingleLine(true);
        return e;
    }

    public static EditText timeEdit(Context c) {
        EditText e = edit(c, InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_NORMAL);
        e.setHint("JJJJ-MM-TT hh:mm");
        return e;
    }

    public static Button button(Context c, String text, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    public static Button small(Context c, String text, View.OnClickListener l) {
        Button b = button(c, text, l);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(c, 10), 0, dp(c, 10), 0);
        return b;
    }

    /** Fahrzeugnamensfeld mit Fahrzeugfarbe als Hintergrund. */
    public static void badge(TextView t, Vehicle v) {
        Context c = t.getContext();
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF000000 | v.color);
        g.setCornerRadius(dp(c, 4));
        t.setBackground(g);
        t.setTextColor(0xFF000000 | Vehicle.textColorFor(v.color));
        t.setText(v.name);
        t.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6));
    }

    public static void alert(Context c, String msg) {
        new AlertDialog.Builder(c).setMessage(msg).setPositiveButton("OK", null).show();
    }

    public static void confirm(Context c, String msg, String yes, Runnable onYes) {
        new AlertDialog.Builder(c).setMessage(msg)
                .setPositiveButton(yes, (d, w) -> onYes.run())
                .setNegativeButton("Abbrechen", null).show();
    }

    /** Datum und Uhrzeit über Auswahldialoge setzen; Zukunft ist im Datumsdialog gesperrt. */
    public static void pickDateTime(Context c, EditText target) {
        LocalDateTime cur = Dt.parseStrict(target.getText().toString());
        if (cur == null) cur = Dt.now();
        final LocalDateTime base = cur;
        DatePickerDialog dd = new DatePickerDialog(c, (view, y, m, d) -> {
            TimePickerDialog td = new TimePickerDialog(c, (tv, h, min) -> {
                LocalDateTime t = LocalDateTime.of(y, m + 1, d, h, min);
                target.setText(Dt.format(t));
            }, base.getHour(), base.getMinute(), true);
            td.show();
        }, base.getYear(), base.getMonthValue() - 1, base.getDayOfMonth());
        dd.getDatePicker().setMaxDate(System.currentTimeMillis());
        dd.show();
    }

    public static void inputDialog(Context c, String title, String initial, Consumer<String> onOk) {
        EditText e = edit(c, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        e.setText(initial);
        e.setSelection(e.getText().length());
        LinearLayout box = vbox(c);
        box.setPadding(dp(c, 20), dp(c, 8), dp(c, 20), 0);
        box.addView(e);
        new AlertDialog.Builder(c).setTitle(title).setView(box)
                .setPositiveButton("OK", (d, w) -> onOk.accept(e.getText().toString().trim()))
                .setNegativeButton("Abbrechen", null).show();
    }

    /** Farbauswahl aus fester Palette. */
    public static void pickColor(Context c, String title, int current, Consumer<Integer> onPick) {
        LinearLayout box = vbox(c);
        int pad = dp(c, 16);
        box.setPadding(pad, pad, pad, 0);
        AlertDialog[] dlg = new AlertDialog[1];
        int perRow = 6;
        LinearLayout row = null;
        for (int i = 0; i < Vehicle.PALETTE.length; i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(row);
            }
            int col = Vehicle.PALETTE[i];
            TextView sw = new TextView(c);
            sw.setGravity(Gravity.CENTER);
            sw.setText(col == (current & 0xFFFFFF) ? "✓" : "");
            sw.setTextColor(0xFF000000 | Vehicle.textColorFor(col));
            sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            GradientDrawable g = new GradientDrawable();
            g.setColor(0xFF000000 | col);
            g.setCornerRadius(dp(c, 6));
            g.setStroke(dp(c, 1), Color.DKGRAY);
            sw.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(c, 44), 1f);
            lp.setMargins(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));
            sw.setLayoutParams(lp);
            sw.setOnClickListener(v -> {
                dlg[0].dismiss();
                onPick.accept(col);
            });
            row.addView(sw);
        }
        dlg[0] = new AlertDialog.Builder(c).setTitle(title).setView(box)
                .setNegativeButton("Abbrechen", null).create();
        dlg[0].show();
    }

    @SuppressWarnings("unused")
    private static long millis(LocalDateTime t) {
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
