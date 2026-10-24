package com.dan.geiranger.scene;

import com.dan.avatar.Behavior;
import com.dan.avatar.Body;
import com.dan.avatar.Fabric;
import com.dan.avatar.LodMesh;
import com.dan.avatar.Motion;
import com.dan.avatar.Outfit;
import com.dan.avatar.Skeleton;
import com.dan.avatar.Skin;
import com.dan.avatar.Wardrobe;
import com.dan.figure.Person;
import com.dan.gltf.Gltf;
import com.dan.gltf.GltfCache;
import com.dan.gltf.Json;
import com.dan.road.Network;
import com.dan.road.Way;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Phase 13 (F18 A): die Menschen. Zwei Arten, wie das Werkbuch sie trennt:
 * <ul>
 * <li><b>Gäste aus {@code com.dan.avatar}</b> (Avatar.jar): auf der Plattform der Ørnesvingen bis zu sechs Personen,
 * auf der Treppe am Storfossen zwei. Jede Person ist eine gewürfelte {@code Person} mit eigenem Körper, Kleidung und
 * Frisur; sie geht mit {@code Motion} (Gang aus Formeln, Füße fest am Boden) von Platz zu Platz am Geländer, dreht sich
 * zur Aussicht, zeigt, winkt, bückt sich zum Foto; {@code Behavior} gibt Atem, Gewichtsverlagerung, Kopf und Blick.
 * Gezeichnet werden sie als Netze in drei Stufen aus ihrer gemischten Ruhelage ({@code LodMesh}, 1,2 / 3,5 / 9 cm) mit
 * Skinning auf der Grafikkarte ({@code CrowdGl}). Grundkörper, Skelett und Kleiderschrank lädt ein Faden im Hintergrund
 * ({@link #FADEN}), dort entstehen auch die Netze; bis dahin fehlen die Gäste.</li>
 * <li><b>Figuren aus {@code com.dan.figure}</b> für Gruppen und Ferne: die Busgruppe an der Plattform (ein Ausflugsbus
 * kommt vom Eidsdal herunter, hält in einer Bucht neben der Plattform, die Leute steigen aus, gehen auf die Plattform,
 * schauen, zeigen, fotografieren und steigen wieder ein), Wanderer von der Anlegestelle hinauf nach Skageflå und
 * zurück, Besucher auf der Treppe am Storfossen. Alle Figuren sind eine Funktion der Uhr ({@link #figuren}): springt
 * die Uhr, stehen sie gleich richtig.</li>
 * </ul>
 * Kajakfahrer sitzen seit Phase 13 als Teil des Netzes im Kajak ({@link Flotte}); {@code figure} kennt kein Sitzen.
 * Fahrpläne und Zahlen sind gesetzt [G]: Busse von Juni bis August alle 45 min (Mai und September seltener), 12 min
 * Halt; Wanderer nach Skageflå zweimal am Tag im Sommer.
 */
public final class Leute {
    public static final String FADEN = "Menschen";
    /** Haltezeit des Ausflugsbusses (s), Anfahrt und Abfahrt (s) und Weg dafür auf der Straße (m) [G]. */
    public static final double HALT = 12 * 60, ANFAHRT = 55, ABFAHRT = 45, ANFAHRT_WEG = 220, ABFAHRT_WEG = 200;
    /** Ankünfte des Busses an der Plattform (Stunde) von Juni bis August, im Mai und September, im April und Oktober [G]. */
    public static final double[] BUS_SOMMER = {9.67, 10.42, 11.17, 11.92, 13.5, 14.25, 15.0, 15.75, 16.5, 17.25},
            BUS_RAND = {10.42, 11.92, 14.25, 16.5}, BUS_SPAET = {11.92};
    /** Busbucht: so weit liegt die Mitte des Busses neben der Mitte der Fahrbahn (m), halbe Länge und Breite des Busses. */
    public static final double BUCHT = 4.1, BUS_HL = 6.1, BUS_HW = 1.275;
    /** Aufbruch der Wanderer an der Anlegestelle unter Skageflå (Stunde) im Sommer und im Mai/September [G]. */
    public static final double[] WANDER_SOMMER = {10.33, 13.33}, WANDER_RAND = {11.0};
    /** Tempo der Wanderer bergauf und bergab (m/s, waagerecht), Rast am Hof (s). */
    public static final double BERGAUF = 0.45, BERGAB = 0.62, RAST = 40 * 60;
    /** Größte Steigung des Pfads nach Skageflå (tan) [G]. */
    public static final double PFAD_STEIG = 0.36;

    // ------------------------------------------------------------------------------------------------- Gäste (Avatar)

    /** Eine Person aus {@code com.dan.avatar}. */
    public static final class Gast {
        public final String name, ort;
        public final long seed;
        public final com.dan.avatar.Person person;
        public final Outfit outfit;
        /** Netze in drei Stufen (fein, mittel, grob), sobald gebacken; Fußhöhe der Ruhelage; Körpergröße (m). */
        public volatile LodMesh[] lod;
        public volatile float footY;
        public volatile double groesse = 1.7;
        /** Für die Grafik: Gelenke als Dual-Quaternionen (8 je Gelenk), Lage der Person, Boden (m über NN). */
        public volatile float[] dq;
        public volatile double x, z, yaw, boden;
        /** Was die Person gerade tut („geht“, „schaut“, „zeigt“ …). */
        public volatile String tut = "wartet";
        public volatile boolean da;

        Motion motion;
        Behavior behavior;
        double[][] rest;
        final SplittableRandom rnd;
        double warte;
        int platz = -1;
        double[] blick;
        boolean geht;

        Gast(String name, String ort, long seed, int monat, boolean regen) {
            this.name = name;
            this.ort = ort;
            this.seed = seed;
            this.person = com.dan.avatar.Person.wuerfeln(seed);
            this.outfit = kleidung(person, monat, regen, seed);
            this.rnd = new SplittableRandom(seed * 31 + 7);
        }
    }

    /** Kleidung für den Fjord [G]: Sommer T-Shirt oder Pullover, sonst Pullover; bei Regen Ölzeug und Gummistiefel. */
    static Outfit kleidung(com.dan.avatar.Person p, int monat, boolean regen, long seed) {
        SplittableRandom r = new SplittableRandom(seed ^ 0x5DEECE66DL);
        double[][] oben = {{0.45, 0.05, 0.04}, {0.05, 0.08, 0.20}, {0.55, 0.42, 0.06}, {0.08, 0.22, 0.10}, {0.62, 0.62, 0.60}, {0.07, 0.07, 0.08},
                {0.20, 0.30, 0.45}, {0.50, 0.18, 0.30}};
        double[][] unten = {{0.06, 0.09, 0.18}, {0.05, 0.05, 0.06}, {0.30, 0.26, 0.18}, {0.20, 0.21, 0.22}, {0.10, 0.14, 0.24}};
        double[] o = oben[r.nextInt(oben.length)], u = unten[r.nextInt(unten.length)];
        if (regen) {
            double[] gelb = r.nextBoolean() ? new double[]{0.62, 0.48, 0.04} : new double[]{0.45, 0.06, 0.04};
            return new Outfit("Regen am Fjord").add("sweater_fisherman", Fabric.OELZEUG, gelb).add("cargo_pants", Fabric.BAUMWOLLE, u)
                    .add("hero_boots_2", Fabric.GUMMI, new double[]{0.06, 0.08, 0.06});
        }
        boolean sommer = monat >= 6 && monat <= 8;
        double w = r.nextDouble();
        if (p.gender < 0.5 && w < 0.3)   // Nacharbeit 13: mit Schuhen (das Sportzeug hat keine)
            return new Outfit("Damen Freizeit").add(w < 0.15 ? "female_casualsuit02" : "female_sportsuit01", null, null).add("mj_shoes", Fabric.LEINEN, new double[]{0.62, 0.62, 0.60});
        if (sommer && w < 0.65)
            return new Outfit("Sommer am Fjord").add("t_shirt_basic_tucked", Fabric.BAUMWOLLE, o)
                    .add(r.nextDouble() < 0.35 ? "jean_shorts" : "cargo_pants", r.nextBoolean() ? Fabric.DENIM : Fabric.BAUMWOLLE, u)
                    .add(r.nextBoolean() ? "mj_shoes" : "boots_ankle", r.nextBoolean() ? Fabric.LEINEN : Fabric.LEDER, new double[]{0.12, 0.10, 0.09});
        return new Outfit("Pullover am Fjord").add("sweater_fisherman", Fabric.STRICK, o).add("cargo_pants", Fabric.BAUMWOLLE, u)
                .add("boots_ankle", Fabric.LEDER, new double[]{0.14, 0.09, 0.06});
    }

    // ---------------------------------------------------------------------------------------------------------- Wege

    /** Ein Linienzug mit Höhe und Bogenlänge (waagerecht gemessen). */
    public static final class Pfad {
        public final double[] x, y, z, s;
        public final double laenge;

        Pfad(List<double[]> p) {
            int n = p.size();
            x = new double[n]; y = new double[n]; z = new double[n]; s = new double[n];
            for (int i = 0; i < n; i++) {
                x[i] = p.get(i)[0]; y[i] = p.get(i)[1]; z[i] = p.get(i)[2];
                if (i > 0) s[i] = s[i - 1] + Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
            }
            laenge = n == 0 ? 0 : s[n - 1];
        }

        /** Punkt an der Stelle d: x, y, z, Richtung (Azimut, rad, 0 = Nord, im Uhrzeigersinn). */
        public double[] at(double d) {
            int n = x.length;
            if (n == 1) return new double[]{x[0], y[0], z[0], 0};
            d = Math.max(0, Math.min(laenge, d));
            int lo = 0, hi = n - 1;
            while (hi - lo > 1) { int m = (lo + hi) >>> 1; if (s[m] <= d) lo = m; else hi = m; }
            double u = s[hi] > s[lo] ? (d - s[lo]) / (s[hi] - s[lo]) : 0;
            return new double[]{x[lo] + (x[hi] - x[lo]) * u, y[lo] + (y[hi] - y[lo]) * u, z[lo] + (z[hi] - z[lo]) * u,
                    Math.atan2(x[hi] - x[lo], -(z[hi] - z[lo]))};
        }

        /** Größte Steigung zwischen zwei Proben (tan). */
        public double steilste() {
            double m = 0;
            for (int i = 1; i < x.length; i++) if (s[i] > s[i - 1] + 1e-6) m = Math.max(m, Math.abs(y[i] - y[i - 1]) / (s[i] - s[i - 1]));
            return m;
        }
    }

    // ------------------------------------------------------------------------------------------------------- Zustand

    private final Terrain t;
    private final Ornevegen o;
    /** Plattform: Mitte x, Deck y, z, nach außen (Einheit), halbe Tiefe und Länge ({@link Plattform#rechteck}). */
    public final double[] deck;
    /** Busbucht: Stelle auf der Fahrbahn s, Seite (−1 links, +1 rechts der Richtung bergauf); null ohne Straße. */
    public final double[] bucht;
    /** Treppe am Storfossen (Fuß zuerst, Oberkante je Probe); null ohne Treppe. */
    public final Pfad treppe;
    private final Bauten bauten;
    /** Pfad von der Anlegestelle unter Skageflå zum Hof; null, wenn er nicht gefunden wurde. */
    public final Pfad skagefla;
    /** Blickpunkte von der Plattform: Ort, Fjord nach Westen, Fjord in der Mitte (x, y, z). */
    public final double[][] aussicht;
    /** Standorte der Gäste am Storfossen (x, Oberkante der Stufe, z) und der Fall (x, y, z), auf den sie schauen. */
    public final double[][] fossStand;
    public final double[] fossBlick;

    public final List<Gast> gaeste;
    /** Ebene ein und aus (Bedienfeld). */
    public volatile boolean show = true;
    /** Wie viele Gäste je Ort gerade da sind. */
    public volatile int aufPlattform, amFoss;
    /** Stand des Hintergrunds: „lädt …“, „bereit“ oder der Fehler. */
    public volatile String avatarStand = "wartet";
    /** Zeit je Schritt im Faden (ms), Schritte bisher. */
    public volatile double schrittMs;
    public volatile long schritte;

    private final ExecutorService faden;
    private Future<?> laeuft;
    private volatile boolean geladen;
    private Skeleton skeleton;
    private Wardrobe wardrobe;
    private double offen;
    private double letzteUhr = Double.NaN;
    private final Map<Integer, boolean[]> deckung = new HashMap<>();

    public Leute(Terrain t, Bauten bauten, Hoefe hoefe) { this(t, bauten, hoefe, 6); }

    /** monat: Kleidung der Gäste nach dem Monat beim Start (die Netze werden einmal gebacken). */
    public Leute(Terrain t, Bauten bauten, Hoefe hoefe, int monat) {
        this.t = t;
        this.o = t.ornevegen;
        this.deck = Plattform.rechteck(o);
        this.bucht = busbucht(t, o, deck);
        this.bauten = bauten;
        this.treppe = bauten == null || bauten.treppe.size() < 2 ? null : new Pfad(bauten.treppe);
        Hoefe.Hof sk = null;
        if (hoefe != null) for (Hoefe.Hof h : hoefe.hoefe) if (h.name().startsWith("Skagefl")) sk = h;
        this.skagefla = sk == null ? null : pfadZumHof(t, sk);
        double[] an = {Site.ANLEGER.x(), 2, Site.ANLEGER.z()};
        double[] west = Site.fromUtm(401_200, 6_887_750), mitte = Site.fromUtm(403_900, 6_887_300);
        aussicht = new double[][]{an, {west[0], 20, west[1]}, {mitte[0], 10, mitte[1]}};
        // Storfossen: zwei Stufen auf der Treppe um den Blick der Ansicht „Am Storfossen“, Blick auf den Fall
        double[] fb = bauten == null ? null : bauten.blick;
        if (fb != null && treppe != null) {
            // zwei und dreieinhalb Meter oberhalb des Blicks, damit die Ansicht 3 sie vor sich hat
            double d0 = naechste(treppe, fb[0], fb[2]);
            double[] a = treppe.at(d0 + 2.6), b = treppe.at(d0 + 4.0);
            fossStand = new double[][]{{a[0], bauten.oberkante(d0 + 2.6), a[2]}, {b[0], bauten.oberkante(d0 + 4.0), b[2]}};
            double hd = Math.toRadians(fb[3]);
            fossBlick = new double[]{fb[0] + Math.sin(hd) * 60, fb[1] + 6, fb[2] - Math.cos(hd) * 60};
        } else {
            fossStand = null;
            fossBlick = null;
        }
        List<Gast> g = new ArrayList<>();
        String[] namen = {"Ingrid", "Lars", "Mia", "Jonas", "Sofie", "Erik", "Hanne", "Ola"};
        if (deck != null) for (int i = 0; i < 6; i++) g.add(new Gast(namen[i], "Plattform", 1301 + i * 17L, monat, false));
        if (fossStand != null) for (int i = 0; i < 2; i++) g.add(new Gast(namen[6 + i], "Storfossen", 1501 + i * 23L, monat, false));
        gaeste = Collections.unmodifiableList(g);
        faden = Executors.newSingleThreadExecutor(r -> { Thread th = new Thread(r, FADEN); th.setDaemon(true); return th; });
    }

    static double naechste(Pfad p, double x, double z) {
        double best = 1e18, bd = 0;
        for (int i = 0; i < p.x.length; i++) {
            double d = Math.hypot(p.x[i] - x, p.z[i] - z);
            if (d < best) { best = d; bd = p.s[i]; }
        }
        return bd;
    }

    // -------------------------------------------------------------------------------------------------- Busbucht

    /**
     * Die Busbucht: eine Stelle bis 120 m vor der Plattform, an der der Bus ganz neben der Fahrbahn stehen kann (Mitte
     * {@link #BUCHT} m neben der Mitte, Gelände unter dem ganzen Bus höchstens 0,6 m über oder unter der Fahrbahn).
     * Bevorzugt die rechte Seite für den Bus, der vom Eidsdal herunterkommt (links der Richtung bergauf).
     * {s, Seite, schlechtester Abstand zum Gelände (m), Abstand zur Mitte der Plattform (m)} oder null.
     */
    static double[] busbucht(Terrain t, Ornevegen o, double[] deck) {
        if (o == null || deck == null) return null;
        Way w = o.way;
        float[] p = new float[8];
        double best = 1e18;
        double[] out = null;
        for (double s = 40; s < w.length - 40; s += 2) {
            w.at(s, 0, p);
            double dPl = Math.hypot(p[0] - deck[0], p[2] - deck[2]);
            if (dPl > 120 || dPl < 14) continue;
            for (int side = -1; side <= 1; side += 2) {
                double worst = 0;
                for (double u = -BUS_HL; u <= BUS_HL + 1e-9; u += 2)
                    for (double q = -BUS_HW; q <= BUS_HW + 1e-9; q += BUS_HW) {
                        w.at(s + u, 0, p);
                        double rx = -p[4], rz = p[3];
                        double x = p[0] + side * rx * (BUCHT + q), z = p[2] + side * rz * (BUCHT + q);
                        worst = Math.max(worst, Math.abs(t.height(x, z) - p[1]));
                        if (Plattform.auf(deck, x, z, 1.0)) worst = 1e9;   // nicht unter der Plattform
                    }
                double score = worst + 0.01 * dPl + (side < 0 ? 0 : 0.15);
                if (worst < 0.6 && score < best) { best = score; out = new double[]{s, side, worst, dPl}; }
            }
        }
        return out;
    }

    /** Lage des Busses auf der Fahrbahn an der Stelle s, quer q (m, + rechts der Richtung bergauf): x, y, z, tx, tz. */
    public double[] strasse(double s, double q) {
        float[] p = new float[8];
        o.way.at(s, 0, p);
        return new double[]{p[0] - p[4] * q, p[1], p[2] + p[3] * q, p[3], p[4]};
    }

    /**
     * Der Ausflugsbus zur Uhr: {x, y, z, Kurs (Grad), Zustand (0 fährt an, 1 steht, 2 fährt ab), Ankunft (s seit
     * Mitternacht)} oder null, wenn keiner da ist.
     */
    public double[] bus(double epochSec) {
        if (bucht == null) return null;
        ZonedDateTime zd = Instant.ofEpochMilli((long) (epochSec * 1000)).atZone(SceneClock.ZONE);
        double sec = zd.toLocalTime().toSecondOfDay() + (epochSec - Math.floor(epochSec));
        for (double h : busplan(zd.toLocalDate())) {
            double a = h * 3600, tt = sec - a;
            if (tt < -ANFAHRT || tt > HALT + ABFAHRT) continue;
            double side = bucht[1], s0 = bucht[0], lane = o.way.type.laneWidth / 2;
            // bergab heißt: s nimmt ab; der Bus fährt auf seiner Spur (links der Richtung bergauf: Seite −1)
            double s, q, zust;
            if (tt < 0) {
                double u = (tt + ANFAHRT) / ANFAHRT;                       // 0 … 1, gebremst
                s = s0 + ANFAHRT_WEG * (1 - u) * (1 - u);
                double ein = Math.max(0, Math.min(1, (25 - (s - s0)) / 25));
                q = side * (lane + (BUCHT - lane) * smooth(ein));
                zust = 0;
            } else if (tt <= HALT) {
                s = s0;
                q = side * BUCHT;
                zust = 1;
            } else {
                double u = (tt - HALT) / ABFAHRT;
                s = s0 - ABFAHRT_WEG * u * u;
                double aus = Math.max(0, Math.min(1, (s0 - s) / 25));
                q = side * (BUCHT - (BUCHT - lane) * smooth(aus));
                zust = 2;
            }
            double[] p = strasse(s, q);
            double kurs = Math.toDegrees(Math.atan2(-p[3], p[4]));   // bergab: gegen die Richtung von s
            return new double[]{p[0], p[1], p[2], (kurs + 360) % 360, zust, a};
        }
        return null;
    }

    /**
     * Netz des Ausflugsbusses im Format der Bauten [G: Reisebus 12,2 × 2,55 × 3,6 m, weiß mit blauem Band]: x nach
     * rechts, y oben, z nach hinten (vorn bei −z), wie die Schiffe; Tür vorn rechts.
     */
    public static float[] busNetz() {
        Bauten.Netz n = new Bauten.Netz();
        float[] weiss = {0.66f, 0.66f, 0.64f}, glas = {0.025f, 0.03f, 0.035f}, blau = {0.03f, 0.08f, 0.22f}, schwarz = {0.02f, 0.02f, 0.02f},
                grau = {0.18f, 0.18f, 0.19f}, rot = {0.6f, 0.02f, 0.02f}, licht = {0.9f, 0.9f, 0.85f};
        double hl = BUS_HL, hw = BUS_HW;
        n.box(0, 0, 0, 1, hl, hw, 0.45, 3.55, weiss);                       // Aufbau
        n.box(0, 0.4, 0, 1, hl - 0.9, hw + 0.012, 1.55, 2.95, glas);        // Fensterband an beiden Seiten
        n.box(0, -hl + 0.02, 0, 1, 0.03, hw - 0.12, 1.25, 3.25, glas);     // Frontscheibe
        n.box(0, hl - 0.02, 0, 1, 0.03, hw - 0.25, 2.1, 3.1, glas);        // Heckscheibe
        n.box(0, 0.4, 0, 1, hl - 0.4, hw + 0.016, 0.95, 1.25, blau);        // Band unter den Fenstern
        n.box(hw + 0.01, -hl + 1.25, 1, 0, 0.012, 0.55, 0.5, 3.0, glas);   // Tür vorn rechts (dünn außen auf der Seite)
        n.box(0, 0, 0, 1, hl - 0.05, hw - 0.05, 3.55, 3.68, grau);          // Dachkante, Klimagerät
        n.box(0, 1.5, 0, 1, 1.6, 0.8, 3.68, 3.9, grau);
        for (int s = -1; s <= 1; s += 2) {
            n.box(s * (hw - 0.3), -hl - 0.01, 0, 1, 0.02, 0.22, 0.75, 0.95, licht);   // Scheinwerfer
            n.box(s * (hw - 0.2), hl + 0.01, 0, 1, 0.02, 0.12, 0.8, 1.6, rot);        // Rückleuchten
        }
        for (double az : new double[]{-hl + 2.2, hl - 3.1, hl - 1.7}) rad(n, az, hw, 0.52, schwarz);
        return n.toArray();
    }

    /** Ein Radpaar quer zur Achse bei z (Achteck je Seite, Lauffläche), Radius r. */
    static void rad(Bauten.Netz n, double z, double hw, double r, float[] col) {
        int k = 10;
        for (int s = -1; s <= 1; s += 2) {
            double xa = s * (hw - 0.32), xb = s * (hw + 0.01);
            for (int i = 0; i < k; i++) {
                double a0 = 2 * Math.PI * i / k, a1 = 2 * Math.PI * (i + 1) / k;
                double y0 = r + Math.sin(a0) * r, z0 = z + Math.cos(a0) * r, y1 = r + Math.sin(a1) * r, z1 = z + Math.cos(a1) * r;
                double[] nn = {0, Math.sin((a0 + a1) / 2), Math.cos((a0 + a1) / 2)};
                n.quad(new double[]{xa, y0, z0}, new double[]{xb, y0, z0}, new double[]{xb, y1, z1}, new double[]{xa, y1, z1}, nn, col);
                // Flanke außen
                n.put(xb, r, z, s, 0, 0, col);
                n.put(xb, y0, z0, s, 0, 0, col);
                n.put(xb, y1, z1, s, 0, 0, col);
            }
        }
    }

    public static double[] busplan(LocalDate d) {
        int m = d.getMonthValue();
        return m >= 6 && m <= 8 ? BUS_SOMMER : m == 5 || m == 9 ? BUS_RAND : m == 4 || m == 10 ? BUS_SPAET : new double[0];
    }

    static double smooth(double u) { u = Math.max(0, Math.min(1, u)); return u * u * (3 - 2 * u); }

    // ---------------------------------------------------------------------------------------- Pfad nach Skageflå

    /**
     * Der Pfad von der Anlegestelle zum Hof [G: die Anlegestelle ist die nächste Stelle am Ufer unter dem Hof, 6 m vom
     * Wasser]. Gesucht als kürzester Weg (A*) auf einem Gitter von {@link #PFAD_ZELLE} m über das Gelände: ein Schritt
     * kostet seine Länge, steiler als {@link #PFAD_STEIG} zusätzlich das Vierfache des Zuviel, steiler als 0,9 (42°)
     * ist er verboten; nicht durch den Fjord. So entstehen von selbst Kehren, wo der Hang steil ist. Danach geglättet
     * und die Höhe neu vom Gelände.
     */
    static Pfad pfadZumHof(Terrain t, Hoefe.Hof hof) {
        double bd = 1e18, ux = 0, uz = 0;
        for (double a = 0; a < 360; a += 2)
            for (double r = 10; r < 900; r += 4) {
                double x = hof.x() + Math.sin(Math.toRadians(a)) * r, z = hof.z() - Math.cos(Math.toRadians(a)) * r;
                if (t.fjord(x, z)) {
                    if (r < bd) { bd = r; ux = x; uz = z; }
                    break;
                }
            }
        if (bd > 1e17) return null;
        double dl = Math.hypot(hof.x() - ux, hof.z() - uz);
        double lx = ux + (hof.x() - ux) / dl * 6, lz = uz + (hof.z() - uz) / dl * 6;
        // Gitter um beide Enden mit 250 m Rand
        double c = PFAD_ZELLE, x0 = Math.min(lx, hof.x()) - 250, z0 = Math.min(lz, hof.z()) - 250;
        int nx = (int) Math.ceil((Math.abs(lx - hof.x()) + 500) / c) + 1, nz = (int) Math.ceil((Math.abs(lz - hof.z()) + 500) / c) + 1;
        double[] hh = new double[nx * nz];
        boolean[] wasser = new boolean[nx * nz];
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                double x = x0 + i * c, z = z0 + j * c;
                hh[j * nx + i] = t.height(x, z);
                wasser[j * nx + i] = t.fjord(x, z);
            }
        int start = (int) Math.round((lz - z0) / c) * nx + (int) Math.round((lx - x0) / c);
        int ziel = (int) Math.round((hof.z() - z0) / c) * nx + (int) Math.round((hof.x() - x0) / c);
        wasser[start] = false;
        double[] g = new double[nx * nz];
        int[] von = new int[nx * nz];
        java.util.Arrays.fill(g, Double.MAX_VALUE);
        java.util.Arrays.fill(von, -1);
        java.util.PriorityQueue<double[]> q = new java.util.PriorityQueue<>((p1, p2) -> Double.compare(p1[0], p2[0]));
        g[start] = 0;
        int zi = ziel % nx, zj = ziel / nx;
        q.add(new double[]{0, start});
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {2, 1}, {1, 2}, {-1, 2}, {-2, 1}, {-2, -1}, {-1, -2}, {1, -2}, {2, -1}};
        while (!q.isEmpty()) {
            double[] e = q.poll();
            int k = (int) e[1];
            if (k == ziel) break;
            int i = k % nx, j = k / nx;
            if (e[0] > g[k] + Math.hypot(i - zi, j - zj) * c + 1e-6) continue;
            for (int[] d : nb) {
                int ii = i + d[0], jj = j + d[1];
                if (ii < 0 || jj < 0 || ii >= nx || jj >= nz) continue;
                int kk = jj * nx + ii;
                if (wasser[kk]) continue;
                double len = Math.hypot(d[0], d[1]) * c, st = Math.abs(hh[kk] - hh[k]) / len;
                if (st > 0.9) continue;
                double cost = len * (1 + 4 * Math.max(0, st - PFAD_STEIG) / PFAD_STEIG);
                if (g[k] + cost < g[kk]) {
                    g[kk] = g[k] + cost;
                    von[kk] = k;
                    q.add(new double[]{g[kk] + Math.hypot(ii - zi, jj - zj) * c, kk});
                }
            }
        }
        if (von[ziel] < 0) return null;
        List<double[]> p = new ArrayList<>();
        for (int k = ziel; k >= 0; k = von[k]) p.add(new double[]{x0 + (k % nx) * c, 0, z0 + (k / nx) * c});
        Collections.reverse(p);
        p.set(0, new double[]{lx, 0, lz});
        p.set(p.size() - 1, new double[]{hof.x(), 0, hof.z()});
        for (double[] v : p) v[1] = t.height(v[0], v[2]);
        // glätten, wo es die Steigung nicht über 0,9 treibt (in Kehren bleibt die Ecke)
        for (int k = 0; k < 3; k++)
            for (int i = 1; i < p.size() - 1; i++) {
                double[] a = p.get(i - 1), b = p.get(i), d = p.get(i + 1);
                double x = (a[0] + b[0] * 2 + d[0]) / 4, z = (a[2] + b[2] * 2 + d[2]) / 4, y = t.height(x, z);
                double s1 = Math.abs(y - a[1]) / Math.max(1e-6, Math.hypot(x - a[0], z - a[2])), s2 = Math.abs(d[1] - y) / Math.max(1e-6, Math.hypot(d[0] - x, d[2] - z));
                if (Math.max(s1, s2) <= 0.9) p.set(i, new double[]{x, y, z});
            }
        return new Pfad(p);
    }

    /** Gitter der Pfadsuche (m). */
    public static final double PFAD_ZELLE = 4;

    // ----------------------------------------------------------------------------------------- Figuren zur Uhr

    /** Höhe zum Gehen: auf dem Deck der Plattform, auf der Fahrbahn, sonst das Gelände. */
    public double boden(double x, double z) {
        if (deck != null && Plattform.auf(deck, x, z, 0)) return deck[1];
        if (o != null) {
            Network.Hit hit = new Network.Hit();
            if (o.net.locate(x, z, hit)) return hit.deck;
        }
        return t.height(x, z);
    }

    /**
     * Alle Figuren zur Uhr (für die Grafik und die Prüfung). Busgruppe, Wanderer und Besucher der Treppe sind reine
     * Funktionen der Zeit.
     */
    public List<Person> figuren(double epochSec) {
        List<Person> out = new ArrayList<>();
        ZonedDateTime zd = Instant.ofEpochMilli((long) (epochSec * 1000)).atZone(SceneClock.ZONE);
        double sec = zd.toLocalTime().toSecondOfDay() + (epochSec - Math.floor(epochSec));
        int monat = zd.getMonthValue();
        busgruppe(epochSec, monat, out);
        wanderer(zd.toLocalDate(), sec, monat, out);
        treppe(zd.toLocalDate(), sec, monat, out);
        return out;
    }

    /** Wie viele Figuren gerade in jeder Gruppe sind: Busgruppe, Wanderer, Treppe (für HUD und Prüfung). */
    public int[] zaehle(double epochSec) {
        int[] n = new int[3];
        for (Person p : figuren(epochSec)) n[p.id / 1000 - 1]++;
        return n;
    }

    /** Größe der Busgruppe zu einer Ankunft (22 bis 31 Personen) [G]. */
    public static int gruppe(double ankunft) { return 22 + (int) (Math.abs(Math.floor(ankunft / 60) * 7919) % 10); }

    /** Die Plätze der Busgruppe auf dem Deck (x, z), je Person einer, nicht zu dicht. */
    List<double[]> plaetzeBus(int n, long seed) {
        List<double[]> out = new ArrayList<>();
        SplittableRandom r = new SplittableRandom(seed);
        double ox = deck[3], oz = deck[4], qx = -oz, qz = ox;
        for (int i = 0; i < n; i++) {
            double[] best = null;
            for (int k = 0; k < 40 && best == null; k++) {
                // vorn am Geländer häufiger (Anteil 60 %), sonst irgendwo auf dem Deck
                double s = r.nextDouble() < 0.6 ? deck[5] - 0.6 - r.nextDouble() * 1.2 : -deck[5] + 1.0 + r.nextDouble() * (2 * deck[5] - 2.2);
                double u = (r.nextDouble() * 2 - 1) * (deck[6] - 0.8);
                double x = deck[0] + ox * s + qx * u, z = deck[2] + oz * s + qz * u;
                boolean frei = Math.abs(s - (Plattform.TIEFE / 2 - 2.5)) > 1.2 || Math.abs(u) > 1.2;   // nicht im Auge der Ansicht 1
                for (double[] q : out) if (Math.hypot(q[0] - x, q[1] - z) < 0.75) { frei = false; break; }
                if (frei) best = new double[]{x, z};
            }
            if (best == null) best = new double[]{deck[0], deck[2]};
            out.add(best);
        }
        return out;
    }

    /** Busgruppe: aussteigen, zur Plattform, schauen, zeigen, fotografieren, zurück, einsteigen. */
    void busgruppe(double epochSec, int monat, List<Person> out) {
        double[] b = bus(epochSec);
        if (b == null || deck == null) return;
        ZonedDateTime zd = Instant.ofEpochMilli((long) (epochSec * 1000)).atZone(SceneClock.ZONE);
        double sec = zd.toLocalTime().toSecondOfDay() + (epochSec - Math.floor(epochSec));
        double tt = sec - b[5];
        if (tt < 8 || tt > HALT) return;
        int n = gruppe(b[5]);
        long seed = (long) b[5] + zd.toLocalDate().toEpochDay() * 86_400L;
        // Tür vorn rechts am Bus (rechts in Fahrtrichtung bergab), 0,8 m daneben
        double k = Math.toRadians(b[3]), fx = Math.sin(k), fz = -Math.cos(k), rx = -fz, rz = fx;
        double tx = b[0] + fx * (BUS_HL - 1.2) + rx * (BUS_HW + 0.8), tz = b[2] + fz * (BUS_HL - 1.2) + rz * (BUS_HW + 0.8);
        // Eingang der Plattform: der Punkt auf dem inneren Rand des Decks, der der Tür am nächsten liegt
        double ox = deck[3], oz = deck[4], qx = -oz, qz = ox;
        double best = 1e18, ex = deck[0], ez = deck[2];
        for (double u = -deck[6] + 0.8; u <= deck[6] - 0.8; u += 0.5)
            for (double s : new double[]{-deck[5] + 0.4}) {
                double x = deck[0] + ox * s + qx * u, z = deck[2] + oz * s + qz * u, d = Math.hypot(x - tx, z - tz);
                if (d < best) { best = d; ex = x; ez = z; }
            }
        double vx = ex - ox * 2.0, vz = ez - oz * 2.0;   // vor dem Deck auf dem Weg
        List<double[]> pl = plaetzeBus(n, seed);
        SplittableRandom r = new SplittableRandom(seed ^ 77);
        for (int i = 0; i < n; i++) {
            double v = 0.95 + 0.3 * r.nextDouble(), sc = 0.9 + 0.16 * r.nextDouble();
            double aus = 10 + 2.2 * i + r.nextDouble();
            double[] ziel = pl.get(i);
            // Weg: Tür, vor das Deck, Eingang, Platz (Abstand wird immer frisch gerechnet)
            double[][] wp = {{tx, tz}, {vx + qx * (r.nextDouble() - 0.5) * 2, vz + qz * (r.nextDouble() - 0.5) * 2}, {ex, ez}, ziel};
            double len = 0;
            for (int j = 1; j < wp.length; j++) len += Math.hypot(wp[j][0] - wp[j - 1][0], wp[j][1] - wp[j - 1][1]);
            double an = aus + len / v;
            double zurueck = HALT - 25 - 2.0 * (n - 1 - i) - len / v;   // so, dass alle vor der Abfahrt drin sind
            double ein = zurueck + len / v;
            Person p = new Person(1000 + i, i == 0 ? Person.Role.GUIDE : Person.Role.ADULT, touristen(monat, r, false));
            p.scale = sc;
            double weg;
            boolean hin = true;
            if (tt < aus || tt > ein) continue;
            if (tt < an) { weg = (tt - aus) * v; p.gait = 1; p.speed = v; }
            else if (tt < zurueck) { weg = len; p.gait = 0; p.speed = 0; }
            else { weg = len - (tt - zurueck) * v; p.gait = 1; p.speed = v; hin = false; }
            double[] q = entlang(wp, weg);
            p.x = q[0];
            p.z = q[1];
            p.y = boden(q[0], q[1]);
            p.heading = hin ? q[2] : q[2] + Math.PI;
            p.phase = (weg / (2 * com.dan.figure.FigureMesh.stride(1, sc))) % 1;
            if (p.gait == 0) {
                // am Platz: zur Aussicht (Westen und Süden) mit eigener Abweichung; dann und wann zeigen, bücken (Foto)
                double[] a = aussicht[i % aussicht.length];
                double h = Math.atan2(a[0] - p.x, -(a[2] - p.z)) + (r.nextDouble() - 0.5) * 0.9;
                double dw = tt - an;
                p.heading = h + 0.25 * Math.sin(dw * 0.05 + i);
                double zeig = 30 + r.nextDouble() * 200, foto = 60 + r.nextDouble() * 300;
                p.point = puls(dw - zeig, 4);
                p.bend = 0.35 * puls(dw - foto, 3);
                p.look = 0.4 * Math.sin(dw * 0.13 + i * 1.7);
            }
            out.add(p);
        }
    }

    /** 0 … 1 … 0 über d Sekunden ab x = 0. */
    static double puls(double x, double d) {
        if (x < 0 || x > d) return 0;
        return Math.sin(Math.PI * x / d);
    }

    /** Punkt auf einem Linienzug (x, z) nach dem Weg d: x, z, Richtung (Azimut, rad). */
    static double[] entlang(double[][] wp, double d) {
        for (int j = 1; j < wp.length; j++) {
            double l = Math.hypot(wp[j][0] - wp[j - 1][0], wp[j][1] - wp[j - 1][1]);
            if (d <= l || j == wp.length - 1) {
                double u = l > 0 ? Math.max(0, Math.min(1, d / l)) : 1;
                return new double[]{wp[j - 1][0] + (wp[j][0] - wp[j - 1][0]) * u, wp[j - 1][1] + (wp[j][1] - wp[j - 1][1]) * u,
                        Math.atan2(wp[j][0] - wp[j - 1][0], -(wp[j][1] - wp[j - 1][1]))};
            }
            d -= l;
        }
        return new double[]{wp[0][0], wp[0][1], 0};
    }

    /** Wanderer nach Skageflå: hinauf, Rast am Hof, hinunter (in Reihe, 2,5 m Abstand). */
    void wanderer(LocalDate d, double sec, int monat, List<Person> out) {
        if (skagefla == null) return;
        int m = monat;
        double[] plan = m >= 6 && m <= 8 ? WANDER_SOMMER : m == 5 || m == 9 ? WANDER_RAND : new double[0];
        double L = skagefla.laenge, up = L / BERGAUF, down = L / BERGAB;
        for (int g = 0; g < plan.length; g++) {
            double t0 = plan[g] * 3600, tt = sec - t0;
            int n = 8;
            if (tt < 0 || tt > up + RAST + down + n * 6) continue;
            SplittableRandom r = new SplittableRandom(d.toEpochDay() * 13 + g);
            for (int i = 0; i < n; i++) {
                double gap = 2.5 * i;
                Person p = new Person(2000 + g * 100 + i, i == 0 ? Person.Role.GUIDE : Person.Role.ADULT, touristen(monat, r, true));
                p.scale = 0.92 + 0.14 * r.nextDouble();
                double s;
                boolean hin;
                double ti = tt - gap / BERGAUF;
                if (ti < 0) continue;
                if (ti < up) { s = ti * BERGAUF; hin = true; p.gait = 1; p.speed = BERGAUF; }
                else if (ti < up + RAST) { s = L; hin = true; p.gait = 0; }
                else if (ti < up + RAST + down) { s = L - (ti - up - RAST) * BERGAB; hin = false; p.gait = 1; p.speed = BERGAB; }
                else continue;   // unten angekommen: abgeholt
                double[] q = skagefla.at(s);
                p.x = q[0];
                p.z = q[2];
                p.y = q[1];
                p.heading = hin ? q[3] : q[3] + Math.PI;
                if (p.gait == 0) {
                    // Rast am Hof: im Kreis um den Hofplatz, Blick über den Fjord
                    double a = i * 2 * Math.PI / n;
                    p.x += Math.cos(a) * 4;
                    p.z += Math.sin(a) * 4;
                    p.y = t.height(p.x, p.z);
                    p.heading = Math.atan2(Site.SCHWESTERN.x() - p.x, -(Site.SCHWESTERN.z() - p.z));
                    p.point = puls(ti - up - 300 - 90 * i, 5);
                }
                p.phase = (s / (2 * com.dan.figure.FigureMesh.stride(1, p.scale))) % 1;
                out.add(p);
            }
        }
    }

    /** Besucher auf der Treppe am Storfossen: jede Person geht hinauf, schaut oben, geht hinunter, Pause; versetzt. */
    void treppe(LocalDate d, double sec, int monat, List<Person> out) {
        if (treppe == null) return;
        boolean saison = monat >= 5 && monat <= 9;
        double von = saison ? 9 * 3600 : 10.5 * 3600, bis = saison ? 20 * 3600 : 15.5 * 3600;
        if (sec < von || sec > bis) return;
        int n = saison ? 6 : 2;
        double L = treppe.laenge, auf = L / 0.42, ab = L / 0.55, oben = 90, unten = 240, periode = auf + oben + ab + unten;
        SplittableRandom r = new SplittableRandom(d.toEpochDay() * 7 + 3);
        for (int i = 0; i < n; i++) {
            double ph = ((sec - von) + i * periode / n + r.nextDouble() * 60) % periode;
            Person p = new Person(3000 + i, Person.Role.ADULT, touristen(monat, r, false));
            p.scale = 0.9 + 0.16 * r.nextDouble();
            double s;
            boolean hin;
            if (ph < auf) { s = ph / auf * L; hin = true; p.gait = 1; p.speed = 0.42; }
            else if (ph < auf + oben) { s = L; hin = true; p.gait = 0; p.point = puls(ph - auf - 20, 4); }
            else if (ph < auf + oben + ab) { s = L - (ph - auf - oben) / ab * L; hin = false; p.gait = 1; p.speed = 0.55; }
            else continue;   // unten: zum Ort hinaus
            double[] q = treppe.at(s);
            // auf der Treppe rechts gehen: 0,4 m neben der Mitte
            double side = hin ? 0.4 : -0.4, rx = Math.cos(q[3]), rz = Math.sin(q[3]);
            p.x = q[0] + rx * side;
            p.z = q[2] + rz * side;
            p.y = bauten.oberkante(s);
            p.heading = hin ? q[3] : q[3] + Math.PI;
            p.phase = (s / (2 * com.dan.figure.FigureMesh.stride(1, p.scale))) % 1;
            out.add(p);
        }
    }

    /** Kleidung der Touristen (figure) [G]: Jacken in kräftigen Farben, lange Hosen, Schuhe; Wanderer mit Rucksack. */
    static com.dan.figure.Outfit touristen(int monat, SplittableRandom r, boolean wanderer) {
        com.dan.figure.Outfit f = new com.dan.figure.Outfit();
        double[][] jacke = {{0.50, 0.05, 0.04}, {0.05, 0.10, 0.30}, {0.62, 0.48, 0.05}, {0.06, 0.26, 0.12}, {0.07, 0.07, 0.08}, {0.55, 0.55, 0.53},
                {0.18, 0.32, 0.50}, {0.55, 0.20, 0.32}, {0.60, 0.30, 0.05}};
        double[][] hose = {{0.06, 0.09, 0.20}, {0.05, 0.05, 0.06}, {0.33, 0.28, 0.20}, {0.20, 0.21, 0.22}, {0.12, 0.16, 0.26}};
        double[][] haut = {{0.80, 0.60, 0.48}, {0.74, 0.54, 0.42}, {0.62, 0.44, 0.33}, {0.45, 0.30, 0.21}, {0.30, 0.19, 0.13}};
        double[][] haar = {{0.10, 0.07, 0.05}, {0.30, 0.20, 0.10}, {0.55, 0.42, 0.25}, {0.06, 0.05, 0.05}, {0.45, 0.45, 0.45}};
        double[] j = jacke[r.nextInt(jacke.length)], h = hose[r.nextInt(hose.length)];
        double[] s0 = haut[r.nextDouble() < 0.7 ? r.nextInt(2) : 2 + r.nextInt(3)];
        // Nacharbeit 13: Haut und Haar aus der Tafel von com.dan.figure (die eigenen Werte wirkten im Licht weißgrau)
        com.dan.figure.Outfit ref = com.dan.figure.Outfit.forMonth(monat, r, false, false);
        double[] s = ref.color(com.dan.figure.Outfit.SKIN), hr0 = ref.color(com.dan.figure.Outfit.HAIR);
        boolean sommer = monat >= 6 && monat <= 8, kurz = sommer && r.nextDouble() < 0.3, aermel = sommer && r.nextDouble() < 0.35;
        f.set(com.dan.figure.Outfit.SKIN, s[0], s[1], s[2]);
        f.set(com.dan.figure.Outfit.JACKET, j[0], j[1], j[2]);
        f.set(com.dan.figure.Outfit.FOREARM, aermel ? s[0] : j[0], aermel ? s[1] : j[1], aermel ? s[2] : j[2]);
        f.set(com.dan.figure.Outfit.TROUSERS, h[0], h[1], h[2]);
        f.set(com.dan.figure.Outfit.KNEE, kurz ? s[0] : h[0], kurz ? s[1] : h[1], kurz ? s[2] : h[2]);
        f.set(com.dan.figure.Outfit.SHIN, kurz ? s[0] : h[0], kurz ? s[1] : h[1], kurz ? s[2] : h[2]);
        f.set(com.dan.figure.Outfit.FOOT, 0.10, 0.08, 0.07);
        double[] hr = hr0;
        f.set(com.dan.figure.Outfit.HAIR, hr[0], hr[1], hr[2]);
        f.hat = r.nextDouble() < (wanderer ? 0.5 : 0.18);
        f.set(com.dan.figure.Outfit.HAT, j[0] * 0.6, j[1] * 0.6, j[2] * 0.6);
        f.pack = wanderer || r.nextDouble() < 0.2;
        f.set(com.dan.figure.Outfit.PACK, 0.12, 0.14, 0.16);
        f.barefoot = false;
        f.label = wanderer ? "Wanderer" : "Tourist";
        return f;
    }

    // --------------------------------------------------------------------------------------- Gäste im Faden

    /** Sind Grundkörper, Skelett und alle Netze der Gäste fertig? */
    public boolean bereit() { return geladen; }

    /** Laden anstoßen (einmal; der Rest läuft im Faden). */
    public void starten() {
        if (laeuft == null) laeuft = faden.submit(this::laden);
    }

    /** Lädt Avatar.jar: Grundkörper (rund 180 MB), Skelett, Kleiderschrank; backt je Gast drei Netze. */
    private void laden() {
        try {
            avatarStand = "lädt Grundkörper …";
            long t0 = System.nanoTime();
            byte[] glb;
            try (var in = Body.class.getResourceAsStream("/com/dan/avatar/res/avatar_base.glb")) {
                if (in == null) throw new IllegalStateException("avatar_base.glb fehlt in Avatar.jar");
                glb = in.readAllBytes();
            }
            Gltf g;
            try {
                g = GltfCache.load(glb, Path.of("daten", "cache"), "avatar_base");   // Zwischenspeicher im Projekt (schneller beim nächsten Start)
            } catch (java.io.IOException | RuntimeException e) {
                g = GltfCache.load(glb, java.nio.file.Files.createTempDirectory("geiranger-avatar"), "avatar_base");
            }
            String rig;
            try (var in = Body.class.getResourceAsStream("/com/dan/avatar/res/avatar_base.rig.json")) {
                rig = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            skeleton = new Skeleton(g, Json.parse(rig));
            wardrobe = Wardrobe.load();
            Body body = new Body(g);
            int k = 0;
            for (Gast gs : gaeste) {
                avatarStand = String.format(Locale.GERMAN, "backt %d von %d …", ++k, gaeste.size());
                Map<String, Double> w = gs.person.weights();
                gs.lod = backen(body, gs);
                gs.footY = body.footY(w);
                gs.rest = skeleton.restFor(w);
                gs.groesse = koerpergroesse(gs.rest);
                gs.motion = new Motion(Motion.line(0, 0, 0), 1.0);
                gs.behavior = new Behavior(gs.seed);
            }
            geladen = true;
            avatarStand = String.format(Locale.GERMAN, "bereit (%.1f s)", (System.nanoTime() - t0) / 1e9);
        } catch (Throwable e) {
            avatarStand = "aus: " + e;
            System.err.println("Geiranger: Avatar nicht verfügbar – " + e);
        }
    }

    /** Höhe des Kopfgelenks über dem Boden plus Schädel (m) aus der Ruhelage. */
    double koerpergroesse(double[][] rest) {
        int h = skeleton.joint("head");
        return h < 0 ? 1.7 : Skeleton.position(rest, h)[1] + 0.2;
    }

    /**
     * Die Netze einer Person in drei Stufen (1,2, 3,5 und 9 cm Gitter) aus der gemischten Ruhelage: Körper ohne
     * Verdecktes, getragene Kleidung in ihrer Farbe, die Frisur als Fläche (wie das Studio für die Menge).
     */
    LodMesh[] backen(Body body, Gast g) {
        body.mix(g.person.weights());
        Body.Part bp = body.body();
        int[] extra = new int[bp.prim.vertexCount];
        int worn = 0;
        List<Body.Part> parts = new ArrayList<>();
        List<int[]> idx = new ArrayList<>();
        List<float[]> cols = new ArrayList<>();
        String haar = HAAR.get(g.person.hair);
        for (Body.Part p : body.parts) {
            String name = p.mesh.name;
            Wardrobe.Item item = wardrobe.item(name);
            if (item != null) {
                Outfit.Piece piece = null;
                for (Outfit.Piece pc : g.outfit.pieces) if (wardrobe.find(pc.mesh) == item) piece = pc;
                if (piece == null) continue;
                worn |= 1 << item.bit;
                if (!item.groups) {
                    final Body.Part part = p;
                    boolean[] cov = deckung.computeIfAbsent(item.bit, b -> Wardrobe.cover(body.body(), part));
                    for (int i = 0; i < cov.length && i < extra.length; i++) if (cov[i]) extra[i] |= 1 << item.bit;
                }
                parts.add(p);
                idx.add(p.prim.indices);
                double[] c = piece.fabric == null ? new double[]{0.1, 0.14, 0.26} : piece.colorOrDefault();
                cols.add(new float[]{(float) c[0], (float) c[1], (float) c[2]});
            } else if (haar != null && name.contains(haar)) {
                parts.add(p);
                idx.add(p.prim.indices);
                cols.add(LodMesh.hairColor(g.person.hairColor, g.person.hairRed));
            }
        }
        int[] saved = bp.extraCover;
        bp.extraCover = extra;
        int[] vis = bp.visibleIndices(worn);
        bp.extraCover = saved;
        parts.add(0, bp);
        idx.add(0, vis);
        cols.add(0, Skin.tone(g.person.melanin, g.person.redness));
        return new LodMesh[]{LodMesh.build(body, parts, idx, cols, 0.012), LodMesh.build(body, parts, idx, cols, 0.035), LodMesh.build(body, parts, idx, cols, 0.09)};
    }

    /** Frisur → Netz im Grundkörper (wie {@code avatarstudio.HairRig.STYLE_MESH}). */
    static final Map<String, String> HAAR = Map.of("bob", "bob02", "lang", "long01", "zopf", "braid01", "dutt", "braidbun", "kurz", "short02", "locken", "afro01");

    /**
     * Vom Bildlauf, je Bild: die Uhr (Epoche in s) und die echte Zeit dt (s). Ist der letzte Schritt des Fadens fertig,
     * bekommt er den nächsten mit der aufgelaufenen Zeit.
     */
    public void step(double epochSec, double dt) {
        offen += Math.max(0, dt);
        if (!geladen || (laeuft != null && !laeuft.isDone())) return;
        double d = Math.min(0.25, offen);
        offen = 0;
        laeuft = faden.submit(() -> schritt(epochSec, d));
    }

    /** Für die Prüfung und Standbilder: im aufrufenden Faden laden und n Schritte zu dt rechnen. */
    public void sofort(double epochSec, int n, double dt) {
        if (!geladen) laden();
        for (int i = 0; i < n && geladen; i++) schritt(epochSec + i * dt, dt);
    }

    /** Ein Schritt aller Gäste: anwesend oder nicht, Regie, Bewegung, Verhalten, Pose → Gelenke für die Grafik. */
    synchronized void schritt(double epochSec, double dt) {
        long t0 = System.nanoTime();
        ZonedDateTime zd = Instant.ofEpochMilli((long) (epochSec * 1000)).atZone(SceneClock.ZONE);
        double h = zd.toLocalTime().toSecondOfDay() / 3600.0;
        int m = zd.getMonthValue();
        boolean sprung = Double.isNaN(letzteUhr) || Math.abs(epochSec - letzteUhr) > 120;
        letzteUhr = epochSec;
        int plattform = anzahl(m, h, "Plattform"), foss = anzahl(m, h, "Storfossen");
        int np = 0, nf = 0;
        Skeleton.Additive add = new Skeleton.Additive();
        for (Gast g : gaeste) {
            boolean soll = g.ort.equals("Plattform") ? np++ < plattform : nf++ < foss;
            if (!soll) { g.da = false; continue; }
            if (!g.da || sprung) aufstellen(g);
            g.da = true;
            regie(g, dt);
            g.motion.step(dt);
            g.behavior.step(dt);
            add.clear();
            g.motion.additive(add);
            g.behavior.additive(add);
            double[][] w = skeleton.pose(skeleton.poses.get("stehen"), g.rest, true, add);
            g.motion.apply(skeleton, w, g.rest);
            g.dq = Skeleton.toDq(Skeleton.skin(w, g.rest));
            g.x = g.motion.x;
            g.z = g.motion.z;
            g.yaw = g.motion.yaw;
        }
        aufPlattform = Math.min(plattform, (int) gaeste.stream().filter(x -> x.ort.equals("Plattform") && x.da).count());
        amFoss = (int) gaeste.stream().filter(x -> x.ort.equals("Storfossen") && x.da).count();
        schritte++;
        schrittMs = (System.nanoTime() - t0) / 1e6;
    }

    /** Wie viele Gäste zur Stunde h im Monat m an einem Ort sind [G]. */
    public static int anzahl(int m, double h, String ort) {
        boolean sommer = m >= 6 && m <= 8, rand = m == 5 || m == 9;
        double von = sommer ? 8.5 : rand ? 9.5 : 10.5, bis = sommer ? 21 : rand ? 19 : 15.5;
        if (h < von || h > bis) return 0;
        if (ort.equals("Storfossen")) return sommer || rand ? 2 : 1;
        return sommer ? 6 : rand ? 4 : 2;
    }

    /** Plätze am Geländer der Plattform (x, z, Blick-x, Blick-z): außen in Reihe, an beiden Enden. */
    List<double[]> plaetze() {
        List<double[]> out = new ArrayList<>();
        double ox = deck[3], oz = deck[4], qx = -oz, qz = ox;
        for (double u = -deck[6] + 1.2; u <= deck[6] - 1.2 + 1e-9; u += 1.5) {
            double s = deck[5] - 0.5;
            out.add(new double[]{deck[0] + ox * s + qx * u, deck[2] + oz * s + qz * u, ox, oz});
        }
        for (int e = -1; e <= 1; e += 2)
            for (double s = -deck[5] + 2.5; s <= deck[5] - 1.0 + 1e-9; s += 1.5) {
                double u = e * (deck[6] - 0.5);
                out.add(new double[]{deck[0] + ox * s + qx * u, deck[2] + oz * s + qz * u, e * qx * 0.7 + ox * 0.7, e * qz * 0.7 + oz * 0.7});
            }
        return out;
    }

    /** Eine Person an ihren Ort stellen (nach dem Laden, wenn sie kommt, nach einem Sprung der Uhr). */
    void aufstellen(Gast g) {
        Motion mo = new Motion(Motion.line(0, 0, 0), 1.0);
        double[] p, b;
        if (g.ort.equals("Plattform")) {
            List<double[]> pl = plaetze();
            int i = freierPlatz(g, pl);
            g.platz = i;
            p = new double[]{pl.get(i)[0], deck[1], pl.get(i)[1]};
            b = new double[]{pl.get(i)[2], pl.get(i)[3]};
        } else {
            int i = g.name.equals("Hanne") ? 0 : 1;
            p = fossStand[Math.min(i, fossStand.length - 1)];
            b = new double[]{fossBlick[0] - p[0], fossBlick[2] - p[2]};
        }
        mo.x = p[0];
        mo.z = p[2];
        mo.yaw = Math.atan2(b[0], b[1]);
        mo.calibrate(skeleton, g.rest, g.person);
        g.motion = mo;
        g.boden = p[1];
        g.warte = 2 + g.rnd.nextDouble() * 6;
        g.geht = false;
        g.tut = "schaut";
        g.behavior.look(Behavior.Look.HORIZON);
        String[] gef = {"freudig", "ruhig", "überrascht", "nachdenklich"};
        g.behavior.feel(gef[g.rnd.nextInt(gef.length)], 0.5 + 0.3 * g.rnd.nextDouble());
    }

    /** Ein Platz am Geländer, an dem (und auf dem Weg zu dem) noch keiner steht. */
    int freierPlatz(Gast g, List<double[]> pl) {
        for (int k = 0; k < 60; k++) {
            int i = g.rnd.nextInt(pl.size());
            if (i == g.platz) continue;
            boolean frei = true;
            for (Gast o2 : gaeste) {
                if (o2 == g || !o2.da || !o2.ort.equals("Plattform")) continue;
                if (o2.platz == i) { frei = false; break; }
                if (o2.motion != null && Math.hypot(o2.motion.x - pl.get(i)[0], o2.motion.z - pl.get(i)[1]) < 1.2) { frei = false; break; }
            }
            if (frei) return i;
        }
        return Math.floorMod((int) g.seed, pl.size());
    }

    /** Was als Nächstes kommt: hingehen, zur Aussicht drehen, schauen, zeigen, winken, fotografieren. */
    void regie(Gast g, double dt) {
        Motion mo = g.motion;
        if (!mo.idle()) return;
        g.warte -= dt;
        if (g.warte > 0) return;
        double r = g.rnd.nextDouble();
        boolean plattform = g.ort.equals("Plattform");
        if (plattform && g.geht) {
            // angekommen: zur Aussicht drehen, eine Weile schauen
            double[] pl = plaetze().get(g.platz);
            mo.then(Motion.turnTo(pl[0] + pl[2] * 50, pl[1] + pl[3] * 50));
            g.geht = false;
            g.tut = "schaut";
            blick(g, aussicht[g.rnd.nextInt(aussicht.length)]);
            g.warte = 6 + g.rnd.nextDouble() * 14;
            return;
        }
        if (plattform && r < 0.35) {
            // weiter zu einem anderen Platz
            List<double[]> pl = plaetze();
            int i = freierPlatz(g, pl);
            g.platz = i;
            mo.then(Motion.goTo(pl.get(i)[0], pl.get(i)[1], 0.65 + 0.2 * g.rnd.nextDouble()));
            g.geht = true;
            g.tut = "geht";
            g.behavior.look(Behavior.Look.FREE);
            g.warte = 0;
            return;
        }
        double[] ziel = plattform ? aussicht[g.rnd.nextInt(aussicht.length)] : fossBlick;
        if (r < 0.6) {
            mo.then(Motion.point(2.5));
            g.tut = "zeigt";
            blick(g, ziel);
        } else if (r < 0.78) {
            mo.then(Motion.bend(2.2));
            g.tut = "fotografiert";
        } else if (r < 0.86) {
            mo.then(Motion.wave(2.0));
            g.tut = "winkt";
            g.behavior.look(Behavior.Look.FREE);
        } else {
            // zur Seite schauen, zu einer anderen Person oder in die Ferne
            Gast nb = null;
            for (Gast o2 : gaeste) if (o2 != g && o2.da && o2.ort.equals(g.ort) && (nb == null || g.rnd.nextBoolean())) nb = o2;
            if (nb != null) blick(g, new double[]{nb.x, nb.boden + 1.6, nb.z});
            else g.behavior.look(Behavior.Look.HORIZON);
            g.tut = "schaut";
        }
        g.warte = 4 + g.rnd.nextDouble() * 10;
    }

    /** Blick auf einen Punkt der Szene (in Figur-Koordinaten an Behavior). */
    void blick(Gast g, double[] p) {
        double dx = p[0] - g.motion.x, dz = p[2] - g.motion.z, c = Math.cos(g.motion.yaw), s = Math.sin(g.motion.yaw);
        g.behavior.lookAt(new double[]{dx * c - dz * s, p[1] - g.boden, dx * s + dz * c});
    }

    /** „Menschen: Plattform 6 (Avatar, bereit) · Storfossen 2 · Busgruppe 27 (Bus steht bis 11:22) · Wanderer 8 · Treppe 5“ */
    public String describe(double epochSec) {
        int[] n = zaehle(epochSec);
        double[] b = bus(epochSec);
        String bus = b == null ? "kein Bus" : b[4] == 0 ? "Bus fährt an" : b[4] == 1
                ? String.format(Locale.GERMAN, "Bus steht bis %s", uhr(b[5] + HALT)) : "Bus fährt ab";
        return String.format(Locale.GERMAN, "Menschen: Plattform %d, Storfossen %d (Avatar, %s) · Busgruppe %d (%s) · Wanderer %d · Treppe %d%s",
                aufPlattform, amFoss, avatarStand, n[0], bus, n[1], n[2], schritte > 0 ? String.format(Locale.GERMAN, " · %.2f ms im Faden", schrittMs) : "");
    }

    static String uhr(double sec) {
        int s = (int) Math.round(sec);
        return String.format(Locale.GERMAN, "%d:%02d", s / 3600, (s / 60) % 60);
    }

    public void dispose() { faden.shutdownNow(); }
}
