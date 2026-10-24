package com.dan.geiranger.gl;

import com.dan.geiranger.scene.Fjordgrund;
import com.dan.geiranger.scene.Terrain;
import com.dan.geiranger.wiring.Water;
import com.dan.ocean.Ocean;
import com.dan.ocean.gl.OceanGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.util.Locale;

/**
 * Phase 3: das Wasser des Fjords aus {@code com.dan.ocean} (Renderer.jar, F4 A) mit dem Preset „Fjord“ und dem
 * Spiegelbild der Wände. Das Preset lebt bis Phase 18 hier in der App und geht dann als {@code Ocean.fjord(bft)} in das
 * Package (Paketvertrag: das Package weiß nichts vom Geirangerfjord, nur von Streichlänge, Tiefe und Farbe).
 * <ul>
 * <li><b>Preset Fjord</b> ({@link #fjord}): aus {@link Ocean#lago} (tiefer See ohne Dünung), Streichlänge
 * {@link #FETCH} m (die Wände nehmen den Wind, der Fjord ist schmal und gewunden), Tiefe {@link Fjordgrund#MITTE} m,
 * weiche Kämme, wenig Schaum. Wind kommt nur in Böen als Kräuseln an: {@code tileBreak} lässt die Kaskaden fleckig
 * schwanken, dazwischen bleibt das Wasser glatt [G].</li>
 * <li><b>Farbe</b>: Gletschermilch, grünlicher als der Lago ({@link #WATER}, {@link #SSS}) [G, nach den Fotos].</li>
 * <li><b>Grund</b>: die Höhentextur des Geländes mit dem gemalten Grund ({@link Fjordgrund}); außerhalb des
 * Hauptrasters {@link Fjordgrund#FERN_TIEFE} m.</li>
 * <li><b>Spiegelbild</b>: com.dan.ocean spiegelt ein Rundumbild (Azimut, Höhe). Der Renderer zeichnet die Szene vom
 * Auge unter dem Spiegel ({@code y' = 2·Spiegel − y}), schneidet dabei alles unter dem Wasser weg und legt sie in das
 * Rundumbild ({@code spiegel.frag}); so spiegeln sich die Wände, nicht der Horizont.</li>
 * </ul>
 */
public final class Wasser {
    /** Streulicht und Kamm: Gletschermilch im Fjord, grünlich türkis (linear) [G]. */
    public static final double[] WATER = {0.028, 0.095, 0.085}, SSS = {0.06, 0.30, 0.26};
    /** Grund im Flachen: grauer Schlick und Geröll; Trübung je m (Gletschermilch, Sicht rund 6 m) [G]. */
    public static final double[] BED = {0.16, 0.15, 0.13};
    public static final double TRUEBUNG = 0.35;
    /** Streichlänge im Fjord (m) [G]. */
    public static final double FETCH = 3_000;
    /** Der Blickwinkel der gespiegelten Ansicht ist um diesen Faktor (im Tangens) weiter als der des Auges: Rand für die Wellen. */
    public static final double MIRROR_MARGIN = 1.3;

    /** Das Preset „Fjord“ bei Beaufort bft (Wind aus Westen, bis die Verdrahtung ihn setzt). */
    public static Ocean fjord(int bft) {
        Ocean o = Ocean.lago(bft);
        o.fetch = FETCH;
        o.depth = Fjordgrund.MITTE;
        o.fetchLimited = true;
        o.swellHeight = 0;
        o.choppiness = 0.6;
        o.foamAmount = 0.4;
        o.windFromDeg = 270;
        o.seed = 62;     // fest, damit Standbilder gleich bleiben
        o.rebuildNow();
        return o;
    }

    /** Wind aus der Verdrahtung (m/s, aus Grad); baut das Spektrum im Hintergrund neu, wenn er sich ändert. */
    public static void wind(Ocean o, double speed, double fromDeg) {
        o.windSpeed = Math.round(speed * 10) / 10.0;
        o.windFromDeg = Math.round(fromDeg);
        o.swellHeight = 0;
    }

    /** Das Auge gespiegelt am Wasser: y' = 2 · Spiegel − y. */
    public static double[] mirrorEye(double[] eye, double level) { return new double[]{eye[0], 2 * level - eye[1], eye[2]}; }

    /**
     * Projektion mal Blick der gespiegelten Ansicht: vom gespiegelten Auge in die gespiegelte Blickrichtung, oben ist
     * oben (bei einem symmetrischen Blickfeld deckt das genau die gespiegelten Sehstrahlen), Blickwinkel um
     * {@link #MIRROR_MARGIN} weiter. Fast senkrecht nach unten dreht das Bild um die Blickachse; das Rundumbild
     * fragt je Richtung, darum stört das nicht.
     */
    public static float[] mirrorViewProj(double[] eye, double[] fwd, double level, double fovyDeg, double aspect, double near, double far, boolean reversed) {
        double[] em = mirrorEye(eye, level);
        double fx = fwd[0], fy = -fwd[1], fz = fwd[2];
        double ux = 0, uy = 1, uz = 0;
        if (Math.abs(fy) > 0.995) { double l = Math.hypot(fx, fz) + 1e-9; ux = -fx / l; uy = 0; uz = -fz / l; }
        float[] view = Mat4.lookAt(em[0], em[1], em[2], em[0] + fx, em[1] + fy, em[2] + fz, ux, uy, uz);
        double fov = 2 * Math.toDegrees(Math.atan(Math.tan(Math.toRadians(fovyDeg / 2)) * MIRROR_MARGIN));
        return Mat4.mul(Mat4.perspective(fov, aspect, near, far, reversed), view);
    }

    public final Ocean ocean = fjord(2);
    final OceanGl gl = new OceanGl();
    boolean ok;
    String error = "", check = "";
    int mirrorTex, mirrorDepth, mirrorFbo, mirrorW, mirrorH;
    int panoTex, panoFbo, panoW;

    /** Die Fläche für die Verdrahtung: über dem Fjord mit Wellen, sonst der mittlere Spiegel. */
    public Water surface(Terrain t) {
        return new Water() {
            @Override public double surfaceAt(double x, double z) { return t.fjord(x, z) ? ocean.heightAt(x, z) : ocean.waterLevel; }
            @Override public double level() { return ocean.waterLevel; }
        };
    }

    void init(GL4 g) {
        try {
            gl.init(g);
            ok = true;
        } catch (RuntimeException e) {
            ok = false;
            error = e.getMessage() == null ? e.toString() : e.getMessage().split("\n")[0];
            System.err.println("Geiranger: ocean nicht gestartet – " + e.getMessage());
        }
    }

    /** Bildspeicher der gespiegelten Ansicht in w × h (RGBA16F, 32F-Tiefe). */
    void mirrorTarget(GL4 g, int w, int h) {
        if (w == mirrorW && h == mirrorH && mirrorFbo != 0) return;
        Gpu.deleteFbos(g, mirrorFbo);
        Gpu.deleteTextures(g, mirrorTex, mirrorDepth);
        mirrorTex = Gpu.texture(g, w, h, GL.GL_RGBA16F, false);
        mirrorDepth = Gpu.texture(g, w, h, GL4.GL_DEPTH_COMPONENT32F, false);
        g.glBindTexture(GL.GL_TEXTURE_2D, mirrorDepth);
        g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        g.glBindTexture(GL.GL_TEXTURE_2D, 0);
        mirrorFbo = Gpu.fbo(g, mirrorTex, mirrorDepth);
        mirrorW = w;
        mirrorH = h;
    }

    /** Das Rundumbild (Breite w, Höhe w/2, RGBA16F), das ocean als Himmel liest. */
    void panoTarget(GL4 g, int w) {
        if (w == panoW && panoFbo != 0) return;
        Gpu.deleteFbos(g, panoFbo);
        Gpu.deleteTextures(g, panoTex);
        panoTex = Gpu.texture(g, w, w / 2, GL.GL_RGBA16F, false);
        g.glBindTexture(GL.GL_TEXTURE_2D, panoTex);
        g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        g.glBindTexture(GL.GL_TEXTURE_2D, 0);
        panoFbo = Gpu.fbo(g, panoTex, 0);
        // die untere Hälfte liest ocean nie (es spiegelt nur nach oben); einmal schwarz, damit nichts Zufälliges drinsteht
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, panoFbo);
        g.glClearColor(0, 0, 0, 1);
        g.glClear(GL.GL_COLOR_BUFFER_BIT);
        panoW = w;
    }

    /** Untere Kante des gerechneten Teils im Rundumbild (Zeile): 5° unter der Kimm. */
    int panoRow0() { return (int) Math.floor(panoW / 2 * (0.5 - 5.0 / 180)); }

    /** Die Lichtwerte für ocean: Farbe, Grund aus dem Gelände, Rundumbild als Himmel. */
    OceanGl.Light light(double[] sun, double[] sunColor, int bedTex, double fogDistance) {
        OceanGl.Light l = new OceanGl.Light();
        l.sunDir = sun;
        l.sunColor = sunColor;
        l.skyTexture = panoTex;
        l.fogDistance = fogDistance;
        l.waterColor = WATER;
        l.sssColor = SSS;
        l.bedTexture = bedTex;
        l.bedRect = new double[]{Terrain.X0, Terrain.Z0, Terrain.N * Terrain.CELL};
        l.bedScale = Terrain.HMAX - Terrain.HMIN;
        l.bedOffset = Terrain.HMIN;
        l.bedOutside = -Fjordgrund.FERN_TIEFE;
        l.bedTurbidity = TRUEBUNG;
        l.bedColor = BED;
        l.tileBreak = 1;
        return l;
    }

    /** „Bft 2 · Windsee Hs 0,0 m … · FFT 4 × 256² · Spiegelbild 816×459 → 2048×1024“ */
    String describe(boolean mirror) {
        if (!ok) return "!Wasser: com.dan.ocean nicht gestartet (" + error + ") – Platzhalter-Spiegel";
        return String.format(Locale.GERMAN, "Wasser: ocean, Preset Fjord · %s · %s · %s%s", ocean.describe(), gl.info(),
                mirror && mirrorW > 0 ? "Spiegelbild " + mirrorW + "×" + mirrorH + " → Rundumbild " + panoW + "×" + panoW / 2 : "Spiegelbild aus (nur Himmel)",
                check.isEmpty() ? "" : " · " + check);
    }

    void dispose(GL4 g) {
        if (ok) gl.dispose(g);
        Gpu.deleteFbos(g, mirrorFbo, panoFbo);
        Gpu.deleteTextures(g, mirrorTex, mirrorDepth, panoTex);
        mirrorFbo = panoFbo = 0;
    }
}
