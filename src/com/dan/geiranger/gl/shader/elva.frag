// elva.frag — Phase 8: Wasser der Geirangelva. Wellen und Schaum treiben mit der Strömung (Flow-Map mit zwei Phasen,
// wie com.dan.river.WaterSurface: nie verzerrt, nie springend); die Rauigkeit wächst mit Geschwindigkeit und Unruhe.
// Farbe: Gletschermilch, trüb (der Grund scheint nur im Flachen durch), Spiegelung nach Fresnel aus dem Himmel,
// Glanz der Sonne, Schatten der Wände und Wolken, Dunst. Vormultipliziertes Alpha: am Ufer und im Flachen durchsichtig.
#include "common.glsl"
#include "terrain_height.glsl"
#include "licht.glsl"
in vec3 vWorld;
in vec4 vFlow;
in float vWet;
out vec4 frag;
uniform float uTurbid;      // Trübe 0..1
void main() {
    if (vWet < 0.02) discard;
    vec2 v = vFlow.xy;
    float sp = length(v), foamIn = clamp(vFlow.z, 0.0, 1.0);
    const float T = 1.6;
    float t0 = fract(uTime / T), t1 = fract(uTime / T + 0.5);
    float w0 = 1.0 - abs(2.0 * t0 - 1.0), w1 = 1.0 - w0;
    vec2 p = vWorld.xz, o0 = v * t0 * T, o1 = v * t1 * T;
    vec2 g0 = lNoiseGrad((p - o0) * 0.6) * 0.6 + lNoiseGrad((p - o0) * 2.3 + 7.3) * 0.35 * 2.3 * 0.3;
    vec2 g1 = lNoiseGrad((p - o1) * 0.6 + 3.1) * 0.6 + lNoiseGrad((p - o1) * 2.3 + 1.7) * 0.35 * 2.3 * 0.3;
    vec2 g = (g0 * w0 + g1 * w1) * (0.05 + 0.04 * min(sp, 5.0) + 0.3 * foamIn);
    vec3 n = normalize(vec3(-g.x, 1.0, -g.y));
    float f0 = lNoise((p - o0) * 1.4), f1 = lNoise((p - o1) * 1.4 + 5.0);
    float f2 = lNoise((p - o0) * 4.1 + 2.0) * w0 + lNoise((p - o1) * 4.1 + 9.0) * w1;
    float fn = (f0 * w0 + f1 * w1) * 0.7 + f2 * 0.3;
    float foam = smoothstep(0.62, 0.8, fn + foamIn * 0.75 - 0.25) * clamp(foamIn * 1.6, 0.0, 1.0);
    vec3 V = normalize(uCam - vWorld);
    float dist = distance(uCam, vWorld);
    float shade = wallLight(vWorld.xz) * cloudLight(vWorld.xz);
    vec3 amb = uSkyMode == 1 ? uSkyAmb : skyColor(vec3(0.0, 1.0, 0.0)) * 0.55 / 3.14159265;
    vec3 R = reflect(-V, n);
    R.y = abs(R.y);
    float F = 0.02 + 0.98 * pow(1.0 - max(dot(n, V), 0.0), 5.0);
    vec3 sun = sunLight() * shade;
    vec3 body = mix(vec3(0.020, 0.070, 0.066), vec3(0.075, 0.140, 0.120), uTurbid) * (sun * max(uSun.y, 0.0) / 3.14159265 + amb * 1.2);
    vec3 spec = sun * pow(max(dot(R, uSun), 0.0), 400.0) * 1.5;
    vec3 foamC = vec3(0.86, 0.90, 0.90) * (sun * (0.3 + 0.7 * max(uSun.y, 0.0)) / 3.14159265 + amb * 1.5);
    vec3 col = mix(body, skyColor(R), F) + spec * (1.0 - foam);
    col = mix(col, foamC, foam);
    float a = vWet * clamp(0.35 + vFlow.w * 0.7, 0.0, 1.0);
    a = max(a, foam * vWet);
    col = aerial(col, -V, dist);
    frag = vec4(col * a, a);
}
