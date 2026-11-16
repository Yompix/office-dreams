// Opaque geometry (blocks, mobs, block entities, hand): writes surface color, normal, lightmap and material;
// lighting is done later in one deferred pass, so shadows are only computed for visible pixels.
#include "/lib/settings.glsl"

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 normal;
varying float mat;

#ifdef VSH
attribute vec4 mc_Entity;
attribute vec4 mc_midTexCoord;
uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform vec3 cameraPosition;
uniform float frameTimeCounter;
uniform float rainStrength;
#include "/lib/waving.glsl"

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    lmcoord = clamp((lmcoord - 1.0 / 32.0) * 16.0 / 15.0, 0.0, 1.0);
    glcolor = gl_Color;
    normal = normalize(gl_NormalMatrix * gl_Normal);
    mat = 1.0;
    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
#ifdef TERRAIN
    float id = mc_Entity.x;
    if (id == 20.0 || id == 21.0 || id == 22.0) mat = 2.0;
    if (id == 30.0 || id == 31.0) mat = 3.0;
#ifdef WAVING_PLANTS
    if (id >= 20.0 && id <= 22.0) {
        vec4 wp = gbufferModelViewInverse * viewPos;
        bool top = gl_MultiTexCoord0.t < mc_midTexCoord.t;
        wp.xyz += waveOffset(wp.xyz + cameraPosition, id, top) * lmcoord.y;
        viewPos = gbufferModelView * wp;
    }
#endif
#endif
#ifdef SPIDEREYES
    mat = 3.0;
#endif
    gl_Position = gl_ProjectionMatrix * viewPos;
}
#endif

#ifdef FSH
uniform sampler2D texture;
#ifdef ENTITY
uniform vec4 entityColor;
#endif

vec2 encodeNormal(vec3 n) { float p = sqrt(n.z * 8.0 + 8.0); return n.xy / p + 0.5; }

void main() {
    vec4 albedo = texture2D(texture, texcoord) * glcolor;
#ifdef ENTITY
    albedo.rgb = mix(albedo.rgb, entityColor.rgb, entityColor.a);
#endif
    if (albedo.a < 0.1) discard;
    vec3 n = normalize(normal);
    if (!gl_FrontFacing) n = -n;
/* DRAWBUFFERS:012 */
    gl_FragData[0] = vec4(albedo.rgb, 1.0);
    gl_FragData[1] = vec4(encodeNormal(n), lmcoord);
    gl_FragData[2] = vec4(mat * 0.2, 0.0, 0.0, 1.0);
}
#endif
