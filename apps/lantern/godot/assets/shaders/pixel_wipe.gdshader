shader_type canvas_item;
// 2.0.1: «пиксельное растворение» между разделами. Блоки 12 dp; каждый проявляется/тает мягко (окно soft),
// в случайном порядке с лёгким ходом сверху вниз — движение непрерывное, без ступенек, но «пиксельное».
uniform float progress = 0.0;    // 0 — блоков нет, 1 — закрыто всё
uniform vec2 px = vec2(400.0, 800.0);
uniform float cell = 12.0;       // сторона блока в dp
uniform float soft = 0.28;       // доля хода, за которую проявляется один блок
uniform vec4 tint : source_color = vec4(0.035, 0.035, 0.04, 0.94);
float h21(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
void fragment() {
	vec2 g = floor(UV * px / cell);
	float r = h21(g) * 0.8 + UV.y * 0.2;
	float e0 = r * (1.0 - soft);
	float a = smoothstep(e0, e0 + soft, progress);
	COLOR = vec4(tint.rgb, a * tint.a);
}
