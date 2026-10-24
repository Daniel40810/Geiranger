package com.dan.geiranger.test;

import com.dan.geiranger.app.Bibliothek;
import com.dan.geiranger.camera.Ansichten;
import com.dan.geiranger.camera.Camera;
import com.dan.geiranger.gl.ComputeProbe;
import com.dan.geiranger.gl.Mat4;
import com.dan.geiranger.gl.Quality;
import com.dan.geiranger.gl.Renderer;
import com.dan.geiranger.gl.Shader;
import com.dan.geiranger.scene.Buehne;
import com.dan.geiranger.scene.Fjordgrund;
import com.dan.geiranger.scene.Wandschatten;
import com.dan.geiranger.gl.Wasser;
import com.dan.geiranger.tools.Fjordprofil;
import com.dan.geiranger.scene.Geo;
import com.dan.geiranger.scene.Physik;
import com.dan.geiranger.scene.SceneClock;
import com.dan.geiranger.scene.Site;
import com.dan.geiranger.scene.Sun;
import com.dan.geiranger.scene.Terrain;
import com.dan.geiranger.gl.TerrainLod;
import com.dan.geiranger.ui.AppIcon;
import com.dan.geiranger.wiring.Wiring;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Prüfprogramm der App ohne Grafikkarte und ohne Datenbank (aus Rungholt, entschlackt). In NetBeans: Rechtsklick →
 * „Run File“, oder Ant-Target {@code pruefung}. Endet mit „ERGEBNIS: alles in Ordnung“ oder mit der Liste der
 * Verstöße (Rückgabewert 1). Wächst mit jeder Phase. Die Packages haben zusätzlich je ihre eigene Prüfklasse in
 * {@code .demo} ({@code FallCheck}, {@code CliffCheck}).
 */
public final class Pruefung {
    private static int ok, bad;

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        mathe();
        ort();
        sonne();
        uhr();
        gelaende();
        fjordgrund();
        wasser();
        wetter();
        wandschatten();
        fels();
        wasserfall();
        faelle();
        elva();
        strasse();
        wald();
        dorf();
        schiffe();
        menschen();
        jahr();
        klang();
        regie();
        daten();
        kamera();
        qualitaet();
        physik();
        shader();
        verdrahtung();
        bibliothek();
        symbol();
        schliff();
        pakete();
        isolation();
        System.out.printf(Locale.GERMAN, "%n%d Prüfungen, %d Verstöße, %.1f s%n", ok + bad, bad, (System.nanoTime() - t0) / 1e9);
        System.out.println(bad == 0 ? "ERGEBNIS: alles in Ordnung" : "ERGEBNIS: " + bad + " Verstöße");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean cond, String what) {
        if (cond) ok++; else bad++;
        System.out.println((cond ? "  ok    " : "  FEHLER ") + what);
    }

    static void mathe() {
        System.out.println("Matrizen");
        float[] v = Mat4.lookAt(10, 20, 30, 0, 14, 0);
        float[] p = Mat4.perspective(55, 16 / 9.0, 0.5, 120000, true);
        float[] vp = Mat4.mul(p, v), id = Mat4.mul(vp, Mat4.invert(vp));
        double err = 0;
        for (int i = 0; i < 16; i++) err = Math.max(err, Math.abs(id[i] - (i % 5 == 0 ? 1 : 0)));
        check(err < 1e-4, String.format(Locale.GERMAN, "Projektion·Blick mal Inverse = Einheit (Fehler %.1e)", err));
        float[] look = Mat4.lookAt(0, 0, 0, 0, 0, -1);
        double[] c = Mat4.apply(Mat4.mul(p, look), 0, 0, -100);
        check(Math.abs(c[2] / c[3] - 0.5 / 100) < 1e-6, "Umgekehrte Tiefe: Punkt in 100 m hat Tiefe Nahebene/100");
        double[] far = Mat4.apply(Mat4.mul(p, look), 0, 0, -18000);
        check(far[2] / far[3] > 0 && far[2] / far[3] < 1e-4, "Gipfel am Rand des Ausschnitts (18 km) liegen zwischen 0 und Nahebene, vor dem Unendlichen");
    }

    static void ort() {
        System.out.println("Ort (UTM 32 N, Ursprung am Anleger)");
        double[] ll = Geo.UTM32N.inverse(Site.E0, Site.N0), en = Geo.UTM32N.forward(ll[0], ll[1]);
        check(Math.hypot(en[0] - Site.E0, en[1] - Site.N0) < 0.01, String.format(Locale.GERMAN, "UTM hin und zurück auf 1 cm (%.4f m)", Math.hypot(en[0] - Site.E0, en[1] - Site.N0)));
        check(Math.abs(Site.LAT - 62.101) < 0.01 && Math.abs(Site.LON - 7.205) < 0.02, "Anleger bei " + Site.dms(Site.LAT, Site.LON) + " (Geiranger, Ende des Fjords)");
        double[] o = Site.fromUtm(Site.E0, Site.N0), n = Site.fromUtm(Site.E0, Site.N0 + 1000);
        check(o[0] == 0 && o[1] == 0 && n[1] == -1000 && n[0] == 0, "Ursprung (0, 0), Nord = −z (F2 A)");
        double[] sw = Geo.UTM32N.forward(Site.SOUTH, Site.WEST), ne = Geo.UTM32N.forward(Site.NORTH, Site.EAST);
        check(Site.UMGEBUNG.eMin() <= sw[0] + 50 && Site.UMGEBUNG.nMin() <= sw[1] + 50 && Site.UMGEBUNG.eMax() >= ne[0] - 50 && Site.UMGEBUNG.nMax() >= ne[1] - 50,
                String.format(Locale.GERMAN, "Umgebung %.1f × %.1f km deckt den GLO-30-Ausschnitt 6,95–7,30° O, 62,05–62,21° N", Site.UMGEBUNG.breite() / 1000, Site.UMGEBUNG.hoehe() / 1000));
        check(Site.KERN_WEST.breite() == 3000 && Site.KERN_WEST.hoehe() == 2000 && Site.KERN_OST.breite() == 2300 && Math.abs(Site.KERN_OST.hoehe() - 3500) <= 50,
                "Kern West 3 × 2 km quer, Kern Ost 2,3 × 3,5 km hochkant (F1 A; seit Phase 9 mit den westlichen Kehren)");
        check(Site.KERN_WEST.contains(Site.SCHWESTERN.x(), Site.SCHWESTERN.z()) && Site.KERN_WEST.contains(Site.FRIAREN.x(), Site.FRIAREN.z())
                && Site.KERN_WEST.contains(Site.SKAGEFLA.x(), Site.SKAGEFLA.z()), "Sieben Schwestern, Friaren und Skageflå liegen in Kern West");
        check(Site.SCHWESTERN.z() < Site.FRIAREN.z(), "Die Schwestern an der Nordwand, Friaren gegenüber im Süden");
        check(Site.KERN_OST.contains(Site.ANLEGER.x(), Site.ANLEGER.z()) && Site.KERN_OST.contains(Site.PLATTFORM.x(), Site.PLATTFORM.z())
                && Site.KERN_OST.contains(Site.STORFOSSEN.x(), Site.STORFOSSEN.z()), "Anleger, Plattform der Ørnesvingen und Storfossen liegen in Kern Ost");
        double d = Math.hypot(Site.SCHWESTERN.x(), Site.SCHWESTERN.z());
        check(d > 5000 && d < 7500, String.format(Locale.GERMAN, "Die Schwestern liegen %.1f km westlich des Anlegers (Werkbuch: rund 6,5 km vom Ort)", d / 1000));
        check(Site.PLATTFORM.z() < -1000 && Site.PLATTFORM.x() < 0, "Die Ørnesvingen liegen nördlich des Orts (Karte, F1)");
    }

    static void sonne() {
        System.out.println("Sonne (62,1° Nord)");
        SceneClock c = new SceneClock(LocalDate.of(2026, 6, 21), 0);
        double day = c.epochMillis();
        double up = hourOf(Sun.crossing(day, Site.LAT, Site.LON, true)), down = hourOf(Sun.crossing(day, Site.LAT, Site.LON, false));
        check(up > 3.0 && up < 4.0 && down > 23.2 && down < 24.0, "21.6.2026 Aufgang " + hm(up) + ", Untergang " + hm(down) + " MESZ");
        double best = -90, bestH = 0;
        for (double h = 11; h < 16; h += 1 / 60.0) {
            c.set(LocalDate.of(2026, 6, 21), h);
            double e = c.sun()[0];
            if (e > best) { best = e; bestH = h; }
        }
        c.set(LocalDate.of(2026, 6, 21), bestH);
        double az = c.sun()[1];
        check(Math.abs(az - 180) < 3 && bestH > 13.3 && bestH < 13.8 && best > 50.5 && best < 52,
                String.format(Locale.GERMAN, "Höchststand %s MESZ bei %.1f°, Azimut %03.0f° – die Sonne steht mittags im Süden", hm(bestH), best, az));
        SceneClock dez = new SceneClock(LocalDate.of(2026, 12, 21), 0);
        double du = hourOf(Sun.crossing(dez.epochMillis(), Site.LAT, Site.LON, true)), dd = hourOf(Sun.crossing(dez.epochMillis(), Site.LAT, Site.LON, false));
        check(dd - du > 4.5 && dd - du < 6.0, String.format(Locale.GERMAN, "21.12.2026 Tag %.1f h (%s bis %s MEZ)", dd - du, hm(du), hm(dd)));
        double[] s = Sun.direction(10, 180);
        check(s[2] > 0.9 && Math.abs(s[0]) < 1e-9, "Sonne im Süden zeigt nach +z (z ist Süd)");
    }

    static void uhr() {
        System.out.println("Uhr");
        SceneClock c = new SceneClock();
        check(c.label().equals("21.6.2026 11:00 MESZ"), "Start: " + c.label() + " (Ein Junitag)");
        check(c.local().getOffset().getTotalSeconds() == 2 * 3600 && SceneClock.ZONE.getId().equals("Europe/Oslo"), "Norwegische Sommerzeit UTC+2");
        c.lapse = 60;
        c.step(30);
        check(c.label().equals("21.6.2026 11:30 MESZ"), "Zeitraffer 1 min/s, 30 s → " + c.label());
        double e0 = c.epochMillis();
        c.lapse = 1e9;
        c.step(1);
        check(Math.abs(c.epochMillis() - e0 - SceneClock.MAX_LAPSE * 1000) < 1e-3, "Zeitraffer höchstens 3600-fach");
        c.set(LocalDate.of(2026, 12, 31), 23.5);
        c.lapse = 3600;
        c.step(1);
        check(c.dateLabel().equals("1.1.2027") && c.zoneLabel().equals("MEZ"), "Jahreswechsel in MEZ: " + c.label());
        SceneClock s = new SceneClock();
        s.lapse = 0;
        s.step(2.5);
        check(Math.abs(s.t() - 2.5) < 1e-9 && s.label().equals("21.6.2026 11:00 MESZ"), "Uhr steht, Laufzeit läuft weiter");
    }

    private static Terrain gelaende;

    static Terrain gelaendeOderNull() {
        if (gelaende == null) gelaende = Terrain.load(Terrain.defaultDir());
        return gelaende.measured() ? gelaende : null;
    }

    static void gelaende() {
        System.out.println("Gelände (HGT 1″, Phase 2)");
        Terrain t = gelaendeOderNull();
        check(t != null, "daten/gelaende gelesen: " + (t == null ? "fehlt (tools.HgtImport)" : t.describe()));
        if (t == null) return;
        java.util.Properties p = t.props;
        check("0".equals(p.getProperty("naht7")), "Naht bei 7° O: die Randspalten von N62E006 und N62E007 sind gleich (größter Unterschied " + p.getProperty("naht7") + " m)");
        double m = Double.parseDouble(p.getProperty("glo30.mittel", "99")), km = Double.parseDouble(p.getProperty("glo30.kern.mittel", "99"));
        check(Math.abs(m) < 2, String.format(Locale.GERMAN, "GLO-30 als Gegenprobe: im Mittel %+.2f m (Streuung %s m, %s Punkte) – unter 2 m", m,
                p.getProperty("glo30.streuung"), p.getProperty("glo30.punkte")));
        System.out.println(String.format(Locale.GERMAN, "        (in den Kernen %+.2f m, Streuung %s m: an den Wänden verschieben sich die Raster um Bruchteile einer Zelle)",
                km, p.getProperty("glo30.kern.streuung")));
        double f = t.fjordKm2();
        check(f > 25 && f < 70, String.format(Locale.GERMAN, "Fjord im Hauptraster %.1f km² (Geirangerfjord und Teile des Sunnylvsfjords)", f));
        double hp = t.height(Site.PLATTFORM.x(), Site.PLATTFORM.z()), ha = t.height(Site.ANLEGER.x(), Site.ANLEGER.z());
        check(Math.abs(hp - 530) < 40, String.format(Locale.GERMAN, "Plattform der Ørnesvingen auf %.0f m (elfte Kehre, rund 530 m; 620 m ist Korsmyra)", hp));
        check(ha >= 0 && ha < 6 && !t.fjord(Site.ANLEGER.x(), Site.ANLEGER.z()), String.format(Locale.GERMAN, "Anleger am Ufer auf NN %+.1f m", ha));
        double fuss = t.height(Site.SCHWESTERN.x(), Site.SCHWESTERN.z()), wand = t.height(Site.SCHWESTERN.x() - 400, Site.SCHWESTERN.z());
        check(fuss < 15 && wand - fuss > 300, String.format(Locale.GERMAN, "Sieben Schwestern: Fuß auf %.0f m, 400 m westlich %.0f m (die Wand)", fuss, wand));
        check(t.fjord(Site.VOM_WASSER[0], Site.VOM_WASSER[1]) && t.height(Site.VOM_WASSER[0], Site.VOM_WASSER[1]) < -50 && Terrain.decode(Terrain.encode(0)) == 0,
                String.format(Locale.GERMAN, "Zwischen Schwestern und Friaren Fjord, NN genau ein Wert, Grund dort %.0f m (Phase 3)", t.height(Site.VOM_WASSER[0], Site.VOM_WASSER[1])));
        double hs = t.height(Site.SKAGEFLA.x(), Site.SKAGEFLA.z()), hk = t.height(Site.KNIVSFLA.x(), Site.KNIVSFLA.z());
        check(Math.abs(hs - 250) < 40 && Math.abs(hk - 250) < 40, String.format(Locale.GERMAN, "Skageflå auf %.0f m, Knivsflå auf %.0f m (Höfe auf ihren Bändern um 250 m)", hs, hk));
        // Übergang ins Fernraster: stetig am Rand des Bands, gleich innen und außen
        double x = Terrain.X0 + Terrain.N * Terrain.CELL - 1, z = Terrain.MZ;
        double inner = t.height(x - Terrain.NAHT - 5, z), outer = t.height(x + 5, z);
        check(Math.abs(t.height(x + 5, z) - t.fernHeight(x + 5, z)) < 1e-9 && Math.abs(t.height(x - Terrain.NAHT - 5, z) - t.mainHeight(x - Terrain.NAHT - 5, z)) < 1e-9
                && Math.abs(t.height(x - 1, z) - t.height(x + 1, z)) < 5, String.format(Locale.GERMAN, "Fernring: außen allein, innen das Hauptraster, dazwischen weich (%.0f / %.0f m)", inner, outer));
        TerrainLod lod = new TerrainLod(t);
        lod.finest = 2;
        lod.finestKern = 0;
        double[] eye = {Site.PLATTFORM.x(), t.height(Site.PLATTFORM.x(), Site.PLATTFORM.z()) + 2, Site.PLATTFORM.z()};
        lod.collect(eye, null);
        float[] full = lod.selection(false);
        double klein = Double.MAX_VALUE;
        for (int i = 0; i < full.length; i += 4) klein = Math.min(klein, full[i + 2]);
        double[] eye2 = {Terrain.MX + 9000, 1400, Terrain.MZ + 9000};
        lod.collect(eye2, null);
        float[] f2 = lod.selection(false);
        double klein2 = Double.MAX_VALUE;
        for (int i = 0; i < f2.length; i += 4) klein2 = Math.min(klein2, f2[i + 2]);
        check(klein <= 128.5 && klein2 >= 511.5, String.format(Locale.GERMAN, "Detailstufen: an der Plattform (Kern Ost) Knoten bis %.0f m (2 m Punktabstand), außerhalb der Kerne nicht unter %.0f m (8 m)", klein, klein2));
    }

    static void fjordgrund() {
        System.out.println("Fjordgrund (F7, Phase 3)");
        check(Math.abs(Fjordgrund.tiefe(0, 500) - Fjordgrund.UFER) < 1e-9 && Math.abs(Fjordgrund.tiefe(0.55, 500) - Fjordgrund.MITTE) < 1e-9
                        && Math.abs(Fjordgrund.tiefe(1, 500) - Fjordgrund.RINNE) < 1e-9,
                "Profil: am Ufer 10 m, ab der halben Breite 150 m, in der Mitte eines breiten Fjords 250 m");
        check(Fjordgrund.tiefe(1, 100) < Fjordgrund.MITTE && Fjordgrund.tiefe(1, 200) < Fjordgrund.RINNE,
                String.format(Locale.GERMAN, "Schmale Arme flacher: 200 m breit %.0f m, 400 m breit %.0f m", Fjordgrund.tiefe(1, 100), Fjordgrund.tiefe(1, 200)));
        check(Fjordgrund.deltaGrenze(0) == Fjordgrund.DELTA_TIEFE && Math.abs(Fjordgrund.deltaGrenze(550) - 152) < 1e-9, "Delta: 2 m an der Mündung, 150 m erst rund 550 m davor");
        Terrain t = gelaendeOderNull();
        if (t == null) return;
        java.util.Properties p = t.props;
        double deepest = Double.parseDouble(p.getProperty("grund.tiefste", "0"));
        check(p.getProperty("grund", "").startsWith("gemalt") && deepest > 230 && deepest <= 251, "Grund gemalt, tiefste Stelle " + deepest + " m (Rinne 250 m)");
        StringBuilder sb = new StringBuilder();
        boolean all = true;
        for (Fjordprofil.Schnitt sn : Fjordprofil.SCHNITTE) {
            double[][] q = Fjordgrund.schnitt(t, sn.x0(), sn.z0(), sn.x1(), sn.z1(), 4);
            double tief = 0, ufer = Double.NaN;
            for (double[] v : q) {
                tief = Math.max(tief, -v[1]);
                if (Double.isNaN(ufer) && v[1] < -1) ufer = -v[1];
            }
            sb.append(String.format(Locale.GERMAN, "%s: Ufer %.0f m, tiefste %.0f m; ", sn.name().substring(0, 1), ufer, tief));
            all &= ufer < 25;
            if (sn.name().startsWith("A")) all &= tief > 140 && tief < 230;
            if (sn.name().startsWith("B")) all &= tief > 230;
        }
        check(all, "Schnitte: " + sb.substring(0, sb.length() - 2) + " (A quer vor den Schwestern Mitte, B im breiten Bogen mit Rinne)");
        double mx = Site.MUENDUNG[0], mz = Site.MUENDUNG[1];
        double dm = 999;
        for (double r = 0; r < 120; r += 12) for (double a = 0; a < 6.3; a += 0.4)
            if (t.fjord(mx + r * Math.cos(a), mz + r * Math.sin(a))) dm = Math.min(dm, -t.height(mx + r * Math.cos(a), mz + r * Math.sin(a)));
        check(dm < 12, String.format(Locale.GERMAN, "Vor der Mündung der Geirangelva das Delta: %.1f m tief", dm));
        double vorAnleger = -t.height(Site.ANLEGER.x() - 130, Site.ANLEGER.z() - 90);
        check(vorAnleger > 20 && vorAnleger < 150, String.format(Locale.GERMAN, "Vor dem Kai (Ansicht Anleger) %.0f m: am Hang des Deltas, nicht in der Mitte", vorAnleger));
        check(Integer.parseInt(p.getProperty("fern.unterNN", "0")) > 1000, "Fernring: " + p.getProperty("fern.unterNN") + " Zellen unter NN auf "
                + Math.round(Fjordgrund.FERN_TIEFE) + " m (der Sunnylvsfjord läuft unter ocean weiter)");
        boolean land = true;
        for (int k = 0; k < t.art.length; k += 97) if (t.art[k] == Terrain.LAND) land &= Terrain.decode(t.h[k]) > 0.5;
        check(land, "Land liegt überall über NN (Senken ohne Fjord gehoben: " + p.getProperty("land.angehoben") + " Zellen), damit ocean dort nicht steht");
    }

    static void wasser() {
        System.out.println("Wasser (ocean, Preset Fjord, Phase 3)");
        com.dan.ocean.Ocean o = Wasser.fjord(0);
        check(o.fetch == Wasser.FETCH && o.swellHeight == 0 && o.depth == Fjordgrund.MITTE && o.fetchLimited, "Preset Fjord: Streichlänge 3 km, keine Dünung, 150 m tief");
        double h0 = o.state().windSeaHs;
        com.dan.ocean.Ocean o4 = Wasser.fjord(4);
        double h4 = o4.state().windSeaHs;
        check(h0 < 0.05 && h4 > h0 && h4 < 0.6, String.format(Locale.GERMAN, "Stiller Morgen Bft 0: H_s %.3f m (Spiegel); Bft 4 nur %.2f m (Wände nehmen den Wind)", h0, h4));
        boolean q = true;
        Quality[] qs = Quality.values();
        for (int i = 0; i < qs.length; i++) {
            q &= qs[i].ocean.ordinal() == i && qs[i].mirrorScale > 0 && qs[i].panoWidth >= 1024;
            if (i > 0) q &= qs[i].mirrorScale >= qs[i - 1].mirrorScale && qs[i].panoWidth >= qs[i - 1].panoWidth;
        }
        check(q, "Stufen: ocean folgt eins zu eins, Spiegelbild und Rundumbild steigen an (" + Quality.MITTEL.summary() + ")");
        // gespiegeltes Auge: der Strahl in Richtung des gespiegelten Blicks läuft durch den Wasserpunkt
        double[] e = {120, 37, -55}, pw = {800, 0, -1400};
        double[] v = {pw[0] - e[0], pw[1] - e[1], pw[2] - e[2]};
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        double[] r = {v[0] / len, -v[1] / len, v[2] / len}, em = {e[0], -e[1], e[2]};
        double[] hit = {em[0] + r[0] * len, em[1] + r[1] * len, em[2] + r[2] * len};
        check(Math.abs(hit[0] - pw[0]) + Math.abs(hit[1] - pw[1]) + Math.abs(hit[2] - pw[2]) < 1e-9, "Spiegelbild: vom Auge unter dem Spiegel trifft der gespiegelte Strahl genau den Wasserpunkt");
        Terrain t = gelaendeOderNull();
        if (t == null) return;
        Renderer rr = new Renderer(new SceneClock(), new Wiring(new SceneClock()), t);
        double x = Site.VOM_WASSER[0], z = Site.VOM_WASSER[1];
        double a = rr.standHeight(x, z);
        rr.showBed = true;
        double b = rr.standHeight(x, z);
        check(a == 0 && b < -50 && rr.waterMode() == 2, String.format(Locale.GERMAN, "Vom Wasser steht man auf NN; „Grund zeigen“ lässt bis auf den Grund (%.0f m)", b));
        rr.placeView(Ansichten.byName("Fjordgrund"));
        check(rr.showBed && rr.camera.eye[1] > 50, "Ansicht „Fjordgrund“ schaltet „Grund zeigen“ ein, Auge über dem Spiegel");
    }

    static void wetter() {
        System.out.println("Wetter (Klima Geiranger, Phase 4)");
        com.dan.weather.Weather w = new com.dan.weather.Weather();
        com.dan.geiranger.wiring.KlimaGeiranger.setzen(w);
        double t0 = java.time.LocalDate.of(2026, 6, 1).atStartOfDay(java.time.ZoneId.of("Europe/Oslo")).toEpochSecond();
        w.startFree(t0, com.dan.weather.Weather.Phase.KLAR);
        int n = 0, rain = 0, fog = 0;
        double wind = 0;
        java.util.EnumSet<com.dan.weather.Weather.Phase> seen = java.util.EnumSet.noneOf(com.dan.weather.Weather.Phase.class);
        for (double t = t0; t < t0 + 120 * 86400; t += 900) {
            com.dan.weather.Weather.Snapshot sn = w.update(t, 0);
            n++;
            if (sn.rain() > 0.3) rain++;
            if (sn.fog() > 0.3) fog++;
            wind += sn.wind();
            seen.add(sn.phase());
        }
        double mw = wind / n;
        check(mw < 6 && rain > n * 0.12 && fog > n * 0.02 && !seen.contains(com.dan.weather.Weather.Phase.ORKAN),
                String.format(Locale.GERMAN, "Freie Regie 120 Tage ab Juni: Wind im Mittel %.1f m/s (im Windschatten der Wände), Regen %.0f %%, Fjordnebel %.0f %% der Zeit, %d Lagen",
                        mw, 100.0 * rain / n, 100.0 * fog / n, seen.size()));
        SceneClock c = new SceneClock();
        Wiring wi = new Wiring(c);
        com.dan.weather.Scenario nb = com.dan.geiranger.wiring.KlimaGeiranger.nebelmorgen();
        c.setHour(5.5);
        wi.start(nb);
        wi.feedWeather(0);
        double f1 = wi.fogAmount;
        com.dan.fog.Fog fg = new com.dan.fog.Fog();
        wi.feedNebel(fg, 2);
        double top = wi.fogTop, am = fg.amount;
        c.setHour(13);
        wi.feedWeather(0);
        check(f1 > 0.7 && am > 0.8 && top < 300 && wi.fogAmount < 0.1, String.format(Locale.GERMAN, "Nebelmorgen: um 5:30 Nebel %.0f %% bis %.0f m über dem Spiegel (die Wände ragen heraus), um 13 Uhr %.0f %%",
                f1 * 100, top, wi.fogAmount * 100));
        c.setHour(10);
        wi.start(com.dan.geiranger.wiring.KlimaGeiranger.regentag());
        wi.feedWeather(0);
        com.dan.fog.Fog band = new com.dan.fog.Fog();
        wi.feedBand(band);
        check(wi.rainRate > 2 && wi.oktas >= 7 && band.amount > 0.5 && wi.bandBase >= 250 && wi.bandTop < 1500 && wi.bandTop > wi.bandBase + 250,
                String.format(Locale.GERMAN, "Regentag um 10 Uhr: %.1f mm/h, %.1f/8 %s, Wolkenband %.0f %% von %.0f bis %.0f m an den Wänden", wi.rainRate, wi.oktas,
                        wi.cloudType.label, band.amount * 100, wi.bandBase, wi.bandTop));
        c.set(LocalDate.of(2026, 6, 21), 11);
        Wiring wj = new Wiring(c);
        com.dan.weather.Scenario jt = com.dan.geiranger.wiring.KlimaGeiranger.junitag();
        wj.start(jt);
        check(Math.abs(jt.hours() - 23.5) < 1e-9 && jt.startHour() == 3.5 && wj.regie && wj.oktas < 5 && wj.rainRate == 0,
                String.format(Locale.GERMAN, "Ein Junitag: %.1f h ab 3:30, Zeitraffer %.0f, um 11 Uhr %.1f/8 ohne Regen", jt.hours(), jt.lapse(), wj.oktas));
        com.dan.sky.Sky sky = new com.dan.sky.Sky();
        wj.feed(sky, 620, Site.LAT, Site.LON);
        check(sky.latDeg == Site.LAT && sky.eyeHeight == 620 && sky.visibilityKm == wj.visibilityKm, "Himmel bekommt Ort (62,1° N), Augenhöhe und Sicht aus der Regie");
        boolean q = true;
        Quality[] qs = Quality.values();
        for (int i = 1; i < qs.length; i++) q &= qs[i].shadowN >= qs[i - 1].shadowN && qs[i].clouds.ordinal() >= qs[i - 1].clouds.ordinal();
        check(q && Quality.MITTEL.shadowN == 1024, "Stufen: Wolken und Karte der Wandschatten steigen an (Mittel 1024², 24 m)");
    }

    static void wasserfall() {
        System.out.println("Wasserfall (com.dan.fall, Phase 6, Lauf über Fels seit Phase 7)");
        com.dan.fall.Fall f = com.dan.fall.demo.DemoCliff.fall();
        check(com.dan.fall.Fall.STAND.contains("Phase 7") && f.strands.size() == 7 && f.bundles.size() == 4000,
                "Engine: 7 Strähnen und 4000 Bündel auf „Mittel“ (Vorführung, Kante 40 m)");
        check(Math.abs(f.height - 250) < 2 && f.fallTime > f.freeFallSeconds(250) && f.veilWidth(249) > f.veilWidth(1),
                String.format(Locale.GERMAN, "250 m in %.1f s (frei %.1f s), Schleier unten %.0f m statt %.0f m", f.fallTime, f.freeFallSeconds(250), f.veilWidth(249), f.veilWidth(1)));
        String v = com.dan.fall.gl.FallGl.source("veil.vert");
        check(v.contains("vec4 fallParcel(") && v.startsWith("#version 430"), "Shader von fall mit Baustein fall.glsl");
    }

    static void fels() {
        System.out.println("Fels (com.dan.cliff, Phase 5)");
        boolean q = true;
        Quality[] qs = Quality.values();
        for (int i = 1; i < qs.length; i++) q &= qs[i].cliff.ordinal() >= qs[i - 1].cliff.ordinal();
        check(q && Quality.MITTEL.cliff == com.dan.cliff.Cliff.Quality.MEDIUM && !Quality.NIEDRIG.cliff.overhangs,
                "Stufen: Fels steigt an (Mittel = MEDIUM, 3 Oktaven bis 1,5 km; Niedrig ohne Überhänge)");
        String tv = Shader.source("terrain.vert", null).text, tf = Shader.source("terrain.frag", null).text;
        check(tv.contains("cliffDisplace(world, vNormal") && tf.contains("cliffMaterial(vWorld, n, uRainWet") && tf.contains("float cliffRelief("),
                "Gelände bindet cliff.glsl: Feinform im Scheitelshader, Material im Bildpunkt");
        Terrain t = gelaendeOderNull();
        if (t == null) return;
        com.dan.geiranger.gl.Fels f = new com.dan.geiranger.gl.Fels(t);
        f.build();
        com.dan.cliff.CliffMap m = f.map();
        System.out.println("  " + m.describe());
        check(m.wallShare > 0.01 && m.wallShare < 0.25 && m.talusShare > 0.002 && f.faelle() != null && m.falls == f.faelle().size() && m.falls >= 6,
                String.format(Locale.GERMAN, "Wandkarte über dem Hauptraster: Wand %.1f %%, Geröll %.1f %%, %d Fälle mit nassen Linien (seit Phase 7 aus ihrem Weg)", m.wallShare * 100, m.talusShare * 100, m.falls));
        // nasser Streifen in der Wand der Schwestern: zwischen Fuß und Kante irgendwo Nässe > 0,5 an einer Wandzelle
        double best = 0;
        for (int dz = -400; dz <= 400; dz += 6)
            for (int dx = -400; dx <= 400; dx += 6) {
                double[] v = m.valuesAt(Site.SCHWESTERN.x() + dx, Site.SCHWESTERN.z() + dz);
                if (v[0] > 0.5) best = Math.max(best, v[2]);
            }
        check(best > 0.5, String.format(Locale.GERMAN, "Nasser Streifen in der Wand der Sieben Schwestern (Nässe %.2f an der Wand)", best));
        java.util.List<com.dan.cliff.Overhang> o = f.overhangs();
        boolean hoch = true;
        for (com.dan.cliff.Overhang h : o) hoch &= h.y > 150;
        check(o.size() == 3 && hoch, String.format(Locale.GERMAN, "Drei Überhänge an den Abrisskanten (Schwestern, Friaren, seit Phase 8 Storsæterfossen), %s",
                o.stream().map(h -> String.format(Locale.GERMAN, "%.0f m ü. NN", h.y)).collect(java.util.stream.Collectors.joining(", "))));
    }

    /** Phase 7: die Wasserfälle an ihren Kanten, die Abflussregie, die Ansichten auf die Fälle, der Fehler im Fels. */
    static void faelle() {
        System.out.println("Wasserfälle (Phase 7)");
        // der Fehler aus der Nacharbeit zu Phase 5: das Hochladen der Wandkarte löschte die Rauschtextur (seit Phase 18 liegt cliff in Renderer.jar;
        // die Quelle steht im Nachbarprojekt Renderer, sonst prüft die Prüfung die Klasse)
        File cq = new File("../Renderer/src/com/dan/cliff/gl/CliffGl.java");
        if (cq.isFile()) {
            String src = readOr(cq.getPath());
            int a = src.indexOf("public void upload("), b = src.indexOf("\n    }\n", a);
            String up = a < 0 || b < 0 ? "" : src.substring(a, b);
            check(!up.isEmpty() && !up.contains("noiseTex"), "cliff: das Hochladen der Wandkarte lässt die Rauschtextur stehen (Selbsttest der Phase 5 meldete ± 96 %)");
        } else {
            boolean m = false;
            try { m = com.dan.cliff.gl.CliffGl.class.getMethod("upload", com.jogamp.opengl.GL4.class, com.dan.cliff.CliffMap.class) != null; } catch (ReflectiveOperationException e) { m = false; }
            check(m, "cliff: CliffGl.upload aus Renderer.jar (Quelle nicht im Projekt)");
        }
        // Abflussregie (F13 A)
        com.dan.geiranger.wiring.Abfluss a = new com.dan.geiranger.wiring.Abfluss();
        double jun = com.dan.geiranger.wiring.Abfluss.season(LocalDate.of(2026, 6, 15)), feb = com.dan.geiranger.wiring.Abfluss.season(LocalDate.of(2026, 2, 15)),
                mai = com.dan.geiranger.wiring.Abfluss.season(LocalDate.of(2026, 5, 20)), okt = com.dan.geiranger.wiring.Abfluss.season(LocalDate.of(2026, 10, 15));
        check(jun > 0.97 && feb < 0.1 && mai > 0.6 && okt < 0.4, String.format(Locale.GERMAN, "Jahreszeit: Juni %.0f %%, 20. Mai %.0f %%, Oktober %.0f %%, Februar %.0f %% vom Juni", jun * 100, mai * 100, okt * 100, feb * 100));
        a.register("Sieben Schwestern", 2.5, false);
        a.register("Regenfall 1", 0.15, true);
        a.step(60, 0, 12, LocalDate.of(2026, 6, 15));
        double dry = a.cubicMetres("Sieben Schwestern"), dryR = a.cubicMetres("Regenfall 1");
        for (int i = 0; i < 10; i++) a.step(3600, 1.0, 12, LocalDate.of(2026, 6, 15));   // 10 h Landregen mit 1 mm/h
        double wet = a.cubicMetres("Sieben Schwestern"), wetR = a.cubicMetres("Regenfall 1"), mem = a.regen;
        for (int i = 0; i < 12; i++) a.step(3600, 0, 12, LocalDate.of(2026, 6, 15));
        check(Math.abs(dry - 2.5 * jun) < 1e-6 && dryR == 0 && wet > dry * 1.4 && wetR > 0.05 && a.regen < mem / 3,
                String.format(Locale.GERMAN, "Regen: nach 10 mm in 10 h %.1f mm im Speicher, Schwestern %.2f statt %.2f m³/s, Regenfall %.2f statt 0; nach 12 h trocken %.1f mm",
                        mem, wet, dry, wetR, a.regen));
        a.hand = 0.5;
        check(Math.abs(a.cubicMetres("Sieben Schwestern") - 1.25) < 1e-9 && a.cubicMetres("Regenfall 1") > 0, "Von Hand: 50 % vom Juni gilt für alle Fälle");
        a.hand = -1;
        for (int i = 0; i < 96; i++) a.step(3600, 0, -20, LocalDate.of(2026, 2, 15));
        check(a.temp < -18 && a.frozen("Sieben Schwestern"), String.format(Locale.GERMAN, "Frost: nach vier Tagen bei −20 °C hat der Fall %.0f °C und steht", a.temp));
        check(Quality.MITTEL.fall == com.dan.fall.Fall.Quality.MEDIUM && Quality.NIEDRIG.fall == com.dan.fall.Fall.Quality.LOW, "Stufen: die App gibt fall ihre Stufe (Mittel = MEDIUM)");
        // Ansichten 8 und 9
        Ansichten.Ansicht s8 = Ansichten.byName("SiebenSchwestern"), s9 = Ansichten.byName("Friaren");
        check(s8 != null && s9 != null && s8.key() == 8 && s9.key() == 9 && Ansichten.ready(s8, Renderer.PHASE), "Ansichten 8 Sieben Schwestern und 9 Friaren");
        Terrain t = gelaendeOderNull();
        if (t == null) return;
        com.dan.geiranger.gl.Fels fe = new com.dan.geiranger.gl.Fels(t);
        fe.build();
        java.util.List<com.dan.geiranger.scene.Faelle.Eintrag> fs = fe.faelle();
        for (com.dan.geiranger.scene.Faelle.Eintrag e : fs) System.out.println("        " + com.dan.geiranger.scene.Faelle.describe(e));
        com.dan.geiranger.scene.Faelle.Eintrag sw = null, fr = null, br = null;
        int regen = 0;
        for (com.dan.geiranger.scene.Faelle.Eintrag e : fs) {
            if (e.def.name().equals("Sieben Schwestern")) sw = e;
            if (e.def.name().equals("Friaren")) fr = e;
            if (e.def.name().equals("Brudesløret")) br = e;
            if (e.def.art() == com.dan.geiranger.scene.Faelle.Art.REGEN) regen++;
        }
        check(sw != null && fr != null && br != null && regen >= 4, String.format(Locale.GERMAN, "Drei benannte Fälle und %d Regenfälle an ihren Kanten", regen));
        if (sw == null || fr == null) return;
        int water = 0;
        for (com.dan.fall.Fall.Strand s : sw.fall.strands) if (s.onWater) water++;
        check(sw.fall.strands.size() == 7 && water >= 5 && Math.abs(sw.kante.y() - 410) < 30 && sw.fall.overRock,
                String.format(Locale.GERMAN, "Sieben Schwestern: sieben Strähnen im Lauf über Fels, Kante auf %.0f m (410 m), %d von 7 kommen im Fjord an", sw.kante.y(), water));
        double[] u = Site.toUtm(sw.kante.x(), sw.kante.z());
        double dk = Math.hypot(u[0] - Site.KNIVSFLA.e(), u[1] - Site.KNIVSFLA.n());
        check(Site.KERN_WEST.contains(sw.kante.x(), sw.kante.z()) && dk < 500, String.format(Locale.GERMAN, "Die Kante der Schwestern liegt in Kern West, %.0f m von Knivsflå", dk));
        int fw = 0;
        for (com.dan.fall.Fall.Strand s : fr.fall.strands) if (s.onWater) fw++;
        check(fw >= 2 && fr.fall.courseLine().contains("über Fels") && Math.abs(fr.kante.y() - 300) < 30,
                String.format(Locale.GERMAN, "Friaren gegenüber: Kante auf %.0f m, %d von %d Strähnen im Fjord · %s", fr.kante.y(), fw, fr.fall.strands.size(), fr.fall.courseLine()));
        // nie im Fels: jede Probe über der Wand, wie sie die Fälle sehen (mit Feinform)
        double worst = 1e9;
        for (com.dan.geiranger.scene.Faelle.Eintrag e : fs)
            for (com.dan.fall.Fall.Strand s : e.fall.strands)
                for (int k = 1; k < com.dan.fall.Fall.PATH; k++) worst = Math.min(worst, s.path[k * 4 + 1] - fe.wand(s.path[k * 4], s.path[k * 4 + 2]));
        check(worst > 0.5, String.format(Locale.GERMAN, "Kein Fall steckt im Fels: das Wasser bleibt mindestens %.2f m über der Wand mit Feinform", worst));
        // Ansichten blicken auf die Fälle und stehen auf dem Fjord
        double[] m8 = sw.mitte(), m9 = fr.mitte();
        double b8 = Math.toDegrees(Math.atan2(m8[0] - s8.x(), -(m8[2] - s8.z()))), b9 = Math.toDegrees(Math.atan2(m9[0] - s9.x(), -(m9[2] - s9.z())));
        double d8 = Math.abs(((b8 - s8.heading()) % 360 + 540) % 360 - 180), d9 = Math.abs(((b9 - s9.heading()) % 360 + 540) % 360 - 180);
        check(t.fjord(s8.x(), s8.z()) && t.fjord(s9.x(), s9.z()) && d8 < 15 && d9 < 15,
                String.format(Locale.GERMAN, "Ansichten 8 und 9 stehen auf dem Fjord und blicken auf ihren Fall (± %.0f° und ± %.0f°)", d8, d9));
        Ansichten.Ansicht vw = Ansichten.byName("VomWasser");
        double bw = Math.toDegrees(Math.atan2(m8[0] - vw.x(), -(m8[2] - vw.z())));
        check(Math.abs(((bw - vw.heading()) % 360 + 540) % 360 - 180) < 15, "„Vom Wasser“ blickt auf die Schwestern an ihrer neuen Lage");
        // Rechnung im Hintergrund: nach mehr Abfluss ein neuer Fall
        com.dan.geiranger.gl.Wasserfaelle wf = new com.dan.geiranger.gl.Wasserfaelle();
        wf.set(fs);
        com.dan.geiranger.wiring.Abfluss ab = new com.dan.geiranger.wiring.Abfluss();
        for (com.dan.geiranger.scene.Faelle.Eintrag e : fs) ab.register(e.def.name(), e.def.juni(), e.def.art() == com.dan.geiranger.scene.Faelle.Art.REGEN);
        ab.step(1, 0, 10, LocalDate.of(2026, 6, 15));
        ab.hand = 2.0;
        com.dan.fall.Fall before = sw.fall;
        for (int i = 0; i < 400 && !(sw.fall != before && wf.idle()); i++) {
            wf.step(ab, 0, 270, com.dan.fall.Fall.Quality.MEDIUM);
            try { Thread.sleep(25); } catch (InterruptedException e) { break; }
        }
        check(sw.fall != before && Math.abs(sw.fall.discharge - 5.0) < 1e-9 && wf.rebuilds >= 1,
                String.format(Locale.GERMAN, "Doppelter Abfluss: der Hintergrund rechnet die Fälle neu (%d Rechnungen), die Schwestern führen %.1f m³/s", wf.rebuilds, sw.fall.discharge));
        check(com.dan.geiranger.gl.Wasserfaelle.wet(ab) == 1 && java.util.List.of(Renderer.PASSES).indexOf("Fälle") == java.util.List.of(Renderer.PASSES).indexOf("Wasser") + 1, "Nässe der Wand folgt dem Abfluss; eigener Durchgang „Fälle“ nach dem Wasser");
    }

    static void wandschatten() {
        System.out.println("Schatten der Wände (F19, Phase 4)");
        Terrain t = gelaendeOderNull();
        if (t == null) return;
        SceneClock c = new SceneClock(LocalDate.of(2026, 6, 21), 6);
        double[] lit = new double[3];
        double[] hrs = {6, 12, 20};
        for (int k = 0; k < 3; k++) {
            c.setHour(hrs[k]);
            double[] sp = c.sun(), d = Sun.direction(sp[0], sp[1]);
            int l = 0, n = 0;
            for (int j = 0; j < Terrain.N; j += 24)
                for (int i = 0; i < Terrain.N; i += 24) {
                    if (t.art[j * Terrain.N + i] != Terrain.FJORD) continue;
                    double x = Terrain.cx(i), z = Terrain.cz(j);
                    if (!Site.UMGEBUNG.contains(x, z)) continue;
                    n++;
                    if (Wandschatten.licht(t, x, 0.5, z, d) > 0.5) l++;
                }
            lit[k] = l / (double) Math.max(1, n);
        }
        check(lit[0] < 0.25 && lit[1] > 0.9 && lit[2] < lit[1], String.format(Locale.GERMAN,
                "21.6.: Wasser in der Sonne um 6 Uhr %.0f %%, mittags %.0f %%, um 20 Uhr %.0f %% (das Licht erreicht den Spiegel spät)", lit[0] * 100, lit[1] * 100, lit[2] * 100));
        double an = Wandschatten.sonneAb(t, c, Site.ANLEGER.x() - 130, Site.ANLEGER.z() - 90, 0);
        double vw = Wandschatten.sonneAb(t, c, Site.VOM_WASSER[0], Site.VOM_WASSER[1], 0);
        check(an > 7 && an < 11 && vw > 4 && vw < 9, String.format(Locale.GERMAN, "Sonne am Wasser: vor dem Anleger ab %s, vor den Schwestern ab %s Uhr (Aufgang 3:39)", hm(an), hm(vw)));
        SceneClock dz = new SceneClock(LocalDate.of(2026, 12, 21), 12);
        check(Double.isNaN(Wandschatten.sonneAb(t, dz, Site.VOM_WASSER[0], Site.VOM_WASSER[1], 0)), "21.12.: die Sonne erreicht das Wasser vor den Schwestern nie");
        double[] up = {0, 1, 0};
        check(Wandschatten.licht(t, Site.PLATTFORM.x(), Site.PLATTFORM.grund() + 2, Site.PLATTFORM.z(), up) == 1 && Wandschatten.licht(t, 0, 0.5, 0, new double[]{0, -1, 0}) == 0,
                "Sonne im Zenit: alles hell; unter der Kimm: alles dunkel");
    }

    static String jarText(String res) {
        try (java.io.InputStream in = com.dan.ocean.Ocean.class.getResourceAsStream(res)) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return "";
        }
    }

    /** Phase 8: Wandschnitt (F12b A), die Geirangelva mit Storfossen, Treppe, Geländer, Steinen; Belichtung im Bild. */
    static void elva() {
        System.out.println("Geirangelva und Storfossen (Phase 8)");
        String lum = Shader.source("lum.frag", null).text, tf = Shader.source("terrain.frag", null).text;
        check(lum.contains("log(max(1e-7, dot(c, vec3(0.2126, 0.7152, 0.0722))))") && Renderer.BILD_GAIN > 1 && Renderer.BILD_GAIN < 2.5,
                "Belichtung im Bild: Logarithmus der Leuchtdichte, Mip-Mittel, Ausgleich zum arithmetischen Mittel des Himmels " + Renderer.BILD_GAIN);
        check(!tf.contains("sin(dot(p, vec2(127.1") && tf.contains("uCliffOn == 0 || y <= uLevel - 3.0") && tf.contains("uWaterMode == 1 ? artShare"),
                "Gelände billiger: Hash ohne Sinus, Platzhalter-Gneis nur ohne cliff, Fjordanteil nur für den Platzhalter-Spiegel");
        Ansichten.Ansicht a3 = Ansichten.byName("AmStorfossen");
        check(a3 != null && a3.key() == 3 && Ansichten.ready(a3, Renderer.PHASE), "Ansicht 3 „Am Storfossen“");
        Terrain t = gelaendeOderNull();
        if (t == null) return;
        String fo = com.dan.geiranger.scene.Formen.describe(t);
        System.out.println("  " + fo);
        double[] ks = com.dan.geiranger.scene.Formen.kante(t, "Sieben Schwestern"), kf = com.dan.geiranger.scene.Formen.kante(t, "Friaren");
        check(ks != null && kf != null && fo.contains("Bett der Geirangelva"), "Formen: Wandschnitt unter Schwestern und Friaren, Bett der Elva");
        if (ks != null && kf != null) {
            double[] dropS = drop(t, ks), dropF = drop(t, kf);
            check(dropS[0] > 0.6 * 250 && dropF[0] > 0.6 * 120, String.format(Locale.GERMAN,
                    "Geschärfte Wände (F12b A): unter der Kante der Schwestern %.0f m auf 30 m (Stufe 250 m), unter Friaren %.0f m (Stufe 120 m)", dropS[0], dropF[0]));
        }
        com.dan.geiranger.scene.Elva e = t.elva;
        check(e != null, "Geirangelva: " + (e == null ? "fehlt" : e.describe()));
        if (e == null) return;
        boolean mono = true;
        for (int i = 1; i < e.n; i++) mono &= e.level[i] >= e.level[i - 1] - 1e-9;
        com.dan.geiranger.scene.Elva.Stufe sf = e.stufen.isEmpty() ? null : e.stufen.get(0);
        check(mono && e.l[e.n - 1] > 1200 && sf != null && sf.name().equals("Storfossen") && e.level[sf.i0()] < 25 && e.level[sf.i1()] > 50 && e.strecken.size() >= 2,
                String.format(Locale.GERMAN, "Spiegel steigt vom Delta (%.1f m) bergauf nie ab; Storfossen als Stufe; %d Strecken als Fluss", e.level[0], e.strecken.size()));
        int unter = 0, alle = 0;
        for (com.dan.geiranger.scene.Elva.Strecke s : e.strecken)
            for (int i = s.i0(); i <= s.i1(); i++) { alle++; if (t.height(e.x[i], e.z[i]) < e.level[i] - 0.4) unter++; }
        check(unter > 0.9 * alle, String.format(Locale.GERMAN, "Das Bett liegt unter dem Spiegel: %d von %d Proben der Strecken mindestens 0,4 m tief", unter, alle));
        com.dan.geiranger.gl.Fluss fl = new com.dan.geiranger.gl.Fluss(t);
        Object[] m = fl.mesh(com.dan.geiranger.scene.Elva.JUNI);
        float[] v = (float[]) m[0];
        double vmax = 0;
        for (int i = 0; i < v.length; i += 8) vmax = Math.max(vmax, Math.hypot(v[i + 3], v[i + 4]));
        check(fl.present() && fl.wetShare > 0.5 && vmax > 1 && vmax < 12 && ((int[]) m[1]).length > 1000,
                String.format(Locale.GERMAN, "Strömung (com.dan.river) im Juni: %d Ecken, %.0f %% nass, höchstens %.1f m/s", v.length / 8, fl.wetShare * 100, vmax));
        com.dan.geiranger.scene.Bauten b = fl.bauten;
        System.out.println("  " + b.describe());
        boolean steigt = true, trocken = true, nah = true;
        for (int i = 1; i < b.treppe.size(); i++) steigt &= b.treppe.get(i)[1] >= b.treppe.get(i - 1)[1] - 1e-6;
        for (double[] p : b.treppe) {
            double[] nb = e.nearest(p[0], p[2]);
            int k = (int) nb[0];
            trocken &= p[1] > e.level[k] + 0.5;
            nah &= nb[1] < e.half[k] + 15;
        }
        check(b.stufen > 200 && b.pfosten > 40 && steigt && trocken && nah && b.steine.size() >= 10,
                String.format(Locale.GERMAN, "Treppe am Storfossen: %d Stufen, steigt, über dem Wasser und am Ufer; %d Pfosten; %d Steine im Fluss", b.stufen, b.pfosten, b.steine.size()));
        com.dan.geiranger.gl.Fels fe = new com.dan.geiranger.gl.Fels(t);
        fe.build();
        com.dan.geiranger.scene.Faelle.Eintrag st = null, ss = null;
        for (com.dan.geiranger.scene.Faelle.Eintrag f : fe.faelle()) {
            if (f.def.name().equals("Storfossen")) st = f;
            if (f.def.name().equals("Storsæterfossen")) ss = f;
        }
        int sw = 0;
        if (st != null) for (com.dan.fall.Fall.Strand s : st.fall.strands) if (s.onWater) sw++;
        check(st != null && st.def.art() == com.dan.geiranger.scene.Faelle.Art.FLUSS && st.fall.waterLevel > 5 && sw >= 3 && ss != null,
                String.format(Locale.GERMAN, "Storfossen als Fall im Lauf über Fels, %d Strähnen erreichen die Elva (Spiegel %.0f m); Storsæterfossen dabei",
                        sw, st == null ? 0 : st.fall.waterLevel));
        // die Ansicht steht auf der Treppe (nicht im Fluss) und blickt auf den Storfossen
        if (st != null) {
            double[] mm = st.mitte();
            double br = Math.toDegrees(Math.atan2(mm[0] - a3.x(), -(mm[2] - a3.z())));
            double d3 = Math.abs(((br - a3.heading()) % 360 + 540) % 360 - 180);
            double[] nb = e.nearest(a3.x(), a3.z());
            check(d3 < 30 && nb[1] > e.half[(int) nb[0]], String.format(Locale.GERMAN, "„Am Storfossen“ steht am Ufer (%.0f m von der Mitte) und blickt auf den Fall (± %.0f°)", nb[1], d3));
        }
    }

    /** Fall unter einer Kante {x, y, z, Richtung}: Höhenunterschied 6 m hinter bis 30 m vor der Kante. */
    static double[] drop(Terrain t, double[] k) {
        double h = Math.toRadians(k[3]), dx = Math.sin(h), dz = -Math.cos(h);
        return new double[]{t.height(k[0] - dx * 6, k[2] - dz * 6) - t.height(k[0] + dx * 30, k[2] + dz * 30)};
    }

    /** Phase 9: der Ørnevegen (F14 A), das Feinraster, die Plattform, die Begegnungsregel; Nacharbeit 8a. */
    static void strasse() {
        System.out.println("Ørnevegen und Plattform (Phase 9)");
        String vv = jarText("/com/dan/fall/gl/veil.vert"), mv = jarText("/com/dan/fall/gl/mist.vert");
        check(vv.contains("uNear") && mv.contains("uNear") && new com.dan.fall.gl.FallGl().nearCap > 0,
                "Nacharbeit 8: Schleier und Nebel der Fälle nah am Auge begrenzt (der Storfossen von der Treppe kostete 18,9 ms)");
        check(Renderer.BILD_FOLGT >= 0.6 && Renderer.BILD_FOLGT <= 0.9, "Nacharbeit 8: die Belichtung folgt dem Bild zu " + Renderer.BILD_FOLGT);
        String th = Shader.source("terrain_height.glsl", null).text;
        check(th.contains("float feinAt(vec2 w)") && th.contains("+ feinAt(w)"), "Gelände auf der Grafikkarte mit Feinraster wie Terrain.height");
        Terrain t = gelaendeOderNull();
        if (t == null) return;
        // Nacharbeit 8a: die Treppe folgt dem Hang, die Ansicht steht auf ihr und schaut hinauf
        com.dan.geiranger.gl.Fluss fl = new com.dan.geiranger.gl.Fluss(t);
        double schwebt = 0;
        for (double[] p : fl.bauten.treppe) schwebt = Math.max(schwebt, p[1] - t.height(p[0], p[2]));
        double[] bl = fl.bauten.blick;
        check(schwebt < 4 && bl != null && bl[4] < 0 && bl[1] - t.height(bl[0], bl[2]) > 1.5,
                String.format(Locale.GERMAN, "Treppe am Storfossen höchstens %.1f m über dem Hang; „Am Storfossen“ auf der Treppe, Blick %.0f° hinauf", schwebt, -(bl == null ? 0 : bl[4])));
        com.dan.geiranger.scene.Ornevegen o = t.ornevegen;
        check(o != null, "Fv63: " + (o == null ? "fehlt" : o.describe()));
        if (o == null) return;
        com.dan.road.Way w = o.way;
        double maxG = 0;
        for (int i = 1; i < w.n; i++) maxG = Math.max(maxG, Math.abs(w.y[i] - w.y[i - 1]) / w.step());
        boolean steigt = true;
        for (int k = 1; k < o.kehren.size(); k++) steigt &= o.kehren.get(k).y() > o.kehren.get(k - 1).y() + 10;
        check(o.kehren.size() == 11 && steigt && maxG < 0.112 && w.length > 7000 && w.length < 10000,
                String.format(Locale.GERMAN, "Elf Kehren, jede höher als die vorige; Steigung bis %.1f %%, %.1f km", maxG * 100, w.length / 1000));
        // die Achse liegt auf dem Gelände mit Feinraster (außer auf Brücken): Fahrbahn = Gelände + Dicke + Absenkung
        int on = 0, all = 0;
        for (int i = 0; i < w.n; i++) {
            if (w.bridge[i]) continue;
            all++;
            if (Math.abs(t.height(w.x[i], w.z[i]) - (w.y[i] - w.type.thickness - 0.08)) < 0.6) on++;
        }
        check(on > 0.95 * all, String.format(Locale.GERMAN, "Feinraster: %d von %d Proben der Achse liegen auf dem geformten Gelände (± 0,6 m) · %s", on, all,
                t.formen.get(t.formen.size() - 1)));
        com.dan.geiranger.scene.Plattform pf = com.dan.geiranger.scene.Plattform.build(t);
        double[] r = com.dan.geiranger.scene.Plattform.rechteck(o);
        double luft = 1e9;
        for (double s = -r[5] + 2.5; s <= r[5] - 0.5; s += 1)   // die innere Kante liegt am Bankett der Straße auf
            for (double u = -r[6] + 0.5; u <= r[6] - 0.5; u += 1) {
                double x = r[0] + r[3] * s - r[4] * u, z = r[2] + r[4] * s + r[3] * u;
                luft = Math.min(luft, r[1] - com.dan.geiranger.scene.Plattform.DICKE - t.height(x, z));
            }
        double[] pu = Site.toUtm(pf.x, pf.z);
        check(pf.pfosten > 20 && luft > 0.9 && Math.abs(pf.y - 530) < 40 && Math.hypot(pu[0] - Site.PLATTFORM.e(), pu[1] - Site.PLATTFORM.n()) < 30,
                String.format(Locale.GERMAN, "%s; unter dem Deck mindestens %.1f m Luft", pf.describe(), luft));
        // Begegnungsregel: 10 min Verkehr im Juni
        com.dan.geiranger.gl.Strasse st = new com.dan.geiranger.gl.Strasse(t);
        double[] eye = {pf.x, pf.y + 2, pf.z};
        for (int i = 0; i < 6000; i++) st.rechne(eye, i * 0.1f, 0.1f, 150);
        check(st.begegnung.begegnungen > 0 && st.fahrzeuge > 30 && st.dreiecke == 0,
                String.format(Locale.GERMAN, "Verkehr nach 10 min: %d Fahrzeuge, %s", st.fahrzeuge, st.begegnung.describe()));
        check(Ansichten.byName("Plattform").key() == Ansichten.PLATTFORM && Site.KERN_OST.contains(pf.x, pf.z), "Ansicht 1 „Plattform“ auf dem Deck, in Kern Ost");
    }

    /** Phase 10: Wald und Bodendecke (Biom Vestland), die Höfe, die Tundra; Nacharbeit: Klassenpfad, Gischt der Elva. */
    static void wald() {
        System.out.println("Wald, Wiesen und Höfe (Phase 10)");
        String quelle = String.valueOf(com.dan.ground.Plant.class.getProtectionDomain().getCodeSource().getLocation());
        String quelleR = String.valueOf(com.dan.road.Way.class.getProtectionDomain().getCodeSource().getLocation());
        check(quelle.contains("Renderer") && quelleR.contains("Renderer"),
                "ground und road kommen aus Renderer.jar, nicht aus den älteren Kopien in FStyle.jar (Klassenpfad: Renderer.jar zuerst) · " + quelle.substring(Math.max(0, quelle.length() - 40)));
        String tf = Shader.source("terrain.frag", null).text;
        check(tf.contains("uniform sampler2D uTundra") && tf.contains("uWiesen") && new File("daten/" + Renderer.TUNDRA).isFile(),
                "Gelände: Tundra mit der Zeichnung des Scans (daten/" + Renderer.TUNDRA + ", F10 A) und Hofwiesen");
        check(Quality.MITTEL.forest() == com.dan.forest.ForestField.Quality.MEDIUM && Quality.NIEDRIG.groundRadius() < Quality.MITTEL.groundRadius(),
                "Stufen: Wald Mittel = MEDIUM (" + Quality.MITTEL.forest().radius / 1000 + " km), Bodendecke " + Quality.MITTEL.groundRadius() + " m");
        Terrain t = gelaendeOderNull();
        if (t == null) return;
        com.dan.geiranger.scene.Hoefe h = com.dan.geiranger.scene.Hoefe.build(t);
        System.out.println("  " + h.describe());
        boolean hofOk = h.hoefe.size() == 2;
        for (com.dan.geiranger.scene.Hoefe.Hof f : h.hoefe) hofOk &= f.haeuser() >= 4 && f.y() > 150 && f.y() < 400 && f.neigung() < 30;
        check(hofOk && h.holz.triangles() > 300 && h.holz.area(com.dan.timber.Material.SOD) > 100 && h.holz.area(com.dan.timber.Material.PLANK) > 200,
                String.format(Locale.GERMAN, "Knivsflå und Skageflå auf ihren Bändern, je mindestens vier Häuser aus timber: Bohlen %.0f m², Grasdach %.0f m² (Phase 11)",
                        h.holz.area(com.dan.timber.Material.PLANK), h.holz.area(com.dan.timber.Material.SOD)));
        com.dan.geiranger.scene.Vestland v = new com.dan.geiranger.scene.Vestland(t, h);
        java.util.Random r = new java.util.Random(10);
        double unten = 0, oben = 0;
        int nu = 0, no = 0, imWasser = 0, aufStrasse = 0, aufWiese = 0;
        for (int i = 0; i < 40000; i++) {
            double[] p = Site.fromUtm(400000 + r.nextDouble() * 8000, 6885500 + r.nextDouble() * 4500);
            double d = v.wald(p[0], p[1]), y = t.height(p[0], p[1]);
            if (t.fjord(p[0], p[1])) { if (d > 0) imWasser++; continue; }
            if (y > 100 && y < 650) { unten += d; nu++; }
            if (y > 950) { oben += d; no++; }
        }
        com.dan.road.Way w = t.ornevegen.way;
        for (int i = 0; i < w.n; i += 5) if (v.wald(w.x[i], w.z[i]) > 0) aufStrasse++;
        for (double[] wi : h.wiesen()) if (v.wald(wi[0], wi[1]) > 0) aufWiese++;
        // Nacharbeit 10: die Hänge sind bis an die Wände grün (Fotos Geiranger1 bis 3), daher bis 92 % statt 75 %
        check(imWasser == 0 && aufStrasse == 0 && aufWiese == 0 && unten / nu > 0.35 && unten / nu < 0.92 && oben / no < 0.02,
                String.format(Locale.GERMAN, "Wald 100 bis 650 m: %.0f %% der Fläche, über 950 m %.1f %%; nie im Fjord, auf der Straße oder den Hofwiesen", 100 * unten / nu, 100 * oben / no));
        // Arten: Erle unten an der Elva, Kiefer an steilen Stellen, Birke überall
        double[] mu = Site.fromUtm(406_250, 6_886_500);
        double erle = 0, birke = 0;
        for (int i = 0; i < 400; i++) {
            double x = mu[0] + (r.nextDouble() - 0.5) * 300, z = mu[1] + (r.nextDouble() - 0.5) * 300;
            erle += v.dichte(x, z, com.dan.geiranger.scene.Vestland.ERLE);
            birke += v.dichte(x, z, com.dan.geiranger.scene.Vestland.BIRKE);
        }
        check(erle > 0.3 * birke && com.dan.geiranger.scene.Vestland.arten().get(0).heightMax <= 15,
                String.format(Locale.GERMAN, "Unten an der Elva Erle zu Birke %.2f; Birken bis %.0f m hoch (Fjordhang)", erle / Math.max(1e-9, birke),
                        com.dan.geiranger.scene.Vestland.arten().get(0).heightMax));
        // ForestField um den Ort: Bäume nur auf Land, über dem Grund
        com.dan.forest.ForestField ff = new com.dan.forest.ForestField(4, 4, v::dichte, (x, z) -> (float) t.height(x, z), 1702);
        ff.setHeights(12, 10, 10, 18);
        ff.apply(com.dan.forest.ForestField.Quality.MEDIUM);
        double[] eye = {mu[0], 120, mu[1] - 800};
        ff.completeNow(eye);
        ff.update(eye, null, 1000);
        int wasser = 0, alle = 0;
        for (int b = 0; b < ff.buckets(); b++)
            for (int i = 0; i < ff.count[b]; i++) {
                float x = ff.bucket[b][i * com.dan.forest.ForestField.STRIDE], z = ff.bucket[b][i * com.dan.forest.ForestField.STRIDE + 2];
                alle++;
                if (t.fjord(x, z)) wasser++;
            }
        check(alle > 2000 && wasser == 0, String.format(Locale.GERMAN, "Wald um den Ort (Reichweite %.1f km): %,d Bäume, keiner im Fjord", ff.radius / 1000, alle));
        // Gischt der Elva (Status_Phase8b): die Stufen schäumen im Bett, statt als Schleier über dem Ufer zu liegen
        check(com.dan.geiranger.scene.Faelle.SPRAY_FLUSS < 0.5, "Nacharbeit 8b: Gischt der Stufen in der Elva auf " + com.dan.geiranger.scene.Faelle.SPRAY_FLUSS);
    }

    static void kamera() {
        System.out.println("Kamera");
        Camera cam = new Camera();
        check(cam.heading() > 230 && cam.heading() < 260 && cam.eye[1] > 1200,
                String.format(Locale.GERMAN, "Grundansicht: hoch über dem Ostarm, Blick %03.0f° den Fjord hinab nach Westsüdwest", cam.heading()));
        cam.place(0, 0, 0, 90, 0);
        cam.groundLevel = Buehne.height(0, 0);
        cam.step(0.1);
        check(cam.eye[1] >= Camera.MIN_EYE - 1e-9, String.format(Locale.GERMAN, "Frei auf der Bühne: Auge bleibt über dem Boden (%.1f m)", cam.eye[1]));
        cam.setMode(Camera.Mode.KREISEN);
        check(cam.mode == Camera.Mode.KREISEN && cam.dist > 1, "Wechsel Frei → Kreisen behält die Ansicht");
        cam.lookAt(1e6, 0, -1e6, 0, 30, 1000);
        check(cam.tx <= Camera.X_MAX && cam.tz >= Camera.Z_MIN, "Drehpunkt bleibt im Ausschnitt");
        int ready = 0;
        boolean ueber = true;
        for (Ansichten.Ansicht a : Ansichten.ALLE) {
            Camera c = new Camera();
            if (Ansichten.apply(a, c, Renderer.PHASE)) {
                ready++;
                ueber &= Math.abs(c.eye[1] - (a.grund() + a.eyeAboveGround())) < 0.01;
            }
        }
        check(ready == 9 && ueber, ready + " von 9 Ansichten stehen (Plattform, Vom Wasser, Am Storfossen, Skageflå, Anleger, Vogelauge, Fjordgrund, Sieben Schwestern, Friaren); ohne Gelände in der Höhe ihres Grunds");
        Terrain t = gelaendeOderNull();
        if (t != null) {
            boolean frei = true;
            StringBuilder sb = new StringBuilder();
            for (Ansichten.Ansicht a : Ansichten.ALLE) {
                Camera c = new Camera();
                // über dem Fjord steht man auf dem Spiegel (Phase 3: der Grund liegt darunter)
                java.util.function.DoubleBinaryOperator stand = (x, z) -> t.fjord(x, z) ? Math.max(0, t.height(x, z)) : t.height(x, z);
                Ansichten.apply(a, c, Renderer.PHASE, stand);
                double g = stand.applyAsDouble(a.x(), a.z());
                frei &= c.eye[1] > g + 1;
                sb.append(String.format(Locale.GERMAN, "%s %.0f m, ", a.name(), g));
            }
            check(frei, "Auf dem Gelände: jedes Auge über dem Grund (" + sb.substring(0, sb.length() - 2) + ")");
            Ansichten.Ansicht w = Ansichten.byName("VomWasser"), an = Ansichten.byName("Anleger");
            Ansichten.Ansicht fg = Ansichten.byName("Fjordgrund");
            check(t.fjord(w.x(), w.z()) && t.fjord(an.x(), an.z()) && t.fjord(fg.x(), fg.z()), "„Vom Wasser“, „Anleger“ und „Fjordgrund“ stehen auf dem Fjord");
            Ansichten.Ansicht pl = Ansichten.byName("Plattform");
            double ph = Math.toRadians(pl.heading()), fx = Math.sin(ph), fz = -Math.cos(ph);
            boolean wasserVoraus = false;
            for (double d = 300; d < 3000; d += 50) wasserVoraus |= t.fjord(pl.x() + fx * d, pl.z() + fz * d);
            check(wasserVoraus, "Von der Plattform liegt der Fjord in Blickrichtung (wie Foto Ørnesvingen2)");
        }
        Ansichten.Ansicht pl = Ansichten.byName("Plattform"), sk = Ansichten.byName("Skagefla");
        check(pl != null && Math.abs(pl.grund() - 536) < 1 && sk != null && sk.name().equals("Skageflå"), "Plattform auf 536 m (Phase 9), „Skagefla“ findet Skageflå (Umgebungsvariablen)");
    }

    /** Phase 11: der Ort Geiranger (F16 A), Laternen, Ansicht 5; Nacharbeit 10: Laub, Waldkarte; Ansicht 7 springt zurück. */
    static void dorf() throws Exception {
        System.out.println("Der Ort Geiranger (Phase 11)");
        String bf = Shader.source("bauten.frag", null).text, tf = Shader.source("terrain.frag", null).text;
        check(bf.contains("uniform float uLamp") && bf.contains("c.r > 1.5") && bf.contains("c.r < -0.5") && Shader.source("glow.frag", null).text.contains("uLamp"),
                "bauten.frag: Verschalung, Fenster (nachts erleuchtet) und Laternen; glow für die Lichthöfe");
        check(tf.contains("uniform sampler2D uWald") && tf.contains("krone"), "Gelände: Kronendach aus der Waldkarte (Nacharbeit 10)");
        com.dan.forest.Species birke = com.dan.geiranger.scene.Vestland.arten().get(0);
        check(birke.summer[1] < 0.16 && birke.spring[1] < 0.2 && birke.leafOut >= 130,
                String.format(Locale.GERMAN, "Birkenlaub dunkler (Sommer %.3f, Frühling %.3f Grün; Austrieb Tag %d) wie auf den Fotos", birke.summer[1], birke.spring[1], birke.leafOut));
        Terrain t = gelaendeOderNull();
        if (t == null) return;
        com.dan.geiranger.scene.Dorf d = com.dan.geiranger.scene.Dorf.build(t);
        System.out.println("  " + d.describe());
        int imWasser = 0, aufStrasse = 0, inElva = 0, schwebt = 0;
        java.util.Map<com.dan.geiranger.scene.Dorf.Art, Integer> n = new java.util.EnumMap<>(com.dan.geiranger.scene.Dorf.Art.class);
        com.dan.road.Network.Hit hit = new com.dan.road.Network.Hit();
        for (com.dan.geiranger.scene.Dorf.Bau b : d.bauten) {
            n.merge(b.art(), 1, Integer::sum);
            if (b.art() == com.dan.geiranger.scene.Dorf.Art.NAUST) continue;   // Naust stehen am Ufer
            double qx = -b.az(), qz = b.ax();
            for (int i = -1; i <= 1; i += 2)
                for (int j = -1; j <= 1; j += 2) {
                    double x = b.x() + b.ax() * b.hl() * i + qx * b.hw() * j, z = b.z() + b.az() * b.hl() * i + qz * b.hw() * j;
                    if (t.fjord(x, z)) imWasser++;
                    if (t.ornevegen.net.locate(x, z, hit) && hit.s > 0.5 && hit.s < hit.way.length - 0.5 && Math.abs(hit.u) < hit.way.type.half() + 0.5) aufStrasse++;
                    double[] nb = t.elva.nearest(x, z);
                    if (nb[1] < t.elva.half[(int) nb[0]]) inElva++;
                }
            if (b.art() != com.dan.geiranger.scene.Dorf.Art.WOHNWAGEN && b.y() - t.height(b.x(), b.z()) > 7) schwebt++;
        }
        check(d.gebaeude() >= 55 && d.gebaeude() <= 75 && n.getOrDefault(com.dan.geiranger.scene.Dorf.Art.HOTEL, 0) == 2 && n.getOrDefault(com.dan.geiranger.scene.Dorf.Art.KIRCHE, 0) == 1
                        && n.getOrDefault(com.dan.geiranger.scene.Dorf.Art.TERMINAL, 0) == 1 && n.getOrDefault(com.dan.geiranger.scene.Dorf.Art.NAUST, 0) == 4
                        && n.getOrDefault(com.dan.geiranger.scene.Dorf.Art.HYTTE, 0) >= 5,
                String.format(Locale.GERMAN, "Rund 60 Gebäude: %d (Kirche, Hotel Union, Hotel Geiranger, Terminal, 4 Naust, Camping)", d.gebaeude()));
        check(imWasser == 0 && aufStrasse == 0 && inElva == 0 && schwebt == 0,
                String.format(Locale.GERMAN, "Keine Ecke im Fjord (%d), auf der Fahrbahn (%d) oder in der Elva (%d); kein Boden mehr als 7 m über dem Grund (%d)", imWasser, aufStrasse, inElva, schwebt));
        double kd = Math.hypot(d.bauten.stream().filter(b -> b.art() == com.dan.geiranger.scene.Dorf.Art.KIRCHE).findFirst().get().x() - com.dan.geiranger.scene.Dorf.KIRCHE[0], 0);
        double[] ku = Site.toUtm(com.dan.geiranger.scene.Dorf.KIRCHE[0], com.dan.geiranger.scene.Dorf.KIRCHE[1]);
        check(kd < 1 && Math.abs(ku[0] - 406_406) < 15 && Math.abs(ku[1] - 6_886_481) < 15, "Kirche bei 62° 05′ 56″ N, 7° 12′ 26″ O (UTM 406 406 / 6 886 481)");
        // Anleger: Deck über Wasser, Pfähle bis zum Grund
        int ueberWasser = 0, proben = 0;
        for (double x = com.dan.geiranger.scene.Dorf.ANLEGER_X1; x <= com.dan.geiranger.scene.Dorf.ANLEGER_X0; x += 2) { proben++; if (t.fjord(x, com.dan.geiranger.scene.Dorf.ANLEGER_Z)) ueberWasser++; }
        check(ueberWasser > 0.6 * proben && d.holz.triangles() > 500 && d.holz.area(com.dan.timber.Material.DECK) > 500,
                String.format(Locale.GERMAN, "Anleger aus timber: %.0f m² Deck, %d von %d m über dem Fjord", d.holz.area(com.dan.timber.Material.DECK), 2 * ueberWasser, 2 * proben));
        check(d.lampen.size() >= 15 && d.fenster > 300, String.format(Locale.GERMAN, "Lichter in der Nacht: %d Laternen, %d Fenster", d.lampen.size(), d.fenster));
        com.dan.geiranger.scene.Vestland v = new com.dan.geiranger.scene.Vestland(t, null, d);
        int baum = 0;
        for (com.dan.geiranger.scene.Dorf.Bau b : d.bauten) if (v.wald(b.x(), b.z()) > 0) baum++;
        check(baum == 0, "Kein Baum auf einem Bau oder dem Campingplatz (" + baum + ")");
        com.dan.geiranger.scene.Ornevegen o = t.ornevegen;
        check(o.strandvegen != null && o.way.x[0] > 0 && o.way.z[0] > 300 && o.strandvegen.length > 120,
                String.format(Locale.GERMAN, "Fv63 vom Storfossen durch den Ort (Beginn %.0f m südlich des Anlegers), Strandvegen %.0f m zum Campingplatz", o.way.z[0], o.strandvegen.length));
        // Ansicht 5: der Ort vom Wasser
        Ansichten.Ansicht an = Ansichten.byName("Anleger");
        double ph = Math.toRadians(an.heading());
        int vorne = 0;
        for (com.dan.geiranger.scene.Dorf.Bau b : d.bauten) {
            double dx = b.x() - an.x(), dz = b.z() - an.z(), l = Math.hypot(dx, dz);
            if ((dx * Math.sin(ph) - dz * Math.cos(ph)) / l > Math.cos(Math.toRadians(30))) vorne++;
        }
        check(t.fjord(an.x(), an.z()) && vorne > 0.6 * d.bauten.size(), "Ansicht 5 „Anleger“: der Ort vom Wasser, " + vorne + " von " + d.bauten.size() + " Bauten im Blickfeld");
        // Waldkarte (Nacharbeit 10): an den Hängen dicht, im Fjord leer
        com.dan.geiranger.gl.Wald w = new com.dan.geiranger.gl.Wald(t);
        byte[] k = w.karteJetzt();
        int hang = 0, hangN = 0, fjord = 0;
        double[] r = Site.UMGEBUNG.scene();
        int N = com.dan.geiranger.gl.Wald.KARTE;
        for (int j = 0; j < N; j += 16)
            for (int i = 0; i < N; i += 16) {
                double x = r[0] + (i + 0.5) * (r[2] - r[0]) / N, z = r[1] + (j + 0.5) * (r[3] - r[1]) / N, y = t.height(x, z);
                int val = k[j * N + i] & 0xFF;
                if (t.fjord(x, z)) { if (val > 0) fjord++; continue; }
                if (y > 100 && y < 600) { hangN++; if (val > 128) hang++; }
            }
        check(fjord == 0 && hang > 0.35 * hangN, String.format(Locale.GERMAN, "Waldkarte %d²: %.0f %% der Hänge 100 bis 600 m über der Hälfte bewaldet, im Fjord nichts", N, 100.0 * hang / Math.max(1, hangN)));
        // Ansicht 7 schaltet „Grund zeigen“ ein, jede andere wieder aus; von Hand bleibt es
        Renderer re = new Renderer();
        re.placeView(Ansichten.byKey(Ansichten.FJORDGRUND));
        boolean an7 = re.showBed;
        re.placeView(Ansichten.byKey(2));
        boolean aus = !re.showBed;
        re.setShowBed(true);
        re.placeView(Ansichten.byKey(2));
        check(an7 && aus && re.showBed, "Ansicht 7 zeigt den Grund, die nächste Ansicht wieder das Wasser; „Grund zeigen“ von Hand bleibt (Fehler aus Status_Phase10a)");
    }

    /** Phase 12: Schiffe nach Fahrplan, Schwimmen mit buoyancy, Kielwasser; Nacharbeit 11 (Fels nah, Risse im Gelände). */
    static void schiffe() throws Exception {
        System.out.println("Schiffe und Kielwasser (Phase 12)");
        String kc = Shader.source("kielwasser.comp", null).text, cg = new String(com.dan.cliff.Cliff.class.getResourceAsStream("/com/dan/cliff/gl/cliff.glsl").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8), tv = Shader.source("terrain.vert", null).text;
        check(kc.contains("uKern[28]") && kc.contains("delle(") && Shader.source("schiff.vert", null).text.contains("uModel"),
                "Kielwasser (iWave, Kern 13 × 13) und Schiffe (Modellmatrix) auf der Grafikkarte");
        check(cg.contains("cliffValueExact") && cg.contains("footprint * 256.0 < s") && tv.contains("cellAt") && !tv.contains("aNode.z / uGridDim), d, uCliffRange"),
                "Nacharbeit 11 (Status_Phase11a): Relief des Fels nah genau gemischt (kein Strichraster), Maßstab der Feinform ohne Sprung zwischen Knoten (kein Spalt)");
        // der Kern antwortet mit |k| (tiefes Wasser), ab |k| = 0,4 auf 6 %
        float[] K = com.dan.geiranger.gl.Kielwasser.KERN;
        double worst = 0;
        for (double k = 0.5; k <= 2.4; k += 0.1)
            for (double ang : new double[]{0, Math.PI / 8, Math.PI / 4}) {
                double r = 0;
                for (int dj = -6; dj <= 6; dj++)
                    for (int di = -6; di <= 6; di++) {
                        if (di * di + dj * dj > 37) continue;
                        int a = Math.max(Math.abs(di), Math.abs(dj)), b = Math.min(Math.abs(di), Math.abs(dj));
                        r += K[a * (a + 1) / 2 + b] * Math.cos(k * (Math.cos(ang) * di + Math.sin(ang) * dj));
                    }
                worst = Math.max(worst, Math.abs(r / k - 1));
            }
        double sum = 0;
        for (int dj = -6; dj <= 6; dj++) for (int di = -6; di <= 6; di++) if (di * di + dj * dj <= 37) { int a = Math.max(Math.abs(di), Math.abs(dj)), b = Math.min(Math.abs(di), Math.abs(dj)); sum += K[a * (a + 1) / 2 + b]; }
        check(worst < 0.06 && Math.abs(sum) < 0.01, String.format(Locale.GERMAN, "Kern des Kielwassers: Antwort |k| für 0,5 ≤ |k| ≤ 2,4 auf %.1f %%, bei k = 0: %.4f", worst * 100, sum));
        // Nacharbeit 12 (Status_Phase12a): fester Schritt; ungleiche Bildzeiten dürfen den Schritt nicht ändern
        double kmax = 0;
        for (double kx = 0; kx <= Math.PI + 1e-9; kx += Math.PI / 32)
            for (double kz = 0; kz <= Math.PI + 1e-9; kz += Math.PI / 32) {
                double r = 0;
                for (int dj = -6; dj <= 6; dj++)
                    for (int di = -6; di <= 6; di++) {
                        if (di * di + dj * dj > 37) continue;
                        int a = Math.max(Math.abs(di), Math.abs(dj)), b = Math.min(Math.abs(di), Math.abs(dj));
                        r += K[a * (a + 1) / 2 + b] * Math.cos(kx * di + kz * dj);
                    }
                kmax = Math.max(kmax, r);
            }
        double dtK = com.dan.geiranger.gl.Kielwasser.DT, cfl = com.dan.geiranger.gl.Kielwasser.G * dtK * dtK / com.dan.geiranger.gl.Kielwasser.ZELLE * kmax;
        java.util.Random rnd = new java.util.Random(12);
        double uhr = 0, feld = 0;
        int summe = 0;
        for (int b = 0; b < 3000; b++) {
            uhr += 0.004 + 0.036 * rnd.nextDouble();          // Bildzeiten 4 bis 40 ms durcheinander
            int n = com.dan.geiranger.gl.Kielwasser.schritte(feld, uhr);
            feld += n * dtK;
            summe += n;
        }
        check(cfl < 2 && uhr - feld >= 0 && uhr - feld < dtK && Math.abs(summe * dtK - feld) < 1e-9,
                String.format(Locale.GERMAN, "Nacharbeit 12: Kielwasser in festen Schritten zu %.0f ms bei Bildzeiten von 4 bis 40 ms (%d Schritte, Rest %.0f ms); stabil: g·dt²/dx · Kern %.2f < 4",
                        dtK * 1000, summe, (uhr - feld) * 1000, cfl));
        Terrain t = gelaendeOderNull();
        if (t == null) return;
        com.dan.geiranger.scene.Flotte f = new com.dan.geiranger.scene.Flotte(t);
        // der Weg liegt im Fjord, mindestens 150 m vom Ufer (außer am Anlegeweg)
        int imLand = 0;
        double minFrei = 1e9;
        for (int i = 0; i < f.weg.x.length; i++) {
            if (f.weg.x[i] < -16000) continue;
            if (!t.fjord(f.weg.x[i], f.weg.z[i])) imLand++;
            if (f.weg.s[i] < f.weg.laenge - 300) minFrei = Math.min(minFrei, f.weg.frei[i]);
        }
        check(imLand == 0 && minFrei > 150 && f.weg.laenge > 18000,
                String.format(Locale.GERMAN, "Weg der Fähre %.1f km im Fjord, mindestens %.0f m vom Ufer", f.weg.laenge / 1000, minFrei));
        // ein Tag im Juni: jede Ecke jedes Schiffs auf Wasser, das tiefer ist als sein Tiefgang
        double e0 = java.time.ZonedDateTime.of(java.time.LocalDate.of(2026, 6, 21), java.time.LocalTime.of(6, 0), com.dan.geiranger.scene.SceneClock.ZONE).toInstant().toEpochMilli() / 1000.0;
        int ecken = 0, aufLand = 0, begegnung = 0;
        double schwV = 1e9;
        for (int k = 0; k <= 14 * 3600; k += 10) {
            f.stelle(e0 + k);
            for (com.dan.geiranger.scene.Flotte.Schiff s : f.schiffe) {
                if (!s.da || s.x < -16000) continue;
                double h = Math.toRadians(s.kurs), fx = Math.sin(h), fz = -Math.cos(h);
                for (double u = -0.95; u <= 0.95; u += 0.475)
                    for (int sd = -1; sd <= 1; sd += 2) {
                        double x = s.x + fx * u * s.hl - fz * sd * s.hw * 0.9, z = s.z + fz * u * s.hl + fx * sd * s.hw * 0.9;
                        ecken++;
                        if (!t.fjord(x, z) || t.height(x, z) > -s.tiefgang - 0.5) aufLand++;
                    }
            }
            com.dan.geiranger.scene.Flotte.Schiff a = f.schiffe.get(0), b = f.schiffe.get(1);
            if (a.da && b.da && a.fahrt > 1 && b.fahrt > 1 && Math.hypot(a.x - b.x, a.z - b.z) < 600) begegnung++;
            if (a.da && Math.hypot(a.x - com.dan.geiranger.scene.Flotte.SCHWESTERN_PASS[0], a.z - com.dan.geiranger.scene.Flotte.SCHWESTERN_PASS[1]) < 300) schwV = Math.min(schwV, a.fahrt);
        }
        check(aufLand == 0 && ecken > 10000, String.format(Locale.GERMAN, "21. Juni 6 bis 20 Uhr: %d Rumpfpunkte, %d an Land oder zu flach", ecken, aufLand));
        check(begegnung > 0 && schwV <= 3.05, String.format(Locale.GERMAN, "Die Fähren begegnen sich unterwegs (%d × 10 s unter 600 m), vor den Schwestern höchstens %.1f m/s", begegnung, schwV));
        // Fahrplan: ab Hellesylt 10:00, an Geiranger 11:05 an der Liegestelle; ab Geiranger 11:40
        f.stelle(e0 + 5 * 3600 + 5 * 60 + 30);
        com.dan.geiranger.scene.Flotte.Schiff an = f.schiffe.get(0);
        double[] l = f.weg.at(f.weg.laenge);
        boolean liegt = an.da && an.fahrt < 0.01 && Math.hypot(an.x - l[0], an.z - l[1]) < 1;
        f.stelle(e0 + 5 * 3600 + 3 * 60);
        boolean faehrtNoch = f.schiffe.get(0).fahrt > 0.05;
        f.stelle(e0 + 5 * 3600 + 41 * 60);
        boolean legtAb = f.schiffe.get(0).zustand.equals("legt ab");
        check(liegt && faehrtNoch && legtAb, "Fahrplan 2025: 10:00 ab Hellesylt, 11:05 an der Liegestelle in Geiranger, 11:40 legt sie wieder ab · " + an.zustand);
        // Kreuzfahrtschiff vor Anker mit Wasser rundum, abseits des Wegs der Fähre
        f.stelle(e0 + 6 * 3600);
        com.dan.geiranger.scene.Flotte.Schiff k = f.schiffe.get(2);
        double minWeg = 1e9;
        for (double u = -1; u <= 1; u += 0.1) {
            double x = k.x + Math.sin(Math.toRadians(k.kurs)) * u * k.hl, z = k.z - Math.cos(Math.toRadians(k.kurs)) * u * k.hl;
            for (int i = 0; i < f.weg.x.length; i++) if (f.weg.x[i] > -3000) minWeg = Math.min(minWeg, Math.hypot(f.weg.x[i] - x, f.weg.z[i] - z));
        }
        double[] ku = Site.toUtm(k.x, k.z);
        check(k.da && k.zustand.startsWith("vor Anker") && minWeg > 100 && Math.hypot(k.x - Site.ANLEGER.x(), k.z - Site.ANLEGER.z()) > 400,
                String.format(Locale.GERMAN, "Kreuzfahrtschiff 250 m vor Anker (UTM %.0f / %.0f), %.0f m vom Anleger, %.0f m neben dem Weg der Fähre", ku[0], ku[1],
                        Math.hypot(k.x - Site.ANLEGER.x(), k.z - Site.ANLEGER.z()), minWeg));
        // Schwimmen auf stillem Wasser: Wasserlinie, kaum Trimm, Eigenperioden
        f.ruhig(e0 + 5 * 3600 + 20 * 60);   // 11:20: die Fähre liegt in Geiranger
        com.dan.geiranger.scene.Flotte.Schiff fa = f.schiffe.get(0);
        com.dan.buoyancy.Floater fl = fa.floater;
        check(fa.da && Math.abs(fa.y) < 0.3 && Math.abs(Math.toDegrees(fl.pitch)) < 1.5 && Math.abs(Math.toDegrees(fl.roll)) < 0.5 && fl.gm > 0.5
                        && fl.heavePeriod() > 3 && fl.heavePeriod() < 9 && fl.rollPeriod() > 5,
                String.format(Locale.GERMAN, "Fähre schwimmt (buoyancy): Wasserlinie %+.2f m, Trimm %.2f°, Krängung %.2f°, GM %.1f m, Tauchen %.1f s, Rollen %.1f s, %.0f t",
                        fa.y, Math.toDegrees(fl.pitch), Math.toDegrees(fl.roll), fl.gm, fl.heavePeriod(), fl.rollPeriod(), fl.mass() / 1000));
        // Nacharbeit 12 (Status_Phase12a): jedes Schiff richtet sich aus 5° Krängung wieder auf (das Kreuzfahrtschiff kenterte)
        StringBuilder sb = new StringBuilder();
        boolean alleStabil = true;
        for (com.dan.geiranger.scene.Flotte.Schiff s : f.schiffe) {
            if (s.floater == null || sb.indexOf(s.art.name()) >= 0) continue;
            com.dan.buoyancy.Floater q = s.floater;
            com.dan.buoyancy.Sea still = com.dan.geiranger.scene.Flotte.sea((x, z) -> 0, () -> 0);
            q.x = q.z = 0;
            q.settle(still);
            q.roll = Math.toRadians(5);
            q.vRoll = 0;
            double maxRoll = 0;
            for (int i = 0; i < 120 * 30; i++) {
                q.sample(still);
                q.integrate(1 / 30.0);
                maxRoll = Math.max(maxRoll, Math.abs(Math.toDegrees(q.roll)));
            }
            boolean ok = q.gm > 0.3 && maxRoll <= 5.01 && Math.abs(Math.toDegrees(q.roll)) < 1.0;
            alleStabil &= ok;
            sb.append(sb.length() == 0 ? "" : " · ").append(s.art.name()).append(String.format(Locale.GERMAN, " GM %.1f m, Rollen %.0f s, nach 2 min %.1f°",
                    q.gm, q.rollPeriod(), Math.abs(Math.toDegrees(q.roll))));
        }
        check(alleStabil, "Nacharbeit 12: alle Schiffe aufrecht aus 5° Krängung · " + sb.toString().replace("FAEHRE", "Fähre").replace("KREUZFAHRT", "Kreuzfahrtschiff").replace("SCHNELLBOOT", "Schnellboot"));
        System.out.println("  " + f.describe());
    }

    /** Phase 13: Busbucht und Fahrplan, Busgruppe, Pfad und Wanderer nach Skageflå, Treppe, Gäste aus Avatar.jar. */
    static void menschen() {
        System.out.println("Menschen (Phase 13)");
        Terrain t = gelaendeOderNull();
        if (t == null) return;
        com.dan.geiranger.scene.Bauten b = com.dan.geiranger.scene.Bauten.build(t);
        com.dan.geiranger.scene.Leute l = new com.dan.geiranger.scene.Leute(t, b, com.dan.geiranger.scene.Hoefe.build(t), 6);
        double lane = t.ornevegen.way.type.laneWidth;
        check(l.bucht != null && l.bucht[2] < 0.6 && l.bucht[3] < 60 && com.dan.geiranger.scene.Leute.BUCHT - com.dan.geiranger.scene.Leute.BUS_HW >= lane,
                l.bucht == null ? "Busbucht: keine gefunden" : String.format(Locale.GERMAN, "Busbucht %.0f m vor der Plattform, ganz neben der Fahrbahn (%.2f m frei), Gelände unter dem Bus ± %.2f m",
                        l.bucht[3], com.dan.geiranger.scene.Leute.BUCHT - com.dan.geiranger.scene.Leute.BUS_HW - lane, l.bucht[2]));
        if (l.bucht == null) return;
        double e0 = java.time.ZonedDateTime.of(2026, 6, 21, 0, 0, 0, 0, com.dan.geiranger.scene.SceneClock.ZONE).toEpochSecond();
        double tA = e0 + com.dan.geiranger.scene.Leute.BUS_SOMMER[2] * 3600, H = com.dan.geiranger.scene.Leute.HALT;
        double[] vor = l.bus(tA - 30), steht = l.bus(tA + 300), weg = l.bus(tA + H + 30), aus = l.bus(tA + H + 120);
        double eJan = java.time.ZonedDateTime.of(2026, 1, 15, 12, 0, 0, 0, com.dan.geiranger.scene.SceneClock.ZONE).toEpochSecond();
        check(vor != null && vor[4] == 0 && steht != null && steht[4] == 1 && weg != null && weg[4] == 2 && aus == null && l.bus(eJan) == null
                        && com.dan.geiranger.scene.Leute.BUS_SOMMER.length == 10,
                String.format(Locale.GERMAN, "Ausflugsbus vom Eidsdal: im Juni 10 Halte zu %.0f min (11:10 fährt an, steht, fährt ab), im Januar keiner", H / 60));
        int[] n0 = l.zaehle(tA + 5), n1 = l.zaehle(tA + 300), n2 = l.zaehle(tA + H - 20);
        java.util.List<com.dan.figure.Person> grp = new java.util.ArrayList<>(l.figuren(tA + 300));
        grp.removeIf(p -> p.id >= 2000);
        double minAb = 1e9, maxDy = 0;
        int aufDeck = 0, gehen = 0;
        for (com.dan.figure.Person p : grp) {
            maxDy = Math.max(maxDy, Math.abs(p.y - l.boden(p.x, p.z)));
            if (com.dan.geiranger.scene.Plattform.auf(l.deck, p.x, p.z, 0)) aufDeck++;
            for (com.dan.figure.Person q : grp) if (q != p) minAb = Math.min(minAb, Math.hypot(p.x - q.x, p.z - q.z));
        }
        for (com.dan.figure.Person p : l.figuren(tA + 60)) if (p.id < 2000 && p.gait > 0.5) gehen++;
        check(n0[0] == 0 && n1[0] >= 22 && n1[0] <= 31 && aufDeck == n1[0] && minAb >= 0.7 && maxDy < 0.01 && gehen > 0 && n2[0] == 0,
                String.format(Locale.GERMAN, "Busgruppe kommt und geht: beim Halten 0, nach 5 min %d auf dem Deck (mindestens %.2f m Abstand, auf dem Boden ± %.3f m), nach 1 min %d unterwegs, vor der Abfahrt alle drin (%d)",
                        aufDeck, minAb, maxDy, gehen, n2[0]));
        com.dan.geiranger.scene.Leute.Pfad pf = l.skagefla;
        boolean imFjord = false;
        double ufer = 1e9;
        if (pf != null) {
            for (int i = 0; i < pf.x.length; i++) imFjord |= t.fjord(pf.x[i], pf.z[i]);
            for (double a = 0; a < 360; a += 5)
                for (double r = 0; r < 40; r += 1)
                    if (t.fjord(pf.x[0] + Math.sin(Math.toRadians(a)) * r, pf.z[0] - Math.cos(Math.toRadians(a)) * r)) { ufer = Math.min(ufer, r); break; }
        }
        com.dan.geiranger.scene.Hoefe.Hof sk = null;
        for (com.dan.geiranger.scene.Hoefe.Hof h : com.dan.geiranger.scene.Hoefe.build(t).hoefe) if (h.name().startsWith("Skagefl")) sk = h;
        int last = pf == null ? 0 : pf.x.length - 1;
        check(pf != null && sk != null && !imFjord && ufer <= 12 && Math.hypot(pf.x[last] - sk.x(), pf.z[last] - sk.z()) < 5 && pf.laenge > 300 && pf.laenge < 1500
                        && pf.steilste() <= 0.9 && pf.y[last] - pf.y[0] > 200,
                pf == null ? "Pfad nach Skageflå: nicht gefunden" : String.format(Locale.GERMAN, "Pfad nach Skageflå: %.0f m von der Anlegestelle (%.0f m vom Wasser) zum Hof, %.0f m hinauf, steilste Stelle %.0f %%, nie im Fjord",
                        pf.laenge, ufer, pf.y[last] - pf.y[0], pf.steilste() * 100));
        if (pf != null) {
            double w0 = e0 + com.dan.geiranger.scene.Leute.WANDER_SOMMER[0] * 3600, up = pf.laenge / com.dan.geiranger.scene.Leute.BERGAUF;
            int unterwegs = 0, amHof = 0;
            for (com.dan.figure.Person p : l.figuren(w0 + 1200)) if (p.id / 1000 == 2) unterwegs++;
            for (com.dan.figure.Person p : l.figuren(w0 + up + 60 + com.dan.geiranger.scene.Leute.RAST / 2))
                if (p.id / 1000 == 2 && Math.hypot(p.x - sk.x(), p.z - sk.z()) < 12) amHof++;
            check(unterwegs == 8 && amHof == 8, String.format(Locale.GERMAN, "Wanderer nach Skageflå: nach 20 min %d auf dem Pfad, %d rasten am Hof (%.0f min hinauf)", unterwegs, amHof, up / 60));
        }
        double maxT = 0;
        int nT = 0;
        for (com.dan.figure.Person p : l.figuren(e0 + 14 * 3600))
            if (p.id / 1000 == 3) {
                double s = 0, best = 1e9;
                for (int i = 0; i < l.treppe.x.length; i++) { double d = Math.hypot(l.treppe.x[i] - p.x, l.treppe.z[i] - p.z); if (d < best) { best = d; s = l.treppe.s[i]; } }
                maxT = Math.max(maxT, Math.abs(p.y - b.oberkante(s)));
                nT++;
            }
        check(nT >= 3 && maxT < 0.4, String.format(Locale.GERMAN, "Treppe am Storfossen: %d Besucher auf den Stufen (± %.2f m)", nT, maxT));
        check(com.dan.geiranger.scene.Leute.anzahl(6, 7.0, "Plattform") == 0 && com.dan.geiranger.scene.Leute.anzahl(6, 12, "Plattform") == 6
                        && com.dan.geiranger.scene.Leute.anzahl(1, 12, "Plattform") == 2 && com.dan.geiranger.scene.Leute.anzahl(6, 12, "Storfossen") == 2,
                "Gäste nach Tageszeit und Monat: im Juni 8:30 bis 21 Uhr sechs auf der Plattform und zwei am Storfossen, im Januar zwei, früh am Morgen keiner");
        // Avatar.jar: laden, backen, 30 s laufen lassen
        long t0 = System.nanoTime();
        double e = e0 + 11 * 3600;
        l.sofort(e, 1, 1 / 30.0);
        double ladeS = (System.nanoTime() - t0) / 1e9;
        if (!l.bereit()) { check(false, "Gäste aus Avatar.jar: " + l.avatarStand); return; }
        java.util.Map<String, double[]> start = new java.util.HashMap<>();
        java.util.Set<String> taten = new java.util.TreeSet<>();
        for (com.dan.geiranger.scene.Leute.Gast g : l.gaeste) start.put(g.name, new double[]{g.x, g.z});
        double maxMs = 0;
        double[] weite = new double[l.gaeste.size()];
        for (int k = 0; k < 90; k++) {
            l.sofort(e + k * 0.333, 10, 1 / 30.0);
            if (k >= 30) maxMs += l.schrittMs / 60;   // Mittel nach dem Anlaufen (Übersetzen im Faden)
            for (int i = 0; i < l.gaeste.size(); i++) {
                com.dan.geiranger.scene.Leute.Gast g = l.gaeste.get(i);
                taten.add(g.tut);
                weite[i] = Math.max(weite[i], Math.hypot(g.x - start.get(g.name)[0], g.z - start.get(g.name)[1]));
            }
        }
        int aufP = 0, nP = 0, gingen = 0, feinMin = Integer.MAX_VALUE, grobMax = 0;
        double nah = 1e9;
        for (int i = 0; i < l.gaeste.size(); i++) {
            com.dan.geiranger.scene.Leute.Gast g = l.gaeste.get(i);
            if (g.ort.equals("Plattform")) { nP++; if (g.da && com.dan.geiranger.scene.Plattform.auf(l.deck, g.x, g.z, -0.25)) aufP++; if (weite[i] > 1) gingen++; }
            for (com.dan.geiranger.scene.Leute.Gast h : l.gaeste) if (h != g && h.ort.equals(g.ort)) nah = Math.min(nah, Math.hypot(g.x - h.x, g.z - h.z));
            feinMin = Math.min(feinMin, g.lod[0].triangles);
            grobMax = Math.max(grobMax, g.lod[2].triangles);
        }
        boolean geste = taten.contains("zeigt") || taten.contains("winkt") || taten.contains("fotografiert");
        check(nP == 6 && aufP == 6 && l.amFoss == 2 && gingen >= 2 && geste && nah >= 0.45 && feinMin > 5000 && grobMax < 1500 && maxMs < 4,
                String.format(Locale.GERMAN, "Gäste aus Avatar.jar (%.1f s geladen): %d auf der Plattform, %d am Storfossen; in 30 s gingen %d, taten %s; nie näher als %.2f m; Netze ab %,d / bis %,d Dreiecke; im Mittel %.2f ms je Schritt",
                        ladeS, aufP, l.amFoss, gingen, taten, nah, feinMin, grobMax, maxMs));
        System.out.println("  " + l.describe(e));
        l.dispose();
    }

    /** Phase 14: das Jahr in Höhenstufen (season), Schnee im Gelände, Frost an den Fällen. */
    static void jahr() throws Exception {
        System.out.println("Jahr (Phase 14)");
        com.dan.geiranger.scene.Jahr j = new com.dan.geiranger.scene.Jahr();
        java.util.function.ToDoubleFunction<int[]> ep = d -> java.time.ZonedDateTime.of(2026, d[0], d[1], 12, 0, 0, 0, com.dan.geiranger.scene.SceneClock.ZONE).toEpochSecond();
        double fe = ep.applyAsDouble(new int[]{2, 15}), ma = ep.applyAsDouble(new int[]{5, 15}), ju = ep.applyAsDouble(new int[]{6, 15}), ok = ep.applyAsDouble(new int[]{10, 15});
        j.settle(fe);
        double feOrt = j.schnee(0), fe600 = j.schnee(600), feEis = j.eis(fe), feLaub = j.laub();
        j.settle(ma);
        double maOrt = j.schnee(0), maGrenze = j.schneegrenze(), maLaub = j.laub();
        j.settle(ju);
        double juGrenze = j.schneegrenze(), juEis = j.eis(ju);
        j.settle(ok);
        double okGrenze = j.schneegrenze(), okHerbst = j.herbst(), ok800 = j.schnee(800);
        check(feOrt > 0.05 && fe600 > 1 && feLaub < 0.1 && maOrt == 0 && maGrenze > 500 && maGrenze < 1100 && maLaub > 0.6 && juGrenze > 1000 && juGrenze < 1500
                        && okHerbst > 0.3 && ok800 == 0 && okGrenze > 1100,
                String.format(Locale.GERMAN, "Das Jahr in zehn Höhenstufen: 15.2. Schnee im Ort %.0f cm, auf 600 m %.1f m, Birken kahl · 15.5. Schneegrenze %.0f m, Laub %.0f %% · 15.6. Schneegrenze %.0f m · 15.10. Herbst %.0f %%, Schneegrenze %.0f m",
                        feOrt * 100, fe600, maGrenze, maLaub * 100, juGrenze, okHerbst * 100, okGrenze));
        double worst = 0;
        for (double e = 0.05; e < 1; e += 0.05) {
            double u = Math.max(0, Math.min(1, -com.dan.geiranger.scene.Jahr.fallTemperatur(e) / 15));
            worst = Math.max(worst, Math.abs(u * u * (3 - 2 * u) - e));
        }
        com.dan.fall.Fall probe = new com.dan.fall.Fall();
        probe.temperature = com.dan.geiranger.scene.Jahr.fallTemperatur(feEis);
        check(feEis > 0.3 && juEis == 0 && worst < 1e-6 && Math.abs(probe.ice() - feEis) < 1e-6,
                String.format(Locale.GERMAN, "Frost an den Fällen (14 Tage auf 300 m): am 15.2. Eis %.0f %%, am 15.6. %.0f %%; com.dan.fall bekommt die passende Temperatur (%.1f °C)",
                        feEis * 100, juEis * 100, probe.temperature));
        String tf = Shader.source("terrain.frag", null).text;
        check(tf.contains("uSnowBand[10]") && tf.contains("cliffMaterial(vWorld, n, uRainWet, clamp(sdep * 6.0") && tf.contains("uJahr.y * uJahr.x"),
                "Gelände: Schnee je Höhe aus season (auch auf den Absätzen von cliff), Birken kahl im Winter und gelb im Herbst, Kronendach mit Schnee");
        com.dan.geiranger.wiring.Abfluss a = new com.dan.geiranger.wiring.Abfluss();
        a.step(0, 0, 5, java.time.LocalDate.of(2026, 2, 15));
        a.frostTemp = com.dan.geiranger.scene.Jahr.fallTemperatur(1);
        check(a.amFall() <= -14.99 && a.frozen("Sieben Schwestern"), "Abfluss: der Frost des Jahres lässt die Fälle stehen, auch wenn die Luft am Mittag 5 °C hat");
        System.out.println("  " + j.describe(ok));
    }

    static void klang() throws Exception {
        System.out.println("Klang (Phase 15)");
        Terrain t = gelaendeOderNull();
        com.dan.geiranger.scene.Klang.Lage L = new com.dan.geiranger.scene.Klang.Lage();
        // Vögel nach Monat und Tageszeit
        double moewe = com.dan.geiranger.scene.Klang.rate(com.dan.soundscape.Soundscape.Kind.MOEWE, 6, 40, true, false, 10, false, 0);
        double schwalbeJan = com.dan.geiranger.scene.Klang.rate(com.dan.soundscape.Soundscape.Kind.SCHWALBE, 1, 10, false, true, 20, false, 0);
        double schwalbeJun = com.dan.geiranger.scene.Klang.rate(com.dan.soundscape.Soundscape.Kind.SCHWALBE, 6, 10, false, true, 20, false, 0);
        double austerNacht = com.dan.geiranger.scene.Klang.rate(com.dan.soundscape.Soundscape.Kind.AUSTERNFISCHER, 6, -8, true, false, 5, false, 0);
        double kraeheNacht = com.dan.geiranger.scene.Klang.rate(com.dan.soundscape.Soundscape.Kind.KRAEHE, 6, -8, false, true, 20, false, 0);
        check(moewe > 0.5 && schwalbeJan == 0 && schwalbeJun > 0.5 && austerNacht > 0 && kraeheNacht == 0,
                String.format(Locale.GERMAN, "Vögel nach Ort, Monat und Tageszeit: Möwe im Juni %.1f/min über dem Wasser, Schwalbe im Januar keine, im Juni %.1f/min, Austernfischer auch nachts, Krähe nachts still",
                        moewe, schwalbeJun));
        if (t == null) { check(false, "Klang braucht das Gelände"); return; }
        java.util.List<com.dan.geiranger.scene.Faelle.Eintrag> fs = com.dan.geiranger.tools.KlangFahrt.faelle(t);
        com.dan.geiranger.tools.KlangFahrt.Fahrt r = com.dan.geiranger.tools.KlangFahrt.fahrt(t, fs, 900, true, 20, 15);
        double mx = -1e9, mxT = 0, fern = -1e9;
        for (int i = 0; i < r.pegel().length; i++) {
            double p = r.pegel()[i];
            if (Double.isNaN(p)) continue;
            if (p > mx) { mx = p; mxT = r.zeit()[i]; }
            if (Math.abs(r.zeit()[i]) >= 600) fern = Math.max(fern, p);
        }
        check(mx > 45 && Math.abs(mxT) < 120 && fern < mx - 15,
                String.format(Locale.GERMAN, "Abnahme, Fahrt mit der Fähre (21.6., MF Bolsøy): die Sieben Schwestern werden lauter und leiser – am Ohr höchstens %.0f dB (%+.0f s an der Stelle vor dem Fall), 10 min davor und danach höchstens %.0f dB",
                        mx, mxT, fern));
        java.util.List<com.dan.geiranger.scene.Echo.Treffer> ec = r.echos();
        boolean zwei = ec.size() >= 2;
        double t1 = zwei ? ec.get(0).laufzeit(343) : 0, t2 = zwei ? ec.get(1).laufzeit(343) : 0;
        double dp = zwei ? Math.abs(((ec.get(0).peilung(r.ohrX(), r.ohrZ()) - ec.get(1).peilung(r.ohrX(), r.ohrZ())) % 360 + 540) % 360 - 180) : 0;
        check(zwei && t1 > 1.5 && t1 < 5 && t2 - t1 > 0.25 && dp > 90,
                "Abnahme, das Horn vor den Schwestern kommt zweimal zurück (Echo der Wände aus dem Gelände): " + r.echoText());
        // im Ton: nach dem Horn still, dann je ein Echo links und rechts (Band um 220 Hz)
        boolean imTon = false;
        String tonText = "kein Ton";
        if (zwei && r.ton() != null && !Double.isNaN(r.hornTon())) {
            int sr = r.rate(), a = (int) (r.hornTon() * sr);
            double[] hl = com.dan.geiranger.tools.KlangFahrt.huelle(r.ton(), sr, 220, a, (int) (6 * sr), 0);
            double[] hr = com.dan.geiranger.tools.KlangFahrt.huelle(r.ton(), sr, 220, a, (int) (6 * sr), 1);
            java.util.function.BiFunction<double[], double[], Double> mittel = (h, w) -> {
                double s = 0; int n = 0;
                for (int i = (int) (w[0] / 0.02); i < Math.min(h.length, (int) (w[1] / 0.02)); i++) { s += Math.pow(10, h[i] / 10); n++; }
                return 10 * Math.log10(s / Math.max(1, n));
            };
            double still = mittel.apply(hl, new double[]{1.4, t1 - 0.2}), e1 = mittel.apply(hl, new double[]{t1 + 0.05, t1 + 0.45}), e2 = mittel.apply(hr, new double[]{t2 + 0.05, t2 + 0.45});
            double horn = mittel.apply(hl, new double[]{0.1, 0.5});
            imTon = e1 > still + 8 && e2 > still + 6 && horn > e1 + 15;
            tonText = String.format(Locale.GERMAN, "Horn %.0f dB, Stille danach %.0f dB, Echo 1 links %.0f dB, Echo 2 rechts %.0f dB (relativ, Band 220 Hz)", horn, still, e1, e2);
        }
        check(imTon, "Im erzeugten Ton: " + tonText);
        check(r.beschreibung().contains("Horn: MF Bolsøy an den Sieben Schwestern") && r.beschreibung().contains("Sieben Schwestern"), "Klang: " + r.beschreibung());
        double[] kn = com.dan.geiranger.tools.KlangFahrt.knacken(t, fs);
        check(kn[1] < kn[0] * 1.5 + 2,
                String.format(Locale.GERMAN, "Nacharbeit 15: in Fahrt kein Knacken (jeder Fall behält seinen Torrent, Ort und Pegel je Bild stetig): Scheitel der 2. Differenz fahrend %.1f, stehend %.1f (%.0f m in 20 s)",
                        kn[1], kn[0], kn[2]));
        // Stellvertreter: Friaren am Anleger ist weiter als 3 km und trotzdem hörbar
        com.dan.geiranger.scene.Klang k = new com.dan.geiranger.scene.Klang(t, com.dan.soundscape.Soundscape.Quality.LOW, null);
        k.echoBereit(20_000);
        L.faelle = fs;
        L.epoch = java.time.ZonedDateTime.of(2026, 6, 21, 12, 0, 0, 0, com.dan.geiranger.scene.SceneClock.ZONE).toEpochSecond();
        L.dt = 0.3;
        com.dan.geiranger.scene.Faelle.Eintrag fe = null;
        for (com.dan.geiranger.scene.Faelle.Eintrag e : fs) if (e.def.name().equals("Friaren")) fe = e;
        // eine Stelle auf dem Weg der Fähre 3,2 bis 4,5 km von Friaren, wo Friaren hörbar ist
        com.dan.geiranger.scene.Flotte flo = new com.dan.geiranger.scene.Flotte(t);
        double dF = 0;
        double[] fr = {Double.NaN, Double.NaN};
        double[] wo = null;
        for (double d = 0; fe != null && d < flo.weg.laenge && Double.isNaN(fr[0]); d += 100) {
            double[] p = flo.weg.at(d);
            double dd = Math.hypot(fe.mitte()[0] - p[0], fe.mitte()[2] - p[1]);
            if (dd < 3200 || dd > 4500) continue;
            L.ohr = new double[]{p[0], 8, p[1]};
            k.update(L);
            k.update(L);
            fr = k.fallPegel("Friaren");
            dF = dd;
            wo = p;
        }
        final double[] w0 = wo;
        check(!Double.isNaN(fr[0]) && w0 != null && k.s.torrents.stream().anyMatch(x -> x.flow > 0.5 && Math.hypot(x.x - w0[0], x.z - w0[1]) <= com.dan.geiranger.scene.Klang.STELLVERTRETER + 1),
                String.format(Locale.GERMAN, "Fälle weiter als 3 km (soundscape hört Torrents nur so weit) stehen als Stellvertreter in 2,8 km: Friaren auf dem Weg der Fähre %.0f m weg, %.0f dB%s",
                        dF, fr[0], fr[1] > 1 ? String.format(Locale.GERMAN, " (Grat −%.0f dB)", fr[1]) : ""));
        System.out.println("  " + k.describe());
    }

    /** Phase 16: die drei Regiebücher als Text, ohne Bild durchgespielt; die Fahrt über die Ørnesvingen; das Kino. */
    static void regie() throws Exception {
        System.out.println("Regiebücher, Fahrten, Kino (Phase 16)");
        java.util.List<com.dan.geiranger.scene.Regie.Book> all;
        try { all = com.dan.geiranger.scene.Regie.all(); } catch (RuntimeException e) { check(false, "die drei Regiebücher lesen: " + e.getMessage()); return; }
        int views = 0;
        StringBuilder bad = new StringBuilder();
        for (com.dan.geiranger.scene.Regie.Book b : all)
            for (com.dan.geiranger.scene.Regie.Zeile z : b.zeilen())
                if (z.befehl().equals("ansicht")) { views++; if (com.dan.geiranger.camera.Ansichten.byName(z.argumente()) == null) bad.append(' ').append(z.argumente()); }
        boolean blatt = all.stream().allMatch(b -> b.zeilen().stream().anyMatch(z -> z.befehl().equals("blatt")));
        int fotos = all.stream().mapToInt(com.dan.geiranger.scene.Regie.Book::fotos).sum();
        check(all.size() == 3 && blatt && bad.length() == 0 && fotos >= 25, String.format(Locale.GERMAN,
                "drei Regiebücher aus scene/regie/*.txt (%s): %d Fotos, jedes mit Blatt, %d Ansichten, alle bekannt%s",
                String.join(", ", all.stream().map(com.dan.geiranger.scene.Regie.Book::name).toList()), fotos, views, bad.length() == 0 ? "" : " – unbekannt:" + bad));
        boolean caught = false;
        try { com.dan.geiranger.scene.Regie.parse("Regiebuch: X\n0:00 zeit 1.1.2026 10:00\n0:05 warte ebbe\n"); } catch (IllegalArgumentException e) { caught = e.getMessage().startsWith("Zeile 3"); }
        check(caught, "ein falsches warte nennt die Zeile");
        Terrain t = gelaendeOderNull();
        if (t == null) { check(false, "Regiebücher brauchen das Gelände"); return; }
        SceneClock c = new SceneClock();
        Wiring w = new Wiring(c);
        w.startFree(com.dan.weather.Weather.Phase.KLAR);
        Renderer r = new Renderer(c, w, t);
        r.ohneBlatt = true;
        // die Fahrt über die Ørnesvingen: auf der Straße, das Auge über der Fahrbahn, von unten bis zur Plattform
        com.dan.geiranger.camera.Fahrt of = r.ornesvingenFahrt(210);
        double unter = 0;
        if (of != null) for (double sm = 0; sm <= of.length; sm += 2) { double[] p = of.at(sm); if (p[1] < r.groundHeight(p[0], p[2]) + 0.5) unter += 2; }
        double[] e0 = of == null ? null : of.pose(0), e1 = of == null ? null : of.pose(1);
        check(of != null && of.length > 4000 && e0[1] < 60 && e1[1] > 500 && unter < 60 && of.maxTurnPerMeter() < 12, of == null ? "Fahrt Ørnesvingen: keine Straße" : String.format(Locale.GERMAN,
                "Fahrt Ørnesvingen: %.1f km im Bus auf der rechten Spur von %.0f auf %.0f m, das Auge 2,6 m über der Fahrbahn, in 210 s, Blick dreht in den Kehren bis %.0f°/m (auf %.0f m unter dem Gelände: an einer Kehre über Grande liegt die Fahrbahn im Hang)", of.length / 1000, e0[1], e1[1], of.maxTurnPerMeter(), unter));
        // die drei Regiebücher ohne Bild
        String[] namen = {"Junitag", "Ornesvingen", "Faehre"};
        int[] fotosSoll = new int[3];
        for (int i = 0; i < 3; i++) fotosSoll[i] = all.get(i).fotos();
        String[] text = new String[3];
        boolean[] ok = new boolean[3];
        for (int i = 0; i < 3; i++) {
            com.dan.geiranger.scene.Regie.Book b = com.dan.geiranger.scene.Regie.byName(namen[i]);
            r.klang.offline();
            r.regieStart(b);
            int steps = 0;
            boolean mitfahrt = false, fahrt = false, horn = false;
            double nahPlattform = 1e9;
            while (r.regie.running() && steps < 8000) {
                r.step(0.1);
                r.bildlos();
                r.klang.s.render(0.1);
                steps++;
                mitfahrt |= r.camera.mitfahrt != null;
                fahrt |= r.camera.fahrt != null;
                if (r.strasse.plattform != null) nahPlattform = Math.min(nahPlattform, Math.hypot(r.camera.eye[0] - r.strasse.plattform.x, r.camera.eye[2] - r.strasse.plattform.z));
                horn |= r.klang.describe().contains("Horn: MF Bolsøy an den Sieben Schwestern");
            }
            java.time.ZonedDateTime end = c.local();
            int f = r.fotos();
            String msg = r.regieLine();
            switch (i) {
                case 0 -> { ok[i] = !r.regie.running() && f == fotosSoll[i] && end.getDayOfMonth() == 22 && end.getHour() == 0 && w.weatherLine().contains("Junitag");
                    text[i] = String.format(Locale.GERMAN, "„Ein Junitag“ ohne Bild: %d Fotos, endet %s um %02d:%02d", f, end.toLocalDate(), end.getHour(), end.getMinute()); }
                case 1 -> { ok[i] = !r.regie.running() && f == fotosSoll[i] && fahrt && nahPlattform < 60 && r.regie.waitedTotal() < 30;
                    text[i] = String.format(Locale.GERMAN, "„Fahrt über die Ørnesvingen“ ohne Bild: %d Fotos, im Bus bis %.0f m an die Plattform, %.0f s gewartet", f, nahPlattform, r.regie.waitedTotal()); }
                default -> { ok[i] = !r.regie.running() && f == fotosSoll[i] && mitfahrt && horn && end.getHour() == 9 && end.getMinute() >= 35 && r.regie.waitedTotal() < 300;
                    text[i] = String.format(Locale.GERMAN, "„Mit der Fähre von Hellesylt“ ohne Bild: %d Fotos, an Bord, das Horn vor den Schwestern, am Anleger %02d:%02d, %.0f s gewartet", f, end.getHour(), end.getMinute(), r.regie.waitedTotal()); }
            }
            check(ok[i], text[i] + (msg.contains("gibt es nicht") || msg.contains("nicht in der Szene") ? " · " + msg : ""));
        }
        r.regieStop();
        // das Kino: Vor- und Abspann, Untertitel aus den Stichworten
        r.kinoStart(com.dan.geiranger.scene.Regie.ornesvingen());
        java.util.List<com.dan.kino.Kino.Lage> seen = new java.util.ArrayList<>();
        String ut = null, abspann = null;
        int steps = 0;
        while (steps++ < 400 && (r.kino.laeuft() || seen.isEmpty())) {
            r.step(0.1);
            r.bildlos();
            com.dan.kino.Kino.Lage l = r.kino.lage();
            if (seen.isEmpty() || seen.get(seen.size() - 1) != l) seen.add(l);
            com.dan.kino.Kino.Tafel tf = r.kino.tafel();
            if (tf != null && tf.art() == com.dan.kino.Kino.Art.UNTERTITEL && ut == null) ut = tf.zeilen().get(0);
            if (tf != null && tf.art() == com.dan.kino.Kino.Art.ABSPANN && abspann == null) abspann = String.join(" / ", tf.zeilen());
            if (steps == 120) r.regie.stop();
        }
        check(seen.equals(java.util.List.of(com.dan.kino.Kino.Lage.VORSPANN, com.dan.kino.Kino.Lage.FILM, com.dan.kino.Kino.Lage.ABSPANN, com.dan.kino.Kino.Lage.AUSKLANG, com.dan.kino.Kino.Lage.AUS))
                        && ut != null && ut.startsWith("10:30") && abspann != null && abspann.startsWith("Fahrt über die Ørnesvingen") && abspann.contains("HGT"),
                "Kino: " + String.join(" → ", seen.stream().map(Enum::name).toList()) + "; erster Untertitel „" + ut + "“; Abspann „" + (abspann == null ? "—" : abspann.substring(0, Math.min(70, abspann.length()))) + " …“");
        r.klang.dispose();
    }

    static void qualitaet() {
        System.out.println("Qualität");
        boolean mono = true;
        Quality[] q = Quality.values();
        for (int i = 1; i < q.length; i++) mono &= q[i].renderScale >= q[i - 1].renderScale && q[i].budgetMs() > q[i - 1].budgetMs();
        check(mono, "Stufen Niedrig → Ultra steigen an");
        check(Math.abs(Quality.MITTEL.budgetMs() - 16.0) < 1e-9, "„Mittel“ hat das Budget aus Abschnitt 10: 16,7 ms je Bild, 0,7 ms Reserve");
        Renderer r = new Renderer();
        check(r.quality() == Quality.MITTEL, "Start auf „Mittel“ (F22 A)");
        r.regulateForTest(30, 10_000_000_000L);
        check(r.renderScale < Quality.MITTEL.renderScale, String.format(Locale.GERMAN, "Grafikkarte 30 ms: Auflösung sinkt auf %.0f %%", r.renderScale * 100));
        check(r.renderScale >= Renderer.MIN_SCALE, "nie unter " + Math.round(Renderer.MIN_SCALE * 100) + " %");
        boolean fein = true;
        for (Quality x : q) fein &= x.terrainKern <= x.terrainFine;
        check(fein && Quality.MITTEL.terrainKern == 0, "In den Kernfenstern ist das Gelände nie gröber als außerhalb; „Mittel“ bis 2 m (F3)");
        check(com.dan.fall.Fall.Quality.values().length == q.length && com.dan.cliff.Cliff.Quality.values().length == q.length,
                "fall und cliff haben je vier Stufen (Paketvertrag Punkt 7)");
    }

    static void physik() {
        System.out.println("Faden der Physik");
        Physik p = new Physik();
        int[] n = {0};
        p.add((t, dt) -> n[0]++);
        p.advance(0.5);
        int steps = p.drain();
        check(steps == Physik.MAX_BEHIND && n[0] == steps, "Fester Schritt 1/120 s, höchstens " + Physik.MAX_BEHIND + " Schritte nachgeholt (" + steps + ")");
        p.advance(Physik.DT * 3.5);
        check(p.drain() == 3, "3,5 Schritte Zeit → 3 Schritte, der Rest wartet");
        p.add((t, dt) -> { throw new IllegalStateException("Probe"); });
        p.advance(Physik.DT);
        p.drain();
        check(p.aufgaben() == 1, "Eine fehlerhafte Aufgabe fällt heraus, die anderen laufen weiter");
    }

    static void shader() {
        System.out.println("Shader");
        for (String[] prog : Renderer.shaderFiles()) {
            for (String f : prog) {
                boolean found;
                String text = "";
                try {
                    text = Shader.source(f, null).text;
                    found = text.startsWith("#version 430 core");
                } catch (RuntimeException e) {
                    found = false;
                }
                check(found, "Shader " + f + " gefunden und Bausteine eingesetzt (" + text.split("\n").length + " Zeilen)");
            }
        }
        String st = Shader.source("terrain.frag", null).text, th = Shader.source("terrain_height.glsl", null).text;
        check(st.contains("artShare(w, 1u)") && st.contains("aerial(") && th.contains("uFern") && th.contains("smoothstep(0.0, uNaht, insideDist(w))"),
                "Gelände: Fjord nach der Art je Zelle, Dunst, Fernring mit Überblendung wie Terrain.height");
        boolean swapped = false, half = false;
        for (String[] prog : Renderer.shaderFiles()) for (String f : prog) {
            String x = Shader.source(f, null).text;
            half |= Pattern.compile("\\bhalf\\b").matcher(x).find();
            swapped |= Pattern.compile("smoothstep\\((-?[\\d.]+), (-?[\\d.]+),").matcher(x).results().anyMatch(m -> Double.parseDouble(m.group(1)) > Double.parseDouble(m.group(2)));
        }
        check(!half && !swapped, "kein reserviertes Wort „half“, kein smoothstep mit vertauschten Kanten (AMD-Treiber sind streng)");
        check(ComputeProbe.expected(0, 63) == 64 * 65 / 2 && ComputeProbe.expected(1, 0) == 65, "Compute-Test: Erwartungswerte der Präfixsumme");
        check(Renderer.PASSES.length == 14 && Renderer.PHASE == 18 && Renderer.PASSES[0].equals("Schatten") && Renderer.PASSES[3].equals("Gelände")
                        && Renderer.PASSES[4].equals("Straße") && Renderer.PASSES[5].equals("Wald") && Renderer.PASSES[6].equals("Ort") && Renderer.PASSES[7].equals("Schiffe")
                        && Renderer.PASSES[8].equals("Menschen") && Renderer.PASSES[9].equals("Wasser") && Renderer.PASSES[10].equals("Fälle") && Renderer.PASSES[11].equals("Wetter"),
                "Vierzehn Durchgänge mit Zeitmessung: " + String.join(", ", Renderer.PASSES));
        String sch = Shader.source("schatten.frag", null).text;
        check(st.contains("wallLight(w) * cloudLight(w)") && st.contains("uSkyAmb") && sch.contains("s * s / (2.0 * EARTH_R)") && sch.contains("artAt(w) == 1u ? max(h0, uLevel)"),
                "Gelände: Schatten der Wände und Wolkenschatten, Himmelslicht aus com.dan.sky; die Schattenkarte mit Erdkrümmung, über dem Fjord ab dem Spiegel");
        String sp = Shader.source("spiegel.frag", null).text, oc = jarText("/com/dan/ocean/gl/surface.frag");
        check(sp.contains("(uv.x - 0.5) * 6.28318530718") && sp.contains("(uv.y - 0.5) * 3.14159265359") && sp.contains("-cos(az) * cos(el)")
                        && oc.contains("atan(d.x, -d.z) / 6.28318530718 + 0.5") && oc.contains("asin(clamp(d.y, -1.0, 1.0)) / 3.14159265359 + 0.5"),
                "Rundumbild des Spiegelbilds in derselben Abbildung, die ocean als Himmel liest (Azimut von Nord, Höhe)");
        check(st.contains("uClip == 1 && vWorld.y < uLevel") && st.contains("uWaterMode == 2") && Shader.source("terrain.vert", null).text.contains("max(h, uLevel)"),
                "Gelände: Spiegelbild schneidet unter dem Wasser ab, „Grund zeigen“ und Platzhalter-Spiegel, wenn ocean fehlt");
    }

    /** Phase 17: Skripte, Startdaten, Spalten der Abfragen, Dateien ohne Datenbank, Laden, Chronik, Zustand, Tageskarte. */
    static void daten() throws Exception {
        System.out.println("Datenbank und Dateien (Phase 17, ohne Oracle)");
        File dir = com.dan.geiranger.data.Verbindung.skripte();
        String[] namen = {"01_tabellen.sql", "02_spatial.sql", "03_sichten.sql", "04_startdaten.sql", "99_entfernen.sql"};
        boolean da = true;
        for (String n : namen) da &= new File(dir, n).isFile();
        check(da, "db/sql mit " + String.join(", ", namen));
        if (!da) return;
        String t01 = Files.readString(new File(dir, namen[0]).toPath(), StandardCharsets.UTF_8), t02 = Files.readString(new File(dir, namen[1]).toPath(), StandardCharsets.UTF_8),
                t03 = Files.readString(new File(dir, namen[2]).toPath(), StandardCharsets.UTF_8), t04 = Files.readString(new File(dir, namen[3]).toPath(), StandardCharsets.UTF_8);
        var a01 = com.dan.store.Skript.zerlegen(t01);
        var spalten = com.dan.store.Skript.spalten(a01);
        java.util.Map<String, java.util.Set<String>> tab = new java.util.TreeMap<>();
        java.util.Set<String> geo = new java.util.TreeSet<>();
        for (var s : spalten) {
            tab.computeIfAbsent(s.tabelle(), k -> new java.util.HashSet<>()).add(s.name());
            if (s.typ().contains("SDO_GEOMETRY")) geo.add(s.tabelle() + "." + s.name());
        }
        int seq = t01.split("CREATE SEQUENCE GR_", -1).length - 1, trig = t01.split("CREATE OR REPLACE TRIGGER GR_", -1).length - 1;
        check(tab.size() == 36 && tab.keySet().stream().allMatch(x -> x.startsWith("GR_")) && seq == 35 && trig == 34 && t01.lines().filter(z -> !z.strip().startsWith("--")).noneMatch(z -> z.contains("IDENTITY") || z.contains("GENERATED")),
                String.format(Locale.GERMAN, "01_tabellen.sql: %d Tabellen GR_, %d Sequenzen, %d Trigger, keine IDENTITY (%d Anweisungen)", tab.size(), seq, trig, a01.size()));
        int szt = 0;
        boolean szOk = true;
        for (var e : tab.entrySet())
            if (e.getValue().contains("SZENENZEIT") && e.getValue().contains("SZENE_MS")) {
                szt++;
                szOk &= t01.contains("BEFORE INSERT OR UPDATE ON " + e.getKey() + "\n") && t01.substring(t01.indexOf("BEFORE INSERT OR UPDATE ON " + e.getKey() + "\n")).split("END;", 2)[0]
                        .contains("INTO :NEW.SZENENZEIT");
            }
        check(szt >= 8 && szOk, "SZENENZEIT (TIMESTAMP WITH TIME ZONE, Europe/Oslo) setzt der Trigger in " + szt + " Tabellen aus SZENE_MS");
        java.util.Set<String> meta = new java.util.TreeSet<>(java.util.List.of(com.dan.geiranger.data.Verbindung.METADATEN));
        boolean idx = true;
        for (String m : meta) idx &= t02.contains("CREATE INDEX " + m.replace('.', '_') + "_SX ON " + m.split("\\.")[0] + " (" + m.split("\\.")[1] + ")")
                && t02.contains("TABLE_NAME = '" + m.split("\\.")[0] + "' AND COLUMN_NAME = '" + m.split("\\.")[1] + "'");
        boolean sichten = true;
        for (String v : com.dan.geiranger.data.Verbindung.SICHTEN) sichten &= t03.contains("CREATE OR REPLACE VIEW " + v + " AS");
        check(geo.equals(meta) && idx && sichten && t02.contains("'GR_TAGESKARTE_V', 'GEOM'"), String.format(Locale.GERMAN,
                "%d Geometriespalten in 8307 mit Metadaten und räumlichem Index (02), %d Sichten (03), Metadaten der Sichten für QGIS", geo.size(), com.dan.geiranger.data.Verbindung.SICHTEN.length));
        // jede Spalte, die die App oder ein Skript nennt, gibt es
        java.util.List<String> fehlt = new java.util.ArrayList<>();
        spaltenPruefen(javaTexte(readOr("src/com/dan/geiranger/data/DbSpeicher.java")), tab, fehlt, "DbSpeicher");
        spaltenPruefen(javaTexte(readOr("src/com/dan/geiranger/data/Verbindung.java")), tab, fehlt, "Verbindung");
        spaltenPruefen(sqlTexte(t03), tab, fehlt, "03_sichten");
        spaltenPruefen(sqlTexte(t04), tab, fehlt, "04_startdaten");
        check(fehlt.isEmpty(), "alle Spalten in DbSpeicher, Einrichter, Sichten und Startdaten stehen in 01_tabellen.sql" + (fehlt.isEmpty() ? "" : ": FEHLT " + String.join(", ", fehlt)));
        // Startdaten: aus dem Code erzeugt und aktuell
        java.util.Map<String, Integer> zahl = com.dan.geiranger.data.Verbindung.zahlen(dir);
        check(zahl.keySet().equals(com.dan.geiranger.data.Verbindung.ZAEHLEN.keySet()), "04_startdaten.sql: Zeile ZAHLEN mit allen " + zahl.size() + " Arten, die der Einrichter zählt: " + zahl);
        Terrain t = gelaendeOderNull();
        if (t != null) {
            long t0 = System.nanoTime();
            var sz = com.dan.geiranger.data.Startdaten.szene(Terrain.defaultDir());
            var alles = com.dan.geiranger.tools.StartdatenSql.Alles.aus(sz);
            String neu = com.dan.geiranger.tools.StartdatenSql.sql(alles);
            check(neu.equals(t04.replace("\r", "")), String.format(Locale.GERMAN, "04_startdaten.sql ist aktuell (aus dem Code neu erzeugt: %,d Zeichen in %.1f s; sonst „ant startdaten“)",
                    neu.length(), (System.nanoTime() - t0) / 1e9));
            int strang = 0, wasser = 0;
            for (var f : alles.faelle()) { strang += f.wege().size(); for (var w : f.wege()) if (w.aufWasser()) wasser++; }
            var schw = alles.faelle().stream().filter(f -> f.name().equals("Sieben Schwestern")).findFirst().orElse(null);
            check(schw != null && schw.wege().size() >= 5 && schw.y() > 300 && strang >= 25 && wasser >= strang / 2, String.format(Locale.GERMAN,
                    "%d Fälle mit %d Wegen der Strähnen (%d enden im Wasser); Sieben Schwestern: Kante %.0f m, %d Wege", alles.faelle().size(), strang, wasser,
                    schw == null ? 0 : schw.y(), schw == null ? 0 : schw.wege().size()));
            var orn = alles.strassen().stream().filter(s -> s.art().equals("ORNEVEGEN")).findFirst().orElse(null);
            check(orn != null && orn.laengeM() > 6000 && orn.steigungMax() < 0.16 && alles.kehren().size() == 11 && alles.gebaeude().size() >= 60 && alles.hoehenlinien().size() >= 30,
                    String.format(Locale.GERMAN, "Fv63 Ørnevegen %.1f km (Steigung bis %.1f %%), %d Kehren, %d Gebäude, %d Orte, %d Höhenlinien (%,.0f km)", orn == null ? 0 : orn.laengeM() / 1000,
                            orn == null ? 0 : orn.steigungMax() * 100, alles.kehren().size(), alles.gebaeude().size(), alles.orte().size(), alles.hoehenlinien().size(),
                            alles.hoehenlinien().stream().mapToDouble(h -> h.linie().laenge()).sum() / 1000));
        }
        var k = com.dan.geiranger.data.Startdaten.klima();
        var fp = com.dan.geiranger.data.Startdaten.fahrplan();
        check(k.size() == 12 && k.get(5).abflussAnteil() == 1.0 && fp.size() == 14 && fp.stream().allMatch(f -> f.jahr() == 2025 && f.fahrzeitMin() == 65)
                        && com.dan.geiranger.data.Startdaten.wetterbuecher().size() == 3 && com.dan.geiranger.data.Startdaten.presets().size() >= 80,
                "Startdaten ohne Gelände: " + com.dan.geiranger.data.Startdaten.zusammenfassung());
        // Geometrie hin und zurück (WKT in 8307)
        var fl = com.dan.geiranger.data.Daten.Geom.flaeche(com.dan.geiranger.data.Daten.rechteck(120, 185, 0.6, 0.8, 15, 8.5));
        var ml = com.dan.geiranger.data.Daten.Geom.multilinie(java.util.List.of(new double[][]{{0, 0}, {100, -50}, {200, 0}}, new double[][]{{-500, 10}, {-400, 30}}));
        var fl2 = com.dan.geiranger.data.Daten.Geom.ausWkt(fl.wkt()); var ml2 = com.dan.geiranger.data.Daten.Geom.ausWkt(ml.wkt());
        check(Math.abs(fl2.flaecheM2() - 4 * 15 * 8.5) < 0.5 && Math.abs(ml2.laenge() - ml.laenge()) < 0.05 && fl.wkt().startsWith("POLYGON ((7.2"),
                String.format(Locale.GERMAN, "WKT in Länge und Breite hin und zurück: Fläche %.1f m² (Soll 510), Mehrfachlinie %.2f m (Soll %.2f m)", fl2.flaecheM2(), ml2.laenge(), ml.laenge()));

        // ohne Datenbank: Dateien in einem frischen Ordner
        File tmp = Files.createTempDirectory("geiranger-daten").toFile();
        var ds = new com.dan.geiranger.data.DateiSpeicher(tmp);
        com.dan.geiranger.data.Datenseite.setzen(ds);
        String lz = com.dan.geiranger.data.Laden.laden(ds, 2026);
        check(java.util.Arrays.equals(com.dan.geiranger.scene.Flotte.ABFAHRTEN, com.dan.geiranger.scene.Flotte.ABFAHRTEN_CODE)
                        && com.dan.geiranger.scene.Flotte.FAHRZEIT == com.dan.geiranger.scene.Flotte.FAHRZEIT_CODE && com.dan.geiranger.wiring.KlimaGeiranger.MONATSMITTEL[6] == 15.0
                        && com.dan.geiranger.wiring.KlimaGeiranger.regiebuecher().size() == 3 && com.dan.geiranger.scene.Regie.all().size() == 3 && "Dateien".equals(com.dan.geiranger.data.Datenseite.herkunft),
                "Laden ohne Datenbank (F26): " + lz);
        // ein anderes Jahr im Fahrplan gewinnt, wenn es zur Uhr passt
        var fp2 = new java.util.ArrayList<>(fp);
        fp2.add(new com.dan.geiranger.data.Daten.Fahrplan("Geiranger–Hellesylt", "NACH_HELLESYLT", "09:15", 70, "06-01", "08-31", 2026, null, null, "EIGEN"));
        java.lang.reflect.Method mf = com.dan.geiranger.data.Laden.class.getDeclaredMethod("fahrplan", java.util.List.class, int.class);
        mf.setAccessible(true);
        String f26 = (String) mf.invoke(null, fp2, 2026), f25 = (String) mf.invoke(null, fp2, 2025);
        check(f26.startsWith("Fahrplan 2026 (1 Abfahrten, 70 min") && f25.startsWith("Fahrplan 2025 (7 Abfahrten, 65 min") && com.dan.geiranger.scene.Flotte.ABFAHRTEN.length == 7,
                "Fahrplan nach dem Jahr der Uhr: " + f26 + " · " + f25);
        // eigenes: Regiebuch, Preset, Merkpunkt, Ansicht
        String rt = "Regiebuch: ~Prüfung Abend\nLänge: 0:30\n0:00 zeit 21.06.2026 21:00 | Abend\n0:05 ansicht Plattform\n0:20 foto Abend\n0:29 blatt\n";
        ds.speichern(new com.dan.geiranger.data.Daten.Regiebuch("~Prüfung Abend", rt, "", null, com.dan.geiranger.data.Daten.EIGEN));
        boolean abgelehnt = false;
        try { ds.speichern(new com.dan.geiranger.data.Daten.Regiebuch("Ein Junitag", rt.replace("~Prüfung Abend", "Ein Junitag"), "", null, "EIGEN")); } catch (com.dan.store.Ablage.Fehler e) { abgelehnt = true; }
        com.dan.geiranger.data.Laden.laden(ds, 2026);
        int buecher = com.dan.geiranger.scene.Regie.all().size();
        var oc = com.dan.geiranger.gl.Wasser.fjord(3);
        ds.speichern(new com.dan.geiranger.data.Daten.PaketPreset("com.dan.ocean", "Ocean", "~Prüfung Fjord 3", "Prüfung", "EIGEN", com.dan.store.Felder.lesen(oc)));
        var pr = ds.presets("com.dan.ocean", "Ocean").stream().filter(p -> p.name().equals("~Prüfung Fjord 3")).findFirst().orElse(null);
        var oc2 = com.dan.ocean.Ocean.calm();
        if (pr != null) com.dan.store.Felder.schreiben(oc2, pr.felder());
        byte[] png = new byte[500];
        for (int i = 0; i < png.length; i++) png[i] = (byte) (i * 7);
        long mid = ds.speichern(new com.dan.geiranger.data.Daten.Merkpunkt(0, "~Prüfung Punkt", "unter den Schwestern", -5700, 3, -790, new double[]{-5400, 40, -700}, 290, 8,
                1782041400000L, png, "EIGEN"), null);
        var mp = ds.merkpunkt(mid);
        long aid = ds.speichern(new com.dan.geiranger.data.Daten.Ansicht(0, "~Prüfung Ansicht", null, "GEMERKT", new double[]{-730, 1400, -840, 270, 25}, null, 1782041400000L, "a=1\n", png, "EIGEN"), null);
        var an = ds.ansicht(aid);
        check(abgelehnt && buecher == 4 && pr != null && com.dan.store.Felder.lesen(oc2).equals(com.dan.store.Felder.lesen(oc)) && java.util.Arrays.equals(mp.vorschau(), png)
                        && "unter den Schwestern".equals(mp.notiz()) && Math.abs(mp.abstand() - Math.sqrt(300 * 300 + 37 * 37 + 90 * 90)) < 0.5 && java.util.Arrays.equals(an.vorschau(), png)
                        && new File(tmp, "chronik/merkpunkte.csv").isFile(),
                "Eigenes in daten/: Regiebuch (mitgeliefertes abgelehnt, Laden findet 4), Preset gelesen und angewandt, Merkpunkt mit Notiz und Vorschau (CSV mit WKT), Ansicht mit Vorschau");
        com.dan.geiranger.scene.Regie.ausDaten = null;

        // Chronik und Zustand an einer Szene ohne Bild
        if (t == null) { check(false, "Chronik braucht das Gelände"); return; }
        SceneClock c = new SceneClock(LocalDate.of(2026, 6, 21), 8.0);
        Wiring w = new Wiring(c);
        w.startFree(com.dan.weather.Weather.Phase.KLAR);
        Renderer r = new Renderer(c, w, t);
        r.klang.echoBereit(90_000);
        var ch = new com.dan.geiranger.data.Chronik(r, false);
        long sid = ds.sitzungBeginnen(Renderer.VERSION, "Prüfung", "MITTEL");
        ch.jetzt();
        c.set(LocalDate.of(2026, 6, 21), 12.5);
        ch.jetzt();
        var tc = ds.tagesChronik(LocalDate.of(2026, 6, 21));
        int hoerner = tc.fahrten().stream().mapToInt(com.dan.geiranger.data.Daten.Schiffsfahrt::hoerner).sum();
        var echo = tc.fahrten().stream().filter(f -> f.echos() != null && f.echos() > 0).findFirst().orElse(null);
        boolean plan = tc.fahrten().stream().filter(f -> f.richtung().startsWith("NACH_")).allMatch(f -> f.anMs() != null && f.anMs() - f.abMs() == 65 * 60_000L);
        var nachG = tc.fahrten().stream().filter(f -> f.richtung().equals("NACH_GEIRANGER")).findFirst().orElse(null);
        check(tc.stunden().size() == 4 && tc.tage().size() == 1 && tc.busse().size() == 4 && tc.fahrten().size() >= 4 && plan && hoerner >= 4 && echo != null && nachG != null
                        && nachG.weg() != null && nachG.weg().laenge() > 5000 && tc.stunden().get(0).abfluss().contains("Sieben Schwestern="),
                String.format(Locale.GERMAN, "Chronik 21.6.2026, 8 bis 12:30 Uhr: %d Stunden, %d Tag, %d Busse (%d Gäste), %d Fahrten nach dem Fahrplan (%d Hörner), Weg nach Geiranger %.1f km; %s",
                        tc.stunden().size(), tc.tage().size(), tc.busse().size(), tc.busse().stream().mapToInt(com.dan.geiranger.data.Daten.Busankunft::gruppe).sum(), tc.fahrten().size(),
                        hoerner, nachG == null || nachG.weg() == null ? 0 : nachG.weg().laenge() / 1000, echo == null ? "kein Echo" : echo.echoText()));
        File status = new File(tmp, "status");
        var tk = com.dan.geiranger.data.Tageskarte.zeichnen(ds, t, LocalDate.of(2026, 6, 21), status);
        check(tk.datei().isFile() && tk.fahrten() == tc.fahrten().size() && tk.busse() == 4, "Tageskarte aus den Dateien: " + tk.datei().getName() + ", " + tk.fahrten() + " Fahrten, "
                + tk.hoerner() + " Hörner, " + tk.busse() + " Busse");
        // Zustand: sichern, Szene verstellen, herstellen
        r.camera.place(-5400, 120, -700, 300, 6);
        r.camera.snap();
        c.lapse = 60;
        r.wald.showForest = false;
        r.flotte.kielwasser.on = false;
        r.abfluss().hand = 1.7;
        w.hand(com.dan.weather.Weather.Var.COVER, 6.5);
        var z = com.dan.geiranger.data.Zustand.erfassen(r);
        ds.sitzungSichern(sid, new com.dan.store.Ablage.Sicherung(com.dan.store.Felder.alsText(z, "Prüfung"), com.dan.geiranger.data.Zustand.spalten(z)));
        var letzte = ds.letzteSitzung(-1);
        double ms0 = c.epochMillis();
        c.set(LocalDate.of(2026, 1, 3), 3);
        c.lapse = 1;
        r.camera.home();
        r.camera.snap();
        r.wald.showForest = true;
        r.flotte.kielwasser.on = true;
        r.abfluss().hand = -1;
        w.resumeRegie();
        var fz = com.dan.geiranger.data.Zustand.anwenden(r, com.dan.store.Felder.ausText(letzte.zustand()));
        double[] kk = com.dan.geiranger.data.Zustand.kamera(r);
        check(letzte != null && letzte.id() == sid && letzte.zeitraffer() == 60 && fz.isEmpty() && Math.abs(c.epochMillis() - ms0) < 1 && c.lapse == 60 && !r.wald.showForest
                        && !r.flotte.kielwasser.on && r.abfluss().hand == 1.7 && Math.abs(w.weather.handValues()[com.dan.weather.Weather.Var.COVER.ordinal()] - 6.5) < 1e-9
                        && Math.abs(kk[0] + 5400) < 0.5 && Math.abs(kk[3] - 300) < 0.5 && z.size() > 40,
                String.format(Locale.GERMAN, "„Weiter wie zuletzt“ aus daten/sitzungen: %d Werte (Uhr, Zeitraffer, Kamera, Wetter mit Handregler, Ebenen, Abfluss von Hand) zurück%s",
                        z.size(), fz.isEmpty() ? "" : ", nicht: " + fz));
        // ein laufendes Regiebuch an seiner Stelle fortsetzen
        r.ohneBlatt = true;
        r.regieWeiter(com.dan.geiranger.scene.Regie.junitag(), 120, false);
        boolean laeuft = r.regie.running() && Math.abs(r.regie.time() - 120) < 1e-6;
        var z2 = com.dan.geiranger.data.Zustand.erfassen(r);
        r.regieStop();
        check(laeuft && "Ein Junitag".equals(z2.get("regiebuch.name")) && "120".equals(z2.get("regiebuch.sekunde")),
                "Regiebuch vorgespult (Stichworte bis 2:00 wirken, ohne Fotos): läuft bei " + z2.get("regiebuch.sekunde") + " s; der Zustand nennt es mit Stelle");
        // Merkpunkt: der Strahl trifft Gelände oder Wasser
        r.camera.place(Site.PLATTFORM.x(), 640, Site.PLATTFORM.z(), 245, 12);
        r.camera.snap();
        double[] hit = r.pickGround(800, 600, 1600, 900);
        check(hit != null && Math.abs(hit[1] - Math.max(r.groundHeight(hit[0], hit[2]), t.fjord(hit[0], hit[2]) ? 0 : -1e9)) < 1.0,
                hit == null ? "Merkpunkt: Strahl trifft nichts" : String.format(Locale.GERMAN, "Umschalt + Klick von der Plattform: Strahl trifft %s bei %.0f m in %.0f m", t.fjord(hit[0], hit[2]) ? "das Wasser" : "das Gelände",
                        hit[1], Math.hypot(hit[0] - Site.PLATTFORM.x(), hit[2] - Site.PLATTFORM.z())));
        // Übernahme aus den Dateien (in einen zweiten Ordner, wie in die Datenbank)
        var nach = new com.dan.geiranger.data.DateiSpeicher(Files.createTempDirectory("geiranger-nach").toFile());
        String ue = com.dan.geiranger.data.Uebernahme.uebernehmen(ds, nach);
        var tc2 = nach.tagesChronik(LocalDate.of(2026, 6, 21));
        check(tc2.fahrten().size() == tc.fahrten().size() && tc2.stunden().size() == 4 && nach.merkpunkte().size() == 1 && new File(tmp, "chronik/stunden.csv.uebernommen").isFile(),
                "Übernehmen: " + ue + " (die CSV heißen danach *.uebernommen)");
        ds.sitzungBeenden(sid);
        ch.beenden();
        check(java.util.Arrays.equals(com.dan.geiranger.ui.ControlPanel.REIHENFOLGE, new String[]{"ZEIT", "KAMERA", "REGIEBÜCHER", "WETTER", "WASSER", "SZENE", "KLANG", "DATEN", "TECHNIK"})
                        && readOr("src/com/dan/geiranger/ui/ControlPanel.java").contains("datenBlock();"),
                "Bedienfeld: Blöcke in fester Reihenfolge " + String.join(", ", com.dan.geiranger.ui.ControlPanel.REIHENFOLGE));
    }

    /** Die Anweisungen eines SQL-Skripts, ohne Kommentarzeilen, je auf einer Zeile. */
    static java.util.List<String> sqlTexte(String sql) {
        java.util.List<String> l = new java.util.ArrayList<>();
        for (var a : com.dan.store.Skript.zerlegen(sql)) l.add(a.sql().lines().filter(z -> !z.strip().startsWith("--")).map(String::strip).reduce((x, y) -> x + " " + y).orElse(""));
        return l;
    }

    /** Die Zeichenketten eines Java-Quelltexts; mit + verbundene Stücke werden zu einer. Ohne regulären Ausdruck (lange Texte). */
    static java.util.List<String> javaTexte(String q) {
        java.util.List<String> l = new java.util.ArrayList<>();
        StringBuilder cur = null;
        int i = 0, n = q.length();
        while (i < n) {
            char c = q.charAt(i);
            if (c == '/' && i + 1 < n && q.charAt(i + 1) == '/') { while (i < n && q.charAt(i) != '\n') i++; continue; }
            if (c == '/' && i + 1 < n && q.charAt(i + 1) == '*') { int e = q.indexOf("*/", i + 2); i = e < 0 ? n : e + 2; continue; }
            if (c == '\'') { i++; while (i < n && q.charAt(i) != '\'') { if (q.charAt(i) == '\\') i++; i++; } i++; continue; }
            if (c == '"') {
                StringBuilder b = new StringBuilder();
                i++;
                while (i < n && q.charAt(i) != '"') { if (q.charAt(i) == '\\') { i++; char e = q.charAt(i); b.append(e == 'n' ? ' ' : e); } else b.append(q.charAt(i)); i++; }
                i++;
                if (cur == null) cur = b; else cur.append(b);
                int j = i;
                while (j < n && Character.isWhitespace(q.charAt(j))) j++;
                if (j < n && q.charAt(j) == '+') {
                    int k = j + 1;
                    while (k < n && Character.isWhitespace(q.charAt(k))) k++;
                    if (k < n && q.charAt(k) == '"') { i = k; continue; }
                }
                l.add(cur.toString());
                cur = null;
                continue;
            }
            i++;
        }
        return l;
    }

    static final java.util.Set<String> SQL_WORTE = java.util.Set.of("SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "NULL", "IS", "IN", "AS", "ON", "JOIN", "LEFT", "ORDER", "BY",
            "GROUP", "CASE", "WHEN", "THEN", "ELSE", "END", "DESC", "ASC", "FETCH", "FIRST", "ROWS", "ONLY", "COUNT", "MAX", "MIN", "SUM", "AVG", "ROUND", "TO_CHAR", "DUAL",
            "VALUES", "INTO", "INSERT", "UPDATE", "SET", "DELETE", "DISTINCT", "MDSYS", "SDO_GEOMETRY", "SDO_POINT_TYPE", "SDO_UTIL", "TO_WKTGEOMETRY", "SDO_GEOM", "SDO_LENGTH",
            "SDO_AREA", "SDO_ANYINTERACT", "TRUE", "NULLS", "LAST", "LISTAGG", "WITHIN", "SYSTIMESTAMP", "INTERVAL", "DAY", "CONNECT", "LEVEL", "NEXTVAL", "CURRVAL", "ROWNUM",
            "TO_NUMBER", "SUBSTR", "TRUNC", "MOD", "NVL", "CROSS", "CAST", "VARCHAR2", "UNION", "ALL", "OVER", "TO_CLOB", "CHR", "MERGE", "USING", "MATCHED", "DATE", "EXISTS",
            "SDO_POINT", "X", "Y", "FM990", "FM9990", "FM00", "FM990.0", "FM990.00", "FM9990.0", "FM", "LIKE", "BETWEEN", "REPLACE", "VIEW", "CREATE");

    /**
     * Prüft die Spalten in SQL-Texten eines Quelltexts: INSERT INTO t (…), UPDATE t SET a = …, und a.SPALTE mit Alias aus
     * FROM t a / JOIN t a. Was fehlt, kommt als „Quelle: TABELLE.SPALTE“ in fehlt.
     */
    static void spaltenPruefen(java.util.List<String> texte, java.util.Map<String, java.util.Set<String>> tab, java.util.List<String> fehlt, String wo) {
        for (String roh : texte) {
            String s = roh.replaceAll("'[^']*'", "''");
            if (!s.contains("GR_")) continue;
            java.util.regex.Matcher ins = Pattern.compile("INSERT INTO (GR_[A-Z_]+) \\(([A-Z_, 0-9]+)\\)").matcher(s);
            while (ins.find()) for (String col : ins.group(2).split(",")) pruefe(tab, ins.group(1), col.strip(), fehlt, wo);
            java.util.regex.Matcher upd = Pattern.compile("UPDATE (GR_[A-Z_]+) (?:t )?SET (.*?)(?: WHERE |$)").matcher(s);
            while (upd.find()) {
                java.util.regex.Matcher a = Pattern.compile("(?:^|, )(?:t\\.)?([A-Z_]+) = ").matcher(upd.group(2));
                while (a.find()) pruefe(tab, upd.group(1), a.group(1), fehlt, wo);
            }
            java.util.Map<String, String> alias = new java.util.HashMap<>();
            java.util.regex.Matcher al = Pattern.compile("(?:FROM|JOIN|INTO|,) (GR_[A-Z_]+) ([a-z]{1,2})\\b").matcher(s);
            while (al.find()) alias.put(al.group(2), al.group(1));
            java.util.regex.Matcher ref = Pattern.compile("\\b([a-z]{1,2})\\.([A-Z_][A-Z_0-9]*)").matcher(s);
            while (ref.find()) {
                String tb = alias.get(ref.group(1));
                if (tb != null && !SQL_WORTE.contains(ref.group(2))) pruefe(tab, tb, ref.group(2), fehlt, wo);
            }
            // einfache Abfragen einer Tabelle: SELECT a, b FROM GR_X (ohne Alias, ohne Join)
            java.util.regex.Matcher sel = Pattern.compile("SELECT ([A-Z_0-9, ]+) FROM (GR_[A-Z_]+)(?: WHERE| ORDER|$| FETCH)").matcher(s);
            while (sel.find()) for (String col : sel.group(1).split(",")) { String cc = col.strip(); if (!cc.isEmpty() && !SQL_WORTE.contains(cc) && !cc.matches("\\d+")) pruefe(tab, sel.group(2), cc, fehlt, wo); }
        }
    }

    static void pruefe(java.util.Map<String, java.util.Set<String>> tab, String t, String col, java.util.List<String> fehlt, String wo) {
        if (col.isEmpty() || !tab.containsKey(t) && t.endsWith("_V")) return;
        java.util.Set<String> s = tab.get(t);
        String e = wo + ": " + t + "." + col;
        if ((s == null || !s.contains(col)) && !fehlt.contains(e)) fehlt.add(e);
    }

    static void verdrahtung() {
        System.out.println("Verdrahtung");
        SceneClock c = new SceneClock();
        Wiring w = new Wiring(c);
        check(w.links().size() == 32 && Wiring.planned().isEmpty(), w.summary());
        check(w.surface() == w.water, "Ohne ocean ist die Fläche der feste Spiegel");
        check(w.water.level() == 0, "Spiegel auf NN");
        boolean phasen = true;
        int last = 0;
        for (Wiring.Link l : Wiring.planned()) { phasen &= l.phase() >= last && l.phase() >= 2 && l.phase() <= 18; last = l.phase(); }
        check(phasen, "Geplante Verbindungen folgen dem Phasenplan (2 bis 17)");
        Renderer r = new Renderer(c, w);
        check(w.surface() != w.water && Math.abs(w.surface().level()) < 1e-9, "Mit dem Renderer gibt ocean (Preset Fjord) die Fläche an die Verdrahtung");
        check(r.hudLines().length > 8 && r.statusLine(1920, 1080).contains("21.6.2026"), "HUD und Statuszeile mit Datum: " + r.cameraLine());
    }

    static void bibliothek() {
        System.out.println("Renderer.jar (F4 A)");
        java.util.List<String> p = Bibliothek.pakete();
        check(p.size() >= 25, Bibliothek.kurz());
        check(Bibliothek.fehlend().isEmpty(), "Alle " + Bibliothek.GEBRAUCHT.length + " Packages, die das Werkbuch braucht, liegen in Renderer.jar");
        boolean neu = true;
        for (String n : Bibliothek.NEU) neu &= !p.contains(n);
        check(!neu && Bibliothek.kurz().contains("fall und cliff aus Geiranger") && jarText("/com/dan/fall/README.md").contains("fall") && jarText("/com/dan/cliff/README.md").contains("cliff"),
                "fall und cliff liegen seit Phase 18 in Renderer.jar (Klassen, Shader, README)");
    }

    static void symbol() throws Exception {
        System.out.println("Programmsymbol (F23 A, Phase 18)");
        boolean all = true, jar = true;
        java.util.List<byte[]> pngs = new java.util.ArrayList<>();
        for (int s : AppIcon.SIZES) {
            java.awt.image.BufferedImage b = AppIcon.paint(s);
            all &= b.getWidth() == s && (b.getRGB(s / 2, s / 2) >>> 24) == 255 && (b.getRGB(0, 0) >>> 24) < 255;
            java.awt.image.BufferedImage r = null;
            try (java.io.InputStream in = AppIcon.class.getResourceAsStream("icon/geiranger_" + s + ".png")) { if (in != null) r = javax.imageio.ImageIO.read(in); }
            boolean gleich = r != null && r.getWidth() == s;
            for (int y = 0; gleich && y < s; y += Math.max(1, s / 16)) for (int x = 0; gleich && x < s; x += Math.max(1, s / 16)) gleich = r.getRGB(x, y) == b.getRGB(x, y);
            jar &= gleich;
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(b, "png", o);
            pngs.add(o.toByteArray());
        }
        check(all, "Symbol in " + AppIcon.SIZES.length + " Größen gemalt, deckend in der Mitte, runde Ecken");
        check(jar, "Die PNGs im Jar (ui/icon/geiranger_NN.png) sind das gemalte Symbol (sonst „ant symbol“)");
        File ico = new File("Geiranger.ico");
        byte[] ib = ico.isFile() ? Files.readAllBytes(ico.toPath()) : new byte[0];
        boolean icoOk = ib.length > 6 && ib[0] == 0 && ib[2] == 1 && (ib[4] & 0xff) == AppIcon.SIZES.length;
        String bat = readOr("Geiranger_Verknuepfung.bat");
        check(icoOk && bat.contains("Geiranger.ico") && bat.contains("CreateShortcut") && bat.contains("dist\\Geiranger.jar"),
                String.format(Locale.GERMAN, "Geiranger.ico mit %d Größen (%,d Bytes); Geiranger_Verknuepfung.bat legt die Verknüpfung auf Desktop und Startmenü", icoOk ? ib[4] & 0xff : 0, ib.length));
    }

    /** Phase 18: Qualitätsstufen für alle Teile, Dauerlauf (Urteil ohne Fenster), Quellenliste, README. */
    static void schliff() throws Exception {
        System.out.println("Schliff (Phase 18)");
        Quality[] q = Quality.values();
        boolean steigt = true;
        for (int i = 1; i < q.length; i++)
            steigt &= q[i].strasseRadius() > q[i - 1].strasseRadius() && q[i].fahrzeugReichweite() > q[i - 1].fahrzeugReichweite() && q[i].leutePx()[2] < q[i - 1].leutePx()[2]
                    && q[i].leutePx()[0] < q[i - 1].leutePx()[0] && q[i].schneeAnteil() >= q[i - 1].schneeAnteil() && q[i].timber().ordinal() == i;
        check(steigt && !Quality.NIEDRIG.kielwasser() && Quality.MITTEL.kielwasser() && Quality.MITTEL.leutePx()[0] == com.dan.geiranger.gl.LeuteGl.FEIN_PX
                        && Quality.MITTEL.timber() == com.dan.timber.Timber.Quality.MEDIUM && Quality.MITTEL.summary().contains("Fv63 2,5 km"),
                "Stufen auch für Höfe (timber), Fv63, Menschen, Kielwasser und Schneefall: " + Quality.MITTEL.summary().substring(Quality.MITTEL.summary().indexOf("Höfe")));
        Terrain t = gelaendeOderNull();
        if (t != null) {
            SceneClock c = new SceneClock();
            Renderer r = new Renderer(c, new Wiring(c), t);
            r.setQuality(Quality.NIEDRIG);
            boolean nied = r.strasse.roads.radius == 1500 && r.strasse.roads.vehicleRange == 700 && r.leute.minPx == 4 && !r.flotte.kielwasser.stufe;
            r.setQuality(Quality.ULTRA);
            boolean ultra = r.strasse.roads.radius == 5000 && r.leute.feinPx == 120 && r.flotte.kielwasser.stufe;
            r.setQuality(Quality.MITTEL);
            check(nied && ultra && r.leute.minPx == com.dan.geiranger.gl.LeuteGl.MIN_PX, "Der Renderer gibt die Stufe weiter: Niedrig (Fv63 1,5 km, Kielwasser aus), Ultra (5 km, Kielwasser an), zurück auf Mittel");
        }
        // Dauerlauf: das Urteil aus erfundenen Messungen (gut, mit Leck, unvollständig)
        java.util.List<com.dan.geiranger.app.Dauerlauf.Punkt> p = new java.util.ArrayList<>();
        for (int i = 0; i < 360; i++) p.add(new com.dan.geiranger.app.Dauerlauf.Punkt(i / 12.0, 55 + 5 * Math.sin(i), 9, 4, 3000, 2100, 60, 6 + i % 20, 20 + i % 30, i / 40, 0, 0, 1 + i / 180));
        java.util.List<double[]> gut = java.util.List.of(new double[]{15, 2100, 3, 2, 60}, new double[]{30, 2140, 3, 2, 61});
        java.util.List<double[]> leck = java.util.List.of(new double[]{15, 2100, 3, 2, 60}, new double[]{30, 2600, 3, 2, 61});
        java.util.List<double[]> halb = java.util.List.of(new double[]{15, 2100, 3, 2, 60}, new double[]{30, 2140, 2, 2, 61});
        var u1 = com.dan.geiranger.app.Dauerlauf.urteil(p, gut, 0, 0, 0.999, 10, 40);
        var u2 = com.dan.geiranger.app.Dauerlauf.urteil(p, leck, 0, 0, 0.999, 10, 40);
        var u3 = com.dan.geiranger.app.Dauerlauf.urteil(p, halb, 1, 0, 0.98, 10, 10);
        long f1 = u1.stream().filter(x -> !x[0].equals("ok")).count(), f2 = u2.stream().filter(x -> !x[0].equals("ok")).count(), f3 = u3.stream().filter(x -> !x[0].equals("ok")).count();
        // der Verkehr wächst von Runde zu Runde (bis 180 Fahrzeuge in Runde 2): ein Leck, obwohl unter der Grenze 200
        java.util.List<com.dan.geiranger.app.Dauerlauf.Punkt> pw = new java.util.ArrayList<>();
        for (var x : p) pw.add(new com.dan.geiranger.app.Dauerlauf.Punkt(x.minute(), x.fps(), x.gpu(), x.cpu(), x.heapMb(), x.lebendMb(), x.faeden(), x.gaeste(),
                x.runde() == 2 ? 125 + (int) (x.minute() * 2) : 125, x.faelle(), x.nichtNach(), x.fehler(), x.runde()));
        var u4 = com.dan.geiranger.app.Dauerlauf.urteil(pw, gut, 0, 0, 0.999, 10, 40);
        boolean wachsen = u4.get(4)[0].equals("FEHLER") && u4.stream().filter(x -> !x[0].equals("ok")).count() == 1;
        var u5 = com.dan.geiranger.app.Dauerlauf.urteil(pw.stream().map(x -> new com.dan.geiranger.app.Dauerlauf.Punkt(x.minute(), x.fps(), x.gpu(), x.cpu(), x.heapMb(), x.lebendMb(),
                x.faeden(), x.gaeste(), 125, x.faelle(), x.nichtNach(), x.fehler(), x.runde())).toList(), gut, 0, 0, 0.999, 10, 40);
        boolean erster = u5.stream().allMatch(x -> x[0].equals("ok"));
        var bild = com.dan.geiranger.app.Dauerlauf.bild(p, gut, u1, f1 == 0, 30, "Mittel");
        check(u1.size() == 7 && f1 == 0 && f2 == 1 && u2.get(3)[0].equals("FEHLER") && f3 == 4 && wachsen && erster && bild.getWidth() == 1600,
                "Dauerlauf: Urteil in 7 Punkten; erfundene Läufe: gut → bestanden, Speicher +500 MB → Leck, ein Regiebuch fehlt + Fehler + Physik + Chronik → 4 Verstöße, "
                        + "Verkehr wächst bis 180 → Verstoß; 125 Fahrzeuge in beiden Runden (wie im ersten Dauerlauf) → bestanden; Bild 1600 × 900");
        check(readOr("src/com/dan/geiranger/ui/ControlPanel.java").contains("dauerlauf(30)") && readOr("src/com/dan/geiranger/app/GeirangerApp.java").contains("geiranger.dauerlauf"),
                "Dauerlauf startet im Block TECHNIK („Dauerlauf 30 min“) oder mit -Dgeiranger.dauerlauf=Minuten");
        // Quellenliste und README
        String ql = com.dan.geiranger.tools.Quellenliste.text();
        check(ql.replace("\r", "").equals(readOr("docs/QUELLEN.md").replace("\r", "")), "docs/QUELLEN.md ist aktuell (" + ql.lines().count() + " Zeilen; sonst „ant quellen“)");
        String rd = readOr("README.md");
        java.util.regex.Matcher m = Pattern.compile("\\]\\((docs/bilder/[^)]+)\\)").matcher(rd);
        int bilder = 0, fehlen = 0;
        while (m.find()) { bilder++; if (!new File(m.group(1)).isFile()) fehlen++; }
        check(rd.contains("Phase 18 von 18") && bilder >= 8 && fehlen == 0 && rd.contains("docs/QUELLEN.md") && rd.contains("Dauerlauf"),
                "README mit " + bilder + " Bildern aus docs/bilder (" + (fehlen == 0 ? "alle da" : fehlen + " fehlen") + "), Quellen und Dauerlauf");
    }

    static void pakete() {
        System.out.println("Projekt (F24 A)");
        File base = new File("src/com/dan");
        if (!base.isDirectory()) { System.out.println("  (Quellordner nicht gefunden, übersprungen)"); return; }
        File[] dirs = base.listFiles(f -> f.isDirectory() && !f.getName().equals("geiranger"));
        check(dirs != null && dirs.length == 0 && !new File("pakete").exists() && new File("tools/JarPruefung.java").isFile() && !new File("tools/PaketPruefung.java").exists() && !readOr("build.xml").contains("renderer-jar"), "keine Element-Packages mehr im Projekt: fall und cliff sind in Renderer.jar, ihre Quelle ist das Projekt Renderer (F4 A)");
        check(readOr(".gitignore").contains("/templates/") && readOr(".gitignore").contains("/db/db.properties"), ".gitignore hält templates/ und db/db.properties aus dem Git");
        check(!new File("Neuer Ordner").exists(), "„Neuer Ordner“ ist aufgeräumt");
        check(new File("docs/claude/GEIRANGER_WERKBUCH_PROMPT.md").isFile(), "Prompt-Entwurf bleibt in docs/claude/");
    }

    static void isolation() throws Exception {
        System.out.println("Isolation");
        File base = new File("src/com/dan");
        if (!base.isDirectory()) { System.out.println("  (Quellordner nicht gefunden, übersprungen)"); return; }
        Pattern p = Pattern.compile("com\\.dan\\.geiranger");
        int found = 0, files = 0;
        File[] dirs = base.listFiles(f -> f.isDirectory() && !f.getName().equals("geiranger"));
        if (dirs != null) for (File d : dirs) {
            try (Stream<Path> s = Files.walk(d.toPath())) {
                for (Path f : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".java"))::iterator) {
                    files++;
                    if (p.matcher(Files.readString(f, StandardCharsets.UTF_8)).find()) { found++; System.out.println("    " + f); }
                }
            }
        }
        check(found == 0, files + " Dateien in " + (dirs == null ? 0 : dirs.length) + " Packages, keine nennt com.dan.geiranger");
    }

    // ------------------------------------------------------------------ Helfer

    static double hourOf(double epochMs) {
        java.time.ZonedDateTime z = Instant.ofEpochMilli((long) epochMs).atZone(SceneClock.ZONE);
        return z.getHour() + z.getMinute() / 60.0 + z.getSecond() / 3600.0;
    }

    static String hm(double h) { return String.format("%d:%02d", (int) h, (int) Math.round((h - (int) h) * 60) % 60); }

    static String readOr(String file) {
        try { return Files.readString(Path.of(file), StandardCharsets.UTF_8); } catch (Exception e) { return ""; }
    }
}
