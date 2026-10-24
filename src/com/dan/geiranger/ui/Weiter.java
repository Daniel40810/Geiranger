package com.dan.geiranger.ui;

import com.dan.geiranger.data.Daten;
import com.dan.geiranger.data.Zustand;
import com.dan.geiranger.gl.Quality;
import com.dan.geiranger.gl.Renderer;
import com.dan.geiranger.scene.Regie;
import com.dan.store.Felder;
import com.dan.store.Protokoll;

import javax.swing.Timer;
import java.util.List;
import java.util.Map;

/**
 * „Weiter wie zuletzt“ (F37 A): einen gesicherten Zustand herstellen, sobald die Szene steht (höchstens 60 s warten).
 * Uhr mit Datum und Zeitraffer, Wetter, Kamera, Ebenen, Qualität und Ton kommen zurück; ein laufendes Regiebuch läuft
 * an seiner Stelle weiter (im Kino, wenn es dort lief).
 */
public final class Weiter {
    private Weiter() { }

    /** Bereit, wenn die Szene ein erstes Bild hat. */
    static boolean bereit(Renderer r) { return r.frames() > 2; }

    /** Wartet höchstens 60 s auf die Szene, dann stellt es her; danach läuft {@code danach} (darf null sein). */
    public static void anwenden(Renderer r, Daten.Sitzung s, Runnable danach) {
        if (s == null || s.zustand() == null) return;
        long bis = System.currentTimeMillis() + 60_000;
        Timer t = new Timer(250, null);
        t.addActionListener(e -> {
            if (!bereit(r) && System.currentTimeMillis() < bis) return;
            t.stop();
            jetzt(r, s);
            if (danach != null) danach.run();
        });
        t.start();
    }

    /** Ein Regiebuch im Kino, sobald die Szene steht (höchstens 60 s warten). */
    public static void kino(Renderer r, Regie.Book b) {
        long bis = System.currentTimeMillis() + 60_000;
        Timer t = new Timer(250, null);
        t.addActionListener(e -> {
            if (!bereit(r) && System.currentTimeMillis() < bis) return;
            t.stop();
            r.kinoStart(b);
        });
        t.start();
    }

    /** Sofort herstellen (die Szene steht schon); gibt zurück, was nicht herzustellen war. */
    public static List<String> jetzt(Renderer r, Daten.Sitzung s) {
        Map<String, String> z = Felder.ausText(s.zustand());
        Quality q = Zustand.qualitaet(z);
        if (q != null && q != r.quality()) r.setQuality(q);
        if (z.containsKey("app.ton")) r.klang.ton(Boolean.parseBoolean(z.get("app.ton")));
        String name = z.get("regiebuch.name");
        Regie.Book b = name == null ? null : Regie.byName(name);
        if (b != null) {
            double sek = com.dan.store.Zahl.lies(z.get("regiebuch.sekunde"));
            r.regieWeiter(b, Double.isNaN(sek) ? 0 : sek, "true".equals(z.get("regiebuch.kino")));
            z.keySet().removeIf(k -> k.startsWith("kamera."));   // die Kamera führt das Regiebuch (Fahrt, Mitfahrt)
        }
        List<String> f = Zustand.anwenden(r, z);
        Protokoll.info("Sitzung", "Weiter wie zuletzt (Sitzung " + s.id() + "): " + z.size() + " Werte" + (b != null ? ", Regiebuch „" + b.name() + "“" : "")
                + (f.isEmpty() ? "" : ", nicht hergestellt: " + String.join(", ", f)));
        r.hudMeldung("Weiter wie zuletzt · " + z.getOrDefault("uhr.text", "") + (b != null ? " · „" + b.name() + "“ läuft weiter" : ""));
        return f;
    }
}
