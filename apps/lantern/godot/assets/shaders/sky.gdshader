shader_type canvas_item;
// Живой фон LANTERN: ночное небо — индиго сверху → почти чёрное внизу, слабая лента северного сияния (три синуса),
// редкие звёзды, тёплое пятно света от «фонаря» в левом верхнем углу. Время (t) двигает приложение — 30 раз в секунду,
// а на простое не двигает вовсе: кадр замирает, экран не перерисовывается (батарея).
uniform float t = 0.0;
uniform vec2 shift = vec2(0.0);      // параллакс: прокрутка и наклон телефона
uniform vec3 accent : source_color = vec3(1.0, 0.71, 0.28);
uniform vec2 px = vec2(400.0, 800.0);
uniform float warm = 1.0;            // сила света фонаря

void fragment() {
	vec2 uv = UV;
	float asp = px.x / px.y;
	float y = uv.y;
	vec3 top = vec3(0.092, 0.104, 0.250);
	vec3 mid = vec3(0.040, 0.047, 0.115);
	vec3 bot = vec3(0.018, 0.021, 0.043);
	vec3 c = mix(top, mid, smoothstep(0.0, 0.5, y));
	c = mix(c, bot, smoothstep(0.42, 1.0, y));
	float x = uv.x * max(asp, 0.5) * 2.0 + shift.x;
	// лента сияния
	float by = 0.27 + 0.055 * sin(x * 2.1 + t * 0.21) + 0.03 * sin(x * 4.7 - t * 0.33) + shift.y;
	float d = y - by;
	float fold = 0.55 + 0.45 * sin(x * 3.3 + t * 0.17 + sin(x * 1.3 - t * 0.11) * 1.5);
	float band = exp(-d * d * (d < 0.0 ? 70.0 : 22.0)) * fold;
	vec3 aur = mix(vec3(0.20, 0.78, 0.66), vec3(0.52, 0.38, 1.0), 0.5 + 0.5 * sin(x * 1.7 + t * 0.07));
	c += aur * band * 0.15;
	// вторая, совсем слабая туманность ниже и правее
	float n2 = exp(-pow((y - 0.72 - 0.05 * sin(x * 1.4 + t * 0.09)) * 3.2, 2.0)) * (0.5 + 0.5 * sin(x * 0.9 - t * 0.05 + 2.0));
	c += vec3(0.30, 0.22, 0.62) * n2 * 0.07;
	// звёзды
	vec2 g = vec2(uv.x * asp, y + shift.y * 0.5) * 26.0;
	vec2 gi = floor(g);
	float h = fract(sin(dot(gi, vec2(127.1, 311.7))) * 43758.5453);
	vec2 off = vec2(fract(h * 17.3), fract(h * 41.7)) - 0.5;
	float star = step(0.955, h) * smoothstep(0.16, 0.0, length(fract(g) - 0.5 - off * 0.6));
	c += vec3(0.9, 0.92, 1.0) * star * (0.30 + 0.25 * sin(t * (0.6 + h) + h * 40.0)) * (1.0 - smoothstep(0.05, 0.55, y));
	// свет фонаря: тёплое пятно сверху слева
	vec2 lp = (uv - vec2(0.06, -0.03)) * vec2(max(asp, 0.6), 1.0);
	float l = length(lp);
	c += accent * (exp(-l * l * 5.5) * 0.23 + exp(-l * 2.4) * 0.05) * warm * (0.94 + 0.06 * sin(t * 1.3));
	// лёгкий шум против «ступенек» на градиенте
	c += (fract(sin(dot(FRAGCOORD.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) * (2.2 / 255.0);
	COLOR = vec4(c, 1.0);
}
