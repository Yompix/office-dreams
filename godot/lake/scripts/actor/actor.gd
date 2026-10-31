class_name Actor
extends Node3D
## Герой: модель MakeHuman (CC0, из Hush Till Six), скелет «переодет» под клипы Quaternius / мокап (HumanRig), клипы
## движений (AnimLib), лицо — моргание, губы по тексту реплики, взгляд головой на точку. Анимация считается вручную каждый
## кадр, поверх неё — голова и лицо (иначе клип перетирает поворот головы).

var key := ""
var model: Node3D
var skel: Skeleton3D
var ap: AnimationPlayer
var meshes: Array = []          # [MeshInstance3D, {форма → индекс}]
var look_target = null          # Vector3 (мир) или null — смотреть по клипу
var look_w := 0.0
var talk_text := ""
var talk_t := -1.0
var talk_dur := 0.0
var talk_words: Array = []      # голос: [[начало, слово], …] — буквы губ идут по словам озвучки
var talk_env := ""              # голос: громкость по 40 мс (0…9) — челюсть открывается по звуку
var blink_t := 2.0
var expr := {}                  # постоянное выражение: {форма → вес} (страх, слёзы…)
var _head := -1
var _neck := -1
var clip := ""
var _hair: Array[ShaderMaterial] = []
var _hair_p := Vector3.ZERO      # точка на пружине: догоняет центр головы с запаздыванием — так отстают пряди
var _hair_v := Vector3.ZERO
var _hair_init := false
var breeze := 0.0                # ветер снаружи (0 — в машине)
var wave := 0.0                  # машет обеими руками над головой (0…1): голосует на обочине, зовёт издалека
var _wave_w := 0.0
var _wave_t := 0.0
var reach := {}                  # тянуть руку к точке (мир): {"_r": Vector3, "_l": Vector3} — взять за руку, дотронуться
var _reach_w := 0.0
var _eye := Vector2.ZERO         # куда смотрят глаза (вправо/вверх, доля полного хода); мелкие рывки — саккады
var _eye_to := Vector2.ZERO
var _sacc_t := 0.6

## насколько свободно качаются волосы: короткие почти не двигаются, каре — кончиками
const HAIR_SOFT := {"noah": 0.3, "iris": 1.0, "roman": 0.15, "danny": 0.0, "zoe": 0.8, "eva": 1.0, "agnes": 0.7, "hale": 0.2}
const HAIR_SHADER := """
shader_type spatial;
render_mode cull_disabled;
uniform sampler2D albedo_tex : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform vec4 albedo : source_color = vec4(1.0);
uniform float cut = 0.18;
uniform float soft = 0.5;
uniform vec3 head_c = vec3(0.0, -99.0, 0.0);   // центр головы (мир)
uniform vec3 sway = vec3(0.0);                 // отставание прядей от головы (мир)
uniform float breeze = 0.0;
void vertex() {
	vec3 w = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	vec3 d = w - head_c;
	// у кожи головы волосы держатся, ниже висков и у кончиков — свободнее
	float k = soft * (smoothstep(-0.02, 0.14, -d.y) + 0.3 * smoothstep(0.11, 0.16, length(d)));
	float n = sin(TIME * 1.9 + w.x * 11.0 + w.y * 5.0) * 0.6 + sin(TIME * 3.3 + w.z * 9.0) * 0.4;
	vec3 off = (sway + vec3(n, n * 0.3, n * 0.8) * (0.0008 + breeze * 0.01)) * min(k, 1.2);
	VERTEX += inverse(mat3(MODEL_MATRIX)) * off;
}
void fragment() {
	vec4 t = texture(albedo_tex, UV);
	ALBEDO = t.rgb * albedo.rgb;
	ALPHA = t.a * albedo.a;
	ALPHA_SCISSOR_THRESHOLD = cut;
	ROUGHNESS = 0.62;
	SPECULAR = 0.4;
	ANISOTROPY = 0.55;            // блик тянется вдоль прядей
	ANISOTROPY_FLOW = vec2(0.0, 1.0);
}
"""
static var _hair_sh: Shader


static func _hair_shader() -> Shader:
	if _hair_sh == null:
		_hair_sh = Shader.new()
		_hair_sh.code = HAIR_SHADER
	return _hair_sh

## буква → форма рта (как viseme в Hush Till Six)
const VIS := {"а": "viseme_aa", "я": "viseme_aa", "a": "viseme_aa", "о": "viseme_O", "ё": "viseme_O", "o": "viseme_O", "у": "viseme_U",
	"ю": "viseme_U", "u": "viseme_U", "w": "viseme_U", "э": "viseme_E", "е": "viseme_E", "e": "viseme_E", "и": "viseme_I", "ы": "viseme_I",
	"i": "viseme_I", "y": "viseme_I", "м": "viseme_PP", "б": "viseme_PP", "п": "viseme_PP", "m": "viseme_PP", "b": "viseme_PP", "p": "viseme_PP",
	"ф": "viseme_FF", "в": "viseme_FF", "f": "viseme_FF", "v": "viseme_FF", "с": "viseme_SS", "з": "viseme_SS", "ц": "viseme_SS", "s": "viseme_SS",
	"z": "viseme_SS", "ш": "viseme_CH", "ж": "viseme_CH", "ч": "viseme_CH", "щ": "viseme_CH", "т": "viseme_DD", "д": "viseme_DD", "н": "viseme_nn",
	"t": "viseme_DD", "d": "viseme_DD", "n": "viseme_nn", "л": "viseme_nn", "l": "viseme_nn", "к": "viseme_kk", "г": "viseme_kk", "х": "viseme_kk",
	"k": "viseme_kk", "g": "viseme_kk", "h": "viseme_kk", "р": "viseme_RR", "r": "viseme_RR"}


static func make(k: String, file: String) -> Actor:
	var a := Actor.new()
	a.key = k
	a.name = k
	var path := file if file.begins_with("res://") else ("res://assets/chars/%s.glb" % file if ResourceLoader.exists("res://assets/chars/%s.glb" % file) else "res://assets/models/%s.glb" % file)
	a.model = (load(path) as PackedScene).instantiate()
	a.add_child(a.model)
	var rig := HumanRig.rebase(a.model)
	a.skel = rig.skel
	a.ap = AnimationPlayer.new()
	a.skel.get_parent().add_child(a.ap)   # дорожки клипов — «Skeleton3D:кость» от родителя скелета
	a.ap.add_animation_library("", AnimLib.library(rig.k))
	a.ap.callback_mode_process = AnimationMixer.ANIMATION_CALLBACK_MODE_PROCESS_MANUAL
	for mi in a.model.find_children("*", "MeshInstance3D", true, false):
		var m := mi as MeshInstance3D
		var nm := String(m.name).to_lower()
		for i in m.mesh.get_surface_count():
			var src := m.get_active_material(i) as BaseMaterial3D
			if src == null:
				continue
			if nm.contains("hair"):   # волосы: свой шейдер — вырезка по альфе, блик вдоль прядей, пряди качаются
				var hm := ShaderMaterial.new()
				hm.shader = _hair_shader()
				hm.set_shader_parameter("albedo_tex", src.albedo_texture)
				hm.set_shader_parameter("albedo", src.albedo_color)
				hm.set_shader_parameter("soft", HAIR_SOFT.get(k, 0.5))
				m.set_surface_override_material(i, hm)
				a._hair.append(hm)
				continue
			# MakeHuman отдаёт кожу и одежду «полупрозрачными» — такие не получают мягких теней (SSAO) и рассеяния в коже;
			# делаем непрозрачными (вырезка по альфе — где у ткани дырки), брови и ресницы — тонкой вырезкой с двух сторон
			var mm := src.duplicate() as BaseMaterial3D
			if nm.contains("brows") or nm.contains("lashes"):
				mm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA_SCISSOR
				mm.alpha_scissor_threshold = 0.25
				mm.cull_mode = BaseMaterial3D.CULL_DISABLED
			elif nm == "skin":
				mm.transparency = BaseMaterial3D.TRANSPARENCY_DISABLED
				mm.subsurf_scatter_enabled = true      # свет чуть проходит под кожу (ПК); в браузере — без этого
				mm.subsurf_scatter_strength = 0.35
				mm.subsurf_scatter_skin_mode = true
			elif nm == "eyes":                      # влажные глаза: блик от фар и приборов — взгляд «живой»
				mm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA_SCISSOR
				mm.alpha_scissor_threshold = 0.5
				mm.roughness = 0.12
				mm.metallic_specular = 0.75
			elif mm.transparency != BaseMaterial3D.TRANSPARENCY_DISABLED:
				mm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA_SCISSOR
				mm.alpha_scissor_threshold = 0.5
			m.set_surface_override_material(i, mm)
		if m.mesh is ArrayMesh and (m.mesh as ArrayMesh).get_blend_shape_count() > 0:
			var d := {}
			for i in (m.mesh as ArrayMesh).get_blend_shape_count():
				d[String((m.mesh as ArrayMesh).get_blend_shape_name(i))] = i
			a.meshes.append([m, d])
	a._head = a.skel.find_bone("head")
	a._neck = a.skel.find_bone("neck_01")
	return a


func play(c: String, at := 0.0) -> void:
	if not ap.has_animation(c):
		push_warning("нет клипа " + c)
		return
	if c != clip:   # кости, которых нет в новом клипе, — в покой (иначе остаётся поза прошлого: спина «в стуле» после посадки)
		skel.reset_bone_poses()
	clip = c
	ap.play(c)
	ap.seek(at, true)


## Реплика: губы по буквам текста за dur секунд. С озвучкой — words ([[начало, слово]]) и env (громкость по 40 мс):
## буква — из слова, которое звучит сейчас, челюсть — по громкости голоса.
func say(text: String, dur: float, words: Array = [], env := "") -> void:
	talk_text = text.to_lower()
	talk_t = 0.0
	talk_dur = maxf(0.4, dur)
	talk_words = words
	talk_env = env


## Буква губ в момент t: по словам озвучки (слово до следующего или +0,45 с) или ровно по тексту.
func _talk_char(t: float) -> String:
	if talk_words.is_empty():
		var i := int(t / talk_dur * talk_text.length())
		return talk_text[i] if i < talk_text.length() else " "
	for j in range(talk_words.size() - 1, -1, -1):
		var w: Array = talk_words[j]
		var t0 := float(w[0])
		if t < t0:
			continue
		var word := String(w[1]).to_lower()
		var t1 := float(talk_words[j + 1][0]) if j + 1 < talk_words.size() else t0 + 0.08 * word.length() + 0.1
		t1 = minf(t1, t0 + 0.09 * word.length() + 0.15)
		if t >= t1 or word.is_empty():
			return " "
		return word[mini(word.length() - 1, int((t - t0) / (t1 - t0) * word.length()))]
	return " "


func _process(delta: float) -> void:
	if ap.is_playing():
		ap.advance(delta)
	var W := expr.duplicate()
	# моргание
	blink_t -= delta
	if blink_t < 0.0:
		blink_t = randf_range(2.2, 5.5)
	var bl := clampf(1.0 - absf(blink_t - 0.08) / 0.08, 0.0, 1.0) if blink_t < 0.16 else 0.0
	W["eyeBlinkLeft"] = maxf(W.get("eyeBlinkLeft", 0.0), bl)
	W["eyeBlinkRight"] = maxf(W.get("eyeBlinkRight", 0.0), bl)
	# глаза: мелкие рывки раз в 0.4–2.5 с (переход ~60 мс), после большого рывка — часто моргание; веки идут за глазами
	_sacc_t -= delta
	if _sacc_t < 0.0:
		_sacc_t = randf_range(0.4, 2.5) * (0.6 if talk_t >= 0.0 else 1.0)
		_eye_to = Vector2(randf_range(-0.22, 0.22), randf_range(-0.14, 0.1))
		if _eye.distance_to(_eye_to) > 0.25 and blink_t > 0.5 and randf() < 0.6:
			blink_t = 0.16
	_eye = _eye.lerp(_eye_to, minf(1.0, delta * 18.0))
	var ex := _eye + Vector2(W.get("_eye_x", 0.0), W.get("_eye_y", 0.0))
	var er := "eyeLookInLeft" if ex.x > 0.0 else "eyeLookOutLeft"
	W[er] = W.get(er, 0.0) + absf(ex.x)
	er = "eyeLookOutRight" if ex.x > 0.0 else "eyeLookInRight"
	W[er] = W.get(er, 0.0) + absf(ex.x)
	for side in ["Left", "Right"]:
		er = ("eyeLookUp" if ex.y > 0.0 else "eyeLookDown") + side
		W[er] = W.get(er, 0.0) + absf(ex.y)
	# губы: буква в текущий момент реплики, челюсть — по слогам
	if talk_t >= 0.0:
		talk_t += delta
		var f := talk_t / talk_dur
		if f >= 1.0:
			talk_t = -1.0
		else:
			var ch := _talk_char(talk_t)
			var v: String = VIS.get(ch, "")
			var open := 0.0 if ch in [" ", ".", ",", "!", "?", "…", "-", "—"] else 0.35 + 0.25 * sin(talk_t * 21.0)
			if talk_env != "":   # озвучка: челюсть по громкости голоса
				var ei := int(talk_t / 0.04)
				open = (float(talk_env.unicode_at(ei) - 48) / 9.0) * 0.75 if ei < talk_env.length() else 0.0
				if ch == " ":
					v = ""
			if v != "":
				W[v] = 0.7
			W["jawOpen"] = open * (0.5 if v in ["viseme_PP", "viseme_FF"] else 1.0)
	for m in meshes:
		var mi: MeshInstance3D = m[0]
		var d: Dictionary = m[1]
		for bn in d:
			mi.set_blend_shape_value(d[bn], W.get(bn, 0.0))
	_update_blood()
	_update_hair(delta)
	_update_wave(delta)
	_update_reach(delta)
	# взгляд: голова и шея поворачиваются к точке (поверх клипа)
	look_w = move_toward(look_w, 1.0 if look_target != null else 0.0, delta * 2.5)
	if look_w > 0.001 and _head >= 0 and look_target != null:
		var hg := skel.global_transform * skel.get_bone_global_pose(_head)
		var to: Vector3 = (look_target as Vector3) - hg.origin
		var fwd := hg.basis.z.normalized()          # лицо MakeHuman смотрит по +Z кости головы
		var axis := fwd.cross(to.normalized())
		if axis.length() > 0.0001:
			var ang := minf(fwd.angle_to(to), 1.1) * look_w
			var q_world := Quaternion(axis.normalized(), ang)
			for b in [[_neck, 0.4], [_head, 0.6]]:
				if b[0] < 0:
					continue
				var bg := skel.global_transform * skel.get_bone_global_pose(b[0])
				var local_axis := (bg.basis.inverse() * axis.normalized()).normalized()
				var rot := skel.get_bone_pose_rotation(b[0]) * Quaternion(local_axis, ang * b[1])
				skel.set_bone_pose_rotation(b[0], rot)


## Машет обеими руками над головой (поверх клипа): плечи вверх и чуть в стороны, предплечья качаются навстречу друг другу.
## Герой без поворота смотрит к +Z, его левая рука — к +X.
func _update_wave(delta: float) -> void:
	_wave_w = move_toward(_wave_w, wave, delta * 3.0)
	if _wave_w < 0.001:
		return
	_wave_t += delta
	var gb := skel.global_transform.basis
	var up := gb.y.normalized()
	var fwd := gb.z.normalized()
	var left := gb.x.normalized()
	for side in [[left, "_l", 0.0], [-left, "_r", PI]]:
		var out: Vector3 = side[0]
		var sw := sin(_wave_t * 6.5 + float(side[2]))
		_aim_bone("upperarm" + side[1], "lowerarm" + side[1], (up * 0.85 + out * 0.5 + fwd * 0.18).normalized(), _wave_w)
		_aim_bone("lowerarm" + side[1], "hand" + side[1], (up + out * (0.05 + 0.42 * sw) + fwd * 0.12).normalized(), _wave_w)


## Рука тянется к точке: плечо — к ней и чуть вниз (локоть согнут), предплечье — прямо в точку.
func _update_reach(delta: float) -> void:
	_reach_w = move_toward(_reach_w, 1.0 if not reach.is_empty() else 0.0, delta * 2.0)
	if _reach_w < 0.001 or reach.is_empty():
		return
	var down := -skel.global_transform.basis.y.normalized()
	for side in reach:
		var tgt: Vector3 = reach[side]
		var b := skel.find_bone("upperarm" + side)
		if b < 0:
			continue
		var sh := (skel.global_transform * skel.get_bone_global_pose(b)).origin
		_aim_bone("upperarm" + side, "lowerarm" + side, ((tgt - sh).normalized() + down * 0.35).normalized(), _reach_w)
		var e := skel.find_bone("lowerarm" + side)
		var el := (skel.global_transform * skel.get_bone_global_pose(e)).origin
		_aim_bone("lowerarm" + side, "hand" + side, (tgt - el).normalized(), _reach_w)


## Повернуть кость так, чтобы она смотрела (от себя к дочерней) по направлению dir (мир); w — доля поворота.
func _aim_bone(bone: String, child: String, dir: Vector3, w: float) -> void:
	var b := skel.find_bone(bone)
	var c := skel.find_bone(child)
	if b < 0 or c < 0:
		return
	var bg := skel.global_transform * skel.get_bone_global_pose(b)
	var cg := skel.global_transform * skel.get_bone_global_pose(c)
	var cur := (cg.origin - bg.origin).normalized()
	var axis := cur.cross(dir)
	if axis.length() < 0.0001:
		return
	var local_axis := (bg.basis.inverse() * axis.normalized()).normalized()
	skel.set_bone_pose_rotation(b, skel.get_bone_pose_rotation(b) * Quaternion(local_axis, cur.angle_to(dir) * w))


## Кровь на руках (сцена 1: «липкие, тёмно-красные, кровь под ногтями»): материал кожи заменяется своим — та же текстура,
## а у кистей (отрезок запястье → середина ладони) кожа темнеет до засохшей крови пятнами, свежая — с блеском.
const BLOOD_SKIN := """
shader_type spatial;
render_mode sss_mode_skin;
uniform sampler2D albedo_tex : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform vec4 albedo : source_color = vec4(1.0);
uniform bool has_tex = false;
uniform float rough = 0.6;
uniform vec3 hl0 = vec3(0.0, -99.0, 0.0);
uniform vec3 hl1 = vec3(0.0, -99.0, 0.0);
uniform vec3 hr0 = vec3(0.0, -99.0, 0.0);
uniform vec3 hr1 = vec3(0.0, -99.0, 0.0);
uniform float blood = 1.0;
varying vec3 vW;
float h3(vec3 p) { return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453); }
float n3(vec3 p) { vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
	return mix(mix(mix(h3(i), h3(i + vec3(1.0, 0.0, 0.0)), f.x), mix(h3(i + vec3(0.0, 1.0, 0.0)), h3(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
		mix(mix(h3(i + vec3(0.0, 0.0, 1.0)), h3(i + vec3(1.0, 0.0, 1.0)), f.x), mix(h3(i + vec3(0.0, 1.0, 1.0)), h3(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z); }
float seg(vec3 p, vec3 a, vec3 b) { vec3 ab = b - a; float t = clamp(dot(p - a, ab) / max(dot(ab, ab), 1e-6), 0.0, 1.0); return length(p - a - ab * t); }
void vertex() { vW = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz; }
void fragment() {
	vec4 t = has_tex ? texture(albedo_tex, UV) : vec4(1.0);
	vec3 c = t.rgb * albedo.rgb;
	float d = min(seg(vW, hl0, hl1), seg(vW, hr0, hr1));
	float n = n3(vW * 70.0) * 0.6 + n3(vW * 19.0) * 0.4;
	float m = blood * (1.0 - smoothstep(0.04, 0.08, d + (n - 0.5) * 0.05));
	m *= smoothstep(0.45, 0.68, n3(vW * 46.0 + 1.7) * 0.7 + n);   // пятнами и разводами: между ними — кожа
	float fresh = smoothstep(0.68, 0.85, n3(vW * 33.0 + 4.0));
	c = mix(c, mix(vec3(0.085, 0.022, 0.016), vec3(0.15, 0.016, 0.012), fresh), m * 0.9);   // засохшая — бурая, свежая — тёмно-красная
	ALBEDO = c;
	ROUGHNESS = mix(rough, mix(0.68, 0.32, fresh), m);
	SPECULAR = 0.5;
	SSS_STRENGTH = 0.35 * (1.0 - m);
}
"""
var _blood_mats: Array[ShaderMaterial] = []


func enable_blood() -> void:
	var sh := Shader.new()
	sh.code = BLOOD_SKIN
	for m in meshes:
		var mi: MeshInstance3D = m[0]
		if not String(mi.name).to_lower().contains("skin"):
			continue
		for i in mi.mesh.get_surface_count():
			var src := mi.get_active_material(i) as BaseMaterial3D
			var sm := ShaderMaterial.new()
			sm.shader = sh
			if src:
				if src.albedo_texture:
					sm.set_shader_parameter("albedo_tex", src.albedo_texture)
					sm.set_shader_parameter("has_tex", true)
				sm.set_shader_parameter("albedo", src.albedo_color)
			mi.set_surface_override_material(i, sm)
			_blood_mats.append(sm)


func _update_blood() -> void:
	if _blood_mats.is_empty():
		return
	var g := func(n: String) -> Vector3:
		return skel.global_transform * skel.get_bone_global_pose(skel.find_bone(n)).origin
	var hl0: Vector3 = g.call("hand_l")
	var hr0: Vector3 = g.call("hand_r")
	var hl1: Vector3 = hl0 + (g.call("middle_02_l") - hl0) * 1.05
	var hr1: Vector3 = hr0 + (g.call("middle_02_r") - hr0) * 1.05
	for sm in _blood_mats:
		sm.set_shader_parameter("hl0", hl0)
		sm.set_shader_parameter("hl1", hl1)
		sm.set_shader_parameter("hr0", hr0)
		sm.set_shader_parameter("hr1", hr1)


## Волосы: точка на пружине догоняет центр головы; разница — смещение прядей (до 1.8 см у кончиков). Рывок головы,
## кочка, поворот — пряди отстают и качаются обратно.
func _update_hair(delta: float) -> void:
	if _hair.is_empty() or _head < 0:
		return
	var hg := skel.global_transform * skel.get_bone_global_pose(_head)
	var c := hg.origin + hg.basis.y.normalized() * 0.09
	if not _hair_init or _hair_p.distance_to(c) > 0.3:   # первый кадр или героя переставили — без рывка
		_hair_p = c
		_hair_v = Vector3.ZERO
		_hair_init = true
	var dt := minf(delta, 0.05)
	_hair_v += ((c - _hair_p) * 110.0 - _hair_v * 13.0 + Vector3(0, -0.15, 0)) * dt
	_hair_p += _hair_v * dt
	var lag := (_hair_p - c).limit_length(0.018)
	for hm in _hair:
		hm.set_shader_parameter("head_c", c)
		hm.set_shader_parameter("sway", lag)
		hm.set_shader_parameter("breeze", breeze)


## Толчок прядям (рывок машины, удар, резкий поворот головы): м/с.
func hair_kick(v: Vector3) -> void:
	_hair_v += v
