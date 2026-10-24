package com.dan.geiranger.scene;

import com.dan.soundscape.Aeolian;
import com.dan.soundscape.AudioOut;
import com.dan.soundscape.Crowd;
import com.dan.soundscape.Hum;
import com.dan.soundscape.Soundscape;
import com.dan.soundscape.Stream;
import com.dan.soundscape.Torrent;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Phase 15: der Klang des Fjords (F20 A) mit {@code com.dan.soundscape}. Ein Ohr an der Kamera; die App setzt je Bild
 * die {@link Lage} (Uhr, Wetter, Fälle, Schiffe, Verkehr, Menschen), diese Klasse stellt daraus die Quellen:
 * <ul>
 * <li><b>Fälle</b> als {@link Torrent}: Pegel aus {@code Fall.soundLevel} (Leistung des fallenden Wassers, weniger
 * unter Eis), abgeschattet hinter Graten ({@link Echo#abschattung}, Maekawa). {@code soundscape} hört einen Torrent
 * nur bis 3 km; weiter entfernte Fälle stehen als Stellvertreter in 2,8 km in derselben Richtung mit dem Pegel, den
 * der Fall am Ohr hat. So werden die Schwestern auf der Fähre lauter und leiser, und Friaren hört man über den
 * Fjord.</li>
 * <li><b>Geirangelva</b> als {@link Stream} alle {@link #ELVA_ABSTAND} m auf den Strecken zwischen den Stufen,
 * Strömung aus dem Abfluss und dem Gefälle.</li>
 * <li><b>Horn</b> ({@link Soundscape.Kind#TYFON}): die Fähre beim Ablegen (lang, 5 s) und an den Schwestern (kurz, 1 s),
 * das Kreuzfahrtschiff beim Auslaufen (6 s) [G]. Vor den Schwestern liegen die Wände beiderseits 400 bis 600 m weg,
 * die Echos kommen nach rund 3 s im Abstand von einer halben Sekunde, erst von der einen, dann von der anderen Seite;
 * darum ist der Ton dort kurz. Mit <b>Echo der Wände</b>: {@link Echo#finde} sucht die Wände, die zum
 * Ohr zurückstreuen; jedes Echo erklingt als Spiegelquelle in der Richtung der Wand. Weil {@code soundscape} den Pegel
 * eines Horns nicht frei dämpft, steht eine schwächere Quelle weiter weg und erklingt entsprechend früher; das ganze
 * Horn kommt dafür bis zu einigen Sekunden später ({@link #verzoegerung}), die Abstände der Echos bleiben richtig.</li>
 * <li><b>Kirchenglocke</b> sonntags von 10:45 bis 10:48 [G], zwei Glocken im Wechsel, mit Echo.</li>
 * <li><b>Maschinen</b> ({@link Hum}): Fähren, Kreuzfahrtschiff, Schnellboot, Fahrzeuge auf der Fv63 (Busse in den
 * Kehren), der Ausflugsbus an der Plattform; an Bord gleichmäßig durch den Rumpf.</li>
 * <li><b>Stimmen</b> ({@link Crowd}): Plattform (Gäste und Busgruppe), Treppe am Storfossen, Hafen.</li>
 * <li><b>Vögel und Tiere</b> nach Ort, Monat und Tageszeit: Silbermöwe über dem Wasser, Austernfischer am Ufer
 * (April bis Juli, auch nachts), Nebelkrähe, Rauchschwalbe im Ort (Mai bis August), Stockente an der Mündung, Kormoran,
 * Hund im Ort, Schafe auf den Hängen (Juni bis September) [G].</li>
 * <li><b>Wetter</b>: Wind und Böen, Regen auf dem Wasser, See nach der Wellenhöhe von {@code ocean}, nur nah am
 * Wasser; Äolstöne am Geländer der Plattform; Wasser an den Pfählen des Anlegers.</li>
 * </ul>
 */
public final class Klang {
    /**
     * Nacharbeit 15 (Auf_der_Fähre.m4a: Rauschen überlagert alles): {@code Fall.soundLevel} setzt ein Millionstel der
     * Fallleistung als Schall an; für die Schwestern und Friaren, deren Strähnen in der Luft zerfallen und als Schleier
     * und Nebel auf Fels und Wasser kommen, ist das zu laut (vor den Schwestern 64 dB, mit Friaren und Brudesløret
     * zusammen ein Rauschen über allem). Der Klang setzt die Fälle um so viel leiser an [G].
     */
    public static final double FALL_DB = -12;
    /**
     * Nacharbeit 15 (Auf_der_Fähre1.m4a: das Rauschen blieb): {@code soundscape} rechnet den Wind am Ohr für eine offene
     * Galerie (22 + 50 lg U dB, bei 3 bis 4 m/s 37 bis 44 dB). Auf dem Deck der Fähre war sonst wenig zu hören, die
     * Anpassung des Ohrs hob das Brausen an, und es lag wie Rauschen über allem. Zwischen den Wänden des Fjords, im
     * Windschatten der Aufbauten und auf dem Land zwischen Bäumen und Häusern weht am Ohr weniger [G]: das Ohr bekommt
     * diesen Anteil des Winds (bei 3,5 m/s rund 30 statt 41 dB).
     */
    public static final double WIND_OHR = 0.6;
    /** Plätze für Fälle, Abschnitte der Elva, Fahrzeuge. */
    public static final int FAELLE = 12, BAECHE = 14, FAHRZEUGE = 6;
    /** Abstand der Abschnitte der Elva (m); Stellvertreter der Fälle (m); Grenze von soundscape für Torrents (m). */
    public static final double ELVA_ABSTAND = 110, STELLVERTRETER = 2800;
    /** Höchstens so viele Echos je Knall. */
    public static final int ECHOS = 4;
    /** Glocke: sonntags ab 10:45 für 3 min, ein Schlag alle 2,1 s [G]. */
    public static final double GLOCKE_AB = 10.75, GLOCKE_DAUER = 180, GLOCKE_TAKT = 2.1;
    /** Horn der Fähre: lang beim Ablegen, kurz an den Schwestern; Kreuzfahrtschiff beim Auslaufen (s). */
    public static final double HORN_LANG = 5, HORN_KURZ = 0.6, HORN_KREUZ = 6;

    /** Was die App je Bild weiß. */
    public static final class Lage {
        /** Szenenzeit (s seit 1970), Bildzeit (s, echt). */
        public double epoch, dt;
        public double[] ohr = new double[3];
        /** Blick (Grad von Nord im Uhrzeigersinn). */
        public double yaw;
        /** Wind (m/s), aus (Grad), Böigkeit, Regen (mm/h, flüssig), Luft (°C), Wellenhöhe und -periode, Sonnenhöhe (°). */
        public double wind, windVon = 270, boee = 0.3, regen, luft = 12, hs, tp = 3, sonne = 30;
        public List<Faelle.Eintrag> faelle = List.of();
        public Elva elva;
        public double elvaQ;
        /** Abfluss je Fall gerade (m³/s, aus {@code wiring.Abfluss}); null: der des gerechneten Falls. */
        public java.util.function.ToDoubleFunction<String> abfluss;
        public Flotte flotte;
        public Leute leute;
        /** Fahrzeuge in der Nähe: {Art (Ordinalzahl von road.Vehicle), x, y, z, Fahrt m/s, Beschleunigung}. */
        public List<double[]> fahrzeuge = List.of();
    }

    public final Soundscape s;
    private final Terrain t;
    private volatile Echo echo;
    private final Torrent[] faelle = new Torrent[FAELLE];
    private final String[] fallName = new String[FAELLE];
    /** Je Platz: Ziel des Pegels am Ohr (dB, alle 0,25 s), geglätteter Pegel (je Bild), Abschattung, Mitte des Falls. */
    private final double[] fallZiel = new double[FAELLE], fallDb = new double[FAELLE], fallSchatten = new double[FAELLE];
    private final double[][] fallPunkt = new double[FAELLE][];
    private final Stream[] baeche = new Stream[BAECHE];
    private final int[] bachIndex = new int[BAECHE];
    private int baecheGesetzt = -1;
    private final Hum[] fahrzeug = new Hum[FAHRZEUGE], schiff = new Hum[4];
    /** Je Platz eines Fahrzeugs: Kennung, letzte Daten, Alter der Daten (s), Ziel- und geglätteter Pegel. */
    private final double[] fzKennung = new double[FAHRZEUGE], fzAlter = new double[FAHRZEUGE], fzZiel = new double[FAHRZEUGE], fzDb = new double[FAHRZEUGE];
    private final double[][] fzDaten = new double[FAHRZEUGE][];
    private List<double[]> fahrzeugListe;
    private final Hum bus;
    private final Crowd plattform, foss, hafen;
    private final SplittableRandom r = new SplittableRandom(15);
    private final ExecutorService rechner = Executors.newSingleThreadExecutor(q -> { Thread th = new Thread(q, "Echo"); th.setDaemon(true); return th; });
    /** Geplante Ereignisse: {Fälligkeit in Ton-Zeit, Art, x, y, z, Dauer oder Ton, Horn 1/0}. */
    private final PriorityQueue<double[]> geplant = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
    private AudioOut out;
    /** Ton an (Bedienfeld). */
    public volatile boolean an = true;
    /** Ob Ereignisse erklingen sollen: Lautsprecher läuft oder ein Werkzeug rechnet den Ton ({@link #offline}). */
    private volatile boolean hoert;
    /** Echos synchron rechnen (Werkzeuge, Prüfung). */
    public volatile boolean synchron;

    // Zustand
    private double letzteEpoch = Double.NaN, wasserNah = 1, takt, nahTakt, welle;
    private final double[] faehreD = {1e9, 1e9};
    private final boolean[] gehupt = new boolean[2];
    private final String[] zustand = {"", "", "", ""};
    private double glockeNaechste = Double.NaN;
    private List<Echo.Treffer> glockeEcho = List.of();
    private double glockeDelta;
    private int glockeSchlag;
    private volatile String letztesHorn = "noch keins", letztesEcho = "";
    private volatile int hoerner, rufe;
    private volatile String vogelZuletzt = "";

    public Klang(Terrain t, Soundscape.Quality q, double[] deck) {
        this.t = t;
        s = Soundscape.calm();
        s.quality = q;
        s.windCarry = true;
        s.autoGain = true;
        s.volume = 1.0;
        s.seed = 63;
        for (int i = 0; i < FAELLE; i++) { faelle[i] = new Torrent("Fall " + (i + 1), 0, -1e5, 0); s.torrents.add(faelle[i]); }
        for (int i = 0; i < BAECHE; i++) { baeche[i] = new Stream("Elva " + (i + 1)); baeche[i].y = -1e5; s.streams.add(baeche[i]); }
        for (int i = 0; i < FAHRZEUGE; i++) { fahrzeug[i] = new Hum("Fahrzeug " + (i + 1), 0, -1e5, 0); fahrzeug[i].levelDb = -100; s.hums.add(fahrzeug[i]); }
        for (int i = 0; i < 4; i++) { schiff[i] = new Hum("Schiff " + (i + 1), 0, -1e5, 0); schiff[i].levelDb = -100; s.hums.add(schiff[i]); }
        bus = new Hum("Ausflugsbus", 0, -1e5, 0);
        bus.levelDb = -100;
        s.hums.add(bus);
        double[] d = deck != null ? deck : new double[]{0, -1e5, 0, 1, 0, 6, 11};
        plattform = new Crowd("Plattform", d[0], d[1] + 1.6, d[2], 0);
        foss = new Crowd("Storfossen", 0, -1e5, 0, 0);
        hafen = new Crowd("Hafen", Dorf.TERMINAL[0], 3, Dorf.TERMINAL[1], 0);
        plattform.radius = 9; foss.radius = 12; hafen.radius = 40;
        s.crowds.add(plattform); s.crowds.add(foss); s.crowds.add(hafen);
        if (deck != null) {
            // Geländer der Plattform: Pfosten 40 mm und Handlauf 50 mm (vorn, quer zur Richtung nach außen)
            double fx = d[0] + d[3] * d[5], fz = d[2] + d[4] * d[5];
            s.bodies.add(new Aeolian("Geländer, Handlauf", fx, d[1] + 1.1, fz, 0.05, 2 * d[6]));
            s.bodies.add(new Aeolian("Geländer, Pfosten", fx, d[1] + 0.55, fz, 0.04, 1.1));
        }
        // Pfähle des Anlegers (alle 4 m an beiden Seiten des Decks)
        for (double x = Dorf.ANLEGER_X1; x <= Dorf.ANLEGER_X0; x += 4) {
            s.pilings.add(new double[]{x, Dorf.ANLEGER_Z - Dorf.ANLEGER_HW});
            s.pilings.add(new double[]{x, Dorf.ANLEGER_Z + Dorf.ANLEGER_HW});
        }
        if (t != null && t.measured()) {
            Thread th = new Thread(() -> echo = new Echo(t), "Echo-Wände");
            th.setDaemon(true);
            th.start();
        }
    }

    /** Wartet, bis die Wandzellen gesammelt sind (Werkzeuge, Prüfung); false ohne Gelände. */
    public boolean echoBereit(long maxMs) {
        long end = System.currentTimeMillis() + maxMs;
        while (echo == null && t != null && t.measured() && System.currentTimeMillis() < end) {
            try { Thread.sleep(20); } catch (InterruptedException e) { return false; }
        }
        return echo != null;
    }

    public Echo echo() { return echo; }

    // ------------------------------------------------------------------ Lautsprecher

    /** Lautsprecher an (eigener Faden von soundscape) oder aus. */
    public synchronized void ton(boolean ein) {
        an = ein;
        if (ein && out == null) {
            out = new AudioOut(s);
            out.start();
            hoert = out.running();
            if (!hoert) System.err.println("Geiranger: kein Ton (" + out.error() + ")");
        } else if (!ein && out != null) {
            out.stop();
            out = null;
            hoert = false;
            synchronized (geplant) { geplant.clear(); }
        }
    }

    /** Werkzeuge und Prüfung: Ereignisse erklingen, der Ton entsteht mit {@code s.render}. */
    public void offline() { hoert = true; synchron = true; }

    public boolean laeuft() { return hoert; }

    public void dispose() {
        ton(false);
        rechner.shutdownNow();
    }

    // ------------------------------------------------------------------ je Bild

    public void update(Lage L) {
        double ex = L.ohr[0], ey = L.ohr[1], ez = L.ohr[2];
        s.earX = ex; s.earY = ey; s.earZ = ez;
        s.earYawDeg = L.yaw;
        s.windSpeed = L.wind * WIND_OHR;
        s.windFromDeg = L.windVon;
        s.gustiness = L.boee;
        s.rainRate = L.regen;
        s.temperatureC = L.luft;
        boolean ueberWasser = t != null && t.fjord(ex, ez);
        double grund = t == null ? 0 : (ueberWasser ? 0 : Math.max(0, t.height(ex, ez)));
        s.waterLevel = grund;
        s.roughness = ueberWasser ? 0.0002 : (Math.hypot(ex - 40, ez - 120) < 600 ? 0.5 : 0.15);
        takt -= L.dt;
        nahTakt -= L.dt;
        if (nahTakt <= 0) { nahTakt = 0.5; wasserNah = wasserNah(ex, ey, ez); }
        welle += (L.hs * wasserNah - welle) * (1 - Math.exp(-Math.max(0, L.dt) / 1.0));
        s.waveHeight = welle;
        s.wavePeriod = Math.max(1, L.tp);
        if (takt <= 0) {
            takt = 0.25;
            faelle(L);
            elva(L);
            menschen(L);
        }
        faelleStellen(L);
        maschinen(L);
        double ep = L.epoch;
        boolean stetig = !Double.isNaN(letzteEpoch) && ep > letzteEpoch && ep - letzteEpoch < 30;
        if (L.flotte != null) hoerner(L, stetig);
        glocke(L, stetig);
        if (hoert && an) tiere(L);
        letzteEpoch = ep;
        // fällige Ereignisse (Horn und Echos, Glocke)
        double now = s.time();
        while (true) {
            double[] e;
            synchronized (geplant) {
                e = geplant.peek();
                if (e == null || e[0] > now) break;
                geplant.poll();
            }
            Soundscape.Kind k = Soundscape.Kind.values()[(int) e[1]];
            if (e[6] > 0) s.horn(k, e[2], e[3], e[4], 1, e[5]);
            else s.event(k, e[2], e[3], e[4], 1, e[5]);
        }
    }

    /** Wie nah das Ohr am Wasser ist (0 weit, 1 am Ufer oder darauf): Abstand zur nächsten Fjordzelle und Höhe. */
    double wasserNah(double ex, double ey, double ez) {
        if (t == null) return 1;
        if (t.fjord(ex, ez)) return Math.max(0, Math.min(1, 1 - (ey - 15) / 300));
        double best = 1e9;
        for (double rr = 12; rr <= 600 && best > 1e8; rr += 12)
            for (int k = 0; k < 24; k++) {
                double a = k * Math.PI / 12;
                if (t.fjord(ex + rr * Math.cos(a), ez + rr * Math.sin(a))) { best = rr; break; }
            }
        double d = Math.hypot(best, Math.max(0, ey));
        double f = Math.max(0, Math.min(1, 1 - (d - 15) / 300));
        return f * Math.sqrt(f);
    }

    // ------------------------------------------------------------------ Fälle und Elva

    void faelle(Lage L) {
        double ex = L.ohr[0], ey = L.ohr[1], ez = L.ohr[2];
        List<double[]> kand = new ArrayList<>();   // {Pegel am Ohr, x, y, z, Abstand, Abschattung, Index}
        List<Faelle.Eintrag> es = L.faelle;
        for (int i = 0; i < es.size(); i++) {
            Faelle.Eintrag e = es.get(i);
            com.dan.fall.Fall f = e.fall;
            if (f == null || f.discharge < 0.003) continue;
            // Nacharbeit 15: der Abfluss gerade (aus der Regie), nicht der, mit dem der Fall zuletzt gerechnet wurde; die
            // App rechnet trockene Fälle nicht neu, die Regenfälle klangen sonst auch ohne Regen mit ihrem Juniwert
            double q = L.abfluss == null ? f.discharge : L.abfluss.applyAsDouble(e.def.name());
            if (q < 0.003) continue;
            double[] m = e.mitte();
            double d = Math.max(1, Math.sqrt(sq(m[0] - ex) + sq(m[1] - ey) + sq(m[2] - ez)));
            double l1 = f.soundLevel(1) + 10 * Math.log10(q / f.discharge) + FALL_DB;
            // abgeschattet: Kante, Mitte und Fuß des Falls getrennt (ein Fall ist 100 bis 400 m hoch), Energie gemittelt
            Echo ec = echo;
            double a = 0;
            if (ec != null) {
                double[][] pk = punkte(e, m);
                double sum = 0;
                for (double[] pp : pk) sum += Math.pow(10, -ec.abschattung(pp[0], pp[1], pp[2], ex, ey, ez, 500) / 10);
                a = -10 * Math.log10(sum / pk.length);
            }
            double le = l1 - 20 * Math.log10(d) - a;
            kand.add(new double[]{le, m[0], m[1], m[2], d, a, i});
        }
        kand.sort((p, q) -> Double.compare(q[0], p[0]));
        // Nacharbeit 15: jeder Fall behält seinen Platz (vorher je Rang: tauschten zwei Fälle die Reihenfolge, sprang ein
        // Torrent von einem Fall zum anderen, und in Fahrt knackte und rauschte es)
        boolean[] gesehen = new boolean[FAELLE];
        for (double[] c : kand) {
            String name = es.get((int) c[6]).def.name();
            int k = -1;
            for (int q = 0; q < FAELLE; q++) if (name.equals(fallName[q])) k = q;
            if (k < 0) {
                if (c[0] < 5) continue;
                for (int q = 0; q < FAELLE && k < 0; q++) if (fallName[q] == null) k = q;
                if (k < 0) {
                    int leise = -1;
                    for (int q = 0; q < FAELLE; q++) if (!gesehen[q] && (leise < 0 || fallZiel[q] < fallZiel[leise])) leise = q;
                    if (leise < 0 || fallZiel[leise] >= c[0]) continue;
                    k = leise;
                }
                fallName[k] = name;
                fallDb[k] = c[0] - 20;   // weich einblenden
            }
            gesehen[k] = true;
            fallZiel[k] = c[0];
            fallSchatten[k] = c[5];
            fallPunkt[k] = new double[]{c[1], c[2], c[3]};
        }
        for (int q = 0; q < FAELLE; q++) if (!gesehen[q] && fallName[q] != null) fallZiel[q] = -40;   // ausblenden
    }

    /**
     * Je Bild: die Torrents an die Fälle stellen, vom Ohr aus gesehen (Stellvertreter höchstens 2,8 km weg), Pegel weich
     * zum Ziel (0,35 s). So wandern Ort und Pegel stetig, auch wenn das Ohr auf der Fähre fährt.
     */
    void faelleStellen(Lage L) {
        double ex = L.ohr[0], ey = L.ohr[1], ez = L.ohr[2], a = 1 - Math.exp(-Math.max(0, L.dt) / 0.35);
        for (int k = 0; k < FAELLE; k++) {
            Torrent tr = faelle[k];
            if (fallName[k] == null || fallPunkt[k] == null) { tr.flow = 0; tr.y = -1e5; continue; }
            fallDb[k] += (fallZiel[k] - fallDb[k]) * a;
            if (fallZiel[k] <= -40 && fallDb[k] < 0) { fallName[k] = null; tr.flow = 0; tr.y = -1e5; continue; }
            double[] m = fallPunkt[k];
            double d = Math.max(1, Math.sqrt(sq(m[0] - ex) + sq(m[1] - ey) + sq(m[2] - ez))), dp = Math.min(d, STELLVERTRETER), u = dp / d;
            tr.x = ex + (m[0] - ex) * u; tr.y = ey + (m[1] - ey) * u; tr.z = ez + (m[2] - ez) * u;
            double l1p = fallDb[k] + 20 * Math.log10(dp);
            tr.flow = fallDb[k] < 5 ? 0 : Math.pow(10, (l1p - 70) / 12);
        }
    }

    /** Kante, Mitte und Fuß eines Falls (Aufprall der mittleren Strähne). */
    static double[][] punkte(Faelle.Eintrag e, double[] m) {
        com.dan.fall.Fall f = e.fall;
        double[] fuss = m;
        if (!f.strands.isEmpty()) { com.dan.fall.Fall.Strand st = f.strands.get(f.strands.size() / 2); fuss = new double[]{st.ix, st.iy + 3, st.iz}; }
        return new double[][]{{e.kante.x(), e.kante.y() + 3, e.kante.z()}, m, fuss};
    }

    /** Pegel eines Falls am Ohr zuletzt (dB) und seine Abschattung (dB), NaN wenn nicht hörbar. */
    public double[] fallPegel(String name) {
        for (int k = 0; k < FAELLE; k++) if (name.equals(fallName[k]) && fallZiel[k] > -40) return new double[]{fallZiel[k], fallSchatten[k]};
        return new double[]{Double.NaN, Double.NaN};
    }

    void elva(Lage L) {
        Elva e = L.elva;
        if (e == null) return;
        if (baecheGesetzt < 0) {
            // Abschnitte auf den Strecken (ohne die Stufen, die sind Fälle) alle ELVA_ABSTAND m
            int k = 0;
            double naechste = 0;
            for (int i = 0; i < e.n && k < BAECHE; i++) {
                if (e.stufe(i) != null) continue;
                if (e.l[i] < naechste) continue;
                baeche[k].x = e.x[i]; baeche[k].z = e.z[i]; baeche[k].y = e.level[i] + 0.3;
                baeche[k].width = Math.max(4, 2 * e.half[i]);
                bachIndex[k] = i;
                k++;
                naechste = e.l[i] + ELVA_ABSTAND;
            }
            baecheGesetzt = k;
        }
        double q = Math.max(0.2, L.elvaQ);
        for (int k = 0; k < baecheGesetzt; k++) {
            int i = bachIndex[k];
            double v = q / (Math.max(4, 2 * e.half[i]) * Elva.TIEFE);
            baeche[k].speed = Math.min(5, Math.max(0.2, v * (1 + 8 * Math.max(0, e.grade[i]))));
        }
    }

    // ------------------------------------------------------------------ Maschinen, Menschen

    void maschinen(Lage L) {
        double ex = L.ohr[0], ey = L.ohr[1], ez = L.ohr[2];
        if (L.flotte != null) {
            List<Flotte.Schiff> ss = L.flotte.schiffe;
            for (int i = 0; i < 4 && i < ss.size(); i++) {
                Flotte.Schiff sch = ss.get(i);
                Hum h = schiff[i];
                if (!sch.da || sch.art == Flotte.Art.KAJAK) { h.levelDb = -100; h.y = -1e5; h.aboard = false; continue; }
                // Nacharbeit 15: die Maschine der Fähre klingt aus dem Schornstein (achtern, über dem Sonnendeck); wer
                // auf einem offenen Deck steht, hört sie dort, nur unter Deck gleichmäßig durch den Rumpf
                double lz = sch.art == Flotte.Art.FAEHRE ? 0.55 * sch.hl - 6 : 0, ly = sch.art == Flotte.Art.FAEHRE ? sch.freibord + 10.5 : 4;
                double[] w = anBord(sch, 0, ly, lz);
                h.x = w[0]; h.y = w[1]; h.z = w[2];
                boolean faehrt = sch.fahrt > 0.5, manoever = sch.zustand.startsWith("legt") || sch.zustand.startsWith("dreht");
                switch (sch.art) {
                    // Nacharbeit 15 (Auf_der_Fähre1.m4a): das Brummen der Maschine ist breitbandig (60 Hz bis 1 kHz) und war auf
                    // dem Deck das Rauschen über allem (58 dB am Ohr); moderne Fähren sind auf dem Sonnendeck leise [G]
                    case FAEHRE -> { h.firingHz = Hum.firing(750, 8); h.levelDb = manoever ? 78 : faehrt ? 71 : 60; h.aboardDb = 56; }
                    case KREUZFAHRT -> { h.firingHz = Hum.firing(720, 12); h.levelDb = faehrt || manoever ? 98 : 88; h.aboardDb = 56; }
                    case SCHNELLBOOT -> { h.firingHz = 5000 / 60.0 * 3; h.levelDb = faehrt ? 96 : sch.zustand.startsWith("liegt") ? -100 : 70; h.aboardDb = 78; }   // am Anleger aus
                    default -> h.levelDb = -100;
                }
                double k = Math.toRadians(sch.kurs), fx = Math.sin(k), fz = -Math.cos(k), dx = ex - sch.x, dz = ez - sch.z;
                double u = dx * fx + dz * fz, v = -dx * fz + dz * fx;
                boolean anBord = Math.abs(u) < sch.hl && Math.abs(v) < sch.hw && ey < sch.y + sch.freibord + 14 && ey > sch.y - 1;
                boolean offen = ey > sch.y + sch.freibord + (sch.art == Flotte.Art.FAEHRE ? 6.5 : 0.5);
                h.aboard = anBord && !offen;
            }
        }
        // Fahrzeuge auf der Fv63: die nächsten mit Motor. Nacharbeit 15: jedes Fahrzeug behält seinen Platz (Kennung),
        // fährt zwischen zwei Listen (alle 0,25 s) mit seiner Fahrt weiter, Pegel weich
        List<double[]> fz = L.fahrzeuge;
        if (fz != fahrzeugListe) {
            fahrzeugListe = fz;
            boolean[] belegt = new boolean[FAHRZEUGE];
            List<double[]> neu = new ArrayList<>();
            for (double[] f : fz) {
                if (f.length < 10 || f[0] == 7 || f[0] == 9) continue;
                int k = -1;
                for (int q = 0; q < FAHRZEUGE; q++) if (fzKennung[q] == f[7] && fzDaten[q] != null) k = q;
                if (k >= 0) { fzDaten[k] = f; fzAlter[k] = 0; belegt[k] = true; fzZiel[k] = 0; }
                else neu.add(f);
            }
            for (double[] f : neu) {
                int k = -1;
                for (int q = 0; q < FAHRZEUGE && k < 0; q++) if (!belegt[q] && (fzDaten[q] == null || fzZiel[q] < -50)) k = q;
                if (k < 0) break;
                fzKennung[k] = f[7]; fzDaten[k] = f; fzAlter[k] = 0; belegt[k] = true; fzDb[k] = -30; fzZiel[k] = 0;
            }
            for (int q = 0; q < FAHRZEUGE; q++) if (!belegt[q]) fzZiel[q] = -100;
        }
        double gl = 1 - Math.exp(-Math.max(0, L.dt) / 0.3);
        for (int q = 0; q < FAHRZEUGE; q++) {
            Hum h = fahrzeug[q];
            double[] f = fzDaten[q];
            if (f == null) { h.levelDb = -100; h.y = -1e5; continue; }
            int art = (int) f[0];
            double v = f[4], a = f[5];
            double rpm, zyl, db;
            switch (art) {
                case 0, 1, 2 -> { rpm = 1100 + 900 * Math.min(1, v / 15) + (a > 0.3 ? 600 : 0); zyl = 4; db = 70 + 8 * Math.min(1, v / 15) + (a > 0.3 ? 4 : 0); }
                case 3 -> { rpm = 1300 + 700 * Math.min(1, v / 15); zyl = 4; db = 76 + 6 * Math.min(1, v / 15); }
                case 4, 5 -> { rpm = 800 + 800 * Math.min(1, v / 12); zyl = 6; db = 80 + 6 * Math.min(1, v / 12) + (a > 0.2 ? 3 : 0); }
                case 6 -> { rpm = 2500 + 3000 * Math.min(1, v / 20); zyl = 2; db = 82 + 8 * Math.min(1, v / 20); }
                case 8 -> { rpm = 1500; zyl = 3; db = 84; }
                default -> { rpm = 1000; zyl = 4; db = -100; }
            }
            if (fzZiel[q] > -100) fzZiel[q] = db;
            fzDb[q] += (fzZiel[q] - fzDb[q]) * gl;
            if (fzZiel[q] <= -100 && fzDb[q] < 0) { fzDaten[q] = null; h.levelDb = -100; h.y = -1e5; continue; }
            fzAlter[q] += L.dt;
            double w = v * Math.min(1, fzAlter[q]);
            h.x = f[1] + f[8] * w; h.y = f[2] + 0.8; h.z = f[3] + f[9] * w;
            h.firingHz = Hum.firing(rpm, (int) zyl);
            h.levelDb = fzDb[q];
            h.aboard = false;
        }
        // der Ausflugsbus an der Plattform
        double[] b = L.leute == null ? null : L.leute.bus(L.epoch);
        if (b == null) { bus.levelDb = -100; bus.y = -1e5; }
        else {
            bus.x = b[0]; bus.y = b[1] + 1; bus.z = b[2];
            boolean steht = b[4] == 1;
            bus.firingHz = Hum.firing(steht ? 650 : 1400, 6);
            bus.levelDb = steht ? 72 : 84;
        }
    }

    /**
     * Ein Punkt an Bord (lx rechts, ly oben, lz nach hinten, wie das Netz) in der Welt, aus Lage und Kurs des Schiffs
     * (ohne Stampfen und Rollen; für den Klang genügt das, und es gilt auch ohne Schwimmkörper).
     */
    public static double[] anBord(Flotte.Schiff s, double lx, double ly, double lz) {
        double k = Math.toRadians(s.kurs), fx = Math.sin(k), fz = -Math.cos(k);   // voraus
        double rx = -fz, rz = fx;                                                     // rechts (Steuerbord)
        return new double[]{s.x + rx * lx - fx * lz, s.y + ly, s.z + rz * lx - fz * lz};
    }

    void menschen(Lage L) {
        Leute le = L.leute;
        ZonedDateTime z = lokal(L.epoch);
        int m = z.getMonthValue();
        double h = z.getHour() + z.getMinute() / 60.0;
        if (le != null) {
            int[] n = le.zaehle(L.epoch);
            double[] b = le.bus(L.epoch);
            plattform.people = le.aufPlattform + (b != null && b[4] == 1 ? n[0] : 0);
            if (le.fossStand != null) {
                double[] f = le.fossStand[0];
                foss.x = f[0]; foss.y = f[1] + 1.6; foss.z = f[2];
                foss.people = le.amFoss + n[2];
            }
        }
        boolean sommer = m >= 6 && m <= 8, rand = m == 5 || m == 9;
        boolean tag = h >= 9 && h <= 19;
        boolean kreuz = false;
        if (L.flotte != null && L.flotte.schiffe.size() > 2) kreuz = L.flotte.schiffe.get(2).da;
        hafen.people = !tag ? 2 : sommer ? 40 + (kreuz ? 80 : 0) : rand ? 15 + (kreuz ? 40 : 0) : 3;
    }

    // ------------------------------------------------------------------ Horn und Glocke

    void hoerner(Lage L, boolean stetig) {
        List<Flotte.Schiff> ss = L.flotte.schiffe;
        for (int i = 0; i < 3 && i < ss.size(); i++) {
            Flotte.Schiff sch = ss.get(i);
            String alt = zustand[i], neu = sch.da ? sch.zustand : "";
            zustand[i] = neu;
            if (!stetig) continue;
            if (sch.art == Flotte.Art.FAEHRE) {
                if (neu.startsWith("legt ab") && !alt.startsWith("legt ab")) horn(sch, HORN_LANG, "beim Ablegen", L);
                double d = sch.da ? Math.hypot(sch.x - Flotte.SCHWESTERN_PASS[0], sch.z - Flotte.SCHWESTERN_PASS[1]) : 1e9;
                if (faehreD[i] < 600 && d > faehreD[i] && !gehupt[i]) { horn(sch, HORN_KURZ, "an den Sieben Schwestern", L); gehupt[i] = true; }
                if (d > 1500) gehupt[i] = false;
                faehreD[i] = d;
            } else if (sch.art == Flotte.Art.KREUZFAHRT) {
                if (neu.startsWith("dreht") && !alt.startsWith("dreht")) horn(sch, HORN_KREUZ, "beim Auslaufen", L);
            }
        }
    }

    void horn(Flotte.Schiff sch, double dauer, String wo, Lage L) {
        hoerner++;
        letztesHorn = String.format(Locale.GERMAN, "%s %s, %s (%.1f s)", sch.name, wo, uhr(L.epoch), dauer);
        knall(Soundscape.Kind.TYFON, sch.x, sch.y + sch.freibord + 10, sch.z, dauer, true, L.ohr.clone(), 220, null);
    }

    /**
     * Ein Knall mit Echo: direkt (hinter einem Grat gedämpft) und je Echo eine Spiegelquelle. Gedämpft wird über den
     * Abstand: eine Quelle, die um g dB leiser sein soll, steht um den Faktor 10^(−g/20) weiter weg und erklingt um
     * die Mehrlaufzeit früher; alles zusammen kommt um die größte Mehrlaufzeit später ({@link #verzoegerung}).
     */
    void knall(Soundscape.Kind k, double x, double y, double z, double dauerOderTon, boolean istHorn, double[] e, double f, List<Echo.Treffer> fest) {
        if (!hoert || !an) return;
        Runnable job = () -> {
            Echo ec = echo;
            List<Echo.Treffer> l = fest != null ? fest : ec == null ? List.of() : ec.finde(x, y, z, e[0], e[1], e[2], f, ECHOS);
            double schatten = ec == null ? 0 : ec.abschattung(x, y, z, e[0], e[1], e[2], f);
            if (istHorn) letztesEcho = Echo.describe(l, e[0], e[2]);
            plane(k, x, y, z, dauerOderTon, istHorn, e, l, schatten, Double.NaN);
        };
        if (synchron) job.run();
        else rechner.submit(job);
    }

    /** Plant Direktschall und Echos; delta: feste Verzögerung (NaN: aus den Echos). Gibt die Verzögerung zurück. */
    double plane(Soundscape.Kind k, double x, double y, double z, double dot, boolean istHorn, double[] e, List<Echo.Treffer> l, double schatten, double delta) {
        double c = s.soundSpeed();
        List<double[]> q = new ArrayList<>();   // {x, y, z, Mehrweg}
        double d0 = Math.max(1, Math.sqrt(sq(x - e[0]) + sq(y - e[1]) + sq(z - e[2])));
        q.add(weiter(e, x, y, z, d0, -schatten));
        for (Echo.Treffer t : l) q.add(weiter(e, t.x(), t.y(), t.z(), t.d1() + t.d2(), Math.min(0, t.gainDb())));
        double mehr = 0;
        for (double[] p : q) mehr = Math.max(mehr, p[3]);
        double dl = Double.isNaN(delta) ? mehr / c : Math.max(delta, mehr / c);
        double now = s.time();
        synchronized (geplant) {
            for (double[] p : q) geplant.add(new double[]{now + dl - p[3] / c, k.ordinal(), p[0], p[1], p[2], dot, istHorn ? 1 : 0});
        }
        verzoegerung = dl;
        return dl;
    }

    /** Zuletzt: um wie viel der Knall später erklingt, damit die Echos leise genug sind (s). */
    public volatile double verzoegerung;

    /** Quelle in Richtung (x, y, z) vom Ohr, Weg d um g dB (≤ 0) gedämpft: {x, y, z, Mehrweg}. */
    static double[] weiter(double[] e, double x, double y, double z, double d, double g) {
        double ux = x - e[0], uy = y - e[1], uz = z - e[2], ul = Math.max(1e-6, Math.sqrt(ux * ux + uy * uy + uz * uz));
        double dd = Math.min(12_000, d * Math.pow(10, -g / 20));
        return new double[]{e[0] + ux / ul * dd, e[1] + uy / ul * dd, e[2] + uz / ul * dd, dd - d};
    }

    void glocke(Lage L, boolean stetig) {
        ZonedDateTime z = lokal(L.epoch);
        double sek = z.toLocalTime().toSecondOfDay(), ab = GLOCKE_AB * 3600;
        boolean zeit = z.getDayOfWeek() == DayOfWeek.SUNDAY && sek >= ab && sek < ab + GLOCKE_DAUER;
        if (!zeit || !stetig || !hoert || !an) { glockeNaechste = Double.NaN; return; }
        double gx = Dorf.KIRCHE[0], gz = Dorf.KIRCHE[1], gy = (t == null ? 30 : t.height(gx, gz)) + 14;
        double now = s.time();
        if (Double.isNaN(glockeNaechste)) {
            // Echos einmal für die Reihe (das Ohr bewegt sich in 3 min kaum)
            Echo ec = echo;
            glockeEcho = ec == null ? List.of() : ec.finde(gx, gy, gz, L.ohr[0], L.ohr[1], L.ohr[2], 400, ECHOS);
            glockeDelta = Double.NaN;
            glockeNaechste = now;
            glockeSchlag = 0;
        }
        if (now >= glockeNaechste) {
            double sch = echo == null ? 0 : echo.abschattung(gx, gy, gz, L.ohr[0], L.ohr[1], L.ohr[2], 400);
            double dl = plane(Soundscape.Kind.GLOCKE, gx, gy, gz, glockeSchlag % 2, false, L.ohr.clone(), glockeEcho, sch, glockeDelta);
            if (Double.isNaN(glockeDelta)) glockeDelta = dl;
            glockeSchlag++;
            glockeNaechste = now + GLOCKE_TAKT * (glockeSchlag % 2 == 0 ? 1.15 : 0.85);
        }
    }

    // ------------------------------------------------------------------ Tiere

    /** Rufe je Minute (echte Zeit) für Art und Lage [G]. */
    public static double rate(Soundscape.Kind k, int monat, double sonne, boolean wasser, boolean ort, double hoehe, boolean schiffNah, double regen) {
        boolean tag = sonne > -4, morgen = sonne > -4 && sonne < 10;
        double f = (morgen ? 1.8 : 1) * (regen > 1 ? 0.5 : 1) * (monat <= 2 || monat >= 11 ? 0.5 : 1);
        double r = switch (k) {
            case MOEWE -> wasser && hoehe < 150 ? (monat >= 4 && monat <= 9 ? 1.2 : 0.5) * (schiffNah ? 1.5 : 1) * (tag ? 1 : 0.2) : 0;
            case AUSTERNFISCHER -> wasser && hoehe < 80 && monat >= 4 && monat <= 7 ? (tag ? 0.5 : 0.25) : 0;
            case KRAEHE -> tag && hoehe < 500 && (ort || !wasser) ? 0.6 : 0;
            case SCHWALBE -> tag && ort && (monat >= 6 && monat <= 8 || monat == 5) ? 1.0 : 0;
            case ENTE, ERPEL -> tag && ort && wasser && monat >= 4 && monat <= 10 ? 0.12 : 0;
            case KORMORAN -> tag && wasser && hoehe < 100 ? 0.05 : 0;
            case HUND -> tag && ort ? 0.12 : 0;
            case SCHAF -> tag && hoehe > 250 && hoehe < 1300 && monat >= 6 && monat <= 9 ? 0.25 : 0;
            default -> 0;
        };
        // die Krähe ruft auch im Winter (sie bleibt), die übrigen halb so oft
        return r * (k == Soundscape.Kind.KRAEHE ? (morgen ? 1.8 : 1) * (regen > 1 ? 0.5 : 1) : f);
    }

    static final Soundscape.Kind[] TIERE = {Soundscape.Kind.MOEWE, Soundscape.Kind.AUSTERNFISCHER, Soundscape.Kind.KRAEHE, Soundscape.Kind.SCHWALBE,
            Soundscape.Kind.ENTE, Soundscape.Kind.ERPEL, Soundscape.Kind.KORMORAN, Soundscape.Kind.HUND, Soundscape.Kind.SCHAF};

    void tiere(Lage L) {
        double ex = L.ohr[0], ey = L.ohr[1], ez = L.ohr[2];
        int monat = lokal(L.epoch).getMonthValue();
        boolean wasser = wasserNah > 0.3, ort = Math.hypot(ex - 40, ez - 120) < 700 && ey < 150;
        boolean schiffNah = false;
        if (L.flotte != null) for (Flotte.Schiff sch : L.flotte.schiffe) if (sch.da && sch.art != Flotte.Art.KAJAK && Math.hypot(sch.x - ex, sch.z - ez) < 400) schiffNah = true;
        double hoehe = ey;
        for (Soundscape.Kind k : TIERE) {
            double rr = rate(k, monat, L.sonne, wasser, ort, hoehe, schiffNah, L.regen);
            if (rr <= 0 || r.nextDouble() > rr / 60 * L.dt) continue;
            double dist = 25 + r.nextDouble() * (k == Soundscape.Kind.HUND || k == Soundscape.Kind.SCHAF ? 350 : 180), a = r.nextDouble() * 2 * Math.PI;
            double x = ex + dist * Math.sin(a), z = ez - dist * Math.cos(a);
            double g = t == null ? 0 : Math.max(0, t.height(x, z));
            boolean flieger = k == Soundscape.Kind.MOEWE || k == Soundscape.Kind.SCHWALBE || k == Soundscape.Kind.KORMORAN || k == Soundscape.Kind.KRAEHE;
            double y = g + (flieger ? 6 + r.nextDouble() * 25 : 0.6);
            s.event(k, x, y, z, 0.4 + 0.6 * r.nextDouble(), 0);
            rufe++;
            vogelZuletzt = k.name();
        }
    }

    // ------------------------------------------------------------------ Beschreibung

    static ZonedDateTime lokal(double ep) { return Instant.ofEpochMilli((long) (ep * 1000)).atZone(SceneClock.ZONE); }

    static String uhr(double ep) { ZonedDateTime z = lokal(ep); return String.format(Locale.GERMAN, "%d:%02d", z.getHour(), z.getMinute()); }

    static double sq(double v) { return v * v; }

    /** „Klang: an · am Ohr 61 dB (See 55, Wind 38, …) · Sieben Schwestern 58 dB · Horn: … · Echo: … · 18 ms je s Ton“ */
    public String describe() {
        StringBuilder sb = new StringBuilder("Klang (soundscape, ");
        sb.append(s.quality).append(String.format(Locale.GERMAN, " %d kHz): ", s.sampleRate() / 1000));
        sb.append(!an ? "aus" : hoert ? "an" : "an, ohne Lautsprecher");
        double[] lv = s.levels();
        if (lv != null && lv.length == Soundscape.PARTS.length) {
            sb.append(String.format(Locale.GERMAN, " · am Ohr %.0f dB (", lv[lv.length - 1]));
            List<String> p = new ArrayList<>();
            for (int i = 0; i < lv.length - 1; i++) if (lv[i] > 20) p.add(String.format(Locale.GERMAN, "%s %.0f", Soundscape.PARTS[i], lv[i]));
            sb.append(p.isEmpty() ? "still" : String.join(", ", p)).append(")");
        }
        List<String> f = new ArrayList<>();
        Integer[] ord = new Integer[FAELLE];
        for (int k = 0; k < FAELLE; k++) ord[k] = k;
        java.util.Arrays.sort(ord, (a, b) -> Double.compare(fallZiel[b], fallZiel[a]));
        for (int k : ord)
            if (f.size() < 3 && fallName[k] != null && fallZiel[k] > 5) f.add(String.format(Locale.GERMAN, "%s %.0f dB%s", fallName[k], fallZiel[k], fallSchatten[k] > 1 ? String.format(Locale.GERMAN, " (Grat −%.0f)", fallSchatten[k]) : ""));
        sb.append(" · Fälle: ").append(f.isEmpty() ? "keiner hörbar" : String.join(", ", f));
        sb.append(" · Horn: ").append(letztesHorn).append(hoerner > 0 ? " · " + letztesEcho : "");
        sb.append(String.format(Locale.GERMAN, " · %d Rufe%s · %.0f ms je s Ton", rufe, vogelZuletzt.isEmpty() ? "" : " (zuletzt " + vogelZuletzt.toLowerCase(Locale.GERMAN) + ")", s.msPerSecond()));
        return sb.toString();
    }
}
