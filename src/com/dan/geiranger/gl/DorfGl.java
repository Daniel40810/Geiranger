package com.dan.geiranger.gl;

import com.dan.geiranger.scene.Dorf;
import com.dan.geiranger.scene.Hoefe;
import com.dan.timber.Mesh;
import com.dan.timber.Timber;
import com.dan.timber.gl.TimberGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.util.Locale;

/**
 * Phase 11: der Ort auf der Grafikkarte. Die gestrichenen Häuser, Hotels, Kirche, Wohnwagen und Laternen zeichnet das
 * Programm der Bauten ({@code bauten.frag} mit Verschalung, Fenstern und Lampen; auch im Spiegelbild); Naust, Anleger
 * und die Höfe Knivsflå und Skageflå zeichnet {@link TimberGl} als ein festes Netz ({@code setStatic}, vergraut:
 * {@link #ALTER}); die Lichthöfe der Laternen kommen nachts als Punkte, die zum Bild addiert werden ({@code glow}).
 */
public final class DorfGl {
    /** Alter des Holzes für timber (0 neu … 1 verwittert): die Bohlen sind silbergrau wie auf den Fotos. */
    public static final double ALTER = 0.85;
    public final Dorf dorf;
    final Hoefe hoefe;
    final Timber timber = new Timber();
    final TimberGl timberGl = new TimberGl();
    private int vao, vbo, count, gVao, gVbo, gCount;
    boolean ok, holzOk;
    /** Ebene ein und aus (Bedienfeld). */
    public volatile boolean show = true;
    String fehler = "";
    private int holzDreiecke;

    public DorfGl(Dorf d, Hoefe h) {
        dorf = d;
        hoefe = h;
        timber.quality = Timber.Quality.MEDIUM;
    }

    /** Phase 18: die Stufe von timber; ändert sie sich, wird das feste Netz beim nächsten Bild neu gebaut. */
    void quality(Timber.Quality q) {
        if (q == timber.quality) return;
        timber.quality = q;
        neu = true;
    }

    private volatile boolean neu;

    /** Baut das feste Netz der Höfe neu, wenn die Stufe wechselte (im GL-Faden vor dem Zeichnen). */
    void pruefen(GL4 gl) {
        if (!neu || !holzOk) return;
        neu = false;
        try {
            Mesh m = new Mesh();
            if (hoefe != null && hoefe.holz != null) m.append(hoefe.holz);
            m.append(dorf.holz);
            holzDreiecke = m.triangles();
            timberGl.setStatic(gl, m, ALTER);
        } catch (RuntimeException ex) {
            fehler = "timber: " + ex.getMessage();
        }
    }

    void init(GL4 gl) {
        float[] e = dorf.ecken;
        if (e.length > 0) {
            int[] a = new int[1], b = new int[1];
            gl.glGenVertexArrays(1, a, 0);
            gl.glGenBuffers(1, b, 0);
            vao = a[0];
            vbo = b[0];
            gl.glBindVertexArray(vao);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, e.length * 4L, FloatBuffer.wrap(e), GL.GL_STATIC_DRAW);
            for (int k = 0; k < 3; k++) {
                gl.glEnableVertexAttribArray(k);
                gl.glVertexAttribPointer(k, 3, GL.GL_FLOAT, false, 36, k * 12L);
            }
            count = e.length / 9;
        }
        if (!dorf.lampen.isEmpty()) {
            float[] p = new float[dorf.lampen.size() * 3];
            for (int i = 0; i < dorf.lampen.size(); i++) {
                double[] l = dorf.lampen.get(i);
                p[3 * i] = (float) l[0];
                p[3 * i + 1] = (float) l[1];
                p[3 * i + 2] = (float) l[2];
            }
            int[] a = new int[1], b = new int[1];
            gl.glGenVertexArrays(1, a, 0);
            gl.glGenBuffers(1, b, 0);
            gVao = a[0];
            gVbo = b[0];
            gl.glBindVertexArray(gVao);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, gVbo);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, p.length * 4L, FloatBuffer.wrap(p), GL.GL_STATIC_DRAW);
            gl.glEnableVertexAttribArray(0);
            gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 12, 0);
            gCount = dorf.lampen.size();
        }
        gl.glBindVertexArray(0);
        ok = true;
        try {
            timberGl.init(gl, timber);
            Mesh m = new Mesh();
            if (hoefe != null && hoefe.holz != null) m.append(hoefe.holz);
            m.append(dorf.holz);
            holzDreiecke = m.triangles();
            timberGl.setStatic(gl, m, ALTER);
            holzOk = true;
        } catch (RuntimeException ex) {
            fehler = "timber: " + ex.getMessage();
            System.err.println("Geiranger: " + fehler);
        }
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }

    /** Die gestrichenen Bauten (Programm bauten gebunden, Uniforms gesetzt). */
    void drawBauten(GL4 gl) {
        if (count == 0 || !show) return;
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, count);
        gl.glBindVertexArray(0);
    }

    /** Naust, Anleger, Höfe aus timber. */
    void drawHolz(GL4 gl, TimberGl.View v, TimberGl.Light l) {
        if (!holzOk || !show) return;
        pruefen(gl);
        timberGl.draw(gl, timber, java.util.List.of(), v, l);
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }

    /** Lichthöfe der Laternen (Programm glow gebunden): addiert, ohne Tiefe zu schreiben. */
    void drawGlow(GL4 gl) {
        if (gCount == 0 || !show) return;
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glEnable(GL4.GL_PROGRAM_POINT_SIZE);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
        gl.glBindVertexArray(gVao);
        gl.glDrawArrays(GL.GL_POINTS, 0, gCount);
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL4.GL_PROGRAM_POINT_SIZE);
        gl.glDepthMask(true);
    }

    String describe() {
        return String.format(Locale.GERMAN, "%s · timber %,d Dreiecke (Höfe, Naust, Anleger)%s", dorf.describe(), holzDreiecke,
                fehler.isEmpty() ? "" : " · !" + fehler);
    }

    void dispose(GL4 gl) {
        if (holzOk) timberGl.dispose(gl);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        if (vbo != 0) gl.glDeleteBuffers(1, new int[]{vbo}, 0);
        if (gVao != 0) gl.glDeleteVertexArrays(1, new int[]{gVao}, 0);
        if (gVbo != 0) gl.glDeleteBuffers(1, new int[]{gVbo}, 0);
        vao = vbo = gVao = gVbo = 0;
        ok = holzOk = false;
    }
}
