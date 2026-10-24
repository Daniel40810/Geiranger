// terrain_height.glsl — Höhe des Geländes für Vertex- und Fragment-Shader (Phase 2, aus Rungholt).
// Hauptraster: R16 (normiert, 2048² zu 12 m, mit Mip-Stufen), Fernring: R16 (512² zu 120 m). Höhe über NN = Wert · uHMap.x
// + uHMap.y, bilinear wie Terrain.height in Java, damit Kamera und Bild dieselbe Fläche sehen. Auf den letzten uNaht m
// innerhalb des Hauptrasters blendet die Höhe weich in den Fernring über, außerhalb gilt der Fernring allein. In der
// Ferne kommen gröbere Mip-Stufen (stetig nach der Entfernung, damit Nachbarknoten dieselbe Fläche sehen).
uniform sampler2D uHeight;
uniform sampler2D uFern;
uniform usampler2D uSource;    // Art je Zelle: 0 Land, 1 Fjord
uniform vec3 uGrid;            // X0, Z0 (Nordwestecke), Zellgröße des Hauptrasters
uniform float uGridN;          // Zellen je Seite
uniform vec3 uFernGrid;        // dasselbe für den Fernring
uniform float uFernN;
uniform float uNaht;           // Breite des Überblendbands (m)
uniform vec2 uHMap;            // Spanne, kleinste Höhe
uniform vec3 uLodRange;
uniform sampler2D uFein;       // Phase 9: Feinraster (R32F, 2 m): Abweichung von Haupt- und Fernraster (Straße), Einheit 6
uniform vec4 uFeinRect;        // Ecke x, z, Ausdehnung x, z (m); z = 0: kein Feinraster        // Sichtweite der feinsten Stufe (m), Beginn des Gleitens (Anteil), Sichtweite in Knotengrößen

vec2 gridUV(vec2 w) { return (w - uGrid.xy) / (uGrid.z * uGridN); }
vec2 fernUV(vec2 w) { return (w - uFernGrid.xy) / (uFernGrid.z * uFernN); }

// Mip-Stufe nach der Entfernung: ein Texel etwa so groß wie der Punktabstand dort
float heightLevel(float dist) { return clamp(log2(max(dist, 1.0) / (64.0 * uLodRange.z * uGrid.z)), 0.0, 10.0); }

// Abstand zum Rand des Hauptrasters (m, positiv innen)
float insideDist(vec2 w) {
    vec2 lo = uGrid.xy, hi = uGrid.xy + uGrid.z * uGridN;
    vec2 d = min(w - lo, hi - w);
    return min(d.x, d.y);
}

// Phase 9: Abweichung aus dem Feinraster (bilinear wie Terrain.feinAt), außerhalb 0
float feinAt(vec2 w) {
    if (uFeinRect.z <= 0.0) return 0.0;
    vec2 uv = (w - uFeinRect.xy) / uFeinRect.zw;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return 0.0;
    return textureLod(uFein, uv, 0.0).r;
}

float heightAt(vec2 w, float lvl) {
    float f = 1.0 - smoothstep(0.0, uNaht, insideDist(w));
    float hm = f < 1.0 ? textureLod(uHeight, gridUV(w), lvl).r : 0.0;
    // der Fernring ist zehnmal gröber: dieselbe Entfernung braucht dort eine um log2(10) kleinere Stufe
    float hf = f > 0.0 ? textureLod(uFern, fernUV(w), max(0.0, lvl - 3.32)).r : 0.0;
    return mix(hm, hf, f) * uHMap.x + uHMap.y + feinAt(w);
}

float terrainHeight(vec2 w, float dist) { return heightAt(w, heightLevel(dist)); }

uint artAt(vec2 w) {
    if (insideDist(w) <= 0.0) return 0u;
    ivec2 c = clamp(ivec2(floor((w - uGrid.xy) / uGrid.z)), ivec2(0), ivec2(int(uGridN) - 1));
    return texelFetch(uSource, c, 0).r;
}

// Art weich: Anteil der Nachbarzellen (2 × 2, bilinear) mit dieser Art – keine Treppen am Ufer
float artShare(vec2 w, uint a) {
    if (insideDist(w) <= 0.0) return a == 0u ? 1.0 : 0.0;
    vec2 u = (w - uGrid.xy) / uGrid.z - 0.5;
    ivec2 i = ivec2(floor(u));
    vec2 f = u - vec2(i);
    float s = 0.0;
    for (int b = 0; b <= 1; b++)
        for (int c = 0; c <= 1; c++) {
            ivec2 q = clamp(i + ivec2(c, b), ivec2(0), ivec2(int(uGridN) - 1));
            float wt = (c == 0 ? 1.0 - f.x : f.x) * (b == 0 ? 1.0 - f.y : f.y);
            s += wt * (texelFetch(uSource, q, 0).r == a ? 1.0 : 0.0);
        }
    return s;
}
