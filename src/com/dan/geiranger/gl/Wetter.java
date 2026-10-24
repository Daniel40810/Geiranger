package com.dan.geiranger.gl;

import com.dan.cloud.Clouds;
import com.dan.cloud.gl.CloudGl;
import com.dan.fog.Fog;
import com.dan.fog.gl.FogGl;
import com.dan.rain.Rain;
import com.dan.rain.gl.RainGl;
import com.dan.sky.Sky;
import com.dan.sky.gl.SkyGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.util.Locale;

/**
 * Phase 4: Himmel und Wetter aus {@code Renderer.jar} (F4 A) an einem Ort: {@code com.dan.sky} (Sonne, Mond, Sterne,
 * Streuung über 62,1° N), {@code com.dan.cloud} (Wolken über den Gipfeln, ihr Schatten), {@code com.dan.rain}
 * (Regen um das Auge, Vorhänge, Ringe auf dem Fjord) und {@code com.dan.fog} zweimal: als Fjordnebel auf dem Wasser
 * und als Wolkenband, das in halber Höhe an den Wänden hängt. Jedes Package startet für sich; was nicht startet,
 * fehlt, der Rest läuft (ohne Himmel bleibt der Verlauf aus Phase 1). Die Eingänge setzt die Verdrahtung, das Licht
 * kommt aus dem Himmel, vorbelichtet wie in Rungholt.
 */
public final class Wetter {
    public final Sky sky = new Sky();
    final SkyGl skyGl = new SkyGl();
    public final Clouds clouds = new Clouds();
    final CloudGl cloudGl = new CloudGl();
    public final Rain rain = new Rain();
    final RainGl rainGl = new RainGl();
    public final Fog nebel = new Fog(), band = new Fog();
    final FogGl nebelGl = new FogGl(), bandGl = new FogGl();
    boolean skyOk, cloudsOk, rainOk, nebelOk, bandOk;
    String skyCheck = "", cloudCheck = "", rainCheck = "", fogCheck = "";
    /** Das Rundumbild des Himmels (mit Wolken), vorbelichtet: Himmel für Spiegelbild, Gelände und Dunst. */
    int panoTex, panoFbo;
    public static final int PANO_W = 512, PANO_H = 256;

    public Wetter() {
        nebel.seed = 6201;
        band.seed = 6202;
        band.softness = 0.35;
    }

    /** Alles starten, jedes für sich; Meldungen an err. */
    void init(GL4 gl, java.util.function.Consumer<String> err) {
        panoTex = Gpu.texture(gl, PANO_W, PANO_H, GL.GL_RGBA16F, false);
        gl.glBindTexture(GL.GL_TEXTURE_2D, panoTex);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        panoFbo = Gpu.fbo(gl, panoTex, 0);
        try {
            skyGl.init(gl);
            skyOk = true;
            System.out.println("Geiranger: Himmel " + skyGl.info());
        } catch (RuntimeException e) {
            fail(err, "Der Himmel (com.dan.sky) lässt sich nicht starten, es bleibt der Verlauf aus Phase 1", e);
        }
        if (skyOk) {
            try {
                cloudGl.init(gl);
                cloudsOk = true;
                System.out.println("Geiranger: Wolken " + cloudGl.info());
            } catch (RuntimeException e) {
                fail(err, "Die Wolken (com.dan.cloud) lassen sich nicht starten, es bleibt der Schleier des Himmels", e);
            }
        }
        try {
            rainGl.init(gl);
            rainOk = true;
        } catch (RuntimeException e) {
            fail(err, "Der Regen (com.dan.rain) lässt sich nicht starten", e);
        }
        try {
            nebelGl.init(gl);
            nebelOk = true;
            bandGl.init(gl);
            bandOk = true;
        } catch (RuntimeException e) {
            fail(err, "Der Nebel (com.dan.fog) lässt sich nicht starten", e);
        }
    }

    private static void fail(java.util.function.Consumer<String> err, String what, RuntimeException e) {
        System.err.println("Geiranger: " + what + ": " + e.getMessage());
        err.accept(what + ".\n" + e.getMessage());
    }

    /** Der aktuelle Himmelszustand (nie null nach dem ersten Schritt). */
    public Sky.State state() {
        Sky.State st = sky.state();
        return st != null ? st : sky.update();
    }

    static boolean sunIsKey(Sky.State st) { return st.sunAlt > -4; }

    /** Richtung des Hauptlichts: die Sonne, nachts der Mond (für Schatten der Wände und Wolken). */
    static double[] keyDir(Sky.State st) { return sunIsKey(st) ? st.sunDirTrue : st.moonDirTrue; }

    /** Licht für die Wolken aus dem Himmelszustand, vorbelichtet; von unten Fels, Wald und dunkles Wasser. */
    CloudGl.Light cloudLight(Sky.State st, double pre) {
        CloudGl.Light l = new CloudGl.Light();
        boolean sunKey = sunIsKey(st);
        l.dir = keyDir(st);
        for (int i = 0; i < 8; i++) l.colorAt[i] = scale(sunKey ? st.sunColorAt(CloudGl.LIGHT_HEIGHTS[i]) : st.moonColorAt(CloudGl.LIGHT_HEIGHTS[i]), pre);
        double[] e = st.skyIrradiance;
        l.ambTop = scale(e, pre / Math.PI);
        double s = Math.max(0, st.sunDir[1]), m = Math.max(0, st.moonDir[1]);
        l.ambBot = new double[3];
        for (int c = 0; c < 3; c++) l.ambBot[c] = (e[c] + st.sunColor[c] * s + st.moonColor[c] * m) * 0.08 / Math.PI * pre;
        l.haze = st.groundExtinction;
        l.flash = new double[]{0, 1, 0, 0};
        l.flashCount = 0;
        return l;
    }

    /**
     * Wie viel Himmelslicht unter der Wolkendecke ankommt (1 = klarer Himmel); unter einer dichten, dunklen Decke gut
     * die Hälfte [G], wie Rungholt.
     */
    double skyDim(boolean cloudsShown) {
        if (!cloudsOk || !cloudsShown) return 1;
        double low = Math.pow(Math.max(0, Math.min(8, clouds.oktas)) / 8, 1.3) * (1 - clouds.type.overcastBrightness);
        return 1 - 0.55 * low;
    }

    /** Licht für Regen und Nebel: {Sonne, Mond, Himmel oben, Himmel am Horizont}, vorbelichtet. */
    double[][] ambient(Sky.State st, double pre, double cloudShade, double skyDim) {
        double[] hor = new double[3];
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * i / 3;
            double[] hh = st.radiance(norm3(new double[]{Math.sin(a), 0.08, -Math.cos(a)}));
            for (int k = 0; k < 3; k++) hor[k] += hh[k] / 6;
        }
        return new double[][]{scale(st.sunColor, pre * cloudShade), scale(st.moonColor, pre * cloudShade), scale(st.skyIrradiance, pre / Math.PI * skyDim), scale(hor, pre)};
    }

    /** Dunstlänge für exp(−d/L) aus der Sichtweite (Koschmieder). */
    static double fogDistance(Sky.State st) { return st.visibilityKm * 1000 / 3.912; }

    /** Anteil des Stäbchensehens (nachts farblos und bläulich), aus der Anpassung des Himmels (wie Rungholt). */
    static double scotopic(Sky.State st) {
        double l = Math.log10(Math.max(1e-12, st.adaptation * Sky.CD_PER_UNIT));
        double t = Math.max(0, Math.min(1, (l - Math.log10(0.003)) / (Math.log10(3) - Math.log10(0.003))));
        return 1 - t * t * (3 - 2 * t);
    }

    static double[] scale(double[] v, double k) { return new double[]{v[0] * k, v[1] * k, v[2] * k}; }

    static double[] norm3(double[] v) {
        double l = Math.max(1e-12, Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]));
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    /** „Himmel … · Wolken … · Regen … · Nebel …“ für das volle HUD (zwei Zeilen). */
    String[] describe(boolean cloudsShown, double cloudShade) {
        Sky.State st = skyOk ? state() : null;
        String a = st == null ? "!Himmel: com.dan.sky nicht gestartet, Verlauf aus Phase 1"
                : String.format(Locale.GERMAN, "Himmel %s · Sonne %.1f° · Mond %.0f %% · Sicht %.0f km%s", skyGl.info(), st.sunAlt, st.moonFraction * 100,
                st.visibilityKm, skyCheck.isEmpty() ? "" : " · " + skyCheck);
        String b = (cloudsOk ? String.format(Locale.GERMAN, "Wolken %s, Sonne am Auge %.0f %%", cloudsShown ? clouds.describe() : "aus", cloudShade * 100) : "!Wolken fehlen")
                + " · " + (rainOk ? rain.describe() : "!Regen fehlt") + " · " + (nebelOk ? "Nebel " + nebel.describe() + " · Band " + band.describe() : "!Nebel fehlt");
        return new String[]{a, b};
    }

    void dispose(GL4 gl) {
        if (skyOk) skyGl.dispose(gl);
        if (cloudsOk) cloudGl.dispose(gl);
        if (rainOk) rainGl.dispose(gl);
        if (nebelOk) nebelGl.dispose(gl);
        if (bandOk) bandGl.dispose(gl);
        Gpu.deleteFbos(gl, panoFbo);
        Gpu.deleteTextures(gl, panoTex);
    }
}
