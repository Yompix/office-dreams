// Lighting of one point. Needs common.glsl. albedo is linear.
// lm.x - block light (torches), lm.y - sky light; vis - sun visibility (shadow * N.L); mat: 1 normal, 2 foliage, 3 emissive
vec3 shadeColor(vec3 albedo, float vis, vec2 lm, float mat) {
    float sky = lm.y * lm.y;
    vec3 amb = ambientColor() * (sky * 0.92 + 0.08);
#ifdef NETHER
    amb = ambientColor();
#endif
    float bl = lm.x;
    vec3 torch = torchColor() * (bl * bl * bl * 0.9 + bl * 0.1) * (1.0 - sky * dayFactor() * 0.5);
    vec3 minL = vec3(0.012, 0.012, 0.016) * MIN_LIGHT;
    vec3 light = amb + torch + minL + sunLightColor() * vis;
    light += vec3(nightVision * 0.5);
    vec3 c = albedo * light;
    if (mat > 2.5) c = albedo * (1.5 + luma(albedo) * 3.0);
    return c;
}
