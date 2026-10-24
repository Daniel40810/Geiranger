# com.dan.cliff – Steilwände

**Stand: Engine (Phase 5 von Geiranger).** Fels als Material und Feinform für jede Gebirgsszene: Gneis in Bändern entlang der Schieferung, Klüfte, Feinform, die die Wand im Scheitelshader verschiebt, Flechten, Moos, Sickerstreifen, Nässe nach Regen, Schnee auf Absätzen, Geröll am Fuß, nasse Streifen unter Fällen und Überhänge als Zusatzgeometrie. Das Package kennt keinen Fjord: Höhenfeld, Nässe, Schnee und die Lage der Fälle kommen von außen. Der Kern braucht nur Java 21, `gl/` zusätzlich JOGL, `demo/` FStyle. Zum Herauskopieren reicht `src/com/dan/cliff/`; in Phase 18 geht es nach `Renderer.jar`.

## Was es kann

- **Keine Streifen an senkrechten Stellen:** Alles ist 3D-Rauschen am Ort, keine Projektion auf Ebenen (auch keine dreiachsige). An einer senkrechten Wand ändert sich die Farbe hoch und quer gleich stark (Prüfung: Verhältnis 1,2).
- **Gneis:** Bänder entlang einer schwach geneigten Schieferung (10° nach Nordwest [G]) in drei Maßstäben (1,6 m, 7 m, 33 m), mit 3D-Rauschen verbogen, dazu rötliche Feldspatlagen und Korn.
- **Klüfte:** zwei senkrechte Scharen (Streichen 30° und 115°, Abstand 9 m [G]) als dunkle Fugen, die mal offen, mal geschlossen sind.
- **Feinform:** Relief entlang der Normale in bis zu vier Oktaven (24 m, 8 m, 2,7 m, 0,9 m; Höhe 4 m), gratig wie verwitterte Bänke, je Oktave gedreht gegen Kästchen im Gitter. Der Scheitelshader verschiebt die Wand bis `Quality.reliefRange`; der Bildpunkt neigt die Normale aus demselben Relief. Feine Oktaven blenden mit der Bildpunktgröße aus (nichts flimmert, in 2 km bleibt die Grobform).
- **Wandkarte** (`CliffMap`): je Zelle Wand, Geröll (unter einer Wand von mindestens 25 m im Umkreis von 110 m), Nässe (Streifen unter Fällen: `addFall` geht vom Fuß bergauf bis zur Kante, unten breiter) und Absatz (flache Stellen in der Wand für Moos und Schnee). Gerechnet einmal je Höhenfeld auf dem Prozessor, als RGBA8 auf die Grafikkarte.
- **Bewuchs und Wetter:** Flechten auf trockenem, steilem Fels; Moos auf nassen Absätzen; Sickerstreifen; nach Regen dunkel und glänzend; Schnee auf Absätzen und flachen Stellen.
- **Überhänge** (`Overhang`): eine Felsnase vor der Wand, oben am weitesten, unten hohl, mit Rauschen; die Rückseite steckt 4 m in der Wand. Gezeichnet mit demselben Material.
- **„Wand hier?“:** `Cliff.isWall(Neigung)`, `Cliff.wallAt(Höhenfeld, x, z, e)` für `fall` und `forest`.
- **Reproduzierbar und schnell:** ganzzahliger Hash (pcg3d) auf einem Gitter, das sich nach 64 Zellen wiederholt. Die Grafikkarte holt die Gitterwerte aus einer 3D-Textur 64³ (R16, Einheit 15): ein Abruf je Rauschwert statt acht Hashes, die quintische Glättung steckt in der Stelle des Abrufs. Die Normale kommt aus den Ableitungen im Bild (ein Reliefwert statt vier); ohne Fels und Schnee rechnet der Bildpunkt nichts. Der Selbsttest vergleicht 64 Punkte (Farbe, Relief); die Filterung der Grafikkarte rundet die Gewichte, Abweichungen von Bruchteilen eines Prozents sind normal.
- **Stufen:** `LOW` (2 Oktaven, ohne Überhänge, Feinform bis 600 m), `MEDIUM` (3, bis 1,5 km), `HIGH` (3, bis 2,5 km), `ULTRA` (4, bis 4 km).

## Benutzung

```java
Cliff cliff = new Cliff();
CliffMap map = CliffMap.build(cliff, terrain::height, x0, z0, 12, 2048);
map.addFall(fussX, fussZ, 400, 30, 1);              // nasser Streifen unter einem Fall

CliffGl gl = new CliffGl();
gl.init(gl4);
gl.upload(gl4, map);
gl.setOverhangs(List.of(new Overhang(x, y, z, 180, 46, 26, 11, 7)));

// im eigenen Shader: Include-Zeile mit dem Pfad /com/dan/cliff/gl/cliff.glsl, dann
//   vec3 p = cliffDisplace(world, normal, footprint, dist, range);          (Scheitelshader, vorher #define CLIFF_VERTEX)
//   CliffSample s = cliffMaterial(world, normal, rainWet, snow, footprint); (Bildpunkt)
gl.bind(gl4, program, cliff, 14);                   // Uniforms, Wandkarte auf Einheit 14, Rauschtextur auf 15
gl.drawOverhangs(gl4, cliff, view, light);          // Zusatzgeometrie in das gebundene Ziel
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `CNoise` | Hash und Wertrauschen (3D, 1D), Ausblenden nach Bildpunktgröße |
| `Cliff` | Eingänge, Stufen, Relief, Material, „Wand hier?“ |
| `CliffMap` | Wandkarte: Wand, Geröll, Nässe unter Fällen, Absatz |
| `Overhang` | Überhang als Netz |
| `gl/cliff.glsl` | Relief und Material Zeile für Zeile wie der Kern |
| `gl/CliffGl` | Uniforms, Wandkarte, Überhänge und Wand als Netz (`rock.vert`, `rock.frag`), Selbsttest (`check.vert`, `check.frag`) |
| `demo/DemoWall` | die Wand der Vorführung als Höhenfeld |
| `demo/CliffScene`, `demo/CliffDemo` | Vorführung mit Reglern; `--bild datei.png [breite]` rechnet sechs Ansichten |
| `demo/CliffCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Demo und Selbsttest

- `com.dan.cliff.demo.CliffDemo`: eine Wand von 300 m mit Absatz, Überhang, nassem Streifen und Geröll. Regler für Feinform, Kluftabstand, Flechten, Moos, Nässe, Schnee, Sonnenhöhe und -richtung, Stufe; „Aus 2 km“, „Aus 50 m“. Unten die Zeit des Felses auf der Grafikkarte.
- `CliffDemo --bild datei.png` rechnet die Wand aus 2 km, aus 50 m, senkrecht von der Seite, am Abend, nach Regen und im Mai mit Schnee.
- `CliffCheck`: 19 Prüfungen ohne Grafikkarte.
- `CliffGl.selfCheck`: 64 Punkte auf der Grafikkarte gegen den Prozessor (auf Mesa: Farbe ± 0,00 %, Relief ± 0,0 mm).

## Grenzen

- Nischen hinter Fällen (Hohlräume in der Wand) kann ein Höhenfeld nicht; sie kommen in Phase 7 mit den Fällen in der Szene als Zusatzgeometrie hinter dem Schleier.
- Die Feinform verschiebt die Wand entlang der Normale des Höhenfelds; Kanten werden dadurch rauer, aber nicht schärfer als das Raster.
- Die Feinform blendet dicht vor dem Auge (unter 40 m) aus, damit die Wand nicht über ein Auge wächst, das knapp über dem Höhenfeld steht.
- Schatten der Feinform fehlen (die Schatten der Wände kommen aus dem Höhenfeld der App).
- Farben und Maße sind gesetzt [G], nach den Fotos von Geiranger; ein anderer Fels (Kalk, Granit) braucht andere Werte.

## Seit Phase 7 von Geiranger

- `CliffMap.paintWet(x, z, breite, stärke)`: Nässe an beliebigen Punkten, etwa die nasse Linie aus `com.dan.fall` (die Wandkarte kennt den Fall nicht, nur die Punkte). `addFall` bleibt für Fälle ohne eigenen Weg.
- `Cliff.fallWet` (0..1): wie nass die Streifen unter den Fällen gerade sind, nach dem Abfluss; die Karte hält die Lage, dieser Wert die Stärke (Uniform `uCliffFallWet`, Prozessor und Grafikkarte gleich).
- Behoben: `CliffGl.upload` löschte beim Hochladen der Wandkarte die 3D-Rauschtextur; danach rechnete der Fels ohne Rauschen (Selbsttest ± 96 %).

