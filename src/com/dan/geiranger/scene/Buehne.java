package com.dan.geiranger.scene;

/**
 * Die flache Bühne von Phase 1 (Platzhalter bis zum Gelände aus den HGT-Kacheln in Phase 2): eine Ebene auf NN, auf
 * der {@code stage.frag} die Marken zur Ausrichtung zeichnet: das 1-km-Raster (alle 5 km kräftiger), den Rand der
 * Umgebung, die beiden Kernfenster (gelb, F1 A), den Ursprung am Anleger mit Nordpfeil und die Orte aus {@link Site}
 * als Punkte. Die Kamera hält sich mit {@link #height} darüber.
 */
public final class Buehne {
    private Buehne() { }

    /** Höhe der Bühne über NN (m): überall 0. */
    public static double height(double x, double z) { return 0; }

    /** „Kern West“, „Kern Ost“, „Umgebung“ oder „außerhalb“ am Ort (x, z). */
    public static String zone(double x, double z) {
        if (Site.KERN_WEST.contains(x, z)) return Site.KERN_WEST.name();
        if (Site.KERN_OST.contains(x, z)) return Site.KERN_OST.name();
        if (Site.UMGEBUNG.contains(x, z)) return Site.UMGEBUNG.name();
        return "außerhalb";
    }

    /** Der nächste Ort aus {@link Site#ORTE} und sein Abstand (m) in der Ebene. */
    public static String naechsterOrt(double x, double z) {
        Site.Ort best = null;
        double bd = Double.MAX_VALUE;
        for (Site.Ort o : Site.ORTE) {
            double d = Math.hypot(x - o.x(), z - o.z());
            if (d < bd) { bd = d; best = o; }
        }
        return best == null ? "—" : String.format(java.util.Locale.GERMAN, "%.1f km bis %s", bd / 1000, best.name());
    }
}
