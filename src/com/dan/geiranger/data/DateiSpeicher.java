package com.dan.geiranger.data;

import com.dan.geiranger.scene.Regie;
import com.dan.store.DateiAblage;
import com.dan.store.Felder;
import com.dan.store.Zahl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Speichern ohne Datenbank (F25 A): Textdateien im Ordner {@code daten/} des Projekts (aus Rungholt). Die
 * mitgelieferten Einträge kommen aus {@link Startdaten} und stehen nicht in Dateien; eigene liegen je in einer Datei.
 * Die Chronik und die Merkpunkte liegen als CSV mit Semikolon und einer Spalte {@code WKT} in Länge und Breite (QGIS:
 * „Getrennte Texte“, Geometrie WKT, EPSG:4326):
 * <pre>
 * daten/chronik/stunden.csv  tage.csv  schiffsfahrten.csv  busse.csv  regielaeufe.csv  merkpunkte.csv (+ merkpunkte/N.png)
 * daten/regiebuecher/*.txt   daten/ansichten/N.txt + N.zustand + N.png   daten/presets/paket/Klasse/*.txt
 * daten/sitzungen/N.txt + N.zustand   daten/protokoll.txt
 * </pre>
 * Beim nächsten Verbinden übernimmt {@link Uebernahme} die Chronik in die Datenbank und benennt die CSV in
 * {@code *.uebernommen} um.
 */
public final class DateiSpeicher extends DateiAblage implements Speicher {

    public DateiSpeicher(File dir) { super(dir); }

    /** Der Ordner daten neben db (oder -Dgeiranger.daten). */
    public static File ordner() {
        String p = System.getProperty("geiranger.daten");
        if (p != null && !p.isEmpty()) return new File(p);
        File db = Verbindung.ordner().getAbsoluteFile();
        File base = db.getParentFile();
        return new File(base != null ? base : new File("."), "daten");
    }

    private static void eigen(boolean mitgeliefert, String was) throws Fehler {
        if (mitgeliefert) throw new Fehler("„" + was + "“ ist mitgeliefert und wird nicht überschrieben; bitte unter einem eigenen Namen speichern");
    }

    // ------------------------------------------------------------------ CSV

    /** Eine CSV-Datei: Semikolon, Text in Anführungszeichen, erste Zeile die Spalten. */
    static final class Csv {
        final File f;
        final String[] spalten;
        List<LinkedHashMap<String, String>> zeilen;

        Csv(File f, String... spalten) { this.f = f; this.spalten = spalten; }

        synchronized List<LinkedHashMap<String, String>> lesen() throws Fehler {
            if (zeilen != null) return zeilen;
            zeilen = new ArrayList<>();
            if (!f.isFile()) return zeilen;
            try {
                List<String> l = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                if (l.isEmpty()) return zeilen;
                String[] kopf = teile(l.get(0));
                for (int i = 1; i < l.size(); i++) {
                    if (l.get(i).isBlank()) continue;
                    String[] v = teile(l.get(i));
                    LinkedHashMap<String, String> m = new LinkedHashMap<>();
                    for (int k = 0; k < kopf.length; k++) m.put(kopf[k], k < v.length ? v[k] : "");
                    zeilen.add(m);
                }
                return zeilen;
            } catch (IOException e) {
                throw new Fehler(f + " nicht lesbar: " + e.getMessage(), e);
            }
        }

        synchronized void schreiben() throws Fehler {
            StringBuilder b = new StringBuilder(String.join(";", spalten)).append('\n');
            for (Map<String, String> m : zeilen) {
                for (int k = 0; k < spalten.length; k++) b.append(k > 0 ? ";" : "").append(feld(m.getOrDefault(spalten[k], "")));
                b.append('\n');
            }
            schreibe(f, b.toString());
        }

        synchronized long anhaengen(LinkedHashMap<String, String> m) throws Fehler {
            long id = naechste();
            LinkedHashMap<String, String> z = new LinkedHashMap<>();
            z.put("ID", Long.toString(id));
            z.putAll(m);
            lesen().add(z);
            schreiben();
            return id;
        }

        synchronized long naechste() throws Fehler {
            long n = 0;
            for (Map<String, String> m : lesen()) n = Math.max(n, (long) Zahl.lies(m.getOrDefault("ID", "0")));
            return n + 1;
        }

        static String feld(String v) {
            if (v == null) return "";
            if (v.indexOf(';') < 0 && v.indexOf('"') < 0 && v.indexOf('\n') < 0 && v.indexOf(',') < 0) return v;
            return "\"" + v.replace("\"", "\"\"").replace("\n", " ") + "\"";
        }

        static String[] teile(String z) {
            List<String> l = new ArrayList<>();
            StringBuilder b = new StringBuilder();
            boolean q = false;
            for (int i = 0; i < z.length(); i++) {
                char c = z.charAt(i);
                if (q) {
                    if (c == '"' && i + 1 < z.length() && z.charAt(i + 1) == '"') { b.append('"'); i++; }
                    else if (c == '"') q = false;
                    else b.append(c);
                } else if (c == '"') q = true;
                else if (c == ';') { l.add(b.toString()); b.setLength(0); }
                else b.append(c);
            }
            l.add(b.toString());
            return l.toArray(new String[0]);
        }
    }

    private Csv stunden, tage, fahrten, busse, regie, merk;

    private synchronized Csv st() {
        if (stunden == null) stunden = new Csv(sub("chronik", "stunden.csv"), "ID", "SITZUNG", "SZENE_MS", "SZENE_TEXT", "SZENE_TAG", "LAGE", "WIND_MS", "RICHTUNG_GRAD",
                "BOEE_MS", "BEDECKUNG_ACHTEL", "WOLKEN", "REGEN_MMH", "SICHT_KM", "NEBEL", "TEMPERATUR_C", "ABFLUSS_ANTEIL", "REGENSPEICHER_MM", "FALL_TEMP_C", "EIS", "ABFLUSS");
        return stunden;
    }
    private synchronized Csv ta() {
        if (tage == null) tage = new Csv(sub("chronik", "tage.csv"), "ID", "SITZUNG", "SZENE_MS", "SZENE_TEXT", "SZENE_TAG", "SCHNEEGRENZE_M", "SCHNEE_ORT_CM", "SCHNEE_600_CM",
                "SCHNEE_1200_CM", "LAUB", "HERBST", "KRONEN", "EIS", "LUFT_C");
        return tage;
    }
    private synchronized Csv fa() {
        if (fahrten == null) fahrten = new Csv(sub("chronik", "schiffsfahrten.csv"), "ID", "SITZUNG", "SCHIFF", "RICHTUNG", "SZENE_MS", "SZENE_TEXT", "SZENE_TAG", "AN_MS",
                "AN_TEXT", "DAUER_MIN", "LAENGE_M", "HOERNER", "HORN_X", "HORN_Z", "HORN_TEXT", "ECHOS", "ECHO_TEXT", "WKT");
        return fahrten;
    }
    private synchronized Csv bu() {
        if (busse == null) busse = new Csv(sub("chronik", "busse.csv"), "ID", "SITZUNG", "SZENE_MS", "SZENE_TEXT", "SZENE_TAG", "AB_MS", "HALT", "GRUPPE", "X_M", "Z_M", "WKT");
        return busse;
    }
    private synchronized Csv rl() {
        if (regie == null) regie = new Csv(sub("chronik", "regielaeufe.csv"), "ID", "SITZUNG", "NAME", "BEGINN_MS", "ENDE_MS", "VOLLSTAENDIG", "KINO", "BILDER_S_MITTEL",
                "GPU_MS_MITTEL", "GEWARTET_S", "FOTOS", "QUALITAET", "BLATT_DATEI");
        return regie;
    }
    private synchronized Csv mp() {
        if (merk == null) merk = new Csv(sub("chronik", "merkpunkte.csv"), "ID", "SITZUNG", "NAME", "NOTIZ", "X_M", "Y_M", "Z_M", "AUGE_X", "AUGE_Y", "AUGE_Z", "BLICK_GRAD",
                "NICK_GRAD", "SZENE_MS", "SZENE_TEXT", "WKT");
        return merk;
    }

    /** Die Dateien der Chronik und der Merkpunkte (für die Übernahme). */
    public List<File> chronikDateien() { return List.of(st().f, ta().f, fa().f, bu().f, rl().f, mp().f); }

    private static String n(double v) { return Double.isNaN(v) ? "" : Zahl.text(Math.round(v * 1000) / 1000.0); }
    private static String n(Double v) { return v == null ? "" : n((double) v); }
    private static double d(String s) { return s == null || s.isEmpty() ? Double.NaN : Zahl.lies(s); }
    private static Long ln(String s) { return s == null || s.isEmpty() ? null : (long) Zahl.lies(s); }
    private static Integer in(String s) { return s == null || s.isEmpty() ? null : (int) Zahl.lies(s); }
    private static String s(Long v) { return v == null ? "" : v.toString(); }
    private static String s(Integer v) { return v == null ? "" : v.toString(); }
    private static String t(String v) { return v == null ? "" : v; }

    private static void sz(Map<String, String> m, long ms) {
        m.put("SZENE_MS", Long.toString(ms));
        m.put("SZENE_TEXT", Daten.text(ms));
        m.put("SZENE_TAG", Daten.tag(ms));
    }

    // ------------------------------------------------------------------ Stand und Stammdaten

    @Override public Daten.Stand stand() throws Fehler {
        return new Daten.Stand(faelle().size(), 0, 0, fahrplan().size(), st().lesen().size(), ta().lesen().size(), fa().lesen().size(), bu().lesen().size(),
                regiebuecher().size(), rl().lesen().size(), ansichten().size(), mp().lesen().size(), presets(null, null).size(), befunde().size(), quellen().size(),
                letzteSitzung(-1));
    }

    @Override public List<Daten.Quelle> quellen() { return Startdaten.quellen(); }
    @Override public List<Daten.Befund> befunde() { return Startdaten.befunde(); }
    @Override public List<Daten.Fall> faelle() { return Startdaten.faelleLeicht(); }
    @Override public List<Daten.Ort> orte() { return Startdaten.orteLeicht(); }
    @Override public List<Daten.Fahrplan> fahrplan() { return Startdaten.fahrplan(); }
    @Override public List<Daten.KlimaMonat> klima() { return Startdaten.klima(); }
    @Override public List<Daten.Hoehenstufe> hoehenstufen() { return Startdaten.hoehenstufen(); }
    @Override public List<Daten.Wetterbuch> wetterbuecher() { return Startdaten.wetterbuecher(); }

    // ------------------------------------------------------------------ Chronik

    @Override public void stunde(Daten.Stunde h, Long sitzung) throws Fehler {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("SITZUNG", s(sitzung));
        sz(m, h.ms());
        m.put("LAGE", t(h.lage()));
        m.put("WIND_MS", n(h.windMs()));
        m.put("RICHTUNG_GRAD", n(h.richtungGrad()));
        m.put("BOEE_MS", n(h.boeeMs()));
        m.put("BEDECKUNG_ACHTEL", n(h.bedeckung()));
        m.put("WOLKEN", t(h.wolken()));
        m.put("REGEN_MMH", n(h.regenMmH()));
        m.put("SICHT_KM", n(h.sichtKm()));
        m.put("NEBEL", n(h.nebel()));
        m.put("TEMPERATUR_C", n(h.temperaturC()));
        m.put("ABFLUSS_ANTEIL", n(h.abflussAnteil()));
        m.put("REGENSPEICHER_MM", n(h.regenspeicherMm()));
        m.put("FALL_TEMP_C", n(h.fallTempC()));
        m.put("EIS", n(h.eis()));
        m.put("ABFLUSS", t(h.abfluss()));
        st().anhaengen(m);
    }

    @Override public void tag(Daten.Tag g, Long sitzung) throws Fehler {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("SITZUNG", s(sitzung));
        sz(m, g.ms());
        m.put("SCHNEEGRENZE_M", n(g.schneegrenzeM()));
        m.put("SCHNEE_ORT_CM", n(g.schneeOrtCm()));
        m.put("SCHNEE_600_CM", n(g.schnee600Cm()));
        m.put("SCHNEE_1200_CM", n(g.schnee1200Cm()));
        m.put("LAUB", n(g.laub()));
        m.put("HERBST", n(g.herbst()));
        m.put("KRONEN", n(g.kronen()));
        m.put("EIS", n(g.eis()));
        m.put("LUFT_C", n(g.luftC()));
        ta().anhaengen(m);
    }

    @Override public long schiffsfahrt(Daten.Schiffsfahrt f, Long sitzung) throws Fehler {
        Csv c = fa();
        synchronized (c) {
            LinkedHashMap<String, String> m = new LinkedHashMap<>();
            long id = f.id() > 0 ? f.id() : c.naechste();
            m.put("ID", Long.toString(id));
            m.put("SITZUNG", s(sitzung));
            m.put("SCHIFF", f.schiff());
            m.put("RICHTUNG", f.richtung());
            sz(m, f.abMs());
            m.put("AN_MS", s(f.anMs()));
            m.put("AN_TEXT", f.anMs() == null ? "" : Daten.text(f.anMs()));
            m.put("DAUER_MIN", n(f.dauerMin()));
            m.put("LAENGE_M", f.weg() == null ? "" : n(Math.round(f.weg().laenge() * 10) / 10.0));
            m.put("HOERNER", Integer.toString(f.hoerner()));
            m.put("HORN_X", f.horn() == null ? "" : n(f.horn()[0]));
            m.put("HORN_Z", f.horn() == null ? "" : n(f.horn()[1]));
            m.put("HORN_TEXT", t(f.hornText()));
            m.put("ECHOS", s(f.echos()));
            m.put("ECHO_TEXT", t(f.echoText()));
            m.put("WKT", f.weg() == null ? "" : f.weg().wkt());
            String sid = Long.toString(id);
            boolean da = false;
            for (int i = 0; i < c.lesen().size(); i++) if (sid.equals(c.lesen().get(i).get("ID"))) { c.lesen().set(i, m); da = true; break; }
            if (!da) c.lesen().add(m);
            c.schreiben();
            return id;
        }
    }

    @Override public void busankunft(Daten.Busankunft b, Long sitzung) throws Fehler {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("SITZUNG", s(sitzung));
        sz(m, b.ms());
        m.put("AB_MS", Long.toString(b.abMs()));
        m.put("HALT", t(b.halt()));
        m.put("GRUPPE", Integer.toString(b.gruppe()));
        m.put("X_M", n(Math.round(b.x() * 10) / 10.0));
        m.put("Z_M", n(Math.round(b.z() * 10) / 10.0));
        m.put("WKT", Double.isNaN(b.x()) ? "" : Daten.Geom.punkt(b.x(), b.z()).wkt());
        bu().anhaengen(m);
    }

    @Override public void regielauf(Daten.Regielauf l, Long sitzung) throws Fehler {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("SITZUNG", s(sitzung));
        m.put("NAME", l.name());
        m.put("BEGINN_MS", Long.toString(l.beginnMs()));
        m.put("ENDE_MS", s(l.endeMs()));
        m.put("VOLLSTAENDIG", l.vollstaendig() ? "1" : "0");
        m.put("KINO", l.kino() ? "1" : "0");
        m.put("BILDER_S_MITTEL", n(l.bilderMittel()));
        m.put("GPU_MS_MITTEL", n(l.gpuMs()));
        m.put("GEWARTET_S", n(l.gewartetS()));
        m.put("FOTOS", Integer.toString(l.fotos()));
        m.put("QUALITAET", t(l.qualitaet()));
        m.put("BLATT_DATEI", t(l.blattDatei()));
        rl().anhaengen(m);
    }

    static Daten.Stunde stunde(Map<String, String> m) {
        return new Daten.Stunde((long) d(m.get("SZENE_MS")), m.get("LAGE"), d(m.get("WIND_MS")), d(m.get("RICHTUNG_GRAD")), d(m.get("BOEE_MS")), d(m.get("BEDECKUNG_ACHTEL")),
                m.get("WOLKEN"), d(m.get("REGEN_MMH")), d(m.get("SICHT_KM")), d(m.get("NEBEL")), d(m.get("TEMPERATUR_C")), d(m.get("ABFLUSS_ANTEIL")), d(m.get("REGENSPEICHER_MM")),
                d(m.get("FALL_TEMP_C")), d(m.get("EIS")), m.get("ABFLUSS"));
    }

    static Daten.Tag tag(Map<String, String> m) {
        double g = d(m.get("SCHNEEGRENZE_M"));
        return new Daten.Tag((long) d(m.get("SZENE_MS")), Double.isNaN(g) ? null : g, d(m.get("SCHNEE_ORT_CM")), d(m.get("SCHNEE_600_CM")), d(m.get("SCHNEE_1200_CM")),
                d(m.get("LAUB")), d(m.get("HERBST")), d(m.get("KRONEN")), d(m.get("EIS")), d(m.get("LUFT_C")));
    }

    static Daten.Schiffsfahrt fahrt(Map<String, String> m, boolean weg) {
        double hx = d(m.get("HORN_X")), hz = d(m.get("HORN_Z"));
        return new Daten.Schiffsfahrt((long) d(m.get("ID")), m.get("SCHIFF"), m.get("RICHTUNG"), (long) d(m.get("SZENE_MS")), ln(m.get("AN_MS")),
                weg ? Daten.Geom.ausWkt(m.get("WKT")) : null, (int) Math.max(0, d(m.get("HOERNER"))), Double.isNaN(hx) ? null : new double[]{hx, hz},
                m.get("HORN_TEXT") == null || m.get("HORN_TEXT").isEmpty() ? null : m.get("HORN_TEXT"), in(m.get("ECHOS")),
                m.get("ECHO_TEXT") == null || m.get("ECHO_TEXT").isEmpty() ? null : m.get("ECHO_TEXT"));
    }

    static Daten.Busankunft bus(Map<String, String> m) {
        return new Daten.Busankunft((long) d(m.get("SZENE_MS")), (long) d(m.get("AB_MS")), m.get("HALT"), (int) d(m.get("GRUPPE")), d(m.get("X_M")), d(m.get("Z_M")));
    }

    static Daten.Regielauf lauf(Map<String, String> m) {
        return new Daten.Regielauf(m.get("NAME"), (long) d(m.get("BEGINN_MS")), ln(m.get("ENDE_MS")), "1".equals(m.get("VOLLSTAENDIG")), "1".equals(m.get("KINO")),
                d(m.get("BILDER_S_MITTEL")), d(m.get("GPU_MS_MITTEL")), d(m.get("GEWARTET_S")), (int) Math.max(0, d(m.get("FOTOS"))), m.get("QUALITAET"), null,
                m.get("BLATT_DATEI"));
    }

    @Override public Daten.Tageschronik tagesChronik(LocalDate tag) throws Fehler {
        String tg = tag.toString();
        List<Daten.Stunde> hs = new ArrayList<>();
        List<Daten.Tag> ts = new ArrayList<>();
        List<Daten.Schiffsfahrt> fs = new ArrayList<>();
        List<Daten.Busankunft> bs = new ArrayList<>();
        Csv c = st();
        synchronized (c) { for (Map<String, String> m : c.lesen()) if (tg.equals(m.get("SZENE_TAG"))) hs.add(stunde(m)); }
        c = ta();
        synchronized (c) { for (Map<String, String> m : c.lesen()) if (tg.equals(m.get("SZENE_TAG"))) ts.add(tag(m)); }
        c = fa();
        synchronized (c) { for (Map<String, String> m : c.lesen()) if (tg.equals(m.get("SZENE_TAG"))) fs.add(fahrt(m, true)); }
        c = bu();
        synchronized (c) { for (Map<String, String> m : c.lesen()) if (tg.equals(m.get("SZENE_TAG"))) bs.add(bus(m)); }
        hs.sort((a, b) -> Long.compare(a.ms(), b.ms()));
        fs.sort((a, b) -> Long.compare(a.abMs(), b.abMs()));
        bs.sort((a, b) -> Long.compare(a.ms(), b.ms()));
        return new Daten.Tageschronik(tag, hs, ts, fs, bs);
    }

    @Override public List<Daten.Schiffsfahrt> fahrten(int n) throws Fehler {
        List<Daten.Schiffsfahrt> l = new ArrayList<>();
        Csv c = fa();
        synchronized (c) {
            List<LinkedHashMap<String, String>> z = c.lesen();
            for (int i = z.size() - 1; i >= 0 && l.size() < n; i--) l.add(fahrt(z.get(i), false));
        }
        return l;
    }

    /** Alle Zeilen der Chronik (für die Übernahme). */
    public List<Daten.Stunde> stundenAlle() throws Fehler { List<Daten.Stunde> l = new ArrayList<>(); for (Map<String, String> m : st().lesen()) l.add(stunde(m)); return l; }
    public List<Daten.Tag> tageAlle() throws Fehler { List<Daten.Tag> l = new ArrayList<>(); for (Map<String, String> m : ta().lesen()) l.add(tag(m)); return l; }
    public List<Daten.Schiffsfahrt> fahrtenAlle() throws Fehler { List<Daten.Schiffsfahrt> l = new ArrayList<>(); for (Map<String, String> m : fa().lesen()) l.add(fahrt(m, true)); return l; }
    public List<Daten.Busankunft> busseAlle() throws Fehler { List<Daten.Busankunft> l = new ArrayList<>(); for (Map<String, String> m : bu().lesen()) l.add(bus(m)); return l; }
    public List<Daten.Regielauf> regielaeufe() throws Fehler { List<Daten.Regielauf> l = new ArrayList<>(); for (Map<String, String> m : rl().lesen()) l.add(lauf(m)); return l; }

    @Override public int chronikLeeren(long sitzung) throws Fehler {
        int n = 0;
        String sid = Long.toString(sitzung);
        for (Csv c : new Csv[]{st(), ta(), fa(), bu(), rl()}) {
            synchronized (c) {
                int vor = c.lesen().size();
                c.lesen().removeIf(m -> sid.equals(m.get("SITZUNG")));
                n += vor - c.lesen().size();
                if (vor != c.lesen().size()) c.schreiben();
            }
        }
        return n;
    }

    @Override public List<long[]> sitzungenMitChronik(int n) throws Fehler {
        Map<Long, long[]> m = new java.util.TreeMap<>(Collections.reverseOrder());
        for (Map<String, String> z : st().lesen()) { Long s = ln(z.get("SITZUNG")); if (s != null) m.computeIfAbsent(s, k -> new long[]{k, 0, 0, 0})[2]++; }
        for (Map<String, String> z : fa().lesen()) { Long s = ln(z.get("SITZUNG")); if (s != null) m.computeIfAbsent(s, k -> new long[]{k, 0, 0, 0})[3]++; }
        for (Csv c : new Csv[]{ta(), bu(), rl()})
            for (Map<String, String> z : c.lesen()) { Long s = ln(z.get("SITZUNG")); if (s != null) m.computeIfAbsent(s, k -> new long[]{k, 0, 0, 0}); }
        List<long[]> l = new ArrayList<>();
        for (long[] v : m.values()) {
            try { v[1] = Long.parseLong(sitzung(v[0]).getOrDefault("beginn", "0")); } catch (Fehler | RuntimeException e) { v[1] = 0; }
            l.add(v);
            if (l.size() >= n) break;
        }
        return l;
    }

    // ------------------------------------------------------------------ Regiebücher und Fahrten

    @Override public List<Daten.Regiebuch> regiebuecher() throws Fehler {
        List<Daten.Regiebuch> l = new ArrayList<>(Startdaten.regiebuecher());
        for (File f : dateien(sub("regiebuecher"))) {
            try {
                String t = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                Regie.Book b = Regie.parse(t);
                l.add(new Daten.Regiebuch(b.name(), t, b.about(), null, Daten.EIGEN));
            } catch (IOException | RuntimeException e) {
                com.dan.store.Protokoll.warnung("Dateien", "Regiebuch " + f.getName() + " nicht lesbar: " + e.getMessage());
            }
        }
        return l;
    }

    private static boolean mitgeliefertesBuch(String name) {
        for (Daten.Regiebuch r : Startdaten.regiebuecher()) if (r.name().equals(name)) return true;
        return false;
    }

    @Override public void speichern(Daten.Regiebuch r) throws Fehler {
        eigen(mitgeliefertesBuch(r.name()), r.name());
        try { Regie.parse(r.text()); } catch (RuntimeException e) { throw new Fehler("Regiebuch nicht lesbar: " + e.getMessage(), e); }
        schreibe(sub("regiebuecher", dateiname(r.name())), r.text());
    }

    @Override public void loeschenRegiebuch(String name) throws Fehler { eigen(mitgeliefertesBuch(name), name); loeschen("regiebuecher", name); }

    @Override public List<Daten.Fahrt> fahrten() { return Startdaten.fahrten(); }

    // ------------------------------------------------------------------ Ansichten

    private static final String[] KAM = {"x", "y", "z", "gier", "nick"};

    private static Daten.Ansicht ansichtAus(Map<String, String> m, String zustand, byte[] png) {
        double[] k = new double[5];
        for (int i = 0; i < 5; i++) k[i] = Zahl.lies(m.get("kamera." + KAM[i]));
        String sm = m.get("szeneMs");
        return new Daten.Ansicht(Long.parseLong(m.get("id")), m.get("name"), null, "GEMERKT", k, null, sm == null || sm.isEmpty() ? null : Long.parseLong(sm), zustand, png,
                Daten.EIGEN);
    }

    @Override public List<Daten.Ansicht> ansichten() throws Fehler {
        List<Daten.Ansicht> l = new ArrayList<>(Startdaten.ansichten());
        List<Daten.Ansicht> g = new ArrayList<>();
        for (File f : dateien(sub("ansichten"))) {
            LinkedHashMap<String, String> m = lies(f);
            if (m.get("id") != null) g.add(ansichtAus(m, null, null));
        }
        g.sort((a, b) -> Long.compare(b.id(), a.id()));
        l.addAll(g);
        return l;
    }

    @Override public Daten.Ansicht ansicht(long id) throws Fehler {
        File f = sub("ansichten", id + ".txt");
        if (!f.isFile()) throw new Fehler("Ansicht " + id + " gibt es nicht");
        LinkedHashMap<String, String> m = lies(f);
        File z = sub("ansichten", id + ".zustand"), p = sub("ansichten", id + ".png");
        try {
            return ansichtAus(m, z.isFile() ? Files.readString(z.toPath(), StandardCharsets.UTF_8) : null, p.isFile() ? Files.readAllBytes(p.toPath()) : null);
        } catch (IOException e) {
            throw new Fehler("Ansicht " + id + " nicht lesbar: " + e.getMessage(), e);
        }
    }

    @Override public long speichern(Daten.Ansicht a, Long sitzung) throws Fehler {
        long id = naechsteNummer(sub("ansichten"));
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("id", Long.toString(id));
        m.put("name", a.name());
        for (int i = 0; i < 5; i++) m.put("kamera." + KAM[i], Zahl.text(a.kamera()[i]));
        if (a.szeneMs() != null) { m.put("szeneMs", a.szeneMs().toString()); m.put("szeneText", Daten.text(a.szeneMs())); }
        if (sitzung != null) m.put("sitzung", sitzung.toString());
        schreibe(sub("ansichten", id + ".txt"), Felder.alsText(m, "Ansicht " + a.name()));
        if (a.zustand() != null) schreibe(sub("ansichten", id + ".zustand"), a.zustand());
        if (a.vorschau() != null) schreibe(sub("ansichten", id + ".png"), a.vorschau());
        return id;
    }

    @Override public void loeschenAnsicht(long id) throws Fehler {
        loesche(sub("ansichten", id + ".txt"), "Ansicht " + id);
        loesche(sub("ansichten", id + ".zustand"), "Ansicht " + id);
        loesche(sub("ansichten", id + ".png"), "Ansicht " + id);
    }

    // ------------------------------------------------------------------ Merkpunkte

    private Daten.Merkpunkt merk(Map<String, String> m, boolean png) {
        long id = (long) d(m.get("ID"));
        double ax = d(m.get("AUGE_X"));
        byte[] p = null;
        if (png) {
            File f = sub("chronik", "merkpunkte", id + ".png");
            try { if (f.isFile()) p = Files.readAllBytes(f.toPath()); } catch (IOException e) { /* ohne Vorschau */ }
        }
        return new Daten.Merkpunkt(id, m.get("NAME"), m.get("NOTIZ"), d(m.get("X_M")), d(m.get("Y_M")), d(m.get("Z_M")),
                Double.isNaN(ax) ? null : new double[]{ax, d(m.get("AUGE_Y")), d(m.get("AUGE_Z"))}, d(m.get("BLICK_GRAD")), d(m.get("NICK_GRAD")), ln(m.get("SZENE_MS")), p,
                Daten.EIGEN);
    }

    @Override public List<Daten.Merkpunkt> merkpunkte() throws Fehler {
        List<Daten.Merkpunkt> l = new ArrayList<>();
        Csv c = mp();
        synchronized (c) { for (Map<String, String> m : c.lesen()) l.add(merk(m, false)); }
        Collections.reverse(l);
        return l;
    }

    @Override public Daten.Merkpunkt merkpunkt(long id) throws Fehler {
        Csv c = mp();
        synchronized (c) {
            for (Map<String, String> m : c.lesen()) if ((long) d(m.get("ID")) == id) return merk(m, true);
        }
        throw new Fehler("Merkpunkt " + id + " gibt es nicht");
    }

    @Override public long speichern(Daten.Merkpunkt p, Long sitzung) throws Fehler {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("SITZUNG", s(sitzung));
        m.put("NAME", p.name());
        m.put("NOTIZ", t(p.notiz()));
        m.put("X_M", n(Math.round(p.x() * 10) / 10.0));
        m.put("Y_M", n(Math.round(p.y() * 10) / 10.0));
        m.put("Z_M", n(Math.round(p.z() * 10) / 10.0));
        double[] a = p.auge();
        m.put("AUGE_X", a == null ? "" : n(a[0]));
        m.put("AUGE_Y", a == null ? "" : n(a[1]));
        m.put("AUGE_Z", a == null ? "" : n(a[2]));
        m.put("BLICK_GRAD", n(p.blick()));
        m.put("NICK_GRAD", n(p.nick()));
        m.put("SZENE_MS", s(p.szeneMs()));
        m.put("SZENE_TEXT", p.szeneMs() == null ? "" : Daten.text(p.szeneMs()));
        m.put("WKT", Daten.Geom.punkt(p.x(), p.z()).wkt());
        long id = mp().anhaengen(m);
        if (p.vorschau() != null) schreibe(sub("chronik", "merkpunkte", id + ".png"), p.vorschau());
        return id;
    }

    @Override public void loeschenMerkpunkt(long id) throws Fehler {
        Csv c = mp();
        synchronized (c) {
            String sid = Long.toString(id);
            if (!c.lesen().removeIf(m -> sid.equals(m.get("ID")))) throw new Fehler("Merkpunkt " + id + " gibt es nicht");
            c.schreiben();
        }
        File f = sub("chronik", "merkpunkte", id + ".png");
        if (f.isFile()) loesche(f, "Merkpunkt " + id);
    }

    /** Alle Merkpunkte mit Vorschau (für die Übernahme). */
    public List<Daten.Merkpunkt> merkpunkteAlle() throws Fehler {
        List<Daten.Merkpunkt> l = new ArrayList<>();
        for (Map<String, String> m : mp().lesen()) l.add(merk(m, true));
        return l;
    }

    // ------------------------------------------------------------------ Presets

    @Override public List<Daten.PaketPreset> presets(String paket, String klasse) throws Fehler {
        List<Daten.PaketPreset> l = new ArrayList<>();
        for (Daten.PaketPreset p : Startdaten.presets())
            if ((paket == null || p.paket().equals(paket)) && (klasse == null || p.klasse().equals(klasse))) l.add(p);
        File base = sub("presets");
        File[] ps = base.listFiles(File::isDirectory);
        if (ps == null) return l;
        for (File pd : ps) {
            if (paket != null && !pd.getName().equals(paket)) continue;
            File[] ks = pd.listFiles(File::isDirectory);
            if (ks == null) continue;
            for (File kd : ks) {
                if (klasse != null && !kd.getName().equals(klasse)) continue;
                for (File f : dateien(kd)) {
                    LinkedHashMap<String, String> m = lies(f);
                    String name = m.remove("preset.name"), about = m.remove("preset.beschreibung");
                    l.add(new Daten.PaketPreset(pd.getName(), kd.getName(), name == null ? f.getName().replace(".txt", "") : name, about, Daten.EIGEN, m));
                }
            }
        }
        return l;
    }

    private static boolean presetMitgeliefert(String paket, String klasse, String name) {
        for (Daten.PaketPreset p : Startdaten.presets()) if (p.paket().equals(paket) && p.klasse().equals(klasse) && p.name().equals(name)) return true;
        return false;
    }

    @Override public void speichern(Daten.PaketPreset p) throws Fehler {
        eigen(presetMitgeliefert(p.paket(), p.klasse(), p.name()), p.name());
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("preset.name", p.name());
        m.put("preset.beschreibung", p.beschreibung() == null ? "" : p.beschreibung());
        m.putAll(p.felder());
        schreibe(sub("presets", p.paket(), p.klasse(), dateiname(p.name())), Felder.alsText(m, "Preset " + p.paket() + "." + p.klasse() + " · " + p.name()));
    }

    @Override public void loeschenPreset(String paket, String klasse, String name) throws Fehler {
        eigen(presetMitgeliefert(paket, klasse, name), name);
        loesche(sub("presets", paket, klasse, dateiname(name)), name);
    }

    // ------------------------------------------------------------------ Sitzung

    @Override public Daten.Sitzung letzteSitzung(long ausser) throws Fehler {
        LinkedHashMap<String, String> m = super.letzteSitzungText(ausser);
        if (m == null) return null;
        String ende = m.get("ende"), zz = m.get("zustandZeit"), ton = m.get(TON);
        double rs = Zahl.lies(m.get(REGIE_SEKUNDE)), ms = Zahl.lies(m.get(SZENE_MS)), zr = Zahl.lies(m.get(ZEITRAFFER));
        return new Daten.Sitzung(Long.parseLong(m.get("id")), Long.parseLong(m.get("beginn")), ende == null || ende.isEmpty() ? null : Long.parseLong(ende),
                "true".equals(m.get("sauber")), m.get("zustand"), zz == null ? null : Long.parseLong(zz), leer(m.get(REGIEBUCH)), Double.isNaN(rs) ? null : rs,
                Double.isNaN(ms) ? null : (long) ms, Double.isNaN(zr) ? null : zr, leer(m.get(QUALITAET)), ton == null || ton.isEmpty() ? null : Boolean.parseBoolean(ton));
    }

    private static String leer(String s) { return s == null || s.isEmpty() ? null : s; }
}
