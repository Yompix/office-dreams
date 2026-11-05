#version 150

// LifeCraft: лёгкая «киношная» графика одним проходом — для слабых ноутбуков.
// Сочные цвета, мягкий контраст, золотой закат, синяя ночь с неоновым свечением,
// холодный пасмурный дождь, лёгкая резкость, кинозерно ночью и виньетка.
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Night;
uniform float Sunset;
uniform float Rain;
uniform float Quality;
uniform float Time;
uniform float Strength;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

vec3 bright(vec2 uv) {
    vec3 c = texture(DiffuseSampler, uv).rgb;
    float l = max(c.r, max(c.g, c.b));
    return c * smoothstep(0.62, 1.0, l);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec3 src = texture(DiffuseSampler, texCoord).rgb;

    // лёгкая резкость: 4 соседа (почти бесплатно, картинка чётче на низком разрешении)
    vec3 n = texture(DiffuseSampler, texCoord + vec2(oneTexel.x, 0.0)).rgb
           + texture(DiffuseSampler, texCoord - vec2(oneTexel.x, 0.0)).rgb
           + texture(DiffuseSampler, texCoord + vec2(0.0, oneTexel.y)).rgb
           + texture(DiffuseSampler, texCoord - vec2(0.0, oneTexel.y)).rgb;
    vec3 c = src + (src - n * 0.25) * 0.35;

    // свечение огней и неона — только в режиме «Красивая»
    if (Quality > 0.5) {
        vec3 glow = vec3(0.0);
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.785398;
            vec2 d = vec2(cos(a), sin(a)) * oneTexel;
            glow += bright(texCoord + d * 3.0);
            glow += bright(texCoord + d * 7.0) * 0.6;
        }
        glow /= 12.8;
        c += glow * mix(0.35, 0.95, Night) * (1.0 + Sunset * 0.5);
    }

    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    // насыщенность: ночью неон ярче, в дождь — приглушённо
    c = mix(vec3(luma), c, mix(1.18, 1.32, Night) - Rain * 0.3);

    // мягкая S-кривая контраста
    c = clamp(c, 0.0, 1.5);
    c = mix(c, c * c * (3.0 - 2.0 * c), 0.35);

    // тон: тёплый день, золотой закат (небо у горизонта светится), синяя ночь, серый дождь
    vec3 day = vec3(1.04, 1.0, 0.95);
    vec3 night = vec3(0.92, 0.96, 1.10);
    c *= mix(day, night, Night);
    c *= mix(vec3(1.0), vec3(1.14, 0.98, 0.84), Sunset * 0.7);
    c += vec3(0.10, 0.04, 0.0) * Sunset * smoothstep(0.35, 1.0, texCoord.y);
    c += vec3(0.02, 0.0, 0.035) * Night * (1.0 - luma);
    c = mix(c, c * vec3(0.9, 0.95, 1.02) * 0.92, Rain * 0.6);

    // кинозерно ночью — чуть-чуть
    c += (hash(texCoord * InSize + fract(Time) * 91.0) - 0.5) * 0.025 * Night;

    // виньетка
    vec2 p = texCoord - 0.5;
    float v = 1.0 - dot(p, p) * mix(0.55, 0.85, max(Night, Rain * 0.7));
    c *= v;

    fragColor = vec4(mix(src, c, Strength), 1.0);
}
