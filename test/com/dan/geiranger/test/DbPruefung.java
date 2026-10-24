package com.dan.geiranger.test;

import com.dan.geiranger.data.Daten;
import com.dan.geiranger.data.DbSpeicher;
import com.dan.geiranger.data.Laden;
import com.dan.geiranger.data.Verbindung;
import com.dan.store.Db;
import com.dan.store.Einrichter;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

/**
 * Prüfung gegen die echte Datenbank (Phase 17): in NetBeans mit „Run File“ starten, nachdem „Einrichten“ im Startbild
 * gelaufen ist. Liest den Zugang aus db/db.properties (Passwort dort oder in GEIRANGER_DB_PASSWORD), prüft das Schema
 * mit dem Einrichter, fragt jede Sicht ab, lädt Fahrplan, Klima und Regiebücher aus GR_ und schreibt einen eigenen
 * Merkpunkt hin und wieder weg (die Chronik und die Sitzungen bleiben unberührt). Endet mit „Alles in Ordnung.“
 */
public final class DbPruefung {
    private DbPruefung() { }

    static int fehler = 0;

    static void check(boolean ok, String text) {
        System.out.println((ok ? "  ok    " : "  FEHLER ") + text);
        if (!ok) fehler++;
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.GERMANY);
        long t0 = System.nanoTime();
        var k = Verbindung.konfig();
        System.out.println("Datenbank: " + k);
        try (Connection c = Db.oeffnen(k)) {
            Einrichter e = Verbindung.einrichter();
            Einrichter.Ergebnis p = e.pruefen(c, Verbindung.skripte());
            for (String z : p.zeilen()) System.out.println("        " + z);
            check(p.ok(), "Einrichter prüft Tabellen, Sichten, Metadaten, Indizes und Startdaten: " + p.kurz());
            check(e.stufe(c) == Verbindung.VERSION, "Schema-Stufe " + e.stufe(c) + " (Soll " + Verbindung.VERSION + ")");
            for (String v : Verbindung.SICHTEN) {
                try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + v)) {
                    r.next();
                    check(true, v + ": " + r.getLong(1) + " Zeilen");
                } catch (java.sql.SQLException x) {
                    check(false, v + ": " + Db.erste(x));
                }
            }
            String tz = Db.einWert(c, "SELECT TO_CHAR(FROM_TZ(TIMESTAMP '2026-06-21 06:00:00', 'UTC') AT TIME ZONE 'Europe/Oslo', 'HH24:MI TZR') FROM DUAL");
            check("08:00 EUROPE/OSLO".equalsIgnoreCase(tz), "Zeitzone der SZENENZEIT: 06:00 UTC am 21.6. ist " + tz);
        }
        DbSpeicher db = new DbSpeicher(k);
        try {
            Daten.Stand st = db.stand();
            check(st.faelle() >= 11 && st.gebaeude() >= 60 && st.kehren() == 11 && st.abfahrten() >= 14 && st.quellen() >= 10,
                    String.format("Stand: %d Fälle, %d Gebäude, %d Kehren, %d Abfahrten, %d Quellen, %d Befunde, %d Presets; Chronik %d Stunden, %d Fahrten",
                            st.faelle(), st.gebaeude(), st.kehren(), st.abfahrten(), st.quellen(), st.befunde(), st.presets(), st.stunden(), st.fahrten()));
            String l = Laden.laden(db, 2026);
            check(l.contains("Fahrplan") && l.contains("Klima (12 Monate)") && com.dan.geiranger.scene.Regie.all().size() >= 3, "Laden aus GR_: " + l);
            long id = db.speichern(new Daten.Merkpunkt(0, "~DbPrüfung", "wird gleich wieder gelöscht", -5700, 3, -790, new double[]{-5400, 40, -700}, 290, 8,
                    1782041400000L, null, Daten.EIGEN), null);
            Daten.Merkpunkt m = db.merkpunkt(id);
            db.loeschenMerkpunkt(id);
            boolean weg = db.merkpunkte().stream().noneMatch(x -> x.id() == id);
            check(m != null && "wird gleich wieder gelöscht".equals(m.notiz()) && Math.abs(m.x() + 5700) < 0.5 && weg,
                    String.format(Locale.GERMAN, "Merkpunkt (SDO_POINT in 8307) geschrieben, gelesen (%.0f m vom Auge) und gelöscht", m == null ? Double.NaN : m.abstand()));
        } finally {
            db.schliessen();
        }
        System.out.printf(Locale.GERMAN, "%n%.1f s%n", (System.nanoTime() - t0) / 1e9);
        System.out.println(fehler == 0 ? "Alles in Ordnung." : fehler + " Fehler.");
        if (fehler > 0) System.exit(1);
    }
}
