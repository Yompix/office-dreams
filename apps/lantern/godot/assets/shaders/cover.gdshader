shader_type canvas_item;
// Обложка игры LANTERN: маленький ночной/закатный пейзаж в НАШЕМ стиле — гладкие силуэты (холмы, горы, море,
// пустыня, лес, ночной город), небо-градиент, солнце или луна, звёзды. Каждая комната — свой пейзаж (seed),
// палитра — от режима игры. Никаких блоков, пикселей и чужой графики. Дёшево: пара десятков синусов на пиксель.
uniform vec3 sky_top : source_color = vec3(0.08, 0.10, 0.25);
uniform vec3 sky_bot : source_color = vec3(0.78, 0.45, 0.23);
uniform vec3 c_far : source_color = vec3(0.42, 0.29, 0.48);
uniform vec3 c_mid : source_color = vec3(0.23, 0.17, 0.36);
uniform vec3 c_near : source_color = vec3(0.11, 0.09, 0.22);
uniform vec3 c_sun : source_color = vec3(1.0, 0.81, 0.54);
uniform float seed = 0.0;
uniform int kind = 0;            // 0 холмы · 1 горы · 2 море · 3 пустыня · 4 лес · 5 ночной город
uniform vec2 px = vec2(100.0, 100.0);
uniform float radius = 12.0;
uniform float shade = 0.0;       // затемнение снизу под текст (большая шапка экрана игры)
uniform float fade = 0.0;        // низ растворяется в фоне (иллюстрации во всю ширину)

float h1(float n) { return fract(sin(n * 127.1 + seed * 13.7) * 43758.5453); }
float h2(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7)) + seed * 7.3) * 43758.5453); }
float tri(float x) { return abs(fract(x) - 0.5) * 2.0; }

// высота силуэта слоя i в точке x (0..1 — доля высоты картинки)
float ridge(float x, float i) {
	float p = h1(i * 3.1) * 6.283;
	float q = h1(i * 5.7 + 1.0) * 6.283;
	float smooth_h = sin(x * (1.3 + i * 0.5) + p) * 0.55 + sin(x * (2.9 + i * 0.8) + q) * 0.3 + sin(x * 6.1 + p * 1.7) * 0.15;
	if (kind == 1) {            // горы: острые вершины
		float m = tri(x * (0.45 + i * 0.12) + h1(i)) * 0.8 + tri(x * (1.1 + i * 0.3) + h1(i + 2.0)) * 0.35 + tri(x * 2.7 + p) * 0.1;
		return (m - 0.55) * (1.25 - i * 0.25);
	}
	if (kind == 3) {            // пустыня: длинные пологие дюны с гребнем
		float d = sin(x * (0.9 + i * 0.35) + p);
		return (d * 0.5 + 0.5) * (d * 0.5 + 0.5) * 0.9 - 0.35 + sin(x * 3.3 + q) * 0.06;
	}
	if (kind == 4) {            // лес: холмы с верхушками елей
		float trees = tri(x * (7.0 + i * 4.0) + h1(i + 4.0)) * (0.22 - i * 0.03);
		float gaps = 0.6 + 0.4 * sin(x * 2.3 + q);
		return smooth_h * 0.5 + trees * gaps;
	}
	if (kind == 5) {            // ночной город: дома разной высоты
		float w = 0.11 + i * 0.05;
		float cell = floor(x / w);
		float hh = h1(cell + i * 17.0);
		float gap = step(0.06, fract(x / w));   // тонкая щель между домами
		return (hh * hh * 1.5 - 0.55 + smooth_h * 0.15) * (1.2 - i * 0.3) * gap - (1.0 - gap) * 0.9;
	}
	return smooth_h;            // холмы
}

void fragment() {
	vec2 uv = UV;
	float asp = px.x / px.y;
	float x = uv.x * asp * 2.2 + h1(9.0) * 10.0;
	float y = uv.y;
	float aa = 1.5 / px.y;
	bool sea = kind == 2;
	float horizon = sea ? 0.60 : 0.64;
	// небо: сверху тёмное, к горизонту — цвет режима, у самого горизонта — отсвет солнца
	float sk = smoothstep(0.0, horizon, y);
	vec3 col = mix(sky_top, sky_bot, sk * sk);
	vec2 sun = vec2((0.22 + h1(2.0) * 0.56) * asp, horizon - 0.12 - h1(3.0) * (sea ? 0.10 : 0.30));
	if (kind == 3) sun.y = horizon - 0.10 - h1(3.0) * 0.12;
	vec2 sp = vec2(uv.x * asp, y) - sun;
	float sd = length(sp);
	float sr = (kind == 3 ? 0.16 : 0.085) + h1(4.0) * 0.03;
	col = mix(col, c_sun, exp(-sd * sd * 9.0) * 0.55);                 // ореол
	col += c_sun * exp(-abs(y - horizon) * 9.0) * 0.14;                // отсвет у горизонта
	// звёзды: редкие точки, гаснут к горизонту
	vec2 g = vec2(uv.x * asp, y) * 26.0;
	vec2 gi = floor(g);
	float st = h2(gi);
	vec2 sc = fract(g) - 0.5 - (vec2(h2(gi + 3.0), h2(gi + 7.0)) - 0.5) * 0.6;
	float star = step(0.90, st) * smoothstep(0.16 + st * 0.08, 0.0, length(sc)) * (1.0 - sk) * (0.5 + st * 0.5);
	col += vec3(1.0, 0.97, 0.9) * star * 0.9;
	// солнце или луна-серп (у каждой третьей комнаты)
	float disc = smoothstep(sr + aa, sr - aa, sd);
	if (h1(5.0) > 0.66 || kind == 5) {
		float cut = smoothstep(sr * 0.92 + aa, sr * 0.92 - aa, length(sp - vec2(sr * 0.55, -sr * 0.25)));
		disc *= 1.0 - cut;
	}
	col = mix(col, mix(c_sun, vec3(1.0), 0.35), disc);
	if (sea) {
		// море: ровный горизонт, лунная дорожка из светлых полос, дальний остров
		float ih = max(0.0, ridge(x * 0.6, 0.0)) * 0.10;
		float isl = horizon - ih;
		col = mix(col, c_far, smoothstep(isl - aa, isl + aa, y) * (1.0 - smoothstep(horizon - aa, horizon + aa, y)) * step(0.004, ih));
		float w = smoothstep(horizon - aa, horizon + aa, y);
		float dy = max(0.0, (y - horizon) / (1.0 - horizon));     // над горизонтом воды нет (и деления на ноль тоже)
		vec3 water = mix(c_mid, c_near, dy);
		float path = exp(-abs(uv.x * asp - sun.x) / (0.03 + dy * 0.22)) * (0.45 + 0.55 * sin(dy * dy * 90.0 + uv.x * 14.0 + seed));
		water += c_sun * max(path, 0.0) * 0.55 * (1.0 - dy * 0.5);
		water += c_sun * 0.05 * sin(dy * 60.0 + x * 3.0) * (1.0 - dy);
		col = mix(col, water, w);
	} else {
		// три слоя силуэтов: дальний светлее (воздушная перспектива), ближний — самый тёмный
		for (int i = 0; i < 3; i++) {
			float fi = float(i);
			float base = horizon + fi * 0.115 + (h1(fi + 20.0) - 0.5) * 0.04;
			float amp = 0.15 - fi * 0.025;
			float edge = base - ridge(x * (1.0 + fi * 0.35) + fi * 3.7, fi) * amp;
			vec3 lc = i == 0 ? c_far : (i == 1 ? c_mid : c_near);
			lc = mix(lc, lc * 0.72, smoothstep(edge, 1.0, y));          // слой темнеет книзу
			if (i == 0) lc = mix(lc, sky_bot, 0.28);                    // дымка у дальнего
			float m = smoothstep(edge - aa, edge + aa, y);
			if (kind == 5 && m > 0.0) {                                 // окна в домах
				vec2 wg = vec2(x * (1.0 + fi * 0.35) * 46.0, (y - edge) * 60.0 / (1.0 + fi * 0.2));
				float lit = step(0.80, h2(floor(wg) + fi * 31.0)) * step(0.25, fract(wg.x)) * step(0.3, fract(wg.y)) * step(1.0, wg.y);
				lc = mix(lc, c_sun, lit * 0.75 * (1.0 - fi * 0.25));
			}
			col = mix(col, lc, m);
		}
	}
	col = mix(col, vec3(0.03, 0.035, 0.08), shade * smoothstep(0.30, 1.0, y) * 0.86);
	// скруглённые углы
	vec2 q = abs(uv * px - px * 0.5) - (px * 0.5 - vec2(radius));
	float d = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
	float a = 1.0 - smoothstep(-0.75, 0.75, d);
	// тонкая светлая кромка сверху — как у стекла
	col += vec3(1.0) * smoothstep(-2.0, -0.4, d) * (1.0 - uv.y) * 0.22 * a;
	a *= 1.0 - fade * smoothstep(0.55, 1.0, uv.y);
	COLOR = vec4(col, a * COLOR.a);
}
