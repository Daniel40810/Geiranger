package com.dan.geiranger.app;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Die Packages aus {@code lib/Renderer.jar} (F4 A: Bibliothek statt Kopie). Findet das Jar über eine Klasse, die
 * darin liegen muss ({@code com.dan.sky.Sky}), und zählt die Packages unter {@code com/dan/}. Für HUD, Bedienfeld und
 * Prüfung; gezeichnet wird daraus ab Phase 3 (ocean).
 */
public final class Bibliothek {
    private Bibliothek() { }

    /** Packages, die Geiranger nach dem Werkbuch (Abschnitt 4) aus Renderer.jar braucht. */
    public static final String[] GEBRAUCHT = {"ground", "forest", "river", "ocean", "wave", "spray", "buoyancy", "sky", "cloud", "weather", "rain",
            "fog", "season", "ice", "road", "timber", "figure", "flock", "soundscape", "kino", "store"};

    /** Packages, die in diesem Projekt neu entstanden (Phase 5 und 6); seit Phase 18 liegen sie in Renderer.jar, die Quellen in {@code pakete/} (Ant-Target {@code renderer-jar}). */
    public static final String[] NEU = {"fall", "cliff"};

    private static volatile List<String> cache;
    private static volatile String jar = "";

    /** Die Packages in Renderer.jar (Namen unter com.dan), sortiert; leer, wenn das Jar fehlt. */
    public static List<String> pakete() {
        List<String> c = cache;
        if (c != null) return c;
        TreeSet<String> s = new TreeSet<>();
        try {
            URL u = Class.forName("com.dan.sky.Sky").getProtectionDomain().getCodeSource().getLocation();
            File f = new File(u.toURI());
            jar = f.getName();
            if (f.isFile()) {
                try (ZipFile z = new ZipFile(f)) {
                    for (ZipEntry e : java.util.Collections.list(z.entries())) {
                        String n = e.getName();
                        if (!n.startsWith("com/dan/") || !n.endsWith(".class")) continue;
                        String[] p = n.split("/");
                        if (p.length >= 4) s.add(p[2]);
                    }
                }
            } else if (f.isDirectory()) {
                File[] d = new File(f, "com/dan").listFiles(File::isDirectory);
                if (d != null) for (File x : d) s.add(x.getName());
            }
        } catch (Exception | LinkageError e) {
            jar = "nicht gefunden (" + e.getClass().getSimpleName() + ")";
        }
        cache = List.copyOf(s);
        return cache;
    }

    /** Was das Werkbuch aus Renderer.jar braucht, dort aber fehlt. */
    public static List<String> fehlend() {
        List<String> p = pakete(), f = new ArrayList<>();
        for (String g : GEBRAUCHT) if (!p.contains(g)) f.add(g);
        for (String g : NEU) if (!p.contains(g)) f.add(g);
        return f;
    }

    /** „31 Packages in Renderer.jar, alle 21 gebrauchten da“ */
    public static String kurz() {
        List<String> p = pakete(), f = fehlend();
        if (p.isEmpty()) return "!nicht gefunden " + jar;
        return String.format(Locale.GERMAN, "%d Packages in %s, %s", p.size(), jar,
                f.isEmpty() ? "alle " + GEBRAUCHT.length + " gebrauchten da, dazu " + String.join(" und ", NEU) + " aus Geiranger" : "es fehlen " + String.join(", ", f));
    }
}
