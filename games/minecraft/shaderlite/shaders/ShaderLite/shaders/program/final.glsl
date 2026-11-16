// Final: sharpening (AMD CAS style), bloom, tonemapping, color grading, vignette.
#include "/lib/settings.glsl"
varying vec2 texcoord;
#ifdef VSH
#include "/program/fullscreen_vsh.glsl"
#endif
#ifdef FSH
#include "/lib/common.glsl"
uniform sampler2D colortex0;
uniform sampler2D colortex4;
uniform float viewWidth, viewHeight;

vec3 sat3(vec3 v) { return clamp(v, 0.0, 1.0); }
vec3 tonemap(vec3 x, float ex) {
    x *= ex;
    return sat3((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14));
}
void main() {
    float eye = float(eyeBrightnessSmooth.y) / 240.0;
#if defined NETHER || defined END
    float expAuto = 1.0;
#else
    float expAuto = mix(1.8, 1.0, eye) * mix(1.5, 1.0, dayFactor());
#endif
    float ex = EXPOSURE * expAuto;
    vec2 px = 1.0 / vec2(viewWidth, viewHeight);
    vec3 c = tonemap(texture2D(colortex0, texcoord).rgb, ex);
#if SHARPEN > 0
    vec3 n = tonemap(texture2D(colortex0, texcoord + vec2(0.0, px.y)).rgb, ex);
    vec3 s = tonemap(texture2D(colortex0, texcoord - vec2(0.0, px.y)).rgb, ex);
    vec3 e = tonemap(texture2D(colortex0, texcoord + vec2(px.x, 0.0)).rgb, ex);
    vec3 w = tonemap(texture2D(colortex0, texcoord - vec2(px.x, 0.0)).rgb, ex);
    vec3 mn = min(c, min(min(n, s), min(e, w)));
    vec3 mx = max(c, max(max(n, s), max(e, w)));
    vec3 amp = sqrt(sat3(min(mn, 1.0 - mx) / max(mx, 1e-4)));
    vec3 wgt = -amp * mix(0.125, 0.2, float(SHARPEN) / 100.0) * (float(SHARPEN) > 0.0 ? 1.0 : 0.0);
    c = sat3((c + (n + s + e + w) * wgt) / (1.0 + 4.0 * wgt));
#endif
#if defined BLOOM || defined GODRAYS
    vec2 bl = min(texcoord * 0.25, floor(vec2(viewWidth, viewHeight) * 0.25) / vec2(viewWidth, viewHeight) - 0.5 / vec2(viewWidth, viewHeight));
    vec3 b = texture2D(colortex4, bl).rgb * BLOOM_STRENGTH;
    b = 1.0 - exp(-b * ex);
    c = 1.0 - (1.0 - c) * (1.0 - b);
#endif

    c = sqrt(c);
    float l = luma(c);
    c = mix(vec3(l), c, SATURATION);
    c = (c - 0.5) * CONTRAST + 0.5;
    c *= vec3(1.0 + WARMTH * 0.25, 1.0, 1.0 - WARMTH * 0.3);
    vec2 v = texcoord - 0.5;
    c *= 1.0 - dot(v, v) * VIGNETTE * 1.6;
    c += (ign(gl_FragCoord.xy) - 0.5) / 255.0;
    gl_FragColor = vec4(sat3(c), 1.0);
}
#endif
