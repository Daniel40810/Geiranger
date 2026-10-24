package com.dan.geiranger.tools;

import com.dan.geiranger.scene.Echo;
import com.dan.geiranger.scene.Elva;
import com.dan.geiranger.scene.Faelle;
import com.dan.geiranger.scene.Flotte;
import com.dan.geiranger.scene.Klang;
import com.dan.geiranger.scene.SceneClock;
import com.dan.geiranger.scene.Terrain;
import com.dan.soundscape.Soundscape;
import com.dan.soundscape.Wav;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 15, Abnahme ohne Lautsprecher: eine Fahrt mit der Fähre an den Sieben Schwestern vorbei (21. Juni 2026, MF
 * Bolsøy von Hellesylt, ab 8:30). Das Ohr steht auf dem Sonnendeck an der Reling Backbord und schaut voraus; die Schwestern liegen links.
 * <ul>
 * <li>Der Pegel der Schwestern am Ohr über ± 15 min (aus {@link Klang}: Leistung des Falls, Abstand, Grat) – er
 * steigt und fällt.</li>
 * <li>Der Ton von 2 min vor bis 1 min nach der Stelle vor den Schwestern, mit dem kurzen Horn dort und seinen Echos
 * (WAV); daraus die Hüllkurve des Horns im Band um 220 Hz.</li>
 * <li>Ein Bild mit beidem.</li>
 * </ul>
 * Aufruf: {@code KlangFahrt [Bild.png] [Ton.wav] [Anleger.wav]}; ohne Argumente nach docs/status.
 */
public final class KlangFahrt {
    private KlangFahrt() { }

    /** Ergebnis einer Fahrt. */
    public record Fahrt(double[] zeit, double[] pegel, double vorbei, double hornZeit, List<Echo.Treffer> echos, float[] ton, int rate,
                        double hornTon, double verzoegerung, String beschreibung, String echoText, double ohrX, double ohrZ) { }

    /** Die Fälle, wie die App sie baut (Juni), ohne die Regenfälle. */
    public static List<Faelle.Eintrag> faelle(Terrain t) {
        if (t.elva == null) t.elva = Elva.build(t);
        List<Faelle.Eintrag> out = new ArrayList<>();
        for (Faelle.Eintrag e : Faelle.build(t, t::height, com.dan.fall.Fall.Quality.MEDIUM)) if (e.def.art() != Faelle.Art.REGEN) out.add(e);
        return out;
    }

    static double epoch(int h, int m, int s) { return ZonedDateTime.of(2026, 6, 21, h, m, s, 0, SceneClock.ZONE).toEpochSecond(); }

    /**
     * Die Fahrt: Pegel der Schwestern je 10 s über ± {@code spanne} s um die Stelle vor den Schwestern; mit
     * {@code ton} der Ton von {@code vor} s davor bis {@code nach} s danach.
     */
    public static Fahrt fahrt(Terrain t, List<Faelle.Eintrag> fs, double spanne, boolean ton, double vor, double nach) {
        Flotte fl = new Flotte(t);
        Flotte.Schiff f = fl.schiffe.get(1);   // MF Bolsøy beginnt in Hellesylt
        // die Stelle vor den Schwestern suchen
        double t0 = epoch(8, 30, 0), best = 1e9, vorbei = t0;
        for (double ep = t0; ep < t0 + Flotte.FAHRZEIT; ep += 2) {
            fl.stelle(ep);
            if (!f.da) continue;
            double d = Math.hypot(f.x - Flotte.SCHWESTERN_PASS[0], f.z - Flotte.SCHWESTERN_PASS[1]);
            if (d < best) { best = d; vorbei = ep; }
        }
        Klang k = new Klang(t, Soundscape.Quality.MEDIUM, null);
        k.echoBereit(20_000);
        Klang.Lage L = lage(fs);
        L.flotte = fl;
        // Pegel über die Fahrt (ohne Ton)
        int n = (int) (2 * spanne / 10) + 1;
        double[] zeit = new double[n], pegel = new double[n];
        for (int i = 0; i < n; i++) {
            double ep = vorbei - spanne + i * 10;
            fl.stelle(ep);
            ohr(L, f, ep);
            L.dt = 0.3;
            k.update(L);
            zeit[i] = ep - vorbei;
            pegel[i] = k.fallPegel(Faelle.SCHWESTERN.name())[0];
        }
        if (!ton) return new Fahrt(zeit, pegel, vorbei, Double.NaN, List.of(), null, 0, Double.NaN, 0, k.describe(), "", 0, 0);
        // der Ton: neu anfangen (gleicher Startwert), das Horn kommt an der Stelle vor den Schwestern von selbst
        Klang h = new Klang(t, Soundscape.Quality.MEDIUM, null);
        h.echoBereit(20_000);
        h.offline();
        h.s.autoGain = false;
        h.s.volume = 0.25;
        L.flotte = fl;
        double schritt = 0.05;
        int sr = h.s.sampleRate();
        float[] all = new float[(int) Math.ceil((vor + nach) * sr * 2) + 4 * sr];
        int pos = 0;
        double hornTon = Double.NaN, hornZeit = Double.NaN;
        List<Echo.Treffer> echos = List.of();
        String echoText = "kein Horn";
        double ohrX = 0, ohrZ = 0;
        for (double ep = vorbei - vor; ep < vorbei + nach; ep += schritt) {
            fl.stelle(ep);
            ohr(L, f, ep);
            L.dt = schritt;
            h.update(L);
            if (Double.isNaN(hornZeit) && !h.describe().contains("Horn: noch keins")) {
                hornZeit = ep - vorbei;
                hornTon = h.s.time() + h.verzoegerung;
                Echo e = h.echo();
                if (e != null) echos = e.finde(f.x, f.y + f.freibord + 10, f.z, L.ohr[0], L.ohr[1], L.ohr[2], 220, Klang.ECHOS);
                echoText = Echo.describe(echos, L.ohr[0], L.ohr[2]);
                ohrX = L.ohr[0]; ohrZ = L.ohr[2];
            }
            float[] b = h.s.render(schritt);
            System.arraycopy(b, 0, all, pos, Math.min(b.length, all.length - pos));
            pos += b.length;
        }
        float[] out = java.util.Arrays.copyOf(all, Math.min(pos, all.length));
        return new Fahrt(zeit, pegel, vorbei, hornZeit, echos, out, sr, hornTon, h.verzoegerung, h.describe(), echoText, ohrX, ohrZ);
    }

    /**
     * Nacharbeit 15 (Status_Phase15a: in Fahrt verzerrt und rauschend): 20 s Ton auf der fahrenden Fähre gegen 20 s an
     * derselben Stelle stehend, je Bild (45 je s) ein Schritt wie in der App. Maß für Knacken: Scheitel der zweiten
     * Differenz gegen ihren Effektivwert, je 50 ms, das Höchste. Gibt {stehend, fahrend, Weg in m}.
     */
    public static double[] knacken(Terrain t, List<Faelle.Eintrag> fs) {
        double[] out = new double[3];
        for (int mode = 0; mode < 2; mode++) {
            Flotte fl = new Flotte(t);
            Flotte.Schiff f = fl.schiffe.get(1);
            Klang k = new Klang(t, Soundscape.Quality.MEDIUM, null);
            k.echoBereit(20_000);
            k.offline();
            k.s.autoGain = false;
            Klang.Lage L = lage(fs);
            L.wind = 0.5; L.hs = 0;
            double ep0 = epoch(9, 2, 0), bild = 1 / 45.0;
            fl.stelle(ep0);
            double x0 = f.x, z0 = f.z;
            int sr = k.s.sampleRate();
            float[] o = new float[(int) (21 * sr * 2)];
            int p = 0;
            for (double ep = ep0; ep < ep0 + 20; ep += bild) {
                fl.stelle(mode == 1 ? ep : ep0);
                ohr(L, f, ep);
                L.dt = bild;
                k.update(L);
                float[] b = k.s.render(bild);
                System.arraycopy(b, 0, o, p, Math.min(b.length, o.length - p));
                p += b.length;
            }
            int blk = sr / 20;
            double worst = 0;
            for (int s0 = 2 * sr; s0 + blk < p / 2; s0 += blk) {
                double e = 0, m = 0;
                for (int i = s0; i < s0 + blk; i++) { double d = o[2 * i] - 2 * o[2 * i - 2] + o[2 * i - 4]; e += d * d; m = Math.max(m, Math.abs(d)); }
                worst = Math.max(worst, m / Math.sqrt(e / blk + 1e-20));
            }
            out[mode] = worst;
            if (mode == 1) out[2] = Math.hypot(f.x - x0, f.z - z0);
        }
        return out;
    }

    static Klang.Lage lage(List<Faelle.Eintrag> fs) {
        Klang.Lage L = new Klang.Lage();
        L.faelle = fs;
        L.wind = 1.5; L.windVon = 270; L.boee = 0.2; L.regen = 0; L.luft = 15; L.hs = 0.1; L.tp = 2; L.sonne = 45;
        return L;
    }

    /** Ohr auf dem Sonnendeck an der Reling Backbord wie „Auf der Fähre mitfahren“, Blick voraus (Backbord links, Steuerbord rechts). */
    static void ohr(Klang.Lage L, Flotte.Schiff f, double ep) {
        L.epoch = ep;
        L.ohr = Klang.anBord(f, -(0.78 * f.hw - 0.7), f.freibord + 6.8 + 1.65, 0.05 * f.hl);
        L.yaw = f.kurs;
    }

    /** Hüllkurve im Band um f (Hz) in dB, je 20 ms, ab Probe a über n Proben (Stereo verschränkt, links und rechts gemittelt). */
    public static double[] huelle(float[] ton, int sr, double f, int a, int n) { return huelle(ton, sr, f, a, n, -1); }

    /** Wie oben für einen Kanal (0 links, 1 rechts, −1 beide). */
    public static double[] huelle(float[] ton, int sr, double f, int a, int n, int kanal) {
        double w0 = 2 * Math.PI * f / sr, q = 3, al = Math.sin(w0) / (2 * q);
        double b0 = al, b2 = -al, a0 = 1 + al, a1 = -2 * Math.cos(w0), a2 = 1 - al;
        double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
        int blk = sr / 50, m = n / blk;
        double[] out = new double[m];
        double acc = 0;
        int c = 0, k = 0;
        for (int i = a; i < a + n && 2 * i + 1 < ton.length; i++) {
            double x = kanal < 0 ? 0.5 * (ton[2 * i] + ton[2 * i + 1]) : ton[2 * i + kanal];
            double y = (b0 * x + b2 * x2 - a1 * y1 - a2 * y2) / a0;
            x2 = x1; x1 = x; y2 = y1; y1 = y;
            acc += y * y;
            if (++c == blk) { if (k < m) out[k++] = 10 * Math.log10(acc / blk + 1e-14); acc = 0; c = 0; }
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        String bild = args.length > 0 ? args[0] : "docs/status/Status_Phase15.png";
        String wav = args.length > 1 ? args[1] : "docs/status/Status_Phase15.wav";
        Terrain t = Terrain.load(Terrain.defaultDir());
        if (!t.measured()) { System.out.println("Kein Gelände (tools.HgtImport)"); return; }
        long c0 = System.nanoTime();
        List<Faelle.Eintrag> fs = faelle(t);
        Fahrt r = fahrt(t, fs, 900, true, 120, 60);
        System.out.printf(Locale.GERMAN, "Fahrt MF Bolsøy, 21.6.2026: vor den Schwestern um %s; Horn %.0f s danach, erklingt %.1f s später (Echos leiser über den Weg)%n",
                java.time.Instant.ofEpochSecond((long) r.vorbei()).atZone(SceneClock.ZONE).toLocalTime(), r.hornZeit(), r.verzoegerung());
        System.out.println("  " + r.echoText());
        double mx = -1e9, mxT = 0, lo = 1e9;
        for (int i = 0; i < r.pegel().length; i++) {
            if (r.pegel()[i] > mx) { mx = r.pegel()[i]; mxT = r.zeit()[i]; }
            if (Math.abs(r.zeit()[i]) > 600 && r.pegel()[i] < lo) lo = r.pegel()[i];
        }
        System.out.printf(Locale.GERMAN, "  Sieben Schwestern am Ohr: höchstens %.0f dB (%+.0f s), 10 min davor oder danach höchstens %.0f dB%n", mx, mxT, lo);
        System.out.println("  " + r.beschreibung());
        File wf = new File(wav);
        if (wf.getParentFile() != null) wf.getParentFile().mkdirs();
        Wav.write(wf, r.ton(), r.rate());
        System.out.printf(Locale.GERMAN, "  %s: %.0f s, %d Hz, %.1f MB · %.1f s gerechnet%n", wav, r.ton().length / 2.0 / r.rate(), r.rate(), wf.length() / 1e6, (System.nanoTime() - c0) / 1e9);
        zeichne(r, new File(bild));
        System.out.println("  " + bild);
    }

    /** Das Bild: oben der Pegel der Schwestern über die Fahrt, unten die Hüllkurve des Horns mit den Echos. */
    static void zeichne(Fahrt r, File f) throws Exception {
        int w = 1600, hgt = 900;
        BufferedImage img = new BufferedImage(w, hgt, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color bg = new Color(13, 19, 22), ink = new Color(231, 236, 236), mute = new Color(143, 160, 164), fj = new Color(93, 183, 174), gold = new Color(232, 190, 106), line = new Color(38, 52, 58);
        g.setColor(bg);
        g.fillRect(0, 0, w, hgt);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));
        g.setColor(ink);
        g.drawString("Phase 15 · Klang: eine Fahrt mit der Fähre an den Sieben Schwestern (21. 6. 2026, MF Bolsøy)", 40, 50);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        // oben: Pegel
        int x0 = 110, x1 = w - 50, y0 = 100, y1 = 430;
        double tMin = r.zeit()[0], tMax = r.zeit()[r.zeit().length - 1], pMin = 10, pMax = 80;
        g.setColor(mute);
        g.drawString("Sieben Schwestern am Ohr (dB) über die Fahrt, Minuten vor und nach der Stelle 260 m vor dem Fall", x0, y0 - 12);
        achsen(g, x0, y0, x1, y1, line, mute);
        for (int p = 20; p <= 80; p += 20) {
            int y = (int) (y1 - (p - pMin) / (pMax - pMin) * (y1 - y0));
            g.setColor(line); g.drawLine(x0, y, x1, y);
            g.setColor(mute); g.drawString(p + " dB", x0 - 70, y + 6);
        }
        for (int m = -15; m <= 15; m += 5) {
            int x = (int) (x0 + (m * 60 - tMin) / (tMax - tMin) * (x1 - x0));
            g.setColor(line); g.drawLine(x, y0, x, y1);
            g.setColor(mute); g.drawString((m > 0 ? "+" : "") + m + " min", x - 24, y1 + 24);
        }
        Path2D pa = new Path2D.Double();
        boolean first = true;
        for (int i = 0; i < r.zeit().length; i++) {
            double p = r.pegel()[i];
            if (Double.isNaN(p)) continue;
            double x = x0 + (r.zeit()[i] - tMin) / (tMax - tMin) * (x1 - x0), y = y1 - (Math.max(pMin, Math.min(pMax, p)) - pMin) / (pMax - pMin) * (y1 - y0);
            if (first) pa.moveTo(x, y); else pa.lineTo(x, y);
            first = false;
        }
        g.setColor(fj);
        g.setStroke(new BasicStroke(3f));
        g.draw(pa);
        if (!Double.isNaN(r.hornZeit())) {
            int x = (int) (x0 + (r.hornZeit() - tMin) / (tMax - tMin) * (x1 - x0));
            g.setColor(gold);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(x, y0, x, y1);
            g.drawString("Horn", x + 6, y0 + 20);
        }
        // unten: Hüllkurve des Horns
        int u0 = 520, u1 = 840;
        g.setStroke(new BasicStroke(1f));
        g.setColor(mute);
        g.drawString("Das Horn vor den Schwestern und seine Echos: Pegel im Band um 220 Hz (dB, relativ), Sekunden nach dem Horn", x0, u0 - 12);
        achsen(g, x0, u0, x1, u1, line, mute);
        if (r.ton() != null && !Double.isNaN(r.hornTon())) {
            int a = (int) ((r.hornTon() - 0.5) * r.rate()), n = (int) (8.5 * r.rate());
            double[][] hk = {huelle(r.ton(), r.rate(), 220, Math.max(0, a), n, 0), huelle(r.ton(), r.rate(), 220, Math.max(0, a), n, 1)};
            double top = -1e9;
            for (double[] hl : hk) for (double v : hl) top = Math.max(top, v);
            double span = 50;
            for (int s = 0; s <= 8; s++) {
                int x = (int) (x0 + (s + 0.5) / 8.5 * (x1 - x0));
                g.setColor(line); g.drawLine(x, u0, x, u1);
                g.setColor(mute); g.drawString(s + " s", x - 10, u1 + 24);
            }
            for (int d = 0; d <= 50; d += 10) {
                int y = (int) (u0 + d / span * (u1 - u0));
                g.setColor(line); g.drawLine(x0, y, x1, y);
                g.setColor(mute); g.drawString("−" + d, x0 - 50, y + 6);
            }
            Color[] kf = {fj, new Color(201, 83, 60)};
            String[] kn = {"links (Backbord, zu den Schwestern)", "rechts (Steuerbord)"};
            for (int c = 0; c < 2; c++) {
                double[] hl = hk[c];
                Path2D ph = new Path2D.Double();
                for (int i = 0; i < hl.length; i++) {
                    double x = x0 + (i * 0.02) / 8.5 * (x1 - x0), y = u0 + Math.min(span, top - hl[i]) / span * (u1 - u0);
                    if (i == 0) ph.moveTo(x, y); else ph.lineTo(x, y);
                }
                g.setColor(kf[c]);
                g.setStroke(new BasicStroke(2.2f));
                g.draw(ph);
                g.drawString(kn[c], x1 - 360, u0 + 70 + 24 * c);
            }
            g.setColor(gold);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{6, 6}, 0));
            for (Echo.Treffer e : r.echos()) {
                double dt = e.laufzeit(343) - 0;   // Laufzeit über die Wand minus Weg direkt (Horn an Bord: ≈ 0)
                int x = (int) (x0 + (dt + 0.5) / 8.5 * (x1 - x0));
                g.drawLine(x, u0, x, u1);
                g.drawString(String.format(Locale.GERMAN, "Echo %.1f s aus %03.0f°", dt, e.peilung(r.ohrX(), r.ohrZ())), x + 6, u0 + 20 + 22 * r.echos().indexOf(e));
            }
        }
        g.setColor(mute);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        g.drawString("Erzeugt mit com.dan.soundscape und tools.KlangFahrt · Ton in Status_Phase15.wav · Pegel und Echos [G]", x0, hgt - 18);
        g.dispose();
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }

    static void achsen(Graphics2D g, int x0, int y0, int x1, int y1, Color line, Color mute) {
        g.setColor(line);
        g.drawRect(x0, y0, x1 - x0, y1 - y0);
    }
}
