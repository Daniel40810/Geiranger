package com.dan.geiranger.gl;

import com.dan.geiranger.scene.Flotte;
import com.dan.ocean.HullMask;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 12: die Schiffe auf der Grafikkarte. Je Schiff ein Netz im Format der Bauten (einmal hochgeladen), gezeichnet
 * mit {@code schiff.vert} und der Modellmatrix aus {@code com.dan.buoyancy}; Licht, Fenster und Lampen aus
 * {@code bauten.frag}. Dazu die Rümpfe für {@code ocean} ({@link HullMask}: kein Wasser im Rumpf, Schaumsaum) und das
 * Kielwasser ({@link Kielwasser}).
 */
public final class FlotteGl {
    public final Flotte flotte;
    public final Kielwasser kielwasser = new Kielwasser();
    private int[] vao, vbo, count;
    boolean ok;
    /** Ebene ein und aus (Bedienfeld). */
    public volatile boolean show = true;
    int gezeichnet;

    public FlotteGl(Flotte f) { flotte = f; }

    void init(GL4 gl) {
        int n = flotte.schiffe.size();
        vao = new int[n];
        vbo = new int[n];
        count = new int[n];
        gl.glGenVertexArrays(n, vao, 0);
        gl.glGenBuffers(n, vbo, 0);
        for (int i = 0; i < n; i++) {
            float[] e = flotte.schiffe.get(i).netz;
            gl.glBindVertexArray(vao[i]);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo[i]);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, e.length * 4L, FloatBuffer.wrap(e), GL.GL_STATIC_DRAW);
            for (int k = 0; k < 3; k++) {
                gl.glEnableVertexAttribArray(k);
                gl.glVertexAttribPointer(k, 3, GL.GL_FLOAT, false, 36, k * 12L);
            }
            count[i] = e.length / 9;
        }
        gl.glBindVertexArray(0);
        kielwasser.init(gl);
        ok = true;
    }

    /** Die Rümpfe für ocean: die acht nächsten Schiffe (ohne Kajaks: zu klein für das Gitter des Wassers). */
    List<HullMask> huellen(double[] eye) {
        List<Flotte.Schiff> l = new ArrayList<>();
        if (show)
            for (Flotte.Schiff s : flotte.schiffe) if (s.da && s.art != Flotte.Art.KAJAK && Math.hypot(s.x - eye[0], s.z - eye[2]) < 6000) l.add(s);
        l.sort((p, q) -> Double.compare(Math.hypot(p.x - eye[0], p.z - eye[2]), Math.hypot(q.x - eye[0], q.z - eye[2])));
        List<HullMask> out = new ArrayList<>();
        for (int i = 0; i < Math.min(8, l.size()); i++) {
            Flotte.Schiff s = l.get(i);
            out.add(new HullMask(s.x, s.z, s.kurs, s.hl, s.hw, s.art == Flotte.Art.FAEHRE ? 2.4 : 2.2));
        }
        return out;
    }

    /** Alle Schiffe in der Nähe zeichnen (Programm schiff gebunden, Uniforms gesetzt). */
    void draw(GL4 gl, Shader s, double[] eye) {
        gezeichnet = 0;
        if (!ok || !show) return;
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_CULL_FACE);
        float[] m = new float[16];
        for (int i = 0; i < count.length; i++) {
            Flotte.Schiff sc = flotte.schiffe.get(i);
            if (!sc.da) continue;
            double d = Math.hypot(sc.x - eye[0], sc.z - eye[2]);
            if (d > (sc.art == Flotte.Art.KAJAK ? 2500 : sc.art == Flotte.Art.SCHNELLBOOT ? 6000 : 25000)) continue;
            double[] md = sc.model();
            for (int k = 0; k < 16; k++) m[k] = (float) md[k];
            gl.glUniformMatrix4fv(s.loc(gl, "uModel"), 1, false, m, 0);
            gl.glBindVertexArray(vao[i]);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, count[i]);
            gezeichnet++;
        }
        gl.glBindVertexArray(0);
    }

    String describe() {
        return String.format(Locale.GERMAN, "%s · %d gezeichnet · %s", flotte.describe(), gezeichnet, kielwasser.describe());
    }

    void dispose(GL4 gl) {
        if (ok) {
            gl.glDeleteVertexArrays(vao.length, vao, 0);
            gl.glDeleteBuffers(vbo.length, vbo, 0);
        }
        kielwasser.dispose(gl);
        ok = false;
    }
}
