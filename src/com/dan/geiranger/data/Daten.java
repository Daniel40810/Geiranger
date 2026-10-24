package com.dan.geiranger.data;

import com.dan.geiranger.scene.SceneClock;
import com.dan.geiranger.scene.Site;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die Datensätze, die {@link Speicher} liest und schreibt (Phase 17, Werkbuch Abschnitt 8, F25–F37 „alle A“). Je
 * Tabelle ein Record mit den Spalten, die die App braucht; ID, ANGELEGT und GEAENDERT führt die Datenbank selbst.
 * Lagen stehen in Szenenmetern (x nach Osten, y über NN, z nach Süden, Ursprung am Anleger); Länge und Breite rechnet
 * {@link #lonLat} über UTM 32 N.
 * <p>
 * Szenenzeit: Millisekunden seit 1970 (UTC) wie die Uhr der Szene, dazu als Text („21.06.2026 09:10 MESZ“) und als Tag
 * („2026-06-21“); den TIMESTAMP in Europe/Oslo (SZENENZEIT) setzt der Trigger.
 */
public final class Daten {
    private Daten() { }

    /** HERKUNFT: mitgeliefert (stellt der Einrichter wieder her) oder eigen (fasst er nie an). */
    public static final String MITGELIEFERT = "MITGELIEFERT", EIGEN = "EIGEN";
    /** BEFUND: belegt, gemessen oder gestaltet [G]. */
    public static final String BELEGT = "BELEGT", GEMESSEN = "GEMESSEN", GESTALTET = "GESTALTET";

    private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm", Locale.GERMANY);

    /** {lon, lat} eines Szenenpunkts (x, z). */
    public static double[] lonLat(double x, double z) {
        double[] ll = Site.latLon(x, z);
        return new double[]{ll[1], ll[0]};
    }

    /** Szenenpunkt {x, z} aus Länge und Breite. */
    public static double[] xz(double lon, double lat) { return Site.xz(new double[]{lat, lon}); }

    public static ZonedDateTime zeit(double epochMs) { return Instant.ofEpochMilli((long) Math.floor(epochMs)).atZone(SceneClock.ZONE); }

    /** „21.06.2026 09:10 MESZ“ */
    public static String text(double epochMs) {
        ZonedDateTime z = zeit(epochMs);
        return ZEIT.format(z) + " " + (SceneClock.ZONE.getRules().isDaylightSavings(z.toInstant()) ? "MESZ" : "MEZ");
    }

    /** „2026-06-21“ */
    public static String tag(double epochMs) { return zeit(epochMs).toLocalDate().toString(); }

    public static LocalDate datum(double epochMs) { return zeit(epochMs).toLocalDate(); }

    /** Uhrzeit in Stunden (norwegische Zeit). */
    public static double stunde(double epochMs) {
        ZonedDateTime z = zeit(epochMs);
        return z.getHour() + z.getMinute() / 60.0 + z.getSecond() / 3600.0;
    }

    /** Beginn eines Tages (00:00 norwegische Zeit) in ms. */
    public static long beginn(LocalDate tag) { return tag.atStartOfDay(SceneClock.ZONE).toInstant().toEpochMilli(); }

    /** „08:30“ aus Minuten nach Mitternacht. */
    public static String hm(int min) { return String.format(Locale.ROOT, "%02d:%02d", Math.floorMod(min, 1440) / 60, Math.floorMod(min, 60)); }

    /** Minuten nach Mitternacht aus „8:30“ oder „08:30“. */
    public static int min(String hm) {
        String[] p = hm.strip().split(":");
        return Integer.parseInt(p[0]) * 60 + (p.length > 1 ? Integer.parseInt(p[1]) : 0);
    }

    /**
     * Eine Geometrie in Szenenmetern: PUNKT, LINIE, MULTILINIE, FLAECHE oder MULTIFLAECHE (aus Rungholt). Bei MULTIFLAECHE
     * ist {@link #ringe} je Teil die Liste der Ringe (erster außen, dann Löcher); sonst stehen die Punkte in {@link #teile}.
     */
    public record Geom(String art, List<double[][]> teile, List<List<double[][]>> ringe) {
        public Geom {
            teile = List.copyOf(teile);
            List<List<double[][]>> r = new ArrayList<>();
            if (ringe != null) for (List<double[][]> x : ringe) r.add(List.copyOf(x));
            ringe = List.copyOf(r);
        }
        public Geom(String art, List<double[][]> teile) { this(art, teile, null); }
        public static Geom punkt(double x, double z) { return new Geom("PUNKT", List.<double[][]>of(new double[][]{{x, z}})); }
        public static Geom linie(double[][] p) { return new Geom("LINIE", List.<double[][]>of(p)); }
        public static Geom multilinie(List<double[][]> p) { return new Geom("MULTILINIE", p); }
        public static Geom flaeche(double[][] p) { return new Geom("FLAECHE", List.<double[][]>of(p)); }
        public double[][] xz() { return teile.get(0); }

        private static double[][] ll(double[][] xz) {
            double[][] r = new double[xz.length][];
            for (int i = 0; i < xz.length; i++) r[i] = lonLat(xz[i][0], xz[i][1]);
            return r;
        }

        /** Ein Ring in Länge und Breite, geschlossen, außen gegen den Uhrzeigersinn, Löcher mit ihm (wie Oracle es will). */
        private static String ring(double[][] xz, boolean aussen) {
            double[][] p = ll(xz);
            double a = 0;
            for (int i = 0; i < p.length; i++) { double[] u = p[i], v = p[(i + 1) % p.length]; a += u[0] * v[1] - v[0] * u[1]; }
            if (a > 0 != aussen) {
                double[][] q = new double[p.length][];
                for (int i = 0; i < p.length; i++) q[i] = p[p.length - 1 - i];
                p = q;
            }
            StringBuilder b = new StringBuilder("(");
            for (int i = 0; i <= p.length; i++) {
                double[] q = p[i % p.length];
                b.append(i > 0 ? ", " : "").append(n7(q[0])).append(' ').append(n7(q[1]));
            }
            return b.append(')').toString();
        }

        static String n7(double v) {
            String s = String.format(Locale.ROOT, "%.7f", v);
            while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
            return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
        }

        private static String linienText(double[][] xz) {
            double[][] p = ll(xz);
            StringBuilder b = new StringBuilder("(");
            for (int i = 0; i < p.length; i++) b.append(i > 0 ? ", " : "").append(n7(p[i][0])).append(' ').append(n7(p[i][1]));
            return b.append(')').toString();
        }

        /** Als WKT in Länge und Breite (7 Stellen, rund 1 cm). */
        public String wkt() {
            return switch (art) {
                case "PUNKT" -> { double[] p = lonLat(xz()[0][0], xz()[0][1]); yield "POINT (" + n7(p[0]) + " " + n7(p[1]) + ")"; }
                case "LINIE" -> "LINESTRING " + linienText(xz());
                case "FLAECHE" -> "POLYGON (" + ring(xz(), true) + ")";
                case "MULTIFLAECHE" -> {
                    StringBuilder b = new StringBuilder("MULTIPOLYGON (");
                    for (int t = 0; t < ringe.size(); t++) {
                        b.append(t > 0 ? ", " : "").append('(');
                        List<double[][]> r = ringe.get(t);
                        for (int i = 0; i < r.size(); i++) b.append(i > 0 ? ", " : "").append(ring(r.get(i), i == 0));
                        b.append(')');
                    }
                    yield b.append(')').toString();
                }
                default -> {
                    StringBuilder b = new StringBuilder("MULTILINESTRING (");
                    for (int i = 0; i < teile.size(); i++) b.append(i > 0 ? ", " : "").append(linienText(teile.get(i)));
                    yield b.append(')').toString();
                }
            };
        }

        /** Aus WKT in Länge und Breite (wie {@code SDO_UTIL.TO_WKTGEOMETRY} es liefert) zurück in Szenenmeter. */
        public static Geom ausWkt(String wkt) {
            if (wkt == null || wkt.isBlank()) return null;
            String w = wkt.strip();
            if (w.startsWith("MULTIPOLYGON") || w.startsWith("POLYGON")) {
                List<List<double[][]>> teile = new ArrayList<>();
                int tiefe = 0, start = -1, ringTiefe = w.startsWith("MULTI") ? 3 : 2;
                List<double[][]> akt = null;
                for (int i = 0; i < w.length(); i++) {
                    char ch = w.charAt(i);
                    if (ch == '(') {
                        tiefe++;
                        if (tiefe == ringTiefe - 1) { akt = new ArrayList<>(); teile.add(akt); }
                        if (tiefe == ringTiefe) start = i;
                    } else if (ch == ')') {
                        if (tiefe == ringTiefe && start >= 0 && akt != null) {
                            double[][] ll = com.dan.store.Wkt.punkte(w.substring(start, i + 1));
                            int n = ll.length > 3 ? ll.length - 1 : ll.length;
                            double[][] p = new double[n][];
                            for (int k = 0; k < n; k++) p[k] = Daten.xz(ll[k][0], ll[k][1]);
                            akt.add(p);
                            start = -1;
                        }
                        tiefe--;
                    }
                }
                if (w.startsWith("POLYGON")) return new Geom("FLAECHE", List.<double[][]>of(teile.get(0).get(0)));
                List<double[][]> aussen = new ArrayList<>();
                for (List<double[][]> t : teile) aussen.add(t.get(0));
                return new Geom("MULTIFLAECHE", aussen, teile);
            }
            String art = w.startsWith("POINT") ? "PUNKT" : w.startsWith("MULTILINESTRING") ? "MULTILINIE" : "LINIE";
            List<double[][]> teile = new ArrayList<>();
            String[] stuecke = art.equals("MULTILINIE") ? w.substring(w.indexOf('(') + 1, w.lastIndexOf(')')).split("\\)\\s*,\\s*\\(") : new String[]{w};
            for (String s : stuecke) {
                double[][] ll = com.dan.store.Wkt.punkte(s);
                double[][] p = new double[ll.length][];
                for (int i = 0; i < ll.length; i++) p[i] = Daten.xz(ll[i][0], ll[i][1]);
                teile.add(p);
            }
            return new Geom(art, teile);
        }

        /** Länge aller Linien (m, in der Ebene der Szene). */
        public double laenge() {
            double s = 0;
            for (double[][] p : teile) for (int i = 0; i + 1 < p.length; i++) s += Math.hypot(p[i + 1][0] - p[i][0], p[i + 1][1] - p[i][1]);
            return s;
        }

        /** Fläche (m², nach Gauß; bei MULTIFLAECHE ohne die Löcher). */
        public double flaecheM2() {
            if (art.equals("MULTIFLAECHE")) {
                double s = 0;
                for (List<double[][]> t : ringe) for (int i = 0; i < t.size(); i++) s += (i == 0 ? 1 : -1) * gauss(t.get(i));
                return s;
            }
            return gauss(xz());
        }

        static double gauss(double[][] p) {
            double a = 0;
            for (int i = 0; i < p.length; i++) { double[] u = p[i], v = p[(i + 1) % p.length]; a += u[0] * v[1] - v[0] * u[1]; }
            return Math.abs(a) / 2;
        }

        public int punkte() {
            int n = 0;
            if (art.equals("MULTIFLAECHE")) { for (List<double[][]> t : ringe) for (double[][] r : t) n += r.length; return n; }
            for (double[][] p : teile) n += p.length;
            return n;
        }
    }

    /** Ein Rechteck: Mitte, Richtung der Länge (Einheitsvektor ax, az), halbe Länge und Breite. */
    public static double[][] rechteck(double x, double z, double ax, double az, double hl, double hw) {
        double l = Math.hypot(ax, az);
        if (l < 1e-9) { ax = 1; az = 0; l = 1; }
        ax /= l;
        az /= l;
        double[][] uv = {{-hl, -hw}, {hl, -hw}, {hl, hw}, {-hl, hw}};
        double[][] p = new double[4][];
        for (int i = 0; i < 4; i++) p[i] = new double[]{x + ax * uv[i][0] - az * uv[i][1], z + az * uv[i][0] + ax * uv[i][1]};
        return p;
    }

    // ================================================================== Quellen und Gelände

    public record Quelle(String kurz, String titel, String url, String art, LocalDate abgerufen, String bemerkung) { }

    public record Befund(String thema, String gegenstand, String befund, String text, List<String> quellen) {
        public Befund { quellen = List.copyOf(quellen); }
    }

    /** Ein Raster (GR_GELAENDE); dateien: Datei → SHA-256 (oder leer). */
    public record Gelaende(String name, Geom ausschnitt, double ursprungE, double ursprungN, double ursprungLon, double ursprungLat, double zelleM, int zellen,
                           double hoeheMin, double hoeheMax, Double ufer, Double mitte, Double rinne, String kacheln, Map<String, String> dateien, String quelle,
                           String bemerkung) {
        public Gelaende { dateien = new LinkedHashMap<>(dateien); }
        public String dateienText() {
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, String> e : dateien.entrySet()) b.append(b.length() > 0 ? "; " : "").append(e.getKey()).append('=').append(e.getValue() == null ? "" : e.getValue());
            return b.toString();
        }
    }

    /** Ein Kernfenster (UTM 32 N). */
    public record Kernfenster(String name, double eMin, double nMin, double eMax, double nMax, String inhalt) {
        public Geom flaeche() {
            double[] a = Site.fromUtm(eMin, nMax), b = Site.fromUtm(eMax, nMin);
            return Geom.flaeche(new double[][]{{a[0], a[1]}, {b[0], a[1]}, {b[0], b[1]}, {a[0], b[1]}});
        }
    }

    /** Was beim Laden geformt wird (GR_FORM). */
    public record Form(String name, String art, Geom flaeche, Double stufeM, Double frontM, int zellen, double zelleM, double mengeM3, String text) { }

    /** Eine Höhenlinie (GR_HOEHENLINIE): alle Teile einer Höhe. */
    public record Hoehenlinie(int hoeheM, Geom linie) {
        public String art() { return hoeheM % 250 == 0 ? "HAUPT" : "NEBEN"; }
    }

    // ================================================================== Wasser

    /** Eine Strähne: Weg (x, y, z je Stützpunkt), Länge, Anteil am Abfluss, Anteil auf dem Fels, Aufschläge, längster freier Fall, Fallzeit, Aufprall. */
    public record Straehne(int nr, double[][] weg, double laengeM, double anteil, double felsAnteil, int aufschlaege, double freiMaxM, double fallzeitS,
                           double[] aufprall, boolean aufWasser) {
        public Geom linie() {
            double[][] p = new double[weg.length][];
            for (int i = 0; i < weg.length; i++) p[i] = new double[]{weg[i][0], weg[i][2]};
            return Geom.linie(p);
        }
        /** Die Höhen der Stützpunkte als Text („410.2 395.8 …“). */
        public String hoehen() {
            StringBuilder b = new StringBuilder();
            for (double[] q : weg) b.append(b.length() > 0 ? " " : "").append(com.dan.store.Zahl.text(Math.round(q[1] * 10) / 10.0));
            return b.toString();
        }
    }

    /** Ein Fall (GR_FALL) mit den Wegen seiner Strähnen. */
    public record Fall(String name, String art, double x, double y, double z, double fussE, double fussN, double richtung, double hoeheM, double breiteM,
                       int straehnen, double juniM3s, boolean ueberhang, boolean nurRegen, double fallhoeheM, double fallzeitS, double freiMaxM, double felsAnteil,
                       String befund, String quelle, String bemerkung, List<Straehne> wege) {
        public Fall { wege = List.copyOf(wege); }
        @Override public String toString() { return name; }
    }

    /** Ein Stück der Geirangelva (GR_ELVA). */
    public record Elva(String name, String art, int nr, Geom linie, double vonM, double bisM, double spiegelOben, double spiegelUnten, double breiteM, double juniM3s) {
        public double laengeM() { return bisM - vonM; }
        public double gefaelle() { return laengeM() <= 0 ? 0 : (spiegelOben - spiegelUnten) / laengeM(); }
    }

    // ================================================================== Klima und Jahr

    public record KlimaMonat(int monat, String name, double temperaturC, double niederschlagMm, double regentage, double abflussAnteil, String bemerkung) { }

    public record Hoehenstufe(int nr, double hoeheM, double abnahme, double mehrJeM, double tJan, double tJul, double niederschlagJahr) { }

    // ================================================================== Straße und Ort

    public record Strasse(String name, String art, Geom linie, double laengeM, double steigungMittel, double steigungMax, double hoeheUnten, double hoeheOben,
                          double breiteM, double spurM, String bemerkung) { }

    public record Kehre(int nr, double x, double y, double z, double stationM, double bogenM, double radiusM) { }

    public record Ort(String name, String art, double x, double y, double z, String bemerkung) {
        public double[] utm() { return Site.toUtm(x, z); }
    }

    public record Gebaeude(int nr, String name, String art, double x, double y, double z, double ax, double az, double hl, double hw, double wandM, String farbe,
                           String dach, String bauweise, String ortsteil) {
        public Geom grundriss() { return Geom.flaeche(rechteck(x, z, ax, az, hl, hw)); }
        public double firstGrad() { double d = Math.toDegrees(Math.atan2(ax, -az)); return ((d % 180) + 180) % 180; }
    }

    // ================================================================== Schiffe

    public record Schiff(String name, String art, double laengeM, double breiteM, double tiefgangM, double freibordM, Double gmM, Double rollzeitS, Double masseT,
                         String bemerkung) { }

    /** Eine Abfahrt (GR_FAHRPLAN): Richtung NACH_HELLESYLT (ab Geiranger) oder NACH_GEIRANGER, Zeit „08:30“, Saison „05-16“ bis „09-14“. */
    public record Fahrplan(String linie, String richtung, String abfahrt, int fahrzeitMin, String saisonVon, String saisonBis, int jahr, String quelle,
                           String bemerkung, String herkunft) {
        public int abfahrtMin() { return min(abfahrt); }
        @Override public String toString() { return abfahrt + (richtung.equals("NACH_HELLESYLT") ? " ab Geiranger" : " ab Hellesylt"); }
    }

    public record Schiffsweg(String name, String art, Geom geom, String bemerkung) { }

    // ================================================================== Regie und Kamera

    /** Ein Wetter-Regiebuch (Format von {@code Scenario.parse}). */
    public record Wetterbuch(String name, double beginnStunde, double zeitraffer, int schritte, String beschreibung, String text, String herkunft) {
        @Override public String toString() { return name; }
    }

    /** Ein Regiebuch als ganzer Text (Format von {@code scene.Regie.parse}). */
    public record Regiebuch(String name, String text, String beschreibung, String datei, String herkunft) {
        @Override public String toString() { return name; }
    }

    public record Stuetzpunkt(int nr, double zeitS, double x, double y, double z, double gier, double nick) { }

    public record Fahrt(String name, String art, Double dauerS, Double laengeM, String beschreibung, String herkunft, List<Stuetzpunkt> punkte) {
        public Fahrt { punkte = List.copyOf(punkte); }
        public Geom weg() {
            if (punkte.size() < 2) return null;
            double[][] p = new double[punkte.size()][];
            for (int i = 0; i < p.length; i++) p[i] = new double[]{punkte.get(i).x(), punkte.get(i).z()};
            return Geom.linie(p);
        }
        @Override public String toString() { return name; }
    }

    /** Eine Ansicht: fest (Tasten 1–9) oder gemerkt (Taste V); kamera = {x, y, z, Gier, Nick}, y NaN heißt: aus dem Gelände. */
    public record Ansicht(long id, String name, Integer taste, String art, double[] kamera, Double augeUeberGrund, Long szeneMs, String zustand, byte[] vorschau,
                          String herkunft) {
        @Override public String toString() { return name; }
    }

    /** Ein Merkpunkt (F35 A): Punkt im Gelände mit Notiz, Auge, Blick, Szenenzeit und Vorschau. */
    public record Merkpunkt(long id, String name, String notiz, double x, double y, double z, double[] auge, double blick, double nick, Long szeneMs,
                            byte[] vorschau, String herkunft) {
        public double abstand() { return auge == null ? Double.NaN : Math.sqrt(Math.pow(x - auge[0], 2) + Math.pow(y - auge[1], 2) + Math.pow(z - auge[2], 2)); }
        @Override public String toString() { return name; }
    }

    public record PaketPreset(String paket, String klasse, String name, String beschreibung, String herkunft, Map<String, String> felder) {
        public PaketPreset { felder = new LinkedHashMap<>(felder); }
        @Override public String toString() { return name; }
    }

    // ================================================================== Chronik (F29)

    /** Eine Szenenstunde: Wetter und Abfluss (Liste „Name=m³/s; …“). */
    public record Stunde(long ms, String lage, double windMs, double richtungGrad, double boeeMs, double bedeckung, String wolken, double regenMmH, double sichtKm,
                         double nebel, double temperaturC, double abflussAnteil, double regenspeicherMm, double fallTempC, double eis, String abfluss) { }

    /** Ein Szenentag: Schnee, Laub, Herbst, Kronenschnee, Eis der Fälle. */
    public record Tag(long ms, Double schneegrenzeM, double schneeOrtCm, double schnee600Cm, double schnee1200Cm, double laub, double herbst, double kronen,
                      double eis, double luftC) { }

    /** Eine Fahrt eines Schiffs; anMs null, solange es fährt. horn: {x, z} oder null. */
    public record Schiffsfahrt(long id, String schiff, String richtung, long abMs, Long anMs, Geom weg, int hoerner, double[] horn, String hornText, Integer echos,
                               String echoText) {
        public Double dauerMin() { return anMs == null ? null : Math.round((anMs - abMs) / 6000.0) / 10.0; }
    }

    public record Busankunft(long ms, long abMs, String halt, int gruppe, double x, double z) { }

    /** Ein Lauf eines Regiebuchs mit dem Blatt als PNG (oder null). */
    public record Regielauf(String name, long beginnMs, Long endeMs, boolean vollstaendig, boolean kino, double bilderMittel, double gpuMs, double gewartetS,
                            int fotos, String qualitaet, byte[] blatt, String blattDatei) { }

    /** Was die Chronik an einem Szenentag weiß (Tageskarte, Block DATEN). */
    public record Tageschronik(LocalDate tag, List<Stunde> stunden, List<Tag> tage, List<Schiffsfahrt> fahrten, List<Busankunft> busse) { }

    // ================================================================== Betrieb

    /** Die letzte Sitzung für „Weiter wie zuletzt“. */
    public record Sitzung(long id, long beginnMs, Long endeMs, boolean sauber, String zustand, Long zustandMs, String regiebuch, Double regieSekunde, Long szeneMs,
                          Double zeitraffer, String qualitaet, Boolean ton) {
        public String szeneText() { return szeneMs == null ? "" : Daten.text(szeneMs); }
    }

    /** Was das Startbild und der Block DATEN über den Stand zeigen. */
    public record Stand(int faelle, int gebaeude, int kehren, int abfahrten, int stunden, int tage, int fahrten, int busse, int regiebuecher, int regielaeufe,
                        int ansichten, int merkpunkte, int presets, int befunde, int quellen, Sitzung letzte) { }
}
