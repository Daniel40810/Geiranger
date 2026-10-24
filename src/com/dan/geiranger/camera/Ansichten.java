package com.dan.geiranger.camera;

import com.dan.geiranger.scene.Buehne;
import com.dan.geiranger.scene.Site;

import java.util.List;
import java.util.Locale;

/**
 * Die Ansichten nach Namen auf den Tasten 1 bis 9 (Werkbuch, Abschnitt 6): Plattform, Vom Wasser, Am Storfossen,
 * Skageflå, Anleger, Vogelauge; seit Phase 3 Fjordgrund (der gemalte Grund ohne Wasser); seit Phase 7 Sieben Schwestern
 * (vom Wasser aus 450 m im Südosten, wie Waterfall1) und Friaren (vom Wasser aus 400 m im Nordwesten, wie Waterfall2).
 * „Vom Wasser“ und „Skageflå“ blicken seit Phase 7 auf die Schwestern an ihrer neuen Lage. „Am Storfossen“ steht seit
 * Phase 8 auf der Treppe am Nordufer (rund ein Drittel hinauf, Auge 1,7 m über der Stufe) und blickt den Fall hinauf. Lagen seit Phase 2 aus dem Gelände und den Orten in {@link Site}: die Plattform blickt
 * nach Westsüdwest den Fjord hinab (wie Foto Ørnesvingen2), „Vom Wasser“ steht im Südwestarm und blickt hinauf zu den
 * Sieben Schwestern, Skageflå über den Fjord nach Westen zu ihnen, der Anleger vom Wasser vor dem Kai den Ostarm hinauf, das Vogelauge über
 * dem Ostarm nach Westen. Über dem Fjord zählt der Spiegel als Grund (seit Phase 3 liegt der Grund darunter). Ohne Gelände steht das Auge in der Höhe {@link Ansicht#grund} über der Ebene.
 */
public final class Ansichten {
    /**
     * Eine Ansicht: Taste, Name, Lage (x, z), Blickrichtung (Grad von Nord), Neigung (Grad nach unten, negativ nach
     * oben), Augenhöhe über Grund, Höhe des Grunds ohne Gelände [G], ab Phase.
     */
    public record Ansicht(int key, String name, double x, double z, double heading, double pitch, double eyeAboveGround, double grund, int phase) {
        @Override public String toString() { return key + " " + name; }
    }

    public static final List<Ansicht> ALLE = List.of(
            new Ansicht(1, "Plattform", Site.PLATTFORM.x(), Site.PLATTFORM.z(), 245, 12, 1.7, Site.PLATTFORM.grund(), 1),
            new Ansicht(2, "Vom Wasser", Site.VOM_WASSER[0], Site.VOM_WASSER[1], 260, -12, 3.0, 0, 1),
            new Ansicht(3, "Am Storfossen", Site.fromUtm(406_395, 6_886_363)[0], Site.fromUtm(406_395, 6_886_363)[1], 120, -18, 3.3, 25, 1),
            new Ansicht(4, "Skageflå", Site.SKAGEFLA.x(), Site.SKAGEFLA.z(), 251, 6, 1.7, Site.SKAGEFLA.grund(), 1),
            // Phase 11: der Ort vom Wasser (vorher stand die Ansicht vor dem Anleger und schaute den Fjord hinab)
            new Ansicht(5, "Anleger", Site.ANLEGER.x() - 280, Site.ANLEGER.z() + 30, 102, -2, 3.0, 0, 1),
            new Ansicht(6, "Vogelauge", -730, -840, 270, 25, 1400, 0, 1),
            new Ansicht(7, "Fjordgrund", -2600, -2230, 262, 16, 90, 0, 3),
            new Ansicht(8, "Sieben Schwestern", Site.fromUtm(400_755, 6_887_230)[0], Site.fromUtm(400_755, 6_887_230)[1], 330, -17, 2.0, 0, 7),
            new Ansicht(9, "Friaren", Site.fromUtm(401_215, 6_887_590)[0], Site.fromUtm(401_215, 6_887_590)[1], 107, -14, 2.0, 0, 7));

    /**
     * Taste der Ansicht „Fjordgrund“ (Phase 3): 90 m über dem Spiegel im breiten Bogen nördlich von Skageflå, Blick den
     * Fjord hinab nach Westen; der Renderer schaltet dafür „Grund zeigen“ ein (Abnahme: Ufer, Mitte und Rinne).
     */
    public static final int FJORDGRUND = 7;
    /** Phase 8a: „Am Storfossen“ steht auf der Treppe ({@code Bauten.blick}). */
    public static final int STORFOSSEN = 3;
    /** Phase 9: „Plattform“ steht auf dem Deck der Ørnesvingen ({@code Strasse.plattform}). */
    public static final int PLATTFORM = 1;

    /** Ob die Ansicht schon einen Ort hat. */
    public static boolean ready(Ansicht a, int phase) { return a.phase() <= phase; }

    public static Ansicht byName(String name) {
        for (Ansicht a : ALLE) if (a.name().equalsIgnoreCase(name) || a.name().replace(" ", "").equalsIgnoreCase(name)) return a;
        // ohne Sonderzeichen (Umgebungsvariablen unter Windows): „Skagefla“, „AmStorfossen“ …
        String n = name.replace(" ", "").toLowerCase(Locale.ROOT);
        for (Ansicht a : ALLE) {
            String k = a.name().replace(" ", "").toLowerCase(Locale.ROOT).replace("å", "a").replace("ø", "o").replace("æ", "ae");
            if (k.equals(n)) return a;
        }
        try { return byKey(Integer.parseInt(name.trim())); } catch (NumberFormatException e) { return null; }
    }

    public static Ansicht byKey(int key) {
        for (Ansicht a : ALLE) if (a.key() == key) return a;
        return null;
    }

    /**
     * Stellt die Kamera auf die Ansicht über dem Boden {@code ground}; ohne Gelände (Grund überall 0) gilt die Höhe
     * {@link Ansicht#grund}. false, wenn sie in dieser Phase noch keinen Ort hat.
     */
    public static boolean apply(Ansicht a, Camera c, int phase, java.util.function.DoubleBinaryOperator ground) {
        if (!ready(a, phase)) return false;
        c.stopFahrt();
        c.diving = false;
        double g = ground.applyAsDouble(a.x(), a.z());
        if (Math.abs(g) < 1e-9) g = a.grund();
        c.place(a.x(), g + a.eyeAboveGround(), a.z(), a.heading(), a.pitch());
        return true;
    }

    /** Wie oben, über der Bühne von Phase 1. */
    public static boolean apply(Ansicht a, Camera c, int phase) { return apply(a, c, phase, Buehne::height); }

    /** „1 Plattform (Auge 1,7 m über Grund, Blick 245°)“ */
    public static String describe(Ansicht a, int phase) {
        if (!ready(a, phase)) return a + " (folgt in Phase " + a.phase() + ")";
        return String.format(Locale.GERMAN, "%s (Auge %.1f m über Grund, Blick %03.0f°)", a, a.eyeAboveGround(), a.heading());
    }

    private Ansichten() { }
}
