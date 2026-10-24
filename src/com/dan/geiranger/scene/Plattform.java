package com.dan.geiranger.scene;

import java.util.Locale;

/**
 * Phase 9: die Aussichtsplattform der Ørnesvingen außen an der elften Kehre [G, nach den Fotos Ørnesvingen1 und 2]: ein
 * Deck aus Beton, {@link #TIEFE} m nach außen und {@link #LAENGE} m entlang der Kehre, auf Stützen über dem Hang, mit
 * einem Geländer aus Stahlpfosten und Seilen an den drei freien Seiten und zwei Bänken. Das Deck liegt auf Höhe der
 * Fahrbahn; das Feinraster senkt den Hang darunter ({@link Formen}).
 * <p>
 * Lage aus {@link Ornevegen#plattform}: Mitte des Decks, Höhe der Fahrbahn, Richtung nach außen. Gezeichnet wie die
 * Bauten an der Elva ({@code bauten.vert/frag}).
 */
public final class Plattform {
    public static final double TIEFE = 12, LAENGE = 22, DICKE = 0.45, GELAENDER = 1.1;
    public static final float[] BETON = {0.42f, 0.41f, 0.39f}, STAHL = {0.30f, 0.31f, 0.33f}, HOLZ = {0.30f, 0.21f, 0.12f};

    /** Ecken (je 9 Werte wie {@link Bauten#ecken}). */
    public final float[] ecken;
    /** Mitte des Decks x, Oberkante, z; Richtung nach außen (Grad); Eckpunkte innen und außen. */
    public final double x, y, z, richtung;
    public final int stuetzen, pfosten;

    private Plattform(float[] e, double x, double y, double z, double r, int st, int pf) {
        ecken = e; this.x = x; this.y = y; this.z = z; richtung = r; stuetzen = st; pfosten = pf;
    }

    /** Oberkante des Decks über der Fahrbahn am Scheitel (m). */
    public static final double UEBER = 0.15;

    /** Das Rechteck des Decks in der Szene: Mitte, Richtung nach außen (Einheit), halbe Tiefe und halbe Länge. */
    public static double[] rechteck(Ornevegen o) {
        if (o == null || o.plattform == null) return null;
        double[] p = o.plattform;
        double a = Math.toRadians(p[3]), ox = Math.sin(a), oz = -Math.cos(a);
        return new double[]{p[0], p[1] + UEBER, p[2], ox, oz, TIEFE / 2, LAENGE / 2};
    }

    /** Liegt (x, z) auf dem Deck (mit Rand r)? */
    public static boolean auf(double[] r, double x, double z, double rand) {
        if (r == null) return false;
        double dx = x - r[0], dz = z - r[2];
        double s = dx * r[3] + dz * r[4], u = -dx * r[4] + dz * r[3];
        return Math.abs(s) <= r[5] + rand && Math.abs(u) <= r[6] + rand;
    }

    public static Plattform build(Terrain t) {
        double[] r = rechteck(t.ornevegen);
        if (r == null) return null;
        Bauten.Netz n = new Bauten.Netz();
        double cx = r[0], top = r[1], cz = r[2], ox = r[3], oz = r[4], ht = r[5], hl = r[6];
        double qx = -oz, qz = ox;   // entlang der Kehre
        n.box(cx, cz, ox, oz, ht, hl, top - DICKE, top, BETON);
        // Stützen alle 6 m, wo der Hang mehr als 0,6 m unter dem Deck liegt
        int st = 0;
        for (double s = -ht + 1.5; s <= ht - 1.4; s += 6)
            for (double u = -hl + 1.5; u <= hl - 1.4; u += 6) {
                double x = cx + ox * s + qx * u, z = cz + oz * s + qz * u, g = t.height(x, z);
                if (g < top - DICKE - 0.6) {
                    n.box(x, z, ox, oz, 0.22, 0.22, g - 0.5, top - DICKE, BETON);
                    st++;
                }
            }
        // Geländer an den drei freien Seiten (außen und an beiden Enden): Pfosten alle 2 m, Handlauf und zwei Seile
        int pf = 0;
        double[][] seiten = {{ht, -hl, ht, hl}, {-ht, -hl, ht, -hl}, {-ht, hl, ht, hl}};
        for (double[] sd : seiten) {
            double ax = cx + ox * sd[0] + qx * sd[1], az = cz + oz * sd[0] + qz * sd[1];
            double bx = cx + ox * sd[2] + qx * sd[3], bz = cz + oz * sd[2] + qz * sd[3];
            double len = Math.hypot(bx - ax, bz - az);
            int k = Math.max(1, (int) Math.round(len / 2));
            for (int i = 0; i <= k; i++) {
                double x = ax + (bx - ax) * i / k, z = az + (bz - az) * i / k;
                n.box(x, z, ox, oz, 0.03, 0.03, top, top + GELAENDER, STAHL);
                pf++;
            }
            n.beam(ax, top + GELAENDER, az, bx, top + GELAENDER, bz, 0.035, STAHL);
            n.beam(ax, top + 0.7, az, bx, top + 0.7, bz, 0.008, STAHL);
            n.beam(ax, top + 0.35, az, bx, top + 0.35, bz, 0.008, STAHL);
        }
        // zwei Bänke quer zur Aussicht
        for (int b = -1; b <= 1; b += 2) {
            double x = cx + ox * 2 + qx * b * 6, z = cz + oz * 2 + qz * b * 6;
            n.box(x, z, qx, qz, 1.0, 0.22, top + 0.42, top + 0.47, HOLZ);
            n.box(x, z, qx, qz, 0.9, 0.18, top, top + 0.42, BETON);
        }
        double head = (Math.toDegrees(Math.atan2(ox, -oz)) + 360) % 360;
        return new Plattform(n.toArray(), cx, top, cz, head, st, pf);
    }

    /** „Plattform der Ørnesvingen: 532 m, Deck 20 × 28 m, 18 Stützen, 64 Pfosten“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Plattform der Ørnesvingen: %.0f m, Deck %.0f × %.0f m, %d Stützen, %d Pfosten", y, TIEFE, LAENGE, stuetzen, pfosten);
    }
}
