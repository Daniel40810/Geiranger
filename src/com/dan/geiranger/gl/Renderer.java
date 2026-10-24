package com.dan.geiranger.gl;

import com.dan.geiranger.app.Bibliothek;
import com.dan.geiranger.camera.Ansichten;
import com.dan.geiranger.camera.Camera;
import com.dan.geiranger.scene.Buehne;
import com.dan.geiranger.scene.Physik;
import com.dan.geiranger.scene.SceneClock;
import com.dan.geiranger.scene.Site;
import com.dan.geiranger.scene.Sun;
import com.dan.geiranger.scene.Terrain;
import com.dan.geiranger.scene.Plattform;
import com.dan.geiranger.wiring.Beaufort;
import com.dan.geiranger.wiring.Wiring;
import com.dan.ocean.gl.OceanGl;
import com.dan.cloud.Clouds;
import com.dan.cloud.gl.CloudGl;
import com.dan.fog.Fog;
import com.dan.fog.gl.FogGl;
import com.dan.rain.gl.RainGl;
import com.dan.sky.Sky;
import com.dan.sky.gl.SkyGl;
import com.dan.geiranger.scene.Wandschatten;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Die Engine auf der Grafikkarte, übernommen aus Rungholt und entschlackt (F5 A): alles, was zum Watt, zur Tide und
 * zur Stadt von 1362 gehörte, ist heraus; geblieben ist das Gerüst, an das die Phasen 3 bis 18 ihre Durchgänge hängen.
 * Je Bild seit Phase 2:
 * <ol>
 * <li><b>Himmel</b>: der Verlauf nach der Sonnenhöhe ({@code sky.frag}, Platzhalter bis {@code com.dan.sky} in
 * Phase 4) in einen HDR-Puffer (RGBA16F, umgekehrte 32F-Tiefe).</li>
 * <li><b>Gelände</b> (Phase 2): die HGT-Kacheln mit 1″ ({@link Terrain}, Hauptraster 24,6 km zu 12 m und Fernring
 * 61 km zu 120 m) als CDLOD ({@link TerrainLod}), in den Kernfenstern bis 2 m Punktabstand; Materialien als Platzhalter
 * nach Höhe und Neigung ({@code terrain.frag}), unter dem Spiegel der gemalte Grund. Ohne {@code daten/gelaende} eben auf NN.</li>
 * <li><b>Spiegelbild</b> (Phase 3): dieselbe Szene vom Auge unter dem Spiegel, alles unter dem Wasser weggeschnitten,
 * in einer kleineren Rechengröße ({@link Quality#mirrorScale}), dann als Rundumbild ({@code spiegel.frag}) für
 * ocean: so spiegeln sich die Wände im Fjord.</li>
 * <li><b>Wasser</b> (Phase 3): {@code com.dan.ocean} mit dem Preset „Fjord“ ({@link Wasser}) über dem gemalten Grund
 * ({@link com.dan.geiranger.scene.Fjordgrund}); „Grund zeigen“ lässt es weg und färbt den Grund nach Tiefe.</li>
 * <li><b>Geirangelva</b> (Phase 8): nach ocean im Durchgang Wasser ({@link Fluss}); Treppe, Geländer und Steine im
 * Durchgang Gelände.</li>
 * <li><b>Fälle</b> (Phase 7): die Wasserfälle aus {@code com.dan.fall} im Lauf über Fels ({@link Wasserfaelle}), nach
 * dem Wasser, damit ihr Fuß über dem Fjord liegt; auch im Spiegelbild.</li>
 * <li><b>Nachbearbeitung</b>: Belichtung, Filmkurve, Gamma ins Fenster.</li>
 * <li><b>HUD</b>: Tempo (Bilder/s, Zeit der Grafikkarte je Durchgang, Prozessor), Uhr mit Datum, Sonne, Kamera.</li>
 * </ol>
 * Geblieben aus Rungholt: Qualitätsstufen mit Auflösungsregelung, Zeitmessung jedes Durchgangs auf der Grafikkarte
 * (Zeitstempel, ein Bild versetzt gelesen), Compute-Test beim Start, HUD kurz/voll/aus, Foto (F12), Shader neu laden
 * (F5), Standbilder ohne Fenster ({@code tools.Tempo}), der Faden der Physik ({@link Physik}) mit festem Schritt. Im
 * Bildlauf werden keine Dateien gelesen.
 */
public final class Renderer implements GLEventListener {
    public static final int MIN_MAJOR = 4, MIN_MINOR = 3;
    public static final String VERSION = "Phase 18";
    /** Die Phase, die dieser Stand erfüllt (für Ansichten und HUD). */
    public static final int PHASE = 18;
    public static final String[] PASSES = {"Schatten", "Himmel", "Spiegelbild", "Gelände", "Straße", "Wald", "Ort", "Schiffe", "Menschen", "Wasser", "Fälle", "Wetter", "Nachbearbeitung", "HUD"};
    private static final int MARKS = PASSES.length + 1;
    /** Nah- und Fernebene der Projektion (m). */
    static final double NEAR = 0.5, FAR = 120_000;

    public final SceneClock clock;
    public final Wiring wiring;
    public final Camera camera = new Camera();
    /** Der Faden der Physik (noch ohne Aufgaben). */
    public final Physik physik = new Physik();
    /** Phase 2: das Gelände aus den HGT-Kacheln (oder eben, wenn {@code daten/gelaende} fehlt) und seine Detailstufen. */
    public final Terrain terrain;
    public final TerrainLod lod = new TerrainLod();
    /** Phase 3: das Wasser aus com.dan.ocean (Preset Fjord) und das Spiegelbild der Wände. */
    public final Wasser wasser = new Wasser();
    /** Phase 3, Block WASSER: „Grund zeigen“ (kein Wasser, Grund nach Tiefe, die Kamera darf hinab) und das Spiegelbild. */
    public volatile boolean showBed, showMirror = true;
    /** Phase 11: „Grund zeigen“ kam von Ansicht 7 (nicht von Hand); jede andere Ansicht zeigt dann wieder das Wasser. */
    private volatile boolean bedVonAnsicht;

    /** „Grund zeigen“ von Hand (Bedienfeld, Taste G): bleibt, bis man es wieder umschaltet. */
    public void setShowBed(boolean on) {
        showBed = on;
        bedVonAnsicht = false;
    }

    /** Für die Prüfung: hat Ansicht 7 „Grund zeigen“ eingeschaltet? */
    public boolean bedVonAnsicht() { return bedVonAnsicht; }
    /** Phase 4: Himmel, Wolken, Regen, Fjordnebel und Wolkenband ({@link Wetter}); die Schatten der Wände ({@link SchattenKarte}). */
    public final Wetter wetter = new Wetter();
    public final SchattenKarte schatten = new SchattenKarte();
    /** Phase 5: die Wände aus com.dan.cliff (Material, Feinform, Wandkarte, Überhänge). */
    public final Fels fels;
    /** Phase 7: die Wasserfälle (com.dan.fall) an ihren Kanten, mit Abfluss aus der Regie ({@link #abfluss()}). */
    public final Wasserfaelle faelle = new Wasserfaelle();
    /** Phase 8: die Geirangelva (com.dan.river) mit Treppe, Geländer und Steinen am Storfossen. */
    public final Fluss fluss;
    /** Phase 9: die Fv63 mit dem Ørnevegen, ihr Verkehr (com.dan.road) und die Plattform der Ørnesvingen. */
    public final Strasse strasse;
    /** Phase 10: Wald (com.dan.forest), Bodendecke (com.dan.ground) und die Höfe Knivsflå und Skageflå. */
    public final Wald wald;
    /** Phase 11: der Ort Geiranger (Häuser, Hotels, Kirche, Anleger, Camping, Laternen; Naust und Höfe aus timber). */
    public final DorfGl ort;
    /** Phase 12: Fähren, Kreuzfahrtschiff, Schnellboot und Kajaks mit Kielwasser (F17 A). */
    public final FlotteGl flotte;
    /** Phase 13: die Menschen (Gäste aus com.dan.avatar, Figuren aus com.dan.figure, der Ausflugsbus). */
    public final LeuteGl leute;
    /** Phase 14: das Jahr in zehn Höhenstufen (com.dan.season): Schnee, Laub, Herbst, Kronenschnee, Frost an den Fällen. */
    public final com.dan.geiranger.scene.Jahr jahr = new com.dan.geiranger.scene.Jahr();
    /** Phase 14: fallender Schnee um das Auge (com.dan.season), Schneefall (mm/h Wasser) zuletzt. */
    private final com.dan.season.gl.SnowfallGl schneefall = new com.dan.season.gl.SnowfallGl();
    private boolean schneeOk;
    public volatile double schneeRate;
    /** Phase 14: das Jahr ein (Schnee, Laub nach season) oder aus (fester Juni wie bis Phase 13). */
    public volatile boolean showJahr = true;
    /** Phase 15: der Klang des Fjords (soundscape, F20 A); den Lautsprecher schaltet die App ein ({@code klang.ton}). */
    public final com.dan.geiranger.scene.Klang klang;
    private final com.dan.geiranger.scene.Klang.Lage klangLage = new com.dan.geiranger.scene.Klang.Lage();
    private double klangTakt;
    /** Trübe der Elva 0..1 (Gletschermilch; nach Regen trüber). */
    public volatile double elvaTurbid = 0.6;
    /** Ohne Verdrahtung (Prüfungen, Werkzeuge) die eigene Abflussregie. */
    private final com.dan.geiranger.wiring.Abfluss ownAbfluss = new com.dan.geiranger.wiring.Abfluss();
    /** Fels aus com.dan.cliff zeichnen (sonst der Platzhalter aus Phase 2), Nässe der Wände nach Regen 0..1. */
    public volatile boolean showCliff = true;
    private double rockWet;
    /** Ebenen ein und aus (Block WETTER). */
    public volatile boolean showClouds = true, showRain = true, showFog = true;
    /** Deckkraft des Regens 0..2. */
    public volatile double rainStrength = 1;
    /** Augenanpassung: geglättete Leuchtdichte, Leuchtdichte im Blickfeld, Zeit bis zur nächsten Messung. */
    private double adaptLum = -1, viewLum = -1, viewTimer;
    /** Anteil der Anpassung (1 = volle, 0 = keine); 0,85 lässt die Nacht Nacht bleiben (wie Rungholt). */
    public volatile double adaptation = 0.85;
    /** Wald, Wiesen und Fels werfen mehr zurück als das Wasser, mit dem com.dan.sky unter der Kimm rechnet [G]. */
    public static final double LAND_GAIN = 2.0;
    /** Wie weit das Auge der Dunkelheit unter Wolken folgt (wie Rungholt). */
    public static final double STORM_ADAPT = 0.6;
    /**
     * Phase 8: die Messung im Bild ist ein geometrisches Mittel, die Schätzung aus com.dan.sky ein arithmetisches; in
     * einem Bild aus Himmel und Wasser liegt das arithmetische rund 1,6- bis 2-mal höher [G, T8 in Phase 8; seit Phase 8a 2,0]. Damit bleibt die
     * Belichtung über dem offenen Fjord wie bis Phase 7, und nur dort, wo Land das Bild füllt, folgt sie dem Land.
     */
    public static final double BILD_GAIN = 2.0;
    /**
     * Phase 8a: wie stark das Auge dem gemessenen Bild folgt (Rest: der Anpassung, die com.dan.sky für den Himmel
     * rechnet). Bis Phase 8 die Hälfte; damit änderte die Messung an der sonnigen Wand der Schwestern nur 3,0 → 2,8.
     * Mit drei Vierteln liegt das Mittel des Bilds nahe am mittleren Grau; die Nacht bleibt über das letzte Viertel dunkel [G].
     */
    public static final double BILD_FOLGT = 0.75;
    private double cloudShade = 1, landInView, sunOnWater = 1, sunOnWaterTimer;
    private int depthCopyTex, depthCopyFbo, depthCopyW, depthCopyH;
    /** Zustand des laufenden Bilds für common(): Himmel aus com.dan.sky, sein Zustand, Vorbelichtung. */
    private boolean frameSky;
    private Sky.State frameSt;
    private double framePre = 1;

    private volatile Quality quality = Quality.MITTEL;
    public volatile double renderScale = quality.renderScale;
    /**
     * Regelt die Auflösung nach der Zeit der Grafikkarte: über dem Budget von {@link #targetFps} sinkt sie in Schritten
     * bis {@link #MIN_SCALE}, mit Luft steigt sie wieder bis zur Stufe (aus Haithabu über Rungholt).
     */
    public volatile boolean autoScale = true;
    public volatile double targetFps = 60;
    public static final double MIN_SCALE = 0.5;
    private long scaleNanos;
    private int scaleSteps;

    public volatile boolean showHud = true, hudFull;
    /** Die Marken auf dem Gelände (Kernfenster, Orte); seit Phase 2 aus, im Block SZENE einzuschalten. */
    public volatile boolean showMarks = false;
    /** Szene anhalten (Uhr, Kamera, Physik). */
    public volatile boolean frozen;
    /** Belichtung in Blendenstufen von Hand dazu. */
    public volatile double exposureBias;
    public volatile boolean photoRequest;
    /** Das nächste fertige Fensterbild an diesen Empfänger (einmal; im GL-Faden). */
    public volatile Consumer<BufferedImage> grabRequest;
    /** Ordner für Fotos (F12); ohne Angabe {@code docs/status} im Arbeitsverzeichnis. */
    public volatile File photoDir;
    /** Name des nächsten Fotos ohne Endung (einmal); ohne Angabe {@code Geiranger_Datum_Zeit}. */
    public volatile String photoName;

    private final Shader skyShader = new Shader("Himmel", null, "fullscreen.vert", "sky.frag"),
            terrainShader = new Shader("Gelände", null, "terrain.vert", "terrain.frag"),
            post = new Shader("Nachbearbeitung", null, "fullscreen.vert", "post.frag"),
            overlay = new Shader("Überlagerung", null, "fullscreen.vert", "overlay.frag"),
            spiegel = new Shader("Spiegelbild", null, "fullscreen.vert", "spiegel.frag"),
            schattenShader = new Shader("Schatten der Wände", null, "fullscreen.vert", "schatten.frag"),
            elvaShader = new Shader("Geirangelva", null, "elva.vert", "elva.frag"),
            bautenShader = new Shader("Bauten", null, "bauten.vert", "bauten.frag"),
            lumShader = new Shader("Belichtungsmessung", null, "fullscreen.vert", "lum.frag"),
            strasseShader = new Shader("Straße", null, "strasse.vert", "strasse.frag"),
            glowShader = new Shader("Lichthöfe", null, "glow.vert", "glow.frag"),
            schiffShader = new Shader("Schiffe", null, "schiff.vert", "bauten.frag");
    /** Phase 8: die Belichtung misst die Leuchtdichte im Bild selbst. */
    private final Belichtung belichtung = new Belichtung();
    /** Ob die Augenanpassung dem Bild folgt (sonst der Schätzung aus Himmel und Land wie bis Phase 7). */
    public volatile boolean bildMessung = !Boolean.getBoolean("geiranger.belichtung.schaetzen");
    private final Shader[] all = {skyShader, terrainShader, post, overlay, spiegel, schattenShader, elvaShader, bautenShader, lumShader, strasseShader, glowShader, schiffShader};
    private final HudOverlay hud = new HudOverlay();
    private int emptyVao, hdrTex, depthTex, hdrFbo, hdrW, hdrH, surfW = 1, surfH = 1, defaultFbo;
    private boolean reversedZ, ready;
    private final int[][] stamp = new int[2][MARKS];
    private final double[] passMs = new double[MARKS - 1];
    private int frame;
    private long lastNanos, statsNanos;
    private int statsFrames;
    private double fps, gpuMs, cpuMs, worstMs;
    private volatile String glInfo = "", error;
    private volatile ComputeProbe.Result compute;
    private volatile boolean reloadShaders;
    private Consumer<String> statusListener = s -> { };
    private Consumer<String> errorListener = s -> { };
    private volatile String photoMsg = "", meldung = "";
    private volatile long photoMsgUntil, meldungUntil;

    public Renderer(SceneClock clock, Wiring wiring) { this(clock, wiring, Terrain.load(Terrain.defaultDir())); }

    public Renderer(SceneClock clock, Wiring wiring, Terrain terrain) {
        this.clock = clock;
        this.wiring = wiring;
        this.terrain = terrain;
        if (wiring != null) {
            wiring.setTerrain(terrain);
            wiring.setSurface(wasser.surface(terrain));
        }
        lod.use(terrain);
        fels = new Fels(terrain);
        fluss = new Fluss(terrain);
        strasse = new Strasse(terrain);
        wald = new Wald(terrain);
        ort = new DorfGl(wald.dorf, wald.hoefe);
        flotte = new FlotteGl(new com.dan.geiranger.scene.Flotte(terrain));
        leute = new LeuteGl(new com.dan.geiranger.scene.Leute(terrain, fluss.bauten, wald.hoefe, clock.date().getMonthValue()));
        klang = new com.dan.geiranger.scene.Klang(terrain, klangQualitaet(quality), leute.leute.deck);
        wetter.sky.latDeg = Site.LAT;
        wetter.sky.lonDeg = Site.LON;
        wetter.sky.epochMillis = clock.epochMillis();
        setQuality(quality);
        camera.groundLevel = groundHeight(camera.eye[0], camera.eye[2]);
        camera.waterLevel = waterLevel();
        camera.targetGround = standHeight(camera.tx, camera.tz);
        camera.snap();
    }

    /** Für Prüfungen und Werkzeuge: Uhr am Start, eigene Verdrahtung. */
    public Renderer() { this(new SceneClock(), null); }

    public void setStatusListener(Consumer<String> l) { statusListener = l; }
    public void setErrorListener(Consumer<String> l) { errorListener = l; }
    public String glInfo() { return glInfo; }
    public String error() { return error; }
    public double fps() { return fps; }
    public double gpuMs() { return gpuMs; }
    public double cpuMs() { return cpuMs; }
    public boolean reversedDepth() { return reversedZ; }
    public ComputeProbe.Result compute() { return compute; }
    public Quality quality() { return quality; }
    public void requestShaderReload() { reloadShaders = true; }
    public double[] lastPassTimes() { return passMs.clone(); }
    public long frames() { return frame; }
    public double takeWorstFrameMs() { double w = worstMs; worstMs = 0; return w; }
    public double fovy() { return Camera.FOVY; }

    /** Alle Shader-Programme mit ihren Dateien (für die Prüfung). */
    public static String[][] shaderFiles() {
        Renderer r = new Renderer();
        String[][] f = new String[r.all.length][];
        for (int i = 0; i < f.length; i++) f[i] = r.all[i].files();
        return f;
    }

    /** Stufe des Klangs zur Qualität (gilt beim Start; die Abtastrate wechselt nicht im Lauf). */
    static com.dan.soundscape.Soundscape.Quality klangQualitaet(Quality q) {
        return switch (q) { case NIEDRIG -> com.dan.soundscape.Soundscape.Quality.LOW; case MITTEL -> com.dan.soundscape.Soundscape.Quality.MEDIUM;
            case HOCH -> com.dan.soundscape.Soundscape.Quality.HIGH; case ULTRA -> com.dan.soundscape.Soundscape.Quality.ULTRA; };
    }

    /** Phase 15: das Ohr an der Kamera, Wetter, Fälle, Schiffe, Verkehr und Menschen an den Klang. */
    private void stepKlang(double dt) {
        com.dan.geiranger.scene.Klang.Lage L = klangLage;
        L.epoch = clock.epochMillis() / 1000.0;
        L.dt = dt;
        L.ohr = camera.eye.clone();
        L.yaw = (Math.toDegrees(Math.atan2(camera.fwd[0], -camera.fwd[2])) + 360) % 360;
        double wind = wiring == null ? 2 : wiring.wind.speed();
        L.wind = wind;
        L.windVon = wiring == null ? 270 : wiring.wind.fromDeg();
        L.boee = wiring == null ? 0.3 : wiring.wind.gustiness();
        // Nacharbeit 15: der Regen aus der Regie (flüssiger Anteil am Auge), nicht das Feld von rain (das ohne Himmel nicht gesetzt wird)
        L.regen = showRain && wiring != null ? wiring.rainRate * (showJahr ? com.dan.season.Year.rainFraction(wiring.airTempC - com.dan.geiranger.scene.Jahr.LAPSE * Math.max(0, camera.eye[1])) : 1) : 0;
        L.luft = (wiring == null ? 15 : wiring.airTempC) - com.dan.geiranger.scene.Jahr.LAPSE * Math.max(0, camera.eye[1]);
        // Windsee im Fjord (kurzer Anlauf) [G]: Hs ≈ 0,0055 U², Tp ≈ 0,32 U, dazu die Dünung von ocean
        L.hs = Math.hypot(0.0055 * wind * wind, wasser.ocean.swellHeight);
        L.tp = Math.max(1, Math.max(0.32 * wind, wasser.ocean.swellHeight > 0.05 ? wasser.ocean.swellPeriod : 0));
        L.sonne = clock.sun()[0];
        L.faelle = faelle.on ? faelle.eintraege() : java.util.List.of();
        L.elva = terrain.elva;
        L.elvaQ = abfluss().cubicMetres(ELVA);
        L.abfluss = abfluss()::cubicMetres;
        L.flotte = flotte.flotte;
        L.leute = leute.leute;
        klangTakt -= dt;
        if (klangTakt <= 0) {
            klangTakt = 0.25;
            L.fahrzeuge = strasse.fahrzeugeNah(camera.eye, com.dan.geiranger.scene.Klang.FAHRZEUGE, 600);
        }
        klang.update(L);
    }

    // ================================================================== Phase 16: Regiebücher, Fahrten, Kino

    /** Phase 16: die Regiebücher (Text mit Blatt am Ende), ihre Fotos und was sie sagen. */
    public final com.dan.geiranger.scene.Regie regie = new com.dan.geiranger.scene.Regie();
    private final RegieCtx regieCtx = new RegieCtx();
    /**
     * Phase 16: der Kinomodus (com.dan.kino). Ein Regiebuch im Kino: ohne Bedienfeld und HUD, Balken 2,39 : 1, Vor- und
     * Abspann, Untertitel aus den Stichworten, Überblendung bei jedem Schnitt, ruhiger Schwenk, Filmkorn, der Ton folgt
     * dem Schwarz.
     */
    public final com.dan.kino.Kino kino = new com.dan.kino.Kino();
    private com.dan.kino.gl.KinoGl kinoGl;
    private final java.util.List<Runnable> kinoSchnitte = new java.util.ArrayList<>();
    private double kinoYaw, kinoTonBasis = Double.NaN;
    private boolean kinoFensterAn;
    private com.dan.geiranger.scene.Regie.Book kinoBuch;
    /** true, wenn das Kino beginnt (Bedienfeld weg, Vollbild), false, wenn es zu Ende ist; im Bildlauf gerufen. */
    public volatile Consumer<Boolean> kinoFenster = an -> { };
    private volatile String captureCaption, sheetName, regieMsg = "";
    private final java.util.List<BufferedImage> album = new java.util.ArrayList<>();
    private final java.util.List<String> albumCaps = new java.util.ArrayList<>();
    private double albumFps, albumGpu;
    private int albumSamples;
    /** Das zuletzt geschriebene Blatt (für Tempo und die Prüfung); null: noch keins. */
    public volatile File lastSheet;
    /** Ohne Blatt (Prüfung). */
    public volatile boolean ohneBlatt;
    /** Was {@code warte vorbei} braucht: der kleinste Abstand der Fähre zur Stelle vor den Schwestern bisher (m). */
    private double vorbeiMin = Double.MAX_VALUE;

    /** Ein Regiebuch von vorn (das Album beginnt leer). */
    public void regieStart(com.dan.geiranger.scene.Regie.Book b) {
        album.clear();
        albumCaps.clear();
        albumFps = albumGpu = 0;
        albumSamples = 0;
        regieMsg = "";
        frozen = false;
        vorbeiMin = Double.MAX_VALUE;
        regie.start(b, regieCtx);
    }

    public void regieStop() {
        regie.stop();
        if (kino.laeuft()) kino.abbrechen();
    }

    /** Ein Regiebuch im Kino (Vorspann mit Titel und Beschreibung, am Ende der Abspann). */
    public void kinoStart(com.dan.geiranger.scene.Regie.Book b) {
        frozen = false;
        regie.beiText = kino::untertitel;
        kinoBuch = b;
        kino.beginnen("Geiranger · Sieben Schwestern und elf Kehren", b.name(), b.about());
        regieStart(b);
    }

    /**
     * Phase 17 („Weiter wie zuletzt“): ein Regiebuch dort fortsetzen, wo es stand (im Kino oder im Fenster); die Fotos
     * vor dieser Stelle fehlen im Blatt.
     */
    public void regieWeiter(com.dan.geiranger.scene.Regie.Book b, double sekunde, boolean imKino) {
        if (imKino) kinoStart(b); else regieStart(b);
        regie.spulen(sekunde, regieCtx);
        hudMeldung("Regiebuch „" + b.name() + "“ weiter ab " + com.dan.geiranger.scene.Regie.zeit(regie.time()));
    }

    /**
     * Phase 17 (F35 A): der Punkt auf Gelände oder Wasser unter dem Bildpunkt (x, y, z) oder null (Himmel). Der Strahl
     * geht in wachsenden Schritten bis 30 km und wird an der Grenze halbiert.
     */
    public double[] pickGround(int px, int py, int w, int h) {
        double[] f = camera.fwd, e = camera.eye;
        double rx = -f[2], rz = f[0], rl = Math.hypot(rx, rz);
        if (rl < 1e-9) { rx = 1; rz = 0; rl = 1; }
        rx /= rl; rz /= rl;
        double ux = -rz * f[1], uy = rz * f[0] - rx * f[2], uz = rx * f[1];   // oben = rechts × vorn
        double th = Math.tan(Math.toRadians(fovy()) / 2), asp = w / (double) Math.max(1, h);
        double nx = 2.0 * (px + 0.5) / w - 1, ny = 1 - 2.0 * (py + 0.5) / h;
        double dx = f[0] + rx * nx * th * asp + ux * ny * th, dy = f[1] + ny * th * uy, dz = f[2] + rz * nx * th * asp + uz * ny * th;
        double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= dl; dy /= dl; dz /= dl;
        double t0 = 0, t = 0.5;
        while (t < 30_000) {
            double x = e[0] + dx * t, y = e[1] + dy * t, z = e[2] + dz * t;
            if (y <= flaeche(x, z)) {
                for (int k = 0; k < 24; k++) {
                    double tm = (t0 + t) / 2, xm = e[0] + dx * tm, zm = e[2] + dz * tm;
                    if (e[1] + dy * tm <= flaeche(xm, zm)) t = tm; else t0 = tm;
                }
                return new double[]{e[0] + dx * t, e[1] + dy * t, e[2] + dz * t};
            }
            t0 = t;
            t += Math.max(0.5, t * 0.01);
        }
        return null;
    }

    /** Gelände oder, über dem Fjord, der Spiegel. */
    double flaeche(double x, double z) { return terrain.fjord(x, z) ? Math.max(groundHeight(x, z), waterLevel()) : groundHeight(x, z); }

    /** Das Kino verlassen (Esc); das Regiebuch läuft im Fenster weiter. */
    public void kinoVerlassen() {
        frozen = false;
        kino.abbrechen();
    }

    /** Die Zeilen des Abspanns (erste Zeile groß). */
    java.util.List<String> abspann(com.dan.geiranger.scene.Regie.Book b) {
        java.util.List<String> z = new java.util.ArrayList<>();
        z.add(b != null ? b.name() : "Geiranger");
        z.add("Ort: der Geirangerfjord, Weltnaturerbe Westnorwegische Fjordlandschaft");
        z.add("Szene: " + clock.dateLabel());
        z.add("Gelände: HGT-Kacheln 1″ (N62E006, N62E007), Fjordgrund gemalt");
        z.add("Wände, Fälle, Wasser, Himmel, Wald, Straße, Schiffe, Menschen, Jahr und Klang: com.dan");
        if (!album.isEmpty()) z.add("Fotos: " + album.size() + " auf dem Blatt in docs/status");
        z.add("Geiranger · Sieben Schwestern und elf Kehren · " + VERSION);
        return z;
    }

    /** Ein Schnitt (Ansicht, Fahrt, Mitfahrt). Im Kino wartet er auf das Merken des Bilds für die Überblendung. */
    private void schnitt(Runnable r) {
        if (kino.schnitt()) kinoSchnitte.add(r);
        else { r.run(); kinoYaw = 0; }
    }

    /** Die wartenden Schnitte ausführen (nach dem Merken oder ohne Kino). */
    private void schnitteJetzt() {
        if (kinoSchnitte.isEmpty()) return;
        java.util.List<Runnable> l = new java.util.ArrayList<>(kinoSchnitte);
        kinoSchnitte.clear();
        for (Runnable x : l) x.run();
        kinoYaw = 0;
    }

    /** Kino weiter, Abspann am Ende des Regiebuchs, Schwenk und Ton. */
    private void kinoSchritt(double dt) {
        com.dan.kino.Kino.Lage l = kino.lage();
        if ((l == com.dan.kino.Kino.Lage.VORSPANN || l == com.dan.kino.Kino.Lage.FILM) && !regie.running() && !regieWantsFrame())
            kino.beenden(abspann(kinoBuch));
        kino.schritt(l == com.dan.kino.Kino.Lage.VORSPANN && regie.waiting() != null ? 0 : dt);   // wartet das Regiebuch im Vorspann, bleibt die Titeltafel
        if (!kino.laeuft() && !kinoSchnitte.isEmpty() && !kino.schnittFaellig()) schnitteJetzt();
        // der Schwenk: nur bei stehender Kamera, als Änderung der Blickrichtung
        double sw = kino.schwenk();
        if (camera.fahrt == null && camera.mitfahrt == null && kinoSchnitte.isEmpty()) {
            double d = sw - kinoYaw;
            if (camera.mode == Camera.Mode.FREI) camera.fyaw += d; else camera.yaw += d;
        }
        kinoYaw = sw;
        // der Ton folgt dem Schwarz
        com.dan.soundscape.Soundscape ss = klang.s;
        if (kino.sichtbar()) {
            if (Double.isNaN(kinoTonBasis)) kinoTonBasis = ss.volume;
            ss.volume = kinoTonBasis * kino.ton();
        } else if (!Double.isNaN(kinoTonBasis)) { ss.volume = kinoTonBasis; kinoTonBasis = Double.NaN; }
    }

    /** Ohne Bild (Prüfung): was der Bildlauf nach dem Zeichnen tut – Schnitte ausführen, Fotos und Blatt verwerfen. */
    public void bildlos() {
        if (kino.schnittFaellig()) { schnitteJetzt(); kino.geschnitten(); }
        if (captureCaption != null) { album.add(null); albumCaps.add(captureCaption); }
        captureCaption = null;
        sheetName = null;
    }

    /** Wie viele Fotos das laufende Regiebuch schon gemacht hat. */
    public int fotos() { return album.size(); }

    /** Ob ein Foto oder das Blatt auf das nächste Bild wartet (Tempo zeichnet dann sofort). */
    public boolean regieWantsFrame() { return captureCaption != null || sheetName != null || kino.schnittFaellig(); }

    /** „Regie: Ein Junitag · 9 Uhr: … · 1:10 von 5:34 · 3 Fotos“ (HUD). */
    public String regieLine() {
        return "Regie: " + regie.describe() + (album.isEmpty() ? "" : " · " + album.size() + " Fotos") + (regieMsg.isEmpty() ? "" : " · " + regieMsg);
    }

    /** Die drei teuersten Durchgänge der Grafikkarte jetzt: „Gelände 5,1 · Himmel 3,0 · Spiegelbild 2,2 ms“. */
    public String topPasses(int n) {
        double[] t = passMs.clone();
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < n; k++) {
            int best = -1;
            for (int i = 0; i < t.length && i < PASSES.length; i++) if (best < 0 || t[i] > t[best]) best = i;
            if (best < 0 || t[best] <= 0) break;
            b.append(k > 0 ? " · " : "").append(PASSES[best]).append(' ').append(String.format(Locale.GERMAN, "%.1f", t[best]));
            t[best] = -1;
        }
        return b.append(" ms").toString();
    }

    /** Ein Schiff der Szene nach Name (Teil, ohne Umlaute). */
    public com.dan.geiranger.scene.Flotte.Schiff schiff(String name) {
        String n = name.toLowerCase(Locale.ROOT).replace("ø", "o").replace("å", "a");
        for (com.dan.geiranger.scene.Flotte.Schiff s : flotte.flotte.schiffe)
            if (s.name.toLowerCase(Locale.ROOT).replace("ø", "o").replace("å", "a").contains(n)) return s;
        return null;
    }

    /** Auf einem bestimmten Schiff mitfahren (Sonnendeck an der Reling Backbord wie {@link #mitfahren}); false, wenn es nicht da ist. */
    public boolean mitfahren(com.dan.geiranger.scene.Flotte.Schiff f, double lookDeg) {
        if (f == null || !f.da) return false;
        final double lx = -(0.78 * f.hw - 0.7), ly = f.freibord + 6.8 + 1.65, lz = 0.05 * f.hl;
        camera.stopFahrt();
        camera.startMitfahrt(() -> {
            double[] m = f.model();
            return new double[]{m[12] + m[0] * lx + m[4] * ly + m[8] * lz, m[13] + m[1] * lx + m[5] * ly + m[9] * lz, m[14] + m[2] * lx + m[6] * ly + m[10] * lz, f.kurs, 0, 0};
        }, lookDeg);
        return true;
    }

    /**
     * Phase 16: die Fahrt „Ørnesvingen“ (Regiebuch „Fahrt über die Ørnesvingen“): im Ausflugsbus auf der rechten Spur
     * vom Ufer bei Grande (400 m vor der ersten Kehre) die elf Kehren hinauf bis zur Busbucht an der Plattform, das Auge
     * 2,6 m über der Fahrbahn, am Ende der Blick über den Fjord. null ohne Straße.
     */
    public com.dan.geiranger.camera.Fahrt ornesvingenFahrt(double dauer) { return ornesvingenFahrt(strasse.ornevegen, leute.leute.bucht, dauer); }

    /** Phase 17: dieselbe Fahrt ohne Bild (Startdaten: GR_FAHRT und GR_STUETZPUNKT); bucht wie {@code Leute.bucht} oder null. */
    public static com.dan.geiranger.camera.Fahrt ornesvingenFahrt(com.dan.geiranger.scene.Ornevegen o, double[] bucht, double dauer) {
        if (o == null || o.kehren.isEmpty()) return null;
        com.dan.road.Way w = o.way;
        double s0 = Math.max(0, o.kehren.get(0).s0() - 400);
        double s1 = bucht != null ? bucht[0] - 15 : o.kehren.get(o.kehren.size() - 1).s1() + 20;
        s1 = Math.min(s1, w.length - 1);
        double lane = w.type.laneWidth / 2;
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        float[] q = new float[8];
        java.util.List<double[]> dir = new java.util.ArrayList<>();
        for (double s = s0; s <= s1; s += 4) {
            w.at(s, 0, q);
            pts.add(new double[]{q[0] - q[4] * lane, q[1] + 2.6, q[2] + q[3] * lane});
            dir.add(new double[]{q[3], q[4]});
        }
        // glätten (±12 m), damit der Versatz auf die Spur in engen Bögen keine Schleifen zieht; nichts rückwärts
        java.util.List<double[]> glatt = new java.util.ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            double sx = 0, sz = 0, sw = 0;
            for (int k = -3; k <= 3; k++) {
                int j = Math.max(0, Math.min(pts.size() - 1, i + k));
                double g = 4 - Math.abs(k);
                sx += pts.get(j)[0] * g; sz += pts.get(j)[2] * g; sw += g;
            }
            double[] n = {sx / sw, pts.get(i)[1], sz / sw};
            if (!glatt.isEmpty()) {
                double[] l = glatt.get(glatt.size() - 1);
                double vor = (n[0] - l[0]) * dir.get(i)[0] + (n[2] - l[2]) * dir.get(i)[1];
                if (vor < 0.5) continue;
            }
            glatt.add(n);
        }
        com.dan.geiranger.camera.Fahrt fa = new com.dan.geiranger.camera.Fahrt("Ørnesvingen", glatt.toArray(new double[0][]), 1, 0, 4);
        fa.duration = Double.isNaN(dauer) ? fa.length / 9 : dauer;
        fa.endHeading = 245;
        fa.endTurn = 60;
        return fa;
    }

    /** Was die Regiebücher in der App tun. */
    private final class RegieCtx implements com.dan.geiranger.scene.Regie.Ctx {
        @Override public void time(java.time.LocalDate date, double hour) { clock.set(date, hour); readapt(); jahr.settle(clock.epochMillis() / 1000.0); }
        @Override public void lapse(double x) { clock.lapse = x; }
        @Override public void weather(com.dan.weather.Scenario s) {
            if (wiring == null) return;
            wiring.regie = true;
            wiring.resumeRegie();
            wiring.start(s);
        }
        @Override public void weatherFree(com.dan.weather.Weather.Phase p, double bft, double fromDeg) {
            if (wiring == null) return;
            wiring.regie = true;
            wiring.resumeRegie();
            wiring.startFree(p);
            if (!Double.isNaN(bft)) wiring.hand(com.dan.weather.Weather.Var.WIND, com.dan.geiranger.wiring.Beaufort.mid((int) Math.round(bft)));
            if (!Double.isNaN(fromDeg)) wiring.hand(com.dan.weather.Weather.Var.DIRECTION, fromDeg);
        }
        @Override public void view(String name) {
            schnitt(() -> {
                camera.stopFahrt();
                camera.stopMitfahrt();
                Ansichten.Ansicht a = Ansichten.byName(name);
                if (a == null || !placeView(a)) regieMsg = "Ansicht „" + name + "“ gibt es nicht";
                readapt();
            });
        }
        @Override public void fahrt(String name, double dauer) {
            schnitt(() -> {
                String k = name.toLowerCase(Locale.ROOT).replace("ø", "o");
                if (k.contains("ornes") || k.contains("kehren")) {
                    com.dan.geiranger.camera.Fahrt f = ornesvingenFahrt(dauer);
                    if (f == null) { regieMsg = "die Fahrt über die Ørnesvingen hat keine Straße"; return; }
                    camera.stopMitfahrt();
                    camera.startFahrt(f);
                } else regieMsg = "Fahrt „" + name + "“ gibt es nicht";
                readapt();
            });
        }
        @Override public void mitfahrt(String name, double blick) {
            com.dan.geiranger.scene.Flotte.Schiff f = schiff(name);
            // dasselbe Schiff, schon an Bord: nur der Blick dreht (kein Schnitt)
            if (f != null && camera.mitfahrt != null && mitSchiff == f) { camera.mitBlick(blick); return; }
            schnitt(() -> {
                if (!mitfahren(f, blick)) regieMsg = "„" + name + "“ ist nicht in der Szene";
                else mitSchiff = f;
                readapt();
            });
        }
        @Override public boolean ready(com.dan.geiranger.scene.Regie.Warte w) {
            com.dan.geiranger.scene.Flotte.Schiff f = w.arg().isEmpty() ? null : schiff(w.arg());
            double d = f == null || !f.da ? Double.MAX_VALUE : Math.hypot(f.x - com.dan.geiranger.scene.Flotte.SCHWESTERN_PASS[0], f.z - com.dan.geiranger.scene.Flotte.SCHWESTERN_PASS[1]);
            if (w.was().equals("vorbei") && d < vorbeiMin) { vorbeiMin = d; return false; }
            return switch (w.was()) {
                case "uhr" -> clock.epochMillis() / 3_600_000.0 >= w.hours();
                case "faehre" -> f != null && f.da;
                case "schwestern" -> d < SCHWESTERN_NAH;
                case "vorbei" -> vorbeiMin < 600 && d > vorbeiMin + 5;
                case "angelegt" -> f != null && f.da && (f.zustand.startsWith("legt an") || f.zustand.startsWith("liegt"));
                case "fahrt" -> camera.fahrt == null;
                default -> true;
            };
        }
        @Override public void capture(String caption) { captureCaption = caption; }
        @Override public void sheet(String name) { sheetName = name; }
        @Override public void say(String text) { regieMsg = text; kino.untertitel(text); }
    }

    /** {@code warte schwestern}: so nah an der Stelle vor den Sieben Schwestern (m). */
    public static final double SCHWESTERN_NAH = 900;
    /** Das Schiff, auf dem die Kamera zuletzt mitfährt (Regiebuch). */
    private com.dan.geiranger.scene.Flotte.Schiff mitSchiff;

    /** Die Fotos des Regiebuchs als ein Blatt nach docs/status (zwei Spalten, Unterschrift in zwei Zeilen; aus Rungholt). */
    private void writeSheet(String name) {
        java.util.List<BufferedImage> bilder = new java.util.ArrayList<>();
        java.util.List<String> caps = new java.util.ArrayList<>();
        for (int i = 0; i < album.size(); i++) if (album.get(i) != null) { bilder.add(album.get(i)); caps.add(albumCaps.get(i)); }
        if (bilder.isEmpty() || ohneBlatt) return;
        try {
            int n = bilder.size(), cols = 2, rows = (n + 1) / 2, cw = 960, ch = (int) Math.round(bilder.get(0).getHeight() * cw / (double) bilder.get(0).getWidth()), cap = 50, head = 44;
            BufferedImage out = new BufferedImage(cols * cw, head + rows * (ch + cap), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new java.awt.Color(13, 19, 22));
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.setColor(new java.awt.Color(231, 236, 236));
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 20));
            g.drawString(String.format(Locale.GERMAN, "Geiranger · Regiebuch „%s“ · %s · %s · Ø %.0f Bilder/s · Grafikkarte Ø %.2f ms · %s", name, clock.dateLabel(), quality.label,
                    albumFps / Math.max(1, albumSamples), albumGpu / Math.max(1, albumSamples), glInfo.replaceAll(" ·.*", "")), 14, 29);
            g.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 16));
            for (int i = 0; i < n; i++) {
                int cx = (i % cols) * cw, cy = head + (i / cols) * (ch + cap);
                g.drawImage(bilder.get(i), cx, cy, cw, ch, null);
                g.setColor(new java.awt.Color(143, 160, 164));
                String[] ln = caps.get(i).split("\n");
                for (int k = 0; k < ln.length; k++) g.drawString(ln[k], cx + 10, cy + ch + 21 + 20 * k);
            }
            g.dispose();
            File dir = photoDir != null ? photoDir : new File("docs/status");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Ordner " + dir + " lässt sich nicht anlegen");
            String fn = "Regiebuch_" + name.replace("ü", "ue").replace("ö", "oe").replace("ä", "ae").replace("ß", "ss").replace("ø", "o").replace("å", "a").replace(' ', '_') + "_"
                    + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".png";
            File f = new File(dir, fn);
            javax.imageio.ImageIO.write(out, "png", f);
            photoMsg = "Regiebuch gespeichert: " + f.getPath();
            lastSheet = f;
            System.out.println(photoMsg);
        } catch (Exception e) {
            photoMsg = "Blatt des Regiebuchs fehlgeschlagen: " + e.getMessage();
            System.err.println(photoMsg);
        }
        photoMsgUntil = System.currentTimeMillis() + 6000;
    }

    /** Schnitt mit gemerktem Bild, dann Balken, Schwarz, Untertitel; Fenster auf und zu. */
    private void kinoBild(GL4 gl, long now) {
        if (kinoGl != null && kinoGl.bereit()) {
            try {
                if (kino.schnittFaellig()) {
                    kinoGl.merken(gl, defaultFbo, surfW, surfH);
                    schnitteJetzt();
                    kino.geschnitten();
                }
                kinoGl.zeichnen(gl, defaultFbo, surfW, surfH, now / 1e9);
            } catch (RuntimeException e) {
                System.err.println("Geiranger: Kino: " + e);
                kinoGl = null;
            }
        } else if (kino.schnittFaellig()) { schnitteJetzt(); kino.geschnitten(); }
        boolean an = kino.laeuft();
        if (an != kinoFensterAn) {
            kinoFensterAn = an;
            try { kinoFenster.accept(an); } catch (RuntimeException e) { System.err.println("Geiranger: Kinofenster: " + e); }
        }
        if (kino.endeMelden()) hudMeldung("Kino zu Ende" + (lastSheet != null ? " · Blatt: " + lastSheet.getName() : ""));
    }

    /**
     * Phase 15: auf der Fähre mitfahren (Abnahme: „Eine Fahrt mit der Fähre“). Nimmt die Fähre, die gerade fährt
     * (die der Kamera am nächsten), Auge auf dem Sonnendeck an der Reling Backbord, Blick voraus. false ohne Fähre in der Szene.
     */
    public boolean mitfahren() { return mitfahren(0); }

    /** Wie {@link #mitfahren()}, Blick um lookDeg zur Seite (negativ nach Backbord). */
    public boolean mitfahren(double lookDeg) {
        // Nacharbeit 15: die Fähre, die der Kamera am nächsten ist (fahrend vor liegend)
        com.dan.geiranger.scene.Flotte.Schiff best = null;
        double bd = Double.MAX_VALUE;
        for (com.dan.geiranger.scene.Flotte.Schiff s : flotte.flotte.schiffe) {
            if (s.art != com.dan.geiranger.scene.Flotte.Art.FAEHRE || !s.da) continue;
            double d = Math.hypot(s.x - camera.eye[0], s.z - camera.eye[2]) + (s.fahrt > 0.5 ? 0 : 20_000);
            if (d < bd) { bd = d; best = s; }
        }
        if (best == null) return false;
        final com.dan.geiranger.scene.Flotte.Schiff f = best;
        // Nacharbeit 15 (Status_Phase15a): das Auge stand im Salon (Wände und Fensterbänder flackerten); jetzt auf dem
        // Sonnendeck über dem Salon (fb + 6,8 m) an der Reling Backbord, hinter der Brücke, 1,65 m über dem Deck
        final double lx = -(0.78 * f.hw - 0.7), ly = f.freibord + 6.8 + 1.65, lz = 0.05 * f.hl;
        camera.startMitfahrt(() -> {
            double[] m = f.model();
            // Modellmatrix spaltenweise (x rechts, y oben, z nach hinten)
            return new double[]{m[12] + m[0] * lx + m[4] * ly + m[8] * lz, m[13] + m[1] * lx + m[5] * ly + m[9] * lz, m[14] + m[2] * lx + m[6] * ly + m[10] * lz, f.kurs, 0, 0};
        }, lookDeg);
        return true;
    }

    public void setQuality(Quality q) {
        quality = q;
        renderScale = q.renderScale;
        lod.lodRange = q.terrainRange;
        lod.finest = q.terrainFine;
        lod.finestKern = q.terrainKern;
        wasser.ocean.quality = q.ocean;
        wetter.clouds.quality = q.clouds;
        wetter.rain.quality = q.rain;
        wetter.nebel.quality = q.fog;
        wetter.band.quality = q.fog;
        if (fels != null) {
            fels.cliff.quality = q.cliff;
            fels.fallQuality = q.fall;
        }
        if (wald != null) wald.quality(q);
        // Phase 18: auch Ort, Fv63, Menschen, Kielwasser und Schneefall folgen der Stufe
        if (ort != null) ort.quality(q.timber());
        if (strasse != null && strasse.roads != null) {
            strasse.roads.radius = (float) q.strasseRadius();
            strasse.roads.vehicleRange = (float) q.fahrzeugReichweite();
        }
        if (leute != null) {
            double[] px = q.leutePx();
            leute.feinPx = px[0];
            leute.mittelPx = px[1];
            leute.minPx = px[2];
        }
        if (flotte != null) flotte.kielwasser.stufe = q.kielwasser();
    }

    /** Die Abflussregie der Fälle (F13): aus der Verdrahtung, ohne sie die eigene. */
    public com.dan.geiranger.wiring.Abfluss abfluss() { return wiring != null ? wiring.abfluss : ownAbfluss; }

    /** Höhe des Grunds am Ort (das Gelände). */
    public double groundHeight(double x, double z) { return terrain.height(x, z); }

    /** Spiegel des Fjords (NN, von Hand verschiebbar). */
    public double waterLevel() { return wiring != null ? wiring.water.level() : 0; }

    /** Der Spiegel unter dem Auge: über dem Fjord der Wasserspiegel, sonst der Grund; bei „Grund zeigen“ immer der Grund. */
    double waterUnder(double x, double z) {
        if (showBed) return groundHeight(x, z);
        return terrain.fjord(x, z) ? Math.max(waterLevel(), groundHeight(x, z)) : groundHeight(x, z);
    }

    /**
     * Worauf man am Ort steht: über dem Fjord der Spiegel (seit Phase 3 liegt dort der Grund bis 250 m tiefer), sonst
     * der Grund; bei „Grund zeigen“ immer der Grund. Für Ansichten, den Drehpunkt der Kamera und freie Standorte.
     */
    public double standHeight(double x, double z) {
        double g = groundHeight(x, z);
        return !showBed && terrain.fjord(x, z) ? Math.max(waterLevel(), g) : g;
    }

    /** Wie das Gelände das Wasser zeichnet (terrain.frag): 0 ocean, 1 Platzhalter-Spiegel (ocean fehlt), 2 Grund zeigen. */
    public int waterMode() { return showBed ? 2 : wasser.ok ? 0 : 1; }

    /** Tiefe des Fjords unter dem Spiegel am Ort (m, 0 auf dem Land). */
    public double depthAt(double x, double z) { return terrain.fjord(x, z) ? Math.max(0, waterLevel() - groundHeight(x, z)) : 0; }

    /** Stellt die Kamera auf eine Ansicht nach Namen. */
    public boolean placeView(Ansichten.Ansicht a) {
        // Phase 3: der Blick auf den Grund lässt das Wasser weg; seit Phase 11 schaltet die nächste Ansicht es wieder ein,
        // wenn es Ansicht 7 abgeschaltet hatte (von Hand abgeschaltet bleibt es aus)
        if (a.key() == Ansichten.FJORDGRUND) {
            if (!showBed) { showBed = true; bedVonAnsicht = true; }
        } else if (bedVonAnsicht) {
            showBed = false;
            bedVonAnsicht = false;
        }
        boolean ok = Ansichten.apply(a, camera, PHASE, (x, z) -> a.key() == Ansichten.FJORDGRUND ? Math.max(waterLevel(), groundHeight(x, z)) : standHeight(x, z));
        // Phase 8a: „Am Storfossen“ steht auf der Treppe (ihr Standort kommt aus den Bauten, nicht aus dem Gelände)
        double[] b = fluss.bauten.blick;
        if (ok && a.key() == Ansichten.STORFOSSEN && b != null) camera.place(b[0], b[1], b[2], b[3], b[4]);
        // Phase 9: „Plattform“ steht auf dem Deck der Ørnesvingen, 2 m vor dem äußeren Geländer
        Plattform pf = strasse.plattform;
        if (ok && a.key() == Ansichten.PLATTFORM && pf != null) {
            double r = Math.toRadians(pf.richtung), d = Plattform.TIEFE / 2 - 2.5;
            camera.place(pf.x + Math.sin(r) * d, pf.y + 1.7, pf.z - Math.cos(r) * d, a.heading(), a.pitch());
        }
        if (ok) hudMeldung("Ansicht " + a);
        return ok;
    }

    /** Eine Meldung für 5 s im HUD (und in der Statuszeile). */
    public void hudMeldung(String text) {
        meldung = text;
        meldungUntil = System.currentTimeMillis() + 5000;
    }

    /**
     * Belichtung: seit Phase 4 aus der Augenanpassung an Himmel und Blickfeld ({@code com.dan.sky}, wie Rungholt), ohne
     * Himmel aus der Sonnenhöhe des Platzhalters (am Tag rund 1,9).
     */
    public double exposure() {
        if (wetter.skyOk && adaptLum > 0) return Sky.State.exposureFor(adaptLum, adaptation) * Math.pow(2, exposureBias);
        double b = Sun.skyBrightness(clock.sun()[0]);
        return 0.6 * Math.pow(b, -0.85) * Math.pow(2, exposureBias);
    }

    /** Augenanpassung neu (nach Sprüngen der Uhr oder der Kamera). */
    public void readapt() {
        viewLum = -1;
        adaptLum = -1;
        belichtung.reset();
        schatten.invalidate();
        if (wetter.skyOk) adapt(wetter.state(), 1);
    }

    /** Wolkenschatten am Auge (1 = Sonne) und ob dort die Sonne das Wasser erreicht (0..1). */
    public double cloudShade() { return cloudShade; }
    public double sunOnWater() { return sunOnWater; }

    private void adapt(Sky.State st, double dt) {
        viewTimer -= dt;
        if (viewLum <= 0 || viewTimer <= 0) {
            viewLum = st.viewLuminance(camera.fwd.clone(), new double[]{0, 1, 0}, fovy(), surfW / (double) Math.max(1, surfH));
            landInView = landInView();
            viewLum *= Math.pow(LAND_GAIN, landInView);
            viewTimer = 0.2;
        }
        double gemessen = bildMessung ? belichtung.lum() : -1;
        double target = gemessen > 0
                // Phase 8: das Bild selbst (Wolken, Wandschatten und sonnige Wände sind darin schon enthalten)
                ? Math.pow(Math.max(1e-14, st.adaptation), 1 - BILD_FOLGT) * Math.pow(Math.max(1e-14, gemessen * BILD_GAIN), BILD_FOLGT)
                : Math.sqrt(Math.max(1e-14, st.adaptation) * Math.max(1e-14, viewLum))
                * (wetter.cloudsOk && showClouds ? Math.pow(wetter.clouds.brightnessFactor(), STORM_ADAPT) : 1)
                * (0.55 + 0.45 * sunOnWater);   // im Schatten der Wände ist es dunkler, das Auge folgt zur Hälfte
        if (adaptLum <= 0 || frozen || dt > 0.5) adaptLum = target;
        else adaptLum = Math.exp(Math.log(adaptLum) + (Math.log(target) - Math.log(adaptLum)) * Math.min(1, dt / 1.0));
    }

    /**
     * Wie viele der zwölf Messstrahlen unter der Kimm auf Land statt Wasser fallen (0..1), wie Rungholt: jeder Strahl
     * läuft in wachsenden Schritten bis 15 km gegen Gelände und Spiegel.
     */
    double landInView() {
        double[] f = camera.fwd, up = {0, 1, 0};
        double[] rt = {f[1] * up[2] - f[2] * up[1], f[2] * up[0] - f[0] * up[2], f[0] * up[1] - f[1] * up[0]};
        double rl = Math.max(1e-9, Math.sqrt(rt[0] * rt[0] + rt[1] * rt[1] + rt[2] * rt[2]));
        rt[0] /= rl; rt[1] /= rl; rt[2] /= rl;
        double[] u = {rt[1] * f[2] - rt[2] * f[1], rt[2] * f[0] - rt[0] * f[2], rt[0] * f[1] - rt[1] * f[0]};
        double ty = Math.tan(Math.toRadians(fovy()) / 2), tx = ty * surfW / (double) Math.max(1, surfH), lv = waterLevel();
        int land = 0;
        for (int j = 0; j < 3; j++)
            for (int i = 0; i < 4; i++) {
                double sx = ((i + 0.5) / 4 * 2 - 1) * tx * 0.8, sy = ((j + 0.5) / 3 * 2 - 1) * ty * 0.8;
                double dx = f[0] + rt[0] * sx + u[0] * sy, dy = f[1] + rt[1] * sx + u[1] * sy, dz = f[2] + rt[2] * sx + u[2] * sy;
                double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= dl; dy /= dl; dz /= dl;
                double t = 1, step = 2;
                while (t < 15000) {
                    double x = camera.eye[0] + dx * t, y = camera.eye[1] + dy * t, z = camera.eye[2] + dz * t;
                    double g = terrain.height(x, z), wl = terrain.fjord(x, z) && !showBed ? lv : -1e9;
                    if (y <= Math.max(g, wl)) {
                        if (g > wl + 0.05) land++;
                        break;
                    }
                    t += step;
                    step = Math.min(120, step * 1.25);
                }
            }
        return land / 12.0;
    }

    // ------------------------------------------------------------------ GL

    @Override
    public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        int major = d.getContext().getGLVersionNumber().getMajor(), minor = d.getContext().getGLVersionNumber().getMinor();
        String renderer = gl.glGetString(GL.GL_RENDERER), vendor = gl.glGetString(GL.GL_VENDOR), version = gl.glGetString(GL.GL_VERSION);
        glInfo = renderer + " · OpenGL " + major + "." + minor;
        System.out.println("Geiranger: " + vendor + " · " + renderer + " · " + version);
        if (major < MIN_MAJOR || (major == MIN_MAJOR && minor < MIN_MINOR)) {
            fail("Die Grafikkarte meldet OpenGL " + major + "." + minor + " (" + renderer + ", Treiber " + version
                    + "). Geiranger braucht OpenGL " + MIN_MAJOR + "." + MIN_MINOR + ".\nBitte den Grafiktreiber aktualisieren.");
            return;
        }
        gl.setSwapInterval(1);
        defaultFbo = d.getContext().getDefaultDrawFramebuffer();
        reversedZ = (major > 4 || minor >= 5) || gl.isExtensionAvailable("GL_ARB_clip_control");
        if (reversedZ) gl.glClipControl(GL4.GL_LOWER_LEFT, GL4.GL_ZERO_TO_ONE);
        try {
            for (Shader s : all) s.build(gl);
        } catch (RuntimeException e) {
            fail(e.getMessage());
            return;
        }
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        emptyVao = v[0];
        for (int[] s : stamp) gl.glGenQueries(MARKS, s, 0);
        compute = ComputeProbe.run(gl);
        System.out.println("Geiranger: " + compute.line());
        lod.upload(gl, terrain);
        wasser.init(gl);
        belichtung.init(gl);
        System.out.println("Geiranger: " + wasser.describe(false));
        wetter.init(gl, errorListener);
        fels.init(gl);
        faelle.init(gl);
        fluss.init(gl);
        strasse.init(gl);
        wald.init(gl);
        ort.init(gl);
        flotte.init(gl);
        leute.init(gl);
        try { schneefall.init(gl); schneeOk = schneefall.ready(); } catch (RuntimeException e) { System.err.println("Geiranger: Schneefall aus – " + e.getMessage()); }
        try {   // Phase 16: der Kinomodus
            kinoGl = new com.dan.kino.gl.KinoGl(kino, new com.dan.kino.KinoMaler());
            kinoGl.init(gl);
        } catch (RuntimeException | LinkageError e) {
            System.err.println("Geiranger: Kino nicht verfügbar (ohne Balken und Blenden): " + e.getMessage());
            kinoGl = null;
        }
        leute.leute.starten();   // Phase 13: Avatar.jar lädt und backt im Faden „Menschen“
        tundraTex = tundra(gl);
        if (wiring != null) wiring.cloudsDrawn = wetter.cloudsOk;
        schatten.size(gl, quality.shadowN);
        System.out.println("Geiranger: Gelände " + terrain.describe());
        physik.start();
        lastNanos = System.nanoTime();
        statsNanos = lastNanos;
        camera.snap();
        ready = true;
    }

    @Override
    public void reshape(GLAutoDrawable d, int x, int y, int w, int h) {
        surfW = Math.max(1, d.getSurfaceWidth());
        surfH = Math.max(1, d.getSurfaceHeight());
    }

    /** Uhr, Physik und Kamera um dt weiterrechnen (vom Bildlauf aufgerufen; Standbilder rufen es mit festem dt). */
    public void step(double dt) {
        regie.step(dt, regieCtx);   // Phase 16: fällige Stichworte des Regiebuchs (Uhr, Wetter, Ansicht, Fahrt)
        kinoSchritt(dt);
        clock.step(dt);
        physik.advance(dt);
        // Phase 4: zuerst die Wetterregie, dann bekommen Himmel, Wolken, Regen und Nebel ihre Eingänge
        boolean cloudsShown = wetter.cloudsOk && showClouds;
        if (wiring != null) {
            if (wiring.regie) wiring.feedWeather(dt);
            wiring.cloudsDrawn = cloudsShown;
            wiring.feed(wetter.sky, camera.eye[1], Site.LAT, Site.LON);
            wiring.feed(wetter.clouds);
        } else wetter.sky.epochMillis = clock.epochMillis();
        if (wetter.skyOk) {
            Sky.State st = wetter.sky.step(dt);
            wetter.clouds.step(dt);
            cloudShade = cloudsShown ? Clouds.shadow(wetter.clouds.snapshot(), camera.eye[0], camera.eye[2], Wetter.keyDir(st)) : 1;
            sunOnWaterTimer -= dt;
            if (sunOnWaterTimer <= 0 || frozen) {
                sunOnWater = terrain.measured() ? Wandschatten.licht(terrain, camera.eye[0], Math.max(camera.eye[1], Math.max(waterLevel(), groundHeight(camera.eye[0], camera.eye[2])) + 0.5),
                        camera.eye[2], Wetter.keyDir(st)) : 1;
                sunOnWaterTimer = 0.5;
            }
            adapt(st, dt);
            if (wiring != null) {
                wiring.feed(wetter.rain, wetter.clouds, wetter.clouds.snapshot(), cloudsShown, camera.eye.clone());
                // Phase 14: unter rund 1 °C am Auge fällt Schnee statt Regen (Anteil nach season)
                double tAuge = wiring.airTempC - com.dan.geiranger.scene.Jahr.LAPSE * Math.max(0, camera.eye[1]);
                double regenAnteil = showJahr ? com.dan.season.Year.rainFraction(tAuge) : 1;
                schneeRate = wetter.rain.rate * (1 - regenAnteil);
                wetter.rain.rate *= regenAnteil;
                wiring.feedNebel(wetter.nebel, st.sunAlt);
                wiring.feedBand(wetter.band);
            }
        }
        if (!showRain) wetter.rain.rate = 0;
        // Phase 5: die Wände werden nass, wenn es regnet (in rund 20 min), und trocknen in rund 3 h (Szenenzeit)
        double sceneDt = dt * Math.max(60, clock.lapse), target = wiring == null ? 0 : Math.min(1, wiring.rainRate / 1.5);
        rockWet += (target - rockWet) * (1 - Math.exp(-sceneDt / (target > rockWet ? 1200 : 10_800)));
        // Phase 7: Abfluss aus Jahreszeit, Regen und Frost; die Fälle bekommen ihn, Wind und Temperatur; Nässe an cliff
        stepFaelle(dt);
        // Phase 14: das Jahr (season in zehn Höhenstufen) mit dem Wetter des Augenblicks; Frost an die Fälle
        double ep = clock.epochMillis() / 1000.0;
        if (showJahr) {
            jahr.step(ep, wiring != null ? wiring.airTempC : 15, wiring != null && showRain ? wiring.rainRate : 0, clock.sun()[0], wiring != null ? wiring.oktas : 2,
                    wiring != null ? wiring.wind.speed() : 2);
            double e = jahr.eis(ep);
            abfluss().frostTemp = e > 0.001 ? com.dan.geiranger.scene.Jahr.fallTemperatur(e) : Double.POSITIVE_INFINITY;
        } else abfluss().frostTemp = Double.POSITIVE_INFINITY;
        // Phase 9: Verkehr und Fahrbahn rechnet der Faden der Straße; hier nur Auge, Zeit und Wetter (seit Phase 14 mit Schnee)
        strasse.wetter(wiring == null ? 0 : wiring.rainRate, clock.sun()[0], wiring == null ? 0 : wiring.wind.speed(), wiring == null ? 270 : wiring.wind.fromDeg(),
                showJahr ? Math.min(1, jahr.schnee(300) / 0.05) : 0);
        strasse.step(camera.eye, clock.t(), dt, clock.hour());
        wetter.rain.step(dt);
        wetter.nebel.step(dt);
        wetter.band.step(dt);
        // Phase 3: ocean läuft mit der Uhr; seit Phase 4 Wind und Böe aus der Regie, Spiegel aus der Verdrahtung
        if (wiring != null) Wasser.wind(wasser.ocean, wiring.regie ? wiring.gustNow * 0.5 + wiring.wind.speed() * 0.5 : wiring.wind.speed(), wiring.wind.fromDeg());
        wasser.ocean.waterLevel = waterLevel();
        wasser.ocean.step(dt);
        // Phase 12: die Schiffe nach der Uhr, Tauchen, Stampfen und Rollen auf ocean; ihre Rümpfe an ocean
        flotte.flotte.schritt(clock.epochMillis() / 1000.0, schiffSee(), dt);
        wasser.ocean.hulls = flotte.huellen(camera.eye);
        // Phase 13: die Gäste gehen im Faden „Menschen“ (Bewegung, Verhalten, Gelenke); die Figuren sind Funktionen der Uhr
        leute.leute.step(clock.epochMillis() / 1000.0, dt);
        // Phase 15: der Klang (Ohr an der Kamera)
        stepKlang(dt);
        camera.groundLevel = groundHeight(camera.eye[0], camera.eye[2]);
        camera.targetGround = standHeight(camera.tx, camera.tz);
        camera.waterLevel = waterUnder(camera.eye[0], camera.eye[2]);
        camera.step(dt);
    }

    @Override
    public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        if (!ready) {
            gl.glClearColor(0.05f, 0.07f, 0.09f, 1);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            return;
        }
        if (reloadShaders) {
            reloadShaders = false;
            try {
                for (Shader s : all) s.build(gl);
                hudMeldung("Shader neu geladen");
                System.out.println("Shader neu geladen.");
            } catch (RuntimeException e) {
                System.err.println(e.getMessage());
                errorListener.accept(e.getMessage());
            }
        }
        defaultFbo = d.getContext().getDefaultDrawFramebuffer();
        readMarks(gl);
        long now = System.nanoTime();
        double rawMs = (now - lastNanos) / 1e6;
        if (frame > 2 && rawMs > worstMs) worstMs = rawMs;
        double dt = Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (!frozen) step(dt);

        surfW = Math.max(1, d.getSurfaceWidth());
        surfH = Math.max(1, d.getSurfaceHeight());
        regulateScale(now);
        int w = Math.max(64, (int) Math.round(surfW * renderScale)), h = Math.max(64, (int) Math.round(surfH * renderScale));
        if (w != hdrW || h != hdrH) resizeTargets(gl, w, h);
        renderFrame(gl, w, h);
        // Phase 16: Fotos eines Regiebuchs (ohne HUD und Balken) und das Blatt am Ende
        String cap = captureCaption;
        if (cap != null) {
            captureCaption = null;
            album.add(readDefault(gl, surfW, surfH));
            albumCaps.add(clock.timeLabel() + " · " + cap + "\nteuer: " + topPasses(3) + String.format(Locale.GERMAN, " · %.0f Bilder/s", fps));
        }
        if (regie.running()) { albumFps += fps; albumGpu += gpuMs; albumSamples++; }
        String sn = sheetName;
        if (sn != null) { sheetName = null; writeSheet(sn); }
        kinoBild(gl, now);   // Phase 16: Kino über das fertige Bild; F12 und Tempo mit
        if (photoRequest) { photoRequest = false; takePhoto(gl, surfW, surfH); }
        Consumer<BufferedImage> gr = grabRequest;
        if (gr != null) { grabRequest = null; try { gr.accept(readDefault(gl, surfW, surfH)); } catch (RuntimeException e) { System.err.println("Geiranger: Bild nicht abgeholt: " + e); } }
        cpuMs = cpuMs * 0.95 + (System.nanoTime() - now) / 1e6 * 0.05;
        frame++;
        stats(now, w, h);
    }

    /** Phase 7: Abflussregie, Fälle (Neurechnung im Hintergrund), Ringwellen an ocean, Nässe der Wände, Licht je Fall. */
    private void stepFaelle(double dt) {
        com.dan.geiranger.wiring.Abfluss a = abfluss();
        java.util.List<com.dan.geiranger.scene.Faelle.Eintrag> fs = fels.faelle();
        if (fs != null && faelle.eintraege().isEmpty() && !fs.isEmpty()) {
            for (com.dan.geiranger.scene.Faelle.Eintrag e : fs)
                a.register(e.def.name(), e.def.juni(), e.def.art() == com.dan.geiranger.scene.Faelle.Art.REGEN);
            faelle.set(fs);
        }
        a.register(ELVA, com.dan.geiranger.scene.Elva.JUNI, false);
        elvaTurbid = Math.min(0.95, 0.55 + a.regen / 40);   // Gletschermilch, nach Regen trüber [G]
        double air = wiring != null ? wiring.airTempC : 15, rain = wiring != null && showRain ? wiring.rainRate : 0;
        a.step(dt * Math.max(1, clock.lapse), rain, air, clock.date());
        if (faelle.eintraege().isEmpty()) return;
        double ws = wiring != null ? wiring.wind.speed() : 0, wf = wiring != null ? wiring.wind.fromDeg() : 270;
        faelle.step(a, ws, wf, quality.fall);
        fels.cliff.fallWet = Wasserfaelle.wet(a);
        if (wasser.ok && !showBed) faelle.ripples(wasser.ocean, a, camera.eye, dt, clock.t());
        double[] sp = clock.sun();
        double[] key = wetter.skyOk && frameSt != null ? Wetter.keyDir(frameSt) : Sun.direction(sp[0], sp[1]);
        faelle.updateLight(terrain, key, dt, false);
    }

    /** Name der Geirangelva in der Abflussregie. */
    public static final String ELVA = "Geirangelva";

    /** Phase 8: Schatten der Wände (Einheiten 11, 12) und Wolkenschatten (13) an ein Programm mit {@code licht.glsl} oder das Gelände. */
    private void bindShadows(GL4 gl, Shader s) {
        boolean wall = schatten.on && terrain.measured();
        gl.glActiveTexture(GL.GL_TEXTURE0 + 11);
        gl.glBindTexture(GL.GL_TEXTURE_2D, wall ? schatten.mainTexture() : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0 + 12);
        gl.glBindTexture(GL.GL_TEXTURE_2D, wall ? schatten.fernTexture() : 0);
        boolean cloudsNow = frameSky && wetter.cloudsOk && showClouds;
        gl.glActiveTexture(GL.GL_TEXTURE0 + 13);
        gl.glBindTexture(GL.GL_TEXTURE_2D, cloudsNow ? wetter.cloudGl.shadowTexture() : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        s.set(gl, "uWallMain", 11);
        s.set(gl, "uWallFern", 12);
        s.set(gl, "uCloudShadow", 13);
        s.set(gl, "uWallOn", wall ? 1 : 0);
        double[] mr = schatten.mainRect(), fr = schatten.fernRect();
        s.set(gl, "uWallRectMain", (float) mr[0], (float) mr[1], (float) mr[2]);
        s.set(gl, "uWallRectFern", (float) fr[0], (float) fr[1], (float) fr[2]);
        double[] cr = cloudsNow ? wetter.cloudGl.shadowRect() : new double[]{0, 0, 1};
        s.set(gl, "uCloudRect", (float) cr[0], (float) cr[1], (float) Math.max(1, cr[2]), cloudsNow ? 1f : 0f);
    }

    /** Phase 8: Treppe, Geländer und Steine (clip: im Spiegelbild, alles unter dem Spiegel weg). */
    private void drawBauten(GL4 gl, float[] vp, float[] inv, double[] sun, double level, double[] eye, boolean clip) {
        if (!fluss.ok) return;
        common(gl, bautenShader, vp, inv, sun, level, eye);
        lod.bind(gl, bautenShader);
        bindShadows(gl, bautenShader);
        bautenShader.set(gl, "uClip", clip ? 1 : 0);
        bautenShader.set(gl, "uLamp", LAMPE);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        fluss.drawBauten(gl);
        if (clip) ort.drawBauten(gl);   // Phase 11: der Ort im Spiegelbild
        gl.glBindVertexArray(emptyVao);
    }

    private int tundraTex;
    /** Kachel der Tundra (Phase 10, F10 A): Ausschnitt aus der BaseColor des Scans, 1024², rund 90 m. */
    public static final String TUNDRA = "tundra/tundra_1024.jpg";

    /** Lädt die Tundra-Kachel aus daten/ (gespiegelt wiederholt, mit Mip-Stufen); ohne Datei 0. */
    private static int tundra(GL4 gl) {
        java.io.File f = new java.io.File(Terrain.defaultDir().getParentFile(), TUNDRA);
        try {
            java.awt.image.BufferedImage im = javax.imageio.ImageIO.read(f);
            if (im == null) return 0;
            int w = im.getWidth(), h = im.getHeight();
            int[] px = im.getRGB(0, 0, w, h, null, 0, w);
            java.nio.ByteBuffer b = java.nio.ByteBuffer.allocateDirect(w * h * 4);
            for (int p : px) b.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) 255);
            b.flip();
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
            int lv = 1 + (int) Math.floor(Math.log(Math.max(w, h)) / Math.log(2));
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, lv, GL.GL_SRGB8_ALPHA8, w, h);
            gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, b);
            gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_MIRRORED_REPEAT);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_MIRRORED_REPEAT);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            System.out.println("Geiranger: Tundra " + w + "² aus " + f.getPath());
            return t[0];
        } catch (java.io.IOException | RuntimeException e) {
            System.err.println("Geiranger: Tundra fehlt (" + f.getPath() + "): " + e.getMessage());
            return 0;
        }
    }

    /** Phase 10: Wald, Bodendecke und Höfe; Licht wie die Fälle (vorbelichtet), die Schatten der Wände von oben. */
    private void drawWald(GL4 gl, float[] vp, float[] inv, double[] sun, double level, Sky.State st, double pre) {
        if (wald.vestland.wand == null && fels.map() != null) {
            com.dan.cliff.CliffMap m = fels.map();
            wald.vestland.wand = (x, z) -> m.valuesAt(x, z)[0];
            wald.field.clear();   // was ohne Wandkarte gepflanzt war, neu
            wald.starteKarte();   // Nacharbeit 10: Kronendach im Gelände
        }
        double ws = wiring != null ? (wiring.regie ? wiring.gustNow : wiring.wind.speed()) : 3;
        double[] to = wiring != null ? wiring.wind.toVector() : new double[]{1, 0, 0};
        boolean zuFuss = camera.eye[1] - groundHeight(camera.eye[0], camera.eye[2]) < 25;
        double gEye = groundHeight(camera.eye[0], camera.eye[2]);
        wald.update(gl, camera.eye, vp, clock.date().getDayOfYear(), to[0], to[2], ws, wiring != null ? 0.5 : 0.3, (float) (clock.t() % 3600), zuFuss,
                showJahr ? jahr.kronen(300) : 0, showJahr ? Math.min(1, jahr.schnee(gEye) / 0.08) : 0);   // Phase 14
        boolean wall = schatten.on && terrain.measured();
        com.dan.forest.gl.ForestGl.Light fl = new com.dan.forest.gl.ForestGl.Light();
        com.dan.ground.gl.GroundGl.Light gL = new com.dan.ground.gl.GroundGl.Light();
        fl.sunDir = gL.sunDir = sun;
        if (frameSky) {
            boolean cloudsNow = wetter.cloudsOk && showClouds;
            double[][] L = wetter.ambient(st, pre, cloudShade, wetter.skyDim(cloudsNow));
            fl.sunColor = gL.sunColor = Wetter.scale(st.sunColor, pre);
            fl.moonDir = gL.moonDir = st.moonDir;
            fl.moonColor = gL.moonColor = Wetter.scale(st.moonColor, pre);
            fl.skyColor = gL.skyColor = L[2];
            fl.fogColor = gL.fogColor = L[3];
            fl.fogDistance = gL.fogDistance = Wetter.fogDistance(st);
            fl.skyTexture = gL.skyTexture = wetter.panoTex;
            fl.skyWeight = 1.0;
            gL.skyWeight = 1.1;
        } else {
            double[] sp = clock.sun();
            fl.sunColor = gL.sunColor = sunLight(sp[0]);
            fl.skyColor = gL.skyColor = Wetter.scale(Renderer.skyPlaceholder(sp[0], 1), 0.55 / Math.PI);
            fl.fogColor = gL.fogColor = Renderer.skyPlaceholder(sp[0], 0);
            fl.fogDistance = gL.fogDistance = FOG_DISTANCE;
        }
        fl.wetness = rockWet;
        if (wall) {
            fl.shadowTexture = gL.shadowTexture = schatten.mainTexture();
            fl.shadowRect = gL.shadowRect = schatten.mainRect();
        }
        com.dan.forest.gl.ForestGl.View fv = new com.dan.forest.gl.ForestGl.View();
        fv.viewProj = vp;
        fv.eye = camera.eye.clone();
        fv.reversedZ = reversedZ;
        wald.drawForest(gl, fv, fl, clock.t() % 3600);
        if (zuFuss) {
            com.dan.ground.gl.GroundGl.View gv = new com.dan.ground.gl.GroundGl.View();
            gv.viewProj = vp;
            gv.eye = camera.eye.clone();
            gv.reversedZ = reversedZ;
            wald.drawGround(gl, gv, gL);
        }
        common(gl, bautenShader, vp, inv, sun, level, camera.eye);
        lod.bind(gl, bautenShader);
        bindShadows(gl, bautenShader);
        bautenShader.set(gl, "uClip", 0);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        wald.drawHoefe(gl);
        gl.glBindVertexArray(emptyVao);
        gl.glDisable(GL.GL_DEPTH_TEST);
    }

    /** Leuchtdichte einer Lampe im vorbelichteten Bild (Lampen der Straße, Laternen und Fenster im Ort). */
    static final float LAMPE = 2.0f;

    /** Licht für timber wie für den Wald (vorbelichtet), die Schatten der Wände von oben. */
    private com.dan.timber.gl.TimberGl.Light holzLicht(double[] sun, Sky.State st, double pre, double level) {
        com.dan.timber.gl.TimberGl.Light l = new com.dan.timber.gl.TimberGl.Light();
        l.sunDir = sun;
        if (frameSky) {
            boolean cloudsNow = wetter.cloudsOk && showClouds;
            double[][] L = wetter.ambient(st, pre, cloudShade, wetter.skyDim(cloudsNow));
            l.sunColor = Wetter.scale(st.sunColor, pre);
            l.moonDir = st.moonDir;
            l.moonColor = Wetter.scale(st.moonColor, pre);
            l.skyColor = L[2];
            l.fogColor = L[3];
            l.fogDistance = Wetter.fogDistance(st);
            l.skyTexture = wetter.panoTex;
            l.skyWeight = 1.0;
        } else {
            double[] sp = clock.sun();
            l.sunColor = sunLight(sp[0]);
            l.skyColor = Wetter.scale(Renderer.skyPlaceholder(sp[0], 1), 0.55 / Math.PI);
            l.fogColor = Renderer.skyPlaceholder(sp[0], 0);
            l.fogDistance = FOG_DISTANCE;
        }
        l.wetness = rockWet;
        l.waterLevel = level;
        if (schatten.on && terrain.measured()) {
            l.shadowTexture = schatten.mainTexture();
            l.shadowRect = schatten.mainRect();
        }
        // die acht nächsten Laternen leuchten nachts auf Deck und Bohlen
        double nacht = 1 - Math.max(0, Math.min(1, (sun[1] + 0.02) / 0.08));
        if (nacht > 0.01 && !ort.dorf.lampen.isEmpty()) {
            java.util.List<double[]> ls = new java.util.ArrayList<>(ort.dorf.lampen);
            double[] e = camera.eye;
            ls.sort(java.util.Comparator.comparingDouble(p -> (p[0] - e[0]) * (p[0] - e[0]) + (p[2] - e[2]) * (p[2] - e[2])));
            int n = Math.min(8, ls.size());
            l.pointPos = new float[32];
            l.pointRgb = new float[32];
            for (int i = 0; i < n; i++) {
                double[] p = ls.get(i);
                l.pointPos[4 * i] = (float) p[0];
                l.pointPos[4 * i + 1] = (float) p[1];
                l.pointPos[4 * i + 2] = (float) p[2];
                l.pointPos[4 * i + 3] = 0.2f;
                float k = (float) (LAMPE * 0.6 * nacht);
                l.pointRgb[4 * i] = k;
                l.pointRgb[4 * i + 1] = k * 0.8f;
                l.pointRgb[4 * i + 2] = k * 0.55f;
            }
            l.pointCount = n;
        }
        return l;
    }

    /** Phase 12: die Wasserfläche für buoyancy (Höhe von ocean über dem Fjord, sonst der Spiegel). */
    private com.dan.buoyancy.Sea schiffSee;

    private com.dan.buoyancy.Sea schiffSee() {
        if (schiffSee == null)
            schiffSee = com.dan.geiranger.scene.Flotte.sea((x, z) -> wasser.ok && terrain.fjord(x, z) ? wasser.ocean.heightAt(x, z) : waterLevel(), this::waterLevel);
        return schiffSee;
    }

    /** Phase 12: Kielwasser nachrechnen (Compute) und die Schiffe zeichnen; clip: im Spiegelbild. */
    private void drawSchiffe(GL4 gl, float[] vp, float[] inv, double[] sun, double level, double[] eye, boolean clip) {
        if (!clip && !showBed) flotte.kielwasser.update(gl, lod, flotte.flotte, clock.epochMillis() / 1000.0, eye[0], eye[2], level);
        common(gl, schiffShader, vp, inv, sun, level, eye);
        lod.bind(gl, schiffShader);
        bindShadows(gl, schiffShader);
        schiffShader.set(gl, "uClip", clip ? 1 : 0);
        schiffShader.set(gl, "uLamp", LAMPE);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        flotte.draw(gl, schiffShader, eye);
        gl.glBindVertexArray(emptyVao);
        gl.glDisable(GL.GL_DEPTH_TEST);
    }

    /**
     * Phase 13: die Menschen. Licht wie Wald und Holz (vorbelichtet); die Sonne zählt am Auge mit dem Schatten der Wände
     * und der Wolken (wer nah genug ist, um gesehen zu werden, steht fast immer im selben Licht wie das Auge).
     */
    private void drawMenschen(GL4 gl, float[] vp, float[] inv, double[] sun, double level, Sky.State st, double pre, int h) {
        if (!leute.show) return;
        double epoch = clock.epochMillis() / 1000.0;
        common(gl, schiffShader, vp, inv, sun, level, camera.eye);
        lod.bind(gl, schiffShader);
        bindShadows(gl, schiffShader);
        schiffShader.set(gl, "uClip", 0);
        schiffShader.set(gl, "uLamp", LAMPE);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        leute.drawBus(gl, schiffShader, epoch, camera.eye);
        gl.glUseProgram(0);
        // Licht: Sonne, Mond, Himmel oben und am Horizont (wie forest/timber), Sichtbarkeit der Sonne am Auge
        double[] sonne, mond = {0, 0, 0}, oben, rand, mondDir = {0, -1, 0};
        double fog;
        if (frameSky) {
            boolean cloudsNow = wetter.cloudsOk && showClouds;
            double[][] L = wetter.ambient(st, pre, cloudShade, wetter.skyDim(cloudsNow));
            sonne = Wetter.scale(st.sunColor, pre);
            mond = Wetter.scale(st.moonColor, pre);
            mondDir = st.moonDir;
            oben = L[2];
            rand = L[3];
            fog = Wetter.fogDistance(st);
        } else {
            double[] sp = clock.sun();
            sonne = sunLight(sp[0]);
            oben = Wetter.scale(Renderer.skyPlaceholder(sp[0], 1), 0.55 / Math.PI);
            rand = Renderer.skyPlaceholder(sp[0], 0);
            fog = FOG_DISTANCE;
        }
        double sicht = Math.max(0, Math.min(1, sunOnWater)) * Math.max(0, Math.min(1, cloudShade));
        com.dan.figure.gl.FigureGl.Light fl = new com.dan.figure.gl.FigureGl.Light();
        fl.sunDir = sun;
        fl.sunColor = sonne;
        fl.moonDir = mondDir;
        fl.moonColor = mond;
        fl.skyTop = oben;
        fl.skyHorizon = rand;
        fl.fogDistance = fog;
        fl.sunShadow = sicht;
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(true);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        leute.drawFiguren(gl, vp, camera.eye, fl, epoch);
        // Gäste (CrowdGl): Führungslicht als ferner Strahler in Richtung der Sonne, Kante aus dem Himmel gegenüber, Aufhellung vom Himmel
        com.dan.avatar.gl.AvatarGl.Light al = new com.dan.avatar.gl.AvatarGl.Light();
        double[] e = camera.eye;
        double R = 1000, sv = Math.max(0, Math.min(1, (sun[1] + 0.02) / 0.06)) * sicht;
        al.keyPos = new float[]{(float) (e[0] + sun[0] * R), (float) (e[1] + sun[1] * R), (float) (e[2] + sun[2] * R)};
        al.keyDir = new float[]{(float) -sun[0], (float) -sun[1], (float) -sun[2]};
        al.keyCol = new float[]{(float) (sonne[0] * sv * R * R), (float) (sonne[1] * sv * R * R), (float) (sonne[2] * sv * R * R)};
        al.keyConeInner = 80;
        al.keyConeOuter = 89.5f;
        double hx = -sun[0], hz = -sun[2], hl = Math.max(1e-6, Math.hypot(hx, hz));
        al.rimPos = new float[]{(float) (e[0] + hx / hl * R * 0.9), (float) (e[1] + R * 0.45), (float) (e[2] + hz / hl * R * 0.9)};
        al.rimCol = new float[]{(float) (rand[0] * 0.35 * R * R), (float) (rand[1] * 0.35 * R * R), (float) (rand[2] * 0.35 * R * R)};
        al.fillCol = new float[]{(float) oben[0], (float) oben[1], (float) oben[2]};
        al.gain = 1f;
        leute.drawGaeste(gl, vp, camera.eye, al, h / (2 * Math.tan(Math.toRadians(fovy()) / 2)));
        gl.glBindVertexArray(emptyVao);
        gl.glDisable(GL.GL_DEPTH_TEST);
    }

    /** Phase 11: der Ort. */
    private void drawOrt(GL4 gl, float[] vp, float[] inv, double[] sun, double level, Sky.State st, double pre) {
        common(gl, bautenShader, vp, inv, sun, level, camera.eye);
        lod.bind(gl, bautenShader);
        bindShadows(gl, bautenShader);
        bautenShader.set(gl, "uClip", 0);
        bautenShader.set(gl, "uLamp", LAMPE);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        ort.drawBauten(gl);
        com.dan.timber.gl.TimberGl.View v = new com.dan.timber.gl.TimberGl.View();
        v.viewProj = vp;
        v.eye = camera.eye.clone();
        v.forward = camera.fwd.clone();
        v.fovyDeg = fovy();
        v.aspect = hdrW / (double) Math.max(1, hdrH);
        v.reversedZ = reversedZ;
        v.tonemap = false;
        v.exposure = 1;
        ort.drawHolz(gl, v, holzLicht(sun, st, pre, level));
        gl.glBindVertexArray(emptyVao);
        gl.glDisable(GL.GL_DEPTH_TEST);
    }

    /** Phase 11: die Lichthöfe der Laternen (nur nachts sichtbar). */
    private void drawGlow(GL4 gl, float[] vp, float[] inv, double[] sun, double level) {
        if (sun[1] > 0.06) return;
        common(gl, glowShader, vp, inv, sun, level, camera.eye);
        glowShader.set(gl, "uLamp", LAMPE);
        glowShader.set(gl, "uPx", (float) (hdrH / (2 * Math.tan(Math.toRadians(fovy()) / 2))));
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        ort.drawGlow(gl);
        gl.glBindVertexArray(emptyVao);
        gl.glDisable(GL.GL_DEPTH_TEST);
    }

    /** Phase 9: die Straße (fest, dann gemischt und Lichthöfe) und die Plattform. */
    private void drawStrasse(GL4 gl, float[] vp, float[] inv, double[] sun, double level) {
        if (!strasse.ok) return;
        strasse.upload(gl);
        common(gl, strasseShader, vp, inv, sun, level, camera.eye);
        lod.bind(gl, strasseShader);
        bindShadows(gl, strasseShader);
        strasseShader.set(gl, "uLamp", LAMPE);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        strasse.drawSolid(gl, strasseShader);
        if (strasse.plattform != null) {
            common(gl, bautenShader, vp, inv, sun, level, camera.eye);
            lod.bind(gl, bautenShader);
            bindShadows(gl, bautenShader);
            bautenShader.set(gl, "uClip", 0);
            gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
            strasse.drawPlattform(gl);
            strasseShader.use(gl);
        }
        strasse.drawBlend(gl, strasseShader);
        gl.glBindVertexArray(emptyVao);
        gl.glDisable(GL.GL_DEPTH_TEST);
    }

    /** Phase 8: das Wasser der Geirangelva (nach ocean). */
    private void drawElva(GL4 gl, float[] vp, float[] inv, double[] sun, double level) {
        if (!fluss.ok) return;
        fluss.update(gl, abfluss().cubicMetres(ELVA));
        common(gl, elvaShader, vp, inv, sun, level, camera.eye);
        lod.bind(gl, elvaShader);
        bindShadows(gl, elvaShader);
        elvaShader.set(gl, "uTurbid", (float) elvaTurbid);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        fluss.drawWater(gl);
        gl.glBindVertexArray(emptyVao);
        gl.glDisable(GL.GL_DEPTH_TEST);
    }

    /** Das Licht der Fälle aus dem Himmel (vorbelichtet) oder dem Platzhalter, wie die Überhänge. */
    private com.dan.fall.gl.FallGl.Light fallLight(double[] sun) {
        com.dan.fall.gl.FallGl.Light l = new com.dan.fall.gl.FallGl.Light();
        l.sunDir = sun;
        if (frameSky) {
            Sky.State st = frameSt;
            l.sunColor = Wetter.scale(st.sunColor, framePre);
            l.ambient = Wetter.scale(st.skyIrradiance, framePre / Math.PI * wetter.skyDim(showClouds));
            l.fogColor = wetter.ambient(st, framePre, 1, 1)[3];
            l.fogDistance = Wetter.fogDistance(st);
        } else {
            double[] sp = clock.sun();
            l.sunColor = sunLight(sp[0]);
            l.ambient = Wetter.scale(Renderer.skyPlaceholder(sp[0], 1), 0.55 / Math.PI);
            l.fogColor = Renderer.skyPlaceholder(sp[0], 0);
            l.fogDistance = FOG_DISTANCE;
        }
        l.display = 0;
        return l;
    }

    /** Phase 7: die Fälle in das gebundene Ziel (Blick vp, Auge eye); lodNear klein im Spiegelbild. */
    private void drawFaelle(GL4 gl, float[] vp, double[] eye, double[] sun, double lodNear) {
        if (faelle.eintraege().isEmpty()) return;
        com.dan.fall.gl.FallGl.View v = new com.dan.fall.gl.FallGl.View();
        v.viewProj = vp;
        v.eye = eye.clone();
        v.reversedZ = reversedZ;
        boolean cloudsNow = frameSky && wetter.cloudsOk && showClouds;
        Clouds.Snapshot cs = cloudsNow ? wetter.clouds.snapshot() : null;
        double[] key = frameSky ? Wetter.keyDir(frameSt) : sun;
        faelle.draw(gl, abfluss(), v, fallLight(sun), clock.t(), (x, z) -> cs == null ? 1 : Clouds.shadow(cs, x, z, key), lodNear);
        gl.glBindVertexArray(emptyVao);
    }

    /** Ein ganzes Bild in Rechengröße w × h, dann ins Fenster. Bei stehender Szene ({@link #frozen}) malt das HUD gleich. */
    void renderFrame(GL4 gl, int w, int h) {
        fels.update(gl);   // Phase 5: Wandkarte und Überhänge hochladen, sobald der Hintergrund fertig ist (einmal)
        mark(gl, 0);
        frameSky = wetter.skyOk;
        Sky.State st = frameSky ? wetter.state() : null;
        frameSt = st;
        if (frameSky && adaptLum <= 0) adapt(st, 1);
        double pre = frameSky ? exposure() : 1;
        framePre = pre;
        double[] sp = clock.sun();
        double[] sun = frameSky ? st.sunDir : Sun.direction(sp[0], sp[1]);
        double[] key = frameSky ? Wetter.keyDir(st) : sun;
        float[] proj = Mat4.perspective(fovy(), w / (double) h, NEAR, FAR, reversedZ);
        float[] vp = Mat4.mul(proj, camera.view());
        float[] inv = Mat4.invert(vp);
        double level = waterLevel();
        int mode = waterMode();
        boolean water = mode == 0;
        boolean cloudsNow = wetter.cloudsOk && showClouds;

        // 1 Schatten der Wände: ein Streifen je Bild, sobald sich die Lichtquelle gedreht hat
        schatten.size(gl, quality.shadowN);
        if (terrain.measured()) {
            common(gl, schattenShader, vp, inv, sun, level, camera.eye);
            lod.bind(gl, schattenShader);
            schatten.step(gl, schattenShader, emptyVao, key);
        }
        mark(gl, 1);

        // 2 Himmel: com.dan.sky (Streuung über 62,1° N, Sonne, Mond, Sterne) und die Wolken ins Bild, dann das
        //   Rundumbild des Himmels (Spiegelbild, Gelände, Dunst); ohne com.dan.sky der Verlauf aus Phase 1
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glClearColor(0, 0, 0, 1);
        gl.glClearDepth(reversedZ ? 0 : 1);
        gl.glDepthMask(true);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glBindVertexArray(emptyVao);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_CULL_FACE);
        CloudGl.Light cl = null;
        if (frameSky) {
            wetter.skyGl.update(gl, st);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            SkyGl.View sv = new SkyGl.View();
            sv.viewProj = vp;
            sv.invViewProj = inv;
            sv.reversedZ = reversedZ;
            sv.width = w;
            sv.height = h;
            sv.fovyDeg = fovy();
            sv.preExposure = pre;
            sv.time = clock.t();
            wetter.skyGl.draw(gl, st, sv);
            if (wetter.skyCheck.isEmpty() && frame > 30) wetter.skyCheck = wetter.skyGl.selfCheck(gl, st);
            gl.glBindVertexArray(emptyVao);
            Clouds.Snapshot cs = wetter.clouds.snapshot();
            if (cloudsNow) {
                cl = wetter.cloudLight(st, pre);
                wetter.cloudGl.update(gl, cs, cl, camera.eye.clone());
                gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
                gl.glViewport(0, 0, w, h);
                CloudGl.View cv = new CloudGl.View();
                cv.viewProj = vp;
                cv.invViewProj = inv;
                cv.eye = camera.eye.clone();
                cv.reversedZ = reversedZ;
                cv.width = w;
                cv.height = h;
                wetter.cloudGl.draw(gl, cs, cl, cv);
                if (wetter.cloudCheck.isEmpty() && frame > 40) wetter.cloudCheck = wetter.cloudGl.selfCheck(gl, cs, cl);
                gl.glBindVertexArray(emptyVao);
            }
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, wetter.panoFbo);
            gl.glViewport(0, 0, Wetter.PANO_W, Wetter.PANO_H);
            wetter.skyGl.drawPanorama(gl, st, pre);
            if (cloudsNow) wetter.cloudGl.drawPanorama(gl, cs, cl, camera.eye.clone());
            gl.glBindVertexArray(emptyVao);
        } else {
            common(gl, skyShader, vp, inv, sun, level, camera.eye);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        if (wetter.rainOk) {
            wetter.rainGl.update(gl, wetter.rain.snapshot());
            if (wetter.rainCheck.isEmpty() && wetter.rain.rate > 0 && frame > 60) wetter.rainCheck = wetter.rainGl.selfCheck(gl, wetter.rain.snapshot());
        }
        mark(gl, 2);

        // 3 Spiegelbild (Phase 3): die Szene vom Auge unter dem Spiegel, dann das Rundumbild für ocean
        if (water) mirror(gl, w, h, sun, level);
        mark(gl, 3);

        // 4 Gelände: CDLOD über Hauptraster und Fernring, in den Kernfenstern bis 2 m; im Fjord der gemalte Grund; seit
        //   Phase 5 Fels aus com.dan.cliff und die Überhänge an den Abrisskanten
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        drawTerrain(gl, vp, inv, sun, level, camera.eye, mode, false);
        drawOverhangs(gl, vp, camera.eye, h, sun);
        drawBauten(gl, vp, inv, sun, level, camera.eye, false);
        if (fels.ok && fels.mapReady() && fels.check.isEmpty() && frame > 50) {
            fels.check = fels.gl.selfCheck(gl, fels.cliff);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
        }
        mark(gl, 4);

        // 5 Straße (Phase 9): Fahrbahn, Ausstattung und Verkehr aus com.dan.road, die Plattform der Ørnesvingen
        drawStrasse(gl, vp, inv, sun, level);
        mark(gl, 5);

        // 6 Wald (Phase 10): Bäume, Bodendecke um das Auge, die Höfe
        drawWald(gl, vp, inv, sun, level, st, pre);
        mark(gl, 6);

        // 7 Ort (Phase 11): Häuser, Hotels, Kirche, Laternen (Programm der Bauten), Naust, Anleger und Höfe (timber)
        drawOrt(gl, vp, inv, sun, level, st, pre);
        mark(gl, 7);

        // 8 Schiffe (Phase 12): Kielwasser nachrechnen (Compute), Fähren, Kreuzfahrtschiff, Schnellboot, Kajaks
        drawSchiffe(gl, vp, inv, sun, level, camera.eye, false);
        mark(gl, 8);

        // 9 Menschen (Phase 13): der Ausflugsbus, die Figuren (Busgruppe, Wanderer, Treppe), die Gäste aus Avatar.jar
        drawMenschen(gl, vp, inv, sun, level, st, pre, h);
        mark(gl, 9);

        // 10 Wasser: ocean rechnen (Compute) und über das Gelände zeichnen; der Grund ist die Höhentextur des Geländes
        if (water) {
            wasser.gl.update(gl, wasser.ocean);
            if (wasser.check.isEmpty() && frame > 30) wasser.check = wasser.gl.selfCheck(gl, wasser.ocean);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            OceanGl.View ov = new OceanGl.View();
            ov.viewProj = vp;
            ov.eye = camera.eye.clone();
            ov.reversedZ = reversedZ;
            OceanGl.Light ol = wasser.light(sun, frameSky ? Wetter.scale(st.sunColor, pre) : sunLight(sp[0]), lod.heightTexture(),
                    frameSky ? Wetter.fogDistance(st) : FOG_DISTANCE);
            if (frameSky) {
                ol.moonDir = st.moonDir;
                ol.moonColor = Wetter.scale(st.moonColor, pre);
            }
            if (cloudsNow) {
                ol.shadowTexture = wetter.cloudGl.shadowTexture();
                ol.shadowRect = wetter.cloudGl.shadowRect();
            }
            // Phase 12: das Kielwasser als lange Welle (Höhe, Neigung, Schaum an steilen Kämmen)
            if (flotte.kielwasser.texture() != 0 && flotte.show) {
                ol.longTexture0 = flotte.kielwasser.texture();
                ol.longRect0 = flotte.kielwasser.rect();
            }
            if (schatten.on && terrain.measured()) {
                ol.bodyShadowTexture = schatten.mainTexture();
                ol.bodyShadowRect = schatten.mainRect();
            }
            if (wetter.rainOk && showRain && wetter.rain.rate > 0) {
                ol.detailTexture = wetter.rainGl.rippleTexture();
                ol.detailTile = wetter.rainGl.rippleTile();
                ol.detailStrength = wetter.rainGl.rippleStrength() * Math.min(1, rainStrength);
                ol.detailRange = 60;
            }
            gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
            wasser.gl.draw(gl, wasser.ocean, ov, ol);
            gl.glBindVertexArray(emptyVao);
            gl.glDisable(GL.GL_DEPTH_TEST);
        }
        // Phase 8: die Geirangelva (auch bei „Grund zeigen“: sie liegt über NN)
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        drawElva(gl, vp, inv, sun, level);
        mark(gl, 10);

        // 11 Fälle (Phase 7): nach dem Wasser, Tiefentest gegen Gelände und Wasser, ohne Tiefe zu schreiben
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        drawGlow(gl, vp, inv, sun, level);   // Phase 11: Lichthöfe der Laternen (nach dem Wasser, addiert)
        drawFaelle(gl, vp, camera.eye, sun, 350);
        if (frame > 70) {
            faelle.selfCheck(gl);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
        }
        gl.glDisable(GL.GL_DEPTH_TEST);
        mark(gl, 11);

        // 12 Wetter: Regen um das Auge und als Vorhang, Fjordnebel auf dem Wasser, das Wolkenband an den Wänden
        if (frameSky) {
            boolean rainNow = wetter.rainOk && showRain && wetter.rain.rate > 0.01;
            boolean nebelNow = wetter.nebelOk && showFog && wetter.nebel.amount > 0.005;
            boolean bandNow = wetter.bandOk && showFog && wetter.band.amount > 0.005;
            if (rainNow || nebelNow || bandNow) {
                copyDepth(gl, w, h);
                double[][] L = wetter.ambient(st, pre, cloudShade, wetter.skyDim(cloudsNow));
                if (rainNow) drawRain(gl, st, vp, inv, w, h, pre, L, cloudsNow);
                if (bandNow) drawFog(gl, wetter.band, wetter.bandGl, st, inv, w, h, L, 15_000);
                if (nebelNow) drawFog(gl, wetter.nebel, wetter.nebelGl, st, inv, w, h, L, 9_000);
            }
            // Phase 14: fallender Schnee um das Auge
            if (schneeOk && showRain && schneeRate > 0.01) {
                double[][] L = wetter.ambient(st, pre, cloudShade, wetter.skyDim(cloudsNow));
                com.dan.season.gl.SeasonGl.View sv = new com.dan.season.gl.SeasonGl.View();
                sv.viewProj = vp;
                sv.eye = camera.eye.clone();
                sv.reversedZ = reversedZ;
                sv.width = w;
                sv.height = h;
                sv.fovyDeg = fovy();
                com.dan.season.gl.SeasonGl.Light sl = new com.dan.season.gl.SeasonGl.Light();
                sl.sunDir = sun;
                sl.sunColor = L[0];
                sl.moonDir = st.moonDir;
                sl.moonColor = L[1];
                sl.sky = L[2];
                sl.fogColor = L[3];
                sl.fogDistance = Wetter.fogDistance(st);
                gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
                gl.glViewport(0, 0, w, h);
                gl.glEnable(GL.GL_DEPTH_TEST);
                gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
                gl.glDepthMask(false);
                double[] to = wiring != null ? wiring.wind.toVector() : new double[]{1, 0, 0};
                schneefall.draw(gl, sv, sl, schneeRate * quality.schneeAnteil(), to, wiring != null ? wiring.wind.speed() : 1, clock.t());
                gl.glDepthMask(true);
                gl.glDisable(GL.GL_DEPTH_TEST);
                gl.glDisable(GL.GL_BLEND);
                gl.glBindVertexArray(emptyVao);
            }
        }
        mark(gl, 12);

        // Phase 8: Belichtung im Bild messen (alle 0,2 s, gelesen ohne Warten beim nächsten Mal)
        if (frameSky && bildMessung) {
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            belichtung.step(gl, lumShader, hdrTex, emptyVao, pre, frozen);
        }

        // 13 Nachbearbeitung ins Fenster (mit com.dan.sky ist das Bild schon vorbelichtet)
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, defaultFbo);
        gl.glViewport(0, 0, surfW, surfH);
        post.use(gl);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        post.set(gl, "uHdr", 0);
        post.set(gl, "uExposure", frameSky ? 1f : (float) exposure());
        post.set(gl, "uScotopic", (float) (frameSky ? Wetter.scotopic(st) : 0));
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        mark(gl, 13);

        // 14 HUD (Phase 16: nicht im Kino und nicht auf den Fotos des Regiebuchs)
        if (showHud && !kino.laeuft() && captureCaption == null) {
            hud.refresh(gl, 250, this::paintHud, frozen);
            hud.draw(gl, overlay, emptyVao, 12, Math.max(0, surfH - hud.height - 12), surfW, surfH);
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        mark(gl, 14);
    }

    /** Die Tiefe des Bilds als Kopie für Regen und Nebel (die Szene bleibt gebunden). */
    private void copyDepth(GL4 gl, int w, int h) {
        if (depthCopyTex == 0 || depthCopyW != w || depthCopyH != h) {
            Gpu.deleteFbos(gl, depthCopyFbo);
            Gpu.deleteTextures(gl, depthCopyTex);
            depthCopyTex = Gpu.texture(gl, w, h, GL4.GL_DEPTH_COMPONENT32F, false);
            gl.glBindTexture(GL.GL_TEXTURE_2D, depthCopyTex);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            depthCopyFbo = Gpu.depthFbo(gl, depthCopyTex);
            depthCopyW = w;
            depthCopyH = h;
        }
        gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, hdrFbo);
        gl.glBindFramebuffer(GL4.GL_DRAW_FRAMEBUFFER, depthCopyFbo);
        gl.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL.GL_DEPTH_BUFFER_BIT, GL.GL_NEAREST);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
    }

    private void drawRain(GL4 gl, Sky.State st, float[] vp, float[] inv, int w, int h, double pre, double[][] L, boolean cloudsNow) {
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        RainGl.View v = new RainGl.View();
        v.viewProj = vp;
        v.invViewProj = inv;
        v.eye = camera.eye.clone();
        v.forward = camera.fwd.clone();
        v.reversedZ = reversedZ;
        v.near = NEAR;
        v.far = FAR;
        v.fovyDeg = fovy();
        v.width = w;
        v.height = h;
        v.depthTexture = depthCopyTex;
        RainGl.Light l = new RainGl.Light();
        l.sunDir = st.sunDir;
        l.sunColor = L[0];
        l.moonDir = st.moonDir;
        l.moonColor = L[1];
        double under = cloudsNow ? Math.min(1, wetter.clouds.type.overcastBrightness * 1.6) : 1;
        l.skyTop = Wetter.scale(st.skyIrradiance, pre / Math.PI * under);
        l.skyHorizon = L[3];
        l.fogDistance = Wetter.fogDistance(st);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        wetter.rainGl.draw(gl, v, l, rainStrength);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glBindVertexArray(emptyVao);
    }

    private void drawFog(GL4 gl, Fog f, FogGl fg, Sky.State st, float[] inv, int w, int h, double[][] L, double maxDist) {
        Fog.Snapshot s = f.snapshot();
        fg.update(gl, s, camera.eye.clone());
        if (wetter.fogCheck.isEmpty() && frame > 100 && f == wetter.nebel) wetter.fogCheck = fg.selfCheck(gl, f, camera.eye.clone());
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        FogGl.View v = new FogGl.View();
        v.invViewProj = inv;
        v.eye = camera.eye.clone();
        v.reversedZ = reversedZ;
        v.width = w;
        v.height = h;
        v.depthTexture = depthCopyTex;
        v.maxDistance = maxDist;
        v.jitter = frozen ? 0.5 : -1;
        v.frame = frame;
        FogGl.Light l = new FogGl.Light();
        l.sunDir = st.sunDir;
        l.sunColor = Wetter.scale(L[0], sunOnWater * 0.6 + 0.4);   // im Fjord liegt der Nebel lange im Schatten der Wände
        l.moonDir = st.moonDir;
        l.moonColor = L[1];
        l.ambientTop = L[2];
        l.ambientHorizon = L[3];
        fg.draw(gl, s, v, l);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glBindVertexArray(emptyVao);
    }

    /** Phase 5: die Überhänge (com.dan.cliff) mit dem Licht des Himmels, vorbelichtet. */
    private void drawOverhangs(GL4 gl, float[] vp, double[] eye, int h, double[] sun) {
        if (!fels.ok || !fels.mapReady() || !showCliff) return;
        com.dan.cliff.gl.CliffGl.View v = new com.dan.cliff.gl.CliffGl.View();
        v.viewProj = vp;
        v.eye = eye.clone();
        v.reversedZ = reversedZ;
        v.pixelAngle = Math.toRadians(fovy()) / Math.max(1, h);
        com.dan.cliff.gl.CliffGl.Light l = new com.dan.cliff.gl.CliffGl.Light();
        l.sunDir = sun;
        if (frameSky) {
            Sky.State st = frameSt;
            l.sunColor = Wetter.scale(st.sunColor, framePre);
            l.ambient = Wetter.scale(st.skyIrradiance, framePre / Math.PI * wetter.skyDim(showClouds));
            double[][] L = wetter.ambient(st, framePre, cloudShade, 1);
            l.fogColor = L[3];
            l.fogDistance = Wetter.fogDistance(st);
        } else {
            double[] sp = clock.sun();
            l.sunColor = sunLight(sp[0]);
            l.ambient = Wetter.scale(Renderer.skyPlaceholder(sp[0], 1), 0.55 / Math.PI);
            l.fogColor = Renderer.skyPlaceholder(sp[0], 0);
            l.fogDistance = FOG_DISTANCE;
        }
        l.shade = cloudShade;
        l.rainWet = rockWet;
        fels.gl.drawOverhangs(gl, fels.cliff, v, l);
        gl.glBindVertexArray(emptyVao);
    }

    /** Zenit (up = 1) oder Horizont (up = 0) des Platzhalter-Himmels, wie skyColorPlaceholder (ohne Sonnensaum). */
    static double[] skyPlaceholder(double e, double up) {
        double b = Sun.skyBrightness(e);
        double day = smooth(-2, 12, e), night = 1 - smooth(-16, -8, e), tw = Math.max(0, Math.min(1, 1 - day - night));
        double hw = Math.pow(1 - up, 4);
        double[] zen = {0.16 * day + 0.10 * tw + 0.05 * night, 0.34 * day + 0.17 * tw + 0.08 * night, 0.72 * day + 0.42 * tw + 0.20 * night};
        double[] hor = {0.62 * day + 0.6 * tw + 0.07 * night, 0.74 * day + 0.4 * tw + 0.09 * night, 0.88 * day + 0.36 * tw + 0.18 * night};
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) c[i] = (zen[i] + (hor[i] - zen[i]) * hw) * b / 0.32;
        return c;
    }

    /** Dunst über dem Wasser (m), wie aerial() in common.glsl ohne com.dan.sky. */
    static final double FOG_DISTANCE = 60_000;

    /** Das Gelände mit allen Uniforms; clip: für das Spiegelbild, alles unter dem Spiegel weg. */
    private void drawTerrain(GL4 gl, float[] vp, float[] inv, double[] sun, double level, double[] eye, int mode, boolean clip) {
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        common(gl, terrainShader, vp, inv, sun, level, eye);
        lod.bind(gl, terrainShader);
        rect(gl, "uKernWest", Site.KERN_WEST);
        rect(gl, "uKernOst", Site.KERN_OST);
        float[] pts = points();
        terrainShader.vec4Array(gl, "uPoints", pts);
        terrainShader.set(gl, "uPointCount", pts.length / 4);
        terrainShader.set(gl, "uMarks", showMarks && !clip ? 1 : 0);
        terrainShader.set(gl, "uWaterMode", mode);
        terrainShader.set(gl, "uClip", clip ? 1 : 0);
        // Phase 10: Hofwiesen (heller, ohne Wald) und die Tundra aus dem Scan „Gigantic Tundra Terrain“ (F10 A, Einheit 4)
        double[][] wi = wald.hoefe.wiesen();
        float[] wv = new float[8];
        for (int i = 0; i < Math.min(2, wi.length); i++) { wv[4 * i] = (float) wi[i][0]; wv[4 * i + 1] = (float) wi[i][1]; wv[4 * i + 2] = (float) wi[i][2]; }
        terrainShader.vec4Array(gl, "uWiesen", wv);
        terrainShader.set(gl, "uWiesenN", Math.min(2, wi.length));
        gl.glActiveTexture(GL.GL_TEXTURE4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tundraTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        terrainShader.set(gl, "uTundra", 4);
        terrainShader.set(gl, "uTundraOn", tundraTex != 0 ? 1 : 0);
        // Nacharbeit 10: Waldkarte für das Kronendach (Einheit 5, über der Umgebung)
        int wk = wald.karteTexture(gl);
        gl.glActiveTexture(GL.GL_TEXTURE5);
        gl.glBindTexture(GL.GL_TEXTURE_2D, wk);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        double[] ur = Site.UMGEBUNG.scene();
        terrainShader.set(gl, "uWald", 5);
        terrainShader.set(gl, "uWaldRect", (float) ur[0], (float) ur[1], wk != 0 ? (float) (ur[2] - ur[0]) : 0f, (float) (ur[3] - ur[1]));
        // Phase 4: Schatten der Wände (Einheiten 11, 12) und Wolkenschatten (13)
        boolean wall = schatten.on && terrain.measured();
        gl.glActiveTexture(GL.GL_TEXTURE0 + 11);
        gl.glBindTexture(GL.GL_TEXTURE_2D, wall ? schatten.mainTexture() : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0 + 12);
        gl.glBindTexture(GL.GL_TEXTURE_2D, wall ? schatten.fernTexture() : 0);
        boolean cloudsNow = frameSky && wetter.cloudsOk && showClouds;
        gl.glActiveTexture(GL.GL_TEXTURE0 + 13);
        gl.glBindTexture(GL.GL_TEXTURE_2D, cloudsNow ? wetter.cloudGl.shadowTexture() : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        terrainShader.set(gl, "uWallMain", 11);
        terrainShader.set(gl, "uWallFern", 12);
        terrainShader.set(gl, "uCloudShadow", 13);
        terrainShader.set(gl, "uWallOn", wall ? 1 : 0);
        double[] mr = schatten.mainRect(), fr = schatten.fernRect();
        terrainShader.set(gl, "uWallRectMain", (float) mr[0], (float) mr[1], (float) mr[2]);
        terrainShader.set(gl, "uWallRectFern", (float) fr[0], (float) fr[1], (float) fr[2]);
        double[] cr = cloudsNow ? wetter.cloudGl.shadowRect() : new double[]{0, 0, 1};
        terrainShader.set(gl, "uCloudRect", (float) cr[0], (float) cr[1], (float) Math.max(1, cr[2]), cloudsNow ? 1f : 0f);
        // Phase 5: Fels aus com.dan.cliff (Uniforms von cliff.glsl, Wandkarte auf Einheit 14)
        boolean cliffNow = fels.ok && fels.mapReady() && showCliff;
        if (cliffNow) {
            fels.gl.bind(gl, terrainShader.id(), fels.cliff, com.dan.cliff.gl.CliffGl.MAP_UNIT);
        }
        int[] vpo = new int[4];
        gl.glGetIntegerv(GL.GL_VIEWPORT, vpo, 0);
        terrainShader.set(gl, "uCliffOn", cliffNow ? 1 : 0);
        terrainShader.set(gl, "uPixelAngle", (float) (Math.toRadians(clip ? fovy() * Wasser.MIRROR_MARGIN : fovy()) / Math.max(1, vpo[3])));
        terrainShader.set(gl, "uCliffRange", (float) fels.cliff.quality.reliefRange);
        terrainShader.set(gl, "uRainWet", (float) rockWet);
        // Phase 14: Schnee je Höhenstufe, Laub, Herbst, Kronenschnee und Gras aus dem Jahr
        gl.glUniform1fv(terrainShader.loc(gl, "uSnowBand[0]"), 10, jahr.baender(), 0);
        terrainShader.set(gl, "uJahr", (float) jahr.laub(), (float) jahr.herbst(), (float) jahr.kronen(300), (float) jahr.gras(100));
        terrainShader.set(gl, "uJahrOn", showJahr ? 1 : 0);
        lod.draw(gl, terrainShader, eye, vp);
        gl.glBindVertexArray(emptyVao);
        gl.glDisable(GL.GL_DEPTH_TEST);
    }

    /**
     * Phase 3: das Spiegelbild. Das Auge gespiegelt am Wasser (y' = 2·Spiegel − y), Blick gespiegelt, Blickwinkel um
     * {@link Wasser#MIRROR_MARGIN} weiter; Himmel und Gelände (ohne alles unter dem Spiegel, eine Stufe gröber) in
     * {@link Quality#mirrorScale} der Rechengröße. Dann das Rundumbild, das ocean als Himmel liest: wo die gespiegelte
     * Ansicht hinsieht, ihr Bild, sonst der Himmel. Ohne Spiegelbild (aus oder Stufe ohne) nur der Himmel.
     */
    private void mirror(GL4 gl, int w, int h, double[] sun, double level) {
        double ms = showMirror ? quality.mirrorScale : 0;
        float[] vpm = null;
        if (ms > 0) {
            int mw = Math.max(64, (int) Math.round(w * ms)), mh = Math.max(64, (int) Math.round(h * ms));
            wasser.mirrorTarget(gl, mw, mh);
            double[] em = Wasser.mirrorEye(camera.eye, level);
            vpm = Wasser.mirrorViewProj(camera.eye, camera.fwd, level, fovy(), w / (double) h, NEAR, FAR, reversedZ);
            float[] invm = Mat4.invert(vpm);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, wasser.mirrorFbo);
            gl.glViewport(0, 0, mw, mh);
            gl.glClearColor(0, 0, 0, 1);
            gl.glClearDepth(reversedZ ? 0 : 1);
            gl.glDepthMask(true);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
            gl.glBindVertexArray(emptyVao);
            gl.glDisable(GL.GL_DEPTH_TEST);
            gl.glDisable(GL.GL_CULL_FACE);
            common(gl, skyShader, vpm, invm, sun, level, em);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            // eine Stufe gröber und etwas kürzer: das Spiegelbild ist kleiner und von Wellen verzogen
            double range = lod.lodRange;
            int fine = lod.finest, kern = lod.finestKern;
            lod.lodRange = range * 0.8;
            lod.finest = Math.min(3, fine + 1);
            lod.finestKern = Math.min(3, kern + 1);
            try {
                drawTerrain(gl, vpm, invm, sun, level, em, 0, true);
                drawOverhangs(gl, vpm, em, mh, sun);
                drawBauten(gl, vpm, invm, sun, level, em, true);
                drawSchiffe(gl, vpm, invm, sun, level, em, true);   // Phase 12
                // Phase 7: die Fälle auch im Spiegel (weniger Bündel, das Bild ist kleiner und verzogen)
                gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, wasser.mirrorFbo);
                gl.glViewport(0, 0, mw, mh);
                drawFaelle(gl, vpm, em, sun, 120);
            } finally {
                lod.lodRange = range;
                lod.finest = fine;
                lod.finestKern = kern;
            }
        }
        wasser.panoTarget(gl, quality.panoWidth);
        int pw = wasser.panoW, ph = pw / 2, r0 = wasser.panoRow0();
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, wasser.panoFbo);
        gl.glViewport(0, r0, pw, ph - r0);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glBindVertexArray(emptyVao);
        common(gl, spiegel, vpm != null ? vpm : Mat4.identity(), Mat4.identity(), sun, level, camera.eye);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, vpm != null ? wasser.mirrorTex : 0);
        spiegel.set(gl, "uMirror", 0);
        spiegel.mat(gl, "uMirrorVP", vpm != null ? vpm : Mat4.identity());
        spiegel.set(gl, "uMirrorOn", vpm != null ? 1 : 0);
        spiegel.set(gl, "uPanoSize", (float) pw, (float) ph);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    /** Ein Fenster als vec4 (xMin, zMin, xMax, zMax) der Szene an das Gelände. */
    private void rect(GL4 gl, String u, Site.Fenster f) {
        double[] s = f.scene();
        terrainShader.set(gl, u, (float) s[0], (float) s[1], (float) s[2], (float) s[3]);
    }

    /** Die Orte als vec4 (x, z, Halbmesser, Art): 0 Aussicht, 1 Fall, 2 Hof. */
    private static float[] points() {
        Site.Ort[] o = Site.ORTE;
        float[] p = new float[o.length * 4];
        for (int i = 0; i < o.length; i++) {
            Site.Ort x = o[i];
            int art = x == Site.SCHWESTERN || x == Site.FRIAREN || x == Site.STORFOSSEN ? 1 : x == Site.SKAGEFLA || x == Site.KNIVSFLA ? 2 : 0;
            p[i * 4] = (float) x.x();
            p[i * 4 + 1] = (float) x.z();
            p[i * 4 + 2] = x == Site.ANLEGER ? 25f : 40f;
            p[i * 4 + 3] = art;
        }
        return p;
    }

    private void common(GL4 gl, Shader s, float[] vp, float[] inv, double[] sun, double level, double[] eye) {
        s.use(gl);
        // Phase 4: mit com.dan.sky Himmel aus dem Rundumbild (Einheit 7), Sonne und Mond vorbelichtet, Dunst aus der Sicht
        s.set(gl, "uSkyMode", frameSky ? 1 : 0);
        if (frameSky) {
            Sky.State st = frameSt;
            double pre = framePre;
            gl.glActiveTexture(GL.GL_TEXTURE7);
            gl.glBindTexture(GL.GL_TEXTURE_2D, wetter.panoTex);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            s.set(gl, "uPano", 7);
            s.set(gl, "uSunColor", Wetter.scale(st.sunColor, pre));
            s.set(gl, "uMoonColor", Wetter.scale(st.moonColor, pre));
            s.set(gl, "uMoonDir", st.moonDir);
            s.set(gl, "uFogDist", (float) Wetter.fogDistance(st));
            s.set(gl, "uPre", (float) pre);
            s.set(gl, "uSkyAmb", Wetter.scale(st.skyIrradiance, pre / Math.PI * wetter.skyDim(showClouds)));
        }
        s.mat(gl, "uVP", vp);
        s.mat(gl, "uInvVP", inv);
        s.set(gl, "uCam", eye);
        s.set(gl, "uSun", sun);
        s.set(gl, "uTime", (float) (clock.t() % 10_000));
        s.set(gl, "uLevel", (float) level);
        s.set(gl, "uReversed", reversedZ ? 1 : 0);
    }

    private void resizeTargets(GL4 gl, int w, int h) {
        Gpu.deleteFbos(gl, hdrFbo);
        Gpu.deleteTextures(gl, hdrTex, depthTex);
        hdrTex = Gpu.texture(gl, w, h, GL.GL_RGBA16F, false);
        depthTex = Gpu.texture(gl, w, h, GL4.GL_DEPTH_COMPONENT32F, false);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depthTex);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        hdrFbo = Gpu.fbo(gl, hdrTex, depthTex);
        hdrW = w;
        hdrH = h;
    }

    /** Sonnenlicht des Platzhalter-Himmels (wie sunLight() in common.glsl), linear; aus Rungholt, für ocean. */
    public static double[] sunLight(double elevDeg) {
        double e = Math.sin(Math.toRadians(elevDeg));
        double vis = smooth(-0.015, 0.01, e), t = smooth(0.02, 0.35, e), f = 2.6 * vis * smooth(-0.02, 0.12, e);
        return new double[]{f, (0.55 + 0.40 * t) * f, (0.28 + 0.60 * t) * f};
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------------ Zeitmessung (Tempo)

    private void mark(GL4 gl, int i) { gl.glQueryCounter(stamp[frame & 1][i], GL4.GL_TIMESTAMP); }

    /** Liest die Zeitstempel des vorigen Bilds, sobald sie da sind (kein Warten im Bildlauf). */
    private void readMarks(GL4 gl) {
        if (frame < 2) return;
        int o = (frame - 1) & 1;
        int[] av = new int[1];
        gl.glGetQueryObjectiv(stamp[o][MARKS - 1], GL4.GL_QUERY_RESULT_AVAILABLE, av, 0);
        if (av[0] == 0) return;
        long[] t = new long[MARKS];
        for (int i = 0; i < MARKS; i++) gl.glGetQueryObjecti64v(stamp[o][i], GL4.GL_QUERY_RESULT, t, i);
        for (int i = 0; i < MARKS - 1; i++) passMs[i] = passMs[i] * 0.9 + Math.max(0, t[i + 1] - t[i]) / 1e6 * 0.1;
        gpuMs = gpuMs * 0.9 + Math.max(0, t[MARKS - 1] - t[0]) / 1e6 * 0.1;
    }

    /** „Himmel 0,10 · Bühne 0,60 · …“ in ms (gleitendes Mittel). */
    public String passTimes() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < passMs.length; i++)
            sb.append(i > 0 ? " · " : "").append(PASSES[i]).append(' ').append(String.format(Locale.GERMAN, "%.2f", passMs[i]));
        return sb.toString();
    }

    private void stats(long now, int w, int h) {
        statsFrames++;
        if (now - statsNanos < 500_000_000L) return;
        fps = statsFrames / ((now - statsNanos) / 1e9);
        statsFrames = 0;
        statsNanos = now;
        statusListener.accept(statusLine(w, h));
    }

    /** Auflösung nach der Zeit der Grafikkarte regeln (siehe {@link #autoScale}). */
    private void regulateScale(long now) {
        if (!autoScale || frame < 60 || now - scaleNanos < 1_500_000_000L) return;
        scaleNanos = now;
        double budget = 1000 / Math.max(10, targetFps), top = quality.renderScale, s = renderScale;
        double want = s * Math.sqrt(budget * 0.9 / Math.max(0.1, gpuMs));
        double next = s;
        if (gpuMs > budget * 1.05) next = Math.max(MIN_SCALE, Math.max(want, s - 0.15));
        else if (gpuMs < budget * 0.7 && s < top) next = Math.min(top, Math.min(want, s + 0.05));
        next = Math.round(next * 20) / 20.0;
        if (Math.abs(next - s) >= 0.045) { renderScale = next; scaleSteps++; }
    }

    /** Wie oft die Regelung die Auflösung geändert hat. */
    public int scaleSteps() { return scaleSteps; }

    /** Für Prüfungen: die Regelung mit vorgegebener Grafikkartenzeit und Bildnummer. */
    public void regulateForTest(double gpuMsNow, long nowNanos) {
        gpuMs = gpuMsNow;
        frame = Math.max(frame, 60);
        regulateScale(nowNanos);
    }

    public String statusLine(int w, int h) {
        String s = String.format(Locale.GERMAN, "%s · %.0f Bilder/s · Grafikkarte %.2f ms · Prozessor %.2f ms · %d×%d (%s%s) · %s",
                clock.label(), fps, gpuMs, cpuMs, w, h, quality.label, autoScale ? String.format(Locale.GERMAN, ", %.0f %%", renderScale * 100) : "", glInfo);
        long nowMs = System.currentTimeMillis();
        if (nowMs < meldungUntil) s = meldung + " · " + s;
        if (nowMs < photoMsgUntil) s = photoMsg + " · " + s;
        return s;
    }

    // ------------------------------------------------------------------ HUD

    BufferedImage paintHud() {
        String[] lines = hudFull ? hudLines() : hudShort();
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        java.awt.FontMetrics fm = pg.getFontMetrics(font);
        int tw = 0;
        for (String l : lines) tw = Math.max(tw, fm.stringWidth(l.startsWith("!") ? l.substring(1) : l));
        pg.dispose();
        int w = Math.max(200, tw + 22), lh = 17, h = 16 + lines.length * lh;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(13, 19, 22, 185));
        g.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
        g.setColor(new Color(93, 183, 174, 225));   // Gletschermilch, wie das Werkbuch
        g.setStroke(new BasicStroke(2f));
        g.drawLine(8, 1, 60, 1);
        g.setFont(font);
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            boolean warn = l.startsWith("!");
            if (warn) l = l.substring(1);
            g.setColor(i == 0 ? new Color(234, 242, 244) : warn ? new Color(224, 163, 63) : new Color(185, 198, 202));
            g.drawString(l, 10, 18 + i * lh);
        }
        g.dispose();
        return im;
    }

    /** Das kurze HUD: Tempo, Uhr und Sonne, Kamera. */
    String[] hudShort() {
        java.util.List<String> l = new java.util.ArrayList<>();
        String q = autoScale ? String.format(Locale.GERMAN, "%s %.0f %%", quality.label, renderScale * 100) : quality.label;
        l.add(String.format(Locale.GERMAN, "%.0f Bilder/s · Grafikkarte %.1f ms · Prozessor %.1f ms · %s %d×%d", fps, gpuMs, cpuMs, q, hdrW, hdrH));
        double[] sp = clock.sun();
        l.add(String.format(Locale.GERMAN, "%s · Sonne %.0f° aus %03.0f°%s · Wind Bft %d%s", clock.label(), sp[0], sp[1],
                sp[0] > 0 ? (sunOnWater > 0.5 ? ", hier in der Sonne" : ", hier im Schatten der Wände") : "",
                wiring == null ? 0 : wiring.wind.beaufort(), waterShort()));
        if (wiring != null && wiring.regie) l.add(wiring.weatherLine());
        Object[] nf = faelle.naechster(abfluss(), camera.eye);
        if (nf != null && (double) nf[1] < 4000) {
            com.dan.geiranger.scene.Faelle.Eintrag e = (com.dan.geiranger.scene.Faelle.Eintrag) nf[0];
            l.add(String.format(Locale.GERMAN, "%s %.0f m · %.2f m³/s (%.0f %% vom Juni%s) · %.0f dB hier", e.def.name(), (double) nf[1], abfluss().cubicMetres(e.def.name()),
                    abfluss().factor() * 100, abfluss().hand >= 0 ? ", von Hand" : "", (double) nf[2]));
        }
        l.add(cameraLine() + " · H: volles HUD, F1: Tasten");
        if (System.currentTimeMillis() < meldungUntil) l.add(meldung);
        if (error != null) l.add("!" + error.split("\n")[0]);
        return l.toArray(new String[0]);
    }

    /** „ · Fjord 136 m tief“ oder „ · Grund zeigen“ für das kurze HUD. */
    private String waterShort() {
        double d = depthAt(camera.eye[0], camera.eye[2]);
        String s = showBed ? " · Grund zeigen" : !wasser.ok ? " · Platzhalter-Spiegel (ocean fehlt)" : "";
        return d > 0 ? s + String.format(Locale.GERMAN, " · Fjord hier %.0f m tief", d) : s;
    }

    /** „Kreisen · Auge 1 240 m über NN, 620 m über Grund · Blick 272° · Kern Ost · 0,4 km bis Anleger“ */
    public String cameraLine() {
        return String.format(Locale.GERMAN, "%s · Auge %.0f m über NN, %.0f m über %s · Blick %03.0f° · %s · %s", camera.modeName(), camera.eye[1],
                camera.eye[1] - camera.waterLevel, !showBed && terrain.fjord(camera.eye[0], camera.eye[2]) ? "dem Fjord" : "Grund", camera.heading(),
                Buehne.zone(camera.eye[0], camera.eye[2]), Buehne.naechsterOrt(camera.eye[0], camera.eye[2]));
    }

    /** Das volle HUD (H): alles, was die Phase weiß. */
    public String[] hudLines() {
        double[] sp = clock.sun();
        ComputeProbe.Result c = compute;
        double[] ll = Site.latLon(camera.eye[0], camera.eye[2]);
        double[] utm = Site.toUtm(camera.eye[0], camera.eye[2]);
        return new String[]{
                String.format(Locale.GERMAN, "%.0f Bilder/s   Grafikkarte %.2f ms   Prozessor %.2f ms   Budget %.1f ms", fps, gpuMs, cpuMs, quality.budgetMs()),
                passTimes(),
                (c == null ? "!Compute-Test läuft …" : (c.ok() ? "" : "!") + c.line()),
                String.format(Locale.GERMAN, "%s · Sonne %.1f° / %03.0f° · Belichtung %.2g (%s)", clock.label(), sp[0], sp[1], exposure(),
                        bildMessung && belichtung.lum() > 0 ? String.format(Locale.GERMAN, "gemessen, Bild %.3g", belichtung.lum()) : "geschätzt"),
                wiring == null ? "Verdrahtung —" : "Verdrahtung " + wiring.summary(),
                wiring == null ? "Wetter —" : wiring.weatherLine(),
                wetter.describe(showClouds, cloudShade)[0],
                wetter.describe(showClouds, cloudShade)[1],
                String.format(Locale.GERMAN, "%s · Sonne am Auge %s", schatten.describe(), sunOnWater > 0.5 ? "ja" : "nein (Schatten der Wände)"),
                fels.describe() + String.format(Locale.GERMAN, " · Wände nass %.0f %%, unter den Fällen %.0f %%", rockWet * 100, fels.cliff.fallWet * 100),
                faelle.describe(abfluss(), camera.eye) + " · " + abfluss().describe(),
                fluss.describe(),
                strasse.describe(),
                wald.describe(),
                ort.describe(),
                flotte.describe(),
                leute.describe(clock.epochMillis() / 1000.0),
                (showJahr ? jahr.describe(clock.epochMillis() / 1000.0) : "Jahr aus (fester Juni)") + (schneeRate > 0.01 ? String.format(Locale.GERMAN, " · Schneefall %.1f mm/h", schneeRate) : ""),
                klang.describe(),
                regieLine(),
                physik.describe(),
                (terrain.measured() ? "" : "!") + "Gelände: " + terrain.describe(),
                String.format(Locale.GERMAN, "Gelände %d Knoten, %,d Punkte · fein bis %d m, in den Kernen %d m · Grund hier NN %+.1f m",
                        lod.nodes, lod.vertices, 2 << lod.finest, 2 << lod.finestKern, camera.groundLevel),
                showBed ? "Wasser aus: Grund zeigen (Linien alle 50 m, hell bei 10, 150 und 250 m)" : wasser.describe(showMirror && quality.mirrorScale > 0),
                String.format(Locale.GERMAN, "Fjordgrund: %s · tiefste Stelle %s m · hier %.0f m tief", terrain.props.getProperty("grund", "nicht gemalt (Gelände von Phase 2)"),
                        terrain.props.getProperty("grund.tiefste", "–"), depthAt(camera.eye[0], camera.eye[2])),
                cameraLine(),
                String.format(Locale.GERMAN, "UTM 32 N  E %.0f  N %.0f · %s", utm[0], utm[1], Site.dms(ll[0], ll[1])),
                "Renderer.jar: " + Bibliothek.kurz(),
                String.format(Locale.GERMAN, "%s · %d×%d · %s", quality.label, hdrW, hdrH, reversedZ ? "umgekehrte Tiefe" : "klassische Tiefe"),
                glInfo};
    }

    // ------------------------------------------------------------------ Bilder

    private void takePhoto(GL4 gl, int w, int h) {
        try {
            BufferedImage img = readDefault(gl, w, h);
            File dir = photoDir != null ? photoDir : new File("docs/status");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Ordner " + dir + " lässt sich nicht anlegen");
            String name = photoName != null ? photoName : "Geiranger_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            photoName = null;
            File f = new File(dir, name + ".png");
            javax.imageio.ImageIO.write(img, "png", f);
            photoMsg = "Foto gespeichert: " + f.getPath();
            System.out.println(photoMsg);
        } catch (Exception e) {
            photoMsg = "Foto fehlgeschlagen: " + e.getMessage();
            System.err.println(photoMsg);
        }
        photoMsgUntil = System.currentTimeMillis() + 5000;
    }

    /** Liest das fertige Fensterbild (nach Nachbearbeitung und HUD). */
    BufferedImage readDefault(GL4 gl, int w, int h) {
        ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
        gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, defaultFbo);
        gl.glReadBuffer(defaultFbo == 0 ? GL.GL_BACK : GL.GL_COLOR_ATTACHMENT0);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            int o = (h - 1 - y) * w * 4;
            for (int x = 0; x < w; x++) {
                int i = o + x * 4;
                row[x] = ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255);
            }
            img.setRGB(0, y, w, 1, row, 0, w);
        }
        return img;
    }

    /** Für Werkzeuge: das letzte Bild als Bild (im GL-Faden aufrufen). */
    public BufferedImage grab(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        defaultFbo = d.getContext().getDefaultDrawFramebuffer();
        return readDefault(gl, d.getSurfaceWidth(), d.getSurfaceHeight());
    }

    // ------------------------------------------------------------------ Ende

    private void fail(String msg) {
        error = msg;
        System.err.println(msg);
        errorListener.accept(msg);
    }

    @Override
    public void dispose(GLAutoDrawable d) {
        physik.stop();
        GL4 gl = d.getGL().getGL4();
        for (Shader s : all) s.dispose(gl);
        lod.dispose(gl);
        wasser.dispose(gl);
        wetter.dispose(gl);
        fels.dispose(gl);
        faelle.dispose(gl);
        fluss.dispose(gl);
        strasse.dispose(gl);
        wald.dispose(gl);
        ort.dispose(gl);
        flotte.dispose(gl);
        leute.dispose(gl);
        klang.dispose();
        if (kinoGl != null) { kinoGl.dispose(gl); kinoGl = null; }
        if (schneeOk) schneefall.dispose(gl);
        schatten.dispose(gl);
        Gpu.deleteFbos(gl, depthCopyFbo);
        Gpu.deleteTextures(gl, depthCopyTex);
        hud.dispose(gl);
        Gpu.deleteFbos(gl, hdrFbo);
        belichtung.dispose(gl);
        Gpu.deleteTextures(gl, hdrTex, depthTex);
        if (emptyVao != 0) gl.glDeleteVertexArrays(1, new int[]{emptyVao}, 0);
        for (int[] s : stamp) if (s[0] != 0) gl.glDeleteQueries(MARKS, s, 0);
        ready = false;
    }

    /** Für das Bedienfeld: Windstärke als Text. */
    public String windLine() {
        if (wiring == null) return "Wind —";
        return String.format(Locale.GERMAN, "Wind Bft %d · %s · aus %03.0f°", wiring.wind.beaufort(), Beaufort.NAME[wiring.wind.beaufort()], wiring.wind.fromDeg());
    }

    /** Für das Bedienfeld (Block WASSER): die Fälle mit Abfluss, der nächste mit Abstand und Pegel. */
    public String fallLine() { return faelle.describe(abfluss(), camera.eye); }

    /** Für das Bedienfeld (Block WASSER): was das Wasser gerade ist und wie tief es unter dem Auge ist. */
    public String waterLine() {
        double d = depthAt(camera.eye[0], camera.eye[2]);
        String here = d > 0 ? String.format(Locale.GERMAN, " · unter dem Auge %.0f m tief", d) : "";
        if (showBed) return "Grund zeigen: kein Wasser, der gemalte Grund nach Tiefe" + here;
        if (!wasser.ok) return "ocean nicht gestartet: Platzhalter-Spiegel" + here;
        return String.format(Locale.GERMAN, "ocean, Preset Fjord · Windsee H_s %.2f m · Spiegelbild %s%s", wasser.ocean.state().windSeaHs,
                showMirror && quality.mirrorScale > 0 ? Math.round(quality.mirrorScale * 100) + " %" : "aus", here);
    }
}
