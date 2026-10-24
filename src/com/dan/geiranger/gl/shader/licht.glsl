// licht.glsl — Phase 8: Schatten der Wände und Wolken und Rauschen für Fluss (elva.frag) und Bauten (bauten.frag),
// wie im Gelände (terrain.frag). Braucht common.glsl und terrain_height.glsl davor.
uniform sampler2D uWallMain, uWallFern;   // Anteil direkten Lichts (Schatten der Wände)
uniform vec3 uWallRectMain, uWallRectFern; // Ecke x, z, Kantenlänge
uniform int uWallOn;
uniform sampler2D uCloudShadow;           // Wolkenschatten (com.dan.cloud)
uniform vec4 uCloudRect;                  // Ecke x, z, Kantenlänge, an

float wallLight(vec2 w) {
    if (uWallOn == 0) return 1.0;
    float f = 1.0 - smoothstep(0.0, uNaht, insideDist(w));
    float a = f < 1.0 ? textureLod(uWallMain, (w - uWallRectMain.xy) / uWallRectMain.z, 0.0).r : 1.0;
    vec2 fu = (w - uWallRectFern.xy) / uWallRectFern.z;
    float b = f > 0.0 && all(greaterThanEqual(fu, vec2(0.0))) && all(lessThanEqual(fu, vec2(1.0))) ? textureLod(uWallFern, fu, 0.0).r : 1.0;
    return mix(a, b, f);
}
float cloudLight(vec2 w) { return uCloudRect.w > 0.0 ? textureLod(uCloudShadow, (w - uCloudRect.xy) / uCloudRect.z, 0.0).r : 1.0; }

float lHash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float lNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(lHash(i), lHash(i + vec2(1, 0)), u.x), mix(lHash(i + vec2(0, 1)), lHash(i + vec2(1, 1)), u.x), u.y);
}
// Gefälle des Rauschens (analytisch, wie lNoise)
vec2 lNoiseGrad(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f), du = 6.0 * f * (1.0 - f);
    float a = lHash(i), b = lHash(i + vec2(1, 0)), c = lHash(i + vec2(0, 1)), d = lHash(i + vec2(1, 1));
    return du * vec2(b - a + (a - b - c + d) * u.y, c - a + (a - b - c + d) * u.x);
}
