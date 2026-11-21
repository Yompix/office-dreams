shader_type canvas_item;
// 2.0: переход «пиксельное растворение» между разделами: экран закрывают чёрные блоки и тают в случайном порядке.
uniform float progress = 1.0;    // 1 — всё закрыто, 0 — блоков нет
uniform vec2 px = vec2(400.0, 800.0);
uniform float cell = 22.0;       // сторона блока в dp
float h21(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
void fragment() {
	vec2 g = floor(UV * px / cell);
	float r = h21(g) * 0.75 + (UV.y) * 0.25;   // чуть сверху вниз
	float on = step(r, progress * 1.02 - 0.01);
	COLOR = vec4(0.02, 0.02, 0.02, on);
}
