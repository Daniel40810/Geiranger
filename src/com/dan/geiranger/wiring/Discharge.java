package com.dan.geiranger.wiring;

/**
 * Abfluss eines Falls (neu in Geiranger, F13 A). Gibt: die Regie aus Jahreszeit, Schmelze und Regen (Phase 7), von
 * Hand im Block WASSER. Nimmt: fall (Strahldicke, Zerfall, Schleier), ocean (Ringwellen), soundscape (Lautstärke),
 * cliff (nasse Streifen), river (Geirangelva mit Storfossen).
 */
public interface Discharge {
    /** Abfluss des Falls mit dem Namen in m³/s (0 = trocken). */
    double cubicMetres(String fall);

    /** Ob der Fall gefroren ist (Winter, F13). */
    default boolean frozen(String fall) { return false; }
}
