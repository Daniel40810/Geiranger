package com.dan.geiranger.gl;

import com.dan.forest.ForestField;
import com.dan.forest.gl.ForestGl;
import com.dan.geiranger.scene.Hoefe;
import com.dan.geiranger.scene.Site;
import com.dan.geiranger.scene.Terrain;
import com.dan.geiranger.scene.Vestland;
import com.dan.ground.Meadow;
import com.dan.ground.gl.GroundGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.util.Locale;

/**
 * Phase 10: Wald, Bodendecke und Höfe auf der Grafikkarte. Die Bäume kommen aus {@code com.dan.forest} ({@link ForestGl}
 * und {@link ForestField}: Kacheln zu 64 m, fünf Stufen, in der Ferne ausgedünnt), die Dichte je Art aus
 * {@link Vestland}; die Bodendecke um das Auge aus {@code com.dan.ground} ({@link Meadow} mit dem Biom „Vestland“,
 * {@link GroundGl}), nur zu Fuß (Auge bis 25 m über dem Grund). Die Häuser der Höfe ({@link Hoefe}) zeichnete bis
 * Phase 10 das Programm der Bauten, seit Phase 11 {@code timber} mit dem Ort ({@link DorfGl}). Nacharbeit 10: die
 * Waldkarte für das Kronendach im Gelände ({@link #starteKarte}). Schatten: die Schatten der Wände gehen als „Wolkenschatten von oben“ an forest und ground (sie
 * prägen den Fjord mehr als die Wolken).
 */
public final class Wald {
    public final Vestland vestland;
    public final Hoefe hoefe;
    /** Phase 11: der Ort (Bauten, Anleger, Campingplatz); hier gebaut, weil Wald und Bodendecke ihn aussparen. */
    public final com.dan.geiranger.scene.Dorf dorf;
    /** Nacharbeit 10: Kantenlänge der Waldkarte (Texel) über der Umgebung. */
    public static final int KARTE = 2048;
    private volatile byte[] karte;
    private volatile boolean karteLaeuft;
    private int karteTex;
    long karteMs;
    final ForestGl forest;
    final ForestField field;
    final GroundGl ground = new GroundGl();
    final Meadow meadow;
    /** Ebenen ein und aus (Bedienfeld). */
    public volatile boolean showForest = true, showGround = true;
    private int hVao, hVbo, hCount;
    boolean ok, groundOk;
    private int day = -1;
    private double kronen;
    String fehler = "";

    public Wald(Terrain t) {
        hoefe = Hoefe.build(t);
        dorf = com.dan.geiranger.scene.Dorf.build(t);
        vestland = new Vestland(t, hoefe, dorf);
        this.terrain = t;
        forest = new ForestGl(Vestland.arten(), 4, 1702);
        field = new ForestField(4, 4, vestland::dichte, (x, z) -> (float) t.height(x, z), 1702);
        meadow = new Meadow(Vestland.biom(), new com.dan.ground.Site() {
            @Override public float height(double x, double z) { return (float) t.height(x, z); }
            @Override public void cover(double x, double z, float[] o) { vestland.decke(x, z, o); }
        });
        meadow.radius = 16;
        meadow.near = 6;
        meadow.seed = 1702;
    }

    private final Terrain terrain;

    /**
     * Nacharbeit 10: die Waldkarte ({@link Vestland#wald} je Texel, {@link #KARTE}² über der Umgebung, rund 9 m) im
     * Hintergrund rechnen, sobald die Wandkarte da ist. Das Gelände malt damit das Kronendach, wo die Bäume in der Ferne
     * ausgedünnt sind (Fotos Geiranger1 bis 3: die Hänge sind grün, nicht grau).
     */
    void starteKarte() {
        if (karteLaeuft || !terrain.measured()) return;
        karteLaeuft = true;
        Thread th = new Thread(() -> {
            long t0 = System.nanoTime();
            double[] r = Site.UMGEBUNG.scene();
            int n = KARTE;
            byte[] b = new byte[n * n];
            java.util.stream.IntStream.range(0, n).parallel().forEach(j -> {
                double z = r[1] + (j + 0.5) * (r[3] - r[1]) / n;
                for (int i = 0; i < n; i++) {
                    double x = r[0] + (i + 0.5) * (r[2] - r[0]) / n;
                    b[j * n + i] = (byte) Math.round(255 * Math.max(0, Math.min(1, vestland.wald(x, z))));
                }
            });
            karteMs = (System.nanoTime() - t0) / 1_000_000;
            karte = b;
        }, "Waldkarte");
        th.setDaemon(true);
        th.setPriority(Thread.MIN_PRIORITY);
        th.start();
    }

    /** Textur der Waldkarte (0 = noch nicht fertig); lädt sie beim ersten Aufruf nach dem Rechnen hoch. */
    int karteTexture(GL4 gl) {
        byte[] b = karte;
        if (karteTex == 0 && b != null) {
            int[] tx = new int[1];
            gl.glGenTextures(1, tx, 0);
            karteTex = tx[0];
            gl.glActiveTexture(GL.GL_TEXTURE5);
            gl.glBindTexture(GL.GL_TEXTURE_2D, karteTex);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_R8, KARTE, KARTE, 0, GL4.GL_RED, GL.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.wrap(b));
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
            gl.glActiveTexture(GL.GL_TEXTURE0);
        }
        return karteTex;
    }

    /** Für die Prüfung: Waldkarte ohne Grafikkarte rechnen und warten. */
    public byte[] karteJetzt() throws InterruptedException {
        starteKarte();
        while (karte == null) Thread.sleep(50);
        return karte;
    }

    void quality(Quality q) {
        field.apply(q.forest());
        meadow.radius = (float) q.groundRadius();
    }

    void init(GL4 gl) {
        try {
            forest.init(gl);
            field.setHeights(forest.heights());
            ok = true;
        } catch (RuntimeException e) {
            fehler = "Wald: " + e.getMessage();
            System.err.println("Geiranger: " + fehler);
        }
        try {
            ground.init(gl);
            groundOk = true;
        } catch (RuntimeException e) {
            fehler += " Bodendecke: " + e.getMessage();
            System.err.println("Geiranger: " + fehler);
        }
        float[] e = hoefe.ecken;
        if (e.length > 0) {
            int[] a = new int[1], b = new int[1];
            gl.glGenVertexArrays(1, a, 0);
            gl.glGenBuffers(1, b, 0);
            hVao = a[0];
            hVbo = b[0];
            gl.glBindVertexArray(hVao);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, hVbo);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, e.length * 4L, FloatBuffer.wrap(e), GL.GL_STATIC_DRAW);
            for (int k = 0; k < 3; k++) {
                gl.glEnableVertexAttribArray(k);
                gl.glVertexAttribPointer(k, 3, GL.GL_FLOAT, false, 36, k * 12L);
            }
            hCount = e.length / 9;
            gl.glBindVertexArray(0);
        }
    }

    /** Je Bild vor dem Zeichnen: Jahreszeit, Wind, Auswahl der Bäume, Bodendecke um das Auge. */
    void update(GL4 gl, double[] eye, float[] vp, int dayOfYear, double windToX, double windToZ, double windSpeed, double gust, float t, boolean zuFuss) {
        update(gl, eye, vp, dayOfYear, windToX, windToZ, windSpeed, gust, t, zuFuss, 0, 0);
    }

    /** Phase 14: dazu Schnee in den Kronen (0..1) und am Boden um das Auge (0..1) aus dem Jahr. */
    void update(GL4 gl, double[] eye, float[] vp, int dayOfYear, double windToX, double windToZ, double windSpeed, double gust, float t, boolean zuFuss,
                double kronenSchnee, double bodenSchnee) {
        if (ok && showForest) {
            if (dayOfYear != day || Math.abs(kronenSchnee - kronen) > 0.05) { forest.setSeason(dayOfYear, (float) kronenSchnee); day = dayOfYear; kronen = kronenSchnee; }
            forest.setWind(windToX, windToZ, windSpeed, gust);
            field.update(eye, vp, 24);
            forest.update(gl, field);
        }
        if (groundOk) {
            meadow.setSeason(dayOfYear, (float) bodenSchnee);
            meadow.wind.speed = (float) windSpeed;
            meadow.wind.direction = (float) Math.toDegrees(Math.atan2(windToZ, windToX));
            ground.update(gl, meadow, t, eye[0], eye[2], !(zuFuss && showGround));
        }
    }

    void drawForest(GL4 gl, ForestGl.View v, ForestGl.Light l, double t) {
        if (ok && showForest) forest.draw(gl, v, l, t);
    }

    void drawGround(GL4 gl, GroundGl.View v, GroundGl.Light l) {
        if (groundOk && showGround) ground.draw(gl, v, l);
    }

    /** Die Häuser der Höfe (Programm bauten gebunden). */
    void drawHoefe(GL4 gl) {
        if (hCount == 0) return;
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(hVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, hCount);
        gl.glBindVertexArray(0);
    }

    String describe() {
        return String.format(Locale.GERMAN, "%s · %s · %s · %s · %s%s", vestland.describe(), ok ? forest.info() : "Wald aus", groundOk ? ground.info() : "Bodendecke aus",
                hoefe.describe(), karte == null ? (karteLaeuft ? "Waldkarte wird gerechnet" : "Waldkarte wartet auf die Wandkarte")
                        : String.format(Locale.GERMAN, "Waldkarte %d² (%d ms)", KARTE, karteMs), fehler.isEmpty() ? "" : " · !" + fehler);
    }

    void dispose(GL4 gl) {
        if (ok) forest.dispose(gl);
        if (groundOk) ground.dispose(gl);
        if (hVao != 0) gl.glDeleteVertexArrays(1, new int[]{hVao}, 0);
        if (hVbo != 0) gl.glDeleteBuffers(1, new int[]{hVbo}, 0);
        if (karteTex != 0) gl.glDeleteTextures(1, new int[]{karteTex}, 0);
        karteTex = 0;
        ok = groundOk = false;
    }
}
