package com.dan.geiranger.data;

import com.dan.geiranger.gl.Renderer;
import com.dan.geiranger.scene.Echo;
import com.dan.geiranger.scene.Faelle;
import com.dan.geiranger.scene.Flotte;
import com.dan.geiranger.scene.Klang;
import com.dan.geiranger.scene.Leute;
import com.dan.geiranger.scene.Regie;
import com.dan.geiranger.scene.SceneClock;
import com.dan.geiranger.wiring.Abfluss;
import com.dan.geiranger.wiring.Wiring;
import com.dan.store.Ablage;
import com.dan.store.Dienst;
import com.dan.store.Protokoll;
import com.dan.weather.Weather;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Die Chronik der Szene (Phase 17, F29 A): beobachtet die laufende Szene alle 0,5 s und schreibt über den gültigen
 * {@link Speicher} in die Datenbank oder in die Dateien. Das Bild wartet nie: beobachtet wird in einem eigenen Faden,
 * geschrieben in einem zweiten (der Reihe nach).
 * <ul>
 * <li><b>Stunden</b>: zu jeder vollen Szenenstunde das Wetter (Lage, Wind, Böen, Bedeckung, Wolken, Regen, Sicht,
 * Fjordnebel, Temperatur) und der Abfluss je Fall (Anteil am Juni, Regenspeicher, Temperatur am Fall, Eis).</li>
 * <li><b>Tage</b>: jeden Szenentag um 12 Uhr Schneegrenze, Schnee im Ort, auf 600 und 1 200 m, Laub, Herbst, Schnee in
 * den Kronen, Eis der Fälle und Luft im Ort.</li>
 * <li><b>Fahrten</b>: jede Fahrt der beiden Fähren und des Kreuzfahrtschiffs, ab und an (bei den Fähren nach dem
 * Fahrplan), der Weg als Linie, jedes Horn (beim Ablegen, an den Sieben Schwestern, beim Auslaufen) mit den Echos der
 * Wände, wie sie an Bord ankommen ({@link Echo#finde}). Die Lage rechnet eine eigene Flotte nach der Uhr in Schritten
 * von 20 s Szenenzeit; so fehlt auch im Zeitraffer keine Fahrt (bis zu zwei Tage Lücke, weiter ist ein Sprung).</li>
 * <li><b>Busse</b>: jede Ankunft des Ausflugsbusses an der Plattform mit der Größe der Gruppe (nach dem Busplan).</li>
 * <li><b>Regiebücher</b>: jeder Lauf mit Blatt (oder abgebrochen), ob im Kino und wie lange gewartet wurde.</li>
 * </ul>
 */
public final class Chronik {
    /** Größte Lücke der Uhr, die noch nachgetragen wird (ms, zwei Tage); weiter ist ein Sprung. */
    public static final double LUECKE_MS = 2 * 86_400_000.0;
    /** Schritt der eigenen Flotte (s Szenenzeit) und Abstand der Punkte des Wegs (m). */
    public static final double SCHRITT_S = 20, PUNKT_M = 50;
    /** Höchstens so viele Stunden je Beobachtung (bei 1 h/s fallen zwei an). */
    static final int STUNDEN_JE_BLICK = 48;

    private final Renderer r;
    private final Flotte probe;
    private final ScheduledExecutorService schauen;
    private final ExecutorService schreiben;
    private volatile boolean an = true;
    /** Zähler für Block DATEN und Prüfung. */
    public volatile int stunden, tage, fahrten, hoerner, busse, regielaeufe, fehler;
    public volatile String letzte = "";

    private double zuletztMs = Double.NaN, probeMs = Double.NaN;
    private final Spur[] spuren = new Spur[3];
    // Regie
    private Regie.Book buch;
    private long buchBeginn;
    private boolean buchKino;
    private File blattVorher;

    /** Eine Fahrt im Werden. */
    static final class Spur {
        boolean unterwegs;
        String schiff, richtung, alt = "";
        long abMs, planAn;
        final List<double[]> punkte = new ArrayList<>();
        final List<String> horn = new ArrayList<>();
        double[] hornLage;
        String hornWo;
        double minD = Double.MAX_VALUE, minX, minZ;
        long minMs;
        boolean schwesternGehupt, halb;
        int still;
    }

    public Chronik(Renderer r) { this(r, true); }

    /** faden false: ohne eigenen Beobachter (Prüfung ruft {@link #jetzt}). */
    public Chronik(Renderer r, boolean faden) {
        this.r = r;
        this.probe = new Flotte(r.terrain);
        schauen = faden ? Executors.newSingleThreadScheduledExecutor(x -> faden(x, "Geiranger-Chronik")) : null;
        schreiben = Executors.newSingleThreadExecutor(x -> faden(x, "Geiranger-Chronik-Schreiben"));
        if (schauen != null) schauen.scheduleWithFixedDelay(this::schauen, 500, 500, TimeUnit.MILLISECONDS);
    }

    private static Thread faden(Runnable x, String n) {
        Thread t = new Thread(x, n);
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    }

    /** Die Chronik schreibt (true) oder nicht; die Beobachtung läuft weiter. */
    public void schreiben(boolean ja) { an = ja; }
    public boolean schreibt() { return an; }

    /** Für Prüfungen: einmal beobachten (im Faden des Aufrufers) und warten, bis alles geschrieben ist. */
    public void jetzt() {
        schauen();
        warten();
    }

    /** Warten, bis die Schreibschlange leer ist (höchstens 60 s). */
    public void warten() {
        try { schreiben.submit(() -> { }).get(60, TimeUnit.SECONDS); } catch (Exception e) { /* weiter */ }
    }

    public void beenden() {
        if (schauen != null) schauen.shutdownNow();
        schreiben.shutdown();
        try { schreiben.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static Long sitzung() { long s = Dienst.sitzung(); return s > 0 ? s : null; }

    private interface Schreib { void tun(Speicher s) throws Ablage.Fehler; }

    private void spaeter(String was, Schreib w) {
        if (!an) return;
        schreiben.submit(() -> {
            try {
                w.tun(Datenseite.speicher());
                letzte = was;
            } catch (Ablage.Fehler | RuntimeException e) {
                fehler++;
                Protokoll.warnung("Chronik", was + ": " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------ beobachten

    synchronized void schauen() {
        try {
            double jetzt = r.clock.epochMillis();
            if (Double.isNaN(zuletztMs) || jetzt < zuletztMs || jetzt - zuletztMs > LUECKE_MS) {
                zuletztMs = jetzt;
                probeMs = jetzt;
                for (int i = 0; i < spuren.length; i++) spuren[i] = null;
            } else if (jetzt > zuletztMs) {
                stunden(zuletztMs, jetzt);
                tage(zuletztMs, jetzt);
                busse(zuletztMs, jetzt);
                schiffe(jetzt);
                zuletztMs = jetzt;
            }
            regie();
        } catch (RuntimeException e) {
            fehler++;
            Protokoll.fehler("Chronik", "Beobachten: " + e, e);
        }
    }

    /** Volle Stunden in (a, b]. */
    private void stunden(double a, double b) {
        long h0 = (long) Math.floor(a / 3_600_000.0) + 1, h1 = (long) Math.floor(b / 3_600_000.0);
        int n = 0;
        for (long h = Math.max(h0, h1 - STUNDEN_JE_BLICK + 1); h <= h1 && n < STUNDEN_JE_BLICK; h++, n++) stunde(h * 3_600_000L);
    }

    /** Die Werte einer Szenenstunde (das Wetter von jetzt, im Zeitraffer für alle Stunden dieses Blicks). */
    Daten.Stunde stundeJetzt(long ms) {
        Wiring w = r.wiring;
        Abfluss a = r.abfluss();
        StringBuilder q = new StringBuilder();
        java.util.List<String> namen = new java.util.ArrayList<>();
        for (Faelle.Eintrag e : r.faelle.eintraege()) namen.add(e.def.name());
        if (namen.isEmpty()) for (Faelle.Def d : Faelle.BENANNT) namen.add(d.name());   // die Fälle sind noch nicht gebaut (oder ohne Bild)
        for (String nm : namen) {
            double m3 = a.cubicMetres(nm);
            q.append(q.length() > 0 ? "; " : "").append(nm).append('=').append(com.dan.store.Zahl.text(Math.round(m3 * 1000) / 1000.0));
        }
        String lage = "KLAR";
        double wind = Double.NaN, von = Double.NaN, boee = Double.NaN, bed = Double.NaN, regen = Double.NaN, sicht = Double.NaN, nebel = Double.NaN, temp = Double.NaN;
        String wolken = null;
        if (w != null) {
            Weather.Snapshot s = w.weather.snapshot();
            lage = s != null ? s.phase().name() : w.regie ? "KLAR" : "VON_HAND";
            wind = w.wind.speed();
            von = w.wind.fromDeg();
            boee = w.gustNow;
            bed = w.oktas;
            wolken = w.cloudType.name();
            regen = w.rainRate;
            sicht = w.visibilityKm;
            nebel = w.fogAmount;
            temp = w.airTempC;
        }
        double eis = r.jahr.eis(ms / 1000.0);
        String abfluss = q.length() > 4000 ? q.substring(0, 3990) + " …" : q.toString();
        return new Daten.Stunde(ms, lage, wind, von, boee, bed, wolken, regen, sicht, nebel, temp, a.factor(), a.regen, a.amFall(), eis, abfluss);
    }

    private void stunde(long ms) {
        Daten.Stunde s = stundeJetzt(ms);
        stunden++;
        spaeter("Stunde " + Daten.text(ms), sp -> sp.stunde(s, sitzung()));
    }

    /** Jeden Tag um 12 Uhr Ortszeit in (a, b]. */
    private void tage(double a, double b) {
        LocalDate d0 = Daten.datum(a), d1 = Daten.datum(b);
        for (LocalDate d = d0; !d.isAfter(d1); d = d.plusDays(1)) {
            long mittag = d.atTime(12, 0).atZone(SceneClock.ZONE).toInstant().toEpochMilli();
            if (mittag > a && mittag <= b) tag(mittag);
        }
    }

    Daten.Tag tagJetzt(long ms) {
        var j = r.jahr;
        double g = j.schneegrenze();
        return new Daten.Tag(ms, Double.isNaN(g) ? null : g, j.schnee(0) * 100, j.schnee(600) * 100, j.schnee(1200) * 100, j.laub(), j.herbst(), j.kronen(300),
                j.eis(ms / 1000.0), r.wiring != null ? r.wiring.airTempC : j.luftOrt);
    }

    private void tag(long ms) {
        Daten.Tag t = tagJetzt(ms);
        tage++;
        spaeter("Tag " + Daten.tag(ms), sp -> sp.tag(t, sitzung()));
    }

    /** Die Ankünfte des Ausflugsbusses in (a, b]. */
    private void busse(double a, double b) {
        Leute le = r.leute.leute;
        LocalDate d0 = Daten.datum(a), d1 = Daten.datum(b);
        for (LocalDate d = d0; !d.isAfter(d1); d = d.plusDays(1)) {
            long tag0 = Daten.beginn(d);
            for (double h : Leute.busplan(d)) {
                long an = tag0 + Math.round(h * 3_600_000);
                if (an <= a || an > b) continue;
                double[] p = le.bucht == null ? null : le.strasse(le.bucht[0], le.bucht[1] * Leute.BUCHT);
                Daten.Busankunft ba = new Daten.Busankunft(an, an + Math.round(Leute.HALT * 1000), "Busbucht vor der Plattform", Leute.gruppe(h * 3600),
                        p == null ? Double.NaN : p[0], p == null ? Double.NaN : p[2]);
                busse++;
                spaeter("Bus " + Daten.text(an), sp -> sp.busankunft(ba, sitzung()));
            }
        }
    }

    // ------------------------------------------------------------------ Schiffe

    /** Die eigene Flotte von der letzten Probe bis jetzt in Schritten von {@link #SCHRITT_S}. */
    private void schiffe(double jetzt) {
        if (Double.isNaN(probeMs)) probeMs = jetzt;
        int n = 0;
        while (probeMs + SCHRITT_S * 1000 <= jetzt && n++ < 20_000) {
            probeMs += SCHRITT_S * 1000;
            probe.stelle(probeMs / 1000.0);
            for (int i = 0; i < 3 && i < probe.schiffe.size(); i++) probeSchiff(i, probe.schiffe.get(i), (long) probeMs);
        }
    }

    static boolean faehrt(Flotte.Schiff s) {
        return s.da && (s.fahrt > 0.05 || s.zustand.startsWith("legt ab") || s.zustand.startsWith("dreht"));
    }

    private void probeSchiff(int i, Flotte.Schiff s, long ms) {
        Spur sp = spuren[i];
        boolean f = faehrt(s);
        // beim ersten Blick schon unterwegs: diese Fahrt nicht halb aufschreiben, erst die nächste
        if (sp == null) { sp = new Spur(); sp.schiff = s.name; sp.alt = s.zustand; sp.halb = f; spuren[i] = sp; }
        if (sp.halb) { if (!f) sp.halb = false; sp.alt = s.zustand; return; }
        if (!sp.unterwegs && f) beginn(sp, s, ms);
        if (sp.unterwegs) {
            double[] l = sp.punkte.isEmpty() ? null : sp.punkte.get(sp.punkte.size() - 1);
            if (s.da && (l == null || Math.hypot(s.x - l[0], s.z - l[1]) >= PUNKT_M)) sp.punkte.add(new double[]{s.x, s.z});
            if (s.art == Flotte.Art.FAEHRE) {
                if (s.zustand.startsWith("legt ab") && !sp.alt.startsWith("legt ab")) horn(sp, s, ms, "beim Ablegen");
                double d = s.da ? Math.hypot(s.x - Flotte.SCHWESTERN_PASS[0], s.z - Flotte.SCHWESTERN_PASS[1]) : Double.MAX_VALUE;
                if (d < 600 && d < sp.minD) { sp.minD = d; sp.minX = s.x; sp.minZ = s.z; sp.minMs = ms; }
                else if (sp.minD < 600 && d > sp.minD + 30 && !sp.schwesternGehupt) {
                    sp.schwesternGehupt = true;
                    hornAn(sp, sp.minX, sp.minZ, sp.minMs, "an den Sieben Schwestern");
                }
            } else if (s.art == Flotte.Art.KREUZFAHRT && s.zustand.startsWith("dreht") && !sp.alt.startsWith("dreht")) horn(sp, s, ms, "beim Auslaufen");
            if (!s.da || !f) {
                sp.still++;
                if (!s.da || sp.still >= 3) ende(sp, ms);
            } else sp.still = 0;
        }
        sp.alt = s.zustand;
    }

    private void beginn(Spur sp, Flotte.Schiff s, long ms) {
        sp.unterwegs = true;
        sp.punkte.clear();
        sp.horn.clear();
        sp.hornLage = null;
        sp.hornWo = null;
        sp.minD = Double.MAX_VALUE;
        sp.schwesternGehupt = false;
        sp.still = 0;
        sp.abMs = ms;
        sp.planAn = -1;
        if (s.art == Flotte.Art.KREUZFAHRT) sp.richtung = s.zustand.startsWith("läuft ein") ? "EIN" : "AUS";
        else {
            sp.richtung = s.zustand.startsWith("legt ab") || s.zustand.startsWith("dreht") || s.zustand.contains("Hellesylt,") ? "NACH_HELLESYLT" : "NACH_GEIRANGER";
            // ab und an nach dem Fahrplan: die Abfahrt, in deren Fahrzeit die Szene gerade liegt
            ZonedDateTime z = Instant.ofEpochMilli(ms).atZone(SceneClock.ZONE);
            double sek = z.toLocalTime().toSecondOfDay();
            long tag0 = Daten.beginn(z.toLocalDate());
            for (double h : Flotte.ABFAHRTEN) {
                double t0 = h * 3600;
                if (sek >= t0 - 60 && sek <= t0 + Flotte.FAHRZEIT) { sp.abMs = tag0 + Math.round(t0 * 1000); sp.planAn = sp.abMs + Math.round(Flotte.FAHRZEIT * 1000); break; }
            }
        }
        sp.punkte.add(new double[]{s.x, s.z});
    }

    private void horn(Spur sp, Flotte.Schiff s, long ms, String wo) { hornAn(sp, s.x, s.z, ms, wo); }

    private void hornAn(Spur sp, double x, double z, long ms, String wo) {
        hoerner++;
        sp.horn.add(wo + " " + Daten.text(ms).substring(11, 16));
        // für die Echos zählt das Horn an den Schwestern, sonst das erste
        if (sp.hornLage == null || wo.contains("Schwestern")) { sp.hornLage = new double[]{x, z}; sp.hornWo = wo; }
    }

    private void ende(Spur sp, long ms) {
        sp.unterwegs = false;
        long an = sp.planAn > 0 && Math.abs(sp.planAn - ms) < 20 * 60_000 ? sp.planAn : ms;
        List<double[]> p = new ArrayList<>(sp.punkte);
        Daten.Geom weg = Startdaten.linie(p, 0.5);
        double[] h = sp.hornLage == null ? null : sp.hornLage.clone();
        String hornText = sp.horn.isEmpty() ? null : String.join("; ", sp.horn), wo = sp.hornWo;
        int n = sp.horn.size();
        String schiff = sp.schiff, richtung = sp.richtung;
        long ab = sp.abMs;
        fahrten++;
        spaeter(schiff + " " + Daten.text(ab), s -> {
            Integer echos = null;
            String echoText = null;
            Echo ec = r.klang.echo();
            if (h != null && ec != null) {
                double y = 12;
                List<Echo.Treffer> l = ec.finde(h[0], y, h[1], h[0], y, h[1], 220, Klang.ECHOS);
                echos = l.size();
                echoText = "Horn " + wo + ": " + Echo.describe(l, h[0], h[1]);
            }
            s.schiffsfahrt(new Daten.Schiffsfahrt(0, schiff, richtung, ab, an, weg, n, h, hornText, echos, echoText), sitzung());
        });
    }

    // ------------------------------------------------------------------ Regiebücher

    private void regie() {
        Regie.Book b = r.regie.running() ? r.regie.book() : null;
        if (b != buch) {
            if (buch != null) regieEnde(buch);
            buch = b;
            if (b != null) { buchBeginn = System.currentTimeMillis(); buchKino = r.kino.laeuft(); blattVorher = r.lastSheet; }
        } else if (b != null && r.kino.laeuft()) buchKino = true;
    }

    private void regieEnde(Regie.Book b) {
        File blatt = r.lastSheet;
        boolean neu = blatt != null && blatt != blattVorher && blatt.isFile();
        byte[] png = null;
        if (neu) try { png = java.nio.file.Files.readAllBytes(blatt.toPath()); } catch (Exception e) { /* ohne Blatt */ }
        Daten.Regielauf l = new Daten.Regielauf(b.name(), buchBeginn, System.currentTimeMillis(), neu, buchKino, r.fps(), r.gpuMs(), r.regie.waitedTotal(),
                b.fotos(), r.quality().name(), png, neu ? blatt.getPath() : null);
        regielaeufe++;
        spaeter("Regiebuch „" + b.name() + "“" + (neu ? " mit Blatt" : " abgebrochen"), s -> s.regielauf(l, sitzung()));
    }

    /** „Chronik: 24 Stunden, 1 Tag, 4 Fahrten (6 Hörner), 3 Busse, 1 Regiebuch · zuletzt …“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Chronik%s: %d Stunden, %d Tage, %d Fahrten (%d Hörner), %d Busse, %d Regiebücher%s%s",
                an ? "" : " (angehalten)", stunden, tage, fahrten, hoerner, busse, regielaeufe, letzte.isEmpty() ? "" : " · zuletzt " + letzte,
                fehler > 0 ? " · " + fehler + " Fehler (Protokoll)" : "");
    }
}
