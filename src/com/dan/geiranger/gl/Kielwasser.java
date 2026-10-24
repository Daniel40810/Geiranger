package com.dan.geiranger.gl;

import com.dan.geiranger.scene.Flotte;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 12: das Kielwasser der Schiffe. {@code ocean} kennt keine Bugwelle und kein Kielwasser (nur Ringwellen und einen
 * Schaumsaum am Rumpf); dafür nimmt es eine „lange Welle“ als Höhenfeld mit Lage ({@code Light.longTexture0},
 * {@code longRect0}) und zeichnet ihre Höhe, Neigung und an steilen Kämmen Schaum. Dieses Feld rechnet die Grafikkarte
 * hier als Wellen tiefen Wassers (iWave nach Tessendorf, {@code kielwasser.comp}): ein Gitter von {@link #N}² Zellen
 * zu {@link #ZELLE} m (1,5 km) um das Auge, in festen Schritten von {@link #DT} s nach der Uhr. Der Kern (13 × 13) ist so
 * gefittet, dass seine Antwort |k| ist; damit läuft jede Welle mit der Geschwindigkeit tiefen Wassers, und ein
 * fahrender Rumpf zieht das Kelvin-Muster mit 19,5° halbem Winkel, schrägen und queren Wellen hinter sich her. Die Ufer
 * werfen es zurück, flaches Wasser dämpft es.
 * <p>
 * Die Schiffe fahren nach der Uhr ({@link Flotte#stelle}); darum kann das Feld seine Vorgeschichte nachrechnen: beim
 * Start und nach einem Sprung des Auges (Ansicht) die letzten {@link #VORLAUF} s, höchstens {@link #JE_BILD} Schritte
 * je Bild. Zieht das Auge weiter, wird das Gitter um ganze Zellen geschoben.
 * <p>
 * Nacharbeit 12 (Status_Phase12a): Das Verfahren (Leapfrog: h' = 2h − h_alt − …) setzt einen gleichen Schritt voraus.
 * Bis Phase 12 lief je Bild ein Schritt so lang wie das Bild (0,1 s höchstens); im Lauf schwankt die Bildzeit, die
 * Geschwindigkeit h − h_alt passt dann nicht mehr zum Schritt, und das Feld schaukelt sich in 40 bis 50 s bis an die
 * Grenze von ± 3 m auf (rund um das Kreuzfahrtschiff: Brecher bei Bft 2). Jetzt rechnet es nur ganze Schritte von
 * {@link #DT}; der Rest wartet bis zum nächsten Bild ({@link #schritte}). Nacharbeit 13 (pruefen_menschen.txt): nach einem Sprung
 * kostete das Nachrechnen von 180 s rund 7 ms je Bild über 1,5 s (Durchgang „Schiffe“); jetzt 90 s Vorlauf.
 */
public final class Kielwasser {
    public static final int N = 512;
    public static final double ZELLE = 3.0, DT = 1.0 / 30, ALPHA = 0.008, G = 9.81, VORLAUF = 90;
    public static final int JE_BILD = 60, SCHIEBEN = 64;
    /**
     * Der Kern nach Abstand (a ≥ b, Index a(a+1)/2 + b): kleinste Quadrate über die Frequenzen |k| ≤ 2,6 (Zelle als
     * Einheit) mit Antwort |k|, dazu Antwort 0 bei k = 0 (Gewicht 200). Von |k| = 0,5 bis 2,5 trifft er |k| auf
     * 6 %, darunter ist er zu klein (Wellen über rund 40 m laufen langsamer als im tiefen Wasser).
     */
    public static final float[] KERN = {
            4.5300184f,
            -2.0103999f, 1.0213067f,
            0.5317759f, -0.1885089f, -0.3950742f,
            -0.028742f, -0.1758959f, 0.4548331f, -0.4093257f,
            0.0611207f, 0.0258803f, -0.1879371f, 0.1567787f, -0.0469262f,
            -0.0937765f, 0.0520866f, 0.0220544f, -0.0292643f, 0f, 0f,
            0.0306165f, -0.0265537f, 0f, 0f, 0f, 0f, 0f};

    final Shader schritt = new Shader("Kielwasser", null, "kielwasser.comp"), schieben = new Shader("Kielwasser schieben", null, "kielwasser_schieben.comp");
    private final int[] tex = new int[4];
    private int prev = 0, cur = 1, next = 2, frei = 3;
    double x0, z0, zeit = Double.NaN;
    boolean ok;
    public volatile boolean on = true;
    /** Phase 18: die Qualitätsstufe lässt das Kielwasser rechnen (auf „Niedrig“ aus); {@link #on} ist der Schalter im Bedienfeld. */
    public volatile boolean stufe = true;
    String fehler = "";
    int schritte, nachzuholen;
    double letzteMs;

    void init(GL4 gl) {
        try {
            schritt.build(gl);
            schieben.build(gl);
            gl.glGenTextures(4, tex, 0);
            for (int t : tex) {
                gl.glBindTexture(GL.GL_TEXTURE_2D, t);
                gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_R32F, N, N);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            }
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            for (int t = 0; t < 4; t++) loeschen(gl, t);
            ok = true;
        } catch (RuntimeException e) {
            fehler = e.getMessage();
            System.err.println("Geiranger: Kielwasser aus – " + fehler);
        }
    }

    private void loeschen(GL4 gl, int i) {
        kopiere(gl, i, i == 0 ? 1 : 0, N * 4, N * 4);
    }

    /** Ziel dst = Quelle src um (dx, dy) Zellen versetzt; außerhalb 0. */
    private void kopiere(GL4 gl, int dst, int src, int dx, int dy) {
        schieben.use(gl);
        gl.glBindImageTexture(0, tex[src], 0, false, 0, GL4.GL_READ_ONLY, GL4.GL_R32F);
        gl.glBindImageTexture(1, tex[dst], 0, false, 0, GL4.GL_WRITE_ONLY, GL4.GL_R32F);
        gl.glUniform2i(schieben.loc(gl, "uOffset"), dx, dy);
        schieben.set(gl, "uN", N);
        gl.glDispatchCompute(N / 16, N / 16, 1);
        gl.glMemoryBarrier(GL4.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL4.GL_TEXTURE_FETCH_BARRIER_BIT);
    }

    /** Textur mit der Höhe des Kielwassers (Zelle i, j hat ihre Mitte bei x0 + (i + 0,5) · ZELLE) oder 0. */
    int texture() { return ok && on && stufe && !Double.isNaN(zeit) ? tex[cur] : 0; }

    /** Rechteck für ocean: x0, z0, Zelle, an. */
    double[] rect() { return new double[]{x0, z0, ZELLE, texture() != 0 ? 1 : 0}; }

    /**
     * Je Bild: Gitter um das Auge legen oder schieben, bis zur Uhrzeit nachrechnen. epoch in Sekunden; renderer bindet
     * die Höhen des Geländes (Land, Tiefe) vorher an das Programm.
     */
    void update(GL4 gl, TerrainLod lod, Flotte flotte, double epoch, double camX, double camZ, double level) {
        if (!ok || !on || !stufe || flotte == null) return;
        long t0 = System.nanoTime();
        double half = N * ZELLE / 2, snap = 32 * ZELLE;
        double nx0 = Math.floor((camX - half) / snap) * snap, nz0 = Math.floor((camZ - half) / snap) * snap;
        int dx = (int) Math.round((nx0 - x0) / ZELLE), dz = (int) Math.round((nz0 - z0) / ZELLE);
        boolean neu = Double.isNaN(zeit) || Math.abs(dx) >= N / 2 || Math.abs(dz) >= N / 2 || epoch < zeit - 1 || epoch - zeit > 600;
        if (neu) {
            for (int t = 0; t < 4; t++) loeschen(gl, t);
            x0 = nx0;
            z0 = nz0;
            zeit = epoch - VORLAUF;
        } else if (Math.abs(dx) >= SCHIEBEN || Math.abs(dz) >= SCHIEBEN) {
            kopiere(gl, frei, prev, dx, dz);
            int a = frei; frei = prev; prev = a;
            kopiere(gl, frei, cur, dx, dz);
            a = frei; frei = cur; cur = a;
            x0 += dx * ZELLE;
            z0 += dz * ZELLE;
        }
        // nachrechnen bis zur Uhr
        schritt.use(gl);
        lod.bind(gl, schritt);
        schritt.set(gl, "uLevel", (float) level);
        schritt.set(gl, "uWin", (float) x0, (float) z0, (float) ZELLE, (float) N);
        gl.glUniform1fv(schritt.loc(gl, "uKern[0]"), KERN.length, KERN, 0);
        int k = 0, n = Math.min(JE_BILD, schritte(zeit, epoch));
        double dt = DT;
        schritt.set(gl, "uStep", (float) dt, (float) ALPHA, (float) (G * dt * dt / ZELLE));
        while (k < n) {
            flotte.stelle(zeit + dt);
            schiffe(gl, flotte, camX, camZ);
            gl.glBindImageTexture(0, tex[prev], 0, false, 0, GL4.GL_READ_ONLY, GL4.GL_R32F);
            gl.glBindImageTexture(1, tex[cur], 0, false, 0, GL4.GL_READ_ONLY, GL4.GL_R32F);
            gl.glBindImageTexture(2, tex[next], 0, false, 0, GL4.GL_WRITE_ONLY, GL4.GL_R32F);
            gl.glDispatchCompute(N / 16, N / 16, 1);
            gl.glMemoryBarrier(GL4.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL4.GL_TEXTURE_FETCH_BARRIER_BIT);
            int a = prev; prev = cur; cur = next; next = a;
            zeit += dt;
            k++;
        }
        flotte.stelle(epoch);
        schritte = k;
        nachzuholen = schritte(zeit, epoch);
        gl.glUseProgram(0);
        letzteMs = (System.nanoTime() - t0) / 1e6;
    }

    /** Ganze Schritte von {@link #DT}, die von zeit bis epoch passen (der Rest wartet; kein kürzerer Schritt). */
    public static int schritte(double zeit, double epoch) {
        return epoch <= zeit ? 0 : (int) Math.floor((epoch - zeit) / DT + 1e-6);
    }

    /** Die (höchstens acht) Schiffe im Gitter mit Rand als Uniforms. */
    private void schiffe(GL4 gl, Flotte flotte, double camX, double camZ) {
        List<Flotte.Schiff> l = new ArrayList<>();
        double r = N * ZELLE / 2 + 300;
        for (Flotte.Schiff s : flotte.schiffe) if (s.da && Math.abs(s.x - camX) < r && Math.abs(s.z - camZ) < r) l.add(s);
        l.sort((p, q) -> Double.compare(Math.hypot(p.x - camX, p.z - camZ), Math.hypot(q.x - camX, q.z - camZ)));
        int n = Math.min(8, l.size());
        float[] a = new float[32], b = new float[32];
        for (int i = 0; i < n; i++) {
            Flotte.Schiff s = l.get(i);
            double h = Math.toRadians(s.kurs);
            a[4 * i] = (float) s.x; a[4 * i + 1] = (float) s.z; a[4 * i + 2] = (float) Math.sin(h); a[4 * i + 3] = (float) Math.cos(h);
            b[4 * i] = (float) s.hl; b[4 * i + 1] = (float) s.hw; b[4 * i + 2] = (float) s.delle; b[4 * i + 3] = (float) s.bug;
        }
        schritt.set(gl, "uShips", n);
        schritt.vec4Array(gl, "uShipA", a);
        schritt.vec4Array(gl, "uShipB", b);
    }

    String describe() {
        if (!ok) return "Kielwasser aus" + (fehler.isEmpty() ? "" : " (" + fehler + ")");
        return String.format(Locale.GERMAN, "Kielwasser %d² zu %.0f m um das Auge, %d Schritte in diesem Bild%s, %.2f ms Prozessor", N, ZELLE, schritte,
                nachzuholen > 0 ? String.format(Locale.GERMAN, " (noch %d nachzuholen)", nachzuholen) : "", letzteMs);
    }

    void dispose(GL4 gl) {
        if (ok) gl.glDeleteTextures(4, tex, 0);
        schritt.dispose(gl);
        schieben.dispose(gl);
        ok = false;
    }
}
