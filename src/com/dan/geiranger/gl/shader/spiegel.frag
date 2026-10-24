// spiegel.frag — Phase 3: das Rundumbild, das com.dan.ocean als Himmel liest (Azimut von Nord über die Breite, Höhe
// −90°…+90° über die Höhe, wie sky() in surface.frag). Wo die gespiegelte Ansicht (uMirror, vom Auge unter dem Spiegel
// gezeichnet, alles unter dem Wasser weggeschnitten) eine Richtung zeigt, steht sie hier, sonst der Himmel des
// Platzhalters. So spiegeln sich im Fjord die Wände und nicht der Horizont. Für eine ebene Fläche ist das genau: der
// Strahl vom Auge über den Wasserpunkt P in Richtung r läuft durch das gespiegelte Auge; die Wellen verbiegen r, und
// das Bild verbiegt sich mit. Nur die obere Hälfte und ein Saum unter der Kimm werden gerechnet (das Wasser spiegelt
// nur nach oben, der Dunst liest die Kimm).
#include "common.glsl"
out vec4 frag;
uniform sampler2D uMirror;
uniform mat4 uMirrorVP;     // Projektion mal Blick der gespiegelten Ansicht
uniform int uMirrorOn;
uniform vec2 uPanoSize;

void main() {
    vec2 uv = gl_FragCoord.xy / uPanoSize;
    float az = (uv.x - 0.5) * 6.28318530718;
    float el = (uv.y - 0.5) * 3.14159265359;
    vec3 d = vec3(sin(az) * cos(el), sin(el), -cos(az) * cos(el));
    vec3 c = skyColor(vec3(d.x, max(d.y, 0.0), d.z));
    if (uMirrorOn == 1) {
        // Richtung als Punkt im Unendlichen: die Lage des gespiegelten Auges fällt heraus
        vec4 cl = uMirrorVP * vec4(d, 0.0);
        if (cl.w > 1e-5) {
            vec2 ndc = cl.xy / cl.w;
            float m = max(abs(ndc.x), abs(ndc.y));
            if (m < 1.0) {
                vec3 img = textureLod(uMirror, ndc * 0.5 + 0.5, 0.0).rgb;
                c = mix(c, img, 1.0 - smoothstep(0.93, 1.0, m));
            }
        }
    }
    frag = vec4(c, 1.0);
}
