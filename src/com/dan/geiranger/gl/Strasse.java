package com.dan.geiranger.gl;

import com.dan.geiranger.scene.Begegnung;
import com.dan.geiranger.scene.Ornevegen;
import com.dan.geiranger.scene.Plattform;
import com.dan.geiranger.scene.Terrain;
import com.dan.road.Batch;
import com.dan.road.Ground;
import com.dan.road.Mover;
import com.dan.road.Roads;
import com.dan.road.Vehicle;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase 9: die Fv63 mit dem Ørnevegen auf der Grafikkarte. {@code com.dan.road} baut je Bild Fahrbahn, Ausstattung,
 * Fahrzeuge und Lichter als Dreiecke ({@link Batch}); das kostet 3 bis 8 ms auf dem Prozessor und läuft darum in
 * einem eigenen Faden ({@link #FADEN}): der GL-Faden schickt Auge und Zeit, der Faden rechnet die Begegnungsregel
 * ({@link Begegnung}), {@code Roads.update} und packt das Ergebnis in Puffer; der GL-Faden lädt den jeweils neuesten
 * hoch, ohne zu warten. Gezeichnet in zwei Gängen: fest (SOLID, THIN, LAMP; Tiefe schreiben), dann was sich mischt
 * (Ecken mit {@code fade} > 0: Staub, Gischt, Ränder) und die Lichthöfe (GLOW, addiert). Dazu die Plattform der
 * Ørnesvingen mit dem Programm der Bauten.
 */
public final class Strasse {
    public static final String FADEN = "Straße";
    /** Werte je Ecke: Lage, Normale, Farbe, Glanz, Mischung, Art. */
    static final int STRIDE = 12;

    public final Ornevegen ornevegen;
    public final Plattform plattform;
    public final Roads roads;
    public final Begegnung begegnung;
    /** Verkehr je Richtung (Fahrzeuge je Stunde); null: aus der Tageszeit [G]. */
    public volatile Double verkehr;
    private volatile boolean running = true;
    private final Object lock = new Object();
    private double[] wantEye;
    private float wantT, wantDt;
    private volatile Frame ready;
    private final AtomicReference<Frame> spare = new AtomicReference<>();
    private Thread worker;
    private int vao, vbo, ibo, nSolid, nBlend, nGlow, pVao, pVbo, pCount, cap, icap;
    boolean ok;
    /** Zuletzt gezeichnet: Dreiecke, Fahrzeuge, Rechenzeit (ms). */
    public volatile int dreiecke, fahrzeuge;
    public volatile double rechenMs;

    static final class Frame {
        float[] v = new float[0];
        int[] idx = new int[0];
        int nv, nSolid, nBlend, nGlow;
    }

    public Strasse(Terrain t) {
        ornevegen = t.ornevegen;
        plattform = Plattform.build(t);
        if (ornevegen != null) {
            Ground g = new Ground() {
                @Override public float height(double x, double z) { return (float) t.height(x, z); }
                @Override public float water(double x, double z) { return Float.NaN; }
            };
            roads = new Roads(ornevegen.net, g, 63);
            roads.radius = 2500;
            roads.vehicleRange = 1200;
            begegnung = new Begegnung(ornevegen);
        } else {
            roads = null;
            begegnung = null;
        }
    }

    public boolean present() { return roads != null; }

    /** Verkehr je Richtung nach der Uhrzeit [G: Juni, 6 bis 22 Uhr bis 150 je Stunde, nachts wenige]. */
    public static double verkehrNachZeit(double hour) {
        double day = Math.max(0, Math.sin(Math.PI * (hour - 5.5) / 17.0));
        return 6 + 144 * day * day;
    }

    /** Wetter auf der Fahrbahn: Regen 0..1, Dunkelheit 0..1 (aus der Sonnenhöhe), Wind. */
    public void wetter(double regenMmH, double sunAltDeg, double windMs, double windFrom) { wetter(regenMmH, sunAltDeg, windMs, windFrom, 0); }

    /** Phase 14: dazu Schnee auf der Fahrbahn (0..1, aus dem Jahr in Höhe der Kehren). */
    public void wetter(double regenMmH, double sunAltDeg, double windMs, double windFrom, double schnee) {
        if (!present()) return;
        roads.weather.snow = (float) Math.max(0, Math.min(1, schnee));
        roads.weather.poles = true;
        roads.weather.rain = (float) Math.min(1, regenMmH / 4);
        roads.weather.dark = (float) Math.max(0, Math.min(1, (2 - sunAltDeg) / 8));
        roads.weather.windSpeed = (float) windMs;
        roads.weather.windDirection = (float) windFrom;
    }

    /** Je Bild aus dem GL-Faden: Auge und Zeit an den Faden (der rechnet, sobald er frei ist). */
    void step(double[] eye, double t, double dt, double hour) {
        if (!present()) return;
        if (worker == null) {
            worker = new Thread(this::loop, FADEN);
            worker.setDaemon(true);
            worker.start();
        }
        double flow = verkehr != null ? verkehr : verkehrNachZeit(hour);
        synchronized (lock) {
            wantEye = eye.clone();
            wantT = (float) t;
            wantDt += (float) dt;
            wantFlow = flow;
            lock.notifyAll();
        }
    }

    private double wantFlow = -1, flowSet = -1;

    private void loop() {
        while (running) {
            double[] eye;
            float t, dt;
            double flow;
            synchronized (lock) {
                while (running && wantEye == null) {
                    try { lock.wait(100); } catch (InterruptedException e) { return; }
                }
                if (!running) return;
                eye = wantEye; t = wantT; dt = Math.min(0.25f, wantDt); flow = wantFlow;
                wantEye = null; wantDt = 0;
            }
            rechne(eye, t, dt, flow);
        }
    }

    /** Ein Schritt ohne Grafikkarte (Faden, Prüfung, Werkzeuge). */
    public synchronized void rechne(double[] eye, float t, float dt, double flow) {
        long t0 = System.nanoTime();
        if (flow >= 0 && Math.abs(flow - flowSet) > 0.5) {
            roads.traffic.flow[ornevegen.way.index] = (float) flow;
            if (ornevegen.strandvegen != null) roads.traffic.flow[ornevegen.strandvegen.index] = (float) (0.15 * flow);   // Phase 11: wenig im Ort
            roads.traffic.rates();
            flowSet = flow;
        }
        begegnung.step(roads.traffic);
        roads.update(t, dt, eye[0], eye[1], eye[2]);
        Frame f = spare.getAndSet(null);
        if (f == null) f = new Frame();
        pack(roads.batch, f);
        int nv = 0;
        for (Mover m : roads.traffic.movers) if (!m.kind.walks()) nv++;
        fahrzeuge = nv;
        ready = f;
        rechenMs = (System.nanoTime() - t0) / 1e6;
    }

    /** Ecken verschachteln und Dreiecke nach Art ordnen: fest, gemischt, Lichthof. */
    static void pack(Batch b, Frame f) {
        if (f.v.length < b.nv * STRIDE) f.v = new float[b.nv * STRIDE + 4096];
        for (int i = 0; i < b.nv; i++) {
            int o = i * STRIDE;
            f.v[o] = b.xyz[3 * i]; f.v[o + 1] = b.xyz[3 * i + 1]; f.v[o + 2] = b.xyz[3 * i + 2];
            f.v[o + 3] = b.nrm[3 * i]; f.v[o + 4] = b.nrm[3 * i + 1]; f.v[o + 5] = b.nrm[3 * i + 2];
            f.v[o + 6] = b.rgb[3 * i]; f.v[o + 7] = b.rgb[3 * i + 1]; f.v[o + 8] = b.rgb[3 * i + 2];
            f.v[o + 9] = b.gloss[i]; f.v[o + 10] = b.fade[i]; f.v[o + 11] = b.kind[i];
        }
        f.nv = b.nv;
        if (f.idx.length < b.nt * 3) f.idx = new int[b.nt * 3 + 4096];
        int k = 0;
        for (int pass = 0; pass < 3; pass++) {
            int start = k;
            for (int t = 0; t < b.nt; t++) {
                int a = b.tri[3 * t], c = b.tri[3 * t + 1], d = b.tri[3 * t + 2];
                boolean glow = b.kind[a] == Batch.GLOW;
                boolean blend = !glow && (b.fade[a] > 0.003f || b.fade[c] > 0.003f || b.fade[d] > 0.003f);
                int cls = glow ? 2 : blend ? 1 : 0;
                if (cls != pass) continue;
                f.idx[k++] = a; f.idx[k++] = c; f.idx[k++] = d;
            }
            if (pass == 0) f.nSolid = k - start;
            else if (pass == 1) f.nBlend = k - start;
            else f.nGlow = k - start;
        }
    }

    void init(GL4 gl) {
        if (!present()) return;
        int[] a = new int[2], b = new int[3];
        gl.glGenVertexArrays(2, a, 0);
        gl.glGenBuffers(3, b, 0);
        vao = a[0]; pVao = a[1]; vbo = b[0]; ibo = b[1]; pVbo = b[2];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, 4L * STRIDE * 4, null, GL4.GL_STREAM_DRAW);
        int[][] at = {{0, 3, 0}, {1, 3, 3}, {2, 3, 6}, {3, 3, 9}};
        for (int[] x : at) {
            gl.glEnableVertexAttribArray(x[0]);
            gl.glVertexAttribPointer(x[0], x[1], GL.GL_FLOAT, false, STRIDE * 4, x[2] * 4L);
        }
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBindVertexArray(0);
        if (plattform != null) {
            float[] pv = plattform.ecken;
            gl.glBindVertexArray(pVao);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, pVbo);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, pv.length * 4L, FloatBuffer.wrap(pv), GL.GL_STATIC_DRAW);
            for (int k = 0; k < 3; k++) {
                gl.glEnableVertexAttribArray(k);
                gl.glVertexAttribPointer(k, 3, GL.GL_FLOAT, false, 36, k * 12L);
            }
            pCount = pv.length / 9;
            gl.glBindVertexArray(0);
        }
        ok = true;
    }

    /** Den neuesten Stand des Fadens hochladen (wenn es einen gibt). */
    void upload(GL4 gl) {
        Frame f = ready;
        if (!ok || f == null) return;
        ready = null;
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        long vb = (long) f.nv * STRIDE * 4;
        if (vb > cap) cap = (int) (vb * 3 / 2);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, cap, null, GL4.GL_STREAM_DRAW);   // verwaisen: der alte Puffer darf noch gezeichnet werden
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, vb, FloatBuffer.wrap(f.v, 0, f.nv * STRIDE));
        int ni = f.nSolid + f.nBlend + f.nGlow;
        long ib = (long) ni * 4;
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        if (ib > icap) icap = (int) (ib * 3 / 2);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, icap, null, GL4.GL_STREAM_DRAW);
        gl.glBufferSubData(GL.GL_ELEMENT_ARRAY_BUFFER, 0, ib, IntBuffer.wrap(f.idx, 0, ni));
        gl.glBindVertexArray(0);
        nSolid = f.nSolid; nBlend = f.nBlend; nGlow = f.nGlow;
        dreiecke = ni / 3;
        spare.set(f);
    }

    /** Die festen Teile (Programm strasse gebunden; uPass 0). */
    void drawSolid(GL4 gl, Shader s) {
        if (!ok || nSolid == 0) return;
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDisable(GL.GL_BLEND);
        s.set(gl, "uPass", 0);
        gl.glBindVertexArray(vao);
        gl.glDrawElements(GL.GL_TRIANGLES, nSolid, GL.GL_UNSIGNED_INT, 0);
        gl.glBindVertexArray(0);
    }

    /** Was sich mischt (uPass 1) und die Lichthöfe (uPass 2), ohne Tiefe zu schreiben. */
    void drawBlend(GL4 gl, Shader s) {
        if (!ok || nBlend + nGlow == 0) return;
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glEnable(GL.GL_BLEND);
        gl.glBindVertexArray(vao);
        if (nBlend > 0) {
            s.set(gl, "uPass", 1);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
            gl.glDrawElements(GL.GL_TRIANGLES, nBlend, GL.GL_UNSIGNED_INT, (long) nSolid * 4);
        }
        if (nGlow > 0) {
            s.set(gl, "uPass", 2);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
            gl.glDrawElements(GL.GL_TRIANGLES, nGlow, GL.GL_UNSIGNED_INT, (long) (nSolid + nBlend) * 4);
        }
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
    }

    /** Die Plattform (Programm bauten gebunden). */
    void drawPlattform(GL4 gl) {
        if (!ok || pCount == 0) return;
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(pVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, pCount);
        gl.glBindVertexArray(0);
    }

    /**
     * Phase 15: die Fahrzeuge mit Motor im Umkreis r um das Auge, nächstes zuerst, höchstens n: je {Art (Ordinalzahl von
     * {@code road.Vehicle}), x, y, z, Fahrt m/s, Beschleunigung m/s², Abstand, Kennung (seed), Richtung x, z} für den Klang. Unter der Sperre des Fadens.
     */
    public synchronized java.util.List<double[]> fahrzeugeNah(double[] eye, int n, double r) {
        java.util.List<double[]> out = new java.util.ArrayList<>();
        if (!present()) return out;
        for (Mover m : roads.traffic.movers) {
            if (m.kind.walks() || m.kind.pedals()) continue;
            double d = Math.hypot(m.x - eye[0], m.z - eye[2]);
            if (d > r) continue;
            double hl = Math.max(1e-6, Math.hypot(m.hx, m.hz));
            out.add(new double[]{m.kind.ordinal(), m.x, m.y, m.z, m.v, m.a, d, m.seed, m.hx / hl, m.hz / hl});
        }
        out.sort(java.util.Comparator.comparingDouble(a -> a[6]));
        return out.size() > n ? new java.util.ArrayList<>(out.subList(0, n)) : out;
    }

    /** Große Fahrzeuge (Bus, Lastwagen, Wohnmobil) gerade unterwegs. */
    public int busse() {
        if (!present()) return 0;
        int n = 0;
        for (Mover m : roads.traffic.movers) if (m.kind == Vehicle.BUS) n++;
        return n;
    }

    /** „Fv63 Ørnevegen: … · 37 Fahrzeuge · 41.000 Dreiecke, 4,2 ms im Faden · Begegnungsregel …“ */
    String describe() {
        if (!present()) return "Fv63: keine (ohne Gelände)";
        return String.format(Locale.GERMAN, "%s · %d Fahrzeuge · %,d Dreiecke, %.1f ms im Faden · %s%s", ornevegen.describe(), fahrzeuge, dreiecke, rechenMs,
                begegnung.describe(), plattform == null ? "" : " · " + plattform.describe());
    }

    void dispose(GL4 gl) {
        running = false;
        synchronized (lock) { lock.notifyAll(); }
        if (vao != 0) gl.glDeleteVertexArrays(2, new int[]{vao, pVao}, 0);
        if (vbo != 0) gl.glDeleteBuffers(3, new int[]{vbo, ibo, pVbo}, 0);
        vao = pVao = vbo = ibo = pVbo = 0;
        ok = false;
    }
}
