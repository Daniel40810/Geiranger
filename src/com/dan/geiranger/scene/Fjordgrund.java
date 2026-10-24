package com.dan.geiranger.scene;

import java.util.Locale;
import java.util.Properties;

/**
 * Phase 3 (F7 A): der gemalte Grund des Fjords. Die HGT-Kacheln setzen das Wasser auf NN; was darunter liegt, malt
 * dieses Profil in die Fjordzellen des Hauptrasters ({@link Terrain#FJORD}) [G, nach Seekarte und Werkbuch]:
 * <ul>
 * <li><b>Ufer</b> {@link #UFER} m: direkt an der Wand, wo der Fels unter Wasser weiterläuft;</li>
 * <li><b>Mitte</b> {@link #MITTE} m: ab rund der halben Breite, in schmalen Seitenarmen flacher;</li>
 * <li><b>Rinne</b> {@link #RINNE} m: in der Mitte, wo der Fjord breiter als 2 · {@link #RINNE_AB} m ist (voll ab 2 · {@link #RINNE_VOLL} m);</li>
 * <li><b>Delta</b> vor dem Dorf: um die Mündung der Geirangelva ({@link Site#MUENDUNG}) {@link #DELTA_TIEFE} m flach,
 * dann mit {@link #DELTA_STEIG} (rund 17°) zur Tiefe.</li>
 * </ul>
 * Rechnung je Zelle: d = Abstand zum nächsten Land (euklidisch, Felzenszwalb), D = größtes d im Umkreis von
 * {@link #FENSTER}/2 m (die halbe Breite des Fjords an dieser Stelle), s = d / D (0 am Ufer, 1 in der Mitte). Danach
 * zweimal weich gezeichnet (nur über Fjordzellen), damit der Grund keine Grate aus dem Höchstwertfilter zeigt. Der
 * Fernring bekommt unter NN eine ebene Tiefe von {@link #FERN_TIEFE} m. Der Rand des Hauptrasters zählt nicht als
 * Ufer: der Fjord läuft dort im Westen und Norden hinaus.
 * <p>
 * Nur JDK; dieselbe Rechnung liest die Prüfung ({@link #tiefe}).
 */
public final class Fjordgrund {
    private Fjordgrund() { }

    /** Tiefen des Profils (m unter NN). */
    public static final double UFER = 10, MITTE = 150, RINNE = 250;
    /** Breite des Fensters für die halbe Fjordbreite D (m). */
    public static final double FENSTER = 1200;
    /** Halbe Breite (m), ab der die Mitte voll tief wird (darunter flacher, Seitenarme). */
    public static final double MITTE_AB = 60, MITTE_VOLL = 220;
    /** Halbe Breite (m), ab der die Rinne beginnt und voll ausgebildet ist. */
    public static final double RINNE_AB = 180, RINNE_VOLL = 320;
    /** Delta: flach im Umkreis {@link #DELTA_R0} m, dann Anstieg der Tiefe je m Abstand. */
    public static final double DELTA_TIEFE = 2, DELTA_R0 = 50, DELTA_STEIG = 0.3;
    /** Tiefe des Fernrings unter NN (m). */
    public static final double FERN_TIEFE = 150;

    /** Tiefe des Profils für s = d / D (0 am Ufer, 1 in der Mitte) und die halbe Breite D (m), ohne Delta. */
    public static double tiefe(double s, double halbeBreite) {
        double m = smooth(MITTE_AB, MITTE_VOLL, halbeBreite), r = smooth(RINNE_AB, RINNE_VOLL, halbeBreite);
        // zur Mitte: am Ufer gleich steil (die Wand läuft unter Wasser weiter), dann flacher auslaufend
        double u = Math.max(0, Math.min(1, s / 0.55));
        return UFER + (MITTE - UFER) * m * (1 - (1 - u) * (1 - u)) + (RINNE - MITTE) * r * smooth(0.6, 1, s);
    }

    /** Größte Tiefe am Abstand r (m) von der Mündung (das Delta). */
    public static double deltaGrenze(double r) { return DELTA_TIEFE + DELTA_STEIG * Math.max(0, r - DELTA_R0); }

    /**
     * Malt den Grund: setzt in {@code y} (Hauptraster, m über NN) jede Fjordzelle auf −Tiefe und schreibt die
     * Kennwerte nach {@code p} (grund.*). Gibt die Tiefe je Zelle zurück (0 auf dem Land).
     */
    public static double[] malen(double[] y, byte[] art, Properties p) {
        int n = Terrain.N;
        double cell = Terrain.CELL;
        // 1 Abstand zum Land in Zellen (quadriert), Rand des Rasters ist kein Land
        double[] d2 = new double[n * n];
        for (int k = 0; k < d2.length; k++) d2[k] = art[k] == Terrain.FJORD ? 1e20 : 0;
        edt(d2, n);
        double[] d = new double[n * n];
        for (int k = 0; k < d.length; k++) d[k] = Math.sqrt(d2[k]) * cell;
        // 2 halbe Breite D: größtes d im Fenster (getrennt nach Zeilen und Spalten)
        int rad = (int) Math.round(FENSTER / 2 / cell);
        double[] big = maxFilter(d, n, rad);
        // 3 Profil und Delta
        double[] t = new double[n * n];
        double mx = Site.MUENDUNG[0], mz = Site.MUENDUNG[1];
        int delta = 0;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int k = j * n + i;
                if (art[k] != Terrain.FJORD) continue;
                double dd = Math.max(d[k], cell * 0.5), D = Math.max(big[k], dd);
                double v = tiefe(dd / D, D);
                double r = Math.hypot(Terrain.cx(i) - mx, Terrain.cz(j) - mz);
                double g = deltaGrenze(r);
                if (g < v) { v = g; delta++; }
                t[k] = v;
            }
        // 4 weich: zweimal 5 × 5 über Fjordzellen; das Delta behält seine Grenze, das Ufer seine Tiefe
        for (int pass = 0; pass < 2; pass++) t = blur(t, art, n, 2);
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int k = j * n + i;
                if (art[k] != Terrain.FJORD) continue;
                double r = Math.hypot(Terrain.cx(i) - mx, Terrain.cz(j) - mz);
                t[k] = Math.max(Math.min(t[k], deltaGrenze(r)), Math.min(UFER, deltaGrenze(r)));
                y[k] = -t[k];
            }
        if (p != null) {
            double deepest = 0, sum = 0;
            long c = 0;
            for (int k = 0; k < t.length; k++) if (art[k] == Terrain.FJORD) { deepest = Math.max(deepest, t[k]); sum += t[k]; c++; }
            p.setProperty("grund", String.format(Locale.ROOT, "gemalt (F7): Ufer %.0f m, Mitte %.0f m, Rinne %.0f m, Delta %.0f m an der Mündung", UFER, MITTE, RINNE, DELTA_TIEFE));
            p.setProperty("grund.tiefste", String.format(Locale.ROOT, "%.1f", deepest));
            p.setProperty("grund.mittel", String.format(Locale.ROOT, "%.1f", c == 0 ? 0 : sum / c));
            p.setProperty("grund.deltaZellen", String.valueOf(delta));
            p.setProperty("grund.fern", String.format(Locale.ROOT, "%.0f", FERN_TIEFE));
        }
        return t;
    }

    /**
     * Glättet das Ufer: die Art je Zelle ist eine Treppe aus 12-m-Zellen, und zwischen Grund (−10 m) und Wandfuß (20
     * bis 50 m) zeichnet die Höhe diese Treppe als Zacken in die Wasserlinie (Befund der Vorschau von Phase 3). In einem
     * Band von {@code band} Zellen beiderseits des Ufers wird die Höhe darum gaußförmig gemittelt (über Land und
     * Fjord), danach bleibt Land über {@code landMin} und Fjord unter −{@code fjordMin}, damit Art und Wasser
     * zusammenpassen. Gibt die Zahl der geglätteten Zellen.
     */
    public static int ufer(double[] y, byte[] art, int band, double landMin, double fjordMin) {
        int n = Terrain.N;
        boolean[] near = new boolean[n * n];
        int count = 0;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                byte a = art[j * n + i];
                search:
                for (int b = Math.max(0, j - band); b <= Math.min(n - 1, j + band); b++)
                    for (int c = Math.max(0, i - band); c <= Math.min(n - 1, i + band); c++)
                        if (art[b * n + c] != a) { near[j * n + i] = true; count++; break search; }
            }
        double sigma = 1.2;
        int r = 3;
        double[] w = new double[r + 1];
        for (int k = 0; k <= r; k++) w[k] = Math.exp(-k * k / (2 * sigma * sigma));
        for (int pass = 0; pass < 2; pass++) {
            double[] src = y.clone();
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    int k = j * n + i;
                    if (!near[k]) continue;
                    double s = 0, ws = 0;
                    for (int b = -r; b <= r; b++) {
                        int jj = j + b;
                        if (jj < 0 || jj >= n) continue;
                        for (int c = -r; c <= r; c++) {
                            int ii = i + c;
                            if (ii < 0 || ii >= n) continue;
                            double q = w[Math.abs(b)] * w[Math.abs(c)];
                            s += q * src[jj * n + ii];
                            ws += q;
                        }
                    }
                    y[k] = s / ws;
                }
            for (int k = 0; k < y.length; k++) {
                if (!near[k]) continue;
                y[k] = art[k] == Terrain.FJORD ? Math.min(y[k], -fjordMin) : Math.max(y[k], landMin);
            }
        }
        return count;
    }

    /** Fernring: alles bis {@link Terrain#WASSER} wird ebener Grund in {@link #FERN_TIEFE} m. Gibt die Zahl der Zellen. */
    public static int fern(double[] f) {
        int c = 0;
        for (int k = 0; k < f.length; k++) if (f[k] <= Terrain.WASSER) { f[k] = -FERN_TIEFE; c++; }
        return c;
    }

    // ------------------------------------------------------------------ Hilfen

    /** Quadrierter euklidischer Abstand (in Zellen) zur nächsten Zelle mit Wert 0 (Felzenszwalb und Huttenlocher). */
    static void edt(double[] f, int n) {
        double[] col = new double[n], out = new double[n], z = new double[n + 1];
        int[] v = new int[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) col[j] = f[j * n + i];
            edt1(col, out, n, v, z);
            for (int j = 0; j < n; j++) f[j * n + i] = out[j];
        }
        for (int j = 0; j < n; j++) {
            System.arraycopy(f, j * n, col, 0, n);
            edt1(col, out, n, v, z);
            System.arraycopy(out, 0, f, j * n, n);
        }
    }

    private static void edt1(double[] f, double[] d, int n, int[] v, double[] z) {
        int k = 0;
        v[0] = 0;
        z[0] = Double.NEGATIVE_INFINITY;
        z[1] = Double.POSITIVE_INFINITY;
        for (int q = 1; q < n; q++) {
            double s = ((f[q] + (double) q * q) - (f[v[k]] + (double) v[k] * v[k])) / (2.0 * q - 2.0 * v[k]);
            while (s <= z[k]) {
                k--;
                s = ((f[q] + (double) q * q) - (f[v[k]] + (double) v[k] * v[k])) / (2.0 * q - 2.0 * v[k]);
            }
            k++;
            v[k] = q;
            z[k] = s;
            z[k + 1] = Double.POSITIVE_INFINITY;
        }
        k = 0;
        for (int q = 0; q < n; q++) {
            while (z[k + 1] < q) k++;
            double dq = q - v[k];
            d[q] = dq * dq + f[v[k]];
        }
    }

    /** Größter Wert im Quadrat (2·rad+1)², getrennt (Zeilen, dann Spalten). */
    static double[] maxFilter(double[] a, int n, int rad) {
        double[] tmp = new double[n * n], out = new double[n * n];
        double[] line = new double[n], res = new double[n];
        for (int j = 0; j < n; j++) {
            System.arraycopy(a, j * n, line, 0, n);
            slidingMax(line, res, n, rad);
            System.arraycopy(res, 0, tmp, j * n, n);
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) line[j] = tmp[j * n + i];
            slidingMax(line, res, n, rad);
            for (int j = 0; j < n; j++) out[j * n + i] = res[j];
        }
        return out;
    }

    /** Gleitendes Maximum mit einer Schlange (monoton fallend), O(n). */
    private static void slidingMax(double[] a, double[] out, int n, int rad) {
        int[] q = new int[n];
        int head = 0, tail = 0, next = 0;
        for (int i = 0; i < n; i++) {
            int hi = Math.min(n - 1, i + rad);
            while (next <= hi) {
                while (tail > head && a[q[tail - 1]] <= a[next]) tail--;
                q[tail++] = next++;
            }
            while (q[head] < i - rad) head++;
            out[i] = a[q[head]];
        }
    }

    /** Mittel im Quadrat (2·rad+1)² über die Fjordzellen allein. */
    static double[] blur(double[] t, byte[] art, int n, int rad) {
        double[] out = t.clone();
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int k = j * n + i;
                if (art[k] != Terrain.FJORD) continue;
                double s = 0;
                int c = 0;
                for (int b = Math.max(0, j - rad); b <= Math.min(n - 1, j + rad); b++)
                    for (int a = Math.max(0, i - rad); a <= Math.min(n - 1, i + rad); a++) {
                        int kk = b * n + a;
                        if (art[kk] != Terrain.FJORD) continue;
                        s += t[kk];
                        c++;
                    }
                out[k] = s / c;
            }
        return out;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Für Prüfung und Profilbild: die Tiefen eines Schnitts von (x0, z0) nach (x1, z1) in Schritten zu step m. */
    public static double[][] schnitt(Terrain t, double x0, double z0, double x1, double z1, double step) {
        int k = (int) Math.ceil(Math.hypot(x1 - x0, z1 - z0) / step) + 1;
        double[][] r = new double[k][2];
        for (int i = 0; i < k; i++) {
            double f = i / (double) (k - 1);
            double x = x0 + (x1 - x0) * f, z = z0 + (z1 - z0) * f;
            r[i][0] = f * Math.hypot(x1 - x0, z1 - z0);
            r[i][1] = t.mainHeight(x, z);
        }
        return r;
    }
}
