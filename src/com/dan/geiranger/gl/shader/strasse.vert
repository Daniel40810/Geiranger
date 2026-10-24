// strasse.vert — Phase 9: die Dreiecke von com.dan.road (Fahrbahn, Ausstattung, Fahrzeuge, Lichter, Staub) in Weltlage.
#include "common.glsl"
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aColor;
layout(location = 3) in vec3 aExtra;   // Glanz, Mischung (fade), Art (0 fest, 1 dünn, 2 Lampe, 3 Lichthof)
out vec3 vWorld;
out vec3 vNormal;
out vec3 vColor;
out vec3 vExtra;
void main() {
    vWorld = aPos;
    vNormal = aNormal;
    vColor = aColor;
    vExtra = aExtra;
    vec2 dh = aPos.xz - uCam.xz;
    gl_Position = uVP * vec4(aPos.x, aPos.y - dot(dh, dh) / (2.0 * EARTH_R), aPos.z, 1.0);
}
