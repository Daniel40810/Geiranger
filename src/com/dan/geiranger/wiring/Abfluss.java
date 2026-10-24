package com.dan.geiranger.wiring;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 7, F13 A: der Abfluss der Fälle aus Jahreszeit, Schmelze und Regen, mit einem Regler von Hand (Block WASSER).
 * Gibt: {@link Discharge} an fall (Strahl, Schleier, Ringwellen, Nässe der Wand) und später an soundscape (Phase 15)
 * und river (Phase 8).
 * <ul>
 * <li><b>Jahreszeit und Schmelze</b> [G, zu prüfen an Pegeln der NVE]: Anteil am Abfluss im Juni je Monatsmitte, dazwischen
 * linear. Die Schmelze füllt die Fälle von Ende April bis in den Juli, danach werden sie dünn; im Winter bleibt ein
 * Rest unter dem Eis.</li>
 * <li><b>Regen</b>: ein Speicher in mm, der mit dem Regen füllt und mit 6 h Halbwertszeit leer läuft (kleine, steile
 * Einzugsgebiete auf Fels [G]); 10 mm im Speicher verdoppeln den Abfluss. Die Regenfälle laufen nur aus ihm: ab rund
 * 2 mm im Speicher, voll ab 12 mm.</li>
 * <li><b>Frost</b>: die Temperatur am Fall folgt der Luft mit 24 h Verzögerung (Fels und Wasser kühlen langsam); unter 0 °C
 * friert der Rand, bei −15 °C steht der Fall ({@code Fall.ice}).</li>
 * </ul>
 */
public final class Abfluss implements Discharge {
    /** Anteil am Abfluss im Juni zur Mitte jedes Monats, Januar bis Dezember [G]. */
    public static final double[] MONAT = {0.08, 0.06, 0.08, 0.25, 0.80, 1.00, 0.70, 0.40, 0.35, 0.30, 0.20, 0.10};
    /** Halbwertszeit des Regenspeichers (s) und Verzögerung der Temperatur am Fall (s). */
    public static final double REGEN_HALB = 6 * 3600, FROST_TAU = 24 * 3600;

    /** Von Hand: Anteil am Abfluss im Juni (0 bis 3), oder −1 für die Regie. */
    public volatile double hand = -1;
    /** Regenspeicher (mm), Temperatur am Fall (°C), Jahreszeit (Anteil am Juni), zuletzt gerechnet. */
    public volatile double regen, temp = 12, jahreszeit = 1;
    private boolean started;
    /**
     * Phase 14: Temperatur, die der Frost des Jahres an den Fällen verlangt ({@code scene.Jahr.eis}, über 14 Tage gemittelt);
     * unendlich, wenn kein Frost. Die Fälle bekommen das Kältere von beiden ({@link #amFall}).
     */
    public volatile double frostTemp = Double.POSITIVE_INFINITY;

    /** Temperatur am Fall für {@code com.dan.fall}: die verzögerte Luft oder der Frost des Jahres, das Kältere. */
    public double amFall() { return Math.min(temp, frostTemp); }

    private record Fall(double juni, boolean nurRegen) { }
    private final Map<String, Fall> faelle = new LinkedHashMap<>();

    /** Einen Fall anmelden: Abfluss im Juni (m³/s) und ob er nur nach Regen läuft. */
    public synchronized void register(String name, double juni, boolean nurRegen) { faelle.put(name, new Fall(juni, nurRegen)); }

    /** Anteil am Juni für einen Tag des Jahres (1 bis 366), zwischen den Monatsmitten linear. */
    public static double season(LocalDate d) {
        double m = d.getMonthValue() - 1 + (d.getDayOfMonth() - 0.5) / d.lengthOfMonth() - 0.5;   // 0 = Mitte Januar
        int a = (int) Math.floor(m);
        double f = m - a;
        return MONAT[Math.floorMod(a, 12)] * (1 - f) + MONAT[Math.floorMod(a + 1, 12)] * f;
    }

    /**
     * Einen Schritt weiter: Szenenzeit dt (s), Regen (mm/h), Lufttemperatur (°C), Datum. Beim ersten Schritt steht die
     * Temperatur am Fall gleich auf der Luft.
     */
    public void step(double sceneDt, double rainMmH, double airC, LocalDate date) {
        jahreszeit = season(date);
        double dt = Math.max(0, sceneDt);
        regen = regen * Math.pow(0.5, dt / REGEN_HALB) + Math.max(0, rainMmH) * dt / 3600;
        if (!started) { temp = airC; started = true; }
        else temp += (airC - temp) * (1 - Math.exp(-dt / FROST_TAU));
    }

    /** Faktor auf den Juni für die Fälle mit eigenem Einzugsgebiet: von Hand oder Jahreszeit mal Regen. */
    public double factor() {
        double h = hand;
        return h >= 0 ? h : jahreszeit * (1 + regen / 10);
    }

    /** Wie stark die Regenfälle laufen, 0..1 (von Hand: wie die anderen, auf 1 begrenzt). */
    public double rainFalls() {
        double h = hand;
        if (h >= 0) return Math.min(1, h);
        double t = Math.max(0, Math.min(1, (regen - 2) / 10));
        return t * t * (3 - 2 * t);
    }

    @Override
    public synchronized double cubicMetres(String name) {
        Fall f = faelle.get(name);
        if (f == null) return 0;
        return f.juni * (f.nurRegen ? rainFalls() : factor());
    }

    /** Gefroren heißt hier: der Fall steht (Temperatur am Fall unter −15 °C, wie {@code Fall.ice} = 1). */
    @Override public boolean frozen(String name) { return amFall() <= -15; }

    /** „Abfluss: Regie, Juni 100 %, Regen 0 mm im Speicher, am Fall 15 °C“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Abfluss %s · %.0f %% vom Juni · Regen %.1f mm im Speicher · am Fall %.0f °C",
                hand >= 0 ? "von Hand" : "Regie (Jahreszeit " + Math.round(jahreszeit * 100) + " %)", factor() * 100, regen, amFall());
    }
}
