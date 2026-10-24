package com.dan.geiranger.ui;

import com.dan.fstyle.FTheme;

import java.awt.Color;

/**
 * Die Farben der Oberfläche aus FTheme (Bedienelemente in FTheme-Farben, die Szene hat ihren eigenen Look; aus
 * Rungholt). Liefert das Thema keine dunklen Farben, nimmt die App ihren Juniabend im Fjord, damit der Rand um die
 * Szene nicht hell aufleuchtet. Dazu die Farben des Werkbuchs: Fall, Gletschermilch, Fjordtiefe, Gneis, Birke, die
 * gelbe Mittellinie der Fv63, rote Holzhäuser, Abendgold.
 */
public final class Theme {
    private Theme() { }

    public static final Color NACHT = new Color(13, 19, 22), FALL = new Color(234, 242, 244), FJORD = new Color(93, 183, 174),
            TIEFE = new Color(44, 75, 82), GNEIS = new Color(154, 161, 166), BIRKE = new Color(163, 190, 85), LINIE = new Color(226, 184, 62),
            HAUS = new Color(201, 83, 60), GOLD = new Color(232, 190, 106);

    public static Color bg() { return pick(FTheme::getBackground, NACHT, Role.DARK); }
    public static Color surface() { return pick(FTheme::getSurface, new Color(20, 28, 32), Role.DARK); }
    public static Color ink() { return pick(FTheme::getText, new Color(231, 236, 236), Role.LIGHT); }
    public static Color muted() { return pick(FTheme::getTextMuted, new Color(143, 160, 164), Role.LIGHT); }
    public static Color accent() { return pick(FTheme::getAccent, FJORD, Role.ANY); }
    public static Color status() { return new Color(9, 13, 15); }
    public static Color line() { return new Color(38, 52, 58); }

    private enum Role { DARK, LIGHT, ANY }

    private static Color pick(java.util.function.Function<FTheme, Color> f, Color fallback, Role role) {
        try {
            Color c = f.apply(FTheme.getInstance());
            if (c == null) return fallback;
            int lum = (c.getRed() * 3 + c.getGreen() * 6 + c.getBlue()) / 10;
            if (role == Role.DARK && lum > 70) return fallback;
            if (role == Role.LIGHT && lum < 120) return fallback;
            return c;
        } catch (RuntimeException | Error e) {
            return fallback;
        }
    }

    /** Die Farbleiste des Werkbuchs (Fall bis Abendgold) mit ihren Anteilen, für Startbild und Symbol. */
    public static final Color[] BAND = {FALL, FJORD, TIEFE, GNEIS, BIRKE, LINIE, HAUS, GOLD};
    public static final double[] BAND_AT = {0, 0.09, 0.30, 0.40, 0.60, 0.74, 0.80, 0.90, 1};
}
