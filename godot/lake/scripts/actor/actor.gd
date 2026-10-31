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
var blink_t := 2.0
var expr := {}                  # постоянное выражение: {форма → вес} (страх, слёзы…)
var _head := -1
var _neck := -1
var clip := ""

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
		if nm.contains("hair") or nm.contains("brows") or nm.contains("lashes"):   # пряди — вырезкой по альфе, мягкий порог, обе стороны
			for i in m.mesh.get_surface_count():
				var src := m.get_active_material(i) as BaseMaterial3D
				if src:
					var hm := src.duplicate() as BaseMaterial3D
					hm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA_SCISSOR
					hm.alpha_scissor_threshold = 0.18 if nm.contains("hair") else 0.25
					hm.cull_mode = BaseMaterial3D.CULL_DISABLED
					hm.roughness = 0.7
					m.set_surface_override_material(i, hm)
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
	clip = c
	ap.play(c)
	ap.seek(at, true)


## Реплика: губы по буквам текста за dur секунд.
func say(text: String, dur: float) -> void:
	talk_text = text.to_lower()
	talk_t = 0.0
	talk_dur = maxf(0.4, dur)


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
	# губы: буква в текущий момент реплики, челюсть — по слогам
	if talk_t >= 0.0:
		talk_t += delta
		var f := talk_t / talk_dur
		if f >= 1.0:
			talk_t = -1.0
		else:
			var i := int(f * talk_text.length())
			var ch := talk_text[i] if i < talk_text.length() else " "
			var v: String = VIS.get(ch, "")
			var open := 0.0 if ch in [" ", ".", ",", "!", "?", "…", "-", "—"] else 0.35 + 0.25 * sin(talk_t * 21.0)
			if v != "":
				W[v] = 0.7
			W["jawOpen"] = open * (0.5 if v in ["viseme_PP", "viseme_FF"] else 1.0)
	for m in meshes:
		var mi: MeshInstance3D = m[0]
		var d: Dictionary = m[1]
		for bn in d:
			mi.set_blend_shape_value(d[bn], W.get(bn, 0.0))
	_update_blood()
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


## Кровь на руках (сцена 1: «липкие, тёмно-красные, кровь под ногтями»): материал кожи заменяется своим — та же текстура,
## а у кистей (отрезок запястье → середина ладони) кожа темнеет до засохшей крови пятнами, свежая — с блеском.
const BLOOD_SKIN := """
shader_type spatial;
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
	m *= smoothstep(0.32, 0.55, n3(vW * 46.0 + 1.7) * 0.7 + n);   // пятнами: между ними — кожа
	float fresh = smoothstep(0.6, 0.8, n3(vW * 33.0 + 4.0));
	c = mix(c, mix(vec3(0.075, 0.012, 0.01), vec3(0.16, 0.012, 0.01), fresh), m * 0.85);
	ALBEDO = c;
	ROUGHNESS = mix(rough, mix(0.5, 0.18, fresh), m);
	SPECULAR = 0.5;
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
