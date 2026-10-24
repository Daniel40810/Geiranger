package com.dan.cliff.demo;

import com.dan.cliff.CNoise;

/**
 * Die Wand der Vorführung als Höhenfeld (ohne Grafikkarte, auch für die Prüfung): Talboden auf 0 m im Süden (+z),
 * Wand nach Norden auf 300 m, ein Absatz bei rund 150 m, leichtes Rauschen.
 */
public final class DemoWall {
    private DemoWall() { }

    public static double height(double x, double z) {
        double s = -z + 30 * (CNoise.value(x / 160, 0, 0, 3) - 0.5) * 2 + 8 * (CNoise.value(x / 40, 0, 5, 3) - 0.5);
        double wall = 300 * smooth(0, 110, s);
        double ledge = 18 * smooth(48, 56, s) * (1 - smooth(56, 70, s));
        double floor = 6 * (CNoise.value(x / 90, z / 90, 1, 4) - 0.5);
        return wall - ledge * 0.5 + floor + 4 * (CNoise.value(x / 25, z / 25, 2, 5) - 0.5);
    }

    /** z der Wand auf der Höhe y bei x (Suche von Süden nach Norden). */
    public static double zAt(double x, double y) {
        for (double z = 80; z > -200; z -= 0.5) if (height(x, z) >= y) return z;
        return -100;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
