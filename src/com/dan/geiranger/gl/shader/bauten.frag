// bauten.frag — Phase 8: Treppe, Geländer und Steine, Licht wie das Gelände (Sonne mit Schatten der Wände und Wolken,
// Himmelslicht, Dunst); Stein mit etwas Rauschen, damit die Stufen nicht wie Plastik aussehen.
// Phase 11: die Häuser des Orts. Die Farbe trägt Zeichen (Dorf): Rot über 1,5 = senkrechte Verschalung (Farbe − 2);
// Rot unter −0,5 = Fenster (Grün = Nummer + u, Blau = v): weißer Rahmen mit Sprossen, Glas spiegelt den Himmel, nachts
// ist gut die Hälfte erleuchtet (warm, Vorhänge); Rot unter −1,5 = Laterne (nachts an, uLamp wie die Lampen der Straße).
// Phase 12: auch die Schiffe (schiff.vert mit Modellmatrix); ihre Positionslichter tragen die Farbe im Zeichen.
#include "common.glsl"
#include "terrain_height.glsl"
#include "licht.glsl"
in vec3 vWorld;
in vec3 vNormal;
in vec3 vColor;
out vec4 frag;
uniform int uClip;
uniform float uLamp;        // Phase 11: Leuchtdichte einer Lampe (vorbelichtet); 0 = keine Lichter

float hash11(float n) { return fract(sin(n * 127.1 + 3.7) * 43758.5453); }

// Phase 11: nachts brennen die Lichter (Sonne unter rund 3°; Mittsommer um Mitternacht −4,5°)
float nacht() { return 1.0 - smoothstep(-0.02, 0.06, uSun.y); }

void main() {
    if (uClip == 1 && vWorld.y < uLevel) discard;
    vec3 n = normalize(vNormal);
    vec3 V = normalize(uCam - vWorld);
    vec3 c = vColor;
    vec3 emit = vec3(0.0);
    float k = 0.88 + 0.24 * lNoise(vWorld.xz * 3.1 + vWorld.yy * 2.3);
    float spec = 0.0;
    if (c.r < -1.5) {
        // Laterne: tags graues Glas, nachts warmes Licht; seit Phase 12 mit eigener Farbe (Positionslichter der
        // Schiffe): Farbe = (−Rot − 2, Grün, Blau), ohne Grün und Blau das warme Licht der Laternen
        vec3 lc = c.g + c.b > 0.001 ? vec3(-c.r - 2.0, c.g, c.b) : vec3(1.0, 0.80, 0.55);
        c = vec3(0.35, 0.35, 0.33) * mix(vec3(1.0), lc, 0.4);
        emit = lc * uLamp * 1.6 * nacht();
        k = 1.0;
    } else if (c.r < -0.5) {
        float id = floor(c.g);
        vec2 uv = vec2(c.g - id, c.b);
        bool rahmen = uv.x < 0.08 || uv.x > 0.92 || uv.y < 0.07 || uv.y > 0.93 || abs(uv.x - 0.5) < 0.025 || abs(uv.y - 0.62) < 0.025;
        if (id > 998.0) rahmen = uv.x < 0.04 || uv.x > 0.96 || uv.y > 0.96 || abs(uv.x - 0.5) < 0.02;   // Ladentür aus Glas
        if (rahmen) { c = vec3(0.64, 0.63, 0.60); k = 1.0; }
        else {
            // Glas: innen dunkel, spiegelt den Himmel (Fresnel), nachts erleuchtet
            c = vec3(0.012, 0.013, 0.015);
            k = 1.0;
            float fres = 0.04 + 0.5 * pow(1.0 - max(dot(n, V), 0.0), 5.0);
            vec3 r = reflect(-V, n);
            r.y = abs(r.y);
            emit += skyColor(r) * fres;
            float an = step(0.45, hash11(id * 1.7 + 0.3));
            float vorhang = 0.55 + 0.45 * hash11(id * 3.1);
            vec3 warm = mix(vec3(1.0, 0.70, 0.40), vec3(1.0, 0.85, 0.65), hash11(id * 5.3));
            emit += warm * uLamp * 0.11 * vorhang * an * nacht() * (0.75 + 0.25 * smoothstep(0.0, 0.6, uv.y));
        }
    } else if (c.r > 1.5) {
        // senkrechte Verschalung: Bretter zu 18 cm mit Fugen und Deckleisten, unten etwas dunkler (Spritzwasser)
        c -= 2.0;
        vec2 tn = normalize(vec2(-n.z, n.x) + 1e-5);
        float t = dot(vWorld.xz, tn);
        float b = fract(t / 0.18);
        float fuge = mix(0.72, 1.0, smoothstep(0.0, 0.06, b) * (1.0 - smoothstep(0.86, 0.92, b)));
        float leiste = 1.0 + 0.06 * (smoothstep(0.88, 0.92, b) - smoothstep(0.97, 1.0, b));
        float brett = 0.94 + 0.12 * hash11(floor(t / 0.18));
        if (abs(n.y) < 0.5) c *= mix(1.0, fuge * leiste * brett, 1.0 - smoothstep(30.0, 120.0, distance(uCam, vWorld)));
        k = 0.95 + 0.1 * lNoise(vWorld.xz * 0.7 + vWorld.yy * 0.4);
    }
    vec3 alb = c * k;
    float shade = wallLight(vWorld.xz) * cloudLight(vWorld.xz);
    vec3 amb = uSkyMode == 1 ? uSkyAmb * (0.6 + 0.4 * n.y) : skyColor(vec3(0.0, 1.0, 0.0)) * 0.55 * (0.6 + 0.4 * n.y) / 3.14159265;
    vec3 lit = alb * ((sunLight() * max(dot(n, uSun), 0.0) + moonLight() * max(dot(n, uMoonDir), 0.0)) * shade / 3.14159265 + amb) + emit;
    frag = vec4(aerial(lit, -V, distance(uCam, vWorld)), 1.0);
}
