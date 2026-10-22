package de.eedesign.fahrtenbuch;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Liest und schreibt die Fahrtenbuch-Datei im Format .xlsx (Office Open XML, ab Excel 2007).
 * Ein Tabellenblatt je Fahrzeug; ein Blatt gilt als Fahrzeugblatt, wenn A1 "Auto" enthält.
 */
public final class XlsxIO {
    private XlsxIO() {}

    public static final String[] HEADER = {
            "Auto", "Startzeit", "Ankunftszeit", "Anfangs km.", "End km.",
            "Startort", "Zwischenziele", "Zielort", "Zuordnung der Fahrt"
    };
    private static final double[] WIDTHS = {18, 17, 17, 11, 11, 24, 28, 24, 26};
    private static final int DEFAULT_COLOR = 0x757575;

    // ------------------------------------------------------------------ Lesen

    public static Book read(InputStream in) throws IOException {
        Map<String, byte[]> zip = unzip(in);
        Book book = new Book();
        if (zip.isEmpty()) return book; // neu angelegte, leere Datei
        try {
            String wbPath = findWorkbookPath(zip);
            String base = wbPath.contains("/") ? wbPath.substring(0, wbPath.lastIndexOf('/') + 1) : "";
            Document wb = dom(need(zip, wbPath));
            Map<String, String> rels = readRels(zip, relsPathFor(wbPath), base);

            List<String> shared = new ArrayList<>();
            String themePath = null, stylesPath = null, sstPath = null;
            Document relDoc = zip.containsKey(relsPathFor(wbPath)) ? dom(zip.get(relsPathFor(wbPath))) : null;
            if (relDoc != null) {
                for (Element r : elems(relDoc.getDocumentElement(), "Relationship")) {
                    String type = r.getAttribute("Type");
                    String target = resolve(base, r.getAttribute("Target"));
                    if (type.endsWith("/sharedStrings")) sstPath = target;
                    else if (type.endsWith("/styles")) stylesPath = target;
                    else if (type.endsWith("/theme")) themePath = target;
                }
            }
            if (sstPath != null && zip.containsKey(sstPath)) shared = readShared(dom(zip.get(sstPath)));
            int[] theme = themePath != null && zip.containsKey(themePath) ? readTheme(dom(zip.get(themePath))) : null;
            Integer[] xfFill = new Integer[0];
            if (stylesPath != null && zip.containsKey(stylesPath)) xfFill = readStyleFills(dom(zip.get(stylesPath)), theme);

            for (Element sh : elems(wb.getDocumentElement(), "sheet")) {
                String name = sh.getAttribute("name");
                String rid = attrLocal(sh, "id");
                String path = rels.get(rid);
                if (path == null || !zip.containsKey(path)) continue;
                readSheet(book, name, dom(zip.get(path)), shared, xfFill, theme);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Die Datei ist keine lesbare Excel-Datei (.xlsx): " + e.getMessage(), e);
        }
        book.sort();
        return book;
    }

    private static void readSheet(Book book, String name, Document doc, List<String> shared,
                                  Integer[] xfFill, int[] theme) {
        Element root = doc.getDocumentElement();
        Map<Integer, Cell[]> rows = new HashMap<>();
        int maxRow = 0;
        for (Element row : elems(root, "row")) {
            int rn = parseInt(row.getAttribute("r"), maxRow + 1);
            maxRow = Math.max(maxRow, rn);
            Cell[] cells = new Cell[HEADER.length];
            int col = 0;
            for (Element c : children(row, "c")) {
                String ref = c.getAttribute("r");
                if (!ref.isEmpty()) col = colIndex(ref);
                if (col < cells.length) cells[col] = readCell(c, shared);
                col++;
            }
            rows.put(rn, cells);
        }
        Cell[] head = rows.get(1);
        if (head == null || head[0] == null || !"auto".equals(head[0].text.trim().toLowerCase(Locale.GERMAN))) {
            return; // kein Fahrzeugblatt
        }
        Integer color = null;
        List<Trip> trips = new ArrayList<>();
        for (int r = 2; r <= maxRow; r++) {
            Cell[] c = rows.get(r);
            if (c == null) continue;
            boolean empty = true;
            for (int i = 1; i < c.length; i++) if (c[i] != null && !c[i].isEmpty()) empty = false;
            if (empty) continue;
            if (color == null && c[0] != null && c[0].style >= 0 && c[0].style < xfFill.length) {
                color = xfFill[c[0].style];
            }
            Trip t = new Trip();
            t.vehicle = name;
            t.start = time(c[1]);
            t.end = time(c[2]);
            t.kmStart = km(c[3]);
            t.kmEnd = km(c[4]);
            t.startOrt = text(c[5]);
            for (String s : text(c[6]).split("\\r?\\n|\\r")) {
                if (!s.trim().isEmpty()) t.zwischen.add(s.trim());
            }
            t.ziel = text(c[7]);
            t.zuordnung = text(c[8]);
            trips.add(t);
        }
        if (color == null) {
            for (Element tc : elems(root, "tabColor")) {
                color = color(tc, theme);
                break;
            }
        }
        book.vehicles.add(new Vehicle(name, color == null ? DEFAULT_COLOR : color));
        book.trips.addAll(trips);
    }

    private static class Cell {
        String text = "";
        Double num;
        int style = 0;

        boolean isEmpty() {
            return num == null && text.trim().isEmpty();
        }
    }

    private static Cell readCell(Element c, List<String> shared) {
        Cell cell = new Cell();
        cell.style = parseInt(c.getAttribute("s"), 0);
        String t = c.getAttribute("t");
        Element v = first(c, "v");
        String vs = v == null ? "" : v.getTextContent();
        switch (t) {
            case "s":
                int idx = parseInt(vs.trim(), -1);
                cell.text = idx >= 0 && idx < shared.size() ? shared.get(idx) : "";
                break;
            case "inlineStr":
                Element is = first(c, "is");
                cell.text = is == null ? "" : richText(is);
                break;
            case "str":
            case "e":
            case "b":
                cell.text = vs;
                break;
            default:
                cell.text = vs;
                try {
                    if (!vs.trim().isEmpty()) cell.num = Double.parseDouble(vs.trim());
                } catch (NumberFormatException ignored) {
                }
        }
        return cell;
    }

    private static LocalDateTime time(Cell c) {
        if (c == null) return null;
        if (c.num != null) return Dt.fromSerial(c.num);
        return Dt.parseLenient(c.text);
    }

    private static Long km(Cell c) {
        if (c == null) return null;
        if (c.num != null) return Math.round(c.num);
        String s = c.text.trim().replace(".", "").replace(" ", "");
        if (s.matches("\\d+")) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static String text(Cell c) {
        if (c == null) return "";
        if (c.num != null && c.text.trim().isEmpty()) return "";
        if (c.num != null) {
            double d = c.num;
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
        }
        return c.text.trim();
    }

    private static List<String> readShared(Document d) {
        List<String> l = new ArrayList<>();
        for (Element si : children(d.getDocumentElement(), "si")) l.add(richText(si));
        return l;
    }

    /** Text aller t-Elemente, ohne phonetische Angaben (rPh). */
    private static String richText(Element e) {
        StringBuilder sb = new StringBuilder();
        collectText(e, sb);
        return sb.toString();
    }

    private static void collectText(Element e, StringBuilder sb) {
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element)) continue;
            String ln = local(n);
            if ("rPh".equals(ln)) continue;
            if ("t".equals(ln)) sb.append(n.getTextContent());
            else collectText((Element) n, sb);
        }
    }

    /** Liefert je Zellformat-Index (cellXfs) die Füllfarbe oder null. */
    private static Integer[] readStyleFills(Document d, int[] theme) {
        Element root = d.getDocumentElement();
        List<Integer> fills = new ArrayList<>();
        Element fillsEl = first(root, "fills");
        if (fillsEl != null) {
            for (Element f : children(fillsEl, "fill")) {
                Element pf = first(f, "patternFill");
                Integer col = null;
                if (pf != null && "solid".equals(pf.getAttribute("patternType"))) {
                    Element fg = first(pf, "fgColor");
                    if (fg != null) col = color(fg, theme);
                }
                fills.add(col);
            }
        }
        Element xfs = first(root, "cellXfs");
        if (xfs == null) return new Integer[0];
        List<Element> list = children(xfs, "xf");
        Integer[] res = new Integer[list.size()];
        for (int i = 0; i < list.size(); i++) {
            int fid = parseInt(list.get(i).getAttribute("fillId"), 0);
            res[i] = fid < fills.size() ? fills.get(fid) : null;
        }
        return res;
    }

    private static int[] readTheme(Document d) {
        String[] order = {"lt1", "dk1", "lt2", "dk2", "accent1", "accent2", "accent3",
                "accent4", "accent5", "accent6", "hlink", "folHlink"};
        int[] res = {0xFFFFFF, 0x000000, 0xEEECE1, 0x1F497D, 0x4F81BD, 0xC0504D,
                0x9BBB59, 0x8064A2, 0x4BACC6, 0xF79646, 0x0000FF, 0x800080};
        for (int i = 0; i < order.length; i++) {
            for (Element e : elems(d.getDocumentElement(), order[i])) {
                Element srgb = first(e, "srgbClr");
                Element sys = first(e, "sysClr");
                try {
                    if (srgb != null) res[i] = Integer.parseInt(srgb.getAttribute("val"), 16);
                    else if (sys != null && !sys.getAttribute("lastClr").isEmpty())
                        res[i] = Integer.parseInt(sys.getAttribute("lastClr"), 16);
                } catch (NumberFormatException ignored) {
                }
                break;
            }
        }
        return res;
    }

    private static Integer color(Element e, int[] theme) {
        try {
            String rgb = e.getAttribute("rgb");
            if (rgb.length() == 8) return Integer.parseInt(rgb.substring(2), 16);
            if (rgb.length() == 6) return Integer.parseInt(rgb, 16);
            String th = e.getAttribute("theme");
            if (!th.isEmpty()) {
                int i = Integer.parseInt(th);
                int base = theme != null && i < theme.length ? theme[i] : 0x808080;
                String tint = e.getAttribute("tint");
                return tint.isEmpty() ? base : applyTint(base, Double.parseDouble(tint));
            }
            String ix = e.getAttribute("indexed");
            if (!ix.isEmpty()) {
                int i = Integer.parseInt(ix);
                if (i < INDEXED.length) return INDEXED[i];
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static int applyTint(int rgb, double tint) {
        float[] hsl = rgbToHsl(rgb);
        if (tint < 0) hsl[2] = (float) (hsl[2] * (1 + tint));
        else hsl[2] = (float) (hsl[2] * (1 - tint) + tint);
        return hslToRgb(hsl);
    }

    private static float[] rgbToHsl(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float h, s, l = (max + min) / 2f, d = max - min;
        if (d == 0) {
            h = 0;
            s = 0;
        } else {
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) h = ((g - b) / d + (g < b ? 6 : 0));
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h /= 6f;
        }
        return new float[]{h, s, l};
    }

    private static int hslToRgb(float[] hsl) {
        float h = hsl[0], s = hsl[1], l = Math.max(0f, Math.min(1f, hsl[2]));
        float r, g, b;
        if (s == 0) {
            r = g = b = l;
        } else {
            float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
            float p = 2 * l - q;
            r = hue(p, q, h + 1f / 3f);
            g = hue(p, q, h);
            b = hue(p, q, h - 1f / 3f);
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    private static float hue(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f / 6f) return p + (q - p) * 6 * t;
        if (t < 0.5f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6;
        return p;
    }

    private static final int[] INDEXED = {
            0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF,
            0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF,
            0x800000, 0x008000, 0x000080, 0x808000, 0x800080, 0x008080, 0xC0C0C0, 0x808080,
            0x9999FF, 0x993366, 0xFFFFCC, 0xCCFFFF, 0x660066, 0xFF8080, 0x0066CC, 0xCCCCFF,
            0x000080, 0xFF00FF, 0xFFFF00, 0x00FFFF, 0x800080, 0x800000, 0x008080, 0x0000FF,
            0x00CCFF, 0xCCFFFF, 0xCCFFCC, 0xFFFF99, 0x99CCFF, 0xFF99CC, 0xCC99FF, 0xFFCC99,
            0x3366FF, 0x33CCCC, 0x99CC00, 0xFFCC00, 0xFF9900, 0xFF6600, 0x666699, 0x969696,
            0x003366, 0x339966, 0x003300, 0x333300, 0x993300, 0x993366, 0x333399, 0x333333
    };

    private static String findWorkbookPath(Map<String, byte[]> zip) throws Exception {
        if (zip.containsKey("_rels/.rels")) {
            Document d = dom(zip.get("_rels/.rels"));
            for (Element r : elems(d.getDocumentElement(), "Relationship")) {
                if (r.getAttribute("Type").endsWith("/officeDocument")) {
                    return resolve("", r.getAttribute("Target"));
                }
            }
        }
        if (zip.containsKey("xl/workbook.xml")) return "xl/workbook.xml";
        throw new IOException("Arbeitsmappe nicht gefunden");
    }

    private static String relsPathFor(String part) {
        int i = part.lastIndexOf('/');
        String dir = i >= 0 ? part.substring(0, i + 1) : "";
        return dir + "_rels/" + part.substring(i + 1) + ".rels";
    }

    private static Map<String, String> readRels(Map<String, byte[]> zip, String path, String base) throws Exception {
        Map<String, String> m = new HashMap<>();
        if (!zip.containsKey(path)) return m;
        Document d = dom(zip.get(path));
        for (Element r : elems(d.getDocumentElement(), "Relationship")) {
            m.put(r.getAttribute("Id"), resolve(base, r.getAttribute("Target")));
        }
        return m;
    }

    private static String resolve(String base, String target) {
        String p = target.startsWith("/") ? target.substring(1) : base + target;
        List<String> parts = new ArrayList<>();
        for (String s : p.split("/")) {
            if (s.isEmpty() || s.equals(".")) continue;
            if (s.equals("..")) {
                if (!parts.isEmpty()) parts.remove(parts.size() - 1);
            } else parts.add(s);
        }
        return String.join("/", parts);
    }

    private static byte[] need(Map<String, byte[]> zip, String p) throws IOException {
        byte[] b = zip.get(p);
        if (b == null) throw new IOException("Teil fehlt: " + p);
        return b;
    }

    private static Map<String, byte[]> unzip(InputStream in) throws IOException {
        Map<String, byte[]> m = new HashMap<>();
        try (ZipInputStream z = new ZipInputStream(in)) {
            ZipEntry e;
            byte[] buf = new byte[16384];
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                int n;
                while ((n = z.read(buf)) > 0) bos.write(buf, 0, n);
                String name = e.getName().replace('\\', '/');
                if (name.startsWith("/")) name = name.substring(1);
                m.put(name, bos.toByteArray());
            }
        }
        return m;
    }

    private static Document dom(byte[] data) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        try {
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignored) {
        }
        f.setExpandEntityReferences(false);
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new ByteArrayInputStream(data));
    }

    private static String local(Node n) {
        String ln = n.getLocalName();
        if (ln == null) {
            ln = n.getNodeName();
            int i = ln.indexOf(':');
            if (i >= 0) ln = ln.substring(i + 1);
        }
        return ln;
    }

    /** Alle Nachfahren mit lokalem Namen. */
    private static List<Element> elems(Element root, String localName) {
        List<Element> l = new ArrayList<>();
        NodeList nl = root.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < nl.getLength(); i++) l.add((Element) nl.item(i));
        if (l.isEmpty()) {
            nl = root.getElementsByTagName(localName);
            for (int i = 0; i < nl.getLength(); i++) l.add((Element) nl.item(i));
        }
        return l;
    }

    private static List<Element> children(Element e, String localName) {
        List<Element> l = new ArrayList<>();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && localName.equals(local(n))) l.add((Element) n);
        }
        return l;
    }

    private static Element first(Element e, String localName) {
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && localName.equals(local(n))) return (Element) n;
        }
        return null;
    }

    private static String attrLocal(Element e, String localName) {
        NamedNodeMap a = e.getAttributes();
        for (int i = 0; i < a.getLength(); i++) {
            Node n = a.item(i);
            if (localName.equals(local(n)) && n.getNodeName().contains(":")) return n.getNodeValue();
        }
        return e.getAttribute(localName);
    }

    private static int colIndex(String ref) {
        int c = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = Character.toUpperCase(ref.charAt(i));
            if (ch < 'A' || ch > 'Z') break;
            c = c * 26 + (ch - 'A' + 1);
        }
        return c - 1;
    }

    private static int parseInt(String s, int def) {
        try {
            return s == null || s.isEmpty() ? def : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // ---------------------------------------------------------------- Schreiben

    public static byte[] write(Book book) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        List<Vehicle> vs = book.vehicles;
        int n = Math.max(1, vs.size());
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            StringBuilder ct = new StringBuilder();
            ct.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                    .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                    .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                    .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                    .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
                    .append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
            for (int i = 1; i <= n; i++) {
                ct.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                        .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
            }
            ct.append("</Types>");
            put(z, "[Content_Types].xml", ct.toString());

            put(z, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");

            StringBuilder wb = new StringBuilder();
            wb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                    .append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
                    .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
                    .append("<bookViews><workbookView activeTab=\"0\"/></bookViews><sheets>");
            StringBuilder rels = new StringBuilder();
            rels.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                    .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            for (int i = 1; i <= n; i++) {
                String name = vs.isEmpty() ? "Fahrtenbuch" : vs.get(i - 1).name;
                wb.append("<sheet name=\"").append(esc(name)).append("\" sheetId=\"").append(i)
                        .append("\" r:id=\"rId").append(i).append("\"/>");
                rels.append("<Relationship Id=\"rId").append(i)
                        .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                        .append(i).append(".xml\"/>");
            }
            wb.append("</sheets></workbook>");
            rels.append("<Relationship Id=\"rId").append(n + 1)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
                    .append("</Relationships>");
            put(z, "xl/workbook.xml", wb.toString());
            put(z, "xl/_rels/workbook.xml.rels", rels.toString());
            put(z, "xl/styles.xml", styles(vs));

            if (vs.isEmpty()) {
                put(z, "xl/worksheets/sheet1.xml", placeholderSheet());
            } else {
                for (int i = 0; i < vs.size(); i++) {
                    put(z, "xl/worksheets/sheet" + (i + 1) + ".xml",
                            sheet(vs.get(i), book.tripsOf(vs.get(i).name), STYLE_VEHICLE0 + i, i == 0));
                }
            }
        }
        return bos.toByteArray();
    }

    private static final int STYLE_DATE = 1, STYLE_WRAP = 2, STYLE_INT = 3, STYLE_VEHICLE0 = 4;

    private static String styles(List<Vehicle> vs) {
        StringBuilder s = new StringBuilder();
        s.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"yyyy\\-mm\\-dd\\ hh:mm\"/></numFmts>")
                .append("<fonts count=\"2\">")
                .append("<font><sz val=\"11\"/><color rgb=\"FF000000\"/><name val=\"Calibri\"/><family val=\"2\"/></font>")
                .append("<font><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/><family val=\"2\"/></font>")
                .append("</fonts>");
        s.append("<fills count=\"").append(2 + vs.size()).append("\">")
                .append("<fill><patternFill patternType=\"none\"/></fill>")
                .append("<fill><patternFill patternType=\"gray125\"/></fill>");
        for (Vehicle v : vs) {
            s.append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF").append(hex(v.color))
                    .append("\"/><bgColor indexed=\"64\"/></patternFill></fill>");
        }
        s.append("</fills>")
                .append("<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>")
                .append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>");
        s.append("<cellXfs count=\"").append(STYLE_VEHICLE0 + vs.size()).append("\">")
                .append("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment vertical=\"top\"/></xf>")
                .append("<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyAlignment=\"1\"><alignment horizontal=\"left\" vertical=\"top\"/></xf>")
                .append("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf>")
                .append("<xf numFmtId=\"1\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyAlignment=\"1\"><alignment vertical=\"top\"/></xf>");
        for (int i = 0; i < vs.size(); i++) {
            int font = Vehicle.textColorFor(vs.get(i).color) == 0xFFFFFF ? 1 : 0;
            s.append("<xf numFmtId=\"0\" fontId=\"").append(font).append("\" fillId=\"").append(2 + i)
                    .append("\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment vertical=\"top\"/></xf>");
        }
        s.append("</cellXfs>")
                .append("<cellStyles count=\"1\"><cellStyle name=\"Standard\" xfId=\"0\" builtinId=\"0\"/></cellStyles>")
                .append("</styleSheet>");
        return s.toString();
    }

    private static String sheetHead(Integer tabColor, boolean selected) {
        StringBuilder s = new StringBuilder();
        s.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
                .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
        if (tabColor != null) s.append("<sheetPr><tabColor rgb=\"FF").append(hex(tabColor)).append("\"/></sheetPr>");
        s.append("<sheetViews><sheetView ").append(selected ? "tabSelected=\"1\" " : "").append("workbookViewId=\"0\">")
                .append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
                .append("<selection pane=\"bottomLeft\" activeCell=\"A2\" sqref=\"A2\"/>")
                .append("</sheetView></sheetViews>")
                .append("<sheetFormatPr defaultRowHeight=\"15\"/><cols>");
        for (int i = 0; i < WIDTHS.length; i++) {
            s.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"")
                    .append(WIDTHS[i]).append("\" customWidth=\"1\"/>");
        }
        s.append("</cols><sheetData>");
        s.append("<row r=\"1\">");
        for (int i = 0; i < HEADER.length; i++) str(s, i, 1, HEADER[i], 0);
        s.append("</row>");
        return s.toString();
    }

    private static String sheet(Vehicle v, List<Trip> trips, int vStyle, boolean selected) {
        StringBuilder s = new StringBuilder(sheetHead(trips.isEmpty() ? v.color : null, selected));
        int r = 2;
        for (Trip t : trips) {
            int lines = Math.max(1, t.zwischen.size());
            s.append("<row r=\"").append(r).append("\"");
            if (lines > 1) s.append(" ht=\"").append(15 * lines).append("\" customHeight=\"1\"");
            s.append(">");
            str(s, 0, r, v.name, vStyle);
            if (t.start != null) num(s, 1, r, Dt.toSerial(t.start), STYLE_DATE);
            if (t.end != null) num(s, 2, r, Dt.toSerial(t.end), STYLE_DATE);
            if (t.kmStart != null) num(s, 3, r, t.kmStart, STYLE_INT);
            if (t.kmEnd != null) num(s, 4, r, t.kmEnd, STYLE_INT);
            str(s, 5, r, t.startOrt, 0);
            str(s, 6, r, String.join("\n", t.zwischen), STYLE_WRAP);
            str(s, 7, r, t.ziel, 0);
            str(s, 8, r, t.zuordnung, 0);
            s.append("</row>");
            r++;
        }
        s.append("</sheetData></worksheet>");
        return s.toString();
    }

    private static String placeholderSheet() {
        String s = sheetHead(null, true);
        // Blatt ohne Fahrzeug: A1 enthält nicht "Auto", wird beim Einlesen ignoriert.
        s = s.replace(">Auto<", ">Keine Fahrzeuge angelegt<");
        return s + "</sheetData></worksheet>";
    }

    private static void str(StringBuilder s, int col, int row, String text, int style) {
        if (text == null || text.isEmpty()) {
            if (style != 0) s.append("<c r=\"").append(ref(col, row)).append("\" s=\"").append(style).append("\"/>");
            return;
        }
        s.append("<c r=\"").append(ref(col, row)).append("\"");
        if (style != 0) s.append(" s=\"").append(style).append("\"");
        s.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(esc(text)).append("</t></is></c>");
    }

    private static void num(StringBuilder s, int col, int row, double value, int style) {
        s.append("<c r=\"").append(ref(col, row)).append("\" s=\"").append(style).append("\"><v>");
        if (value == Math.rint(value)) s.append((long) value);
        else s.append(BigDecimalString.of(value));
        s.append("</v></c>");
    }

    /** Dezimaldarstellung ohne Exponent. */
    private static final class BigDecimalString {
        static String of(double d) {
            return new java.math.BigDecimal(Double.toString(d)).toPlainString();
        }
    }

    private static String ref(int col, int row) {
        return String.valueOf((char) ('A' + col)) + row;
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                case '\r': break;
                case '\n': case '\t': b.append(c); break;
                default:
                    if (c >= 0x20 && c != 0xFFFE && c != 0xFFFF) b.append(c);
            }
        }
        return b.toString();
    }

    private static void put(ZipOutputStream z, String name, String content) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }
}
