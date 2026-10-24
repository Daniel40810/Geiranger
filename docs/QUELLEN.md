# Geiranger – Quellen und Befunde

Erzeugt von `tools/Quellenliste` aus `src/com/dan/geiranger/data/quellen.txt` und `befunde.txt`. Dieselben Zeilen stehen in den Tabellen `GR_QUELLE`, `GR_BEFUND` und `GR_BEFUND_QUELLE` (Sicht `GR_BEFUND_V`). Jeder Befund ist **belegt** (eine Quelle nennt es), **gemessen** (aus Daten wie den HGT-Kacheln abgeleitet) oder **gestaltet** [G] (so gebaut, dass es glaubhaft ist, ohne Beleg).

10 Quellen · 25 Befunde (9 belegt, 1 gemessen, 15 gestaltet) · 10 Vorlagen · 5 Bibliotheken

## Quellen

| Kurz | Art | Titel | Abgerufen | Wofür |
|---|---|---|---|---|
| WP_SCHWESTERN | Netz | [Wikipedia: Seven Sisters Waterfall (Norway)](https://en.wikipedia.org/wiki/Seven_Sisters_Waterfall_(Norway)) | 18.10.2026 | Höhen, Lage, Friaren, Knivsflå |
| WP_FV63 | Netz | [Wikipedia: Norwegian County Road 63](https://en.wikipedia.org/wiki/Norwegian_County_Road_63) | 18.10.2026 | Länge, Ørnevegen bis 620 m, Wintersperre |
| FJORDNORWAY | Netz | [Fjord Norway: The Geirangerfjord](https://www.fjordnorway.com/en/see-and-do/the-geirangerfjord) | 18.10.2026 | Länge, Tiefe, Gipfel, Skageflå und Knivsflå |
| WP_KIRCHE | Netz | [Wikipedia: Geiranger Church](https://en.wikipedia.org/wiki/Geiranger_Church) | 21.10.2026 | achteckige weiße Holzkirche von 1842, Dachreiter |
| DANGEROUSROADS | Netz | [dangerousroads.org: Ørnesvingen road](https://www.dangerousroads.org/europe/norway/9248-ornesvingen-road.html) | 20.10.2026 | 11 Kehren, 7,3 km ab Geiranger, höchstens 10 %, höchster Punkt 620 m bei Korsmyra |
| WP_FAEHRE | Netz | [Wikipedia: Geiranger–Hellesylt Ferry](https://en.wikipedia.org/wiki/Geiranger%E2%80%93Hellesylt_Ferry) | 22.10.2026 | die Linie, die Fähren, rund eine Stunde Fahrt; Fahrplan 2025 nach Fjord1 (2026 eingestellt) |
| KYSTVERKET | Netz | [Kystverket: Ankeranvisning Geiranger 2026](https://www.kystverket.no/contentassets/f9d6d17d609144a6afc371582ce08144/ankeranvisning-utskriftvenlig-2026.pdf/download) | 22.10.2026 | Ankerplätze, 8 und 5 Knoten vor dem Ort |
| DTM1 | Daten | [data.norge.no: Høyde DTM1](https://data.norge.no/en/datasets/1a7327eb-1fa5-3432-8dea-fc198a5ede13/hoyde-dtm1) | 17.10.2026 | Bezug über hoydedata.no, GeoTIFF, CC BY 4.0; für die Szene nicht verwendet (F6: HGT 1″) |
| HGT | Daten | HGT-Kacheln 1″ N62E006 und N62E007 (DEM1) | 18.10.2026 | Oberflächenmodell, Hauptraster 12 m und Fernring 120 m aus tools.HgtImport (Phase 2) |
| APP | App | Geiranger Studio: gestaltet [G] |  | Was die Szene setzt, wo keine Quelle es belegt: Lagen, Abläufe, Zahlen mit [G] im Werkbuch |

## Befunde nach Thema

### Fjord

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Länge und Tiefe | belegt | Rund 20 km als innerster Arm des Storfjords, knapp 260 m tief; der gemalte Grund (Ufer 10 m, Mitte 150 m, Rinne 250 m) ist gestaltet [G] | FJORDNORWAY |
| Fjordgrund | gestaltet [G] | Das Raster kennt unter dem Spiegel nichts; Ufer, Mitte und Rinne sowie das Delta der Geirangelva sind gemalt (F7) | APP |

### Gelände

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Raster | gemessen | HGT 1″ (N62E006, N62E007), Hauptraster 2048² zu 12 m, Fernring 512² zu 120 m, Ursprung am Anleger (UTM 32 N) | HGT |
| Wände unter den Fällen | gestaltet [G] | Das 1″-Raster rundet die Wände auf 50 bis 76°; unter den Schwestern (250 m) und Friaren (120 m) schärft die App eine Stufe (F12b A) | APP |

### Fälle

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Sieben Schwestern | belegt | 410 m gesamt, rund 250 m freier Fall, sieben Strähnen, gleich südlich von Knivsflå; Kante und Abfluss im Juni (2,5 m³/s) gestaltet [G] | WP_SCHWESTERN, APP |
| Friaren | belegt | Der Freier gegenüber den Schwestern auf der Südseite; Höhe 300 m und Abfluss gestaltet [G] | WP_SCHWESTERN, APP |
| Brudesløret | gestaltet [G] | Der Brautschleier am Südufer westlich von Friaren nach der Karte; Höhe (560 m) und Abfluss gestaltet [G] | APP |
| Storsæterfossen | gestaltet [G] | Über Vinje, begehbar hinter dem Wasser; Lage nach Karte und Abflussnetz ± 300 m, 30 m hoch [G] | APP |
| Abfluss im Jahr | gestaltet [G] | Anteil am Juni je Monat (Schmelze von Ende April bis Juli, Rest unter dem Eis), Regenspeicher mit 6 h Halbwertszeit; an Pegeln der NVE nicht geprüft (F13) | APP |
| Regenfälle | gestaltet [G] | Rinnsale an den steilsten Wänden von Kern West (bis zu sechs, im Gelände fünf gefunden), die nur nach Regen laufen [G] | APP |

### Elva

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Geirangelva | gestaltet [G] | Lauf aus dem Abflussnetz des Geländes, Bett eingetieft, Abfluss im Juni 25 m³/s [G]; der Storfossen als Stufe von 11 auf 67 m | APP |

### Straße

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Ørnevegen | belegt | Fv63 in elf Kehren vom Ufer bis Korsmyra (620 m), höchstens 10 %; Lage der Kehren nach Karte und Fotos (F14), die Plattform an der elften Kehre | WP_FV63, DANGEROUSROADS, APP |
| Wintersperre | belegt | Fv63 Langvatnet–Geiranger meist von Anfang November bis Ende Mai gesperrt | WP_FV63 |

### Der Ort

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Kirche | belegt | Achteckige weiße Holzkirche von 1842 mit Dachreiter; Lage in der Szene nach Koordinaten und Gelände | WP_KIRCHE |
| Gebäude | gestaltet [G] | Rund 60 Gebäude: Hotel Union, Hotel Geiranger, Terminal, Läden, Wohnhäuser, Camping, Naust; Lage, Farbe und Dach gestaltet [G] | APP |
| Höfe Knivsflå und Skageflå | belegt | Verlassene Fjordhöfe auf schmalen Felsbändern; Häuser und Lage auf dem Band gestaltet [G] | FJORDNORWAY, APP |

### Schiffe

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Fähre Geiranger–Hellesylt | belegt | Rund eine Stunde Fahrt, Saison im Sommer; die Szene fährt nach dem Fahrplan 2025, Fjord1 hat die Linie 2026 eingestellt | WP_FAEHRE |
| Ankerplatz und Fahrt vor dem Ort | belegt | Ankerplätze vor dem Ort, 8 Knoten im Fahrwasser, 5 Knoten im Hafen | KYSTVERKET |
| Kreuzfahrtschiff | gestaltet [G] | Mai bis September, ein Anlauf am Tag (ein 8:00, aus 17:30), GM 2,2 m nach Nacharbeit 12 [G] | APP |

### Klima

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Klima „Geiranger“ | gestaltet [G] | Monatsmittel der Temperatur, Niederschlag und Regentage im Ort, Lagen und Übergänge der Wetterregie [G] | APP |

### Jahr

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Höhenstufen | gestaltet [G] | Zehn Stufen alle 200 m, 0,65 K je 100 m kälter, 5 % je 100 m mehr Niederschlag [G] | APP |

### Wald

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Biom Vestland | gestaltet [G] | Birke bis rund 760 m, Erle am Wasser, Kiefer auf Absätzen, Fichte in Beständen; Laub nach den Fotos Geiranger1 bis 3 [G] | APP |

### Menschen

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Busgruppe | gestaltet [G] | Ausflugsbus von Juni bis August alle 45 min, 12 min Halt in einer Bucht vor der Plattform, 22 bis 31 Gäste [G] | APP |

### Klang

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Echo der Wände | gestaltet [G] | Spiegelquellen aus dem Gelände (Lambert-Rückstreuung), Abschattung hinter Graten nach Maekawa [G] | APP |

### Regie

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Regiebücher | gestaltet [G] | „Ein Junitag“, „Fahrt über die Ørnesvingen“, „Mit der Fähre von Hellesylt“: Abläufe, Zeiten und Blickpunkte gestaltet [G] | APP |

## Vorlagen in `templates/`

Nicht im Git (mehrere GB, fremde Rechte); was die App braucht, liegt nach dem Import in `daten/`.

| Datei | Was | Wofür |
|---|---|---|
| Karte-Geirangerfjord.png | Karte des Fjords mit Ort, Fv63 und Höfen | Lage der Kernfenster (F1), Achse der Ørnesvingen (F14), Kehrenstreifen |
| N62E006.zip, N62E007.zip (HGT 1″) | Oberflächenmodell „DEM1“, 1 Bogensekunde | Gelände der ganzen Szene (tools.HgtImport, Phase 2) |
| N58E007.zip, N62E008.zip | weitere HGT-Kacheln | geprüft, nicht im Ausschnitt |
| rasters_COP30 …/output_hh.tif | Copernicus GLO-30 | Vergleich mit dem HGT (+0,9 m, Streuung 6,5 m) |
| Geiranger1.jpg … Geiranger3.jpg | Fotos des Orts und der Hänge | Farben der Häuser, Wald bis an die Wände, vergraute Bohlen der Höfe |
| Ørnesvingen1.jpg … Ørnesvingen3.jpg | Fotos von der Plattform und den Kehren | Plattform, Blick zum Ort, Ansicht 1 |
| Waterfall1.jpg … Waterfall3.jpg | Fotos der Sieben Schwestern | Strähnen, Gischt, nasse Wand (Phase 7) |
| gigantic_tundra_terrain_viyjcbq_high.zip | Scan „Gigantic Tundra Terrain“ | Zeichnung der Tundra über der Waldgrenze (F10 A, daten/tundra/) |
| gigantic_sandstone_terrain_…zip, ice_cliff_…zip | weitere Scans | geprüft, nicht verwendet |
| S1C_IW_RAW__0SDV_20261023…SAFE.zip | Sentinel-1-Rohdaten | geprüft, für die Szene nicht nötig |

## Bibliotheken

| Datei | Inhalt |
|---|---|
| lib/Renderer.jar | 33 Packages aus Lighthouse, Haithabu, Glacier und Rungholt, seit Phase 18 mit fall und cliff aus Geiranger (Quelle: Projekt Renderer) |
| lib/jogl-all.jar, gluegen-rt.jar | JOGL (OpenGL 4.3) mit den Windows-Natives |
| lib/FStyle.jar | die Oberfläche im F-Stil (FFrame, FButton, FComboBox …) |
| lib/Avatar.jar | Menschen (com.dan.avatar, F18 A) |
| lib/ojdbc11.jar, sdoapi.jar, sdogr.jar, sdoutl.jar | Oracle JDBC und Spatial (Schema GR_, Phase 17) |
