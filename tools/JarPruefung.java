import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Prüfung von {@code lib/Renderer.jar} (Ant-Target {@code check-packages}, seit Phase 18). Die Element-Packages liegen
 * nicht mehr im Projekt: ihre Quelle ist das Projekt Renderer, dort prüft {@code tools/PaketPruefung.java} jedes Package
 * einzeln (Paketvertrag Punkt 9). Hier wird nur geprüft, ob das Jar alles hat, was Geiranger braucht: jedes Package mit
 * Klassen und README, Klassen für Java 21 (Version 65) oder älter, und bei {@code fall} und {@code cliff} die Shader.
 * Aufruf ohne Ant: {@code java tools/JarPruefung.java lib/Renderer.jar}.
 */
public final class JarPruefung {
    /** Was Geiranger aus Renderer.jar braucht (wie {@code app.Bibliothek}: GEBRAUCHT und NEU). */
    static final String[] GEBRAUCHT = {"ground", "forest", "river", "ocean", "wave", "spray", "buoyancy", "sky", "cloud", "weather", "rain", "fog", "season", "ice",
            "road", "timber", "figure", "flock", "soundscape", "kino", "store", "fall", "cliff"};
    static final String[][] SHADER = {{"fall", "gl/veil.vert", "gl/veil.frag", "gl/strand.vert", "gl/mist.vert", "gl/pool.frag", "gl/fall.glsl"},
            {"cliff", "gl/rock.vert", "gl/rock.frag", "gl/cliff.glsl"}};

    public static void main(String[] a) throws Exception {
        File jar = new File(a.length > 0 ? a[0] : "lib/Renderer.jar");
        if (!jar.isFile()) { System.err.println("check-packages: " + jar + " fehlt"); System.exit(1); }
        Map<String, int[]> p = new TreeMap<>();   // Klassen, README, höchste Version
        try (ZipFile z = new ZipFile(jar)) {
            for (ZipEntry e : java.util.Collections.list(z.entries())) {
                String n = e.getName();
                if (!n.startsWith("com/dan/") || e.isDirectory()) continue;
                String[] t = n.split("/");
                if (t.length < 4) continue;
                int[] v = p.computeIfAbsent(t[2], k -> new int[3]);
                if (n.endsWith(".class")) {
                    v[0]++;
                    try (InputStream in = z.getInputStream(e); DataInputStream d = new DataInputStream(in)) {
                        d.readInt();
                        d.readUnsignedShort();
                        v[2] = Math.max(v[2], d.readUnsignedShort());
                    }
                } else if (t.length == 4 && t[3].equals("README.md")) v[1] = 1;
            }
            int fehler = 0;
            System.out.println("check-packages: " + jar.getName() + " mit " + p.size() + " Packages (Quelle: Projekt Renderer)");
            System.out.println("  Package        Klassen  README  Java");
            for (String g : GEBRAUCHT) {
                int[] v = p.get(g);
                boolean ok = v != null && v[0] > 0 && v[1] == 1 && v[2] <= 65;
                if (!ok) fehler++;
                System.out.printf("  %-14s %-8s %-7s %-5s %s%n", g, v == null ? "–" : v[0], v == null || v[1] == 0 ? "fehlt" : "ok", v == null ? "–" : v[2] - 44, ok ? "" : "FEHLER");
            }
            for (String[] s : SHADER)
                for (int i = 1; i < s.length; i++)
                    if (z.getEntry("com/dan/" + s[0] + "/" + s[i]) == null) { fehler++; System.out.println("  FEHLER: Shader com/dan/" + s[0] + "/" + s[i] + " fehlt"); }
            if (fehler > 0) { System.out.println("check-packages: " + fehler + " Fehler – Renderer neu bauen (Clean and Build) und dist/Renderer.jar nach lib/ kopieren"); System.exit(1); }
            System.out.println("check-packages: alle " + GEBRAUCHT.length + " gebrauchten Packages in " + jar.getName() + ", Shader von fall und cliff da.");
        }
    }
}
