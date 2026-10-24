package com.dan.geiranger.wiring;

import com.dan.weather.Scenario;
import com.dan.weather.Weather;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 4: das Klima „Geiranger“ für {@code com.dan.weather} [G, nach Klimatabellen Vestlands gerundet, zu prüfen an
 * den Normalwerten des Meteorologisk institutt]. Der innere Fjord liegt im Windschatten der Wände: Wind kommt
 * abgeschwächt und längs der Achse an, am Tag fjordeinwärts aus Westen, nachts und bei Föhn fjordauswärts aus Osten.
 * Fronten vom Atlantik bringen Landregen mit Wolken, die in halber Höhe an den Wänden hängen; nach klaren, stillen
 * Nächten liegt Nebel im Fjord. Keine Dünung. Gewitter selten, Föhn im Winterhalbjahr.
 * <p>
 * Dazu drei Regiebücher für die Abnahme von Phase 4: {@link #junitag()}, {@link #nebelmorgen()}, {@link #regentag()}.
 */
public final class KlimaGeiranger {
    private KlimaGeiranger() { }

    /** Monatsmittel der Lufttemperatur im Ort (°C, Januar bis Dezember) [G]. */
    public static final double[] MONATSMITTEL = {-1.5, -1.0, 1.5, 5.0, 9.5, 13.0, 15.0, 14.5, 11.0, 6.5, 2.5, -0.5};

    /** Zielwerte je Lage (Spalten wie {@link Weather.Var}); im Fjord weniger Wind, keine Dünung, mehr Feuchte [G]. */
    public static final double[][] ZIELE = {
            //  Wind  Richt Böig Bed  hoch Wolke Regen Sicht Nebel Druck  Temp Feuchte Dünung Gewitter
            {3.0, 270, 0.20, 2.0, 0.10, 0, 0.0, 45, 0.0, 1022, 13, 70, 0, 0},    // klar: Fjordwind am Tag
            {4.0, 245, 0.22, 4.5, 0.60, 1, 0.0, 30, 0.0, 1012, 12, 80, 0, 0},    // aufziehend
            {6.5, 250, 0.28, 8.0, 0.20, 3, 3.5, 8, 0.0, 1000, 11, 96, 0, 0},     // Front: Landregen vom Atlantik
            {12.0, 250, 0.35, 7.5, 0.00, 3, 6.0, 6, 0.0, 985, 10, 93, 0, 1},     // Sturm (zwischen den Wänden gedämpft)
            {7.0, 260, 0.35, 7.0, 0.00, 4, 10.0, 5, 0.0, 1006, 14, 90, 0, 2},    // Gewitter, selten
            {5.0, 290, 0.25, 6.0, 0.10, 1, 0.8, 15, 0.0, 1006, 10, 88, 0, 0},    // abklingend: Schauer
            {3.5, 300, 0.20, 3.5, 0.10, 0, 0.0, 40, 0.0, 1015, 11, 78, 0, 0},    // aufklarend
            {0.8, 90, 0.08, 1.0, 0.20, 2, 0.0, 12, 0.85, 1022, 10, 99, 0, 0},    // Fjordnebel nach stiller Nacht
            {9.0, 110, 0.40, 2.5, 0.20, 0, 0.0, 80, 0.0, 1005, 17, 40, 0, 0},    // Föhn fjordauswärts aus Osten
            {22.0, 260, 0.35, 8.0, 0.00, 3, 8.0, 4, 0.0, 970, 8, 95, 0, 1}};     // Orkan (die Kette erreicht ihn nicht)

    /** Übergänge der freien Regie (Zeile: von, Spalte: nach; Reihenfolge wie {@link Weather.Phase}). */
    public static final double[][] UEBERGAENGE = {
            {0, 0.60, 0, 0, 0, 0, 0, 0.30, 0.10, 0},
            {0.15, 0, 0.85, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0.20, 0.05, 0.75, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 1, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 1, 0, 0, 0, 0},
            {0, 0, 0.25, 0, 0, 0, 0.75, 0, 0, 0},
            {0.70, 0, 0, 0, 0, 0, 0, 0.30, 0, 0},
            {0.80, 0.20, 0, 0, 0, 0, 0, 0, 0, 0},
            {0.50, 0.50, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0.50, 0, 0.50, 0, 0, 0, 0}};

    /** Mittlere Verweildauer je Lage (h). */
    public static final double[] VERWEILEN = {16, 6, 6, 6, 2, 4, 4, 5, 8, 6};

    /** Setzt das Klima in eine Wetterregie (Zeitzone Europe/Oslo, Sommer um den 15. Juli). */
    public static void setzen(Weather w) {
        w.zone = java.time.ZoneId.of("Europe/Oslo");
        w.climate = MONATSMITTEL.clone();
        w.summerDay = 196;
        w.targets = copy(ZIELE);
        w.transitions = copy(UEBERGAENGE);
        w.dwellHours = VERWEILEN.clone();
        w.seed = 62;
    }

    static double[][] copy(double[][] a) {
        double[][] r = new double[a.length][];
        for (int i = 0; i < a.length; i++) r[i] = a[i].clone();
        return r;
    }

    private static final Weather.Var W = Weather.Var.WIND, D = Weather.Var.DIRECTION, C = Weather.Var.COVER, F = Weather.Var.FOG,
            V = Weather.Var.VISIBILITY, H = Weather.Var.HUMIDITY, T = Weather.Var.TEMPERATURE, R = Weather.Var.RAIN, K = Weather.Var.CLOUD,
            G = Weather.Var.GUST;

    /**
     * „Ein Junitag“ (Abnahme Phase 4): ab 3:30 Uhr im Zeitraffer 12 min/s. Am Morgen liegt der Fjord im Schatten der
     * Wände, die Sonne erreicht das Wasser erst, wenn sie über die Kanten steigt; mittags Fjordwind aus Westen,
     * nachmittags Quellwolken über den Gipfeln, abends schläft der Wind ein.
     */
    public static Scenario junitag() {
        List<Scenario.Step> l = new ArrayList<>();
        l.add(Scenario.Step.of(2.0, Weather.Phase.KLAR, "Morgen im Schatten der Wände", "Plattform", W, 0.8, D, 90, C, 1.0, F, 0.1, V, 40, H, 90, T, 9, G, 0.1));
        l.add(Scenario.Step.of(4.0, Weather.Phase.KLAR, "Die Sonne steigt über die Wände", "Vom Wasser", W, 1.5, D, 120, C, 1.5, F, 0.0, T, 12));
        l.add(Scenario.Step.of(5.0, Weather.Phase.KLAR, "Fjordwind am Mittag", "Skageflå", W, 3.5, D, 270, C, 3.0, T, 17, H, 65));
        l.add(Scenario.Step.of(5.0, Weather.Phase.KLAR, "Quellwolken über den Gipfeln", "Plattform", W, 3.0, D, 265, C, 4.0, T, 18));
        l.add(Scenario.Step.of(4.0, Weather.Phase.AUFKLAREND, "Der Abend, der Wind schläft ein", "Anleger", W, 1.0, D, 100, C, 2.0, T, 14));
        l.add(Scenario.Step.of(3.5, Weather.Phase.KLAR, "Helle Nacht", "Vom Wasser", W, 0.5, C, 1.0, T, 10, H, 88));
        return new Scenario("Ein Junitag", 3.5, 720, l);
    }

    /**
     * „Ein Nebelmorgen im Fjord“: nach einer stillen, klaren Nacht liegt Nebel auf dem Wasser; nach Sonnenaufgang hebt er
     * sich an den Wänden und ist am Vormittag fort. Ab 4:30 Uhr, Zeitraffer 4 min/s.
     */
    public static Scenario nebelmorgen() {
        List<Scenario.Step> l = new ArrayList<>();
        l.add(Scenario.Step.of(2.0, Weather.Phase.NEBEL, "Nebel liegt im Fjord", "Plattform", F, 0.9, W, 0.5, D, 90, C, 1.0, V, 12, H, 99, T, 8, G, 0.05));
        l.add(Scenario.Step.of(1.5, Weather.Phase.NEBEL, "Die Sonne über dem Nebel", "Plattform", F, 0.85, W, 0.6, T, 9));
        l.add(Scenario.Step.of(1.5, Weather.Phase.NEBEL, "Der Nebel steigt an den Wänden", "Vom Wasser", F, 0.55, W, 1.2, H, 96, T, 11));
        l.add(Scenario.Step.of(1.0, Weather.Phase.KLAR, "Fetzen über dem Wasser", "Vom Wasser", F, 0.2, W, 2.0, C, 1.5, H, 88, T, 13));
        l.add(Scenario.Step.of(4.0, Weather.Phase.KLAR, "Ein klarer Tag", "Skageflå", F, 0.0, W, 3.0, D, 270, C, 2.0, V, 45, H, 72, T, 16));
        return new Scenario("Ein Nebelmorgen im Fjord", 4.5, 240, l);
    }

    /**
     * „Ein Regentag“: eine Front vom Atlantik zieht den Fjord herein, Landregen, die Wolken hängen in halber Höhe an
     * den Wänden; am Nachmittag Schauer, abends reißt es auf. Ab 6:00 Uhr, Zeitraffer 6 min/s.
     */
    public static Scenario regentag() {
        List<Scenario.Step> l = new ArrayList<>();
        l.add(Scenario.Step.of(2.5, Weather.Phase.AUFZIEHEND, "Wolken ziehen den Fjord herein", "Plattform", C, 6.0, W, 4.0, D, 250, H, 85, T, 12));
        l.add(Scenario.Step.of(6.0, Weather.Phase.FRONT, "Landregen, die Wolken hängen an den Wänden", "Anleger", C, 8.0, K, 3, R, 3.5, V, 8, W, 5.0, H, 97, T, 11));
        l.add(Scenario.Step.of(3.0, Weather.Phase.ABKLINGEND, "Schauer", "Vom Wasser", C, 6.5, R, 1.0, V, 15, W, 4.0, T, 11));
        l.add(Scenario.Step.of(3.5, Weather.Phase.AUFKLAREND, "Es reißt auf", "Skageflå", C, 3.5, R, 0.0, V, 35, W, 2.5, T, 12));
        return new Scenario("Ein Regentag", 6.0, 360, l);
    }

    /** Die Regiebücher für das Bedienfeld. */
    public static List<Scenario> regiebuecher() { List<Scenario> d = ausDaten; return d != null && !d.isEmpty() ? d : code(); }

    /** Die drei Wetter-Regiebücher aus dem Code. */
    public static List<Scenario> code() { return List.of(junitag(), nebelmorgen(), regentag()); }

    /** Phase 17 (F26 A, F36 A): die Wetter-Regiebücher aus GR_WETTERBUCH ({@code data.Laden}); null: der Code. */
    public static volatile List<Scenario> ausDaten;
}
