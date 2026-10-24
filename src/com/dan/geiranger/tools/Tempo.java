package com.dan.geiranger.tools;

import com.dan.geiranger.camera.Ansichten;
import com.dan.geiranger.gl.Quality;
import com.dan.geiranger.gl.Renderer;
import com.dan.geiranger.scene.SceneClock;
import com.dan.geiranger.wiring.Wiring;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import java.io.File;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Tempo-Probe und Standbild ohne Fenster (aus Rungholt, entschlackt). Rechnet die Szene unsichtbar in fester Größe,
 * misst die Wanduhr je Bild bis glFinish und die Zeit jedes Durchgangs auf der Grafikkarte und schreibt auf Wunsch
 * ein PNG. So lässt sich jede Phase vorher und nachher vergleichen und im Leistungsbudget (Werkbuch, Abschnitt 10)
 * eintragen.
 * <pre>
 * Tempo [bilder] [breite] [höhe] [png|-] [NIEDRIG|MITTEL|HOCH|ULTRA] [uhrzeit] [datum JJJJ-MM-TT]
 * Tempo 120 1920 1080 docs/status/Status_Phase2.png MITTEL 11 2026-06-21
 * (Ansicht auch als -Dansicht=Plattform)
 * Umgebung: OHNE_HUD, OHNE_MARKEN, HUD_VOLL, ANSICHT=Name einer Ansicht (Plattform, VomWasser, AmStorfossen, Skagefla,
 * Anleger, Vogelauge, Fjordgrund, seit Phase 7 SiebenSchwestern, Friaren) oder „oben“ (senkrecht über dem Ausschnitt), KAMERA=x,z,Richtung,Neigung,Höhe (frei stehend).
 * Seit Phase 3: WIND=Beaufort (0 bis 8, Wind von Hand), WASSER=grund (Grund zeigen) oder ohneSpiegel; seit Phase 4
 * WETTER=junitag, nebel oder regen (Regiebuch am Tag der Uhr, sonst freie Regie ab „klar“); alles auch als -Dwind=…,
 * -Dwasser=…, -Dwetter=… (aus Ant); seit Phase 5 KAMERA bzw. -Dkamera=x,z,Richtung,Neigung,Höhe und FELS=aus bzw. -Dfels=aus;
 * seit Phase 8 ABFLUSS bzw. -Dabfluss=Anteil vom Juni (von Hand) und BELICHTUNG=schaetzen (ohne Messung im Bild);
 * seit Phase 9 RICHTUNG bzw. -Dkamera.richtung=Grad (seit Phase 10 auch NEIGUNG bzw. -Dkamera.neigung) und BUS=1 bzw. -Dbus=1 (Verkehr bis zu einer Begegnung in einer Kehre, dann der Blick darauf);
 * seit Phase 12 SCHIFF bzw. -Dschiff=Name,Abstand,Peilung,Höhe; seit Phase 13 MENSCHEN bzw. -Dmenschen=plattform|gaeste|bus|skagefla; seit Phase 15 MITFAHRT bzw. -Dmitfahrt=Blick (auf der Fähre); seit Phase 16 REGIE bzw. -Dregie=Junitag|Ornesvingen|Faehre (ein Regiebuch mit Blatt), KINO=1 bzw. -Dkino=1 (im Kinomodus, Kinobilder).
 * </pre>
 */
public final class Tempo {
    private Tempo() { }

    /** Phase 13: die Kamera zu den Menschen (Plattform, ein Gast aus der Nähe, Bus und Gruppe, Wanderer nach Skageflå). */
    static void menschenKamera(com.dan.geiranger.gl.Renderer r, String was, double epoch) {
        com.dan.geiranger.scene.Leute l = r.leute.leute;
        double[] dk = l.deck;
        if (dk == null && !was.equals("skagefla")) { System.out.println("Menschen: keine Plattform"); return; }
        switch (was) {
            case "plattform" -> {
                // vom inneren Rand des Decks nach außen über die Leute zum Fjord
                double x = dk[0] - dk[3] * 5.2, z = dk[2] - dk[4] * 5.2, y = dk[1] + 1.65;
                r.camera.place(x, y, z, 245, 6);   // Nacharbeit 13: wie Ansicht 1 über den Fjord (nach außen, 270°, steht der Hang)
            }
            case "gaeste" -> {
                com.dan.geiranger.scene.Leute.Gast g = null;
                for (com.dan.geiranger.scene.Leute.Gast q : l.gaeste)
                    if (q.da && q.ort.equals("Plattform") && (g == null || Math.hypot(q.x - dk[0], q.z - dk[2]) < Math.hypot(g.x - dk[0], g.z - dk[2]))) g = q;
                if (g == null) { System.out.println("Menschen: kein Gast auf der Plattform"); return; }
                // schräg von vorn innen: 2,6 m hinter der Blickrichtung nach innen versetzt, Augenhöhe
                double fx = Math.sin(g.yaw), fz = Math.cos(g.yaw);
                double x = g.x - fx * 1.8 - dk[3] * 2.2 + fz * 1.2, z = g.z - fz * 1.8 - dk[4] * 2.2 - fx * 1.2, y = g.boden + 1.6;
                double hd = Math.toDegrees(Math.atan2(g.x - x, -(g.z - z)));
                r.camera.place(x, y, z, (hd + 360) % 360, Math.toDegrees(Math.atan2(0.45, Math.hypot(g.x - x, g.z - z))));
                System.out.println("Kamera bei " + g.name + " (" + g.tut + ")");
            }
            case "bus" -> {
                double[] b = l.bus(epoch);
                if (b == null) { System.out.println("Menschen: kein Bus um diese Zeit"); return; }
                // von der äußeren Ecke des Decks, die dem Bus am nächsten liegt, auf Bus und Gruppe
                double qx = -dk[4], qz = dk[3], sg = Math.signum((b[0] - dk[0]) * qx + (b[2] - dk[2]) * qz);
                double x = dk[0] + dk[3] * 5.3 + qx * sg * 9.5, z = dk[2] + dk[4] * 5.3 + qz * sg * 9.5, y = dk[1] + 1.7;
                double hd = Math.toDegrees(Math.atan2(b[0] - x, -(b[2] - z))), dd = Math.hypot(b[0] - x, b[2] - z);
                r.camera.place(x, y, z, (hd + 360) % 360, Math.toDegrees(Math.atan2(y - (b[1] + 1.2), dd)));
            }
            case "skagefla" -> {
                double sx = 0, sz = 0, sy = 0;
                int n = 0;
                for (com.dan.figure.Person p : l.figuren(epoch)) if (p.id / 1000 == 2) { sx += p.x; sy += p.y; sz += p.z; n++; }
                if (n == 0) { System.out.println("Menschen: keine Wanderer um diese Zeit"); return; }
                sx /= n; sy /= n; sz /= n;
                // 22 m zum Fjord hin (bergab) und 5 m darüber
                double gx = r.groundHeight(sx + 2, sz) - r.groundHeight(sx - 2, sz), gz = r.groundHeight(sx, sz + 2) - r.groundHeight(sx, sz - 2), gl = Math.max(1e-6, Math.hypot(gx, gz));
                double x = sx - gx / gl * 22, z = sz - gz / gl * 22, y = Math.max(r.groundHeight(x, z) + 2, sy + 5);
                double hd = Math.toDegrees(Math.atan2(sx - x, -(sz - z)));
                r.camera.place(x, y, z, (hd + 360) % 360, Math.toDegrees(Math.atan2(y - (sy + 1), Math.hypot(sx - x, sz - z))));
            }
            default -> System.out.println("Menschen: " + was + " unbekannt (plattform, gaeste, bus, skagefla)");
        }
        System.out.println(l.describe(epoch));
    }

    private static String env(String name, String prop) {
        String v = System.getenv(name);
        return v != null ? v : System.getProperty(prop);
    }

    public static void main(String[] a) throws Exception {
        int frames = a.length > 0 ? Integer.parseInt(a[0]) : 120;
        int w = a.length > 1 ? Integer.parseInt(a[1]) : 1920, h = a.length > 2 ? Integer.parseInt(a[2]) : 1080;
        File png = a.length > 3 && !a[3].equals("-") ? new File(a[3]) : null;
        Quality q = a.length > 4 ? Quality.valueOf(a[4]) : Quality.MITTEL;
        double hour = a.length > 5 ? Double.parseDouble(a[5].replace(',', '.')) : SceneClock.START_HOUR;
        LocalDate day = a.length > 6 && !a[6].equals("-") ? LocalDate.parse(a[6]) : SceneClock.START_DATE;

        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
        d.setAutoSwapBufferMode(false);
        SceneClock clock = new SceneClock(day, hour);
        clock.lapse = 0;
        Wiring wiring = new Wiring(clock);
        Renderer r = new Renderer(clock, wiring);
        r.frozen = true;
        r.setQuality(q);
        r.autoScale = false;
        if (System.getenv("OHNE_HUD") != null) r.showHud = false;
        if (System.getenv("HUD_VOLL") != null) r.hudFull = true;
        if (System.getenv("OHNE_MARKEN") != null) r.showMarks = false;
        String wind = env("WIND", "wind"), wa = env("WASSER", "wasser"), we = env("WETTER", "wetter");
        // Phase 4: das Wetter wie in der App aus der Regie; WETTER=junitag|nebel|regen startet ein Regiebuch am Tag der Uhr
        com.dan.weather.Scenario sc = we == null ? null : switch (we.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "junitag" -> com.dan.geiranger.wiring.KlimaGeiranger.junitag();
            case "nebel", "nebelmorgen" -> com.dan.geiranger.wiring.KlimaGeiranger.nebelmorgen();
            case "regen", "regentag" -> com.dan.geiranger.wiring.KlimaGeiranger.regentag();
            default -> null;
        };
        if (sc != null) wiring.start(sc);
        else wiring.startFree(com.dan.weather.Weather.Phase.KLAR);
        if (wind != null) wiring.hand(com.dan.weather.Weather.Var.WIND, com.dan.geiranger.wiring.Beaufort.mid(Integer.parseInt(wind.trim())));
        if ("grund".equalsIgnoreCase(wa)) r.showBed = true;
        if ("ohneSpiegel".equalsIgnoreCase(wa)) r.showMirror = false;
        com.dan.geiranger.gl.Wasser.wind(r.wasser.ocean, wiring.wind.speed(), wiring.wind.fromDeg());
        r.wasser.ocean.rebuildNow();
        String an = System.getenv("ANSICHT");
        if (an == null) an = System.getProperty("ansicht");   // Phase 2: aus Ant ohne Umgebungsvariable
        if ("oben".equals(an)) { r.camera.lookAt(-4100, 0, -3300, 0, 89, 21000); r.camera.snap(); }
        else if (an != null && Ansichten.byName(an) != null) r.placeView(Ansichten.byName(an));
        String rich = env("RICHTUNG", "kamera.richtung");   // Phase 9: an der Ansicht nur die Blickrichtung ändern (Grad)
        String neig = env("NEIGUNG", "kamera.neigung");   // Phase 10: dazu die Neigung (Grad, nach unten positiv)
        if (rich != null || neig != null)
            r.camera.place(r.camera.fx, r.camera.fy, r.camera.fz, rich != null ? Double.parseDouble(rich.trim()) : r.camera.fyaw,
                    neig != null ? Double.parseDouble(neig.trim()) : r.camera.fpitch);
        String kam = env("KAMERA", "kamera");   // x,z,Richtung,Neigung,Höhe über Grund (frei)
        if ("aus".equalsIgnoreCase(env("FELS", "fels"))) r.showCliff = false;   // Phase 5: Fels aus cliff ab-, Platzhalter an
        String abf = env("ABFLUSS", "abfluss");   // Phase 8: Abfluss von Hand, Anteil vom Juni (1,6 = Hochwasser)
        if (abf != null) r.abfluss().hand = Double.parseDouble(abf.trim().replace(',', '.'));
        if ("schaetzen".equalsIgnoreCase(env("BELICHTUNG", "belichtung"))) r.bildMessung = false;   // Phase 8: wie bis Phase 7
        // Phase 12: SCHIFF bzw. -Dschiff=Name,Abstand,Peilung,Höhe: die Kamera neben ein Schiff (Peilung in Grad zum Kurs,
        // 0 voraus, 90 Steuerbord, 180 achteraus; Höhe über dem Spiegel), Blick auf das Schiff
        String sch = env("SCHIFF", "schiff");
        if (sch != null) {
            String[] k = sch.split(",");
            com.dan.geiranger.scene.Flotte fl = r.flotte.flotte;
            fl.stelle(clock.epochMillis() / 1000.0);
            String n = k[0].trim().toLowerCase(Locale.ROOT).replace("ø", "o");
            com.dan.geiranger.scene.Flotte.Schiff s = null;
            for (com.dan.geiranger.scene.Flotte.Schiff x : fl.schiffe)
                if (x.da && x.name.toLowerCase(Locale.ROOT).replace("ø", "o").contains(n)) { s = x; break; }
            if (s == null) System.out.println("Schiff " + k[0] + ": nicht in der Szene · " + fl.describe());
            else {
                double dd = Double.parseDouble(k[1]), pe = Math.toRadians(s.kurs + Double.parseDouble(k[2])), hh = Double.parseDouble(k[3]);
                double ex = s.x + Math.sin(pe) * dd, ez = s.z - Math.cos(pe) * dd, ey = r.waterLevel() + hh;
                double head = (Math.toDegrees(Math.atan2(s.x - ex, -(s.z - ez))) + 360) % 360;
                r.camera.place(ex, ey, ez, head, Math.toDegrees(Math.atan2(ey - (r.waterLevel() + 6), dd)));
                System.out.println("Kamera am Schiff: " + s.name + " " + s.zustand);
            }
        }
        // Phase 15: MITFAHRT bzw. -Dmitfahrt=Blick: auf der Fähre mitfahren, Blick in Grad zur Seite (negativ nach Backbord)
        String mf = env("MITFAHRT", "mitfahrt");
        if (mf != null) {
            r.flotte.flotte.stelle(clock.epochMillis() / 1000.0);
            r.camera.step(0);   // Auge an die Ansicht (die nächste Fähre zählt)
            System.out.println(r.mitfahren(Double.parseDouble(mf.trim())) ? "Mitfahrt auf der Fähre" : "Mitfahrt: keine Fähre in der Szene · " + r.flotte.flotte.describe());
        }
        if (kam != null) {
            String[] k = kam.split(",");
            double kx = Double.parseDouble(k[0]), kz = Double.parseDouble(k[1]);
            r.camera.place(kx, r.standHeight(kx, kz) + Double.parseDouble(k[4]), kz, Double.parseDouble(k[2]), Double.parseDouble(k[3]));
        }
        d.addGLEventListener(r);
        d.display();
        if (r.error() != null) { System.err.println("FEHLER: " + r.error()); System.exit(2); }
        // Phase 5: die Wandkarte entsteht im Hintergrund; gemessen wird erst mit Fels; seit Phase 7 auch mit den Fällen,
        // wenn sie zu Abfluss, Wind und Temperatur passen (der Hintergrund rechnet sie nach dem Start neu)
        for (long t0 = System.currentTimeMillis(); (r.fels.map() == null || r.fels.faelle() == null) && System.currentTimeMillis() - t0 < 60_000; ) Thread.sleep(50);
        for (int i = 0; i < 10; i++) { r.step(1 / 60.0); d.display(); }
        for (long t0 = System.currentTimeMillis(); !r.faelle.idle() && System.currentTimeMillis() - t0 < 30_000; ) { r.step(1 / 60.0); d.display(); Thread.sleep(20); }
        for (int i = 0; i < 70; i++) { r.step(1 / 60.0); d.display(); }   // Selbsttests von Fels und Fällen laufen ab Bild 50 und 70
        // Phase 13: die Gäste aus Avatar.jar laden und backen im Faden „Menschen“; dann stellen sie sich auf. MENSCHEN bzw.
        // -Dmenschen=plattform|gaeste|bus|skagefla stellt die Kamera zu den Menschen (Lagen aus der Szene).
        for (long t0 = System.currentTimeMillis(); !r.leute.leute.bereit() && !r.leute.leute.avatarStand.startsWith("aus") && System.currentTimeMillis() - t0 < 90_000; ) Thread.sleep(50);
        for (int i = 0; i < 120; i++) { r.step(1 / 60.0); d.display(); }
        String me = env("MENSCHEN", "menschen");
        if (me != null) {
            menschenKamera(r, me.trim().toLowerCase(Locale.ROOT), clock.epochMillis() / 1000.0);
            for (int i = 0; i < 120; i++) { r.step(1 / 60.0); d.display(); }   // Nacharbeit 13: das Kielwasser rechnet nach dem Sprung nach
        }
        // Phase 16: REGIE bzw. -Dregie=Junitag|Ornesvingen|Faehre: ein Regiebuch mit Blatt, KINO=1 bzw. -Dkino=1 im Kinomodus
        String rb = env("REGIE", "regie");
        if (rb != null) { regie(r, d, rb.trim(), png, "1".equals(env("KINO", "kino"))); d.destroy(); System.exit(0); }
        // Phase 9: BUS=1 bzw. -Dbus=1: den Verkehr laufen lassen, bis ein Bus eine Kehre hat und vor ihr jemand wartet,
        // dann von außen und oben auf diese Kehre schauen (Abnahme: der Bus wartet nicht, der Gegenverkehr wartet)
        if ("1".equals(env("BUS", "bus")) && r.strasse.present()) {
            com.dan.geiranger.gl.Strasse st = r.strasse;
            double[] hit = null;
            double t = clock.t();
            for (int i = 0; i < 36_000 && hit == null; i++) {   // höchstens eine Stunde Verkehr
                st.rechne(r.camera.eye.clone(), (float) (t + i * 0.1), 0.1f, com.dan.geiranger.gl.Strasse.verkehrNachZeit(clock.hour()));
                hit = st.begegnung.busBegegnung(st.roads.traffic);
            }
            if (hit != null) {
                com.dan.geiranger.scene.Ornevegen.Kehre k = st.ornevegen.kehren.get((int) hit[0]);
                double[] mu = com.dan.geiranger.scene.Ornevegen.mitten().get((int) hit[0]);
                double[] c = com.dan.geiranger.scene.Site.fromUtm(mu[0], mu[1]);
                double dx = k.x() - c[0], dz = k.z() - c[1], l = Math.max(1e-6, Math.hypot(dx, dz));
                double ex = k.x() + dx / l * 45, ez = k.z() + dz / l * 45, ey = Math.max(k.y() + 70, r.groundHeight(k.x() + dx / l * 45, k.z() + dz / l * 45) + 40);
                double head = Math.toDegrees(Math.atan2(k.x() - ex, -(k.z() - ez)));
                r.camera.place(ex, ey, ez, (head + 360) % 360, Math.toDegrees(Math.atan2(ey - k.y(), 45)));
                System.out.println("Begegnung in Kehre " + k.nr() + " nach " + String.format(Locale.GERMAN, "%.0f s", 0.0) + " · " + st.begegnung.describe());
            } else System.out.println("Begegnung: in einer Stunde Verkehr keine gefunden");
            for (int i = 0; i < 20; i++) { r.step(1 / 60.0); d.display(); }
        }
        double best = Double.MAX_VALUE, sum = 0;
        for (int i = 0; i < frames; i++) {
            long t0 = System.nanoTime();
            r.step(1 / 60.0);
            d.display();
            d.getContext().makeCurrent();
            d.getGL().glFinish();
            d.getContext().release();
            double ms = (System.nanoTime() - t0) / 1e6;
            best = Math.min(best, ms);
            sum += ms;
        }
        System.out.println(r.glInfo());
        System.out.println(r.compute() == null ? "Compute-Test: nicht gelaufen" : r.compute().line());
        System.out.printf(Locale.GERMAN, "%d×%d %s, %s: bestes Bild %.2f ms, Mittel %.2f ms (%.0f Bilder/s)%n", w, h, q.label, clock.label(), best, sum / frames, 1000 / (sum / frames));
        System.out.println("Durchgänge (ms): " + r.passTimes());
        for (String l : r.hudLines()) System.out.println("  " + l);
        if (png != null) {
            File dir = png.getAbsoluteFile().getParentFile();
            if (dir != null) dir.mkdirs();
            final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
            d.invoke(true, x -> { img[0] = r.grab(x); return true; });
            javax.imageio.ImageIO.write(img[0], "png", png);
            System.out.println("Bild: " + png.getPath());
        }
        d.destroy();
        System.exit(0);
    }

    /**
     * Phase 16: ein Regiebuch ohne Fenster in Schritten von 1/30 s; jedes zehnte Bild und jedes Foto wird gezeichnet. Am
     * Ende liegt das Blatt in docs/status (Regiebuch_….png) und als Kopie unter dem Namen des PNG. Mit Kino die
     * Kinobilder (Titel, Aufblende, jedes Foto, Abspann) als png_kino_01.png …
     */
    static void regie(Renderer r, GLOffscreenAutoDrawable d, String name, File png, boolean kino) throws Exception {
        com.dan.geiranger.scene.Regie.Book b = com.dan.geiranger.scene.Regie.byName(name);
        if (b == null) { System.err.println("FEHLER: kein Regiebuch „" + name + "“"); return; }
        if (png != null) r.photoDir = png.getAbsoluteFile().getParentFile();
        java.util.List<java.awt.image.BufferedImage> kinoBilder = new java.util.ArrayList<>();
        long c0 = System.currentTimeMillis();
        if (kino) r.kinoStart(b); else r.regieStart(b);
        r.frozen = true;   // die Szene läuft nur über r.step(dt) in festen Schritten
        double dt = 1 / 30.0, sek = 0, abspannSeit = -1;
        long n = 0;
        int fotos = 0;
        while (r.regie.running() || r.regieWantsFrame() || (kino && r.kino.laeuft())) {
            sek += dt;
            r.step(dt);
            boolean grab = false;
            if (kino) {
                boolean foto = r.regieWantsFrame() && !r.kino.schnittFaellig();
                if (r.kino.lage() == com.dan.kino.Kino.Lage.ABSPANN) abspannSeit = abspannSeit < 0 ? 0 : abspannSeit + dt;
                grab = Math.abs(sek - 3) < dt / 2 || Math.abs(sek - 8.5) < dt / 2 || Math.abs(abspannSeit - 6) < dt / 2 || (foto && fotos++ < 99);
            }
            if (++n % 10 == 0 || r.regieWantsFrame() || grab) {
                if (grab) r.grabRequest = kinoBilder::add;
                d.display();
            }
            if (n % 900 == 0) System.out.println("  " + r.regieLine() + " · " + clock(r));
            if (sek > 3600) { System.err.println("FEHLER: Regiebuch läuft über eine Stunde"); break; }
        }
        d.display();
        System.out.printf(Locale.GERMAN, "Regiebuch „%s“: %d Fotos, %.0f s Film (%.0f s gewartet), %.0f s gerechnet · Blatt: %s%n", b.name(), r.fotos(), sek, r.regie.waitedTotal(),
                (System.currentTimeMillis() - c0) / 1000.0, r.lastSheet);
        for (String l : r.hudLines()) System.out.println("  " + l);
        if (png != null && r.lastSheet != null) {
            java.nio.file.Files.copy(r.lastSheet.toPath(), png.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Bild: " + png.getPath());
        }
        if (kino && png != null) {
            File dir = png.getAbsoluteFile().getParentFile();
            String stem = png.getName().replaceFirst("\\.png$", "");
            for (int i = 0; i < kinoBilder.size(); i++)
                javax.imageio.ImageIO.write(kinoBilder.get(i), "png", new File(dir, String.format(Locale.ROOT, "%s_kino_%02d.png", stem, i + 1)));
            System.out.println(kinoBilder.size() + " Kinobilder nach " + dir);
        }
    }

    static String clock(Renderer r) { return r.clock.label(); }
}
