class_name PostFX
extends CanvasLayer
## Обработка кадра поверх 3D — перенос PB.comp (scenes.js) и наложений кадра (engine.js frame): «ночная» плёнка (тени глубже,
## холодные тени и тёплый свет, чуть меньше цвета), камера наблюдения (рыбий глаз, дрожь строк, мягкая оптика, зелёный монохром,
## строки развёртки), ИК-режим (ч/б), виньетка, зерно; эффекты кадра fx: static, lamp, flicker, dawn, dark, light, snow, чёрная склейка.
## Тонмаппинг (ACES) и экспозицию делает Environment Godot, здесь — то, что старая игра делала после них.

const SHADER := """
shader_type canvas_item;
uniform sampler2D screen : hint_screen_texture, filter_linear;
uniform float t = 0.0;
uniform float cctv = 0.0;
uniform float ir = 0.0;
uniform float vhs = 0.7;
uniform float hq = 1.0;
uniform float scare = 0.0;
uniform float grain_k = 0.07;      // зерно наложением (grain() старой игры)
uniform float white_k = 0.0;       // белая засветка (static, flicker, snow)
uniform float black_k = 0.0;       // затемнение (lamp, dark, light, fin, чёрная склейка)
uniform float dawn = 0.0;          // рассвет: тёплый мягкий свет
uniform float dawn_p = 0.0;
uniform float tear = 0.0;          // перемотка записи: строки рвутся вбок
uniform float run_side = 0.0;      // бег: свет гаснет слева (-1) или справа (1)
float h(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
vec3 overlay(vec3 c, float n) { return mix(2.0 * c * n, 1.0 - 2.0 * (1.0 - c) * (1.0 - n), step(0.5, c)); }
vec3 soft(vec3 c, vec3 s) { return mix(2.0 * c * s + c * c * (1.0 - 2.0 * s), sqrt(max(c, 0.0)) * (2.0 * s - 1.0) + 2.0 * c * (1.0 - s), step(0.5, s)); }
void fragment() {
	vec2 px = SCREEN_PIXEL_SIZE;
	float asp = px.y / px.x;
	vec2 uv = SCREEN_UV, d = uv - 0.5;
	float r = length(d * vec2(asp, 1.0)) / max(asp, 1.0) * 1.6;
	uv += d * dot(d, d) * (0.02 + 0.12 * cctv);                                   // объектив (у камер наблюдения — «рыбий глаз»)
	float row = floor(uv.y * 540.0), fr = floor(t * 24.0);
	uv.x += (h(vec2(row, fr)) - 0.5) * vhs * cctv * 0.002;                         // запись с камеры чуть дрожит строками
	uv += d * scare * 0.05 * (h(vec2(fr, 3.0)) - 0.5);
	float band = floor(uv.y * 36.0);
	float tb = step(1.0 - 0.35 * tear, h(vec2(band, fr)));
	uv.x += tb * (h(vec2(band, fr + 7.0)) - 0.5) * 0.09 * tear;
	float ca = (mix(0.0012, 0.0006, hq) + 0.012 * scare) * (0.25 + r * r * 1.4);   // цветная кайма объектива
	vec3 c = vec3(texture(screen, uv + d * ca).r, texture(screen, uv).g, texture(screen, uv - d * ca).b);
	if (cctv > 0.5) {   // дешёвая оптика и сжатие: картинка мягче
		vec2 q = 1.6 / vec2(960.0, 540.0);
		c = mix(c, (texture(screen, uv + q).rgb + texture(screen, uv - q).rgb + texture(screen, uv + vec2(q.x, -q.y)).rgb + texture(screen, uv - vec2(q.x, -q.y)).rgb) * 0.25, mix(0.6, 0.15, hq));
	}
	if (scare > 0.02) {   // скример: рывок к центру смазывает картинку, свет режет красным
		vec3 z = c;
		for (int i = 1; i < 7; i++) z += texture(screen, uv - d * float(i) * 0.018 * scare).rgb;
		c = mix(c, z / 7.0, min(1.0, scare * 1.3));
		c *= vec3(1.0 + 0.5 * scare, 1.0 - 0.25 * scare, 1.0 - 0.3 * scare);
	}
	c = pow(max(c, vec3(0.0)), vec3(1.15)) * 0.988 + 0.012;                          // ночь: тени глубже, но чёрное — не «дыра»
	float l = dot(c, vec3(0.299, 0.587, 0.114));
	float sk = smoothstep(0.012, 0.07, c.r - c.g) * smoothstep(-0.01, 0.04, c.g - c.b) * smoothstep(0.03, 0.14, l);   // тёплые тона (кожа, дерево) не зеленеют
	c = mix(c * mix(vec3(0.86, 1.0, 1.07), vec3(0.97, 0.99, 1.0), sk) + vec3(0.0, 0.008, 0.018) * (1.0 - 0.7 * sk), c * vec3(1.06, 0.99, 0.9), smoothstep(0.18, 0.7, l));
	c = mix(vec3(l), c, 0.88 + 0.07 * sk);
	c = mix(c, vec3(l) * vec3(0.86, 1.0, 0.9), cctv * mix(0.55, 0.38, hq)) + max(c - 0.6, 0.0) * cctv * 0.8;
	c = mix(c, vec3(pow(max(l, 0.0), 0.78) * 1.2) * vec3(0.9, 1.0, 0.94), ir);      // ИК-режим: ч/б, светлее у объектива
	c *= 1.0 - cctv * mix(0.09, 0.035, hq) * step(1.0, mod(floor(FRAGCOORD.y / 2.0), 2.0));
	c *= 1.0 - smoothstep(0.55, 1.5, r) * 0.6;                                         // виньетка
	c += (h(FRAGCOORD.xy + fract(t * 7.13) * 731.0) - 0.5) * (0.03 * (0.45 + 2.2 * l * (1.0 - l)) + cctv * mix(0.05, 0.022, hq) + vhs * 0.015);
	if (dawn > 0.0) {   // рассвет: тёплый мягкий свет и коричневая дымка
		c = mix(c, soft(clamp(c, 0.0, 1.0), vec3(1.0, 0.627, 0.314)), 0.75 * dawn);
		c = 1.0 - (1.0 - c) * (1.0 - vec3(0.275, 0.137, 0.04) * (0.15 + 0.15 * dawn_p) * dawn);
	}
	c = mix(c, overlay(clamp(c, 0.0, 1.0), h(FRAGCOORD.xy * 0.73 + fract(t * 3.7) * 977.0)), grain_k);
	c = mix(c, vec3(1.0), white_k + tb * tear * 0.12 * step(0.8, fract(uv.y * 36.0)));
	if (run_side != 0.0) {   // свет гаснет с этой стороны: от края к середине
		float s = (run_side < 0.0 ? SCREEN_UV.x : 1.0 - SCREEN_UV.x) / 0.5;
		float a = s < 0.7 ? mix(0.97, 0.75, s / 0.7) : mix(0.75, 0.0, clamp((s - 0.7) / 0.3, 0.0, 1.0));
		c *= 1.0 - a;
	}
	c *= 1.0 - black_k;
	if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) c = vec3(0.0);
	COLOR = vec4(c, 1.0);
}
"""

var rect: ColorRect
var mat: ShaderMaterial


func _init() -> void:
	layer = 0
	rect = ColorRect.new()
	rect.set_anchors_preset(Control.PRESET_FULL_RECT)
	rect.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var sh := Shader.new()
	sh.code = SHADER
	mat = ShaderMaterial.new()
	mat.shader = sh
	rect.material = mat
	add_child(rect)


func set_p(k: String, v) -> void:
	mat.set_shader_parameter(k, v)


## Эффекты кадра (engine.js frame): fx кадра, время кадра t, всё время T, доля кадра p (0…1), fin — финал (темнеет к концу).
func beat_fx(fx: String, t: float, T: float, p: float, beat: Dictionary, bdur: float) -> void:
	var white := 0.0
	var black := 0.0
	var gr := 0.07 if fx == "" or fx == "rain" else 0.0
	if fx == "static" and t > 1.2:
		gr = 0.55
		white = 0.04 + 0.04 * sin(t * 40.0)
	if fx == "lamp" and fmod(T * 3.1, 9.0) < 0.25:
		black = 0.35
	if fx == "flicker" and fmod(T, 4.0) < 0.5:
		white = 0.04 * absf(sin(T * 31.0))
	if fx == "dark":
		black = maxf(black, p * 0.85)
	if fx == "dawn":
		gr = 0.06
	if beat.has("light"):
		black = maxf(black, 0.35 * (1.0 - minf(1.0, t / 6.0)))
	if beat.get("fin", false):
		black = maxf(black, minf(1.0, 0.1 + 0.22 * minf(1.0, t / 2.5) + 0.7 * maxf(0.0, (t - bdur + 1.4) / 1.4)))
	for s in beat.get("snow", []):   # сигнал камеры рвётся помехами
		if t >= float(s[0]) and t < float(s[1]):
			gr = 0.95
			white = 0.08 + 0.1 * randf()
	if beat.get("black") != null and t >= float(beat.black):
		black = 1.0
	set_p("t", T)
	set_p("grain_k", gr + (0.12 if fx == "cctv" else 0.0))
	set_p("white_k", clampf(white, 0.0, 1.0))
	set_p("black_k", clampf(black, 0.0, 1.0))
	set_p("dawn", 1.0 if fx == "dawn" else 0.0)
	set_p("dawn_p", p)
