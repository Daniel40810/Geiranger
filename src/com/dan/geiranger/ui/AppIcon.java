package com.dan.geiranger.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Das Programmsymbol (F23: ein gemalter Fjord): der Blick den Fjord hinab zwischen zwei dunklen Wänden, links ein
 * weißer Fall, der Abendhimmel im Spiegel des Wassers, Goldkante wie die Vorgänger. Phase 18 malt es neu: steile
 * Wände bis an den Rand, die Sieben Schwestern an der linken, im Kerb ein ferner Berg mit Schnee, Wände und Berg im Wasser
 * gespiegelt, der Glanz der Abendsonne. Seit Phase 18 liegen die PNGs im Jar unter {@code com/dan/geiranger/ui/icon/geiranger_NN.png} und als
 * {@code icon/Geiranger.ico} für die Verknüpfung. Fehlt eine Größe, malt die App sie selbst.
 */
public final class AppIcon {
    public static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    private AppIcon() { }

    public static List<Image> images() {
        List<Image> l = new ArrayList<>();
        for (int s : SIZES) l.add(load(s));
        return l;
    }

    public static BufferedImage load(int size) {
        try (InputStream in = AppIcon.class.getResourceAsStream("icon/geiranger_" + size + ".png")) {
            if (in != null) {
                BufferedImage b = javax.imageio.ImageIO.read(in);
                if (b != null) return b;
            }
        } catch (java.io.IOException e) { /* gemaltes Symbol */ }
        return paint(size);
    }

    /** Das Symbol, in jeder Größe neu gemalt. */
    public static BufferedImage paint(int s) {
        BufferedImage im = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double r = s * 0.18, inset = Math.max(1, s * 0.035);
        // Goldkante
        g.setPaint(new GradientPaint(0, 0, new Color(240, 210, 120), s, s, new Color(150, 105, 30)));
        g.fill(new RoundRectangle2D.Double(0, 0, s, s, r * 2, r * 2));
        RoundRectangle2D inner = new RoundRectangle2D.Double(inset, inset, s - 2 * inset, s - 2 * inset, r * 1.7, r * 1.7);
        double hor = s * 0.60;
        // Abendhimmel
        g.setPaint(new GradientPaint(0, 0, new Color(28, 50, 78), 0, (float) hor, new Color(238, 182, 110)));
        g.fill(inner);
        g.setClip(inner);
        Path2D fern = berg(s, hor), links = wandLinks(s, hor), rechts = wandRechts(s, hor);
        // das Wasser: der Abendhimmel im Spiegel, nach vorn dunkles Fjordgrün
        g.setPaint(new GradientPaint(0, (float) hor, new Color(206, 160, 106), 0, s, new Color(16, 62, 66)));
        g.fill(new Rectangle2D.Double(0, hor, s, s - hor));
        // Spiegelbild von Berg und Wänden, gestaucht und dunkel
        java.awt.geom.AffineTransform spiegel = new java.awt.geom.AffineTransform();
        spiegel.translate(0, hor);
        spiegel.scale(1, -0.62);
        spiegel.translate(0, -hor);
        g.setColor(new Color(70, 84, 100, 150));
        g.fill(spiegel.createTransformedShape(fern));
        g.setColor(new Color(14, 22, 26, 185));
        g.fill(spiegel.createTransformedShape(links));
        g.fill(spiegel.createTransformedShape(rechts));
        // ferner Berg mit Schnee im Kerb des Fjords
        g.setColor(new Color(108, 122, 146));
        g.fill(fern);
        if (s >= 24) {
            Path2D schnee = new Path2D.Double();
            schnee.moveTo(s * 0.455, s * 0.355);
            schnee.lineTo(s * 0.50, s * 0.31);
            schnee.lineTo(s * 0.548, s * 0.36);
            schnee.lineTo(s * 0.52, s * 0.35);
            schnee.lineTo(s * 0.50, s * 0.37);
            schnee.lineTo(s * 0.48, s * 0.35);
            schnee.closePath();
            g.setColor(new Color(236, 232, 226));
            g.fill(schnee);
        }
        // die Wände aus Gneis: links in der Abendsonne, rechts im Schatten
        g.setPaint(new GradientPaint((float) (s * 0.42), (float) (s * 0.3), new Color(150, 142, 126), 0, 0, new Color(62, 66, 66)));
        g.fill(links);
        g.setPaint(new GradientPaint((float) (s * 0.62), 0, new Color(58, 64, 70), (float) s, 0, new Color(24, 28, 32)));
        g.fill(rechts);
        // Birken am Fuß der Wände
        if (s >= 32) {
            g.setColor(new Color(66, 104, 60));
            Path2D wl = new Path2D.Double();
            wl.moveTo(0, hor + 0.5);
            wl.lineTo(0, hor - s * 0.04);
            wl.curveTo(s * 0.15, hor - s * 0.03, s * 0.30, hor - s * 0.05, s * 0.44, hor);
            wl.closePath();
            g.fill(wl);
            Path2D wr = new Path2D.Double();
            wr.moveTo(s, hor + 0.5);
            wr.lineTo(s, hor - s * 0.035);
            wr.curveTo(s * 0.85, hor - s * 0.03, s * 0.70, hor - s * 0.04, s * 0.57, hor);
            wr.closePath();
            g.fill(wr);
        }
        // die Sieben Schwestern: weiße Strähnen an der linken Wand, unten Gischt
        int n = s >= 64 ? 5 : s >= 32 ? 3 : 2;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < n; i++) {
                double x0 = s * (0.10 + 0.036 * i * 5 / n), x1 = x0 + s * (0.03 + 0.008 * i);
                double y0 = s * (0.20 + 0.035 * ((i * 3) % 4) / 3.0);
                float w = (float) Math.max(pass == 0 ? 1.5 : 0.8, s * (pass == 0 ? 0.034 : i % 2 == 0 ? 0.014 : 0.010));
                g.setColor(pass == 0 ? new Color(236, 244, 246, 70) : new Color(244, 249, 250));
                g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D st = new Path2D.Double();
                st.moveTo(x0, y0);
                st.curveTo(x0 + s * 0.004, y0 + s * 0.06, x1 - s * 0.012, hor - s * 0.16, x1, hor - s * 0.035);
                g.draw(st);
            }
        }
        if (s >= 24) {
            g.setColor(new Color(240, 246, 248, 120));
            g.fill(new java.awt.geom.Ellipse2D.Double(s * 0.07, hor - s * 0.055, s * 0.26, s * 0.07));
            g.setColor(new Color(240, 246, 248, 28));
            g.fill(new java.awt.geom.Ellipse2D.Double(s * 0.10, hor + s * 0.01, s * 0.18, s * 0.08));
        }
        // Glanz der Sonne auf dem Wasser
        if (s >= 24) {
            g.setStroke(new BasicStroke((float) Math.max(1, s * 0.012), BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL));
            for (int i = 0; i < 4; i++) {
                double y = hor + s * (0.035 + 0.075 * i), half = s * (0.05 + 0.05 * i);
                g.setColor(new Color(255, 226, 170, 150 - 30 * i));
                g.draw(new Line2D.Double(s * 0.52 - half, y, s * 0.52 + half, y));
            }
        }
        g.dispose();
        return im;
    }

    private static Path2D berg(int s, double hor) {
        Path2D p = new Path2D.Double();
        p.moveTo(s * 0.32, hor);
        p.lineTo(s * 0.44, s * 0.40);
        p.lineTo(s * 0.50, s * 0.31);
        p.lineTo(s * 0.58, s * 0.39);
        p.lineTo(s * 0.62, s * 0.37);
        p.lineTo(s * 0.70, hor);
        p.closePath();
        return p;
    }

    private static Path2D wandLinks(int s, double hor) {
        Path2D p = new Path2D.Double();
        p.moveTo(-1, -1);
        p.lineTo(s * 0.30, -1);
        p.curveTo(s * 0.33, s * 0.20, s * 0.36, s * 0.42, s * 0.45, hor + 0.5);
        p.lineTo(-1, hor + 0.5);
        p.closePath();
        return p;
    }

    private static Path2D wandRechts(int s, double hor) {
        Path2D p = new Path2D.Double();
        p.moveTo(s + 1, -1);
        p.lineTo(s * 0.76, -1);
        p.curveTo(s * 0.72, s * 0.22, s * 0.66, s * 0.44, s * 0.56, hor + 0.5);
        p.lineTo(s + 1, hor + 0.5);
        p.closePath();
        return p;
    }

    /** Setzt das Symbol am Fenster und, wo unterstützt, in der Taskleiste. */
    public static void install(java.awt.Window w) {
        List<Image> l = images();
        w.setIconImages(l);
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar tb = java.awt.Taskbar.getTaskbar();
                if (tb.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) tb.setIconImage(l.get(l.size() - 1));
            }
        } catch (Exception | Error ignored) {
            // Windows nimmt ohnehin die Fenstersymbole
        }
    }
}
