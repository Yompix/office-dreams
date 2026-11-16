// Waving plants. id: 20 leaves, 21 grass/flowers (bottom fixed), 22 upper half of tall plants
vec3 waveOffset(vec3 p, float id, bool top) {
    float t = frameTimeCounter * WAVING_SPEED;
    float strength = 1.0 + rainStrength * 1.2;
    float w1 = sin(t * 1.9 + p.x * 0.55 + p.z * 0.35);
    float w2 = sin(t * 1.3 + p.z * 0.65 - p.x * 0.2);
    float gust = sin(t * 0.37 + p.x * 0.05 + p.z * 0.04) * 0.5 + 0.5;
    vec3 o = vec3(w1, 0.0, w2) * (0.4 + gust * 0.6) * strength;
    if (id == 20.0) return o * 0.045 + vec3(0.0, sin(t * 2.3 + p.x + p.y * 0.7) * 0.012, 0.0);
    if (id == 21.0) return top ? o * 0.11 : vec3(0.0);
    return o * (top ? 0.16 : 0.11);
}
