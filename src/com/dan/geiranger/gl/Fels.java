package com.dan.geiranger.gl;

import com.dan.cliff.Cliff;
import com.dan.cliff.CliffMap;
import com.dan.cliff.Overhang;
import com.dan.cliff.gl.CliffGl;
import com.dan.fall.Fall;
import com.dan.geiranger.scene.Faelle;
import com.dan.geiranger.scene.Site;
import com.dan.geiranger.scene.Terrain;
import com.jogamp.opengl.GL4;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 5: die Wände aus {@code com.dan.cliff} in der Szene. Die Wandkarte entsteht im Hintergrund aus dem Hauptraster
 * (2048² zu 12 m). Seit Phase 7 rechnet derselbe Hintergrund danach die Wasserfälle ({@link Faelle}) auf der Wand mit
 * ihrer Feinform ({@link #wand}), malt ihre nassen Linien in die Karte ({@code CliffMap.paintWet}, vorher Streifen nach
 * Lagen aus {@link Site}) und setzt die Überhänge an die Kanten der Hauptfälle mit Überhang (Sieben Schwestern,
 * Friaren): die Nase steht knapp unter der Kante, das Wasser fällt vor ihr frei. Gelände ({@code terrain.vert},
 * {@code terrain.frag}) bindet {@code cliff.glsl}; die Überhänge zeichnet {@link CliffGl}.
 */
public final class Fels {
    public final Cliff cliff = new Cliff();
    final CliffGl gl = new CliffGl();
    private final Terrain terrain;
    private volatile CliffMap map;
    private volatile List<Overhang> overhangs = List.of();
    private volatile List<Faelle.Eintrag> faelle;
    /** Stufe, mit der die Fälle beim Bau gerechnet werden. */
    public volatile Fall.Quality fallQuality = Fall.Quality.MEDIUM;
    private boolean uploaded;
    boolean ok;
    String error = "", check = "";
    private volatile long buildMs, fallMs;

    public Fels(Terrain t) {
        this.terrain = t;
        cliff.seed = 62;
        // Phase 8a: der Gneis am Geirangerfjord ist dunkler als die Grundwerte von cliff (Flechten, Nässe, Ruß der
        // Rinnen); mit ihnen war die sonnige Wand der Schwestern heller als der Himmel (sRGB 180 gegen 148). Auf 70 % [G, Fotos]
        cliff.gneissLight = new double[]{0.245, 0.24, 0.23};
        cliff.gneissDark = new double[]{0.125, 0.125, 0.135};
        cliff.feldspar = new double[]{0.31, 0.25, 0.22};
        cliff.lichen = new double[]{0.19, 0.21, 0.15};
        cliff.talus = new double[]{0.22, 0.22, 0.205};
        if (t.measured()) {
            Thread th = new Thread(this::build, "Wandkarte");
            th.setDaemon(true);
            th.start();
        }
    }

    /**
     * Die Wand für die Fälle: das Gelände, an der Wand so weit nach außen gehoben, wie die Feinform höchstens vorsteht
     * ({@code cliffDisplace} schiebt um bis zu {@link #RIPPE} m entlang der Normale, als Höhe / cos θ, höchstens 6 m),
     * damit das Wasser nicht in den Rippen der Wand steckt. Glatt, ohne das Rauschen selbst: in den Mulden des Rauschens
     * bliebe das Wasser liegen.
     */
    public double wand(double x, double z) { return wand(map, x, z); }

    private double wand(CliffMap m, double x, double z) {
        double h = terrain.height(x, z);
        if (m == null) return h;
        double wall = m.valuesAt(x, z)[0];
        if (wall <= 0.01) return h;
        double e = 3, gx = (terrain.height(x + e, z) - terrain.height(x - e, z)) / (2 * e), gz = (terrain.height(x, z + e) - terrain.height(x, z - e)) / (2 * e);
        double ny = 1 / Math.sqrt(gx * gx + gz * gz + 1);
        return h + Math.min(6, RIPPE * wall / Math.max(0.3, ny));
    }

    /** Wie weit die Feinform der Wand höchstens vorsteht (m): die halbe Höhe des Reliefs über die Oktaven (4 m: 2 + 0,8 + 0,3). */
    public static final double RIPPE = 3;

    /** Wandkarte, Fälle, nasse Linien, Überhänge (im Hintergrund, rund zwei Sekunden). */
    public void build() {
        long t0 = System.nanoTime();
        CliffMap m = CliffMap.build(cliff, terrain::height, Terrain.X0, Terrain.Z0, Terrain.CELL, Terrain.N);
        buildMs = (System.nanoTime() - t0) / 1_000_000;
        long t1 = System.nanoTime();
        Fall.Ground wnd = (x, z) -> wand(m, x, z);
        List<Faelle.Eintrag> fs = new ArrayList<>(Faelle.build(terrain, wnd, fallQuality));
        // Überhänge an den Kanten der Hauptfälle; das Wasser reißt dann an der Front der Nase ab
        List<Overhang> o = new ArrayList<>();
        int seed = 1;
        for (int i = 0; i < fs.size(); i++) {
            Faelle.Eintrag e = fs.get(i);
            if (!e.def.ueberhang()) continue;
            Overhang h = lip(terrain, e, seed++);
            o.add(h);
            Faelle.Eintrag n = Faelle.make(e.def, nose(h), terrain::height, wnd, fallQuality, true);
            if (n != null) fs.set(i, n);
        }
        for (Faelle.Eintrag e : fs) {
            double k = e.def.art() == Faelle.Art.REGEN ? 0.6 : 1;
            for (double[] p : e.fall.wetLine(6)) m.paintWet(p[0], p[1], p[2], k);
            m.falls++;
        }
        overhangs = o;
        map = m;
        faelle = List.copyOf(fs);
        fallMs = (System.nanoTime() - t1) / 1_000_000;
    }

    /**
     * Die Abrisskante eines Falls als Überhang: die Mitte der Nase auf der Wand 8 m unter der Kante (von der Kante
     * bergab gesucht), Blick bergab, 24 m hoch, 9 m Ausladung, so breit wie die Kante (mindestens 30 m).
     */
    public static Overhang lip(Terrain t, Faelle.Eintrag e, int seed) {
        Faelle.Kante k = e.kante;
        double h = Math.toRadians(k.heading()), dx = Math.sin(h), dz = -Math.cos(h), x = k.x(), z = k.z();
        for (int s = 0; s < 400 && t.height(x, z) > k.y() - 8; s++) { x += dx * 0.5; z += dz * 0.5; }
        // auf der Höhe k.y − 8 (an einer geschärften Wand fällt das Gelände in einem Schritt von 0,5 m um 20 m)
        return new Overhang(x, Math.max(k.y() - 8, t.height(x, z)), z, k.heading(), Math.max(30, e.def.breite() * 1.1), 24, 9, seed);
    }

    /** Wo das Wasser von einem Überhang abreißt: an seiner Front auf gut einem Drittel seiner Höhe über der Mitte (dort steht er am weitesten vor). */
    public static Faelle.Kante nose(Overhang o) {
        double h = Math.toRadians(o.headingDeg), out = o.protrusion(0, 0.35) - o.embed * 0.5;
        return new Faelle.Kante(o.x + Math.sin(h) * out, o.y + 0.35 * o.height / 2, o.z - Math.cos(h) * out, o.headingDeg);
    }

    /** Die Fälle, sobald der Hintergrund fertig ist (sonst null). */
    public List<Faelle.Eintrag> faelle() { return faelle; }

    public CliffMap map() { return map; }
    public List<Overhang> overhangs() { return overhangs; }

    void init(GL4 g) {
        try {
            gl.init(g);
            ok = true;
        } catch (RuntimeException e) {
            ok = false;
            error = e.getMessage() == null ? e.toString() : e.getMessage().split("\n")[0];
            System.err.println("Geiranger: cliff nicht gestartet – " + e.getMessage());
        }
    }

    /** Lädt die Wandkarte hoch, sobald sie fertig ist (im GL-Faden, einmal). */
    void update(GL4 g) {
        CliffMap m = map;
        if (!ok || uploaded || m == null || faelle == null) return;
        gl.upload(g, m);
        gl.setOverhangs(overhangs);
        uploaded = true;
    }

    boolean mapReady() { return uploaded; }

    /** „Fels: Cliff … · Wandkarte … (0,9 s) · 2 Überhänge · Grafikkarte = Prozessor …“ */
    String describe() {
        if (!ok) return "!Fels: com.dan.cliff nicht gestartet (" + error + "), Platzhalter aus Phase 2";
        CliffMap m = map;
        return String.format(Locale.GERMAN, "Fels: %s · %s · %d Überhänge%s", cliff.describe(),
                m == null || faelle == null ? "Wandkarte entsteht …" : m.describe() + String.format(Locale.GERMAN, " (%.1f s, Fälle %.1f s)", buildMs / 1000.0, fallMs / 1000.0), overhangs.size(),
                check.isEmpty() ? "" : " · " + check);
    }

    void dispose(GL4 g) { if (ok) gl.dispose(g); }
}
