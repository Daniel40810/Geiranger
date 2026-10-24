package com.dan.geiranger.scene;

import com.dan.season.Climate;
import com.dan.season.Year;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Phase 14: das Jahr am Fjord. {@code com.dan.season} rechnet Schnee, Kronenschnee, Laub und Herbstfärbung für einen
 * Ort; am Geirangerfjord liegen zwischen dem Ufer und den Gipfeln 1 700 m. Darum rechnet diese Klasse das Jahr in
 * {@link #HOEHEN Höhenstufen} alle 200 m: dasselbe gewürfelte Wetter ({@link #SEED}), die Temperatur mit der Höhe
 * kälter ({@link #LAPSE}), der Niederschlag mit der Höhe mehr ({@link #MEHR_JE_M}) [G]. Daraus kommen die Schneehöhe je
 * Höhe (Gelände, Fels, Bodendecke, Straße), der Schnee in den Kronen, Laub und Herbst (Wald) und der Frost an den
 * Fällen ({@link #eis}).
 * <p>
 * Klima im Ort [G]: Temperatur aus {@code KlimaGeiranger} (Januar −1,5 °C, Juli 15 °C), Niederschlag rund 1 400 mm im
 * Jahr, am meisten von September bis Januar, an 15 bis 20 Tagen im Monat. Mit dem Startwert 2026 liegt am 15. Februar im
 * Ort Schnee, im Mai liegt er ab rund 800 m, im Juni ab rund 1 300 m, im Oktober ab rund 1 400 m.
 */
public final class Jahr {
    /** Höhenstufen (m über NN): alle 200 m vom Ufer bis über die Gipfel. */
    public static final double[] HOEHEN = {0, 200, 400, 600, 800, 1000, 1200, 1400, 1600, 1800};
    /** Abnahme der Temperatur mit der Höhe (K/m) und Zunahme des Niederschlags (Anteil je m) [G]. */
    public static final double LAPSE_CODE = 0.0065, MEHR_JE_M_CODE = 0.0005;
    /** Phase 17: was gilt ({@code data.Laden} setzt es aus GR_HOEHENSTUFE vor dem Bau des Jahrs). */
    public static volatile double LAPSE = LAPSE_CODE, MEHR_JE_M = MEHR_JE_M_CODE;
    /** Niederschlag im Ort (mm, Januar bis Dezember) und Regentage [G]. */
    public static final double[] NIEDERSCHLAG = {140, 110, 110, 75, 60, 70, 85, 110, 155, 165, 145, 155},
            REGENTAGE = {18, 15, 16, 13, 12, 13, 14, 16, 19, 20, 18, 19};
    /** Startwert des gewürfelten Wetters (2026: Schnee im Ort am 15. Februar). */
    public static final long SEED = 2026;
    /** Höhe, an der der Frost der Fälle gemessen wird (m): rund die Mitte der Wand unter den Schwestern. */
    public static final double FALL_HOEHE = 300;

    private final Year[] baender = new Year[HOEHEN.length];
    private final Year.State[] zustand = new Year.State[HOEHEN.length];
    private double letzte = Double.NaN;
    private int eisTag = -1;
    private double eis;
    /** Lufttemperatur im Ort zuletzt (°C). */
    public volatile double luftOrt = Double.NaN;

    public Jahr() {
        for (int i = 0; i < HOEHEN.length; i++) {
            double h = HOEHEN[i];
            double[] t = new double[12], p = new double[12];
            for (int m = 0; m < 12; m++) {
                t[m] = com.dan.geiranger.wiring.KlimaGeiranger.MONATSMITTEL[m] - LAPSE * h;
                p[m] = NIEDERSCHLAG[m] * (1 + MEHR_JE_M * h);
            }
            baender[i] = new Year(new Climate(t, p, REGENTAGE.clone()));
            baender[i].seed = SEED;
        }
    }

    static ZonedDateTime lokal(double epochSec) { return Instant.ofEpochMilli((long) (epochSec * 1000)).atZone(SceneClock.ZONE); }

    /** Das Jahr bis zur Uhr vorrechnen (ohne Wetter des Augenblicks; das Klima würfelt es). */
    public synchronized void settle(double epochSec) {
        ZonedDateTime z = lokal(epochSec);
        double hr = z.toLocalTime().toSecondOfDay() / 3600.0;
        for (int i = 0; i < baender.length; i++) zustand[i] = baender[i].settle(z.getYear(), z.getDayOfYear(), hr);
        letzte = epochSec;
    }

    /**
     * Je Bild: die Uhr und das Wetter im Ort (Luft °C, Niederschlag mm/h, Sonnenhöhe °, Bedeckung in Achteln, Wind m/s).
     * Springt die Uhr um mehr als drei Stunden, rechnet {@code season} selbst vor.
     */
    public synchronized void step(double epochSec, double luftC, double mmH, double sonneGrad, double achtel, double windMs) {
        if (Double.isNaN(letzte) || zustand[0] == null) settle(epochSec);
        double dt = epochSec - letzte;
        if (dt == 0) return;
        ZonedDateTime z = lokal(epochSec);
        double hr = z.toLocalTime().toSecondOfDay() / 3600.0;
        luftOrt = luftC;
        for (int i = 0; i < baender.length; i++) {
            double h = HOEHEN[i];
            zustand[i] = baender[i].step(dt, z.getYear(), z.getDayOfYear(), hr, luftC - LAPSE * h, mmH * (1 + MEHR_JE_M * h), sonneGrad, achtel, windMs);
        }
        letzte = epochSec;
    }

    /** Zustand der Stufe i (nach settle oder step). */
    public synchronized Year.State stufe(int i) { return zustand[i]; }

    private double lerp(double y, java.util.function.ToDoubleFunction<Year.State> f) {
        Year.State[] z = zustand;
        if (z[0] == null) return 0;
        double u = Math.max(0, Math.min(HOEHEN.length - 1, y / 200.0));
        int a = (int) Math.floor(u), b = Math.min(HOEHEN.length - 1, a + 1);
        double t = u - a;
        return f.applyAsDouble(z[a]) * (1 - t) + f.applyAsDouble(z[b]) * t;
    }

    /** Schnee auf ebenem Boden in der Höhe y (m; Höhe mal Bedeckung). */
    public synchronized double schnee(double y) { return lerp(y, s -> s.snowDepth() * s.snowCover()); }

    /** Schnee in den Kronen in der Höhe y (0..1). */
    public synchronized double kronen(double y) { return lerp(y, Year.State::treeSnow); }

    /** Laub (0 kahl … 1 voll) und Herbstfärbung (0 … 1) im Ort; Gras (0 matt … 1 saftig) in der Höhe y. */
    public synchronized double laub() { return zustand[0] == null ? 1 : zustand[0].foliage(); }
    public synchronized double herbst() { return zustand[0] == null ? 0 : zustand[0].autumn(); }
    public synchronized double gras(double y) { return lerp(y, Year.State::grassLush); }

    /** Schnee je Stufe für den Shader (m, Höhe mal Bedeckung). */
    public synchronized float[] baender() {
        float[] o = new float[HOEHEN.length];
        for (int i = 0; i < o.length; i++) o[i] = zustand[i] == null ? 0 : (float) (zustand[i].snowDepth() * zustand[i].snowCover());
        return o;
    }

    /** Tiefste Höhe mit mehr als 5 cm Schnee (m) oder NaN, wenn nirgends. */
    public synchronized double schneegrenze() {
        for (double y = 0; y <= HOEHEN[HOEHEN.length - 1]; y += 10) if (schnee(y) > 0.05) return y;
        return Double.NaN;
    }

    /**
     * Frost an den Fällen 0..1 [G]: Mittel der gewürfelten Tagestemperatur über die letzten 14 Tage in
     * {@link #FALL_HOEHE}: bei 0 °C beginnt das Eis am Rand, bei −6 °C im Mittel steht der Fall (im Winter führen die Fälle
     * nur wenige Prozent des Junis und frieren schnell).
     */
    public synchronized double eis(double epochSec) {
        ZonedDateTime z = lokal(epochSec);
        int tag = z.getYear() * 400 + z.getDayOfYear();
        if (tag != eisTag) {
            Year y = baender[(int) Math.round(FALL_HOEHE / 200)];
            double s = 0;
            for (int k = 0; k < 14; k++) {
                LocalDate d = z.toLocalDate().minusDays(k);
                s += y.dayMean(d.getYear(), d.getDayOfYear());
            }
            double m = s / 14 + LAPSE * (Math.round(FALL_HOEHE / 200) * 200 - FALL_HOEHE);
            double u = Math.max(0, Math.min(1, -m / 6));
            eis = u * u * (3 - 2 * u);
            eisTag = tag;
        }
        return eis;
    }

    /**
     * Die Temperatur, die {@code com.dan.fall} für einen Eisanteil e braucht ({@code Fall.ice} = smoothstep von 0 bis
     * −15 °C, hier umgekehrt).
     */
    public static double fallTemperatur(double e) {
        e = Math.max(0, Math.min(1, e));
        double u = 0.5 - Math.sin(Math.asin(1 - 2 * e) / 3);
        return -15 * u;
    }

    /** „Jahr: Schnee im Ort 27 cm, auf 600 m 2,3 m, Schneegrenze 0 m · Laub 0 %, Herbst 0 % · Fälle Eis 45 %“ */
    public synchronized String describe(double epochSec) {
        double g = schneegrenze();
        return String.format(Locale.GERMAN, "Jahr (season, %d Höhenstufen): Schnee im Ort %.0f cm, auf 600 m %.0f cm, auf 1 200 m %.0f cm · Schneegrenze %s · Laub %.0f %%, Herbst %.0f %% · Kronenschnee %.0f %% · Fälle Eis %.0f %%",
                HOEHEN.length, schnee(0) * 100, schnee(600) * 100, schnee(1200) * 100, Double.isNaN(g) ? "keine" : String.format(Locale.GERMAN, "%.0f m", g),
                laub() * 100, herbst() * 100, kronen(300) * 100, eis(epochSec) * 100);
    }
}
