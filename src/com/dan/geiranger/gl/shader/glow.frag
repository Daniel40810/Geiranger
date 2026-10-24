// glow.frag — Phase 11: Lichthof einer Laterne, zum Bild addiert (nachts, im Dunst schwächer).
#include "common.glsl"
in float vFern;
out vec4 frag;
uniform float uLamp;
void main() {
    vec2 p = gl_PointCoord * 2.0 - 1.0;
    float r2 = dot(p, p);
    if (r2 > 1.0) discard;
    float nacht = 1.0 - smoothstep(-0.02, 0.06, uSun.y);
    float g = 0.05 * exp(-r2 * 4.0) + 0.25 * exp(-r2 * 30.0);
    frag = vec4(vec3(1.0, 0.80, 0.55) * uLamp * g * nacht * vFern * (1.0 - smoothstep(0.92, 1.0, r2)), 1.0);
}
