shader_type canvas_item;
// LANTERN 2.0 — фон в стиле Yompix Games: почти чёрная стена из пиксельных блоков разной серости (блоки изредка
// «перещёлкиваются»), мягкий свет в центре, пиксельные облака снизу медленно плывут, поверх — плёночное зерно.
// Считается в маленьком SubViewport (1/4 стороны экрана) и растягивается без сглаживания — пиксели остаются чёткими.
// Время (t) двигает приложение (30 раз в секунду, на простое — стоит). Цвета только серые.
uniform float t = 0.0;
uniform vec2 shift = vec2(0.0);
uniform vec2 px = vec2(400.0, 800.0);     // размер экрана в dp
uniform float cells = 22.0;               // блоков по ширине
uniform float warm = 1.0;                 // сила света в центре (экран знакомства — ярче)

float h21(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
	vec2 i = floor(p), f = fract(p);
	f = f * f * (3.0 - 2.0 * f);
	return mix(mix(h21(i), h21(i + vec2(1, 0)), f.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), f.x), f.y);
}

void fragment() {
	vec2 uv = UV;
	float asp = px.x / px.y;
	// --- стена из блоков: каждый блок своей серости, раз в несколько секунд часть блоков меняется
	vec2 g = vec2(uv.x, (uv.y + shift.y) / asp) * cells;
	vec2 gi = floor(g);
	float epoch = floor(t * 0.35 + h21(gi) * 7.0);
	float v = h21(gi + epoch * 13.1);
	float blk = 0.035 + 0.075 * v * v;
	// мелкие «осколки» внутри крупных блоков (как на аватаре)
	vec2 g2 = floor(g * 2.0);
	blk += step(0.86, h21(g2 + 3.7)) * 0.03;
	vec3 c = vec3(blk);
	// --- свет: мягкое пятно в верхней трети (там, где на аватаре фигура) + виньетка
	vec2 lp = (uv - vec2(0.5, 0.30)) * vec2(max(asp, 0.6), 1.0);
	c += vec3(0.11) * exp(-dot(lp, lp) * 4.5) * warm;
	c *= 1.0 - 0.55 * smoothstep(0.35, 1.05, length((uv - 0.5) * vec2(1.0, 1.25)));
	// --- пиксельные облака снизу: шум, квантованный по сетке, плывёт вбок
	vec2 cg = floor(vec2(uv.x * cells * 2.0, uv.y * cells * 2.0 / asp));
	float cx = cg.x / (cells * 2.0);
	float cy = cg.y / (cells * 2.0 / asp);
	// верхний край облаков — «бугристая» линия из двух слоёв шума, по ступенькам сетки
	float top_y = 0.86 - 0.07 * noise(vec2(cx * 5.0 + t * 0.02 + shift.x, 1.3)) - 0.05 * noise(vec2(cx * 11.0 - t * 0.013, 7.1));
	float cloud = step(top_y, cy);
	float shade = 0.17 + 0.12 * noise(vec2(cg * 0.45)) + 0.08 * step(cy, top_y + 0.02);   // тёмные: текст поверх читается
	c = mix(c, vec3(shade), cloud * 0.9);
	// и тонкая «дымка» облаков сверху справа (как на заставке)
	float n2 = noise(vec2(cx * 4.0 - t * 0.012, cy * 4.0 + 9.0));
	float top = step(0.70, n2 * (1.0 - smoothstep(0.05, 0.28, cy)) * 1.6 * smoothstep(0.55, 0.95, cx));
	c = mix(c, vec3(0.42), top * 0.5);
	// --- зерно (меняется со временем — «живая» плёнка)
	float gr = h21(floor(FRAGCOORD.xy) + floor(t * 12.0) * vec2(7.3, 3.1));
	c += (gr - 0.5) * 0.07;
	COLOR = vec4(clamp(c, 0.0, 1.0), 1.0);
}
