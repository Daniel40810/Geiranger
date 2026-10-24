package com.dan.geiranger.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 10: die Fjordhöfe Knivsflå und Skageflå auf ihren Bändern [G, nach Beschreibungen und Fotos]: je ein Wohnhaus,
 * eine Scheune (Løe), ein Speicher auf Stelzen (Stabbur) und zwei kleine Nebenhäuser, alle mit Grasdach (Torvtak), auf
 * Fundamenten aus Bruchstein. Seit Phase 11 aus {@code com.dan.timber} ({@link Holzbau}): senkrechte, vergraute Bohlen
 * wie auf den Fotos Geiranger1 und 3 (bis Phase 10 Kästen im Format der Bauten, geteert und rot). Darum eine Hofwiese ohne
 * Bäume ({@link #WIESE} m). Die Lage sucht {@link #build} in {@link #SUCHE} m um den Ort aus {@link Site} die flachste
 * Stelle des Bands; die Häuser stehen entlang der Höhenlinie, der Hang fällt nach vorn zum Fjord.
 * <p>
 * Die Häuser zeichnet {@code TimberGl} mit dem Ort ({@code DorfGl}).
 */
public final class Hoefe {
    public static final double SUCHE = 110, WIESE = 75;
    public static final float[] TEER = {0.11f, 0.075f, 0.05f}, ROT = {0.30f, 0.07f, 0.045f}, WEISS = {0.62f, 0.61f, 0.57f},
            GRAS = {0.12f, 0.16f, 0.055f}, STEIN = {0.25f, 0.245f, 0.23f};

    /** Ein Hof: Name, Mitte (Szene), Höhe, Richtung der Höhenlinie (Einheit), Neigung des Bands (Grad). */
    public record Hof(String name, double x, double y, double z, double tx, double tz, double neigung, int haeuser) { }

    public final List<Hof> hoefe;
    /** Bis Phase 10 die Häuser im Format der Bauten; seit Phase 11 leer (die Häuser sind aus {@code timber}, {@link #holz}). */
    public final float[] ecken;
    /** Phase 11: die Häuser aus {@code com.dan.timber} (Bohlen, Grasdach, Bruchstein; Weltkoordinaten). */
    public final com.dan.timber.Mesh holz;

    private Hoefe(List<Hof> h, float[] e, com.dan.timber.Mesh m) { hoefe = h; ecken = e; holz = m; }

    /** Ein Haus: Lage entlang (a) und quer (b) zum Hof, halbe Länge und Breite, Wandhöhe, Wandfarbe, Stelzen. */
    record Haus(double a, double b, double hl, double hw, double wand, float[] farbe, boolean stelzen) { }

    static final Haus[] PLAN = {
            new Haus(0, 0, 6.0, 3.6, 4.2, WEISS, false),        // Wohnhaus
            new Haus(16, -6, 8.0, 4.6, 5.0, ROT, false),        // Løe (Scheune), etwas bergauf
            new Haus(-11, 3, 2.4, 2.4, 2.6, TEER, true),        // Stabbur auf Stelzen
            new Haus(-18, -4, 3.0, 2.2, 2.4, TEER, false),      // Nebenhaus
            new Haus(8, 9, 2.6, 2.0, 2.3, TEER, false)};        // Waschhaus

    public static Hoefe build(Terrain t) {
        List<Hof> out = new ArrayList<>();
        com.dan.timber.Mesh m = new com.dan.timber.Mesh();
        if (t.measured())
            for (Site.Ort o : new Site.Ort[]{Site.KNIVSFLA, Site.SKAGEFLA}) {
                // flachste Stelle in SUCHE m (Neigung über 15 m), nicht im Fjord, nicht tiefer als 120 m
                double bx = o.x(), bz = o.z(), best = Double.MAX_VALUE;
                for (double dz = -SUCHE; dz <= SUCHE; dz += 6)
                    for (double dx = -SUCHE; dx <= SUCHE; dx += 6) {
                        if (dx * dx + dz * dz > SUCHE * SUCHE) continue;
                        double x = o.x() + dx, z = o.z() + dz;
                        if (t.fjord(x, z) || t.height(x, z) < 120) continue;
                        double s = slope(t, x, z, 15) + 0.0004 * (dx * dx + dz * dz) / SUCHE;   // nah am Ort bevorzugt
                        if (s < best) { best = s; bx = x; bz = z; }
                    }
                double gx = (t.height(bx + 10, bz) - t.height(bx - 10, bz)) / 20, gz = (t.height(bx, bz + 10) - t.height(bx, bz - 10)) / 20;
                double gl = Math.max(1e-6, Math.hypot(gx, gz));
                double nx = -gx / gl, nz = -gz / gl;          // bergab
                double ax = -nz, az = nx;                      // entlang der Höhenlinie
                int k = 0;
                for (Haus h : PLAN) {
                    double cx = bx + ax * h.a() + nx * h.b(), cz = bz + az * h.a() + nz * h.b();
                    if (t.fjord(cx, cz)) continue;
                    Holzbau.hus(m, t, cx, cz, ax, az, h.hl(), h.hw(), h.wand(), h.stelzen());
                    k++;
                }
                out.add(new Hof(o.name(), bx, t.height(bx, bz), bz, ax, az, Math.toDegrees(Math.atan(gl)), k));
            }
        return new Hoefe(out, new float[0], m);
    }

    /** Phase 17: Namen der Häuser des Plans (für GR_GEBAEUDE). */
    public static final String[] PLAN_NAMEN = {"Wohnhaus", "Løe", "Stabbur", "Nebenhaus", "Waschhaus"};

    /** Phase 17: ein Haus eines Hofs, wie {@link #build} es setzt: Name, Mitte, Richtung, halbe Länge und Breite, Wandhöhe, Farbe. */
    public record HausLage(String name, double x, double y, double z, double ax, double az, double hl, double hw, double wand, String farbe, boolean stelzen) { }

    /** Phase 17: die Häuser eines Hofs (ohne die, die im Fjord lägen). */
    public static List<HausLage> haeuser(Terrain t, Hof h) {
        List<HausLage> l = new ArrayList<>();
        double nx = h.tz(), nz = -h.tx();   // bergab (ax = −nz, az = nx)
        for (int i = 0; i < PLAN.length; i++) {
            Haus hs = PLAN[i];
            double cx = h.x() + h.tx() * hs.a() + nx * hs.b(), cz = h.z() + h.tz() * hs.a() + nz * hs.b();
            if (t.fjord(cx, cz)) continue;
            l.add(new HausLage(PLAN_NAMEN[i] + " " + h.name(), cx, t.height(cx, cz), cz, h.tx(), h.tz(), hs.hl(), hs.hw(), hs.wand(),
                    hs.farbe() == WEISS ? "weiß" : hs.farbe() == ROT ? "rot" : "geteert", hs.stelzen()));
        }
        return l;
    }

    static double slope(Terrain t, double x, double z, double r) {
        double gx = (t.height(x + r, z) - t.height(x - r, z)) / (2 * r), gz = (t.height(x, z + r) - t.height(x, z - r)) / (2 * r);
        return Math.hypot(gx, gz);
    }

    /** Die Hofwiesen als {x, z, Radius} für das Gelände und den Wald. */
    public double[][] wiesen() {
        double[][] w = new double[hoefe.size()][];
        for (int i = 0; i < w.length; i++) w[i] = new double[]{hoefe.get(i).x(), hoefe.get(i).z(), WIESE};
        return w;
    }

    /** „Höfe: Knivsflå 256 m (5 Häuser, Band 14°), Skageflå …“ */
    public String describe() {
        if (hoefe.isEmpty()) return "Höfe: keine (ohne Gelände)";
        StringBuilder sb = new StringBuilder("Höfe:");
        for (Hof h : hoefe) sb.append(String.format(Locale.GERMAN, " %s %.0f m (%d Häuser, Band %.0f°)", h.name(), h.y(), h.haeuser(), h.neigung()));
        return sb.toString();
    }
}
