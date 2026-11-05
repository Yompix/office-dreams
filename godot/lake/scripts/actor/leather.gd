class_name Leather
extends RefCounted
## Кожаная куртка Ноя (порядок редактора от 5 ноя, п. 6: «доработать куртку — фактура кожи, складки»).
## Материал из сборки был ровным, как пластик: крупное зерно пятнами, блик по всей куртке, складок нет.
## Здесь — свой шейдер поверх тех же развёрток и того же цвета (крой, сетка и симуляция ткани в сборке не меняются):
##  • зерно кожи мельче и глубже (Poly Haven brown_leather, CC0), блеск неровный — по карте шероховатости;
##  • складки на сгибе локтя — тем глубже, чем сильнее согнута рука (по костям, каждый кадр); с внутренней стороны сгиба
##    глубже, на самом локте — мельче;
##  • напуск над поясом: волнистые складки над низом куртки, сзади и по бокам сильнее (спереди — молния);
##  • выпуклости складок светлее и суше (потёртая кожа), глубина складок темнее.
## Только у сетки cloth_jacket (она одна такая — у Ноя); молния и прочие детали остаются со своими материалами.

const TEX := "res://assets/ph/brown_leather/brown_leather_%s_1k.jpg"
const COLOR := Color(0.0962, 0.0361, 0.016)   # прежний цвет куртки (линейный): тот же отцовский коричневый
const LUM_MEAN := 0.0418                        # средняя яркость текстуры brown_leather (линейная) — от неё только узор
const GRAIN := 2.2                              # зерно во столько раз мельче прежнего

const SHADER := """
shader_type spatial;
render_mode cull_back, diffuse_burley, specular_schlick_ggx;
uniform sampler2D t_alb : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D t_nor : hint_normal, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D t_arm : hint_default_white, filter_linear_mipmap_anisotropic, repeat_enable;
uniform vec3 color = vec3(0.0962, 0.0361, 0.016);
uniform float lum_mean = 0.0418;
uniform vec2 uv_scale = vec2(6.0);
uniform float depth = 1.15;
uniform vec3 sh_l; uniform vec3 el_l; uniform vec3 wr_l;   // плечо, локоть, запястье (мир)
uniform vec3 sh_r; uniform vec3 el_r; uniform vec3 wr_r;
uniform float bend_l = 0.0; uniform float bend_r = 0.0;   // 0 — рука прямая, 1 — согнута сильно
uniform vec3 hips; uniform vec3 up = vec3(0.0, 1.0, 0.0); uniform vec3 fwd = vec3(0.0, 0.0, 1.0);
uniform float hem = -0.1;                                  // низ куртки относительно таза (вдоль up)
varying vec3 vW;
float h3(vec3 p) { return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453); }
float n3(vec3 p) { vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
	return mix(mix(mix(h3(i), h3(i + vec3(1.0, 0.0, 0.0)), f.x), mix(h3(i + vec3(0.0, 1.0, 0.0)), h3(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
		mix(mix(h3(i + vec3(0.0, 0.0, 1.0)), h3(i + vec3(1.0, 0.0, 1.0)), f.x), mix(h3(i + vec3(0.0, 1.0, 1.0)), h3(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z); }

// складки у сгиба локтя: кольца поперёк руки (шаг ~2,3 см, неровные), глубже с внутренней стороны сгиба.
// g — наклон нормали (мир), c — глубина складки, r — гребень
void elbow(vec3 p, vec3 js, vec3 je, vec3 jw, float bend, inout vec3 g, inout float c, inout float r) {
	vec3 u = normalize(je - js + vec3(0.0, 1e-5, 0.0));
	vec3 f = normalize(jw - je + vec3(0.0, 1e-5, 0.0));
	vec3 ax = normalize(u + f + vec3(1e-5));
	float t = dot(p - je, ax);
	vec3 rad = p - je - ax * t;
	float d = length(rad);
	vec3 inn = f - u;
	float il = length(inn);
	float side = il > 1e-3 ? dot(rad / max(d, 1e-4), inn / il) : 0.0;   // 1 — внутри сгиба, −1 — на локте
	float zone = (1.0 - smoothstep(0.03, 0.12, abs(t))) * (1.0 - smoothstep(0.075, 0.1, d));
	float w = zone * (0.15 + 0.85 * bend) * mix(0.3, 1.0, smoothstep(-0.4, 0.5, side));
	float ph = t * 270.0 + (n3(p * 37.0) - 0.5) * 4.0 + side * 1.4;
	float s = sin(ph);
	g += -ax * cos(ph) * w * 0.85;
	c = max(c, w * smoothstep(0.1, 1.0, -s));
	r = max(r, w * smoothstep(0.35, 1.0, s));
}

void vertex() { vW = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz; }

void fragment() {
	vec2 uv = UV * uv_scale;
	vec3 g = vec3(0.0);
	float cav = 0.0;
	float rid = 0.0;
	elbow(vW, sh_l, el_l, wr_l, bend_l, g, cav, rid);
	elbow(vW, sh_r, el_r, wr_r, bend_r, g, cav, rid);
	// напуск над поясом: волнистые складки поперёк туловища от низа куртки вверх на ~13 см; спереди (молния) — слабее
	float yh = dot(vW - hips, up) - hem;
	vec3 hz = vW - hips - up * dot(vW - hips, up);
	float front = smoothstep(0.0, 0.7, dot(normalize(hz + vec3(1e-5)), fwd));
	float wz = smoothstep(0.0, 0.025, yh) * (1.0 - smoothstep(0.07, 0.13, yh)) * mix(1.0, 0.35, front);
	float wph = yh * 200.0 + (n3(vW * 9.0) - 0.5) * 7.0;
	g += -up * cos(wph) * wz * 0.6;
	cav = max(cav, wz * smoothstep(0.2, 1.0, -sin(wph)));
	rid = max(rid, wz * smoothstep(0.4, 1.0, sin(wph)));
	NORMAL = normalize(NORMAL - (VIEW_MATRIX * vec4(g, 0.0)).xyz);
	vec3 tx = texture(t_alb, uv).rgb;
	float l = dot(tx, vec3(0.3, 0.59, 0.11)) / lum_mean;
	vec3 base = color * (1.0 + (l - 1.0) * 0.5) * (0.92 + 0.16 * n3(vW * 4.0));   // узор кожи и крупные пятна — мягко
	float wear = smoothstep(0.6, 0.9, n3(vW * 7.0 + 3.1)) * 0.25 + rid * 0.5;      // потёртости — светлее
	base = mix(base, base * 1.8 + vec3(0.012, 0.007, 0.004), wear);
	base *= 1.0 - cav * 0.4;                                                        // глубина складки — темнее
	ALBEDO = base;
	NORMAL_MAP = texture(t_nor, uv).rgb;
	NORMAL_MAP_DEPTH = depth;
	float rough_map = texture(t_arm, uv).g;
	ROUGHNESS = clamp(mix(0.34, 0.62, rough_map) - rid * 0.16 + cav * 0.14 + wear * 0.06, 0.24, 0.9);   // кожа с неровным блеском
	SPECULAR = 0.5;
	AO = 1.0 - cav * 0.5;
}
"""

static var _shader: Shader = null


## Куртка героя → свой материал. Возвращает материалы, которым каждый кадр нужны кости (Actor._update_leather).
static func apply(a: Node3D, skel: Skeleton3D) -> Array[ShaderMaterial]:
	var out: Array[ShaderMaterial] = []
	if "--no-leather" in OS.get_cmdline_user_args():   # сравнение «до/после» на кадрах проверки
		return out
	if _shader == null:
		_shader = Shader.new()
		_shader.code = SHADER
	for n in a.find_children("cloth_jacket", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		if mi.mesh == null:
			continue
		var pel := skel.find_bone("pelvis")
		var hem := -0.1
		if pel >= 0:   # низ куртки в позе покоя — над тазом или под ним (для напуска над поясом); герой ещё не в сцене — по цепочке узлов
			var py := (HumanRig._xform_to(skel, a) * skel.get_bone_global_rest(pel).origin).y
			var my := (HumanRig._xform_to(mi, a) * mi.mesh.get_aabb().position).y
			hem = my - py
		for i in mi.mesh.get_surface_count():
			var src := mi.get_active_material(i) as BaseMaterial3D
			if src == null:
				continue
			var rn := String(src.resource_name)
			if rn.contains("_zip") or rn.contains("_pull") or rn.contains("_button") or rn.contains("_emb") or rn.contains("_patch"):
				continue
			var sm := ShaderMaterial.new()
			sm.shader = _shader
			sm.set_shader_parameter("t_alb", load(TEX % "diff"))
			sm.set_shader_parameter("t_nor", load(TEX % "nor_gl"))
			sm.set_shader_parameter("t_arm", load(TEX % "arm"))
			sm.set_shader_parameter("color", Vector3(COLOR.r, COLOR.g, COLOR.b))
			sm.set_shader_parameter("lum_mean", LUM_MEAN)
			sm.set_shader_parameter("uv_scale", Vector2(src.uv1_scale.x, src.uv1_scale.y) * GRAIN)
			sm.set_shader_parameter("hem", hem)
			mi.set_surface_override_material(i, sm)
			out.append(sm)
	return out


## Каждый кадр: суставы рук, сгиб локтей, таз и направление героя.
static func update(mats: Array[ShaderMaterial], a: Node3D, skel: Skeleton3D) -> void:
	if mats.is_empty():
		return
	var g := func(n: String) -> Vector3:
		var b := skel.find_bone(n)
		return skel.global_transform * skel.get_bone_global_pose(b).origin if b >= 0 else a.global_position
	var p := {}
	for k in ["upperarm_l", "lowerarm_l", "hand_l", "upperarm_r", "lowerarm_r", "hand_r", "pelvis"]:
		p[k] = g.call(k)
	var bend := func(s: Vector3, e: Vector3, w: Vector3) -> float:
		var u := (e - s).normalized()
		var f := (w - e).normalized()
		return clampf((acos(clampf(u.dot(f), -1.0, 1.0)) - 0.2) / 2.0, 0.0, 1.0)   # 90° → 0,69; 135° → 1
	var bl: float = bend.call(p.upperarm_l, p.lowerarm_l, p.hand_l)
	var br: float = bend.call(p.upperarm_r, p.lowerarm_r, p.hand_r)
	var up := a.global_transform.basis.y.normalized()
	var fwd := a.global_transform.basis.z.normalized()
	for sm in mats:
		sm.set_shader_parameter("sh_l", p.upperarm_l)
		sm.set_shader_parameter("el_l", p.lowerarm_l)
		sm.set_shader_parameter("wr_l", p.hand_l)
		sm.set_shader_parameter("sh_r", p.upperarm_r)
		sm.set_shader_parameter("el_r", p.lowerarm_r)
		sm.set_shader_parameter("wr_r", p.hand_r)
		sm.set_shader_parameter("bend_l", bl)
		sm.set_shader_parameter("bend_r", br)
		sm.set_shader_parameter("hips", p.pelvis)
		sm.set_shader_parameter("up", up)
		sm.set_shader_parameter("fwd", fwd)
