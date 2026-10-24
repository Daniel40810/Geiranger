package com.dan.geiranger.gl;

import com.dan.cliff.Cliff;
import com.dan.cloud.Clouds;
import com.dan.fog.Fog;
import com.dan.rain.Rain;

import java.util.Locale;

/**
 * Qualitätsstufen der App (aus Rungholt, entschlackt). Phase 1: Anteil der Bildgröße, in dem gerechnet wird, und das
 * Zeitbudget; Phase 2: Sichtweite und Punktabstand des Geländes, in den Kernfenstern feiner (F3). Jede weitere Package-Phase hängt die Stufe ihres Packages an (Phase 2 Gelände, 3 ocean, 4 Himmel und
 * Wetter, 5 cliff, 6 fall, 10 forest und ground, 12 Schiffe, 13 Menschen); die Packages haben ihre eigene Kopie dieser
 * Aufzählung (Paketvertrag Punkt 7) und folgen der App eins zu eins über {@link #ordinal()}; Phase 3 setzt die Stufe
 * von ocean und das Spiegelbild der Wände, Phase 4 die von Wolken, Regen, Nebel und die Karte der Wandschatten.
 * Ziel (F22 A): „Mittel“ hält 60 Bilder/s in Full HD auf der Radeon 680M mit 2 GB, „Hoch“ und „Ultra“ für Standbilder und Kino.
 */
public enum Quality {
    NIEDRIG("Niedrig", 0.67, 2.2, 3, 1, com.dan.ocean.Ocean.Quality.LOW, 0.35, 1024, Clouds.Quality.FLAT, Rain.Quality.LOW, Fog.Quality.LOW, 512, Cliff.Quality.LOW, com.dan.fall.Fall.Quality.LOW),
    MITTEL("Mittel", 0.85, 2.6, 2, 0, com.dan.ocean.Ocean.Quality.MEDIUM, 0.5, 2048, Clouds.Quality.MEDIUM, Rain.Quality.LOW, Fog.Quality.MEDIUM, 1024, Cliff.Quality.MEDIUM, com.dan.fall.Fall.Quality.MEDIUM),
    HOCH("Hoch", 1.0, 3.0, 2, 0, com.dan.ocean.Ocean.Quality.HIGH, 0.5, 4096, Clouds.Quality.HIGH, Rain.Quality.HIGH, Fog.Quality.HIGH, 1024, Cliff.Quality.HIGH, com.dan.fall.Fall.Quality.HIGH),
    ULTRA("Ultra", 1.0, 3.4, 1, 0, com.dan.ocean.Ocean.Quality.ULTRA, 0.75, 4096, Clouds.Quality.ULTRA, Rain.Quality.ULTRA, Fog.Quality.ULTRA, 2048, Cliff.Quality.ULTRA, com.dan.fall.Fall.Quality.ULTRA);

    public final String label;
    /** Anteil der Bildgröße, in dem gerechnet wird. */
    public final double renderScale;

    /** Phase 2: Sichtweite jeder Geländestufe in ihren Knotengrößen (CDLOD). */
    public final double terrainRange;
    /** Phase 2: feinste Stufe außerhalb der Kernfenster und in ihnen (0 = 2 m, 1 = 4 m, 2 = 8 m, 3 = 16 m Punktabstand). */
    public final int terrainFine, terrainKern;

    /** Phase 3: Stufe von com.dan.ocean (FFT-Größe, Kaskaden). */
    public final com.dan.ocean.Ocean.Quality ocean;
    /**
     * Phase 3: das Spiegelbild der Wände. Anteil der Rechengröße, in dem die gespiegelte Ansicht gezeichnet wird (0 =
     * keine, das Wasser spiegelt nur den Himmel), und Breite des Rundumbilds, das ocean als Himmel liest (Höhe halb).
     */
    public final double mirrorScale;
    public final int panoWidth;

    /** Phase 4: Stufen von Wolken, Regen und Nebel; Kantenlänge der Karte für die Schatten der Wände (Texel). */
    public final Clouds.Quality clouds;
    public final Rain.Quality rain;
    public final Fog.Quality fog;
    public final int shadowN;
    /** Phase 5: Stufe von com.dan.cliff (Oktaven der Feinform, Überhänge, Reichweite). */
    public final Cliff.Quality cliff;
    /** Phase 7: Stufe von com.dan.fall (Abschnitte der Strähnen, Bündel, Nebelballen). */
    public final com.dan.fall.Fall.Quality fall;

    Quality(String label, double renderScale, double terrainRange, int terrainFine, int terrainKern, com.dan.ocean.Ocean.Quality ocean, double mirrorScale, int panoWidth,
            Clouds.Quality clouds, Rain.Quality rain, Fog.Quality fog, int shadowN, Cliff.Quality cliff, com.dan.fall.Fall.Quality fall) {
        this.cliff = cliff;
        this.fall = fall;
        this.clouds = clouds;
        this.rain = rain;
        this.fog = fog;
        this.shadowN = shadowN;
        this.ocean = ocean;
        this.mirrorScale = mirrorScale;
        this.panoWidth = panoWidth;
        this.label = label;
        this.renderScale = renderScale;
        this.terrainRange = terrainRange;
        this.terrainFine = terrainFine;
        this.terrainKern = terrainKern;
    }

    /**
     * Bildzeit, die die Stufe höchstens verbrauchen darf (ms): „Mittel“ 16,0 ms nach dem Budget im Werkbuch
     * (Abschnitt 10: 16,7 ms je Bild, davon 0,7 ms Reserve), „Hoch“ das Doppelte, „Niedrig“ die Hälfte.
     */
    public double budgetMs() {
        return switch (this) { case NIEDRIG -> 8.0; case MITTEL -> 16.0; case HOCH -> 32.0; case ULTRA -> 50.0; };
    }

    /** Phase 10: Stufe von com.dan.forest (Reichweite, Stufengrenzen, Abstand der Plätze), eins zu eins. */
    public com.dan.forest.ForestField.Quality forest() { return com.dan.forest.ForestField.Quality.values()[ordinal()]; }

    /** Phase 10: Reichweite der Bodendecke um das Auge (m). */
    public double groundRadius() {
        return switch (this) { case NIEDRIG -> 10; case MITTEL -> 16; case HOCH -> 24; case ULTRA -> 32; };
    }

    /** Phase 18: Stufe von com.dan.timber (Naust, Anleger, Höfe), eins zu eins. */
    public com.dan.timber.Timber.Quality timber() { return com.dan.timber.Timber.Quality.values()[ordinal()]; }

    /** Phase 18: Fv63 – Reichweite der Straße und der Fahrzeuge um das Auge (m). */
    public double strasseRadius() { return switch (this) { case NIEDRIG -> 1500; case MITTEL -> 2500; case HOCH -> 3500; case ULTRA -> 5000; }; }
    public double fahrzeugReichweite() { return switch (this) { case NIEDRIG -> 700; case MITTEL -> 1200; case HOCH -> 1800; case ULTRA -> 2500; }; }

    /** Phase 18: Menschen – Größe im Bild (Bildpunkte), ab der das feine und das mittlere Netz kommt, und unter der niemand gezeichnet wird. */
    public double[] leutePx() {
        return switch (this) { case NIEDRIG -> new double[]{320, 60, 4}; case MITTEL -> new double[]{220, 40, 2.5}; case HOCH -> new double[]{160, 28, 1.8}; case ULTRA -> new double[]{120, 20, 1.2}; };
    }

    /** Phase 18: Kielwasser der Schiffe (Höhenfeld 512² auf der Grafikkarte); auf „Niedrig“ aus. */
    public boolean kielwasser() { return this != NIEDRIG; }

    /** Phase 18: Anteil der Schneeflocken (Schneefall aus season). */
    public double schneeAnteil() { return switch (this) { case NIEDRIG -> 0.35; case MITTEL -> 0.6; case HOCH, ULTRA -> 1.0; }; }

    /** Was die Stufe einstellt, als eine Zeile (Bedienfeld, README). */
    public String summary() {
        return String.format(Locale.GERMAN, "%s: %d %% der Auflösung, Budget %.1f ms, Gelände %.1f Knoten, am Auge %d m, in den Kernen %d m, Wasser FFT %d² × %d, Spiegelbild %s",
                label, Math.round(renderScale * 100), budgetMs(), terrainRange, 2 << terrainFine, 2 << terrainKern, ocean.n, ocean.cascades,
                mirrorScale > 0 ? Math.round(mirrorScale * 100) + " % (Rundumbild " + panoWidth + " × " + panoWidth / 2 + ")" : "aus")
                + String.format(Locale.GERMAN, ", Wolken %s, Schatten der Wände %d², Fels %s, Fälle %s, Wald %s (%.1f km), Bodendecke %.0f m", clouds, shadowN, cliff, fall,
                        forest(), forest().radius / 1000, groundRadius())
                + String.format(Locale.GERMAN, ", Höfe %s, Fv63 %.1f km (Fahrzeuge %.1f km), Menschen ab %.1f px, Kielwasser %s, Schneeflocken %.0f %%", timber(),
                        strasseRadius() / 1000, fahrzeugReichweite() / 1000, leutePx()[2], kielwasser() ? "an" : "aus", schneeAnteil() * 100);
    }

    @Override public String toString() { return label; }
}
