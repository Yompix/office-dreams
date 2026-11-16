// Shadow pass: renders the world from the sun into a small texture
#include "/lib/settings.glsl"
#include "/lib/shadow.glsl"
varying vec2 texcoord;
varying vec4 glcolor;

#ifdef VSH
attribute vec4 mc_Entity;
attribute vec4 mc_midTexCoord;
uniform mat4 shadowModelView;
uniform mat4 shadowModelViewInverse;
uniform vec3 cameraPosition;
uniform float frameTimeCounter;
uniform float rainStrength;
#include "/lib/waving.glsl"
void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    glcolor = gl_Color;
    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
    float id = mc_Entity.x;
    if (id == 10.0) { gl_Position = vec4(10.0); return; }
#ifdef WAVING_PLANTS
    if (id >= 20.0 && id <= 22.0) {
        vec4 wp = shadowModelViewInverse * viewPos;
        bool top = gl_MultiTexCoord0.t < mc_midTexCoord.t;
        wp.xyz += waveOffset(wp.xyz + cameraPosition, id, top);
        viewPos = shadowModelView * wp;
    }
#endif
    gl_Position = gl_ProjectionMatrix * viewPos;
    gl_Position.xy = distortShadow(gl_Position.xy);
    gl_Position.z *= 0.5;
}
#endif

#ifdef FSH
uniform sampler2D tex;
void main() {
    vec4 c = texture2D(tex, texcoord) * glcolor;
    if (c.a < 0.1) discard;
    gl_FragData[0] = c;
}
#endif
