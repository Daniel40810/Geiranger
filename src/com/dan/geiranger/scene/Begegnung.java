package com.dan.geiranger.scene;

import com.dan.road.Mover;
import com.dan.road.Traffic;
import com.dan.road.Vehicle;
import com.dan.road.Way;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 9: die Begegnungsregel für Busse in den Kehren des Ørnevegen. Ein Bus (oder Lastwagen, Wohnmobil über 7 m)
 * braucht in einer Kehre beide Streifen. Kommt er an eine Kehre, die frei ist, gehört sie ihm: der Gegenverkehr hält
 * vor ihrer Einfahrt, bis er durch ist. Ist die Kehre von der Gegenseite besetzt, wartet er selbst vor der Einfahrt.
 * <p>
 * {@code com.dan.road} kennt die Regel nicht (sie gehört nach F4 später ins Paket, Phase 18); sie läuft hier über
 * {@link Traffic#obstacles}: je wartende Richtung ein Halt (Radius 0,2 m) mitten auf ihrem Streifen vor der Einfahrt,
 * den nur dieser Streifen sieht. Je Schritt neu, vor {@code Roads.update}.
 */
public final class Begegnung {
    /** Rand der Kehre vor Ein- und Ausfahrt (m), Weg, auf dem ein Bus die Kehre schon beansprucht (m). */
    public static final double RAND = 12, ANFAHRT = 40;
    /** Ab dieser Länge gilt ein Fahrzeug als groß (m). */
    public static final double GROSS = 7;

    private final Way way;
    private final List<Ornevegen.Kehre> kehren;
    /** Je Kehre: wem sie gehört (+1, −1 Richtung, 0 frei) und wer davor wartet (Richtung, 0 keiner). */
    public final int[] besitzer, wartet;
    /** Wie oft schon jemand vor einer Kehre gewartet hat; wie viele große Fahrzeuge gerade unterwegs sind. */
    public int begegnungen, grosse;
    private final boolean[] warteteSchon;
    private final float[] stops = new float[3 * 64];

    public Begegnung(Ornevegen o) {
        way = o.way;
        kehren = o.kehren;
        besitzer = new int[kehren.size()];
        wartet = new int[kehren.size()];
        warteteSchon = new boolean[kehren.size() * 2];
    }

    static boolean gross(Vehicle v) { return v.length >= GROSS; }

    /** Ein Schritt: Besitz der Kehren, dann die Halte an traffic. */
    public void step(Traffic tr) {
        int n = kehren.size(), ns = 0;
        grosse = 0;
        for (Mover m : tr.movers) if (m.way() == way && gross(m.kind)) grosse++;
        for (int k = 0; k < n; k++) {
            Ornevegen.Kehre kh = kehren.get(k);
            double a = kh.s0() - RAND, b = kh.s1() + RAND;
            boolean[] drin = new boolean[2], grossNah = new boolean[2];
            for (Mover m : tr.movers) {
                if (m.way() != way || m.kind.walks() || m.kind.pedals()) continue;
                double s = m.s();
                int d = m.dir(), di = d > 0 ? 0 : 1;
                if (s >= a && s <= b) drin[di] = true;
                if (gross(m.kind)) {
                    // nähert sich der Kehre in Fahrtrichtung oder ist in ihr
                    double vor = d > 0 ? a - s : s - b;
                    if ((s >= a && s <= b) || (vor > 0 && vor < ANFAHRT)) grossNah[di] = true;
                }
            }
            int own = besitzer[k];
            if (own != 0 && !grossNah[own > 0 ? 0 : 1]) own = 0;           // durch: frei
            if (own == 0) {
                // ein großes Fahrzeug nimmt die Kehre, wenn die Gegenseite nicht schon drin ist
                if (grossNah[0] && !drin[1]) own = 1;
                else if (grossNah[1] && !drin[0]) own = -1;
            }
            besitzer[k] = own;
            int w = 0;
            if (own != 0) w = -own;                                          // der Gegenverkehr wartet
            else if (grossNah[0] && drin[1]) w = 1;                          // der Bus wartet, bis die Kehre frei ist
            else if (grossNah[1] && drin[0]) w = -1;
            // nur halten, wer noch nicht in der Kehre ist (wer drin ist, fährt hinaus)
            wartet[k] = w;
            if (w != 0 && ns < stops.length / 3) {
                double s = w > 0 ? a - 3 : b + 3;
                float[] p = new float[8];
                way.at(s, w * way.type.laneCenter(0), p);
                stops[3 * ns] = p[0];
                stops[3 * ns + 1] = p[2];
                stops[3 * ns + 2] = 0.2f;
                ns++;
                int key = 2 * k + (w > 0 ? 0 : 1);
                if (!warteteSchon[key] && jemandWartet(tr, w, s)) { begegnungen++; warteteSchon[key] = true; }
            } else {
                warteteSchon[2 * k] = warteteSchon[2 * k + 1] = false;
            }
        }
        tr.obstacles(stops, ns);
    }

    /** Steht in Richtung d jemand vor dem Halt bei s (langsamer als 1 m/s, höchstens 25 m davor)? */
    private boolean jemandWartet(Traffic tr, int d, double s) {
        for (Mover m : tr.movers) {
            if (m.way() != way || m.dir() != d || m.v > 1) continue;
            double vor = d > 0 ? s - m.s() : m.s() - s;
            if (vor > 0 && vor < 25) return true;
        }
        return false;
    }

    /**
     * Für Bilder (Tempo, Abnahme): eine Kehre, die gerade ein Bus besitzt, während vor ihr jemand wartet; {Nummer ab 0,
     * Bus x, Bus z} oder null.
     */
    public double[] busBegegnung(Traffic tr) {
        for (int k = 0; k < besitzer.length; k++) {
            if (besitzer[k] == 0) continue;
            Ornevegen.Kehre kh = kehren.get(k);
            double a = kh.s0() - RAND, b = kh.s1() + RAND;
            for (Mover m : tr.movers) {
                if (m.way() != way || m.kind != Vehicle.BUS || m.dir() != besitzer[k]) continue;
                double s = m.s();
                if (s < a || s > b) continue;
                double halt = -besitzer[k] > 0 ? a - 3 : b + 3;
                if (jemandWartet(tr, -besitzer[k], halt)) return new double[]{k, m.x, m.z};
            }
        }
        return null;
    }

    /** Die erste Kehre, vor der gerade jemand wartet (Nummer ab 1), oder 0. */
    public int wartendeKehre() {
        for (int k = 0; k < wartet.length; k++) if (wartet[k] != 0) return kehren.get(k).nr();
        return 0;
    }

    /** „Begegnungen 3 · Kehre 7: Bus bergauf, Gegenverkehr wartet“ */
    public String describe() {
        StringBuilder sb = new StringBuilder(String.format(Locale.GERMAN, "Begegnungsregel: %d große Fahrzeuge, %d Mal gewartet", grosse, begegnungen));
        for (int k = 0; k < besitzer.length; k++)
            if (besitzer[k] != 0) sb.append(String.format(Locale.GERMAN, " · Kehre %d: großes Fahrzeug %s, Gegenverkehr wartet", kehren.get(k).nr(), besitzer[k] > 0 ? "bergauf" : "bergab"));
        return sb.toString();
    }
}
