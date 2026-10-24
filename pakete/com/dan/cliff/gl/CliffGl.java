package com.dan.cliff.gl;

import com.dan.cliff.Cliff;
import com.dan.cliff.CliffMap;
import com.dan.cliff.Overhang;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * com.dan.cliff auf der Grafikkarte (JOGL, OpenGL 4.3):
 * <ul>
 * <li>{@link #GLSL}: der Baustein {@code cliff.glsl} zum Einbinden in eigene Shader (Gelände): Feinform im
 * Scheitelshader, Material je Bildpunkt; {@link #bind} setzt seine Uniforms und die Wandkarte.</li>
 * <li>{@link #upload}: die Wandkarte ({@link CliffMap}) als RGBA8.</li>
 * <li>{@link #setOverhangs}, {@link #drawOverhangs}: Überhänge als Zusatzgeometrie mit demselben Material.</li>
 * <li>{@link #setWall}, {@link #drawWall}: ein Höhenfeld als Netz mit Feinform (für die Vorführung ohne App).</li>
 * <li>{@link #selfCheck}: 64 Punkte auf der Grafikkarte gegen {@link Cliff#material} und {@link Cliff#relief}.</li>
 * </ul>
 * Das Package kennt keine Szene: Licht und Blick kommen als Zahlen ({@link View}, {@link Light}), vorbelichtet.
 */
public final class CliffGl {
    /** Pfad des Bausteins im Klassenpfad (für {@code #include}). */
    public static final String GLSL = "/com/dan/cliff/gl/cliff.glsl";
    /** Einheit der Wandkarte, wenn der Aufrufer nichts anderes sagt. */
    public static final int MAP_UNIT = 14;
    /** Einheit der Rauschtextur (64³, R16): die Gitterwerte von {@link com.dan.cliff.CNoise}. */
    public static final int NOISE_UNIT = 15;

    /** Blick: Matrix, Auge, umgekehrte Tiefe, Öffnung eines Bildpunkts (rad). */
    public static final class View {
        public float[] viewProj;
        public double[] eye = {0, 0, 0};
        public boolean reversedZ;
        public double pixelAngle = 0.001;
    }

    /** Licht, vorbelichtet: Richtung und Bestrahlung der Sonne, Himmel (Bestrahlung/π), Dunst; Anteil direkt, Nässe, Schnee. */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {2.6, 2.5, 2.3}, ambient = {0.25, 0.3, 0.38}, fogColor = {0.6, 0.7, 0.8};
        public double fogDistance = 40_000, shade = 1, rainWet, snow;
        /** Für Vorführungen ohne eigene Nachbearbeitung: Belichtung, Filmkurve und Gamma gleich im Fels (0 = linear, HDR). */
        public double display;
    }

    private int mapTex, mapN, noiseTex;
    private double[] mapRect = {0, 0, 1};
    private int progRock, progCheck, vao, vbo, ibo, count, wallVao, wallVbo, wallIbo, wallCount, checkVao, checkVbo;
    private List<Overhang> overhangs = List.of();
    private boolean overhangsDirty, ready;
    private String info = "";

    public boolean ready() { return ready; }
    public String info() { return info; }
    public int mapTexture() { return mapTex; }
    public double[] mapRect() { return mapRect.clone(); }

    public void init(GL4 gl) {
        progRock = program(gl, "Fels", "rock.vert", "rock.frag");
        progCheck = program(gl, "Fels-Prüfung", "check.vert", "check.frag");
        // die Gitterwerte als 3D-Textur: ein Abruf je Rauschwert statt acht Hashes
        int P = com.dan.cliff.CNoise.PERIOD;
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        noiseTex = t[0];
        gl.glBindTexture(GL4.GL_TEXTURE_3D, noiseTex);
        gl.glTexStorage3D(GL4.GL_TEXTURE_3D, 1, GL4.GL_R16, P, P, P);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 2);
        gl.glTexSubImage3D(GL4.GL_TEXTURE_3D, 0, 0, 0, 0, P, P, P, GL4.GL_RED, GL.GL_UNSIGNED_SHORT,
                java.nio.ShortBuffer.wrap(com.dan.cliff.CNoise.latticeTexture()));
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL4.GL_TEXTURE_WRAP_R, GL.GL_REPEAT);
        gl.glBindTexture(GL4.GL_TEXTURE_3D, 0);
        ready = true;
        info = "Baustein cliff.glsl, Fels-Netz";
    }

    // ------------------------------------------------------------------ Baustein

    /**
     * Setzt die Uniforms von {@code cliff.glsl} im Programm prog und bindet die Wandkarte auf Einheit unit (ohne Karte:
     * Wand aus der Neigung). Das Programm muss gebunden sein.
     */
    public void bind(GL4 gl, int prog, Cliff c, int unit) {
        int k = gl.glGetUniformLocation(prog, "uCliffSeed");
        if (k < 0) return;   // Programm ohne Fels
        gl.glUniform1i(k, c.seed);
        double rockFrom = 1 - Math.cos(Math.toRadians(c.rockSlopeDeg)), w0 = 1 - Math.cos(Math.toRadians(c.wallSlopeDeg - 5)), w1 = 1 - Math.cos(Math.toRadians(c.wallSlopeDeg + 5));
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uCliffSlope"), (float) rockFrom, (float) w0, (float) w1, c.quality.octaves);
        double dip = Math.toRadians(c.foliationDipDeg), dd = Math.toRadians(c.foliationDipDirDeg);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uCliffFol"), (float) (Math.sin(dip) * Math.sin(dd)), (float) Math.cos(dip), (float) (-Math.sin(dip) * Math.cos(dd)));
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uCliffJoint"), (float) Math.toRadians(c.jointADeg + 90), (float) Math.toRadians(c.jointBDeg + 90),
                (float) c.jointSpacing, (float) c.jointWidth);
        gl.glUniform2f(gl.glGetUniformLocation(prog, "uCliffRelief"), (float) c.reliefAmp, (float) c.reliefScale);
        set3(gl, prog, "uCliffLight", c.gneissLight);
        set3(gl, prog, "uCliffDark", c.gneissDark);
        set3(gl, prog, "uCliffFeld", c.feldspar);
        set3(gl, prog, "uCliffLichen", c.lichen);
        set3(gl, prog, "uCliffMoss", c.moss);
        set3(gl, prog, "uCliffTalus", c.talus);
        set3(gl, prog, "uCliffSnow", c.snow);
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uCliffAmount"), (float) c.lichenAmount, (float) c.mossAmount, (float) c.seepAmount, (float) c.feldsparAmount);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uCliffFallWet"), (float) Math.max(0, Math.min(1, c.fallWet)));
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
        gl.glActiveTexture(GL.GL_TEXTURE0 + NOISE_UNIT);
        gl.glBindTexture(GL4.GL_TEXTURE_3D, noiseTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uCliffNoise"), NOISE_UNIT);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uCliffMap"), unit);
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uCliffMapRect"), (float) mapRect[0], (float) mapRect[1], (float) mapRect[2], mapTex != 0 ? 1f : 0f);
    }

    private static void set3(GL4 gl, int prog, String u, double[] v) {
        gl.glUniform3f(gl.glGetUniformLocation(prog, u), (float) v[0], (float) v[1], (float) v[2]);
    }

    /** Die Wandkarte hochladen (ersetzt eine vorige). */
    public void upload(GL4 gl, CliffMap m) {
        if (mapTex == 0 || mapN != m.n) {
            if (mapTex != 0) gl.glDeleteTextures(1, new int[]{mapTex}, 0);
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            mapTex = t[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA8, m.n, m.n);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            mapN = m.n;
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, m.n, m.n, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, ByteBuffer.wrap(m.rgba));
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        mapRect = new double[]{m.x0, m.z0, m.n * m.cell};
    }

    // ------------------------------------------------------------------ Überhänge und Wand

    /** Die Überhänge setzen (das Netz entsteht beim nächsten Zeichnen). */
    public void setOverhangs(List<Overhang> o) {
        overhangs = List.copyOf(o);
        overhangsDirty = true;
    }

    public int overhangCount() { return overhangs.size(); }

    /** Überhänge zeichnen (in den gebundenen Bildspeicher, Tiefentest wie der Aufrufer). */
    public void drawOverhangs(GL4 gl, Cliff c, View v, Light l) {
        if (!c.quality.overhangs || overhangs.isEmpty()) return;
        if (overhangsDirty) {
            List<float[]> vs = new ArrayList<>();
            List<int[]> is = new ArrayList<>();
            for (Overhang o : overhangs) { Overhang.Mesh m = o.mesh(28); vs.add(m.vertices); is.add(m.indices); }
            int[] ids = upload(gl, vao, vbo, ibo, vs, is);
            vao = ids[0]; vbo = ids[1]; ibo = ids[2]; count = ids[3];
            overhangsDirty = false;
        }
        drawRock(gl, c, v, l, vao, count, false);
    }

    /**
     * Ein Höhenfeld als Netz (für die Vorführung): n × n Ecken über dem Quadrat (x0, z0, Kante), Normalen aus dem
     * Feld. Die Feinform verschiebt die Ecken beim Zeichnen.
     */
    public void setWall(GL4 gl, Cliff.Heights h, double x0, double z0, double size, int n) {
        float[] v = new float[n * n * 6];
        double cell = size / (n - 1);
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double x = x0 + i * cell, z = z0 + j * cell, y = h.height(x, z);
                double gx = (h.height(x + cell, z) - h.height(x - cell, z)) / (2 * cell), gz = (h.height(x, z + cell) - h.height(x, z - cell)) / (2 * cell);
                double l = Math.sqrt(gx * gx + 1 + gz * gz);
                int k = (j * n + i) * 6;
                v[k] = (float) x; v[k + 1] = (float) y; v[k + 2] = (float) z;
                v[k + 3] = (float) (-gx / l); v[k + 4] = (float) (1 / l); v[k + 5] = (float) (-gz / l);
            }
        int[] idx = new int[(n - 1) * (n - 1) * 6];
        int t = 0;
        for (int j = 0; j < n - 1; j++)
            for (int i = 0; i < n - 1; i++) {
                int a = j * n + i, b = a + 1, c = a + n, d = c + 1;
                idx[t++] = a; idx[t++] = c; idx[t++] = d;
                idx[t++] = a; idx[t++] = d; idx[t++] = b;
            }
        int[] ids = upload(gl, wallVao, wallVbo, wallIbo, List.of(v), List.of(idx));
        wallVao = ids[0]; wallVbo = ids[1]; wallIbo = ids[2]; wallCount = ids[3];
    }

    public void drawWall(GL4 gl, Cliff c, View v, Light l) { drawRock(gl, c, v, l, wallVao, wallCount, true); }

    private void drawRock(GL4 gl, Cliff c, View v, Light l, int va, int n, boolean displace) {
        if (va == 0 || n == 0) return;
        gl.glUseProgram(progRock);
        bind(gl, progRock, c, MAP_UNIT);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(progRock, "uVP"), 1, false, v.viewProj, 0);
        gl.glUniform3f(gl.glGetUniformLocation(progRock, "uEye"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        gl.glUniform1i(gl.glGetUniformLocation(progRock, "uDisplace"), displace ? 1 : 0);
        gl.glUniform1f(gl.glGetUniformLocation(progRock, "uPixelAngle"), (float) v.pixelAngle);
        gl.glUniform1f(gl.glGetUniformLocation(progRock, "uRange"), (float) c.quality.reliefRange);
        set3(gl, progRock, "uSunDir", l.sunDir);
        set3(gl, progRock, "uSunColor", l.sunColor);
        set3(gl, progRock, "uAmbient", l.ambient);
        set3(gl, progRock, "uFogColor", l.fogColor);
        gl.glUniform1f(gl.glGetUniformLocation(progRock, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(gl.glGetUniformLocation(progRock, "uShade"), (float) l.shade);
        gl.glUniform1f(gl.glGetUniformLocation(progRock, "uRainWet"), (float) l.rainWet);
        gl.glUniform1f(gl.glGetUniformLocation(progRock, "uSnow"), (float) l.snow);
        gl.glUniform1f(gl.glGetUniformLocation(progRock, "uDisplay"), (float) l.display);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(v.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(va);
        gl.glDrawElements(GL.GL_TRIANGLES, n, GL.GL_UNSIGNED_INT, 0);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    /** Netze zu einem Puffer zusammenlegen; gibt {vao, vbo, ibo, Indexzahl}. */
    private static int[] upload(GL4 gl, int va, int vb, int ib, List<float[]> vs, List<int[]> is) {
        int nv = 0, ni = 0;
        for (float[] v : vs) nv += v.length;
        for (int[] i : is) ni += i.length;
        FloatBuffer fb = FloatBuffer.allocate(nv);
        IntBuffer ibuf = IntBuffer.allocate(ni);
        int base = 0;
        for (int k = 0; k < vs.size(); k++) {
            fb.put(vs.get(k));
            for (int x : is.get(k)) ibuf.put(x + base);
            base += vs.get(k).length / 6;
        }
        fb.flip();
        ibuf.flip();
        if (va == 0) {
            int[] t = new int[1];
            gl.glGenVertexArrays(1, t, 0);
            va = t[0];
            int[] b = new int[2];
            gl.glGenBuffers(2, b, 0);
            vb = b[0];
            ib = b[1];
        }
        gl.glBindVertexArray(va);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vb);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) nv * 4, fb, GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ib);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) ni * 4, ibuf, GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 24, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 24, 12);
        gl.glBindVertexArray(0);
        return new int[]{va, vb, ib, ni};
    }

    // ------------------------------------------------------------------ Selbsttest

    /**
     * Rechnet 64 Punkte (an einer Wand, im Umkreis von 200 m um den Ursprung) auf der Grafikkarte und vergleicht Farbe
     * und Relief mit dem Prozessor (ohne Wandkarte). Ergebnis: „Grafikkarte = Prozessor: Farbe ± 0,4 %, Relief ± 1 mm“.
     */
    public String selfCheck(GL4 gl, Cliff c) {
        int n = 64;
        float[] pts = new float[n * 6];
        java.util.Random r = new java.util.Random(c.seed);
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, sl = Math.toRadians(30 + r.nextDouble() * 60);
            pts[i * 6] = (float) (r.nextDouble() * 400 - 200);
            pts[i * 6 + 1] = (float) (r.nextDouble() * 300);
            pts[i * 6 + 2] = (float) (r.nextDouble() * 400 - 200);
            pts[i * 6 + 3] = (float) (Math.sin(sl) * Math.sin(a));
            pts[i * 6 + 4] = (float) Math.cos(sl);
            pts[i * 6 + 5] = (float) (-Math.sin(sl) * Math.cos(a));
        }
        double fp = 0.05;
        int[] save = new int[4];
        gl.glGetIntegerv(GL.GL_VIEWPORT, save, 0);
        int[] fbo = new int[1], tex = new int[1];
        gl.glGenTextures(1, tex, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA32F, n, 1);
        gl.glGenFramebuffers(1, fbo, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo[0]);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, tex[0], 0);
        gl.glViewport(0, 0, n, 1);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glUseProgram(progCheck);
        int mt = mapTex;
        mapTex = 0;   // ohne Karte: Wand aus der Neigung, wie Cliff.material(..., null, ...)
        bind(gl, progCheck, c, MAP_UNIT);
        mapTex = mt;
        gl.glUniform1f(gl.glGetUniformLocation(progCheck, "uCount"), n);
        gl.glUniform1f(gl.glGetUniformLocation(progCheck, "uFootprint"), (float) fp);
        int[] ids = upload(gl, checkVao, checkVbo, 0, List.of(pts), List.of(new int[0]));
        checkVao = ids[0];
        checkVbo = ids[1];
        if (ids[2] != 0) gl.glDeleteBuffers(1, new int[]{ids[2]}, 0);
        gl.glBindVertexArray(checkVao);
        gl.glDrawArrays(GL.GL_POINTS, 0, n);
        gl.glBindVertexArray(0);
        FloatBuffer out = FloatBuffer.allocate(n * 4);
        gl.glReadPixels(0, 0, n, 1, GL.GL_RGBA, GL.GL_FLOAT, out);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        gl.glDeleteFramebuffers(1, fbo, 0);
        gl.glDeleteTextures(1, tex, 0);
        gl.glViewport(save[0], save[1], save[2], save[3]);
        gl.glUseProgram(0);
        return compare(c, pts, out.array(), fp);
    }

    /** Vergleich Grafikkarte gegen Prozessor (auch für Prüfungen mit einer anderen Grafikschicht). */
    public static String compare(Cliff c, float[] pts, float[] gpu, double fp) {
        double col = 0, rel = 0;
        for (int i = 0; i < pts.length / 6; i++) {
            Cliff.Sample s = c.material(pts[i * 6], pts[i * 6 + 1], pts[i * 6 + 2], pts[i * 6 + 3], pts[i * 6 + 4], pts[i * 6 + 5], null, 0, 0, fp);
            for (int k = 0; k < 3; k++) col = Math.max(col, Math.abs(s.albedo[k] - gpu[i * 4 + k]) / Math.max(0.05, s.albedo[k]));
            rel = Math.max(rel, Math.abs(c.relief(pts[i * 6], pts[i * 6 + 1], pts[i * 6 + 2], fp) - gpu[i * 4 + 3]));
        }
        return String.format(Locale.GERMAN, "Grafikkarte = Prozessor: Farbe ± %.2f %%, Relief ± %.1f mm", col * 100, rel * 1000);
    }

    public void dispose(GL4 gl) {
        for (int p : new int[]{progRock, progCheck}) if (p != 0) gl.glDeleteProgram(p);
        for (int a : new int[]{vao, wallVao, checkVao}) if (a != 0) gl.glDeleteVertexArrays(1, new int[]{a}, 0);
        for (int b : new int[]{vbo, ibo, wallVbo, wallIbo, checkVbo}) if (b != 0) gl.glDeleteBuffers(1, new int[]{b}, 0);
        if (mapTex != 0) gl.glDeleteTextures(1, new int[]{mapTex}, 0);
        mapTex = progRock = progCheck = vao = wallVao = checkVao = 0;
        ready = false;
    }

    // ------------------------------------------------------------------ Shader laden

    /** Quelltext mit eingesetzten Bausteinen ({@code #include "datei"} aus diesem Package). */
    public static String source(String file) {
        StringBuilder sb = new StringBuilder("#version 430 core\n");
        expand(file, sb, new HashSet<>());
        return sb.toString();
    }

    private static void expand(String file, StringBuilder sb, Set<String> seen) {
        if (!seen.add(file)) return;
        for (String l : read(file).split("\r?\n", -1)) {
            String t = l.trim();
            if (t.startsWith("#include")) expand(t.substring(t.indexOf('"') + 1, t.lastIndexOf('"')), sb, seen);
            else sb.append(l).append('\n');
        }
    }

    static String read(String file) {
        try (InputStream in = CliffGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("cliff: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cliff: " + file + ": " + e.getMessage(), e);
        }
    }

    static int program(GL4 gl, String name, String vert, String frag) {
        int prog = gl.glCreateProgram();
        int[] sh = new int[2];
        String[] f = {vert, frag};
        for (int i = 0; i < 2; i++) {
            sh[i] = gl.glCreateShader(i == 0 ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(sh[i], 1, new String[]{source(f[i])}, null, 0);
            gl.glCompileShader(sh[i]);
            int[] ok = new int[1];
            gl.glGetShaderiv(sh[i], GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                int[] len = new int[1];
                gl.glGetShaderiv(sh[i], GL4.GL_INFO_LOG_LENGTH, len, 0);
                byte[] log = new byte[Math.max(1, len[0])];
                gl.glGetShaderInfoLog(sh[i], log.length, null, 0, log, 0);
                throw new IllegalStateException("cliff: " + f[i] + " (" + name + ") lässt sich nicht übersetzen:\n" + new String(log, StandardCharsets.UTF_8));
            }
            gl.glAttachShader(prog, sh[i]);
        }
        gl.glLinkProgram(prog);
        int[] ok = new int[1];
        gl.glGetProgramiv(prog, GL4.GL_LINK_STATUS, ok, 0);
        for (int s : sh) gl.glDeleteShader(s);
        if (ok[0] == 0) throw new IllegalStateException("cliff: " + name + " lässt sich nicht binden");
        return prog;
    }
}
