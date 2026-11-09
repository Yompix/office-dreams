class_name Beams
extends Node3D
## Лучи ламп и пыль в них — перенос cineLoc / cineTick (scenes.js). Под каждой потолочной лампой конус света: цвет складывается
## с кадром, края мягкие, у лампы ярче, внутри «дышит» шум; сила луча = сила лампы / её обычная сила (base из выгрузки).
## Пылинки висят вокруг камеры (сетка 8 м повторяется) и видны только в лучах шести самых заметных ламп.
## Одинаково на ПК и на сайте: в старой игре красное зарево серверной и тёплые столбы света в холле — это они, а не стены.

const BEAM_SHADER := """
shader_type spatial;
render_mode unshaded, blend_add, depth_draw_never, cull_disabled, shadows_disabled, fog_disabled;
uniform float uH = 2.8;
uniform vec3 uC = vec3(1.0);   // цвет лампы, линейный (как Color three.js)
uniform float uI = 0.0;
uniform float uT = 0.0;
uniform float uFog = 0.05;
uniform float uK = 1.0;        // сайт (Compatibility): прозрачное складывается не в линейном свете — луч сильнее (зарево серверной как на ПК)
varying float vY;
varying vec3 vW;
varying vec3 vN;
float h(vec3 p) { return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }
float n3(vec3 p) { vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
	return mix(mix(mix(h(i), h(i + vec3(1.0, 0.0, 0.0)), f.x), mix(h(i + vec3(0.0, 1.0, 0.0)), h(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
		mix(mix(h(i + vec3(0.0, 0.0, 1.0)), h(i + vec3(1.0, 0.0, 1.0)), f.x), mix(h(i + vec3(0.0, 1.0, 1.0)), h(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z); }
void vertex() {
	vY = VERTEX.y / uH + 0.5;
	vW = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	vN = mat3(MODEL_MATRIX) * NORMAL;
}
void fragment() {
	// всё в мировых координатах из своих переменных: встроенные VIEW / VERTEX на сайте (Compatibility) давали другой луч
	vec3 tc = CAMERA_POSITION_WORLD - vW;
	float d = length(tc);
	float f = clamp(abs(dot(normalize(vN), tc / max(d, 1e-4))), 0.0, 1.0);
	float a = pow(f, 2.5) * pow(clamp(vY, 0.0, 1.0), 1.4) * uI * exp(-d * uFog * 1.4) * smoothstep(0.15, 1.2, d);
	a *= 0.65 + 0.7 * n3(vW * 2.2 + vec3(0.0, -uT * 0.15, uT * 0.07));
	if (isnan(a) || isinf(a)) { a = 0.0; }   // у основания конуса: иначе чёрный пунктир по полу (так было в старой игре 29 окт)
	ALBEDO = uC * max(a, 0.0) * 0.22 * uK;
}
"""
const WEB_K := 1.8

const DUST_SHADER := """
shader_type spatial;
render_mode unshaded, blend_add, depth_draw_never, cull_disabled, shadows_disabled, fog_disabled;
uniform float uT = 0.0;
uniform float uPx = 900.0;
uniform vec4 uL[6];
varying float vA;
void vertex() {
	vec3 p = VERTEX + vec3(sin(uT * 0.13 + VERTEX.y * 3.0) * 0.3, -uT * 0.025, cos(uT * 0.11 + VERTEX.x * 2.0) * 0.3);
	vec3 cam = CAMERA_POSITION_WORLD;
	p = cam + mod(p - cam + 4.0, 8.0) - 4.0;
	float a = 0.0;
	for (int i = 0; i < 6; i++) {
		vec3 d = p - uL[i].xyz;
		float r = 0.25 + max(0.0, -d.y) * 0.42;
		a += uL[i].w * (1.0 - smoothstep(r * 0.3, r, length(d.xz))) * step(d.y, 0.1) * smoothstep(-4.0, -0.2, d.y);
	}
	vec4 mv = VIEW_MATRIX * vec4(p, 1.0);
	float z = -mv.z;
	vA = min(1.0, a) * smoothstep(0.3, 1.0, z) * (1.0 - smoothstep(3.0, 7.0, z)) * (0.45 + 0.55 * fract(sin(dot(VERTEX, vec3(12.9, 78.2, 37.7))) * 4375.0));
	POINT_SIZE = uPx * (0.012 + 0.012 * fract(VERTEX.x * 91.0)) / max(z, 0.1);
	POSITION = PROJECTION_MATRIX * mv;
}
void fragment() {
	float a = (1.0 - smoothstep(0.0, 0.5, length(POINT_COORD - 0.5))) * vA;
	ALBEDO = vec3(1.0, 0.93, 0.82) * a * 0.7;
}
"""

static var _beam_sh: Shader
static var _dust_sh: Shader
var list: Array = []          # [{ l: Light3D, base, mat, top }]
var dust: MeshInstance3D
var fog_d := 0.05


func setup(lights: Dictionary, defs: Array, fog_density: float, pc: bool) -> void:
	fog_d = fog_density
	if _beam_sh == null:
		_beam_sh = Shader.new()
		_beam_sh.code = BEAM_SHADER
		_dust_sh = Shader.new()
		_dust_sh.code = DUST_SHADER
	for d in defs:
		var l: Light3D = lights.get(String(d.l))
		if l == null:
			continue
		var cm := CylinderMesh.new()
		cm.top_radius = float(d.r0)
		cm.bottom_radius = float(d.r1)
		cm.height = float(d.h)
		cm.radial_segments = 28
		cm.rings = 1
		cm.cap_top = false
		cm.cap_bottom = false
		var m := ShaderMaterial.new()
		m.shader = _beam_sh
		m.render_priority = 5
		m.set_shader_parameter("uH", float(d.h))
		m.set_shader_parameter("uFog", fog_d)
		m.set_shader_parameter("uK", 1.0 if pc else (float(OS.get_environment("BEAMK")) if OS.get_environment("BEAMK") != "" else WEB_K))
		var mi := MeshInstance3D.new()
		mi.mesh = cm
		mi.material_override = m
		mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		mi.position = Vector3(float(d.pos[0]), float(d.pos[1]), float(d.pos[2]))
		add_child(mi)
		list.append({"l": l, "base": float(d.base), "mat": m, "top": mi.position + Vector3(0, float(d.h) / 2.0 + 0.02, 0)})
	if list.is_empty():
		return
	# пыль: точки в кубе 8 м (сайт — меньше точек, как «Кино» на телефоне)
	var n := 2200 if pc else 900
	var rng := RandomNumberGenerator.new()
	rng.seed = 7
	var pts := PackedVector3Array()
	pts.resize(n)
	for i in n:
		pts[i] = Vector3(rng.randf() * 8.0, rng.randf() * 8.0, rng.randf() * 8.0)
	var arr := []
	arr.resize(Mesh.ARRAY_MAX)
	arr[Mesh.ARRAY_VERTEX] = pts
	var am := ArrayMesh.new()
	am.add_surface_from_arrays(Mesh.PRIMITIVE_POINTS, arr)
	var dm := ShaderMaterial.new()
	dm.shader = _dust_sh
	dm.render_priority = 6
	dust = MeshInstance3D.new()
	dust.mesh = am
	dust.material_override = dm
	dust.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	dust.custom_aabb = AABB(Vector3(-500, -500, -500), Vector3(1000, 1000, 1000))   # точки двигает шейдер — не отсекать
	add_child(dust)


## Каждый кадр: сила лучей по лампам (запись кадра их меняет), время шума, шесть ламп для пыли — ярче и ближе к камере.
func tick(t: float, cam: Camera3D) -> void:
	if list.is_empty():
		return
	for b in list:
		var l: Light3D = b.l
		var on := l.visible and l.is_visible_in_tree()
		var c := l.light_color.srgb_to_linear()
		b.mat.set_shader_parameter("uI", clampf(Location.intensity(l) / float(b.base), 0.0, 1.6) * (1.0 if on else 0.0))
		b.mat.set_shader_parameter("uC", Vector3(c.r, c.g, c.b))
		b.mat.set_shader_parameter("uT", t)
	if dust == null or cam == null:
		return
	var cp := cam.global_position
	var lit := list.duplicate()
	var sc := func(b) -> float:
		var l: Light3D = b.l
		return Location.intensity(l) * (1.0 if l.visible else 0.0) / (1.0 + (b.top as Vector3).distance_squared_to(cp))
	lit.sort_custom(func(a, b): return sc.call(a) > sc.call(b))
	var L6 := []
	for i in 6:
		if i < lit.size():
			var l: Light3D = lit[i].l
			var p: Vector3 = l.global_position
			L6.append(Vector4(p.x, p.y, p.z, clampf(Location.intensity(l) / float(lit[i].base), 0.0, 1.4) * (1.0 if l.visible else 0.0)))
		else:
			L6.append(Vector4(0, -99, 0, 0))
	var dm := dust.material_override as ShaderMaterial
	dm.set_shader_parameter("uL", L6)
	dm.set_shader_parameter("uT", t)
	var vp := get_viewport()
	dm.set_shader_parameter("uPx", vp.get_visible_rect().size.y * vp.scaling_3d_scale * 0.9)
