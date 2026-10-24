// bauten.vert — Phase 8: Treppe, Geländer und Steine an der Elva (Lage, Normale, Farbe je Ecke).
#include "common.glsl"
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aColor;
out vec3 vWorld;
out vec3 vNormal;
out vec3 vColor;
void main() {
    vWorld = aPos;
    vNormal = aNormal;
    vColor = aColor;
    vec2 dh = aPos.xz - uCam.xz;
    gl_Position = uVP * vec4(aPos.x, aPos.y - dot(dh, dh) / (2.0 * EARTH_R), aPos.z, 1.0);
}
