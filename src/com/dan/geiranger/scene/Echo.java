package com.dan.geiranger.scene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Phase 15: Schall an den Wänden. Zwei Dinge rechnet diese Klasse aus dem Hauptraster des Geländes (12 m), ohne
 * Grafikkarte:
 * <ul>
 * <li><b>Echo</b> ({@link #finde}): die Spiegelquellen eines Knalls (Horn, Glocke). Ein Stück Wand wirft den Schall
 * zum Ohr zurück, wenn es Quelle und Ohr sieht; Fels streut rau (nach Lambert, siehe {@link #finde}). Das Echo kommt dann aus der Richtung der Wand, als käme es von einer Quelle in der Entfernung Quelle → Wand →
 * Ohr (Spiegelquelle); Laufzeit, Abnahme mit dem Abstand, Luftdämpfung und Richtung rechnet {@code soundscape} dann
 * selbst. Wie stark das Echo ist, folgt aus der Fläche, die zurückstreut, und dem Reflexionsgrad von Fels [G: 0,85
 * im Schalldruck].</li>
 * <li><b>Abschattung</b> ({@link #abschattung}): wie viel ein Grat zwischen Quelle und Ohr dämpft, nach Maekawa mit
 * der Fresnelzahl N = 2 δ / λ des Umwegs δ über die höchste Kante (10 lg(1 + 20 N) dB, höchstens 25 dB) [G].</li>
 * </ul>
 * Die Wandzellen (steiler als {@link #STEIL}°) sammelt der Konstruktor einmal (rund 0,1 s).
 */
public final class Echo {
    /** Ab dieser Neigung (Grad) zählt eine Zelle als Wand, die spiegelt. */
    public static final double STEIL = 38;
    /** Reflexionsgrad von Fels im Schalldruck [G]. */
    public static final double FELS = 0.85;
    /** Weiter als so weit (m) sucht das Echo nicht; schwächer als {@link #MIN_DB} zählt es nicht. */
    public static final double REICHWEITE = 4500, MIN_DB = -10;
    /** Schallgeschwindigkeit bei 10 °C (m/s), nur für die Laufzeit in der Beschreibung. */
    public static final double C = 337.5;

    /** Ein Echo: Ort der Spiegelquelle, der Wand, Laufzeit (s, über die Wand), Wege (m), Stärke (dB gegen die Spiegelquelle). */
    public record Treffer(double x, double y, double z, double wx, double wy, double wz, double d1, double d2, double gainDb, double flaeche) {
        public double laufzeit(double c) { return (d1 + d2) / c; }
        /** Richtung der Wand vom Ohr (Grad von Nord, im Uhrzeigersinn). */
        public double peilung(double ex, double ez) { return (Math.toDegrees(Math.atan2(wx - ex, -(wz - ez))) + 360) % 360; }
    }

    private final Terrain t;
    /** Wandzellen: Mitte (x, y, z) und Normale (nx, ny, nz). */
    private final float[] wx, wy, wz, nx, ny, nz;
    public final int zellen;
    public static boolean DEBUG;

    public Echo(Terrain t) {
        this.t = t;
        int n = Terrain.N, c = 0;
        double grenze = Math.tan(Math.toRadians(STEIL)) * 2 * Terrain.CELL;
        float[][] b = new float[6][1 << 16];
        for (int j = 1; j < n - 1; j++) {
            for (int i = 1; i < n - 1; i++) {
                double hx = t.at(i + 1, j) - t.at(i - 1, j), hz = t.at(i, j + 1) - t.at(i, j - 1);
                if (hx * hx + hz * hz < grenze * grenze) continue;
                double y = t.at(i, j);
                if (y < 2) continue;
                double ax = -hx / (2 * Terrain.CELL), az = -hz / (2 * Terrain.CELL), l = Math.sqrt(ax * ax + 1 + az * az);
                if (c == b[0].length) for (int k = 0; k < 6; k++) b[k] = java.util.Arrays.copyOf(b[k], c * 2);
                b[0][c] = (float) Terrain.cx(i); b[1][c] = (float) y; b[2][c] = (float) Terrain.cz(j);
                b[3][c] = (float) (ax / l); b[4][c] = (float) (1 / l); b[5][c] = (float) (az / l);
                c++;
            }
        }
        zellen = c;
        wx = java.util.Arrays.copyOf(b[0], c); wy = java.util.Arrays.copyOf(b[1], c); wz = java.util.Arrays.copyOf(b[2], c);
        nx = java.util.Arrays.copyOf(b[3], c); ny = java.util.Arrays.copyOf(b[4], c); nz = java.util.Arrays.copyOf(b[5], c);
    }

    /** Boden für den Schall: Hauptraster, über dem Fjord der Spiegel (NN). */
    double boden(double x, double z) { return Math.max(0, t.mainHeight(x, z)); }

    /** Ob die Linie von a nach b frei ist (am Ende {@code rand} m frei gelassen: dort steht die Wand selbst). */
    boolean frei(double ax, double ay, double az, double bx, double by, double bz, double randA, double randB) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int n = Math.max(2, (int) (d / 15));
        for (int k = 1; k < n; k++) {
            double s = d * k / n;
            if (s < randA || s > d - randB) continue;
            double u = s / d, x = ax + dx * u, y = ay + dy * u, z = az + dz * u;
            if (boden(x, z) > y + 0.5) return false;
        }
        return true;
    }

    /**
     * Abschattung (dB, ≥ 0) zwischen Quelle s und Ohr e für die Frequenz f (Hz) nach Maekawa über die Kante, die am
     * weitesten in die Linie ragt.
     */
    public double abschattung(double sx, double sy, double sz, double ex, double ey, double ez, double f) {
        double dx = ex - sx, dy = ey - sy, dz = ez - sz, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 30) return 0;
        int n = Math.max(4, Math.min(400, (int) (d / 20)));
        double best = 0;
        for (int k = 1; k < n; k++) {
            double u = (double) k / n, x = sx + dx * u, z = sz + dz * u, y = sy + dy * u;
            double h = boden(x, z) - y;
            if (h <= 0) continue;
            double a = d * u, b = d - a;
            double delta = Math.sqrt(a * a + h * h) + Math.sqrt(b * b + h * h) - d;
            best = Math.max(best, delta);
        }
        if (best <= 0) return 0;
        double lambda = 343 / f, nF = 2 * best / lambda;
        return Math.min(25, 10 * Math.log10(1 + 20 * nF));
    }

    /**
     * Die Echos eines Knalls an der Quelle s, gehört am Ohr e: höchstens {@code max} Treffer, nach der Laufzeit
     * geordnet. Der Ort jedes Treffers ist die Spiegelquelle.
     * <p>
     * Das 12-m-Raster rundet die Wände auf 50 bis 76° ab; ein glatter Spiegel würfe den Schall vom Wasser aus in den
     * Himmel. Fels ist aber auf jeder Größe rau, größer als die Wellenlänge des Horns (1,5 m). Darum streut jede
     * Wandzelle nach Lambert (Energie ∝ A cos θ<sub>ein</sub> cos θ<sub>aus</sub> / (π d₁² d₂²)); die Zellen einer
     * Klasse aus Laufzeit (0,1 s) und Peilung (15°) ergeben zusammen ein Echo, dessen Stärke gegen die Spiegelquelle
     * im Abstand d₁ + d₂ gilt: g² = R² Σ(…) (d₁ + d₂)² [G]. Von den drei stärksten Zellen jeder Klasse wird geprüft, ob
     * sie Quelle und Ohr sehen; der sichtbare Anteil zählt.
     */
    public List<Treffer> finde(double sx, double sy, double sz, double ex, double ey, double ez, double f, int max) {
        double cell2 = Terrain.CELL * Terrain.CELL, r2 = REICHWEITE * REICHWEITE;
        java.util.Map<Long, double[]> klassen = new java.util.HashMap<>();
        for (int k = 0; k < zellen; k++) {
            double px = wx[k], py = wy[k], pz = wz[k];
            double ax = px - sx, ay = py - sy, az = pz - sz;
            if (ax * ax + az * az > r2) continue;
            double d1 = Math.sqrt(ax * ax + ay * ay + az * az);
            if (d1 < 20) continue;
            double bx = ex - px, by = ey - py, bz = ez - pz, d2 = Math.sqrt(bx * bx + by * by + bz * bz);
            if (d2 < 20 || d2 > REICHWEITE) continue;
            double n0 = nx[k], n1 = ny[k], n2 = nz[k];
            double ein = -(ax * n0 + ay * n1 + az * n2) / d1, aus = (bx * n0 + by * n1 + bz * n2) / d2;
            if (ein < 0.05 || aus < 0.05) continue;
            double e = cell2 / Math.max(0.2, n1) * ein * aus / (Math.PI * d1 * d1 * d2 * d2);
            double delay = (d1 + d2) / C, peil = Math.toDegrees(Math.atan2(px - ex, -(pz - ez)));
            long key = ((long) Math.floor(delay / 0.15) << 20) ^ (long) Math.floor((peil + 720) / 40);
            // 0 Energie, 1..5 gewichtete Lage und Wege, 6..8 die drei stärksten Zellen, 9..11 ihre Energie
            double[] a = klassen.computeIfAbsent(key, q -> new double[]{0, 0, 0, 0, 0, 0, -1, -1, -1, 0, 0, 0});
            a[0] += e; a[1] += e * px; a[2] += e * py; a[3] += e * pz; a[4] += e * d1; a[5] += e * d2;
            for (int m = 0; m < 3; m++) {
                if (e > a[9 + m]) {
                    for (int q = 2; q > m; q--) { a[6 + q] = a[5 + q]; a[9 + q] = a[8 + q]; }
                    a[6 + m] = k; a[9 + m] = e;
                    break;
                }
            }
        }
        List<double[]> kand = new ArrayList<>(klassen.values());
        // Stärke gegen die Spiegelquelle: g² = R² E (d₁ + d₂)²
        kand.sort(Comparator.comparingDouble((double[] a) -> -a[0] * Math.pow((a[4] + a[5]) / a[0], 2)));
        // Klassen prüfen (Sicht), dann zu Echos zusammenfassen: eine Wand streut über 0,2 bis 0,5 s und über einige
        // zehn Grad zurück; was näher als 0,45 s und 60° beieinanderliegt, ist ein Echo (Energie addiert)
        List<double[]> gruppen = new ArrayList<>();   // Energie g², Σg²·x, ·y, ·z, ·d1, ·d2, Σg²·Laufzeit, Σg²·Peilung (als Einheitsvektor x, z)
        int geprueft = 0;
        for (double[] a : kand) {
            if (geprueft >= 60) break;
            double px = a[1] / a[0], py = a[2] / a[0], pz = a[3] / a[0], d1 = a[4] / a[0], d2 = a[5] / a[0], dd = d1 + d2;
            double g2 = FELS * FELS * a[0] * dd * dd;
            if (10 * Math.log10(Math.max(1e-12, g2)) < MIN_DB - 10) break;
            geprueft++;
            double sicht = 0, summe = 0;
            for (int m = 0; m < 3; m++) {
                int k = (int) a[6 + m];
                if (k < 0) continue;
                summe += a[9 + m];
                double qx = wx[k] + nx[k] * 6, qy = wy[k] + ny[k] * 6 + 1, qz = wz[k] + nz[k] * 6;
                if (frei(sx, sy, sz, qx, qy, qz, 5, 30) && frei(ex, ey, ez, qx, qy, qz, 5, 30)) sicht += a[9 + m];
            }
            if (summe <= 0 || sicht <= 0) continue;
            g2 *= sicht / summe;
            double delay = dd / C, peil = Math.atan2(px - ex, -(pz - ez));
            double[] ziel = null;
            for (double[] g : gruppen) {
                double gd = g[7] / g[0], gp = Math.atan2(g[8], g[9]);
                double dp = Math.abs(Math.toDegrees(((peil - gp) % (2 * Math.PI) + 3 * Math.PI) % (2 * Math.PI) - Math.PI));
                if (Math.abs(gd - delay) < 0.45 && dp < 60) { ziel = g; break; }
            }
            if (ziel == null) { ziel = new double[10]; gruppen.add(ziel); }
            ziel[0] += g2; ziel[1] += g2 * px; ziel[2] += g2 * py; ziel[3] += g2 * pz; ziel[4] += g2 * d1; ziel[5] += g2 * d2;
            ziel[6] += a[0]; ziel[7] += g2 * delay; ziel[8] += g2 * Math.sin(peil); ziel[9] += g2 * Math.cos(peil);
        }
        gruppen.sort(Comparator.comparingDouble((double[] g) -> -g[0]));
        List<Treffer> out = new ArrayList<>();
        for (double[] g : gruppen) {
            if (out.size() >= max) break;
            double db = 10 * Math.log10(g[0]);
            if (DEBUG) System.out.printf(Locale.ROOT, " Echo %.2f s %.1f dB%n", g[7] / g[0], db);
            if (db < MIN_DB) continue;
            double px = g[1] / g[0], py = g[2] / g[0], pz = g[3] / g[0], d1 = g[4] / g[0], d2 = g[5] / g[0], dd = d1 + d2;
            double ux = px - ex, uy = py - ey, uz = pz - ez, ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
            out.add(new Treffer(ex + ux / ul * dd, ey + uy / ul * dd, ez + uz / ul * dd, px, py, pz, d1, d2, db, g[6]));
        }
        out.sort(Comparator.comparingDouble(e -> e.d1() + e.d2()));
        return out;
    }

    /** „2 Echos: 1,6 s aus 352° (−1 dB), 3,9 s aus 170° (−4 dB)“ */
    public static String describe(List<Treffer> l, double ex, double ez) {
        if (l == null || l.isEmpty()) return "kein Echo";
        StringBuilder sb = new StringBuilder(l.size() + (l.size() == 1 ? " Echo: " : " Echos: "));
        for (int i = 0; i < l.size(); i++) {
            Treffer e = l.get(i);
            if (i > 0) sb.append(", ");
            sb.append(String.format(Locale.GERMAN, "%.1f s aus %03.0f° (%+.0f dB)", e.laufzeit(C), e.peilung(ex, ez), e.gainDb()));
        }
        return sb.toString();
    }
}
