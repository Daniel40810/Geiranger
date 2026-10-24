-- =====================================================================================================
-- Geiranger · Datenbank · 03_sichten.sql
-- Sieben Sichten: die Fälle mit Juni-Abfluss und dem Abfluss der letzten Szenenstunde, die Szene als eine Ebene
-- für QGIS (Fälle, Strähnen, Straße, Kehren, Orte, Gebäude, Elva, Merkpunkte), die Tageskarte (dazu Fahrten,
-- Hörner, Busse und Abfluss eines Szenentags), der Fahrplan mit Ankunft, das Jahr aus der Chronik (Schneegrenze,
-- Laub, Abfluss), die Befunde mit ihren Quellen und die letzte Sitzung für „Weiter wie zuletzt“.
-- Längen und Flächen auf dem Ellipsoid, Toleranz 5 cm. CREATE OR REPLACE: beliebig oft wiederholbar.
-- =====================================================================================================

-- Die Fälle mit Lage als Zahlen, Juni-Abfluss und dem Abfluss der jüngsten Szenenstunde der Chronik (Anteil am Juni mal
-- Juni-Abfluss; die Regenfälle laufen nur nach Regen und haben hier keinen Wert).
CREATE OR REPLACE VIEW GR_FALL_V AS
SELECT f.ID,
       f.NAME,
       f.ART,
       f.KANTE.SDO_POINT.X AS LAENGE,
       f.KANTE.SDO_POINT.Y AS BREITE,
       f.X_M,
       f.Y_M,
       f.Z_M,
       f.RICHTUNG_GRAD,
       f.HOEHE_M,
       f.BREITE_M,
       f.STRAEHNEN,
       f.JUNI_M3S,
       f.UEBERHANG,
       f.FALLHOEHE_M,
       f.FREI_MAX_M,
       f.FELS_ANTEIL,
       (SELECT COUNT(*) FROM GR_STRAEHNE x WHERE x.FALL_ID = f.ID) AS WEGE,
       s.SZENE_TEXT AS STAND,
       s.ABFLUSS_ANTEIL,
       CASE WHEN f.NUR_REGEN = 1 THEN NULL ELSE ROUND(f.JUNI_M3S * s.ABFLUSS_ANTEIL, 3) END AS ABFLUSS_JETZT_M3S,
       s.EIS,
       f.BEFUND,
       q.KURZ AS QUELLE,
       f.HERKUNFT,
       f.KANTE
FROM GR_FALL f
LEFT JOIN GR_QUELLE q ON q.ID = f.QUELLE_ID
LEFT JOIN (SELECT SZENE_TEXT, ABFLUSS_ANTEIL, EIS
             FROM (SELECT SZENE_TEXT, ABFLUSS_ANTEIL, EIS FROM GR_STUNDE ORDER BY ID DESC)
            WHERE ROWNUM = 1) s ON 1 = 1;

-- Die Szene als eine Ebene für QGIS: Fälle (Kante), Strähnen, Straße, Kehren, Orte, Gebäude, Elva und Merkpunkte.
-- OBJEKT_ID = Ebene × 10^9 + ID; Stil nach EBENE und ART.
CREATE OR REPLACE VIEW GR_GEIRANGER_V AS
SELECT 1000000000 + f.ID AS OBJEKT_ID, 'FALL' AS EBENE, f.ART, f.NAME, f.HOEHE_M AS WERT, f.KANTE AS GEOM
FROM GR_FALL f
UNION ALL
SELECT 2000000000 + x.ID, 'STRAEHNE', f.ART, f.NAME || ' · Strähne ' || x.NR, x.LAENGE_M, x.LINIE
FROM GR_STRAEHNE x JOIN GR_FALL f ON f.ID = x.FALL_ID
UNION ALL
SELECT 3000000000 + s.ID, 'STRASSE', s.ART, s.NAME, s.LAENGE_M, s.LINIE
FROM GR_STRASSE s
UNION ALL
SELECT 4000000000 + k.ID, 'KEHRE', 'KEHRE', 'Kehre ' || k.NR, k.Y_M, k.SCHEITEL
FROM GR_KEHRE k
UNION ALL
SELECT 5000000000 + o.ID, 'ORT', o.ART, o.NAME, o.Y_M, o.LAGE
FROM GR_ORT o
UNION ALL
SELECT 6000000000 + g.ID, 'GEBAEUDE', g.ART, NVL(g.NAME, g.ART || ' ' || g.NR), g.WAND_M, g.GRUNDRISS
FROM GR_GEBAEUDE g
UNION ALL
SELECT 7000000000 + e.ID, 'ELVA', e.ART, e.NAME, e.LAENGE_M, e.LINIE
FROM GR_ELVA e
UNION ALL
SELECT 8000000000 + m.ID, 'MERKPUNKT', 'EIGEN', m.NAME, m.Y_M, m.LAGE
FROM GR_MERKPUNKT m;

-- Die Tageskarte: die Szene (ohne Tag, immer da), dazu je Szenentag die Fahrten der Schiffe (Weg), die Hörner (Ort mit
-- Zahl der Echos), die Ankünfte des Busses und der Abfluss der Hauptfälle je Stunde (an der Kante).
-- QGIS: Ebene GR_TAGESKARTE_V, Schlüssel OBJEKT_ID, Filter  "TAG" = '2026-06-21' OR "TAG" IS NULL , Stil nach EBENE.
CREATE OR REPLACE VIEW GR_TAGESKARTE_V AS
SELECT g.OBJEKT_ID, g.EBENE, CAST(NULL AS VARCHAR2(10)) AS TAG, g.ART, g.NAME AS TEXT, g.WERT, g.GEOM
FROM GR_GEIRANGER_V g
UNION ALL
SELECT 9000000000 + s.ID, 'SCHIFFSFAHRT', s.SZENE_TAG, s.RICHTUNG, s.SCHIFF || ' ' || s.SZENE_TEXT || ' bis ' || s.AN_TEXT, s.DAUER_MIN, s.WEG
FROM GR_SCHIFFSFAHRT s WHERE s.WEG IS NOT NULL
UNION ALL
SELECT 10000000000 + s.ID, 'HORN', s.SZENE_TAG, s.RICHTUNG, s.SCHIFF || ': ' || s.HORN_TEXT, s.ECHOS, s.HORN
FROM GR_SCHIFFSFAHRT s WHERE s.HORN IS NOT NULL
UNION ALL
SELECT 11000000000 + b.ID, 'BUS', b.SZENE_TAG, 'BUS', b.HALT || ' ' || b.SZENE_TEXT || ', ' || b.GRUPPE || ' Gäste', b.GRUPPE, b.LAGE
FROM GR_BUSANKUNFT b WHERE b.LAGE IS NOT NULL
UNION ALL
SELECT 12000000000 + h.ID * 1000 + MOD(f.ID, 1000), 'ABFLUSS', h.SZENE_TAG, f.ART, f.NAME || ' ' || h.SZENE_TEXT, ROUND(f.JUNI_M3S * h.ABFLUSS_ANTEIL, 3), f.KANTE
FROM GR_STUNDE h CROSS JOIN GR_FALL f
WHERE f.ART IN ('HAUPT', 'NEBEN') AND h.ABFLUSS_ANTEIL IS NOT NULL;

-- Der Fahrplan mit Ankunft und Häfen (F31 A). Die App fährt nach dem Jahr der Uhr, sonst nach dem jüngsten Jahr davor.
CREATE OR REPLACE VIEW GR_FAHRPLAN_V AS
SELECT p.ID,
       p.LINIE,
       p.JAHR,
       p.RICHTUNG,
       CASE p.RICHTUNG WHEN 'NACH_HELLESYLT' THEN 'Geiranger' ELSE 'Hellesylt' END AS AB_HAFEN,
       p.ABFAHRT,
       CASE p.RICHTUNG WHEN 'NACH_HELLESYLT' THEN 'Hellesylt' ELSE 'Geiranger' END AS AN_HAFEN,
       TO_CHAR(TRUNC(MOD(p.ABFAHRT_MIN + p.FAHRZEIT_MIN, 1440) / 60), 'FM00') || ':' || TO_CHAR(MOD(p.ABFAHRT_MIN + p.FAHRZEIT_MIN, 60), 'FM00') AS ANKUNFT,
       p.FAHRZEIT_MIN,
       p.SAISON_VON,
       p.SAISON_BIS,
       (SELECT COUNT(*) FROM GR_SCHIFFSFAHRT s
         WHERE s.RICHTUNG = p.RICHTUNG AND SUBSTR(s.SZENE_TEXT, 12, 5) = p.ABFAHRT) AS FAHRTEN_CHRONIK,
       q.KURZ AS QUELLE,
       p.HERKUNFT
FROM GR_FAHRPLAN p
LEFT JOIN GR_QUELLE q ON q.ID = p.QUELLE_ID;

-- Das Jahr aus der Chronik: je Szenentag Schneegrenze, Schnee im Ort, Laub, Herbst und Eis der Fälle, dazu Regen und der
-- mittlere Abfluss der Stunden und der Abflussanteil des Klimas im selben Monat.
CREATE OR REPLACE VIEW GR_JAHR_V AS
SELECT t.SZENE_TAG AS TAG,
       TO_NUMBER(SUBSTR(t.SZENE_TAG, 6, 2)) AS MONAT,
       MIN(t.SCHNEEGRENZE_M) AS SCHNEEGRENZE_M,
       ROUND(AVG(t.SCHNEE_ORT_CM), 1) AS SCHNEE_ORT_CM,
       ROUND(AVG(t.SCHNEE_600_CM), 1) AS SCHNEE_600_CM,
       ROUND(AVG(t.LAUB), 3) AS LAUB,
       ROUND(AVG(t.HERBST), 3) AS HERBST,
       ROUND(MAX(t.EIS), 3) AS EIS,
       ROUND(AVG(t.LUFT_C), 2) AS LUFT_C,
       (SELECT ROUND(SUM(h.REGEN_MMH), 1) FROM GR_STUNDE h WHERE h.SZENE_TAG = t.SZENE_TAG) AS REGEN_MM,
       (SELECT ROUND(AVG(h.ABFLUSS_ANTEIL), 3) FROM GR_STUNDE h WHERE h.SZENE_TAG = t.SZENE_TAG) AS ABFLUSS_ANTEIL,
       (SELECT k.ABFLUSS_ANTEIL FROM GR_KLIMA_MONAT k WHERE k.MONAT = TO_NUMBER(SUBSTR(t.SZENE_TAG, 6, 2))) AS ABFLUSS_KLIMA,
       (SELECT k.TEMPERATUR_C FROM GR_KLIMA_MONAT k WHERE k.MONAT = TO_NUMBER(SUBSTR(t.SZENE_TAG, 6, 2))) AS TEMPERATUR_KLIMA_C
FROM GR_TAG t
GROUP BY t.SZENE_TAG;

-- Befunde mit ihren Quellen (Kurzzeichen) in einer Zeile.
CREATE OR REPLACE VIEW GR_BEFUND_V AS
SELECT b.ID,
       b.THEMA,
       b.GEGENSTAND,
       b.BEFUND,
       b.TEXT,
       (SELECT LISTAGG(q.KURZ, ', ') WITHIN GROUP (ORDER BY q.KURZ)
          FROM GR_BEFUND_QUELLE bq JOIN GR_QUELLE q ON q.ID = bq.QUELLE_ID
         WHERE bq.BEFUND_ID = b.ID) AS QUELLEN
FROM GR_BEFUND b;

-- Die jüngste Sitzung mit gesichertem Zustand, ob sauber beendet oder abgebrochen.
CREATE OR REPLACE VIEW GR_LETZTE_SITZUNG_V AS
SELECT *
FROM (SELECT s.ID, s.BEGINN, s.ENDE, s.SAUBER_BEENDET, s.APP_VERSION, s.QUALITAET, s.TON, s.SZENE_MS, s.SZENE_TEXT, s.SZENENZEIT, s.ZEITRAFFER,
             s.KAM_X, s.KAM_Y, s.KAM_Z, s.KAM_GIER_GRAD, s.KAM_NICK_GRAD, r.NAME AS REGIEBUCH, s.REGIE_SEKUNDE, s.ZUSTAND_ZEIT
      FROM GR_SITZUNG s
      LEFT JOIN GR_REGIEBUCH r ON r.ID = s.REGIEBUCH_ID
      WHERE s.ZUSTAND IS NOT NULL
      ORDER BY s.ZUSTAND_ZEIT DESC NULLS LAST, s.ID DESC)
WHERE ROWNUM = 1;

-- Ende 03_sichten.sql
