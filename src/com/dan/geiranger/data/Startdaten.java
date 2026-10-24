package com.dan.geiranger.data;

import com.dan.geiranger.camera.Ansichten;
import com.dan.geiranger.gl.Renderer;
import com.dan.geiranger.scene.Bauten;
import com.dan.geiranger.scene.Dorf;
import com.dan.geiranger.scene.Faelle;
import com.dan.geiranger.scene.Flotte;
import com.dan.geiranger.scene.Hoefe;
import com.dan.geiranger.scene.Jahr;
import com.dan.geiranger.scene.Leute;
import com.dan.geiranger.scene.Ornevegen;
import com.dan.geiranger.scene.Regie;
import com.dan.geiranger.scene.SceneClock;
import com.dan.geiranger.scene.Site;
import com.dan.geiranger.scene.Terrain;
import com.dan.geiranger.wiring.Abfluss;
import com.dan.geiranger.wiring.KlimaGeiranger;
import com.dan.store.Felder;
import com.dan.store.Zahl;
import com.dan.weather.Scenario;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Die mitgelieferten Daten (Phase 17, Werkbuch Abschnitt 8, F25–F37 „alle A“). Der Code bleibt die Quelle (F26 A):
 * Quellen und Befunde aus {@code quellen.txt} und {@code befunde.txt} in diesem Package, Klima und Höhenstufen aus
 * {@code wiring.KlimaGeiranger}, {@code scene.Jahr} und {@code wiring.Abfluss}, der Fahrplan aus {@code scene.Flotte},
 * Wetter- und Regiebücher, Ansichten und die Presets der Packages; was das Gelände braucht (Raster, Formen, Höhenlinien,
 * Fälle mit den Wegen der Strähnen, Elva, Straße, Kehren, Orte, Gebäude, Schiffe und Wege, die Fahrt über die
 * Ørnesvingen), aus einer {@link Szene}. Daraus schreibt {@code tools.StartdatenSql} das Skript
 * {@code db/sql/04_startdaten.sql}; ohne Datenbank liest {@link DateiSpeicher} die leichten Listen.
 * <p>
 * Die Werte, die die App beim Start aus der Datenbank übernimmt ({@link Laden}), stehen hier als Abschrift des Codes,
 * genommen, bevor sie jemand überschreibt.
 */
public final class Startdaten {
    private Startdaten() { }

    // ------------------------------------------------------------------ Abschrift des Codes (vor Laden)

    static final double[] TEMP = KlimaGeiranger.MONATSMITTEL.clone(), NIEDERSCHLAG = Jahr.NIEDERSCHLAG.clone(), REGENTAGE = Jahr.REGENTAGE.clone(),
            ABFLUSS = Abfluss.MONAT.clone(), HOEHEN = Jahr.HOEHEN.clone();
    static final double LAPSE = Jahr.LAPSE_CODE, MEHR_JE_M = Jahr.MEHR_JE_M_CODE;

    /** Lädt diese Klasse (und nimmt damit die Abschrift), bevor {@link Laden} etwas überschreibt. */
    static void sichern() { if (TEMP.length != 12) throw new IllegalStateException("Klima ohne zwölf Monate"); }

    // ------------------------------------------------------------------ Textdateien im Package

    static List<String[]> zeilen(String datei) {
        try (InputStream in = Startdaten.class.getResourceAsStream(datei)) {
            if (in == null) throw new IllegalStateException(datei + " fehlt im Package com.dan.geiranger.data");
            List<String[]> l = new ArrayList<>();
            for (String z : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (z.isBlank() || z.startsWith("#")) continue;
                String[] p = z.split("\\|", -1);
                for (int i = 0; i < p.length; i++) p[i] = p[i].strip();
                l.add(p);
            }
            return l;
        } catch (IOException e) {
            throw new IllegalStateException(datei + " nicht lesbar: " + e.getMessage(), e);
        }
    }

    private static String leer(String s) { return s == null || s.isEmpty() ? null : s; }

    public static List<Daten.Quelle> quellen() {
        List<Daten.Quelle> l = new ArrayList<>();
        for (String[] p : zeilen("quellen.txt"))
            l.add(new Daten.Quelle(p[0], p[3], leer(p[4]), p[1], p[2].isEmpty() ? null : LocalDate.parse(p[2]), leer(p[5])));
        return l;
    }

    public static List<Daten.Befund> befunde() {
        List<Daten.Befund> l = new ArrayList<>();
        for (String[] p : zeilen("befunde.txt")) {
            List<String> q = new ArrayList<>();
            if (p.length > 4) for (String x : p[4].split(",")) if (!x.isBlank()) q.add(x.strip());
            l.add(new Daten.Befund(p[0], p[1], p[2], p[3], q));
        }
        return l;
    }

    // ------------------------------------------------------------------ Klima und Jahr (F36)

    static final String[] MONATE = {"Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember"};

    public static List<Daten.KlimaMonat> klima() {
        List<Daten.KlimaMonat> l = new ArrayList<>();
        for (int m = 0; m < 12; m++)
            l.add(new Daten.KlimaMonat(m + 1, MONATE[m], TEMP[m], NIEDERSCHLAG[m], REGENTAGE[m], ABFLUSS[m],
                    m == 5 ? "Abfluss der Fälle voll (Schmelze), Bezug aller Anteile" : m <= 1 || m == 11 ? "Fälle unter dem Eis, Rest" : null));
        return l;
    }

    public static List<Daten.Hoehenstufe> hoehenstufen() {
        List<Daten.Hoehenstufe> l = new ArrayList<>();
        double jahr = 0;
        for (double n : NIEDERSCHLAG) jahr += n;
        for (int i = 0; i < HOEHEN.length; i++) {
            double h = HOEHEN[i];
            l.add(new Daten.Hoehenstufe(i + 1, h, LAPSE, MEHR_JE_M, r2(TEMP[0] - LAPSE * h), r2(TEMP[6] - LAPSE * h), r1(jahr * (1 + MEHR_JE_M * h))));
        }
        return l;
    }

    // ------------------------------------------------------------------ Fahrplan (F31)

    public static final String LINIE = "Geiranger–Hellesylt";
    public static final int FAHRPLAN_JAHR = 2025;

    /**
     * Der Fahrplan des Codes: zwei Fähren im Wechsel, zu jeder Abfahrtszeit eine ab Geiranger und eine ab Hellesylt
     * (MF Veøy beginnt den Tag in Geiranger, MF Bolsøy in Hellesylt), 65 min, vom 16. Mai bis 14. September 2025.
     */
    public static List<Daten.Fahrplan> fahrplan() {
        List<Daten.Fahrplan> l = new ArrayList<>();
        String von = mmdd(Flotte.SAISON_AB_CODE), bis = mmdd(Flotte.SAISON_BIS_CODE);
        int dauer = (int) Math.round(Flotte.FAHRZEIT_CODE / 60);
        for (double h : Flotte.ABFAHRTEN_CODE) {
            String ab = Daten.hm((int) Math.round(h * 60));
            l.add(new Daten.Fahrplan(LINIE, "NACH_HELLESYLT", ab, dauer, von, bis, FAHRPLAN_JAHR, "WP_FAEHRE", null, Daten.MITGELIEFERT));
            l.add(new Daten.Fahrplan(LINIE, "NACH_GEIRANGER", ab, dauer, von, bis, FAHRPLAN_JAHR, "WP_FAEHRE", null, Daten.MITGELIEFERT));
        }
        return l;
    }

    static String mmdd(MonthDay d) { return String.format(Locale.ROOT, "%02d-%02d", d.getMonthValue(), d.getDayOfMonth()); }

    // ------------------------------------------------------------------ Wetter- und Regiebücher, Ansichten

    static final Map<String, String> WETTER_TEXT = Map.of(
            "Ein Junitag", "Ab 3:30 im Zeitraffer: der Fjord am Morgen im Schatten der Wände, mittags Fjordwind aus Westen, nachmittags Quellwolken, abends still",
            "Ein Nebelmorgen im Fjord", "Nach stiller Nacht liegt Nebel im Fjord und löst sich am Vormittag auf",
            "Ein Regentag", "Eine Front vom Atlantik: Landregen, das Wolkenband hängt in halber Höhe an den Wänden, die Regenfälle laufen");

    public static List<Daten.Wetterbuch> wetterbuecher() {
        List<Daten.Wetterbuch> l = new ArrayList<>();
        for (Scenario s : KlimaGeiranger.code())
            l.add(new Daten.Wetterbuch(s.name(), s.startHour(), s.lapse(), s.steps().size(), WETTER_TEXT.get(s.name()), s.toText(), Daten.MITGELIEFERT));
        return l;
    }

    /** Kopfwerte eines Regiebuchs für GR_REGIEBUCH: Szenenzeit des ersten {@code zeit}, Zeitraffer, Fotos, Warten, Blatt. */
    public record Kopf(Long szeneMs, Double raffer, int fotos, int warten, boolean blatt) {
        public static Kopf aus(Regie.Book b) {
            Long t = null;
            Double r = null;
            int fotos = 0, warten = 0;
            boolean blatt = false;
            for (Regie.Zeile z : b.zeilen()) {
                switch (z.befehl()) {
                    case "zeit" -> {
                        if (t != null) break;
                        try {
                            String[] p = z.argumente().strip().split("\\s+");
                            String[] d = p[0].split("\\.");
                            String[] u = p.length > 1 ? p[1].split(":") : new String[]{"0", "0"};
                            t = ZonedDateTime.of(LocalDate.of(Integer.parseInt(d[2]), Integer.parseInt(d[1]), Integer.parseInt(d[0])),
                                    LocalTime.of(Integer.parseInt(u[0]), Integer.parseInt(u[1])), SceneClock.ZONE).toInstant().toEpochMilli();
                        } catch (RuntimeException e) { /* bleibt leer */ }
                    }
                    case "raffer" -> { if (r == null) r = Zahl.lies(z.argumente().strip()); }
                    case "foto" -> fotos++;
                    case "warte" -> warten++;
                    case "blatt" -> blatt = true;
                    default -> { }
                }
            }
            return new Kopf(t, r, fotos, warten, blatt);
        }
    }

    /** Die Zeilen „Wetter:“ und „Schritt:“ eines Regiebuchs (der Wetterplan) oder null. */
    public static String wetterBlock(String text) {
        StringBuilder b = new StringBuilder();
        for (String z : text.split("\\R")) {
            String s = z.strip(), low = s.toLowerCase(Locale.ROOT);
            if (low.startsWith("wetter:") || low.startsWith("schritt:")) b.append(b.length() > 0 ? "\n" : "").append(s);
        }
        return b.length() == 0 ? null : b.toString();
    }

    public static List<Daten.Regiebuch> regiebuecher() {
        List<Daten.Regiebuch> l = new ArrayList<>();
        for (String d : Regie.DATEIEN) {
            String t = Regie.text(d);
            Regie.Book b = Regie.parse(t);
            l.add(new Daten.Regiebuch(b.name(), t, b.about(), d, Daten.MITGELIEFERT));
        }
        return l;
    }

    /** Die neun festen Ansichten (Tasten 1–9). Kamera: x, Augenhöhe NaN (aus dem Gelände), z, Blick, Neigung. */
    public static List<Daten.Ansicht> ansichten() {
        List<Daten.Ansicht> l = new ArrayList<>();
        for (Ansichten.Ansicht a : Ansichten.ALLE)
            l.add(new Daten.Ansicht(0, a.name(), a.key(), "FEST", new double[]{a.x(), Double.NaN, a.z(), a.heading(), a.pitch()}, a.eyeAboveGround(), null, null, null,
                    Daten.MITGELIEFERT));
        return l;
    }

    public static final String FAHRT_ORNESVINGEN = "Fahrt über die Ørnesvingen";

    /** Die Fahrt ohne Gelände: ohne Stützpunkte. */
    public static List<Daten.Fahrt> fahrten() {
        return List.of(new Daten.Fahrt(FAHRT_ORNESVINGEN, "STRASSE", null, null, BESCHREIBUNG_FAHRT, Daten.MITGELIEFERT, List.of()));
    }

    static final String BESCHREIBUNG_FAHRT = "Im Bus vom Ufer bei Grande (400 m vor der ersten Kehre) die elf Kehren hinauf bis zur Busbucht an der Plattform, Auge 2,6 m "
            + "über der rechten Spur, am Ende der Blick über den Fjord (Regiebuch „Fahrt über die Ørnesvingen“)";

    // ------------------------------------------------------------------ Presets (F34)

    /** Die Klassen, deren Presets mitkommen: jede öffentliche statische Methode ohne Argument, die die Klasse liefert. */
    public static final String[] PAKETE = {"com.dan.ocean.Ocean", "com.dan.sky.Sky", "com.dan.cloud.Clouds", "com.dan.fog.Fog", "com.dan.rain.Rain",
            "com.dan.cliff.Cliff", "com.dan.fall.Fall", "com.dan.forest.Species", "com.dan.ground.Plant", "com.dan.road.WayType", "com.dan.season.Climate",
            "com.dan.soundscape.Soundscape", "com.dan.buoyancy.Buoyancy"};

    /** Die Stände der Szene: je Klasse Name → Hersteller (zusätzlich zu den Presets der Packages). */
    static Map<String, Map<String, Supplier<Object>>> szenePresets() {
        Map<String, Map<String, Supplier<Object>>> m = new LinkedHashMap<>();
        m.put("com.dan.ocean.Ocean", Map.of("Fjord (Szene, Bft 2)", () -> com.dan.geiranger.gl.Wasser.fjord(2)));
        m.put("com.dan.sky.Sky", Map.of("Szene", com.dan.sky.Sky::new));
        m.put("com.dan.cliff.Cliff", Map.of("Gneis (Szene)", com.dan.cliff.Cliff::new));
        m.put("com.dan.fall.Fall", Map.of("Szene", com.dan.fall.Fall::new));
        List<com.dan.forest.Species> arten = com.dan.geiranger.scene.Vestland.arten();
        String[] an = {"Birke (Vestland)", "Erle (Vestland)", "Waldkiefer (Vestland)", "Fichte (Vestland)"};
        Map<String, Supplier<Object>> sp = new LinkedHashMap<>();
        for (int i = 0; i < arten.size() && i < an.length; i++) { Object o = arten.get(i); sp.put(an[i], () -> o); }
        m.put("com.dan.forest.Species", sp);
        Map<String, Supplier<Object>> wt = new LinkedHashMap<>();
        wt.put("Ørnevegen (Szene)", Ornevegen::typ);
        wt.put("Strandvegen (Szene)", Ornevegen::typDorf);
        m.put("com.dan.road.WayType", wt);
        m.put("com.dan.season.Climate", Map.of("Geiranger (Ort, Szene)", () -> new com.dan.season.Climate(TEMP.clone(), NIEDERSCHLAG.clone(), REGENTAGE.clone())));
        return m;
    }

    private static List<Daten.PaketPreset> presets;

    public static synchronized List<Daten.PaketPreset> presets() {
        if (presets != null) return presets;
        List<Daten.PaketPreset> l = new ArrayList<>();
        Map<String, Map<String, Supplier<Object>>> szene = szenePresets();
        for (String k : PAKETE) {
            try {
                Class<?> c = Class.forName(k);
                for (Map.Entry<String, Supplier<Object>> e : szene.getOrDefault(k, Map.of()).entrySet())
                    l.add(new Daten.PaketPreset(c.getPackageName(), c.getSimpleName(), e.getKey(), "Stand der Szene Geiranger", Daten.MITGELIEFERT, ohneUhr(Felder.lesen(e.getValue().get()))));
                List<Method> ms = new ArrayList<>();
                for (Method m : c.getMethods())
                    if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0 && m.getReturnType() == c) ms.add(m);
                ms.sort(Comparator.comparing(Method::getName));
                for (Method m : ms) {
                    Object o = m.invoke(null);
                    l.add(new Daten.PaketPreset(c.getPackageName(), c.getSimpleName(), m.getName(), "Preset " + m.getName() + "() aus " + c.getPackageName(),
                            Daten.MITGELIEFERT, ohneUhr(Felder.lesen(o))));
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Preset von " + k + " nicht lesbar: " + e, e);
            }
        }
        presets = Collections.unmodifiableList(l);
        return presets;
    }

    /** Ein Preset hält keine Uhrzeit (sonst wäre 04_startdaten.sql bei jedem Erzeugen anders); die Uhr führt die Szene. */
    static Map<String, String> ohneUhr(Map<String, String> f) {
        Map<String, String> m = new LinkedHashMap<>(f);
        m.keySet().removeIf(k -> k.equals("epochMillis") || k.endsWith(".epochMillis"));
        return m;
    }

    /** Die Typen der Felder eines Presets (DOUBLE, INT, BOOLEAN, STRING, ENUM …) aus seiner Klasse. */
    public static Map<String, String> typen(Daten.PaketPreset p) {
        try {
            return Felder.typen(Class.forName(p.paket() + "." + p.klasse()));
        } catch (ClassNotFoundException e) {
            return Map.of();
        }
    }

    // ------------------------------------------------------------------ ohne Gelände (Dateien)

    /** Die benannten Fälle ohne Gelände: Fuß statt Kante, ohne Wege. */
    public static List<Daten.Fall> faelleLeicht() {
        List<Daten.Fall> l = new ArrayList<>();
        for (Faelle.Def d : Faelle.BENANNT)
            l.add(new Daten.Fall(d.name(), d.art().name(), d.x(), d.hoehe(), d.z(), d.e(), d.n(), Double.NaN, d.hoehe(), d.breite(), d.straehnen(), d.juni(), d.ueberhang(),
                    false, Double.NaN, Double.NaN, Double.NaN, Double.NaN, befundFall(d), quelleFall(d), "ohne Gelände: Lage am Fuß", List.of()));
        return l;
    }

    public static List<Daten.Ort> orteLeicht() {
        List<Daten.Ort> l = new ArrayList<>();
        for (Site.Ort o : Site.ORTE) l.add(new Daten.Ort(o.name(), artOrt(o), o.x(), o.grund(), o.z(), null));
        return l;
    }

    static String artOrt(Site.Ort o) {
        if (o == Site.ANLEGER) return "ANLEGER";
        if (o == Site.PLATTFORM) return "PLATTFORM";
        if (o == Site.KNIVSFLA || o == Site.SKAGEFLA) return "HOF";
        return "FALL";
    }

    static String befundFall(Faelle.Def d) {
        return d == Faelle.SCHWESTERN || d == Faelle.FRIAREN ? Daten.BELEGT : Daten.GESTALTET;
    }

    static String quelleFall(Faelle.Def d) { return d == Faelle.SCHWESTERN || d == Faelle.FRIAREN ? "WP_SCHWESTERN" : "APP"; }

    // ================================================================== mit Gelände

    /** Was das Gelände braucht, einmal gebaut (dauert einige Sekunden: die Fälle werden gerechnet). */
    public record Szene(File dir, Terrain t, List<Faelle.Eintrag> faelle, Flotte flotte, Dorf dorf, Hoefe hoefe, Bauten bauten, Leute leute) {
        public boolean gemessen() { return t.measured(); }
    }

    public static Szene szene(File gelaendeDir) {
        Terrain t = Terrain.load(gelaendeDir);
        if (!t.measured()) return new Szene(gelaendeDir, t, List.of(), new Flotte(t), null, null, null, null);
        List<Faelle.Eintrag> f = Faelle.build(t, t::height, com.dan.fall.Fall.Quality.MEDIUM);
        Bauten b = Bauten.build(t);
        Hoefe h = Hoefe.build(t);
        Leute l = t.ornevegen == null ? null : new Leute(t, b, h);
        return new Szene(gelaendeDir, t, f, new Flotte(t), Dorf.build(t), h, b, l);
    }

    static double r1(double v) { return Math.round(v * 10) / 10.0; }
    static double r2(double v) { return Math.round(v * 100) / 100.0; }
    static double r3(double v) { return Math.round(v * 1000) / 1000.0; }

    /** Eine Linie ohne Punkte, die näher als min m am vorigen liegen (Oracle meldet sonst „redundant“); null bei weniger als zwei. */
    static Daten.Geom linie(List<double[]> p, double min) {
        List<double[]> q = new ArrayList<>();
        for (double[] x : p) if (q.isEmpty() || Math.hypot(x[0] - q.get(q.size() - 1)[0], x[1] - q.get(q.size() - 1)[1]) >= min) q.add(new double[]{x[0], x[1]});
        return q.size() < 2 ? null : Daten.Geom.linie(q.toArray(new double[0][]));
    }

    // ------------------------------------------------------------------ Gelände, Fenster, Formen, Höhenlinien

    public static List<Daten.Gelaende> gelaende(Szene s) {
        Terrain t = s.t();
        Map<String, String> dateien = new LinkedHashMap<>();
        for (String f : new String[]{Terrain.HOEHE, Terrain.FERN, Terrain.ARTEN, Terrain.INFO}) dateien.put(f, sha256(new File(s.dir(), f)));
        double[] ll = Site.latLon(0, 0);
        String kacheln = t.props.getProperty("quelle", "keine Höhendaten");
        double hmin = 1e9, hmax = -1e9, fmin = 1e9, fmax = -1e9;
        for (short v : t.h) { double y = Terrain.decode(v); hmin = Math.min(hmin, y); hmax = Math.max(hmax, y); }
        for (short v : t.fern) { double y = Terrain.decode(v); fmin = Math.min(fmin, y); fmax = Math.max(fmax, y); }
        double x1 = Terrain.X0 + Terrain.N * Terrain.CELL, z1 = Terrain.Z0 + Terrain.N * Terrain.CELL;
        double fx1 = Terrain.FX0 + Terrain.FN * Terrain.FCELL, fz1 = Terrain.FZ0 + Terrain.FN * Terrain.FCELL;
        return List.of(
                new Daten.Gelaende("Hauptraster 12 m", Daten.Geom.flaeche(new double[][]{{Terrain.X0, Terrain.Z0}, {x1, Terrain.Z0}, {x1, z1}, {Terrain.X0, z1}}),
                        Site.E0, Site.N0, ll[1], ll[0], Terrain.CELL, Terrain.N, r1(hmin), r1(hmax), com.dan.geiranger.scene.Fjordgrund.UFER,
                        com.dan.geiranger.scene.Fjordgrund.MITTE, com.dan.geiranger.scene.Fjordgrund.RINNE, kacheln, dateien, "HGT",
                        String.format(Locale.GERMAN, "%,.1f × %,.1f km, Mitte bei x %.0f, z %.0f; unter dem Spiegel der gemalte Grund (F7); dazu das Feinraster 2 m über den Kehren (GR_FORM)",
                                Terrain.N * Terrain.CELL / 1000, Terrain.N * Terrain.CELL / 1000, Terrain.MX, Terrain.MZ)),
                new Daten.Gelaende("Fernring 120 m", Daten.Geom.flaeche(new double[][]{{Terrain.FX0, Terrain.FZ0}, {fx1, Terrain.FZ0}, {fx1, fz1}, {Terrain.FX0, fz1}}),
                        Site.E0, Site.N0, ll[1], ll[0], Terrain.FCELL, Terrain.FN, r1(fmin), r1(fmax), null, Fjordgrund_FERN(), null, kacheln, Map.of(), "HGT",
                        String.format(Locale.GERMAN, "%,.1f × %,.1f km um das Hauptraster (Horizont, Berge bis Hellesylt)", Terrain.FN * Terrain.FCELL / 1000,
                                Terrain.FN * Terrain.FCELL / 1000)));
    }

    private static Double Fjordgrund_FERN() { return com.dan.geiranger.scene.Fjordgrund.FERN_TIEFE; }

    public static List<Daten.Kernfenster> kernfenster() {
        return List.of(
                new Daten.Kernfenster(Site.KERN_WEST.name(), Site.KERN_WEST.eMin(), Site.KERN_WEST.nMin(), Site.KERN_WEST.eMax(), Site.KERN_WEST.nMax(),
                        "Knivsflå, Sieben Schwestern, Friaren, Skageflå; das Fenster der Fälle (3 × 2 km, quer)"),
                new Daten.Kernfenster(Site.KERN_OST.name(), Site.KERN_OST.eMin(), Site.KERN_OST.nMin(), Site.KERN_OST.eMax(), Site.KERN_OST.nMax(),
                        "Der Ort bis Møllsæter mit den Kehren des Ørnevegen und der Plattform (hochkant)"),
                new Daten.Kernfenster(Site.UMGEBUNG.name(), Site.UMGEBUNG.eMin(), Site.UMGEBUNG.nMin(), Site.UMGEBUNG.eMax(), Site.UMGEBUNG.nMax(),
                        "Rechteck um den GLO-30-Ausschnitt (F6)"));
    }

    public static List<Daten.Form> formen(Terrain t) {
        List<Daten.Form> l = new ArrayList<>();
        for (com.dan.geiranger.scene.Formen.Form f : t.formDaten) {
            String text = null;
            for (String s : t.formen) if (s.startsWith(f.name().replace("Wand unter ", "")) || (f.art().equals("STRASSE") && s.startsWith("Fv63")) || s.startsWith(f.name())) text = s;
            l.add(new Daten.Form(f.name(), f.art(), Daten.Geom.flaeche(f.flaeche()), Double.isNaN(f.stufe()) ? null : r1(f.stufe()), Double.isNaN(f.front()) ? null : r1(f.front()),
                    f.zellen(), f.zelle(), Math.round(f.m3()), text));
        }
        return l;
    }

    public static List<Daten.Hoehenlinie> hoehenlinien(Terrain t) { return t.measured() ? Hoehenlinien.rechnen(t) : List.of(); }

    // ------------------------------------------------------------------ Fälle und Elva (F30)

    public static List<Daten.Fall> faelle(Szene s) {
        if (!s.gemessen()) return faelleLeicht();
        List<Daten.Fall> l = new ArrayList<>();
        for (Faelle.Eintrag e : s.faelle()) {
            Faelle.Def d = e.def;
            com.dan.fall.Fall f = e.fall;
            List<Daten.Straehne> wege = new ArrayList<>();
            double frei = 0, fels = 0;
            int nr = 0;
            for (com.dan.fall.Fall.Strand st : f.strands) {
                nr++;
                List<double[]> p3 = new ArrayList<>();
                if (st.path != null) for (int k = 0; k + 3 < st.path.length; k += 4) p3.add(new double[]{st.path[k], st.path[k + 1], st.path[k + 2]});
                else { p3.add(new double[]{st.x, st.y, st.z}); p3.add(new double[]{st.ix, st.iy, st.iz}); }
                // in 3D vereinfachen (x, z in der Ebene, dann die Höhe): erst nach der Höhe ausdünnen, dann doppelte Lagen weg
                List<double[]> q = new ArrayList<>();
                for (double[] p : p3) {
                    if (!q.isEmpty()) {
                        double[] v = q.get(q.size() - 1);
                        if (Math.hypot(p[0] - v[0], p[2] - v[2]) < 1.0 && Math.abs(p[1] - v[1]) < 8) continue;
                    }
                    q.add(p);
                }
                double[] last = p3.get(p3.size() - 1);
                if (q.get(q.size() - 1) != last) q.add(last);
                List<double[]> w = new ArrayList<>();
                for (double[] p : q) {
                    if (!w.isEmpty()) { double[] v = w.get(w.size() - 1); if (Math.hypot(p[0] - v[0], p[2] - v[2]) < 0.2) { v[1] = Math.min(v[1], p[1]); continue; } }
                    w.add(p.clone());
                }
                if (w.size() < 2) w.add(new double[]{st.ix + 0.25, st.iy, st.iz});
                frei = Math.max(frei, st.longestFree);
                fels += st.rockShare;
                wege.add(new Daten.Straehne(nr, w.toArray(new double[0][]), r1(st.length), r3(st.share), r3(st.rockShare), st.landings, r1(st.longestFree), r2(st.tImpact),
                        new double[]{st.ix, st.iy, st.iz}, st.onWater));
            }
            double[] fuss = {d.e(), d.n()};
            l.add(new Daten.Fall(d.name(), d.art().name(), r1(e.kante.x()), r1(e.kante.y()), r1(e.kante.z()), Math.round(fuss[0] * 10) / 10.0, Math.round(fuss[1] * 10) / 10.0,
                    r1(e.kante.heading()), r1(d.hoehe()), r1(d.breite()), f.strands.size(), d.juni(), d.ueberhang(), d.art() == Faelle.Art.REGEN, r1(f.height), r2(f.fallTime),
                    r1(frei), f.strands.isEmpty() ? 0 : r3(fels / f.strands.size()), d.art() == Faelle.Art.FLUSS ? Daten.GESTALTET : befundFall(d),
                    d.art() == Faelle.Art.FLUSS ? "APP" : quelleFall(d),
                    d.art() == Faelle.Art.REGEN ? "Aus dem Gelände gesucht; läuft nur nach Regen" : d.art() == Faelle.Art.FLUSS ? "Stufe der Geirangelva"
                            : "Wege auf dem Gelände ohne die Feinform der Wand (die Szene rechnet sie mit cliff)", wege));
        }
        return l;
    }

    public static List<Daten.Elva> elva(Terrain t) {
        com.dan.geiranger.scene.Elva e = t.elva;
        if (e == null) return List.of();
        List<Daten.Elva> l = new ArrayList<>();
        l.add(stueck(e, "Geirangelva", "LAUF", 1, 0, e.n - 1));
        int nr = 0;
        for (com.dan.geiranger.scene.Elva.Stufe s : e.stufen) l.add(stueck(e, s.name(), "STUFE", ++nr, s.i0(), s.i1()));
        nr = 0;
        for (com.dan.geiranger.scene.Elva.Strecke s : e.strecken) { nr++; l.add(stueck(e, "Strecke " + nr, "STRECKE", nr, s.i0(), s.i1())); }
        return l;
    }

    static Daten.Elva stueck(com.dan.geiranger.scene.Elva e, String name, String art, int nr, int i0, int i1) {
        List<double[]> p = new ArrayList<>();
        double half = 0;
        for (int i = i0; i <= i1; i++) { p.add(new double[]{e.x[i], e.z[i]}); half += e.half[i]; }
        double[][] v = Hoehenlinien.vereinfachen(p.toArray(new double[0][]), 0.5);
        Daten.Geom g = linie(List.of(v), 0.1);
        return new Daten.Elva(name, art, nr, g, r1(e.l[i0]), r1(e.l[i1]), r2(e.level[i1]), r2(e.level[i0]), r1(2 * half / (i1 - i0 + 1)), com.dan.geiranger.scene.Elva.JUNI);
    }

    // ------------------------------------------------------------------ Straße, Kehren, Orte, Gebäude (F32)

    public static List<Daten.Strasse> strassen(Terrain t) {
        Ornevegen o = t.ornevegen;
        if (o == null) return List.of();
        com.dan.road.Way w = o.way;
        double[] ende = Site.fromUtm(Ornevegen.DORF[Ornevegen.DORF.length - 1][0], Ornevegen.DORF[Ornevegen.DORF.length - 1][1]);
        int iOrt = 0;
        double bd = 1e18;
        for (int i = 0; i < w.n; i++) { double d = Math.hypot(w.x[i] - ende[0], w.z[i] - ende[1]); if (d < bd) { bd = d; iOrt = i; } }
        List<Daten.Strasse> l = new ArrayList<>();
        l.add(strasse(w, 0, iOrt, "Fv63 durch den Ort", "ORT", "Vom Fuß des Storfossen östlich um das Hafenbecken zum Ufer (Phase 11)"));
        l.add(strasse(w, iOrt, w.n - 1, "Fv63 Ørnevegen", "ORNEVEGEN", "Vom Ort am Ufer nach Grande, die elf Kehren hinauf, über Møllsæter bis an den Rand von Kern Ost (Phase 9)"));
        if (o.strandvegen != null) l.add(strasse(o.strandvegen, 0, o.strandvegen.n - 1, "Strandvegen", "STRANDVEGEN", "Von der Fv63 hinab zum Campingplatz am Delta (Phase 11)"));
        return l;
    }

    static Daten.Strasse strasse(com.dan.road.Way w, int i0, int i1, String name, String art, String text) {
        List<double[]> p = new ArrayList<>();
        double max = 0, lo = 1e9, hi = -1e9, len = 0, steig = 0;
        for (int i = i0; i <= i1; i++) {
            p.add(new double[]{w.x[i], w.z[i]});
            lo = Math.min(lo, w.y[i]);
            hi = Math.max(hi, w.y[i]);
            if (i > i0) {
                double d = Math.hypot(w.x[i] - w.x[i - 1], w.z[i] - w.z[i - 1]);
                len += d;
                if (d > 1e-6) steig += Math.abs(w.y[i] - w.y[i - 1]);
            }
        }
        // größte Steigung über 50 m Weg (in den Kehren sind die Stücke kurz, dort hebt sich der Rand)
        double[] sw = new double[i1 - i0 + 1];
        for (int i = i0 + 1; i <= i1; i++) sw[i - i0] = sw[i - i0 - 1] + Math.hypot(w.x[i] - w.x[i - 1], w.z[i] - w.z[i - 1]);
        for (int i = i0, j = i0; i <= i1; i++) {
            while (j < i1 && sw[j - i0] - sw[i - i0] < 50) j++;
            double d = sw[j - i0] - sw[i - i0];
            if (d >= 50) max = Math.max(max, Math.abs(w.y[j] - w.y[i]) / d);
        }
        double[][] v = Hoehenlinien.vereinfachen(p.toArray(new double[0][]), 0.3);
        return new Daten.Strasse(name, art, linie(List.of(v), 0.1), r1(len), len > 0 ? Math.round(steig / len * 1e4) / 1e4 : 0, Math.round(max * 1e4) / 1e4, r1(lo), r1(hi),
                r2(2 * w.type.laneWidth + 2 * w.type.shoulder), r2(w.type.laneWidth), text);
    }

    public static List<Daten.Kehre> kehren(Terrain t) {
        Ornevegen o = t.ornevegen;
        if (o == null) return List.of();
        List<Daten.Kehre> l = new ArrayList<>();
        for (Ornevegen.Kehre k : o.kehren)
            l.add(new Daten.Kehre(k.nr(), r1(k.x()), r1(k.y()), r1(k.z()), r1((k.s0() + k.s1()) / 2), r1(k.s1() - k.s0()), Ornevegen.RADIUS));
        return l;
    }

    public static List<Daten.Ort> orte(Szene s) {
        if (!s.gemessen()) return orteLeicht();
        Terrain t = s.t();
        List<Daten.Ort> l = new ArrayList<>();
        l.add(new Daten.Ort("Anleger", "ANLEGER", Site.ANLEGER.x(), r1(t.height(Site.ANLEGER.x(), Site.ANLEGER.z())), Site.ANLEGER.z(), "Ursprung der Szenenmeter (F2)"));
        Ornevegen o = t.ornevegen;
        if (o != null && o.plattform != null)
            l.add(new Daten.Ort("Plattform Ørnesvingen", "PLATTFORM", r1(o.plattform[0]), r1(o.plattform[1]), r1(o.plattform[2]), "Außen an der elften Kehre (Phase 9)"));
        Leute le = s.leute();
        if (le != null && le.bucht != null) {
            double[] p = le.strasse(le.bucht[0], le.bucht[1] * Leute.BUCHT);
            l.add(new Daten.Ort("Busbucht vor der Plattform", "BUSBUCHT", r1(p[0]), r1(p[1]), r1(p[2]), "Halt des Ausflugsbusses (12 min, Phase 13)"));
        }
        if (s.bauten() != null && s.bauten().treppe.size() >= 2) {
            double[] a = s.bauten().treppe.get(0), b = s.bauten().treppe.get(s.bauten().treppe.size() - 1);
            l.add(new Daten.Ort("Treppe am Storfossen (Fuß)", "TREPPE", r1(a[0]), r1(a[1]), r1(a[2]), "308 Stufen am Nordufer der Elva (Phase 8)"));
            l.add(new Daten.Ort("Treppe am Storfossen (oben)", "TREPPE", r1(b[0]), r1(b[1]), r1(b[2]), null));
        }
        if (le != null && le.skagefla != null)
            l.add(new Daten.Ort("Anlegestelle unter Skageflå", "ANLEGER", r1(le.skagefla.x[0]), r1(le.skagefla.y[0]), r1(le.skagefla.z[0]), "Beginn des Pfads zum Hof (Phase 13)"));
        for (Site.Ort so : Site.ORTE) {
            if (so == Site.ANLEGER || so == Site.PLATTFORM) continue;
            l.add(new Daten.Ort(so.name(), artOrt(so), so.x(), r1(Math.max(0, t.height(so.x(), so.z()))), so.z(), null));
        }
        if (s.hoefe() != null)
            for (Hoefe.Hof h : s.hoefe().hoefe)
                l.add(new Daten.Ort("Hof " + h.name(), "HOF", r1(h.x()), r1(h.y()), r1(h.z()), String.format(Locale.GERMAN, "%d Häuser auf dem Band, Neigung %.0f°", h.haeuser(), h.neigung())));
        l.add(new Daten.Ort("Ankerplatz", "ANKER", Flotte.ANKER[0], 0, Flotte.ANKER[1], "Kreuzfahrtschiff vor Anker, Bug nach Süden (Kystverket)"));
        for (Ansichten.Ansicht a : Ansichten.ALLE)
            l.add(new Daten.Ort("Ansicht " + a.key() + " · " + a.name(), "ANSICHT", r1(a.x()), r1(Math.max(0, t.height(a.x(), a.z())) + a.eyeAboveGround()), r1(a.z()),
                    String.format(Locale.GERMAN, "Blick %03.0f°, Neigung %.0f°", a.heading(), a.pitch())));
        return l;
    }

    public static List<Daten.Gebaeude> gebaeude(Szene s) {
        List<Daten.Gebaeude> l = new ArrayList<>();
        if (s.dorf() == null) return l;
        int nr = 0;
        for (Dorf.Bau b : s.dorf().bauten) {
            String bauweise = b.art() == Dorf.Art.NAUST ? "TIMBER" : "GESTRICHEN";
            String teil = b.y() > 60 ? "Vinje" : "Geiranger";
            l.add(new Daten.Gebaeude(++nr, b.name(), b.art().name(), r1(b.x()), r1(b.y()), r1(b.z()), b.ax(), b.az(), r1(b.hl()), r1(b.hw()), r1(b.wand()), b.farbe(), b.dach(),
                    bauweise, teil));
        }
        if (s.hoefe() != null)
            for (Hoefe.Hof h : s.hoefe().hoefe)
                for (Hoefe.HausLage hs : Hoefe.haeuser(s.t(), h))
                    l.add(new Daten.Gebaeude(++nr, hs.name(), "HOF", r1(hs.x()), r1(hs.y()), r1(hs.z()), hs.ax(), hs.az(), r1(hs.hl()), r1(hs.hw()), r1(hs.wand()), hs.farbe(),
                            "Gras", "TIMBER", h.name()));
        return l;
    }

    // ------------------------------------------------------------------ Schiffe (F31)

    public static List<Daten.Schiff> schiffe(Flotte f) {
        List<Daten.Schiff> l = new ArrayList<>();
        for (Flotte.Schiff s : f.schiffe) {
            com.dan.buoyancy.Floater fl = s.floater;
            String text = switch (s.art) {
                case FAEHRE -> "Fähre Geiranger–Hellesylt (Fjord1), 65 min nach dem Fahrplan 2025";
                case KREUZFAHRT -> "Mai bis September vor Anker (8:00 bis 17:30), Schwerpunkt 1,5 m über dem Kiel (Nacharbeit 12)";
                case SCHNELLBOOT -> "Fahrten zu den Sieben Schwestern ab dem Anleger";
                case KAJAK -> "Paddelt von 9 bis 20 Uhr vor dem Ort";
            };
            l.add(new Daten.Schiff(s.name, s.art.name(), r1(2 * s.hl), r1(2 * s.hw), r2(s.tiefgang), r2(s.freibord), fl == null ? null : r2(fl.gm),
                    fl == null ? null : r1(fl.rollPeriod()), fl == null ? null : r1(fl.mass() / 1000), text));
        }
        return l;
    }

    public static List<Daten.Schiffsweg> schiffswege(Flotte f) {
        List<Daten.Schiffsweg> l = new ArrayList<>();
        l.add(new Daten.Schiffsweg("Fähre herein", "HEREIN", weg(f.weg), "Vom Westrand (Richtung Hellesylt) an den Sieben Schwestern vorbei zur Liegestelle am Anleger"));
        l.add(new Daten.Schiffsweg("Fähre hinaus", "HINAUS", weg(f.wegHinaus), "Ab 75 m vor der Liegestelle (gedreht) zurück zum Westrand"));
        l.add(new Daten.Schiffsweg("Kreuzfahrtschiff herein", "HEREIN", weg(f.wegKreuz), "Weg der Fähre bis vor das Becken, dann nach Süden zum Anker"));
        l.add(new Daten.Schiffsweg("Ankerplatz Kreuzfahrtschiff", "ANKER", Daten.Geom.punkt(Flotte.ANKER[0], Flotte.ANKER[1]), "Kystverket: Ankerplätze vor dem Ort"));
        return l;
    }

    static Daten.Geom weg(Flotte.Weg w) {
        List<double[]> p = new ArrayList<>();
        for (int i = 0; i < w.x.length; i++) p.add(new double[]{w.x[i], w.z[i]});
        return linie(p, 0.5);
    }

    // ------------------------------------------------------------------ Fahrten (F33)

    public static List<Daten.Fahrt> fahrten(Szene s) {
        if (!s.gemessen() || s.t().ornevegen == null) return fahrten();
        com.dan.geiranger.camera.Fahrt fa = Renderer.ornesvingenFahrt(s.t().ornevegen, s.leute() == null ? null : s.leute().bucht, Double.NaN);
        if (fa == null) return fahrten();
        double[][] sp = fa.stuetzpunkte();
        List<Daten.Stuetzpunkt> p = new ArrayList<>();
        int nr = 0;
        for (int i = 0; i < sp.length; i++) {
            if (i % 5 != 0 && i != sp.length - 1) continue;
            double[] q = sp[i];
            p.add(new Daten.Stuetzpunkt(++nr, r2(q[0]), r2(q[1]), r2(q[2]), r2(q[3]), r2(q[4]), r2(q[5])));
        }
        return List.of(new Daten.Fahrt(FAHRT_ORNESVINGEN, "STRASSE", r1(fa.duration), r1(fa.length), BESCHREIBUNG_FAHRT, Daten.MITGELIEFERT, p));
    }

    // ------------------------------------------------------------------ Hilfen

    /** SHA-256 einer Datei (hex) oder null, wenn sie fehlt. */
    public static String sha256(File f) {
        if (!f.isFile()) return null;
        try (InputStream in = new java.io.FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            StringBuilder b = new StringBuilder();
            for (byte x : md.digest()) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Für Prüfungen: die leichten Zahlen auf einen Blick. */
    public static String zusammenfassung() {
        return String.format(Locale.GERMAN, "%d Quellen, %d Befunde, %d Monate Klima, %d Höhenstufen, %d Abfahrten, %d Wetterbücher, %d Regiebücher, %d Ansichten, %d Presets",
                quellen().size(), befunde().size(), klima().size(), hoehenstufen().size(), fahrplan().size(), wetterbuecher().size(), regiebuecher().size(), ansichten().size(),
                presets().size());
    }
}
