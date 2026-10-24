// glow.vert — Phase 11: Lichthof einer Laterne als Punkt, Größe nach Abstand (Halbmesser rund 2,5 m).
#include "common.glsl"
layout(location = 0) in vec3 aPos;
uniform float uPx;          // Bildhöhe in Pixel / (2 · tan(fovy / 2))
out float vFern;
void main() {
    vec2 dh = aPos.xz - uCam.xz;
    gl_Position = uVP * vec4(aPos.x, aPos.y - dot(dh, dh) / (2.0 * EARTH_R), aPos.z, 1.0);
    float d = max(distance(uCam, aPos), 1.0);
    gl_PointSize = clamp(2.0 * 2.5 * uPx / d, 3.0, 160.0);
    vFern = 1.0 - smoothstep(1500.0, 6000.0, d);
}
