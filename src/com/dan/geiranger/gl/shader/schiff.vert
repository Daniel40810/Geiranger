// schiff.vert — Phase 12: die Schiffe; Netz im Format der Bauten in Schiffskoordinaten (x Steuerbord, y oben,
// z achtern), Lage, Kurs, Tauchen, Stampfen und Rollen aus com.dan.buoyancy als Modellmatrix. Licht in bauten.frag.
#include "common.glsl"
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aColor;
uniform mat4 uModel;
out vec3 vWorld;
out vec3 vNormal;
out vec3 vColor;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vWorld = w.xyz;
    vNormal = mat3(uModel) * aNormal;
    vColor = aColor;
    vec2 dh = w.xz - uCam.xz;
    gl_Position = uVP * vec4(w.x, w.y - dot(dh, dh) / (2.0 * EARTH_R), w.z, 1.0);
}
