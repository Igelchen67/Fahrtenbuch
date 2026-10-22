package de.eedesign.fahrtenbuch;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;

/** Zugriff auf die gewählte Excel-Datei. */
public final class Repo {
    private Repo() {}

    public static Book book;
    public static Uri uri;
    public static String fileName = "";
    public static String error;

    /** Liest die zuletzt gewählte Datei neu ein. */
    public static void load(Context c) {
        error = null;
        String u = Store.lastUri(c);
        if (u == null) {
            uri = null;
            book = null;
            fileName = "";
            return;
        }
        uri = Uri.parse(u);
        fileName = queryName(c, uri);
        try {
            byte[] data = readAll(c, uri);
            book = XlsxIO.read(new ByteArrayInputStream(data));
        } catch (SecurityException e) {
            book = null;
            error = "Kein Zugriff mehr auf die Datei. Bitte erneut öffnen.";
        } catch (IOException e) {
            book = null;
            error = "Datei kann nicht gelesen werden: " + e.getMessage();
        }
    }

    public static boolean ready() {
        return book != null && uri != null;
    }

    /** Schreibt die Arbeitsmappe; bei Fehler wird der Dateistand neu geladen und die Ausnahme weitergereicht. */
    public static void save(Context c) throws IOException {
        if (!ready()) throw new IOException("Keine Datei geöffnet.");
        book.sort();
        byte[] data = XlsxIO.write(book);
        try {
            backup(c);
            writeAll(c, uri, data);
        } catch (IOException | RuntimeException e) {
            load(c);
            throw e instanceof IOException ? (IOException) e : new IOException(e.getMessage(), e);
        }
        load(c);
        if (book == null) throw new IOException(error == null ? "Datei nach dem Speichern nicht lesbar." : error);
    }

    /** Legt die neu erstellte Datei als leere Arbeitsmappe an. */
    public static void createNew(Context c, Uri u) throws IOException {
        writeAll(c, u, XlsxIO.write(new Book()));
    }

    /** Sicherung des bisherigen Dateistands im App-Speicher (wird bei jedem Speichern ersetzt). */
    private static void backup(Context c) {
        try {
            byte[] old = readAll(c, uri);
            if (old.length == 0) return;
            try (FileOutputStream f = new FileOutputStream(new java.io.File(c.getFilesDir(), "sicherung.xlsx"))) {
                f.write(old);
            }
        } catch (Exception ignored) {
        }
    }

    private static byte[] readAll(Context c, Uri u) throws IOException {
        try (InputStream in = c.getContentResolver().openInputStream(u)) {
            if (in == null) throw new IOException("Datei nicht verfügbar.");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private static void writeAll(Context c, Uri u, byte[] data) throws IOException {
        ContentResolver cr = c.getContentResolver();
        try (OutputStream os = cr.openOutputStream(u, "wt")) {
            if (os == null) throw new IOException("Datei nicht beschreibbar.");
            os.write(data);
            os.flush();
            return;
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            // Modus "wt" nicht unterstützt: Datei öffnen und selbst kürzen.
        }
        try (ParcelFileDescriptor pfd = cr.openFileDescriptor(u, "rw")) {
            if (pfd == null) throw new IOException("Datei nicht beschreibbar.");
            try (FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor())) {
                fos.getChannel().truncate(0);
                fos.getChannel().position(0);
                fos.write(data);
                fos.flush();
            }
        }
    }

    public static String queryName(Context c, Uri u) {
        try (Cursor cur = c.getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                String s = cur.getString(0);
                if (s != null) return s;
            }
        } catch (Exception ignored) {
        }
        String s = u.getLastPathSegment();
        return s == null ? "" : s;
    }
}
