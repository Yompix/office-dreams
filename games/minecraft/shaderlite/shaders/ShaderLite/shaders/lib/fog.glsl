// Fog and aerial perspective (distant things fade into the sky color), underwater, lava, cave fog.
// Applied right where each surface is shaded (deferred1, water, particles) - saves a full-screen pass.
vec3 applyFog(vec3 col, vec3 viewPos, bool isSky) {
    float dist = length(viewPos);
    vec3 dir = viewPos / dist;
    float fog;
    vec3 fc;
    if (isEyeInWater == 1) {
        fog = 1.0 - exp(-dist * 0.055);
        fc = vec3(0.02, 0.10, 0.14) * (ambientColor() + 0.1);
    } else if (isEyeInWater == 2) {
        fog = 1.0 - exp(-dist * 0.8); fc = vec3(1.2, 0.35, 0.05);
    } else if (isEyeInWater == 3) {
        fog = 1.0 - exp(-dist * 0.5); fc = vec3(0.6, 0.65, 0.7);
    } else {
#if defined NETHER
        fog = 1.0 - exp(-dist * 0.010 * FOG_DENSITY);
        fog = max(fog, smoothstep(far * 0.5, far, dist));
#elif defined END
        fog = 1.0 - exp(-dist * 0.004 * FOG_DENSITY);
#else
        float air = 1.0 - exp(-dist * 0.0014 * FOG_DENSITY * (1.0 + rainStrength * 4.0));
        float border = smoothstep(far * 0.78, far * 1.0, dist);
        fog = max(air, border);
#endif
        fc = fogColorFor(dir);
        float eye = float(eyeBrightnessSmooth.y) / 240.0;
        fc *= mix(0.06, 1.0, eye);
    }
    if (isSky && isEyeInWater == 0) fog = 0.0;
    fog = mix(fog, 1.0, blindness);
    if (blindness > 0.0) fc *= 1.0 - blindness;
    return mix(col, fc, fog);
}
