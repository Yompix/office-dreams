// Shadow lookup. Needs shadow.glsl and the uniforms below. TAPS = 1, 4 or 8.
uniform sampler2DShadow shadowtex0;
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;
uniform mat4 gbufferModelViewInverse;

const vec2 poisson8[8] = vec2[8](
    vec2(-0.613, 0.617), vec2(0.170, -0.040), vec2(-0.299, -0.792), vec2(0.645, 0.493),
    vec2(-0.651, -0.208), vec2(0.421, -0.570), vec2(-0.017, 0.909), vec2(0.946, -0.126));

// Returns 0..1: how much sun reaches the point (without N.L). fade = 1 outside the shadow map.
float sampleShadow(vec3 viewPos, vec3 nView, float NdotL, vec2 noisePos, out float fade) {
    vec3 wp = (gbufferModelViewInverse * vec4(viewPos, 1.0)).xyz;
    vec3 wn = mat3(gbufferModelViewInverse) * nView;
    float dist = length(wp);
    fade = smoothstep(shadowDistance * 0.80, shadowDistance * 0.98, dist);
    if (fade >= 1.0) return 1.0;
    float texel = 2.0 * shadowDistance / float(shadowMapResolution);
    wp += wn * texel * (0.6 + dist * 0.025) * (NdotL > 0.0 ? 1.0 : -1.0);
    vec4 sp = shadowProjection * (shadowModelView * vec4(wp, 1.0));
    float df = distortFactor(sp.xy);
    sp.xy /= df;
    sp.z *= 0.5;
    sp.xyz = sp.xyz * 0.5 + 0.5;
    sp.z -= 0.00015;
    if (sp.x <= 0.0 || sp.y <= 0.0 || sp.x >= 1.0 || sp.y >= 1.0) { fade = 1.0; return 1.0; }
#if TAPS == 1
    return shadow2D(shadowtex0, sp.xyz).x;
#else
    float r = 1.4 / float(shadowMapResolution) * df;
    float a = ign(noisePos) * 6.2832;
    mat2 rot = mat2(cos(a), sin(a), -sin(a), cos(a));
    float s = 0.0;
#if TAPS == 4
    for (int i = 0; i < 4; i++) s += shadow2D(shadowtex0, vec3(sp.xy + rot * poisson8[i * 2] * r * 1.6, sp.z)).x;
    return s * 0.25;
#else
    for (int i = 0; i < 8; i++) s += shadow2D(shadowtex0, vec3(sp.xy + rot * poisson8[i] * r * 2.0, sp.z)).x;
    return s * 0.125;
#endif
#endif
}
