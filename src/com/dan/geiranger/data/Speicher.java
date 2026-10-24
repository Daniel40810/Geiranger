package com.dan.geiranger.data;

import com.dan.store.Ablage;

import java.time.LocalDate;
import java.util.List;

/**
 * Wohin die App speichert: in das Schema GEIRANGER ({@link DbSpeicher}) oder, ohne Datenbank, in Dateien im Ordner
 * {@code daten/} ({@link DateiSpeicher}, F25 A). Beide können dasselbe; die Oberfläche kennt nur diese Schnittstelle.
 * Sitzung und Protokoll kommen aus {@link Ablage} (com.dan.store).
 * <p>
 * Was die App beim Start liest (F26 A: Fahrplan, Klima, Höhenstufen, Wetter- und Regiebücher, Presets), kommt über
 * {@link Laden}; die Chronik (Stunden, Tage, Fahrten, Busse, Läufe der Regiebücher) schreibt {@link Chronik} im Faden
 * der Daten. Mitgelieferte Einträge werden nie überschrieben, sondern abgelehnt.
 */
public interface Speicher extends Ablage {

    /** Schlüssel der Werte, die eine {@link Ablage.Sicherung} über die Sitzung mitbringt (Spalten von GR_SITZUNG). */
    String SZENE_MS = "szeneMs", ZEITRAFFER = "zeitraffer", KAMERA = "kamera", QUALITAET = "qualitaet", TON = "ton", REGIEBUCH = "regiebuch",
            REGIE_SEKUNDE = "regieSekunde";

    Daten.Stand stand() throws Fehler;

    // ------------------------------------------------------------------ Stammdaten (F27, F30, F32)

    List<Daten.Quelle> quellen() throws Fehler;

    List<Daten.Befund> befunde() throws Fehler;

    /** Die Fälle mit den Wegen ihrer Strähnen. */
    List<Daten.Fall> faelle() throws Fehler;

    List<Daten.Ort> orte() throws Fehler;

    // ------------------------------------------------------------------ was die App beim Start liest (F26 A)

    /** Der Fahrplan aller Jahre (die App wählt das Jahr, {@link Laden}). */
    List<Daten.Fahrplan> fahrplan() throws Fehler;

    /** Das Klima „Geiranger“ je Monat (zwölf Zeilen). */
    List<Daten.KlimaMonat> klima() throws Fehler;

    /** Die zehn Höhenstufen des Jahrs. */
    List<Daten.Hoehenstufe> hoehenstufen() throws Fehler;

    List<Daten.Wetterbuch> wetterbuecher() throws Fehler;

    // ------------------------------------------------------------------ Chronik (F29)

    void stunde(Daten.Stunde s, Long sitzung) throws Fehler;

    void tag(Daten.Tag t, Long sitzung) throws Fehler;

    /** Eine Fahrt anlegen (id 0) oder fortschreiben; gibt ihre Nummer zurück. */
    long schiffsfahrt(Daten.Schiffsfahrt f, Long sitzung) throws Fehler;

    void busankunft(Daten.Busankunft b, Long sitzung) throws Fehler;

    void regielauf(Daten.Regielauf l, Long sitzung) throws Fehler;

    /** Was die Chronik an einem Szenentag weiß. */
    Daten.Tageschronik tagesChronik(LocalDate tag) throws Fehler;

    /** Die letzten Fahrten der Chronik, neueste zuerst. */
    List<Daten.Schiffsfahrt> fahrten(int n) throws Fehler;

    /** Die eigenen Zeilen der Chronik einer Sitzung löschen; gibt zurück, wie viele. */
    int chronikLeeren(long sitzung) throws Fehler;

    /** Sitzungen mit Chronik: {id, Beginn ms, Stunden, Fahrten}, neueste zuerst. */
    List<long[]> sitzungenMitChronik(int n) throws Fehler;

    // ------------------------------------------------------------------ Regie und Kamera

    List<Daten.Regiebuch> regiebuecher() throws Fehler;
    void speichern(Daten.Regiebuch r) throws Fehler;
    void loeschenRegiebuch(String name) throws Fehler;

    List<Daten.Fahrt> fahrten() throws Fehler;

    /** Ansichten ohne Zustand und Vorschau (für Listen): feste zuerst, dann gemerkte, neueste zuerst. */
    List<Daten.Ansicht> ansichten() throws Fehler;
    /** Eine Ansicht ganz, mit Zustand und Vorschau. */
    Daten.Ansicht ansicht(long id) throws Fehler;
    /** Speichert eine neue gemerkte Ansicht und gibt ihre Nummer zurück. */
    long speichern(Daten.Ansicht a, Long sitzung) throws Fehler;
    void loeschenAnsicht(long id) throws Fehler;

    // ------------------------------------------------------------------ Merkpunkte (F35)

    /** Alle Merkpunkte ohne Vorschau, neueste zuerst. */
    List<Daten.Merkpunkt> merkpunkte() throws Fehler;
    /** Ein Merkpunkt mit Vorschau. */
    Daten.Merkpunkt merkpunkt(long id) throws Fehler;
    long speichern(Daten.Merkpunkt m, Long sitzung) throws Fehler;
    void loeschenMerkpunkt(long id) throws Fehler;

    // ------------------------------------------------------------------ Packages (F34)

    /** Presets eines Package (paket = „com.dan.ocean“, klasse = „Ocean“); null für alle. */
    List<Daten.PaketPreset> presets(String paket, String klasse) throws Fehler;
    void speichern(Daten.PaketPreset p) throws Fehler;
    void loeschenPreset(String paket, String klasse, String name) throws Fehler;

    // ------------------------------------------------------------------ Betrieb

    /** Die letzte Sitzung mit Zustand vor der laufenden (oder null). */
    Daten.Sitzung letzteSitzung(long ausser) throws Fehler;

    /** Die letzten n Zeilen des Protokolls zum Anzeigen. */
    List<String> letzteZeilen(int n) throws Fehler;
}
