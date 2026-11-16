// Bloom at 1/4 resolution: BLOOM_PASS 0 - bright pass (+ sun rays), 1 - blur X, 2 - blur Y.
#include "/lib/settings.glsl"
varying vec2 texcoord;
#ifdef VSH
#include "/program/fullscreen_vsh.glsl"
#endif
#ifdef FSH
uniform float viewWidth, viewHeight;
#if BLOOM_PASS == 0
#include "/lib/common.glsl"
uniform sampler2D colortex0;
#ifdef GODRAYS
uniform sampler2D depthtex0;
uniform mat4 gbufferProjection;
#endif
void main() {
    vec2 px = 1.0 / vec2(viewWidth, viewHeight);
    vec3 c = texture2D(colortex0, texcoord + px * vec2(-1.0, -1.0)).rgb
           + texture2D(colortex0, texcoord + px * vec2( 1.0, -1.0)).rgb
           + texture2D(colortex0, texcoord + px * vec2(-1.0,  1.0)).rgb
           + texture2D(colortex0, texcoord + px * vec2( 1.0,  1.0)).rgb;
    c *= 0.25;
    float l = luma(c);
#ifdef BLOOM
    vec3 b = c * (max(l - 0.9, 0.0) / max(l, 1e-4));
#else
    vec3 b = vec3(0.0);
#endif
#if defined GODRAYS && !defined NETHER && !defined END
    vec4 sp = gbufferProjection * vec4(sunPosition, 1.0);
    if (sp.w > 0.0 && isEyeInWater == 0) {
        vec2 sun = sp.xy / sp.w * 0.5 + 0.5;
        vec2 stepv = (sun - texcoord) / 12.0;
        vec2 p = texcoord + stepv * ign(gl_FragCoord.xy);
        float acc = 0.0;
        for (int i = 0; i < 12; i++) { acc += step(1.0, texture2D(depthtex0, p).x); p += stepv; }
        float fall = sat(1.0 - length((sun - texcoord) * vec2(viewWidth / viewHeight, 1.0)) * 0.9);
        b += sunLightColor() * acc / 12.0 * fall * fall * 0.35 * (0.4 + sunsetFactor()) * float(eyeBrightnessSmooth.y) / 240.0;
    }
#endif
/* DRAWBUFFERS:4 */
    gl_FragData[0] = vec4(b, 1.0);
}
#else
#if BLOOM_PASS == 1
uniform sampler2D colortex4;
#define SRC colortex4
#define DIR vec2(1.0, 0.0)
/* DRAWBUFFERS:5 */
#else
uniform sampler2D colortex5;
#define SRC colortex5
#define DIR vec2(0.0, 1.0)
/* DRAWBUFFERS:4 */
#endif
void main() {
    // source data occupies the lower-left quarter of the buffer (scale 0.25)
    vec2 full = vec2(viewWidth, viewHeight);
    vec2 o = DIR / full;
    vec2 tc = texcoord * 0.25;
    vec2 lim = floor(full * 0.25) / full - 0.5 / full;
    // 9-tap gaussian using 5 bilinear fetches
#define T(x) texture2D(SRC, clamp(x, 0.5 / full, lim)).rgb
    vec3 c = T(tc) * 0.2270;
    c += (T(tc + o * 1.3846) + T(tc - o * 1.3846)) * 0.3162;
    c += (T(tc + o * 3.2308) + T(tc - o * 3.2308)) * 0.0703;
    gl_FragData[0] = vec4(c, 1.0);
}
#endif
#endif
