// Small stuff without shadows: particles, rain, lines, beacon beam, clouds, translucent hand items.
#include "/lib/settings.glsl"
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 viewPos;

#ifdef VSH
void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    lmcoord = clamp((lmcoord - 1.0 / 32.0) * 16.0 / 15.0, 0.0, 1.0);
    glcolor = gl_Color;
    vec4 vp = gl_ModelViewMatrix * gl_Vertex;
    viewPos = vp.xyz;
    gl_Position = gl_ProjectionMatrix * vp;
}
#endif

#ifdef FSH
#include "/lib/common.glsl"
#include "/lib/lighting.glsl"
#include "/lib/fog.glsl"
uniform sampler2D texture;
void main() {
#ifdef NOTEX
    vec4 c = glcolor;
#else
    vec4 c = texture2D(texture, texcoord) * glcolor;
#endif
    if (c.a < 0.004) discard;
    vec3 col;
#if defined RAW
    col = c.rgb;
#elif defined UNLIT
    col = toLinear(c.rgb) * UNLIT;
#elif defined CLOUDS
    vec3 light = ambientColor() * 1.1 + sunLightColor() * 0.45;
#if defined NETHER || defined END
    light = vec3(1.0);
#endif
    col = toLinear(c.rgb) * light;
    float d = length(viewPos);
    c.a *= 1.0 - smoothstep(far * 2.0, far * 4.5, d);
#elif defined WEATHER
    col = toLinear(c.rgb) * (ambientColor() * 1.4 + 0.05);
    c.a *= 0.55;
#else
    col = shadeColor(toLinear(c.rgb), pow(lmcoord.y, 4.0) * 0.6, lmcoord, 1.0);
#endif
#if !defined CLOUDS && !defined RAW
    col = applyFog(col, viewPos, false);
#endif
#ifdef CLOUDS
/* DRAWBUFFERS:02 */
    gl_FragData[0] = vec4(col, c.a);
    gl_FragData[1] = vec4(1.0, 0.0, 0.0, c.a);
#else
/* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(col, c.a);
#endif
}
#endif
