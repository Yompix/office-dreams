// Full resolution: surface color * (sky + torches + sun). Shadow comes from the reduced buffer,
// upsampled with depth awareness (no halos on block edges).
#include "/lib/settings.glsl"
varying vec2 texcoord;
#ifdef VSH
#include "/program/fullscreen_vsh.glsl"
#endif
#ifdef FSH
#include "/lib/common.glsl"
#include "/lib/lighting.glsl"
#include "/lib/fog.glsl"
uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D colortex3;
uniform sampler2D depthtex0;
uniform mat4 gbufferProjectionInverse;
uniform float viewWidth, viewHeight;

/*
const int colortex0Format = R11F_G11F_B10F;
const int colortex1Format = RGBA8;
const int colortex2Format = R8;
const int colortex3Format = RG16F;
const int colortex4Format = R11F_G11F_B10F;
const int colortex5Format = R11F_G11F_B10F;
const vec4 colortex0ClearColor = vec4(0.0, 0.0, 0.0, 1.0);
const vec4 colortex2ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const bool colortex3Clear = false;
const bool colortex4Clear = false;
const bool colortex5Clear = false;
*/

// colortex3 is full-size; the scaled pass (scale.deferred) fills only its lower-left part.
float upsampleVis(float z) {
#if LIGHT_SCALE == 100
    return texture2D(colortex3, texcoord).x;
#else
    vec2 full = vec2(viewWidth, viewHeight);
    vec2 low = floor(full * (float(LIGHT_SCALE) / 100.0));
    vec2 p = texcoord * low - 0.5;
    vec2 f = fract(p);
    vec2 b = clamp(floor(p), vec2(0.0), low - 2.0);
    vec2 s00 = texture2D(colortex3, (b + vec2(0.5, 0.5)) / full).xy;
    vec2 s10 = texture2D(colortex3, (b + vec2(1.5, 0.5)) / full).xy;
    vec2 s01 = texture2D(colortex3, (b + vec2(0.5, 1.5)) / full).xy;
    vec2 s11 = texture2D(colortex3, (b + vec2(1.5, 1.5)) / full).xy;
    float k = 1.0 / (0.04 * z + 0.05);
    vec4 w = vec4((1.0 - f.x) * (1.0 - f.y), f.x * (1.0 - f.y), (1.0 - f.x) * f.y, f.x * f.y);
    w *= exp(-abs(vec4(s00.y, s10.y, s01.y, s11.y) - z) * k) + 1e-4;
    return dot(w, vec4(s00.x, s10.x, s01.x, s11.x)) / dot(w, vec4(1.0));
#endif
}

void main() {
    vec3 col = texture2D(colortex0, texcoord).rgb;
    float depth = texture2D(depthtex0, texcoord).x;
    float mat = texture2D(colortex2, texcoord).x * 5.0;
    if (depth >= 1.0) {
        // sky: gradient + whatever was drawn on top (sun, moon, stars)
        vec4 vp = gbufferProjectionInverse * vec4(vec3(texcoord, 1.0) * 2.0 - 1.0, 1.0);
#if defined NETHER
        col = toLinear(fogColor);
#elif defined END
        col = col;
#else
        col = skyGradient(normalize(vp.xyz)) * mix(0.04, 1.0, float(eyeBrightnessSmooth.y) / 240.0) + col;
#endif
    } else if (mat > 0.5 && mat < 3.5) {
        vec4 data = texture2D(colortex1, texcoord);
        vec4 vp = gbufferProjectionInverse * vec4(vec3(texcoord, depth) * 2.0 - 1.0, 1.0);
        float z = -vp.z / vp.w;
        float vis = upsampleVis(z);
        col = shadeColor(toLinear(col), vis, data.zw, mat);
    }
    if (mat < 3.6 || depth >= 1.0) {
        vec4 fp = gbufferProjectionInverse * vec4(vec3(texcoord, depth) * 2.0 - 1.0, 1.0);
        col = applyFog(col, fp.xyz / fp.w, depth >= 1.0);
    }
/* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(col, 1.0);
}
#endif
