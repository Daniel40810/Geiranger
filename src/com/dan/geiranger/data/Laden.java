package com.dan.geiranger.data;

import com.dan.geiranger.scene.Flotte;
import com.dan.geiranger.scene.Jahr;
import com.dan.geiranger.scene.Regie;
import com.dan.geiranger.wiring.Abfluss;
import com.dan.geiranger.wiring.KlimaGeiranger;
import com.dan.store.Protokoll;
import com.dan.weather.Scenario;

import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Was die App beim Start aus dem {@link Speicher} liest (F26 A): der Fahrplan (GR_FAHRPLAN → {@code Flotte.ABFAHRTEN},
 * Fahrzeit, Saison), das Klima je Monat (GR_KLIMA_MONAT → Temperatur, Niederschlag, Regentage, Abflussanteil), die
 * Höhenstufen (GR_HOEHENSTUFE → Abnahme der Temperatur, Zunahme des Niederschlags), die Wetter-Regiebücher
 * (GR_WETTERBUCH) und die Regiebücher (GR_REGIEBUCH, mitgelieferte und eigene). Was fehlt oder nicht passt, bleibt beim
 * Code; das sagt {@link Datenseite#herkunft}. Muss vor dem Bau der Szene laufen (Flotte und Jahr lesen beim Bau).
 */
public final class Laden {
    private Laden() { }

    /** Liest alles; gibt eine Zeile für Protokoll und Startbild zurück. */
    public static String laden(Speicher sp, int jahr) {
        Startdaten.sichern();
        List<String> ok = new ArrayList<>(), aus = new ArrayList<>();
        String quelle = sp.datenbank() ? "Datenbank" : "Dateien";
        try { ok.add(fahrplan(sp.fahrplan(), jahr)); } catch (Exception e) { aus.add("Fahrplan (" + e.getMessage() + ")"); }
        try { ok.add(klima(sp.klima())); } catch (Exception e) { aus.add("Klima (" + e.getMessage() + ")"); }
        try { ok.add(hoehenstufen(sp.hoehenstufen())); } catch (Exception e) { aus.add("Höhenstufen (" + e.getMessage() + ")"); }
        try { ok.add(wetterbuecher(sp.wetterbuecher())); } catch (Exception e) { aus.add("Wetterbücher (" + e.getMessage() + ")"); }
        try { ok.add(regiebuecher(sp.regiebuecher())); } catch (Exception e) { aus.add("Regiebücher (" + e.getMessage() + ")"); }
        Datenseite.herkunft = aus.isEmpty() ? quelle : ok.isEmpty() ? "Code" : quelle + " (teils Code)";
        String z = "Aus " + sp.wo() + ": " + String.join(" · ", ok) + (aus.isEmpty() ? "" : " · aus dem Code: " + String.join(", ", aus));
        Protokoll.info("Daten", z);
        return z;
    }

    /** Der Fahrplan des Jahres (sonst des jüngsten Jahres davor, sonst des ältesten); beide Richtungen zu denselben Zeiten. */
    static String fahrplan(List<Daten.Fahrplan> l, int jahr) {
        if (l.isEmpty()) throw new IllegalStateException("leer");
        int best = Integer.MIN_VALUE, min = Integer.MAX_VALUE;
        for (Daten.Fahrplan f : l) { if (f.jahr() <= jahr && f.jahr() > best) best = f.jahr(); min = Math.min(min, f.jahr()); }
        int j = best == Integer.MIN_VALUE ? min : best;
        TreeSet<Integer> zeiten = new TreeSet<>();
        int dauer = -1;
        String von = null, bis = null;
        for (Daten.Fahrplan f : l) {
            if (f.jahr() != j) continue;
            zeiten.add(f.abfahrtMin());
            dauer = Math.max(dauer, f.fahrzeitMin());
            von = f.saisonVon();
            bis = f.saisonBis();
        }
        if (zeiten.isEmpty() || dauer <= 0) throw new IllegalStateException("kein Fahrplan für " + j);
        double[] ab = new double[zeiten.size()];
        int i = 0;
        for (int m : zeiten) ab[i++] = m / 60.0;
        MonthDay a = MonthDay.parse("--" + von), b = MonthDay.parse("--" + bis);
        Flotte.ABFAHRTEN = ab;
        Flotte.FAHRZEIT = dauer * 60.0;
        Flotte.SAISON_AB = a;
        Flotte.SAISON_BIS = b;
        return String.format(Locale.GERMAN, "Fahrplan %d (%d Abfahrten, %d min, %s bis %s)", j, zeiten.size(), dauer, von, bis);
    }

    static String klima(List<Daten.KlimaMonat> l) {
        if (l.size() != 12) throw new IllegalStateException(l.size() + " statt 12 Monate");
        double[] t = new double[12], p = new double[12], r = new double[12], a = new double[12];
        for (Daten.KlimaMonat k : l) {
            int m = k.monat() - 1;
            if (m < 0 || m > 11) throw new IllegalStateException("Monat " + k.monat());
            t[m] = k.temperaturC(); p[m] = k.niederschlagMm(); r[m] = k.regentage(); a[m] = k.abflussAnteil();
        }
        System.arraycopy(t, 0, KlimaGeiranger.MONATSMITTEL, 0, 12);
        System.arraycopy(p, 0, Jahr.NIEDERSCHLAG, 0, 12);
        System.arraycopy(r, 0, Jahr.REGENTAGE, 0, 12);
        System.arraycopy(a, 0, Abfluss.MONAT, 0, 12);
        return "Klima (12 Monate)";
    }

    static String hoehenstufen(List<Daten.Hoehenstufe> l) {
        if (l.size() != Jahr.HOEHEN.length) throw new IllegalStateException(l.size() + " statt " + Jahr.HOEHEN.length + " Stufen");
        for (int i = 0; i < l.size(); i++) Jahr.HOEHEN[i] = l.get(i).hoeheM();
        Jahr.LAPSE = l.get(0).abnahme();
        Jahr.MEHR_JE_M = l.get(0).mehrJeM();
        return "Höhenstufen (" + l.size() + ")";
    }

    static String wetterbuecher(List<Daten.Wetterbuch> l) {
        List<Scenario> s = new ArrayList<>();
        for (Daten.Wetterbuch w : l) {
            try { s.add(Scenario.parse(w.text())); } catch (RuntimeException e) { Protokoll.warnung("Daten", "Wetterbuch „" + w.name() + "“ nicht lesbar: " + e.getMessage()); }
        }
        if (s.isEmpty()) throw new IllegalStateException("keins lesbar");
        KlimaGeiranger.ausDaten = List.copyOf(s);
        return s.size() + " Wetterbücher";
    }

    static String regiebuecher(List<Daten.Regiebuch> l) {
        List<Regie.Book> b = new ArrayList<>();
        int eigene = 0;
        for (Daten.Regiebuch r : l) {
            try {
                b.add(Regie.parse(r.text()));
                if (Daten.EIGEN.equals(r.herkunft())) eigene++;
            } catch (RuntimeException e) {
                Protokoll.warnung("Daten", "Regiebuch „" + r.name() + "“ nicht lesbar: " + e.getMessage());
            }
        }
        if (b.isEmpty()) throw new IllegalStateException("keins lesbar");
        Regie.ausDaten = List.copyOf(b);
        return b.size() + " Regiebücher" + (eigene > 0 ? " (" + eigene + " eigene)" : "");
    }
}
