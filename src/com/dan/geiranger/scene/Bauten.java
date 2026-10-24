package com.dan.geiranger.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Phase 8: was am Ufer der Geirangelva gebaut ist oder liegt, als Netz ohne Grafikkarte (Ecken mit Lage, Normale,
 * Farbe; je drei Ecken ein Dreieck):
 * <ul>
 * <li><b>Treppe am Storfossen</b>: am Nordufer neben der Stufe, 5,5 m außerhalb des Betts, von ihrem Fuß bis zu ihrer
 * Kante; Stufen zu 18 cm, so viele, wie die Höhe braucht (in Geiranger sind es 327, die Zahl ist nicht nachgemessen),
 * jede auf einem Sockel aus Stein, der 2,5 m tief reicht (das 12-m-Raster kann eine Treppe nicht tragen). Die Treppe
 * folgt dem Gelände über 10 m geglättet, mindestens 1,5 m über dem Wasser.</li>
 * <li><b>Geländer</b> zum Fluss hin: Pfosten alle 2,4 m, 1 m hoch, Handlauf und Knieleiste.</li>
 * <li><b>Steine</b> in den Strecken der Elva, wo sie mehr als 2 % fällt (je 15 m einer mit halber Wahrscheinlichkeit,
 * 0,5 bis 1,4 m groß, bis 0,6 m über dem Wasser) [G]; dieselben Steine bekommt {@code com.dan.river} für die Strömung.</li>
 * </ul>
 */
public final class Bauten {
    /** Stufenhöhe, Breite der Treppe, Abstand vom Bett, Tiefe des Sockels (m). */
    public static final double STUFE = 0.18, BREITE = 1.6, ABSTAND = 5.5, SOCKEL = 2.5;
    public static final float[] STEIN = {0.36f, 0.35f, 0.33f}, GELAENDER = {0.30f, 0.31f, 0.32f}, HOLZ = {0.32f, 0.22f, 0.13f}, FELS = {0.24f, 0.24f, 0.25f};

    /** Ecken: je 9 Werte (x, y, z, nx, ny, nz, r, g, b). */
    public final float[] ecken;
    public final int stufen, pfosten;
    /** Steine im Fluss: je {x, z, Radius, Höhe der Kuppe über dem Spiegel}. */
    public final List<double[]> steine;
    /** Die Treppe als Linie (je Probe x, y, z der Mitte), Fuß zuerst. */
    public final List<double[]> treppe;

    /** Phase 8a: Standort der Ansicht „Am Storfossen“ auf der Treppe: x, Auge (m über NN), z, Richtung, Neigung; ohne Treppe null. */
    public final double[] blick;

    /** Phase 13: die Blöcke der Treppe (Anfang und Ende auf der Linie, Oberkante), für Menschen auf den Stufen. */
    public final List<double[]> bloecke;

    private Bauten(float[] e, int s, int p, List<double[]> st, List<double[]> tr, double[] b, List<double[]> bl) {
        ecken = e; stufen = s; pfosten = p; steine = st; treppe = tr; blick = b; bloecke = bl;
    }

    /** Phase 13: Oberkante der Stufe an der Stelle s der Linie (waagerecht von ihrem Fuß gemessen). */
    public double oberkante(double s) { return topAt(bloecke, s); }

    /** Alles an der Elva; ohne Elva leer. */
    public static Bauten build(Terrain t) {
        Elva e = t.elva;
        Netz n = new Netz();
        List<double[]> tr = new ArrayList<>(), st = new ArrayList<>(), blocks = new ArrayList<>();
        double[] blick = null, cumL = new double[0];
        int stufen = 0, pfosten = 0;
        if (e != null) {
            Elva.Stufe sf = e.stufen.isEmpty() ? null : e.stufen.get(0);
            if (sf != null) {
                // Linie der Treppe: Nordufer (die Seite mit kleinerem z)
                int a = sf.i0(), b = sf.i1();
                double sx = -e.dz[(a + b) / 2], sz = e.dx[(a + b) / 2];
                if (sz > 0) { sx = -sx; sz = -sz; }
                List<double[]> raw = new ArrayList<>();
                for (int i = a; i <= b; i++) {
                    double px = e.x[i] + sx * (e.half[i] + ABSTAND), pz = e.z[i] + sz * (e.half[i] + ABSTAND);
                    raw.add(new double[]{px, Math.max(t.height(px, pz), e.level[i] + 1.5), pz});
                }
                // über 10 m glätten, dann nie fallend
                int w = 2;
                for (int i = 0; i < raw.size(); i++) {
                    double s = 0;
                    int c = 0;
                    for (int k = Math.max(0, i - w); k <= Math.min(raw.size() - 1, i + w); k++) { s += raw.get(k)[1]; c++; }
                    double y = s / c;
                    if (i > 0) y = Math.max(y, tr.get(i - 1)[1]);
                    tr.add(new double[]{raw.get(i)[0], y, raw.get(i)[2]});
                }
                double len = 0;
                double[] cum = new double[tr.size()];
                for (int i = 1; i < tr.size(); i++) cum[i] = len += Math.hypot(tr.get(i)[0] - tr.get(i - 1)[0], tr.get(i)[2] - tr.get(i - 1)[2]);
                double y0 = tr.get(0)[1], y1 = tr.get(tr.size() - 1)[1];
                stufen = Math.max(1, (int) Math.round((y1 - y0) / STUFE));
                double rise = (y1 - y0) / stufen;
                // Phase 8a: die Stufen folgen dem Hang (die Linie tr, auf Stufenhöhe gerundet), statt mit gleichem
                // Steigungsmaß von unten nach oben durchzulaufen; sonst schwebte die Treppe unten bis 15 m über dem Grund
                // und steckte oben im Hang. Gleich hohe Stücke werden ein Block (ein Podest auf flachen Stücken).
                double ds = 0.1, a0 = 0;
                int lvPrev = -1;
                final int nSt = stufen;
                java.util.function.DoubleUnaryOperator lvl = sAt -> Math.min(nSt, Math.max(1, Math.ceil((along(tr, cum, sAt)[1] - y0) / rise - 1e-9)));
                List<double[]> stepTops = blocks;
                cumL = cum;
                for (double sa = 0; sa <= len + 1e-9; sa += ds) {
                    int lv = (int) lvl.applyAsDouble(Math.min(len, sa));
                    if (lvPrev < 0) lvPrev = lv;
                    boolean last = sa + ds > len + 1e-9;
                    if (lv != lvPrev || last) {
                        double bEnd = last ? len : sa;
                        block(n, tr, cum, t, a0, bEnd, y0 + lvPrev * rise, stepTops);
                        a0 = bEnd;
                        lvPrev = lv;
                    }
                }
                // Geländer zum Fluss hin (gegen sx, sz)
                double rx = -sx * (BREITE / 2 - 0.08), rz = -sz * (BREITE / 2 - 0.08);
                double[] prev = null;
                for (double s = 0; s <= len; s += 2.4) {
                    double[] p = along(tr, cum, s);
                    double top = topAt(stepTops, s);
                    double px = p[0] + rx, pz = p[2] + rz;
                    n.box(px, pz, 1, 0, 0.035, 0.035, top, top + 1.0, GELAENDER);
                    pfosten++;
                    if (prev != null) {
                        n.beam(prev[0], prev[1] + 1.0, prev[2], px, top + 1.0, pz, 0.03, HOLZ);
                        n.beam(prev[0], prev[1] + 0.5, prev[2], px, top + 0.5, pz, 0.02, GELAENDER);
                    }
                    prev = new double[]{px, top, pz};
                }
            }
            // Phase 8a: der Blick von der Treppe (Ansicht „Am Storfossen“): auf dem Block, der ein Drittel der Höhe
            // erreicht, 1,7 m über der Stufe, flussauf auf die Elva rund 60 m weiter oben
            if (sf != null && !blocks.isEmpty()) {
                double yA = blocks.get(0)[2], yB = blocks.get(blocks.size() - 1)[2], want = yA + (yB - yA) / 3;
                double[] k = blocks.get(blocks.size() - 1);
                for (double[] q : blocks) if (q[2] >= want) { k = q; break; }
                double[] p = along(tr, cumL, (k[0] + k[1]) / 2);
                double[] nb = e.nearest(p[0], p[2]);
                int kr = (int) nb[0], kt = kr;
                while (kt < sf.i1() && e.l[kt] < e.l[kr] + 60) kt++;
                double ey = k[2] + 1.7, ty = e.level[kt] + 2;
                double hd = Math.toDegrees(Math.atan2(e.x[kt] - p[0], -(e.z[kt] - p[2])));
                double pd = -Math.toDegrees(Math.atan2(ty - ey, Math.hypot(e.x[kt] - p[0], e.z[kt] - p[2])));   // Kamera: Neigung nach unten positiv
                blick = new double[]{p[0], ey, p[2], (hd + 360) % 360, pd};
            }
            // Steine in den Strecken
            SplittableRandom r = new SplittableRandom(808);
            for (Elva.Strecke s : e.strecken)
                for (int i = s.i0(); i <= s.i1(); i += 6) {
                    if (e.grade[i] < 0.02 || r.nextDouble() > 0.5) continue;
                    double u = (r.nextDouble() * 2 - 1) * 0.75 * e.half[i], rad = 0.5 + 0.9 * r.nextDouble(), top = 0.1 + 0.5 * r.nextDouble();
                    double x = e.x[i] - e.dz[i] * u, z = e.z[i] + e.dx[i] * u;
                    st.add(new double[]{x, z, rad, top});
                    n.rock(x, e.level[i] + top - rad, z, rad, r.nextLong(), FELS);
                }
        }
        return new Bauten(n.toArray(), stufen, pfosten, st, tr, blick, java.util.Collections.unmodifiableList(blocks));
    }

    /** Ein Block der Treppe von a bis b (m auf der Linie) mit der Oberkante top; unten bis in den Grund; merkt ihn in stepTops. */
    static void block(Netz n, List<double[]> tr, double[] cum, Terrain t, double a, double b, double top, List<double[]> stepTops) {
        if (b - a < 1e-6) return;
        double[] p0 = along(tr, cum, a), p1 = along(tr, cum, b);
        double dx = p1[0] - p0[0], dz = p1[2] - p0[2], dl = Math.max(1e-6, Math.hypot(dx, dz));
        double cx = (p0[0] + p1[0]) / 2, cz = (p0[2] + p1[2]) / 2;
        double g = Math.min(t.height(p0[0], p0[2]), Math.min(t.height(p1[0], p1[2]), t.height(cx, cz)));
        n.box(cx, cz, dx / dl, dz / dl, (b - a) / 2 + 0.02, BREITE / 2, Math.min(top - SOCKEL, g - 0.5), top, STEIN);
        stepTops.add(new double[]{a, b, top});
    }

    /** Oberkante der Treppe an der Stelle s der Linie (Blöcke: Anfang, Ende, Oberkante). */
    static double topAt(List<double[]> stepTops, double s) {
        for (double[] k : stepTops) if (s >= k[0] - 1e-9 && s <= k[1] + 1e-9) return k[2];
        return stepTops.isEmpty() ? 0 : stepTops.get(stepTops.size() - 1)[2];
    }

    /** Punkt auf der Linie nach der Länge s (waagerecht gemessen). */
    static double[] along(List<double[]> p, double[] cum, double s) {
        int i = 1;
        while (i < p.size() - 1 && cum[i] < s) i++;
        double f = Math.max(0, Math.min(1, (s - cum[i - 1]) / Math.max(1e-9, cum[i] - cum[i - 1])));
        double[] a = p.get(i - 1), b = p.get(i);
        return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
    }

    /** Ein einfaches Netz aus Quadern, Balken und Steinen. */
    static final class Netz {
        private float[] v = new float[9 * 4096];
        private int n;

        void put(double x, double y, double z, double nx, double ny, double nz, float[] c) {
            if (n + 9 > v.length) v = java.util.Arrays.copyOf(v, v.length * 2);
            v[n++] = (float) x; v[n++] = (float) y; v[n++] = (float) z;
            v[n++] = (float) nx; v[n++] = (float) ny; v[n++] = (float) nz;
            v[n++] = c[0]; v[n++] = c[1]; v[n++] = c[2];
        }

        void quad(double[] a, double[] b, double[] c, double[] d, double[] nn, float[] col) {
            for (double[] p : new double[][]{a, b, c, a, c, d}) put(p[0], p[1], p[2], nn[0], nn[1], nn[2], col);
        }

        /** Quader: Mitte (x, z), Richtung (dx, dz), halbe Länge und Breite, Unter- und Oberkante. */
        void box(double x, double z, double dx, double dz, double hl, double hw, double y0, double y1, float[] col) {
            double qx = -dz, qz = dx;
            double[][] c = new double[8][];
            for (int k = 0; k < 8; k++) {
                double sl = (k & 1) == 0 ? -hl : hl, sw = (k & 2) == 0 ? -hw : hw, y = (k & 4) == 0 ? y0 : y1;
                c[k] = new double[]{x + dx * sl + qx * sw, y, z + dz * sl + qz * sw};
            }
            quad(c[4], c[5], c[7], c[6], new double[]{0, 1, 0}, col);
            quad(c[0], c[2], c[3], c[1], new double[]{0, -1, 0}, col);
            quad(c[1], c[3], c[7], c[5], new double[]{dx, 0, dz}, col);
            quad(c[0], c[4], c[6], c[2], new double[]{-dx, 0, -dz}, col);
            quad(c[2], c[6], c[7], c[3], new double[]{qx, 0, qz}, col);
            quad(c[0], c[1], c[5], c[4], new double[]{-qx, 0, -qz}, col);
        }

        /**
         * Satteldach (Phase 10): First entlang (dx, dz) über der Mitte (x, z), halbe Länge hl, halbe Breite hw (mit
         * Überstand), Traufe auf y0, First auf y1; zwei Dachflächen und die Giebeldreiecke (Farbe gable).
         */
        void roof(double x, double z, double dx, double dz, double hl, double hw, double y0, double y1, float[] col, float[] gable) {
            double qx = -dz, qz = dx;
            double[] a = {x - dx * hl + qx * hw, y0, z - dz * hl + qz * hw}, b = {x + dx * hl + qx * hw, y0, z + dz * hl + qz * hw};
            double[] c = {x + dx * hl, y1, z + dz * hl}, d = {x - dx * hl, y1, z - dz * hl};
            double[] e = {x - dx * hl - qx * hw, y0, z - dz * hl - qz * hw}, f = {x + dx * hl - qx * hw, y0, z + dz * hl - qz * hw};
            double[] n1 = norm(cross(sub(b, a), sub(d, a))), n2 = norm(cross(sub(d, e), sub(f, e)));
            if (n1[1] < 0) n1 = new double[]{-n1[0], -n1[1], -n1[2]};
            if (n2[1] < 0) n2 = new double[]{-n2[0], -n2[1], -n2[2]};
            quad(a, b, c, d, n1, col);
            quad(e, d, c, f, n2, col);
            // Giebel unter dem Dach (etwas eingerückt), Dachstärke als Kante
            double in = 0.35;
            double[] ga = {x - dx * (hl - in) + qx * (hw - in), y0, z - dz * (hl - in) + qz * (hw - in)};
            double[] gb = {x - dx * (hl - in) - qx * (hw - in), y0, z - dz * (hl - in) - qz * (hw - in)};
            double[] gc = {x - dx * (hl - in), y1 - 0.2, z - dz * (hl - in)};
            double[] gn = {-dx, 0, -dz};
            for (double[] q : new double[][]{ga, gb, gc}) put(q[0], q[1], q[2], gn[0], gn[1], gn[2], gable);
            double[] ha = {x + dx * (hl - in) + qx * (hw - in), y0, z + dz * (hl - in) + qz * (hw - in)};
            double[] hb = {x + dx * (hl - in) - qx * (hw - in), y0, z + dz * (hl - in) - qz * (hw - in)};
            double[] hc = {x + dx * (hl - in), y1 - 0.2, z + dz * (hl - in)};
            for (double[] q : new double[][]{ha, hb, hc}) put(q[0], q[1], q[2], dx, 0, dz, gable);
        }

        /** Balken von a nach b, quadratisch mit halber Stärke r. */
        void beam(double ax, double ay, double az, double bx, double by, double bz, double r, float[] col) {
            double dx = bx - ax, dy = by - ay, dz = bz - az, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (l < 1e-6) return;
            dx /= l; dy /= l; dz /= l;
            double[] s = norm(new double[]{-dz, 0, dx}), u = norm(cross(s, new double[]{dx, dy, dz}));
            double[][] c = new double[8][];
            for (int k = 0; k < 8; k++) {
                double e = (k & 1) == 0 ? 0 : l, ss = (k & 2) == 0 ? -r : r, uu = (k & 4) == 0 ? -r : r;
                c[k] = new double[]{ax + dx * e + s[0] * ss + u[0] * uu, ay + dy * e + s[1] * ss + u[1] * uu, az + dz * e + s[2] * ss + u[2] * uu};
            }
            quad(c[4], c[5], c[7], c[6], u, col);
            quad(c[0], c[2], c[3], c[1], new double[]{-u[0], -u[1], -u[2]}, col);
            quad(c[2], c[6], c[7], c[3], s, col);
            quad(c[0], c[1], c[5], c[4], new double[]{-s[0], -s[1], -s[2]}, col);
        }

        /** Ein Stein: Ikosaeder, einmal unterteilt, mit Zufall verbeult. */
        void rock(double x, double y, double z, double r, long seed, float[] col) {
            double t = (1 + Math.sqrt(5)) / 2;
            double[][] p = {{-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0}, {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t}, {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}};
            int[][] f = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                    {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};
            SplittableRandom rr = new SplittableRandom(seed);
            double sy = 0.55 + 0.3 * rr.nextDouble(), bump = 0.18;
            for (int[] tri : f) {
                double[] a = norm(p[tri[0]]), b = norm(p[tri[1]]), c = norm(p[tri[2]]);
                double[] ab = norm(mid(a, b)), bc = norm(mid(b, c)), ca = norm(mid(c, a));
                for (double[][] s : new double[][][]{{a, ab, ca}, {ab, b, bc}, {ca, bc, c}, {ab, bc, ca}}) {
                    double[][] w = new double[3][];
                    for (int k = 0; k < 3; k++) {
                        double d = 1 + bump * (hash(s[k]) - 0.5);
                        w[k] = new double[]{x + s[k][0] * r * d, y + s[k][1] * r * d * sy, z + s[k][2] * r * d};
                    }
                    double[] nn = norm(cross(sub(w[1], w[0]), sub(w[2], w[0])));
                    for (double[] q : w) put(q[0], q[1], q[2], nn[0], nn[1], nn[2], col);
                }
            }
        }

        static double hash(double[] p) {
            double h = Math.sin(p[0] * 127.1 + p[1] * 311.7 + p[2] * 74.7) * 43758.5453;
            return h - Math.floor(h);
        }

        float[] toArray() { return java.util.Arrays.copyOf(v, n); }
    }

    static double[] mid(double[] a, double[] b) { return new double[]{(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2}; }
    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] norm(double[] a) {
        double l = Math.max(1e-12, Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]));
        return new double[]{a[0] / l, a[1] / l, a[2] / l};
    }

    /** Dreiecke im Netz. */
    public int dreiecke() { return ecken.length / 27; }

    /** „Treppe am Storfossen: 311 Stufen, 58 m hoch, 210 m lang · Geländer 88 Pfosten · 14 Steine“ */
    public String describe() {
        if (treppe.isEmpty()) return "Bauten: keine (ohne Elva)";
        double h = treppe.get(treppe.size() - 1)[1] - treppe.get(0)[1];
        return String.format(Locale.GERMAN, "Treppe am Storfossen: %d Stufen, %.0f m hoch · Geländer %d Pfosten · %d Steine im Fluss · %,d Dreiecke",
                stufen, h, pfosten, steine.size(), dreiecke());
    }
}
