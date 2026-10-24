package com.dan.geiranger.tools;

import com.dan.geiranger.scene.Fjordgrund;
import com.dan.geiranger.scene.Site;
import com.dan.geiranger.scene.Terrain;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Phase 3: das Prüfbild des gemalten Grunds ({@code docs/status/Fjordprofil.png}). Oben die Karte des Fjords mit der
 * Tiefe als Farbe (hell flach, dunkel tief) und Linien alle 50 m, darunter drei Querschnitte mit den Marken des
 * Profils (Ufer 10 m, Mitte 150 m, Rinne 250 m). Für die Abnahme von Phase 3 neben dem Blick unter die Fläche.
 * <pre>
 * Fjordprofil [daten/gelaende] [docs/status/Fjordprofil.png]
 * </pre>
 */
public final class Fjordprofil {
    private Fjordprofil() { }

    /** Ein Schnitt: Name, Anfang und Ende in der Szene (x, z). */
    public record Schnitt(String name, double x0, double z0, double x1, double z1) { }

    /** Die drei Schnitte: quer vor den Sieben Schwestern, quer im breiten Teil (Rinne), längs zum Delta. */
    public static final Schnitt[] SCHNITTE = {
            new Schnitt("A  quer: Sieben Schwestern – Friaren", -5520, -1220, -4740, -660),
            new Schnitt("B  quer: breiter Bogen im Norden (Rinne)", -3300, -2950, -3300, -1450),
            new Schnitt("C  längs: Fjordkopf bis zur Mündung (Delta)", -700, -1500, Site.MUENDUNG[0], Site.MUENDUNG[1]),
    };

    public static void main(String[] a) throws Exception {
        File dir = new File(a.length > 0 ? a[0] : "daten/gelaende");
        File png = new File(a.length > 1 ? a[1] : "docs/status/Fjordprofil.png");
        Terrain t = Terrain.load(dir);
        write(t, png);
        System.out.println("Fjordprofil: " + png);
    }

    public static void write(Terrain t, File png) throws Exception {
        // Ausschnitt: der Fjord in der Umgebung, x −10 300 … 1 300, z −4 600 … 1 000 (Ost-West 11,6 km)
        double x0 = -10_300, x1 = 1_300, z0 = -4_600, z1 = 1_000;
        int w = 1400, mh = (int) Math.round(w * (z1 - z0) / (x1 - x0)), ph = 230, gap = 26;
        int h = mh + 3 * (ph + gap) + 70;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double sc = w / (x1 - x0);
        for (int py = 0; py < mh; py++)
            for (int px = 0; px < w; px++) {
                double x = x0 + (px + 0.5) / sc, z = z0 + (py + 0.5) / sc;
                double y = t.mainHeight(x, z);
                int rgb;
                if (t.fjord(x, z)) {
                    double d = Math.max(0, -y), k = Math.min(1, d / 260);
                    double r = 0.62 - 0.55 * k, g = 0.82 - 0.62 * k, b = 0.86 - 0.52 * k;
                    double c = d % 50, e = 1.2 / sc;
                    if (d > 5 && (c < e || 50 - c < e)) { r *= 0.55; g *= 0.55; b *= 0.55; }
                    rgb = rgb(r, g, b);
                } else {
                    double hx = (t.mainHeight(x + 12, z) - t.mainHeight(x - 12, z)) / 24, hz = (t.mainHeight(x, z + 12) - t.mainHeight(x, z - 12)) / 24;
                    double s = Math.max(0.3, Math.min(1.2, 0.85 + (-hx + hz) * 0.6)), k = Math.min(1, y / 1600);
                    rgb = rgb((0.36 + 0.3 * k) * s, (0.38 + 0.26 * k) * s, (0.30 + 0.34 * k) * s);
                }
                img.setRGB(px, py, rgb);
            }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font f = new Font(Font.SANS_SERIF, Font.PLAIN, 13), fb = new Font(Font.SANS_SERIF, Font.BOLD, 13);
        g.setStroke(new BasicStroke(2f));
        Color[] col = {new Color(226, 184, 62), new Color(201, 83, 60), new Color(93, 183, 174)};
        for (int i = 0; i < SCHNITTE.length; i++) {
            Schnitt s = SCHNITTE[i];
            g.setColor(col[i]);
            int ax = (int) ((s.x0() - x0) * sc), az = (int) ((s.z0() - z0) * sc), bx = (int) ((s.x1() - x0) * sc), bz = (int) ((s.z1() - z0) * sc);
            g.drawLine(ax, az, bx, bz);
            g.setFont(fb);
            g.drawString(s.name().substring(0, 1), ax - 14, az - 4);
        }
        g.setFont(fb);
        for (Site.Ort o : Site.ORTE) {
            int x = (int) ((o.x() - x0) * sc), y = (int) ((o.z() - z0) * sc);
            if (x < 0 || x > w || y < 0 || y > mh) continue;
            g.setColor(new Color(240, 240, 240));
            g.fillOval(x - 3, y - 3, 6, 6);
            g.drawString(o.name(), x + 6, y + 4);
        }
        int mx = (int) ((Site.MUENDUNG[0] - x0) * sc), mz = (int) ((Site.MUENDUNG[1] - z0) * sc);
        g.setColor(new Color(240, 240, 240));
        g.drawOval(mx - 5, mz - 5, 10, 10);
        g.drawString("Mündung", mx + 8, mz + 14);
        // Querschnitte
        for (int i = 0; i < SCHNITTE.length; i++) {
            Schnitt s = SCHNITTE[i];
            int top = mh + gap + i * (ph + gap);
            g.setColor(new Color(13, 19, 22));
            g.fillRect(0, top - gap, w, ph + gap);
            double[][] p = Fjordgrund.schnitt(t, s.x0(), s.z0(), s.x1(), s.z1(), 4);
            double len = p[p.length - 1][0];
            int l = 60, r = w - 20;
            double ymax = 120, ymin = -280;
            java.util.function.DoubleUnaryOperator Y = v -> top + (ymax - Math.max(ymin, Math.min(ymax, v))) / (ymax - ymin) * ph;
            g.setFont(f);
            for (double v : new double[]{0, -Fjordgrund.UFER, -Fjordgrund.MITTE, -Fjordgrund.RINNE}) {
                int yy = (int) Y.applyAsDouble(v);
                g.setColor(v == 0 ? new Color(93, 183, 174) : new Color(70, 84, 88));
                g.drawLine(l, yy, r, yy);
                g.setColor(new Color(160, 172, 176));
                if (v == 0) g.drawString("NN", 8, yy + 4);
                else if (v == -Fjordgrund.UFER) g.drawString("Ufer", r - 34, yy + 14);
                else g.drawString(String.format(Locale.GERMAN, "%.0f m", v), 8, yy + 4);
            }
            Path2D.Double path = new Path2D.Double();
            Path2D.Double water = new Path2D.Double();
            water.moveTo(l, Y.applyAsDouble(0));
            for (int k = 0; k < p.length; k++) {
                double xx = l + p[k][0] / len * (r - l), yy = Y.applyAsDouble(p[k][1]);
                if (k == 0) path.moveTo(xx, yy); else path.lineTo(xx, yy);
                water.lineTo(xx, Math.max(yy, Y.applyAsDouble(0)));
            }
            water.lineTo(r, Y.applyAsDouble(0));
            water.closePath();
            g.setColor(new Color(40, 92, 104));
            g.fill(water);
            g.setColor(col[i]);
            g.setStroke(new BasicStroke(2f));
            g.draw(path);
            double deepest = 0;
            for (double[] q : p) deepest = Math.min(deepest, q[1]);
            g.setColor(new Color(231, 236, 236));
            g.setFont(fb);
            g.drawString(String.format(Locale.GERMAN, "%s · %.1f km · tiefste Stelle %.0f m · %.1f-fach überhöht", s.name(), len / 1000, -deepest,
                    (ph / (ymax - ymin)) / ((r - l) / len)), l, top + 14);
        }
        g.setColor(new Color(13, 19, 22));
        g.fillRect(0, h - 44, w, 44);
        g.setColor(new Color(231, 236, 236));
        g.setFont(f);
        g.drawString(String.format(Locale.GERMAN, "Gemalter Fjordgrund (F7): %s · tiefste Zelle %s m, Mittel %s m · Linien alle 50 m · Querschnitte überhöht",
                "Ufer 10 m, Mitte 150 m, Rinne 250 m, Delta vor dem Dorf", t.props.getProperty("grund.tiefste", "–"), t.props.getProperty("grund.mittel", "–")), 10, h - 18);
        g.dispose();
        File d = png.getAbsoluteFile().getParentFile();
        if (d != null) d.mkdirs();
        javax.imageio.ImageIO.write(img, "png", png);
    }

    private static int rgb(double r, double g, double b) {
        return ((int) Math.max(0, Math.min(255, r * 255)) << 16) | ((int) Math.max(0, Math.min(255, g * 255)) << 8) | (int) Math.max(0, Math.min(255, b * 255));
    }
}
