// Translucent blocks: water (waves, sky reflection, sun glint, depth), glass, ice. Lit right here.
#include "/lib/settings.glsl"
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 normal;
varying vec3 viewPos;
varying vec3 worldPos;
varying float isWater;

#ifdef VSH
attribute vec4 mc_Entity;
uniform mat4 gbufferModelViewInverse;
uniform vec3 cameraPosition;
void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    lmcoord = clamp((lmcoord - 1.0 / 32.0) * 16.0 / 15.0, 0.0, 1.0);
    glcolor = gl_Color;
    normal = normalize(gl_NormalMatrix * gl_Normal);
    isWater = mc_Entity.x == 10.0 ? 1.0 : 0.0;
    vec4 vp = gl_ModelViewMatrix * gl_Vertex;
    viewPos = vp.xyz;
    worldPos = (gbufferModelViewInverse * vp).xyz + cameraPosition;
    gl_Position = gl_ProjectionMatrix * vp;
}
#endif

#ifdef FSH
#include "/lib/common.glsl"
#include "/lib/lighting.glsl"
#include "/lib/fog.glsl"
#include "/lib/shadow.glsl"
uniform sampler2D texture;
uniform sampler2D depthtex1;
uniform mat4 gbufferModelView;
uniform float near;
uniform float viewWidth, viewHeight;
#if defined SHADOWS && !defined NETHER && !defined END
#define TAPS 1
#include "/lib/shadowsample.glsl"
#endif

float linZ(float d) { return 2.0 * near * far / (far + near - (d * 2.0 - 1.0) * (far - near)); }

// Waves: sum of sines, normal computed analytically (no textures, very cheap)
vec3 waveNormal(vec2 p) {
    float t = frameTimeCounter;
    vec2 g = vec2(0.0);
    vec2 d1 = vec2(0.80, 0.60), d2 = vec2(-0.52, 0.85);
    g += d1 * cos(dot(p, d1) * 1.3 + t * 1.6) * 1.3 * 0.50;
    g += d2 * cos(dot(p, d2) * 2.1 + t * 2.2) * 2.1 * 0.25;
#if WATER_WAVES >= 4
    vec2 d3 = vec2(0.28, -0.96), d4 = vec2(0.96, 0.27);
    g += d3 * cos(dot(p, d3) * 3.7 + t * 3.1) * 3.7 * 0.10;
    g += d4 * cos(dot(p, d4) * 5.9 + t * 4.3) * 5.9 * 0.05;
#endif
    g *= 0.075 * (1.0 + rainStrength);
    return normalize(vec3(-g.x, 1.0, -g.y));
}

void main() {
    vec4 albedo = texture2D(texture, texcoord) * glcolor;
    vec3 n = normalize(normal);
    if (!gl_FrontFacing) n = -n;
    vec3 L = normalize(shadowLightPosition);
    vec3 V = normalize(viewPos);
    float NdotL = dot(n, L);
    float vis = 0.0;
#if defined SHADOWS && !defined NETHER && !defined END
    float fade;
    float sh = sampleShadow(viewPos, n, NdotL, gl_FragCoord.xy, fade);
    sh = mix(sh, pow(lmcoord.y, 6.0), fade) * sat(lmcoord.y * 6.0);
#else
    float sh = pow(lmcoord.y, 5.0);
#endif
    vec3 col;
    float alpha = albedo.a;
    if (isWater > 0.5) {
        vec3 up = normalize(upPosition);
        vec3 wn = n;
#if WATER_WAVES > 0
        if (dot(n, up) > 0.9) {
            vec3 wv = waveNormal(worldPos.xz);
            wv.xz /= 1.0 + length(viewPos) * 0.035;            // far away: calmer (no shimmering stripes)
            wn = normalize(mat3(gbufferModelView) * normalize(wv));
        }
#endif
        float cosT = sat(dot(-V, wn));
        float fres = 0.02 + 0.98 * pow(1.0 - cosT, 5.0);
        vec3 R = reflect(V, wn);
        float rh = dot(R, up);
        R = normalize(R + up * max(0.0, -rh + 0.02));
        float skyMask = lmcoord.y * lmcoord.y;
#if defined NETHER || defined END
        vec3 refl = toLinear(fogColor);
#else
        vec3 refl = skyGradient(R) * skyMask + ambientColor() * 0.05 * (1.0 - skyMask);
        // sun glint
        refl += sunLightColor() * pow(max(dot(R, L), 0.0), 220.0) * 6.0 * sh;
#endif
        // water depth: shallow = clear, deep = dark blue
        float behind = linZ(texture2D(depthtex1, gl_FragCoord.xy / vec2(viewWidth, viewHeight)).x);
        float wdepth = max(behind - linZ(gl_FragCoord.z), 0.0);
        vec3 tint = toLinear(glcolor.rgb) * vec3(0.30, 0.62, 0.78);
        vec3 body = shadeColor(tint * 0.30, sh * max(NdotL, 0.0) * 0.5, lmcoord, 1.0);
        float a = mix(WATER_ALPHA * 0.92, min(WATER_ALPHA + 0.27, 0.97), 1.0 - exp(-wdepth * 0.35));
        if (isEyeInWater == 1) { fres = 0.0; a = 0.25; }
        col = mix(body, refl, fres);
        alpha = sat(mix(a, 1.0, fres));
    } else {
        vis = sh * max(NdotL, 0.0);
        col = shadeColor(toLinear(albedo.rgb), vis, lmcoord, 1.0);
    }
    col = applyFog(col, viewPos, false);
/* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(col, alpha);
}
#endif
