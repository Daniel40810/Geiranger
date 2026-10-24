package com.dan.geiranger.data;

import com.dan.store.Db;
import com.dan.store.Einrichter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Das Schema GEIRANGER (Phase 17, Werkbuch Abschnitt 8): Ort von {@code db/}, Zugang aus {@code db/db.properties} und
 * der Einrichter mit allem, was er für Geiranger prüft. Schema und Benutzer legt {@code db/USER.sql} an
 * ({@code manage_user('Geiranger', …)} auf PDBORCL).
 */
public final class Verbindung {
    private Verbindung() { }

    public static final String PRAEFIX = "GR_";
    public static final String ENV = "GEIRANGER_DB_PASSWORD";
    /** Die Ausbaustufe dieser App (GR_SCHEMA.VERSION). */
    public static final int VERSION = 1;
    public static final String[] SICHTEN = {"GR_FALL_V", "GR_GEIRANGER_V", "GR_TAGESKARTE_V", "GR_FAHRPLAN_V", "GR_JAHR_V", "GR_BEFUND_V", "GR_LETZTE_SITZUNG_V"};
    public static final String[] METADATEN = {"GR_GELAENDE.AUSSCHNITT", "GR_KERNFENSTER.FLAECHE", "GR_FORM.FLAECHE", "GR_HOEHENLINIE.LINIE", "GR_FALL.KANTE",
            "GR_STRAEHNE.LINIE", "GR_STRAEHNE.AUFPRALL", "GR_ELVA.LINIE", "GR_STRASSE.LINIE", "GR_KEHRE.SCHEITEL", "GR_ORT.LAGE", "GR_GEBAEUDE.GRUNDRISS",
            "GR_SCHIFFSWEG.GEOM", "GR_SITZUNG.AUGE", "GR_SCHIFFSFAHRT.WEG", "GR_SCHIFFSFAHRT.HORN", "GR_BUSANKUNFT.LAGE", "GR_ANSICHT.LAGE", "GR_FAHRT.WEG",
            "GR_MERKPUNKT.LAGE"};

    /** Der Ordner db: -Dgeiranger.db.dir, sonst ./db, sonst db neben dem Jar. */
    public static File ordner() { return Db.ordner("geiranger.db.dir", Verbindung.class); }

    public static File skripte() { return new File(ordner(), "sql"); }

    /**
     * Der Zugang aus db/db.properties. Fehlt die Datei, wird sie aus der Vorlage db.properties.example angelegt (ohne
     * Passwort); das Startbild fragt dann danach und trägt es nach dem Verbinden ein.
     */
    public static Db.Konfig konfig() throws IOException {
        File f = new File(ordner(), "db.properties"), vorlage = new File(ordner(), "db.properties.example");
        if (!f.isFile() && vorlage.isFile()) Files.copy(vorlage.toPath(), f.toPath());
        return Db.konfig(f, "Geiranger", ENV);
    }

    /** Die räumlichen Indizes (je Geometriespalte einer: GR_FALL_KANTE_SX …). */
    public static String[] raeumlich() {
        String[] r = new String[METADATEN.length];
        for (int i = 0; i < r.length; i++) r[i] = METADATEN[i].replace('.', '_') + "_SX";
        return r;
    }

    /** Die Zahlen der Startdaten aus der Zeile {@code -- ZAHLEN: Art=n; …} von 04_startdaten.sql. */
    public static Map<String, Integer> zahlen(File dir) {
        Map<String, Integer> m = new LinkedHashMap<>();
        try (var lines = Files.lines(new File(dir, "04_startdaten.sql").toPath(), StandardCharsets.UTF_8)) {
            String z = lines.filter(l -> l.startsWith("-- ZAHLEN:")).findFirst().orElse(null);
            if (z != null)
                for (String p : z.substring(10).split(";")) {
                    String[] kv = p.strip().split("=");
                    if (kv.length == 2) m.put(kv[0].strip(), Integer.parseInt(kv[1].strip()));
                }
        } catch (IOException | RuntimeException e) { /* dann prüft der Einrichter ohne Zahlen */ }
        return m;
    }

    /** Die SQL-Abfrage je Zahl der Startdaten. */
    public static final Map<String, String> ZAEHLEN = new LinkedHashMap<>();
    static {
        String m = " WHERE HERKUNFT = 'MITGELIEFERT'";
        ZAEHLEN.put("Quellen", "SELECT COUNT(*) FROM GR_QUELLE" + m);
        ZAEHLEN.put("Befunde", "SELECT COUNT(*) FROM GR_BEFUND" + m);
        ZAEHLEN.put("Belege", "SELECT COUNT(*) FROM GR_BEFUND_QUELLE x JOIN GR_BEFUND b ON b.ID = x.BEFUND_ID WHERE b.HERKUNFT = 'MITGELIEFERT'");
        ZAEHLEN.put("Raster", "SELECT COUNT(*) FROM GR_GELAENDE" + m);
        ZAEHLEN.put("Kernfenster", "SELECT COUNT(*) FROM GR_KERNFENSTER" + m);
        ZAEHLEN.put("Formen", "SELECT COUNT(*) FROM GR_FORM" + m);
        ZAEHLEN.put("Höhenlinien", "SELECT COUNT(*) FROM GR_HOEHENLINIE" + m);
        ZAEHLEN.put("Fälle", "SELECT COUNT(*) FROM GR_FALL" + m);
        ZAEHLEN.put("Strähnen", "SELECT COUNT(*) FROM GR_STRAEHNE x JOIN GR_FALL f ON f.ID = x.FALL_ID WHERE f.HERKUNFT = 'MITGELIEFERT'");
        ZAEHLEN.put("Elva", "SELECT COUNT(*) FROM GR_ELVA" + m);
        ZAEHLEN.put("Klima", "SELECT COUNT(*) FROM GR_KLIMA_MONAT" + m);
        ZAEHLEN.put("Höhenstufen", "SELECT COUNT(*) FROM GR_HOEHENSTUFE" + m);
        ZAEHLEN.put("Straßen", "SELECT COUNT(*) FROM GR_STRASSE" + m);
        ZAEHLEN.put("Kehren", "SELECT COUNT(*) FROM GR_KEHRE" + m);
        ZAEHLEN.put("Orte", "SELECT COUNT(*) FROM GR_ORT" + m);
        ZAEHLEN.put("Gebäude", "SELECT COUNT(*) FROM GR_GEBAEUDE" + m);
        ZAEHLEN.put("Schiffe", "SELECT COUNT(*) FROM GR_SCHIFF" + m);
        ZAEHLEN.put("Abfahrten", "SELECT COUNT(*) FROM GR_FAHRPLAN" + m);
        ZAEHLEN.put("Schiffswege", "SELECT COUNT(*) FROM GR_SCHIFFSWEG" + m);
        ZAEHLEN.put("Wetterbücher", "SELECT COUNT(*) FROM GR_WETTERBUCH" + m);
        ZAEHLEN.put("Regiebücher", "SELECT COUNT(*) FROM GR_REGIEBUCH" + m);
        ZAEHLEN.put("Stichworte", "SELECT COUNT(*) FROM GR_STICHWORT x JOIN GR_REGIEBUCH r ON r.ID = x.REGIEBUCH_ID WHERE r.HERKUNFT = 'MITGELIEFERT'");
        ZAEHLEN.put("Ansichten", "SELECT COUNT(*) FROM GR_ANSICHT" + m + " AND ART = 'FEST'");
        ZAEHLEN.put("Fahrten", "SELECT COUNT(*) FROM GR_FAHRT" + m);
        ZAEHLEN.put("Stützpunkte", "SELECT COUNT(*) FROM GR_STUETZPUNKT x JOIN GR_FAHRT f ON f.ID = x.FAHRT_ID WHERE f.HERKUNFT = 'MITGELIEFERT'");
        ZAEHLEN.put("Presets", "SELECT COUNT(*) FROM GR_PAKET_PRESET" + m);
        ZAEHLEN.put("Presetwerte", "SELECT COUNT(*) FROM GR_PAKET_WERT x JOIN GR_PAKET_PRESET p ON p.ID = x.PAKET_PRESET_ID WHERE p.HERKUNFT = 'MITGELIEFERT'");
    }

    /** Der Einrichter für das Schema GEIRANGER. */
    public static Einrichter einrichter() {
        Einrichter e = new Einrichter();
        e.praefix = PRAEFIX;
        e.version = VERSION;
        e.sichten = SICHTEN.clone();
        e.metadaten = METADATEN.clone();
        e.raeumlich = raeumlich();
        e.startdaten = () -> {
            List<Einrichter.Soll> l = new ArrayList<>();
            Map<String, Integer> z = zahlen(skripte());
            for (Map.Entry<String, String> q : ZAEHLEN.entrySet()) if (z.containsKey(q.getKey())) l.add(new Einrichter.Soll(q.getKey(), q.getValue(), z.get(q.getKey())));
            return l;
        };
        e.rechnet = () -> List.of(
                new Einrichter.Soll("Sieben Schwestern: Kante über NN, Juni-Abfluss, Wege",
                        "SELECT TO_CHAR(Y_M, 'FM9990') || ' m, ' || TO_CHAR(JUNI_M3S, 'FM990.0') || ' m³/s, ' || TO_CHAR(WEGE) || ' Wege' FROM GR_FALL_V WHERE NAME = 'Sieben Schwestern'", -1),
                new Einrichter.Soll("Fv63 Ørnevegen (km auf dem Ellipsoid)",
                        "SELECT TO_CHAR(MDSYS.SDO_GEOM.SDO_LENGTH(LINIE, 0.05, 'unit=KM'), 'FM990.00') FROM GR_STRASSE WHERE ART = 'ORNEVEGEN'", -1),
                new Einrichter.Soll("Hauptraster (km² auf dem Ellipsoid, Szene 24,6 × 24,6 = 604)",
                        "SELECT TO_CHAR(MDSYS.SDO_GEOM.SDO_AREA(AUSSCHNITT, 0.05, 'unit=SQ_KM'), 'FM9990.0') FROM GR_GELAENDE WHERE NAME = 'Hauptraster 12 m'", -1),
                new Einrichter.Soll("Gebäude in Kern Ost (räumlich)",
                        "SELECT TO_CHAR(COUNT(*)) FROM GR_GEBAEUDE g, GR_KERNFENSTER k WHERE k.NAME = 'Kern Ost' AND MDSYS.SDO_ANYINTERACT(g.GRUNDRISS, k.FLAECHE) = 'TRUE'", -1),
                new Einrichter.Soll("Höhenlinien (km zusammen)", "SELECT TO_CHAR(ROUND(SUM(LAENGE_KM))) || ' km in ' || TO_CHAR(COUNT(*)) || ' Höhen' FROM GR_HOEHENLINIE", -1),
                new Einrichter.Soll("Fahrplan je Jahr (Abfahrten)",
                        "SELECT LISTAGG(JAHR || ': ' || N, ', ') WITHIN GROUP (ORDER BY JAHR) FROM (SELECT JAHR, COUNT(*) AS N FROM GR_FAHRPLAN GROUP BY JAHR)", -1),
                new Einrichter.Soll("Chronik: Stunden, Tage, Fahrten, Busse, Läufe der Regiebücher",
                        "SELECT (SELECT COUNT(*) FROM GR_STUNDE) || ', ' || (SELECT COUNT(*) FROM GR_TAG) || ', ' || (SELECT COUNT(*) FROM GR_SCHIFFSFAHRT) || ', ' "
                                + "|| (SELECT COUNT(*) FROM GR_BUSANKUNFT) || ', ' || (SELECT COUNT(*) FROM GR_REGIELAUF) FROM DUAL", -1),
                new Einrichter.Soll("Tageskarte: Objekte in GR_TAGESKARTE_V", "SELECT TO_CHAR(COUNT(*)) FROM GR_TAGESKARTE_V", -1));
        return e;
    }
}
