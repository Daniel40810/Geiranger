package com.dan.geiranger.scene;

import com.dan.buoyancy.Floater;
import com.dan.buoyancy.Sea;

import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Phase 12: die Schiffe auf dem Fjord (F17 A) [G, wo nicht anders genannt].
 * <ul>
 * <li><b>Die Fähre Hellesylt–Geiranger</b> (Fjord1, MF Veøy und MF Bolsøy): Fahrplan Sommer 2025 (16. Mai bis
 * 14. September, Norway's Best), in beiden Richtungen ab 8:30, 10:00, 11:40, 13:20, 15:00, 16:30 und 18:00, Fahrzeit
 * 1 h 05 min. Zwei Fähren begegnen sich auf halbem Weg; die eine liegt über Nacht in Geiranger. Der Weg
 * ({@code faehre_route.txt}) läuft in der Mitte des Fjords und 260 m vor den Sieben Schwestern vorbei, wo die Fähre für
 * die Gäste auf 3 m/s langsamer wird; im Fahrwasser vor dem Ort gelten 8 Knoten, im Hafen 5 Knoten (Kystverket,
 * Ankeranweisung 2026). Die Reisegeschwindigkeit folgt aus der Fahrzeit. Fjord1 nennt die Linie 2026 „bis auf
 * Weiteres eingestellt“; die Szene fährt sie nach dem Fahrplan von 2025. Maße [G]: 70 × 14 m, 3 m Tiefgang.</li>
 * <li><b>Ein Kreuzfahrtschiff</b> vor Anker (1. Mai bis 30. September täglich [G]): 250 × 32 m (größte Länge auf
 * Position 2), 8 m Tiefgang; kommt von 6:40 bis 8:00 herein, liegt bis 17:30 und fährt wieder hinaus. Ankerplatz: die
 * Ankeranweisung nennt N 62° 06,105′, O 7° 12,156′; dort liegt im Gelände der Weg der Fähre, und der Tenderanleger soll
 * 400 bis 1 000 m entfernt sein. Darum liegt das Schiff 450 m westlich des Anlegers im Becken vor dem Delta
 * ({@link #ANKER}), mit 145 m Wasser zu jeder Seite.</li>
 * <li><b>Ein Schnellboot</b> (RIB, 9 m) fährt von 10 bis 16 Uhr zu jeder geraden Stunde mit 12 m/s zu den Sieben
 * Schwestern, kreist dort zweimal und kommt zurück.</li>
 * <li><b>Sechs Kajaks</b> paddeln von 9 bis 20 Uhr auf einer Runde im Becken vor dem Ort, 200 m neben dem Weg der
 * Fähre und dem Ankerplatz (1,1 m/s).</li>
 * </ul>
 * Alles fährt nach der Uhr: Lage, Kurs und Fahrt sind eine Funktion der Zeit ({@link #stelle}), damit das Kielwasser
 * ({@code gl.Kielwasser}) seine Vorgeschichte nachrechnen kann. Tauchen, Stampfen und Rollen rechnet
 * {@code com.dan.buoyancy} ({@link Floater}, Säulen über die Wasserlinie) auf dem Wasser von {@code ocean}.
 * <p>
 * Die Netze stehen im Format der Bauten (x nach Steuerbord, y nach oben, z nach achtern; der Bug liegt bei −z), wie
 * {@link Floater#model}; Fenster und Lampen wie im Ort ({@link Dorf}).
 */
public final class Flotte {
    public enum Art { FAEHRE, KREUZFAHRT, SCHNELLBOOT, KAJAK }

    /** Fahrplan der Fähre: Abfahrten (Stunden) in beiden Richtungen, Fahrzeit (s), Saison. */
    public static final double[] ABFAHRTEN_CODE = {8.5, 10.0, 11 + 40 / 60.0, 13 + 20 / 60.0, 15.0, 16.5, 18.0};
    public static final double FAHRZEIT_CODE = 65 * 60;
    public static final MonthDay SAISON_AB_CODE = MonthDay.of(5, 16), SAISON_BIS_CODE = MonthDay.of(9, 14);
    /** Phase 17 (F26 A, F31 A): was gilt; {@code data.Laden} setzt es beim Start aus GR_FAHRPLAN (vor dem Bau der Flotte), sonst der Code. */
    public static volatile double[] ABFAHRTEN = ABFAHRTEN_CODE.clone();
    public static volatile double FAHRZEIT = FAHRZEIT_CODE;
    public static volatile MonthDay SAISON_AB = SAISON_AB_CODE, SAISON_BIS = SAISON_BIS_CODE;
    /** Vom Rand des Rasters nach Hellesylt (1,3 km) und Ablegen dort: so lange vor dem Rand bzw. nach ihm (s). */
    public static final double AUSSEN = 300;
    /** Ankerplatz des Kreuzfahrtschiffs (Szene x, z) und Kurs vor Anker (Bug nach Süden, wie es hereinkommt). */
    public static final double[] ANKER = {-440, 40};
    public static final double ANKER_KURS = 180;
    /** Grenzen der Fahrt: vor den Schwestern, Fahrwasser vor dem Ort (8 kn), Hafen (5 kn) in m/s. */
    public static final double SCHWESTERN_V = 3.0, ACHT_KNOTEN = 4.1, FUENF_KNOTEN = 2.6;

    /** Ein Schiff: Maße, Netz, Schwimmkörper und die Lage nach der Uhr. */
    public static final class Schiff {
        public final Art art;
        public final String name;
        /** Halbe Länge und Breite in der Wasserlinie, Tiefgang, Freibord (m). */
        public final double hl, hw, tiefgang, freibord;
        /** Delle, die der Rumpf ins Wasser drückt (m), und Länge von Bug und Heck dafür (m): Eingang des Kielwassers. */
        public final double delle, bug;
        public final float[] netz;
        public final Floater floater;
        /** Lage nach der Uhr: x, z, Kurs (Grad), Fahrt (m/s); da = in der Szene. */
        public volatile double x, z, kurs, fahrt;
        public volatile boolean da;
        public String zustand = "";
        boolean warDa;

        Schiff(Art art, String name, double hl, double hw, double tiefgang, double freibord, double delle, double bug, float[] netz, Floater f) {
            this.art = art; this.name = name; this.hl = hl; this.hw = hw; this.tiefgang = tiefgang; this.freibord = freibord;
            this.delle = delle; this.bug = bug; this.netz = netz; this.floater = f;
        }

        /** Modellmatrix (spaltenweise) aus dem Schwimmkörper oder aus Lage und Kurs. */
        public double[] model() {
            if (floater != null) return floater.model();
            double h = Math.toRadians(-kurs), c = Math.cos(h), s = Math.sin(h);
            return new double[]{c, 0, -s, 0, 0, 1, 0, 0, s, 0, c, 0, x, y, z, 1};
        }

        /** Höhe der Wasserlinie (ohne Schwimmkörper: der Spiegel). */
        public volatile double y;
    }

    public final List<Schiff> schiffe;
    public final Weg weg;
    private final Terrain t;

    // ------------------------------------------------------------------------------------------------------------ Weg

    /** Ein Weg als Linienzug mit Bogenlänge; Lage, Richtung und Abstand zum Ufer an jeder Stelle. */
    public static final class Weg {
        public final double[] x, z, s, frei;
        public final double laenge;

        Weg(double[] x, double[] z, double[] frei) {
            this.x = x; this.z = z; this.frei = frei;
            s = new double[x.length];
            for (int i = 1; i < x.length; i++) s[i] = s[i - 1] + Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
            laenge = s[s.length - 1];
        }

        int seg(double d) {
            int lo = 0, hi = s.length - 2;
            while (lo < hi) {
                int m = (lo + hi + 1) >>> 1;
                if (s[m] <= d) lo = m; else hi = m - 1;
            }
            return lo;
        }

        /** x, z, Richtung x, Richtung z, Abstand zum Ufer an der Stelle d (m vom Anfang). */
        public double[] at(double d) {
            d = Math.max(0, Math.min(laenge, d));
            int i = seg(d);
            double l = Math.max(1e-9, s[i + 1] - s[i]), f = (d - s[i]) / l;
            double dx = (x[i + 1] - x[i]) / l, dz = (z[i + 1] - z[i]) / l;
            // Richtung über ±40 m gemittelt (der Linienzug hat Ecken alle 50 m)
            double[] a = punkt(d - 40), b = punkt(d + 40);
            double tx = b[0] - a[0], tz = b[1] - a[1], tl = Math.hypot(tx, tz);
            if (tl > 1e-6) { dx = tx / tl; dz = tz / tl; }
            return new double[]{x[i] + (x[i + 1] - x[i]) * f, z[i] + (z[i + 1] - z[i]) * f, dx, dz, frei[i] + (frei[i + 1] - frei[i]) * f};
        }

        double[] punkt(double d) {
            d = Math.max(0, Math.min(laenge, d));
            int i = seg(d);
            double l = Math.max(1e-9, s[i + 1] - s[i]), f = (d - s[i]) / l;
            return new double[]{x[i] + (x[i + 1] - x[i]) * f, z[i] + (z[i + 1] - z[i]) * f};
        }

        /** Stelle auf dem Weg, die (px, pz) am nächsten liegt. */
        public double naechste(double px, double pz) {
            double best = 1e18, bd = 0;
            for (int i = 0; i + 1 < x.length; i++) {
                double ex = x[i + 1] - x[i], ez = z[i + 1] - z[i], l2 = ex * ex + ez * ez;
                double f = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((px - x[i]) * ex + (pz - z[i]) * ez) / l2));
                double qx = x[i] + ex * f - px, qz = z[i] + ez * f - pz, d2 = qx * qx + qz * qz;
                if (d2 < best) { best = d2; bd = s[i] + f * Math.sqrt(l2); }
            }
            return bd;
        }

        static Weg laden() {
            List<double[]> p = new ArrayList<>();
            try (java.io.InputStream in = Flotte.class.getResourceAsStream("faehre_route.txt")) {
                if (in == null) throw new IllegalStateException("faehre_route.txt fehlt");
                for (String l : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                    l = l.trim();
                    if (l.isEmpty() || l.startsWith("#")) continue;
                    String[] f = l.split("\\s+");
                    p.add(new double[]{Double.parseDouble(f[0]), Double.parseDouble(f[1]), Double.parseDouble(f[2])});
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
            double[] x = new double[p.size()], z = new double[p.size()], fr = new double[p.size()];
            for (int i = 0; i < p.size(); i++) { x[i] = p.get(i)[0]; z[i] = p.get(i)[1]; fr[i] = p.get(i)[2]; }
            return new Weg(x, z, fr);
        }
    }

    // ---------------------------------------------------------------------------------------------- Fahrt nach Zeit

    /**
     * Ein Fahrprofil auf dem Weg: zulässige Fahrt je Stelle (Zonen, Bremsen vor dem Ziel, Anfahren am Start), dazu die
     * Reisefahrt, die die Zeit trifft. {@link #stelle(double)} gibt die Stelle nach t Sekunden.
     */
    static final class Profil {
        static final double DS = 5;
        final double[] grenze;   // ohne Reisefahrt
        final double[] zeit;     // ab Start bis zur Stelle i·DS
        final double reise;

        Profil(double[] grenze, double dauer) {
            this.grenze = grenze;
            double lo = 0.5, hi = 12;
            for (int k = 0; k < 60; k++) {
                double m = (lo + hi) / 2;
                if (dauer(m) > dauer) lo = m; else hi = m;
            }
            reise = hi;
            zeit = new double[grenze.length];
            for (int i = 1; i < grenze.length; i++) zeit[i] = zeit[i - 1] + DS / v(i - 0.5, reise);
        }

        double v(double i, double reise) {
            int a = (int) Math.floor(i), b = Math.min(grenze.length - 1, a + 1);
            return Math.max(0.15, Math.min(reise, Math.min(grenze[Math.max(0, a)], grenze[b])));
        }

        double dauer(double reise) {
            double t = 0;
            for (int i = 1; i < grenze.length; i++) t += DS / v(i - 0.5, reise);
            return t;
        }

        /** Stelle (m) und Fahrt (m/s) nach t s. */
        double[] stelle(double t) {
            if (t <= 0) return new double[]{0, v(0, reise)};
            if (t >= zeit[zeit.length - 1]) return new double[]{(zeit.length - 1) * DS, 0};
            int lo = 0, hi = zeit.length - 1;
            while (hi - lo > 1) {
                int m = (lo + hi) >>> 1;
                if (zeit[m] <= t) lo = m; else hi = m;
            }
            double f = (t - zeit[lo]) / Math.max(1e-9, zeit[hi] - zeit[lo]);
            return new double[]{(lo + f) * DS, DS / Math.max(1e-9, zeit[hi] - zeit[lo])};
        }

        double laenge() { return (grenze.length - 1) * DS; }
    }

    /** Zonen auf dem Weg der Fähre: vor den Schwestern, Fahrwasser vor dem Ort, Hafen. */
    double zone(double px, double pz) {
        double v = 1e9;
        if (Math.hypot(px - SCHWESTERN_PASS[0], pz - SCHWESTERN_PASS[1]) < 700) v = Math.min(v, SCHWESTERN_V);
        if (px > -1600) v = Math.min(v, ACHT_KNOTEN);
        if (Math.hypot(px, pz + 58) < 600) v = Math.min(v, FUENF_KNOTEN);
        return v;
    }

    /** Punkt 260 m vor den Sieben Schwestern auf dem Weg der Fähre (Szene x, z). */
    public static final double[] SCHWESTERN_PASS = {-5504, -646};

    /** Fahrprofil der Fähre nach Geiranger: am Rand in Fahrt, im Ziel mit 0,04 m/s² bis zum Stand. */
    final Profil herein, hinaus;
    /** Der Weg der Fähre rückwärts ab 75 m vor der Liegestelle (zum Ablegen). */
    public final Weg wegHinaus;
    /** Weg und Profil des Kreuzfahrtschiffs (eigener Weg: der Fähre bis vor das Becken, dann zum Anker). */
    public final Weg wegKreuz;
    final Profil kreuzHerein;
    /** Ablegen in Geiranger: rückwärts von der Liegestelle (s) und Drehen auf dem Teller (s). */
    static final double RUECKWAERTS = 80, DREHEN = 120;

    Profil profil(Weg w, boolean anfahren, boolean bremsen, double dauer, double reiseMax) {
        int n = (int) Math.ceil(w.laenge / Profil.DS) + 1;
        double[] g = new double[n];
        for (int i = 0; i < n; i++) {
            double d = Math.min(w.laenge, i * Profil.DS);
            double[] p = w.at(d);
            double v = Math.min(reiseMax, zone(p[0], p[1]));
            double rest = w.laenge - d;
            if (bremsen) v = Math.min(v, Math.sqrt(2 * 0.04 * Math.max(0, rest)) + 0.05);
            if (anfahren) v = Math.min(v, Math.sqrt(2 * 0.04 * d) + 0.3);
            g[i] = v;
        }
        return new Profil(g, dauer);
    }

    // ------------------------------------------------------------------------------------------------------- Bau

    public Flotte(Terrain t) {
        this.t = t;
        weg = Weg.laden();
        // Fahrzeit im Raster: 65 min weniger je 5 min draußen (Hellesylt – Rand) und beim Ablegen in Geiranger 80 + 120 s
        herein = profil(weg, false, true, FAHRZEIT - AUSSEN, 7.5);
        // hinaus: der Weg rückwärts ab 75 m vor der Liegestelle (dort ist sie fertig gedreht)
        int n0 = weg.x.length;
        List<double[]> r = new ArrayList<>();
        double[] a0 = weg.at(weg.laenge - 75);
        r.add(new double[]{a0[0], a0[1], a0[4]});
        for (int i = n0 - 1; i >= 0; i--) if (weg.s[i] < weg.laenge - 75) r.add(new double[]{weg.x[i], weg.z[i], weg.frei[i]});
        double[] rx = new double[r.size()], rz = new double[r.size()], rf = new double[r.size()];
        for (int i = 0; i < r.size(); i++) { rx[i] = r.get(i)[0]; rz[i] = r.get(i)[1]; rf[i] = r.get(i)[2]; }
        wegHinaus = new Weg(rx, rz, rf);
        hinaus = profil(wegHinaus, true, false, FAHRZEIT - AUSSEN - RUECKWAERTS - DREHEN, 7.5);
        // Kreuzfahrtschiff: Weg der Fähre bis zur Stelle vor dem Becken, dann nach Süden zum Anker
        double d0 = weg.naechste(ANKER[0], ANKER[1] - 420);
        List<double[]> k = new ArrayList<>();
        for (int i = 0; i < weg.x.length && weg.s[i] < d0; i++) k.add(new double[]{weg.x[i], weg.z[i], weg.frei[i]});
        k.add(new double[]{ANKER[0], ANKER[1] - 300, 150});
        k.add(new double[]{ANKER[0], ANKER[1] - 150, 150});
        k.add(new double[]{ANKER[0], ANKER[1], 145});
        double[] kx = new double[k.size()], kz = new double[k.size()], kf = new double[k.size()];
        for (int i = 0; i < k.size(); i++) { kx[i] = k.get(i)[0]; kz[i] = k.get(i)[1]; kf[i] = k.get(i)[2]; }
        wegKreuz = new Weg(kx, kz, kf);
        kreuzHerein = profil(wegKreuz, false, true, 80 * 60, 6.0);
        List<Schiff> s = new ArrayList<>();
        s.add(faehre("MF Veøy"));
        s.add(faehre("MF Bolsøy"));
        s.add(kreuzfahrt());
        s.add(schnellboot());
        for (int i = 0; i < 6; i++) s.add(kajak(i));
        schiffe = Collections.unmodifiableList(s);
    }

    static Floater schwimmer(String name, double hl, double hw, double tiefgang, double freibord, double cg, double vollBug, double vollHeck) {
        int ns = 12, nq = 3;
        List<double[]> c = new ArrayList<>();
        for (int i = 0; i < ns; i++) {
            double u = -1 + (i + 0.5) * 2.0 / ns;
            double b = halbe(hw, u, vollBug, vollHeck);
            for (int j = 0; j < nq; j++) {
                double q = -b + (j + 0.5) * 2 * b / nq;
                // s nach vorn: u > 0 ist der Bug
                c.add(new double[]{u * hl, q, (2 * hl / ns) * (2 * b / nq), -tiefgang, freibord});
            }
        }
        Floater f = new Floater(name, c.toArray(new double[0][]), cg, 0.26 * 2 * hl, 0.38 * 2 * hw);
        f.zetaPitch = 0.25;
        f.zetaRoll = 0.08;
        return f;
    }

    /** Halbe Breite in der Wasserlinie bei u (−1 Heck … 1 Bug) wie HullMask: Bug spitz, Heck voll. */
    static double halbe(double hw, double u, double vollBug, double vollHeck) {
        double a = Math.abs(u), p = u > 0 ? vollBug : vollHeck;
        return a >= 1 ? 0 : hw * Math.pow(1 - Math.pow(a, p), 0.8);
    }

    // ---------------------------------------------------------------------------------------------- Netze (Rümpfe)

    static float[] panel(float[] c) { return new float[]{c[0] + 2, c[1] + 2, c[2] + 2}; }

    static final float[] WEISS = {0.68f, 0.68f, 0.66f}, RUMPF_BLAU = {0.03f, 0.06f, 0.13f}, ROT_UNTEN = {0.22f, 0.05f, 0.035f},
            SCHWARZ = {0.03f, 0.03f, 0.035f}, GRAU = {0.30f, 0.31f, 0.32f}, DECK = {0.16f, 0.17f, 0.17f}, HOLZDECK = {0.35f, 0.26f, 0.16f},
            GELB = {0.62f, 0.45f, 0.05f}, ROTKAJAK = {0.45f, 0.06f, 0.04f}, HAUT = {0.55f, 0.40f, 0.30f}, JACKE = {0.08f, 0.20f, 0.45f};
    /** Lampen: Rot unter −1,5, Farbe = (−Rot − 2, Grün, Blau); weiß, rot, grün (Positionslichter). */
    static final float[] L_WEISS = {-3f, 1f, 0.95f}, L_ROT = {-3f, 0.03f, 0.02f}, L_GRUEN = {-2.05f, 1f, 0.35f};

    /** Ein Rumpf aus Spanten: Bordwand mit Unterwasserfarbe, Wasserpass und Farbe darüber, Deck und Boden. */
    static void rumpf(Bauten.Netz n, double hl, double hw, double unten, double oben, double vollBug, double vollHeck,
                      float[] ueber, float[] pass, float[] unter, float[] deck, double sprung) {
        rumpf(n, hl, hw, unten, oben, vollBug, vollHeck, ueber, pass, unter, deck, sprung, null);
    }

    /** Wie oben, dazu ein Streifen (Farbe) von 1,1 bis 0,2 m unter dem Deck. */
    static void rumpf(Bauten.Netz n, double hl, double hw, double unten, double oben, double vollBug, double vollHeck,
                      float[] ueber, float[] pass, float[] unter, float[] deck, double sprung, float[] streifen) {
        int ns = 28;
        double[] us = new double[ns + 1];
        for (int i = 0; i <= ns; i++) us[i] = -1 + 2.0 * i / ns;
        for (int i = 0; i < ns; i++) {
            double u0 = us[i], u1 = us[i + 1];
            // am Bug läuft der Rumpf in einer Spitze zusammen; Breite oben etwas mehr (Ausfall des Spants)
            double b0 = Math.max(0.05, halbe(hw, u0 * 0.999, vollBug, vollHeck)), b1 = Math.max(0.05, halbe(hw, u1 * 0.999, vollBug, vollHeck));
            double z0 = -u0 * hl, z1 = -u1 * hl;
            double o0 = oben + sprung * u0 * u0, o1 = oben + sprung * u1 * u1;
            double[][] baender = streifen == null ? new double[][]{{unten, -0.25}, {-0.25, 0.35}, {0.35, 1e9}}
                    : new double[][]{{unten, -0.25}, {-0.25, 0.35}, {0.35, oben - 1.1}, {oben - 1.1, oben - 0.2}, {oben - 0.2, 1e9}};
            float[][] farben = streifen == null ? new float[][]{unter, pass, ueber} : new float[][]{unter, pass, ueber, streifen, ueber};
            for (int s = -1; s <= 1; s += 2) {
                for (int k = 0; k < baender.length; k++) {
                    double y0a = baender[k][0], y1a = Math.min(o0, baender[k][1]), y0b = baender[k][0], y1b = Math.min(o1, baender[k][1]);
                    if (y1a <= y0a + 1e-4 && y1b <= y0b + 1e-4) continue;
                    double f0a = 0.82 + 0.18 * (y0a - unten) / (oben - unten), f1a = 0.82 + 0.18 * (y1a - unten) / (oben - unten);
                    double f0b = 0.82 + 0.18 * (y0b - unten) / (oben - unten), f1b = 0.82 + 0.18 * (y1b - unten) / (oben - unten);
                    double[] a = {s * b0 * f0a, y0a, z0}, b = {s * b1 * f0b, y0b, z1}, c = {s * b1 * f1b, y1b, z1}, d = {s * b0 * f1a, y1a, z0};
                    double nx = s, nz = (b1 - b0) / Math.max(1e-6, z1 - z0) * -s;
                    double l = Math.hypot(nx, nz);
                    n.quad(a, b, c, d, new double[]{nx / l, 0, nz / l}, farben[k]);
                }
            }
            // Deck und Boden
            n.quad(new double[]{-b0, o0, z0}, new double[]{b0, o0, z0}, new double[]{b1, o1, z1}, new double[]{-b1, o1, z1}, new double[]{0, 1, 0}, deck);
            n.quad(new double[]{-b0 * 0.82, unten, z0}, new double[]{b0 * 0.82, unten, z0}, new double[]{b1 * 0.82, unten, z1}, new double[]{-b1 * 0.82, unten, z1}, new double[]{0, -1, 0}, unter);
        }
        // Spiegelheck
        double bh = halbe(hw, -0.999, vollBug, vollHeck);
        n.quad(new double[]{-bh * 0.82, unten, hl}, new double[]{bh * 0.82, unten, hl}, new double[]{bh, oben + sprung, hl}, new double[]{-bh, oben + sprung, hl}, new double[]{0, 0, 1}, ueber);
    }

    /** Fensterreihe an einer Seite (x = const) von z0 bis z1, Höhe y, Schritt, Breite und Höhe der Fenster. */
    static int reihe(Bauten.Netz n, double x, double nx, double z0, double z1, double y, double schritt, double fw, double fh, int id) {
        for (double z = z0 + schritt / 2; z <= z1 - schritt / 2 + 1e-6; z += schritt) {
            double xx = x + nx * 0.04;
            double[][] c = {{xx, y, z + fw / 2 * nx}, {xx, y, z - fw / 2 * nx}, {xx, y + fh, z - fw / 2 * nx}, {xx, y + fh, z + fw / 2 * nx}};
            double[][] uv = {{0.002, 0}, {0.998, 0}, {0.998, 1}, {0.002, 1}};
            int k = id++ % 997;
            for (int q : new int[]{0, 1, 2, 0, 2, 3})
                n.put(c[q][0], c[q][1], c[q][2], nx, 0, 0, new float[]{-1, (float) (k + uv[q][0]), (float) uv[q][1]});
        }
        return id;
    }

    /** Fensterreihe quer (z = const) von x0 bis x1. */
    static int quer(Bauten.Netz n, double z, double nz, double x0, double x1, double y, double schritt, double fw, double fh, int id) {
        for (double x = x0 + schritt / 2; x <= x1 - schritt / 2 + 1e-6; x += schritt) {
            double zz = z + nz * 0.04;
            double[][] c = {{x - fw / 2 * nz, y, zz}, {x + fw / 2 * nz, y, zz}, {x + fw / 2 * nz, y + fh, zz}, {x - fw / 2 * nz, y + fh, zz}};
            double[][] uv = {{0.002, 0}, {0.998, 0}, {0.998, 1}, {0.002, 1}};
            int k = id++ % 997;
            for (int q : new int[]{0, 1, 2, 0, 2, 3})
                n.put(c[q][0], c[q][1], c[q][2], 0, 0, nz, new float[]{-1, (float) (k + uv[q][0]), (float) uv[q][1]});
        }
        return id;
    }

    /** Positionslichter nach KVR: Topplicht weiß, Seitenlichter rot (Backbord) und grün (Steuerbord), Hecklicht weiß. */
    static void lichter(Bauten.Netz n, double hl, double hw, double deck, double topp, double mastZ, double mastFuss) {
        if (topp - mastFuss > 0.2) n.beam(0, mastFuss, mastZ, 0, topp, mastZ, 0.06, GRAU);
        n.box(0, mastZ, 0, 1, 0.18, 0.18, topp, topp + 0.3, L_WEISS);
        n.box(-hw * 0.95, -hl * 0.2, 0, 1, 0.12, 0.1, deck + 1.0, deck + 1.25, L_ROT);
        n.box(hw * 0.95, -hl * 0.2, 0, 1, 0.12, 0.1, deck + 1.0, deck + 1.25, L_GRUEN);
        n.box(0, hl * 0.98, 0, 1, 0.12, 0.1, deck + 1.2, deck + 1.45, L_WEISS);
    }

    Schiff faehre(String name) {
        double hl = 35, hw = 7, tg = 3.0, fb = 2.6;
        Bauten.Netz n = new Bauten.Netz();
        // Wasserpass bis 0,35 m schwarz, darunter rot, unter dem Deck ein blaues Band [G: Fjord1 weiß mit Blau]
        rumpf(n, hl, hw, -tg, fb, 2.4, 8, WEISS, SCHWARZ, ROT_UNTEN, DECK, 0.3, RUMPF_BLAU);
        // Aufbauten nur, wo der Rumpf breit genug ist: Wagendeck mit Seitenwänden, Salondeck mit Fensterband, Brücke,
        // Schornstein (vorn bei z = −hl)
        double za = -0.5 * hl, zb = 0.8 * hl, bw = 0.84 * hw, sa = -0.35 * hl, sb = 0.55 * hl, sw = 0.78 * hw;
        n.box(0, (za + zb) / 2, 0, 1, (zb - za) / 2, bw, fb, fb + 4.0, panel(WEISS));
        n.box(0, (sa + sb) / 2, 0, 1, (sb - sa) / 2, sw, fb + 4.0, fb + 6.8, panel(WEISS));
        n.box(0, sa + 4.5, 0, 1, 4.0, hw * 0.6, fb + 6.8, fb + 9.0, panel(WEISS));
        n.box(0, sb - 6, 0, 1, 2.2, 1.6, fb + 6.8, fb + 10.5, RUMPF_BLAU);
        int id = 0;
        for (int s = -1; s <= 1; s += 2) {
            id = reihe(n, s * sw, s, sa, sb, fb + 4.8, 2.2, 1.6, 1.2, id);
            id = reihe(n, s * bw, s, za, zb, fb + 2.2, 7.0, 1.0, 0.8, id);
        }
        id = quer(n, sa + 0.5, -1, -hw * 0.6, hw * 0.6, fb + 7.2, 1.6, 1.3, 1.2, id);
        id = quer(n, sa, -1, -sw, sw, fb + 4.8, 2.2, 1.6, 1.2, id);
        // Reling oben
        for (int s = -1; s <= 1; s += 2) n.beam(s * sw, fb + 7.8, sa, s * sw, fb + 7.8, sb, 0.03, WEISS);
        lichter(n, hl, hw, fb + 4.0, fb + 12.5, sa + 4.5, fb + 9.0);
        return new Schiff(Art.FAEHRE, name, hl, hw, tg, fb, 0.5, 6, n.toArray(), schwimmer(name, hl, hw, tg, fb, 1.0, 2.4, 8));
    }

    /**
     * Höhe des Schwerpunkts des Kreuzfahrtschiffs über der Wasserlinie (m). Nacharbeit 12 (Status_Phase12a): mit 4,0 m
     * war GM −0,29 m, das Schiff kenterte vor Anker auf rund 46° Krängung. Mit 1,5 m: GM 2,2 m, Rollen rund 19 s, wie
     * bei großen Kreuzfahrtschiffen (GM 2 bis 3 m, Rollzeit 18 bis 25 s) [G].
     */
    public static final double KREUZ_SCHWERPUNKT = 1.5;

    Schiff kreuzfahrt() {
        double hl = 125, hw = 16, tg = 8, fb = 10;
        Bauten.Netz n = new Bauten.Netz();
        rumpf(n, hl, hw, -tg, fb, 2.2, 7, WEISS, SCHWARZ, ROT_UNTEN, DECK, 1.0, RUMPF_BLAU);
        // Aufbau in Stufen: elf Decks zu 2,9 m, vorn und hinten abgetreppt
        int id = 0;
        double y = fb;
        // je Stufe: hinten und vorn (u, Bug bei +1), Breite; die Breite nie über den Rumpf an den Enden
        double[][] stufen = {{-0.86, 0.62, 0.97, 4}, {-0.78, 0.55, 0.95, 4}, {-0.62, 0.48, 0.92, 3}};
        for (double[] st : stufen) {
            double za = -st[1] * hl, zb = -st[0] * hl;
            double bw = Math.min(hw * st[2], Math.min(halbe(hw, st[1], 2.2, 7), halbe(hw, st[0], 2.2, 7)) * 0.98);
            double y1 = y + st[3] * 2.9;
            n.box(0, (za + zb) / 2, 0, 1, (zb - za) / 2, bw, y, y1, panel(WEISS));
            for (int d = 0; d < (int) st[3]; d++) {
                double yy = y + 0.9 + d * 2.9;
                for (int s = -1; s <= 1; s += 2) id = reihe(n, s * bw, s, za + 3, zb - 3, yy, 3.0, 2.2, 1.6, id);
                id = quer(n, za, -1, -bw + 1, bw - 1, yy, 3.0, 2.2, 1.6, id);
            }
            y = y1;
        }
        // Rettungsboote (orange) an beiden Seiten, Schornstein, Brücke vorn
        for (int s = -1; s <= 1; s += 2)
            for (int b = 0; b < 8; b++) n.box(s * (hw + 0.6), -40 + b * 11, 0, 1, 4.2, 1.4, fb + 9.0, fb + 11.2, new float[]{0.65f, 0.25f, 0.03f});
        n.box(0, 60, 0, 1, 7, 4.5, y, y + 9, RUMPF_BLAU);
        // Brücke mit Nocken an der Front der zweiten Stufe, im obersten Deck dort
        n.box(0, -hl * 0.55 + 1.5, 0, 1, 2.5, hw + 1.2, fb + 20.3, fb + 23.2, panel(WEISS));
        id = quer(n, -hl * 0.55 - 1.0, -1, -hw, hw, fb + 20.9, 1.8, 1.6, 1.4, id);
        lichter(n, hl, hw, fb, y + 12, -hl * 0.4, y);
        return new Schiff(Art.KREUZFAHRT, "Kreuzfahrtschiff", hl, hw, tg, fb, 0.9, 15, n.toArray(), schwimmer("Kreuzfahrtschiff", hl, hw, tg, fb, KREUZ_SCHWERPUNKT, 2.2, 7));
    }

    Schiff schnellboot() {
        double hl = 4.5, hw = 1.5, tg = 0.45, fb = 0.9;
        Bauten.Netz n = new Bauten.Netz();
        rumpf(n, hl, hw, -tg, fb, 2.0, 6, GRAU, SCHWARZ, GRAU, SCHWARZ, 0.15);
        // Schläuche, Konsole, Fahrgäste in roten Anzügen
        for (int s = -1; s <= 1; s += 2) n.beam(s * hw * 0.92, fb + 0.1, hl * 0.95, s * hw * 0.5, fb + 0.25, -hl * 0.9, 0.28, SCHWARZ);
        n.box(0, 0.5, 0, 1, 0.5, 0.45, fb, fb + 1.2, GRAU);
        for (int k = 0; k < 4; k++)
            for (int s = -1; s <= 1; s += 2) {
                double z = 1.4 + k * 0.8, x = s * 0.55;
                n.box(x, z, 0, 1, 0.2, 0.22, fb, fb + 1.05, new float[]{0.55f, 0.05f, 0.03f});
                n.box(x, z, 0, 1, 0.11, 0.11, fb + 1.05, fb + 1.3, HAUT);
            }
        n.beam(0, fb + 1.2, 0.5, 0, fb + 1.9, 0.5, 0.03, SCHWARZ);   // Bügel für das Topplicht
        lichter(n, hl, hw, fb, fb + 1.9, 0.5, fb + 1.9);
        return new Schiff(Art.SCHNELLBOOT, "Schnellboot", hl, hw, tg, fb, 0.25, 2, n.toArray(), schwimmer("Schnellboot", hl, hw, tg, fb, 0.35, 2.0, 6));
    }

    Schiff kajak(int i) {
        double hl = 2.6, hw = 0.3;
        Bauten.Netz n = new Bauten.Netz();
        float[] farbe = i % 3 == 0 ? GELB : i % 3 == 1 ? ROTKAJAK : new float[]{0.05f, 0.30f, 0.32f};
        rumpf(n, hl, hw, -0.12, 0.22, 2.0, 2.0, farbe, farbe, farbe, farbe, 0.08);
        // Paddler: Körper, Kopf, Paddel quer
        n.box(0, 0.2, 0, 1, 0.16, 0.2, 0.22, 0.85, JACKE);
        n.box(0, 0.2, 0, 1, 0.11, 0.11, 0.85, 1.1, HAUT);
        n.beam(-1.1, 0.75, 0.05, 1.1, 0.95, 0.35, 0.025, SCHWARZ);
        return new Schiff(Art.KAJAK, "Kajak " + (i + 1), hl, hw, 0.12, 0.22, 0.02, 0.6, n.toArray(), null);
    }

    // ------------------------------------------------------------------------------------------------- Lage nach Zeit

    /** In der Saison der Fähre? */
    static boolean saison(LocalDate d) {
        MonthDay m = MonthDay.from(d);
        return !m.isBefore(SAISON_AB) && !m.isAfter(SAISON_BIS);
    }

    /**
     * Alle Schiffe nach der Uhr stellen (Lage, Kurs, Fahrt, da). epochSec: Sekunden seit 1970 (UTC).
     */
    public void stelle(double epochSec) {
        ZonedDateTime z = Instant.ofEpochMilli((long) Math.floor(epochSec * 1000)).atZone(SceneClock.ZONE);
        LocalDate d = z.toLocalDate();
        double h = z.getHour() * 3600 + z.getMinute() * 60 + z.getSecond() + (epochSec - Math.floor(epochSec));
        faehren(schiffe.get(0), schiffe.get(1), d, h);
        kreuz(schiffe.get(2), d, h);
        boot(schiffe.get(3), h);
        for (int i = 0; i < 6; i++) kajak(schiffe.get(4 + i), i, h);
    }

    /** Liegestelle der Fähre in Geiranger: Ende des Wegs, Kurs Ost. */
    double[] liegestelle() { double[] p = weg.at(weg.laenge); return new double[]{p[0], p[1], 90}; }

    void setze(Schiff s, double x, double z, double kurs, double v, boolean da, String zustand) {
        s.x = x; s.z = z; s.kurs = (kurs + 360) % 360; s.fahrt = v; s.da = da; s.zustand = zustand;
    }

    static double kursAus(double dx, double dz) { return (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360; }

    /**
     * Die beiden Fähren: Fähre A beginnt den Tag in Geiranger (erste Abfahrt dort 8:30), Fähre B in Hellesylt; danach
     * wechseln die Richtungen mit jeder Abfahrt. Abends liegt die Fähre, die zuletzt nach Geiranger kam, an der
     * Liegestelle; außerhalb der Saison fährt keine.
     */
    void faehren(Schiff a, Schiff b, LocalDate d, double h) {
        if (!saison(d)) {
            setze(a, 0, 0, 0, 0, false, "außerhalb der Saison");
            setze(b, 0, 0, 0, 0, false, "außerhalb der Saison");
            return;
        }
        for (int k = 0; k < 2; k++) {
            Schiff s = k == 0 ? a : b;
            boolean inGeiranger = k == 0;   // wo die Fähre vor der ersten Abfahrt liegt
            boolean gesetzt = false;
            for (int i = 0; i < ABFAHRTEN.length && !gesetzt; i++) {
                double t0 = ABFAHRTEN[i] * 3600, t1 = t0 + FAHRZEIT;
                boolean nachG = !inGeiranger;
                if (h < t0) {
                    // liegt vor dieser Abfahrt dort, wo sie ist
                    if (inGeiranger) { double[] l = liegestelle(); setze(s, l[0], l[1], l[2], 0, true, String.format(Locale.GERMAN, "liegt in Geiranger, ab %s", uhr(t0))); }
                    else setze(s, 0, 0, 0, 0, false, "in Hellesylt");
                    gesetzt = true;
                } else if (h < t1) {
                    if (nachG) herein(s, h - t0, t1);
                    else hinaus(s, h - t0, t1);
                    gesetzt = true;
                }
                inGeiranger = nachG;
            }
            if (!gesetzt) {
                if (inGeiranger) { double[] l = liegestelle(); setze(s, l[0], l[1], l[2], 0, true, "liegt über Nacht in Geiranger"); }
                else setze(s, 0, 0, 0, 0, false, "über Nacht in Hellesylt");
            }
        }
    }

    static String uhr(double sec) { int m = (int) Math.round(sec / 60); return String.format(Locale.GERMAN, "%d:%02d", m / 60, m % 60); }

    /** Hellesylt → Geiranger, tau s nach der Abfahrt. */
    void herein(Schiff s, double tau, double ankunft) {
        double t = tau - AUSSEN;
        if (t < 0) { setze(s, 0, 0, 0, 0, false, "von Hellesylt unterwegs"); return; }
        double[] st = herein.stelle(t);
        double[] p = weg.at(st[0]);
        double off = spur(p[4], s.hw);
        setze(s, p[0] - p[3] * off, p[1] + p[2] * off, kursAus(p[2], p[3]), st[1], true,
                st[1] < 0.05 ? "legt an" : String.format(Locale.GERMAN, "nach Geiranger, an %s", uhr(ankunft)));
        // in den letzten 60 m zur Liegestelle auf die Mitte (die Spur endet am Anleger)
        if (weg.laenge - st[0] < 120) {
            double f = Math.max(0, (weg.laenge - st[0]) / 120);
            s.x = p[0] - p[3] * off * f;
            s.z = p[1] + p[2] * off * f;
        }
    }

    /** Geiranger → Hellesylt, tau s nach der Abfahrt: rückwärts von der Liegestelle, drehen, dann hinaus. */
    void hinaus(Schiff s, double tau, double ankunft) {
        double[] l = liegestelle();
        if (tau < RUECKWAERTS) {
            double f = tau / RUECKWAERTS, back = 75 * (1 - Math.cos(Math.PI * f)) / 2;   // weich an und aus
            double[] p = weg.at(weg.laenge - back);
            setze(s, p[0], p[1], 90, 75 * Math.PI / 2 / RUECKWAERTS * Math.sin(Math.PI * f), true, "legt ab");
            return;
        }
        double sAnfang = weg.laenge - 75;
        double[] p0 = weg.at(sAnfang);
        double[] q0 = wegHinaus.at(0);
        double kursHinaus = kursAus(q0[2], q0[3]);
        if (tau < RUECKWAERTS + DREHEN) {
            double f = (tau - RUECKWAERTS) / DREHEN, k = 90 + ((kursHinaus - 90 + 540) % 360 - 180) * (1 - Math.cos(Math.PI * f)) / 2;
            setze(s, p0[0], p0[1], k, 0, true, "dreht im Hafen");
            return;
        }
        double t = tau - RUECKWAERTS - DREHEN;
        double[] st = hinaus.stelle(t);
        if (st[0] >= wegHinaus.laenge - 0.5) { setze(s, 0, 0, 0, 0, false, "nach Hellesylt unterwegs"); return; }
        double[] p = wegHinaus.at(st[0]);
        double off = spur(p[4], s.hw) * Math.min(1, st[0] / 120);
        setze(s, p[0] - p[3] * off, p[1] + p[2] * off, kursAus(p[2], p[3]), st[1], true, String.format(Locale.GERMAN, "nach Hellesylt, an %s", uhr(ankunft)));
    }

    /** Rechtsfahrgebot: Versatz der Spur nach Steuerbord (m), nie näher als 60 m ans Ufer. */
    static double spur(double frei, double hw) { return Math.max(0, Math.min(70, frei - hw - 60)); }

    /** Kreuzfahrtschiff: 6:40 bis 8:00 herein, vor Anker bis 17:30 (Bug nach Süden), drehen, bis 18:50 hinaus. */
    void kreuz(Schiff s, LocalDate d, double h) {
        int m = d.getMonthValue();
        if (m < 5 || m > 9) { setze(s, 0, 0, 0, 0, false, "kein Anlauf (Oktober bis April)"); return; }
        double an = 8 * 3600, ab = 17.5 * 3600, dauer = 80 * 60, drehen = 8 * 60;
        if (h < an - dauer || h > ab + drehen + dauer) { setze(s, 0, 0, 0, 0, false, "auf See"); return; }
        if (h < an) {
            double[] st = kreuzHerein.stelle(h - (an - dauer));
            double[] p = wegKreuz.at(st[0]);
            double off = spur(p[4], s.hw) * Math.min(1, (wegKreuz.laenge - st[0]) / 800);
            setze(s, p[0] - p[3] * off, p[1] + p[2] * off, kursAus(p[2], p[3]), st[1], true, "läuft ein, vor Anker 8:00");
            return;
        }
        if (h < ab) { setze(s, ANKER[0], ANKER[1], ANKER_KURS, 0, true, "vor Anker bis 17:30"); return; }
        if (h < ab + drehen) {
            double f = (h - ab) / drehen;
            setze(s, ANKER[0], ANKER[1], ANKER_KURS + 180 * (1 - Math.cos(Math.PI * f)) / 2, 0, true, "dreht vor Anker");
            return;
        }
        // hinaus: dasselbe Profil rückwärts (wie herein, aber von hinten gelesen)
        double tt = h - ab - drehen, total = kreuzHerein.zeit[kreuzHerein.zeit.length - 1];
        double[] st = kreuzHerein.stelle(total - tt);
        double[] p = wegKreuz.at(st[0]);
        double off = spur(p[4], s.hw) * Math.min(1, (wegKreuz.laenge - st[0]) / 800);
        setze(s, p[0] + p[3] * off, p[1] - p[2] * off, kursAus(-p[2], -p[3]), st[1], true, "läuft aus");
    }

    /** Schnellboot: zu jeder geraden Stunde von 10 bis 16 Uhr zu den Schwestern (12 m/s), dort zwei Kreise, zurück. */
    void boot(Schiff s, double h) {
        double v = 12, sv = weg.naechste(SCHWESTERN_PASS[0], SCHWESTERN_PASS[1]), ende = weg.laenge - 140;
        double strecke = ende - sv, fahrt = strecke / v, kreis = 2 * 2 * Math.PI * 90 / 6.0, glit = 40;
        double[] pe = weg.at(ende);
        double lx = pe[0] + 12, lz = pe[1] + 25;   // Liegeplatz neben dem Weg
        for (int k = 10; k <= 16; k += 2) {
            double t = h - k * 3600;
            if (t < 0 || t > 2 * (fahrt + glit) + kreis) continue;
            if (t < glit) {
                double[] a = bootPunkt(ende, -1, s.hw);
                double f = t / glit, g = f * f * (3 - 2 * f);
                setze(s, lx + (a[0] - lx) * g, lz + (a[1] - lz) * g, kursAus(-pe[2], -pe[3]), v * f, true, "legt ab");
                return;
            }
            t -= glit;
            if (t < fahrt) {
                double[] a = bootPunkt(ende - t * v, -1, s.hw);
                setze(s, a[0], a[1], a[2], v, true, "zu den Sieben Schwestern");
                return;
            }
            t -= fahrt;
            if (t < kreis) {
                double a = t / kreis * 4 * Math.PI;
                double[] p = bootPunkt(sv, -1, s.hw);
                // zwei Kreise mit 90 m, Anfang und Ende am Ende der Hinfahrt
                double cx = p[0] - Math.sin(Math.toRadians(p[2]) + Math.PI / 2) * 90, cz = p[1] + Math.cos(Math.toRadians(p[2]) + Math.PI / 2) * 90;
                double a0 = Math.atan2(p[1] - cz, p[0] - cx), aa = a0 + a;
                setze(s, cx + Math.cos(aa) * 90, cz + Math.sin(aa) * 90, kursAus(-Math.sin(aa), Math.cos(aa)), 6, true, "vor den Sieben Schwestern");
                return;
            }
            t -= kreis;
            if (t < fahrt) {
                double[] a = bootPunkt(sv + t * v, 1, s.hw, Math.min(1, t * v / 400));
                setze(s, a[0], a[1], a[2], v, true, "zurück nach Geiranger");
                return;
            }
            t -= fahrt;
            double[] a = bootPunkt(ende, 1, s.hw);
            double f = Math.min(1, t / glit), g = f * f * (3 - 2 * f);
            setze(s, a[0] + (lx - a[0]) * g, a[1] + (lz - a[1]) * g, kursAus(pe[2], pe[3]), v * (1 - f), true, "legt an");
            return;
        }
        setze(s, lx, lz, kursAus(-pe[2], -pe[3]), 0, true, "liegt am Anleger");
    }

    /** Punkt des Schnellboots auf dem Weg an der Stelle d, Richtung r (−1 hinaus, 1 herein): x, z, Kurs. */
    double[] bootPunkt(double d, int r, double hw) { return bootPunkt(d, r, hw, 1); }

    /** Wie oben; wechsel 0..1 führt auf der Rückfahrt von der Seite der Hinfahrt auf die eigene (nach dem Kreis). */
    double[] bootPunkt(double d, int r, double hw, double wechsel) {
        double[] p = weg.at(d);
        // rechts der eigenen Fahrt, aber innen neben der Spur der Fähre (die Hälfte ihres Versatzes)
        double off = (spur(p[4], hw) * 0.5 + 25) * (2 * wechsel - 1), dx = p[2] * r, dz = p[3] * r;
        return new double[]{p[0] - dz * off, p[1] + dx * off, kursAus(dx, dz)};
    }

    /** Kajaks: von 9 bis 20 Uhr auf einem Kreis von 70 bis 80 m im Becken vor dem Ort, gleichmäßig verteilt. */
    void kajak(Schiff s, int i, double h) {
        if (h < 9 * 3600 || h > 20 * 3600) { setze(s, 0, 0, 0, 0, false, "an Land"); return; }
        double cx = KAJAK_MITTE[0], cz = KAJAK_MITTE[1], r = 70 + 9 * (i % 2), v = 1.1;
        double a = (h - 9 * 3600) * v / r + i * Math.PI / 3;
        setze(s, cx + Math.cos(a) * r, cz + Math.sin(a) * r, kursAus(-Math.sin(a), Math.cos(a)), v, true, "paddelt");
    }

    /** Mitte der Kajakrunde im Becken vor dem Delta. */
    public static final double[] KAJAK_MITTE = {-620, -140};

    // -------------------------------------------------------------------------------------------- Schwimmen (Physik)

    /**
     * Ein Schritt: Lage nach der Uhr, dann Tauchen, Stampfen und Rollen auf dem Wasser (alle 0,1 s abgefragt).
     * dt in echten Sekunden (die Wellen laufen mit der Laufzeit, nicht mit der Uhr).
     */
    public void schritt(double epochSec, Sea sea, double dt) {
        stelle(epochSec);
        for (Schiff s : schiffe) {
            if (!s.da) { s.warDa = false; continue; }
            Floater f = s.floater;
            if (f == null) { s.y = sea.height(s.x, s.z); continue; }
            f.x = s.x;
            f.z = s.z;
            f.heading = s.kurs;
            f.vx = f.vz = 0;
            if (!s.warDa) f.settle(sea);
            else {
                f.sample(sea);
                f.integrate(Math.min(0.1, dt));
            }
            s.y = f.y;
            s.warDa = true;
        }
    }

    /** Die Wasserfläche für buoyancy: Höhe von ocean über dem Fjord. */
    public static Sea sea(java.util.function.DoubleBinaryOperator hoehe, java.util.function.DoubleSupplier spiegel) {
        return new Sea() {
            @Override public double height(double x, double z) { return hoehe.applyAsDouble(x, z); }
            @Override public double level() { return spiegel.getAsDouble(); }
        };
    }

    /** Für die Prüfung: Schiffe ohne Grafik stellen und in Ruhe schwimmen lassen. */
    public void ruhig(double epochSec) {
        schritt(epochSec, sea((x, z) -> 0, () -> 0), 0);
        for (int i = 0; i < 600; i++) schritt(epochSec, sea((x, z) -> 0, () -> 0), 1 / 60.0);
    }

    /** „Schiffe: MF Veøy nach Geiranger (an 11:05), MF Bolsøy …, Kreuzfahrtschiff vor Anker, Schnellboot …, 6 Kajaks“ */
    public String describe() {
        StringBuilder sb = new StringBuilder("Schiffe:");
        int kajak = 0;
        for (Schiff s : schiffe) {
            if (s.art == Art.KAJAK) { if (s.da) kajak++; continue; }
            sb.append(String.format(Locale.GERMAN, " %s %s%s ·", s.name, s.zustand, s.da && s.fahrt > 0.2 ? String.format(Locale.GERMAN, " (%.1f m/s)", s.fahrt) : ""));
        }
        sb.append(' ').append(kajak).append(" Kajaks");
        return sb.toString();
    }

    public Terrain terrain() { return t; }
}
