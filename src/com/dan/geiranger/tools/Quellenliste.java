package com.dan.geiranger.tools;

import com.dan.geiranger.data.Daten;
import com.dan.geiranger.data.Startdaten;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 18 (nach Rungholt): die Quellenliste {@code docs/QUELLEN.md} aus denselben Textdateien wie die Startdaten der
 * Datenbank ({@code data/quellen.txt}, {@code befunde.txt}). So steht dieselbe Liste im Schema GR_, in {@code daten/}
 * und auf GitHub; dazu die Vorlagen in {@code templates/} (nicht im Git) und die Bibliotheken. Aufruf (Run File oder
 * Ant-Target {@code quellen}) ohne Argumente schreibt {@code docs/QUELLEN.md}; die Prüfung vergleicht die Datei mit
 * {@link #text()}.
 */
public final class Quellenliste {
    private Quellenliste() { }

    public static final File DATEI = new File("docs/QUELLEN.md");

    /** Die Vorlagen in templates/ (Datei, was, wofür). */
    static final String[][] VORLAGEN = {
            {"Karte-Geirangerfjord.png", "Karte des Fjords mit Ort, Fv63 und Höfen", "Lage der Kernfenster (F1), Achse der Ørnesvingen (F14), Kehrenstreifen"},
            {"N62E006.zip, N62E007.zip (HGT 1″)", "Oberflächenmodell „DEM1“, 1 Bogensekunde", "Gelände der ganzen Szene (tools.HgtImport, Phase 2)"},
            {"N58E007.zip, N62E008.zip", "weitere HGT-Kacheln", "geprüft, nicht im Ausschnitt"},
            {"rasters_COP30 …/output_hh.tif", "Copernicus GLO-30", "Vergleich mit dem HGT (+0,9 m, Streuung 6,5 m)"},
            {"Geiranger1.jpg … Geiranger3.jpg", "Fotos des Orts und der Hänge", "Farben der Häuser, Wald bis an die Wände, vergraute Bohlen der Höfe"},
            {"Ørnesvingen1.jpg … Ørnesvingen3.jpg", "Fotos von der Plattform und den Kehren", "Plattform, Blick zum Ort, Ansicht 1"},
            {"Waterfall1.jpg … Waterfall3.jpg", "Fotos der Sieben Schwestern", "Strähnen, Gischt, nasse Wand (Phase 7)"},
            {"gigantic_tundra_terrain_viyjcbq_high.zip", "Scan „Gigantic Tundra Terrain“", "Zeichnung der Tundra über der Waldgrenze (F10 A, daten/tundra/)"},
            {"gigantic_sandstone_terrain_…zip, ice_cliff_…zip", "weitere Scans", "geprüft, nicht verwendet"},
            {"S1C_IW_RAW__0SDV_20261023…SAFE.zip", "Sentinel-1-Rohdaten", "geprüft, für die Szene nicht nötig"},
    };

    /** Die Bibliotheken der App. */
    static final String[][] BIBLIOTHEKEN = {
            {"lib/Renderer.jar", "33 Packages aus Lighthouse, Haithabu, Glacier und Rungholt, seit Phase 18 mit fall und cliff aus Geiranger (Quellen in pakete/)"},
            {"lib/jogl-all.jar, gluegen-rt.jar", "JOGL (OpenGL 4.3) mit den Windows-Natives"},
            {"lib/FStyle.jar", "die Oberfläche im F-Stil (FFrame, FButton, FComboBox …)"},
            {"lib/Avatar.jar", "Menschen (com.dan.avatar, F18 A)"},
            {"lib/ojdbc11.jar, sdoapi.jar, sdogr.jar, sdoutl.jar", "Oracle JDBC und Spatial (Schema GR_, Phase 17)"},
    };

    public static String text() {
        List<Daten.Quelle> qs = Startdaten.quellen();
        List<Daten.Befund> bs = Startdaten.befunde();
        StringBuilder b = new StringBuilder();
        b.append("# Geiranger – Quellen und Befunde\n\n");
        b.append("Erzeugt von `tools/Quellenliste` aus `src/com/dan/geiranger/data/quellen.txt` und `befunde.txt`. Dieselben Zeilen stehen in den Tabellen ")
                .append("`GR_QUELLE`, `GR_BEFUND` und `GR_BEFUND_QUELLE` (Sicht `GR_BEFUND_V`). Jeder Befund ist **belegt** (eine Quelle nennt es), **gemessen** ")
                .append("(aus Daten wie den HGT-Kacheln abgeleitet) oder **gestaltet** [G] (so gebaut, dass es glaubhaft ist, ohne Beleg).\n\n");
        b.append(String.format(Locale.GERMAN, "%d Quellen · %d Befunde (%d belegt, %d gemessen, %d gestaltet) · %d Vorlagen · %d Bibliotheken\n\n",
                qs.size(), bs.size(), zahl(bs, "BELEGT"), zahl(bs, "GEMESSEN"), zahl(bs, "GESTALTET"), VORLAGEN.length, BIBLIOTHEKEN.length));
        b.append("## Quellen\n\n| Kurz | Art | Titel | Abgerufen | Wofür |\n|---|---|---|---|---|\n");
        for (Daten.Quelle q : qs)
            b.append("| ").append(q.kurz()).append(" | ").append(art(q.art())).append(" | ")
                    .append(q.url() == null || q.url().isBlank() ? esc(q.titel()) : "[" + esc(q.titel()) + "](" + q.url() + ")").append(" | ")
                    .append(q.abgerufen() == null ? "" : q.abgerufen().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))).append(" | ")
                    .append(esc(q.bemerkung() == null ? "" : q.bemerkung())).append(" |\n");
        b.append("\n## Befunde nach Thema\n");
        Map<String, StringBuilder> themen = new LinkedHashMap<>();
        for (Daten.Befund x : bs) {
            StringBuilder t = themen.computeIfAbsent(x.thema(), k -> new StringBuilder("\n### " + thema(k) + "\n\n| Gegenstand | Befund | Text | Quellen |\n|---|---|---|---|\n"));
            t.append("| ").append(esc(x.gegenstand())).append(" | ").append(befund(x.befund())).append(" | ").append(esc(x.text())).append(" | ")
                    .append(String.join(", ", x.quellen())).append(" |\n");
        }
        for (StringBuilder t : themen.values()) b.append(t);
        b.append("\n## Vorlagen in `templates/`\n\nNicht im Git (mehrere GB, fremde Rechte); was die App braucht, liegt nach dem Import in `daten/`.\n\n| Datei | Was | Wofür |\n|---|---|---|\n");
        for (String[] v : VORLAGEN) b.append("| ").append(esc(v[0])).append(" | ").append(esc(v[1])).append(" | ").append(esc(v[2])).append(" |\n");
        b.append("\n## Bibliotheken\n\n| Datei | Inhalt |\n|---|---|\n");
        for (String[] v : BIBLIOTHEKEN) b.append("| ").append(esc(v[0])).append(" | ").append(esc(v[1])).append(" |\n");
        return b.toString();
    }

    private static long zahl(List<Daten.Befund> bs, String art) { return bs.stream().filter(x -> art.equals(x.befund())).count(); }

    private static String art(String a) {
        return switch (a == null ? "" : a) {
            case "WEB" -> "Netz";
            case "BUCH" -> "Buch";
            case "DATEN" -> "Daten";
            case "APP" -> "App";
            default -> a == null ? "" : a;
        };
    }

    private static String befund(String b) {
        return switch (b) { case "BELEGT" -> "belegt"; case "GEMESSEN" -> "gemessen"; case "GESTALTET" -> "gestaltet [G]"; default -> b; };
    }

    private static String thema(String t) {
        return switch (t) {
            case "GELAENDE" -> "Gelände"; case "FAELLE" -> "Fälle"; case "STRASSE" -> "Straße"; case "SCHIFFE" -> "Schiffe"; case "ORT" -> "Der Ort";
            default -> t.charAt(0) + t.substring(1).toLowerCase(Locale.ROOT);
        };
    }

    private static String esc(String s) { return s == null ? "" : s.replace("|", "\\|").replace("\n", " "); }

    public static void main(String[] a) throws Exception {
        File f = a.length > 0 ? new File(a[0]) : DATEI;
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), text(), StandardCharsets.UTF_8);
        System.out.println("Quellenliste: " + f.getAbsolutePath());
    }
}
