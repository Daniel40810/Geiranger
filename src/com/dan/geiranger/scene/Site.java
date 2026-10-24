package com.dan.geiranger.scene;

/**
 * Der Ort der Szene: der Geirangerfjord mit dem Ort Geiranger, den Ørnesvingen und den Wasserfällen (Werkbuch,
 * Abschnitte 2 und 5).
 * <p>
 * Raum (F2 A): UTM Zone 32 Nord (ETRS89, EPSG:25832) mit dem Ursprung am Fähranleger im Ort [G, aus der Karte, Phase 2
 * prüft am Gelände]. Echter Maßstab: 1 Einheit = 1 m, x nach Osten, y nach oben, z nach Süden, Nord = −z, y = 0 ist
 * Normalnull (NN2000, für die Szene gleich dem mittleren Wasserstand im Fjord). Für die Datenbank und QGIS liegt die
 * Lage zusätzlich in SRID 8307 (WGS84) vor.
 * <p>
 * Die Lagen der Kernfenster stammen aus der Karte {@code templates/Karte-Geirangerfjord.png} (± 300 m, F1 A), die
 * Lagen der Orte unten aus der Karte und den Quellen des Werkbuchs [G]; Phase 2 legt sie auf das Gelände der
 * HGT-Kacheln und korrigiert sie dort.
 */
public final class Site {
    private Site() { }

    /** Ursprung der Szene: der Fähranleger in Geiranger [G], UTM 32 N. */
    public static final double E0 = 406_330, N0 = 6_886_760;
    public static final int SRID = 8307, EPSG_UTM = 25832;
    /** Breite und Länge des Ursprungs (aus E0/N0 gerechnet). */
    public static final double LAT, LON;
    static {
        double[] ll = Geo.UTM32N.inverse(E0, N0);
        LAT = ll[0];
        LON = ll[1];
    }

    /** Der Ausschnitt der Umgebung nach dem GLO-30-Zuschnitt (Werkbuch, Abschnitt 1) in Grad: West, Süd, Ost, Nord. */
    public static final double WEST = 6.95, SOUTH = 62.05, EAST = 7.30, NORTH = 62.21;

    /** Mittlerer Erdradius in m und der wirksame Radius für die Sicht mit normaler Lichtbrechung (k = 0,13). */
    public static final double EARTH_R = 6_371_000, EARTH_R_OPTICAL = EARTH_R / (1 - 0.13);

    /** Ein rechteckiges Fenster in UTM 32 N (Ost- und Nordwerte in m). */
    public record Fenster(String name, double eMin, double nMin, double eMax, double nMax) {
        /** In der Szene: {xMin, zMin, xMax, zMax}. */
        public double[] scene() {
            double[] a = fromUtm(eMin, nMax), b = fromUtm(eMax, nMin);
            return new double[]{a[0], a[1], b[0], b[1]};
        }
        public double breite() { return eMax - eMin; }
        public double hoehe() { return nMax - nMin; }
        public boolean contains(double x, double z) {
            double[] u = toUtm(x, z);
            return u[0] >= eMin && u[0] <= eMax && u[1] >= nMin && u[1] <= nMax;
        }
    }

    /**
     * F1 A: Kern West quer (Knivsflå, Sieben Schwestern, Friaren, Skageflå) und Kern Ost hochkant (Ort bis Møllsæter).
     * Phase 2 hat Kern Ost am Gelände um 300 m nach Süden gerückt: der Kopf des Fjords liegt bei N 6 886 600, der Ort
     * mit dem Storfossen südlich davon (vorher N 6 886 550–6 890 100 aus der Karte). Phase 9 hat Kern Ost um 300 m
     * nach Westen verbreitert (2,3 km): die westlichen Kehren des Ørnevegen und die Plattform liegen bei E 404 360.
     */
    public static final Fenster KERN_WEST = new Fenster("Kern West", 399_700, 6_887_000, 402_700, 6_889_000),
            KERN_OST = new Fenster("Kern Ost", 404_300, 6_886_250, 406_600, 6_889_800);

    /** Die Umgebung (Rechteck um den GLO-30-Ausschnitt in UTM 32 N, gerundet). */
    public static final Fenster UMGEBUNG = new Fenster("Umgebung", 392_800, 6_881_400, 411_600, 6_898_800);

    /** Ein benannter Ort [G] mit Lage in UTM 32 N und Höhe des Grunds über NN (für die Bühne von Phase 1). */
    public record Ort(String name, double e, double n, double grund) {
        public double x() { return e - E0; }
        public double z() { return -(n - N0); }
    }

    /**
     * Die Orte, seit Phase 2 aus dem Gelände der HGT-Kacheln und der Karte gesetzt (Ufer aus der Fjordmaske, Höhen aus
     * den Höhenlinien; ± 50 m gegen die Karte, die Lage im Raster 1″ selbst ± 30 m):
     * <ul>
     * <li>der Anleger am Kopf des Fjords (Ufer, NN +2 m) als Ursprung;</li>
     * <li>die Plattform der Ørnesvingen seit Phase 9 außen an der elften Kehre des Ørnevegen (an der Karte eingepasst,
     * rund 530 m; bis Phase 8 auf der Höhenlinie 620 m, aber 620 m ist Korsmyra, der höchste Punkt der Straße), mit
     * Blick den Fjord hinab nach Westen (Foto Ørnesvingen2: links die Halbinsel Skagen, rechts die Nordwand);</li>
     * <li>die Sieben Schwestern seit Phase 7 an der steilsten Stelle des Nordufers unter Knivsflå (vorher 300 m weiter
     * nordöstlich, wo die Wand im Raster nur mit 50° steigt): in 120 m vom Wasser auf 300 m (im Raster bis 76°), darüber
     * die obere Wand; Knivsflå auf seinem Band auf 250 m; Friaren gegenüber am Ufer der Halbinsel Skagen, Skageflå auf
     * deren Nordwestecke auf 250 m;</li>
     * <li>der Storfossen in der Geirangelva im Ort, seit Phase 8 in der Mitte der steilen Stufe der Elva (von 11 auf 67 m,
     * aus dem Abflussnetz des Geländes), rund 430 m südlich des Anlegers.</li>
     * </ul>
     * Die Höhe {@link Ort#grund} gilt nur ohne Gelände; mit Gelände zählt das Raster.
     */
    public static final Ort ANLEGER = new Ort("Anleger", E0, N0, 2),
            PLATTFORM = new Ort("Plattform Ørnesvingen", 404_366, 6_889_643, 536),
            SCHWESTERN = new Ort("Sieben Schwestern", 400_610, 6_887_550, 0),
            KNIVSFLA = new Ort("Knivsflå", 400_660, 6_887_980, 250),
            FRIAREN = new Ort("Friaren", 401_480, 6_887_500, 0),
            SKAGEFLA = new Ort("Skageflå", 402_090, 6_888_150, 250),
            STORFOSSEN = new Ort("Storfossen", 406_429, 6_886_338, 36);
    public static final Ort[] ORTE = {ANLEGER, PLATTFORM, SCHWESTERN, KNIVSFLA, FRIAREN, SKAGEFLA, STORFOSSEN};

    /** Ein Punkt im Fjord zwischen den Schwestern und Friaren (Szene x, z): Startpunkt der Fjordmaske, Ansicht „Vom Wasser“. */
    public static final double[] VOM_WASSER = fromUtm(401_200, 6_887_750);

    /**
     * Phase 3: die Mündung der Geirangelva am Kopf des Fjords (Szene x, z), Mitte des gemalten Deltas. Aus dem Gelände:
     * das Tal der Elva läuft von Süden durch das Dorf und erreicht den Fjord an seiner Spitze westlich des Anlegers [G].
     */
    public static final double[] MUENDUNG = fromUtm(406_050, 6_886_430);

    /** Szenenpunkt (x, z) aus UTM 32 Nord (Ost- und Nordwert in m). */
    public static double[] fromUtm(double east, double north) { return new double[]{east - E0, -(north - N0)}; }

    /** UTM 32 Nord (Ost, Nord) aus einem Szenenpunkt (x, z). */
    public static double[] toUtm(double x, double z) { return new double[]{x + E0, N0 - z}; }

    /** Breite und Länge (Grad) eines Szenenpunkts. */
    public static double[] latLon(double x, double z) { return Geo.UTM32N.inverse(x + E0, N0 - z); }

    /** Szenenpunkt (x, z) aus Breite und Länge (Grad, als {lat, lon}). */
    public static double[] xz(double[] latLon) {
        double[] en = Geo.UTM32N.forward(latLon[0], latLon[1]);
        return fromUtm(en[0], en[1]);
    }

    /** Absenkung der Oberfläche in d m Entfernung durch die Erdkrümmung: d²/2R. */
    public static double curvatureDrop(double d) { return d * d / (2 * EARTH_R); }

    /** Entfernung der Kimm für ein Auge h m über dem Wasser, mit normaler Lichtbrechung (m). */
    public static double horizon(double h) { return Math.sqrt(2 * EARTH_R_OPTICAL * Math.max(0, h)); }

    /** „62°06′05″ N · 7°12′20″ E“ */
    public static String dms(double lat, double lon) {
        return dms(lat, lat >= 0 ? "N" : "S") + " · " + dms(lon, lon >= 0 ? "E" : "W");
    }

    private static String dms(double v, String h) {
        v = Math.abs(v);
        int d = (int) v;
        double mm = (v - d) * 60;
        int m = (int) mm;
        int s = (int) Math.round((mm - m) * 60);
        if (s == 60) { s = 0; m++; }
        if (m == 60) { m = 0; d++; }
        return String.format("%d°%02d′%02d″ %s", d, m, s, h);
    }
}
