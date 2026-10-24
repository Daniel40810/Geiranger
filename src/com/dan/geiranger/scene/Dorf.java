package com.dan.geiranger.scene;

import com.dan.timber.Material;
import com.dan.timber.Mesh;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Phase 11: der Ort Geiranger am Kopf des Fjords (F16 A) [G, nach der Karte, dem Gelände, Fotos und Beschreibungen].
 * <p>
 * <b>Lage</b>: der Talboden der Geirangelva (Delta auf 1 bis 15 m) und die Hänge östlich darüber. Fest gesetzt sind
 * der Anleger im Hafenbecken (ein Bohlendeck auf Pfählen, 62 × 10 m), das Terminal am Ufer, das Hotel Geiranger am
 * Ufer, Hotel Union am Hang, die achteckige weiße Holzkirche von 1842 über dem Storfossen (62° 05′ 56″ N, 7° 12′ 26″ O,
 * Wikipedia), vier Bootshäuser (Naust) am Ufer nördlich des Hafens und der Campingplatz am Delta (Rezeption, Hütten,
 * Wohnwagen). Die übrigen Häuser setzt {@link #build} aus einem Raster: auf Land über dem Ufer, nicht steiler als 29°,
 * nicht in der Elva, nicht auf der Straße, mit Abstand zueinander, bevorzugt 10 bis 40 m an der Fv63 oder am
 * Strandvegen ({@link Ornevegen#DORF}, {@link Ornevegen#STRANDVEGEN}); an der Straße parallel zu ihr, sonst entlang der
 * Höhenlinie.
 * <p>
 * <b>Bauweise</b>: gestrichene Holzhäuser mit senkrechter Verschalung (weiß, rot, ocker, grau, blaugrau), Sockel aus
 * Beton, Satteldach aus Schiefer, Ziegel oder Gras, weiße Fenster und Eckbretter; Läden an der Straße, ein- bis
 * zweigeschossig. Gezeichnet mit dem Programm der Bauten ({@code bauten.frag}: Verschalung, Fenster mit Rahmen und
 * Sprossen, nachts zu gut der Hälfte erleuchtet, Laternen). Die alten Bauten (Naust, Anleger, die Höfe) aus
 * {@code com.dan.timber} ({@link Holzbau}).
 * <p>
 * <b>Kodierung der Farben</b> (Format von {@link Bauten#ecken}): Rot über 1,5 = Verschalung (Farbe = Wert − 2); Rot
 * unter −0,5 = Fenster (Grün = Nummer + u, Blau = v im Fenster); Rot unter −1,5 = Laterne (leuchtet nachts).
 */
public final class Dorf {
    public enum Art { WOHNHAUS, LADEN, UTHUS, HOTEL, KIRCHE, TERMINAL, REZEPTION, HYTTE, WOHNWAGEN, NAUST }

    /** Ein Bau: Art, Name, Mitte (x, Boden, z), Richtung des Firsts (Einheit), halbe Länge und Breite, Wandhöhe. */
    public record Bau(Art art, String name, double x, double y, double z, double ax, double az, double hl, double hw, double wand, String farbe, String dach) {
        public Bau(Art art, String name, double x, double y, double z, double ax, double az, double hl, double hw, double wand) {
            this(art, name, x, y, z, ax, az, hl, hw, wand, null, null);
        }
        public double radius() { return Math.hypot(hl, hw); }
    }

    /** Phase 17: der Name einer Farbe dieser Klasse (für GR_GEBAEUDE), oder null. */
    public static String farbname(float[] c) {
        if (c == null) return null;
        String[] n = {"weiß", "rot", "ocker", "grau", "blaugrau", "grün", "geteert", "Schiefer", "Ziegel", "Gras", "Beton"};
        float[][] f = {WEISS, ROT, OCKER, GRAU, BLAUGRAU, GRUEN, TEER, SCHIEFER, ZIEGEL, GRAS, BETON};
        for (int i = 0; i < f.length; i++) if (java.util.Arrays.equals(f[i], c)) return n[i];
        return null;
    }

    public static final float[] WEISS = {0.62f, 0.61f, 0.57f}, ROT = {0.30f, 0.07f, 0.045f}, OCKER = {0.50f, 0.36f, 0.13f},
            GRAU = {0.40f, 0.41f, 0.40f}, BLAUGRAU = {0.27f, 0.33f, 0.37f}, GRUEN = {0.19f, 0.26f, 0.19f}, TEER = {0.11f, 0.075f, 0.05f},
            SCHIEFER = {0.055f, 0.06f, 0.065f}, ZIEGEL = {0.30f, 0.10f, 0.06f}, GRAS = {0.12f, 0.16f, 0.055f},
            BETON = {0.36f, 0.355f, 0.34f}, TUER = {0.16f, 0.12f, 0.09f}, MAST = {0.24f, 0.25f, 0.26f}, LAMPE = {-2f, 0f, 0f};

    /** Fest gesetzte Orte (Szene x, z) [G, aus Karte, Koordinaten und Gelände]. */
    public static final double[] KIRCHE = {76, 279}, UNION = {120, 185}, HOTEL = {-15, 40}, TERMINAL = {10, -10}, REZEPTION = {-24, 165};
    /** Der Anleger: Wurzel am Ufer (x, z), Länge nach Westen, halbe Breite, Deck über NN. */
    public static final double ANLEGER_X0 = 24, ANLEGER_X1 = -38, ANLEGER_Z = -58, ANLEGER_HW = 5, ANLEGER_DECK = 2.3;
    /** Zahl der Häuser aus dem Raster. */
    public static final int RASTERHAEUSER = 38;
    /** Höfe auf der Terrasse von Vinje über dem Ort (rund 85 m, ohne Straße im Modell). */
    public static final int VINJE = 6;

    public final List<Bau> bauten;
    /** Ecken für das Programm der Bauten (9 Werte je Ecke). */
    public final float[] ecken;
    /** Naust und Anleger aus timber (Weltkoordinaten). */
    public final Mesh holz;
    /** Laternen: Lage des Lichts (x, y, z). */
    public final List<double[]> lampen;
    public final int fenster;

    private Dorf(List<Bau> b, float[] e, Mesh h, List<double[]> l, int f) {
        bauten = b; ecken = e; holz = h; lampen = l; fenster = f;
    }

    /** Zahl der Gebäude (ohne Wohnwagen). */
    public int gebaeude() {
        int n = 0;
        for (Bau b : bauten) if (b.art() != Art.WOHNWAGEN) n++;
        return n;
    }

    /** Steht an (x, z) ein Bau (mit Rand)? Für Wald und Bodendecke. */
    public boolean bebaut(double x, double z, double rand) {
        if (x < -320 || x > 450 || z < -330 || z > 480) return false;
        for (Bau b : bauten) {
            double dx = x - b.x(), dz = z - b.z();
            double r = b.radius() + rand;
            if (dx * dx + dz * dz > r * r) continue;
            double s = Math.abs(dx * b.ax() + dz * b.az()), u = Math.abs(-dx * b.az() + dz * b.ax());
            if (s <= b.hl() + rand && u <= b.hw() + rand) return true;
        }
        if (x > ANLEGER_X1 - 3 && x < ANLEGER_X0 + 3 && Math.abs(z - ANLEGER_Z) < ANLEGER_HW + 3) return true;
        return false;
    }

    /** Liegt (x, z) auf dem Campingplatz (Wiese, keine Bäume)? */
    public boolean camping(double x, double z) {
        return Math.hypot(x - CAMP[0], (z - CAMP[1]) * 0.6) < CAMP[2];
    }

    /** Campingplatz: Mitte (x, z) und Halbachse (m, in z gestreckt). */
    static final double[] CAMP = {-62, 175, 48};

    // ------------------------------------------------------------------------------------------------------------ Bau

    private final static class Bauer {
        final Terrain t;
        final Bauten.Netz n = new Bauten.Netz();
        final Mesh m = new Mesh();
        final List<Bau> bauten = new ArrayList<>();
        final List<double[]> lampen = new ArrayList<>();
        final double[] strasse;      // Proben der Straßen im Ort: x, z, halbe Breite
        int fenster;

        Bauer(Terrain t, double[] s) { this.t = t; strasse = s; }

        /** Abstand zur nächsten Straße (vom Rand der Fahrbahn). */
        double strassenAbstand(double x, double z) {
            double best = 1e9;
            for (int i = 0; i < strasse.length; i += 3) best = Math.min(best, Math.hypot(x - strasse[i], z - strasse[i + 1]) - strasse[i + 2]);
            return best;
        }

        /** Richtung der nächsten Straße (Einheit). */
        double[] strassenRichtung(double x, double z) {
            int bi = 0;
            double best = 1e18;
            for (int i = 0; i < strasse.length; i += 3) {
                double d = Math.hypot(x - strasse[i], z - strasse[i + 1]);
                if (d < best) { best = d; bi = i; }
            }
            int a = Math.max(0, bi - 3), b = Math.min(strasse.length - 3, bi + 3);
            double dx = strasse[b] - strasse[a], dz = strasse[b + 1] - strasse[a + 1], l = Math.max(1e-6, Math.hypot(dx, dz));
            return new double[]{dx / l, dz / l};
        }

        /** Richtung der Höhenlinie (Einheit). */
        double[] hoehenlinie(double x, double z) {
            double gx = t.height(x + 6, z) - t.height(x - 6, z), gz = t.height(x, z + 6) - t.height(x, z - 6), l = Math.hypot(gx, gz);
            if (l < 1e-6) return new double[]{1, 0};
            return new double[]{-gz / l, gx / l};
        }

        /** Tiefster und höchster Grund unter den Ecken. */
        double[] grund(double x, double z, double ax, double az, double hl, double hw) {
            double qx = -az, qz = ax, lo = 1e9, hi = -1e9;
            for (int i = -1; i <= 1; i++)
                for (int j = -1; j <= 1; j++) {
                    double y = t.height(x + ax * hl * i + qx * hw * j, z + az * hl * i + qz * hw * j);
                    lo = Math.min(lo, y);
                    hi = Math.max(hi, y);
                }
            return new double[]{lo, hi};
        }

        static float[] panel(float[] c) { return new float[]{c[0] + 2, c[1] + 2, c[2] + 2}; }

        /** Fenster vor einer Wand: Mitte (x, y, z), Normale (nx, nz), Breite, Höhe. */
        void fenster(double x, double y, double z, double nx, double nz, double w, double h) {
            double rx = -nz, rz = nx, o = 0.04;
            x += nx * o;
            z += nz * o;
            int id = fenster++ % 997;
            double[][] c = {{x - rx * w / 2, y, z - rz * w / 2}, {x + rx * w / 2, y, z + rz * w / 2}, {x + rx * w / 2, y + h, z + rz * w / 2}, {x - rx * w / 2, y + h, z - rz * w / 2}};
            double[][] uv = {{0.002, 0}, {0.998, 0}, {0.998, 1}, {0.002, 1}};
            for (int k : new int[]{0, 1, 2, 0, 2, 3})
                n.put(c[k][0], c[k][1], c[k][2], nx, 0, nz, new float[]{-1, (float) (id + uv[k][0]), (float) uv[k][1]});
        }

        /** Tür vor einer Wand. */
        void tuer(double x, double y, double z, double nx, double nz, double w, double h, float[] col) {
            double rx = -nz, rz = nx;
            x += nx * 0.04;
            z += nz * 0.04;
            n.quad(new double[]{x - rx * w / 2, y, z - rz * w / 2}, new double[]{x + rx * w / 2, y, z + rz * w / 2},
                    new double[]{x + rx * w / 2, y + h, z + rz * w / 2}, new double[]{x - rx * w / 2, y + h, z - rz * w / 2}, new double[]{nx, 0, nz}, col);
        }

        /** Fensterreihen an einer Wand der Länge 2·halb: je Geschoss alle {@code schritt} m. */
        void reihe(double cx, double cz, double nx, double nz, double halb, double floor, int geschosse, double schritt, double w, double h, boolean tuerMitte) {
            double rx = -nz, rz = nx;
            int k = Math.max(1, (int) Math.floor((2 * halb - 1.2) / schritt));
            for (int g = 0; g < geschosse; g++)
                for (int i = 0; i < k; i++) {
                    double s = (i - (k - 1) / 2.0) * schritt;
                    if (g == 0 && tuerMitte && Math.abs(s) < schritt * 0.5) continue;
                    fenster(cx + rx * s, floor + 0.9 + g * 2.9, cz + rz * s, nx, nz, w, h);
                }
        }

        /**
         * Ein gestrichenes Holzhaus (auch Laden, Hotel, Terminal): Sockel bis unter den tiefsten Punkt, verschalte Wände,
         * Eckbretter, Satteldach mit Neigung {@code neig} (tan), Fenster je Geschoss, Tür zur Straße (−q oder +q).
         */
        Bau haus(Art art, String name, double x, double z, double ax, double az, double hl, double hw, int geschosse, double wand, double neig,
                 float[] farbe, float[] dach, double schritt, double fw, double fh) {
            double[] g = grund(x, z, ax, az, hl, hw);
            double floor = g[1] + 0.25, qx = -az, qz = ax;
            n.box(x, z, ax, az, hl + 0.1, hw + 0.1, g[0] - 0.6, floor, BETON);
            n.box(x, z, ax, az, hl, hw, floor, floor + wand, panel(farbe));
            double eave = floor + wand, ridge = eave + hw * neig;
            float[] gable = panel(farbe);
            n.roof(x, z, ax, az, hl + 0.45, hw + 0.55, eave - 0.55 * neig, ridge, dach, gable);
            // Eckbretter weiß (bei weißen Häusern dunkler abgesetzt nicht nötig)
            if (farbe != WEISS)
                for (int i = -1; i <= 1; i += 2)
                    for (int j = -1; j <= 1; j += 2)
                        n.box(x + ax * (hl + 0.02) * i + qx * (hw + 0.02) * j, z + az * (hl + 0.02) * i + qz * (hw + 0.02) * j, ax, az, 0.08, 0.08, floor, eave, WEISS);
            // Fenster an beiden Langseiten und in den Giebeln, Tür zur Straße
            double[] sr = strassenRichtung(x, z);
            double sd = strassenAbstand(x + qx * 3, z + qz * 3) < strassenAbstand(x - qx * 3, z - qz * 3) ? 1 : -1;
            for (int s = -1; s <= 1; s += 2)
                reihe(x + qx * hw * s, z + qz * hw * s, qx * s, qz * s, hl, floor, geschosse, schritt, fw, fh, s == sd);
            for (int e = -1; e <= 1; e += 2)
                reihe(x + ax * hl * e, z + az * hl * e, ax * e, az * e, hw, floor, geschosse, Math.max(schritt, 2.4), fw, fh, false);
            if (wand < geschosse * 2.9 - 0.5 || hw * neig > 2.6)   // Fenster im Dachgeschoss (Giebel)
                for (int e = -1; e <= 1; e += 2)
                    fenster(x + ax * (hl + 0.02) * e, eave + 0.3, z + az * (hl + 0.02) * e, ax * e, az * e, Math.min(1.0, hw * 0.4), Math.min(1.1, hw * neig - 0.6));
            tuer(x + qx * hw * sd, floor, z + qz * hw * sd, qx * sd, qz * sd, art == Art.HOTEL || art == Art.LADEN || art == Art.TERMINAL ? 2.2 : 1.0, 2.2,
                    art == Art.LADEN || art == Art.TERMINAL ? new float[]{-1, 999.5f, 0.5f} : TUER);
            Bau b = new Bau(art, name, x, floor, z, ax, az, hl, hw, wand, farbname(farbe), farbname(dach));
            bauten.add(b);
            return b;
        }

        /** Die Kirche: achteckig, weiß, Schindeldach zur Mitte, darauf ein achteckiger Dachreiter mit Helm. */
        void kirche(double x, double z) {
            double r = 7.6, wand = 6.4, rd = r + 0.7;
            double[] g = grund(x, z, 1, 0, r, r);
            double floor = g[1] + 0.3, eave = floor + wand, apex = eave + 5.6;
            float[] holz = panel(WEISS), dach = {0.10f, 0.095f, 0.09f};
            n.box(x, z, 1, 0, r, r, g[0] - 0.6, floor, BETON);
            for (int k = 0; k < 8; k++) {
                double a0 = Math.PI / 8 + k * Math.PI / 4, a1 = a0 + Math.PI / 4, am = (a0 + a1) / 2;
                double[] p0 = {x + Math.sin(a0) * r, floor, z - Math.cos(a0) * r}, p1 = {x + Math.sin(a1) * r, floor, z - Math.cos(a1) * r};
                double[] nn = {Math.sin(am), 0, -Math.cos(am)};
                n.quad(p0, p1, new double[]{p1[0], eave, p1[2]}, new double[]{p0[0], eave, p0[2]}, nn, holz);
                // Dach: Dreieck von der Traufe zur Spitze
                double[] e0 = {x + Math.sin(a0) * rd, eave - 0.4, z - Math.cos(a0) * rd}, e1 = {x + Math.sin(a1) * rd, eave - 0.4, z - Math.cos(a1) * rd};
                double[] top = {x, apex, z};
                double[] dn = Bauten.norm(Bauten.cross(Bauten.sub(e1, e0), Bauten.sub(top, e0)));
                if (dn[1] < 0) dn = new double[]{-dn[0], -dn[1], -dn[2]};
                for (double[] q : new double[][]{e0, e1, top}) n.put(q[0], q[1], q[2], dn[0], dn[1], dn[2], dach);
                // hohe Fenster, im Westen das Portal
                double fx = x + Math.sin(am) * Math.cos(Math.PI / 8) * r, fz = z - Math.cos(am) * Math.cos(Math.PI / 8) * r;
                if (k == 5) tuer(fx, floor, fz, nn[0], nn[2], 1.6, 2.8, TUER);
                else fenster(fx, floor + 1.6, fz, nn[0], nn[2], 1.1, 2.6);
            }
            // Dachreiter
            double rr = 1.9, t0 = apex - 2.2, t1 = apex + 3.2, helm = t1 + 5.0;
            for (int k = 0; k < 8; k++) {
                double a0 = Math.PI / 8 + k * Math.PI / 4, a1 = a0 + Math.PI / 4, am = (a0 + a1) / 2;
                double[] p0 = {x + Math.sin(a0) * rr, t0, z - Math.cos(a0) * rr}, p1 = {x + Math.sin(a1) * rr, t0, z - Math.cos(a1) * rr};
                double[] nn = {Math.sin(am), 0, -Math.cos(am)};
                n.quad(p0, p1, new double[]{p1[0], t1, p1[2]}, new double[]{p0[0], t1, p0[2]}, nn, holz);
                double[] e0 = {x + Math.sin(a0) * (rr + 0.3), t1, z - Math.cos(a0) * (rr + 0.3)}, e1 = {x + Math.sin(a1) * (rr + 0.3), t1, z - Math.cos(a1) * (rr + 0.3)};
                double[] top = {x, helm, z};
                double[] dn = Bauten.norm(Bauten.cross(Bauten.sub(e1, e0), Bauten.sub(top, e0)));
                if (dn[1] < 0) dn = new double[]{-dn[0], -dn[1], -dn[2]};
                for (double[] q : new double[][]{e0, e1, top}) n.put(q[0], q[1], q[2], dn[0], dn[1], dn[2], dach);
                fenster(x + Math.sin(am) * rr * 0.93, t0 + 1.4, z - Math.cos(am) * rr * 0.93, nn[0], nn[2], 0.6, 1.0);
            }
            n.beam(x, helm, z, x, helm + 1.6, z, 0.04, MAST);   // Kreuz
            n.beam(x - 0.45, helm + 1.15, z, x + 0.45, helm + 1.15, z, 0.035, MAST);
            bauten.add(new Bau(Art.KIRCHE, "Geiranger kyrkje", x, floor, z, 1, 0, r, r, wand, "weiß", "Schindeln"));
        }

        /** Ein Wohnwagen oder Wohnmobil: weißer Kasten mit dunklen Fenstern. */
        void wohnwagen(double x, double z, double ax, double az, Random rnd) {
            double hl = 3.0 + rnd.nextDouble(), hw = 1.15, g = t.height(x, z);
            float[] c = rnd.nextDouble() < 0.8 ? new float[]{0.66f, 0.66f, 0.64f} : new float[]{0.55f, 0.53f, 0.47f};
            n.box(x, z, ax, az, hl, hw, g + 0.35, g + 2.8, c);
            n.box(x, z, ax, az, hl - 0.6, hw + 0.01, g + 1.4, g + 2.1, new float[]{0.03f, 0.035f, 0.04f});
            n.box(x, z, ax, az, hl * 0.9, hw * 0.9, g, g + 0.4, new float[]{0.05f, 0.05f, 0.05f});
            bauten.add(new Bau(Art.WOHNWAGEN, "Wohnwagen", x, g, z, ax, az, hl, hw, 2.8, "weiß", null));
        }

        /** Laterne: Mast, Ausleger zur Straße, Leuchte (nachts an). */
        void laterne(double x, double z, double tx, double tz) {
            double g = t.height(x, z), h = 6.0;
            n.beam(x, g - 0.3, z, x, g + h, z, 0.07, MAST);
            n.beam(x, g + h, z, x + tx * 1.2, g + h + 0.15, z + tz * 1.2, 0.04, MAST);
            n.box(x + tx * 1.3, z + tz * 1.3, tx, tz, 0.3, 0.14, g + h - 0.05, g + h + 0.12, MAST);
            n.box(x + tx * 1.3, z + tz * 1.3, tx, tz, 0.26, 0.11, g + h - 0.09, g + h - 0.04, LAMPE);
            lampen.add(new double[]{x + tx * 1.3, g + h - 0.2, z + tz * 1.3});
        }

        /** Ein Naust (Bootshaus) aus timber: Giebel zum Wasser, Grasdach, auf Steinen. */
        void naust(double x, double z, double ax, double az) {
            Holzbau.hus(m, t, x, z, ax, az, 4.2, 2.6, 2.6, false);
            bauten.add(new Bau(Art.NAUST, "Naust", x, t.height(x, z), z, ax, az, 4.2, 2.6, 2.6, "geteert", "Gras"));
        }

        /** Der Anleger aus timber: Bohlendeck auf Pfahlreihen alle 3 m bis zum Grund, Poller. */
        void anleger() {
            double y = ANLEGER_DECK, x0 = ANLEGER_X1, x1 = ANLEGER_X0, z0 = ANLEGER_Z - ANLEGER_HW, z1 = ANLEGER_Z + ANLEGER_HW;
            Holzbau.box(m, (x0 + x1) / 2, ANLEGER_Z, 1, 0, (x1 - x0) / 2, ANLEGER_HW, y - 0.35, y, Material.DECK);
            for (double x = x0 + 0.5; x <= x1 - 2; x += 3)
                for (double z : new double[]{z0 + 0.4, ANLEGER_Z, z1 - 0.4}) {
                    double g = Math.min(t.height(x, z), y - 0.5) - 1.0;
                    Holzbau.pfahl(m, Holzbau.p(x, g, z), Holzbau.p(x, y - 0.3, z), 0.22, Material.POST);
                }
            for (double x = x0 + 2; x <= x1 - 6; x += 9)
                for (double z : new double[]{z0 + 0.5, z1 - 0.5})
                    Holzbau.pfahl(m, Holzbau.p(x, y, z), Holzbau.p(x, y + 0.6, z), 0.18, Material.POST);
            // Laternen am Anleger
            for (double x = x0 + 6; x <= x1 - 4; x += 18) {
                double g = y;
                n.beam(x, g, z1 - 0.6, x, g + 5, z1 - 0.6, 0.06, MAST);
                n.box(x, z1 - 0.6, 1, 0, 0.18, 0.18, g + 5, g + 5.25, MAST);
                n.box(x, z1 - 0.6, 1, 0, 0.15, 0.15, g + 4.93, g + 5.0, LAMPE);
                lampen.add(new double[]{x, g + 4.8, z1 - 0.6});
            }
        }
    }

    /** Proben der Straßen im Ort (x, z, halbe Breite) aus dem Netz der Fv63 und dem Strandvegen. */
    static double[] strassen(Ornevegen o) {
        List<double[]> s = new ArrayList<>();
        if (o != null) {
            for (com.dan.road.Way w : o.net.ways)
                for (int i = 0; i < w.n; i += 2) {
                    if (w.x[i] < -400 || w.x[i] > 500 || w.z[i] < -350 || w.z[i] > 500) continue;
                    s.add(new double[]{w.x[i], w.z[i], w.type.half()});
                }
        }
        double[] out = new double[s.size() * 3];
        for (int i = 0; i < s.size(); i++) System.arraycopy(s.get(i), 0, out, 3 * i, 3);
        return out;
    }

    public static Dorf build(Terrain t) {
        Bauer b = new Bauer(t, strassen(t.ornevegen));
        if (!t.measured() || t.ornevegen == null) return new Dorf(List.of(), new float[0], new Mesh(), List.of(), 0);
        Random rnd = new Random(1102);
        // --- fest gesetzte Bauten
        double[] hl = b.hoehenlinie(UNION[0], UNION[1]);
        b.haus(Art.HOTEL, "Hotel Union", UNION[0], UNION[1], hl[0], hl[1], 30, 8.5, 4, 12.2, 0.45, WEISS, SCHIEFER, 2.6, 1.2, 1.5);
        b.haus(Art.HOTEL, "Hotel Geiranger", HOTEL[0], HOTEL[1], 0, 1, 24, 7, 4, 12.0, 0.25, new float[]{0.56f, 0.56f, 0.53f}, SCHIEFER, 2.5, 1.4, 1.5);
        b.haus(Art.TERMINAL, "Terminal", TERMINAL[0], TERMINAL[1], 0, 1, 11, 4.5, 1, 4.6, 0.2, BLAUGRAU, SCHIEFER, 2.2, 1.6, 2.0);
        b.kirche(KIRCHE[0], KIRCHE[1]);
        b.anleger();
        // vier Naust am Ufer nördlich des Hafens, Giebel zum Wasser (nach Westen)
        double[][] naust = {{-31, -104}, {-30, -119}, {-33, -134}, {-41, -152}};
        for (double[] p : naust) {
            // Richtung: vom Land zum Wasser (Gefälle), First quer zum Ufer
            double gx = t.height(p[0] + 5, p[1]) - t.height(p[0] - 5, p[1]), gz = t.height(p[0], p[1] + 5) - t.height(p[0], p[1] - 5), l = Math.max(1e-6, Math.hypot(gx, gz));
            b.naust(p[0], p[1], -gx / l, -gz / l);
        }
        // --- Campingplatz: Rezeption, Hütten am Ostrand, Wohnwagen in Reihen
        double[] sr = b.strassenRichtung(REZEPTION[0], REZEPTION[1]);
        b.haus(Art.REZEPTION, "Geiranger Camping", REZEPTION[0], REZEPTION[1], sr[0], sr[1], 6, 4, 1, 3.4, 0.55, ROT, GRAS, 2.4, 1.0, 1.2);
        int hytter = 0, wagen = 0;
        for (double z = CAMP[1] - 70; z <= CAMP[1] + 70; z += 9)
            for (double x = CAMP[0] - 40; x <= CAMP[0] + 40; x += 11) {
                double px = x + (rnd.nextDouble() - 0.5) * 3, pz = z + (rnd.nextDouble() - 0.5) * 3;
                if (Math.hypot(px - CAMP[0], (pz - CAMP[1]) * 0.6) > CAMP[2] - 6) continue;
                if (!platzFrei(t, b, px, pz, 4.5, 6)) continue;
                double[] d = b.strassenRichtung(px, pz);
                if (!eckenFrei(t, b, px, pz, d[0], d[1], 4.0, 2.2)) continue;
                boolean hytte = b.strassenAbstand(px, pz) < 16 && hytter < 10;
                if (hytte) {
                    float[] c = hytter % 3 == 0 ? TEER : ROT;
                    b.haus(Art.HYTTE, "Hytte", px, pz, d[0], d[1], 2.6, 2.1, 1, 2.4, 0.6, c, GRAS, 2.0, 0.7, 0.8);
                    hytter++;
                } else if (wagen < 16 && rnd.nextDouble() < 0.8) {
                    b.wohnwagen(px, pz, d[0], d[1], rnd);
                    wagen++;
                }
            }
        // --- die übrigen Häuser aus dem Raster
        List<double[]> kand = new ArrayList<>();
        for (double z = -230; z <= 440; z += 7)
            for (double x = -240; x <= 420; x += 7) {
                double px = x + (rnd.nextDouble() - 0.5) * 5, pz = z + (rnd.nextDouble() - 0.5) * 5;
                double sd = b.strassenAbstand(px, pz);
                if (sd < 7 || sd > 140) continue;
                double score = Math.abs(sd - 18) / 45 + 0.6 * rnd.nextDouble() + 0.002 * Math.hypot(px - 30, pz - 120);
                kand.add(new double[]{px, pz, score, sd});
            }
        kand.sort((p, q) -> Double.compare(p[2], q[2]));
        int raster = 0;
        float[][] farben = {WEISS, WEISS, WEISS, WEISS, ROT, ROT, ROT, OCKER, GRAU, BLAUGRAU, GRUEN, WEISS};
        float[][] daecher = {SCHIEFER, SCHIEFER, SCHIEFER, ZIEGEL, GRAS, GRAS};
        for (double[] k : kand) {
            if (raster >= RASTERHAEUSER) break;
            double px = k[0], pz = k[1], sd = k[3];
            Art art = sd < 20 && pz > -60 && pz < 170 && rnd.nextDouble() < 0.45 ? Art.LADEN : rnd.nextDouble() < 0.1 ? Art.UTHUS : Art.WOHNHAUS;
            double hlh, hwh, wand, neig;
            int gs;
            switch (art) {
                case LADEN -> { hlh = 7 + 3 * rnd.nextDouble(); hwh = 5 + rnd.nextDouble(); gs = 2; wand = 5.8; neig = 0.5; }
                case UTHUS -> { hlh = 4 + 2 * rnd.nextDouble(); hwh = 3 + rnd.nextDouble(); gs = 1; wand = 3.2; neig = 0.65; }
                default -> { hlh = 5 + 1.6 * rnd.nextDouble(); hwh = 3.6 + 0.7 * rnd.nextDouble(); gs = rnd.nextDouble() < 0.6 ? 2 : 1; wand = gs == 2 ? 5.6 : 3.6; neig = 0.58 + 0.12 * rnd.nextDouble(); }
            }
            double[] d = sd < 35 ? b.strassenRichtung(px, pz) : b.hoehenlinie(px, pz);
            if (!platzFrei(t, b, px, pz, Math.hypot(hlh, hwh) + 2.5, 0)) continue;
            if (!eckenFrei(t, b, px, pz, d[0], d[1], hlh, hwh)) continue;
            float[] farbe = art == Art.UTHUS ? (rnd.nextDouble() < 0.5 ? ROT : TEER) : farben[rnd.nextInt(farben.length)];
            float[] dach = art == Art.UTHUS ? GRAS : art == Art.LADEN ? SCHIEFER : daecher[rnd.nextInt(daecher.length)];
            b.haus(art, art == Art.LADEN ? "Laden" : art == Art.UTHUS ? "Uthus" : "Wohnhaus", px, pz, d[0], d[1], hlh, hwh, gs, wand, neig, farbe, dach,
                    art == Art.LADEN ? 2.4 : 2.7, art == Art.LADEN ? 1.4 : 1.0, art == Art.LADEN ? 1.6 : 1.25);
            raster++;
        }
        // --- Vinje: Wohnhaus und Scheune je Hof auf der Terrasse über dem Ort
        int vinje = 0;
        for (int versuch = 0; versuch < 4000 && vinje < VINJE; versuch++) {
            double px = 190 + rnd.nextDouble() * 170, pz = 150 + rnd.nextDouble() * 250;
            if (t.height(px, pz) < 75) continue;
            double[] d = b.hoehenlinie(px, pz);
            boolean wohn = vinje % 2 == 0;
            double hlh = wohn ? 5.5 : 7.5, hwh = wohn ? 3.8 : 4.6;
            if (!platzFrei(t, b, px, pz, Math.hypot(hlh, hwh) + (wohn ? 25 : 2.5), 0) || !eckenFrei(t, b, px, pz, d[0], d[1], hlh, hwh)) continue;
            b.haus(wohn ? Art.WOHNHAUS : Art.UTHUS, wohn ? "Vinje" : "Løe", px, pz, d[0], d[1], hlh, hwh, wohn ? 2 : 1, wohn ? 5.6 : 4.6, 0.62,
                    wohn ? WEISS : ROT, wohn ? SCHIEFER : GRAS, 2.7, 1.0, 1.25);
            vinje++;
            if (!wohn) continue;
            // die Scheune 14 bis 18 m daneben entlang der Höhenlinie
            double sx = px + d[0] * 16, sz = pz + d[1] * 16;
            if (platzFrei(t, b, sx, sz, 8, 0) && eckenFrei(t, b, sx, sz, d[0], d[1], 7.5, 4.6)) {
                b.haus(Art.UTHUS, "Løe", sx, sz, d[0], d[1], 7.5, 4.6, 1, 4.6, 0.62, ROT, GRAS, 3.0, 0.8, 0.9);
                vinje++;
            }
        }
        // --- Laternen an der Fv63 im Ort (alle 32 m, Talseite) und am Strandvegen (alle 30 m)
        for (com.dan.road.Way w : t.ornevegen.net.ways) {
            double schritt = w == t.ornevegen.strandvegen ? 30 : 32;
            double last = -1e9;
            float[] q = new float[8];
            for (double s = 4; s < w.length; s += 2) {
                w.at(s, 0, q);
                if (q[0] < -150 || q[0] > 200 || q[2] < -200 || q[2] > 380) continue;
                if (s - last < schritt) continue;
                float[] q2 = new float[8];
                w.at(Math.min(w.length, s + 1), 0, q2);
                double tx = q2[0] - q[0], tz = q2[2] - q[2], tl = Math.max(1e-6, Math.hypot(tx, tz));
                tx /= tl;
                tz /= tl;
                double nx = tz, nz = -tx;   // rechts der Fahrtrichtung
                double off = w.type.half() + 0.8;
                double lx = q[0] + nx * off, lz = q[2] + nz * off;
                if (t.fjord(lx, lz) || b.strassenAbstand(lx, lz) < 0.3 || bebautIntern(b, lx, lz, 0.8)) {
                    lx = q[0] - nx * off;
                    lz = q[2] - nz * off;
                    nx = -nx;
                    nz = -nz;
                    if (t.fjord(lx, lz) || bebautIntern(b, lx, lz, 0.8)) continue;
                }
                b.laterne(lx, lz, -nx, -nz);
                last = s;
            }
        }
        return new Dorf(Collections.unmodifiableList(b.bauten), b.n.toArray(), b.m, Collections.unmodifiableList(b.lampen), b.fenster);
    }

    static boolean bebautIntern(Bauer b, double x, double z, double rand) {
        for (Bau u : b.bauten) {
            double dx = x - u.x(), dz = z - u.z();
            double s = Math.abs(dx * u.ax() + dz * u.az()), w = Math.abs(-dx * u.az() + dz * u.ax());
            if (s <= u.hl() + rand && w <= u.hw() + rand) return true;
        }
        return false;
    }

    /** Freier Platz: Land über dem Ufer, nicht zu steil, nicht in der Elva, Abstand zu Straße und Bauten. */
    static boolean platzFrei(Terrain t, Bauer b, double x, double z, double r, double strasse) {
        if (t.fjord(x, z)) return false;
        double h = t.height(x, z);
        if (h < Terrain.WASSER + 1.4 || h > 125) return false;
        double gx = (t.height(x + 6, z) - t.height(x - 6, z)) / 12, gz = (t.height(x, z + 6) - t.height(x, z - 6)) / 12;
        if (Math.hypot(gx, gz) > 0.55) return false;
        Elva e = t.elva;
        if (e != null) {
            double[] nb = e.nearest(x, z);
            if (nb[1] < e.half[(int) nb[0]] + 8 + r * 0.5) return false;
        }
        if (strasse > 0 && b.strassenAbstand(x, z) < strasse) return false;
        if (Plattform.auf(Plattform.rechteck(t.ornevegen), x, z, r)) return false;
        for (Bau u : b.bauten) if (Math.hypot(x - u.x(), z - u.z()) < r + u.radius() + 3) return false;
        if (x > ANLEGER_X1 - r && x < ANLEGER_X0 + r && Math.abs(z - ANLEGER_Z) < ANLEGER_HW + r) return false;
        return true;
    }

    /** Alle Ecken (mit 1 m Rand) auf Land, über dem Ufer und mindestens 1,5 m neben der Fahrbahn. */
    static boolean eckenFrei(Terrain t, Bauer b, double x, double z, double ax, double az, double hl, double hw) {
        double qx = -az, qz = ax;
        for (int i = -1; i <= 1; i++)
            for (int j = -1; j <= 1; j++) {
                double cx = x + ax * (hl + 1) * i + qx * (hw + 1) * j, cz = z + az * (hl + 1) * i + qz * (hw + 1) * j;
                if (t.fjord(cx, cz) || t.height(cx, cz) < Terrain.WASSER + 1.2) return false;
                if (b.strassenAbstand(cx, cz) < 1.5) return false;
            }
        double[] g = b.grund(x, z, ax, az, hl, hw);
        return g[1] - g[0] < 3.5;   // nicht mehr als 3,5 m Sockel
    }

    /** „Ort: 61 Gebäude (Kirche, 2 Hotels, …), 16 Wohnwagen, 412 Fenster, 22 Laternen“ */
    public String describe() {
        if (bauten.isEmpty()) return "Ort: keiner (ohne Gelände)";
        int[] c = new int[Art.values().length];
        for (Bau b : bauten) c[b.art().ordinal()]++;
        return String.format(Locale.GERMAN, "Ort: %d Gebäude (%d Wohnhäuser, %d Läden, %d Nebenhäuser, %d Hotels, Kirche, Terminal, %d Naust, Camping mit %d Hütten und %d Wohnwagen) · %d Fenster · %d Laternen · Anleger %.0f × %.0f m",
                gebaeude(), c[Art.WOHNHAUS.ordinal()], c[Art.LADEN.ordinal()], c[Art.UTHUS.ordinal()], c[Art.HOTEL.ordinal()], c[Art.NAUST.ordinal()],
                c[Art.HYTTE.ordinal()], c[Art.WOHNWAGEN.ordinal()], fenster, lampen.size(), ANLEGER_X0 - ANLEGER_X1, 2 * ANLEGER_HW);
    }
}
