package com.dan.geiranger.gl;

import com.dan.geiranger.scene.Bauten;
import com.dan.geiranger.scene.Elva;
import com.dan.geiranger.scene.Terrain;
import com.dan.river.FlowField;
import com.dan.river.RiverPath;
import com.dan.river.Rock;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 8: die Geirangelva und die Bauten an ihrem Ufer auf der Grafikkarte. Die Strömung rechnet {@code com.dan.river}
 * auf dem Prozessor ({@link FlowField} je Strecke, mit den Steinen aus {@link Bauten}); je Ecke eines Bands über die
 * Breite des Flusses (alle 2,5 m eine Reihe zu 13 Ecken, bis 30 % über das Ufer hinaus) gehen Spiegel, Strömung,
 * Schaum, Tiefe und Nässe an {@code elva.vert/frag}. Neu gerechnet wird, wenn sich der Abfluss um mehr als 8 % ändert.
 * {@code com.dan.river} hat (noch) keinen eigenen Zeichner; dieser gehört zur App und kann in Phase 18 als
 * {@code river.gl} nach {@code Renderer.jar}.
 */
public final class Fluss {
    /** Ecken je Reihe und wie weit das Band über die halbe Breite hinausreicht. */
    public static final int COLS = 13;
    public static final double OVER = 1.3;

    private final Elva elva;
    public final Bauten bauten;
    private final List<FlowField> fields = new ArrayList<>();
    private final List<Elva.Strecke> strecken = new ArrayList<>();
    private int vao, vbo, ibo, count, bVao, bVbo, bCount;
    private double builtQ = -1;
    /** Abfluss der Elva (m³/s), zuletzt gerechnet; Anteil nasser Ecken. */
    public volatile double q, wetShare;
    boolean ok;

    public Fluss(Terrain t) {
        elva = t.elva;
        bauten = Bauten.build(t);
        if (elva != null)
            for (Elva.Strecke s : elva.strecken) {
                RiverPath p = new RiverPath(elva.riverPoints(s), Elva.SCHRITT, 14);
                FlowField f = new FlowField(p);
                for (double[] r : bauten.steine)
                    if (p.locate(r[0], r[1], new RiverPath.Loc())) f.add(new Rock(r[0], r[1], r[2], r[3]));
                fields.add(f);
                strecken.add(s);
            }
    }

    public boolean present() { return elva != null && !strecken.isEmpty(); }

    /**
     * Die Ecken des Bands für den Abfluss q (m³/s): je Ecke x, y, z, vx, vz, Schaum, Tiefe, nass (8 Werte), und die
     * Dreiecke. Ohne Grafikkarte (Prüfung).
     */
    public Object[] mesh(double q) {
        List<float[]> verts = new ArrayList<>();
        List<int[]> tris = new ArrayList<>();
        FlowField.Flow f = new FlowField.Flow();
        int base = 0, wet = 0, all = 0;
        for (int k = 0; k < strecken.size(); k++) {
            Elva.Strecke s = strecken.get(k);
            FlowField ff = fields.get(k);
            ff.discharge = (float) (q / Elva.MITTEL);
            int rows = s.i1() - s.i0() + 1;
            for (int i = s.i0(); i <= s.i1(); i++)
                for (int c = 0; c < COLS; c++) {
                    double u = (c / (COLS - 1.0) * 2 - 1) * OVER * elva.half[i];
                    double x = elva.x[i] - elva.dz[i] * u, z = elva.z[i] + elva.dx[i] * u;
                    float[] v = new float[8];
                    v[0] = (float) x;
                    v[2] = (float) z;
                    boolean hit = ff.sample(x, z, f);
                    if (hit && f.wet) {
                        v[1] = f.level;
                        v[3] = f.vx;
                        v[4] = f.vz;
                        v[5] = Math.min(1f, Math.max(f.foam, f.turb * 0.8f));
                        v[6] = f.depth;
                        v[7] = (float) (1 - smooth(0.8, 1.0, Math.abs(f.u)));
                        wet++;
                    } else {
                        v[1] = (float) elva.level[i];
                        v[6] = 0;
                        v[7] = 0;
                    }
                    all++;
                    verts.add(v);
                }
            for (int r = 0; r < rows - 1; r++)
                for (int c = 0; c < COLS - 1; c++) {
                    int a = base + r * COLS + c, b = a + 1, d = a + COLS, e = d + 1;
                    tris.add(new int[]{a, d, e});
                    tris.add(new int[]{a, e, b});
                }
            base += rows * COLS;
        }
        float[] vf = new float[verts.size() * 8];
        for (int i = 0; i < verts.size(); i++) System.arraycopy(verts.get(i), 0, vf, i * 8, 8);
        int[] idx = new int[tris.size() * 3];
        for (int i = 0; i < tris.size(); i++) System.arraycopy(tris.get(i), 0, idx, i * 3, 3);
        wetShare = all == 0 ? 0 : wet / (double) all;
        return new Object[]{vf, idx};
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    void init(GL4 gl) {
        if (!present()) return;
        int[] t = new int[2];
        gl.glGenVertexArrays(2, t, 0);
        vao = t[0];
        bVao = t[1];
        int[] b = new int[3];
        gl.glGenBuffers(3, b, 0);
        vbo = b[0];
        ibo = b[1];
        bVbo = b[2];
        float[] bv = bauten.ecken;
        gl.glBindVertexArray(bVao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, bVbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, Math.max(4, bv.length) * 4L, bv.length == 0 ? null : FloatBuffer.wrap(bv), GL.GL_STATIC_DRAW);
        for (int k = 0; k < 3; k++) {
            gl.glEnableVertexAttribArray(k);
            gl.glVertexAttribPointer(k, 3, GL.GL_FLOAT, false, 36, k * 12L);
        }
        bCount = bv.length / 9;
        gl.glBindVertexArray(0);
        ok = true;
    }

    /** Neu rechnen, wenn sich der Abfluss um mehr als 8 % geändert hat (im GL-Faden). */
    void update(GL4 gl, double qNow) {
        if (!ok) return;
        if (builtQ >= 0 && Math.abs(qNow - builtQ) <= 0.08 * Math.max(0.5, builtQ)) return;
        Object[] m = mesh(Math.max(0.2, qNow));
        float[] v = (float[]) m[0];
        int[] idx = (int[]) m[1];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, v.length * 4L, FloatBuffer.wrap(v), GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, idx.length * 4L, IntBuffer.wrap(idx), GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 32, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 4, GL.GL_FLOAT, false, 32, 12);
        gl.glEnableVertexAttribArray(2);
        gl.glVertexAttribPointer(2, 1, GL.GL_FLOAT, false, 32, 28);
        gl.glBindVertexArray(0);
        count = idx.length;
        builtQ = qNow;
        q = qNow;
    }

    /** Das Wasser (Programm elva gebunden, Uniforms gesetzt): Tiefentest, ohne Tiefe zu schreiben, vormultipliziert. */
    void drawWater(GL4 gl) {
        if (!ok || count == 0) return;
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glBindVertexArray(vao);
        gl.glDrawElements(GL.GL_TRIANGLES, count, GL.GL_UNSIGNED_INT, 0);
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
    }

    /** Die Bauten (Programm bauten gebunden, Uniforms gesetzt): Tiefe schreiben. */
    void drawBauten(GL4 gl) {
        if (!ok || bCount == 0) return;
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(bVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, bCount);
        gl.glBindVertexArray(0);
    }

    /** „Geirangelva: 3 Strecken, 1 340 Ecken, 92 % nass · 23,8 m³/s · Treppe …“ */
    String describe() {
        if (!present()) return "Geirangelva: keine (ohne Gelände)";
        return String.format(Locale.GERMAN, "%s · %d Strecken als Fluss, %.1f m³/s, %.0f %% des Bands nass · %s", elva.describe(), strecken.size(), q,
                wetShare * 100, bauten.describe());
    }

    void dispose(GL4 gl) {
        if (vao != 0) gl.glDeleteVertexArrays(2, new int[]{vao, bVao}, 0);
        if (vbo != 0) gl.glDeleteBuffers(3, new int[]{vbo, ibo, bVbo}, 0);
        vao = bVao = vbo = ibo = bVbo = 0;
        ok = false;
    }
}
