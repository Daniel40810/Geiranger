package com.dan.geiranger.wiring;

/**
 * Das Gelände: Höhe am Ort über NN. Gibt: die Bühne (Phase 1), das Gelände aus den HGT-Kacheln mit dem gemalten
 * Fjordgrund (Phasen 2 und 3). Nimmt: cliff (Neigung, Wände), fall (Kanten, Aufprall), ocean (Grund unter dem
 * Spiegel), forest, ground, road, river, timber, Kamera.
 */
public interface Terrain {
    /** Höhe des Bodens in m über NN am Ort (x, z); im Fjord der Grund unter dem Wasser. */
    double height(double x, double z);

    /** Wassertiefe am Ort (m, 0 = trocken). */
    default double waterDepth(double x, double z) { return Math.max(0, -height(x, z)); }

    /** Neigung des Bodens als Normale (Einheitsvektor). */
    default double[] normal(double x, double z) {
        double e = 1, hx = height(x + e, z) - height(x - e, z), hz = height(x, z + e) - height(x, z - e);
        double nx = -hx / (2 * e), nz = -hz / (2 * e), l = Math.sqrt(nx * nx + 1 + nz * nz);
        return new double[]{nx / l, 1 / l, nz / l};
    }

    /** Neigung in Grad (0 = eben, 90 = senkrecht); für cliff die Grenze zwischen Hang und Wand. */
    default double slopeDeg(double x, double z) { return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, normal(x, z)[1])))); }
}
