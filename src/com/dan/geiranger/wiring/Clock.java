package com.dan.geiranger.wiring;

/**
 * Zeit der Szene. Gibt: die App ({@code SceneClock}). Nimmt: alle Packages, vor allem {@code sky} (Sonne über den
 * Gipfeln), {@code season} (Schmelze, Schnee) und {@code fall} (Abfluss nach Jahreszeit). Die Laufzeit {@code t} läuft
 * auch bei stehender Uhr weiter (Wellen, Strähnen), die Uhrzeit {@code epochMillis} folgt dem Zeitraffer.
 */
public interface Clock {
    /** Laufzeit der Szene in s und letzter Schritt in s. */
    double t();
    double dt();

    /** Szenenzeit in ms seit 1970 (UTC). */
    double epochMillis();

    /** Ort in Grad (Anleger in Geiranger). */
    double latDeg();
    double lonDeg();
}
