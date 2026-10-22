package de.eedesign.fahrtenbuch;

import android.content.Context;
import android.text.InputType;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import java.util.List;

/** Freie Eingabe mit Auswahlliste; Antippen zeigt die vollständige Liste. */
public class Combo extends AutoCompleteTextView {

    public Combo(Context c) {
        super(c);
        setThreshold(1);
        setSingleLine(true);
        setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    }

    public void setItems(List<String> items) {
        setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, items));
    }

    public void setValue(String s) {
        setText(s == null ? "" : s, false);
    }

    public String value() {
        return getText().toString().trim();
    }

    @Override
    public boolean enoughToFilter() {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean r = super.onTouchEvent(e);
        if (e.getAction() == MotionEvent.ACTION_UP) {
            post(this::showAll);
        }
        return r;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    public void showAll() {
        if (getAdapter() == null || getAdapter().getCount() == 0 && getText().length() == 0) return;
        if (getWindowToken() == null) return;
        performFiltering(getText(), 0);
        showDropDown();
    }
}
