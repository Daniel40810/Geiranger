// schatten.frag — Phase 4 (F19 A): die Schatten der Wände je Sonnenstand, Zeile für Zeile wie scene.Wandschatten.
// Je Texel einer Karte über dem Hauptraster oder dem Fernring (uShadowRect: Ecke x, z, Kantenlänge, Texelgröße) läuft
// ein Strahl in der Fläche zur Lichtquelle (uKey) über das Gelände, Schritte wachsend bis 30 km, mit Erdkrümmung; der
// steilste Winkel ist der Horizont. Ausgabe: Anteil direkten Lichts 0..1, weich um den Sonnenrand. Über dem Fjord
// beginnt der Strahl auf dem Spiegel (der Grund darunter bekommt das Licht des Wassers).
#include "common.glsl"
#include "terrain_height.glsl"
out vec4 frag;
uniform vec4 uShadowRect;
uniform vec3 uKey;
const float SOFT = 0.012;

void main() {
    vec2 w = uShadowRect.xy + gl_FragCoord.xy * uShadowRect.w;
    float hl = length(uKey.xz);
    float alt = atan(uKey.y, hl);
    if (alt < -SOFT) { frag = vec4(0.0); return; }
    if (hl < 1e-6) { frag = vec4(1.0); return; }
    float h0 = heightAt(w, 0.0);
    float y = (artAt(w) == 1u ? max(h0, uLevel) : h0) + 2.0 + 0.25 * uShadowRect.w;
    vec2 d = uKey.xz / hl;
    float tanAlt = tan(max(alt, -0.2));
    float maxTan = -1e9, stp = 0.5 * uShadowRect.w, s = stp;
    for (int i = 0; i < 200; i++) {
        if (s > 30000.0) break;
        float lvl = clamp(log2(max(stp, 1.0) / uGrid.z) - 1.0, 0.0, 8.0);
        float g = heightAt(w + d * s, lvl) - s * s / (2.0 * EARTH_R);
        maxTan = max(maxTan, (g - y) / s);
        if (maxTan > tanAlt + 0.05 || y + s * tanAlt > 2000.0) break;
        stp *= 1.07;
        s += stp;
    }
    frag = vec4(smoothstep(-SOFT, SOFT, alt - atan(maxTan)));
}
