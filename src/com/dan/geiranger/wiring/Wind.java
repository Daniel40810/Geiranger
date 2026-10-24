package com.dan.geiranger.wiring;

/**
 * Wind: Stärke, Richtung, Böigkeit. Gibt: {@code weather} (Phase 4). Nimmt: ocean (Kräuselung in Böen), cloud, rain,
 * fog, fall (der Schleier zieht weg), forest, ground, soundscape. Bis Phase 4 setzt die App ihn von Hand.
 */
public interface Wind {
    /** Mittlere Geschwindigkeit in m/s, 10 m über Grund. */
    double speed();

    /** Richtung, aus der der Wind kommt, in Grad von Nord im Uhrzeigersinn. */
    double fromDeg();

    /** Böigkeit 0..1 (Anteil, um den Böen über dem Mittel liegen). */
    default double gustiness() { return 0.3; }

    /** Windstärke nach Beaufort (0..12) aus der Geschwindigkeit, WMO-Grenzen. */
    default int beaufort() { return Beaufort.of(speed()); }

    /** Wohin er weht, als Einheitsvektor der Szene (x Ost, z Süd). */
    default double[] toVector() {
        double a = Math.toRadians(fromDeg() + 180);
        return new double[]{Math.sin(a), 0, -Math.cos(a)};
    }
}
