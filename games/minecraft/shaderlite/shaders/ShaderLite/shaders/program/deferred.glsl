// Light pass at reduced resolution (LIGHT_SCALE): shadow * N.L for each pixel.
// Shadows are the most expensive part and change smoothly, so 1/4 of the pixels is enough.
#include "/lib/settings.glsl"
varying vec2 texcoord;
#ifdef VSH
#include "/program/fullscreen_vsh.glsl"
#endif
#ifdef FSH
#include "/lib/common.glsl"
#include "/lib/shadow.glsl"
uniform sampler2D depthtex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform mat4 gbufferProjectionInverse;
uniform float near;
#if defined SHADOWS && !defined NETHER && !defined END
#define TAPS SHADOW_FILTER
#include "/lib/shadowsample.glsl"
#endif

vec3 decodeNormal(vec2 e) {
    vec2 f = e * 4.0 - 2.0; float d = dot(f, f);
    float g = sqrt(1.0 - d / 4.0);
    return vec3(f * g, 1.0 - d / 2.0);
}

void main() {
    float depth = texture2D(depthtex0, texcoord).x;
    float mat = texture2D(colortex2, texcoord).x * 5.0;
    vec4 data = texture2D(colortex1, texcoord);
    vec4 vp = gbufferProjectionInverse * vec4(vec3(texcoord, depth) * 2.0 - 1.0, 1.0);
    vec3 viewPos = vp.xyz / vp.w;
    float vis = 0.0;
    if (depth < 1.0 && mat > 0.5 && mat < 3.5) {
        vec3 n = decodeNormal(data.xy);
        float NdotL = dot(n, normalize(shadowLightPosition));
        float NdotLs = mat > 1.5 ? abs(NdotL) * 0.65 + 0.35 : NdotL;
        if (NdotLs > 0.0) {
#if defined SHADOWS && !defined NETHER && !defined END
            float fade;
            float sh = sampleShadow(viewPos, n, NdotL, gl_FragCoord.xy, fade);
            float far_ = pow(data.w, 6.0);
            sh = mix(sh, far_, fade);
            vis = sh * NdotLs * sat(data.w * 6.0);
#else
            vis = NdotLs * pow(data.w, 5.0);
#endif
        }
    }
/* DRAWBUFFERS:3 */
    gl_FragData[0] = vec4(vis, -viewPos.z, 0.0, 1.0);
}
#endif
