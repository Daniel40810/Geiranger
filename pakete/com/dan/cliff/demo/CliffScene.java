package com.dan.cliff.demo;

import com.dan.cliff.Cliff;
import com.dan.cliff.CliffMap;
import com.dan.cliff.Overhang;
import com.dan.cliff.gl.CliffGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.util.List;

/**
 * Die Szene der Vorführung ohne App: eine Wand von 300 m über einem Talboden, oben eine Hochfläche, ein Absatz in
 * halber Höhe, ein Überhang unter der Kante, ein nasser Streifen, wo ein Fall herabkäme, Geröll am Fuß. Das Auge
 * kreist um einen Punkt an der Wand ({@link #yaw}, {@link #pitch}, {@link #dist}).
 */
public final class CliffScene implements GLEventListener {
    public final Cliff cliff = new Cliff();
    public final CliffGl gl = new CliffGl();
    public final CliffMap map;
    public final Overhang overhang;
    public volatile double yaw = 10, pitch = 8, dist = 600, tx = 0, ty = 150, tz = -60, fovy = 50;
    public volatile double sunAlt = 30, sunAz = 160, rainWet = 0, snow = 0, exposure = 1.4;
    public volatile String check = "";
    public volatile double gpuMs;
    private boolean ready;
    private int w = 1, h = 1;
    private final int[] query = new int[2];
    private int frame;

    /** Die Wand ({@link DemoWall}). */
    public static double height(double x, double z) { return DemoWall.height(x, z); }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    public CliffScene() {
        map = CliffMap.build(cliff, CliffScene::height, -600, -600, 2.5, 480);
        map.addFall(0, 20, 290, 10, 1);
        double wy = 268;
        overhang = new Overhang(-6, wy, zAt(-6, wy) + 1, 180, 46, 26, 11, 7);
    }

    static double zAt(double x, double y) { return DemoWall.zAt(x, y); }

    @Override
    public void init(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.init(g);
        gl.upload(g, map);
        gl.setWall(g, CliffScene::height, -600, -600, 1200, 481);
        gl.setOverhangs(List.of(overhang));
        g.glGenQueries(2, query, 0);
        ready = true;
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int ww, int hh) { w = Math.max(1, ww); h = Math.max(1, hh); }

    @Override
    public void display(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        if (!ready) return;
        w = Math.max(1, d.getSurfaceWidth());
        h = Math.max(1, d.getSurfaceHeight());
        g.glViewport(0, 0, w, h);
        double[] sky = skyColor();
        double k = exposure;
        g.glClearColor((float) disp(sky[0] * k), (float) disp(sky[1] * k), (float) disp(sky[2] * k), 1);
        g.glClearDepth(1);
        g.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        CliffGl.View v = view();
        CliffGl.Light l = light();
        if (frame > 2) {
            long[] t = new long[2];
            g.glGetQueryObjecti64v(query[0], GL4.GL_QUERY_RESULT, t, 0);
            g.glGetQueryObjecti64v(query[1], GL4.GL_QUERY_RESULT, t, 1);
            gpuMs = gpuMs * 0.9 + (t[1] - t[0]) / 1e6 * 0.1;
        }
        g.glQueryCounter(query[0], GL4.GL_TIMESTAMP);
        gl.drawWall(g, cliff, v, l);
        gl.drawOverhangs(g, cliff, v, l);
        g.glQueryCounter(query[1], GL4.GL_TIMESTAMP);
        if (check.isEmpty() && frame > 3) check = gl.selfCheck(g, cliff);
        frame++;
    }

    static double disp(double c) { return Math.pow(1 - Math.exp(-c), 1 / 2.2); }

    double[] sunDir() {
        double a = Math.toRadians(sunAlt), z = Math.toRadians(sunAz);
        return new double[]{Math.cos(a) * Math.sin(z), Math.sin(a), -Math.cos(a) * Math.cos(z)};
    }

    double[] skyColor() {
        double b = 0.25 + 0.75 * smooth(-4, 20, sunAlt);
        return new double[]{0.45 * b, 0.62 * b, 0.85 * b};
    }

    CliffGl.Light light() {
        CliffGl.Light l = new CliffGl.Light();
        l.sunDir = sunDir();
        double s = 2.6 * smooth(-1, 8, sunAlt);
        l.sunColor = new double[]{s, s * (0.75 + 0.2 * smooth(0, 25, sunAlt)), s * (0.6 + 0.3 * smooth(0, 25, sunAlt))};
        double[] sk = skyColor();
        l.ambient = new double[]{sk[0] * 0.9, sk[1] * 0.9, sk[2] * 0.9};
        l.fogColor = sk;
        l.fogDistance = 25_000;
        l.rainWet = rainWet;
        l.snow = snow;
        l.display = exposure;
        return l;
    }

    CliffGl.View view() {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double fx = Math.sin(yr) * Math.cos(pr), fy = -Math.sin(pr), fz = -Math.cos(yr) * Math.cos(pr);
        double ex = tx - fx * dist, ey = ty - fy * dist, ez = tz - fz * dist;
        CliffGl.View v = new CliffGl.View();
        v.eye = new double[]{ex, ey, ez};
        v.viewProj = mul(perspective(fovy, w / (double) h, 0.5, 30_000), lookAt(ex, ey, ez, tx, ty, tz));
        v.pixelAngle = Math.toRadians(fovy) / h;
        return v;
    }

    // ------------------------------------------------------------------ Matrizen (Spalten zuerst)

    static float[] perspective(double fovy, double aspect, double n, double f) {
        double t = 1 / Math.tan(Math.toRadians(fovy) / 2);
        float[] m = new float[16];
        m[0] = (float) (t / aspect);
        m[5] = (float) t;
        m[10] = (float) ((f + n) / (n - f));
        m[11] = -1;
        m[14] = (float) (2 * f * n / (n - f));
        return m;
    }

    static float[] lookAt(double ex, double ey, double ez, double cx, double cy, double cz) {
        double fx = cx - ex, fy = cy - ey, fz = cz - ez, fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        double sx = fy * 0 - fz * 1, sy = fz * 0 - fx * 0, sz = fx * 1 - fy * 0, sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx /= sl; sy /= sl; sz /= sl;
        double ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;
        float[] m = new float[16];
        m[0] = (float) sx; m[4] = (float) sy; m[8] = (float) sz;
        m[1] = (float) ux; m[5] = (float) uy; m[9] = (float) uz;
        m[2] = (float) -fx; m[6] = (float) -fy; m[10] = (float) -fz;
        m[12] = (float) -(sx * ex + sy * ey + sz * ez);
        m[13] = (float) -(ux * ex + uy * ey + uz * ez);
        m[14] = (float) (fx * ex + fy * ey + fz * ez);
        m[15] = 1;
        return m;
    }

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++)
            for (int rr = 0; rr < 4; rr++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + rr] * b[c * 4 + k];
                r[c * 4 + rr] = s;
            }
        return r;
    }

    @Override
    public void dispose(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.dispose(g);
        g.glDeleteQueries(2, query, 0);
    }
}
