package com.dan.geiranger.tools;

import com.dan.geiranger.data.Chronik;
import com.dan.geiranger.data.DateiSpeicher;
import com.dan.geiranger.data.Datenseite;
import com.dan.geiranger.data.Laden;
import com.dan.geiranger.data.Tageskarte;
import com.dan.geiranger.gl.Renderer;
import com.dan.geiranger.scene.SceneClock;
import com.dan.geiranger.scene.Terrain;
import com.dan.geiranger.wiring.Wiring;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;

/**
 * Statusbild Phase 17 ohne Fenster und ohne Datenbank: ein Szenentag (Standard 21.6.2026, 7 bis 21 Uhr) läuft in
 * Stundenschritten durch die Chronik in einen frischen Ordner, dann zeichnet die {@link Tageskarte} daraus das Bild.
 * Aufruf: {@code Tageslauf <tag> <png>}.
 */
public final class Tageslauf {
    private Tageslauf() { }

    public static void main(String[] a) throws Exception {
        LocalDate tag = a.length > 0 ? LocalDate.parse(a[0]) : LocalDate.of(2026, 6, 21);
        File png = new File(a.length > 1 ? a[1] : "docs/status/Status_Phase17.png");
        long t0 = System.nanoTime();
        Terrain t = Terrain.load(Terrain.defaultDir());
        File tmp = Files.createTempDirectory("geiranger-tageslauf").toFile();
        DateiSpeicher ds = new DateiSpeicher(tmp);
        Datenseite.setzen(ds);
        Laden.laden(ds, tag.getYear());
        SceneClock c = new SceneClock(tag, 7.0);
        Wiring w = new Wiring(c);
        w.startFree(com.dan.weather.Weather.Phase.KLAR);
        Renderer r = new Renderer(c, w, t);
        r.klang.echoBereit(90_000);
        Chronik ch = new Chronik(r, false);
        ds.sitzungBeginnen(Renderer.VERSION, "Tageslauf", "MITTEL");
        for (double h = 7; h <= 21.001; h += 0.5) {
            c.set(tag, h);
            w.feedWeather(1.0);
            ch.jetzt();
        }
        ch.beenden();
        Tageskarte.Ergebnis e = Tageskarte.zeichnen(ds, t, tag, tmp, "Chronik ohne Datenbank · tools.Tageslauf · " + com.dan.geiranger.gl.Renderer.VERSION);
        png.getParentFile().mkdirs();
        Files.copy(e.datei().toPath(), png.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        System.out.printf(java.util.Locale.GERMAN, "%s: %d Fahrten, %d Hörner, %d Busse, %d Stunden (%.1f s)%n", png, e.fahrten(), e.hoerner(), e.busse(), e.stunden(),
                (System.nanoTime() - t0) / 1e9);
    }
}
