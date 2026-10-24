package com.dan.geiranger.data;

import com.dan.store.Ablage;
import com.dan.store.Protokoll;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Übernahme aus den Dateien in die Datenbank (F25 A, aus Rungholt): die Chronik (Stunden, Tage, Fahrten, Busse, Läufe
 * der Regiebücher), die Merkpunkte mit Vorschau und die eigenen Regiebücher, Ansichten und Presets. Die Nummern der
 * Sitzungen aus den Dateien gibt es in der Datenbank nicht; die Zeilen kommen ohne Sitzung hinein. Danach heißen die CSV
 * {@code *.uebernommen}, damit sie kein zweites Mal kommen; die übrigen Dateien bleiben.
 */
public final class Uebernahme {
    private Uebernahme() { }

    /** Was übernommen wurde, als Text („24 Stunden, 1 Tag …“). */
    public static String uebernehmen(DateiSpeicher von, Speicher nach) throws Ablage.Fehler {
        int st = 0, ta = 0, fa = 0, bu = 0, rl = 0, mp = 0, rb = 0, an = 0, pr = 0;
        for (Daten.Stunde s : von.stundenAlle()) { nach.stunde(s, null); st++; }
        for (Daten.Tag t : von.tageAlle()) { nach.tag(t, null); ta++; }
        for (Daten.Schiffsfahrt f : von.fahrtenAlle()) {
            nach.schiffsfahrt(new Daten.Schiffsfahrt(0, f.schiff(), f.richtung(), f.abMs(), f.anMs(), f.weg(), f.hoerner(), f.horn(), f.hornText(), f.echos(), f.echoText()), null);
            fa++;
        }
        for (Daten.Busankunft b : von.busseAlle()) { nach.busankunft(b, null); bu++; }
        for (Daten.Regielauf l : von.regielaeufe()) {
            byte[] png = null;
            if (l.blattDatei() != null && !l.blattDatei().isEmpty()) {
                try { png = java.nio.file.Files.readAllBytes(new File(l.blattDatei()).toPath()); } catch (Exception x) { /* ohne Blatt */ }
            }
            nach.regielauf(new Daten.Regielauf(l.name(), l.beginnMs(), l.endeMs(), l.vollstaendig(), l.kino(), l.bilderMittel(), l.gpuMs(), l.gewartetS(), l.fotos(),
                    l.qualitaet(), png, l.blattDatei()), null);
            rl++;
        }
        List<Daten.Merkpunkt> ms = new ArrayList<>(von.merkpunkteAlle());
        for (Daten.Merkpunkt m : ms) { nach.speichern(m, null); mp++; }
        for (Daten.Regiebuch r : von.regiebuecher()) if (Daten.EIGEN.equals(r.herkunft())) { nach.speichern(r); rb++; }
        for (Daten.Ansicht a : von.ansichten()) if (Daten.EIGEN.equals(a.herkunft())) { nach.speichern(von.ansicht(a.id()), null); an++; }
        for (Daten.PaketPreset p : von.presets(null, null)) if (Daten.EIGEN.equals(p.herkunft())) { nach.speichern(p); pr++; }
        List<String> umbenannt = new ArrayList<>();
        for (File f : von.chronikDateien()) {
            if (!f.isFile()) continue;
            File neu = new File(f.getPath() + ".uebernommen");
            for (int i = 2; neu.exists(); i++) neu = new File(f.getPath() + "." + i + ".uebernommen");
            if (f.renameTo(neu)) umbenannt.add(f.getName());
        }
        String t = String.format(Locale.GERMAN, "%d Stunden, %d Tage, %d Fahrten, %d Busse, %d Läufe der Regiebücher, %d Merkpunkte, %d Regiebücher, %d Ansichten, %d Presets",
                st, ta, fa, bu, rl, mp, rb, an, pr);
        Protokoll.info("Übernahme", t + " aus " + von.wo() + " nach " + nach.wo() + (umbenannt.isEmpty() ? "" : "; umbenannt: " + String.join(", ", umbenannt)));
        return t;
    }

    /** Ob es in den Dateien etwas zu übernehmen gibt. */
    public static boolean etwasDa(DateiSpeicher d) {
        for (File f : d.chronikDateien()) if (f.isFile()) return true;
        return false;
    }
}
