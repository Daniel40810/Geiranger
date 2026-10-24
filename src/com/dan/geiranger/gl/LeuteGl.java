package com.dan.geiranger.gl;

import com.dan.avatar.LodMesh;
import com.dan.avatar.gl.AvatarGl;
import com.dan.avatar.gl.CrowdGl;
import com.dan.figure.Person;
import com.dan.figure.gl.FigureGl;
import com.dan.geiranger.scene.Leute;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 13: die Menschen auf der Grafikkarte. Die Gäste aus {@code com.dan.avatar} als Netze der Menge
 * ({@link CrowdGl}: Skinning mit Dual-Quaternionen im Scheitelshader, einfaches Licht), je Person die Stufe nach ihrer
 * Größe im Bild: fein (1,2 cm) ab {@link #FEIN_PX} Bildpunkten, mittel (3,5 cm) ab {@link #MITTEL_PX}, sonst grob
 * (9 cm). Die Figuren aus {@code com.dan.figure} ({@link FigureGl}, eine Instanz je Person). Der Ausflugsbus als Netz
 * im Format der Bauten mit dem Programm der Schiffe (Modellmatrix).
 */
public final class LeuteGl {
    public static final double FEIN_PX = 220, MITTEL_PX = 40, MIN_PX = 2.5;
    /** Höchstens so viele Gäste auf einmal (Plätze im Puffer der Gelenke). */
    public static final int PLAETZE = 16;
    /** Phase 18: die Schwellen nach der Qualitätsstufe (fein, mittel, kleinste), Vorgabe „Mittel“. */
    public volatile double feinPx = FEIN_PX, mittelPx = MITTEL_PX, minPx = MIN_PX;

    public final Leute leute;
    final FigureGl figureGl = new FigureGl();
    final CrowdGl crowdGl = new CrowdGl();
    boolean figurOk, crowdOk, crowdInit, busOk;
    String fehler = "";
    /** Ebene ein und aus (Bedienfeld). */
    public volatile boolean show = true;
    private final Map<Leute.Gast, CrowdGl.Mesh[]> netze = new HashMap<>();
    private final Map<Leute.Gast, Integer> platz = new HashMap<>();
    private int busVao, busVbo, busCount;
    /** Zuletzt gezeichnet: Figuren, Gäste je Stufe (fein, mittel, grob), Bus. */
    int figuren, bus;
    final int[] stufen = new int[3];

    public LeuteGl(Leute l) { leute = l; }

    void init(GL4 gl) {
        try {
            figureGl.init(gl);
            figureGl.maxRange = 2500;
            figurOk = figureGl.ready();
        } catch (RuntimeException e) {
            fehler = "Figuren: " + e.getMessage();
            System.err.println("Geiranger: " + fehler);
        }
        float[] e = Leute.busNetz();
        int[] t = new int[1];
        gl.glGenVertexArrays(1, t, 0);
        busVao = t[0];
        gl.glGenBuffers(1, t, 0);
        busVbo = t[0];
        gl.glBindVertexArray(busVao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, busVbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, e.length * 4L, FloatBuffer.wrap(e), GL.GL_STATIC_DRAW);
        for (int k = 0; k < 3; k++) {
            gl.glEnableVertexAttribArray(k);
            gl.glVertexAttribPointer(k, 3, GL.GL_FLOAT, false, 36, k * 12L);
        }
        gl.glBindVertexArray(0);
        busCount = e.length / 9;
        busOk = true;
    }

    /** Der Ausflugsbus (Programm der Schiffe gebunden, Uniforms gesetzt). */
    void drawBus(GL4 gl, Shader s, double epochSec, double[] eye) {
        bus = 0;
        if (!busOk || !show) return;
        double[] b = leute.bus(epochSec);
        if (b == null || Math.hypot(b[0] - eye[0], b[2] - eye[2]) > 6000) return;
        double h = Math.toRadians(-b[3]), c = Math.cos(h), sn = Math.sin(h);
        float[] m = {(float) c, 0, (float) -sn, 0, 0, 1, 0, 0, (float) sn, 0, (float) c, 0, (float) b[0], (float) b[1], (float) b[2], 1};
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glUniformMatrix4fv(s.loc(gl, "uModel"), 1, false, m, 0);
        gl.glBindVertexArray(busVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, busCount);
        gl.glBindVertexArray(0);
        bus = 1;
    }

    /** Die Figuren (Busgruppe, Wanderer, Treppe) im Umkreis. */
    void drawFiguren(GL4 gl, float[] vp, double[] eye, FigureGl.Light l, double epochSec) {
        figuren = 0;
        if (!figurOk || !show) return;
        List<Person> alle = leute.figuren(epochSec);
        alle.removeIf(p -> Math.hypot(p.x - eye[0], p.z - eye[2]) > figureGl.maxRange);
        if (alle.isEmpty()) return;
        FigureGl.View v = new FigureGl.View();
        v.viewProj = vp;
        v.eye = eye.clone();
        figureGl.draw(gl, alle, v, l);
        figuren = figureGl.drawn();
    }

    /**
     * Die Gäste: Netze hochladen, sobald sie gebacken sind, Gelenke je Bild, Stufe nach der Größe im Bild.
     * pxProM: Bildpunkte je Meter in 1 m Abstand (Bildhöhe / (2 tan(fovy/2))).
     */
    void drawGaeste(GL4 gl, float[] vp, double[] eye, AvatarGl.Light l, double pxProM) {
        stufen[0] = stufen[1] = stufen[2] = 0;
        if (!show || !leute.bereit()) return;
        if (!crowdInit) {
            crowdInit = true;
            try {
                crowdGl.init(gl, 53, PLAETZE);
                crowdOk = crowdGl.error() == null;
                if (!crowdOk) fehler = "Avatar: " + crowdGl.error();
            } catch (RuntimeException e) {
                fehler = "Avatar: " + e.getMessage();
            }
            if (!crowdOk) System.err.println("Geiranger: " + fehler);
        }
        if (!crowdOk) return;
        boolean begun = false;
        for (Leute.Gast g : leute.gaeste) {
            LodMesh[] lm = g.lod;
            float[] dq = g.dq;
            if (!g.da || lm == null || dq == null) continue;
            double d = Math.max(0.3, Math.sqrt((g.x - eye[0]) * (g.x - eye[0]) + (g.boden + 0.9 - eye[1]) * (g.boden + 0.9 - eye[1]) + (g.z - eye[2]) * (g.z - eye[2])));
            double px = g.groesse * pxProM / d;
            if (px < minPx) continue;
            CrowdGl.Mesh[] ms = netze.get(g);
            if (ms == null) {
                ms = new CrowdGl.Mesh[lm.length];
                for (int i = 0; i < lm.length; i++) ms[i] = crowdGl.upload(gl, lm[i]);
                netze.put(g, ms);
                platz.put(g, platz.size() % PLAETZE);
            }
            int st = px >= feinPx ? 0 : px >= mittelPx ? 1 : 2;
            if (!begun) {
                gl.glEnable(GL.GL_DEPTH_TEST);
                gl.glDepthMask(true);
                crowdGl.begin(gl, vp, eye.clone(), l);
                begun = true;
            }
            int sl = platz.get(g);
            crowdGl.bones(gl, sl, dq);
            crowdGl.draw(gl, ms[st], sl, (float) g.x, (float) (g.boden - g.footY), (float) g.z, 0f, 1.01f, new float[]{0, 0, 0});
            stufen[st]++;
        }
        gl.glUseProgram(0);
        gl.glBindVertexArray(0);
    }

    String describe(double epochSec) {
        String gl = String.format(Locale.GERMAN, " · gezeichnet: %d Figuren, Gäste %d fein/%d mittel/%d grob%s", figuren, stufen[0], stufen[1], stufen[2], bus > 0 ? ", Bus" : "");
        return leute.describe(epochSec) + gl + (fehler.isEmpty() ? "" : " · " + fehler);
    }

    void dispose(GL4 gl) {
        if (figurOk) figureGl.dispose(gl);
        if (crowdOk) {
            for (CrowdGl.Mesh[] ms : netze.values()) for (CrowdGl.Mesh m : ms) crowdGl.free(gl, m);
            crowdGl.dispose(gl);
        }
        if (busOk) {
            gl.glDeleteVertexArrays(1, new int[]{busVao}, 0);
            gl.glDeleteBuffers(1, new int[]{busVbo}, 0);
        }
        leute.dispose();
        figurOk = crowdOk = busOk = false;
    }
}
