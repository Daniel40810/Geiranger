// elva.vert — Phase 8: die Oberfläche der Geirangelva als Band über ihre Breite. Je Ecke vom Prozessor
// (com.dan.river): Lage auf dem Spiegel, Strömung (x, z), Unruhe und Schaum, Tiefe, nass (0 am Ufer).
#include "common.glsl"
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec4 aFlow;   // vx, vz, Schaum/Unruhe 0..1, Tiefe (m)
layout(location = 2) in float aWet;
out vec3 vWorld;
out vec4 vFlow;
out float vWet;
void main() {
    vWorld = aPos;
    vFlow = aFlow;
    vWet = aWet;
    vec2 dh = aPos.xz - uCam.xz;
    gl_Position = uVP * vec4(aPos.x, aPos.y - dot(dh, dh) / (2.0 * EARTH_R), aPos.z, 1.0);
}
