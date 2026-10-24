# Geiranger – Prompt für das Werkbuch (Entwurf 23.10.2026)

## Auftrag
Lege für das NetBeans-Projekt `C:\Users\Dan40810\Documents\NetBeansProjects\Geiranger` zuerst **nur ein Werkbuch** an
(Artefakt „Geiranger Werkbuch“), im Stil der Vorgänger (Glacier, Rungholt, Kīlauea): Auftrag, Bestand, Grundsatzfragen
F1 … Fn mit Antworten A/B/C (A = Vorschlag), Phasenplan, Abnahme je Phase mit Statusbild in `docs/status`.
Kein Code, keine Skripte, bevor die Grundsatzfragen beantwortet sind.

Szene: 3D-Echtzeitszene des Geirangerfjords. Ein **kleiner, hochdetaillierter Kernbereich** – Ort Geiranger,
Ørnesvingen (Serpentinen der Fv63 mit Aussichtspunkt) und die Wasserfälle (Sieben Schwestern, Friaren, Brudesløret) –
liegt auf **DTM1** (1 m). Die Umgebung bis zum Rand liegt auf **COP30**. Der Übergang zwischen beiden darf nicht sichtbar sein.

## Bestand im Projekt
- `lib/Renderer.jar` – fertige Packages aus den Vorgängern, u. a. road, river, spray, fog, cloud, rain, weather, season,
  sky, forest, ground, ocean, wave, buoyancy, flock, figure, kino, store, soundscape (Java 21, JOGL).
- `lib/FStyle.jar` (FFrame, Oberfläche), `lib/Avatar.jar` (Figuren, glTF-Lader), JOGL, ojdbc11, sdoapi/sdogr/sdoutl.
- `templates/rasters_COP30 (2)/output_hh.tif` – GLO-30, WGS84, Float32, 1260 × 576 px, 1″,
  6,95–7,30° O / 62,05–62,21° N (ca. 18 × 18 km).
- `templates/*.zip` – Scan-Assets: Gigantic Sandstone Terrain (glTF), Gigantic Tundra Terrain (FBX), Ice Cliff (FBX), je 4K-PBR.
- `templates/Geiranger1-3.jpg`, `Ørnesvingen1-3.jpg`, `Waterfall1-3.jpg` – Stimmungs-/Referenzbilder.
- `db/USER.sql` (Schema GEIRANGER per manage_user, Rolle DB_CONNECT), `db/db.properties(.example)`, `db/Role_Grantee.sql`.
- `src/`, `icon/`, `docs/status/` sind leer; `main.class` ist nicht gesetzt.

## Was das Werkbuch klären soll (Grundsatzfragen, Auswahl)
1. Zuschnitt des Kernbereichs: ein Fenster oder drei (Ort, Ørnesvingen, Wasserfälle); Auflösung 1 m oder 2 m im Kern.
2. Koordinatensystem der Szene: UTM 32 (EPSG:25832) für alles, COP30 dorthin umprojiziert.
3. Naht DTM1/COP30: Überblendband, Höhenabgleich, Detailstufen (LOD) über die ganze Fläche.
4. Fjordgrund: flacher Spiegel oder echte Tiefendaten.
5. Felswände: reines Höhenfeld oder zusätzliche Wandgeometrie an den Fällen (Überhänge, Nischen hinter dem Wasser).
6. Wasserfälle als neue Engine (freier Fall, Aufreißen in Strähnen, Gischt und Nebel am Fuß, Abfluss nach Jahreszeit),
   anknüpfend an spray und river.
7. Ørnesvingen mit com.dan.road: Trasse aus echten Achsdaten, Haarnadelkurven, Aussichtsplattform, Busse und Wohnmobile.
8. Rolle von Blender (siehe unten).
9. Verwendung der Scan-Assets: welche passen (Tundra für die Hochfläche?), welche entfallen (Sandstein?), wofür das Ice Cliff.
10. Weitere Themen: Schiffe im Fjord, Ort Geiranger (Häuser, Kirche), Wetter und Tageszeit, Jahreszeit (Schmelzwasser).
11. Datenbank: Schema GEIRANGER mit Präfix (z. B. GR_), DTM1 als GeoRaster, store-Package für Presets/Sitzung.
12. Programmsymbol (ico) im Stil der Vorgänger.

## Blender
Blender 5.2 LTS mit MCP-Erweiterung und BlenderGIS steht zur Verfügung. Vorschlag für F8:
A) Blender nur als Werkbank zur Vorbereitung (DEM-Prüfung, Asset-Umwandlung nach GLB, Kamerapositionen nach den Fotos,
   ggf. Wandgeometrie an den Fällen formen); die Szene selbst läuft in Java.
B) Blender als Geometriequelle wie bei FWaterfall (auch Wasser).
C) Ohne Blender.
