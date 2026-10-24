package com.dan.geiranger.scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Phase 8: die Geirangelva vom Delta bis über den Storfossen, als Mittellinie mit Spiegel und Breite.
 * <p>
 * <b>Lauf</b>: aus dem Abflussnetz des Geländes (D8 auf den HGT-Kacheln nach Auffüllen der Senken, Phase 8): die
 * Zellen mit dem größten Einzugsgebiet von der Mündung bergauf, rund alle 36 m ein Stützpunkt ({@link #LAUF}, UTM 32 N).
 * Im Raster mündet die Elva rund 180 m südwestlich des Anlegers; ihr Einzugsgebiet im Hauptraster ist dort 34 km² groß.
 * Die Stützpunkte werden zweimal nach Chaikin geglättet und alle {@link #SCHRITT} m abgetastet.
 * <p>
 * <b>Spiegel</b>: die tiefste Höhe quer zum Lauf (± 12 m, das Bett im Raster), über 25 m gemittelt, vom Delta bergauf
 * nie fallend, darüber 0,3 m Wasser über dem Raster; an der Mündung NN + 0,3 m.
 * <p>
 * <b>Stufen</b>: wo das Gefälle über 30 m mehr als 12 % beträgt, auf mindestens 40 m, fließt das Wasser nicht mehr als
 * Fluss, sondern als Fall ({@code com.dan.fall} im Lauf über Fels): der Storfossen (von rund 11 auf 67 m) und weiter
 * oben zwei Kaskaden (88 bis 111 m und 115 bis 161 m; ihre Namen sind nicht geprüft, sie heißen hier „Kaskade 1“ und
 * „Kaskade 2“). Dazwischen und darunter rechnet {@code com.dan.river} die Strömung.
 * <p>
 * <b>Breite</b> [G, nach Fotos]: halbe Breite 9 m im Delta, 7 m durch den Ort, 6 m im Tal darüber.
 */
public final class Elva {
    /** Stützpunkte des Laufs (UTM 32 N, Ost, Nord) von der Mündung bergauf, aus dem Abflussnetz des Geländes. */
    public static final double[][] LAUF = {
            {406160, 6886690}, {406190, 6886650}, {406214, 6886614}, {406226, 6886578}, {406250, 6886554}, {406274, 6886518},
            {406286, 6886482}, {406298, 6886446}, {406298, 6886410}, {406322, 6886374}, {406358, 6886362}, {406394, 6886350},
            {406430, 6886338}, {406466, 6886326}, {406502, 6886290}, {406514, 6886254}, {406514, 6886218}, {406526, 6886182},
            {406562, 6886182}, {406598, 6886182}, {406622, 6886182}, {406646, 6886170}, {406670, 6886134}, {406682, 6886098},
            {406694, 6886062}, {406730, 6886038}, {406766, 6886026}, {406790, 6885990}, {406814, 6885966}, {406850, 6885954},
            {406874, 6885930}, {406898, 6885894}, {406922, 6885858}, {406922, 6885822}, {406946, 6885786}, {406970, 6885750},
            {407006, 6885714}, {407030, 6885690}, {407042, 6885654}};
    /** Abtastung längs des Laufs (m). */
    public static final double SCHRITT = 2.5;
    /** Ab diesem Gefälle über 30 m wird eine Stufe zum Fall; Mindestlänge einer Stufe (m). */
    public static final double STEIL = 0.12, STUFE_MIN = 40;
    /** Wassertiefe in der Mitte bei mittlerem Abfluss auf den Strecken und in den Stufen (m) [G]. */
    public static final double TIEFE = 1.5, TIEFE_STUFE = 0.8;
    /** Abfluss der Elva im Juni (m³/s) und im Jahresmittel (für {@code river}: Abfluss relativ zum Mittel) [G, NVE nicht geprüft]. */
    public static final double JUNI = 25, MITTEL = 10;

    public final int n;
    /** Je Probe: Lage (Szene), Länge ab der Mündung (m), Spiegel (m über NN), halbe Breite (m), Gefälle, Richtung flussab (x, z). */
    public final double[] x, z, l, level, half, grade, dx, dz;

    /** Eine Stufe (Fall) zwischen den Proben i0 (unten) und i1 (oben). */
    public record Stufe(String name, int i0, int i1) { }

    /** Eine Strecke, auf der die Elva als Fluss läuft, zwischen den Proben i0 (unten) und i1 (oben). */
    public record Strecke(int i0, int i1) { }

    public final List<Stufe> stufen;
    public final List<Strecke> strecken;

    private Elva(double[][] p, Terrain t) {
        n = p.length;
        x = new double[n]; z = new double[n]; l = new double[n]; level = new double[n]; half = new double[n];
        grade = new double[n]; dx = new double[n]; dz = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = p[i][0];
            z[i] = p[i][1];
            l[i] = i == 0 ? 0 : l[i - 1] + Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
        }
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            double ex = x[a] - x[b], ez = z[a] - z[b], el = Math.max(1e-9, Math.hypot(ex, ez));
            dx[i] = ex / el;   // flussab = zur Mündung (kleinere Länge)
            dz[i] = ez / el;
        }
        // Bett im Raster: die tiefste Höhe quer zum Lauf
        double[] bed = new double[n];
        for (int i = 0; i < n; i++) {
            double lo = Double.MAX_VALUE;
            for (double u = -12; u <= 12; u += 3) lo = Math.min(lo, t.height(x[i] - dz[i] * u, z[i] + dx[i] * u));
            bed[i] = lo;
        }
        // über 25 m gemittelt, dann vom Delta bergauf nie fallend
        int w = (int) Math.round(12.5 / SCHRITT);
        for (int i = 0; i < n; i++) {
            double s = 0;
            int c = 0;
            for (int k = Math.max(0, i - w); k <= Math.min(n - 1, i + w); k++) { s += bed[k]; c++; }
            level[i] = Math.max(Terrain.WASSER - 0.2, s / c) + 0.3;
            if (i > 0) level[i] = Math.max(level[i], level[i - 1]);
            half[i] = l[i] < 150 ? 9 : l[i] < 700 ? 7 : 6;
        }
        int g = (int) Math.round(15 / SCHRITT);
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - g), b = Math.min(n - 1, i + g);
            grade[i] = (level[b] - level[a]) / Math.max(1, l[b] - l[a]);
        }
        // Stufen: zusammenhängend steil, Lücken unter 20 m geschlossen, mindestens 40 m lang
        List<int[]> st = new ArrayList<>();
        int start = -1, gap = 0, gapMax = (int) Math.round(20 / SCHRITT);
        for (int i = 0; i < n; i++) {
            boolean steep = grade[i] > STEIL;
            if (steep) {
                if (start < 0) start = i;
                gap = 0;
            } else if (start >= 0 && ++gap > gapMax) {
                st.add(new int[]{start, i - gap});
                start = -1;
                gap = 0;
            }
        }
        if (start >= 0) st.add(new int[]{start, n - 1});
        List<Stufe> stufenL = new ArrayList<>();
        for (int[] s : st) {
            if (l[s[1]] - l[s[0]] < STUFE_MIN) continue;
            String name = stufenL.isEmpty() ? "Storfossen" : "Geirangelva, Kaskade " + stufenL.size();
            stufenL.add(new Stufe(name, Math.max(0, s[0] - 2), Math.min(n - 1, s[1] + 2)));
        }
        stufen = Collections.unmodifiableList(stufenL);
        List<Strecke> sr = new ArrayList<>();
        int from = 0;
        for (Stufe s : stufen) {
            if (l[s.i0()] - l[from] >= 30) sr.add(new Strecke(from, s.i0()));
            from = s.i1();
        }
        if (l[n - 1] - l[from] >= 30) sr.add(new Strecke(from, n - 1));
        strecken = Collections.unmodifiableList(sr);
    }

    /** Die Elva auf dem (noch ungeformten) Gelände; ohne Höhendaten null. */
    public static Elva build(Terrain t) {
        if (!t.measured()) return null;
        List<double[]> p = new ArrayList<>();
        for (double[] u : LAUF) p.add(Site.fromUtm(u[0], u[1]));
        for (int r = 0; r < 2; r++) p = chaikin(p);
        // gleichmäßig alle SCHRITT m
        List<double[]> q = new ArrayList<>();
        q.add(p.get(0));
        double carry = 0;
        for (int i = 1; i < p.size(); i++) {
            double[] a = p.get(i - 1), b = p.get(i);
            double seg = Math.hypot(b[0] - a[0], b[1] - a[1]), s = SCHRITT - carry;
            while (s <= seg) {
                double f = s / seg;
                q.add(new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f});
                s += SCHRITT;
            }
            carry = seg - (s - SCHRITT);
        }
        return new Elva(q.toArray(new double[0][]), t);
    }

    static List<double[]> chaikin(List<double[]> p) {
        List<double[]> r = new ArrayList<>();
        r.add(p.get(0));
        for (int i = 0; i < p.size() - 1; i++) {
            double[] a = p.get(i), b = p.get(i + 1);
            r.add(new double[]{0.75 * a[0] + 0.25 * b[0], 0.75 * a[1] + 0.25 * b[1]});
            r.add(new double[]{0.25 * a[0] + 0.75 * b[0], 0.25 * a[1] + 0.75 * b[1]});
        }
        r.add(p.get(p.size() - 1));
        return r;
    }

    /** Nächste Probe zum Ort (x, z) und der Abstand (m); {index, abstand} (Suche über alle Proben). */
    public double[] nearest(double px, double pz) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double d = (x[i] - px) * (x[i] - px) + (z[i] - pz) * (z[i] - pz);
            if (d < bd) { bd = d; best = i; }
        }
        return new double[]{best, Math.sqrt(bd)};
    }

    /** Ob die Probe i in einer Stufe liegt. */
    public Stufe stufe(int i) {
        for (Stufe s : stufen) if (i >= s.i0() && i <= s.i1()) return s;
        return null;
    }

    /** Stützpunkte einer Strecke für {@code com.dan.river.RiverPath} in Fließrichtung (von oben nach unten): x, z, Spiegel, halbe Breite. */
    public double[][] riverPoints(Strecke s) {
        double[][] r = new double[s.i1() - s.i0() + 1][];
        for (int i = s.i1(), k = 0; i >= s.i0(); i--, k++) r[k] = new double[]{x[i], z[i], level[i], half[i]};
        return r;
    }

    /** „Geirangelva: 1 540 m vom Delta bis 165 m, 2 Stufen (Storfossen 10–68 m, …), 2 Strecken“ */
    public String describe() {
        StringBuilder sb = new StringBuilder(String.format(Locale.GERMAN, "Geirangelva: %.0f m vom Delta bis %.0f m über NN", l[n - 1], level[n - 1]));
        for (Stufe s : stufen) sb.append(String.format(Locale.GERMAN, " · %s %.0f–%.0f m (%.0f m lang)", s.name(), level[s.i0()], level[s.i1()], l[s.i1()] - l[s.i0()]));
        sb.append(String.format(Locale.GERMAN, " · %d Strecken als Fluss", strecken.size()));
        return sb.toString();
    }
}
