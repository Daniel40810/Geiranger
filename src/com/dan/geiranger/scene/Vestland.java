package com.dan.geiranger.scene;

import com.dan.forest.Species;
import com.dan.ground.Biome;
import com.dan.ground.Plant;

import java.util.List;
import java.util.Locale;
import java.util.function.DoubleBinaryOperator;

/**
 * Phase 10: Wald und Bodendecke am Geirangerfjord, Biom „Vestland“ (F10, F16 A) [G, nach Fotos und Beschreibungen
 * westnorwegischer Fjordhänge].
 * <p>
 * <b>Bäume</b> ({@code com.dan.forest}, {@link #ARTEN}): Birke herrscht vor (Bjørk, an den Hängen bis zur Waldgrenze),
 * Erle unten am Ufer und an der Elva (die Grauerle des Fjords; {@code forest} kennt die Schwarzerle, sie steht für
 * beide), Waldkiefer auf trockenen, felsigen Absätzen, Fichte in einzelnen gepflanzten Beständen unter 450 m. Die
 * Waldgrenze liegt bei rund {@link #WALDGRENZE} m und schwankt um ± 50 m; darüber Tundra.
 * <p>
 * <b>Wo kein Wald steht</b>: Fjord und Ufer unter 1,5 m, Wände (Wandkarte von {@code cliff} über 0,25, Neigung über
 * 34° dünner, über 44° nichts), die Straße mit Einschnitt und Damm, die Elva, die Hofwiesen ({@link Hoefe}), die
 * Plattform; im Ort nur ein Viertel (Gärten; Phase 11). Lichtungen aus Rauschen machen den Bestand fleckig.
 * <p>
 * <b>Bodendecke</b> ({@code com.dan.ground}, {@link #biom}): Wiesenrispe, Segge in feuchten Senken, Adlerfarn,
 * Glockenblume, Hahnenfuß, Weißklee, Schafgarbe, Steine; unter dem Wald wenig Gras, Laubstreu und Erde; über der
 * Waldgrenze mehr Stein.
 */
public final class Vestland {
    /** Waldgrenze (m) [G: Birke am Geirangerfjord rund 700 bis 800 m]. */
    public static final double WALDGRENZE = 760;
    /**
     * Grenzen des Bestands. Nacharbeit 10 (nach den Fotos Geiranger1 bis 3: die Hänge sind bis an die Wände grün): Wald
     * bis 40° voll, über 56° keiner (Phase 10: 34° und 44°); Wandkarte ab 0,35 dünner, ab 0,8 nichts (0,25 und 0,6);
     * Lichtungen seltener (0,62 + 0,6 · Rauschen statt 0,45 + 0,75 ·).
     */
    public static final double[] STEIL = {40, 56}, WAND = {0.35, 0.8}, LICHTUNG = {0.62, 0.6};
    /** Arten in dieser Reihenfolge: Birke, Erle, Waldkiefer, Fichte. */
    public static final int BIRKE = 0, ERLE = 1, KIEFER = 2, FICHTE = 3;

    /** Die Arten für {@code ForestGl}, an den Fjord angepasst (Birken niedriger und schlanker als im Tiefland). */
    public static List<Species> arten() {
        Species b = Species.birch(), e = Species.alder(), k = Species.scotsPine(), f = Species.spruce();
        // Nacharbeit 10: das Laub wie auf den Fotos Geiranger1 bis 3 (Juni: sattes, dunkles Grün, nicht das helle
        // Maigrün), Austrieb zwei bis drei Wochen später als im Tiefland
        b.spring = new float[]{0.075f, 0.18f, 0.035f};
        b.summer = new float[]{0.05f, 0.135f, 0.028f};
        b.leafOut = 135;
        // Nacharbeit 14 (Status_Phase14a): kahle Birken zeigten im April helle, sandfarbene Kronen (die weiße Rinde
        // auf allen Ästen); Zweige und obere Äste der Birke sind dunkel rotbraun, nur der Stamm ist weiß [G]
        b.barkTop = new float[]{0.13f, 0.075f, 0.065f};
        b.barkTopFrom = 0.42f;
        // Herbst: Gold bis Ocker mit grünen Resten wie an den Hängen im Oktober, nicht das reine Gelb des Tieflands
        b.autumn = new float[][]{{0.46f, 0.34f, 0.04f}, {0.38f, 0.30f, 0.05f}, {0.30f, 0.26f, 0.06f}, {0.44f, 0.24f, 0.04f}};
        e.spring = new float[]{0.05f, 0.13f, 0.03f};
        e.leafOut = 130;
        f.shoots = new float[]{0.06f, 0.13f, 0.045f};
        k.shoots = new float[]{0.06f, 0.12f, 0.06f};
        b.heightMin = 7; b.heightMax = 14;
        e.heightMin = 6; e.heightMax = 12;
        k.heightMin = 6; k.heightMax = 13;
        f.heightMin = 12; f.heightMax = 22;
        return List.of(b, e, k, f);
    }

    public static Biome biom() {
        return new Biome("Vestland · Fjordhang")
                .add(Plant.meadowGrass(), 0, 8.0f, 0.0f)
                .add(Plant.sedge(), 0, 2.5f, 1.0f)
                .add(Plant.bracken(), 0, 1.2f, -0.2f)
                .add(Plant.harebell(), 1, 0.8f, -0.2f)
                .add(Plant.buttercup(), 1, 0.8f, 0.3f)
                .add(Plant.whiteClover(), 1, 1.4f, 0.2f)
                .add(Plant.yarrow(), 1, 0.5f, -0.3f)
                .add(Plant.pebble(), 2, 3.0f, 0.0f)
                .add(Plant.stone(), 2, 0.6f, 0.0f)
                .add(Plant.boulder(), 2, 0.03f, 0.0f)
                .add(Plant.litter(), 3, 0.5f, 0.0f)
                .add(Plant.soil(), 3, 0.06f, 0.0f);
    }

    private final Terrain t;
    private final double[][] wiesen;
    private final double[] deck;
    /** Wandanteil der Wandkarte von cliff an (x, z) 0..1 (gesetzt, sobald die Karte fertig ist), sonst null. */
    public volatile DoubleBinaryOperator wand;
    private static final ThreadLocal<double[]> LAST = ThreadLocal.withInitial(() -> new double[]{Double.NaN, Double.NaN, 0, 0, 0, 0});

    private final Dorf dorf;

    public Vestland(Terrain t, Hoefe h) { this(t, h, null); }

    /** Seit Phase 11 mit dem Ort: keine Bäume auf den Bauten, dem Anleger und dem Campingplatz. */
    public Vestland(Terrain t, Hoefe h, Dorf d) {
        this.t = t;
        this.wiesen = h == null ? new double[0][] : h.wiesen();
        this.deck = Plattform.rechteck(t.ornevegen);
        this.dorf = d;
    }

    static double smooth(double a, double b, double x) {
        double v = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return v * v * (3 - 2 * v);
    }

    /** Wertrauschen 0..1 (Gitter 1, glatt), reproduzierbar. */
    static double noise(double x, double z, int seed) {
        double fx = Math.floor(x), fz = Math.floor(z), u = x - fx, v = z - fz;
        u = u * u * (3 - 2 * u);
        v = v * v * (3 - 2 * v);
        int i = (int) fx, j = (int) fz;
        double a = hash(i, j, seed), b = hash(i + 1, j, seed), c = hash(i, j + 1, seed), d = hash(i + 1, j + 1, seed);
        return (a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v;
    }

    static double hash(int i, int j, int s) {
        int h = i * 374761393 + j * 668265263 + s * 982451653;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    /** Neigung (Grad) über ± r m. */
    double neigung(double x, double z, double r) {
        double gx = (t.height(x + r, z) - t.height(x - r, z)) / (2 * r), gz = (t.height(x, z + r) - t.height(x, z - r)) / (2 * r);
        return Math.toDegrees(Math.atan(Math.hypot(gx, gz)));
    }

    /** Wie dicht Wald an (x, z) stehen kann (0..1), ohne Rücksicht auf die Art. */
    public double wald(double x, double z) {
        if (t.fjord(x, z)) return 0;
        double h = t.height(x, z);
        if (h < Terrain.WASSER + 1.5) return 0;
        double grenze = WALDGRENZE + 100 * (noise(x / 180, z / 180, 3) - 0.5);
        double d = 1 - smooth(grenze - 80, grenze + 30, h);
        if (d <= 0) return 0;
        double s = neigung(x, z, 6);
        d *= 1 - smooth(STEIL[0], STEIL[1], s);
        if (d <= 0) return 0;
        DoubleBinaryOperator w = wand;
        if (w != null) d *= 1 - smooth(WAND[0], WAND[1], w.applyAsDouble(x, z));
        if (d <= 0) return 0;
        // Straße (Fahrbahn, Bankett, Einschnitt) und Feinraster
        Ornevegen o = t.ornevegen;
        if (o != null) {
            com.dan.road.Network.Hit hit = HIT.get();
            if (o.net.locate(x, z, hit) && Math.abs(hit.u) < o.way.type.half() + 3) return 0;
            if (Math.abs(t.feinAt(x, z)) > 0.4) d *= 0.1;
        }
        Elva e = t.elva;
        if (e != null) {
            double[] nb = e.nearest(x, z);
            if (nb[1] < e.half[(int) nb[0]] + 2.5) return 0;
        }
        for (double[] wi : wiesen) {
            double r = Math.hypot(x - wi[0], z - wi[1]);
            d *= smooth(wi[2], wi[2] + 25, r);
        }
        if (Plattform.auf(deck, x, z, 4)) return 0;
        // der Ort: Gärten statt Wald, seit Phase 11 keine Bäume auf den Bauten und dem Campingplatz
        double ra = Math.hypot(x - Site.ANLEGER.x(), z - Site.ANLEGER.z());
        if (ra < 900 && h < 60) d *= 0.25 + 0.75 * smooth(600, 900, ra);
        if (dorf != null && ra < 700 && (dorf.bebaut(x, z, 3) || dorf.camping(x, z))) return 0;
        // Lichtungen
        d *= Math.max(0, Math.min(1, LICHTUNG[0] + LICHTUNG[1] * noise(x / 45, z / 45, 7)));
        return d;
    }

    private static final ThreadLocal<com.dan.road.Network.Hit> HIT = ThreadLocal.withInitial(com.dan.road.Network.Hit::new);

    /** Dichte je Art (für {@code ForestField.Density}); die vier Arten an derselben Stelle werden einmal gerechnet. */
    public double dichte(double x, double z, int art) {
        double[] c = LAST.get();
        if (c[0] != x || c[1] != z) {
            c[0] = x;
            c[1] = z;
            double d = wald(x, z);
            if (d <= 0) { c[2] = c[3] = c[4] = c[5] = 0; }
            else {
                double h = t.height(x, z), s = neigung(x, z, 6);
                double erle = 0.12 + 0.6 * (1 - smooth(8, 110, h));
                Elva e = t.elva;
                if (e != null && h < 250) {
                    double[] nb = e.nearest(x, z);
                    erle += 0.6 * (1 - smooth(10, 45, nb[1]));
                }
                double kiefer = 0.04 + 0.7 * smooth(22, 36, s) + 0.1 * smooth(350, 650, h);
                double fichte = h < 450 && noise(x / 260, z / 260, 11) > 0.7 ? 1.2 : 0.01;
                double birke = 1;
                double sum = birke + erle + kiefer + fichte;
                c[2] = d * birke / sum;
                c[3] = d * erle / sum;
                c[4] = d * kiefer / sum;
                c[5] = d * fichte / sum;
            }
        }
        return c[2 + art];
    }

    /** Bodendecke für {@code ground.Site}: Gras, Blumen, Steine, Erde, Feuchte (je 0..1). */
    public void decke(double x, double z, float[] o) {
        if (t.fjord(x, z)) { o[0] = o[1] = o[2] = o[3] = o[4] = 0; return; }
        double h = t.height(x, z), s = neigung(x, z, 3);
        double offen = 1 - smooth(30, 45, s);
        DoubleBinaryOperator w = wand;
        if (w != null) offen *= 1 - smooth(0.2, 0.5, w.applyAsDouble(x, z));
        Ornevegen or = t.ornevegen;
        if (or != null) {
            com.dan.road.Network.Hit hit = HIT.get();
            if (or.net.locate(x, z, hit) && Math.abs(hit.u) < or.way.type.paved() + 0.3) offen = 0;
        }
        if (Plattform.auf(deck, x, z, 0.5)) offen = 0;
        if (dorf != null && dorf.bebaut(x, z, 0.3)) offen = 0;
        double wald = wald(x, z), hoch = smooth(WALDGRENZE - 50, WALDGRENZE + 150, h);
        o[0] = (float) (offen * (0.9 - 0.6 * wald - 0.4 * hoch));
        o[1] = (float) (offen * (0.35 - 0.3 * wald) * (1 - 0.6 * hoch));
        o[2] = (float) (Math.min(1, offen * (0.1 + 0.5 * hoch) + (1 - offen) * 0.4));
        o[3] = (float) (offen * 0.35 * wald);
        o[4] = (float) (0.4 + 0.5 * (1 - smooth(5, 60, h)) + 0.3 * noise(x / 25, z / 25, 5) - 0.15);
    }

    /** „Vestland: Birke, Erle, Waldkiefer, Fichte · Waldgrenze 760 m“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Vestland: Birke, Erle, Waldkiefer, Fichte · Waldgrenze %.0f m ± 50 m%s", WALDGRENZE,
                wand == null ? " · Wandkarte fehlt noch" : "");
    }
}
