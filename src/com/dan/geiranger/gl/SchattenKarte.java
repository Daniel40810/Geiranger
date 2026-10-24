package com.dan.geiranger.gl;

import com.dan.geiranger.scene.Terrain;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.util.Locale;

/**
 * Phase 4 (F19 A): die Schatten der Wände als zwei Karten (R8, Anteil direkten Lichts): über dem Hauptraster in
 * {@code n}² Texeln, über dem Fernring in 256² ({@code schatten.frag}). Neu gerechnet wird, sobald sich die Richtung
 * zur Lichtquelle um mehr als {@link #TURN} gedreht hat, und zwar in Streifen zu 256 Zeilen je Bild in eine zweite
 * Karte; ist sie fertig, tauschen beide. Im Zeitraffer läuft der Schatten so ein, zwei Bilder hinter der Sonne her,
 * kostet aber nie mehr als einen Streifen je Bild.
 */
public final class SchattenKarte {
    /** Drehung der Lichtquelle (rad), ab der neu gerechnet wird (0,1°). */
    public static final double TURN = 0.0017;
    public static final int FERN_N = 256, BAND = 256;

    private final int[] main = new int[2], fern = new int[2], mainFbo = new int[2], fernFbo = new int[2];
    private int n, front, band = -1, bands;
    private double[] key, pending;
    /** Zahl der fertigen Durchläufe (für HUD und Prüfung). */
    public int cycles;
    /** Zeit des letzten vollen Durchlaufs auf der Grafikkarte ist nicht gemessen; die Pass-Zeit „Schatten“ zeigt die Last je Bild. */
    public boolean on = true;

    /** Karten in der Größe n (Hauptraster) anlegen oder neu anlegen. */
    void size(GL4 gl, int size) {
        if (size == n && main[0] != 0) return;
        dispose(gl);
        n = size;
        for (int i = 0; i < 2; i++) {
            main[i] = tex(gl, n);
            fern[i] = tex(gl, FERN_N);
            mainFbo[i] = Gpu.fbo(gl, main[i], 0);
            fernFbo[i] = Gpu.fbo(gl, fern[i], 0);
            clear(gl, mainFbo[i]);
            clear(gl, fernFbo[i]);
        }
        key = null;
        band = -1;
        bands = Math.max(1, n / BAND);
    }

    private static int tex(GL4 gl, int s) {
        int t = Gpu.texture(gl, s, s, GL.GL_R8, false);
        return t;
    }

    private static void clear(GL4 gl, int fbo) {
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glClearColor(1, 1, 1, 1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
    }

    /**
     * Einen Schritt: beginnt bei gedrehter Lichtquelle einen neuen Durchlauf, rechnet den nächsten Streifen.
     * Rückgabe: ob gerechnet wurde. Der Shader ist schon mit Gelände (lod.bind) und common versehen.
     */
    boolean step(GL4 gl, Shader s, int emptyVao, double[] dir) {
        if (!on || n == 0) return false;
        if (band < 0) {
            if (key != null && angle(key, dir) < TURN) return false;
            pending = dir.clone();
            band = 0;
        }
        int back = 1 - front;
        s.set(gl, "uKey", pending);
        gl.glBindVertexArray(emptyVao);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        if (band == 0) {
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fernFbo[back]);
            gl.glViewport(0, 0, FERN_N, FERN_N);
            double fs = Terrain.FN * Terrain.FCELL;
            s.set(gl, "uShadowRect", (float) Terrain.FX0, (float) Terrain.FZ0, (float) fs, (float) (fs / FERN_N));
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, mainFbo[back]);
        int rows = n / bands;
        gl.glViewport(0, band * rows, n, rows);
        double ms = Terrain.N * Terrain.CELL;
        s.set(gl, "uShadowRect", (float) Terrain.X0, (float) Terrain.Z0, (float) ms, (float) (ms / n));
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        band++;
        if (band >= bands) {
            front = back;
            key = pending;
            band = -1;
            cycles++;
        }
        return true;
    }

    /** Sofort neu rechnen (nach Sprüngen der Uhr): der nächste Schritt beginnt einen Durchlauf. */
    public void invalidate() { key = null; band = -1; }

    public int mainTexture() { return main[front]; }
    public int fernTexture() { return fern[front]; }
    public double[] mainRect() { return new double[]{Terrain.X0, Terrain.Z0, Terrain.N * Terrain.CELL}; }
    public double[] fernRect() { return new double[]{Terrain.FX0, Terrain.FZ0, Terrain.FN * Terrain.FCELL}; }
    public boolean ready() { return key != null; }

    /** „1024² + 256², 4 Streifen je Durchlauf, 12 Durchläufe“ */
    public String describe() {
        return !on ? "Schatten der Wände aus" : String.format(Locale.GERMAN, "Schatten der Wände %d² (%.0f m) + Fernring %d², %d Streifen je Durchlauf, %d Durchläufe",
                n, Terrain.N * Terrain.CELL / Math.max(1, n), FERN_N, bands, cycles);
    }

    static double angle(double[] a, double[] b) {
        double d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        return Math.acos(Math.max(-1, Math.min(1, d)));
    }

    void dispose(GL4 gl) {
        Gpu.deleteFbos(gl, mainFbo[0], mainFbo[1], fernFbo[0], fernFbo[1]);
        Gpu.deleteTextures(gl, main[0], main[1], fern[0], fern[1]);
        for (int i = 0; i < 2; i++) main[i] = fern[i] = mainFbo[i] = fernFbo[i] = 0;
        n = 0;
    }
}
