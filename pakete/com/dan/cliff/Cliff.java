package com.dan.cliff;

import java.util.Locale;

/**
 * Eine Steilwand als Material und Feinform für jede Gebirgsszene, Kern ohne Grafikkarte. Dieselben Formeln stehen in
 * {@code gl/cliff.glsl}; der Kern rechnet sie für Prüfungen, Abfragen und den Selbsttest der Grafikkarte nach.
 * <ul>
 * <li><b>Gneis</b> in Bändern entlang der Schieferung (eine schwach geneigte Fläche, {@link #foliationDipDeg},
 * {@link #foliationDipDirDeg}), hell und dunkel in drei Maßstäben (1,6 m, 7 m, 33 m), dazu rötliche Feldspatlagen;
 * die Bänder sind mit 3D-Rauschen verbogen, darum laufen sie als Linien quer über jede Wand.</li>
 * <li><b>Klüfte</b>: zwei senkrechte Scharen (Streichen {@link #jointADeg}, {@link #jointBDeg}, Abstand
 * {@link #jointSpacing}) als dunkle Fugen.</li>
 * <li><b>Feinform</b>: Relief entlang der Normale in bis zu vier Maßstäben ({@link #reliefScale}, Höhe
 * {@link #reliefAmp}); der Scheitelshader verschiebt die Wand damit, der Bildpunkt neigt die Normale aus demselben
 * Relief.</li>
 * <li><b>Bewuchs und Wetter</b>: Flechten auf trockenem, steilem Fels, Moos auf nassen Absätzen, nasse Streifen, wo
 * Wasser sickert (dunkel), Schnee auf Absätzen, Geröll am Fuß.</li>
 * </ul>
 * Alles ist 3D-Rauschen am Ort, keine Projektion auf Ebenen: senkrechte Stellen ziehen keine Streifen. Feine Anteile
 * blenden mit der Bildpunktgröße aus ({@code footprint}), damit nichts flimmert.
 * <p>
 * Eingänge: Lage und Normale, die Wandkarte ({@link CliffMap}: Wand, Geröll, Nässe, Absatz), Nässe aus dem Regen,
 * Schnee. Ausgänge: Farbe, Anteil Fels, geneigte Normale, Nässe ({@link Sample}); „Wand hier?“ ({@link #isWall},
 * {@link #wallAt}). Paketvertrag: nur Java 21 im Kern, JOGL in {@code gl/}, FStyle nur in {@code demo/}; alle Felder
 * öffentlich; gleicher {@link #seed}, gleiches Bild.
 */
public final class Cliff {
    /** Qualitätsstufen (eigene Kopie, Paketvertrag Punkt 7): Oktaven der Feinform, Zusatzgeometrie, Reichweite. */
    public enum Quality {
        LOW(2, false, 600), MEDIUM(3, true, 1500), HIGH(3, true, 2500), ULTRA(4, true, 4000);
        /** Oktaven des Reliefs, ob Überhänge gezeichnet werden, Entfernung bis zu der die Feinform die Wand verschiebt (m). */
        public final int octaves;
        public final boolean overhangs;
        public final double reliefRange;
        Quality(int o, boolean ov, double r) { octaves = o; overhangs = ov; reliefRange = r; }
    }

    /** Stand des Packages (für App und Prüfung). */
    public static final String STAND = "Engine (Phase 5)";

    public volatile Quality quality = Quality.MEDIUM;
    /** Zufall: gleicher Wert, gleiches Bild. */
    public volatile int seed = 1;
    /** Ab dieser Neigung (Grad) wird der Hang zu Fels, ab {@link #wallSlopeDeg} gilt er als Wand [G, nach den Fotos]. */
    public volatile double rockSlopeDeg = 38, wallSlopeDeg = 55;
    /** Schieferung: Fallen und Fallrichtung (Grad) [G: im Westnorwegischen Gneis flach, um 10° nach Nordwest]. */
    public volatile double foliationDipDeg = 10, foliationDipDirDeg = 315;
    /** Klüfte: Streichen der zwei Scharen (Grad von Nord), Abstand (m), Breite der Fuge (m). */
    public volatile double jointADeg = 30, jointBDeg = 115, jointSpacing = 9, jointWidth = 0.25;
    /** Feinform: Höhe (m) und größter Maßstab (m) des Reliefs; jede kleinere Oktave ein Drittel. */
    public volatile double reliefAmp = 4.0, reliefScale = 24;
    /** Farben (linear, Albedo): heller und dunkler Gneis, Feldspat, Flechten, Moos, Geröll, Schnee. */
    public volatile double[] gneissLight = {0.35, 0.34, 0.33}, gneissDark = {0.19, 0.19, 0.20}, feldspar = {0.44, 0.35, 0.31},
            lichen = {0.27, 0.29, 0.22}, moss = {0.10, 0.16, 0.05}, talus = {0.30, 0.30, 0.28}, snow = {0.82, 0.84, 0.88};
    /** Anteile 0..1: Flechten, Moos, Sickerstreifen, Feldspat. */
    public volatile double lichenAmount = 0.5, mossAmount = 0.5, seepAmount = 0.35, feldsparAmount = 0.3;
    /**
     * Wie nass die Streifen unter den Fällen gerade sind, 0..1 (seit Geiranger Phase 7): die Wandkarte hält die Lage der
     * Streifen ({@link CliffMap#addFall}, {@link CliffMap#paintWet}), dieser Wert ihre Stärke nach dem Abfluss. 1 = so nass,
     * wie die Karte sagt.
     */
    public volatile double fallWet = 1;

    /** Ob eine Neigung (Grad) als Wand zählt. */
    public boolean isWall(double slopeDeg) { return slopeDeg >= wallSlopeDeg; }

    /** Höhenfeld von außen (das Package kennt kein Gelände). */
    public interface Heights {
        double height(double x, double z);
    }

    /** Wand am Ort 0..1 aus der Neigung des Höhenfelds (Abstand der Proben e m), weich um {@link #wallSlopeDeg}. */
    public double wallAt(Heights h, double x, double z, double e) {
        double gx = (h.height(x + e, z) - h.height(x - e, z)) / (2 * e), gz = (h.height(x, z + e) - h.height(x, z - e)) / (2 * e);
        double slope = Math.toDegrees(Math.atan(Math.hypot(gx, gz)));
        return smooth(wallSlopeDeg - 5, wallSlopeDeg + 5, slope);
    }

    // ------------------------------------------------------------------ Feinform

    /** Eine feste Drehung (orthonormal, Zeilen zuerst) gegen die Kästchen des Gitters, wie {@code CLIFF_ROT} in GLSL. */
    static final double[] ROT = {0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64};

    static double[] rot(double x, double y, double z) {
        return new double[]{ROT[0] * x + ROT[1] * y + ROT[2] * z, ROT[3] * x + ROT[4] * y + ROT[5] * z, ROT[6] * x + ROT[7] * y + ROT[8] * z};
    }

    /** Relief (m) am Ort, um 0; footprint (m je Bildpunkt) blendet feine Oktaven aus. Wie {@code cliffRelief}. */
    public double relief(double x, double y, double z, double footprint) {
        double s = reliefScale, a = reliefAmp, r = 0, px = x, py = y, pz = z;
        for (int o = 0; o < quality.octaves; o++) {
            // je Oktave gedreht, damit das Gitter des Rauschens keine Kästchen zeichnet
            double qx = ROT[0] * px + ROT[1] * py + ROT[2] * pz, qy = ROT[3] * px + ROT[4] * py + ROT[5] * pz, qz = ROT[6] * px + ROT[7] * py + ROT[8] * pz;
            px = qx; py = qy; pz = qz;
            double w = CNoise.band(s, footprint);
            // gratig: 1 − |2n − 1| hebt Rippen heraus, wie verwitterte Bänke
            double n = CNoise.value(px / s, py / s, pz / s, seed + 11 + o);
            r += a * w * (0.5 - Math.abs(2 * n - 1));
            s /= 3;
            a /= 2.6;
        }
        return r;
    }

    // ------------------------------------------------------------------ Material

    /** Ergebnis je Punkt: Farbe (Albedo, linear), Anteil Fels, Normale, Nässe 0..1, Schnee 0..1. */
    public static final class Sample {
        public final double[] albedo = new double[3], normal = new double[3];
        public double rock, wet, snow;
    }

    /**
     * Material am Ort (x, y, z) mit der Normale des Geländes (Einheitsvektor), der Wandkarte {@code map} (Wand, Geröll,
     * Nässe, Absatz; null = aus der Neigung), Nässe aus dem Regen (0..1), Schnee (0..1) und der Bildpunktgröße
     * footprint (m). Wie {@code cliffMaterial} in {@code gl/cliff.glsl}.
     */
    public Sample material(double x, double y, double z, double nx, double ny, double nz, double[] map, double rainWet, double snowAmt, double footprint) {
        Sample s = new Sample();
        double slope = 1 - ny;
        double rockFrom = 1 - Math.cos(Math.toRadians(rockSlopeDeg));
        double w0 = 1 - Math.cos(Math.toRadians(wallSlopeDeg - 5)), w1 = 1 - Math.cos(Math.toRadians(wallSlopeDeg + 5));
        double wall = map != null ? map[0] : smooth(w0, w1, slope);
        double tal = map != null ? map[1] : 0, wetMap = map != null ? map[2] : 0, ledge = map != null ? map[3] : 0;
        s.rock = Math.max(Math.max(smooth(rockFrom, rockFrom + 0.12, slope), wall), tal * 0.45);

        if (s.rock <= 0.003 && snowAmt <= 0) {   // ohne Fels und Schnee nichts weiter (wie cliff.glsl)
            System.arraycopy(talus, 0, s.albedo, 0, 3);
            s.normal[0] = nx; s.normal[1] = ny; s.normal[2] = nz;
            return s;
        }
        // Bänder entlang der Schieferung, verbogen
        double dip = Math.toRadians(foliationDipDeg), dd = Math.toRadians(foliationDipDirDeg);
        double fx = Math.sin(dip) * Math.sin(dd), fy = Math.cos(dip), fz = -Math.sin(dip) * Math.cos(dd);
        double warp = (CNoise.value(x / 40, y / 40, z / 40, seed + 1) - 0.5) * 14 + (CNoise.value(x / 6, y / 6, z / 6, seed + 2) - 0.5) * 1.6 * CNoise.band(6, footprint);
        double f = x * fx + y * fy + z * fz + warp;
        double b = 0.45 * CNoise.value1(f / 33, seed + 3) + 0.35 * CNoise.value1(f / 7, seed + 4)
                + 0.20 * (CNoise.value1(f / 1.6, seed + 5) - 0.5) * CNoise.band(1.6, footprint) + 0.1;
        b = smooth(0.25, 0.75, b);
        double[] col = mix(gneissDark, gneissLight, b);
        double fel = smooth(0.78, 0.9, CNoise.value1(f / 4.5, seed + 6)) * feldsparAmount;
        col = mix(col, feldspar, fel);
        double[] g0 = rot(x, y, z);
        double grain = (CNoise.value(g0[0] / 0.7, g0[1] / 0.7, g0[2] / 0.7, seed + 7) - 0.5) * 0.18 * CNoise.band(0.7, footprint);
        col = scale(col, 1 + grain);

        // Klüfte: zwei senkrechte Scharen, Fugen dunkel
        double joint = 0;
        double[] jd = {jointADeg, jointBDeg};
        for (int k = 0; k < 2; k++) {
            double a = Math.toRadians(jd[k] + 90);
            double u = (x * Math.sin(a) - z * Math.cos(a)) / jointSpacing + (CNoise.value(x / 25, y / 25, z / 25, seed + 8 + k) - 0.5) * 0.8;
            double toJoint = (0.5 - Math.abs(u - Math.floor(u) - 0.5)) * jointSpacing;
            double open = smooth(0.35, 0.65, CNoise.value(x / 12, y / 30, z / 12, seed + 20 + k));
            joint = Math.max(joint, (1 - smooth(jointWidth * 0.5, jointWidth * 1.5, toJoint)) * open);
        }
        joint *= CNoise.band(jointWidth * 4, footprint) * wall;
        col = scale(col, 1 - 0.55 * joint);

        // Sickerstreifen: senkrecht gestrecktes Rauschen (gewollt), dazu die Nässe der Karte (unter Fällen) und der Regen
        double seep = smooth(0.66, 0.78, CNoise.value(x / 3.5, y / 70, z / 3.5, seed + 9)) * seepAmount * wall;
        s.wet = clamp(Math.max(Math.max(wetMap * fallWet, seep), rainWet * s.rock));
        // Flechten auf trockenem, steilem Fels; Moos auf nassen Absätzen
        double[] q = rot(x, y, z);
        double li = smooth(0.5, 0.72, CNoise.value(q[0] / 2.2, q[1] / 2.2, q[2] / 2.2, seed + 10)) * lichenAmount * (1 - s.wet) * s.rock;
        col = mix(col, lichen, li * 0.7);
        double mo = clamp((ledge * 0.9 + s.wet * 0.45) * mossAmount * 2) * smooth(0.4, 0.62, CNoise.value(q[0] / 3.5, q[1] / 3.5, q[2] / 3.5, seed + 12));
        col = mix(col, moss, mo);
        // Geröll am Fuß: Blöcke als steiles Rauschen im gedrehten Gitter (keine Würfel: die schnitten Dreiecke in den Hang)
        double blk = 0.5 + (smooth(0.3, 0.7, CNoise.value(q[0] / 1.3, q[1] / 1.3, q[2] / 1.3, seed + 13)) - 0.5) * CNoise.band(1.3, footprint);
        col = mix(col, scale(talus, 0.75 + 0.5 * blk), tal * (1 - wall));
        // nass: dunkler
        col = scale(col, 1 - 0.45 * s.wet);
        // Schnee auf Absätzen und flachen Stellen
        double flat = Math.max(ledge, 1 - smooth(0.05, 0.15, slope));
        s.snow = clamp(snowAmt * flat * smooth(0.3, 0.5, CNoise.value(x / 5, y / 5, z / 5, seed + 14) + snowAmt * 0.3));
        col = mix(col, snow, s.snow);
        System.arraycopy(col, 0, s.albedo, 0, 3);

        // Normale aus dem Relief (gleiche Feinform wie der Scheitelshader)
        double e = Math.max(0.35, footprint);
        double r0 = relief(x, y, z, footprint);
        double gx = (relief(x + e, y, z, footprint) - r0) / e * s.rock, gy = (relief(x, y + e, z, footprint) - r0) / e * s.rock,
                gz = (relief(x, y, z + e, footprint) - r0) / e * s.rock;
        double gn = gx * nx + gy * ny + gz * nz;
        double mx = nx - (gx - gn * nx), my = ny - (gy - gn * ny), mz = nz - (gz - gn * nz);
        double ml = Math.sqrt(mx * mx + my * my + mz * mz);
        s.normal[0] = mx / ml;
        s.normal[1] = my / ml;
        s.normal[2] = mz / ml;
        return s;
    }

    /** „Cliff · Engine (Phase 5) · MEDIUM · Fels ab 38°, Wand ab 55° · Relief 4 m bis 1500 m · Klüfte 9 m“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Cliff · %s · %s · Fels ab %.0f°, Wand ab %.0f° · Relief %.1f m bis %.0f m · Klüfte %.0f m", STAND, quality,
                rockSlopeDeg, wallSlopeDeg, reliefAmp, quality.reliefRange, jointSpacing);
    }

    // ------------------------------------------------------------------ Hilfen

    public static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    static double[] mix(double[] a, double[] b, double t) { return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t}; }

    static double[] scale(double[] a, double k) { return new double[]{a[0] * k, a[1] * k, a[2] * k}; }
}
