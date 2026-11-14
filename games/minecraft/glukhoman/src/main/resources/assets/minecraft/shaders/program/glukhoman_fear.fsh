#version 150
// Глухомань: зерно, виньетка, аберрация и обесцвечивание от страха, волны рядом с существом,
// в кат-сценах — размытие фона (перевод фокуса), лучи света, цветокоррекция и разрыв неба.
uniform sampler2D DiffuseSampler;
in vec2 texCoord;
uniform vec2 OutSize;
uniform float Time, Fear, Near, Strength, Flash, Blur, Rays, RayX, RayY, Tear, TintR, TintG, TintB, Cone, Crush, Vhs, Glitch;
out vec4 fragColor;

float rand(vec2 c) { return fract(sin(dot(c, vec2(12.9898, 78.233))) * 43758.5453); }

vec3 tap(vec2 uv, vec2 d, float ca) {
    return vec3(texture(DiffuseSampler, uv + d * ca).r, texture(DiffuseSampler, uv).g, texture(DiffuseSampler, uv - d * ca).b);
}

void main() {
    vec2 uv = texCoord;
    // ---- кассета: дорожки, рывки, срыв кадра ----
    if (Vhs > 0.01) {
        float row = floor(uv.y * OutSize.y / 2.0);
        // мягкая полоса трекинга: живёт в нижней трети, меняет высоту, тащит строки вбок
        float trk = 0.04 + pow(fract(Time * 0.037), 1.7) * 0.4;
        float bh = 0.02 + 0.03 * rand(vec2(floor(Time * 0.5), 3.0));
        float band = smoothstep(bh, 0.0, abs(uv.y - trk)) * (0.4 + 0.6 * Glitch + 0.25);
        uv.x += (rand(vec2(row, floor(Time * 30.0))) - 0.3) * 0.012 * band * Vhs;
        // «переключение головок»: нижние строки кадра всегда дёргаются
        float hs = smoothstep(0.035, 0.0, uv.y);
        uv.x += (0.012 + rand(vec2(row, floor(Time * 25.0))) * 0.02) * hs * Vhs;
        // срыв строк при близости твари / низком рассудке
        float tearOn = step(1.0 - Glitch * 0.35, rand(vec2(floor(uv.y * 40.0), floor(Time * 12.0))));
        uv.x += tearOn * (rand(vec2(row, Time)) - 0.5) * 0.08 * Glitch;
        // редкий «прокат» кадра по вертикали
        float roll = step(0.985 - Glitch * 0.05, rand(vec2(floor(Time * 2.0), 7.0)));
        uv.y = fract(uv.y + roll * fract(Time * 3.0) * 0.3 * (0.3 + Glitch));
        // низкое разрешение плёнки
        vec2 res = OutSize * mix(1.0, 0.42, Vhs);
        uv = (floor(uv * res) + 0.5) / res;
    }
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
    // конус фонаря: вне пятна света — почти ничего не видно
    if (Cone > 0.01) {
        vec2 e = (texCoord - vec2(0.5, 0.47)) * vec2(1.0, 1.35);
        float spot = smoothstep(0.48, 0.12, length(e));
        col *= mix(1.0, 0.08 + 0.92 * spot, Cone);
    }
    // «киношная» тьма: продавленные чёрные, холодная бирюза, мало цвета
    if (Crush > 0.01) {
        float lum = dot(col, vec3(0.299, 0.587, 0.114));
        col = mix(col, vec3(lum), 0.45 * Crush);
        col = max(col - 0.035 * Crush, 0.0) / (1.0 - 0.035 * Crush);
        col = pow(col, vec3(1.0 + 0.35 * Crush));
        col *= mix(vec3(1.0), vec3(0.86, 1.0, 1.04), Crush);
    }
    if (Vhs > 0.01) {
        // цветовой «расплыв» по горизонтали и сдвиг хромы
        vec2 px = vec2(1.0 / OutSize.x, 0.0);
        vec3 bleed = vec3(0.0);
        for (int i = -3; i <= 3; i++) bleed += texture(DiffuseSampler, uv + px * float(i) * 2.5).rgb;
        bleed /= 7.0;
        float y = dot(col, vec3(0.299, 0.587, 0.114));
        vec3 chroma = bleed - dot(bleed, vec3(0.299, 0.587, 0.114));
        col = mix(col, vec3(y) + chroma * 0.85 + vec3(texture(DiffuseSampler, uv + px * 3.0).r - bleed.r, 0.0, texture(DiffuseSampler, uv - px * 3.0).b - bleed.b) * 0.35, Vhs);
        // строчная развёртка и плёночный шум
        if (mod(gl_FragCoord.y, 2.0) < 1.0) col *= 1.0 - 0.07 * Vhs;
        // шум в полосе трекинга и внизу кадра: редкие яркие мазки по горизонтали, цветная кайма, мягкие края
        float trk2 = 0.04 + pow(fract(Time * 0.037), 1.7) * 0.4;
        float bh2 = 0.02 + 0.03 * rand(vec2(floor(Time * 0.5), 3.0));
        float dz = (uv.y - trk2) / bh2;
        float zone = exp(-dz * dz * 2.0) * (0.45 + 0.7 * Glitch) + smoothstep(0.03, 0.0, uv.y) * 0.7;
        float line = floor(uv.y * OutSize.y / 2.0);
        float start = rand(vec2(line, floor(Time * 20.0)));
        float len = 0.05 + 0.25 * rand(vec2(line + 3.0, floor(Time * 20.0)));
        float sx = uv.x - start;
        float streak = step(0.0, sx) * step(sx, len) * step(0.82, rand(vec2(line * 0.37, floor(Time * 20.0) + 1.0)));
        float nz = streak * zone * (1.0 - sx / max(len, 0.001));
        col += vec3(0.95, 0.85, 1.0) * nz * 0.55 * Vhs;
        col += vec3(0.06, -0.02, 0.07) * zone * Vhs;
        // выпадение: целая полоса серого «снега»
        float drop = step(1.0 - Glitch * 0.025, rand(vec2(floor(uv.y * 18.0), floor(Time * 8.0)))) * step(0.5, Vhs);
        col = mix(col, vec3(rand(uv * Time)) * 0.6, drop * Vhs);
        col *= 1.0 - 0.12 * Vhs;
    }
    // зерно плёнки и виньетка
    float g = rand(uv * OutSize + fract(Time * 7.13) * 91.0) - 0.5;
    col += g * (0.03 + Fear * 0.05) * s;
    col *= 1.0 - smoothstep(0.35, 0.95, r) * (0.3 + Fear * 0.4) * s;
    col = mix(col, vec3(1.0, 0.96, 0.95), Flash);
    fragColor = vec4(col, 1.0);
}
