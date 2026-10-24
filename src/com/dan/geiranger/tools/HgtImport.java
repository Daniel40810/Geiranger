package com.dan.geiranger.tools;

import com.dan.geiranger.scene.Fjordgrund;
import com.dan.geiranger.scene.Site;
import com.dan.geiranger.scene.Terrain;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Phase 2: liest die HGT-Kacheln mit 1″ (je 3 601 × 3 601 Werte, 16 Bit mit Vorzeichen, höchstwertiges Byte zuerst,
 * Meter über dem Geoid, das Meer auf 0) direkt aus den Zips in {@code templates/}, setzt sie zu einem Mosaik von 6° bis
 * 8° O und 62° bis 63° N zusammen und schreibt das Gelände der Szene nach {@code daten/gelaende} ({@link Terrain}):
 * <ul>
 * <li>je Zelle die Lage der Szene nach UTM 32 N, von dort nach Breite und Länge, und die Höhe bikubisch (Catmull-Rom)
 * aus dem Mosaik; die Kacheln teilen ihre Randspalte bei 7° O, darum entsteht dort keine Kante (F6, neu);</li>
 * <li>der Fernring zu 120 m aus demselben Mosaik, südlich von 62° N fortgesetzt und auf {@link Terrain#FERN_FUELLUNG}
 * auslaufend [G];</li>
 * <li>die Art je Zelle ({@link Terrain#classify}) mit Startpunkten im Fjord;</li>
 * <li>die Gegenprobe gegen GLO-30 ({@code templates/rasters_COP30 (2)/output_hh.tif}): Mittel und Streuung der
 * Differenz auf dem Land, im ganzen Raster und in den Kernfenstern (Abnahme: im Mittel unter 2 m);</li>
 * <li>ein Prüfbild mit Schummerung, Fjord, Kernfenstern und den Orten aus {@link Site}.</li>
 * </ul>
 * <pre>
 * HgtImport [templates] [daten/gelaende] [docs/status/Gelaende_Import.png]
 * </pre>
 */
public final class HgtImport {
    private HgtImport() { }

    /** Werte je Kachelseite und Kacheln (West nach Ost). */
    static final int S = 3601;
    static final String[] KACHELN = {"N62E006", "N62E007"};
    static final double LAT0 = 63, LON0 = 6;
    static final int COLS = (S - 1) * KACHELN.length + 1;

    public static void main(String[] a) throws Exception {
        File tpl = new File(a.length > 0 ? a[0] : "templates");
        File out = new File(a.length > 1 ? a[1] : "daten/gelaende");
        File png = new File(a.length > 2 ? a[2] : "docs/status/Gelaende_Import.png");
        long t0 = System.nanoTime();
        short[] mosaic = mosaic(tpl);
        Terrain t = build(mosaic);
        File glo = new File(tpl, "rasters_COP30 (2)/output_hh.tif");
        if (glo.isFile()) gegenprobe(t, GeoTiff.read(glo));
        else t.props.setProperty("glo30", "nicht gefunden");
        t.save(out);
        writeImage(t, png);
        File profil = new File(png.getParentFile(), "Fjordprofil.png");
        Fjordprofil.write(t, profil);
        System.out.println(t.describe());
        for (String k : new String[]{"raster", "fern", "tiefste", "hoechste", "fjord.km2", "grund", "grund.tiefste", "grund.mittel", "grund.deltaZellen", "ufer.geglaettet", "land.angehoben", "fern.unterNN", "naht7", "fern.ohneKachel", "glo30.mittel", "glo30.streuung",
                "glo30.kern.mittel", "glo30.kern.streuung", "glo30.punkte"})
            System.out.println("  " + k + " = " + t.props.getProperty(k));
        System.out.printf(Locale.GERMAN, "Gelände nach %s, Prüfbild %s, %.1f s%n", out, png, (System.nanoTime() - t0) / 1e9);
    }

    /** Das Mosaik der Kacheln: Zeilen von 63° N nach 62° N, Spalten von 6° O nach 8° O, 1″ Abstand. */
    static short[] mosaic(File tpl) throws IOException {
        short[] m = new short[S * COLS];
        for (int k = 0; k < KACHELN.length; k++) {
            short[] tile = readTile(tpl, KACHELN[k]);
            if (k > 0) for (int r = 0; r < S; r++) NAHT_MAX = Math.max(NAHT_MAX, Math.abs(m[r * COLS + k * (S - 1)] - tile[r * S]));
            for (int r = 0; r < S; r++) System.arraycopy(tile, r * S, m, r * COLS + k * (S - 1), S);
        }
        return m;
    }

    /** Eine Kachel aus {@code <name>.zip} (darin {@code <name>.hgt}) oder als {@code <name>.hgt}. */
    static short[] readTile(File tpl, String name) throws IOException {
        File zip = new File(tpl, name + ".zip"), hgt = new File(tpl, name + ".hgt");
        short[] s = new short[S * S];
        if (zip.isFile()) {
            try (ZipFile z = new ZipFile(zip)) {
                ZipEntry e = z.getEntry(name + ".hgt");
                if (e == null) throw new IOException(zip + ": " + name + ".hgt fehlt");
                try (InputStream in = z.getInputStream(e)) { read(in, s); }
            }
        } else if (hgt.isFile()) {
            try (InputStream in = new java.io.FileInputStream(hgt)) { read(in, s); }
        } else throw new IOException("Kachel " + name + " fehlt in " + tpl);
        return s;
    }

    private static void read(InputStream in, short[] s) throws IOException {
        DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(in, 1 << 16));
        for (int i = 0; i < s.length; i++) s[i] = d.readShort();
    }

    /** Höhe bikubisch (Catmull-Rom) bei Breite und Länge; NaN außerhalb des Mosaiks. */
    static double sample(short[] m, double lat, double lon) {
        double c = (lon - LON0) * 3600, r = (LAT0 - lat) * 3600;
        if (c < 0 || r < 0 || c > COLS - 1 || r > S - 1) return Double.NaN;
        int c0 = (int) Math.floor(c), r0 = (int) Math.floor(r);
        double fc = c - c0, fr = r - r0, sum = 0;
        for (int b = -1; b <= 2; b++) {
            double wr = cr(b - fr), row = 0;
            int rr = Math.max(0, Math.min(S - 1, r0 + b));
            for (int k = -1; k <= 2; k++) {
                int cc = Math.max(0, Math.min(COLS - 1, c0 + k));
                row += cr(k - fc) * m[rr * COLS + cc];
            }
            sum += wr * row;
        }
        return sum;
    }

    /** Catmull-Rom-Gewicht für den Abstand x. */
    static double cr(double x) {
        x = Math.abs(x);
        if (x < 1) return 1.5 * x * x * x - 2.5 * x * x + 1;
        if (x < 2) return -0.5 * x * x * x + 2.5 * x * x - 4 * x + 2;
        return 0;
    }

    /** Baut Hauptraster, Fernring und Art aus dem Mosaik. */
    public static Terrain build(short[] m) {
        int n = Terrain.N;
        double[] y = new double[n * n];
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        int miss = 0;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double[] ll = Site.latLon(Terrain.cx(i), Terrain.cz(j));
                double v = sample(m, ll[0], ll[1]);
                if (Double.isNaN(v)) { miss++; v = 0; }
                y[j * n + i] = v;
                lo = Math.min(lo, v);
                hi = Math.max(hi, v);
            }
        // Startpunkte im Fjord: vor den Sieben Schwestern und vor dem Anleger
        int[] seeds = {Terrain.index(Site.VOM_WASSER[0], Site.VOM_WASSER[1]), Terrain.index(-300, -150)};
        byte[] art = Terrain.classify(y, seeds);
        // Der Fjord liegt auf NN; die bikubische Kurve schwingt an den Wänden bis rund 15 m unter NN über, das glättet
        // der Fjord weg; danach malt Fjordgrund den Grund (Phase 3)
        int under = 0, lifted = 0;
        for (int k = 0; k < y.length; k++) if (art[k] == Terrain.FJORD) { if (y[k] < -0.5) under++; y[k] = 0; }
        // Phase 3: Land auf NN ohne Verbindung zum Fjord (Senken hinter dem Ufer) knapp darüber, sonst stünde dort das
        // Wasser von ocean auf gleicher Höhe wie das Gelände
        for (int k = 0; k < y.length; k++) if (art[k] == Terrain.LAND && y[k] < LAND_MIN) { y[k] = LAND_MIN; lifted++; }
        Properties p = new Properties();
        // Phase 3 (F7): der gemalte Grund unter dem Spiegel
        GRUND = Fjordgrund.malen(y, art, p);
        p.setProperty("ufer.geglaettet", String.valueOf(Fjordgrund.ufer(y, art, 2, LAND_MIN, 0.3)));
        p.setProperty("land.angehoben", String.valueOf(lifted));
        short[] h = new short[n * n];
        for (int k = 0; k < h.length; k++) h[k] = Terrain.encode(y[k]);
        short[] fern = fern(m);
        p.setProperty("quelle", "HGT 1″ N62E006 + N62E007 (DEM1)");
        p.setProperty("raster", n + " x " + n + " zu " + Terrain.CELL + " m, Ecke " + Terrain.X0 + " / " + Terrain.Z0);
        p.setProperty("fern", Terrain.FN + " x " + Terrain.FN + " zu " + Terrain.FCELL + " m, Ecke " + Terrain.FX0 + " / " + Terrain.FZ0);
        p.setProperty("tiefste", String.format(Locale.ROOT, "%.1f", lo));
        p.setProperty("hoechste", String.format(Locale.ROOT, "%.1f", hi));
        p.setProperty("ohneWert", String.valueOf(miss));
        p.setProperty("fjord.ueberschwinger", String.valueOf(under));
        p.setProperty("fern.unterNN", String.valueOf(FERN_UNTER));
        Terrain t = new Terrain(h, fern, art, p);
        p.setProperty("fjord.km2", String.format(Locale.ROOT, "%.2f", t.fjordKm2()));
        p.setProperty("naht7", String.valueOf(NAHT_MAX));
        for (Site.Ort o : Site.ORTE) p.setProperty("ort." + o.name(), String.format(Locale.ROOT, "%.1f", t.height(o.x(), o.z())));
        return t;
    }

    /**
     * Die Naht bei 7° O: größter Unterschied (m) zwischen der rechten Randspalte von N62E006 und der linken von N62E007;
     * beide liegen auf demselben Meridian. Das Mosaik nimmt die Spalte nur einmal, bikubisch über sie hinweg.
     */
    static int NAHT_MAX;

    /** Der Fernring; südlich von 62° N der letzte Wert der Spalte, der auf 15 km in die Füllhöhe ausläuft. */
    static short[] fern(short[] m) {
        int n = Terrain.FN;
        double[] y = new double[n * n];
        boolean[] miss = new boolean[n * n];
        int missing = 0;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double[] ll = Site.latLon(Terrain.fcx(i), Terrain.fcz(j));
                double v = sample(m, ll[0], ll[1]);
                if (Double.isNaN(v)) { miss[j * n + i] = true; missing++; }
                y[j * n + i] = v;
            }
        for (int i = 0; i < n; i++) {
            double last = Terrain.FERN_FUELLUNG;
            int lastJ = -1;
            for (int j = 0; j < n; j++) {
                int k = j * n + i;
                if (!miss[k]) { last = y[k]; lastJ = j; continue; }
                double d = lastJ < 0 ? 1 : Math.min(1, (j - lastJ) * Terrain.FCELL / 15_000);
                y[k] = last + (Terrain.FERN_FUELLUNG - last) * d * d * (3 - 2 * d);
            }
        }
        FERN_UNTER = Fjordgrund.fern(y);
        short[] s = new short[n * n];
        for (int k = 0; k < s.length; k++) s[k] = Terrain.encode(y[k]);
        FERN_OHNE = missing;
        return s;
    }

    static int FERN_OHNE, FERN_UNTER;
    /** Land darunter hebt der Import auf diesen Wert (m über NN). */
    static final double LAND_MIN = 0.6;
    /** Die zuletzt gemalte Tiefe je Zelle (für das Profilbild). */
    static double[] GRUND;

    /** Gegenprobe gegen GLO-30 (geografisch, 1″): Differenz HGT − GLO-30 auf dem Land. */
    static void gegenprobe(Terrain t, GeoTiff g) {
        double s = 0, s2 = 0, ks = 0, ks2 = 0;
        long c = 0, kc = 0;
        for (int j = 0; j < Terrain.N; j += 2)
            for (int i = 0; i < Terrain.N; i += 2) {
                if (t.art[j * Terrain.N + i] == Terrain.FJORD) continue;
                double x = Terrain.cx(i), z = Terrain.cz(j);
                double[] ll = Site.latLon(x, z);
                double v = sampleGeo(g, ll[1], ll[0]);
                if (Double.isNaN(v) || v <= 0.5) continue;
                double d = t.at(i, j) - v;
                s += d; s2 += d * d; c++;
                if (Site.KERN_WEST.contains(x, z) || Site.KERN_OST.contains(x, z)) { ks += d; ks2 += d * d; kc++; }
            }
        Properties p = t.props;
        p.setProperty("glo30.punkte", String.valueOf(c));
        if (c > 0) {
            double mean = s / c;
            p.setProperty("glo30.mittel", String.format(Locale.ROOT, "%.2f", mean));
            p.setProperty("glo30.streuung", String.format(Locale.ROOT, "%.2f", Math.sqrt(Math.max(0, s2 / c - mean * mean))));
        }
        if (kc > 0) {
            double mean = ks / kc;
            p.setProperty("glo30.kern.mittel", String.format(Locale.ROOT, "%.2f", mean));
            p.setProperty("glo30.kern.streuung", String.format(Locale.ROOT, "%.2f", Math.sqrt(Math.max(0, ks2 / kc - mean * mean))));
        }
        p.setProperty("fern.ohneKachel", String.format(Locale.ROOT, "%.3f", FERN_OHNE / (double) (Terrain.FN * Terrain.FN)));
    }

    /** Bilinear aus einem geografischen GeoTIFF an (Länge, Breite); NaN außerhalb. */
    static double sampleGeo(GeoTiff g, double lon, double lat) {
        double u = (lon - g.east0) / g.sizeX - 0.5, v = (g.north0 - lat) / g.sizeY - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        if (i < 0 || j < 0 || i + 1 >= g.width || j + 1 >= g.height) return Double.NaN;
        double fx = u - i, fz = v - j;
        double a = g.at(i, j), b = g.at(i + 1, j), c = g.at(i, j + 1), d = g.at(i + 1, j + 1);
        return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
    }

    /** Prüfbild 1 024²: Schummerung nach Höhe, Fjord blau, Kernfenster gelb, Orte rot, 1-km-Raster. */
    static void writeImage(Terrain t, File png) throws Exception {
        int s = 1024, n = Terrain.N, f = n / s;
        BufferedImage img = new BufferedImage(s, s + 64, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < s; y++)
            for (int x = 0; x < s; x++) {
                int i = x * f, j = y * f;
                double h = t.at(i, j), hx = (t.at(i + f, j) - t.at(i - f, j)) / (2 * f * Terrain.CELL), hz = (t.at(i, j + f) - t.at(i, j - f)) / (2 * f * Terrain.CELL);
                double shade = Math.max(0.25, Math.min(1.25, 0.85 + (-hx + hz) * 0.8));
                double[] c;
                if (t.art[j * n + i] == Terrain.FJORD) c = new double[]{0.16, 0.36, 0.42};
                else {
                    double k = Math.max(0, Math.min(1, h / 1600));
                    c = k < 0.4 ? new double[]{0.33 + 0.2 * k, 0.45 + 0.1 * k, 0.28} : new double[]{0.45 + 0.4 * (k - 0.4), 0.45 + 0.4 * (k - 0.4), 0.40 + 0.5 * (k - 0.4)};
                    shade = shade * 1.0;
                }
                int r = (int) Math.max(0, Math.min(255, c[0] * shade * 255)), g = (int) Math.max(0, Math.min(255, c[1] * shade * 255)), b = (int) Math.max(0, Math.min(255, c[2] * shade * 255));
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double sc = s / (n * Terrain.CELL);
        g.setColor(new Color(255, 255, 255, 40));
        for (double e = Math.ceil((Terrain.X0 + Site.E0) / 1000) * 1000; e < Terrain.X0 + Site.E0 + n * Terrain.CELL; e += 1000) {
            int x = (int) ((e - Site.E0 - Terrain.X0) * sc);
            g.drawLine(x, 0, x, s);
        }
        for (double no = Math.floor((Site.N0 - Terrain.Z0) / 1000) * 1000; no > Site.N0 - Terrain.Z0 - n * Terrain.CELL; no -= 1000) {
            int y = (int) ((Site.N0 - no - Terrain.Z0) * sc);
            g.drawLine(0, y, s, y);
        }
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(226, 184, 62));
        for (Site.Fenster w : new Site.Fenster[]{Site.KERN_WEST, Site.KERN_OST}) {
            double[] r = w.scene();
            g.drawRect((int) ((r[0] - Terrain.X0) * sc), (int) ((r[1] - Terrain.Z0) * sc), (int) ((r[2] - r[0]) * sc), (int) ((r[3] - r[1]) * sc));
        }
        g.setColor(new Color(93, 183, 174));
        double[] u = Site.UMGEBUNG.scene();
        g.drawRect((int) ((u[0] - Terrain.X0) * sc), (int) ((u[1] - Terrain.Z0) * sc), (int) ((u[2] - u[0]) * sc), (int) ((u[3] - u[1]) * sc));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        for (Site.Ort o : Site.ORTE) {
            int x = (int) ((o.x() - Terrain.X0) * sc), y = (int) ((o.z() - Terrain.Z0) * sc);
            g.setColor(new Color(201, 83, 60));
            g.fillOval(x - 4, y - 4, 8, 8);
            g.setColor(Color.WHITE);
            g.drawString(o.name(), x + 7, y + 4);
        }
        g.setColor(new Color(13, 19, 22));
        g.fillRect(0, s, s, 64);
        g.setColor(new Color(231, 236, 236));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString(t.describe() + " · Fjord " + t.props.getProperty("fjord.km2") + " km² · NN " + t.props.getProperty("tiefste") + " bis "
                + t.props.getProperty("hoechste") + " m", 10, s + 20);
        g.drawString(String.format(Locale.GERMAN, "Raster 2048² zu 12 m (24,6 km), UTM 32 N · GLO-30-Gegenprobe: im Mittel %s m, Streuung %s m (Kerne %s / %s m) · Naht 7° O: %s",
                t.props.getProperty("glo30.mittel", "–"), t.props.getProperty("glo30.streuung", "–"), t.props.getProperty("glo30.kern.mittel", "–"),
                t.props.getProperty("glo30.kern.streuung", "–"), t.props.getProperty("naht7")), 10, s + 42);
        g.dispose();
        File dir = png.getAbsoluteFile().getParentFile();
        if (dir != null) dir.mkdirs();
        javax.imageio.ImageIO.write(img, "png", png);
    }
}
