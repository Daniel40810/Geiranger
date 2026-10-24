// terrain.vert — Gelände aus dem Viererbaum (CDLOD, aus Rungholt). Ein Gitter je Knoten, Lage und Größe je Instanz.
// Eingaben: aGrid (0..1 im Knoten), aNode (x0, z0, Kante in m, Stufe). Höhe aus terrain_height.glsl.
// Geomorphing: im äußeren Teil ihres Bereichs gleiten die ungeraden Punkte auf die Lage der gröberen Stufe; Höhe und
// Mip-Stufe hängen nur von der Endlage ab, darum passen Nachbarknoten. Ausgaben: vWorld, vNormal (grob), vDist.
// Seit Phase 5 verschiebt com.dan.cliff die Wände (Feinform, cliff.glsl). Seit Phase 3 liegt im Fjord der gemalte Grund unter NN.
#include "common.glsl"
#include "terrain_height.glsl"
#define CLIFF_VERTEX
#include "/com/dan/cliff/gl/cliff.glsl"
layout(location = 0) in vec2 aGrid;
layout(location = 1) in vec4 aNode;
uniform float uGridDim;
uniform int uWaterMode;
uniform int uCliffOn;        // Phase 5: Feinform der Wände aus com.dan.cliff
uniform float uPixelAngle;   // Öffnung eines Bildpunkts (rad)
uniform float uCliffRange;   // bis zu dieser Entfernung verschiebt die Feinform (m)     // 1: ocean fehlt, der Grund steht als Platzhalter-Spiegel auf uLevel (terrain.frag)
out vec3 vWorld;
out vec3 vNormal;
out float vDist;

void main() {
    vec2 w = aNode.xy + aGrid * aNode.z;
    float hc = heightAt(w, 3.0);
    float d0 = distance(vec3(w.x, hc, w.y), uCam);
    float end = uLodRange.x * exp2(aNode.w), start = end * uLodRange.y;
    float morph = clamp((d0 - start) / (end * 0.97 - start), 0.0, 1.0);
    vec2 frac = fract(aGrid * uGridDim * 0.5) * 2.0 / uGridDim;
    w -= frac * aNode.z * morph;
    hc = heightAt(w, 3.0);
    float d = distance(vec3(w.x, hc, w.y), uCam);
    float h = terrainHeight(w, d);
    if (uWaterMode == 1) h = max(h, uLevel);
    vec3 world = vec3(w.x, h, w.y);
    float e = clamp(d / (64.0 * uLodRange.z), 2.0, 400.0);
    float hx0 = terrainHeight(w - vec2(e, 0.0), d), hx1 = terrainHeight(w + vec2(e, 0.0), d);
    float hz0 = terrainHeight(w - vec2(0.0, e), d), hz1 = terrainHeight(w + vec2(0.0, e), d);
    vNormal = normalize(vec3(hx0 - hx1, 2.0 * e, hz0 - hz1));
    // Phase 5: die Feinform verschiebt die Wand entlang der Normale (nur an der Wand, in der Ferne ausgeblendet)
    //   Maßstab: Bildpunkte oder Abstand der Gitterpunkte, das Größere (sonst zeichnet das Gitter Dreiecke in die Wand).
    //   Seit Nacharbeit 11 hängt der Abstand der Gitterpunkte nur vom Abstand zum Auge ab (Zelle / Ende des Bereichs ist
    //   auf jeder Stufe 1 / (64 · uLodRange.z)), nicht vom Knoten: zwei Nachbarknoten verschoben dieselbe Kante sonst
    //   verschieden weit, und durch den Spalt sah man den Himmel (Status_Phase11a)
    float cellAt = max(2.0 * d / (64.0 * uLodRange.z), 2.0 * uLodRange.x / (64.0 * uLodRange.z));
    if (uCliffOn == 1) world = cliffDisplace(world, vNormal, max(d * uPixelAngle * 4.0, cellAt), d, uCliffRange);
    vWorld = world;
    vDist = d;
    // Erdkrümmung: in 10 km liegt der Boden 7,8 m tiefer als seine Höhe sagt, am Rand des Fernrings 70 m
    vec2 dh = world.xz - uCam.xz;
    gl_Position = uVP * vec4(world.x, world.y - dot(dh, dh) / (2.0 * EARTH_R), world.z, 1.0);
}
