package com.dan.geiranger.gl;

import com.dan.fall.Fall;
import com.dan.fall.gl.FallGl;
import com.dan.geiranger.scene.Faelle;
import com.dan.geiranger.scene.Site;
import com.dan.geiranger.scene.Terrain;
import com.dan.geiranger.scene.Wandschatten;
import com.dan.geiranger.wiring.Abfluss;
import com.dan.ocean.Ocean;
import com.jogamp.opengl.GL4;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase 7: die Wasserfälle in der Szene ({@link Faelle}, gezeichnet mit {@code com.dan.fall}). Je Bild bekommen sie
 * Abfluss ({@link Abfluss}), Wind und die Temperatur am Fall; ändert sich etwas merklich (Abfluss um 10 %, Wind um
 * 1,5 m/s oder 25°, Temperatur um 1,5 °C), rechnet ein Hintergrundfaden den Fall neu und tauscht ihn aus, während die
 * Grafikkarte den alten weiterzeichnet. Ausgänge: Ringwellen an ocean ({@link Ocean#addImpulse}, nur für Fälle bis
 * 1,5 km vom Auge, höchstens sechs je Sekunde: ocean hält 32 Ringe für 4 s), Nässe der Wand an cliff
 * ({@code Cliff.fallWet}) und für das HUD Abfluss, Lautstärke und Zeit auf der Grafikkarte.
 * <p>
 * Licht: Sonne und Mond wie das Gelände, dazu je Fall der Schatten der Wände (an Kante, Mitte und Fuß, alle 2 s neu)
 * und der Wolkenschatten an seiner Mitte.
 */
public final class Wasserfaelle {
    public final FallGl gl = new FallGl();
    private volatile List<Faelle.Eintrag> eintraege = List.of();
    private final Map<Faelle.Eintrag, Fall> drawn = new IdentityHashMap<>();
    private final Map<Faelle.Eintrag, double[]> licht = new IdentityHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Fälle");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean busy = new AtomicBoolean();
    private double rippleClock, lichtTimer;
    private final Map<Fall.Strand, Double> nextRing = new IdentityHashMap<>();
    boolean ok;
    String error = "", check = "";
    /** Fälle zeichnen (Block WASSER). */
    public volatile boolean on = true;
    /** Wie viele Neurechnungen es gab (für die Prüfung und das HUD). */
    public volatile int rebuilds;
    private volatile boolean pending = true;

    /** Ob alle Fälle zu ihren Eingängen passen und keine Rechnung läuft (für Standbilder: erst dann messen). */
    public boolean idle() { return !pending && !busy.get() && !eintraege.isEmpty(); }

    /** Die Fälle der Szene setzen (aus dem Hintergrund, sobald Wandkarte und Wege fertig sind). */
    public void set(List<Faelle.Eintrag> e) { eintraege = List.copyOf(e); }

    public List<Faelle.Eintrag> eintraege() { return eintraege; }

    /** Der Eintrag zu einem Namen oder null. */
    public Faelle.Eintrag get(String name) {
        for (Faelle.Eintrag e : eintraege) if (e.def.name().equals(name)) return e;
        return null;
    }

    void init(GL4 g) {
        try {
            gl.init(g);
            ok = true;
        } catch (RuntimeException e) {
            ok = false;
            error = e.getMessage() == null ? e.toString() : e.getMessage().split("\n")[0];
            System.err.println("Geiranger: fall nicht gestartet – " + e.getMessage());
        }
    }

    /**
     * Eingänge je Bild: Abfluss, Wind, Temperatur am Fall, Stufe. Neu gerechnet wird im Hintergrund, immer nur ein Fall
     * auf einmal (der mit der größten Abweichung zuerst).
     */
    public void step(Abfluss a, double windSpeed, double windFrom, Fall.Quality q) {
        Faelle.Eintrag worst = null;
        double worstDev = 0;
        for (Faelle.Eintrag e : eintraege) {
            double want = a.cubicMetres(e.def.name());
            Fall f = e.fall;
            if (want < 0.003) continue;   // trocken: nicht gezeichnet, nicht gerechnet
            double dev = Math.abs(want - e.q) / Math.max(0.01, Math.max(want, e.q)) / 0.1;
            dev = Math.max(dev, Math.abs(a.amFall() - e.temp) / 1.5);
            dev = Math.max(dev, Math.abs(windSpeed - e.wind) / 1.5);
            double dd = Math.abs(((windFrom - e.windFrom) % 360 + 540) % 360 - 180);
            if (windSpeed > 1 || e.wind > 1) dev = Math.max(dev, dd / 25);
            if (f.quality != q) dev = Math.max(dev, 2);
            if (dev > 1 && dev > worstDev) { worst = e; worstDev = dev; }
        }
        pending = worst != null;
        if (worst == null || !busy.compareAndSet(false, true)) return;
        Faelle.Eintrag e = worst;
        double want = a.cubicMetres(e.def.name()), temp = a.amFall();
        e.q = want;
        e.temp = temp;
        e.wind = windSpeed;
        e.windFrom = windFrom;
        Fall src = e.fall;
        worker.execute(() -> {
            try {
                Fall nf = src.copy();
                nf.discharge = want;
                nf.temperature = temp;
                nf.windSpeed = windSpeed;
                nf.windFromDeg = windFrom;
                nf.quality = q;
                nf.update();
                e.fall = nf;
                rebuilds++;
            } catch (RuntimeException ex) {
                System.err.println("Geiranger: Fall " + e.def.name() + " nicht neu gerechnet – " + ex);
            } finally {
                busy.set(false);
            }
        });
    }

    /** Ob ein Fall gerade gezeichnet wird (Abfluss da). */
    public static boolean laeuft(Abfluss a, Faelle.Eintrag e) { return a.cubicMetres(e.def.name()) >= 0.003; }

    /**
     * Ringwellen an ocean: je Strähne, die im Wasser ankommt, ein Ring nach ihrer Periode ({@code Fall.ripples}), nur
     * für Fälle bis 1,5 km vom Auge und höchstens sechs je Sekunde. dt in Echtzeit.
     */
    public void ripples(Ocean ocean, Abfluss a, double[] eye, double dt, double time) {
        rippleClock = Math.min(1, rippleClock + dt * 6);
        for (Faelle.Eintrag e : eintraege) {
            if (!laeuft(a, e) || e.def.art() == Faelle.Art.FLUSS) continue;   // die Stufen der Elva enden im Fluss, nicht im Fjord
            Fall f = e.fall;
            double[] m = e.mitte();
            if (Math.hypot(m[0] - eye[0], m[2] - eye[2]) > 1500) continue;
            List<double[]> r = f.ripples();
            int k = 0;
            for (Fall.Strand s : f.strands) {
                if (!s.onWater) continue;
                double[] rr = r.get(k++);
                double next = nextRing.getOrDefault(s, time);
                if (time < next || rippleClock < 1) continue;
                ocean.addImpulse(rr[0], rr[1], Math.min(0.8, rr[2] * 2));
                nextRing.put(s, time + Math.max(rr[3], 1.0));
                rippleClock -= 1;
            }
        }
        if (nextRing.size() > 400) nextRing.clear();
    }

    /** Wie nass die Streifen unter den Fällen sind, 0..1, aus dem Abfluss (Wand bleibt nass bis rund 30 % [G]). */
    public static double wet(Abfluss a) { return Math.max(0, Math.min(1, 0.3 + 0.7 * Math.min(1, a.factor()))); }

    /** Licht je Fall neu (Schatten der Wände an Kante, Mitte und Fuß), alle 2 s; dt in Echtzeit. */
    void updateLight(Terrain t, double[] keyDir, double dt, boolean force) {
        lichtTimer -= dt;
        if (!force && lichtTimer > 0 && licht.size() == eintraege.size()) return;
        lichtTimer = 2;
        for (Faelle.Eintrag e : eintraege) {
            Fall f = e.fall;
            double sum = 0;
            int n = 0;
            for (Fall.Strand s : f.strands) {
                if (n >= 3) break;
                double[][] pts = {{s.x, s.y + 2, s.z}, {(s.x + s.ix) / 2, (s.y + s.iy) / 2 + 2, (s.z + s.iz) / 2}, {s.ix, s.iy + 2, s.iz}};
                for (double[] p : pts) sum += t.measured() ? Wandschatten.licht(t, p[0], p[1], p[2], keyDir) : 1;
                n++;
            }
            licht.put(e, new double[]{n == 0 ? 1 : sum / (3 * n)});
        }
    }

    double sunAt(Faelle.Eintrag e) { double[] l = licht.get(e); return l == null ? 1 : l[0]; }

    /**
     * Zeichnet alle laufenden Fälle in das gebundene Ziel (nach dem Wasser; Tiefentest ohne Tiefe zu schreiben).
     * cloudShade: Wolkenschatten je Ort (x, z) → 0..1. lodNear: Abstand, bis zu dem alle Bündel gezeichnet werden.
     */
    void draw(GL4 g, Abfluss a, FallGl.View v, FallGl.Light base, double time, java.util.function.DoubleBinaryOperator cloudShade, double lodNear) {
        if (!ok || !on) return;
        gl.lodNear = lodNear;
        for (Faelle.Eintrag e : eintraege) {
            Fall f = e.fall;
            Fall old = drawn.put(e, f);
            if (old != null && old != f) gl.forget(g, old);
            if (!laeuft(a, e)) continue;
            double[] m = e.mitte();
            double k = sunAt(e) * cloudShade.applyAsDouble(m[0], m[2]);
            FallGl.Light l = new FallGl.Light();
            l.sunDir = base.sunDir;
            l.sunColor = new double[]{base.sunColor[0] * k, base.sunColor[1] * k, base.sunColor[2] * k};
            l.ambient = base.ambient;
            l.fogColor = base.fogColor;
            l.fogDistance = base.fogDistance;
            l.display = 0;
            gl.draw(g, f, v, l, time);
        }
    }

    /** Selbsttest am ersten Fall (Bahn auf der Grafikkarte gegen den Prozessor). */
    void selfCheck(GL4 g) {
        if (!ok || !check.isEmpty() || eintraege.isEmpty()) return;
        check = gl.selfCheck(g, eintraege.get(0).fall);
    }

    /** Zeit aller Fälle auf der Grafikkarte im letzten Bild (ms) und wie viele gezeichnet wurden. */
    double[] gpu() {
        double ms = 0;
        int n = 0;
        for (Faelle.Eintrag e : eintraege) {
            double t = gl.gpuMs(e.fall);
            if (t > 0) { ms += t; n++; }
        }
        return new double[]{ms, n};
    }

    /** Der nächste laufende Fall zum Auge: Eintrag, Abstand (m), Pegel (dB). */
    Object[] naechster(Abfluss a, double[] eye) {
        Faelle.Eintrag best = null;
        double bd = Double.MAX_VALUE;
        for (Faelle.Eintrag e : eintraege) {
            if (!laeuft(a, e)) continue;
            double[] m = e.mitte();
            double d = Math.sqrt((m[0] - eye[0]) * (m[0] - eye[0]) + (m[1] - eye[1]) * (m[1] - eye[1]) + (m[2] - eye[2]) * (m[2] - eye[2]));
            if (d < bd) { bd = d; best = e; }
        }
        return best == null ? null : new Object[]{best, bd, best.fall.soundLevel(bd)};
    }

    /** HUD: „Fälle: Sieben Schwestern 2,4 m³/s · Friaren 1,4 · … · Regenfälle 0 von 6 · nächster …“ */
    String describe(Abfluss a, double[] eye) {
        if (!ok) return "!Fälle: com.dan.fall nicht gestartet (" + error + ")";
        List<Faelle.Eintrag> es = eintraege;
        if (es.isEmpty()) return "Fälle: Wege entstehen …";
        StringBuilder sb = new StringBuilder("Fälle: ");
        int regen = 0, regenOn = 0;
        List<String> named = new ArrayList<>();
        for (Faelle.Eintrag e : es) {
            if (e.def.art() == Faelle.Art.REGEN) { regen++; if (laeuft(a, e)) regenOn++; continue; }
            Fall f = e.fall;
            named.add(String.format(Locale.GERMAN, "%s %.2f m³/s%s", e.def.name(), a.cubicMetres(e.def.name()), f.ice() > 0.01 ? String.format(Locale.GERMAN, " (Eis %.0f %%)", f.ice() * 100) : ""));
        }
        sb.append(String.join(" · ", named)).append(String.format(Locale.GERMAN, " · Regenfälle %d von %d", regenOn, regen));
        Object[] n = naechster(a, eye);
        if (n != null) sb.append(String.format(Locale.GERMAN, " · nächster %s %.0f m, %.0f dB", ((Faelle.Eintrag) n[0]).def.name(), (double) n[1], (double) n[2]));
        double[] gp = gpu();
        sb.append(String.format(Locale.GERMAN, " · %d gezeichnet, %.2f ms", (int) gp[1], gp[0]));
        if (!check.isEmpty()) sb.append(" · ").append(check);
        return sb.toString();
    }

    /** Lage eines Falls als Zeile (Bedienfeld, README). */
    static String lage(Faelle.Eintrag e) {
        double[] u = Site.toUtm(e.kante.x(), e.kante.z());
        return String.format(Locale.GERMAN, "%s · Kante %.0f m (E %.0f, N %.0f)", e.def.name(), e.kante.y(), u[0], u[1]);
    }

    void dispose(GL4 g) {
        if (ok) gl.dispose(g);
        drawn.clear();
        worker.shutdownNow();
    }
}
