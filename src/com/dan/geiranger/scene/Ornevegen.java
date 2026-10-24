package com.dan.geiranger.scene;

import com.dan.road.Ground;
import com.dan.road.Network;
import com.dan.road.Way;
import com.dan.road.WayType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Phase 9: die Fv63 (seit Phase 11 vom Storfossen durch den Ort, {@link #DORF}) am Ufer entlang nach Grande und als Ørnevegen in elf Kehren hinauf, über die Plattform der
 * Ørnesvingen in der elften Kehre weiter nach Norden bis an den Rand von Kern Ost.
 * <p>
 * <b>Achse (F14 A, nach der Karte)</b>: die Karte {@code templates/Karte-Geirangerfjord.png} ist an der Uferlinie des
 * Geländes eingepasst (11,57 m je Bildpunkt, 5,19° gedreht, wie UTM 33: 98,3 % der Bildpunkte stimmen in Wasser und
 * Land). Die Kehren liegen dort zwischen Grande und Møllsæter in einem Streifen, dessen West- und Ostrand die Karte
 * zeigt; die Höhenlinien des Geländes stimmen mit denen der Karte. Die Achse ist aus dem Gelände gezogen
 * ({@link #ACHSE}): jeder Schenkel läuft mit 9 % entlang der Höhenlinien vom einen Rand zum anderen (30 m außerhalb der
 * Strichmitte), dort wendet er in einer Kehre als Halbkreis mit {@link #RADIUS} m; die Schenkel sind über 45 m
 * gemittelt. So ergeben sich ohne Vorgabe elf Kehren, die den Kehren der Karte folgen; die elfte liegt 45 m neben der
 * westlichsten Kehre der Karte unter dem Gjerdefossen. Dort steht die Plattform (rund 530 m; 620 m ist Korsmyra, der
 * höchste Punkt der Straße [Quelle: dangerousroads.org]).
 * <p>
 * <b>Querschnitt</b> [G, nach Fotos]: zwei Streifen zu 2,75 m, Bankett 0,3 m, gelbe Mittellinie, Leitpfosten,
 * Schneestangen, Schutzplanken; Tempo 50 km/h, in den Kehren aus der Krümmung (rund 20 km/h).
 */
public final class Ornevegen {
    /** Zentrumsradius der Kehren (m) [G: Busse über 12 m wenden in den Kehren über beide Streifen]. */
    public static final double RADIUS = 13;
    /** Achse der Kehren (Ressource {@code ornevegen_achse.txt}, UTM): Stützpunkte und Mitten der Kehren. */
    public static final String ACHSE = "ornevegen_achse.txt";
    /**
     * Phase 11: die Fv63 durch den Ort (UTM, von Süden): vom Fuß des Storfossen am Ostrand des Talbodens hinab, östlich um
     * das Hafenbecken am Anleger und am Ufer nach Norden, wo {@link #UFER} beginnt [nach der Karte und dem Gelände].
     */
    public static final double[][] DORF = {
            {406_385, 6_886_385}, {406_368, 6_886_430}, {406_355, 6_886_490}, {406_348, 6_886_560}, {406_355, 6_886_630},
            {406_368, 6_886_690}, {406_375, 6_886_745}, {406_372, 6_886_795}, {406_366, 6_886_845}, {406_340, 6_886_885},
            {406_305, 6_886_925}};
    /** Phase 11: der Strandvegen von der Fv63 hinab zum Campingplatz am Delta (UTM). */
    public static final double[][] STRANDVEGEN = {
            {406_363, 6_886_697}, {406_340, 6_886_680}, {406_310, 6_886_650}, {406_285, 6_886_610}, {406_275, 6_886_565}};

    /** Vom Ort am Ufer nach Grande bis vor die erste Kehre (UTM; auf 4 bis 14 m über NN gesetzt, wo das Raster das Ufer zeigt). */
    public static final double[][] UFER = {
            {406_266, 6_886_970}, {406_222, 6_887_028}, {406_030, 6_887_330}, {405_808, 6_887_546}, {405_742, 6_887_798},
            {405_588, 6_888_161}, {405_418, 6_888_312}};
    /** Nach der elften Kehre: über Møllsæter nach Norden bis an den Rand von Kern Ost (UTM). */
    public static final double[][] OBEN = {
            {404_750, 6_889_570}, {404_900, 6_889_660}, {405_020, 6_889_700}, {405_100, 6_889_950}, {405_200, 6_890_300}};

    public final Network net;
    public final Way way;
    /** Phase 11: der Strandvegen im Ort (schmal, ohne Mittellinie). */
    public final Way strandvegen;
    /** Die Kehren auf der Achse: Anfang und Ende (m ab dem Ort), Höhe der Mitte, Nummer 1 bis 11. */
    public record Kehre(int nr, double s0, double s1, double y, double x, double z) { }
    public final List<Kehre> kehren;
    /** Plattform in der elften Kehre: Mitte des Decks x, Höhe der Fahrbahn am Scheitel, z, Richtung nach außen (Grad). */
    public final double[] plattform;

    private Ornevegen(Network net, Way way, Way strand, List<Kehre> k, double[] p) {
        this.net = net; this.way = way; this.strandvegen = strand; this.kehren = k; this.plattform = p;
    }

    /** Phase 11: Querschnitt des Strandvegen (Ortsstraße: 2 × 2,4 m, ohne Markierung, Planken und Schneestangen, 30 km/h). */
    public static WayType typDorf() {
        WayType t = WayType.road();
        t.name = "Strandvegen";
        t.laneWidth = 2.4f;
        t.shoulder = 0.2f;
        t.verge = 0.6f;
        t.marking = WayType.Marking.NONE;
        t.guardrail = false;
        t.snowPoles = false;
        t.signs = false;
        t.posts = false;
        t.speedLimit = 8.3f;
        t.designSpeed = 8f;
        t.surface = new float[]{0.06f, 0.06f, 0.062f};
        return t;
    }

    static double[] szene(double[][] utm) {
        double[] xz = new double[utm.length * 2];
        for (int i = 0; i < utm.length; i++) {
            double[] p = Site.fromUtm(utm[i][0], utm[i][1]);
            xz[2 * i] = p[0];
            xz[2 * i + 1] = p[1];
        }
        return xz;
    }

    /** Querschnitt der Fv63 am Ørnevegen. */
    public static WayType typ() {
        WayType t = WayType.road();
        t.name = "Fv63 Ørnevegen";
        t.laneWidth = 2.75f;
        t.shoulder = 0.3f;
        t.verge = 1.0f;
        t.maxGrade = 0.11f;
        t.smoothing = 40f;
        t.maxBank = 0.05f;
        t.designSpeed = 12f;
        t.speedLimit = 13.9f;
        t.guardrail = true;
        t.clearance = 2f;
        t.surface = new float[]{0.05f, 0.05f, 0.052f};
        return t;
    }

    /** Achse in UTM (E, N): Ufer, die Kehren aus der Ressource, oben hinaus. */
    public static List<double[]> achse() {
        List<double[]> out = new ArrayList<>();
        for (double[] p : DORF) out.add(new double[]{p[0], p[1]});
        for (double[] p : UFER) out.add(new double[]{p[0], p[1]});
        for (String[] r : zeilen()) if (!r[0].equals("K")) out.add(new double[]{Double.parseDouble(r[0]), Double.parseDouble(r[1])});
        for (double[] p : OBEN) out.add(new double[]{p[0], p[1]});
        return out;
    }

    /** Mitten der Kehren (UTM) von unten nach oben. */
    public static List<double[]> mitten() {
        List<double[]> out = new ArrayList<>();
        for (String[] r : zeilen()) if (r[0].equals("K")) out.add(new double[]{Double.parseDouble(r[1]), Double.parseDouble(r[2])});
        return out;
    }

    static List<String[]> zeilen() {
        List<String[]> out = new ArrayList<>();
        try (java.io.InputStream in = Ornevegen.class.getResourceAsStream(ACHSE)) {
            if (in == null) throw new IllegalStateException(ACHSE + " fehlt");
            for (String l : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                l = l.trim();
                if (l.isEmpty() || l.startsWith("#")) continue;
                out.add(l.split("\\s+"));
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    /** Die Straße auf dem Gelände (vor dem Feinraster); Grund am Ufer nie unter NN + 1,5 m. Ohne Höhendaten null. */
    public static Ornevegen build(Terrain t) {
        if (!t.measured()) return null;
        List<double[]> a = achse();
        double[] xz = new double[a.size() * 2];
        int k = 0;
        double[] prev = null;
        for (double[] u : a) {
            double[] p = Site.fromUtm(u[0], u[1]);
            if (prev != null && Math.hypot(p[0] - prev[0], p[1] - prev[1]) < 1.0) continue;
            xz[k++] = p[0];
            xz[k++] = p[1];
            prev = p;
        }
        xz = java.util.Arrays.copyOf(xz, k);
        Ground g = new Ground() {
            @Override public float height(double x, double z) { return (float) Math.max(t.height(x, z), Terrain.WASSER + 1.0); }
            @Override public float water(double x, double z) { return Float.NaN; }
        };
        Network net = new Network();
        net.reach = 18;
        net.shelf = 1.2f;
        net.cutSlope = 1.2f;   // Fels: steile Einschnitte
        net.fillSlope = 0.7f;
        Way w = net.add(new Way(typ(), false, xz));
        Way sv = net.add(new Way(typDorf(), false, szene(STRANDVEGEN)));
        net.build(g);
        // die Kehren auf der Achse: die Proben, die näher als RADIUS + 5 m an der Mitte einer Kehre liegen
        List<Kehre> ks = new ArrayList<>();
        int nr = 0;
        for (double[] mu : mitten()) {
            double[] c = Site.fromUtm(mu[0], mu[1]);
            int i0 = -1, i1 = -1;
            for (int i = 0; i < w.n; i++)
                if (Math.hypot(w.x[i] - c[0], w.z[i] - c[1]) < RADIUS + 5) { if (i0 < 0) i0 = i; i1 = i; }
            if (i0 < 0) continue;
            // Scheitel: die Probe, die am weitesten von der Mündung der Kehre (Mitte zwischen Ein- und Ausfahrt) liegt
            double mx = (w.x[i0] + w.x[i1]) / 2, mz = (w.z[i0] + w.z[i1]) / 2, bd = -1;
            int m = (i0 + i1) / 2;
            for (int i = i0; i <= i1; i++) {
                double d = Math.hypot(w.x[i] - mx, w.z[i] - mz);
                if (d > bd) { bd = d; m = i; }
            }
            ks.add(new Kehre(++nr, i0 * w.step(), i1 * w.step(), w.y[m], w.x[m], w.z[m]));
        }
        // Plattform: außen an der elften Kehre (am Westrand: außen ist Westen), 10 m vor der Mitte der Fahrbahn
        double[] pl = null;
        List<double[]> mm = mitten();
        if (!mm.isEmpty()) {
            double[] mu = mm.get(mm.size() - 1);
            double[] apex = Site.fromUtm(mu[0] - RADIUS, mu[1]), pp = Site.fromUtm(mu[0] - RADIUS - 10, mu[1]);
            Network.Hit hit = new Network.Hit();
            double y = net.locate(apex[0], apex[1], hit) ? hit.deck : g.height(apex[0], apex[1]);
            pl = new double[]{pp[0], y, pp[1], 270};
        }
        return new Ornevegen(net, w, sv, Collections.unmodifiableList(ks), pl);
    }

    /** „Fv63 Ørnevegen: 7,4 km, 11 Kehren, von 4 bis 590 m, Steigung bis 10,5 %“ */
    public String describe() {
        double maxG = 0, lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int i = 1; i < way.n; i++) maxG = Math.max(maxG, Math.abs(way.y[i] - way.y[i - 1]) / way.step());
        for (int i = 0; i < way.n; i++) { lo = Math.min(lo, way.y[i]); hi = Math.max(hi, way.y[i]); }
        return String.format(Locale.GERMAN, "%s: %.1f km, %d Kehren, von %.0f bis %.0f m, Steigung bis %.1f %%, Plattform auf %.0f m",
                way.type.name, way.length / 1000, kehren.size(), lo, hi, maxG * 100, plattform == null ? 0 : plattform[1]);
    }
}
