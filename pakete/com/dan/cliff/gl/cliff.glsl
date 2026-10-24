// cliff.glsl — com.dan.cliff: Fels als Material und Feinform, Zeile für Zeile wie Cliff.java und CNoise.java.
// Einbinden über eine Include-Zeile mit dem absoluten Pfad /com/dan/cliff/gl/cliff.glsl im Klassenpfad; die Uniforms setzt
// CliffGl.bind. Alles ist 3D-Rauschen am Ort (keine Projektion auf Ebenen): senkrechte Wände ziehen keine Streifen.
//   cliffRelief(p, footprint)                 Relief in m um 0 (Feinform), für den Scheitelshader und die Normale
//   cliffDisplace(p, n, footprint, range)     verschobener Ort: entlang der Normale um das Relief, nur an der Wand
//   cliffMaterial(p, n, rainWet, snow, fp)    Farbe, Normale, Anteil Fels, Nässe, Schnee (nur im Bildpunkt: dFdx)
// Das Rauschen kommt aus der 3D-Textur uCliffNoise (64³, CNoise.latticeTexture), die CliffGl.bind auf Einheit 15 legt.
uniform int uCliffSeed;
uniform vec4 uCliffSlope;      // Fels ab (1 − cos), Wand von, Wand bis (1 − cos), Oktaven des Reliefs
uniform vec3 uCliffFol;        // Normale der Schieferung
uniform vec4 uCliffJoint;      // Winkel der Scharen A und B (rad, schon + 90°), Abstand, Breite (m)
uniform vec2 uCliffRelief;     // Höhe (m), größter Maßstab (m)
uniform vec3 uCliffLight, uCliffDark, uCliffFeld, uCliffLichen, uCliffMoss, uCliffTalus, uCliffSnow;
uniform vec4 uCliffAmount;     // Flechten, Moos, Sickerstreifen, Feldspat
uniform float uCliffFallWet;   // Stärke der nassen Streifen unter den Fällen 0..1 (Cliff.fallWet, nach dem Abfluss)
uniform sampler2D uCliffMap;   // Wand, Geröll, Nässe, Absatz (CliffMap)
uniform vec4 uCliffMapRect;    // x0, z0, Kantenlänge (m), an

uint cliffHash(int x, int y, int z) {
    uvec3 v = uvec3(uint(x), uint(y), uint(z)) * 1664525u + 1013904223u;
    v.x += v.y * v.z; v.y += v.z * v.x; v.z += v.x * v.y;
    v ^= v >> 16u;
    v.x += v.y * v.z; v.y += v.z * v.x; v.z += v.x * v.y;
    return v.x ^ v.y ^ v.z;
}

float cliffUnit(uint h) { return float(h >> 8u) * (1.0 / 16777216.0); }
float cliffFade(float t) { return t * t * t * (t * (t * 6.0 - 15.0) + 10.0); }

uniform sampler3D uCliffNoise;  // Gitterwerte 64³ (R16, wiederholt), CNoise.latticeTexture

// Wertrauschen: ein Abruf an der geglätteten Stelle; die Grafikkarte mischt trilinear mit den quintischen Gewichten
float cliffValue(vec3 p, int seed) {
    p += vec3(float((seed * 37) & 63), float((seed * 59) & 63), float((seed * 1013) & 63));
    p -= 64.0 * floor(p / 64.0);
    vec3 i = floor(p), t = p - i;
    vec3 f = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    return textureLod(uCliffNoise, (i + f + 0.5) / 64.0, 0.0).r;
}

// Seit Geiranger Nacharbeit 11: dieselbe Mischung genau gerechnet (acht Gitterwerte). Die Grafikkarte mischt trilinear
// nur mit 8 Bit Gewicht (256 Stufen je Zelle); ganz nah, wo eine Zelle viele Bildpunkte deckt, zeichneten diese Stufen
// über die Ableitungen der Normale ein Raster aus Strichen in die Wand (Status_Phase11a). cliffRelief nimmt sie dort, wo
// eine Stufe breiter ist als ein Bildpunkt.
float cliffValueExact(vec3 p, int seed) {
    p += vec3(float((seed * 37) & 63), float((seed * 59) & 63), float((seed * 1013) & 63));
    p -= 64.0 * floor(p / 64.0);
    vec3 i = floor(p), t = p - i;
    vec3 f = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    ivec3 a = ivec3(i) & 63, b = (a + 1) & 63;
    float c000 = texelFetch(uCliffNoise, ivec3(a.x, a.y, a.z), 0).r, c100 = texelFetch(uCliffNoise, ivec3(b.x, a.y, a.z), 0).r;
    float c010 = texelFetch(uCliffNoise, ivec3(a.x, b.y, a.z), 0).r, c110 = texelFetch(uCliffNoise, ivec3(b.x, b.y, a.z), 0).r;
    float c001 = texelFetch(uCliffNoise, ivec3(a.x, a.y, b.z), 0).r, c101 = texelFetch(uCliffNoise, ivec3(b.x, a.y, b.z), 0).r;
    float c011 = texelFetch(uCliffNoise, ivec3(a.x, b.y, b.z), 0).r, c111 = texelFetch(uCliffNoise, ivec3(b.x, b.y, b.z), 0).r;
    return mix(mix(mix(c000, c100, f.x), mix(c010, c110, f.x), f.y), mix(mix(c001, c101, f.x), mix(c011, c111, f.x), f.y), f.z);
}

float cliffValue1(float x, int seed) {
    float f = floor(x);
    int ix = int(f);
    return mix(cliffUnit(cliffHash(ix, seed, 7)), cliffUnit(cliffHash(ix + 1, seed, 7)), cliffFade(x - f));
}

float cliffBand(float lambda, float footprint) { return 1.0 - smoothstep(0.5 * lambda, 1.5 * lambda, 2.0 * footprint); }

// eine feste Drehung gegen die Kästchen des Gitters (wie Cliff.ROT, Zeilen von ROT als Spalten: q = p * CLIFF_ROT = ROT · p)
const mat3 CLIFF_ROT = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);

float cliffRelief(vec3 p, float footprint) {
    float s = uCliffRelief.y, a = uCliffRelief.x, r = 0.0;
    for (int o = 0; o < 4; o++) {
        if (float(o) >= uCliffSlope.w) break;
        p = p * CLIFF_ROT;
        float w = cliffBand(s, footprint);
        if (w <= 0.0) break;   // die feineren Oktaven sind noch kleiner (gleiches Ergebnis, weniger Abrufe in der Ferne)
        // nah (die 256 Stufen einer Zelle liegen weiter als ein Bildpunkt auseinander) genau gemischt, sonst ein Abruf
        float n = footprint * 256.0 < s ? cliffValueExact(p / s, uCliffSeed + 11 + o) : cliffValue(p / s, uCliffSeed + 11 + o);
        r += a * w * (0.5 - abs(2.0 * n - 1.0));
        s /= 3.0;
        a /= 2.6;
    }
    return r;
}

// Wandkarte am Ort; ohne Karte aus der Neigung (Wand), sonst 0
vec4 cliffMapAt(vec2 xz, float slope) {
    if (uCliffMapRect.w > 0.0) {
        vec2 uv = (xz - uCliffMapRect.xy) / uCliffMapRect.z;
        if (all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)))) return textureLod(uCliffMap, uv, 0.0);
    }
    return vec4(smoothstep(uCliffSlope.y, uCliffSlope.z, slope), 0.0, 0.0, 0.0);
}

// Feinform: der Ort, entlang der Normale um das Relief verschoben, nur an der Wand und bis range m vom Auge
vec3 cliffDisplace(vec3 p, vec3 n, float footprint, float dist, float range) {
    float wall = cliffMapAt(p.xz, 1.0 - n.y).x;
    // in der Ferne aus, und dicht vor dem Auge auch (sonst schöbe sich die Wand über ein Auge, das 2 m über dem Höhenfeld steht)
    float fade = (1.0 - smoothstep(range * 0.7, range, dist)) * smoothstep(4.0, 40.0, dist);
    if (wall * fade <= 0.0) return p;
    return p + n * cliffRelief(p, footprint) * wall * fade;
}

// Material nur im Bildpunkt (dFdx); Scheitelshader setzen vor der Include-Zeile #define CLIFF_VERTEX
#ifndef CLIFF_VERTEX
struct CliffSample { vec3 albedo; vec3 normal; float rock; float wet; float snow; };

CliffSample cliffMaterial(vec3 p, vec3 n, float rainWet, float snowAmt, float footprint) {
    CliffSample s;
    float slope = 1.0 - n.y;
    vec4 m = cliffMapAt(p.xz, slope);
    float wall = m.x, tal = m.y, wetMap = m.z, ledge = m.w;
    s.rock = max(max(smoothstep(uCliffSlope.x, uCliffSlope.x + 0.12, slope), wall), tal * 0.45);
    int sd = uCliffSeed;

    // Normale aus dem Relief über die Ableitungen im Bild (Oberflächengradient): ein Reliefwert statt vier; die
    // Ableitungen stehen außerhalb jeder Verzweigung, ohne Fels ist das Relief 0
    float r0 = s.rock > 0.003 ? cliffRelief(p, footprint) : 0.0;
    vec3 dpx = dFdx(p), dpy = dFdy(p);
    float drx = dFdx(r0), dry = dFdy(r0);
    vec3 r1 = cross(dpy, n), r2 = cross(n, dpx);
    float det = dot(dpx, r1);
    vec3 sg = sign(det) * (drx * r1 + dry * r2);
    s.normal = abs(det) > 1e-8 ? normalize(abs(det) * n - sg * s.rock) : n;
    s.wet = 0.0;
    s.snow = 0.0;
    s.albedo = uCliffTalus;
    if (s.rock <= 0.003 && snowAmt <= 0.0) return s;

    float b6 = cliffBand(6.0, footprint);
    float warp = (cliffValue(p / 40.0, sd + 1) - 0.5) * 14.0 + (b6 > 0.0 ? (cliffValue(p / 6.0, sd + 2) - 0.5) * 1.6 * b6 : 0.0);
    float f = dot(p, uCliffFol) + warp;
    float b = 0.45 * cliffValue1(f / 33.0, sd + 3) + 0.35 * cliffValue1(f / 7.0, sd + 4)
            + 0.20 * (cliffValue1(f / 1.6, sd + 5) - 0.5) * cliffBand(1.6, footprint) + 0.1;
    b = smoothstep(0.25, 0.75, b);
    vec3 col = mix(uCliffDark, uCliffLight, b);
    float fel = smoothstep(0.78, 0.9, cliffValue1(f / 4.5, sd + 6)) * uCliffAmount.w;
    col = mix(col, uCliffFeld, fel);
    vec3 q = p * CLIFF_ROT;
    float b07 = cliffBand(0.7, footprint);
    if (b07 > 0.0) col *= 1.0 + (cliffValue(q / 0.7, sd + 7) - 0.5) * 0.18 * b07;

    float bj = cliffBand(uCliffJoint.w * 4.0, footprint) * wall;
    if (bj > 0.0) {
        float joint = 0.0;
        for (int k = 0; k < 2; k++) {
            float a = k == 0 ? uCliffJoint.x : uCliffJoint.y;
            float u = (p.x * sin(a) - p.z * cos(a)) / uCliffJoint.z + (cliffValue(p / 25.0, sd + 8 + k) - 0.5) * 0.8;
            float toJoint = (0.5 - abs(u - floor(u) - 0.5)) * uCliffJoint.z;
            float open = smoothstep(0.35, 0.65, cliffValue(p / vec3(12.0, 30.0, 12.0), sd + 20 + k));
            joint = max(joint, (1.0 - smoothstep(uCliffJoint.w * 0.5, uCliffJoint.w * 1.5, toJoint)) * open);
        }
        col *= 1.0 - 0.55 * joint * bj;
    }

    float seep = wall > 0.0 ? smoothstep(0.66, 0.78, cliffValue(p / vec3(3.5, 70.0, 3.5), sd + 9)) * uCliffAmount.z * wall : 0.0;
    s.wet = clamp(max(max(wetMap * uCliffFallWet, seep), rainWet * s.rock), 0.0, 1.0);
    float li = smoothstep(0.5, 0.72, cliffValue(q / 2.2, sd + 10)) * uCliffAmount.x * (1.0 - s.wet) * s.rock;
    col = mix(col, uCliffLichen, li * 0.7);
    float mw = clamp((ledge * 0.9 + s.wet * 0.45) * uCliffAmount.y * 2.0, 0.0, 1.0);
    if (mw > 0.0) col = mix(col, uCliffMoss, mw * smoothstep(0.4, 0.62, cliffValue(q / 3.5, sd + 12)));
    float tw = tal * (1.0 - wall);
    if (tw > 0.0) {
        float blk = 0.5 + (smoothstep(0.3, 0.7, cliffValue(q / 1.3, sd + 13)) - 0.5) * cliffBand(1.3, footprint);
        col = mix(col, uCliffTalus * (0.75 + 0.5 * blk), tw);
    }
    col *= 1.0 - 0.45 * s.wet;
    float flatv = max(ledge, 1.0 - smoothstep(0.05, 0.15, slope));
    if (snowAmt > 0.0) s.snow = clamp(snowAmt * flatv * smoothstep(0.3, 0.5, cliffValue(p / 5.0, sd + 14) + snowAmt * 0.3), 0.0, 1.0);
    col = mix(col, uCliffSnow, s.snow);
    s.albedo = col;
    return s;
}
#endif
