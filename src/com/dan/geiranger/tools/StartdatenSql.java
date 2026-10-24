package com.dan.geiranger.tools;

import com.dan.geiranger.data.Daten;
import com.dan.geiranger.data.Startdaten;
import com.dan.geiranger.data.Verbindung;
import com.dan.geiranger.scene.Regie;
import com.dan.geiranger.scene.Terrain;
import com.dan.store.Zahl;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Schreibt die mitgelieferten Daten als SQL: {@code db/sql/04_startdaten.sql} (Phase 17, F26 A: der Code bleibt die
 * Quelle; nicht von Hand ändern). MERGE je Eintrag mit Schlüssel; Kinder (Belege, Strähnen, Stichworte, Stützpunkte,
 * Werte) und die Gebäude werden gelöscht und neu angelegt. Eigene Zeilen (HERKUNFT = 'EIGEN') bleiben unberührt.
 * Beliebig oft wiederholbar.
 * <p>
 * Geometrien: Punkte als {@code SDO_POINT_TYPE}, Linien und Flächen als WKT über {@code SDO_GEOMETRY(wkt, 8307)}; lange
 * Texte (WKT, Regiebücher) in Stücken zu höchstens 1 000 Zeichen als {@code TO_CLOB(…) || TO_CLOB(…)}; sehr lange WKT
 * (Höhenlinien) in einem PL/SQL-Block, der den CLOB Stück für Stück zusammensetzt.
 * <p>
 * Aufruf: {@code ant startdaten} oder {@code java … com.dan.geiranger.tools.StartdatenSql [daten/gelaende] [db/sql]}.
 */
public final class StartdatenSql {
    private StartdatenSql() { }

    static final String M = "'MITGELIEFERT'";

    static String s(String v) {
        if (v == null) return "NULL";
        return "'" + v.replace("'", "''").replace("\r", "").replace("\n", "' || CHR(10) || '") + "'";
    }

    /** Ein langer Text als CLOB in Stücken. */
    static String clob(String v) {
        if (v == null) return "NULL";
        String t = v.replace("\r", "");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length(); ) {
            int e = Math.min(t.length(), i + 1000);
            if (e < t.length() && Character.isHighSurrogate(t.charAt(e - 1))) e--;
            b.append(i > 0 ? "\n    || " : "").append("TO_CLOB(").append(s(t.substring(i, e))).append(")");
            i = e;
        }
        return b.toString();
    }

    static String n(Double v) { return v == null || Double.isNaN(v) || Double.isInfinite(v) ? "NULL" : Zahl.text(Math.round(v * 1e6) / 1e6); }

    static String n(double v) { return n((Double) v); }

    static String n8(double v) { return java.math.BigDecimal.valueOf(Math.round(v * 1e8) / 1e8).stripTrailingZeros().toPlainString(); }

    static String i(Integer v) { return v == null ? "NULL" : v.toString(); }

    static String b(boolean v) { return v ? "1" : "0"; }

    static String d(LocalDate d) { return d == null ? "NULL" : "DATE '" + d + "'"; }

    static String quelle(String kurz) { return kurz == null ? "NULL" : "(SELECT MAX(ID) FROM GR_QUELLE WHERE KURZ = " + s(kurz) + ")"; }

    static String punkt(double x, double z) {
        double[] ll = Daten.lonLat(x, z);
        return "MDSYS.SDO_GEOMETRY(2001, 8307, MDSYS.SDO_POINT_TYPE(" + n8(ll[0]) + ", " + n8(ll[1]) + ", NULL), NULL, NULL)";
    }

    /** Bis zu dieser Länge des WKT steht die Geometrie im MERGE; länger: eigener Block ({@link #langeGeom}). */
    static final int LANG = 30_000;

    static String geom(Daten.Geom g) {
        if (g == null) return "NULL";
        if (g.art().equals("PUNKT")) return punkt(g.xz()[0][0], g.xz()[0][1]);
        String w = g.wkt();
        if (w.length() <= 1000) return "MDSYS.SDO_GEOMETRY(" + s(w) + ", 8307)";
        return "MDSYS.SDO_GEOMETRY(" + clob(w) + ", 8307)";
    }

    static boolean lang(Daten.Geom g) { return g != null && !g.art().equals("PUNKT") && g.wkt().length() > LANG; }

    /** Eine sehr lange Geometrie: PL/SQL setzt den CLOB zusammen und schreibt ihn mit UPDATE … WHERE. */
    static void langeGeom(StringBuilder b, String tabelle, String spalte, String where, Daten.Geom g) {
        String w = g.wkt();
        b.append("DECLARE\n  w CLOB;\nBEGIN\n  DBMS_LOB.CREATETEMPORARY(w, TRUE);\n");
        for (int k = 0; k < w.length(); k += 1000) {
            String teil = w.substring(k, Math.min(w.length(), k + 1000));
            b.append("  DBMS_LOB.WRITEAPPEND(w, ").append(teil.length()).append(", '").append(teil.replace("'", "''")).append("');\n");
        }
        b.append("  UPDATE ").append(tabelle).append(" SET ").append(spalte).append(" = MDSYS.SDO_GEOMETRY(w, 8307) WHERE ").append(where).append(";\n");
        b.append("  DBMS_LOB.FREETEMPORARY(w);\nEND;\n/\n");
    }

    private static void kopf(StringBuilder b, String t) {
        b.append("\n-- ").append("-".repeat(Math.max(4, 100 - t.length()))).append(' ').append(t).append('\n');
    }

    /** Die mitgelieferten Daten auf einmal. */
    public record Alles(List<Daten.Gelaende> gelaende, List<Daten.Kernfenster> kernfenster, List<Daten.Form> formen, List<Daten.Hoehenlinie> hoehenlinien,
                        List<Daten.Fall> faelle, List<Daten.Elva> elva, List<Daten.Strasse> strassen, List<Daten.Kehre> kehren, List<Daten.Ort> orte,
                        List<Daten.Gebaeude> gebaeude, List<Daten.Schiff> schiffe, List<Daten.Schiffsweg> schiffswege, List<Daten.Fahrt> fahrten) {
        public static Alles aus(Startdaten.Szene sz) {
            Terrain t = sz.t();
            return new Alles(Startdaten.gelaende(sz), Startdaten.kernfenster(), Startdaten.formen(t), Startdaten.hoehenlinien(t), Startdaten.faelle(sz), Startdaten.elva(t),
                    Startdaten.strassen(t), Startdaten.kehren(t), Startdaten.orte(sz), Startdaten.gebaeude(sz), Startdaten.schiffe(sz.flotte()),
                    Startdaten.schiffswege(sz.flotte()), Startdaten.fahrten(sz));
        }
    }

    /** Die Zahlen der mitgelieferten Zeilen je Art (Zeile „-- ZAHLEN:“, der Einrichter prüft sie). */
    public static Map<String, Integer> zahlen(Alles a) {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("Quellen", Startdaten.quellen().size());
        m.put("Befunde", Startdaten.befunde().size());
        int belege = 0;
        for (Daten.Befund f : Startdaten.befunde()) belege += f.quellen().size();
        m.put("Belege", belege);
        m.put("Raster", a.gelaende().size());
        m.put("Kernfenster", a.kernfenster().size());
        m.put("Formen", a.formen().size());
        m.put("Höhenlinien", a.hoehenlinien().size());
        m.put("Fälle", a.faelle().size());
        int st = 0;
        for (Daten.Fall f : a.faelle()) st += f.wege().size();
        m.put("Strähnen", st);
        m.put("Elva", a.elva().size());
        m.put("Klima", Startdaten.klima().size());
        m.put("Höhenstufen", Startdaten.hoehenstufen().size());
        m.put("Straßen", a.strassen().size());
        m.put("Kehren", a.kehren().size());
        m.put("Orte", a.orte().size());
        m.put("Gebäude", a.gebaeude().size());
        m.put("Schiffe", a.schiffe().size());
        m.put("Abfahrten", Startdaten.fahrplan().size());
        m.put("Schiffswege", a.schiffswege().size());
        m.put("Wetterbücher", Startdaten.wetterbuecher().size());
        m.put("Regiebücher", Startdaten.regiebuecher().size());
        int sw = 0;
        for (Daten.Regiebuch r : Startdaten.regiebuecher()) sw += Regie.parse(r.text()).zeilen().size();
        m.put("Stichworte", sw);
        m.put("Ansichten", Startdaten.ansichten().size());
        m.put("Fahrten", a.fahrten().size());
        int sp = 0;
        for (Daten.Fahrt f : a.fahrten()) sp += f.punkte().size();
        m.put("Stützpunkte", sp);
        m.put("Presets", Startdaten.presets().size());
        int pw = 0;
        for (Daten.PaketPreset p : Startdaten.presets()) pw += p.felder().size();
        m.put("Presetwerte", pw);
        return m;
    }

    /** MERGE INTO t USING (SELECT key AS col …) q ON (…) WHEN MATCHED THEN UPDATE … WHERE HERKUNFT … WHEN NOT MATCHED THEN INSERT …. */
    static void merge(StringBuilder b, String tabelle, String[] schluessel, String[] schluesselWerte, String[] spalten, String[] werte, boolean herkunft) {
        StringBuilder using = new StringBuilder(), on = new StringBuilder(), upd = new StringBuilder(), cols = new StringBuilder(), vals = new StringBuilder();
        for (int k = 0; k < schluessel.length; k++) {
            using.append(k > 0 ? ", " : "").append(schluesselWerte[k]).append(" AS ").append(schluessel[k]);
            on.append(k > 0 ? " AND " : "").append("t.").append(schluessel[k]).append(" = q.").append(schluessel[k]);
            cols.append(k > 0 ? ", " : "").append(schluessel[k]);
            vals.append(k > 0 ? ", " : "").append(schluesselWerte[k]);
        }
        for (int k = 0; k < spalten.length; k++) {
            upd.append(k > 0 ? ", " : "").append("t.").append(spalten[k]).append(" = ").append(werte[k]);
            cols.append(", ").append(spalten[k]);
            vals.append(", ").append(werte[k]);
        }
        b.append("MERGE INTO ").append(tabelle).append(" t USING (SELECT ").append(using).append(" FROM DUAL) q ON (").append(on).append(")\n");
        if (spalten.length > 0) b.append("WHEN MATCHED THEN UPDATE SET ").append(upd).append(herkunft ? " WHERE t.HERKUNFT = " + M : "").append('\n');
        b.append("WHEN NOT MATCHED THEN INSERT (").append(cols).append(herkunft ? ", HERKUNFT" : "").append(")\n  VALUES (").append(vals)
                .append(herkunft ? ", " + M : "").append(");\n");
    }

    /** Das ganze Skript. */
    public static String sql(Alles a) {
        StringBuilder b = new StringBuilder();
        Map<String, Integer> z = zahlen(a);
        b.append("""
                -- =====================================================================================================
                -- Geiranger · Datenbank · 04_startdaten.sql
                -- ERZEUGT aus com.dan.geiranger.data.Startdaten (tools.StartdatenSql, ant startdaten) – nicht von Hand ändern.
                -- Mitgelieferte Zeilen (HERKUNFT = 'MITGELIEFERT') werden angelegt oder wiederhergestellt; eigene Zeilen
                -- bleiben unberührt. Beliebig oft wiederholbar. Der Code bleibt die Quelle (F26 A); die App liest beim Start
                -- Fahrplan, Klima, Höhenstufen, Wetter- und Regiebücher aus der Datenbank.
                -- =====================================================================================================
                """);
        StringBuilder zl = new StringBuilder("-- ZAHLEN:");
        for (Map.Entry<String, Integer> e : z.entrySet()) zl.append(' ').append(e.getKey()).append('=').append(e.getValue()).append(';');
        b.append(zl).append("\n\n-- Texte mit „&“: in SQL Developer keine Ersetzungsvariablen\nSET DEFINE OFF\n");

        kopf(b, "Ausbaustufe");
        b.append("MERGE INTO GR_SCHEMA t USING (SELECT 1 AS VERSION FROM DUAL) q ON (t.VERSION = q.VERSION)\n")
                .append("WHEN NOT MATCHED THEN INSERT (VERSION, APP_VERSION, TEXT) VALUES (1, 'Phase 17', ")
                .append(s("Datenbank: 36 Tabellen, Fälle mit Wegen, Straße, Ort, Fahrplan, Klima, Chronik der Stunden, Tage, Fahrten und Busse, Merkpunkte, Sitzung"))
                .append(");\n");

        kopf(b, "Quellen");
        for (Daten.Quelle q : Startdaten.quellen())
            merge(b, "GR_QUELLE", new String[]{"KURZ"}, new String[]{s(q.kurz())}, new String[]{"TITEL", "URL", "ART", "ABGERUFEN", "BEMERKUNG"},
                    new String[]{s(q.titel()), s(q.url()), s(q.art()), d(q.abgerufen()), s(q.bemerkung())}, true);

        kopf(b, "Befunde");
        for (Daten.Befund f : Startdaten.befunde())
            merge(b, "GR_BEFUND", new String[]{"THEMA", "GEGENSTAND"}, new String[]{s(f.thema()), s(f.gegenstand())}, new String[]{"BEFUND", "TEXT"},
                    new String[]{s(f.befund()), s(f.text())}, true);
        b.append("DELETE FROM GR_BEFUND_QUELLE WHERE BEFUND_ID IN (SELECT ID FROM GR_BEFUND WHERE HERKUNFT = ").append(M).append(");\n");
        for (Daten.Befund f : Startdaten.befunde())
            for (String q : f.quellen())
                b.append("INSERT INTO GR_BEFUND_QUELLE (BEFUND_ID, QUELLE_ID) SELECT b.ID, q.ID FROM GR_BEFUND b, GR_QUELLE q WHERE b.THEMA = ").append(s(f.thema()))
                        .append(" AND b.GEGENSTAND = ").append(s(f.gegenstand())).append(" AND b.HERKUNFT = ").append(M).append(" AND q.KURZ = ").append(s(q)).append(";\n");

        kopf(b, "Gelände (F27 A)");
        for (Daten.Gelaende g : a.gelaende())
            merge(b, "GR_GELAENDE", new String[]{"NAME"}, new String[]{s(g.name())},
                    new String[]{"AUSSCHNITT", "URSPRUNG_E", "URSPRUNG_N", "URSPRUNG_LAENGE", "URSPRUNG_BREITE", "EPSG", "ZELLE_M", "ZELLEN", "BREITE_KM", "HOEHE_MIN_M",
                            "HOEHE_MAX_M", "GRUND_UFER_M", "GRUND_MITTE_M", "GRUND_RINNE_M", "KACHELN", "DATEIEN", "QUELLE_ID", "BEMERKUNG"},
                    new String[]{geom(g.ausschnitt()), n(g.ursprungE()), n(g.ursprungN()), n8(g.ursprungLon()), n8(g.ursprungLat()), "25832", n(g.zelleM()),
                            Integer.toString(g.zellen()), n(Math.round(g.zelleM() * g.zellen() / 10.0) / 100.0), n(g.hoeheMin()), n(g.hoeheMax()), n(g.ufer()), n(g.mitte()),
                            n(g.rinne()), s(g.kacheln()), s(g.dateien().isEmpty() ? null : g.dateienText()), quelle(g.quelle()), s(g.bemerkung())}, true);

        kopf(b, "Kernfenster (F1 A)");
        for (Daten.Kernfenster k : a.kernfenster())
            merge(b, "GR_KERNFENSTER", new String[]{"NAME"}, new String[]{s(k.name())},
                    new String[]{"FLAECHE", "E_MIN", "N_MIN", "E_MAX", "N_MAX", "BREITE_M", "HOEHE_M", "INHALT"},
                    new String[]{geom(k.flaeche()), n(k.eMin()), n(k.nMin()), n(k.eMax()), n(k.nMax()), n(k.eMax() - k.eMin()), n(k.nMax() - k.nMin()), s(k.inhalt())}, true);

        kopf(b, "Formen (Phase 8 und 9)");
        for (Daten.Form f : a.formen())
            merge(b, "GR_FORM", new String[]{"NAME"}, new String[]{s(f.name())},
                    new String[]{"ART", "FLAECHE", "STUFE_M", "FRONT_M", "ZELLEN", "ZELLE_M", "MENGE_M3", "TEXT"},
                    new String[]{s(f.art()), geom(f.flaeche()), n(f.stufeM()), n(f.frontM()), Integer.toString(f.zellen()), n(f.zelleM()), n((double) Math.round(f.mengeM3())),
                            s(f.text())}, true);

        kopf(b, "Höhenlinien alle 50 m (F27 A)");
        for (Daten.Hoehenlinie h : a.hoehenlinien()) {
            Daten.Geom g = h.linie();
            merge(b, "GR_HOEHENLINIE", new String[]{"HOEHE_M"}, new String[]{Integer.toString(h.hoeheM())}, new String[]{"ART", "TEILE", "PUNKTE", "LAENGE_KM"},
                    new String[]{s(h.art()), Integer.toString(g.teile().size()), Integer.toString(g.punkte()), n(Math.round(g.laenge() / 10) / 100.0)}, true);
            if (lang(g)) langeGeom(b, "GR_HOEHENLINIE", "LINIE", "HOEHE_M = " + h.hoeheM() + " AND HERKUNFT = " + M, g);
            else b.append("UPDATE GR_HOEHENLINIE SET LINIE = ").append(geom(g)).append("\n  WHERE HOEHE_M = ").append(h.hoeheM()).append(" AND HERKUNFT = ").append(M).append(";\n");
        }

        kopf(b, "Fälle mit den Wegen ihrer Strähnen (F30 A)");
        for (Daten.Fall f : a.faelle()) {
            merge(b, "GR_FALL", new String[]{"NAME"}, new String[]{s(f.name())},
                    new String[]{"ART", "KANTE", "X_M", "Y_M", "Z_M", "FUSS_E", "FUSS_N", "RICHTUNG_GRAD", "HOEHE_M", "BREITE_M", "STRAEHNEN", "JUNI_M3S", "UEBERHANG",
                            "NUR_REGEN", "FALLHOEHE_M", "FALLZEIT_S", "FREI_MAX_M", "FELS_ANTEIL", "BEFUND", "QUELLE_ID", "BEMERKUNG"},
                    new String[]{s(f.art()), punkt(f.x(), f.z()), n(f.x()), n(f.y()), n(f.z()), n(f.fussE()), n(f.fussN()), n(f.richtung()), n(f.hoeheM()), n(f.breiteM()),
                            Integer.toString(f.straehnen()), n(f.juniM3s()), b(f.ueberhang()), b(f.nurRegen()), n(f.fallhoeheM()), n(f.fallzeitS()), n(f.freiMaxM()),
                            n(f.felsAnteil()), s(f.befund()), quelle(f.quelle()), s(f.bemerkung())}, true);
            String eltern = "FROM GR_FALL WHERE NAME = " + s(f.name()) + " AND HERKUNFT = " + M;
            b.append("DELETE FROM GR_STRAEHNE WHERE FALL_ID IN (SELECT ID ").append(eltern).append(");\n");
            for (Daten.Straehne w : f.wege()) {
                Daten.Geom l = w.linie();
                double[] auf = w.aufprall();
                b.append("INSERT INTO GR_STRAEHNE (FALL_ID, NR, LINIE, HOEHEN, PUNKTE, LAENGE_M, ANTEIL, FELS_ANTEIL, AUFSCHLAEGE, FREI_MAX_M, FALLZEIT_S, AUFPRALL, AUFPRALL_Y_M, AUF_WASSER)\n")
                        .append("  SELECT ID, ").append(w.nr()).append(", ").append(geom(l)).append(", ").append(s(w.hoehen())).append(", ").append(w.weg().length).append(", ")
                        .append(n(w.laengeM())).append(", ").append(n(w.anteil())).append(", ").append(n(w.felsAnteil())).append(", ").append(w.aufschlaege()).append(", ")
                        .append(n(w.freiMaxM())).append(", ").append(n(w.fallzeitS())).append(", ").append(auf == null ? "NULL" : punkt(auf[0], auf[2])).append(", ")
                        .append(auf == null ? "NULL" : n(Math.round(auf[1] * 10) / 10.0)).append(", ").append(b(w.aufWasser())).append(' ').append(eltern).append(";\n");
            }
        }

        kopf(b, "Geirangelva (Phase 8)");
        for (Daten.Elva e : a.elva())
            merge(b, "GR_ELVA", new String[]{"ART", "NR"}, new String[]{s(e.art()), Integer.toString(e.nr())},
                    new String[]{"NAME", "LINIE", "VON_M", "BIS_M", "LAENGE_M", "SPIEGEL_OBEN_M", "SPIEGEL_UNTEN_M", "GEFAELLE", "BREITE_M", "JUNI_M3S"},
                    new String[]{s(e.name()), geom(e.linie()), n(e.vonM()), n(e.bisM()), n(Math.round(e.laengeM() * 10) / 10.0), n(e.spiegelOben()), n(e.spiegelUnten()),
                            n(Math.round(e.gefaelle() * 1e4) / 1e4), n(e.breiteM()), n(e.juniM3s())}, true);

        kopf(b, "Klima „Geiranger“ je Monat (F36 A, F13)");
        for (Daten.KlimaMonat k : Startdaten.klima())
            merge(b, "GR_KLIMA_MONAT", new String[]{"MONAT"}, new String[]{Integer.toString(k.monat())},
                    new String[]{"NAME", "TEMPERATUR_C", "NIEDERSCHLAG_MM", "REGENTAGE", "ABFLUSS_ANTEIL", "BEMERKUNG"},
                    new String[]{s(k.name()), n(k.temperaturC()), n(k.niederschlagMm()), n(k.regentage()), n(k.abflussAnteil()), s(k.bemerkung())}, true);

        kopf(b, "Höhenstufen des Jahrs (Phase 14)");
        for (Daten.Hoehenstufe h : Startdaten.hoehenstufen())
            merge(b, "GR_HOEHENSTUFE", new String[]{"NR"}, new String[]{Integer.toString(h.nr())},
                    new String[]{"HOEHE_M", "ABNAHME_K_JE_M", "MEHR_JE_M", "TEMP_JAN_C", "TEMP_JUL_C", "NIEDERSCHLAG_MM"},
                    new String[]{n(h.hoeheM()), n(h.abnahme()), n(h.mehrJeM()), n(h.tJan()), n(h.tJul()), n(h.niederschlagJahr())}, true);

        kopf(b, "Straße, Kehren, Orte (F32 A)");
        for (Daten.Strasse st : a.strassen()) {
            String[] cols = {"ART", "LAENGE_M", "STEIGUNG_MITTEL", "STEIGUNG_MAX", "HOEHE_UNTEN_M", "HOEHE_OBEN_M", "BREITE_M", "FAHRSPUR_M", "BEMERKUNG"};
            String[] vals = {s(st.art()), n(st.laengeM()), n(st.steigungMittel()), n(st.steigungMax()), n(st.hoeheUnten()), n(st.hoeheOben()), n(st.breiteM()), n(st.spurM()),
                    s(st.bemerkung())};
            merge(b, "GR_STRASSE", new String[]{"NAME"}, new String[]{s(st.name())}, cols, vals, true);
            if (lang(st.linie())) langeGeom(b, "GR_STRASSE", "LINIE", "NAME = " + s(st.name()) + " AND HERKUNFT = " + M, st.linie());
            else b.append("UPDATE GR_STRASSE SET LINIE = ").append(geom(st.linie())).append("\n  WHERE NAME = ").append(s(st.name())).append(" AND HERKUNFT = ").append(M).append(";\n");
        }
        for (Daten.Kehre k : a.kehren())
            merge(b, "GR_KEHRE", new String[]{"NR"}, new String[]{Integer.toString(k.nr())},
                    new String[]{"SCHEITEL", "X_M", "Y_M", "Z_M", "STATION_M", "BOGEN_M", "RADIUS_M"},
                    new String[]{punkt(k.x(), k.z()), n(k.x()), n(k.y()), n(k.z()), n(k.stationM()), n(k.bogenM()), n(k.radiusM())}, true);
        for (Daten.Ort o : a.orte()) {
            double[] u = o.utm();
            merge(b, "GR_ORT", new String[]{"NAME"}, new String[]{s(o.name())}, new String[]{"ART", "LAGE", "X_M", "Y_M", "Z_M", "UTM_E", "UTM_N", "BEMERKUNG"},
                    new String[]{s(o.art()), punkt(o.x(), o.z()), n(o.x()), n(o.y()), n(o.z()), n(Math.round(u[0] * 10) / 10.0), n(Math.round(u[1] * 10) / 10.0), s(o.bemerkung())},
                    true);
        }

        kopf(b, "Gebäude des Orts und der Höfe (F32 A): die mitgelieferten neu");
        b.append("DELETE FROM GR_GEBAEUDE WHERE HERKUNFT = ").append(M).append(";\n");
        for (Daten.Gebaeude g : a.gebaeude())
            b.append("INSERT INTO GR_GEBAEUDE (NR, NAME, ART, GRUNDRISS, X_M, Y_M, Z_M, LAENGE_M, BREITE_M, WAND_M, FIRST_GRAD, FARBE, DACH, BAUWEISE, ORTSTEIL, HERKUNFT)\n  VALUES (")
                    .append(g.nr()).append(", ").append(s(g.name())).append(", ").append(s(g.art())).append(", ").append(geom(g.grundriss())).append(", ").append(n(g.x()))
                    .append(", ").append(n(g.y())).append(", ").append(n(g.z())).append(", ").append(n(2 * g.hl())).append(", ").append(n(2 * g.hw())).append(", ")
                    .append(n(g.wandM())).append(", ").append(n(Math.round(g.firstGrad() * 10) / 10.0)).append(", ").append(s(g.farbe())).append(", ").append(s(g.dach()))
                    .append(", ").append(s(g.bauweise())).append(", ").append(s(g.ortsteil())).append(", ").append(M).append(");\n");

        kopf(b, "Schiffe, Fahrplan, Wege (F31 A)");
        for (Daten.Schiff sh : a.schiffe())
            merge(b, "GR_SCHIFF", new String[]{"NAME"}, new String[]{s(sh.name())},
                    new String[]{"ART", "LAENGE_M", "BREITE_M", "TIEFGANG_M", "FREIBORD_M", "GM_M", "ROLLZEIT_S", "MASSE_T", "BEMERKUNG"},
                    new String[]{s(sh.art()), n(sh.laengeM()), n(sh.breiteM()), n(sh.tiefgangM()), n(sh.freibordM()), n(sh.gmM()), n(sh.rollzeitS()), n(sh.masseT()),
                            s(sh.bemerkung())}, true);
        for (Daten.Fahrplan f : Startdaten.fahrplan())
            merge(b, "GR_FAHRPLAN", new String[]{"LINIE", "RICHTUNG", "ABFAHRT", "JAHR"}, new String[]{s(f.linie()), s(f.richtung()), s(f.abfahrt()), Integer.toString(f.jahr())},
                    new String[]{"ABFAHRT_MIN", "FAHRZEIT_MIN", "SAISON_VON", "SAISON_BIS", "QUELLE_ID", "BEMERKUNG"},
                    new String[]{Integer.toString(f.abfahrtMin()), Integer.toString(f.fahrzeitMin()), s(f.saisonVon()), s(f.saisonBis()), quelle(f.quelle()), s(f.bemerkung())}, true);
        for (Daten.Schiffsweg w : a.schiffswege())
            merge(b, "GR_SCHIFFSWEG", new String[]{"NAME"}, new String[]{s(w.name())}, new String[]{"ART", "GEOM", "LAENGE_M", "BEMERKUNG"},
                    new String[]{s(w.art()), geom(w.geom()), w.geom() == null || w.geom().art().equals("PUNKT") ? "NULL" : n(Math.round(w.geom().laenge() * 10) / 10.0),
                            s(w.bemerkung())}, true);

        kopf(b, "Wetter-Regiebücher (F36 A)");
        for (Daten.Wetterbuch w : Startdaten.wetterbuecher()) {
            b.append("MERGE INTO GR_WETTERBUCH t USING (SELECT ").append(s(w.name())).append(" AS NAME FROM DUAL) q ON (t.NAME = q.NAME)\n")
                    .append("WHEN NOT MATCHED THEN INSERT (NAME, TEXT, HERKUNFT) VALUES (").append(s(w.name())).append(", '-', ").append(M).append(");\n");
            b.append("UPDATE GR_WETTERBUCH SET BEGINN_STUNDE = ").append(n(w.beginnStunde())).append(", ZEITRAFFER = ").append(n(w.zeitraffer())).append(", SCHRITTE = ")
                    .append(w.schritte()).append(", BESCHREIBUNG = ").append(s(w.beschreibung())).append(",\n  TEXT = ").append(clob(w.text())).append("\n  WHERE NAME = ")
                    .append(s(w.name())).append(" AND HERKUNFT = ").append(M).append(";\n");
        }

        kopf(b, "Regiebücher (F33 A)");
        for (Daten.Regiebuch r : Startdaten.regiebuecher()) {
            Regie.Book bk = Regie.parse(r.text());
            Startdaten.Kopf k = Startdaten.Kopf.aus(bk);
            String[] cols = {"BESCHREIBUNG", "DATEI", "SZENE_MS", "SZENE_TEXT", "ZEITRAFFER", "LAENGE_S", "FOTOS", "WARTEN", "BLATT", "WETTER"};
            String[] vals = {s(r.beschreibung()), s(r.datei()), k.szeneMs() == null ? "NULL" : k.szeneMs().toString(), s(k.szeneMs() == null ? null : Daten.text(k.szeneMs())),
                    n(k.raffer()), n(bk.length()), Integer.toString(k.fotos()), Integer.toString(k.warten()), b(k.blatt()), s(Startdaten.wetterBlock(r.text()))};
            b.append("MERGE INTO GR_REGIEBUCH t USING (SELECT ").append(s(r.name())).append(" AS NAME FROM DUAL) q ON (t.NAME = q.NAME)\n")
                    .append("WHEN NOT MATCHED THEN INSERT (NAME, LAENGE_S, TEXT, HERKUNFT) VALUES (").append(s(r.name())).append(", ").append(n(bk.length()))
                    .append(", '-', ").append(M).append(");\n");
            StringBuilder upd = new StringBuilder();
            for (int c = 0; c < cols.length; c++) upd.append(c > 0 ? ", " : "").append(cols[c]).append(" = ").append(vals[c]);
            b.append("UPDATE GR_REGIEBUCH SET ").append(upd).append(",\n  TEXT = ").append(clob(r.text())).append("\n  WHERE NAME = ").append(s(r.name()))
                    .append(" AND HERKUNFT = ").append(M).append(";\n");
            String eltern = "FROM GR_REGIEBUCH WHERE NAME = " + s(r.name()) + " AND HERKUNFT = " + M;
            b.append("DELETE FROM GR_STICHWORT WHERE REGIEBUCH_ID IN (SELECT ID ").append(eltern).append(");\n");
            int nr = 0;
            for (Regie.Zeile zz : bk.zeilen())
                b.append("INSERT INTO GR_STICHWORT (REGIEBUCH_ID, NR, SEKUNDE, BEFEHL, ARGUMENTE, TEXT) SELECT ID, ").append(++nr).append(", ").append(n(zz.sekunde())).append(", ")
                        .append(s(zz.befehl().toUpperCase(Locale.ROOT))).append(", ").append(s(zz.argumente().isEmpty() ? null : zz.argumente())).append(", ")
                        .append(s(zz.text().isEmpty() ? null : zz.text())).append(' ').append(eltern).append(";\n");
        }

        kopf(b, "Ansichten (Tasten 1–9)");
        for (Daten.Ansicht an : Startdaten.ansichten()) {
            double[] c = an.kamera();
            merge(b, "GR_ANSICHT", new String[]{"NAME", "ART"}, new String[]{s(an.name()), "'FEST'"},
                    new String[]{"TASTE", "KAM_X", "KAM_Z", "KAM_GIER_GRAD", "KAM_NICK_GRAD", "AUGE_UEBER_GRUND_M", "LAGE"},
                    new String[]{i(an.taste()), n(c[0]), n(c[2]), n(c[3]), n(c[4]), n(an.augeUeberGrund()), punkt(c[0], c[2])}, true);
        }

        kopf(b, "Kamerafahrten (F33 A)");
        for (Daten.Fahrt f : a.fahrten()) {
            merge(b, "GR_FAHRT", new String[]{"NAME"}, new String[]{s(f.name())}, new String[]{"ART", "DAUER_S", "LAENGE_M", "WEG", "BESCHREIBUNG"},
                    new String[]{s(f.art()), n(f.dauerS()), n(f.laengeM()), geom(f.weg()), s(f.beschreibung())}, true);
            String eltern = "FROM GR_FAHRT WHERE NAME = " + s(f.name()) + " AND HERKUNFT = " + M;
            b.append("DELETE FROM GR_STUETZPUNKT WHERE FAHRT_ID IN (SELECT ID ").append(eltern).append(");\n");
            for (Daten.Stuetzpunkt p : f.punkte())
                b.append("INSERT INTO GR_STUETZPUNKT (FAHRT_ID, NR, ZEIT_S, KAM_X, KAM_Y, KAM_Z, KAM_GIER_GRAD, KAM_NICK_GRAD) SELECT ID, ").append(p.nr()).append(", ")
                        .append(n(p.zeitS())).append(", ").append(n(p.x())).append(", ").append(n(p.y())).append(", ").append(n(p.z())).append(", ").append(n(p.gier()))
                        .append(", ").append(n(p.nick())).append(' ').append(eltern).append(";\n");
        }

        kopf(b, "Presets der Packages (F34 A)");
        for (Daten.PaketPreset p : Startdaten.presets()) {
            merge(b, "GR_PAKET_PRESET", new String[]{"PAKET", "KLASSE", "NAME"}, new String[]{s(p.paket()), s(p.klasse()), s(p.name())}, new String[]{"BESCHREIBUNG"},
                    new String[]{s(p.beschreibung())}, true);
            String eltern = "FROM GR_PAKET_PRESET WHERE PAKET = " + s(p.paket()) + " AND KLASSE = " + s(p.klasse()) + " AND NAME = " + s(p.name()) + " AND HERKUNFT = " + M;
            b.append("DELETE FROM GR_PAKET_WERT WHERE PAKET_PRESET_ID IN (SELECT ID ").append(eltern).append(");\n");
            Map<String, String> typ = Startdaten.typen(p);
            for (Map.Entry<String, String> e : p.felder().entrySet())
                b.append("INSERT INTO GR_PAKET_WERT (PAKET_PRESET_ID, FELD, TYP, WERT) SELECT ID, ").append(s(e.getKey())).append(", ").append(s(typ.getOrDefault(e.getKey(), "STRING")))
                        .append(", ").append(s(e.getValue().isEmpty() ? null : e.getValue())).append(' ').append(eltern).append(";\n");
        }
        b.append("\nCOMMIT;\n\n-- Ende 04_startdaten.sql\n");
        return b.toString();
    }

    public static void main(String[] args) throws Exception {
        File gel = args.length > 0 ? new File(args[0]) : Terrain.defaultDir();
        File out = args.length > 1 ? new File(args[1]) : Verbindung.skripte();
        long t0 = System.nanoTime();
        Startdaten.Szene sz = Startdaten.szene(gel);
        if (!sz.gemessen()) System.out.println("Achtung: ohne " + gel + " fehlen Gelände, Fälle, Straße, Ort und Höhenlinien");
        Alles a = Alles.aus(sz);
        String sql = sql(a);
        out.mkdirs();
        File f = new File(out, "04_startdaten.sql");
        Files.writeString(f.toPath(), sql, StandardCharsets.UTF_8);
        System.out.printf(Locale.GERMAN, "%s: %,d Zeichen, %,d Zeilen in %.1f s%n", f, sql.length(), sql.split("\n").length, (System.nanoTime() - t0) / 1e9);
        System.out.println(zahlen(a));
        System.exit(0);
    }
}
