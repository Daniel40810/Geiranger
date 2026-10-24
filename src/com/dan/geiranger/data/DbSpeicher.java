package com.dan.geiranger.data;

import com.dan.geiranger.scene.Regie;
import com.dan.store.Db;
import com.dan.store.Protokoll;
import com.dan.store.Zahl;

import java.io.StringReader;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Speichern im Schema GEIRANGER (Phase 17, aus Rungholt). Eine Verbindung, alle Zugriffe synchronisiert (der Faden der
 * Daten, die Chronik und die Oberfläche teilen sie). Punkte schreibt sie als {@code SDO_POINT_TYPE} (so lesen die
 * Sichten Länge und Breite als Zahl), Linien und Flächen als WKT über {@code SDO_GEOMETRY(clob, 8307)}; gelesen wird mit
 * {@code SDO_UTIL.TO_WKTGEOMETRY}, ohne {@code sdoapi}. Szenenzeit als ms seit 1970 mit Text und Tag; SZENENZEIT setzt
 * der Trigger. Mitgelieferte Zeilen werden nie überschrieben.
 */
public final class DbSpeicher implements Speicher {
    private final Db.Konfig konfig;
    private Connection c;
    private final String wo;

    public DbSpeicher(Db.Konfig k) throws Fehler {
        this.konfig = k;
        try {
            c = Db.oeffnen(k);
            String w = Db.einWert(c, "select user || ' auf ' || sys_context('USERENV','CON_NAME') from dual");
            wo = "Datenbank " + w;
        } catch (SQLException e) {
            throw new Fehler("Keine Verbindung zu " + k.kurz() + ": " + Db.erste(e), e);
        }
    }

    /** Für Prüfungen und den Einrichter: die offene Verbindung. */
    public synchronized Connection verbindung() throws Fehler {
        try {
            if (c == null || c.isClosed() || !c.isValid(3)) c = Db.oeffnen(konfig);
            return c;
        } catch (SQLException e) {
            throw new Fehler("Verbindung verloren: " + Db.erste(e), e);
        }
    }

    @Override public String wo() { return wo; }
    @Override public boolean datenbank() { return true; }

    private interface Arbeit<T> { T tun(Connection c) throws SQLException; }

    private synchronized <T> T mit(String was, Arbeit<T> a) throws Fehler {
        Connection cc = verbindung();
        try {
            return a.tun(cc);
        } catch (SQLException e) {
            throw new Fehler(was + ": " + Db.erste(e), e);
        }
    }

    /** In einer Transaktion: alles oder nichts. */
    private synchronized <T> T zusammen(String was, Arbeit<T> a) throws Fehler {
        Connection cc = verbindung();
        try {
            cc.setAutoCommit(false);
            T t = a.tun(cc);
            cc.commit();
            return t;
        } catch (SQLException e) {
            try { cc.rollback(); } catch (SQLException e2) { /* weiter */ }
            throw new Fehler(was + ": " + Db.erste(e), e);
        } finally {
            try { cc.setAutoCommit(true); } catch (SQLException e) { /* weiter */ }
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private static int zahl(Connection c, String sql, Object... a) throws SQLException {
        String v = Db.einWert(c, sql, a);
        return v == null ? 0 : Integer.parseInt(v);
    }

    private static long neueId(Connection c, String seq) throws SQLException { return Long.parseLong(Db.einWert(c, "SELECT TO_CHAR(" + seq + ".CURRVAL) FROM DUAL")); }

    static void setD(PreparedStatement st, int i, Double v) throws SQLException {
        if (v == null || Double.isNaN(v) || Double.isInfinite(v)) st.setNull(i, Types.NUMERIC); else st.setDouble(i, v);
    }

    static void setS(PreparedStatement st, int i, String v) throws SQLException {
        if (v == null || v.isEmpty()) st.setNull(i, Types.VARCHAR);
        else if (v.length() > 3900) st.setCharacterStream(i, new StringReader(v), v.length());
        else st.setString(i, v);
    }

    private static void setClob(PreparedStatement st, int i, String v) throws SQLException {
        if (v == null) st.setNull(i, Types.CLOB); else st.setCharacterStream(i, new StringReader(v), v.length());
    }

    private static void setL(PreparedStatement st, int i, Long v) throws SQLException {
        if (v == null || v < 0) st.setNull(i, Types.NUMERIC); else st.setLong(i, v);
    }

    private static void setMs(PreparedStatement st, int i, Long ms) throws SQLException {
        if (ms == null) st.setNull(i, Types.NUMERIC); else st.setLong(i, ms);
    }

    /** Eine Szenenzeit: ms, Text, Tag ab Index i; gibt den nächsten Index zurück. */
    private static int setSz(PreparedStatement st, int i, Long ms) throws SQLException {
        if (ms == null) { st.setNull(i, Types.NUMERIC); st.setNull(i + 1, Types.VARCHAR); st.setNull(i + 2, Types.VARCHAR); return i + 3; }
        st.setLong(i, ms);
        st.setString(i + 1, Daten.text(ms));
        st.setString(i + 2, Daten.tag(ms));
        return i + 3;
    }

    private static Long ms(ResultSet r, String col) throws SQLException {
        long v = r.getLong(col);
        return r.wasNull() ? null : v;
    }

    private static Double dbl(ResultSet r, String col) throws SQLException {
        double v = r.getDouble(col);
        return r.wasNull() ? null : v;
    }

    private static double d(ResultSet r, String col) throws SQLException {
        double v = r.getDouble(col);
        return r.wasNull() ? Double.NaN : v;
    }

    private static Integer integer(ResultSet r, String col) throws SQLException {
        int v = r.getInt(col);
        return r.wasNull() ? null : v;
    }

    private static String clob(ResultSet r, String col) throws SQLException {
        Clob cl = r.getClob(col);
        return cl == null ? null : cl.getSubString(1, (int) cl.length());
    }

    private static Daten.Geom geom(ResultSet r, String col) throws SQLException {
        String w = clob(r, col);
        return w == null ? null : Daten.Geom.ausWkt(w);
    }

    static final String PUNKT = "MDSYS.SDO_GEOMETRY(2001, 8307, MDSYS.SDO_POINT_TYPE(?, ?, NULL), NULL, NULL)";

    /** SQL für eine Geometrie: Punkt als SDO_POINT_TYPE mit zwei Platzhaltern, sonst WKT mit einem; null: NULL. */
    private static String geomSql(Daten.Geom g) {
        if (g == null) return "NULL";
        if (g.art().equals("PUNKT")) return PUNKT;
        return "MDSYS.SDO_GEOMETRY(?, 8307)";
    }

    /** Die Platzhalter von {@link #geomSql} füllen; gibt den nächsten Index zurück. */
    private static int setGeom(PreparedStatement st, int i, Daten.Geom g) throws SQLException {
        if (g == null) return i;
        if (g.art().equals("PUNKT")) {
            double[] ll = Daten.lonLat(g.xz()[0][0], g.xz()[0][1]);
            st.setDouble(i, Math.round(ll[0] * 1e8) / 1e8);
            st.setDouble(i + 1, Math.round(ll[1] * 1e8) / 1e8);
            return i + 2;
        }
        setClob(st, i, g.wkt());
        return i + 1;
    }

    static Double r1(double v) { return Double.isNaN(v) ? null : Math.round(v * 10) / 10.0; }
    static Double r2(double v) { return Double.isNaN(v) ? null : Math.round(v * 100) / 100.0; }
    static Double r3(double v) { return Double.isNaN(v) ? null : Math.round(v * 1000) / 1000.0; }

    private static boolean mitgeliefert(Connection c, String tabelle, String name) throws SQLException {
        return zahl(c, "SELECT COUNT(*) FROM " + tabelle + " WHERE NAME = ? AND HERKUNFT = 'MITGELIEFERT'", name) > 0;
    }

    private static final String REGIEBUCH_ID = "(SELECT MAX(ID) FROM GR_REGIEBUCH WHERE NAME = ?)";

    // ------------------------------------------------------------------ Stand

    @Override public Daten.Stand stand() throws Fehler {
        return mit("Stand", c -> new Daten.Stand(zahl(c, "SELECT COUNT(*) FROM GR_FALL"), zahl(c, "SELECT COUNT(*) FROM GR_GEBAEUDE"), zahl(c, "SELECT COUNT(*) FROM GR_KEHRE"),
                zahl(c, "SELECT COUNT(*) FROM GR_FAHRPLAN"), zahl(c, "SELECT COUNT(*) FROM GR_STUNDE"), zahl(c, "SELECT COUNT(*) FROM GR_TAG"),
                zahl(c, "SELECT COUNT(*) FROM GR_SCHIFFSFAHRT"), zahl(c, "SELECT COUNT(*) FROM GR_BUSANKUNFT"), zahl(c, "SELECT COUNT(*) FROM GR_REGIEBUCH"),
                zahl(c, "SELECT COUNT(*) FROM GR_REGIELAUF"), zahl(c, "SELECT COUNT(*) FROM GR_ANSICHT"), zahl(c, "SELECT COUNT(*) FROM GR_MERKPUNKT"),
                zahl(c, "SELECT COUNT(*) FROM GR_PAKET_PRESET"), zahl(c, "SELECT COUNT(*) FROM GR_BEFUND"), zahl(c, "SELECT COUNT(*) FROM GR_QUELLE"), letzte(c, -1)));
    }

    // ------------------------------------------------------------------ Quellen, Befunde, Fälle, Orte

    @Override public List<Daten.Quelle> quellen() throws Fehler {
        return mit("Quellen", c -> {
            List<Daten.Quelle> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT KURZ, TITEL, URL, ART, ABGERUFEN, BEMERKUNG FROM GR_QUELLE ORDER BY ID")) {
                while (r.next()) {
                    java.sql.Date ab = r.getDate(5);
                    l.add(new Daten.Quelle(r.getString(1), r.getString(2), r.getString(3), r.getString(4), ab == null ? null : ab.toLocalDate(), r.getString(6)));
                }
            }
            return l;
        });
    }

    @Override public List<Daten.Befund> befunde() throws Fehler {
        return mit("Befunde", c -> {
            Map<Long, List<String>> q = new LinkedHashMap<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT x.BEFUND_ID, q.KURZ FROM GR_BEFUND_QUELLE x JOIN GR_QUELLE q ON q.ID = x.QUELLE_ID ORDER BY x.ID")) {
                while (r.next()) q.computeIfAbsent(r.getLong(1), k -> new ArrayList<>()).add(r.getString(2));
            }
            List<Daten.Befund> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT ID, THEMA, GEGENSTAND, BEFUND, TEXT FROM GR_BEFUND ORDER BY ID")) {
                while (r.next()) l.add(new Daten.Befund(r.getString(2), r.getString(3), r.getString(4), r.getString(5), q.getOrDefault(r.getLong(1), List.of())));
            }
            return l;
        });
    }

    @Override public List<Daten.Fall> faelle() throws Fehler {
        return mit("Fälle", c -> {
            Map<Long, List<Daten.Straehne>> w = new LinkedHashMap<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT FALL_ID, NR, MDSYS.SDO_UTIL.TO_WKTGEOMETRY(LINIE) AS WKT, HOEHEN, LAENGE_M, ANTEIL, "
                    + "FELS_ANTEIL, AUFSCHLAEGE, FREI_MAX_M, FALLZEIT_S, AUFPRALL.SDO_POINT.X AS AX, AUFPRALL.SDO_POINT.Y AS AY, AUFPRALL_Y_M, AUF_WASSER FROM GR_STRAEHNE ORDER BY FALL_ID, NR")) {
                while (r.next()) {
                    Daten.Geom g = geom(r, "WKT");
                    String[] hs = r.getString("HOEHEN") == null ? new String[0] : r.getString("HOEHEN").split(" ");
                    double[][] p = new double[g == null ? 0 : g.xz().length][];
                    for (int i = 0; i < p.length; i++) p[i] = new double[]{g.xz()[i][0], i < hs.length ? Zahl.lies(hs[i]) : Double.NaN, g.xz()[i][1]};
                    Double ax = dbl(r, "AX"), ay = dbl(r, "AY");
                    double[] auf = ax == null || ay == null ? null : Daten.xz(ax, ay);
                    w.computeIfAbsent(r.getLong(1), k -> new ArrayList<>()).add(new Daten.Straehne(r.getInt(2), p, d(r, "LAENGE_M"), d(r, "ANTEIL"), d(r, "FELS_ANTEIL"),
                            r.getInt("AUFSCHLAEGE"), d(r, "FREI_MAX_M"), d(r, "FALLZEIT_S"), auf == null ? null : new double[]{auf[0], d(r, "AUFPRALL_Y_M"), auf[1]},
                            r.getInt("AUF_WASSER") == 1));
                }
            }
            List<Daten.Fall> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT f.ID, f.NAME, f.ART, f.X_M, f.Y_M, f.Z_M, f.FUSS_E, f.FUSS_N, f.RICHTUNG_GRAD, f.HOEHE_M, "
                    + "f.BREITE_M, f.STRAEHNEN, f.JUNI_M3S, f.UEBERHANG, f.NUR_REGEN, f.FALLHOEHE_M, f.FALLZEIT_S, f.FREI_MAX_M, f.FELS_ANTEIL, f.BEFUND, q.KURZ AS QUELLE, "
                    + "f.BEMERKUNG FROM GR_FALL f LEFT JOIN GR_QUELLE q ON q.ID = f.QUELLE_ID ORDER BY f.ID")) {
                while (r.next())
                    l.add(new Daten.Fall(r.getString("NAME"), r.getString("ART"), d(r, "X_M"), d(r, "Y_M"), d(r, "Z_M"), d(r, "FUSS_E"), d(r, "FUSS_N"), d(r, "RICHTUNG_GRAD"),
                            d(r, "HOEHE_M"), d(r, "BREITE_M"), r.getInt("STRAEHNEN"), d(r, "JUNI_M3S"), r.getInt("UEBERHANG") == 1, r.getInt("NUR_REGEN") == 1,
                            d(r, "FALLHOEHE_M"), d(r, "FALLZEIT_S"), d(r, "FREI_MAX_M"), d(r, "FELS_ANTEIL"), r.getString("BEFUND"), r.getString("QUELLE"), r.getString("BEMERKUNG"),
                            w.getOrDefault(r.getLong(1), List.of())));
            }
            return l;
        });
    }

    @Override public List<Daten.Ort> orte() throws Fehler {
        return mit("Orte", c -> {
            List<Daten.Ort> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT NAME, ART, X_M, Y_M, Z_M, BEMERKUNG FROM GR_ORT ORDER BY ID")) {
                while (r.next()) l.add(new Daten.Ort(r.getString(1), r.getString(2), d(r, "X_M"), d(r, "Y_M"), d(r, "Z_M"), r.getString(6)));
            }
            return l;
        });
    }

    // ------------------------------------------------------------------ was die App beim Start liest (F26)

    @Override public List<Daten.Fahrplan> fahrplan() throws Fehler {
        return mit("Fahrplan", c -> {
            List<Daten.Fahrplan> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT p.LINIE, p.RICHTUNG, p.ABFAHRT, p.FAHRZEIT_MIN, p.SAISON_VON, p.SAISON_BIS, p.JAHR, "
                    + "q.KURZ AS QUELLE, p.BEMERKUNG, p.HERKUNFT FROM GR_FAHRPLAN p LEFT JOIN GR_QUELLE q ON q.ID = p.QUELLE_ID ORDER BY p.JAHR, p.ABFAHRT_MIN, p.RICHTUNG")) {
                while (r.next())
                    l.add(new Daten.Fahrplan(r.getString(1), r.getString(2), r.getString(3), r.getInt(4), r.getString(5), r.getString(6), r.getInt(7), r.getString(8),
                            r.getString(9), r.getString(10)));
            }
            return l;
        });
    }

    @Override public List<Daten.KlimaMonat> klima() throws Fehler {
        return mit("Klima", c -> {
            List<Daten.KlimaMonat> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT MONAT, NAME, TEMPERATUR_C, NIEDERSCHLAG_MM, REGENTAGE, ABFLUSS_ANTEIL, BEMERKUNG "
                    + "FROM GR_KLIMA_MONAT ORDER BY MONAT")) {
                while (r.next()) l.add(new Daten.KlimaMonat(r.getInt(1), r.getString(2), r.getDouble(3), r.getDouble(4), r.getDouble(5), r.getDouble(6), r.getString(7)));
            }
            return l;
        });
    }

    @Override public List<Daten.Hoehenstufe> hoehenstufen() throws Fehler {
        return mit("Höhenstufen", c -> {
            List<Daten.Hoehenstufe> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT NR, HOEHE_M, ABNAHME_K_JE_M, MEHR_JE_M, TEMP_JAN_C, TEMP_JUL_C, NIEDERSCHLAG_MM "
                    + "FROM GR_HOEHENSTUFE ORDER BY NR")) {
                while (r.next()) l.add(new Daten.Hoehenstufe(r.getInt(1), r.getDouble(2), r.getDouble(3), r.getDouble(4), d(r, "TEMP_JAN_C"), d(r, "TEMP_JUL_C"), d(r, "NIEDERSCHLAG_MM")));
            }
            return l;
        });
    }

    @Override public List<Daten.Wetterbuch> wetterbuecher() throws Fehler {
        return mit("Wetterbücher", c -> {
            List<Daten.Wetterbuch> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT NAME, BEGINN_STUNDE, ZEITRAFFER, SCHRITTE, BESCHREIBUNG, TEXT, HERKUNFT FROM GR_WETTERBUCH "
                    + "ORDER BY CASE HERKUNFT WHEN 'MITGELIEFERT' THEN 0 ELSE 1 END, ID")) {
                while (r.next())
                    l.add(new Daten.Wetterbuch(r.getString(1), d(r, "BEGINN_STUNDE"), d(r, "ZEITRAFFER"), r.getInt(4), r.getString(5), clob(r, "TEXT"), r.getString(7)));
            }
            return l;
        });
    }

    // ------------------------------------------------------------------ Chronik

    @Override public void stunde(Daten.Stunde s, Long sitzung) throws Fehler {
        zusammen("Stunde", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GR_STUNDE (SITZUNG_ID, SZENE_MS, SZENE_TEXT, SZENE_TAG, LAGE, WIND_MS, RICHTUNG_GRAD, BOEE_MS, "
                    + "BEDECKUNG_ACHTEL, WOLKEN, REGEN_MMH, SICHT_KM, NEBEL, TEMPERATUR_C, ABFLUSS_ANTEIL, REGENSPEICHER_MM, FALL_TEMP_C, EIS, ABFLUSS) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                setL(st, 1, sitzung);
                int i = setSz(st, 2, s.ms());
                setS(st, i, s.lage());
                setD(st, i + 1, r2(s.windMs()));
                setD(st, i + 2, r1(s.richtungGrad()));
                setD(st, i + 3, r2(s.boeeMs()));
                setD(st, i + 4, r2(s.bedeckung()));
                setS(st, i + 5, s.wolken());
                setD(st, i + 6, r2(s.regenMmH()));
                setD(st, i + 7, r2(s.sichtKm()));
                setD(st, i + 8, r3(s.nebel()));
                setD(st, i + 9, r2(s.temperaturC()));
                setD(st, i + 10, r3(s.abflussAnteil()));
                setD(st, i + 11, r2(s.regenspeicherMm()));
                setD(st, i + 12, r2(s.fallTempC()));
                setD(st, i + 13, r3(s.eis()));
                setS(st, i + 14, s.abfluss());
                st.executeUpdate();
            }
            return null;
        });
    }

    @Override public void tag(Daten.Tag t, Long sitzung) throws Fehler {
        zusammen("Tag", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GR_TAG (SITZUNG_ID, SZENE_MS, SZENE_TEXT, SZENE_TAG, SCHNEEGRENZE_M, SCHNEE_ORT_CM, SCHNEE_600_CM, "
                    + "SCHNEE_1200_CM, LAUB, HERBST, KRONEN, EIS, LUFT_C) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                setL(st, 1, sitzung);
                int i = setSz(st, 2, t.ms());
                setD(st, i, t.schneegrenzeM() == null ? null : (double) Math.round(t.schneegrenzeM()));
                setD(st, i + 1, r1(t.schneeOrtCm()));
                setD(st, i + 2, r1(t.schnee600Cm()));
                setD(st, i + 3, r1(t.schnee1200Cm()));
                setD(st, i + 4, r3(t.laub()));
                setD(st, i + 5, r3(t.herbst()));
                setD(st, i + 6, r3(t.kronen()));
                setD(st, i + 7, r3(t.eis()));
                setD(st, i + 8, r2(t.luftC()));
                st.executeUpdate();
            }
            return null;
        });
    }

    @Override public long schiffsfahrt(Daten.Schiffsfahrt f, Long sitzung) throws Fehler {
        Daten.Geom horn = f.horn() == null ? null : Daten.Geom.punkt(f.horn()[0], f.horn()[1]);
        return zusammen("Schiffsfahrt", c -> {
            boolean neu = f.id() <= 0;
            String sql = neu
                    ? "INSERT INTO GR_SCHIFFSFAHRT (SCHIFF, RICHTUNG, SZENE_MS, SZENE_TEXT, SZENE_TAG, AN_MS, AN_TEXT, DAUER_MIN, WEG, LAENGE_M, HOERNER, HORN, HORN_TEXT, ECHOS, "
                    + "ECHO_TEXT, SITZUNG_ID) VALUES (?, ?, ?, ?, ?, ?, ?, ?, " + geomSql(f.weg()) + ", ?, ?, " + geomSql(horn) + ", ?, ?, ?, ?)"
                    : "UPDATE GR_SCHIFFSFAHRT SET SCHIFF = ?, RICHTUNG = ?, SZENE_MS = ?, SZENE_TEXT = ?, SZENE_TAG = ?, AN_MS = ?, AN_TEXT = ?, DAUER_MIN = ?, WEG = "
                    + geomSql(f.weg()) + ", LAENGE_M = ?, HOERNER = ?, HORN = " + geomSql(horn) + ", HORN_TEXT = ?, ECHOS = ?, ECHO_TEXT = ? WHERE ID = ?";
            try (PreparedStatement st = c.prepareStatement(sql)) {
                st.setString(1, f.schiff());
                st.setString(2, f.richtung());
                int i = setSz(st, 3, f.abMs());
                setMs(st, i, f.anMs());
                setS(st, i + 1, f.anMs() == null ? null : Daten.text(f.anMs()));
                setD(st, i + 2, f.dauerMin());
                i = setGeom(st, i + 3, f.weg());
                setD(st, i, f.weg() == null ? null : r1(f.weg().laenge()));
                st.setInt(i + 1, f.hoerner());
                i = setGeom(st, i + 2, horn);
                setS(st, i, f.hornText());
                if (f.echos() == null) st.setNull(i + 1, Types.NUMERIC); else st.setInt(i + 1, f.echos());
                setS(st, i + 2, f.echoText() == null ? null : f.echoText().length() > 990 ? f.echoText().substring(0, 990) + " …" : f.echoText());
                if (neu) setL(st, i + 3, sitzung); else st.setLong(i + 3, f.id());
                st.executeUpdate();
            }
            return neu ? neueId(c, "GR_SCHIFFSFAHRT_SEQ") : f.id();
        });
    }

    @Override public void busankunft(Daten.Busankunft b, Long sitzung) throws Fehler {
        zusammen("Busankunft", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GR_BUSANKUNFT (SITZUNG_ID, SZENE_MS, SZENE_TEXT, SZENE_TAG, AB_MS, HALT, GRUPPE, LAGE) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, " + PUNKT + ")")) {
                setL(st, 1, sitzung);
                int i = setSz(st, 2, b.ms());
                st.setLong(i, b.abMs());
                setS(st, i + 1, b.halt());
                st.setInt(i + 2, b.gruppe());
                setGeom(st, i + 3, Daten.Geom.punkt(b.x(), b.z()));
                st.executeUpdate();
            }
            return null;
        });
    }

    @Override public void regielauf(Daten.Regielauf l, Long sitzung) throws Fehler {
        zusammen("Lauf eines Regiebuchs", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GR_REGIELAUF (REGIEBUCH_ID, NAME, BEGINN, ENDE, VOLLSTAENDIG, KINO, BILDER_S_MITTEL, GPU_MS_MITTEL, "
                    + "GEWARTET_S, FOTOS, QUALITAET, BLATT, BLATT_DATEI, SITZUNG_ID) VALUES (" + REGIEBUCH_ID + ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                st.setString(1, l.name());
                st.setString(2, l.name());
                st.setTimestamp(3, new Timestamp(l.beginnMs()));
                if (l.endeMs() == null) st.setNull(4, Types.TIMESTAMP); else st.setTimestamp(4, new Timestamp(l.endeMs()));
                st.setInt(5, l.vollstaendig() ? 1 : 0);
                st.setInt(6, l.kino() ? 1 : 0);
                setD(st, 7, r2(l.bilderMittel()));
                setD(st, 8, r3(l.gpuMs()));
                setD(st, 9, r1(l.gewartetS()));
                st.setInt(10, l.fotos());
                setS(st, 11, l.qualitaet());
                if (l.blatt() == null) st.setNull(12, Types.BLOB); else st.setBytes(12, l.blatt());
                setS(st, 13, l.blattDatei());
                setL(st, 14, sitzung);
                st.executeUpdate();
            }
            return null;
        });
    }

    private static final String FAHRT_SQL = "SELECT ID, SCHIFF, RICHTUNG, SZENE_MS, AN_MS, HOERNER, HORN.SDO_POINT.X AS HX, HORN.SDO_POINT.Y AS HY, HORN_TEXT, ECHOS, ECHO_TEXT";

    private static Daten.Schiffsfahrt fahrt(ResultSet r, boolean weg) throws SQLException {
        Double hx = dbl(r, "HX"), hy = dbl(r, "HY");
        return new Daten.Schiffsfahrt(r.getLong("ID"), r.getString("SCHIFF"), r.getString("RICHTUNG"), r.getLong("SZENE_MS"), ms(r, "AN_MS"), weg ? geom(r, "WKT") : null,
                r.getInt("HOERNER"), hx == null || hy == null ? null : Daten.xz(hx, hy), r.getString("HORN_TEXT"), integer(r, "ECHOS"), r.getString("ECHO_TEXT"));
    }

    @Override public Daten.Tageschronik tagesChronik(LocalDate tag) throws Fehler {
        String t = tag.toString();
        return mit("Chronik des Tages", c -> {
            List<Daten.Stunde> hs = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT SZENE_MS, LAGE, WIND_MS, RICHTUNG_GRAD, BOEE_MS, BEDECKUNG_ACHTEL, WOLKEN, REGEN_MMH, SICHT_KM, NEBEL, "
                    + "TEMPERATUR_C, ABFLUSS_ANTEIL, REGENSPEICHER_MM, FALL_TEMP_C, EIS, ABFLUSS FROM GR_STUNDE WHERE SZENE_TAG = ? ORDER BY SZENE_MS, ID")) {
                st.setString(1, t);
                try (ResultSet r = st.executeQuery()) {
                    while (r.next())
                        hs.add(new Daten.Stunde(r.getLong(1), r.getString(2), d(r, "WIND_MS"), d(r, "RICHTUNG_GRAD"), d(r, "BOEE_MS"), d(r, "BEDECKUNG_ACHTEL"), r.getString(7),
                                d(r, "REGEN_MMH"), d(r, "SICHT_KM"), d(r, "NEBEL"), d(r, "TEMPERATUR_C"), d(r, "ABFLUSS_ANTEIL"), d(r, "REGENSPEICHER_MM"), d(r, "FALL_TEMP_C"),
                                d(r, "EIS"), r.getString(16)));
                }
            }
            List<Daten.Tag> ts = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT SZENE_MS, SCHNEEGRENZE_M, SCHNEE_ORT_CM, SCHNEE_600_CM, SCHNEE_1200_CM, LAUB, HERBST, KRONEN, EIS, LUFT_C "
                    + "FROM GR_TAG WHERE SZENE_TAG = ? ORDER BY ID")) {
                st.setString(1, t);
                try (ResultSet r = st.executeQuery()) {
                    while (r.next())
                        ts.add(new Daten.Tag(r.getLong(1), dbl(r, "SCHNEEGRENZE_M"), d(r, "SCHNEE_ORT_CM"), d(r, "SCHNEE_600_CM"), d(r, "SCHNEE_1200_CM"), d(r, "LAUB"),
                                d(r, "HERBST"), d(r, "KRONEN"), d(r, "EIS"), d(r, "LUFT_C")));
                }
            }
            List<Daten.Schiffsfahrt> fs = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement(FAHRT_SQL + ", MDSYS.SDO_UTIL.TO_WKTGEOMETRY(WEG) AS WKT FROM GR_SCHIFFSFAHRT WHERE SZENE_TAG = ? ORDER BY SZENE_MS, ID")) {
                st.setString(1, t);
                try (ResultSet r = st.executeQuery()) { while (r.next()) fs.add(fahrt(r, true)); }
            }
            List<Daten.Busankunft> bs = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT b.SZENE_MS, b.AB_MS, b.HALT, b.GRUPPE, b.LAGE.SDO_POINT.X AS LX, b.LAGE.SDO_POINT.Y AS LY FROM GR_BUSANKUNFT b "
                    + "WHERE b.SZENE_TAG = ? ORDER BY b.SZENE_MS, b.ID")) {
                st.setString(1, t);
                try (ResultSet r = st.executeQuery()) {
                    while (r.next()) {
                        Double lx = dbl(r, "LX"), ly = dbl(r, "LY");
                        double[] xz = lx == null || ly == null ? new double[]{Double.NaN, Double.NaN} : Daten.xz(lx, ly);
                        bs.add(new Daten.Busankunft(r.getLong(1), r.getLong(2), r.getString(3), r.getInt(4), xz[0], xz[1]));
                    }
                }
            }
            return new Daten.Tageschronik(tag, hs, ts, fs, bs);
        });
    }

    @Override public List<Daten.Schiffsfahrt> fahrten(int n) throws Fehler {
        return mit("Fahrten", c -> {
            List<Daten.Schiffsfahrt> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement(FAHRT_SQL + " FROM GR_SCHIFFSFAHRT ORDER BY ID DESC FETCH FIRST ? ROWS ONLY")) {
                st.setInt(1, n);
                try (ResultSet r = st.executeQuery()) { while (r.next()) l.add(fahrt(r, false)); }
            }
            return l;
        });
    }

    static final String[] CHRONIK = {"GR_STUNDE", "GR_TAG", "GR_SCHIFFSFAHRT", "GR_BUSANKUNFT", "GR_REGIELAUF"};

    @Override public int chronikLeeren(long sitzung) throws Fehler {
        return zusammen("Chronik leeren", c -> {
            int n = 0;
            for (String t : CHRONIK)
                try (PreparedStatement st = c.prepareStatement("DELETE FROM " + t + " WHERE SITZUNG_ID = ?")) { st.setLong(1, sitzung); n += st.executeUpdate(); }
            return n;
        });
    }

    @Override public List<long[]> sitzungenMitChronik(int n) throws Fehler {
        return mit("Sitzungen mit Chronik", c -> {
            List<long[]> l = new ArrayList<>();
            StringBuilder ex = new StringBuilder();
            for (String t : CHRONIK) ex.append(ex.length() > 0 ? " OR " : "").append("EXISTS (SELECT 1 FROM ").append(t).append(" x WHERE x.SITZUNG_ID = s.ID)");
            try (PreparedStatement st = c.prepareStatement("SELECT s.ID, s.BEGINN, (SELECT COUNT(*) FROM GR_STUNDE h WHERE h.SITZUNG_ID = s.ID) AS H, "
                    + "(SELECT COUNT(*) FROM GR_SCHIFFSFAHRT f WHERE f.SITZUNG_ID = s.ID) AS F FROM GR_SITZUNG s WHERE " + ex + " ORDER BY s.ID DESC FETCH FIRST ? ROWS ONLY")) {
                st.setInt(1, n);
                try (ResultSet r = st.executeQuery()) { while (r.next()) l.add(new long[]{r.getLong(1), r.getTimestamp(2).getTime(), r.getLong(3), r.getLong(4)}); }
            }
            return l;
        });
    }

    // ------------------------------------------------------------------ Regiebücher und Fahrten

    @Override public List<Daten.Regiebuch> regiebuecher() throws Fehler {
        return mit("Regiebücher", c -> {
            List<Daten.Regiebuch> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT NAME, TEXT, BESCHREIBUNG, DATEI, HERKUNFT FROM GR_REGIEBUCH "
                    + "ORDER BY CASE HERKUNFT WHEN 'MITGELIEFERT' THEN 0 ELSE 1 END, ID")) {
                while (r.next()) l.add(new Daten.Regiebuch(r.getString(1), clob(r, "TEXT"), r.getString(3), r.getString(4), r.getString(5)));
            }
            return l;
        });
    }

    @Override public void speichern(Daten.Regiebuch rb) throws Fehler {
        Regie.Book b;
        try { b = Regie.parse(rb.text()); } catch (RuntimeException e) { throw new Fehler("Regiebuch nicht lesbar: " + e.getMessage(), e); }
        if (!b.name().equals(rb.name())) throw new Fehler("Der Name im Text („" + b.name() + "“) passt nicht zu „" + rb.name() + "“");
        Startdaten.Kopf k = Startdaten.Kopf.aus(b);
        zusammen("Regiebuch speichern", c -> {
            if (mitgeliefert(c, "GR_REGIEBUCH", b.name())) throw new SQLException("„" + b.name() + "“ ist mitgeliefert; bitte unter einem eigenen Namen speichern");
            try (PreparedStatement st = c.prepareStatement("DELETE FROM GR_REGIEBUCH WHERE NAME = ? AND HERKUNFT = 'EIGEN'")) { st.setString(1, b.name()); st.executeUpdate(); }
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GR_REGIEBUCH (NAME, BESCHREIBUNG, DATEI, SZENE_MS, SZENE_TEXT, ZEITRAFFER, LAENGE_S, FOTOS, WARTEN, BLATT, "
                    + "WETTER, TEXT, HERKUNFT) VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'EIGEN')")) {
                st.setString(1, b.name());
                setS(st, 2, b.about());
                setMs(st, 3, k.szeneMs());
                setS(st, 4, k.szeneMs() == null ? null : Daten.text(k.szeneMs()));
                setD(st, 5, k.raffer());
                st.setDouble(6, b.length());
                st.setInt(7, k.fotos());
                st.setInt(8, k.warten());
                st.setInt(9, k.blatt() ? 1 : 0);
                setS(st, 10, Startdaten.wetterBlock(rb.text()));
                setClob(st, 11, rb.text());
                st.executeUpdate();
            }
            long id = Long.parseLong(Db.einWert(c, "SELECT TO_CHAR(ID) FROM GR_REGIEBUCH WHERE NAME = ?", b.name()));
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GR_STICHWORT (REGIEBUCH_ID, NR, SEKUNDE, BEFEHL, ARGUMENTE, TEXT) VALUES (?, ?, ?, ?, ?, ?)")) {
                int nr = 0;
                for (Regie.Zeile z : b.zeilen()) {
                    st.setLong(1, id);
                    st.setInt(2, ++nr);
                    st.setDouble(3, z.sekunde());
                    st.setString(4, z.befehl().toUpperCase(Locale.ROOT));
                    setS(st, 5, z.argumente());
                    setS(st, 6, z.text());
                    st.addBatch();
                }
                st.executeBatch();
            }
            return null;
        });
    }

    @Override public void loeschenRegiebuch(String name) throws Fehler {
        zusammen("Regiebuch löschen", c -> {
            if (mitgeliefert(c, "GR_REGIEBUCH", name)) throw new SQLException("„" + name + "“ ist mitgeliefert und wird nicht gelöscht");
            try (PreparedStatement st = c.prepareStatement("DELETE FROM GR_REGIEBUCH WHERE NAME = ? AND HERKUNFT = 'EIGEN'")) { st.setString(1, name); st.executeUpdate(); }
            return null;
        });
    }

    @Override public List<Daten.Fahrt> fahrten() throws Fehler {
        return mit("Fahrten", c -> {
            Map<Long, List<Daten.Stuetzpunkt>> p = new LinkedHashMap<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT FAHRT_ID, NR, ZEIT_S, KAM_X, KAM_Y, KAM_Z, KAM_GIER_GRAD, KAM_NICK_GRAD "
                    + "FROM GR_STUETZPUNKT ORDER BY FAHRT_ID, NR")) {
                while (r.next())
                    p.computeIfAbsent(r.getLong(1), k -> new ArrayList<>()).add(new Daten.Stuetzpunkt(r.getInt(2), r.getDouble(3), d(r, "KAM_X"), d(r, "KAM_Y"), d(r, "KAM_Z"),
                            d(r, "KAM_GIER_GRAD"), d(r, "KAM_NICK_GRAD")));
            }
            List<Daten.Fahrt> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT ID, NAME, ART, DAUER_S, LAENGE_M, BESCHREIBUNG, HERKUNFT FROM GR_FAHRT "
                    + "ORDER BY CASE HERKUNFT WHEN 'MITGELIEFERT' THEN 0 ELSE 1 END, ID")) {
                while (r.next())
                    l.add(new Daten.Fahrt(r.getString(2), r.getString(3), dbl(r, "DAUER_S"), dbl(r, "LAENGE_M"), r.getString(6), r.getString(7), p.getOrDefault(r.getLong(1), List.of())));
            }
            return l;
        });
    }

    // ------------------------------------------------------------------ Ansichten

    private static final String ANSICHT_SQL = "SELECT ID, NAME, TASTE, ART, KAM_X, KAM_Y, KAM_Z, KAM_GIER_GRAD, KAM_NICK_GRAD, AUGE_UEBER_GRUND_M, SZENE_MS, HERKUNFT";

    private static Daten.Ansicht ansicht(ResultSet r, String zustand, byte[] png) throws SQLException {
        double[] k = {d(r, "KAM_X"), d(r, "KAM_Y"), d(r, "KAM_Z"), d(r, "KAM_GIER_GRAD"), d(r, "KAM_NICK_GRAD")};
        return new Daten.Ansicht(r.getLong("ID"), r.getString("NAME"), integer(r, "TASTE"), r.getString("ART"), k, dbl(r, "AUGE_UEBER_GRUND_M"), ms(r, "SZENE_MS"), zustand,
                png, r.getString("HERKUNFT"));
    }

    @Override public List<Daten.Ansicht> ansichten() throws Fehler {
        return mit("Ansichten", c -> {
            List<Daten.Ansicht> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery(ANSICHT_SQL + " FROM GR_ANSICHT ORDER BY CASE ART WHEN 'FEST' THEN 0 ELSE 1 END, TASTE, ID DESC")) {
                while (r.next()) l.add(ansicht(r, null, null));
            }
            return l;
        });
    }

    @Override public Daten.Ansicht ansicht(long id) throws Fehler {
        return mit("Ansicht", c -> {
            try (PreparedStatement st = c.prepareStatement(ANSICHT_SQL + ", ZUSTAND, VORSCHAU FROM GR_ANSICHT WHERE ID = ?")) {
                st.setLong(1, id);
                try (ResultSet r = st.executeQuery()) {
                    if (!r.next()) throw new SQLException("Ansicht " + id + " gibt es nicht");
                    return ansicht(r, clob(r, "ZUSTAND"), r.getBytes("VORSCHAU"));
                }
            }
        });
    }

    @Override public long speichern(Daten.Ansicht a, Long sitzung) throws Fehler {
        Daten.Geom lage = Daten.Geom.punkt(a.kamera()[0], a.kamera()[2]);
        return zusammen("Ansicht merken", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GR_ANSICHT (NAME, ART, KAM_X, KAM_Y, KAM_Z, KAM_GIER_GRAD, KAM_NICK_GRAD, LAGE, SZENE_MS, SZENE_TEXT, "
                    + "ZUSTAND, VORSCHAU, SITZUNG_ID, HERKUNFT) VALUES (?, 'GEMERKT', ?, ?, ?, ?, ?, " + geomSql(lage) + ", ?, ?, ?, ?, ?, 'EIGEN')")) {
                st.setString(1, a.name().length() > 80 ? a.name().substring(0, 80) : a.name());
                for (int i = 0; i < 5; i++) setD(st, 2 + i, r2(a.kamera()[i]));
                int i = setGeom(st, 7, lage);
                setMs(st, i, a.szeneMs());
                setS(st, i + 1, a.szeneMs() == null ? null : Daten.text(a.szeneMs()));
                setClob(st, i + 2, a.zustand());
                if (a.vorschau() == null) st.setNull(i + 3, Types.BLOB); else st.setBytes(i + 3, a.vorschau());
                setL(st, i + 4, sitzung);
                st.executeUpdate();
            }
            return neueId(c, "GR_ANSICHT_SEQ");
        });
    }

    @Override public void loeschenAnsicht(long id) throws Fehler {
        zusammen("Ansicht löschen", c -> {
            try (PreparedStatement st = c.prepareStatement("DELETE FROM GR_ANSICHT WHERE ID = ? AND HERKUNFT = 'EIGEN'")) {
                st.setLong(1, id);
                if (st.executeUpdate() == 0) throw new SQLException("Ansicht " + id + " ist mitgeliefert oder gibt es nicht");
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ Merkpunkte

    private static final String MERK_SQL = "SELECT ID, NAME, NOTIZ, X_M, Y_M, Z_M, AUGE_X, AUGE_Y, AUGE_Z, BLICK_GRAD, NICK_GRAD, SZENE_MS, HERKUNFT";

    private static Daten.Merkpunkt merk(ResultSet r, byte[] png) throws SQLException {
        double ax = d(r, "AUGE_X");
        return new Daten.Merkpunkt(r.getLong("ID"), r.getString("NAME"), r.getString("NOTIZ"), d(r, "X_M"), d(r, "Y_M"), d(r, "Z_M"),
                Double.isNaN(ax) ? null : new double[]{ax, d(r, "AUGE_Y"), d(r, "AUGE_Z")}, d(r, "BLICK_GRAD"), d(r, "NICK_GRAD"), ms(r, "SZENE_MS"), png, r.getString("HERKUNFT"));
    }

    @Override public List<Daten.Merkpunkt> merkpunkte() throws Fehler {
        return mit("Merkpunkte", c -> {
            List<Daten.Merkpunkt> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery(MERK_SQL + " FROM GR_MERKPUNKT ORDER BY ID DESC")) { while (r.next()) l.add(merk(r, null)); }
            return l;
        });
    }

    @Override public Daten.Merkpunkt merkpunkt(long id) throws Fehler {
        return mit("Merkpunkt", c -> {
            try (PreparedStatement st = c.prepareStatement(MERK_SQL + ", VORSCHAU FROM GR_MERKPUNKT WHERE ID = ?")) {
                st.setLong(1, id);
                try (ResultSet r = st.executeQuery()) {
                    if (!r.next()) throw new SQLException("Merkpunkt " + id + " gibt es nicht");
                    return merk(r, r.getBytes("VORSCHAU"));
                }
            }
        });
    }

    @Override public long speichern(Daten.Merkpunkt m, Long sitzung) throws Fehler {
        Daten.Geom lage = Daten.Geom.punkt(m.x(), m.z());
        return zusammen("Merkpunkt", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GR_MERKPUNKT (NAME, NOTIZ, LAGE, X_M, Y_M, Z_M, AUGE_X, AUGE_Y, AUGE_Z, BLICK_GRAD, NICK_GRAD, ABSTAND_M, "
                    + "SZENE_MS, SZENE_TEXT, VORSCHAU, SITZUNG_ID, HERKUNFT) VALUES (?, ?, " + geomSql(lage) + ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'EIGEN')")) {
                st.setString(1, m.name().length() > 80 ? m.name().substring(0, 80) : m.name());
                setS(st, 2, m.notiz() == null ? null : m.notiz().length() > 1000 ? m.notiz().substring(0, 1000) : m.notiz());
                int i = setGeom(st, 3, lage);
                setD(st, i, r1(m.x()));
                setD(st, i + 1, r1(m.y()));
                setD(st, i + 2, r1(m.z()));
                double[] a = m.auge();
                setD(st, i + 3, a == null ? null : r1(a[0]));
                setD(st, i + 4, a == null ? null : r1(a[1]));
                setD(st, i + 5, a == null ? null : r1(a[2]));
                setD(st, i + 6, r1(m.blick()));
                setD(st, i + 7, r1(m.nick()));
                setD(st, i + 8, r1(m.abstand()));
                setMs(st, i + 9, m.szeneMs());
                setS(st, i + 10, m.szeneMs() == null ? null : Daten.text(m.szeneMs()));
                if (m.vorschau() == null) st.setNull(i + 11, Types.BLOB); else st.setBytes(i + 11, m.vorschau());
                setL(st, i + 12, sitzung);
                st.executeUpdate();
            }
            return neueId(c, "GR_MERKPUNKT_SEQ");
        });
    }

    @Override public void loeschenMerkpunkt(long id) throws Fehler {
        zusammen("Merkpunkt löschen", c -> {
            try (PreparedStatement st = c.prepareStatement("DELETE FROM GR_MERKPUNKT WHERE ID = ? AND HERKUNFT = 'EIGEN'")) {
                st.setLong(1, id);
                if (st.executeUpdate() == 0) throw new SQLException("Merkpunkt " + id + " gibt es nicht");
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ Presets

    @Override public List<Daten.PaketPreset> presets(String paket, String klasse) throws Fehler {
        return mit("Presets", c -> {
            Map<Long, Map<String, String>> w = new LinkedHashMap<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT PAKET_PRESET_ID, FELD, WERT FROM GR_PAKET_WERT ORDER BY PAKET_PRESET_ID, ID")) {
                while (r.next()) w.computeIfAbsent(r.getLong(1), k -> new LinkedHashMap<>()).put(r.getString(2), r.getString(3) == null ? "" : r.getString(3));
            }
            List<Daten.PaketPreset> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT ID, PAKET, KLASSE, NAME, BESCHREIBUNG, HERKUNFT FROM GR_PAKET_PRESET WHERE (? IS NULL OR PAKET = ?) "
                    + "AND (? IS NULL OR KLASSE = ?) ORDER BY PAKET, KLASSE, CASE HERKUNFT WHEN 'MITGELIEFERT' THEN 0 ELSE 1 END, NAME")) {
                st.setString(1, paket);
                st.setString(2, paket);
                st.setString(3, klasse);
                st.setString(4, klasse);
                try (ResultSet r = st.executeQuery()) {
                    while (r.next())
                        l.add(new Daten.PaketPreset(r.getString(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6), w.getOrDefault(r.getLong(1), Map.of())));
                }
            }
            return l;
        });
    }

    @Override public void speichern(Daten.PaketPreset p) throws Fehler {
        Map<String, String> typ = Startdaten.typen(p);
        zusammen("Preset speichern", c -> {
            if (zahl(c, "SELECT COUNT(*) FROM GR_PAKET_PRESET WHERE PAKET = ? AND KLASSE = ? AND NAME = ? AND HERKUNFT = 'MITGELIEFERT'", p.paket(), p.klasse(), p.name()) > 0)
                throw new SQLException("„" + p.name() + "“ ist mitgeliefert; bitte unter einem eigenen Namen speichern");
            try (PreparedStatement st = c.prepareStatement("DELETE FROM GR_PAKET_PRESET WHERE PAKET = ? AND KLASSE = ? AND NAME = ? AND HERKUNFT = 'EIGEN'")) {
                st.setString(1, p.paket()); st.setString(2, p.klasse()); st.setString(3, p.name()); st.executeUpdate();
            }
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GR_PAKET_PRESET (PAKET, KLASSE, NAME, BESCHREIBUNG, HERKUNFT) VALUES (?, ?, ?, ?, 'EIGEN')")) {
                st.setString(1, p.paket()); st.setString(2, p.klasse()); st.setString(3, p.name()); setS(st, 4, p.beschreibung()); st.executeUpdate();
            }
            long id = Long.parseLong(Db.einWert(c, "SELECT TO_CHAR(ID) FROM GR_PAKET_PRESET WHERE PAKET = ? AND KLASSE = ? AND NAME = ?", p.paket(), p.klasse(), p.name()));
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GR_PAKET_WERT (PAKET_PRESET_ID, FELD, TYP, WERT) VALUES (?, ?, ?, ?)")) {
                for (Map.Entry<String, String> e : p.felder().entrySet()) {
                    st.setLong(1, id);
                    st.setString(2, e.getKey());
                    st.setString(3, typ.getOrDefault(e.getKey(), "STRING"));
                    setS(st, 4, e.getValue());
                    st.addBatch();
                }
                st.executeBatch();
            }
            return null;
        });
    }

    @Override public void loeschenPreset(String paket, String klasse, String name) throws Fehler {
        zusammen("Preset löschen", c -> {
            try (PreparedStatement st = c.prepareStatement("DELETE FROM GR_PAKET_PRESET WHERE PAKET = ? AND KLASSE = ? AND NAME = ? AND HERKUNFT = 'EIGEN'")) {
                st.setString(1, paket); st.setString(2, klasse); st.setString(3, name);
                if (st.executeUpdate() == 0) throw new SQLException("„" + name + "“ ist mitgeliefert oder gibt es nicht");
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ Sitzungen

    @Override public long sitzungBeginnen(String version, String gpu, String qualitaet) throws Fehler {
        return zusammen("Sitzung beginnen", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GR_SITZUNG (RECHNER, BENUTZER, APP_VERSION, GPU, QUALITAET) VALUES (?, ?, ?, ?, ?)")) {
                setS(st, 1, com.dan.store.DateiAblage.rechner());
                setS(st, 2, System.getProperty("user.name", ""));
                st.setString(3, version.length() > 20 ? version.substring(0, 20) : version);
                setS(st, 4, gpu == null ? null : gpu.length() > 120 ? gpu.substring(0, 120) : gpu);
                setS(st, 5, qualitaet);
                st.executeUpdate();
            }
            long id = neueId(c, "GR_SITZUNG_SEQ");
            try (Statement st = c.createStatement()) { st.executeUpdate("DELETE FROM GR_LOG WHERE ZEIT < SYSTIMESTAMP - INTERVAL '90' DAY"); }
            return id;
        });
    }

    @Override public void sitzungSichern(long id, Sicherung s) throws Fehler {
        Map<String, String> sp = s.spalten();
        double rs = Zahl.lies(sp.get(REGIE_SEKUNDE)), zr = Zahl.lies(sp.get(ZEITRAFFER)), msz = Zahl.lies(sp.get(SZENE_MS));
        String[] k = sp.getOrDefault(KAMERA, "").split(" ");
        double[] kam = new double[5];
        for (int i = 0; i < 5; i++) kam[i] = i < k.length ? Zahl.lies(k[i]) : Double.NaN;
        Daten.Geom auge = Double.isNaN(kam[0]) || Double.isNaN(kam[2]) ? null : Daten.Geom.punkt(kam[0], kam[2]);
        String ton = sp.get(TON);
        zusammen("Sitzung sichern", c -> {
            try (PreparedStatement st = c.prepareStatement("UPDATE GR_SITZUNG SET ZUSTAND = ?, ZUSTAND_ZEIT = SYSTIMESTAMP, REGIEBUCH_ID = " + REGIEBUCH_ID + ", REGIE_SEKUNDE = ?, "
                    + "SZENE_MS = ?, SZENE_TEXT = ?, ZEITRAFFER = ?, AUGE = " + geomSql(auge) + ", KAM_X = ?, KAM_Y = ?, KAM_Z = ?, KAM_GIER_GRAD = ?, KAM_NICK_GRAD = ?, "
                    + "QUALITAET = ?, TON = ? WHERE ID = ?")) {
                setClob(st, 1, s.zustand());
                setS(st, 2, sp.get(REGIEBUCH));
                setD(st, 3, Double.isNaN(rs) ? null : Math.round(rs * 100) / 100.0);
                if (Double.isNaN(msz)) { st.setNull(4, Types.NUMERIC); st.setNull(5, Types.VARCHAR); }
                else { st.setLong(4, (long) msz); st.setString(5, Daten.text(msz)); }
                setD(st, 6, Double.isNaN(zr) ? null : zr);
                int i = setGeom(st, 7, auge);
                for (int j = 0; j < 5; j++) setD(st, i + j, j < 3 ? r1(kam[j]) : r2(kam[j]));
                setS(st, i + 5, sp.get(QUALITAET));
                if (ton == null || ton.isEmpty()) st.setNull(i + 6, Types.NUMERIC); else st.setInt(i + 6, Boolean.parseBoolean(ton) ? 1 : 0);
                st.setLong(i + 7, id);
                st.executeUpdate();
            }
            return null;
        });
    }

    @Override public void sitzungBeenden(long id) throws Fehler {
        zusammen("Sitzung beenden", c -> {
            try (PreparedStatement st = c.prepareStatement("UPDATE GR_SITZUNG SET ENDE = SYSTIMESTAMP, SAUBER_BEENDET = 1 WHERE ID = ?")) { st.setLong(1, id); st.executeUpdate(); }
            return null;
        });
    }

    private static Daten.Sitzung letzte(Connection c, long ausser) throws SQLException {
        try (PreparedStatement st = c.prepareStatement("SELECT s.ID, s.BEGINN, s.ENDE, s.SAUBER_BEENDET, s.ZUSTAND, s.ZUSTAND_ZEIT, r.NAME AS REGIEBUCH, s.REGIE_SEKUNDE, "
                + "s.SZENE_MS, s.ZEITRAFFER, s.QUALITAET, s.TON FROM GR_SITZUNG s LEFT JOIN GR_REGIEBUCH r ON r.ID = s.REGIEBUCH_ID WHERE s.ZUSTAND IS NOT NULL AND s.ID <> ? "
                + "ORDER BY s.ZUSTAND_ZEIT DESC NULLS LAST, s.ID DESC FETCH FIRST 1 ROWS ONLY")) {
            st.setLong(1, ausser);
            try (ResultSet r = st.executeQuery()) {
                if (!r.next()) return null;
                Timestamp b = r.getTimestamp(2), e = r.getTimestamp(3), zz = r.getTimestamp(6);
                Integer ton = integer(r, "TON");
                return new Daten.Sitzung(r.getLong(1), b.getTime(), e == null ? null : e.getTime(), r.getInt(4) == 1, clob(r, "ZUSTAND"), zz == null ? null : zz.getTime(),
                        r.getString(7), dbl(r, "REGIE_SEKUNDE"), ms(r, "SZENE_MS"), dbl(r, "ZEITRAFFER"), r.getString("QUALITAET"), ton == null ? null : ton == 1);
            }
        }
    }

    @Override public Daten.Sitzung letzteSitzung(long ausser) throws Fehler { return mit("Letzte Sitzung", c -> letzte(c, ausser)); }

    // ------------------------------------------------------------------ Protokoll

    @Override public void protokoll(List<Protokoll.Zeile> zeilen, Long sitzung) throws Fehler {
        if (zeilen.isEmpty()) return;
        zusammen("Protokoll", c -> {
            List<Long> ids = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT GR_LOG_SEQ.NEXTVAL FROM DUAL CONNECT BY LEVEL <= ?")) {
                st.setInt(1, zeilen.size());
                try (ResultSet r = st.executeQuery()) { while (r.next()) ids.add(r.getLong(1)); }
            }
            try (PreparedStatement st = c.prepareStatement("INSERT INTO GR_LOG (ID, SITZUNG_ID, ZEIT, STUFE, QUELLE, TEXT, SZENENZEIT, ORT, DETAIL) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (int i = 0; i < zeilen.size(); i++) {
                    Protokoll.Zeile z = zeilen.get(i);
                    st.setLong(1, ids.get(i));
                    setL(st, 2, sitzung);
                    st.setTimestamp(3, new Timestamp(z.zeitMs()));
                    st.setString(4, z.stufe());
                    st.setString(5, z.quelle().length() > 60 ? z.quelle().substring(0, 60) : z.quelle());
                    st.setString(6, z.text().length() > 1000 ? z.text().substring(0, 997) + " …" : z.text());
                    setS(st, 7, z.szenenzeit() == null ? null : z.szenenzeit().length() > 40 ? z.szenenzeit().substring(0, 40) : z.szenenzeit());
                    setS(st, 8, z.ort() == null ? null : z.ort().length() > 40 ? z.ort().substring(0, 40) : z.ort());
                    setClob(st, 9, z.detail());
                    st.addBatch();
                }
                st.executeBatch();
            }
            return null;
        });
    }

    @Override public List<String> letzteZeilen(int n) throws Fehler {
        return mit("Protokoll lesen", c -> {
            List<String> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT ZEIT, STUFE, QUELLE, TEXT FROM GR_LOG ORDER BY ID DESC FETCH FIRST ? ROWS ONLY")) {
                st.setInt(1, n);
                try (ResultSet r = st.executeQuery()) {
                    while (r.next())
                        l.add(0, Protokoll.zeile(new Protokoll.Zeile(r.getTimestamp(1).getTime(), r.getString(2), r.getString(3), r.getString(4), null, null, null)));
                }
            }
            return l;
        });
    }

    @Override public synchronized void schliessen() {
        try { if (c != null) c.close(); } catch (SQLException e) { /* zu */ }
        c = null;
    }
}
