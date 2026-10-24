package com.dan.geiranger.scene;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Properties;

/**
 * Das Gelände der Szene (Phase 2, F3 und F6 in der Fassung vom 23.10.2026): eine Quelle für alles, die HGT-Kacheln
 * {@code N62E006} und {@code N62E007} mit 1″ („DEM1“), nach UTM 32 N umgetastet ({@code tools.HgtImport}).
 * <ul>
 * <li><b>Hauptraster</b>: 2048 × 2048 Zellen zu 12 m (24,6 km) um die Mitte der Umgebung; die Kacheln haben rund
 * 31 m Nord-Süd und 15 m Ost-West, darum liegt die Zelle unter dem feineren der beiden Abstände. Höhen in m über NN.</li>
 * <li><b>Fernring</b>: 512 × 512 Zellen zu 120 m (61 km) um dieselbe Mitte, damit der Horizont aus Bergen besteht und
 * nicht am Rand des Hauptrasters abbricht. Südlich von 62° N liegen keine Kacheln vor; dort setzt der Import den Rand
 * fort und lässt ihn auf {@link #FERN_FUELLUNG} auslaufen [G].</li>
 * </ul>
 * Je Zelle des Hauptrasters die Art: {@link #FJORD} (auf NN und vom Fjord aus erreichbar), sonst {@link #LAND}. Die
 * Kacheln setzen das Meer auf 0; der Grund darunter ist seit Phase 3 gemalt ({@link Fjordgrund}, F7), die Fjordzellen
 * tragen seine Höhe unter NN.
 * <p>
 * Zwischen beiden Rastern blendet die Höhe auf den letzten {@link #NAHT} m innerhalb des Hauptrasters weich über; Java
 * ({@link #height}) und {@code terrain_height.glsl} rechnen dasselbe, darum sehen Kamera und Bild dieselbe Fläche.
 * Höhen liegen als 16-Bit-Werte vor ({@link #HMIN} bis {@link #HMAX}, 3,7 cm Stufe, NN genau), dieselben Werte gehen als
 * R16-Textur auf die Grafikkarte. Ohne {@code daten/gelaende} entsteht das Gelände aus der Bühne von Phase 1.
 */
public final class Terrain implements com.dan.geiranger.wiring.Terrain {
    public static final int N = 2048, FN = 512;
    public static final double CELL = 12, FCELL = 120;
    /** Mitte beider Raster in der Szene (Mitte der Umgebung, E 402 200, N 6 890 100). */
    public static final double MX = -4130, MZ = -3340;
    /** Nordwestecke des Hauptrasters; Zelle (i, j) hat ihre Mitte bei X0 + (i + 0,5) · CELL. */
    public static final double X0 = MX - N * CELL / 2, Z0 = MZ - N * CELL / 2;
    /** Nordwestecke des Fernrings. */
    public static final double FX0 = MX - FN * FCELL / 2, FZ0 = MZ - FN * FCELL / 2;
    /** Höhenbereich der 16-Bit-Werte: Stufe 300/8192 m (3,7 cm), so gewählt, dass NN genau ein Wert ist (8192). */
    public static final double HMIN = -300, HMAX = HMIN + 65535 * (300.0 / 8192);
    /** Breite des Überblendbands am Rand des Hauptrasters (m). */
    public static final double NAHT = 800;
    /** Höhe, auf die der Fernring dort ausläuft, wo keine Kachel vorliegt (südlich von 62° N) [G]. */
    public static final double FERN_FUELLUNG = 700;
    /** Art je Zelle. */
    public static final byte LAND = 0, FJORD = 1;
    public static final String[] ART = {"Land", "Fjord"};
    /** Bis zu dieser Höhe zählt eine Zelle als Wasser, wenn sie mit dem Fjord verbunden ist (die Kacheln setzen 0). */
    public static final double WASSER = 0.5;

    /** Höhen als vorzeichenlose 16-Bit-Werte, Zeile für Zeile von Nord nach Süd. */
    public final short[] h, fern;
    public final byte[] art;
    public final String source;
    public final Properties props;
    private static final int LEVELS = 12;
    private final float[][] minPyr = new float[LEVELS][], maxPyr = new float[LEVELS][];
    private final int[] side = new int[LEVELS];
    private final float[][] fMin = new float[LEVELS][], fMax = new float[LEVELS][];
    private final int[] fSide = new int[LEVELS];

    /**
     * Seit Phase 8: benannte Punkte, die {@link Formen} beim Laden setzt (etwa die Kante über einer geschärften Wand:
     * x, y, z, Richtung bergab), und was geformt wurde.
     */
    public final java.util.Map<String, double[]> punkte = new java.util.LinkedHashMap<>();
    public final java.util.List<String> formen = new java.util.ArrayList<>();
    /** Phase 17: dasselbe als Zahlen (für GR_FORM). */
    public final java.util.List<Formen.Form> formDaten = new java.util.ArrayList<>();
    /** Seit Phase 8: die Geirangelva (Lauf, Spiegel, Stufen), auf dem ungeformten Gelände bestimmt; ohne Höhendaten null. */
    public volatile Elva elva;
    /** Seit Phase 9: die Fv63 mit dem Ørnevegen; ohne Höhendaten null. */
    public volatile Ornevegen ornevegen;

    /**
     * Seit Phase 9: das Feinraster ({@link #FEIN} m) über den Kehren, für Formen, die das 12-m-Raster nicht trägt
     * (Einschnitt, Berme und Damm der Straße): je Zelle die Abweichung (m) von der Höhe aus Hauptraster und Fernring.
     * {@link #height} rechnet sie bilinear dazu, {@code terrain_height.glsl} ebenso ({@code uFein}). Ohne Feinraster null.
     */
    public static final double FEIN = 2;
    public volatile float[] fein;
    public double feinX0, feinZ0;
    public int feinNx, feinNz;

    public Terrain(short[] h, short[] fern, byte[] art, Properties props) {
        this.h = h;
        this.fern = fern;
        this.art = art;
        this.props = props;
        this.source = props.getProperty("quelle", "Bühne von Phase 1 (keine Höhendaten)");
        pyramid(h, N, minPyr, maxPyr, side);
        pyramid(fern, FN, fMin, fMax, fSide);
    }

    /** Die Min-Max-Pyramide des Hauptrasters neu (nachdem {@link #h} geändert wurde, etwa von {@link Formen}). */
    public void refresh() { pyramid(h, N, minPyr, maxPyr, side); }

    /** Ob echte Höhendaten geladen sind (sonst die Bühne von Phase 1). */
    public boolean measured() { return props.containsKey("quelle"); }

    // ------------------------------------------------------------------ Abfragen

    public static short encode(double y) {
        double v = (y - HMIN) / (HMAX - HMIN) * 65535.0;
        return (short) (int) Math.max(0, Math.min(65535, Math.round(v)));
    }

    public static double decode(short s) { return HMIN + (s & 0xFFFF) / 65535.0 * (HMAX - HMIN); }

    public double at(int i, int j) {
        i = Math.max(0, Math.min(N - 1, i));
        j = Math.max(0, Math.min(N - 1, j));
        return decode(h[j * N + i]);
    }

    public double fernAt(int i, int j) {
        i = Math.max(0, Math.min(FN - 1, i));
        j = Math.max(0, Math.min(FN - 1, j));
        return decode(fern[j * FN + i]);
    }

    /** Mitte der Zelle i (x) bzw. j (z) im Hauptraster. */
    public static double cx(int i) { return X0 + (i + 0.5) * CELL; }
    public static double cz(int j) { return Z0 + (j + 0.5) * CELL; }
    /** Mitte der Zelle im Fernring. */
    public static double fcx(int i) { return FX0 + (i + 0.5) * FCELL; }
    public static double fcz(int j) { return FZ0 + (j + 0.5) * FCELL; }

    /** Art am Ort; außerhalb des Hauptrasters Land. */
    public byte artAt(double x, double z) {
        int i = (int) Math.floor((x - X0) / CELL), j = (int) Math.floor((z - Z0) / CELL);
        if (i < 0 || j < 0 || i >= N || j >= N) return LAND;
        return art[j * N + i];
    }

    /** Ob am Ort der Fjord steht. */
    public boolean fjord(double x, double z) { return artAt(x, z) == FJORD; }

    /** Abstand zum Rand des Hauptrasters (m, positiv innen, negativ außen). */
    public static double inside(double x, double z) {
        double x1 = X0 + N * CELL, z1 = Z0 + N * CELL;
        return Math.min(Math.min(x - X0, x1 - x), Math.min(z - Z0, z1 - z));
    }

    /** Anteil des Fernrings an der Höhe (0 tief innen, 1 außen), wie {@code terrain_height.glsl}. */
    public static double fernShare(double x, double z) { return 1 - smooth(0, NAHT, inside(x, z)); }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Höhe des Hauptrasters an (x, z), bilinear zwischen den Zellmitten wie die Textur. */
    public double mainHeight(double x, double z) {
        double u = (x - X0) / CELL - 0.5, v = (z - Z0) / CELL - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fx = u - i, fz = v - j;
        return (at(i, j) * (1 - fx) + at(i + 1, j) * fx) * (1 - fz) + (at(i, j + 1) * (1 - fx) + at(i + 1, j + 1) * fx) * fz;
    }

    /** Höhe des Fernrings an (x, z), bilinear. */
    public double fernHeight(double x, double z) {
        double u = (x - FX0) / FCELL - 0.5, v = (z - FZ0) / FCELL - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fx = u - i, fz = v - j;
        return (fernAt(i, j) * (1 - fx) + fernAt(i + 1, j) * fx) * (1 - fz) + (fernAt(i, j + 1) * (1 - fx) + fernAt(i + 1, j + 1) * fx) * fz;
    }

    /** Oberfläche an (x, z): Hauptraster, am Rand in den Fernring übergeblendet, außerhalb der Fernring. */
    @Override
    public double height(double x, double z) { return baseHeight(x, z) + feinAt(x, z); }

    /** Oberfläche ohne Feinraster (Hauptraster und Fernring). */
    public double baseHeight(double x, double z) {
        double f = fernShare(x, z);
        if (f <= 0) return mainHeight(x, z);
        if (f >= 1) return fernHeight(x, z);
        return mainHeight(x, z) * (1 - f) + fernHeight(x, z) * f;
    }

    /** Abweichung aus dem Feinraster an (x, z), bilinear zwischen den Zellmitten; außerhalb 0. */
    public double feinAt(double x, double z) {
        float[] d = fein;
        if (d == null) return 0;
        double u = (x - feinX0) / FEIN - 0.5, v = (z - feinZ0) / FEIN - 0.5;
        if (u < 0 || v < 0 || u > feinNx - 1 || v > feinNz - 1) return 0;
        int i = Math.min(feinNx - 2, (int) u), j = Math.min(feinNz - 2, (int) v);
        double fx = u - i, fz = v - j;
        int c = j * feinNx + i;
        return (d[c] * (1 - fx) + d[c + 1] * fx) * (1 - fz) + (d[c + feinNx] * (1 - fx) + d[c + feinNx + 1] * fx) * fz;
    }

    /** Wassertiefe bis NN (m): über dem Fjord bis zum gemalten Grund (Phase 3), auf dem Land 0. */
    @Override
    public double waterDepth(double x, double z) { return fjord(x, z) ? Math.max(0, -height(x, z)) : 0; }

    /** Kleinste und größte Höhe im Quadrat (x0, z0, Kante) aus den Min-Max-Pyramiden beider Raster. */
    public void range(double x0, double z0, double size, double[] out) {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        double[] r = new double[2];
        boolean mainHit = x0 + size > X0 && z0 + size > Z0 && x0 < X0 + N * CELL && z0 < Z0 + N * CELL;
        boolean fullyMain = inside(x0, z0) > NAHT && inside(x0 + size, z0 + size) > NAHT;
        if (mainHit) {
            pyrRange(minPyr, maxPyr, side, N, X0, Z0, CELL, x0, z0, size, r);
            lo = r[0];
            hi = r[1];
        }
        if (!fullyMain) {
            pyrRange(fMin, fMax, fSide, FN, FX0, FZ0, FCELL, x0, z0, size, r);
            lo = Math.min(lo, r[0]);
            hi = Math.max(hi, r[1]);
        }
        out[0] = lo;
        out[1] = hi;
    }

    private static void pyrRange(float[][] mn, float[][] mx, int[] sd, int n, double ox, double oz, double cell, double x0, double z0, double size, double[] out) {
        int i0 = (int) Math.floor((x0 - ox) / cell) - 1, j0 = (int) Math.floor((z0 - oz) / cell) - 1;
        int i1 = (int) Math.ceil((x0 + size - ox) / cell) + 1, j1 = (int) Math.ceil((z0 + size - oz) / cell) + 1;
        i0 = Math.max(0, Math.min(n - 1, i0)); j0 = Math.max(0, Math.min(n - 1, j0));
        i1 = Math.max(0, Math.min(n - 1, i1)); j1 = Math.max(0, Math.min(n - 1, j1));
        int span = Math.max(i1 - i0, j1 - j0) + 1, k = 0;
        while (k < LEVELS - 1 && mn[k + 1] != null && (1 << (k + 1)) * 2 <= span) k++;
        int s = sd[k];
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int j = j0 >> k; j <= j1 >> k; j++)
            for (int i = i0 >> k; i <= i1 >> k; i++) {
                int ii = Math.min(s - 1, i), jj = Math.min(s - 1, j);
                lo = Math.min(lo, mn[k][jj * s + ii]);
                hi = Math.max(hi, mx[k][jj * s + ii]);
            }
        out[0] = lo;
        out[1] = hi;
    }

    private static void pyramid(short[] src, int n0, float[][] minPyr, float[][] maxPyr, int[] side) {
        float[] base = new float[n0 * n0];
        for (int i = 0; i < base.length; i++) base[i] = (float) decode(src[i]);
        minPyr[0] = base;
        maxPyr[0] = base;
        side[0] = n0;
        int n = n0;
        for (int k = 1; k < LEVELS && n > 1; k++) {
            int m = (n + 1) / 2;
            float[] mn = new float[m * m], mx = new float[m * m], pmn = minPyr[k - 1], pmx = maxPyr[k - 1];
            for (int j = 0; j < m; j++)
                for (int i = 0; i < m; i++) {
                    float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
                    for (int b = 0; b < 2; b++)
                        for (int a = 0; a < 2; a++) {
                            int ii = Math.min(n - 1, 2 * i + a), jj = Math.min(n - 1, 2 * j + b);
                            lo = Math.min(lo, pmn[jj * n + ii]);
                            hi = Math.max(hi, pmx[jj * n + ii]);
                        }
                    mn[j * m + i] = lo;
                    mx[j * m + i] = hi;
                }
            minPyr[k] = mn;
            maxPyr[k] = mx;
            side[k] = m;
            n = m;
        }
    }

    /** Anteil der Zellen je Art im Hauptraster. */
    public double[] shares() {
        long[] c = new long[ART.length];
        for (byte b : art) c[Math.max(0, Math.min(ART.length - 1, b))]++;
        double[] r = new double[c.length];
        for (int k = 0; k < c.length; k++) r[k] = c[k] / (double) art.length;
        return r;
    }

    /** Fläche des Fjords im Hauptraster (km²). */
    public double fjordKm2() { return shares()[FJORD] * N * N * CELL * CELL / 1e6; }

    /** „HGT 1″ N62E006 + N62E007 · Land 95 %, Fjord 5 %“ */
    public String describe() {
        double[] s = shares();
        StringBuilder b = new StringBuilder(source);
        for (int k = 0; k < s.length; k++) b.append(k == 0 ? " · " : ", ").append(ART[k]).append(String.format(Locale.GERMAN, " %.1f %%", s[k] * 100));
        return b.toString();
    }

    // ------------------------------------------------------------------ Dateien

    public static final String HOEHE = "hoehe.r16", FERN = "fern.r16", ARTEN = "art.u8", INFO = "gelaende.properties";

    /** Liest {@code daten/gelaende} (oder die Bühne, wenn es fehlt). */
    public static Terrain load(File dir) {
        File hf = new File(dir, HOEHE), ff = new File(dir, FERN), af = new File(dir, ARTEN), pf = new File(dir, INFO);
        if (!hf.isFile() || !ff.isFile() || !af.isFile() || !pf.isFile()) return flat();
        try {
            Properties p = new Properties();
            try (InputStream in = new FileInputStream(pf)) { p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)); }
            short[] h = readShorts(hf, N);
            short[] f = readShorts(ff, FN);
            byte[] a = Files.readAllBytes(af.toPath());
            if (a.length != N * N) throw new IOException(af + ": falsche Größe");
            Terrain t = new Terrain(h, f, a, p);
            if (!Boolean.getBoolean("geiranger.roh")) Formen.anwenden(t);   // Phase 8: Wände schärfen, Bett der Elva
            return t;
        } catch (IOException | RuntimeException e) {
            System.err.println("Gelände: " + e.getMessage() + " – es bleibt die Bühne von Phase 1");
            return flat();
        }
    }

    private static short[] readShorts(File f, int n) throws IOException {
        byte[] raw = Files.readAllBytes(f.toPath());
        if (raw.length != n * n * 2) throw new IOException(f + ": " + raw.length + " Bytes statt " + n * n * 2);
        short[] s = new short[n * n];
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s);
        return s;
    }

    /** Ordner {@code daten/gelaende} im Arbeitsverzeichnis ({@code -Dgeiranger.daten=…} setzt ihn anders). */
    public static File defaultDir() {
        String d = System.getProperty("geiranger.daten");
        return new File(d != null ? d : "daten", "gelaende");
    }

    public void save(File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Ordner " + dir + " lässt sich nicht anlegen");
        writeShorts(new File(dir, HOEHE), h);
        writeShorts(new File(dir, FERN), fern);
        Files.write(new File(dir, ARTEN).toPath(), art);
        try (OutputStream out = new java.io.FileOutputStream(new File(dir, INFO))) {
            props.store(new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8), "Gelaende der Szene Geiranger (tools.HgtImport)");
        }
    }

    private static void writeShorts(File f, short[] s) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(s.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        bb.asShortBuffer().put(s);
        Files.write(f.toPath(), bb.array());
    }

    /** Das Gelände aus der Bühne von Phase 1 ({@link Buehne}), wenn {@code daten/gelaende} fehlt: überall NN, Land. */
    public static Terrain flat() {
        short[] h = new short[N * N], f = new short[FN * FN];
        java.util.Arrays.fill(h, encode(0));
        java.util.Arrays.fill(f, encode(0));
        return new Terrain(h, f, new byte[N * N], new Properties());
    }

    /**
     * Ordnet jede Zelle des Hauptrasters zu: Flutung über alle Zellen bis {@link #WASSER} vom Rand des Rasters (der
     * Fjord läuft im Westen und Norden hinaus) und von den Startpunkten {@code seeds} (Zellindex) aus; was sie erreicht,
     * ist {@link #FJORD}, der Rest {@link #LAND}. Seen über NN bleiben Land (Phase 3 prüft sie).
     */
    public static byte[] classify(double[] y, int... seeds) {
        byte[] a = new byte[N * N];
        int[] queue = new int[N * N];
        int head = 0, tail = 0;
        java.util.List<Integer> start = new java.util.ArrayList<>();
        for (int k = 0; k < N; k++) for (int e : new int[]{k, (N - 1) * N + k, k * N, k * N + N - 1}) start.add(e);
        for (int s : seeds) start.add(s);
        for (int e : start)
            if (e >= 0 && e < a.length && a[e] != FJORD && y[e] <= WASSER) { a[e] = FJORD; queue[tail++] = e; }
        while (head < tail) {
            int c = queue[head++], i = c % N, j = c / N;
            int[] nb = {i > 0 ? c - 1 : -1, i < N - 1 ? c + 1 : -1, j > 0 ? c - N : -1, j < N - 1 ? c + N : -1};
            for (int n : nb) {
                if (n < 0 || a[n] == FJORD || y[n] > WASSER) continue;
                a[n] = FJORD;
                queue[tail++] = n;
            }
        }
        return a;
    }

    /** Zellindex zum Ort (x, z) im Hauptraster, −1 außerhalb. */
    public static int index(double x, double z) {
        int i = (int) Math.floor((x - X0) / CELL), j = (int) Math.floor((z - Z0) / CELL);
        return i < 0 || j < 0 || i >= N || j >= N ? -1 : j * N + i;
    }
}
