package com.dan.geiranger.scene;

import java.util.Locale;

/**
 * Phase 8: was am Gelände von Hand geformt wird, bevor irgendetwas es sieht (Kamera, Detailstufen, Wandkarte, Schatten,
 * Spiegelbild, Fälle). {@link Terrain#load} wendet es auf die Rohdaten aus {@code daten/gelaende} an; die Dateien
 * bleiben unverändert ({@code -Dgeiranger.roh=true} lädt sie ohne).
 * <ul>
 * <li><b>Wandschnitt</b> (F12b A): Das 1″-Raster rundet die Wände ab; unter den Kanten der Sieben Schwestern und von
 * Friaren wird die Wand zu einer fast senkrechten Stufe geschärft, ohne das Gelände zu verschieben: über der halben
 * Höhe der Stufe wird aufgefüllt (ein Absatz bis zur Front), darunter abgetragen bis zum Fuß der Stufe, von dort fällt
 * eine Halde mit 35° ab, bis sie das alte Gelände trifft. Quer läuft der Schnitt auf 60 m weich aus. Die Kante über
 * der Front steht danach in {@link Terrain#punkte} („Wand Sieben Schwestern“: x, y, z, Richtung).</li>
 * <li><b>Bett der Geirangelva</b>: entlang ihres Laufs ({@link Elva}) wird ein Bett eingetieft: in der Mitte
 * {@link Elva#TIEFE} m unter dem Spiegel (in den Stufen {@link Elva#TIEFE_STUFE} m), zum Ufer parabelförmig flacher,
 * jenseits der halben Breite steigen die Ufer mit 35° bis zum alten Gelände. Nur abgetragen, nie aufgefüllt: das
 * Wasser liegt in einer Rinne, und kein Gelände ragt über den Spiegel. Das 12-m-Raster macht daraus eine Rinne von
 * zwei Zellen; die Uferlinie im Bild kommt vom Wasser, nicht vom Raster.</li>
 * </ul>
 * Höhen der Stufen [G]: die Schwestern 250 m (ihr freier Fall), Friaren 120 m (oben frei, darunter die Platten wie auf
 * Waterfall2).
 */
public final class Formen {
    private Formen() { }

    /** Phase 17: was geformt wurde, als Zahlen (GR_FORM): Name, Art (WAND, FLUSSBETT, STRASSE), Fläche (Szene x, z), Stufe, Front, Zellen, Zellgröße, Menge (m³). */
    public record Form(String name, String art, double[][] flaeche, double stufe, double front, int zellen, double zelle, double m3) { }

    /** Ein Wandschnitt: Fall (Name, Fuß, Höhe der Kante, Breite wie in {@link Faelle}) und Höhe der Stufe (m). */
    public record Schnitt(Faelle.Def fall, double stufe) { }

    public static final java.util.List<Schnitt> SCHNITTE = java.util.List.of(
            new Schnitt(Faelle.SCHWESTERN, 250), new Schnitt(Faelle.FRIAREN, 120));
    /** Seitlicher Auslauf (m), Neigung der Halde unter der Stufe (Grad), Absenkung des Absatzes bis zur Front (m). */
    public static final double AUSLAUF = 60, HALDE = 35, ABSATZ = 4;

    /** Alles anwenden (nur mit echten Höhendaten). */
    public static void anwenden(Terrain t) {
        if (!t.measured()) return;
        Elva e = Elva.build(t);   // auf dem ungeformten Gelände
        for (Schnitt s : SCHNITTE) wandschnitt(t, s);
        if (e != null) flussbett(t, e);
        t.elva = e;
        t.refresh();
        Ornevegen o = Ornevegen.build(t);   // Phase 9: auf dem geformten Gelände, dann ihr Feinraster
        if (o != null) strasse(t, o);
        t.ornevegen = o;
    }

    /** Rand des Feinrasters um die Achse der Straße (m). */
    public static final double FEIN_RAND = 40;

    /**
     * Phase 9: Einschnitt, Berme und Damm der Straße ({@code Network.shape}) ins Feinraster: über dem Rechteck um die
     * Achse (mit {@link #FEIN_RAND} m Rand) je Zelle die geformte Höhe minus die Höhe aus Haupt- und Fernraster. Über dem
     * Fjord nichts.
     */
    static void strasse(Terrain t, Ornevegen o) {
        com.dan.road.Way w = o.way;
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i < w.n; i++) {
            x0 = Math.min(x0, w.x[i]); x1 = Math.max(x1, w.x[i]); z0 = Math.min(z0, w.z[i]); z1 = Math.max(z1, w.z[i]);
        }
        x0 = Math.floor((x0 - FEIN_RAND) / Terrain.FEIN) * Terrain.FEIN;
        z0 = Math.floor((z0 - FEIN_RAND) / Terrain.FEIN) * Terrain.FEIN;
        int nx = (int) Math.ceil((x1 + FEIN_RAND - x0) / Terrain.FEIN), nz = (int) Math.ceil((z1 + FEIN_RAND - z0) / Terrain.FEIN);
        float[] d = new float[nx * nz];
        java.util.concurrent.atomic.DoubleAdder cut = new java.util.concurrent.atomic.DoubleAdder(), fill = new java.util.concurrent.atomic.DoubleAdder();
        final double fx0 = x0, fz0 = z0;
        java.util.stream.IntStream.range(0, nz).parallel().forEach(j -> {
            for (int i = 0; i < nx; i++) {
                double x = fx0 + (i + 0.5) * Terrain.FEIN, z = fz0 + (j + 0.5) * Terrain.FEIN;
                if (t.fjord(x, z)) continue;
                double b = t.baseHeight(x, z), y = o.net.shape(x, z, b);
                double dd = y - b;
                if (Math.abs(dd) < 0.01) continue;
                d[j * nx + i] = (float) dd;
                if (dd < 0) cut.add(-dd * Terrain.FEIN * Terrain.FEIN); else fill.add(dd * Terrain.FEIN * Terrain.FEIN);
            }
        });
        // unter dem Deck der Plattform mindestens 1,2 m Luft (sonst steckte das Deck im Hang)
        double[] r = Plattform.rechteck(o);
        int lowered = 0;
        if (r != null)
            for (int j = 0; j < nz; j++)
                for (int i = 0; i < nx; i++) {
                    double x = x0 + (i + 0.5) * Terrain.FEIN, z = z0 + (j + 0.5) * Terrain.FEIN;
                    if (!Plattform.auf(r, x, z, 2.5)) continue;
                    double b = t.baseHeight(x, z), lim = r[1] - Plattform.DICKE - 1.2 - b;
                    if (d[j * nx + i] > lim) { d[j * nx + i] = (float) lim; lowered++; }
                }
        t.feinX0 = x0;
        t.feinZ0 = z0;
        t.feinNx = nx;
        t.feinNz = nz;
        t.fein = d;
        int geaendert = 0;
        for (float v : d) if (v != 0) geaendert++;
        t.formDaten.add(new Form("Feinraster der Fv63", "STRASSE", new double[][]{{x0, z0}, {x0 + nx * Terrain.FEIN, z0}, {x0 + nx * Terrain.FEIN, z0 + nz * Terrain.FEIN}, {x0, z0 + nz * Terrain.FEIN}},
                Double.NaN, Double.NaN, geaendert, Terrain.FEIN, cut.sum() + fill.sum()));
        t.formen.add(String.format(Locale.GERMAN, "Fv63: Feinraster %d × %d zu %.0f m, Einschnitt %.2f Mio. m³, Damm %.2f Mio. m³, unter der Plattform %d Zellen gesenkt",
                nx, nz, Terrain.FEIN, cut.sum() / 1e6, fill.sum() / 1e6, lowered));
    }

    /** Ufer der Elva: Neigung über der halben Breite (Grad). */
    public static final double UFER = 35;

    /** Tieft das Bett der Elva ein (nur Abtrag). */
    static void flussbett(Terrain t, Elva e) {
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE, reach = 40;
        for (int i = 0; i < e.n; i++) {
            x0 = Math.min(x0, e.x[i]); x1 = Math.max(x1, e.x[i]); z0 = Math.min(z0, e.z[i]); z1 = Math.max(z1, e.z[i]);
        }
        int i0 = (int) Math.floor((x0 - reach - Terrain.X0) / Terrain.CELL), i1 = (int) Math.ceil((x1 + reach - Terrain.X0) / Terrain.CELL);
        int j0 = (int) Math.floor((z0 - reach - Terrain.Z0) / Terrain.CELL), j1 = (int) Math.ceil((z1 + reach - Terrain.Z0) / Terrain.CELL);
        double tan = Math.tan(Math.toRadians(UFER)), moved = 0;
        int changed = 0;
        for (int j = Math.max(0, j0); j <= Math.min(Terrain.N - 1, j1); j++)
            for (int i = Math.max(0, i0); i <= Math.min(Terrain.N - 1, i1); i++) {
                int c = j * Terrain.N + i;
                if (t.art[c] == Terrain.FJORD) continue;
                double[] nb = e.nearest(Terrain.cx(i), Terrain.cz(j));
                int k = (int) nb[0];
                double d = nb[1], hw = e.half[k];
                if (d > hw + 30) continue;
                double depth = e.stufe(k) != null ? TIEFE(true) : TIEFE(false);
                double target = d <= hw ? e.level[k] - 0.2 - depth * (1 - (d / hw) * (d / hw)) : e.level[k] + 0.3 + (d - hw) * tan;
                double orig = Terrain.decode(t.h[c]);
                if (target < orig - 0.05) {
                    moved += (orig - target) * Terrain.CELL * Terrain.CELL;
                    changed++;
                    t.h[c] = Terrain.encode(target);
                }
            }
        double bx0 = Terrain.X0 + Math.max(0, i0) * Terrain.CELL, bx1 = Terrain.X0 + (Math.min(Terrain.N - 1, i1) + 1) * Terrain.CELL;
        double bz0 = Terrain.Z0 + Math.max(0, j0) * Terrain.CELL, bz1 = Terrain.Z0 + (Math.min(Terrain.N - 1, j1) + 1) * Terrain.CELL;
        t.formDaten.add(new Form("Bett der Geirangelva", "FLUSSBETT", new double[][]{{bx0, bz0}, {bx1, bz0}, {bx1, bz1}, {bx0, bz1}}, Double.NaN, Double.NaN, changed, Terrain.CELL, moved));
        t.formen.add(String.format(Locale.GERMAN, "Bett der Geirangelva: %d Zellen, %.2f Mio. m³ abgetragen", changed, moved / 1e6));
    }

    private static double TIEFE(boolean stufe) { return stufe ? Elva.TIEFE_STUFE : Elva.TIEFE; }

    /** Punkt der Kante über der geschärften Wand eines Falls (x, y, z, Richtung bergab) oder null. */
    public static double[] kante(Terrain t, String fall) { return t.punkte.get("Wand " + fall); }

    /**
     * Schärft die Wand unter der Kante eines Falls. Gesucht wird die Kante auf dem ungeformten Gelände
     * ({@link Faelle#kante}); die Front steht dort, wo das alte Gelände die halbe Stufe hinab ist.
     */
    static void wandschnitt(Terrain t, Schnitt sc) {
        Faelle.Def d = sc.fall();
        Faelle.Kante k = Faelle.kante(t::height, d.x(), d.z(), d.hoehe());
        if (k == null) return;
        double h = Math.toRadians(k.heading()), dx = Math.sin(h), dz = -Math.cos(h), ax = Math.cos(h), az = Math.sin(h);
        double H = sc.stufe(), half = d.breite() / 2, yTop = k.y(), yBot = yTop - H;
        // Front: wo das alte Gelände die halbe Stufe hinab ist
        double front = -1, ende = -1;
        for (double s = 0; s < 800; s += 1) {
            double y = t.height(k.x() + dx * s, k.z() + dz * s);
            if (front < 0 && y <= yTop - H / 2) front = s;
            if (front >= 0 && y <= yBot - (s - front) * Math.tan(Math.toRadians(HALDE))) { ende = s; break; }
        }
        if (front < 0) return;
        if (ende < 0) ende = front + 300;
        double tan = Math.tan(Math.toRadians(HALDE)), face = Terrain.CELL * 0.5;
        // betroffene Zellen: Rechteck um Kante, Front und Halde, quer mit Auslauf
        double reach = half + AUSLAUF;
        double[][] corners = {{-20, -reach}, {-20, reach}, {ende + 30, -reach}, {ende + 30, reach}};
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (double[] c : corners) {
            double x = k.x() + dx * c[0] + ax * c[1], z = k.z() + dz * c[0] + az * c[1];
            x0 = Math.min(x0, x); x1 = Math.max(x1, x); z0 = Math.min(z0, z); z1 = Math.max(z1, z);
        }
        int i0 = (int) Math.floor((x0 - Terrain.X0) / Terrain.CELL), i1 = (int) Math.ceil((x1 - Terrain.X0) / Terrain.CELL);
        int j0 = (int) Math.floor((z0 - Terrain.Z0) / Terrain.CELL), j1 = (int) Math.ceil((z1 - Terrain.Z0) / Terrain.CELL);
        int changed = 0;
        double moved = 0;
        for (int j = Math.max(0, j0); j <= Math.min(Terrain.N - 1, j1); j++)
            for (int i = Math.max(0, i0); i <= Math.min(Terrain.N - 1, i1); i++) {
                int c = j * Terrain.N + i;
                if (t.art[c] == Terrain.FJORD) continue;
                double x = Terrain.cx(i) - k.x(), z = Terrain.cz(j) - k.z();
                double s = x * dx + z * dz, u = x * ax + z * az;
                double wu = 1 - smooth(half, half + AUSLAUF, Math.abs(u));
                if (wu <= 0 || s < -20 || s > ende + 30) continue;
                double orig = Terrain.decode(t.h[c]), target;
                if (s < 0) target = orig;
                else if (s < front) target = Math.max(orig, yTop - ABSATZ * s / front);
                else if (s < front + face) target = Math.min(orig, yTop - ABSATZ);
                else target = Math.min(orig, yBot - (s - front - face) * tan);
                double ws = smooth(-20, 0, s) * (1 - smooth(ende, ende + 30, s));
                double y = orig + wu * ws * (target - orig);
                if (Math.abs(y - orig) > 0.05) {
                    moved += Math.abs(y - orig) * Terrain.CELL * Terrain.CELL;
                    changed++;
                    t.h[c] = Terrain.encode(Math.max(Terrain.WASSER + 0.5, y));
                }
            }
        double[][] fl = new double[4][];
        double[][] ec = {{-20, -reach}, {ende + 30, -reach}, {ende + 30, reach}, {-20, reach}};
        for (int q = 0; q < 4; q++) fl[q] = new double[]{k.x() + dx * ec[q][0] + ax * ec[q][1], k.z() + dz * ec[q][0] + az * ec[q][1]};
        t.formDaten.add(new Form("Wand unter " + d.name(), "WAND", fl, H, front, changed, Terrain.CELL, moved));
        t.punkte.put("Wand " + d.name(), new double[]{k.x() + dx * (front - 1), yTop - ABSATZ, k.z() + dz * (front - 1), k.heading()});
        t.formen.add(String.format(Locale.GERMAN, "%s: Stufe %.0f m, Front %.0f m vor der Kante, %d Zellen, %.1f Mio. m³ bewegt",
                d.name(), H, front, changed, moved / 1e6));
    }

    static double smooth(double a, double b, double x) {
        double v = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return v * v * (3 - 2 * v);
    }

    /** „Formen: Sieben Schwestern: Stufe 250 m …; Friaren: …“ */
    public static String describe(Terrain t) {
        return t.formen.isEmpty() ? "Formen: keine (Rohdaten)" : "Formen: " + String.join("; ", t.formen);
    }
}
