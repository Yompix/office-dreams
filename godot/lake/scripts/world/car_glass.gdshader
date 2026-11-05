shader_type spatial;
// Стёкла седана (car.gd, сетка «glass» и стёкла вырезанных дверей). Нормали модели смотрят наружу, поэтому:
// снаружи (лицевая сторона) — тонировка и отражение по Френелю: под острым углом стекло почти зеркальное, как настоящее,
// и сзади-сверху машина больше не похожа на кабриолет; заднее и задние боковые (за стойкой B) темнее лобового —
// заводская тонировка; изнутри (обратная сторона) — прежняя лёгкая вуаль, кадры в салоне не меняются;
// дальние стёкла, видные сквозь ближние снаружи, — почти прозрачные (не белят ближнее стекло).
// frost 0…1 — иней (гл. 2, мотор заглушён): одинаково с обеих сторон, как раньше.
render_mode blend_mix, cull_disabled, depth_draw_opaque, specular_schlick_ggx;

uniform vec4 in_color : source_color = vec4(0.45, 0.52, 0.6, 0.16);
uniform float in_rough = 0.05;
uniform vec3 out_color : source_color = vec3(0.025, 0.03, 0.036);
uniform float out_front = 0.30;     // лобовое и передние боковые
uniform float out_rear = 0.66;      // задние боковые и заднее (на ПК салон светлее — тонировка плотнее)
uniform float rear_from = 0.40;     // граница по оси машины (вперёд — −Z): под стойкой B (z 0,33…0,61)
uniform float rear_to = 0.55;
uniform float fresnel_pow = 4.0;
uniform float fresnel_max = 0.92;
uniform float out_far = 0.06;
uniform float frost : hint_range(0.0, 1.0) = 0.0;
uniform vec4 frost_color : source_color = vec4(0.8, 0.84, 0.9, 0.6);
uniform float frost_rough = 0.75;

varying vec3 lp;
varying float cam_in;

void vertex() {
	lp = VERTEX;
	vec3 c = (inverse(MODEL_MATRIX) * vec4(CAMERA_POSITION_WORLD, 1.0)).xyz;   // камера в осях машины
	cam_in = (abs(c.x) < 0.95 && c.y > 0.2 && c.y < 1.62 && c.z > -0.75 && c.z < 1.8) ? 1.0 : 0.0;
}

void fragment() {
	vec3 col;
	float a;
	float r;
	if (FRONT_FACING) {
		float base = mix(out_front, out_rear, smoothstep(rear_from, rear_to, lp.z));
		float f = pow(1.0 - clamp(dot(NORMAL, VIEW), 0.0, 1.0), fresnel_pow);
		col = out_color;
		a = mix(base, fresnel_max, f);
		r = 0.03;
	} else {
		col = in_color.rgb;
		a = mix(out_far, in_color.a, cam_in);
		r = in_rough;
	}
	ALBEDO = mix(col, frost_color.rgb, frost);
	ALPHA = mix(a, frost_color.a, frost);
	ROUGHNESS = mix(r, frost_rough, frost);
	SPECULAR = 0.9;
	METALLIC = 0.0;
}
