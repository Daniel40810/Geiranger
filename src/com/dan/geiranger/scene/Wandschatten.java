package com.dan.geiranger.scene;

/**
 * Phase 4 (F19 A): die Schatten der Wände je Sonnenstand. Von einem Punkt läuft ein Strahl über das Gelände zur Sonne
 * (in der Fläche zur Sonne hin, Schritte wachsend von einer halben Zelle bis 30 km, mit Erdkrümmung); der steilste
 * Winkel zu einem Hindernis ist der Horizont in Richtung der Sonne. Licht = weicher Übergang um den Sonnenrand
 * (Halbmesser 0,27°, dazu eine Zelle Unschärfe). Dieselbe Rechnung steht in {@code schatten.frag}; die Grafikkarte
 * rechnet damit je Sonnenstand eine Karte über Hauptraster und Fernring, Gelände und ocean lesen sie.
 * <p>
 * Nur JDK. Für HUD und Prüfung: wann die Sonne einen Ort am Wasser erreicht ({@link #sonneAb}).
 */
public final class Wandschatten {
    private Wandschatten() { }

    public static final double EARTH_R = 6_371_000;
    /** Weichheit des Übergangs (rad): Sonnenhalbmesser 0,0047 und Unschärfe. */
    public static final double SOFT = 0.012;
    /** Größte Reichweite des Strahls (m) und Faktor, um den die Schritte wachsen. */
    public static final double REACH = 30_000, GROW = 1.07;
    /** Höchste Höhe im Gelände, über der der Strahl abbrechen darf (m). */
    public static final double TOP = 2_000;

    /**
     * Anteil direkten Lichts 0..1 am Punkt (x, y, z) für die Richtung zur Lichtquelle {@code dir} (Einheitsvektor,
     * y nach oben). Unter der Kimm 0.
     */
    public static double licht(Terrain t, double x, double y, double z, double[] dir) {
        double h = Math.hypot(dir[0], dir[2]);
        double alt = Math.atan2(dir[1], h);
        if (alt < -SOFT) return 0;
        if (h < 1e-6) return 1;
        double dx = dir[0] / h, dz = dir[2] / h, tanAlt = Math.tan(Math.max(alt, -0.2));
        double maxTan = -1e9, step = Terrain.CELL * 0.5, s = step;
        while (s < REACH) {
            double g = t.height(x + dx * s, z + dz * s) - s * s / (2 * EARTH_R);
            double tn = (g - y) / s;
            if (tn > maxTan) maxTan = tn;
            if (maxTan > tanAlt + 0.05) break;          // schon ganz im Schatten
            if (y + s * tanAlt > TOP) break;              // der Strahl liegt über jedem Gipfel: nichts verdeckt mehr
            step *= GROW;
            s += step;
        }
        double horizon = Math.atan(maxTan);
        return smooth(-SOFT, SOFT, alt - horizon);
    }

    /**
     * Ab wann (Stunde der Ortszeit, Schritte von 5 min ab 2 Uhr) am Tag der Uhr die Sonne den Punkt (x, z) auf dem
     * Spiegel erreicht; NaN, wenn nie.
     */
    public static double sonneAb(Terrain t, SceneClock clock, double x, double z, double level) {
        SceneClock c = new SceneClock(clock.date(), 2);
        for (double hr = 2; hr < 23; hr += 1 / 12.0) {
            c.setHour(hr);
            double[] sp = c.sun();
            if (sp[0] > 0 && licht(t, x, level + 0.5, z, Sun.direction(sp[0], sp[1])) > 0.5) return hr;
        }
        return Double.NaN;
    }

    static double smooth(double a, double b, double x) {
        double u = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return u * u * (3 - 2 * u);
    }
}
