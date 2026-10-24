package com.dan.geiranger.ui;

import com.dan.fbutton.FButton;
import com.dan.geiranger.app.Bibliothek;
import com.dan.geiranger.data.DateiSpeicher;
import com.dan.geiranger.data.Daten;
import com.dan.geiranger.data.Datenseite;
import com.dan.geiranger.data.DbSpeicher;
import com.dan.geiranger.data.Speicher;
import com.dan.geiranger.data.Verbindung;
import com.dan.geiranger.gl.Renderer;
import com.dan.geiranger.scene.Regie;
import com.dan.geiranger.scene.SceneClock;
import com.dan.geiranger.scene.Site;
import com.dan.store.Db;
import com.dan.store.Einrichter;
import com.dan.store.Protokoll;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * Das Startbild vor der Szene. Seit Phase 17 (F37 A, aus Rungholt) mit dem Stand der Datenbank (Schema GEIRANGER): es
 * verbindet über {@code db/db.properties}, fragt nach dem Passwort, wenn keins hinterlegt ist, prüft die Ausbaustufe
 * und zeigt Fälle, Gebäude, Fahrplan, die Chronik (Stunden, Tage, Fahrten, Busse), Regiebücher, Ansichten, Merkpunkte
 * und Presets, die letzte Sitzung (geordnet beendet oder abgebrochen) und das letzte Ergebnis des Einrichters. Drei
 * Wege: „Weiter wie zuletzt“, „Neu“ und ein Regiebuch „Im Kino“; dazu „Einrichten“, „Ohne Datenbank“ und „Beenden“.
 * Ist alles bereit, geht es nach 3 s mit „Neu“ weiter; solange eingerichtet werden muss oder ein Passwort fehlt, nicht.
 * Jede Eingabe hält an. Ohne Startbild: {@code -Dgeiranger.splash=false} (dann gilt, was sich ohne Rückfrage verbinden
 * lässt).
 */
public final class Startbild extends JDialog {
    private static final int SEKUNDEN = 3;
    private static final Preferences PREFS = prefs();

    /** Was gewählt wurde: Speicher, für „Weiter wie zuletzt“ die Sitzung, für das Kino das Regiebuch (sonst null); beenden. */
    public record Wahl(Speicher speicher, Daten.Sitzung weiter, Regie.Book kino, boolean beenden) {
        public Wahl(Speicher speicher, Daten.Sitzung weiter) { this(speicher, weiter, null, false); }
    }

    private final JLabel dbLine = new JLabel(" "), standLine = new JLabel(" "), countLine = new JLabel(" ");
    private final JPanel pwRow = new JPanel(new BorderLayout(8, 0));
    private final com.dan.ftextfield.FPasswordfield pw = new com.dan.ftextfield.FPasswordfield();
    private final com.dan.ftextarea.FTextArea log = new com.dan.ftextarea.FTextArea(8, 60);
    private final javax.swing.JScrollPane logPane = new javax.swing.JScrollPane(log);
    private final FButton neu = new FButton("Neu"), weiter = new FButton("Weiter wie zuletzt"), einrichten = new FButton("Einrichten"),
            ohne = new FButton("Ohne Datenbank"), ende = new FButton("Beenden");
    private final com.dan.fcombobox.FComboBox buchBox = new com.dan.fcombobox.FComboBox();
    private Timer timer;
    private int rest = SEKUNDEN;
    private boolean angehalten, fertig;
    private AWTEventListener aufmerken;
    private volatile Db.Konfig konfig;
    private volatile DbSpeicher db;
    private volatile Speicher speicher = new DateiSpeicher(DateiSpeicher.ordner());
    private volatile Daten.Sitzung letzte;
    private Wahl wahl;

    private static Preferences prefs() {
        try { return Preferences.userRoot().node("com/dan/geiranger/daten"); } catch (RuntimeException | Error e) { return null; }
    }

    /** Zeigt das Startbild und kehrt zurück, wenn es zu ist (modal); setzt den Speicher der App. */
    public static Wahl zeigen() {
        Startbild s = new Startbild();
        s.setVisible(true);
        Wahl w = s.wahl != null ? s.wahl : new Wahl(s.speicher, null);
        if (!w.beenden()) Datenseite.setzen(w.speicher());
        return w;
    }

    /** Ohne Startbild: verbinden, wenn es ohne Rückfrage geht und das Schema eingerichtet ist, sonst Dateien. */
    public static Wahl stumm() {
        Speicher sp = new DateiSpeicher(DateiSpeicher.ordner());
        try {
            Db.Konfig k = Verbindung.konfig();
            DbSpeicher d = Datenseite.verbinden(k, null);
            if (d != null && Verbindung.einrichter().stufe(d.verbindung()) >= Verbindung.VERSION) sp = d;
            else if (d != null) d.schliessen();
        } catch (Exception e) { /* ohne Datenbank */ }
        Datenseite.setzen(sp);
        return new Wahl(sp, null);
    }

    /** Statuszeile unten rechts im Fenster: wohin gespeichert wird. */
    public static String statuszeile() { return Datenseite.speicher().wo() + " · " + Datenseite.herkunft; }

    private Startbild() {
        super((java.awt.Frame) null, "Geiranger", true);
        setUndecorated(true);
        JPanel root = new JPanel(new BorderLayout(18, 14)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, new Color(22, 32, 36), 0, getHeight(), new Color(10, 14, 16)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(Theme.FJORD);
                g2.fillRect(0, 0, getWidth(), 3);
                int y = getHeight() - 6, w = getWidth();
                for (int i = 0; i < Theme.BAND.length; i++) {
                    g2.setColor(Theme.BAND[i]);
                    g2.fillRect((int) (w * Theme.BAND_AT[i]), y, (int) Math.ceil(w * (Theme.BAND_AT[i + 1] - Theme.BAND_AT[i])) + 1, 6);
                }
            }
        };
        root.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(46, 62, 68)), BorderFactory.createEmptyBorder(22, 26, 24, 28)));
        JLabel il = new JLabel(new javax.swing.ImageIcon(AppIcon.load(128)));
        il.setVerticalAlignment(JLabel.TOP);
        root.add(il, BorderLayout.WEST);
        JPanel mid = new JPanel();
        mid.setOpaque(false);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("GEIRANGER");
        title.setFont(new Font("Serif", Font.PLAIN, 34));
        title.setForeground(Theme.FALL);
        JLabel sub = new JLabel("Sieben Schwestern und elf Kehren · der Geirangerfjord, " + Site.dms(Site.LAT, Site.LON).replace(" · ", " "));
        sub.setFont(new Font("SansSerif", Font.PLAIN, 14));
        sub.setForeground(Theme.FJORD);
        SceneClock start = new SceneClock();
        JLabel info = new JLabel("<html><body style='width:520px'>" + Renderer.VERSION + ": Oberfläche und Datenbank. Der Fjord ab dem " + start.label()
                + " mit den Wänden, den Sieben Schwestern, Friaren und Brudesløret, der Geirangelva, dem Ørnevegen in elf Kehren, dem Ort, den Fähren, "
                + "den Menschen, dem Jahr, dem Klang und drei Regiebüchern im Kino. Fahrplan, Klima und Regiebücher kommen aus dem Schema GEIRANGER, "
                + "die Chronik (Stunden, Tage, Fahrten, Busse), Merkpunkte und die Sitzung gehen dorthin oder ohne Datenbank nach daten/.<br>"
                + "Renderer.jar: " + Bibliothek.kurz().replace("!", "") + "</body></html>");
        info.setFont(new Font("SansSerif", Font.PLAIN, 12));
        info.setForeground(new Color(170, 186, 192));
        info.setBorder(BorderFactory.createEmptyBorder(10, 0, 8, 0));
        for (JLabel l : new JLabel[]{dbLine, standLine, countLine}) {
            l.setFont(new Font("SansSerif", Font.PLAIN, 12));
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        for (JLabel l : new JLabel[]{title, sub, info}) l.setAlignmentX(Component.LEFT_ALIGNMENT);
        dbLine.setForeground(new Color(224, 190, 120));
        standLine.setForeground(new Color(196, 206, 210));
        standLine.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        countLine.setForeground(Theme.FJORD);
        countLine.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        pwRow.setOpaque(false);
        pwRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel pl = ControlPanel.small("Passwort für das Schema:");
        FButton verb = new FButton("Verbinden");
        verb.addActionListener(e -> mitPasswort());
        pw.addActionListener(e -> mitPasswort());
        pwRow.add(pl, BorderLayout.WEST);
        pwRow.add(pw, BorderLayout.CENTER);
        pwRow.add(verb, BorderLayout.EAST);
        pwRow.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        pwRow.setMaximumSize(new Dimension(600, 40));
        pwRow.setVisible(false);
        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        logPane.setPreferredSize(new Dimension(600, 170));
        logPane.setVisible(false);
        mid.add(title);
        mid.add(sub);
        mid.add(info);
        mid.add(dbLine);
        mid.add(pwRow);
        mid.add(standLine);
        mid.add(logPane);
        mid.add(countLine);
        root.add(mid, BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        south.setOpaque(false);
        ohne.setToolTipText("Mit Dateien im Ordner daten/ statt der Datenbank");
        einrichten.setToolTipText("Die Skripte aus db/sql ausführen und alles prüfen (löscht nie etwas)");
        weiter.setToolTipText("Die letzte Sitzung herstellen: Uhr mit Datum und Zeitraffer, Wetter, Kamera, Ebenen, Qualität, Ton und das laufende Regiebuch");
        neu.setToolTipText("Die Szene neu: 21. Juni 2026, 11 Uhr, klarer Junitag");
        ohne.addActionListener(e -> { if (db != null) db.schliessen(); db = null; speicher = new DateiSpeicher(DateiSpeicher.ordner()); schliessen(new Wahl(speicher, null)); });
        einrichten.addActionListener(e -> einrichten());
        weiter.addActionListener(e -> schliessen(new Wahl(speicher, letzte)));
        neu.addActionListener(e -> schliessen(new Wahl(speicher, null)));
        ende.addActionListener(e -> schliessen(new Wahl(speicher, null, null, true)));
        einrichten.setEnabled(false);
        weiter.setEnabled(false);
        // ein Regiebuch gleich im Kino
        fuellen(alleBuecher(null));
        buchBox.setPreferredSize(new Dimension(240, buchBox.getPreferredSize().height));
        FButton kino = new FButton("Im Kino");
        kino.setToolTipText("Das Regiebuch im Kinomodus: ganzer Bildschirm, Vor- und Abspann, Untertitel; Esc beendet das Kino, Leertaste hält an");
        kino.addActionListener(e -> {
            if (buchBox.getSelectedItem() instanceof Regie.Book b) schliessen(new Wahl(speicher, null, b, false));
        });
        south.add(buchBox);
        south.add(kino);
        south.add(javax.swing.Box.createHorizontalStrut(14));
        south.add(ohne);
        south.add(einrichten);
        south.add(ende);
        south.add(weiter);
        south.add(neu);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        dbLine.setText("Datenbank: verbinde …");
        pack();
        setSize(Math.max(getWidth(), 920), Math.max(getHeight(), 440));
        setLocationRelativeTo(null);
        try { setIconImages(AppIcon.images()); } catch (RuntimeException e) { /* ohne Symbol */ }
        getRootPane().setDefaultButton(neu);
        aufmerken = ev -> {
            if (ev.getSource() instanceof Component c && SwingUtilities.getWindowAncestor(c) == this && !angehalten
                    && (ev.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED || ev.getID() == java.awt.event.KeyEvent.KEY_PRESSED) && !(c instanceof FButton))
                anhalten("Angehalten. „Neu“ oder „Weiter wie zuletzt“ startet die Szene.");
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(aufmerken, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        hintergrund(this::pruefen);
    }

    /** Die Regiebücher des Speichers (mitgelieferte und eigene); ohne Speicher die mitgelieferten. */
    static List<Regie.Book> alleBuecher(Speicher sp) {
        List<Regie.Book> l = new ArrayList<>();
        try {
            if (sp != null) for (Daten.Regiebuch r : sp.regiebuecher()) try { l.add(Regie.parse(r.text())); } catch (RuntimeException e) { /* übergehen */ }
        } catch (Exception e) { /* dann die Dateien */ }
        if (l.isEmpty()) try { l.addAll(Regie.mitgeliefert()); } catch (RuntimeException e) { /* keine */ }
        return l;
    }

    private void fuellen(List<Regie.Book> l) {
        Object alt = buchBox.getSelectedItem();
        buchBox.removeAllItems();
        for (Regie.Book b : l) buchBox.addItem(b);
        if (alt != null) for (int i = 0; i < buchBox.getItemCount(); i++) if (buchBox.getItemAt(i).toString().equals(alt.toString())) buchBox.setSelectedIndex(i);
    }

    private static void hintergrund(Runnable r) {
        Thread t = new Thread(r, "Geiranger-Startbild");
        t.setDaemon(true);
        t.start();
    }

    private void anhalten(String text) {
        angehalten = true;
        if (timer != null) timer.stop();
        countLine.setText(text);
    }

    /** Verbinden, Stufe prüfen, Stand zeigen (im Hintergrund). */
    private void pruefen() {
        String zeile;
        boolean zaehlen = true;
        try {
            konfig = Verbindung.konfig();
            if (!konfig.hatPasswort()) {
                String sl = stand(speicher, "Bis dahin: ");
                SwingUtilities.invokeLater(() -> {
                    dbLine.setText("Datenbank " + konfig.kurz() + ": bitte das Passwort eingeben (es wird nach dem Verbinden in db/db.properties eingetragen).");
                    pwRow.setVisible(true);
                    standLine.setText(sl);
                    anhalten("Wartet auf das Passwort oder „Ohne Datenbank“.");
                    pack();
                    pw.requestFocusInWindow();
                });
                return;
            }
            String[] grund = {""};
            DbSpeicher d = Datenseite.verbinden(konfig, grund);
            if (d == null) {
                zeile = "Datenbank " + konfig.kurz() + " nicht erreichbar (" + grund[0] + "). Ohne Datenbank: Dateien in daten/.";
                Protokoll.warnung("Startbild", zeile);
                rest = 8;
            } else {
                db = d;
                int st = Verbindung.einrichter().stufe(d.verbindung());
                if (st < Verbindung.VERSION) {
                    zeile = d.wo() + ": " + (st == 0 ? "noch keine Tabellen GR_" : st < 0 ? "Tabellen ohne GR_SCHEMA" : "Ausbaustufe " + st + " statt " + Verbindung.VERSION)
                            + ". Bitte „Einrichten“ (führt db/sql aus, löscht nie etwas) oder „Ohne Datenbank“.";
                    zaehlen = false;
                } else {
                    speicher = d;
                    zeile = d.wo() + " · Ausbaustufe " + st + " · eingerichtet";
                }
            }
        } catch (FileNotFoundException e) {
            zeile = "Keine db/db.properties (Vorlage db/db.properties.example). Ohne Datenbank: Dateien in daten/.";
        } catch (Exception e) {
            zeile = "Datenbank: " + e.getMessage() + ". Ohne Datenbank: Dateien in daten/.";
            rest = 8;
        }
        String z = zeile;
        boolean zz = zaehlen;
        String sl = stand(speicher, "");
        try { letzte = speicher.letzteSitzung(-1); } catch (Exception e) { letzte = null; }
        List<Regie.Book> bs = alleBuecher(speicher);
        SwingUtilities.invokeLater(() -> {
            dbLine.setText("<html><body style='width:600px'>" + z + "</body></html>");
            standLine.setText(sl);
            fuellen(bs);
            einrichten.setEnabled(db != null);
            weiter.setEnabled(letzte != null && letzte.zustand() != null);
            pack();
            setSize(Math.max(getWidth(), 920), getHeight());
            if (zz && !angehalten) zaehlen(); else if (!zz) anhalten("Wartet: „Einrichten“, „Ohne Datenbank“ oder „Neu“ mit Dateien.");
        });
    }

    /** Eine Zeile mit dem Stand der Daten. */
    static String stand(Speicher sp, String vor) {
        try {
            Daten.Stand s = sp.stand();
            StringBuilder b = new StringBuilder("<html><body style='width:600px'>").append(vor).append(sp.wo()).append(": ");
            b.append(String.format(Locale.GERMAN, "%d Fälle, %d Gebäude, %d Abfahrten im Fahrplan; Chronik mit %d Stunden, %d Tagen, %d Fahrten, %d Bussen; "
                            + "%d Regiebücher (%d Läufe), %d Ansichten, %d Merkpunkte, %d Presets, %d Befunde aus %d Quellen.",
                    s.faelle(), s.gebaeude(), s.abfahrten(), s.stunden(), s.tage(), s.fahrten(), s.busse(), s.regiebuecher(), s.regielaeufe(), s.ansichten(), s.merkpunkte(),
                    s.presets(), s.befunde(), s.quellen()));
            Daten.Sitzung l = s.letzte();
            if (l != null) {
                String wann = DateTimeFormatter.ofPattern("dd.MM. HH:mm").format(Instant.ofEpochMilli(l.zustandMs() != null ? l.zustandMs() : l.beginnMs()).atZone(ZoneId.systemDefault()));
                b.append("<br>Letzte Sitzung ").append(wann).append(l.sauber() ? ", geordnet beendet" : ", <b>abgebrochen</b>");
                if (l.szeneMs() != null) b.append(" · Szene ").append(l.szeneText());
                if (l.zeitraffer() != null && l.zeitraffer() != 1) b.append(String.format(Locale.GERMAN, " · Zeitraffer %.0f", l.zeitraffer()));
                if (l.regiebuch() != null) b.append(" · Regiebuch „").append(l.regiebuch()).append("“");
            }
            String e = PREFS == null ? "" : PREFS.get("einrichter", "");
            if (!e.isEmpty()) b.append("<br>Einrichter zuletzt: ").append(e);
            return b.append("</body></html>").toString();
        } catch (Exception e) {
            return "Stand nicht lesbar: " + e.getMessage();
        }
    }

    /** Das Ergebnis eines Einrichtens merken (für Startbild und Block DATEN). */
    static void merkeEinrichter(String kurz) {
        Datenseite.einrichterStand = kurz;
        if (PREFS != null) try { PREFS.put("einrichter", kurz + " (" + DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").format(java.time.LocalDateTime.now()) + ")"); } catch (RuntimeException e) { /* nicht gemerkt */ }
    }

    private void mitPasswort() {
        char[] p = pw.getPassword();
        if (p.length == 0 || konfig == null) return;
        Db.Konfig k = konfig.mitPasswort(p);
        dbLine.setText("Datenbank: verbinde …");
        hintergrund(() -> {
            String[] grund = {""};
            DbSpeicher d = Datenseite.verbinden(k, grund);
            if (d == null) {
                SwingUtilities.invokeLater(() -> dbLine.setText("<html><body style='width:600px'>Nicht verbunden: " + grund[0] + "</body></html>"));
                return;
            }
            try { Db.passwortSpeichern(k, p); } catch (Exception e) { Protokoll.warnung("Startbild", "Passwort nicht in db.properties eingetragen: " + e.getMessage()); }
            d.schliessen();
            SwingUtilities.invokeLater(() -> { pwRow.setVisible(false); angehalten = false; });
            konfig = k;
            hintergrund(this::pruefen);
        });
    }

    /** Einrichten mit Fortschritt; danach gilt die Datenbank. */
    private void einrichten() {
        DbSpeicher d = db;
        if (d == null) return;
        anhalten("Einrichten läuft …");
        einrichten.setEnabled(false);
        neu.setEnabled(false);
        logPane.setVisible(true);
        log.setText("");
        pack();
        setSize(Math.max(getWidth(), 920), getHeight());
        hintergrund(() -> {
            try {
                Connection c = d.verbindung();
                Einrichter.Ergebnis r = Verbindung.einrichter().einrichten(c, Verbindung.skripte(), (t, a) -> SwingUtilities.invokeLater(() ->
                        countLine.setText(String.format(Locale.GERMAN, "Einrichten %.0f %% · %s", a * 100, t))));
                merkeEinrichter(r.kurz());
                Protokoll.info("Einrichter", r.kurz() + " · " + r.ausgefuehrt() + " Anweisungen");
                for (String z : r.zeilen()) if (!z.startsWith("ok") && !z.contains("alles in Ordnung")) Protokoll.warnung("Einrichter", z);
                if (r.ok()) speicher = d;
                String sl = stand(speicher, "");
                try { letzte = speicher.letzteSitzung(-1); } catch (Exception e) { letzte = null; }
                List<Regie.Book> bs = alleBuecher(speicher);
                SwingUtilities.invokeLater(() -> {
                    log.setText(String.join("\n", r.zeilen()));
                    log.setCaretPosition(0);
                    standLine.setText(sl);
                    fuellen(bs);
                    dbLine.setText(r.ok() ? d.wo() + " · eingerichtet: alles in Ordnung" : "<html>Einrichter: " + r.kurz() + "</html>");
                    countLine.setText(r.ok() ? "Fertig. „Neu“ startet die Szene." : "Einrichter mit Befunden; siehe oben. „Neu“ startet mit " + speicher.wo() + ".");
                    einrichten.setEnabled(true);
                    neu.setEnabled(true);
                    weiter.setEnabled(letzte != null && letzte.zustand() != null);
                });
            } catch (Exception e) {
                Protokoll.fehler("Einrichter", "Einrichten abgebrochen: " + e.getMessage(), e);
                SwingUtilities.invokeLater(() -> {
                    log.setText("Einrichten abgebrochen: " + e.getMessage());
                    einrichten.setEnabled(true);
                    neu.setEnabled(true);
                });
            }
        });
    }

    private void zaehlen() {
        countLine.setText("Die Szene startet in " + rest + " s …");
        timer = new Timer(1000, e -> {
            if (angehalten) return;
            rest--;
            if (rest <= 0) schliessen(new Wahl(speicher, null));
            else countLine.setText("Die Szene startet in " + rest + " s …");
        });
        timer.start();
    }

    private void schliessen(Wahl w) {
        if (fertig) return;
        fertig = true;
        wahl = w;
        if (db != null && w.speicher() != db) db.schliessen();
        if (timer != null) timer.stop();
        if (aufmerken != null) Toolkit.getDefaultToolkit().removeAWTEventListener(aufmerken);
        dispose();
    }
}
