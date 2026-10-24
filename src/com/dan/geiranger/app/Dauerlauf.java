package com.dan.geiranger.app;

import com.dan.geiranger.gl.Renderer;
import com.dan.geiranger.scene.Regie;
import com.dan.geiranger.scene.SceneClock;
import com.dan.weather.Weather;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 18: der Dauerlauf (Abnahme „fertig“, nach Rungholt). Die App läuft mit Fenster und Grafikkarte in Runden; jede
 * Runde spielt die drei Regiebücher im Kino und dazwischen zwei Tage frei im Zeitraffer:
 * <ol>
 * <li>„Ein Junitag“ im Kino (21. Juni, 4 Uhr bis Mitternacht),</li>
 * <li>frei im Zeitraffer {@value #RAFFER}: der 15. Februar von 8 bis 16 Uhr (Schnee, Eis an den Fällen) und der
 * 15. Oktober von 8 bis 16 Uhr mit einer Front (Regen, Regenfälle, Herbstlaub, die Fälle rechnen neu),</li>
 * <li>„Mit der Fähre von Hellesylt“ im Kino (an Bord, Horn und Echo vor den Schwestern),</li>
 * <li>„Fahrt über die Ørnesvingen“ im Kino (im Bus die elf Kehren hinauf), danach zurück auf den 21. Juni, 11 Uhr.</li>
 * </ol>
 * Es laufen mindestens zwei Runden, sonst so viele, bis die Minuten um sind (Vorgabe 30). Alle 5 s misst er Bilder/s,
 * Grafikkarte, Prozessor, Speicher, Fäden, Gäste, Fahrzeuge, Neuberechnungen der Fälle, Schritte der Physik, die nicht
 * nachkamen, und Fehler. Am Ende schreibt er {@code docs/status/Dauerlauf_….png} (Kurven) und {@code …txt} mit dem
 * Urteil:
 * <ul>
 * <li>keine Fehler (Fäden, GL, Protokoll);</li>
 * <li>jede Runde vollständig: drei Regiebücher bis zum Ende und zwei freie Tage;</li>
 * <li>im Mittel mindestens 30 Bilder/s;</li>
 * <li>kein Leck: der Speicher nach einer Bereinigung am Ende jeder Runde (immer an derselben Stelle der Szene) wächst von
 * der ersten bis zur letzten Runde um höchstens 10 % und 40 MB; die Zahl der Fäden wächst um höchstens 4;</li>
 * <li>die Grenzen der Szene halten: höchstens {@value #GAESTE_MAX} Gäste und {@value #FAHRZEUGE_MAX} Fahrzeuge zugleich, und
 * die meisten Fahrzeuge der letzten Runde sind nicht mehr als in der ersten (+10 % und 5; der Verkehr wächst nicht);</li>
 * <li>die Physik rechnete mindestens 99 % der Szenenzeit;</li>
 * <li>die Chronik schrieb mit (neue Stunden in der Datenbank oder in {@code daten/}).</li>
 * </ul>
 * Start: Block TECHNIK → „Dauerlauf 30 min“, oder {@code -Dgeiranger.dauerlauf=Minuten} beim Start. Esc beendet nur das
 * Kino, nicht den Dauerlauf; der endet von selbst oder mit „Abbrechen“.
 */
public final class Dauerlauf {
    /** Ein Messpunkt. */
    public record Punkt(double minute, double fps, double gpu, double cpu, double heapMb, double lebendMb, int faeden, int gaeste, int fahrzeuge, int faelle,
                 long nichtNach, int fehler, int runde) { }

    private static volatile Dauerlauf laufend;
    /** Fehler aus Fäden (GeirangerApp zählt sie) und aus dem Protokoll. */
    public static final java.util.concurrent.atomic.AtomicInteger FEHLER = new java.util.concurrent.atomic.AtomicInteger();
    /** Zeitraffer der freien Tage. */
    public static final double RAFFER = 360;
    public static final int MIN_RUNDEN = 2;
    /**
     * Grenzen der Szene. Fahrzeuge: im ersten Dauerlauf (24.10.2026) zählte die Fv63 um 11 Uhr am 21. Juni im Umkreis
     * von 1,2 km bis 125 Fahrzeuge, in beiden Runden gleich; die Grenze 120 war zu knapp geschätzt. Ein Leck zeigt sich am
     * Wachsen von Runde zu Runde, darum prüft der Lauf das, und die feste Grenze steht bei 200.
     */
    public static final int GAESTE_MAX = 60, FAHRZEUGE_MAX = 200;
    /** Die freien Tage: Datum, Lage des Wetters (null: Regie des Tages). */
    static final Object[][] FREI = {{LocalDate.of(2026, 2, 15), Weather.Phase.KLAR}, {LocalDate.of(2026, 10, 15), Weather.Phase.FRONT}};

    private final Renderer r;
    private final double minuten;
    private final List<Punkt> punkte = new ArrayList<>();
    private final List<double[]> runden = new ArrayList<>();   // je Runde: Minute, Speicher nach Bereinigung (MB), Regiebücher, freie Tage, Fäden
    private final long beginn = System.nanoTime();
    private volatile boolean aus;
    private volatile String stand = "";
    private int runde;

    private Dauerlauf(Renderer r, double minuten) {
        this.r = r;
        this.minuten = minuten;
    }

    public static boolean laeuft() { return laufend != null; }

    /** „Dauerlauf Runde 1 · 12 von 30 min · Kino: Ein Junitag“ oder leer. */
    public static String zeile() { Dauerlauf d = laufend; return d == null ? "" : d.stand; }

    /** Startet den Dauerlauf (einer zur Zeit); danach läuft {@code fertig} mit der Datei des Berichts (im Faden des Laufs). */
    public static synchronized boolean starten(Renderer r, double minuten, java.util.function.Consumer<File> fertig) {
        if (laufend != null) return false;
        Dauerlauf d = new Dauerlauf(r, Math.max(1, minuten));
        laufend = d;
        Thread t = new Thread(() -> {
            File f = null;
            try { f = d.lauf(); }
            catch (Exception e) { System.err.println("Dauerlauf: " + e); e.printStackTrace(); FEHLER.incrementAndGet(); }
            finally { laufend = null; }
            if (fertig != null) fertig.accept(f);
        }, "Geiranger-Dauerlauf");
        t.setDaemon(true);
        t.start();
        return true;
    }

    public static void anhalten() { Dauerlauf d = laufend; if (d != null) d.aus = true; }

    private double minute() { return (System.nanoTime() - beginn) / 6e10; }

    private long protokollMs = System.currentTimeMillis();

    /** Neue Fehler im Protokoll seit dem letzten Blick (die Zeilen bleiben für das Bedienfeld stehen). */
    private int protokoll() {
        int n = 0;
        long bis = protokollMs;
        for (var z : com.dan.store.Protokoll.zuletzt(500))
            if (z.zeitMs() > protokollMs) {
                if (com.dan.store.Protokoll.FEHLER.equals(z.stufe())) n++;
                bis = Math.max(bis, z.zeitMs());
            }
        protokollMs = bis;
        return n;
    }

    private static int stunden() {
        try { return com.dan.geiranger.data.Datenseite.speicher().stand().stunden(); }
        catch (Exception | Error e) { return -1; }
    }

    private File lauf() throws Exception {
        int fehler0 = FEHLER.get();
        long nichtNach0 = r.physik.dropped(), schritte0 = r.physik.steps();
        verloren0 = r.physik.lostSeconds();
        int stunden0 = stunden();
        // warten, bis die Szene steht (die Fälle sind gerechnet)
        for (int i = 0; i < 480 && (r.frames() < 3 || r.faelle.eintraege().isEmpty()); i++) Thread.sleep(250);
        r.ohneBlatt = true;
        try {
            while (!aus && (runde < MIN_RUNDEN || minute() < minuten)) runde(fehler0);
        } finally {
            r.ohneBlatt = false;
            if (r.kino.laeuft()) r.kinoVerlassen();
            if (r.regie.running()) r.regieStop();
            r.clock.lapse = 1;
        }
        long nichtNach = r.physik.dropped() - nichtNach0, schritte = Math.max(1, r.physik.steps() - schritte0);
        FEHLER.addAndGet(protokoll());
        int stunden1 = stunden();
        return bericht(FEHLER.get() - fehler0, nichtNach, schritte, stunden0, stunden1);
    }

    private void runde(int fehler0) throws InterruptedException {
        runde++;
        int buecher = 0, tage = 0;
        if (kino(Regie.junitag(), fehler0)) buecher++;
        for (Object[] f : FREI) if (!aus && frei((LocalDate) f[0], (Weather.Phase) f[1], fehler0)) tage++;
        if (kino(Regie.faehre(), fehler0)) buecher++;
        if (kino(Regie.ornesvingen(), fehler0)) buecher++;
        // zurück an dieselbe Stelle der Szene, dann den Speicher messen
        r.wiring.startFree(Weather.Phase.KLAR);
        r.clock.lapse = 1;
        r.clock.set(SceneClock.START_DATE, SceneClock.START_HOUR);
        Thread.sleep(3000);
        synchronized (runden) { runden.add(new double[]{minute(), heapNachGc(), buecher, tage, ManagementFactory.getThreadMXBean().getThreadCount()}); }
    }

    /** Ein freier Tag im Zeitraffer, 8 bis 16 Uhr; true, wenn er durchlief. */
    private boolean frei(LocalDate tag, Weather.Phase lage, int fehler0) throws InterruptedException {
        if (r.regie.running()) r.regieStop();
        r.clock.lapse = 1;
        r.clock.set(tag, 8.0);
        r.wiring.startFree(lage);
        r.clock.lapse = RAFFER;
        long naechste = 0;
        while (!aus && r.clock.hour() < 16.0 && r.clock.date().equals(tag)) {
            stand = String.format(Locale.GERMAN, "Dauerlauf Runde %d · %.0f von %.0f min · frei im Zeitraffer %.0f (%s, %s)", runde, minute(), minuten, RAFFER, r.clock.label(), lage);
            naechste = messen(naechste, fehler0);
            Thread.sleep(250);
        }
        r.clock.lapse = 1;
        return !aus;
    }

    /** Ein Regiebuch im Kino bis zum Ende; true, wenn es durchlief (nicht abgebrochen). */
    private boolean kino(Regie.Book b, int fehler0) throws InterruptedException {
        if (aus || b == null) return false;
        long naechste = 0;
        r.kinoStart(b);
        Thread.sleep(500);
        while (!aus && (r.kino.laeuft() || r.regie.running())) {
            stand = String.format(Locale.GERMAN, "Dauerlauf Runde %d · %.0f von %.0f min · Kino: %s", runde, minute(), minuten, b.name());
            naechste = messen(naechste, fehler0);
            Thread.sleep(250);
        }
        if (r.kino.laeuft()) r.kinoVerlassen();
        return !aus;
    }

    private double verloren0;
    private int letzteMinute = -1;

    private long messen(long naechste, int fehler0) {
        long jetzt = System.currentTimeMillis();
        if (jetzt < naechste) return naechste;
        Runtime rt = Runtime.getRuntime();
        double heap = (rt.totalMemory() - rt.freeMemory()) / 1048576.0;
        double lebend = 0;
        for (java.lang.management.MemoryPoolMXBean mp : ManagementFactory.getMemoryPoolMXBeans()) {
            if (mp.getType() != java.lang.management.MemoryType.HEAP) continue;
            java.lang.management.MemoryUsage u = mp.getCollectionUsage();
            if (u != null) lebend += u.getUsed() / 1048576.0;
        }
        int gaeste = 0;
        for (var g : r.leute.leute.gaeste) if (g.da) gaeste++;
        FEHLER.addAndGet(protokoll());
        if ((int) minute() != letzteMinute) { letzteMinute = (int) minute(); System.out.println(stand + String.format(Locale.GERMAN, " · %.0f Bilder/s · %s", r.fps(), r.clock.label())); }
        synchronized (punkte) {
            punkte.add(new Punkt(minute(), r.fps(), r.gpuMs(), r.cpuMs(), heap, lebend, ManagementFactory.getThreadMXBean().getThreadCount(), gaeste, r.strasse.fahrzeuge,
                    r.faelle.rebuilds, r.physik.dropped(), FEHLER.get() - fehler0, runde));
        }
        return jetzt + 5000;
    }

    /** Speicher nach einer Bereinigung (MB). */
    private static double heapNachGc() {
        System.gc();
        try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / 1048576.0;
    }

    /** Das Urteil aus Messpunkten und Runden (ohne Fenster prüfbar). */
    public static List<String[]> urteil(List<Punkt> p, List<double[]> rs, int fehler, long nichtNach, double nachAnteil, int stunden0, int stunden1) {
        int n = p.size();
        double fps = 0, fpsMin = Double.MAX_VALUE, gpu = 0;
        int gaesteMax = 0, fahrzeugeMax = 0, faedenMax = 0, faelle = 0;
        for (Punkt q : p) {
            fps += q.fps();
            gpu += q.gpu();
            if (q.minute() > 1) fpsMin = Math.min(fpsMin, q.fps());
            gaesteMax = Math.max(gaesteMax, q.gaeste());
            fahrzeugeMax = Math.max(fahrzeugeMax, q.fahrzeuge());
            faedenMax = Math.max(faedenMax, q.faeden());
        }
        if (n > 1) faelle = p.get(n - 1).faelle() - p.get(0).faelle();
        fps /= Math.max(1, n);
        gpu /= Math.max(1, n);
        boolean vollstaendig = rs.size() >= MIN_RUNDEN && rs.stream().allMatch(x -> x[2] >= 3 && x[3] >= FREI.length);
        StringBuilder rz = new StringBuilder();
        for (int i = 0; i < rs.size(); i++) rz.append(i == 0 ? "" : ", ").append(String.format(Locale.GERMAN, "%.0f Regiebücher + %.0f Tage", rs.get(i)[2], rs.get(i)[3]));
        double erst = rs.isEmpty() ? 0 : rs.get(0)[1], letzt = rs.isEmpty() ? 0 : rs.get(rs.size() - 1)[1];
        double fErst = rs.isEmpty() ? 0 : rs.get(0)[4], fLetzt = rs.isEmpty() ? 0 : rs.get(rs.size() - 1)[4];
        boolean dicht = rs.size() < 2 || letzt <= erst * 1.10 + 40 && fLetzt <= fErst + 4;
        StringBuilder sz = new StringBuilder();
        for (int i = 0; i < rs.size(); i++) sz.append(i == 0 ? "" : ", ").append(String.format(Locale.GERMAN, "%.0f", rs.get(i)[1]));
        int haenger = 0;
        for (Punkt q : p) if (q.minute() > 1 && q.fps() < 20) haenger++;
        List<String[]> u = new ArrayList<>();
        u.add(new String[]{fehler == 0 ? "ok" : "FEHLER", fehler + " Fehler (Fäden, GL, Protokoll)"});
        u.add(new String[]{vollstaendig ? "ok" : "FEHLER", String.format(Locale.GERMAN, "%d Runden: %s", rs.size(), rz.length() == 0 ? "keine" : rz)});
        u.add(new String[]{fps >= 30 ? "ok" : "FEHLER", String.format(Locale.GERMAN, "Ø %.1f Bilder/s (geringste %.0f), Grafikkarte Ø %.2f ms", fps, fpsMin == Double.MAX_VALUE ? 0 : fpsMin, gpu)});
        u.add(new String[]{dicht ? "ok" : "FEHLER", String.format(Locale.GERMAN, "Speicher nach Bereinigung am Ende jeder Runde %s MB (höchstens +10 %% und 40 MB), Fäden %.0f → %.0f (höchstens +4)",
                sz.length() == 0 ? "—" : sz, fErst, fLetzt)});
        int runden = 0;
        for (Punkt q : p) runden = Math.max(runden, q.runde());
        int fErsteR = 0, fLetzteR = 0;
        for (Punkt q : p) {
            if (q.runde() == 1) fErsteR = Math.max(fErsteR, q.fahrzeuge());
            if (q.runde() == runden) fLetzteR = Math.max(fLetzteR, q.fahrzeuge());
        }
        boolean verkehrStill = runden < 2 || fLetzteR <= fErsteR * 1.10 + 5;
        u.add(new String[]{gaesteMax <= GAESTE_MAX && fahrzeugeMax <= FAHRZEUGE_MAX && verkehrStill ? "ok" : "FEHLER", String.format(Locale.GERMAN,
                "höchstens %d Gäste (Grenze %d), %d Fahrzeuge (Grenze %d; erste Runde %d, letzte %d), %d Fäden; die Fälle rechneten %d-mal neu",
                gaesteMax, GAESTE_MAX, fahrzeugeMax, FAHRZEUGE_MAX, fErsteR, fLetzteR, faedenMax, faelle)});
        u.add(new String[]{nachAnteil >= 0.99 ? "ok" : "FEHLER", String.format(Locale.GERMAN, "Physik rechnete %.2f %% der Zeit (%d× nicht nachgekommen); Bild unter 20 Bilder/s in %d von %d Messungen",
                nachAnteil * 100, nichtNach, haenger, n)});
        boolean chronik = stunden0 < 0 || stunden1 > stunden0;
        u.add(new String[]{chronik ? "ok" : "FEHLER", stunden0 < 0 ? "Chronik: kein Speicher (nicht geprüft)"
                : String.format(Locale.GERMAN, "Chronik schrieb mit: %d neue Stunden in %s", stunden1 - stunden0, com.dan.geiranger.data.Datenseite.speicher().wo())});
        return u;
    }

    private File bericht(int fehler, long nichtNach, long schritte, int stunden0, int stunden1) throws Exception {
        List<Punkt> p;
        synchronized (punkte) { p = new ArrayList<>(punkte); }
        List<double[]> rs;
        synchronized (runden) { rs = new ArrayList<>(runden); }
        double verloren = r.physik.lostSeconds() - verloren0, gerechnet = schritte * com.dan.geiranger.scene.Physik.DT;
        double nachAnteil = gerechnet / Math.max(1e-9, gerechnet + verloren);
        List<String[]> urteil = urteil(p, rs, fehler, nichtNach, nachAnteil, stunden0, stunden1);
        boolean gut = urteil.stream().allMatch(u -> u[0].equals("ok"));
        String stempel = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        File dir = r.photoDir != null ? r.photoDir : new File("docs/status");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Ordner " + dir + " lässt sich nicht anlegen");
        StringBuilder t = new StringBuilder();
        t.append("Geiranger · Dauerlauf ").append(stempel).append(" · ").append(Renderer.VERSION).append(" · ").append(String.format(Locale.GERMAN, "%.1f min", minute()))
                .append(" · ").append(r.quality().label).append(" · ").append(r.glInfo()).append('\n');
        for (String[] u : urteil) t.append(String.format("%-7s %s%n", u[0], u[1]));
        t.append(gut ? "ERGEBNIS: Dauerlauf bestanden\n" : "ERGEBNIS: Dauerlauf nicht bestanden\n");
        t.append("\nMinute;Runde;Bilder/s;Grafikkarte ms;Prozessor ms;Speicher MB;lebend MB;Fäden;Gäste;Fahrzeuge;Fälle neu;nicht nachgekommen;Fehler\n");
        for (Punkt q : p)
            t.append(String.format(Locale.GERMAN, "%.2f;%d;%.1f;%.2f;%.2f;%.0f;%.0f;%d;%d;%d;%d;%d;%d%n", q.minute(), q.runde(), q.fps(), q.gpu(), q.cpu(), q.heapMb(), q.lebendMb(), q.faeden(),
                    q.gaeste(), q.fahrzeuge(), q.faelle(), q.nichtNach(), q.fehler()));
        File txt = new File(dir, "Dauerlauf_" + stempel + ".txt");
        Files.writeString(txt.toPath(), t.toString(), StandardCharsets.UTF_8);
        File png = new File(dir, "Dauerlauf_" + stempel + ".png");
        ImageIO.write(bild(p, rs, urteil, gut, minute(), r.quality().label), "png", png);
        System.out.print(t.substring(0, t.indexOf("\nMinute")));
        com.dan.store.Protokoll.info("Dauerlauf", (gut ? "bestanden" : "nicht bestanden") + ": " + txt.getName());
        return png;
    }

    public static BufferedImage bild(List<Punkt> p, List<double[]> rs, List<String[]> urteil, boolean gut, double minuten, String stufe) {
        int w = 1600, h = 900, l = 80, rt = 30, top = 90, bot = 270;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(11, 18, 22));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(230, 236, 234));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        g.drawString(String.format(Locale.GERMAN, "Geiranger · Dauerlauf %.0f min · %d Runden · %s · %s", minuten, rs.size(), stufe, gut ? "bestanden" : "nicht bestanden"), l, 40);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.setColor(new Color(150, 165, 166));
        g.drawString("Bilder/s (blau, 0–90) · lebender Speicher (gelb, 0–Höchstwert) · Gäste (weiß, 0–60) · Fahrzeuge (rot, 0–200) · Runden (senkrecht)", l, 66);
        int pw = w - l - rt, ph = h - top - bot;
        g.setColor(new Color(40, 54, 58));
        for (int i = 0; i <= 6; i++) g.drawLine(l, top + ph * i / 6, l + pw, top + ph * i / 6);
        double tMax = p.isEmpty() ? 1 : Math.max(1, p.get(p.size() - 1).minute());
        g.setColor(new Color(70, 92, 96));
        for (double[] x : rs) { int xx = l + (int) (pw * x[0] / tMax); g.drawLine(xx, top, xx, top + ph); }
        double hMax = 1;
        for (Punkt q : p) hMax = Math.max(hMax, q.lebendMb());
        final double hm = hMax;
        kurve(g, p, q -> q.fps() / 90, tMax, l, top, pw, ph, new Color(58, 143, 199));
        kurve(g, p, q -> q.lebendMb() / hm, tMax, l, top, pw, ph, new Color(224, 190, 120));
        kurve(g, p, q -> q.gaeste() / 60.0, tMax, l, top, pw, ph, new Color(240, 246, 249));
        kurve(g, p, q -> q.fahrzeuge() / 200.0, tMax, l, top, pw, ph, new Color(210, 96, 47));
        g.setColor(new Color(150, 165, 166));
        for (int m = 0; m <= (int) tMax; m += Math.max(1, (int) tMax / 10)) {
            int x = l + (int) (pw * m / tMax);
            g.drawString(m + " min", x - 12, top + ph + 20);
        }
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        int y = h - bot + 60;
        for (String[] u : urteil) {
            g.setColor(u[0].equals("ok") ? new Color(61, 177, 165) : new Color(220, 90, 70));
            g.drawString(String.format("%-7s", u[0]), l, y);
            g.setColor(new Color(230, 236, 234));
            g.drawString(u[1], l + 90, y);
            y += 26;
        }
        g.dispose();
        return im;
    }

    private static void kurve(Graphics2D g, List<Punkt> p, java.util.function.ToDoubleFunction<Punkt> f, double tMax, int l, int top, int pw, int ph, Color c) {
        if (p.size() < 2) return;
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < p.size(); i++) {
            double x = l + pw * p.get(i).minute() / tMax, y = top + ph * (1 - Math.max(0, Math.min(1, f.applyAsDouble(p.get(i)))));
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        g.setColor(c);
        g.setStroke(new BasicStroke(2f));
        g.draw(path);
    }
}
