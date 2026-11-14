#version 150
// Глухомань: зерно, виньетка, аберрация и обесцвечивание от страха, волны рядом с существом,
// в кат-сценах — размытие фона (перевод фокуса), лучи света, цветокоррекция и разрыв неба.
uniform sampler2D DiffuseSampler;
in vec2 texCoord;
uniform vec2 OutSize;
uniform float Time, Fear, Near, Strength, Flash, Blur, Rays, RayX, RayY, Tear, TintR, TintG, TintB;
out vec4 fragColor;

float rand(vec2 c) { return fract(sin(dot(c, vec2(12.9898, 78.233))) * 43758.5453); }

vec3 tap(vec2 uv, vec2 d, float ca) {
    return vec3(texture(DiffuseSampler, uv + d * ca).r, texture(DiffuseSampler, uv).g, texture(DiffuseSampler, uv - d * ca).b);
}

void main() {
    vec2 uv = texCoord;
    vec2 d = uv - 0.5;
    float r = length(d);
    float s = Strength;
    // волны искажения: существо рядом
    uv += vec2(sin(uv.y * 38.0 + Time * 6.0), cos(uv.x * 31.0 + Time * 5.0)) * Near * 0.005 * s;
    float ca = (0.0012 + Fear * 0.004 + Near * 0.007) * s * r * 2.0;
    vec3 col = tap(uv, d, ca);
    // размытие к краям (перевод фокуса в кат-сценах)
    if (Blur > 0.01) {
        vec2 px = 1.0 / OutSize;
        float rad = Blur * smoothstep(0.08, 0.5, r) * 6.0;
        vec3 acc = col;
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.785398;
            acc += texture(DiffuseSampler, uv + vec2(cos(a), sin(a)) * px * rad * 2.0).rgb;
        }
        col = acc / 9.0;
    }
    // лучи света от точки (радиальное размытие ярких мест)
    if (Rays > 0.01) {
        vec2 lp = vec2(RayX, RayY);
        vec2 step = (uv - lp) / 24.0;
        vec2 p = uv;
        vec3 glow = vec3(0.0);
        float w = 1.0;
        for (int i = 0; i < 24; i++) {
            p -= step;
            vec3 c = texture(DiffuseSampler, p).rgb;
            glow += max(c - 0.55, 0.0) * w;
            w *= 0.93;
        }
        col += glow / 12.0 * Rays * s;
    }
    // цветокоррекция и обесцвечивание от страха
    col *= vec3(TintR, TintG, TintB);
    float l = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(l), clamp(0.12 + Fear * 0.5, 0.0, 0.8) * s);
    col *= vec3(0.96, 1.0, 1.05);
    // разрыв неба: светящаяся рваная трещина в верхней части
    if (Tear > 0.01) {
        // рваная ломаная линия: две «пилы» разной частоты, по краям светящееся марево
        float y0 = 0.80 + (abs(fract(uv.x * 7.0) - 0.5) - 0.25) * 0.05 + (abs(fract(uv.x * 23.0 + 0.37) - 0.5) - 0.25) * 0.02
                 + (rand(vec2(floor(uv.x * 90.0), 3.0)) - 0.5) * 0.006;
        float dist = abs(uv.y - y0);
        float core = exp(-dist * 420.0 / (Tear + 0.05));
        float halo = exp(-dist * 22.0) * 0.35;
        col = mix(col, col * vec3(0.6, 0.45, 0.5), halo * Tear);
        col += vec3(1.0, 0.55, 0.35) * (core * 1.8 + halo * 0.5) * Tear;
    }
    // зерно плёнки и виньетка
    float g = rand(uv * OutSize + fract(Time * 7.13) * 91.0) - 0.5;
    col += g * (0.03 + Fear * 0.05) * s;
    col *= 1.0 - smoothstep(0.35, 0.95, r) * (0.3 + Fear * 0.4) * s;
    col = mix(col, vec3(1.0, 0.96, 0.95), Flash);
    fragColor = vec4(col, 1.0);
}
