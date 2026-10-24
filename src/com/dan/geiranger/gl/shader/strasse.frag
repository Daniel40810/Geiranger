// strasse.frag — Phase 9: Licht wie das Gelände (Sonne mit Schatten der Wände und Wolken, Himmelslicht, Dunst); Glanz
// spiegelt den Himmel (nasse Fahrbahn, Lack); Lampen leuchten selbst (uLamp: Leuchtdichte einer Lampe im vorbelichteten
// Bild), Lichthöfe werden addiert. uPass 0: fest, 1: gemischt (fade), 2: Lichthöfe.
#include "common.glsl"
#include "terrain_height.glsl"
#include "licht.glsl"
in vec3 vWorld;
in vec3 vNormal;
in vec3 vColor;
in vec3 vExtra;
out vec4 frag;
uniform int uPass;
uniform float uLamp;
void main() {
    float kind = vExtra.z, fade = clamp(vExtra.y, 0.0, 1.0), gloss = clamp(vExtra.x, 0.0, 1.0);
    vec3 V = normalize(uCam - vWorld);
    float dist = distance(uCam, vWorld);
    if (kind > 2.5) {                                   // Lichthof: addiert, im Dunst schwächer
        vec3 g = vColor * uLamp * (1.0 - fade);
        frag = vec4(g * exp(-dist / 4000.0), 0.0);
        return;
    }
    vec3 lit;
    if (kind > 1.5) lit = vColor * uLamp;               // Lampe
    else {
        vec3 n = normalize(vNormal);
        if (kind > 0.5 && dot(n, V) < 0.0) n = -n;      // dünn: von beiden Seiten
        float shade = wallLight(vWorld.xz) * cloudLight(vWorld.xz);
        vec3 amb = uSkyMode == 1 ? uSkyAmb * (0.6 + 0.4 * n.y) : skyColor(vec3(0.0, 1.0, 0.0)) * 0.55 * (0.6 + 0.4 * n.y) / 3.14159265;
        vec3 sunL = (sunLight() * max(dot(n, uSun), 0.0) + moonLight() * max(dot(n, uMoonDir), 0.0)) * shade;
        lit = vColor * (sunL / 3.14159265 + amb);
        if (gloss > 0.01) {
            vec3 r = reflect(-V, n);
            float fres = 0.04 + 0.96 * pow(1.0 - max(dot(n, V), 0.0), 5.0);
            vec3 sky = skyColor(vec3(r.x, max(r.y, 0.02), r.z));
            vec3 h = normalize(uSun + V);
            float spec = pow(max(dot(n, h), 0.0), 20.0 + 200.0 * gloss) * (8.0 + 40.0 * gloss);
            lit = mix(lit, sky, fres * gloss) + sunLight() * shade * spec * gloss * fres / 3.14159265;
        }
    }
    vec3 col = aerial(lit, -V, dist);
    float a = uPass == 1 ? 1.0 - fade : 1.0;
    frag = vec4(col * a, a);
}
