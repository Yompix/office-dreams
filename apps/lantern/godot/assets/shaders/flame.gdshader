shader_type canvas_item;
render_mode blend_mix;
// 2.0.3: пламя LANTERN — единственное цветное и яркое место в монохромном интерфейсе Yompix.
// Время приходит из скрипта (t), а не TIME: так пламя замирает вне экрана и в простое (экран не перерисовывается).
// style: 0 белое · 1 пиксельное · 2 неоновое · 3 призрачное · 4 золотая корона · 5 глитч · 6 радужное
//        7 первые огни · 8 сезон (премиум) · 9 легендарное · 100 аура (рамка вокруг карточки)
uniform float t = 0.0;
uniform int style = 0;
uniform vec4 c_core : source_color = vec4(1.0, 1.0, 1.0, 1.0);
uniform vec4 c_mid : source_color = vec4(1.0, 0.85, 0.55, 1.0);
uniform vec4 c_edge : source_color = vec4(1.0, 0.45, 0.1, 1.0);
uniform float glow = 0.6;        // сила ореола
uniform float intensity = 1.0;   // общая яркость (мелкие значки — тише)
uniform vec2 px = vec2(64.0, 64.0);   // размер в пикселях (для пиксельного стиля и ауры)

float h11(float n) { return fract(sin(n * 127.1) * 43758.5453); }
float h21(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
	vec2 i = floor(p); vec2 f = fract(p);
	f = f * f * (3.0 - 2.0 * f);
	return mix(mix(h21(i), h21(i + vec2(1, 0)), f.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), f.x), f.y);
}
float fbm(vec2 p) {
	float v = 0.0; float a = 0.5;
	for (int i = 0; i < 4; i++) { v += a * vnoise(p); p *= 2.03; a *= 0.5; }
	return v;
}
vec3 hue(float h) { return clamp(abs(fract(h + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0); }

// форма пламени: 1 в сердцевине, 0 снаружи; uv.x -1..1, uv.y 0 (низ) .. 1 (верх)
float flame_shape(vec2 uv, float tt, float wob) {
	float n = fbm(vec2(uv.x * 2.6, uv.y * 3.2 - tt * 2.4));
	float w = (1.0 - uv.y) * 0.36 + 0.03 - (1.0 - smoothstep(0.0, 0.3, uv.y)) * 0.12;     // капля: узкое основание, шире в середине
	float x = uv.x + (n - 0.5) * wob * uv.y + sin(uv.y * 5.0 - tt * 3.0) * 0.05 * uv.y;
	float body = 1.0 - smoothstep(w * 0.55, w, abs(x));
	float top = 1.0 - smoothstep(0.45, 0.9, uv.y + (n - 0.5) * 0.35);
	float bottom = smoothstep(0.0, 0.12, uv.y);
	return clamp(body * top * bottom * (0.62 + n * 0.45), 0.0, 1.0);
}

vec3 ramp(float v) {
	vec3 c = mix(c_edge.rgb * 0.85, c_mid.rgb, smoothstep(0.2, 0.62, v));
	return mix(c, c_core.rgb, smoothstep(0.72, 1.0, v));
}

// искры: n точек поднимаются над пламенем
float embers(vec2 uv, float tt, float n, float sz) {
	float a = 0.0;
	for (int i = 0; i < 8; i++) {
		if (float(i) >= n) break;
		float fi = float(i);
		float life = fract(tt * (0.35 + h11(fi) * 0.4) + h11(fi + 7.0));
		vec2 p = vec2((h11(fi + 3.0) - 0.5) * 0.9 + sin(tt * 2.0 + fi) * 0.08 * life, 0.25 + life * 0.85);
		float d = length((uv - p) * vec2(1.0, 0.8));
		a += (1.0 - smoothstep(0.0, sz, d)) * (1.0 - life);
	}
	return a;
}

// звёздочки-искры короны чиби (4 луча)
float star_sparks(vec2 uv, float tt) {
	float a = 0.0;
	for (int i = 0; i < 5; i++) {
		float fi = float(i);
		float ang = fi * 1.2566 + tt * 0.6;
		vec2 p = vec2(cos(ang) * 0.62, 0.55 + sin(ang) * 0.28);
		vec2 d = abs(uv - p);
		float tw = 0.5 + 0.5 * sin(tt * 5.0 + fi * 2.0);
		a += tw * (max(0.0, 1.0 - (d.x * 26.0 + d.y * 3.5)) + max(0.0, 1.0 - (d.y * 26.0 + d.x * 3.5)));
	}
	return clamp(a, 0.0, 1.5);
}

void fragment() {
	vec2 uv = vec2(UV.x * 2.0 - 1.0, 1.0 - UV.y);
	float tt = t;
	vec3 col = vec3(0.0);
	float a = 0.0;

	if (style == 100) {
		// аура вокруг карточки: светящаяся рамка, по которой бегут языки пламени
		vec2 q = UV * px;
		float dx = min(q.x, px.x - q.x);
		float dy = min(q.y, px.y - q.y);
		float d = min(dx, dy);
		float per = (q.x + q.y) / (px.x + px.y);
		float n = fbm(vec2(per * 18.0 - tt * 1.6, d * 0.12));
		float edge = exp(-d * 0.22) * (0.55 + n * 0.9);
		vec3 c = mix(c_edge.rgb, c_core.rgb, n);
		if (c_mid.a < 0.5) c = hue(per * 2.0 - tt * 0.15) * 1.1;     // радужная
		col = c;
		a = clamp(edge, 0.0, 1.0);
		COLOR = vec4(col, clamp(a * intensity, 0.0, 1.0));
	} else {
		vec2 p = uv;
		float pix = 0.0;
		if (style == 1) {   // 8-битное: крупные пиксели, рывки по кадрам
			vec2 cells = vec2(22.0, 22.0);
			p = (floor(UV * cells) + 0.5) / cells;
			p = vec2(p.x * 2.0 - 1.0, 1.0 - p.y);
			tt = floor(t * 8.0) / 8.0;
			pix = 1.0;
		}
		if (style == 5) {   // глитч: строки сдвигаются рывками
			float band = floor(UV.y * 12.0);
			float j = step(0.82, h21(vec2(band, floor(t * 9.0))));
			p.x += (h21(vec2(band, floor(t * 9.0) + 3.0)) - 0.5) * 0.5 * j;
		}
		float wob = style == 3 ? 1.1 : 0.7;
		float f = flame_shape(p, tt, wob);
		if (pix > 0.5) f = floor(f * 4.0) / 4.0;
		if (style == 2) {   // неон: светится контур, середина почти пустая
			float ring = smoothstep(0.06, 0.18, f) * (1.0 - smoothstep(0.26, 0.42, f));
			col = mix(c_edge.rgb, c_core.rgb, 0.5 + 0.5 * sin(tt * 2.0));
			a = ring;
			col = mix(c_mid.rgb * 0.6, col, ring);
			a = max(a, smoothstep(0.3, 0.9, f) * 0.18);
			float halo = (1.0 - smoothstep(0.0, 0.08, f)) * smoothstep(0.0, 0.02, f);
			float outer = exp(-abs(p.x) * 3.0) * (1.0 - smoothstep(0.1, 0.85, p.y)) * 0.35;
			col = mix(c_mid.rgb, col, a / max(0.001, max(a, outer + halo)));
			a = max(a, outer + halo * 0.6);
		} else if (style == 3) {   // призрак: полупрозрачное, с «хвостами»
			float wisp = fbm(vec2(p.x * 4.0, p.y * 2.0 - tt * 1.2));
			f *= 0.6 + wisp * 0.7;
			col = ramp(f);
			a = smoothstep(0.05, 0.6, f) * 0.8;
		} else if (style == 5) {   // глитч: каналы разъезжаются
			float fr = flame_shape(p + vec2(0.07, 0.0), tt, wob);
			float fb = flame_shape(p - vec2(0.07, 0.0), tt, wob);
			col = ramp(f);
			float ar = smoothstep(0.05, 0.3, fr), ab = smoothstep(0.05, 0.3, fb), af = smoothstep(0.05, 0.3, f);
			col = mix(mix(col, c_edge.rgb, ar * (1.0 - af)), c_core.rgb, ab * (1.0 - af));
			a = max(af, max(ar, ab) * 0.85);
		} else if (style == 6 || style == 9) {   // радуга / легендарное: цвет бежит снизу вверх
			vec3 rb = hue(p.y * 0.8 - tt * 0.25 + p.x * 0.2);
			col = mix(rb, c_core.rgb, smoothstep(0.75, 1.0, f));
			if (style == 9) col = mix(col, ramp(f), 0.5);
			a = smoothstep(0.04, 0.3, f);
		} else {
			col = ramp(f);
			a = smoothstep(0.04, 0.3, f);
		}
		// ореол у основания (мягкий, цветом края)
		float border = smoothstep(0.0, 0.18, min(min(UV.x, 1.0 - UV.x), min(UV.y, 1.0 - UV.y)));
		float g = exp(-length((uv - vec2(0.0, 0.3)) * vec2(1.0, 1.3)) * 3.4) * glow * border;
		float ga = g * 0.45;
		col = mix(c_edge.rgb, col, a / max(0.001, max(a, ga)));
		a = max(a, ga);
		// искры
		float ne = (style == 4 || style == 7 || style == 9) ? 7.0 : (style == 0 ? 3.0 : (style == 8 ? 5.0 : 0.0));
		if (ne > 0.0) {
			float e = clamp(embers(uv, tt, ne, style == 1 ? 0.07 : 0.05), 0.0, 1.0);
			col = mix(col, mix(c_mid.rgb, c_core.rgb, 0.6), e);
			a = max(a, e);
		}
		if (style == 4 || style == 9) {
			float st = clamp(star_sparks(uv, tt), 0.0, 1.0);
			col = mix(col, c_core.rgb, st);
			a = max(a, st);
		}
		COLOR = vec4(col * (0.85 + 0.15 * intensity), clamp(a * intensity, 0.0, 1.0));
	}
}
