package com.dan.geiranger.scene;

import com.dan.timber.Material;
import com.dan.timber.Mesh;

/**
 * Phase 11: alte Holzbauten aus {@code com.dan.timber} (F16 A): Spaltbohlen ({@link Material#PLANK}), die mit dem Alter
 * vergrauen, Grasdach ({@link Material#SOD}), Fundament aus Bruchstein ({@link Material#STONE}), Türblatt, Bohlendeck
 * und Pfähle. Die Netze stehen in der Szene (Weltkoordinaten) und gehen als ein festes Netz an {@code TimberGl}
 * ({@code setStatic}); die Häuser von {@code timber} selbst sind wikingerzeitlich (Reet, Flechtwerk) und passen nicht.
 * <p>
 * Ein Haus liegt mit dem First entlang (ax, az); quer dazu q = (−az, ax). Jede Fläche wird so gewendet, dass ihre
 * Normale nach außen zeigt ({@code timber} zeichnet nur Vorderseiten, gegen den Uhrzeigersinn).
 */
final class Holzbau {
    private Holzbau() { }

    static double[] p(double x, double y, double z) { return new double[]{x, y, z}; }

    /** Viereck p0..p3 mit Normale ungefähr n; p0→p1 bleibt die Faser (u), notfalls wird gespiegelt. */
    static void quad(Mesh m, double[] p0, double[] p1, double[] p2, double[] p3, double[] n, Material mat, double ao) {
        double[] c = Bauten.cross(Bauten.sub(p1, p0), Bauten.sub(p3, p0));
        if (c[0] * n[0] + c[1] * n[1] + c[2] * n[2] >= 0) m.quad(p0, p1, p2, p3, mat, 0, 0, ao);
        else m.quad(p3, p2, p1, p0, mat, 0, 0, ao);
    }

    static void tri(Mesh m, double[] a, double[] b, double[] c, double[] n, Material mat) {
        double[] k = Bauten.cross(Bauten.sub(b, a), Bauten.sub(c, a));
        if (k[0] * n[0] + k[1] * n[1] + k[2] * n[2] >= 0) m.triangle(a, b, c, mat, 1);
        else m.triangle(a, c, b, mat, 1);
    }

    /** Quader: Mitte (x, z), Richtung (ax, az), halbe Länge und Breite, Unter- und Oberkante; Faser senkrecht. */
    static void box(Mesh m, double x, double z, double ax, double az, double hl, double hw, double y0, double y1, Material mat) {
        double qx = -az, qz = ax;
        double[][] c = new double[8][];
        for (int k = 0; k < 8; k++) {
            double sl = (k & 1) == 0 ? -hl : hl, sw = (k & 2) == 0 ? -hw : hw, y = (k & 4) == 0 ? y0 : y1;
            c[k] = p(x + ax * sl + qx * sw, y, z + az * sl + qz * sw);
        }
        // Seiten: p0 unten, p1 oben (Faser senkrecht), p3 unten daneben
        quad(m, c[1], c[5], c[7], c[3], new double[]{ax, 0, az}, mat, 0.7);
        quad(m, c[0], c[4], c[6], c[2], new double[]{-ax, 0, -az}, mat, 0.7);
        quad(m, c[2], c[6], c[7], c[3], new double[]{qx, 0, qz}, mat, 0.7);
        quad(m, c[0], c[4], c[5], c[1], new double[]{-qx, 0, -qz}, mat, 0.7);
        quad(m, c[4], c[5], c[7], c[6], new double[]{0, 1, 0}, mat, 1);
        quad(m, c[0], c[1], c[3], c[2], new double[]{0, -1, 0}, mat, 0.5);
    }

    /** Balken von a nach b (rund mit {@code sides} Seiten), Pfähle und Pfosten. */
    static void pfahl(Mesh m, double[] a, double[] b, double r, Material mat) {
        m.beam(a, b, r, 6, mat, true, 0.6, 1);
    }

    /**
     * Ein altes Holzhaus wie auf Knivsflå und Skageflå (Fotos Geiranger1 und 3): Bruchsteinsockel bis unter den tiefsten
     * Punkt, Wände aus senkrechten Bohlen, Grasdach mit Überstand und Windbrettern, Tür an der Talseite (−q), zwei kleine
     * Fenster je Langseite. Mit {@code stelzen} steht es auf vier Steinpfeilern (Stabbur). Gibt die Firsthöhe zurück.
     */
    static double hus(Mesh m, Terrain t, double cx, double cz, double ax, double az, double hl, double hw, double wand, boolean stelzen) {
        double qx = -az, qz = ax, lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int i = -1; i <= 1; i += 2)
            for (int j = -1; j <= 1; j += 2) {
                double y = t.height(cx + ax * hl * i + qx * hw * j, cz + az * hl * i + qz * hw * j);
                lo = Math.min(lo, y);
                hi = Math.max(hi, y);
            }
        double floor = hi + 0.3;
        if (stelzen) {
            for (int i = -1; i <= 1; i += 2)
                for (int j = -1; j <= 1; j += 2)
                    box(m, cx + ax * (hl - 0.4) * i + qx * (hw - 0.4) * j, cz + az * (hl - 0.4) * i + qz * (hw - 0.4) * j, ax, az, 0.3, 0.3, lo - 0.3, floor + 1.0, Material.STONE);
            floor += 1.0;
        } else box(m, cx, cz, ax, az, hl + 0.12, hw + 0.12, lo - 0.5, floor, Material.STONE);
        double eave = floor + wand, ridge = eave + hw * 0.62;   // Dachneigung rund 32°
        // Wände
        double[][] c = new double[4][];
        for (int k = 0; k < 4; k++) {
            double sl = (k & 1) == 0 ? -hl : hl, sw = (k & 2) == 0 ? -hw : hw;
            c[k] = new double[]{cx + ax * sl + qx * sw, cz + az * sl + qz * sw};
        }
        wand(m, c[1], c[3], floor, eave, new double[]{ax, 0, az});
        wand(m, c[0], c[2], floor, eave, new double[]{-ax, 0, -az});
        wand(m, c[2], c[3], floor, eave, new double[]{qx, 0, qz});
        wand(m, c[0], c[1], floor, eave, new double[]{-qx, 0, -qz});
        // Giebel
        for (int e = -1; e <= 1; e += 2) {
            double[] a = p(cx + ax * hl * e + qx * hw, eave, cz + az * hl * e + qz * hw);
            double[] b = p(cx + ax * hl * e - qx * hw, eave, cz + az * hl * e - qz * hw);
            double[] r = p(cx + ax * hl * e, ridge, cz + az * hl * e);
            tri(m, a, b, r, new double[]{ax * e, 0, az * e}, Material.PLANK);
        }
        // Grasdach: zwei Flächen mit Überstand, Dicke 0,3 m, Windbretter an den Giebeln
        double ol = hl + 0.45, ow = hw + 0.55, drop = 0.55 * 0.62, d = 0.3;
        for (int s = -1; s <= 1; s += 2) {
            double[] e0 = p(cx - ax * ol + qx * ow * s, eave - drop, cz - az * ol + qz * ow * s);
            double[] e1 = p(cx + ax * ol + qx * ow * s, eave - drop, cz + az * ol + qz * ow * s);
            double[] r0 = p(cx - ax * ol, ridge, cz - az * ol), r1 = p(cx + ax * ol, ridge, cz + az * ol);
            double[] n = Bauten.norm(new double[]{qx * s * 0.62, 1, qz * s * 0.62});
            double[] up = {0, d, 0};
            quad(m, add(e0, up), add(r0, up), add(r1, up), add(e1, up), n, Material.SOD, 1);
            quad(m, e0, r0, r1, e1, new double[]{-n[0], -n[1], -n[2]}, Material.PLANK, 0.5);
            // Traufkante (Soden) und Windbretter an beiden Enden
            quad(m, e0, add(e0, up), add(e1, up), e1, new double[]{qx * s, 0, qz * s}, Material.SOD, 0.8);
            quad(m, e0, add(e0, up), add(r0, up), r0, new double[]{-ax, 0, -az}, Material.PLANK, 0.8);
            quad(m, e1, add(e1, up), add(r1, up), r1, new double[]{ax, 0, az}, Material.PLANK, 0.8);
        }
        // Tür an der Talseite (−q), zwei kleine Fenster je Langseite
        double dx = cx - qx * (hw + 0.03), dz = cz - qz * (hw + 0.03);
        double[] n = {-qx, 0, -qz};
        quad(m, p(dx - ax * 0.5, floor, dz - az * 0.5), p(dx - ax * 0.5, floor + 1.9, dz - az * 0.5), p(dx + ax * 0.5, floor + 1.9, dz + az * 0.5),
                p(dx + ax * 0.5, floor, dz + az * 0.5), n, Material.DOOR, 0.8);
        for (int s = -1; s <= 1; s += 2)
            for (int k = -1; k <= 1; k += 2) {
                double fx = cx + qx * (hw + 0.03) * s + ax * hl * 0.55 * k, fz = cz + qz * (hw + 0.03) * s + az * hl * 0.55 * k, y = floor + 1.1;
                double[] fn = {qx * s, 0, qz * s};
                quad(m, p(fx - ax * 0.35, y, fz - az * 0.35), p(fx - ax * 0.35, y + 0.6, fz - az * 0.35), p(fx + ax * 0.35, y + 0.6, fz + az * 0.35),
                        p(fx + ax * 0.35, y, fz + az * 0.35), fn, Material.DARK, 1);
            }
        return ridge;
    }

    /** Eine Wand aus senkrechten Bohlen zwischen den Ecken a und b (x, z), Normale nach außen. */
    static void wand(Mesh m, double[] a, double[] b, double y0, double y1, double[] n) {
        quad(m, p(a[0], y0, a[1]), p(a[0], y1, a[1]), p(b[0], y1, b[1]), p(b[0], y0, b[1]), n, Material.PLANK, 0.75);
    }

    static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
}
