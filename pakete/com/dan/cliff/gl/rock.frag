// rock.frag — com.dan.cliff: Fels mit dem Material aus cliff.glsl, Licht von Sonne (mit Anteil uShade) und Himmel,
// Dunst zur Ferne. Ausgabe HDR linear, schon vorbelichtet wie das Licht, das hereinkommt.
#include "cliff.glsl"
in vec3 vWorld;
in vec3 vNormal;
out vec4 frag;
uniform vec3 uEye;
uniform float uPixelAngle;
uniform vec3 uSunDir, uSunColor, uAmbient, uFogColor;
uniform float uFogDist, uShade, uRainWet, uSnow;
uniform float uDisplay;   // > 0: Belichtung, Filmkurve und Gamma gleich hier (Vorführung ohne Nachbearbeitung)
void main() {
    vec3 n = normalize(vNormal);
    float d = distance(vWorld, uEye);
    CliffSample s = cliffMaterial(vWorld, n, uRainWet, uSnow, d * uPixelAngle);
    vec3 nn = s.normal;
    float lam = max(dot(nn, uSunDir), 0.0);
    vec3 col = s.albedo * (uSunColor * lam * uShade / 3.14159265 + uAmbient * (0.6 + 0.4 * nn.y));
    // nasser Fels glänzt ein wenig
    vec3 v = normalize(uEye - vWorld);
    vec3 h = normalize(uSunDir + v);
    col += uSunColor * uShade * s.wet * 0.04 * pow(max(dot(nn, h), 0.0), 60.0);
    col = mix(col, uFogColor, 1.0 - exp(-d / max(uFogDist, 1.0)));
    if (uDisplay > 0.0) col = pow(1.0 - exp(-col * uDisplay), vec3(1.0 / 2.2));
    frag = vec4(col, 1.0);
}
