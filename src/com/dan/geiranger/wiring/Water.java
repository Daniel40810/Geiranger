package com.dan.geiranger.wiring;

/**
 * Der Spiegel des Fjords. Gibt: die App (Phase 1 fest auf NN), ab Phase 3 {@code ocean} mit Preset „Fjord“ (lange
 * Welle der Gezeit [G, zu prüfen], Kielwasser, Ringwellen unter den Fällen). Nimmt: fall (Tosbecken), buoyancy
 * (Schiffe), river (Mündung), Kamera.
 */
public interface Water {
    /** Wasserspiegel am Ort (m über NN). */
    double surfaceAt(double x, double z);

    /** Mittlerer Spiegel (m über NN). */
    default double level() { return surfaceAt(0, 0); }
}
