package com.dan.cliff.demo;

import com.dan.cliff.CNoise;
import com.dan.cliff.Cliff;
import com.dan.cliff.CliffMap;
import com.dan.cliff.Overhang;

import java.util.Locale;

/**
 * Prüfung des Packages ohne Fenster und ohne Grafikkarte (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder
 * mit Rückgabewert 1. Prüft: Hash und Rauschen (Bereich, Stetigkeit, Seed), Material nach Neigung, keine Streifen an
 * senkrechten Stellen (die Farbe ändert sich entlang einer senkrechten Wand in beiden Richtungen ähnlich stark),
 * Feinform (Höhe, Ausblenden in der Ferne, Neigung der Normale), Wandkarte (Wand, Geröll unter der Wand, Absatz,
 * nasser Streifen), Überhang (ragt hinaus, oben weiter als unten), Stufen, Reproduzierbarkeit, Nässe und Schnee.
 */
public final class CliffCheck {
    private CliffCheck() { }
    private static int bad;

    public static void main(String[] args) {
        check(CNoise.hash(1, 2, 3) == CNoise.hash(1, 2, 3) && CNoise.hash(1, 2, 3) != CNoise.hash(1, 2, 4), "Hash: gleich bei gleichen Zahlen, anders bei anderen");
        double lo = 1, hi = 0, jump = 0;
        for (int i = 0; i < 20000; i++) {
            double x = i * 0.037, v = CNoise.value(x, x * 0.7, -x * 1.3, 5);
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
            if (i > 0) jump = Math.max(jump, Math.abs(v - CNoise.value(x - 0.037, (x - 0.037) * 0.7, -(x - 0.037) * 1.3, 5)));
        }
        check(lo >= 0 && hi < 1 && hi - lo > 0.8 && jump < 0.12, String.format(Locale.GERMAN, "Rauschen 0..1 (%.3f bis %.3f), stetig (größter Sprung %.3f bei 3,7 cm)", lo, hi, jump));
        check(CNoise.value(10.5, -3.2, 7.7, 1) != CNoise.value(10.5, -3.2, 7.7, 2), "Anderer Seed, anderes Rauschen");

        Cliff c = new Cliff();
        Cliff.Sample flat = c.material(100, 50, 100, 0, 1, 0, null, 0, 0, 0.05), wall = c.material(100, 50, 100, 0.99, 0.14, 0, null, 0, 0, 0.05);
        check(flat.rock < 0.05 && wall.rock > 0.95, String.format(Locale.GERMAN, "Fels nach Neigung: flach %.2f, Wand %.2f", flat.rock, wall.rock));
        check(c.isWall(70) && !c.isWall(30), "Wand ab " + Math.round(c.wallSlopeDeg) + "°");

        // keine Streifen: an einer senkrechten Wand (Normale +x) ändert sich die Farbe hoch und quer ähnlich stark
        double dy = 0, dz = 0;
        int n = 0;
        for (int i = 0; i < 400; i++) {
            double y = 20 + i * 0.73, z = -50 + (i * 37 % 100);
            Cliff.Sample a = c.material(0, y, z, 1, 0, 0, null, 0, 0, 0.05);
            Cliff.Sample b = c.material(0, y + 0.5, z, 1, 0, 0, null, 0, 0, 0.05);
            Cliff.Sample e = c.material(0, y, z + 0.5, 1, 0, 0, null, 0, 0, 0.05);
            dy += Math.abs(lum(a) - lum(b));
            dz += Math.abs(lum(a) - lum(e));
            n++;
        }
        double ratio = Math.max(dy, dz) / Math.max(1e-9, Math.min(dy, dz));
        check(ratio < 2.5, String.format(Locale.GERMAN, "Senkrechte Wand: Wechsel der Farbe hoch %.4f, quer %.4f je 0,5 m (Verhältnis %.2f, ohne Streifen unter 2,5)", dy / n, dz / n, ratio));

        double rmax = 0, rmin = 0, far = 0;
        for (int i = 0; i < 4000; i++) {
            double r = c.relief(i * 1.7, i * 0.9, -i * 1.3, 0.05);
            rmax = Math.max(rmax, r);
            rmin = Math.min(rmin, r);
            far = Math.max(far, Math.abs(c.relief(i * 1.7, i * 0.9, -i * 1.3, 100)));
        }
        check(rmax - rmin > c.reliefAmp * 0.8 && rmax - rmin < c.reliefAmp * 3 && far < 0.01,
                String.format(Locale.GERMAN, "Feinform: %.1f bis %.1f m nah, in der Ferne (1 Bildpunkt = 100 m) aus (%.3f m)", rmin, rmax, far));
        Cliff.Sample n1 = c.material(10, 80, 10, 0.94, 0.34, 0, null, 0, 0, 0.05);
        double tilt = Math.toDegrees(Math.acos(Math.min(1, n1.normal[0] * 0.94 + n1.normal[1] * 0.34)));
        check(tilt > 0.5 && tilt < 60, String.format(Locale.GERMAN, "Die Feinform neigt die Normale (%.1f°)", tilt));

        CliffMap m = CliffMap.build(c, DemoWall::height, -600, -600, 2.5, 480);
        m.addFall(0, 20, 290, 10, 1);
        double[] atWall = m.valuesAt(0, DemoWall.zAt(0, 150)), atFloor = m.valuesAt(200, 400), atFall = m.valuesAt(0, DemoWall.zAt(0, 100));
        check(atWall[0] > 0.8 && atFloor[0] < 0.1, String.format(Locale.GERMAN, "Wandkarte: Wand %.2f an der Wand, %.2f im Tal", atWall[0], atFloor[0]));
        check(m.talusShare > 0.005 && atFloor[1] < 0.05, String.format(Locale.GERMAN, "Geröll am Fuß (%.1f %% der Fläche), nicht mitten im Tal (%.2f)", m.talusShare * 100, atFloor[1]));
        check(atFall[2] > 0.5 && m.valuesAt(300, DemoWall.zAt(300, 100))[2] < 0.1, String.format(Locale.GERMAN, "Nasser Streifen unter dem Fall (%.2f), daneben trocken", atFall[2]));
        check(m.ledgeShare > 0.0005, String.format(Locale.GERMAN, "Absätze in der Wand: %.2f %% der Fläche", m.ledgeShare * 100));

        Overhang o = new Overhang(0, 260, -100, 180, 40, 24, 10, 7);
        Overhang.Mesh mesh = o.mesh(20);
        double top = o.protrusion(0, 0.8), bottom = o.protrusion(0, -0.8);
        check(o.maxReach() > 6 && top > bottom && mesh.vertexCount() == 400 && mesh.indices.length == 19 * 19 * 6,
                String.format(Locale.GERMAN, "Überhang ragt %.1f m hinaus, oben %.1f m, unten %.1f m (hohl), Netz %d Ecken", o.maxReach(), top, bottom, mesh.vertexCount()));
        int outward = 0;
        for (int i = 0; i < mesh.vertexCount(); i++) if (mesh.vertices[i * 6 + 5] > -0.2) outward++;   // Blick nach Süden (+z)
        check(outward > mesh.vertexCount() * 0.9, "Normalen des Überhangs zeigen aus der Wand (" + outward + " von " + mesh.vertexCount() + ")");

        check(Cliff.Quality.values().length == 4 && Cliff.Quality.LOW.octaves < Cliff.Quality.ULTRA.octaves && !Cliff.Quality.LOW.overhangs, "Vier Stufen: Oktaven steigen, Niedrig ohne Überhänge");
        Cliff c2 = new Cliff();
        check(lum(c2.material(5, 6, 7, 0, 0.3, 0.95, null, 0, 0, 0.05)) == lum(c.material(5, 6, 7, 0, 0.3, 0.95, null, 0, 0, 0.05)), "Gleicher Seed, gleiche Farbe");
        c2.seed = 2;
        check(lum(c2.material(5, 6, 7, 0, 0.3, 0.95, null, 0, 0, 0.05)) != lum(c.material(5, 6, 7, 0, 0.3, 0.95, null, 0, 0, 0.05)), "Anderer Seed, andere Farbe");
        Cliff.Sample dry = c.material(3, 40, 3, 1, 0, 0, null, 0, 0, 0.05), wet = c.material(3, 40, 3, 1, 0, 0, null, 1, 0, 0.05);
        check(lum(wet) < lum(dry) * 0.75, String.format(Locale.GERMAN, "Nach Regen dunkler (%.3f statt %.3f)", lum(wet), lum(dry)));
        Cliff.Sample sn = c.material(3, 40, 3, 0, 1, 0, null, 0, 1, 0.05);
        check(sn.snow > 0.3 && lum(sn) > lum(flat), String.format(Locale.GERMAN, "Schnee auf flachen Stellen (%.2f)", sn.snow));
        System.out.println(c.describe());
        System.out.println(m.describe());
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Fehler.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static double lum(Cliff.Sample s) { return 0.2126 * s.albedo[0] + 0.7152 * s.albedo[1] + 0.0722 * s.albedo[2]; }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }
}
