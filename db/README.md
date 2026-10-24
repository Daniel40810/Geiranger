# Datenbank Geiranger (Phase 17)

Schema **GR_** in Oracle (Dienst PDBORCL, Benutzer `Geiranger`, angelegt mit `manage_user` wie in `docs/Anweisung.txt`).
Die App richtet alles selbst ein: im Startbild **Einrichten** – am Ende steht „alles in Ordnung“.

## Dateien

| Datei | Inhalt |
|---|---|
| `db.properties` | Zugang mit Passwort – **nie ins Git** (steht in `.gitignore`), nie weitergeben |
| `db.properties.example` | Vorlage ohne Passwort; fehlt `db.properties`, legt die App sie daraus an |
| `sql/01_tabellen.sql` | 36 Tabellen, je ID aus Sequenz und Trigger (nie IDENTITY); `SZENENZEIT` setzt der Trigger aus `SZENE_MS` (TIMESTAMP WITH TIME ZONE, Europe/Oslo) |
| `sql/02_spatial.sql` | Metadaten (`USER_SDO_GEOM_METADATA`, SRID 8307) und räumliche Indizes der 20 Geometriespalten und der Sichten für QGIS |
| `sql/03_sichten.sql` | 7 Sichten: `GR_FALL_V`, `GR_GEIRANGER_V`, `GR_TAGESKARTE_V`, `GR_FAHRPLAN_V`, `GR_JAHR_V`, `GR_BEFUND_V`, `GR_LETZTE_SITZUNG_V` |
| `sql/04_startdaten.sql` | die mitgelieferten Daten, aus dem Code erzeugt (`ant startdaten`); Zeilen mit `HERKUNFT = 'MITGELIEFERT'` werden beim Einrichten aufgefrischt, `EIGEN` bleibt unberührt |
| `sql/99_entfernen.sql` | entfernt alle GR_-Objekte (nur von Hand, z. B. im SQL Developer) |

Das Passwort steht in `db.properties` oder in der Umgebungsvariablen `GEIRANGER_DB_PASSWORD`. Bleibt es leer, fragt das
Startbild danach und trägt es nach dem ersten erfolgreichen Verbinden ein (Klartext).

## Prüfen

- Ohne Datenbank: `ant pruefung` (prüft auch die Skripte, die Spalten jeder Abfrage und ob `04_startdaten.sql` aktuell ist).
- Mit Datenbank: `test/com/dan/geiranger/test/DbPruefung.java` mit **Run File** (oder `ant dbpruefung`) – endet mit „Alles in Ordnung.“

## QGIS

1. *Layer → Data Source Manager → Oracle → New*: Name `Geiranger`, Host `localhost`, Port `1521`, Database `PDBORCL`,
   Benutzer `Geiranger`. „Only look in metadata table“ anhaken (dann erscheinen genau die Geometrien aus `02_spatial.sql`).
2. Verbinden, dann hinzufügen:
   - `GR_GEIRANGER_V` – die Szene in einer Ebene: Fälle, Strähnen, Fv63, Kehren, Orte, Gebäude, Elva, Merkpunkte; nach `EBENE` kategorisiert einfärben.
   - `GR_TAGESKARTE_V` – dazu die Chronik: Schiffsfahrten (`SCHIFFSFAHRT`, Weg), Hörner (`HORN`, mit Zahl der Echos in `WERT`),
     Busankünfte (`BUS`), Abfluss je Fall und Stunde (`ABFLUSS`). Filter `"TAG" IS NULL OR "TAG" = '2026-06-21'` zeigt einen Szenentag –
     das ist die Tageskarte, die die App im Block DATEN als PNG zeichnet.
   - `GR_HOEHENLINIE` (alle 100 m, Feld `HOEHE_M` als Beschriftung) und `GR_GELAENDE` (Ausschnitt des Rasters) als Hintergrund.
3. Projekt-KBS: WGS 84 (EPSG:4326) für Länge und Breite, oder ETRS89 / UTM 32N (EPSG:25832) für Meter – QGIS rechnet um.
4. Wer Objekte in QGIS anlegt oder verschiebt (z. B. Merkpunkte), setzt `HERKUNFT = 'EIGEN'`; die App liest sie beim nächsten Start.

## Hinweise

- Koordinaten: die Szene rechnet in Metern um den Anleger, gespeichert wird in Länge und Breite (8307). Lange Linien
  (Höhenlinien) stehen in `04_startdaten.sql` als PL/SQL-Block mit `DBMS_LOB`.
- Die Chronik wächst mit jeder Sitzung (eine Zeile je Szenenstunde, je Fahrt, je Bus). Wer neu anfangen will:
  `DELETE FROM GR_STUNDE` usw. – oder `99_entfernen.sql` und neu einrichten (eigene Daten gehen dabei verloren).
