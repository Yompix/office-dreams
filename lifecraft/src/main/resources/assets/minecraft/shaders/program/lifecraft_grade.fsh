#version 150

// LifeCraft: цветокоррекция «как в кино» — сочные цвета, мягкий контраст,
// тёплый день и холодная ночь, свечение ярких огней (неон, фонари) и виньетка.
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Night;
uniform float Strength;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

vec3 bright(vec2 uv) {
    vec3 c = texture(DiffuseSampler, uv).rgb;
    float l = max(c.r, max(c.g, c.b));
    return c * smoothstep(0.62, 1.0, l);
}

void main() {
    vec3 src = texture(DiffuseSampler, texCoord).rgb;
    vec3 c = src;

    // свечение: 16 выборок по кругу на двух радиусах
    vec3 glow = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.785398;
        vec2 d = vec2(cos(a), sin(a)) * oneTexel;
        glow += bright(texCoord + d * 3.0);
        glow += bright(texCoord + d * 7.0) * 0.6;
    }
    glow /= 12.8;
    c += glow * mix(0.35, 0.9, Night);

    // насыщенность (сильнее ночью, чтобы неон «горел»)
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(luma), c, mix(1.18, 1.32, Night));

    // мягкая S-кривая контраста
    c = clamp(c, 0.0, 1.5);
    c = mix(c, c * c * (3.0 - 2.0 * c), 0.35);

    // тон: днём тёплый, ночью синеватый с пурпурными тенями
    vec3 day = vec3(1.04, 1.0, 0.95);
    vec3 night = vec3(0.92, 0.96, 1.10);
    c *= mix(day, night, Night);
    c += vec3(0.02, 0.0, 0.035) * Night * (1.0 - luma);

    // виньетка
    vec2 p = texCoord - 0.5;
    float v = 1.0 - dot(p, p) * mix(0.55, 0.85, Night);
    c *= v;

    fragColor = vec4(mix(src, c, Strength), 1.0);
}
