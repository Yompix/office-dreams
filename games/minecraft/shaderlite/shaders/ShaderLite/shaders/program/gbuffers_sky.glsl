// Sky. The gradient itself is computed later (deferred1) so sky, fog and reflections match.
// Here: stars, sun, moon.
#include "/lib/settings.glsl"
varying vec2 texcoord;
varying vec4 glcolor;
#ifdef VSH
void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    glcolor = gl_Color;
    gl_Position = ftransform();
}
#endif
#ifdef FSH
uniform int renderStage;
uniform sampler2D texture;
uniform float rainStrength;
void main() {
    vec4 c = vec4(0.0, 0.0, 0.0, 1.0);
#ifdef SKYTEXTURED
    c = texture2D(texture, texcoord) * glcolor;
    c.rgb = c.rgb * c.rgb;
    if (renderStage == MC_RENDER_STAGE_SUN) c.rgb *= 6.0;
    else if (renderStage == MC_RENDER_STAGE_MOON) c.rgb *= 1.2;
    c.rgb *= c.a * (1.0 - rainStrength);
    c.a = 1.0;
#else
    if (renderStage == MC_RENDER_STAGE_STARS) c = vec4(glcolor.rgb * glcolor.a * 1.5, 1.0);
    else if (renderStage == MC_RENDER_STAGE_SUNSET) discard;
#endif
/* DRAWBUFFERS:0 */
    gl_FragData[0] = c;
}
#endif
