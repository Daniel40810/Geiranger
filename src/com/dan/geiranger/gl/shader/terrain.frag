// terrain.frag — das Gelände aus den HGT-Kacheln (Phase 2), mit dem gemalten Grund des Fjords (Phase 3). Fels seit
// Phase 5 aus com.dan.cliff (cliff.glsl); sonst Platzhalter, bis forest und ground (Phase 10) und season (Phase 14) übernehmen
// [G, nach den Fotos]:
//   Fels (Gneis) ab rund 42° Neigung, mit Bändern quer zur Wand; Wald (Birke, Erle) bis zur Waldgrenze bei 750 m;
//   Bergwiese auf flachen Stellen unter der Waldgrenze; Tundra und Blockfelder darüber; Schneefelder über 1 300 m auf
//   flachen Stellen (Juni); ein dunkler, nasser Saum am Ufer; unter dem Spiegel grauer Schlick und Geröll.
// Das Wasser zeichnet seit Phase 3 com.dan.ocean über dem Gelände. uWaterMode:
//   0  ocean läuft: hier nur der Grund (unter der Fläche, sichtbar im Flachen und am Ufer);
//   1  ocean fehlt: der Spiegel aus Phase 2 als Platzhalter (terrain.vert hebt den Grund auf den Spiegel);
//   2  „Grund zeigen“: kein Wasser, der Grund nach Tiefe gefärbt, Linien alle 50 m, hell bei Ufer 10 m, Mitte 150 m
//      und Rinne 250 m (Abnahme von Phase 3).
// uClip = 1: das Spiegelbild (gezeichnet vom Auge unter dem Spiegel); alles unter dem Wasser fällt weg.
// Licht seit Phase 4: Sonne und Mond aus com.dan.sky (vorbelichtet), Himmelslicht als Bestrahlung/π (uSkyAmb), die
// Schatten der Wände je Sonnenstand (schatten.frag, Karten über Hauptraster und Fernring) und der Wolkenschatten;
// ohne com.dan.sky der Platzhalter aus Phase 1 (Lambert und Himmel). Dunst nach Entfernung und Höhe (common.glsl). Die Normale kommt je Bildpunkt aus der Höhentextur, damit die Wände in der Ferne Form haben.
// Mit uMarks = 1 die Kernfenster (gelb) und die Orte (Punkte) auf dem Gelände.
#include "common.glsl"
#include "terrain_height.glsl"
#include "/com/dan/cliff/gl/cliff.glsl"
in vec3 vWorld;
in vec3 vNormal;
in float vDist;
out vec4 frag;
uniform int uMarks;
uniform vec4 uKernWest, uKernOst;
uniform vec4 uPoints[8];
uniform int uPointCount;
uniform int uWaterMode;
uniform int uClip;
uniform int uCliffOn;       // Phase 5: Fels aus com.dan.cliff (Gneis, Klüfte, Feinform, Flechten, Moos, Nässe, Geröll)
uniform float uPixelAngle;
uniform float uRainWet;     // Nässe nach Regen 0..1
uniform sampler2D uWallMain, uWallFern;   // Phase 4: Anteil direkten Lichts (Schatten der Wände)
uniform vec3 uWallRectMain, uWallRectFern; // Ecke x, z, Kantenlänge
uniform int uWallOn;
uniform sampler2D uCloudShadow;           // Phase 4: Wolkenschatten (com.dan.cloud)
uniform vec4 uCloudRect;                  // Ecke x, z, Kantenlänge, an
uniform vec4 uWiesen[2];                  // Phase 10: Hofwiesen x, z, Radius
uniform int uWiesenN;
uniform sampler2D uTundra;                // Phase 10 (F10 A): Kachel aus dem Scan „Gigantic Tundra Terrain“, sRGB, gespiegelt
uniform int uTundraOn;
uniform sampler2D uWald;                  // Nacharbeit 10: Waldkarte (Vestland.wald 0..1) über der Umgebung
uniform vec4 uWaldRect;                   // Ecke x, z, Kante x (0 = aus), Kante z
uniform float uSnowBand[10];              // Phase 14: Schnee je Höhenstufe (alle 200 m ab NN, m Höhe mal Bedeckung) aus season
uniform vec4 uJahr;                       // Phase 14: Laub (0 kahl … 1), Herbst (0 … 1), Schnee in den Kronen, Gras (0 matt … 1)
uniform int uJahrOn;

// Phase 14: Schnee auf ebenem Boden in der Höhe y (m), zwischen den Stufen linear
float snowAt(float y) {
    float u = clamp(y / 200.0, 0.0, 9.0);
    int a = int(floor(u)), b = min(a + 1, 9);
    return mix(uSnowBand[a], uSnowBand[b], u - float(a));
}

// Anteil direkten Lichts aus den Schatten der Wände; am Rand des Hauptrasters in den Fernring übergeblendet
float wallLight(vec2 w) {
    if (uWallOn == 0) return 1.0;
    float f = 1.0 - smoothstep(0.0, uNaht, insideDist(w));
    float a = f < 1.0 ? textureLod(uWallMain, (w - uWallRectMain.xy) / uWallRectMain.z, 0.0).r : 1.0;
    vec2 fu = (w - uWallRectFern.xy) / uWallRectFern.z;
    float b = f > 0.0 && all(greaterThanEqual(fu, vec2(0.0))) && all(lessThanEqual(fu, vec2(1.0))) ? textureLod(uWallFern, fu, 0.0).r : 1.0;
    return mix(a, b, f);
}
float cloudLight(vec2 w) { return uCloudRect.w > 0.0 ? textureLod(uCloudShadow, (w - uCloudRect.xy) / uCloudRect.z, 0.0).r : 1.0; }

// Phase 8: Hash ohne Sinus (Hoskins, „hash without sine“): gleiche Statistik, ein Viertel der Kosten auf RDNA
float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}
float fbm(vec2 p) { return 0.5 * vnoise(p) + 0.3 * vnoise(p * 2.1 + 3.7) + 0.2 * vnoise(p * 4.3 - 1.3); }

float line(float d, float w) {
    float f = max(fwidth(d), 1e-4);
    return 1.0 - smoothstep(w, w + 1.5 * f, abs(d));
}
float rectDist(vec2 p, vec4 r) {
    vec2 c = 0.5 * (r.xy + r.zw), h = 0.5 * (r.zw - r.xy);
    vec2 q = abs(p - c) - h;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0);
}

void main() {
    if (uClip == 1 && vWorld.y < uLevel - 0.2) discard;
    vec2 w = vWorld.xz;
    vec3 eye = uCam - vWorld;
    float dist = length(eye);
    vec3 v = eye / max(dist, 1e-3);
    // Normale je Bildpunkt (Abstand der Proben wächst mit der Entfernung)
    float lvl = heightLevel(dist);
    float e = max(6.0, dist * 0.0015);
    // Phase 9: wo das Feinraster formt (Einschnitt und Damm der Straße), feiner abtasten, sonst verschwimmt die Böschung
    e = mix(e, max(1.5, dist * 0.002), smoothstep(0.02, 0.3, abs(feinAt(w))) * (1.0 - smoothstep(300.0, 600.0, dist)));
    float hx0 = heightAt(w - vec2(e, 0.0), lvl), hx1 = heightAt(w + vec2(e, 0.0), lvl);
    float hz0 = heightAt(w - vec2(0.0, e), lvl), hz1 = heightAt(w + vec2(0.0, e), lvl);
    vec3 n = normalize(vec3(hx0 - hx1, 2.0 * e, hz0 - hz1));
    float y = vWorld.y;

    // ---------------------------------------------------------------- Land
    float slope = 1.0 - n.y;                                   // 0 eben … 1 senkrecht
    float rn = fbm(w * 0.011), rn2 = fbm(w * 0.045 + 7.0);
    float rock = smoothstep(0.22 + 0.06 * rn, 0.34 + 0.06 * rn, slope);
    float tree = 1.0 - smoothstep(680.0 + 120.0 * rn, 820.0 + 120.0 * rn, y);
    float snow = smoothstep(1250.0 + 200.0 * rn, 1400.0 + 200.0 * rn, y) * (1.0 - smoothstep(0.18, 0.30, slope)) * smoothstep(0.35, 0.6, rn2 + 0.15);
    // Phase 14: Schnee aus season je Höhe; nahe der Schneegrenze fleckig, steile Hänge halten ihn schlechter
    float sdep = uJahrOn == 1 ? snowAt(y) : 0.0;
    if (uJahrOn == 1) snow = smoothstep(0.015, 0.10, sdep * (0.5 + 1.0 * rn2)) * (1.0 - smoothstep(0.30 + 0.08 * rn, 0.58, slope));
    vec3 forest = mix(vec3(0.075, 0.115, 0.045), vec3(0.11, 0.15, 0.06), rn2);
    vec3 meadow = mix(vec3(0.20, 0.25, 0.09), vec3(0.24, 0.27, 0.12), rn);
    if (uJahrOn == 1) {
        // Phase 14: Gras matt im Winter, Birken kahl, im Herbst gelb (die Fichten und Kiefern bleiben grün)
        meadow = mix(mix(vec3(0.21, 0.19, 0.12), vec3(0.24, 0.21, 0.13), rn), meadow, uJahr.w);
        float birke = smoothstep(0.35, 0.65, rn2);
        vec3 kahl = mix(vec3(0.10, 0.085, 0.07), vec3(0.13, 0.11, 0.09), rn);
        forest = mix(forest, mix(kahl, forest, uJahr.x), birke);
        forest = mix(forest, mix(vec3(0.30, 0.20, 0.04), vec3(0.36, 0.16, 0.05), rn), uJahr.y * uJahr.x * birke * 0.85);
    }
    vec3 tundra = mix(vec3(0.26, 0.25, 0.17), vec3(0.33, 0.32, 0.27), rn2);
    // Gneis mit Bändern, die der Höhe folgen (wie Lagen quer zur Wand); Feineres kommt aus cliff
    // (Rauschen entlang der Wand und in der Höhe, nicht in der Kartenebene: sonst ziehen senkrechte Wände Streifen)
    // (Phase 8: nur noch ohne cliff gerechnet – mit cliff wurde es überschrieben und kostete zwei fbm je Bildpunkt)
    vec3 gneis;
    if (uCliffOn == 0 || y <= uLevel - 3.0) {
        float rw = fbm(vec2((w.x + w.y) * 0.004, y * 0.012)), rw2 = fbm(vec2((w.x - w.y) * 0.02, y * 0.05) + 3.0);
        float band = 0.5 + 0.5 * sin(y * 0.11 + rw * 6.0);
        gneis = mix(vec3(0.22, 0.22, 0.22), vec3(0.34, 0.33, 0.31), 0.5 * band + 0.3 * rw2 + 0.2 * rw);
    } else {
        // Phase 5: com.dan.cliff statt des Platzhalters; die Normale neigt sich mit der Feinform. Unter dem Wasser nichts,
        // im Spiegelbild nur die grobe Form (vierfacher Maßstab: das Spiegelbild ist klein und die Wellen verziehen es)
        CliffSample cs = cliffMaterial(vWorld, n, uRainWet, clamp(sdep * 6.0, 0.0, 1.0), dist * uPixelAngle * (uClip == 1 ? 4.0 : 1.0));
        rock = cs.rock;
        gneis = cs.albedo;
        n = normalize(mix(n, cs.normal, cs.rock));
    }
    float flat_ = 1.0 - smoothstep(0.06, 0.16, slope);
    // Phase 10: Tundra mit der Zeichnung des Scans (Farbe relativ zu seinem Mittel, rund 90 m je Kachel, zwei Maßstäbe)
    if (uTundraOn == 1) {
        vec3 t1 = texture(uTundra, w / 90.0).rgb, t2 = texture(uTundra, w / 23.0 + 0.37).rgb;
        vec3 avg = vec3(0.0836, 0.0664, 0.0308);
        vec3 k = clamp(mix(t1, t2, 0.35) / avg, vec3(0.45), vec3(2.2));
        tundra = mix(tundra, tundra * k * vec3(0.95, 1.0, 1.05), 0.85 * (1.0 - smoothstep(2500.0, 6000.0, dist)));
    }
    // Phase 10: Hofwiesen (gemäht, heller) statt Wald
    float hof = 0.0;
    for (int i = 0; i < uWiesenN; i++) hof = max(hof, 1.0 - smoothstep(uWiesen[i].z - 20.0, uWiesen[i].z + 8.0, length(w - uWiesen[i].xy)));
    meadow = mix(meadow, vec3(0.17, 0.25, 0.075), hof);
    forest = mix(forest, meadow, hof);
    vec3 low = mix(forest, meadow, max(flat_ * 0.6, hof));
    vec3 ground = mix(tundra, low, tree);
    vec3 alb = mix(ground, gneis, rock);
    // Nacharbeit 10: das Kronendach aus der Waldkarte. Wo Wald steht, ist der Hang dunkelgrün, auch wo die Bäume in der
    // Ferne ausgedünnt sind und auf steilem Fels (Fotos Geiranger1 bis 3: die Hänge sind grün, nicht grau)
    float waldDecke = 0.0;
    if (uWaldRect.z > 0.0) {
        float wd = texture(uWald, (w - uWaldRect.xy) / uWaldRect.zw).r;
        vec3 krone = mix(vec3(0.030, 0.062, 0.022), vec3(0.055, 0.095, 0.032), fbm(w * 0.07)) * (0.85 + 0.3 * vnoise(w * 0.35));
        if (uJahrOn == 1) {
            // Phase 14: das Kronendach nach der Jahreszeit (Birke kahl oder gelb), Schnee in den Kronen
            float birke = smoothstep(0.30, 0.70, fbm(w * 0.05 + 11.0));
            vec3 kahl = mix(vec3(0.085, 0.072, 0.060), vec3(0.11, 0.095, 0.08), vnoise(w * 0.3));
            krone = mix(krone, mix(kahl, krone, uJahr.x), birke);
            krone = mix(krone, mix(vec3(0.27, 0.18, 0.035), vec3(0.33, 0.14, 0.04), vnoise(w * 0.2)), uJahr.y * uJahr.x * birke * 0.8);
            krone = mix(krone, vec3(0.70, 0.72, 0.76), clamp(uJahr.z * 0.6 + (1.0 - uJahr.x) * birke * snow * 0.5, 0.0, 0.8) * step(0.001, sdep));
        }
        waldDecke = smoothstep(0.04, 0.55, wd) * 0.94;
        alb = mix(alb, krone, waldDecke);
    }
    // unter dem Kronendach liegt der Schnee nur zum Teil frei (Phase 14)
    if (uJahrOn == 1) snow *= 1.0 - 0.75 * waldDecke;
    alb = mix(alb, vec3(0.80, 0.82, 0.86), snow);
    alb *= mix(0.55, 1.0, smoothstep(0.5, 4.0, y - uLevel));    // nasser Saum am Ufer
    float shade = wallLight(w) * cloudLight(w);
    vec3 direct = (sunLight() * max(dot(n, uSun), 0.0) + moonLight() * max(dot(n, uMoonDir), 0.0)) * shade;
    // mit com.dan.sky physikalisch: Leuchtdichte = Albedo/π · Bestrahlung, uSkyAmb ist schon Bestrahlung/π
    vec3 amb = uSkyMode == 1 ? uSkyAmb * (0.6 + 0.4 * n.y) : skyColor(vec3(0.0, 1.0, 0.0)) * 0.55 * (0.6 + 0.4 * n.y) / 3.14159265;
    vec3 lit = alb * (direct / 3.14159265 + amb);

    // ---------------------------------------------------------------- Grund unter dem Spiegel (Phase 3)
    float depth = uLevel - y;
    float under = smoothstep(-0.5, 0.5, depth);
    if (uWaterMode == 0 && under > 0.0) {
        // Schlick und Geröll; die Fläche von ocean liegt darüber, sichtbar bleibt der Grund nur im Flachen
        vec3 bed = mix(vec3(0.16, 0.15, 0.13), vec3(0.11, 0.11, 0.10), rn2);
        vec3 bl = bed * (sunLight() * shade * max(dot(n, uSun), 0.0) * exp(-0.35 * max(depth, 0.0)) / 3.14159265 + amb);
        lit = mix(lit, bl, under);
    }
    if (uWaterMode == 2 && under > 0.0) {
        // Grund zeigen: hell im Flachen, dunkelblau in der Rinne; Linien alle 50 m, Ufer, Mitte und Rinne hervorgehoben
        float k = clamp(depth / 260.0, 0.0, 1.0);
        vec3 c = mix(vec3(0.70, 0.86, 0.82), vec3(0.05, 0.16, 0.36), sqrt(k));
        float fw = max(fwidth(depth), 1e-3);
        float c50 = 1.0 - smoothstep(0.0, 1.2 * fw, abs(mod(depth + 25.0, 50.0) - 25.0) - 0.15 * fw);
        float key = 0.0;
        for (int i = 0; i < 3; i++) {
            float t = i == 0 ? 10.0 : i == 1 ? 150.0 : 250.0;
            key = max(key, 1.0 - smoothstep(0.0, 1.6 * fw, abs(depth - t) - 0.4 * fw));
        }
        vec3 lb = c * (sunLight() * shade * (0.25 + 0.75 * max(dot(n, uSun), 0.0)) / 3.14159265 + amb * 1.5);
        lb = mix(lb, lb * 0.45, c50 * smoothstep(1.0, 5.0, depth));
        lb = mix(lb, vec3(0.95, 0.78, 0.30) * (sunLight() / 3.14159265 + amb * 3.0) * 0.6, key);
        lit = mix(lit, lb, under);
    }
    float water = uWaterMode == 1 ? artShare(w, 1u) : 0.0;   // Phase 8: nur noch, wo es gebraucht wird
    if (water > 0.0) {
        // ocean fehlt: der Spiegel aus Phase 2 (terrain.vert hebt den Grund auf den Spiegel)
        vec3 r = reflect(-v, vec3(0.0, 1.0, 0.0));
        float cosT = max(v.y, 0.0);
        float fres = 0.02 + 0.98 * pow(1.0 - cosT, 5.0);
        vec3 body = vec3(0.012, 0.055, 0.052) * (sunLight() * shade * max(uSun.y, 0.0) / 3.14159265 + amb);
        vec3 wall = vec3(0.10, 0.12, 0.08) * (sunLight() * 0.35 / 3.14159265 + amb);
        vec3 refl = mix(wall, skyColor(vec3(r.x, max(r.y, 0.02), r.z)), smoothstep(0.12, 0.45, r.y));
        vec3 wcol = mix(body, refl, fres);
        lit = mix(lit, wcol, water * smoothstep(-0.05, 0.05, uLevel + 0.01 - y));
    }

    // ---------------------------------------------------------------- Marken
    if (uMarks == 1) {
        float lw = max(1.5, dist * 0.0012);
        vec3 gelb = vec3(0.89, 0.72, 0.24) * 0.35;
        lit = mix(lit, gelb, line(rectDist(w, uKernWest), lw) * 0.9);
        lit = mix(lit, gelb, line(rectDist(w, uKernOst), lw) * 0.9);
        for (int i = 0; i < uPointCount; i++) {
            vec4 o = uPoints[i];
            float dd = length(w - o.xy) - o.z;
            vec3 c = (o.w < 0.5 ? vec3(0.92, 0.95, 0.96) : o.w < 1.5 ? vec3(0.45, 0.85, 0.85) : vec3(0.64, 0.75, 0.33)) * 0.35;
            lit = mix(lit, c, 1.0 - smoothstep(-lw, lw, dd));
        }
    }
    frag = vec4(aerial(lit, -v, dist), 1.0);
}
