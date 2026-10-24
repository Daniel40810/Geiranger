package com.dan.geiranger.data;

import com.dan.geiranger.scene.Ornevegen;
import com.dan.geiranger.scene.Site;
import com.dan.geiranger.scene.Terrain;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Die Tageskarte (F37 A, Block DATEN): eine Draufsicht als PNG mit dem Fjord vom Westrand bis Geiranger, Gelände als
 * Schummerung, Fv63, Fällen und Orten, dazu aus der Chronik des Szenentags die Wege der Schiffe, die Hörner mit der Zahl
 * ihrer Echos, die Ankünfte des Busses und eine Zeile mit Wetter und Abfluss. Dieselbe Karte zeichnet QGIS aus
 * {@code GR_TAGESKARTE_V}. Gelesen wird über den {@link Speicher}.
 */
public final class Tageskarte {
    private Tageskarte() { }

    public static final int W = 1600, H = 1000;
    /** Ausschnitt (Szene, m): vom Westrand des Rasters bis hinter den Ort, 16 × 10 km. */
    public static final double X0 = -13200, Z0 = -6600, BREITE = 16000;
    static final Color HINTER = new Color(11, 18, 22), WASSER = new Color(34, 70, 78), LAND = new Color(70, 84, 66), TEXT = new Color(232, 236, 234),
            GRAU = new Color(150, 165, 166), STRASSE = new Color(226, 184, 62), FALL = new Color(214, 238, 246), FAEHRE = new Color(240, 200, 120),
            KREUZ = new Color(230, 120, 90), HORN = new Color(250, 90, 70), BUS = new Color(120, 200, 240), ORT = new Color(250, 250, 250);

    public record Ergebnis(File datei, int fahrten, int hoerner, int busse, int stunden) { }

    public static Ergebnis zeichnen(Speicher sp, Terrain t, LocalDate tag, File dir) throws Exception { return zeichnen(sp, t, tag, dir, sp.wo()); }

    /** Wie oben, mit eigener Angabe der Herkunft oben rechts (Phase 18: das Statusbild nennt keinen Ordner auf Zeit). */
    public static Ergebnis zeichnen(Speicher sp, Terrain t, LocalDate tag, File dir, String herkunft) throws Exception {
        Daten.Tageschronik c = sp.tagesChronik(tag);
        List<Daten.Fall> fs = sp.faelle();
        double s = W / BREITE;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(HINTER);
        g.fillRect(0, 0, W, H);
        if (t != null && t.measured()) {
            for (int py = 0; py < H; py += 2)
                for (int px = 0; px < W; px += 2) {
                    double x = X0 + px / s, z = Z0 + py / s;
                    if (t.fjord(x, z)) { g.setColor(WASSER); g.fillRect(px, py, 2, 2); continue; }
                    double h = t.height(x, z), hx = t.height(x + 24, z) - t.height(x - 24, z), hz = t.height(x, z + 24) - t.height(x, z - 24);
                    double licht = Math.max(0.25, Math.min(1.3, 0.85 + (-hx + hz) / 160));
                    double hoch = Math.min(1, Math.max(0, h / 1700));
                    int rr = c8((LAND.getRed() + hoch * 110) * licht), gg = c8((LAND.getGreen() + hoch * 100) * licht), bb = c8((LAND.getBlue() + hoch * 110) * licht);
                    g.setColor(new Color(rr, gg, bb));
                    g.fillRect(px, py, 2, 2);
                }
            Ornevegen o = t.ornevegen;
            if (o != null) {
                g.setColor(STRASSE);
                g.setStroke(new BasicStroke(2f));
                Path2D p = new Path2D.Double();
                for (int i = 0; i < o.way.n; i++) {
                    double x = (o.way.x[i] - X0) * s, y = (o.way.z[i] - Z0) * s;
                    if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
                }
                g.draw(p);
            }
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (Daten.Fall f : fs) {
            double x = (f.x() - X0) * s, y = (f.z() - Z0) * s;
            g.setColor(FALL);
            g.fillOval((int) x - 4, (int) y - 4, 8, 8);
            if (!"REGEN".equals(f.art()) && !"FLUSS".equals(f.art())) g.drawString(f.name(), (int) x + 7, (int) y - 5);
        }
        for (Site.Ort o : new Site.Ort[]{Site.ANLEGER, Site.PLATTFORM, Site.KNIVSFLA, Site.SKAGEFLA}) {
            double x = (o.x() - X0) * s, y = (o.z() - Z0) * s;
            g.setColor(ORT);
            g.fillRect((int) x - 3, (int) y - 3, 6, 6);
            g.drawString(o.name(), (int) x + 6, (int) y + 14);
        }
        int hoerner = 0;
        for (Daten.Schiffsfahrt f : c.fahrten()) {
            Color col = f.richtung().equals("EIN") || f.richtung().equals("AUS") ? KREUZ : FAEHRE;
            if (f.weg() != null) {
                g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 200));
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (double[][] teil : f.weg().teile()) {
                    Path2D p = new Path2D.Double();
                    for (int i = 0; i < teil.length; i++) {
                        double x = (teil[i][0] - X0) * s, y = (teil[i][1] - Z0) * s;
                        if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
                    }
                    g.draw(p);
                }
            }
            if (f.horn() != null) {
                hoerner += f.hoerner();
                double x = (f.horn()[0] - X0) * s, y = (f.horn()[1] - Z0) * s;
                g.setColor(HORN);
                g.fillOval((int) x - 6, (int) y - 6, 12, 12);
                g.setColor(TEXT);
                g.drawString((f.echos() == null ? "Horn" : f.echos() + " Echos"), (int) x + 8, (int) y + 4);
            }
        }
        for (Daten.Busankunft b : c.busse()) {
            if (Double.isNaN(b.x())) continue;
            double x = (b.x() - X0) * s, y = (b.z() - Z0) * s;
            g.setColor(BUS);
            g.fillRect((int) x - 5, (int) y - 5, 10, 10);
        }
        if (!c.busse().isEmpty() && !Double.isNaN(c.busse().get(0).x())) {
            Daten.Busankunft b = c.busse().get(0);
            g.setColor(BUS);
            int ges = 0;
            for (Daten.Busankunft x : c.busse()) ges += x.gruppe();
            g.drawString(c.busse().size() + " Busse, " + ges + " Gäste", (int) ((b.x() - X0) * s) + 8, (int) ((b.z() - Z0) * s) - 8);
        }
        // Kopf und Fuß
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, W, 54);
        g.fillRect(0, H - 40, W, 40);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 24));
        g.drawString("Geiranger · Tageskarte " + tag.format(java.time.format.DateTimeFormatter.ofPattern("d. MMMM uuuu", Locale.GERMANY)), 18, 34);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(GRAU);
        double regen = 0, abfluss = 0, tmin = 99, tmax = -99, windMax = 0;
        for (Daten.Stunde h : c.stunden()) {
            if (!Double.isNaN(h.regenMmH())) regen += h.regenMmH();
            if (!Double.isNaN(h.abflussAnteil())) abfluss += h.abflussAnteil();
            if (!Double.isNaN(h.temperaturC())) { tmin = Math.min(tmin, h.temperaturC()); tmax = Math.max(tmax, h.temperaturC()); }
            if (!Double.isNaN(h.windMs())) windMax = Math.max(windMax, h.windMs());
        }
        String wetter = c.stunden().isEmpty() ? "keine Stunde in der Chronik"
                : String.format(Locale.GERMAN, "%d Stunden: %.0f bis %.0f °C, Wind bis %.1f m/s, Regen %.1f mm, Abfluss im Mittel %.0f %% vom Juni", c.stunden().size(), tmin, tmax,
                windMax, regen, abfluss / c.stunden().size() * 100);
        String jahr = c.tage().isEmpty() ? "" : String.format(Locale.GERMAN, " · Schneegrenze %s, Laub %.0f %%",
                c.tage().get(0).schneegrenzeM() == null ? "keine" : Math.round(c.tage().get(0).schneegrenzeM()) + " m", c.tage().get(0).laub() * 100);
        g.drawString(c.fahrten().size() + " Fahrten (" + hoerner + " Hörner) · " + c.busse().size() + " Busse · " + wetter + jahr, 18, H - 15);
        g.drawString(herkunft, W - 18 - g.getFontMetrics().stringWidth(herkunft), 34);
        g.dispose();
        if (!dir.isDirectory()) dir.mkdirs();
        File f = new File(dir, "Tageskarte_" + tag + ".png");
        ImageIO.write(img, "png", f);
        return new Ergebnis(f, c.fahrten().size(), hoerner, c.busse().size(), c.stunden().size());
    }

    static int c8(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }
}
