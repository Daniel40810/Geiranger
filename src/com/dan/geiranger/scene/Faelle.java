package com.dan.geiranger.scene;

import com.dan.fall.Fall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Phase 7: die Wasserfälle an ihren Kanten (F12 A). Jeder Fall ist ein {@link Fall} aus {@code com.dan.fall} im Lauf
 * über Fels ({@link Fall#overRock}): Das 1″-Raster rundet die Wände auf 50 bis 75° ab, ein freier Fall von der Kante
 * landete gleich im Hang. So gleitet das Wasser über die Wand, springt ab, wo sie schneller wegfällt als die Wurfbahn,
 * und kommt im Fjord an.
 * <p>
 * <b>Lagen</b> [G, am Gelände gesetzt]: Der Fuß jedes Falls liegt am Ufer, die Kante dort, wo der Weg vom Fuß bergauf
 * (die höchste von 16 Richtungen in 30 m, mit Trägheit, wie {@code CliffMap.addFall}) die Höhe {@link Def#hoehe} über dem
 * Fuß erreicht; das Wasser fließt von dort bergab, die Kante steht quer dazu.
 * <ul>
 * <li><b>Sieben Schwestern</b>: an der steilsten Stelle des Nordufers unter Knivsflå ({@link Site#SCHWESTERN}, seit Phase 7,
 * vorher 300 m weiter nordöstlich): die Wand steigt dort in 120 m vom Wasser auf rund 300 m (im Raster bis 76°), darüber
 * mit 45° weiter. Kante 410 m über dem Wasser (die Höhe der Fälle), 90 m breit, sieben Strähnen.</li>
 * <li><b>Friaren</b>: gegenüber am Westufer der Halbinsel Skagen (Lage aus Phase 2), Kante auf 300 m über der steilen
 * Stufe (118 bis 367 m mit 60 bis 75°), darunter Platten bis ans Wasser; vier Strähnen, die sich unten auffächern wie auf
 * Waterfall2.</li>
 * <li><b>Brudesløret</b>, der Brautschleier: am Südufer westlich von Friaren, wo die Wand von 260 auf 580 m mit 60 bis
 * 77° steigt; Kante auf 560 m, breit und dünn, fünf Strähnen. Er liegt am Westrand von Kern West.</li>
 * <li><b>Regenfälle</b>: sechs Rinnsale an den steilsten Wänden von Kern West (aus dem Gelände gesucht, mindestens
 * 250 m von jedem anderen Fall), die nur nach Regen laufen.</li>
 * </ul>
 * Der Storsæterfossen (über Vinje, mit begehbarer Nische) kommt mit der Geirangelva in Phase 8.
 * <p>
 * <b>Abfluss</b> im Juni [G, nach Fotos und der Größe der Einzugsgebiete; zu prüfen an Pegeln der NVE]: die Schwestern
 * 2,5 m³/s zusammen, Friaren 1,5, Brudesløret 0,6, je Regenfall bis 0,15. Den Gang über das Jahr gibt
 * {@code wiring.Abfluss}.
 */
public final class Faelle {
    private Faelle() { }

    /** Haupt-, Neben- und Regenfälle. */
    public enum Art {
        HAUPT("im Detail"), NEBEN("am Rand der Fenster"), REGEN("nur nach Regen"), FLUSS("Stufe der Geirangelva");
        public final String label;
        Art(String l) { label = l; }
    }

    /**
     * Ein Fall: Name, Fuß am Ufer (UTM 32 N), Höhe der Kante über dem Fuß (m), Breite der Kante (m), Strähnen (0 = nach
     * Breite und Stufe), Abfluss im Juni (m³/s), Art, ob ein Überhang an der Kante steht (com.dan.cliff), Startwert,
     * Querfreiheit ({@code Fall.lateralFreedom}: klein hält die Strähnen getrennt, groß lässt sie in Rinnen zusammenlaufen).
     */
    public record Def(String name, double e, double n, double hoehe, double breite, int straehnen, double juni, Art art, boolean ueberhang, long seed, double quer) {
        public double x() { return e - Site.E0; }
        public double z() { return -(n - Site.N0); }
    }

    public static final Def SCHWESTERN = new Def("Sieben Schwestern", Site.SCHWESTERN.e(), Site.SCHWESTERN.n(), 410, 90, 7, 2.5, Art.HAUPT, true, 7, 0.15),
            FRIAREN = new Def("Friaren", Site.FRIAREN.e(), Site.FRIAREN.n(), 300, 30, 4, 1.5, Art.HAUPT, true, 11, 0.6),
            BRUDESLORET = new Def("Brudesløret", 400_180, 6_887_128, 560, 40, 5, 0.6, Art.NEBEN, false, 13, 0.3);
    /**
     * Phase 8: der Storsæterfossen über Vinje, an der steilsten Stufe des Bachs aus der Karte (E 407 640, N 6 887 140,
     * Fuß auf rund 505 m; Lage nach Karte und Abflussnetz ± 300 m) [G]: 30 m hoch, mit Nische (Überhang), hinter der man
     * gehen kann.
     */
    /** Gischt der Stufen in der Elva (Fall.sprayScale) [G, Status_Phase8b]. */
    public static final double SPRAY_FLUSS = 0.3;
    public static final Def STORSAETER = new Def("Storsæterfossen", 407_642, 6_887_142, 30, 10, 2, 0.8, Art.NEBEN, true, 17, 0.5);
    /** Die benannten Fälle (die Regenfälle kommen aus dem Gelände, {@link #regenfaelle}, die Stufen der Elva aus {@link Elva}). */
    public static final List<Def> BENANNT = List.of(SCHWESTERN, FRIAREN, BRUDESLORET, STORSAETER);
    /** Zahl, Abfluss (m³/s, nach starkem Regen) und Mindestabstand (m) der Regenfälle [G]. */
    public static final int REGEN_ZAHL = 6;
    public static final double REGEN_ABFLUSS = 0.15, REGEN_ABSTAND = 250;

    /** Die Kante eines Falls: Mitte (x, y, z) und Richtung, in die das Wasser abfließt (Kompass, bergab). */
    public record Kante(double x, double y, double z, double heading) { }

    /**
     * Vom Fuß die Falllinie hinauf bis zur Höhe fuß + hoehe (Schritte von 3 m entlang des Gefälles über 12 m); die Kante
     * blickt bergab, gemittelt über die letzten 30 m. null, wenn der Weg die Höhe nicht erreicht (flach oder Gipfel).
     */
    public static Kante kante(Fall.Ground g, double x, double z, double hoehe) {
        double y0 = Math.max(0, g.height(x, z)), mx = 0, mz = 0;
        for (int s = 0; s < 6000; s++) {
            double y = g.height(x, z);
            double gx = (g.height(x + 6, z) - g.height(x - 6, z)) / 12, gz = (g.height(x, z + 6) - g.height(x, z - 6)) / 12, gl = Math.hypot(gx, gz);
            if (gl < 1e-4) return null;
            gx /= gl;
            gz /= gl;
            mx = 0.9 * mx + 0.1 * gx;
            mz = 0.9 * mz + 0.1 * gz;
            if (y - y0 >= hoehe) {
                double head = Math.toDegrees(Math.atan2(-mx, mz));   // bergab = gegen die Laufrichtung (Kompass: x Ost, −z Nord)
                return new Kante(x, y, z, (head + 360) % 360);
            }
            x += gx * 3;
            z += gz * 3;
        }
        return null;
    }

    /**
     * Die Regenfälle: Uferzellen in Kern West, über denen das Gelände in 300 m Umkreis am höchsten steigt (mindestens
     * 350 m), die steilsten zuerst, jeder mindestens {@link #REGEN_ABSTAND} von den benannten Fällen (Fuß, Mitte, Kante)
     * und voneinander;
     * Kante auf 60 % dieser Höhe, 12 m breit.
     */
    public static List<Def> regenfaelle(Terrain t) {
        if (!t.measured()) return List.of();
        double[] k = Site.KERN_WEST.scene();
        int i0 = (int) Math.floor((k[0] - Terrain.X0) / Terrain.CELL), i1 = (int) Math.floor((k[2] - Terrain.X0) / Terrain.CELL);
        int j0 = (int) Math.floor((k[1] - Terrain.Z0) / Terrain.CELL), j1 = (int) Math.floor((k[3] - Terrain.Z0) / Terrain.CELL);
        List<double[]> c = new ArrayList<>();
        int r = 25;
        for (int j = j0; j <= j1; j += 2)
            for (int i = i0; i <= i1; i += 2) {
                if (t.art[j * Terrain.N + i] == Terrain.FJORD) continue;
                boolean shore = false;
                for (int dj = -1; dj <= 1 && !shore; dj++)
                    for (int di = -1; di <= 1; di++)
                        if (t.art[Math.max(0, Math.min(Terrain.N - 1, j + dj)) * Terrain.N + Math.max(0, Math.min(Terrain.N - 1, i + di))] == Terrain.FJORD) { shore = true; break; }
                if (!shore) continue;
                double h0 = t.at(i, j), top = h0;
                for (int dj = -r; dj <= r; dj += 2)
                    for (int di = -r; di <= r; di += 2) top = Math.max(top, t.at(i + di, j + dj));
                if (top - h0 >= 350) c.add(new double[]{top - h0, Terrain.cx(i), Terrain.cz(j)});
            }
        c.sort((a, b) -> Double.compare(b[0], a[0]));
        List<double[]> taken = new ArrayList<>();
        for (Def d : BENANNT) {
            taken.add(new double[]{d.x(), d.z()});
            Kante kk = kante(t::height, d.x(), d.z(), d.hoehe());
            if (kk != null) {
                taken.add(new double[]{kk.x, kk.z});
                taken.add(new double[]{(kk.x + d.x()) / 2, (kk.z + d.z()) / 2});
            }
        }
        List<Def> out = new ArrayList<>();
        for (double[] p : c) {
            if (out.size() >= REGEN_ZAHL) break;
            boolean free = true;
            for (double[] q : taken) if (Math.hypot(p[1] - q[0], p[2] - q[1]) < REGEN_ABSTAND) { free = false; break; }
            if (!free) continue;
            taken.add(new double[]{p[1], p[2]});
            double[] u = Site.toUtm(p[1], p[2]);
            out.add(new Def("Regenfall " + (out.size() + 1), Math.round(u[0]), Math.round(u[1]), Math.round(p[0] * 0.6), 12, 2, REGEN_ABFLUSS, Art.REGEN, false, 101 + out.size(), 0.5));
        }
        return out;
    }

    /** Ein Fall der Szene: seine Beschreibung, die Kante und der Fall, den die Grafikkarte gerade zeichnet. */
    public static final class Eintrag {
        public final Def def;
        public final Kante kante;
        /** Der Fall, wie er gerade gezeichnet wird (wird nach Änderungen des Abflusses durch eine neue Rechnung ersetzt). */
        public volatile Fall fall;
        /** Abfluss (m³/s), Temperatur am Fall (°C) und Wind, mit denen {@link #fall} gerechnet ist. */
        public volatile double q, temp, wind, windFrom;

        Eintrag(Def d, Kante k, Fall f) { def = d; kante = k; fall = f; q = f.discharge; temp = f.temperature; }

        /** Mitte des Falls (x, y, z): halb zwischen Kante und Aufprall. */
        public double[] mitte() {
            Fall f = fall;
            if (f.strands.isEmpty()) return new double[]{kante.x, kante.y, kante.z};
            Fall.Strand s = f.strands.get(f.strands.size() / 2);
            return new double[]{(kante.x + s.ix) / 2, (kante.y + s.iy) / 2, (kante.z + s.iz) / 2};
        }
    }

    /**
     * Der Fall zu einer Beschreibung an seiner Kante (Lauf über Fels, Abfluss im Juni, Wasserspiegel NN), auf dem Boden
     * {@code ground} (die Szene gibt die Wand mit Feinform). Die Kante sucht {@link #kante} auf {@code base} (dem Gelände
     * ohne Feinform). null ohne Kante.
     */
    public static Eintrag make(Def d, Fall.Ground base, Fall.Ground ground, Fall.Quality q) {
        Kante k = kante(base, d.x(), d.z(), d.hoehe());
        return k == null ? null : make(d, k, base, ground, q, false);
    }

    /**
     * Der Fall an einer gegebenen Kante. kanteHoehe: beide Enden der Kante auf der Höhe von k (etwa an der Front eines
     * Überhangs, den das Höhenfeld nicht kennt), sonst auf dem Gelände {@code base}.
     */
    public static Eintrag make(Def d, Kante k, Fall.Ground base, Fall.Ground ground, Fall.Quality q, boolean kanteHoehe) {
        return make(d, k, base, ground, q, kanteHoehe, 0);
    }

    /** Wie oben, mit dem Wasserspiegel unten (die Stufen der Elva enden im Fluss, nicht im Fjord). */
    public static Eintrag make(Def d, Kante k, Fall.Ground base, Fall.Ground ground, Fall.Quality q, boolean kanteHoehe, double spiegel) {
        Fall f = new Fall();
        f.name = d.name();
        f.seed = d.seed();
        f.quality = q;
        double h = Math.toRadians(k.heading), ax = Math.cos(h), az = Math.sin(h);   // quer zur Flussrichtung (sin h, −cos h)
        double w = d.breite() / 2;
        f.lipA = new double[]{k.x - ax * w, 0, k.z - az * w};
        f.lipB = new double[]{k.x + ax * w, 0, k.z + az * w};
        f.lipA[1] = kanteHoehe ? k.y : ground.height(f.lipA[0], f.lipA[2]) + 0.3;
        f.lipB[1] = kanteHoehe ? k.y : ground.height(f.lipB[0], f.lipB[2]) + 0.3;
        f.headingDeg = k.heading;
        f.strandCount = d.straehnen();
        f.discharge = d.juni();
        f.waterLevel = spiegel;
        f.ground = ground;
        f.overRock = true;
        f.lateralFreedom = d.quer();
        f.strandShare = d.art() == Art.HAUPT && d.straehnen() >= 5 ? 0.55 : 1;   // die Schwestern: sieben Fäden mit Lücken
        if (d.art() == Art.FLUSS) f.sprayScale = SPRAY_FLUSS;   // Phase 10: Kaskaden schäumen im Bett, statt zu zerstäuben
        f.update();
        return new Eintrag(d, k, f);
    }

    /** Alle Fälle der Szene (benannte und Regenfälle); ohne Gelände leer. */
    public static List<Eintrag> build(Terrain t, Fall.Ground ground, Fall.Quality q) {
        if (!t.measured()) return List.of();
        List<Eintrag> out = new ArrayList<>();
        List<Def> defs = new ArrayList<>(BENANNT);
        defs.addAll(regenfaelle(t));
        // Phase 8: die Stufen der Geirangelva, Kante am oberen Ende, so breit wie das Bett, Abfluss der Elva
        Elva ev = t.elva;
        if (ev != null)
            for (Elva.Stufe s : ev.stufen) {
                int a = s.i0(), b = s.i1();
                double[] u = Site.toUtm(ev.x[a], ev.z[a]);
                Def d = new Def(s.name(), u[0], u[1], ev.level[b] - ev.level[a], ev.half[b] * 1.4, 5, Elva.JUNI, Art.FLUSS, false, 31 + b, 0.35);
                double head = (Math.toDegrees(Math.atan2(ev.dx[b], -ev.dz[b])) + 360) % 360;
                Eintrag en = make(d, new Kante(ev.x[b], ev.level[b], ev.z[b], head), t::height, ground, q, false, ev.level[a]);
                if (en == null) continue;
                int unten = 0;
                for (Fall.Strand st : en.fall.strands) if (st.onWater) unten++;
                if (unten * 2 >= en.fall.strands.size()) out.add(en);   // eine Stufe, deren Wasser in einer Mulde liegen bleibt, entfällt
            }
        int regen = 0;
        for (Def d : defs) {
            if (d.art() == Art.REGEN) d = new Def("Regenfall " + (regen + 1), d.e(), d.n(), d.hoehe(), d.breite(), d.straehnen(), d.juni(), d.art(), d.ueberhang(), d.seed(), d.quer());
            double[] p = Formen.kante(t, d.name());   // Phase 8: Kante über der geschärften Wand (F12b)
            Eintrag e = p != null ? make(d, new Kante(p[0], p[1], p[2], p[3]), t::height, ground, q, false) : make(d, t::height, ground, q);
            if (e == null) continue;
            out.add(e);
            if (d.art() == Art.REGEN) regen++;
        }
        return Collections.unmodifiableList(out);
    }

    /** „Sieben Schwestern: Kante 410 m (E 400 812, N 6 888 020), nach 182° · 7 Strähnen …“ */
    public static String describe(Eintrag e) {
        double[] u = Site.toUtm(e.kante.x, e.kante.z);
        Fall f = e.fall;
        return String.format(Locale.GERMAN, "%s: Kante %.0f m über NN (E %.0f, N %.0f), nach %03.0f° · %d Strähnen · %.2f m³/s · %s",
                e.def.name(), e.kante.y, u[0], u[1], e.kante.heading, f.strands.size(), f.discharge, f.courseLine());
    }
}
