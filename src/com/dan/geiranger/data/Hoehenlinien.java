package com.dan.geiranger.data;

import com.dan.geiranger.scene.Terrain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Höhenlinien aus dem Hauptraster (F27 A, GR_HOEHENLINIE): das Raster auf {@link #FAKTOR} × 12 m gemittelt, je Höhe
 * „Marching Squares“, die Stücke zu Linien verbunden, nach Douglas-Peucker auf {@link #TOLERANZ} m vereinfacht, Stücke
 * unter {@link #MIN_LAENGE} m verworfen. Für QGIS über dem Satellitenbild genau genug; die Szene braucht sie nicht.
 */
public final class Hoehenlinien {
    private Hoehenlinien() { }

    public static final int ABSTAND = 50, FAKTOR = 4;
    public static final double TOLERANZ = 6, MIN_LAENGE = 150;

    /** Alle Höhen von {@link #ABSTAND} m bis zur höchsten Stelle. */
    public static List<Daten.Hoehenlinie> rechnen(Terrain t) {
        int m = Terrain.N / FAKTOR;
        double[] v = new double[m * m];
        double hi = -1e9;
        for (int j = 0; j < m; j++)
            for (int i = 0; i < m; i++) {
                double s = 0;
                for (int b = 0; b < FAKTOR; b++)
                    for (int a = 0; a < FAKTOR; a++) s += t.at(i * FAKTOR + a, j * FAKTOR + b);
                v[j * m + i] = s / (FAKTOR * FAKTOR);
                hi = Math.max(hi, v[j * m + i]);
            }
        List<Daten.Hoehenlinie> l = new ArrayList<>();
        for (int h = ABSTAND; h < hi; h += ABSTAND) {
            List<double[][]> teile = linien(v, m, h);
            if (!teile.isEmpty()) l.add(new Daten.Hoehenlinie(h, Daten.Geom.multilinie(teile)));
        }
        return l;
    }

    /** Lage einer Probe des gemittelten Rasters (Szene). */
    static double px(double i) { return Terrain.X0 + (i * FAKTOR + FAKTOR / 2.0) * Terrain.CELL; }

    static double pz(double j) { return Terrain.Z0 + (j * FAKTOR + FAKTOR / 2.0) * Terrain.CELL; }

    /** Die Linien einer Höhe. */
    static List<double[][]> linien(double[] v, int m, double h) {
        // Kanten: waagrecht (i,j)-(i+1,j) = 2·(j·m+i), senkrecht (i,j)-(i,j+1) = 2·(j·m+i)+1
        Map<Long, double[]> punkt = new HashMap<>();
        Map<Long, List<long[]>> an = new HashMap<>();
        List<long[]> segs = new ArrayList<>();
        for (int j = 0; j + 1 < m; j++)
            for (int i = 0; i + 1 < m; i++) {
                double a = v[j * m + i], b = v[j * m + i + 1], c = v[(j + 1) * m + i + 1], d = v[(j + 1) * m + i];
                int k = (a >= h ? 1 : 0) | (b >= h ? 2 : 0) | (c >= h ? 4 : 0) | (d >= h ? 8 : 0);
                if (k == 0 || k == 15) continue;
                long top = 2L * (j * (long) m + i), left = top + 1, bottom = 2L * ((j + 1) * (long) m + i), right = 2L * (j * (long) m + i + 1) + 1;
                if (!punkt.containsKey(top)) punkt.put(top, new double[]{px(i + f(a, b, h)), pz(j)});
                if (!punkt.containsKey(bottom)) punkt.put(bottom, new double[]{px(i + f(d, c, h)), pz(j + 1)});
                if (!punkt.containsKey(left)) punkt.put(left, new double[]{px(i), pz(j + f(a, d, h))});
                if (!punkt.containsKey(right)) punkt.put(right, new double[]{px(i + 1), pz(j + f(b, c, h))});
                double mitte = (a + b + c + d) / 4;
                switch (k) {
                    case 1, 14 -> segs.add(new long[]{left, top});
                    case 2, 13 -> segs.add(new long[]{top, right});
                    case 3, 12 -> segs.add(new long[]{left, right});
                    case 4, 11 -> segs.add(new long[]{right, bottom});
                    case 6, 9 -> segs.add(new long[]{top, bottom});
                    case 7, 8 -> segs.add(new long[]{left, bottom});
                    case 5 -> { if (mitte >= h) { segs.add(new long[]{left, bottom}); segs.add(new long[]{top, right}); } else { segs.add(new long[]{left, top}); segs.add(new long[]{right, bottom}); } }
                    case 10 -> { if (mitte >= h) { segs.add(new long[]{left, top}); segs.add(new long[]{right, bottom}); } else { segs.add(new long[]{left, bottom}); segs.add(new long[]{top, right}); } }
                    default -> { }
                }
            }
        for (long[] s : segs) {
            an.computeIfAbsent(s[0], x -> new ArrayList<>()).add(s);
            an.computeIfAbsent(s[1], x -> new ArrayList<>()).add(s);
        }
        java.util.Set<long[]> frei = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        frei.addAll(segs);
        List<double[][]> out = new ArrayList<>();
        // erst offene Linien (Enden mit nur einem Stück), dann Ringe
        List<long[]> starts = new ArrayList<>();
        for (Map.Entry<Long, List<long[]>> e : an.entrySet()) if (e.getValue().size() == 1) starts.add(new long[]{e.getKey()});
        for (long[] st : starts) if (!an.get(st[0]).isEmpty() && frei.contains(an.get(st[0]).get(0))) folge(st[0], an, frei, punkt, out);
        for (long[] s : segs) if (frei.contains(s)) folge(s[0], an, frei, punkt, out);
        return out;
    }

    private static void folge(long start, Map<Long, List<long[]>> an, java.util.Set<long[]> frei, Map<Long, double[]> punkt, List<double[][]> out) {
        List<double[]> p = new ArrayList<>();
        long cur = start;
        p.add(punkt.get(cur));
        while (true) {
            long[] next = null;
            for (long[] s : an.get(cur)) if (frei.contains(s)) { next = s; break; }
            if (next == null) break;
            frei.remove(next);
            cur = next[0] == cur ? next[1] : next[0];
            double[] q = punkt.get(cur), l = p.get(p.size() - 1);
            if (Math.hypot(q[0] - l[0], q[1] - l[1]) > 0.1) p.add(q);
        }
        if (p.size() < 2) return;
        double[][] q = vereinfachen(p.toArray(new double[0][]), TOLERANZ);
        double len = 0;
        for (int i = 1; i < q.length; i++) len += Math.hypot(q[i][0] - q[i - 1][0], q[i][1] - q[i - 1][1]);
        if (len >= MIN_LAENGE && q.length >= 2) out.add(q);
    }

    static double f(double a, double b, double h) { return Math.abs(b - a) < 1e-9 ? 0.5 : Math.max(0, Math.min(1, (h - a) / (b - a))); }

    /** Douglas-Peucker in der Ebene (x, z; weitere Werte je Punkt kommen mit). */
    public static double[][] vereinfachen(double[][] p, double tol) {
        if (p.length < 3) return p;
        boolean[] keep = new boolean[p.length];
        keep[0] = keep[p.length - 1] = true;
        java.util.ArrayDeque<int[]> st = new java.util.ArrayDeque<>();
        st.push(new int[]{0, p.length - 1});
        while (!st.isEmpty()) {
            int[] r = st.pop();
            double best = -1;
            int bi = -1;
            for (int i = r[0] + 1; i < r[1]; i++) {
                double d = abstand(p[i], p[r[0]], p[r[1]]);
                if (d > best) { best = d; bi = i; }
            }
            if (bi >= 0 && best > tol) {
                keep[bi] = true;
                st.push(new int[]{r[0], bi});
                st.push(new int[]{bi, r[1]});
            }
        }
        List<double[]> o = new ArrayList<>();
        for (int i = 0; i < p.length; i++) if (keep[i]) o.add(p[i]);
        return o.toArray(new double[0][]);
    }

    static double abstand(double[] q, double[] a, double[] b) {
        double dx = b[0] - a[0], dz = b[1] - a[1], l2 = dx * dx + dz * dz;
        double u = l2 < 1e-12 ? 0 : Math.max(0, Math.min(1, ((q[0] - a[0]) * dx + (q[1] - a[1]) * dz) / l2));
        return Math.hypot(q[0] - a[0] - u * dx, q[1] - a[1] - u * dz);
    }
}
