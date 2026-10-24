package com.dan.geiranger.data;

import com.dan.geiranger.camera.Camera;
import com.dan.geiranger.gl.Quality;
import com.dan.geiranger.gl.Renderer;
import com.dan.store.Zahl;
import com.dan.weather.Scenario;
import com.dan.weather.Weather;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Der Zustand der Szene als Text (F37 A, „Weiter wie zuletzt“): Uhr mit Datum und Zeitraffer, Kamera, Wetter (Regie,
 * Lage, Wetter-Regiebuch mit Beginn, Handregler, Wolkenband von Hand), alle Ebenen, Abfluss von Hand, Belichtung, HUD,
 * Qualität und Ton, dazu das laufende Regiebuch mit Stelle und ob es im Kino lief. Dasselbe Format (Zeilen
 * {@code schluessel=wert}, com.dan.store.Felder) dient für gemerkte Ansichten und die Dateien ohne Datenbank.
 * <p>
 * Die Ebenen stehen als Liste von Schaltern ({@link #EBENEN}): so kommt nur zurück, was die Oberfläche auch schaltet.
 */
public final class Zustand {
    private Zustand() { }

    /** Ein Schalter: Name im Zustand, lesen, setzen. */
    record Schalter(String name, Supplier<Boolean> lesen, Consumer<Boolean> setzen) { }

    /** Ein Regler (Zahl): Name, lesen, setzen. */
    record Regler(String name, Supplier<Double> lesen, Consumer<Double> setzen) { }

    static List<Schalter> ebenen(Renderer r) {
        List<Schalter> l = new ArrayList<>();
        l.add(new Schalter("ebene.spiegelbild", () -> r.showMirror, v -> r.showMirror = v));
        l.add(new Schalter("ebene.grund", () -> r.showBed, r::setShowBed));
        l.add(new Schalter("ebene.wolken", () -> r.showClouds, v -> r.showClouds = v));
        l.add(new Schalter("ebene.regen", () -> r.showRain, v -> r.showRain = v));
        l.add(new Schalter("ebene.nebel", () -> r.showFog, v -> r.showFog = v));
        l.add(new Schalter("ebene.schatten", () -> r.schatten.on, v -> { r.schatten.on = v; r.schatten.invalidate(); }));
        l.add(new Schalter("ebene.fels", () -> r.showCliff, v -> r.showCliff = v));
        l.add(new Schalter("ebene.jahr", () -> r.showJahr, v -> r.showJahr = v));
        l.add(new Schalter("ebene.faelle", () -> r.faelle.on, v -> r.faelle.on = v));
        if (r.strasse.present()) l.add(new Schalter("ebene.verkehr", () -> r.strasse.roads.showTraffic, v -> r.strasse.roads.showTraffic = v));
        l.add(new Schalter("ebene.wald", () -> r.wald.showForest, v -> r.wald.showForest = v));
        l.add(new Schalter("ebene.boden", () -> r.wald.showGround, v -> r.wald.showGround = v));
        l.add(new Schalter("ebene.ort", () -> r.ort.show, v -> r.ort.show = v));
        l.add(new Schalter("ebene.schiffe", () -> r.flotte.show, v -> r.flotte.show = v));
        l.add(new Schalter("ebene.kielwasser", () -> r.flotte.kielwasser.on, v -> r.flotte.kielwasser.on = v));
        l.add(new Schalter("ebene.menschen", () -> r.leute.show, v -> { r.leute.show = v; r.leute.leute.show = v; }));
        l.add(new Schalter("ebene.marken", () -> r.showMarks, v -> r.showMarks = v));
        l.add(new Schalter("szene.bildmessung", () -> r.bildMessung, v -> { r.bildMessung = v; r.readapt(); }));
        l.add(new Schalter("szene.aufloesungSelbst", () -> r.autoScale, v -> r.autoScale = v));
        if (r.wiring != null) {
            l.add(new Schalter("ebene.fjordnebel", () -> r.wiring.fogOn, v -> r.wiring.fogOn = v));
            l.add(new Schalter("ebene.wolkenband", () -> r.wiring.bandOn, v -> r.wiring.bandOn = v));
        }
        return l;
    }

    static List<Regler> regler(Renderer r) {
        List<Regler> l = new ArrayList<>();
        l.add(new Regler("szene.belichtung", () -> r.exposureBias, v -> r.exposureBias = v));
        l.add(new Regler("szene.aufloesung", () -> r.renderScale, v -> { if (!r.autoScale) r.renderScale = Math.max(Renderer.MIN_SCALE, Math.min(1, v)); }));
        l.add(new Regler("abfluss.hand", () -> r.abfluss().hand, v -> r.abfluss().hand = v));
        l.add(new Regler("klang.lautstaerke", () -> r.klang.s.volume, v -> r.klang.s.volume = Math.max(0, Math.min(2, v))));
        if (r.wiring != null) l.add(new Regler("wetter.band", () -> r.wiring.bandHand, v -> r.wiring.bandHand = v));
        return l;
    }

    /** Erfasst den Zustand (aus dem Swing-Faden oder dem Faden der Daten; die Felder sind volatile). */
    public static LinkedHashMap<String, String> erfassen(Renderer r) {
        LinkedHashMap<String, String> z = new LinkedHashMap<>();
        z.put("app.version", Renderer.VERSION);
        z.put("app.qualitaet", r.quality().name());
        z.put("app.ton", Boolean.toString(r.klang.an));
        z.put("uhr.ms", Long.toString((long) Math.floor(r.clock.epochMillis())));
        z.put("uhr.text", Daten.text(r.clock.epochMillis()));
        z.put("uhr.lapse", Zahl.text(r.clock.lapse));
        Camera c = r.camera;
        z.put("kamera.modus", c.mode == Camera.Mode.KREISEN ? "KREISEN" : "FREI");
        z.put("kamera.kreisen", Boolean.toString(c.autoOrbit));
        double[] k = kamera(r);
        String[] n = {"x", "y", "z", "gier", "nick"};
        for (int i = 0; i < 5; i++) z.put("kamera." + n[i], Zahl.text(Math.round(k[i] * 1000) / 1000.0));
        z.put("kamera.ziel", Zahl.text(c.tx) + " " + Zahl.text(c.ty) + " " + Zahl.text(c.tz) + " " + Zahl.text(c.yaw) + " " + Zahl.text(c.pitch) + " " + Zahl.text(c.dist));
        z.put("szene.hud", !r.showHud ? "aus" : r.hudFull ? "voll" : "kurz");
        for (Schalter s : ebenen(r)) z.put(s.name(), Boolean.toString(s.lesen().get()));
        for (Regler g : regler(r)) z.put(g.name(), Zahl.text(Math.round(g.lesen().get() * 10000) / 10000.0));
        if (r.wiring != null) {
            Weather w = r.wiring.weather;
            Weather.Snapshot ws = w.snapshot();
            z.put("wetter.an", Boolean.toString(r.wiring.regie));
            z.put("wetter.lage", ws != null ? ws.phase().name() : "KLAR");
            Scenario sc = w.scenario();
            if (sc != null && ws != null && ws.step() >= 0) {
                z.put("wetter.text", sc.toText());
                z.put("wetter.beginn", Zahl.text(w.scenarioStart()));
            }
            double[] h = w.handValues();
            for (int i = 0; i < h.length; i++) if (!Double.isNaN(h[i])) z.put("wetter.hand." + Weather.Var.values()[i].name(), Zahl.text(h[i]));
        }
        var bk = r.regie.book();
        if (bk != null && r.regie.running()) {
            z.put("regiebuch.name", bk.name());
            z.put("regiebuch.sekunde", Zahl.text(Math.round(r.regie.time() * 100) / 100.0));
            z.put("regiebuch.kino", Boolean.toString(r.kino.laeuft()));
        }
        return z;
    }

    /** Die Spalten von GR_SITZUNG aus einem Zustand (für {@code Ablage.Sicherung}). */
    public static Map<String, String> spalten(Map<String, String> z) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(Speicher.SZENE_MS, z.getOrDefault("uhr.ms", ""));
        m.put(Speicher.ZEITRAFFER, z.getOrDefault("uhr.lapse", ""));
        m.put(Speicher.KAMERA, String.join(" ", z.getOrDefault("kamera.x", ""), z.getOrDefault("kamera.y", ""), z.getOrDefault("kamera.z", ""),
                z.getOrDefault("kamera.gier", ""), z.getOrDefault("kamera.nick", "")));
        m.put(Speicher.QUALITAET, z.getOrDefault("app.qualitaet", ""));
        m.put(Speicher.TON, z.getOrDefault("app.ton", ""));
        if (z.containsKey("regiebuch.name")) { m.put(Speicher.REGIEBUCH, z.get("regiebuch.name")); m.put(Speicher.REGIE_SEKUNDE, z.get("regiebuch.sekunde")); }
        return m;
    }

    /** Kamera für Ansichten: {x, y, z, Gier, Nick} aus dem laufenden Bild (Nick positiv nach unten). */
    public static double[] kamera(Renderer r) {
        Camera c = r.camera;
        double[] e = c.eye, f = c.fwd;
        double nick = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -f[1]))));
        return new double[]{e[0], e[1], e[2], c.heading(), nick};
    }

    public static Long szeneMs(Map<String, String> z) {
        double v = Zahl.lies(z.get("uhr.ms"));
        return Double.isNaN(v) ? null : (long) v;
    }

    public static Quality qualitaet(Map<String, String> z) {
        try { return Quality.valueOf(z.get("app.qualitaet")); } catch (RuntimeException e) { return null; }
    }

    /**
     * Stellt den Zustand her (aus dem Swing-Faden), ohne Qualität, Ton und Regiebuch (das macht {@code ui.Weiter}).
     * Gibt zurück, was nicht ging.
     */
    public static List<String> anwenden(Renderer r, Map<String, String> z) {
        List<String> fehler = new ArrayList<>();
        for (Schalter s : ebenen(r)) {
            String v = z.get(s.name());
            if (v == null) continue;
            try { s.setzen().accept(Boolean.parseBoolean(v)); } catch (RuntimeException e) { fehler.add(s.name()); }
        }
        Map<String, Regler> rg = new LinkedHashMap<>();
        for (Regler g : regler(r)) rg.put(g.name(), g);
        // erst die Auflösung selbst regeln, dann die Auflösung
        for (Regler g : rg.values()) {
            double v = Zahl.lies(z.get(g.name()));
            if (Double.isNaN(v)) continue;
            try { g.setzen().accept(v); } catch (RuntimeException e) { fehler.add(g.name()); }
        }
        String hud = z.get("szene.hud");
        if (hud != null) { r.showHud = !hud.equals("aus"); r.hudFull = hud.equals("voll"); }
        Long ms = szeneMs(z);
        if (ms != null) r.clock.setEpochMillis(ms);
        double lapse = Zahl.lies(z.get("uhr.lapse"));
        if (!Double.isNaN(lapse)) r.clock.lapse = lapse;
        Camera c = r.camera;
        if (z.containsKey("kamera.modus") || z.containsKey("kamera.x")) kamera(c, z, fehler);
        if (r.wiring != null) wetter(r, z, fehler);
        r.readapt();
        return fehler;
    }

    static void kamera(Camera c, Map<String, String> z, List<String> fehler) {
        c.stopFahrt();
        c.stopMitfahrt();
        String[] ziel = z.getOrDefault("kamera.ziel", "").split(" ");
        if ("KREISEN".equals(z.get("kamera.modus")) && ziel.length == 6) {
            c.mode = Camera.Mode.KREISEN;
            c.lookAt(Zahl.lies(ziel[0]), Zahl.lies(ziel[1]), Zahl.lies(ziel[2]), Zahl.lies(ziel[3]), Zahl.lies(ziel[4]), Zahl.lies(ziel[5]));
            c.autoOrbit = "true".equals(z.get("kamera.kreisen"));
            c.snap();
        } else {
            double x = Zahl.lies(z.get("kamera.x")), y = Zahl.lies(z.get("kamera.y")), zz = Zahl.lies(z.get("kamera.z"));
            if (!Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(zz)) {
                c.place(x, y, zz, Zahl.lies(z.get("kamera.gier")), Zahl.lies(z.get("kamera.nick")));
                c.snap();
            } else fehler.add("kamera");
        }
    }

    static void wetter(Renderer r, Map<String, String> z, List<String> fehler) {
        {
            Weather w = r.wiring.weather;
            String text = z.get("wetter.text");
            boolean gestartet = false;
            if (text != null) {
                try {
                    Scenario sc = Scenario.parse(text);
                    double b = Zahl.lies(z.get("wetter.beginn"));
                    r.wiring.regie = true;
                    w.start(sc, Double.isNaN(b) ? r.clock.epochMillis() / 1000.0 : b);
                    gestartet = true;
                } catch (RuntimeException ex) {
                    fehler.add("wetter.text: " + ex.getMessage());
                }
            }
            if (!gestartet) {
                Weather.Phase p;
                try { p = Weather.Phase.valueOf(z.getOrDefault("wetter.lage", "KLAR")); } catch (IllegalArgumentException ex) { p = Weather.Phase.KLAR; }
                r.wiring.startFree(p);
            }
            double band = r.wiring.bandHand;
            r.wiring.resumeRegie();
            r.wiring.bandHand = band;
            for (Weather.Var v : Weather.Var.values()) {
                double hv = Zahl.lies(z.get("wetter.hand." + v.name()));
                if (!Double.isNaN(hv)) r.wiring.hand(v, hv);
            }
            r.wiring.regie = !"false".equals(z.get("wetter.an"));
        }
    }
}
