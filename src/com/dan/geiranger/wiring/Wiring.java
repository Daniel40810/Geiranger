package com.dan.geiranger.wiring;

import com.dan.cloud.CloudType;
import com.dan.cloud.Clouds;
import com.dan.fog.Fog;
import com.dan.geiranger.scene.Buehne;
import com.dan.rain.Rain;
import com.dan.sky.Sky;
import com.dan.weather.Scenario;
import com.dan.weather.Weather;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Die Verdrahtung der App (Paketvertrag Punkt 4): Hier und nur hier reden die Packages miteinander. Jedes Package
 * bietet Eingänge an und meldet Ereignisse über die kleinen Schnittstellen dieses Pakets ({@link Clock},
 * {@link Terrain}, {@link Water}, {@link Wind}, {@link Lighting}, {@link Wall}, {@link Discharge}); die Phasen 2 bis 17
 * hängen ihre Verbindungen hier ein. Bringt ein Package eine eigene Kopie einer Schnittstelle mit (gleicher Name,
 * gleiche Form), übersetzt {@code wiring} mit einer Zeile.
 * <p>
 * Phase 1: die Uhr, Wind von Hand (Bft 2 aus Westen, ein ruhiger Junitag im Fjord [G]), der Spiegel fest auf NN und
 * die flache Bühne als Gelände. Phase 2: das Gelände aus den HGT-Kacheln ersetzt die Bühne für Kamera und Ansichten. Phase 3: der gemalte Grund geht an ocean, ocean (Preset Fjord) gibt die Fläche ({@link #surface()}).
 * <p>
 * Phase 4: die Wetterregie ({@code com.dan.weather}) mit dem Klima „Geiranger“ ({@link KlimaGeiranger}) setzt je Bild
 * Wind, Böen, Bedeckung, Wolkenart, Regen, Sicht und Fjordnebel; daraus bekommen Himmel ({@code sky}), Wolken
 * ({@code cloud}), Regen ({@code rain}), der Fjordnebel und das Wolkenband an den Wänden (beide {@code fog}) ihre
 * Eingänge, der Wind geht an ocean. Ohne Regie ({@link #regie} aus) bleibt der Wind von Hand. {@link #planned()} nennt, was nach dem Phasenplan kommt, {@link #links()} was schon verbunden ist.
 * <p>
 * Phase 7: {@link Abfluss} gibt jedem Fall seinen Abfluss ({@link Discharge}) aus Jahreszeit, Regen und Frost; cliff gibt
 * fall die Wand (Wandkarte und Feinform), fall gibt cliff die nassen Streifen und ocean die Ringwellen.
 */
public final class Wiring {
    /** Eine Verbindung: von wem, an wen, über welche Schnittstelle, in welcher Phase. */
    public record Link(String from, String to, String via, int phase) {
        @Override public String toString() { return from + " → " + to + " (" + via + ", Phase " + phase + ")"; }
    }

    /** Der Wind: von Hand oder seit Phase 4 aus der Regie ({@link #regie}). Start: Bft 2 aus Westen, fjordeinwärts [G]. */
    public static final class ManualWind implements Wind {
        public volatile double speed = Beaufort.mid(2), fromDeg = 270, gust = 0.3;
        @Override public double speed() { return speed; }
        @Override public double fromDeg() { return fromDeg; }
        @Override public double gustiness() { return gust; }
    }

    /** Der mittlere Spiegel des Fjords: fest auf NN, von Hand verschiebbar; die Wellen darauf rechnet seit Phase 3 ocean. */
    public static final class Spiegel implements Water {
        public volatile double level;
        @Override public double surfaceAt(double x, double z) { return level; }
        @Override public double level() { return level; }
    }

    public final Clock clock;
    public final ManualWind wind = new ManualWind();
    /** Phase 7: der Abfluss der Fälle aus Jahreszeit, Schmelze, Regen und Frost, mit Regler von Hand (F13 A). */
    public final Abfluss abfluss = new Abfluss();
    public final Spiegel water = new Spiegel();
    private volatile Terrain terrain = Buehne::height;
    private final List<Link> links = new ArrayList<>();

    /** Was der Phasenplan (Werkbuch, Abschnitt 9) noch verbindet. */
    private static final List<Link> PLANNED = List.of();

    public Wiring(Clock clock) {
        this.clock = clock;
        links.add(new Link("SceneClock", "Kamera, Himmel-Platzhalter, HUD", "Clock", 1));
        links.add(new Link("HGT-Kacheln (Gelände)", "Kamera, Ansichten, Höhe am Auge", "Terrain", 2));
        links.add(new Link("Wind von Hand", "HUD, Bedienfeld", "Wind", 1));
        links.add(new Link("Spiegel auf NN", "Kamera", "Water", 1));
        links.add(new Link("Fjordgrund (gemalt)", "ocean", "Terrain", 3));
        links.add(new Link("ocean (Preset Fjord)", "Kamera, Spiegelbild der Wände", "Water", 3));
        links.add(new Link("weather (Klima Geiranger)", "Wind → ocean, cloud, rain, fog", "Wind, Böe", 4));
        links.add(new Link("weather", "sky, cloud, rain, fog (Fjordnebel, Wolkenband)", "Bedeckung, Art, Regen, Sicht, Nebel", 4));
        links.add(new Link("Uhr", "sky", "Clock → Zeit, Breite, Länge (Sonne über Süden, Mond, Sterne über dem Fjord)", 4));
        links.add(new Link("sky", "Gelände, ocean, cloud, fog, rain", "Lighting (Sonne, Mond, Himmel, Dunst, vorbelichtet)", 4));
        links.add(new Link("Gelände", "Gelände, ocean", "Schatten der Wände je Sonnenstand", 4));
        links.add(new Link("cloud", "Gelände, ocean", "Wolkenschatten (Karte um das Auge)", 4));
        links.add(new Link("sky", "cliff (Gelände, Überhänge)", "Lighting", 5));
        links.add(new Link("cliff (Wandkarte, Feinform)", "fall (Wand für den Lauf über Fels)", "Wall", 7));
        links.add(new Link("Abfluss (Jahreszeit, Regen, Frost)", "fall", "Discharge", 7));
        links.add(new Link("fall", "cliff (nasse Streifen), ocean (Ringwellen)", "Wall, Water", 7));
        links.add(new Link("weather", "fall (Wind, Temperatur), Abfluss (Regen)", "Wind, Temperatur, Regen", 7));
        links.add(new Link("Abfluss", "river (Geirangelva), fall (Storfossen, Kaskaden)", "Discharge", 8));
        links.add(new Link("river (Steine, Spiegel)", "Bauten am Ufer, fall (Stufen enden im Fluss)", "Water", 8));
        links.add(new Link("road (Ørnevegen, Trasse)", "Gelände (Feinraster: Einschnitt, Damm), Plattform", "Terrain", 9));
        links.add(new Link("weather, sky", "road (Nässe, Licht der Fahrzeuge), Verkehr nach der Uhrzeit", "Regen, Wind, Sonnenhöhe, Clock", 9));
        links.add(new Link("Gelände, cliff (Wandkarte), road, river, Höfe", "forest, ground (Biom Vestland: wo was wächst)", "Terrain, Wall", 10));
        links.add(new Link("weather, sky, Schatten der Wände", "forest, ground (Wind, Licht, Jahreszeit)", "Wind, Lighting, Clock", 10));
        links.add(new Link("Gelände, road (Fv63 im Ort, Strandvegen), river", "Ort (Bauten), timber (Naust, Anleger, Höfe), forest, ground (frei um die Bauten)", "Terrain", 11));
        links.add(new Link("sky, Uhr, Schatten der Wände", "Ort (Fenster und Laternen nachts), timber (Licht, Laternen, Wasserlinie)", "Lighting, Clock, Water", 11));
        links.add(new Link("Uhr (Fahrplan), ocean (Höhe)", "Schiffe: Lage nach Fahrplan, buoyancy (Tauchen, Stampfen, Rollen); Kielwasser (iWave) → ocean (lange Welle), Rümpfe → ocean (HullMask)", "Clock, Water", 12));
        links.add(new Link("Avatar (Gäste), figure (Busgruppe, Wanderer, Treppe)", "Szene: Plattform, Storfossen, Pfad nach Skageflå", "Terrain, Clock", 13));
        links.add(new Link("season (Jahr in zehn Höhenstufen)", "Gelände und cliff (Schnee je Höhe), forest und ground (Laub, Herbst, Kronen- und Bodenschnee), fall (Frost), road (Schnee), Schneefall", "Clock, Temperatur, Regen", 14));
        links.add(new Link("fall (Pegel), Abfluss, Gelände (Grate), river, Schiffe, road, Menschen, weather", "soundscape (Ohr an der Kamera): Fälle, Elva, Maschinen, Stimmen, Wind, Regen, See",
                "Discharge, Terrain, Wind, Clock", 15));
        links.add(new Link("Schiffe (Horn), Kirche (Glocke)", "soundscape mit Echo der Wände (Spiegelquellen aus dem Gelände)", "Terrain, Wall, Clock", 15));
        links.add(new Link("Regiebücher (Text), kino", "Uhr, Zeitraffer, weather, Kamera (Ansichten, Fahrt über die Ørnesvingen, Mitfahrt auf der Fähre), Fotos, Blatt", "Clock, Wind", 16));
        links.add(new Link("store (Schema GEIRANGER oder daten/)", "Fahrplan, Klima, Höhenstufen, Wetter- und Regiebücher beim Start; Chronik (Stunden, Tage, Fahrten mit Horn und Echo, Busse), Sitzung, Merkpunkte, Ansichten, Presets",
                "Clock, Discharge, Wind", 17));
        KlimaGeiranger.setzen(weather);
    }

    // ------------------------------------------------------------------ Phase 4: Wetter

    /** Die Wetterregie mit dem Klima „Geiranger“; Tagesgang in Ortszeit (Europe/Oslo). */
    public final Weather weather = new Weather();
    /** Ob die Regie die Eingänge setzt (die App schaltet sie ein; Prüfungen und Werkzeuge setzen die Felder selbst). */
    public volatile boolean regie;
    /** Wind in der laufenden Böe (m/s), Lufttemperatur (°C), Luftdruck (hPa). */
    public volatile double gustNow = Beaufort.mid(2), airTempC = 15, pressureHPa = 1018;
    /** Sicht in km, Regen in mm/h, Bedeckung in Achteln, hohe Schicht 0..1, Fjordnebel 0..1. */
    public volatile double visibilityKm = 45, rainRate, oktas = 2, highCover = 0.1, fogAmount;
    public volatile CloudType cloudType = CloudType.CUMULUS;
    /** Wolkenband an den Wänden: −1 aus dem Wetter, sonst 0..1 von Hand. */
    public volatile double bandHand = -1;
    /** Was das Wolkenband zuletzt war (für HUD und Prüfung): Menge, Unter- und Obergrenze (m über NN). */
    public volatile double bandAmount, bandBase, bandTop;
    /** Unter- und Obergrenze des Fjordnebels zuletzt (m über dem Spiegel). */
    public volatile double fogBase, fogTop;
    /** Ebenen ein und aus (Bedienfeld). */
    public volatile boolean fogOn = true, bandOn = true;
    /** Zeichnet die App echte Wolken? Dann bekommt der Himmel keinen Schleier. */
    public volatile boolean cloudsDrawn;

    /** Die Regie einen Schritt weiter (Szenenzeit aus der Uhr, Böen in Echtzeit) und ihre Werte übernehmen. */
    public Weather.Snapshot feedWeather(double realDt) {
        Weather.Snapshot s = weather.update(clock.epochMillis() / 1000.0, realDt);
        apply(s);
        return s;
    }

    /** Handregler: eine Größe der Regie sofort setzen und halten, bis „Regie weiter“. Ohne Regie: Wind direkt. */
    public void hand(Weather.Var v, double value) {
        if (!regie) {
            if (v == Weather.Var.WIND) wind.speed = value;
            else if (v == Weather.Var.DIRECTION) wind.fromDeg = value;
            else if (v == Weather.Var.COVER) oktas = value;
            else if (v == Weather.Var.RAIN) rainRate = value;
            else if (v == Weather.Var.FOG) fogAmount = value;
            else if (v == Weather.Var.VISIBILITY) visibilityKm = value;
            return;
        }
        weather.set(v, value);
        Weather.Snapshot s = weather.snapshot();
        if (s != null) apply(s);
    }

    /** „Regie weiter“: alle Handregler lösen. */
    public void resumeRegie() {
        weather.release();
        bandHand = -1;
    }

    /** Freie Regie ab jetzt, beginnend mit einer Lage. */
    public void startFree(Weather.Phase p) {
        regie = true;
        weather.startFree(clock.epochMillis() / 1000.0, p);
        apply(weather.update(clock.epochMillis() / 1000.0, 0));
    }

    /** Ein Regiebuch ab seinem Beginn am laufenden Tag (die Uhr springt dorthin, der Zeitraffer folgt dem Buch). */
    public void start(Scenario s) {
        regie = true;
        double t0 = startOf(s);
        weather.start(s, t0);
        apply(weather.update(clock.epochMillis() / 1000.0, 0));
    }

    /** Beginn eines Regiebuchs am Tag der Uhr (s seit 1970). */
    public double startOf(Scenario s) {
        java.time.ZonedDateTime d = java.time.Instant.ofEpochMilli((long) clock.epochMillis()).atZone(java.time.ZoneId.of("Europe/Oslo"))
                .toLocalDate().atStartOfDay(java.time.ZoneId.of("Europe/Oslo"));
        return d.toEpochSecond() + s.startHour() * 3600;
    }

    private void apply(Weather.Snapshot s) {
        if (s == null) return;
        wind.speed = s.wind();
        wind.fromDeg = s.fromDeg();
        wind.gust = s.gustiness();
        gustNow = s.gustNow();
        oktas = s.oktas();
        highCover = s.high();
        cloudType = CloudType.valueOf(s.cloud().name());
        rainRate = Math.max(0, s.rain());
        visibilityKm = Math.max(0.2, s.visibilityKm());
        fogAmount = s.fog();
        airTempC = s.temperature();
        pressureHPa = s.pressure();
    }

    /** Dunst als Auslöschung je m aus der Sicht (Koschmieder). */
    public double hazePerM() { return 2.996 / (Math.max(0.5, visibilityKm) * 1000); }

    /**
     * Dem Himmel Zeit, Ort, Augenhöhe, Sicht und Bedeckung (je Bild, vor {@code sky.step}). Rückstrahlung des Bodens
     * [G]: Wald, Fels und dunkles Wasser, um 0,10.
     */
    public void feed(Sky sky, double eyeAsl, double latDeg, double lonDeg) {
        sky.epochMillis = clock.epochMillis();
        sky.latDeg = latDeg;
        sky.lonDeg = lonDeg;
        sky.eyeHeight = Math.max(1, eyeAsl);
        sky.visibilityKm = visibilityKm;
        sky.groundAlbedo = 0.10;
        sky.cloudCover = cloudsDrawn ? 0 : oktas / 8;
        sky.cloudVeil = !cloudsDrawn;
        sky.rainbow = 0;
    }

    /** Den Wolken Bedeckung, Art, hohe Schicht und den Wind in Wolkenhöhe (über den Gipfeln freier: 2,2-fach, 20° rechtsdrehend) [G]. */
    public void feed(Clouds c) {
        c.oktas = oktas;
        c.type = cloudType;
        c.highCover = highCover;
        c.windSpeed = Math.max(3, wind.speed() * 2.2);
        c.windFromDeg = (wind.fromDeg() + 20) % 360;
        c.lens = 0;
    }

    /** Dem Regen Rate, Wind, Wolkenuntergrenze, Spiegel und Auge; mit gezeichneten Wolken nur unter ihren dichten Stellen. */
    public void feed(Rain r, Clouds c, Clouds.Snapshot cs, boolean cloudsShown, double[] eye) {
        double rate = Math.max(0, rainRate);
        c.precipitation = rate > 0 ? Math.min(1, 0.35 + rate / 25) : 0;
        r.rate = rate;
        r.windSpeed = regie ? gustNow : wind.speed();
        r.windFromDeg = wind.fromDeg();
        r.gust = wind.gustiness();
        r.waterLevel = water.level();
        r.cloudBase = Math.min(cs.type().base, bandAmount > 0.3 ? bandBase : 1e9);
        r.eyeX = eye[0];
        r.eyeY = eye[1];
        r.eyeZ = eye[2];
        // im Fjord regnet es bei Landregen überall: die Wolkendecke ist geschlossen, das Band hängt darunter
        if (!cloudsShown || rate <= 0 || oktas >= 7) { r.curtain = null; return; }
        double p = Math.max(1e-3, cs.precipitation());
        r.curtain = (x, z) -> Math.min(1, Clouds.rain(cs, x, z) / p * 2);
    }

    /**
     * Dem Fjordnebel Menge, Sicht, Bedeckung und Höhe [G]: Nebel n (0..1) gibt die Menge (ab 0,02 bis 0,6 voll), die
     * Sicht in der Bank 3 km · (40/3000)^(n^0,6), die Obergrenze 40 + 160 n² m über dem Spiegel (der Nebel füllt den
     * Fjord, die Wände ragen heraus). Steigt die Sonne über 3°, hebt er sich an den Wänden; Wind über 5 m/s reißt ihn
     * auf.
     */
    public void feedNebel(Fog f, double sunAltDeg) {
        double n = Math.max(0, Math.min(1, fogAmount));
        double windCut = 1 - smooth(5, 9, wind.speed());
        double lift = smooth(3, 20, sunAltDeg);
        double top = (40 + 160 * n * n) * (1 + 0.8 * lift);
        f.amount = fogOn ? smooth(0.02, 0.6, n) * windCut : 0;
        f.visibilityM = 3000 * Math.pow(40 / 3000.0, Math.pow(n, 0.6));
        f.coverage = Math.min(1, 0.6 + 0.5 * n);
        f.top = top;
        f.base = 0.8 * top * lift;
        f.bankSize = 1800;
        f.wisps = 0.45;
        f.windSpeed = wind.speed();
        f.windFromDeg = wind.fromDeg();
        f.waterLevel = water.level();
        f.hazeVisibilityM = visibilityKm * 1000;
        fogTop = top;
        fogBase = f.base;
    }

    /**
     * Dem Wolkenband an den Wänden [G]: bei tiefer, dichter Decke (Stratus, Nimbostratus, Stratocumulus ab 5/8) und
     * feuchter Luft hängt eine Schicht in halber Höhe an den Wänden, Unterkante an der Wolkenbasis (250 bis 900 m),
     * 300 bis 550 m dick; in ihr sieht man 150 m weit. Starker Wind zerreißt sie. Gezeichnet als zweite Nebelschicht,
     * damit sie die Wände verdeckt, wo sie vor ihnen liegt.
     */
    public void feedBand(Fog f) {
        double low = cloudType == CloudType.STRATUS || cloudType == CloudType.NIMBOSTRATUS ? 1 : cloudType == CloudType.STRATOCUMULUS ? 0.7 : 0.3;
        double a = bandHand >= 0 ? bandHand : smooth(4.5, 7.5, oktas) * low * (1 - smooth(9, 15, wind.speed()));
        double base = Math.max(250, Math.min(900, cloudType.base * 0.8));
        double top = base + 300 + 250 * smooth(5, 8, oktas);
        f.amount = bandOn ? a : 0;
        f.visibilityM = 150;
        f.coverage = Math.min(1, 0.45 + 0.5 * a);
        f.top = top;
        f.base = base;
        f.bankSize = 1400;
        f.wisps = 0.7;
        f.windSpeed = Math.max(1, wind.speed() * 1.5);
        f.windFromDeg = wind.fromDeg();
        f.waterLevel = 0;
        f.hazeVisibilityM = visibilityKm * 1000;
        bandAmount = f.amount;
        bandBase = base;
        bandTop = top;
    }

    /** „Regie: Ein Junitag, Fjordwind am Mittag“ oder „Wind von Hand“. */
    public String weatherLine() {
        Weather.Snapshot s = weather.snapshot();
        String src = !regie || s == null ? "von Hand" : (s.scenario().isEmpty() ? "freie Regie, " + s.phase().label : s.scenario() + ": " + s.stepText())
                + (s != null && regie && weather.overrides() > 0 ? ", " + weather.overrides() + " von Hand" : "");
        return String.format(Locale.GERMAN, "Wetter %s · %.0f/8 %s · Regen %.1f mm/h · Sicht %.0f km · Nebel %.0f %% · %.0f °C",
                src, oktas, cloudType.label, rainRate, visibilityKm, fogAmount * 100, airTempC);
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /**
     * Phase 3: die Fläche des Fjords, wie ocean sie rechnet (Spiegel plus Wellen); bis ocean läuft und ohne
     * Grafikkarte der feste {@link #water}. fall (Phase 6) und buoyancy (Phase 12) lesen hier.
     */
    public Water surface() { return surface; }
    public void setSurface(Water w) { surface = w == null ? water : w; }
    private volatile Water surface = water;

    /** Das Gelände, das die Packages bekommen (seit Phase 2 aus den HGT-Kacheln, sonst die Bühne). */
    public Terrain terrain() { return terrain; }
    public void setTerrain(Terrain t) { terrain = t == null ? Buehne::height : t; }

    public List<Link> links() { return Collections.unmodifiableList(links); }

    /** Was nach dem Phasenplan noch kommt. */
    public static List<Link> planned() { return PLANNED; }

    /** „4 Verbindungen, 18 geplant · Wind Bft 2 aus 270° · Spiegel NN +0,00 m“ */
    public String summary() {
        return String.format(Locale.GERMAN, "%d Verbindungen, %d geplant · Wind Bft %d aus %03.0f° · Spiegel NN %+.2f m",
                links.size(), PLANNED.size(), wind.beaufort(), wind.fromDeg(), water.level());
    }
}
