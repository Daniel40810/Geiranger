package com.dan.geiranger.ui;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcollapsiblepanel.FCollapsiblePanel;
import com.dan.fcombobox.FComboBox;
import com.dan.fslider.FSlider;
import com.dan.geiranger.app.Bibliothek;
import com.dan.geiranger.camera.Ansichten;
import com.dan.geiranger.camera.Camera;
import com.dan.geiranger.gl.ComputeProbe;
import com.dan.geiranger.gl.Quality;
import com.dan.geiranger.gl.Renderer;
import com.dan.geiranger.gl.SceneView;
import com.dan.geiranger.scene.SceneClock;
import com.dan.geiranger.scene.Sun;
import com.dan.geiranger.wiring.Wiring;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * Das Bedienfeld rechts als Klappblöcke (aus Rungholt, entschlackt). Seit Phase 1: ZEIT mit Datum, Uhrzeit,
 * Zeitraffer und Sonne; KAMERA mit den Ansichten nach Namen; SZENE mit Qualität, Auflösung, HUD, Marken, Foto und
 * Shadern; TECHNIK mit Tempo, Compute-Test, Physik, Packages und Verdrahtung. Seit Phase 3 WASSER mit „Grund zeigen“,
 * Spiegelbild und dem Wind; seit Phase 7 dort die Wasserfälle mit ihrem Abfluss (Regie oder von Hand). Seit Phase 4 WETTER mit Regiebüchern, Lagen,
 * Handreglern (Bedeckung, Regen, Nebel, Wolkenband), „Regie weiter“ und den Ebenen. Nach dem Werkbuch (Abschnitt 7)
 * kommen VERKEHR (Phase 9) und DATEN (Phase 17) dazu. Offen bleibt, was zuletzt offen war
 * (je Rechner gemerkt). Jeder Regler schreibt direkt in die öffentlichen Felder von Uhr, Kamera, Verdrahtung und
 * Renderer; es gibt keinen zweiten Zustand.
 */
public final class ControlPanel extends JPanel implements javax.swing.Scrollable {
    private final SceneView view;
    private final Renderer r;
    private final SceneClock clock;
    private final Wiring wiring;
    private final JLabel dateLbl = label(""), timeLbl = label(""), sunLbl = small(""), camLbl = small(""), perfLbl = small(""), computeLbl = small(""),
            glLbl = small(""), physLbl = small(""), linksLbl = small(""), hudLbl = small(""), scaleLbl = label(""), qualLbl = small(""), pkgLbl = small(""),
            waterLbl = small(""), fallLbl = small(""), klangLbl = small(""), regieLbl = small(""), abflussLbl = label(""), windLbl = label(""), weatherLbl = small(""), coverLbl = label(""), rainLbl = label(""), fogLbl = label(""), bandLbl = label("");
    private final FSlider time = new FSlider(0, 24 * 60 - 1, 11 * 60), scaleSl = new FSlider(50, 100, 85), expSl = new FSlider(-30, 30, 0);
    private final FCheckBox free = new FCheckBox("Frei fliegen (F)"), orbit = new FCheckBox("Von selbst kreisen (R)"),
            marks = new FCheckBox("Marken (Kernfenster, Orte)"), autoBox = new FCheckBox("Auflösung selbst regeln"),
            bed = new FCheckBox("Grund zeigen, Wasser aus (G)"), mirrorBox = new FCheckBox("Spiegelbild der Wände"),
            messBox = new FCheckBox("Belichtung im Bild messen");
    private final FSlider bftSl = new FSlider(0, 8, 2), dirSl = new FSlider(0, 35, 27), abflussSl = new FSlider(0, 300, 100);
    private final FCheckBox verkehrBox = new FCheckBox("Verkehr auf der Fv63 (Ørnevegen)"), waldBox = new FCheckBox("Wald (Birke, Erle, Kiefer, Fichte)"),
            bodenBox = new FCheckBox("Bodendecke um das Auge"), ortBox = new FCheckBox("Ort Geiranger (Häuser, Anleger, Laternen)"),
            schiffBox = new FCheckBox("Schiffe (Fähren, Kreuzfahrtschiff, Schnellboot, Kajaks)"), kielBox = new FCheckBox("Kielwasser"),
            menschenBox = new FCheckBox("Menschen (Gäste, Busgruppe, Wanderer, Treppe)");
    private final FCheckBox fallsBox = new FCheckBox("Wasserfälle"), abflussRegie = new FCheckBox("Abfluss aus der Regie (Jahreszeit, Regen, Frost)");
    private final FSlider coverSl = new FSlider(0, 80, 20), rainSl = new FSlider(0, 100, 0), fogSl = new FSlider(0, 100, 0), bandSl = new FSlider(0, 100, 0);
    private final FCheckBox cloudsBox = new FCheckBox("Wolken"), rainBox = new FCheckBox("Regen"), fogBox = new FCheckBox("Fjordnebel und Wolkenband"),
            wallBox = new FCheckBox("Schatten der Wände");
    private FComboBox lapseBox, qualBox, hudBox;
    private boolean fromScene;
    private int ticks;
    private JPanel target = this;
    private static final Preferences PREFS = prefs();
    private final java.util.List<Object[]> bloecke = new java.util.ArrayList<>();
    private final java.util.List<FCollapsiblePanel> zu = new java.util.ArrayList<>();
    private static final double[] LAPSE = {0, 1, 10, 60, 180, 720, 3600};

    private static Preferences prefs() {
        try { return Preferences.userRoot().node("com/dan/geiranger/bedienfeld"); } catch (RuntimeException | Error e) { return null; }
    }

    public ControlPanel(SceneView view, Wiring wiring) {
        this.view = view;
        this.r = view.renderer();
        this.clock = r.clock;
        this.wiring = wiring;
        setBackground(Theme.bg());
        setOpaque(true);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 8, 16, 10));

        // ============================================================== ZEIT
        block("ZEIT", true);
        put(dateLbl);
        JPanel days = grid(3);
        FButton minus = new FButton("− Tag"), heute = new FButton("21.6.2026"), plus = new FButton("+ Tag");
        minus.addActionListener(e -> { clock.shiftDays(-1); syncFromScene(); view.requestFocusInWindow(); });
        plus.addActionListener(e -> { clock.shiftDays(1); syncFromScene(); view.requestFocusInWindow(); });
        heute.setToolTipText("Zurück zum Start der Szene: 21. Juni 2026, 11:00 Uhr MESZ");
        heute.addActionListener(e -> { clock.set(SceneClock.START_DATE, SceneClock.START_HOUR); syncFromScene(); view.requestFocusInWindow(); });
        days.add(minus); days.add(heute); days.add(plus);
        put(days);
        FComboBox monthBox = new FComboBox(new Object[]{"Monat springen …", "21. Januar", "21. Februar", "21. März", "21. April", "21. Mai", "21. Juni",
                "21. Juli", "21. August", "21. September", "21. Oktober", "21. November", "21. Dezember"});
        monthBox.setToolTipText("Zum 21. eines Monats im laufenden Jahr, gleiche Uhrzeit (Schmelze, gefrorene Fälle und gelbe Birken kommen mit Phase 14)");
        monthBox.addActionListener(e -> {
            int i = monthBox.getSelectedIndex();
            if (i <= 0) return;
            clock.set(LocalDate.of(clock.date().getYear(), i, 21), clock.hour());
            monthBox.setSelectedIndex(0);
            syncFromScene();
            view.requestFocusInWindow();
        });
        put(monthBox);
        put(timeLbl);
        put(time);
        time.addChangeListener(e -> { if (!fromScene) clock.setHour(time.getValue() / 60.0); labels(); });
        put(label("Zeitraffer"));
        lapseBox = new FComboBox(new Object[]{"Uhr steht", "Echtzeit", "10-fach", "1 min/s", "3 min/s", "12 min/s (ein Tag in 2 min)", "1 h/s"});
        lapseBox.setSelectedIndex(1);
        lapseBox.addActionListener(e -> { clock.lapse = LAPSE[Math.max(0, lapseBox.getSelectedIndex())]; view.requestFocusInWindow(); });
        put(lapseBox);
        put(sunLbl);
        put(small("<html><body style='width:215px'>Norwegische Zeit (MEZ, im Sommer MESZ). Auf 62,1° Nord steht die Sonne im Sommer gegen 13:30 Uhr "
                + "im Süden; im Juni bleibt es die ganze Nacht hell. Der Himmel ist bis Phase 4 ein Verlauf nach der Sonnenhöhe, "
                + "die Schatten der Wände kommen mit dem Gelände und com.dan.sky.</body></html>"));

        // ============================================================== KAMERA
        block("KAMERA", true);
        put(camLbl);
        free.addActionListener(e -> { view.setMode(free.isSelected() ? Camera.Mode.FREI : Camera.Mode.KREISEN); view.requestFocusInWindow(); });
        orbit.addActionListener(e -> { view.setAutoOrbit(orbit.isSelected()); view.requestFocusInWindow(); });
        put(free);
        put(orbit);
        view.setModeListener(m -> javax.swing.SwingUtilities.invokeLater(() -> free.setSelected(m == Camera.Mode.FREI)));
        view.setOrbitListener(b -> javax.swing.SwingUtilities.invokeLater(() -> orbit.setSelected(b)));
        JPanel home = grid(1);
        FButton hb = new FButton("Pos1 Grundansicht");
        hb.addActionListener(e -> { view.goHome(); view.requestFocusInWindow(); });
        home.add(hb);
        put(home);
        // Phase 15: auf der Fähre mitfahren (Ziehen schaut umher, eine Bewegungstaste steigt aus)
        JPanel mit = grid(1);
        FButton mb = new FButton("Auf der Fähre mitfahren");
        mb.setToolTipText("Oberdeck der Fähre, die gerade fährt (Saison 16. Mai bis 14. September); W A S D oder F steigt aus");
        mb.addActionListener(e -> {
            if (!r.mitfahren())
                com.dan.foptionpane.FOptionPane.showMessageDialog(this, "<html><body style='width:300px'>Gerade ist keine Fähre im Fjord. Sie fährt vom 16. Mai bis 14. September "
                        + "ab Geiranger um 8:30, 11:40, 15:00 und 18:00, ab Hellesylt um 10:00, 13:20 und 16:30 (rund 1 h 05 min).</body></html>",
                        "Mitfahren", com.dan.foptionpane.FOptionPane.INFORMATION_MESSAGE);
            view.requestFocusInWindow();
        });
        mit.add(mb);
        put(mit);
        sub("Ansichten (Tasten 1 bis " + Ansichten.ALLE.size() + ")");
        JPanel views = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        views.setOpaque(false);
        for (Ansichten.Ansicht a : Ansichten.ALLE) {
            FButton b = new FButton(a.key() + " " + a.name());
            boolean ok = Ansichten.ready(a, Renderer.PHASE);
            b.setEnabled(ok);
            b.setToolTipText(Ansichten.describe(a, Renderer.PHASE));
            b.addActionListener(e -> ansicht(a));
            views.add(b);
            view.setKeyAction(java.awt.event.KeyEvent.VK_0 + a.key(), () -> javax.swing.SwingUtilities.invokeLater(() -> ansicht(a)));
        }
        put(views);
        put(small("<html><body style='width:215px'>Seit Phase 2 auf dem Gelände der HGT-Kacheln: die Plattform auf 617 m mit Blick den Fjord hinab, "
                + "„Vom Wasser“ vor den Sieben Schwestern, Skageflå auf 265 m über Friaren.</body></html>"));

        // ============================================================== WASSER (Phase 3)
        block("WASSER", false);
        put(waterLbl);
        bed.setSelected(r.showBed);
        bed.addActionListener(e -> { r.setShowBed(bed.isSelected()); view.requestFocusInWindow(); });
        put(bed);
        mirrorBox.setSelected(r.showMirror);
        mirrorBox.addActionListener(e -> { r.showMirror = mirrorBox.isSelected(); view.requestFocusInWindow(); });
        put(mirrorBox);
        // Phase 7: die Wasserfälle und ihr Abfluss (F13 A)
        sub("Wasserfälle (Phase 7)");
        put(fallLbl);
        fallsBox.setSelected(r.faelle.on);
        fallsBox.addActionListener(e -> { r.faelle.on = fallsBox.isSelected(); view.requestFocusInWindow(); });
        put(fallsBox);
        if (r.strasse.present()) {
            verkehrBox.setSelected(r.strasse.roads.showTraffic);
            verkehrBox.addActionListener(e -> { r.strasse.roads.showTraffic = verkehrBox.isSelected(); view.requestFocusInWindow(); });
            put(verkehrBox);
        }
        waldBox.setSelected(r.wald.showForest);
        waldBox.addActionListener(e -> { r.wald.showForest = waldBox.isSelected(); view.requestFocusInWindow(); });
        put(waldBox);
        bodenBox.setSelected(r.wald.showGround);
        bodenBox.addActionListener(e -> { r.wald.showGround = bodenBox.isSelected(); view.requestFocusInWindow(); });
        put(bodenBox);
        ortBox.setSelected(r.ort.show);
        ortBox.addActionListener(e -> { r.ort.show = ortBox.isSelected(); view.requestFocusInWindow(); });
        put(ortBox);
        schiffBox.setSelected(r.flotte.show);
        schiffBox.addActionListener(e -> { r.flotte.show = schiffBox.isSelected(); view.requestFocusInWindow(); });
        put(schiffBox);
        kielBox.setSelected(r.flotte.kielwasser.on);
        kielBox.addActionListener(e -> { r.flotte.kielwasser.on = kielBox.isSelected(); view.requestFocusInWindow(); });
        put(kielBox);
        menschenBox.setSelected(r.leute.show);
        menschenBox.addActionListener(e -> { r.leute.show = r.leute.leute.show = menschenBox.isSelected(); view.requestFocusInWindow(); });
        put(menschenBox);
        abflussRegie.setSelected(r.abfluss().hand < 0);
        abflussRegie.addActionListener(e -> {
            r.abfluss().hand = abflussRegie.isSelected() ? -1 : abflussSl.getValue() / 100.0;
            labels();
            view.requestFocusInWindow();
        });
        put(abflussRegie);
        put(abflussLbl);
        put(abflussSl);
        abflussSl.setToolTipText("Abfluss von Hand als Anteil am Juni (0 bis 300 %); hält, bis „Abfluss aus der Regie“ wieder an ist");
        abflussSl.addChangeListener(e -> {
            if (fromScene) return;
            r.abfluss().hand = abflussSl.getValue() / 100.0;
            abflussRegie.setSelected(false);
            labels();
        });
        put(small("<html><body style='width:215px'>Die Fälle rechnet com.dan.fall im Lauf über Fels: das Wasser gleitet über die "
                + "Wand, springt ab, wo sie schneller wegfällt als die Wurfbahn, und kommt im Fjord mit Gischt und Ringwellen an. "
                + "Der Abfluss folgt der Schmelze (Juni voll, Februar unter dem Eis) und dem Regen; nach Regen laufen sechs Rinnsale "
                + "an den steilsten Wänden. Ansichten 8 und 9 zeigen die Schwestern und Friaren vom Wasser.</body></html>"));
        view.setKeyAction(java.awt.event.KeyEvent.VK_G, () -> javax.swing.SwingUtilities.invokeLater(() -> {
            r.setShowBed(!r.showBed);
            bed.setSelected(r.showBed);
            r.hudMeldung(r.showBed ? "Grund zeigen: Wasser aus, Linien alle 50 m" : "Wasser wieder an");
        }));
        sub("Wind (von Hand hält ihn, „Regie weiter“ im Block WETTER gibt ihn frei)");
        put(windLbl);
        put(bftSl);
        bftSl.addChangeListener(e -> { if (!fromScene) wiring.hand(com.dan.weather.Weather.Var.WIND, com.dan.geiranger.wiring.Beaufort.mid(bftSl.getValue())); labels(); });
        put(label("aus Richtung"));
        put(dirSl);
        dirSl.addChangeListener(e -> { if (!fromScene) wiring.hand(com.dan.weather.Weather.Var.DIRECTION, dirSl.getValue() * 10); labels(); });
        put(small("<html><body style='width:215px'>Das Wasser rechnet com.dan.ocean mit dem Preset „Fjord“: tiefer See ohne Dünung, kurze "
                + "Streichlänge zwischen den Wänden, Wind nur fleckig als Kräuseln. Ein stiller Morgen (Bft 0 bis 1) zeigt die Wände im "
                + "Spiegel. „Grund zeigen“ lässt das Wasser weg: der gemalte Grund mit Ufer 10 m, Mitte 150 m und Rinne 250 m, davor das "
                + "Delta der Geirangelva; die Kamera darf dann hinab.</body></html>"));

        // ============================================================== WETTER (Phase 4)
        block("WETTER", false);
        put(weatherLbl);
        java.util.List<com.dan.weather.Scenario> wetterBuecher = com.dan.geiranger.wiring.KlimaGeiranger.regiebuecher();
        java.util.List<Object> wb = new java.util.ArrayList<>();
        wb.add("Regiebuch …");
        for (com.dan.weather.Scenario sc : wetterBuecher)
            wb.add(sc.name() + String.format(Locale.GERMAN, " (ab %d:%02d%s)", (int) sc.startHour(), (int) Math.round(sc.startHour() % 1 * 60), sc.lapse() > 1 ? ", Zeitraffer" : ""));
        FComboBox bookBox = new FComboBox(wb.toArray());
        bookBox.setToolTipText("Startet ein Regiebuch am laufenden Tag: die Uhr springt an seinen Beginn, der Zeitraffer folgt dem Buch");
        bookBox.addActionListener(e -> {
            int i = bookBox.getSelectedIndex();
            if (i <= 0) return;
            com.dan.weather.Scenario sc = wetterBuecher.get(i - 1);
            clock.set(clock.date(), sc.startHour());
            clock.lapse = sc.lapse();
            wiring.start(sc);
            r.readapt();
            r.hudMeldung("Regiebuch „" + sc.name() + "“");
            bookBox.setSelectedIndex(0);
            syncFromScene();
            view.requestFocusInWindow();
        });
        put(bookBox);
        java.util.List<Object> lagen = new java.util.ArrayList<>();
        lagen.add("Lage von Hand …");
        for (com.dan.weather.Weather.Phase ph : com.dan.weather.Weather.Phase.values()) if (ph != com.dan.weather.Weather.Phase.ORKAN) lagen.add(ph.label);
        FComboBox phaseBox = new FComboBox(lagen.toArray());
        phaseBox.setToolTipText("Freie Regie ab jetzt, beginnend mit dieser Lage (danach wählt das Klima „Geiranger“)");
        phaseBox.addActionListener(e -> {
            int i = phaseBox.getSelectedIndex();
            if (i <= 0) return;
            com.dan.weather.Weather.Phase ph = com.dan.weather.Weather.Phase.values()[i - 1];
            wiring.startFree(ph);
            r.hudMeldung("Freie Regie: " + ph.label);
            phaseBox.setSelectedIndex(0);
            view.requestFocusInWindow();
        });
        put(phaseBox);
        put(coverLbl);
        put(coverSl);
        coverSl.addChangeListener(e -> { if (!fromScene) wiring.hand(com.dan.weather.Weather.Var.COVER, coverSl.getValue() / 10.0); labels(); });
        put(rainLbl);
        put(rainSl);
        rainSl.addChangeListener(e -> { if (!fromScene) wiring.hand(com.dan.weather.Weather.Var.RAIN, rainSl.getValue() / 10.0); labels(); });
        put(fogLbl);
        put(fogSl);
        fogSl.addChangeListener(e -> { if (!fromScene) wiring.hand(com.dan.weather.Weather.Var.FOG, fogSl.getValue() / 100.0); labels(); });
        put(bandLbl);
        put(bandSl);
        bandSl.addChangeListener(e -> { if (!fromScene) wiring.bandHand = bandSl.getValue() / 100.0; labels(); });
        JPanel resume = grid(1);
        FButton rb = new FButton("Regie weiter");
        rb.setToolTipText("Alle Handregler lösen; die Größen gleiten mit ihrer Trägheit zur Regie zurück");
        rb.addActionListener(e -> { if (!wiring.regie) wiring.startFree(com.dan.weather.Weather.Phase.KLAR); wiring.resumeRegie(); view.requestFocusInWindow(); });
        resume.add(rb);
        put(resume);
        cloudsBox.setSelected(r.showClouds);
        cloudsBox.addActionListener(e -> { r.showClouds = cloudsBox.isSelected(); view.requestFocusInWindow(); });
        rainBox.setSelected(r.showRain);
        rainBox.addActionListener(e -> { r.showRain = rainBox.isSelected(); view.requestFocusInWindow(); });
        fogBox.setSelected(r.showFog);
        fogBox.addActionListener(e -> { r.showFog = fogBox.isSelected(); view.requestFocusInWindow(); });
        wallBox.setSelected(r.schatten.on);
        wallBox.addActionListener(e -> { r.schatten.on = wallBox.isSelected(); r.schatten.invalidate(); view.requestFocusInWindow(); });
        put(cloudsBox);
        put(rainBox);
        put(fogBox);
        put(wallBox);
        put(small("<html><body style='width:215px'>Klima „Geiranger“: im Windschatten der Wände wenig Wind, am Tag fjordeinwärts aus "
                + "Westen; Fronten vom Atlantik bringen Landregen, die Wolken hängen dann in halber Höhe an den Wänden (Wolkenband); "
                + "nach stillen Nächten liegt Nebel im Fjord. Die Schatten der Wände rechnet die Grafikkarte je Sonnenstand aus dem "
                + "Gelände: an einem Junimorgen erreicht die Sonne das Wasser erst spät.</body></html>"));

        // ============================================================== REGIEBÜCHER (Phase 16)
        block("REGIEBÜCHER", false);
        java.util.List<com.dan.geiranger.scene.Regie.Book> buecher;
        try { buecher = com.dan.geiranger.scene.Regie.all(); } catch (RuntimeException ex) { buecher = java.util.List.of(); put(small("Regiebücher nicht lesbar: " + ex.getMessage())); }
        FComboBox regieBox = new FComboBox(buecher.toArray());
        put(regieBox);
        JLabel buchInfo = small("");
        Runnable zeigeBuch = () -> {
            if (regieBox.getSelectedItem() instanceof com.dan.geiranger.scene.Regie.Book b)
                buchInfo.setText("<html><body style='width:215px'>" + esc(b.about()) + String.format(Locale.GERMAN, " · %d:%02d min ohne Warten, %d Fotos", (int) b.length() / 60, (int) b.length() % 60, b.fotos()) + "</body></html>");
        };
        regieBox.addActionListener(e -> { zeigeBuch.run(); view.requestFocusInWindow(); });
        zeigeBuch.run();
        JPanel regieRow = grid(2);
        FButton regieGo = new FButton("Von vorn"), regieHalt = new FButton("Anhalten");
        regieGo.setToolTipText("Das Regiebuch von vorn: Datum, Uhrzeit, Wetter, Ansichten, Fahrten; am Ende ein Blatt mit den Fotos in docs/status");
        FCheckBox imKino = new FCheckBox("Im Kino zeigen");
        imKino.setSelected(true);
        imKino.setToolTipText("Ganzer Bildschirm ohne Bedienfeld, Breitwand mit Balken, Vor- und Abspann, Untertitel aus den Stichworten, "
                + "Überblendung bei jedem Schnitt. Esc beendet das Kino (das Regiebuch läuft weiter), Leertaste hält an, F12 fotografiert.");
        regieGo.addActionListener(e -> {
            if (regieBox.getSelectedItem() instanceof com.dan.geiranger.scene.Regie.Book b) { if (imKino.isSelected()) r.kinoStart(b); else r.regieStart(b); }
            view.requestFocusInWindow();
        });
        regieHalt.addActionListener(e -> { r.regieStop(); view.requestFocusInWindow(); });
        regieRow.add(regieGo);
        regieRow.add(regieHalt);
        put(regieRow);
        put(imKino);
        put(buchInfo);
        put(regieLbl);
        put(small("<html><body style='width:215px'>Drei Regiebücher als Text (scene/regie/*.txt): ein Junitag im Zeitraffer, im Bus über die "
                + "Ørnesvingen, mit der Fähre von Hellesylt. Jedes setzt Datum, Uhr, Zeitraffer und Wetter, wechselt Ansichten, Fahrten und "
                + "Mitfahrten, wartet auf die Fähre („warte“) und fotografiert; am Ende liegt ein Blatt mit allen Fotos in docs/status "
                + "(Regiebuch_….png). Im Kino: Esc heraus, Leertaste hält an.</body></html>"));

        // ============================================================== KLANG (Phase 15)
        block("KLANG", false);
        FCheckBox tonBox = new FCheckBox("Ton (Lautsprecher)");
        boolean tonAn = PREFS == null || PREFS.getBoolean("ton", true);
        tonBox.setSelected(tonAn);
        r.klang.ton(tonAn);
        tonBox.addActionListener(e -> {
            r.klang.ton(tonBox.isSelected());
            if (PREFS != null) PREFS.putBoolean("ton", tonBox.isSelected());
            view.requestFocusInWindow();
        });
        put(tonBox);
        put(label("Lautstärke"));
        FSlider lautSl = new FSlider(0, 200, (int) Math.round(r.klang.s.volume * 100));
        lautSl.addChangeListener(e -> r.klang.s.volume = lautSl.getValue() / 100.0);
        put(lautSl);
        put(klangLbl);
        put(small("<html><body style='width:215px'>Erzeugter Ton (com.dan.soundscape), keine Aufnahmen: die Fälle nach Abstand und Abfluss, hinter Graten "
                + "leiser; die Geirangelva; das Horn der Fähre beim Ablegen und vor den Sieben Schwestern mit dem Echo der Wände; "
                + "Maschinen, Busse in den Kehren, Stimmen auf der Plattform, Möwen, Austernfischer, Krähen, Schwalben, Schafe; "
                + "Wind, Regen, Wellen; sonntags um 10:45 die Kirchenglocke.</body></html>"));

        // ============================================================== SZENE
        block("SZENE", false);
        put(label("Qualität"));
        qualBox = new FComboBox(Quality.values());
        qualBox.setSelectedItem(r.quality());
        qualBox.addActionListener(e -> { r.setQuality((Quality) qualBox.getSelectedItem()); view.requestFocusInWindow(); });
        put(qualBox);
        put(qualLbl);
        autoBox.setSelected(r.autoScale);
        autoBox.addActionListener(e -> { r.autoScale = autoBox.isSelected(); if (!r.autoScale) r.renderScale = scaleSl.getValue() / 100.0; view.requestFocusInWindow(); });
        put(autoBox);
        put(scaleLbl);
        put(scaleSl);
        scaleSl.addChangeListener(e -> {
            if (fromScene) return;
            r.autoScale = false;
            autoBox.setSelected(false);
            r.renderScale = scaleSl.getValue() / 100.0;
        });
        put(label("Belichtung (Blenden von Hand)"));
        put(expSl);
        expSl.addChangeListener(e -> { if (!fromScene) r.exposureBias = expSl.getValue() / 10.0; });
        messBox.setSelected(r.bildMessung);
        messBox.addActionListener(e -> { r.bildMessung = messBox.isSelected(); r.readapt(); view.requestFocusInWindow(); });
        put(messBox);
        put(label("HUD"));
        hudBox = new FComboBox(new Object[]{"kurz", "voll", "aus"});
        hudBox.addActionListener(e -> {
            int i = hudBox.getSelectedIndex();
            r.showHud = i != 2;
            r.hudFull = i == 1;
            view.requestFocusInWindow();
        });
        put(hudBox);
        marks.setSelected(r.showMarks);
        marks.addActionListener(e -> { r.showMarks = marks.isSelected(); view.requestFocusInWindow(); });
        put(marks);
        JPanel pic = grid(2);
        FButton photo = new FButton("Foto (F12)"), shaders = new FButton("Shader neu (F5)");
        photo.addActionListener(e -> { r.photoRequest = true; view.requestFocusInWindow(); });
        shaders.addActionListener(e -> { r.requestShaderReload(); view.requestFocusInWindow(); });
        pic.add(photo); pic.add(shaders);
        put(pic);
        put(small("<html><body style='width:215px'>Fotos gehen nach docs/status. Marken: gelb die Kernfenster (F1 A, dort Gelände bis 2 m), "
                + "weiß die Aussichtspunkte, hell türkis die Fälle, grün die Höfe Skageflå und Knivsflå.</body></html>"));

        // ============================================================== DATEN (Phase 17)
        datenBlock();

        // ============================================================== TECHNIK
        block("TECHNIK", false);
        put(perfLbl);
        put(computeLbl);
        put(glLbl);
        put(physLbl);
        sub("Packages");
        put(pkgLbl);
        sub("Verdrahtung");
        put(linksLbl);
        sub("Alle Zeilen des HUD");
        put(hudLbl);
        sub("Protokoll (zuletzt)");
        put(protLbl);
        sub("Dauerlauf (Abnahme „fertig“)");
        put(dauerLbl);
        JPanel dauerRow = grid(2);
        FButton dauerGo = new FButton("Dauerlauf 30 min"), dauerHalt = new FButton("Abbrechen");
        dauerGo.setToolTipText("Abnahme „fertig“: je Runde „Ein Junitag“, zwei Tage im Zeitraffer, „Mit der Fähre von Hellesylt“ und „Fahrt über die Ørnesvingen“ im Kino; "
                + "mindestens zwei Runden. Esc beendet nur das Kino, nicht den Dauerlauf.");
        dauerGo.addActionListener(e -> { dauerlauf(30); view.requestFocusInWindow(); });
        dauerHalt.addActionListener(e -> com.dan.geiranger.app.Dauerlauf.anhalten());
        dauerRow.add(dauerGo); dauerRow.add(dauerHalt);
        put(dauerRow);

        reihenfolge();
        for (Object[] b : bloecke) ((FCollapsiblePanel) b[0]).setBodyComponent((JComponent) b[1]);
        for (FCollapsiblePanel p : zu) p.setExpanded(false, false);
        add(Box.createVerticalGlue());
        view.setKeyAction(java.awt.event.KeyEvent.VK_F1, () -> javax.swing.SwingUtilities.invokeLater(this::tasten));
        view.setKeyAction(java.awt.event.KeyEvent.VK_SPACE, () -> javax.swing.SwingUtilities.invokeLater(() ->
                lapseBox.setSelectedIndex(clock.lapse == 0 ? 1 : 0)));
        syncFromScene();
        new Timer(500, e -> {
            // Befund aus Glacier: solange eine Auswahlliste offen ist, nichts auffrischen; neue Texte ändern die Höhe
            // der Zeilen, der Block legt sich neu aus, und Swing schließt die Liste, sobald sich ihr Feld bewegt
            if (popupOpen(this) || menuOpen()) return;
            syncFromScene();
            if (ticks++ % 2 == 0) technik();
            datenZeilen();
            nachmessen();
            dauerZeile();
        }).start();
    }

    // ============================================================== Phase 18: Dauerlauf

    private final JLabel dauerLbl = small("<html><body style='width:215px'>Drei Regiebücher im Kino und zwei Tage im Zeitraffer je Runde, mindestens zwei Runden; Bericht in docs/status/Dauerlauf_….</body></html>");

    private void dauerlauf(double minuten) {
        boolean ok = com.dan.geiranger.app.Dauerlauf.starten(view.renderer(), minuten, png -> javax.swing.SwingUtilities.invokeLater(() ->
                dauerLbl.setText(png == null ? "Dauerlauf ohne Bericht beendet (siehe Konsole)." : "Fertig: " + png.getName() + " (und .txt) in docs/status")));
        if (!ok) dauerLbl.setText("Es läuft schon ein Dauerlauf.");
    }

    /** Die Zeile des Dauerlaufs (im Takt des Bedienfelds). */
    private void dauerZeile() {
        String z = com.dan.geiranger.app.Dauerlauf.zeile();
        if (!z.isEmpty()) dauerLbl.setText("<html><body style='width:215px'>" + z + "</body></html>");
    }

    // ============================================================== Phase 17: DATEN

    private com.dan.geiranger.data.Chronik chronik;
    private final JLabel datenLbl = small(""), chronikLbl = small(""), heuteLbl = small(""), herkunftLbl = small(""), presetLbl = small(""), protLbl = small(""),
            gemerktLbl = small(""), merkLbl = small("");
    private FComboBox paketBox, presetBox, sitzungBox, gemerktBox, merkBox;
    private java.util.List<com.dan.geiranger.data.Daten.PaketPreset> presetListe = java.util.List.of();
    private java.util.List<com.dan.geiranger.data.Daten.Ansicht> gemerktListe = java.util.List.of();
    private java.util.List<com.dan.geiranger.data.Daten.Merkpunkt> merkListe = java.util.List.of();
    private volatile String datenNote = "";
    private long naechsteListe;

    /** Phase 17: die Chronik der laufenden Szene (aus der App, sobald sie steht). */
    public void chronik(com.dan.geiranger.data.Chronik c) {
        chronik = c;
        neuLesen();
    }

    private static com.dan.geiranger.data.Speicher sp() { return com.dan.geiranger.data.Datenseite.speicher(); }

    /** Eine Auswahl neu füllen, ohne dass ihr Hörer feuert; die Auswahl bleibt, wenn es den Eintrag noch gibt. */
    private void fuellen(FComboBox box, java.util.List<?> l, String wahl) {
        fromScene = true;
        try {
            box.removeAllItems();
            int k = -1;
            for (int i = 0; i < l.size(); i++) { box.addItem(l.get(i)); if (l.get(i).toString().equals(wahl)) k = i; }
            if (k >= 0) box.setSelectedIndex(k); else if (!l.isEmpty()) box.setSelectedIndex(0);
        } finally { fromScene = false; }
    }

    private static String gewaehlt(FComboBox box) { Object o = box.getSelectedItem(); return o == null ? null : o.toString(); }

    /** Im Faden der Daten arbeiten, danach auf dem Swing-Faden weiter; Fehler landen im Protokoll und in einer Meldung. */
    private <T> void hinten(String was, java.util.concurrent.Callable<T> arbeit, java.util.function.Consumer<T> danach) {
        com.dan.store.Dienst.spaeter(() -> {
            try {
                T t = arbeit.call();
                javax.swing.SwingUtilities.invokeLater(() -> danach.accept(t));
            } catch (Exception ex) {
                com.dan.store.Protokoll.fehler("Bedienfeld", was + ": " + ex.getMessage(), ex instanceof RuntimeException ? ex : null);
                javax.swing.SwingUtilities.invokeLater(() -> com.dan.foptionpane.FOptionPane.showMessageDialog(this, was + " ging nicht:\n" + ex.getMessage(),
                        "Geiranger · " + was, com.dan.foptionpane.FOptionPane.ERROR_MESSAGE));
            }
        });
    }

    /** Eine Sitzung mit Chronik in der Auswahl. */
    private record Sitzungszeile(long id, long beginn, long stunden, long fahrten) {
        @Override public String toString() {
            String w = beginn <= 0 ? "" : java.time.format.DateTimeFormatter.ofPattern("dd.MM. HH:mm").format(java.time.Instant.ofEpochMilli(beginn).atZone(java.time.ZoneId.systemDefault()));
            return "Sitzung " + id + " · " + w + " · " + stunden + " Stunden, " + fahrten + " Fahrten" + (id == com.dan.store.Dienst.sitzung() ? " (diese)" : "");
        }
    }

    /**
     * Block DATEN (F37 A): Speicherort, Verbinden, Einrichten mit Fortschritt, Prüfen, Übernehmen, Sitzung sichern, Weiter
     * wie zuletzt, Chronik mit Tageskarte, Presets der Packages, gemerkte Ansichten (Taste V) und Merkpunkte (Umschalt +
     * Klick).
     */
    private void datenBlock() {
        block("DATEN", false);
        put(datenLbl);
        put(herkunftLbl);
        JPanel g = new JPanel(new java.awt.GridLayout(0, 2, 6, 4));
        g.setOpaque(false);
        FButton verb = new FButton("Verbinden …"), ohne = new FButton("Ohne Datenbank"), einr = new FButton("Einrichten"), pruef = new FButton("Prüfen"),
                ueber = new FButton("Übernehmen"), sichern = new FButton("Sitzung sichern"), weiter = new FButton("Weiter wie zuletzt"), karte = new FButton("Tageskarte");
        verb.setToolTipText("Mit dem Schema aus db/db.properties verbinden (fragt nach dem Passwort, wenn keins hinterlegt ist)");
        verb.addActionListener(e -> verbinden());
        ohne.setToolTipText("Mit Dateien in daten/ weiterarbeiten");
        ohne.addActionListener(e -> {
            com.dan.geiranger.data.Datenseite.setzen(new com.dan.geiranger.data.DateiSpeicher(com.dan.geiranger.data.DateiSpeicher.ordner()));
            neuLesen();
        });
        einr.setToolTipText("Die Skripte aus db/sql ausführen und alles prüfen; löscht nie etwas");
        einr.addActionListener(e -> einrichter(false));
        pruef.setToolTipText("Nur prüfen: Tabellen, Spalten, Sichten, Metadaten, räumliche Indizes, Zahl der Startdaten");
        pruef.addActionListener(e -> einrichter(true));
        ueber.setToolTipText("Chronik, Merkpunkte, eigene Regiebücher, Ansichten und Presets aus daten/ in die Datenbank (die CSV heißen danach *.uebernommen)");
        ueber.addActionListener(e -> uebernehmen());
        sichern.setToolTipText("Den Zustand jetzt sichern (sonst alle 2 min und beim Ende)");
        sichern.addActionListener(e -> {
            com.dan.store.Dienst.spaeter(() -> com.dan.store.Dienst.sichern(true));
            r.hudMeldung("Sitzung gesichert");
            view.requestFocusInWindow();
        });
        weiter.setToolTipText("Die letzte gesicherte Sitzung herstellen: Uhr, Datum, Zeitraffer, Wetter, Kamera, Ebenen, Qualität, Ton, laufendes Regiebuch");
        weiter.addActionListener(e -> weiterWieZuletzt());
        karte.setToolTipText("Draufsicht des Szenentags als PNG nach docs/status: Fahrten der Schiffe, Hörner mit Echos, Busse, Wetter und Abfluss (wie GR_TAGESKARTE_V in QGIS)");
        karte.addActionListener(e -> tageskarte());
        g.add(verb); g.add(ohne); g.add(einr); g.add(pruef); g.add(ueber); g.add(sichern); g.add(weiter); g.add(karte);
        put(g);
        sub("Chronik");
        put(chronikLbl);
        put(heuteLbl);
        JPanel cg = grid(2);
        FButton schreib = new FButton("Chronik aus"), leeren = new FButton("Leeren …");
        schreib.setToolTipText("Die Chronik anhalten oder wieder schreiben lassen (die Szene läuft weiter)");
        schreib.addActionListener(e -> {
            if (chronik == null) return;
            chronik.schreiben(!chronik.schreibt());
            schreib.setText(chronik.schreibt() ? "Chronik aus" : "Chronik an");
        });
        sitzungBox = new FComboBox();
        leeren.setToolTipText("Stunden, Tage, Fahrten, Busse und Läufe der Regiebücher der gewählten Sitzung löschen");
        leeren.addActionListener(e -> {
            if (!(sitzungBox.getSelectedItem() instanceof Sitzungszeile s)) return;
            if (com.dan.foptionpane.FOptionPane.showConfirmDialog(this, "Die Chronik der Sitzung " + s.id + " löschen?\n" + s, "Geiranger · Chronik leeren",
                    com.dan.foptionpane.FOptionPane.YES_NO_OPTION) != com.dan.foptionpane.FOptionPane.YES_OPTION) return;
            hinten("Chronik leeren", () -> sp().chronikLeeren(s.id), n -> { r.hudMeldung("Chronik der Sitzung " + s.id + ": " + n + " Zeilen gelöscht"); neuLesen(); });
        });
        cg.add(schreib);
        cg.add(leeren);
        put(sitzungBox);
        put(cg);
        sub("Presets der Packages");
        java.util.List<String> pk = new java.util.ArrayList<>();
        for (String k : com.dan.geiranger.data.Startdaten.PAKETE) pk.add(k.substring(k.lastIndexOf('.') + 1) + " · " + k.substring(0, k.lastIndexOf('.')));
        paketBox = new FComboBox(pk.toArray());
        presetBox = new FComboBox();
        paketBox.addActionListener(e -> { if (!fromScene) listePresets(null); });
        put(paketBox);
        put(presetBox);
        FButton an = new FButton("Anwenden"), unter = new FButton("Speichern unter …");
        an.setToolTipText("Alle Felder des Presets in das laufende Objekt der Szene schreiben");
        an.addActionListener(e -> presetAnwenden());
        unter.setToolTipText("Die Felder, wie sie jetzt in der Szene stehen, als eigenes Preset");
        unter.addActionListener(e -> presetSpeichern());
        JPanel pg = grid(2);
        pg.add(an);
        pg.add(unter);
        put(pg);
        put(presetLbl);
        sub("Gemerkte Ansichten (Taste V merkt)");
        gemerktBox = new FComboBox();
        put(gemerktBox);
        FButton gh = new FButton("Hin"), gl = new FButton("Löschen");
        gh.addActionListener(e -> gemerkteAnsicht());
        gl.addActionListener(e -> {
            int i = gemerktBox.getSelectedIndex();
            if (i < 0 || i >= gemerktListe.size()) return;
            long id = gemerktListe.get(i).id();
            hinten("Ansicht löschen", () -> { sp().loeschenAnsicht(id); return null; }, x -> listeGemerkt());
        });
        JPanel gg = grid(2);
        gg.add(gh);
        gg.add(gl);
        put(gg);
        put(gemerktLbl);
        view.setKeyAction(java.awt.event.KeyEvent.VK_V, () -> javax.swing.SwingUtilities.invokeLater(this::ansichtMerken));
        sub("Merkpunkte (Umschalt + Klick ins Gelände)");
        merkBox = new FComboBox();
        merkBox.addActionListener(e -> { if (!fromScene) merkZeile(); });
        put(merkBox);
        FButton mh = new FButton("Hin"), ml = new FButton("Löschen");
        mh.setToolTipText("Die Kamera an das Auge des Merkpunkts, Blick wie beim Setzen, die Uhr auf seine Szenenzeit");
        mh.addActionListener(e -> merkpunktHin());
        ml.addActionListener(e -> {
            int i = merkBox.getSelectedIndex();
            if (i < 0 || i >= merkListe.size()) return;
            long id = merkListe.get(i).id();
            hinten("Merkpunkt löschen", () -> { sp().loeschenMerkpunkt(id); return null; }, x -> listeMerk());
        });
        JPanel mg = grid(2);
        mg.add(mh);
        mg.add(ml);
        put(mg);
        put(merkLbl);
        view.setShiftClickListener((x, y, w, h) -> javax.swing.SwingUtilities.invokeLater(() -> merkpunktSetzen(x, y, w, h)));
        put(small("<html><body style='width:215px'>Schema GEIRANGER auf PDBORCL (SRID 8307) oder ohne Datenbank daten/. Beim Start liest die App Fahrplan, "
                + "Klima, Höhenstufen, Wetter- und Regiebücher daraus; die Chronik schreibt je Szenenstunde Wetter und Abfluss, je Tag Schnee und Laub, jede "
                + "Fahrt der Fähren und des Kreuzfahrtschiffs mit Horn und Echos und jeden Bus. Das Protokoll geht alle 5 s, der Zustand alle 2 min und beim Ende "
                + "dorthin. In QGIS: GR_GEIRANGER_V (die Szene), GR_TAGESKARTE_V (Filter auf TAG), GR_FALL_V; Anleitung in db/README.md.</body></html>"));
    }

    private String paket() { String s = gewaehlt(paketBox); return s == null ? null : s.substring(s.indexOf(" · ") + 3); }
    private String klasse() { String s = gewaehlt(paketBox); return s == null ? null : s.substring(0, s.indexOf(" · ")); }

    /** Das laufende Objekt der Szene zu einer Klasse mit Presets, oder null. */
    Object objekt(String klasse) {
        if (klasse == null) return null;
        return switch (klasse) {
            case "Ocean" -> r.wasser.ocean;
            case "Sky" -> r.wetter.sky;
            case "Clouds" -> r.wetter.clouds;
            case "Fog" -> r.wetter.nebel;
            case "Rain" -> r.wetter.rain;
            case "Cliff" -> r.fels.cliff;
            case "Soundscape" -> r.klang.s;
            default -> null;
        };
    }

    private void listePresets(String wahl) {
        String p = paket(), k = klasse();
        if (p == null) return;
        hinten("Presets lesen", () -> sp().presets(p, k), l -> {
            presetListe = l;
            fuellen(presetBox, l, wahl);
            Object o = objekt(k);
            txt(presetLbl, "<html><body style='width:215px'>" + l.size() + " Presets · " + (o == null ? "nicht in der Szene (nur zum Nachlesen)" : "in der Szene: "
                    + o.getClass().getSimpleName() + (k.equals("Fog") ? " (Fjordnebel)" : "")) + "</body></html>");
        });
    }

    private void presetAnwenden() {
        int i = presetBox.getSelectedIndex();
        if (i < 0 || i >= presetListe.size()) return;
        var pr = presetListe.get(i);
        Object o = objekt(pr.klasse());
        if (o == null) { txt(presetLbl, pr.klasse() + " läuft nicht in der Szene."); return; }
        java.util.List<String> f = com.dan.store.Felder.schreiben(o, pr.felder());
        txt(presetLbl, "<html><body style='width:215px'>„" + esc(pr.name()) + "“ angewandt: " + (pr.felder().size() - f.size()) + " Felder"
                + (f.isEmpty() ? "" : ", nicht: " + esc(String.join(", ", f))) + "</body></html>");
        com.dan.store.Protokoll.info("Preset", pr.klasse() + " ← „" + pr.name() + "“");
        view.requestFocusInWindow();
    }

    private void presetSpeichern() {
        String p = paket(), k = klasse();
        Object o = objekt(k);
        if (o == null) { txt(presetLbl, k + " läuft nicht in der Szene."); return; }
        String n = com.dan.foptionpane.FOptionPane.showInputDialog(this, "Name des Presets für " + k, "Geiranger · Preset speichern", com.dan.foptionpane.FOptionPane.QUESTION_MESSAGE);
        if (n == null || n.isBlank()) return;
        var pr = new com.dan.geiranger.data.Daten.PaketPreset(p, k, n.strip(), "Aus der Szene am " + java.time.LocalDate.now(), com.dan.geiranger.data.Daten.EIGEN,
                com.dan.store.Felder.lesen(o));
        hinten("Preset speichern", () -> { sp().speichern(pr); return null; }, x -> listePresets(pr.name()));
    }

    private void listeGemerkt() {
        hinten("Ansichten lesen", () -> sp().ansichten(), l -> {
            java.util.List<com.dan.geiranger.data.Daten.Ansicht> g = new java.util.ArrayList<>();
            for (var a : l) if ("GEMERKT".equals(a.art())) g.add(a);
            gemerktListe = g;
            fuellen(gemerktBox, g, null);
            txt(gemerktLbl, g.size() + " gemerkte Ansichten in " + sp().wo());
        });
    }

    /** Ein Bild der Szene verkleinert als PNG (für Vorschau), oder null. */
    static byte[] vorschau(java.awt.image.BufferedImage img) {
        try {
            java.awt.image.BufferedImage s = new java.awt.image.BufferedImage(320, (int) Math.round(320.0 * img.getHeight() / img.getWidth()), java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2 = s.createGraphics();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(img, 0, 0, s.getWidth(), s.getHeight(), null);
            g2.dispose();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(s, "png", bo);
            return bo.toByteArray();
        } catch (Exception ex) {
            return null;
        }
    }

    private void ansichtMerken() {
        double[] k = com.dan.geiranger.data.Zustand.kamera(r);
        long ms = (long) clock.epochMillis();
        String name = com.dan.geiranger.data.Daten.text(ms) + " · " + Math.round(k[1]) + " m";
        String z = com.dan.store.Felder.alsText(com.dan.geiranger.data.Zustand.erfassen(r), "Geiranger · gemerkte Ansicht");
        r.grabRequest = img -> {
            byte[] v = vorschau(img);
            var a = new com.dan.geiranger.data.Daten.Ansicht(0, name, null, "GEMERKT", k, null, ms, z, v, com.dan.geiranger.data.Daten.EIGEN);
            long s = com.dan.store.Dienst.sitzung();
            hinten("Ansicht merken", () -> sp().speichern(a, s > 0 ? s : null), id -> { r.hudMeldung("Ansicht gemerkt: " + name); listeGemerkt(); });
        };
    }

    private void gemerkteAnsicht() {
        int i = gemerktBox.getSelectedIndex();
        if (i < 0 || i >= gemerktListe.size()) return;
        long id = gemerktListe.get(i).id();
        hinten("Ansicht lesen", () -> sp().ansicht(id), a -> {
            if (a.zustand() != null) {
                java.util.List<String> f = com.dan.geiranger.data.Zustand.anwenden(r, com.dan.store.Felder.ausText(a.zustand()));
                if (!f.isEmpty()) com.dan.store.Protokoll.warnung("Ansicht", "nicht hergestellt: " + String.join(", ", f));
            } else {
                double[] k = a.kamera();
                r.camera.stopFahrt();
                r.camera.stopMitfahrt();
                r.camera.place(k[0], k[1], k[2], k[3], k[4]);
                r.camera.snap();
                if (a.szeneMs() != null) clock.setEpochMillis(a.szeneMs());
            }
            view.setMode(r.camera.mode);
            syncFromScene();
            view.requestFocusInWindow();
        });
    }

    /** Umschalt + Klick: ein Merkpunkt mit Name und Notiz an der Stelle unter dem Zeiger (F35 A). */
    private void merkpunktSetzen(int x, int y, int w, int h) {
        double[] p = r.pickGround(x, y, w, h);
        if (p == null) { r.hudMeldung("Merkpunkt: dort ist Himmel"); return; }
        double[] k = com.dan.geiranger.data.Zustand.kamera(r);
        long ms = (long) clock.epochMillis();
        double[] u = com.dan.geiranger.scene.Site.toUtm(p[0], p[2]);
        com.dan.ftextfield.FTextfield nameF = new com.dan.ftextfield.FTextfield();
        nameF.setText(String.format(Locale.GERMAN, "Punkt auf %.0f m", p[1]));
        com.dan.ftextarea.FTextArea notiz = new com.dan.ftextarea.FTextArea(4, 30);
        notiz.setLineWrap(true);
        notiz.setWrapStyleWord(true);
        JPanel pn = new JPanel(new BorderLayout(0, 6));
        pn.add(new JLabel(String.format(Locale.GERMAN, "<html>E %.0f, N %.0f, %.0f m über NN · %.0f m vom Auge<br>%s</html>", u[0], u[1], p[1],
                Math.sqrt(Math.pow(p[0] - k[0], 2) + Math.pow(p[1] - k[1], 2) + Math.pow(p[2] - k[2], 2)), com.dan.geiranger.data.Daten.text(ms))), BorderLayout.NORTH);
        JPanel felder = new JPanel(new BorderLayout(0, 4));
        felder.add(nameF, BorderLayout.NORTH);
        felder.add(new javax.swing.JScrollPane(notiz), BorderLayout.CENTER);
        pn.add(felder, BorderLayout.CENTER);
        if (com.dan.foptionpane.FOptionPane.showConfirmDialog(this, pn, "Geiranger · Merkpunkt", com.dan.foptionpane.FOptionPane.OK_CANCEL_OPTION)
                != com.dan.foptionpane.FOptionPane.OK_OPTION) { view.requestFocusInWindow(); return; }
        String name = nameF.getText() == null || nameF.getText().isBlank() ? "Merkpunkt" : nameF.getText().strip();
        String text = notiz.getText() == null || notiz.getText().isBlank() ? null : notiz.getText().strip();
        r.grabRequest = img -> {
            byte[] v = vorschau(img);
            var m = new com.dan.geiranger.data.Daten.Merkpunkt(0, name, text, p[0], p[1], p[2], new double[]{k[0], k[1], k[2]}, k[3], k[4], ms, v,
                    com.dan.geiranger.data.Daten.EIGEN);
            long s = com.dan.store.Dienst.sitzung();
            hinten("Merkpunkt", () -> sp().speichern(m, s > 0 ? s : null), id -> { r.hudMeldung("Merkpunkt „" + name + "“ gesetzt"); listeMerk(); });
        };
        view.requestFocusInWindow();
    }

    private void listeMerk() {
        hinten("Merkpunkte lesen", () -> sp().merkpunkte(), l -> {
            merkListe = l;
            fuellen(merkBox, l, gewaehlt(merkBox));
            merkZeile();
        });
    }

    private void merkZeile() {
        int i = merkBox.getSelectedIndex();
        if (i < 0 || i >= merkListe.size()) { txt(merkLbl, merkListe.size() + " Merkpunkte in " + sp().wo()); return; }
        var m = merkListe.get(i);
        double[] u = com.dan.geiranger.scene.Site.toUtm(m.x(), m.z());
        txt(merkLbl, String.format(Locale.GERMAN, "<html><body style='width:215px'>%d Merkpunkte · „%s“: E %.0f, N %.0f, %.0f m · %s%s</body></html>", merkListe.size(),
                esc(m.name()), u[0], u[1], m.y(), m.szeneMs() == null ? "" : com.dan.geiranger.data.Daten.text(m.szeneMs()), m.notiz() == null ? "" : "<br>" + esc(m.notiz())));
    }

    private void merkpunktHin() {
        int i = merkBox.getSelectedIndex();
        if (i < 0 || i >= merkListe.size()) return;
        var m = merkListe.get(i);
        r.camera.stopFahrt();
        r.camera.stopMitfahrt();
        if (m.auge() != null) r.camera.place(m.auge()[0], m.auge()[1], m.auge()[2], m.blick(), m.nick());
        else r.camera.place(m.x(), m.y() + 50, m.z() + 120, 0, 20);
        r.camera.snap();
        if (m.szeneMs() != null) clock.setEpochMillis(m.szeneMs());
        view.setMode(r.camera.mode);
        syncFromScene();
        r.hudMeldung("Merkpunkt „" + m.name() + "“");
        view.requestFocusInWindow();
    }

    /** Nach einem Wechsel des Speichers alle Listen neu lesen. */
    private void neuLesen() {
        if (paketBox == null) return;
        listePresets(null);
        listeGemerkt();
        listeMerk();
        naechsteListe = 0;
        datenZeilen();
    }

    private void verbinden() {
        datenNote = "verbinde …";
        hinten("Verbinden", com.dan.geiranger.data.Verbindung::konfig, k -> {
            com.dan.store.Db.Konfig kk = k;
            char[] neu = null;
            if (!k.hatPasswort()) {
                com.dan.ftextfield.FPasswordfield pw = new com.dan.ftextfield.FPasswordfield();
                JPanel p = new JPanel(new BorderLayout(0, 6));
                p.add(new JLabel("Passwort für " + k.kurz() + ":"), BorderLayout.NORTH);
                p.add(pw, BorderLayout.CENTER);
                if (com.dan.foptionpane.FOptionPane.showConfirmDialog(this, p, "Geiranger · Datenbank", com.dan.foptionpane.FOptionPane.OK_CANCEL_OPTION)
                        != com.dan.foptionpane.FOptionPane.OK_OPTION || pw.getPassword().length == 0) { datenNote = ""; return; }
                neu = pw.getPassword();
                kk = k.mitPasswort(neu);
            }
            char[] eingabe = neu;
            com.dan.store.Db.Konfig kf = kk;
            hinten("Verbinden", () -> {
                String[] grund = {""};
                com.dan.geiranger.data.DbSpeicher d = com.dan.geiranger.data.Datenseite.verbinden(kf, grund);
                if (d == null) throw new com.dan.store.Ablage.Fehler(grund[0]);
                if (eingabe != null) try { com.dan.store.Db.passwortSpeichern(kf, eingabe); } catch (Exception ex) { com.dan.store.Protokoll.warnung("Daten", "Passwort nicht eingetragen: " + ex.getMessage()); }
                int st = com.dan.geiranger.data.Verbindung.einrichter().stufe(d.verbindung());
                return new Object[]{d, st};
            }, o -> {
                var d = (com.dan.geiranger.data.DbSpeicher) o[0];
                int st = (Integer) o[1];
                if (st < com.dan.geiranger.data.Verbindung.VERSION) {
                    if (com.dan.foptionpane.FOptionPane.showConfirmDialog(this, d.wo() + " ist noch nicht eingerichtet (Stufe " + st + ").\nJetzt einrichten? (löscht nie etwas)",
                            "Geiranger · Datenbank", com.dan.foptionpane.FOptionPane.YES_NO_OPTION) != com.dan.foptionpane.FOptionPane.YES_OPTION) { d.schliessen(); datenNote = "nicht eingerichtet"; return; }
                    com.dan.geiranger.data.Datenseite.setzen(d);
                    einrichter(false);
                    return;
                }
                com.dan.geiranger.data.Datenseite.setzen(d);
                datenNote = "verbunden";
                neuLesen();
            });
        });
    }

    private void einrichter(boolean nurPruefen) {
        if (!(sp() instanceof com.dan.geiranger.data.DbSpeicher d)) {
            com.dan.foptionpane.FOptionPane.showMessageDialog(this, "Erst mit der Datenbank verbinden („Verbinden …“).", "Geiranger · Einrichter", com.dan.foptionpane.FOptionPane.ERROR_MESSAGE);
            return;
        }
        datenNote = nurPruefen ? "prüfe …" : "richte ein …";
        hinten(nurPruefen ? "Prüfen" : "Einrichten", () -> {
            var e = com.dan.geiranger.data.Verbindung.einrichter();
            return nurPruefen ? e.pruefen(d.verbindung(), com.dan.geiranger.data.Verbindung.skripte())
                    : e.einrichten(d.verbindung(), com.dan.geiranger.data.Verbindung.skripte(), (t, a) -> datenNote = String.format(Locale.GERMAN, "%.0f %% · %s", a * 100, t));
        }, er -> {
            Startbild.merkeEinrichter(er.kurz());
            datenNote = "";
            if (er.ok()) com.dan.store.Dienst.spaeter(com.dan.store.Dienst::neuBeginnen);
            com.dan.store.Protokoll.info("Einrichter", (nurPruefen ? "Prüfen: " : "Einrichten: ") + er.kurz());
            for (String z : er.zeilen()) if (!z.startsWith("ok") && !z.contains("alles in Ordnung")) com.dan.store.Protokoll.warnung("Einrichter", z);
            javax.swing.JScrollPane spn = zeilenFenster(er.zeilen());
            com.dan.foptionpane.FOptionPane.showMessageDialog(this, spn, "Geiranger · " + (nurPruefen ? "Prüfen: " : "Einrichten: ") + er.kurz(),
                    er.ok() ? com.dan.foptionpane.FOptionPane.INFORMATION_MESSAGE : com.dan.foptionpane.FOptionPane.WARNING_MESSAGE);
            neuLesen();
        });
    }

    private void uebernehmen() {
        if (!sp().datenbank()) {
            com.dan.foptionpane.FOptionPane.showMessageDialog(this, "Erst mit der Datenbank verbinden („Verbinden …“).", "Geiranger · Übernehmen", com.dan.foptionpane.FOptionPane.ERROR_MESSAGE);
            return;
        }
        var von = new com.dan.geiranger.data.DateiSpeicher(com.dan.geiranger.data.DateiSpeicher.ordner());
        datenNote = "übernehme …";
        hinten("Übernehmen", () -> com.dan.geiranger.data.Uebernahme.uebernehmen(von, sp()), z -> {
            datenNote = "übernommen";
            com.dan.foptionpane.FOptionPane.showMessageDialog(this, "Aus " + von.wo() + " nach " + sp().wo() + ":\n" + z, "Geiranger · Übernahme",
                    com.dan.foptionpane.FOptionPane.INFORMATION_MESSAGE);
            neuLesen();
        });
    }

    private void weiterWieZuletzt() {
        hinten("Weiter wie zuletzt", () -> {
            var s = sp().letzteSitzung(com.dan.store.Dienst.sitzung());
            if (s == null || s.zustand() == null) throw new com.dan.store.Ablage.Fehler("Keine gesicherte Sitzung in " + sp().wo());
            return s;
        }, s -> { Weiter.jetzt(r, s); view.setMode(r.camera.mode); syncFromScene(); });
    }

    private void tageskarte() {
        java.time.LocalDate tag = clock.date();
        if (chronik != null) chronik.warten();
        java.io.File dir = r.photoDir != null ? r.photoDir : new java.io.File("docs/status");
        hinten("Tageskarte", () -> com.dan.geiranger.data.Tageskarte.zeichnen(sp(), r.terrain, tag, dir), k -> {
            r.hudMeldung("Tageskarte: " + k.datei().getPath());
            txt(heuteLbl, String.format(Locale.GERMAN, "<html><body style='width:215px'>Tageskarte %s: %d Fahrten (%d Hörner), %d Busse, %d Stunden · %s</body></html>",
                    tag, k.fahrten(), k.hoerner(), k.busse(), k.stunden(), k.datei().getName()));
        });
    }

    /** Jede halbe Sekunde: Zeilen von DATEN; alle 10 s die Listen aus dem Speicher. */
    private void datenZeilen() {
        if (paketBox == null) return;
        var c = chronik;
        txt(herkunftLbl, "<html><body style='width:215px'>Fahrplan, Klima, Höhenstufen, Wetter- und Regiebücher aus: " + esc(com.dan.geiranger.data.Datenseite.herkunft) + "</body></html>");
        txt(chronikLbl, "<html><body style='width:215px'>" + esc(c == null ? "Chronik: startet mit der Szene" : c.describe()) + "</body></html>");
        long gs = com.dan.store.Dienst.gesichert();
        txt(datenLbl, "<html><body style='width:215px'>" + esc(sp().wo()) + (datenNote.isEmpty() ? "" : " · " + esc(datenNote))
                + "<br>Sitzung " + com.dan.store.Dienst.sitzung() + (gs > 0 ? ", gesichert " + String.format("%tR", gs) + " Uhr (Rechner)" : ", noch nicht gesichert")
                + (com.dan.geiranger.data.Datenseite.einrichterStand.isEmpty() ? "" : "<br>Einrichter: " + esc(com.dan.geiranger.data.Datenseite.einrichterStand))
                + (com.dan.store.Dienst.letzterFehler().isEmpty() ? "" : "<br>Zuletzt: " + esc(com.dan.store.Dienst.letzterFehler())) + "</body></html>");
        long now = System.currentTimeMillis();
        if (now < naechsteListe) return;
        naechsteListe = now + 10_000;
        java.time.LocalDate tag = clock.date();
        com.dan.store.Dienst.spaeter(() -> {
            try {
                var s = sp();
                var heute = s.tagesChronik(tag);
                var sitz = s.sitzungenMitChronik(12);
                var prot = s.letzteZeilen(8);
                StringBuilder fl = new StringBuilder();
                for (var f : heute.fahrten())
                    fl.append(fl.length() > 0 ? ", " : "").append(f.schiff().replace("MF ", "")).append(' ').append(hm(f.abMs()))
                            .append(f.richtung().equals("NACH_GEIRANGER") ? "→G" : f.richtung().equals("NACH_HELLESYLT") ? "→H" : f.richtung().equals("EIN") ? " ein" : " aus");
                int gaeste = 0;
                for (var b : heute.busse()) gaeste += b.gruppe();
                String h = String.format(Locale.GERMAN, "<html><body style='width:215px'>Heute (%s): %d Stunden · %s%s</body></html>", tag, heute.stunden().size(),
                        heute.fahrten().isEmpty() ? "noch keine Fahrt" : esc(fl.toString()), heute.busse().isEmpty() ? "" : String.format(Locale.GERMAN, " · %d Busse, %d Gäste",
                                heute.busse().size(), gaeste));
                java.util.List<Sitzungszeile> sl = new java.util.ArrayList<>();
                for (long[] x : sitz) sl.add(new Sitzungszeile(x[0], x[1], x[2], x[3]));
                StringBuilder pb = new StringBuilder("<html><body style='width:215px'>");
                for (String z : prot) pb.append(esc(z.length() > 160 ? z.substring(0, 160) + " …" : z)).append("<br>");
                String ps = pb.append("</body></html>").toString();
                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (popupOpen(this) || menuOpen()) return;
                    if (!heuteLbl.getText().startsWith("<html><body style='width:215px'>Tageskarte")) txt(heuteLbl, h);
                    fuellen(sitzungBox, sl, gewaehlt(sitzungBox));
                    txt(protLbl, ps);
                });
            } catch (Exception ex) {
                javax.swing.SwingUtilities.invokeLater(() -> txt(heuteLbl, "Chronik nicht lesbar: " + ex.getMessage()));
            }
        });
    }

    /** Die Blöcke in fester Reihenfolge (F37 A). */
    public static final String[] REIHENFOLGE = {"ZEIT", "KAMERA", "REGIEBÜCHER", "WETTER", "WASSER", "SZENE", "KLANG", "DATEN", "TECHNIK"};

    private void reihenfolge() {
        java.util.Map<String, FCollapsiblePanel> m = new java.util.LinkedHashMap<>();
        for (Object[] b : bloecke) m.put(((FCollapsiblePanel) b[0]).getTitle(), (FCollapsiblePanel) b[0]);
        removeAll();
        boolean erst = true;
        for (String t : REIHENFOLGE) {
            FCollapsiblePanel p = m.remove(t);
            if (p == null) continue;
            if (!erst) add(Box.createVerticalStrut(6));
            add(p);
            erst = false;
        }
        for (FCollapsiblePanel p : m.values()) { add(Box.createVerticalStrut(6)); add(p); }
    }

    /** Für Prüfungen: die Titel der Blöcke, wie sie im Bedienfeld stehen. */
    public java.util.List<String> bloecke() {
        java.util.List<String> l = new java.util.ArrayList<>();
        for (Component c : getComponents()) if (c instanceof FCollapsiblePanel p) l.add(p.getTitle());
        return l;
    }

    private final java.util.Map<FCollapsiblePanel, Integer> hoehe = new java.util.HashMap<>();

    /**
     * Wie Rungholt: FCollapsiblePanel merkt sich die Höhe des Inhalts beim Aufklappen. Wachsen danach Zeilen, schneidet
     * der Block unten ab. Darum alle halbe Sekunde nachmessen und offene Blöcke neu setzen.
     */
    private void nachmessen() {
        boolean changed = false;
        for (Object[] b : bloecke) {
            FCollapsiblePanel p = (FCollapsiblePanel) b[0];
            if (!p.isExpanded()) { hoehe.remove(p); continue; }
            JComponent body = (JComponent) b[1];
            body.invalidate();
            int h = body.getPreferredSize().height;
            Integer old = hoehe.put(p, h);
            if (old == null || old != h) { p.setExpanded(true, false); changed = true; }
        }
        if (changed) { revalidate(); repaint(); }
    }

    /** Ist irgendwo im Bedienfeld eine Auswahlliste aufgeklappt? */
    private static boolean popupOpen(java.awt.Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof javax.swing.JComboBox<?> b && b.isPopupVisible()) return true;
            if (k instanceof java.awt.Container cc && popupOpen(cc)) return true;
        }
        return false;
    }

    /** Ist irgendein Aufklappmenü offen (auch die Liste einer Auswahl)? Dann darf sich nichts neu auslegen. */
    public static boolean menuOpen() { return javax.swing.MenuSelectionManager.defaultManager().getSelectedPath().length > 0; }

    /** Text nur setzen, wenn er sich ändert (sonst legt Swing den Block bei jedem Takt neu aus). */
    private static void txt(JLabel l, String t) { if (!t.equals(l.getText())) l.setText(t); }

    private void ansicht(Ansichten.Ansicht a) {
        if (r.placeView(a)) {
            free.setSelected(r.camera.mode == Camera.Mode.FREI);
            orbit.setSelected(false);
        }
        view.requestFocusInWindow();
    }

    private void tasten() {
        StringBuilder an = new StringBuilder();
        for (Ansichten.Ansicht a : Ansichten.ALLE) an.append(an.length() > 0 ? ", " : "").append(a.key()).append(' ').append(a.name());
        com.dan.foptionpane.FOptionPane.showMessageDialog(this, "<html><body style='width:420px'>"
                + "<b>Szene</b><br>Links ziehen: drehen · rechts ziehen: verschieben · Rad, Bild↑/↓, +/−: zoomen<br>"
                + "Pfeile: drehen · W A S D: gleiten · Q/E: ab und auf (frei) · Umschalt: schneller<br>"
                + "F: frei fliegen · R: von selbst kreisen · Pos1: Grundansicht · H: HUD kurz, voll, aus · G: Grund zeigen<br>"
                + "Leertaste: Uhr anhalten und weiter · F12: Foto nach docs/status · F5: Shader neu laden<br>"
                + "V: Ansicht merken (mit Vorschau) · Umschalt + Klick: Merkpunkt ins Gelände (Block DATEN)<br>"
                + "Ansichten: " + an + "<br><br>"
                + "<b>Fenster</b><br>F1: diese Übersicht · F11: nur die Szene (Bedienfeld und Statuszeile weg und wieder da)</body></html>",
                "Geiranger · Tasten", com.dan.foptionpane.FOptionPane.INFORMATION_MESSAGE);
    }

    /** Regler nach der Szene stellen (alle 0,5 s). */
    private void syncFromScene() {
        fromScene = true;
        int m = (int) Math.round(clock.hour() * 60) % (24 * 60);
        if (!time.getValueIsAdjusting()) time.setValue(m);
        if (!scaleSl.getValueIsAdjusting()) scaleSl.setValue((int) Math.round(r.renderScale * 100));
        if (!expSl.getValueIsAdjusting()) expSl.setValue((int) Math.round(r.exposureBias * 10));
        if (!bftSl.getValueIsAdjusting()) bftSl.setValue(Math.min(8, wiring.wind.beaufort()));
        if (!coverSl.getValueIsAdjusting()) coverSl.setValue((int) Math.round(wiring.oktas * 10));
        if (!rainSl.getValueIsAdjusting()) rainSl.setValue((int) Math.round(Math.min(10, wiring.rainRate) * 10));
        if (!fogSl.getValueIsAdjusting()) fogSl.setValue((int) Math.round(wiring.fogAmount * 100));
        if (!bandSl.getValueIsAdjusting()) bandSl.setValue((int) Math.round(wiring.bandAmount * 100));
        if (!dirSl.getValueIsAdjusting()) dirSl.setValue((int) Math.round(wiring.wind.fromDeg() / 10) % 36);
        if (bed.isSelected() != r.showBed) bed.setSelected(r.showBed);
        fromScene = false;
        labels();
    }

    private void labels() {
        txt(dateLbl, clock.local().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d. MMMM u", Locale.GERMANY)));
        txt(timeLbl, "Uhrzeit " + clock.timeLabel());
        double[] s = clock.sun();
        double day = clock.local().toLocalDate().atStartOfDay(SceneClock.ZONE).toInstant().toEpochMilli();
        double up = Sun.crossing(day, clock.lat, clock.lon, true), down = Sun.crossing(day, clock.lat, clock.lon, false);
        txt(sunLbl, String.format(Locale.GERMAN, "<html><body style='width:215px'>Sonne %.1f° hoch, aus %03.0f° (%s) · Aufgang %s, Untergang %s</body></html>",
                s[0], s[1], compass(s[1]), hm(up), hm(down)));
        txt(camLbl, "<html><body style='width:215px'>" + esc(r.cameraLine()) + "</body></html>");
        txt(scaleLbl, String.format(Locale.GERMAN, "Auflösung %.0f %% (%s)", r.renderScale * 100, r.autoScale ? "geregelt, " + r.scaleSteps() + "× geändert" : "fest"));
        txt(qualLbl, "<html><body style='width:215px'>" + r.quality().summary() + "</body></html>");
        txt(windLbl, r.windLine());
        txt(weatherLbl, "<html><body style='width:215px'>" + esc(wiring.weatherLine()) + "</body></html>");
        txt(coverLbl, String.format(Locale.GERMAN, "Bedeckung %.1f/8 · %s", wiring.oktas, wiring.cloudType.label));
        txt(rainLbl, String.format(Locale.GERMAN, "Regen %.1f mm/h", wiring.rainRate));
        txt(fogLbl, String.format(Locale.GERMAN, "Fjordnebel %.0f %% (bis %.0f m)", wiring.fogAmount * 100, wiring.fogTop));
        txt(bandLbl, String.format(Locale.GERMAN, "Wolkenband %.0f %% (%.0f bis %.0f m)%s", wiring.bandAmount * 100, wiring.bandBase, wiring.bandTop, wiring.bandHand >= 0 ? ", von Hand" : ""));
        txt(waterLbl, "<html><body style='width:215px'>" + esc(r.waterLine()) + "</body></html>");
        txt(fallLbl, "<html><body style='width:215px'>" + esc(r.fallLine()) + "</body></html>");
        txt(klangLbl, "<html><body style='width:215px'>" + esc(r.klang.describe()) + "</body></html>");
        txt(regieLbl, "<html><body style='width:215px'>" + esc(r.regieLine()) + "</body></html>");
        com.dan.geiranger.wiring.Abfluss a = r.abfluss();
        txt(abflussLbl, a.hand >= 0 ? String.format(Locale.GERMAN, "Abfluss von Hand: %.0f %% vom Juni", a.hand * 100)
                : String.format(Locale.GERMAN, "Abfluss: %.0f %% vom Juni (Regie)", a.factor() * 100));
        if (a.hand < 0 && !abflussSl.getValueIsAdjusting()) {
            fromScene = true;
            abflussSl.setValue((int) Math.round(Math.min(3, a.factor()) * 100));
            fromScene = false;
        }
    }

    private void technik() {
        txt(perfLbl, String.format(Locale.GERMAN, "<html><body style='width:215px'>%.0f Bilder/s · Grafikkarte %.2f ms · Prozessor %.2f ms<br>%s</body></html>",
                r.fps(), r.gpuMs(), r.cpuMs(), r.passTimes()));
        ComputeProbe.Result c = r.compute();
        txt(computeLbl, "<html><body style='width:215px'>" + (c == null ? "Compute-Test läuft …" : c.line()) + "</body></html>");
        txt(glLbl, "<html><body style='width:215px'>" + r.glInfo() + (r.reversedDepth() ? " · umgekehrte Tiefe" : "") + "</body></html>");
        txt(physLbl, "<html><body style='width:215px'>" + r.physik.describe() + "</body></html>");
        txt(pkgLbl, "<html><body style='width:215px'>" + esc(Bibliothek.kurz().replace("!", "")) + ": " + String.join(", ", Bibliothek.pakete())
                + ".<br>Neu hier: " + String.join(", ", Bibliothek.NEU) + " (Engines seit Phase 5 und 6, in der Szene seit Phase 5 und 7). Die Isolationsprüfung <i>check-packages</i> läuft nach jedem Übersetzen.</body></html>");
        StringBuilder sb = new StringBuilder("<html><body style='width:215px'>").append(esc(wiring.summary()));
        for (Wiring.Link l : wiring.links()) sb.append("<br>✓ ").append(esc(l.toString()));
        for (Wiring.Link l : Wiring.planned()) sb.append("<br>· ").append(esc(l.toString()));
        txt(linksLbl, sb.append("</body></html>").toString().replace("→", "&rarr;"));
        StringBuilder h = new StringBuilder("<html><body style='width:215px'>");
        for (String l : r.hudLines()) h.append(esc(l.startsWith("!") ? l.substring(1) : l)).append("<br>");
        txt(hudLbl, h.append("</body></html>").toString());
    }

    /**
     * Phase 18 (nach Status_Phase17db): die Zeilen des Einrichters gut lesbar auf dunklem Grund – „ok“ hell, was nicht
     * in Ordnung ist bernsteinfarben, die letzte Zeile (das Urteil) fett.
     */
    static javax.swing.JScrollPane zeilenFenster(java.util.List<String> zeilen) {
        StringBuilder h = new StringBuilder("<html><body style='width:560px;font-family:sans-serif;font-size:11px'>");
        for (int i = 0; i < zeilen.size(); i++) {
            String z = zeilen.get(i), c = z.startsWith("ok") ? "#dfe8e6" : z.contains("alles in Ordnung") ? "#5db7ae" : "#e2b85a";
            boolean letzte = i == zeilen.size() - 1;
            h.append("<div style='color:").append(c).append(";padding:1px 0'>").append(letzte ? "<b>" : "")
                    .append(z.startsWith("ok ") ? "<span style='color:#5db7ae'>ok</span> " + esc(z.substring(3)) : esc(z)).append(letzte ? "</b>" : "").append("</div>");
        }
        JLabel t = new JLabel(h.append("</body></html>").toString());
        t.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        t.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        t.setOpaque(true);
        t.setBackground(Theme.surface());
        javax.swing.JScrollPane spn = new javax.swing.JScrollPane(t, javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        spn.getViewport().setBackground(Theme.surface());
        spn.setBorder(BorderFactory.createLineBorder(Theme.line()));
        spn.getVerticalScrollBar().setUnitIncrement(16);
        spn.setPreferredSize(new Dimension(640, 420));
        return spn;
    }

    private static String esc(String t) { return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    static String hm(double epochMs) {
        if (Double.isNaN(epochMs)) return "—";
        return String.format("%tR", Instant.ofEpochMilli((long) epochMs).atZone(SceneClock.ZONE));
    }

    static String compass(double deg) {
        String[] n = {"Nord", "Nordnordost", "Nordost", "Ostnordost", "Ost", "Ostsüdost", "Südost", "Südsüdost",
                "Süd", "Südsüdwest", "Südwest", "Westsüdwest", "West", "Westnordwest", "Nordwest", "Nordnordwest"};
        return n[(int) Math.round(((deg % 360) + 360) % 360 / 22.5) % 16];
    }

    // ------------------------------------------------------------ Aufbau

    /** Ein Klappblock (FCollapsiblePanel); offen bleibt, was zuletzt offen war. */
    private void block(String title, boolean offen) {
        FCollapsiblePanel p = new FCollapsiblePanel() {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setTitle(title);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel inhalt = new JPanel();
        inhalt.setOpaque(false);
        inhalt.setLayout(new BoxLayout(inhalt, BoxLayout.Y_AXIS));
        inhalt.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 8));
        bloecke.add(new Object[]{p, inhalt});
        String key = "offen." + title;
        boolean auf = PREFS == null ? offen : PREFS.getBoolean(key, offen);
        if (!auf) zu.add(p);
        p.addFCollapsiblePanelListener((panel, expanded) -> {
            if (PREFS != null) try { PREFS.putBoolean(key, expanded); } catch (RuntimeException ex) { /* nicht gemerkt */ }
        });
        if (getComponentCount() > 0) add(Box.createVerticalStrut(6));
        add(p);
        target = inhalt;
    }

    /** Eine Zwischenüberschrift im Block. */
    private void sub(String text) {
        JLabel h = new JLabel(text);
        h.setForeground(Theme.accent());
        h.setFont(new Font("SansSerif", Font.BOLD, 11));
        h.setBorder(BorderFactory.createEmptyBorder(10, 0, 3, 0));
        put(h);
    }

    private void put(Component c) { target.add(row(c)); }

    private static JPanel grid(int n) {
        JPanel p = new JPanel(new java.awt.GridLayout(1, n, 6, 0));
        p.setOpaque(false);
        return p;
    }

    /** Eine Zeile, die mit ihrem Inhalt wächst. */
    private static JComponent row(Component c) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        p.add(c, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    static JLabel label(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Theme.ink());
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        return l;
    }

    static JLabel small(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Theme.muted());
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        return l;
    }

    // ------------------------------------------------------------ Scrollable: nur senkrecht
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(java.awt.Rectangle rr, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(java.awt.Rectangle rr, int o, int d) { return 120; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
