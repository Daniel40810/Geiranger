package com.dan.geiranger.scene;

import com.dan.weather.Scenario;
import com.dan.weather.Weather;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Phase 16: die Regiebücher als Text mit Blatt (aus Rungholt, dort aus Glacier). Die drei mitgelieferten stehen in
 * {@code regie/*.txt} neben dieser Klasse. Ein Regiebuch ist eine Folge von Stichworten in echten Sekunden seit dem
 * Start: Datum und Uhrzeit, Zeitraffer, Wetter, Ansichten, Fahrten und Mitfahrten, Fotos und am Ende ein Blatt mit allen
 * Fotos in {@code docs/status}. Was ein Stichwort tut, führt die App über {@link Ctx} aus; so läuft dasselbe Regiebuch
 * auch ohne Bild (Prüfung).
 * <p>
 * {@code warte} hält die Uhr des Regiebuchs an, bis die Szene so weit ist: eine Uhrzeit, die Fähre ist im Fjord, sie
 * nähert sich den Sieben Schwestern, sie ist an ihnen vorbei, sie hat angelegt, eine Fahrt ist zu Ende. Ein
 * {@code warte} ist immer ein eigenes Stichwort; was danach in derselben Sekunde steht, kommt, sobald die Bedingung
 * erfüllt ist. Nach {@link #WARTE_HOECHSTENS} Sekunden geht es mit einer Meldung weiter.
 * <ul>
 * <li><b>Ein Junitag</b> (21.6.2026, 4 Uhr bis Mitternacht, Zeitraffer 240, das Wetter „Ein Junitag“): Plattform, vom
 * Wasser, die Schwestern, Friaren, die Fähre legt ab, der Storfossen, Skageflå, das Vogelauge, Busse an der Plattform,
 * das Abendlicht, die helle Nacht. Vierzehn Fotos.</li>
 * <li><b>Fahrt über die Ørnesvingen</b> (21.6.2026, 10:30): im Bus vom Ufer die elf Kehren hinauf bis zur Plattform.</li>
 * <li><b>Mit der Fähre von Hellesylt</b> (21.6.2026, die MF Bolsøy ab 8:30): an Bord vom Westrand an den Sieben
 * Schwestern vorbei (das Horn mit seinem Echo, Zeitraffer 1) bis zum Anleger.</li>
 * </ul>
 * Gestaltet [G]: Abläufe, Zeiten, Blickpunkte.
 */
public final class Regie {

    /** Was ein Regiebuch braucht; die App führt es aus (ohne Bild: die Prüfung). */
    public interface Ctx {
        void time(LocalDate date, double hour);
        void lapse(double x);
        /** Ein Wetter-Regiebuch: aus dem Kopf des Regiebuchs oder nach Namen aus dem Klima „Geiranger“. */
        void weather(Scenario s);
        /** Freie Regie ab einer Lage, dazu Wind von Hand (Bft, woher), NaN = von der Regie. */
        void weatherFree(Weather.Phase p, double bft, double fromDeg);
        void view(String name);
        /** Eine Kamerafahrt nach Namen; Dauer in echten Sekunden (NaN = ihre eigene). */
        void fahrt(String name, double dauer);
        /** An Bord eines Schiffs mitfahren (Name oder Teil davon), Blick zur Seite (Grad, negativ nach Backbord). */
        void mitfahrt(String schiff, double blick);
        /** Ob die Szene so weit ist (für {@code warte}). */
        boolean ready(Warte w);
        void capture(String caption);
        void sheet(String name);
        void say(String text);
    }

    /** Worauf {@code warte} wartet: eine Uhrzeit (Stunden seit 1970) oder ein Ereignis der Szene, ggf. mit Name. */
    public record Warte(String was, double hours, String arg) {
        @Override public String toString() {
            if (was.equals("uhr")) return "Uhr " + java.time.Instant.ofEpochMilli((long) (hours * 3_600_000)).atZone(SceneClock.ZONE).toLocalTime();
            return switch (was) {
                case "faehre" -> "die Fähre " + arg;
                case "schwestern" -> arg + " vor den Schwestern";
                case "vorbei" -> arg + " an den Schwestern vorbei";
                case "angelegt" -> arg + " am Anleger";
                case "fahrt" -> "das Ende der Fahrt";
                default -> was;
            };
        }
    }

    /** Ein Stichwort: Sekunde (echte Zeit seit dem Start), Text, Handlung; bei {@code warte} die Bedingung. */
    public record Cue(double t, String text, Consumer<Ctx> action, Warte warte) { }

    /** Ein Regiebuch: Name, Beschreibung, Länge (s, ohne Wartezeit), Stichworte, Text, Zeilen, Wetter aus dem Kopf (oder null). */
    public record Book(String name, String about, double length, List<Cue> cues, String text, List<Zeile> zeilen, Scenario weather) {
        @Override public String toString() { return name; }
        /** Wie viele Fotos das Regiebuch macht. */
        public int fotos() { return (int) zeilen.stream().filter(z -> z.befehl().equals("foto")).count(); }
    }

    /** Länger wartet ein {@code warte} nicht (echte Sekunden). */
    public static final double WARTE_HOECHSTENS = 600;

    // ------------------------------------------------------------------ Ablauf

    /** Bekommt den Text jedes Stichworts (der Kinomodus macht Untertitel daraus); null = niemand. */
    public volatile Consumer<String> beiText;

    private volatile Book book;
    private double t, waited, waitedTotal;
    private int next;
    private volatile String status = "";
    private int fired;

    /** Regiebuch beginnen (das erste Stichwort sofort). */
    public void start(Book b, Ctx c) {
        book = b;
        t = 0;
        next = 0;
        fired = 0;
        waited = waitedTotal = 0;
        status = b.name() + " beginnt";
        step(0, c);
    }

    public void stop() {
        Book b = book;
        book = null;
        if (b != null) status = b.name() + " angehalten";
    }

    public boolean running() { return book != null; }

    public Book book() { return book; }

    /** Uhr des Regiebuchs (s, steht beim Warten). */
    public double time() { return t; }

    public int fired() { return fired; }

    /** Wie lange schon gewartet wurde (s, alle {@code warte} zusammen). */
    public double waitedTotal() { return waitedTotal; }

    /** Das {@code warte}, an dem das Regiebuch gerade steht, oder null. */
    public Warte waiting() {
        Book b = book;
        if (b == null || next >= b.cues().size()) return null;
        Cue q = b.cues().get(next);
        return q.warte() != null && q.t() <= t ? q.warte() : null;
    }

    /** Um dt echte Sekunden weiter, fällige Stichworte ausführen; an einem {@code warte} steht die Uhr. */
    public void step(double dt, Ctx c) {
        Book b = book;
        if (b == null) return;
        double d = Math.max(0, dt);
        if (next < b.cues().size() && b.cues().get(next).warte() != null && b.cues().get(next).t() <= t) {
            waited += d;
            waitedTotal += d;
        } else t += d;
        while (next < b.cues().size() && b.cues().get(next).t() <= t) {
            Cue q = b.cues().get(next);
            if (q.warte() != null) {
                if (!c.ready(q.warte()) && waited < WARTE_HOECHSTENS) {
                    t = q.t();
                    status = String.format(Locale.GERMAN, "%s · wartet auf %s", b.name(), q.warte());
                    return;
                }
                status = waited >= WARTE_HOECHSTENS ? b.name() + " · " + q.warte() + " kam nicht, weiter" : b.name() + " · " + q.warte() + ": weiter";
                waited = 0;
                next++;
                fired++;
                if (!q.text().isEmpty()) {
                    Consumer<String> h = beiText;
                    if (h != null) h.accept(q.text());
                }
                continue;
            }
            next++;
            if (!q.text().isEmpty()) {
                status = String.format(Locale.GERMAN, "%s · %s", b.name(), q.text());
                Consumer<String> h = beiText;
                if (h != null) h.accept(q.text());   // Untertitel im Kino
            }
            try { q.action().accept(c); } catch (RuntimeException e) { status = b.name() + " · Fehler bei „" + q.text() + "“: " + e.getMessage(); }
            fired++;
        }
        if (t >= b.length() && next >= b.cues().size()) {
            book = null;
            status = b.name() + " ist zu Ende (" + fired + " Stichworte)";
        }
    }

    /**
     * Phase 17 („Weiter wie zuletzt“): das laufende Regiebuch bis zur Sekunde {@code bis} vorspulen. Die Stichworte davor
     * wirken (Datum, Uhr, Zeitraffer, Wetter, Ansicht, Fahrt, Mitfahrt), Fotos, Blatt und Texte nicht, {@code warte} gilt
     * als erfüllt; danach läuft es von dort weiter.
     */
    public void spulen(double bis, Ctx c) {
        Book b = book;
        if (b == null || bis <= t) return;
        Ctx leise = new Ctx() {
            @Override public void time(LocalDate date, double hour) { c.time(date, hour); }
            @Override public void lapse(double x) { c.lapse(x); }
            @Override public void weather(Scenario s) { c.weather(s); }
            @Override public void weatherFree(Weather.Phase p, double bft, double fromDeg) { c.weatherFree(p, bft, fromDeg); }
            @Override public void view(String name) { c.view(name); }
            @Override public void fahrt(String name, double dauer) { c.fahrt(name, dauer); }
            @Override public void mitfahrt(String schiff, double blick) { c.mitfahrt(schiff, blick); }
            @Override public boolean ready(Warte w) { return true; }
            @Override public void capture(String caption) { }
            @Override public void sheet(String name) { }
            @Override public void say(String text) { }
        };
        double ziel = Math.min(bis, b.length() - 0.5);
        while (next < b.cues().size() && b.cues().get(next).t() < ziel) {
            Cue q = b.cues().get(next++);
            fired++;
            if (q.warte() != null) continue;
            try { q.action().accept(leise); } catch (RuntimeException e) { status = b.name() + " · beim Vorspulen: " + e.getMessage(); }
        }
        t = Math.max(t, ziel);
        waited = 0;
        status = String.format(Locale.GERMAN, "%s · weiter ab %s", b.name(), zeit(t));
    }

    /** „Ein Junitag · 9 Uhr: … · 1:10 von 5:33“ */
    public String describe() {
        Book b = book;
        if (b == null) return status.isEmpty() ? "kein Regiebuch" : status;
        return String.format(Locale.GERMAN, "%s · %d:%02d von %d:%02d", status, (int) t / 60, (int) t % 60, (int) b.length() / 60, (int) b.length() % 60);
    }

    // ------------------------------------------------------------------ die Regiebücher (als Text)

    /** Die drei mitgelieferten Regiebücher aus den Textdateien neben dieser Klasse (regie/*.txt). */
    public static final String[] DATEIEN = {"junitag.txt", "ornesvingen.txt", "faehre.txt"};

    public static List<Book> all() { List<Book> d = ausDaten; return d != null && !d.isEmpty() ? d : mitgeliefert(); }

    /** Die drei mitgelieferten Regiebücher aus den Textdateien. */
    public static List<Book> mitgeliefert() { return List.of(junitag(), ornesvingen(), faehre()); }

    /** Phase 17 (F26 A, F33 A): die Regiebücher aus GR_REGIEBUCH oder daten/regiebuecher, mit den eigenen ({@code data.Laden}); null: die Dateien. */
    public static volatile List<Book> ausDaten;

    /** Nach Name, auch ohne Umlaute und in Teilen („Junitag“, „Ornesvingen“, „Faehre“). */
    public static Book byName(String n) {
        String k = plain(n);
        for (Book b : all()) if (plain(b.name()).equals(k)) return b;
        for (Book b : all()) if (plain(b.name()).contains(k)) return b;
        return null;
    }

    static String plain(String s) {
        return s.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss").replace("ø", "o").replace("å", "a")
                .replace("æ", "ae").replace(" ", "").replace("„", "").replace("“", "");
    }

    public static Book junitag() { return mitgeliefert(DATEIEN[0]); }

    public static Book ornesvingen() { return mitgeliefert(DATEIEN[1]); }

    public static Book faehre() { return mitgeliefert(DATEIEN[2]); }

    /** Der Text eines mitgelieferten Regiebuchs. */
    public static String text(String datei) {
        try (java.io.InputStream in = Regie.class.getResourceAsStream("regie/" + datei)) {
            if (in == null) throw new IllegalStateException("Regiebuch regie/" + datei + " fehlt im Build");
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Regiebuch regie/" + datei + " nicht lesbar: " + e.getMessage(), e);
        }
    }

    private static Book mitgeliefert(String datei) { return parse(text(datei)); }

    /** Die Befehle der Stichworte (klein geschrieben im Text). */
    public static final List<String> BEFEHLE = List.of("zeit", "raffer", "wetter", "wetter_frei", "ansicht", "fahrt", "mitfahrt", "warte", "foto", "blatt", "sage");

    /** Die Bedingungen von {@code warte}. */
    public static final List<String> WARTEN = List.of("uhr", "faehre", "schwestern", "vorbei", "angelegt", "fahrt");

    /** Ein Stichwort, wie es im Text steht: Sekunde, Befehl, Argumente, Text fürs HUD. */
    public record Zeile(double sekunde, String befehl, String argumente, String text) { }

    /**
     * Liest ein Regiebuch aus Text. Kopfzeilen: {@code Regiebuch:}, {@code Beschreibung:}, {@code Länge:} (m:ss),
     * {@code Wetter: hh:mm | Zeitraffer} mit folgenden {@code Schritt:}-Zeilen im Format von {@code Scenario.parse}. Dann
     * je Zeile ein Stichwort: {@code m:ss befehl argumente | Text}. Zeilen zur selben Zeit ohne Text gehören zum
     * Stichwort davor, außer {@code warte}. Fehler nennen die Zeile.
     */
    public static Book parse(String text) {
        String name = null, about = "";
        double length = Double.NaN;
        String wetterKopf = null;
        StringBuilder steps = new StringBuilder();
        List<Zeile> zeilen = new ArrayList<>();
        int no = 0;
        LocalDate[] day = {null};
        List<Warte> warten = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            no++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String low = line.toLowerCase(Locale.ROOT);
            try {
                if (low.startsWith("regiebuch:")) name = line.substring(10).strip();
                else if (low.startsWith("beschreibung:")) about = line.substring(13).strip();
                else if (low.startsWith("länge:")) length = sekunden(line.substring(6).strip());
                else if (low.startsWith("wetter:")) wetterKopf = line.substring(7).strip();
                else if (low.startsWith("schritt:")) steps.append(line).append('\n');
                else {
                    Zeile z = zeile(line);
                    if (z.befehl().equals("zeit")) day[0] = datum(z.argumente().split("\\s+")[0]);
                    if (z.befehl().equals("warte")) warten.add(warte(z.argumente(), day[0]));
                    else warten.add(null);
                    zeilen.add(z);
                }
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Zeile " + no + ": " + e.getMessage() + " („" + line + "“)");
            }
        }
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("Es fehlt die Zeile „Regiebuch: Name“");
        if (zeilen.isEmpty()) throw new IllegalArgumentException("Das Regiebuch hat keine Stichworte");
        Scenario sc = null;
        if (wetterKopf != null) {
            String[] w = wetterKopf.split("\\|");
            String beginn = w[0].strip(), raffer = w.length > 1 ? w[1].strip() : "1";
            sc = Scenario.parse("Szenario: " + name + "\nBeginn: " + beginn + "\nZeitraffer: " + raffer + "\n" + steps);
        } else if (steps.length() > 0) throw new IllegalArgumentException("Schritte ohne Zeile „Wetter: hh:mm | Zeitraffer“");
        Integer[] idx = new Integer[zeilen.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, java.util.Comparator.comparingDouble(i -> zeilen.get(i).sekunde()));
        List<Zeile> sorted = new ArrayList<>();
        List<Warte> sortedW = new ArrayList<>();
        for (int i : idx) { sorted.add(zeilen.get(i)); sortedW.add(warten.get(i)); }
        List<Cue> cues = new ArrayList<>();
        for (int i = 0; i < sorted.size(); ) {
            Zeile z = sorted.get(i);
            if (sortedW.get(i) != null) { cues.add(new Cue(z.sekunde(), z.text(), x -> { }, sortedW.get(i))); i++; continue; }
            List<Zeile> grp = new ArrayList<>(List.of(z));
            int k = i + 1;
            while (k < sorted.size() && sorted.get(k).sekunde() == z.sekunde() && sorted.get(k).text().isEmpty() && sortedW.get(k) == null) grp.add(sorted.get(k++));
            final Scenario wsc = sc;
            List<Consumer<Ctx>> acts = new ArrayList<>();
            for (Zeile g : grp) acts.add(handlung(g, wsc, name));
            cues.add(new Cue(z.sekunde(), z.text(), x -> { for (Consumer<Ctx> a : acts) a.accept(x); }, null));
            i = k;
        }
        if (Double.isNaN(length)) length = cues.get(cues.size() - 1).t() + 1;
        return new Book(name, about, length, cues, text, sorted, sc);
    }

    /** „1:48“, „0:04“, „96“ oder „1:36.5“ → Sekunden. */
    static double sekunden(String s) {
        int c = s.indexOf(':');
        if (c < 0) return Double.parseDouble(s);
        return Integer.parseInt(s.substring(0, c)) * 60 + Double.parseDouble(s.substring(c + 1));
    }

    /** Sekunden → „m:ss“. */
    public static String zeit(double s) {
        int m = (int) (s / 60);
        double r = s - m * 60;
        return Math.abs(r - Math.rint(r)) < 1e-6 ? String.format(Locale.ROOT, "%d:%02d", m, (int) Math.rint(r)) : String.format(Locale.ROOT, "%d:%04.1f", m, r);
    }

    static LocalDate datum(String s) {
        String[] d = s.split("\\.");
        if (d.length != 3) throw new IllegalArgumentException("Datum TT.MM.JJJJ erwartet");
        return LocalDate.of(Integer.parseInt(d[2]), Integer.parseInt(d[1]), Integer.parseInt(d[0]));
    }

    /** „uhr 17:10“, „faehre Bolsøy“, „schwestern Bolsøy“, „vorbei Bolsøy“, „angelegt Bolsøy“, „fahrt“. */
    static Warte warte(String a, LocalDate day) {
        String[] p = a.strip().split("\\s+", 2);
        String w = p[0].toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ü", "ue");
        if (!WARTEN.contains(w)) throw new IllegalArgumentException("warte " + String.join("|", WARTEN) + " erwartet");
        if (w.equals("uhr")) {
            if (day == null) throw new IllegalArgumentException("warte uhr vor dem ersten zeit");
            if (p.length < 2) throw new IllegalArgumentException("warte uhr hh:mm erwartet");
            double h = sekunden(p[1].strip()) / 60;
            int sec = (int) Math.round(Math.max(0, Math.min(86_399, h * 3600)));
            long ms = java.time.ZonedDateTime.of(day, java.time.LocalTime.ofSecondOfDay(sec), SceneClock.ZONE).toInstant().toEpochMilli();
            return new Warte("uhr", ms / 3_600_000.0, "");
        }
        if (!w.equals("fahrt") && p.length < 2) throw new IllegalArgumentException("warte " + w + " Schiff erwartet");
        return new Warte(w, Double.NaN, p.length > 1 ? p[1].strip() : "");
    }

    static Zeile zeile(String line) {
        String cmd = line, txt = "";
        int bar = line.indexOf(" | ");
        if (bar >= 0) { cmd = line.substring(0, bar).strip(); txt = line.substring(bar + 3).strip(); }
        String[] p = cmd.split("\\s+", 3);
        if (p.length < 2) throw new IllegalArgumentException("Zeit und Befehl erwartet");
        double t = sekunden(p[0]);
        String b = p[1].toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ü", "ue");
        if (!BEFEHLE.contains(b)) throw new IllegalArgumentException("unbekannter Befehl „" + p[1] + "“, bekannt sind " + String.join(", ", BEFEHLE));
        return new Zeile(t, b, p.length > 2 ? p[2].strip() : "", txt);
    }

    /** Name und eine Zahl am Ende („Ørnesvingen 300“, „Bolsøy -25“); ohne Zahl NaN. */
    static Object[] nameZahl(String a) {
        String[] p = a.strip().split("\\s+");
        try {
            double v = Double.parseDouble(p[p.length - 1].replace(',', '.'));
            if (p.length > 1) return new Object[]{String.join(" ", java.util.Arrays.copyOf(p, p.length - 1)), v};
        } catch (NumberFormatException e) { /* kein Zahlenende */ }
        return new Object[]{a.strip(), Double.NaN};
    }

    /** Was ein Befehl tut; prüft die Argumente schon beim Lesen. */
    static Consumer<Ctx> handlung(Zeile z, Scenario sc, String buch) {
        String a = z.argumente();
        switch (z.befehl()) {
            case "zeit": {
                String[] p = a.split("\\s+");
                if (p.length != 2) throw new IllegalArgumentException("zeit TT.MM.JJJJ hh:mm erwartet");
                LocalDate date = datum(p[0]);
                double h = sekunden(p[1]) / 60;
                return x -> x.time(date, h);
            }
            case "raffer": { double v = Double.parseDouble(a); return x -> x.lapse(v); }
            case "wetter": {
                Scenario s = sc;
                if (!a.isEmpty()) {
                    s = null;
                    for (Scenario k : com.dan.geiranger.wiring.KlimaGeiranger.regiebuecher()) if (plain(k.name()).equals(plain(a))) s = k;
                    if (s == null) throw new IllegalArgumentException("kein Wetter „" + a + "“ (Ein Junitag, Ein Nebelmorgen im Fjord, Ein Regentag)");
                }
                if (s == null) throw new IllegalArgumentException("wetter ohne Namen und ohne Zeile „Wetter:“ im Kopf");
                final Scenario fs = s;
                return x -> x.weather(fs);
            }
            case "wetter_frei": {
                String[] p = a.split("\\s+");
                Weather.Phase ph = Weather.Phase.valueOf(p[0].toUpperCase(Locale.ROOT));
                double bft = p.length > 1 && !p[1].equals("-") ? Double.parseDouble(p[1]) : Double.NaN;
                double deg = p.length > 2 && !p[2].equals("-") ? Double.parseDouble(p[2]) : Double.NaN;
                return x -> x.weatherFree(ph, bft, deg);
            }
            case "ansicht": { need(a, "ansicht Name"); return x -> x.view(a); }
            case "fahrt": {
                need(a, "fahrt Name [Dauer]");
                Object[] nz = nameZahl(a);
                String n = (String) nz[0];
                double d = (double) nz[1];
                return x -> x.fahrt(n, d);
            }
            case "mitfahrt": {
                need(a, "mitfahrt Schiff [Blick]");
                Object[] nz = nameZahl(a);
                String n = (String) nz[0];
                double bl = Double.isNaN((double) nz[1]) ? 0 : (double) nz[1];
                return x -> x.mitfahrt(n, bl);
            }
            case "foto": { need(a, "foto Unterschrift"); return x -> x.capture(a); }
            case "blatt": { String n = a.isEmpty() ? buch : a; return x -> x.sheet(n); }
            case "sage": { need(a, "sage Text"); return x -> x.say(a); }
            default: throw new IllegalArgumentException(z.befehl());
        }
    }

    private static void need(String a, String form) { if (a.isEmpty()) throw new IllegalArgumentException(form + " erwartet"); }
}
