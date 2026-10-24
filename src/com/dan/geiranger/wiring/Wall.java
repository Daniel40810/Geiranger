package com.dan.geiranger.wiring;

/**
 * Die Wand (neu in Geiranger). Gibt: {@code cliff} (Phase 5): wo Fels steht, wie steil, wie nass, ob eine Nische
 * dahinter liegt. Nimmt: fall (Aufprall auf Stufen, Nische), forest (kein Baum auf der Wand), ground, flock, Kamera.
 */
public interface Wall {
    /** Ob am Ort Wand ist (Neigung über der Grenze von cliff oder markierte Zusatzgeometrie). */
    boolean wall(double x, double z);

    /** Abstand bis zur Wand entlang des Strahls von (x, y, z) in Richtung (dx, dy, dz) in m, oder NaN ohne Treffer bis maxDist. */
    double hit(double x, double y, double z, double dx, double dy, double dz, double maxDist);

    /** Nässe der Wandfläche 0..1 (unter Fällen, nach Regen). */
    default double wetness(double x, double y, double z) { return 0; }
}
