class_name Actor
extends Node3D
## Герой: модель MakeHuman (CC0), скелет «переодет» под клипы Quaternius / мокап (HumanRig), клипы движений (AnimLib).
## Каждый кадр, по слоям (каждый слой пишет поверх результата предыдущего, ничего не накапливается):
##   1) ход (walk_to): плавный поворот, разгон и торможение, шаг клипа подстроен под скорость — стопы не скользят;
##   2) клипы (AnimationPlayer, вручную): плавное смешивание при смене клипа, повтор циклов, удержание позы;
##   3) жизнь тела (Manner): дыхание, перенос веса, микродвижения головы, осанка героя и его состояния;
##   4) взгляд: внимание (цель сцены → говорящий рядом → свои мысли), голова и грудь поворачиваются плавно, глаза ведут;
##   5) руки: тянуться к точке, махать, держать предмет (пальцы обхватывают);
##   6) лицо: моргание по характеру, глаза на цель, выражение плавно, брови на ударных слогах, губы по озвучке;
##   7) волосы: кости и пружины (HairRig) — инерция, ветер, столкновения с телом.

var key := ""
var model: Node3D
var skel: Skeleton3D
var ap: AnimationPlayer
var meshes: Array = []          # [MeshInstance3D, {форма → индекс}, {форма → прошлый вес}]
var _shape_to := {}             # форма → [[MeshInstance3D, индекс], …] (все сетки, где она есть)
var _shape_last := {}           # форма → вес, записанный в сетки (только ненулевые и недавно обнулённые)
var _post_cache := {}           # состояние → осанка (манера + состояние), чтобы не собирать словарь каждый кадр
var look_target = null          # Vector3 (мир) или null
var look_actor: Actor = null    # смотреть на голову другого героя (следует за ним)
var listen_to: Actor = null     # говорит рядом — смотреть на него, если сцена не задала взгляд (ставит game.gd)
var look_w := 0.0               # доля поворота головы к цели (0…1)
var talk_text := ""
var talk_t := -1.0
var talk_dur := 0.0
var talk_words: Array = []      # голос: [[начало, слово], …] — буквы губ идут по словам озвучки
var talk_env := ""              # голос: громкость по 40 мс (0…9) — челюсть открывается по звуку
var blink_t := 2.0              # до следующего моргания, с (1e9 — не моргает: фото, двойник, мёртвый)
var expr := {}                  # выражение лица, к которому лицо плавно идёт: {форма → вес}
var expr_name := ""
var mood := ""                  # состояние поверх манеры: tense, afraid, pain, other («второй он»), sleep, dead
var clip := ""
var breeze := 0.0               # ветер снаружи (0 — в машине, 1 — буран)
var wind_dir := Vector3(1, 0, 0.3)
var wave := 0.0                 # машет обеими руками над головой (0…1)
var _wave_w := 0.0
var _wave_t := 0.0
var reach := {}:                # кисть — в точку (мир): {"_r": Vector3, "_l": Vector3}; двухзвенная IK: плечо и локоть
	set(v):
		reach = v
		reach_pole = {}         # локоть и ладонь задаются заново к каждой новой цели — не тянутся из прошлой сцены
		reach_palm = {}
var reach_pole := {}            # куда смотрит локоть (мир): {"_r": Vector3}; по умолчанию — вниз, наружу и чуть назад
var reach_palm := {}            # куда повёрнута ладонь (мир): {"_r": Vector3}; по умолчанию — как в клипе
var _reach_w := 0.0
var _palm_tw := {}              # поворот ладони прошлого кадра (рад) — для непрерывности
var grip := {}                  # держит предмет: {"_r": 0…1, "_l": 0…1} — пальцы обхватывают
var _grip_w := {"_r": 0.0, "_l": 0.0}
var walking := false
var _hair: Array[ShaderMaterial] = []
var hair_sim: SpringBoneSimulator3D = null
var skirt_sim: SpringBoneSimulator3D = null   # подол пальто, юбки, платья (SkirtRig)
var _kick := Vector3.ZERO       # толчок прядям (рывок машины): затухает
var _m: Dictionary = {}         # манера героя
var _bone := {}                 # имя кости → индекс
var _base := {}                 # кость → поворот клипа в этом кадре (до слоёв)
var _snap := 2                  # первые кадры после создания/расстановки — без смешивания
var _t := 0.0
var _seed := 0.0
# взгляд
var _ay := 0.0                  # поворот головы к цели (рад, вокруг вертикали тела) и его скорость
var _ayv := 0.0
var _ap := 0.0                  # наклон головы к цели (рад)
var _apv := 0.0
var _avert_t := 0.0             # отвести взгляд: сколько ещё, куда
var _avert := Vector2.ZERO
var _avert_cd := 3.0
var _eye := Vector2.ZERO
var _eye_to := Vector2.ZERO
var _sacc_t := 0.6
# лицо
var _face: Dictionary = {}      # текущие веса выражения (плавно к expr)
var _vis: Dictionary = {}       # текущие веса губ
var _jaw := 0.0
var _smile_t := 0.0
var _smile_cd := 6.0
var _emph := 0.0                # ударение в речи (брови)
# ход
var _goal := Vector3.ZERO
var _speed := 1.15
var _v := 0.0
var _then := "Idle"
var _face_to = null             # поворот по приходу (рад) или null
var _turn_to = null             # плавный поворот на месте (рад)
var _walk_clip := ""
var leg := 0.9                  # длина ноги (м): шаг клипа под рост героя
var _skip := 0
var _acc := 0.0
var _ginv := {}                 # кость → обратный мировой базис (один раз за кадр: слоям хватает, углы малые)

const HAIR_SHADER := """
shader_type spatial;
render_mode cull_disabled;
uniform sampler2D albedo_tex : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform vec4 albedo : source_color = vec4(1.0);
uniform float cut = 0.18;
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

## клипы-циклы (повторяются); остальные — одно действие, по окончании герой держит последнюю позу
const LOOPS := ["Idle", "Idle_FoldArms", "Idle_Lantern", "Idle_No", "Idle_Rail", "Idle_Rail_Call", "Idle_Talking", "Idle_TalkingPhone",
	"Idle_Torch", "Yes", "Walk", "Walk_Carry", "Walk_Formal", "Rk_Walk", "Jog_Fwd", "Sprint", "Crouch_Idle", "Crouch_Fwd", "Driving",
	"Sitting_Idle", "Sitting_Talking", "Fixing_Kneeling", "Farm_Harvest", "Farm_PlantSeed", "Farm_Watering", "Cmu_Mop",
	"Zombie_Idle", "Zombie_Walk_Fwd", "Zombie_Scratch", "Rk_Arguing", "Rk_Chatting", "Rk_Chatting2", "Rk_Conversation", "Rk_LookAround",
	"Rk_Phone", "Rk_Pointing", "Rk_SitIdle", "Rk_SitIdle2", "Rk_SitTalk", "Rk_SitTalk2", "Rk_Watching"]
## естественная скорость шага клипа на метр длины ноги (м/с): у одного клипа шаг пропорционален ноге, темп тот же —
## поэтому Айрис (нога 0,59 м) в «Rk_Walk» идёт 0,85 м/с, Ной (0,95 м) — 1,38 м/с. Замер: scenes/dev/anim_lab.tscn --lab walk.
const STRIDE := {"Rk_Walk": 1.45, "Walk": 1.13, "Walk_Formal": 1.12, "Walk_Carry": 0.73, "Crouch_Fwd": 0.96, "Jog_Fwd": 2.7,
	"Sprint": 4.4, "Zombie_Walk_Fwd": 1.10}
## классы клипов для слоя «жизнь»: стоит — всё; сидит / на корточках — дыхание и голова; идёт, действует — дыхание
const STAND := ["Idle", "Idle_FoldArms", "Idle_Lantern", "Idle_No", "Idle_Rail", "Idle_Rail_Call", "Idle_Talking", "Idle_TalkingPhone",
	"Idle_Torch", "Yes", "Rk_Arguing", "Rk_Chatting", "Rk_Chatting2", "Rk_Conversation", "Rk_LookAround", "Rk_Phone", "Rk_Pointing", "Rk_Watching"]
const SEAT := ["Rk_SitIdle", "Rk_SitIdle2", "Rk_SitTalk", "Rk_SitTalk2", "Sitting_Idle", "Sitting_Talking", "Driving", "Crouch_Idle", "Fixing_Kneeling"]
const LIE := ["LayToIdle", "Death01"]

## буква → форма рта (как viseme в Hush Till Six)
const VIS := {"а": "viseme_aa", "я": "viseme_aa", "a": "viseme_aa", "о": "viseme_O", "ё": "viseme_O", "o": "viseme_O", "у": "viseme_U",
	"ю": "viseme_U", "u": "viseme_U", "w": "viseme_U", "э": "viseme_E", "е": "viseme_E", "e": "viseme_E", "и": "viseme_I", "ы": "viseme_I",
	"i": "viseme_I", "y": "viseme_I", "м": "viseme_PP", "б": "viseme_PP", "п": "viseme_PP", "m": "viseme_PP", "b": "viseme_PP", "p": "viseme_PP",
	"ф": "viseme_FF", "в": "viseme_FF", "f": "viseme_FF", "v": "viseme_FF", "с": "viseme_SS", "з": "viseme_SS", "ц": "viseme_SS", "s": "viseme_SS",
	"z": "viseme_SS", "ш": "viseme_CH", "ж": "viseme_CH", "ч": "viseme_CH", "щ": "viseme_CH", "т": "viseme_DD", "д": "viseme_DD", "н": "viseme_nn",
	"t": "viseme_DD", "d": "viseme_DD", "n": "viseme_nn", "л": "viseme_nn", "l": "viseme_nn", "к": "viseme_kk", "г": "viseme_kk", "х": "viseme_kk",
	"k": "viseme_kk", "g": "viseme_kk", "h": "viseme_kk", "р": "viseme_RR", "r": "viseme_RR"}
const VIS_ALL := ["viseme_aa", "viseme_CH", "viseme_DD", "viseme_E", "viseme_FF", "viseme_I", "viseme_kk", "viseme_nn", "viseme_O",
	"viseme_PP", "viseme_RR", "viseme_SS", "viseme_TH", "viseme_U"]
const LAYER_BONES := ["pelvis", "spine_01", "spine_02", "spine_03", "neck_01", "head", "clavicle_l", "clavicle_r",
	"upperarm_l", "upperarm_r", "lowerarm_l", "lowerarm_r", "hand_l", "hand_r"]

## Замер стоимости героев (стенд scenes/dev/anim_lab): сумма микросекунд всех героев и число кадров.
static var prof_us := 0
static var prof_frames := 0
static var _prof_frame := -1
static var prof_sections := false
static var stride_override := 0.0   # стенд: подбор естественной скорости шага
static var sec_us := {}
## камера, по которой решается, виден ли герой (дальних и невидимых считаем реже)
static var view_cam: Camera3D = null


static func _hair_shader() -> Shader:
	if _hair_sh == null:
		_hair_sh = Shader.new()
		_hair_sh.code = HAIR_SHADER
	return _hair_sh


## Заранее подгрузить модели героев в фоне (меню, начало главы): потом Actor.make — миллисекунды, а не 0,3–0,5 с посреди сцены.
static func prefetch(keys: Array) -> void:
	for k in keys:
		var path := "res://assets/chars/%s.glb" % k
		if ResourceLoader.exists(path) and not ResourceLoader.has_cached(path):
			ResourceLoader.load_threaded_request(path)


static func _load_model(path: String) -> PackedScene:
	if ResourceLoader.load_threaded_get_status(path) != ResourceLoader.THREAD_LOAD_INVALID_RESOURCE:
		var r := ResourceLoader.load_threaded_get(path)
		if r is PackedScene:
			return r
	return load(path) as PackedScene


static func make(k: String, file: String) -> Actor:
	var a := Actor.new()
	a.key = k
	a.name = k
	a._m = Manner.of(k)
	a._seed = float(hash(k) % 1000) * 0.37
	a.blink_t = randf_range(0.5, 3.0)
	var path := file if file.begins_with("res://") else ("res://assets/chars/%s.glb" % file if ResourceLoader.exists("res://assets/chars/%s.glb" % file) else "res://assets/models/%s.glb" % file)
	a.model = _load_model(path).instantiate()
	a.add_child(a.model)
	var rig := HumanRig.rebase(a.model)
	a.skel = rig.skel
	a.ap = AnimationPlayer.new()
	a.skel.get_parent().add_child(a.ap)   # дорожки клипов — «Skeleton3D:кость» от родителя скелета
	a.ap.add_animation_library("", AnimLib.library(rig.k))
	a.ap.callback_mode_process = AnimationMixer.ANIMATION_CALLBACK_MODE_PROCESS_MANUAL
	a.ap.deterministic = true            # при смешивании кости, которых нет в новом клипе, плавно уходят в покой
	ClothFit.apply(a, file)
	var hair_mi: MeshInstance3D = null
	for mi in a.model.find_children("*", "MeshInstance3D", true, false):
		var m := mi as MeshInstance3D
		var nm := String(m.name).to_lower()
		for i in m.mesh.get_surface_count():
			var src := m.get_active_material(i) as BaseMaterial3D
			if src == null:
				continue
			if nm.contains("hair"):   # волосы: свой материал — вырезка по альфе, блик вдоль прядей; движение — кости (HairRig)
				var hm := ShaderMaterial.new()
				hm.shader = _hair_shader()
				hm.set_shader_parameter("albedo_tex", src.albedo_texture)
				hm.set_shader_parameter("albedo", src.albedo_color)
				m.set_surface_override_material(i, hm)
				a._hair.append(hm)
				hair_mi = m
				continue
			# MakeHuman отдаёт кожу и одежду «полупрозрачными» — такие не получают мягких теней (SSAO) и рассеяния в коже;
			# делаем непрозрачными (вырезка по альфе — где у ткани дырки), брови и ресницы — тонкой вырезкой с двух сторон
			var mm := src.duplicate() as BaseMaterial3D
			if nm.contains("brows") or nm.contains("lashes"):
				mm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA_SCISSOR
				mm.alpha_scissor_threshold = 0.25
				mm.cull_mode = BaseMaterial3D.CULL_DISABLED
				mm.normal_enabled = false   # у бровей MakeHuman в слоте нормалей — сама цветная текстура (бугры на лбу)
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
			Fabric.tune(mm, nm)   # ткань по имени сетки: шерсть, трикотаж, кожа, нейлон, дождевик… (Fabric)
			m.set_surface_override_material(i, mm)
	if hair_mi:
		a.hair_sim = HairRig.build(a, hair_mi)
	a.skirt_sim = SkirtRig.build(a)
	for mi in a.model.find_children("*", "MeshInstance3D", true, false):
		var m := mi as MeshInstance3D
		if m.mesh is ArrayMesh and (m.mesh as ArrayMesh).get_blend_shape_count() > 0:
			var d := {}
			var am := m.mesh as ArrayMesh
			for i in am.get_blend_shape_count():
				d[String(am.get_blend_shape_name(i))] = i
			# у волос формы лица нужны только для бровей и век (чёлка над бровями); губы — нет
			if String(m.name).to_lower().contains("hair"):
				for v in VIS_ALL + ["jawOpen", "mouthClose", "mouthFunnel", "mouthPucker"]:
					d.erase(v)
			a.meshes.append([m, d, {}])
			for sn in d:
				if not a._shape_to.has(sn):
					a._shape_to[sn] = []
				(a._shape_to[sn] as Array).append([m, int(d[sn])])
	for b in a.skel.get_bone_count():
		a._bone[a.skel.get_bone_name(b)] = b
	var to_a := HumanRig._xform_to(a.skel, a)
	var g := func(n: String) -> Vector3: return to_a * a.skel.get_bone_global_rest(a._bi(n)).origin
	if a._bi("thigh_l") >= 0 and a._bi("calf_l") >= 0 and a._bi("foot_l") >= 0:
		a.leg = (g.call("thigh_l") as Vector3).distance_to(g.call("calf_l")) + (g.call("calf_l") as Vector3).distance_to(g.call("foot_l"))
	return a


func _bi(n: String) -> int:
	return int(_bone.get(n, -1))


## Клип. blend < 0 — смешивание по умолчанию (0,3 с), 0 — сразу (расстановка, склейка, удержание позы).
func play(c: String, at := 0.0, blend := -1.0) -> void:
	if not ap.has_animation(c):
		push_warning("нет клипа " + c)
		return
	var an := ap.get_animation(c)
	an.loop_mode = Animation.LOOP_LINEAR if c in LOOPS else Animation.LOOP_NONE
	var b := blend
	if b < 0.0:
		b = _blend_time(clip, c)
	if _snap > 0 or not is_inside_tree() or not is_visible_in_tree():
		b = 0.0
	if c == clip and ap.is_playing() and b > 0.0 and at <= 0.0:   # тот же клип: продолжить (повтор play каждую реплику не дёргает позу)
		return
	clip = c
	if c != _walk_clip:
		ap.speed_scale = 1.0
	if b <= 0.0:
		skel.reset_bone_poses()
		ap.play(c, 0.0)
		ap.seek(at, true)
	else:
		ap.play(c, b)
		if at > 0.0:
			ap.seek(at, true)
	_base.clear()


func _blend_time(from: String, to: String) -> float:
	if from == "":
		return 0.0
	if to in ["Hit_Chest", "Hit_Head", "Hit_Knockback", "Punch_Jab", "Death01"]:
		return 0.12
	if to in ["Sitting_Enter", "Sitting_Exit", "LayToIdle"] or from in SEAT or to in SEAT:
		return 0.45
	if to in STRIDE or from in STRIDE:
		return 0.28
	return 0.35


## Встал сюда (расстановка в начале плана): поза сразу, без перетекания, волосы — без рывка.
func placed() -> void:
	_snap = 2
	walking = false
	_turn_to = null
	if hair_sim:
		hair_sim.reset()
	if skirt_sim:
		skirt_sim.reset()


## Реплика: губы по буквам текста за dur секунд. С озвучкой — words ([[начало, слово]]) и env (громкость по 40 мс).
## Реплика оборвана (пропуск, конец сцены): губы закрываются сразу, а не шевелятся молча до конца строки.
func shut() -> void:
	talk_t = -1.0


func say(text: String, dur: float, words: Array = [], env := "") -> void:
	talk_text = text.to_lower()
	talk_t = 0.0
	talk_dur = maxf(0.4, dur)
	talk_words = words
	talk_env = env


func set_expr(nm: String, w: Dictionary) -> void:
	expr_name = nm
	expr = w.duplicate()
	if nm == "dead":
		mood = "dead"
		blink_t = 1e9
	elif nm in ["fear", "shock"]:
		mood = "afraid"
	elif nm in ["pain", "wince"]:
		mood = "pain"
	elif nm == "sleep":
		mood = "sleep"
	elif mood in ["afraid", "pain", "sleep"]:
		mood = ""


func set_mood(m: String) -> void:
	mood = m
	if m == "dead":
		blink_t = 1e9


## Пройти к точке: плавный разворот, разгон, торможение у цели; шаг клипа — под скорость (стопы не едут).
func walk_to(dst: Vector3, speed := 1.15, step_clip := "Rk_Walk", then := "Idle", face = null) -> void:
	_goal = dst
	_speed = speed
	_then = then
	_face_to = deg_to_rad(float(face)) if face != null else null
	_walk_clip = step_clip
	walking = true
	_turn_to = null
	play(step_clip, 0.0)


func stop_walk() -> void:
	walking = false
	_v = 0.0
	ap.speed_scale = 1.0


func _process(delta: float) -> void:
	var t0 := Time.get_ticks_usec()
	_tick(delta)
	prof_us += Time.get_ticks_usec() - t0
	var fr := Engine.get_process_frames()
	if fr != _prof_frame:
		_prof_frame = fr
		prof_frames += 1


## Видим ли героя камерой и насколько он далеко (0 — у камеры, 1 — далеко, 2 — не в кадре).
func _view() -> float:
	if view_cam == null or not is_instance_valid(view_cam) or not view_cam.is_inside_tree():
		return 0.0
	var p := global_position + Vector3(0, 1.0, 0)
	var dist := view_cam.global_position.distance_to(p)
	if not is_visible_in_tree() or (not view_cam.is_position_in_frustum(p) and dist > 2.0):
		return 2.0
	return clampf((dist - 6.0) / 14.0, 0.0, 1.0)


func _tick(delta: float) -> void:
	_t += delta
	_update_walk(delta)
	# дальние и невидимые: анимация реже (каждый 2-й / 4-й кадр), лицо и мелочи — пропуск; у камеры — каждый кадр
	var vw := _view()
	_acc += delta
	var every := 1 if vw < 0.5 else (2 if vw <= 1.0 else 4)
	_skip += 1
	if _skip < every and _snap <= 0:
		return
	_skip = 0
	var dt := _acc
	_acc = 0.0
	var qa := Time.get_ticks_usec()
	if ap.is_playing():
		ap.advance(dt)
		if prof_sections:
			sec_us["клип"] = int(sec_us.get("клип", 0)) + Time.get_ticks_usec() - qa
		_capture_base()
	else:
		_restore_base()   # поза удержана (клип кончился или на паузе): слои ниже пишут поверх неё, а не поверх прошлого кадра
	if skirt_sim:
		SkirtRig.drive(skel, skirt_sim)   # поводки подола — за бёдрами клипа (до пружин)
	if _snap > 0:
		_snap -= 1
	if vw > 1.5:   # не в кадре: позу держим, остальное не считаем
		return
	var q0 := Time.get_ticks_usec()
	_grip_scan(dt)
	_cache_bases()
	_life(dt)
	var q1 := Time.get_ticks_usec()
	_gaze(dt)
	var q2 := Time.get_ticks_usec()
	_update_wave(dt)
	_update_reach(dt)
	_update_grip(dt)
	var q3 := Time.get_ticks_usec()
	_face_update(dt)
	var q4 := Time.get_ticks_usec()
	_update_blood()
	_update_hair(dt)
	if prof_sections:
		sec_us["тело"] = int(sec_us.get("тело", 0)) + q1 - q0
		sec_us["взгляд"] = int(sec_us.get("взгляд", 0)) + q2 - q1
		sec_us["руки"] = int(sec_us.get("руки", 0)) + q3 - q2
		sec_us["лицо"] = int(sec_us.get("лицо", 0)) + q4 - q3


func _capture_base() -> void:
	for n in LAYER_BONES:
		var b := _bi(n)
		if b >= 0:
			_base[b] = skel.get_bone_pose_rotation(b)


func _restore_base() -> void:
	if _base.is_empty():
		_capture_base()
		return
	for b in _base:
		skel.set_bone_pose_rotation(int(b), _base[b])


func _cache_bases() -> void:
	_ginv.clear()
	var sgt := skel.global_transform
	for n in LAYER_BONES:
		var b := _bi(n)
		if b >= 0:
			_ginv[b] = (sgt * skel.get_bone_global_pose(b)).basis.orthonormalized().inverse()


## Поворот кости вокруг оси в мировом пространстве (поверх текущей позы).
func _rot(b: int, axis_w: Vector3, ang: float) -> void:
	if b < 0 or absf(ang) < 1e-6:
		return
	var inv: Basis
	if _ginv.has(b):
		inv = _ginv[b]
	else:
		inv = (skel.global_transform * skel.get_bone_global_pose(b)).basis.orthonormalized().inverse()
	skel.set_bone_pose_rotation(b, skel.get_bone_pose_rotation(b) * Quaternion((inv * axis_w).normalized(), ang))


## Плавный шум (−1…1) с периодом около p секунд: сумма синусов с несоразмерными частотами — не повторяется.
func _noise(p: float, ofs: float) -> float:
	var w := TAU / maxf(p, 0.1)
	var s := _seed + ofs
	return (sin(_t * w + s) * 0.55 + sin(_t * w * 1.73 + s * 2.1) * 0.3 + sin(_t * w * 0.41 + s * 3.7) * 0.15)


func _mv(k: String, d: float) -> float:
	var mo: Dictionary = Manner.MOOD.get(mood, {})
	return float(_m.get(k, d)) * float(mo.get(k, 1.0))


# ---------------------------------------------------------------------------------------------------------------- ход

func _update_walk(delta: float) -> void:
	if _turn_to != null and not walking:   # плавный поворот на месте (по приходу)
		var tgt: float = _turn_to
		rotation.y = lerp_angle(rotation.y, tgt, 1.0 - exp(-delta * 5.0 * float(_m.get("tempo", 1.0))))
		if absf(wrapf(tgt - rotation.y, -PI, PI)) < 0.01:
			rotation.y = tgt
			_turn_to = null
	if not walking:
		return
	var d := Vector3(_goal.x - position.x, 0.0, _goal.z - position.z)
	var dist := d.length()
	var want_yaw := atan2(d.x, d.z) if dist > 0.05 else rotation.y
	var err := wrapf(want_yaw - rotation.y, -PI, PI)
	rotation.y += clampf(err * (1.0 - exp(-delta * 9.0)), -delta * 4.0, delta * 4.0)
	# скорость: разгон ~2 м/с², торможение к цели, медленнее, пока разворачивается
	var vmax := _speed * clampf(1.0 - (absf(err) - 0.5) / 1.2, 0.25, 1.0)
	vmax = minf(vmax, sqrt(2.0 * 1.8 * maxf(dist - 0.02, 0.0)) + 0.05)
	_v = move_toward(_v, vmax, delta * (2.2 if vmax > _v else 3.0))
	var step := _v * delta
	if dist <= maxf(step, 0.025):
		position.x = _goal.x
		position.z = _goal.z
		walking = false
		_v = 0.0
		ap.speed_scale = 1.0
		play(_then, 0.0)
		if _face_to != null:
			_turn_to = _face_to
		return
	var fwd := Vector3(sin(rotation.y), 0.0, cos(rotation.y))
	position += (fwd * 0.65 + d.normalized() * 0.35).normalized() * step
	position.y = move_toward(position.y, _goal.y, delta * 0.8)
	var nat: float = float(STRIDE.get(clip, 0.0)) * leg
	if stride_override > 0.0:
		nat = stride_override
	if nat > 0.0:
		ap.speed_scale = clampf(_v / nat, 0.55, 1.45)


# ---------------------------------------------------------------------------------------------------------------- тело

func _life(dt: float) -> void:
	if mood == "dead":
		return
	var stand := clip in STAND
	var seat := clip in SEAT
	var lie := clip in LIE
	var moving := walking or clip in STRIDE
	var basis := global_transform.basis.orthonormalized()
	var up := basis.y
	var fwd := basis.z
	var tilt := basis.x   # ось наклона: поворот на + — грудь, шея, голова вперёд/вниз (проверено: вокруг −X было бы назад)
	if not _post_cache.has(mood):
		var pc: Dictionary = (_m.get("posture", {}) as Dictionary).duplicate()
		var mpost: Dictionary = (Manner.MOOD.get(mood, {}) as Dictionary).get("posture", {})
		for k in mpost:
			pc[k] = float(pc.get(k, 0.0)) + float(mpost[k])
		_post_cache[mood] = pc
	var post: Dictionary = _post_cache[mood]
	# доверие и близость (ставит game.gd): Айрис рядом с теми, кому доверяет, Дэнни рядом с Айрис — плечи опускаются
	var relax := float(get_meta("relax", 0.0))
	var pw := 1.0 if (stand or seat) else (0.5 if moving else 0.0)
	pw *= 1.0 - 0.6 * relax
	# осанка: грудь, шея, голова (+ вперёд/вниз), плечи (+ вверх)
	_rot(_bi("spine_03"), tilt, deg_to_rad(float(post.get("spine", 0.0))) * pw)
	_rot(_bi("neck_01"), tilt, deg_to_rad(float(post.get("neck", 0.0))) * pw)
	_rot(_bi("head"), tilt, deg_to_rad(float(post.get("head", 0.0))) * pw)
	match String(get_meta("carrying", "")):
		"front":   # несёт человека на руках: поясница откинута назад под весом, шея и голова — вперёд, смотрит под ноги
			_rot(_bi("spine_01"), tilt, -deg_to_rad(3.0))
			_rot(_bi("spine_02"), tilt, -deg_to_rad(4.0))
			_rot(_bi("spine_03"), tilt, -deg_to_rad(2.0))
			_rot(_bi("neck_01"), tilt, deg_to_rad(5.0))
			_rot(_bi("head"), tilt, deg_to_rad(4.0))
		"back":    # несёт на спине: корпус вперёд, голова поднята — смотрит на дорогу
			_rot(_bi("spine_01"), tilt, deg_to_rad(6.0))
			_rot(_bi("spine_02"), tilt, deg_to_rad(5.0))
			_rot(_bi("spine_03"), tilt, deg_to_rad(4.0))
			_rot(_bi("neck_01"), tilt, -deg_to_rad(6.0))
			_rot(_bi("head"), tilt, -deg_to_rad(6.0))
	var sh := deg_to_rad(float(post.get("shoulders", 0.0))) * pw   # + — плечи вверх
	_rot(_bi("clavicle_l"), fwd, sh)
	_rot(_bi("clavicle_r"), fwd, -sh)
	# дыхание: вдох короче выдоха; грудь чуть раскрывается, плечи приподнимаются
	var rate := _mv("breath", 0.26)
	var amp := _mv("amp", 1.0) * (0.6 if moving else 1.0) * (0.7 if lie else 1.0)
	var ph := fposmod(_t * rate + _seed, 1.0)
	var br := smoothstep(0.0, 0.4, ph) * (1.0 - smoothstep(0.4, 1.0, ph))
	_rot(_bi("spine_02"), tilt, -deg_to_rad(0.45) * br * amp)
	_rot(_bi("spine_03"), tilt, -deg_to_rad(0.85) * br * amp)
	_rot(_bi("clavicle_l"), fwd, deg_to_rad(0.7) * br * amp)
	_rot(_bi("clavicle_r"), fwd, -deg_to_rad(0.7) * br * amp)
	if not stand:
		return
	# перенос веса: медленно с ноги на ногу — таз наклоняется, грудь компенсирует (голова остаётся ровно)
	var sw := _mv("sway", 0.5) * (1.0 - relax * 0.3)
	var s := _noise(float(_m.get("sway_t", 8.0)), 1.3) * sw
	_rot(_bi("pelvis"), fwd, deg_to_rad(0.9) * s)
	_rot(_bi("spine_01"), fwd, -deg_to_rad(0.55) * s)
	_rot(_bi("spine_02"), fwd, -deg_to_rad(0.3) * s)
	_rot(_bi("pelvis"), up, deg_to_rad(0.5) * _noise(float(_m.get("sway_t", 8.0)) * 1.4, 4.2) * sw)


# ---------------------------------------------------------------------------------------------------------------- взгляд

func _attention() -> Variant:
	if look_actor and is_instance_valid(look_actor):
		return look_actor.head_pos()
	if look_target != null:
		return look_target
	if listen_to and is_instance_valid(listen_to) and listen_to != self and bool(_m.get("listen", true)) \
			and listen_to.is_visible_in_tree() and listen_to.global_position.distance_to(global_position) < 6.0:
		return listen_to.head_pos()
	return null


func head_pos() -> Vector3:
	var h := _bi("head")
	if h < 0:
		return global_position + Vector3(0, 1.6, 0)
	return skel.global_transform * skel.get_bone_global_pose(h).origin + Vector3(0, 0.07, 0)


func _gaze(dt: float) -> void:
	var h := _bi("head")
	if h < 0:
		return
	var basis := global_transform.basis.orthonormalized()
	var up := basis.y
	var fwd := basis.z
	var hg := skel.global_transform * skel.get_bone_global_pose(h)
	# куда смотрит голова по клипу (относительно тела)
	var hf := hg.basis.z.normalized()                      # лицо MakeHuman смотрит по +Z кости головы
	var cy := atan2(hf.dot(-basis.x), hf.dot(fwd))
	var cp := asin(clampf(hf.dot(up), -1.0, 1.0))
	var att: Variant = _attention()
	var ty := cy
	var tp := cp
	var has := att != null and mood != "dead"
	if has:
		var tl := ((att as Vector3) - hg.origin).normalized()
		ty = atan2(tl.dot(-basis.x), tl.dot(fwd))
		tp = asin(clampf(tl.dot(up), -1.0, 1.0))
		# отвести взгляд и вернуться (собеседник, мысли): у каждого по характеру
		_avert_cd -= dt
		if _avert_t > 0.0:
			_avert_t -= dt
		elif _avert_cd <= 0.0 and mood != "other":
			_avert_cd = randf_range(2.5, 7.0)
			if randf() < float(_m.get("avert", 0.25)):
				_avert_t = randf_range(0.6, 1.6)
				var dn := float(_m.get("down", 0.5))
				_avert = Vector2(randf_range(-0.35, 0.35) * (1.0 - dn), -randf_range(0.12, 0.3) * dn)
		if _avert_t > 0.0:
			ty += _avert.x
			tp += _avert.y
	# микродвижения головы: еле заметные, медленные
	var ha := _mv("head", 0.6)
	ty += deg_to_rad(1.2) * ha * _noise(5.5, 7.1)
	tp += deg_to_rad(0.8) * ha * _noise(6.5, 9.3)
	ty = clampf(ty, -1.45, 1.45)
	tp = clampf(tp, -0.7, 0.6)
	# плавно, без рывка: «пружина» с критическим затуханием; темп — по характеру
	var w := 7.5 * _mv("tempo", 1.0)
	var k := minf(dt, 0.05)
	_ayv += (-(2.0 * w) * _ayv - w * w * wrapf(_ay - ty, -PI, PI)) * k
	_ay += _ayv * k
	_apv += (-(2.0 * w) * _apv - w * w * (_ap - tp)) * k
	_ap += _apv * k
	if _snap > 0:
		_ay = ty
		_ap = tp
		_ayv = 0.0
		_apv = 0.0
	look_w = move_toward(look_w, 1.0 if has else 0.0, dt * 3.0)
	var dy := wrapf(_ay - cy, -PI, PI)
	var dp := _ap - cp
	# поворот раскладывается: грудь берёт то, что больше 45°, шея и голова — остальное
	var chest := clampf(absf(dy) - deg_to_rad(45.0), 0.0, deg_to_rad(30.0)) * signf(dy)
	var rest := dy - chest
	# угол «+» — вправо (как у глаз: eyeLookOutRight), а поворот вокруг «вверх» на «+» — влево; поэтому минус
	_rot(_bi("spine_02"), up, -chest * 0.45)
	_rot(_bi("spine_03"), up, -chest * 0.55)
	_rot(_bi("neck_01"), up, -rest * 0.4)
	_rot(h, up, -rest * 0.6)
	var dir_h := (fwd * cos(_ay) - basis.x * sin(_ay)).normalized()
	var pitch_axis := up.cross(dir_h).normalized()
	_rot(_bi("neck_01"), pitch_axis, -dp * 0.35)
	_rot(h, pitch_axis, -dp * 0.65)
	# наклон головы набок — едва заметно
	_rot(h, dir_h, deg_to_rad(1.2) * ha * _noise(9.0, 2.2))
	# глаза: на цель (быстрее головы), мелкие саккады вокруг
	var ex := 0.0
	var ey := 0.0
	if has:
		ex = clampf(wrapf(ty - _ay, -PI, PI), -0.5, 0.5)
		ey = clampf(tp - _ap, -0.35, 0.35)
	_sacc_t -= dt
	if _sacc_t < 0.0:
		var sr: Array = _m.get("sacc", [0.6, 2.2])
		_sacc_t = randf_range(float(sr[0]), float(sr[1])) * (0.6 if talk_t >= 0.0 else 1.0)
		var amp := 0.035 if has else 0.16
		if mood == "other":
			amp = 0.01
		_eye_to = Vector2(randf_range(-amp, amp), randf_range(-amp * 0.7, amp * 0.5))
		if not has and randf() < float(_m.get("down", 0.5)) * 0.3:   # задумался: глаза вниз
			_eye_to.y -= 0.15
	_eye = _eye.lerp(_eye_to + Vector2(ex, ey) / 0.55, minf(1.0, dt * 22.0))
	# большой поворот головы — часто сопровождается морганием
	if absf(_ayv) > 2.5 and blink_t > 0.6 and blink_t < 1e8 and randf() < dt * 3.0:
		blink_t = 0.2


# ---------------------------------------------------------------------------------------------------------------- руки

## Машет обеими руками над головой: плечи вверх и чуть в стороны, предплечья качаются навстречу друг другу.
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


## Кисть — в точку: двухзвенная IK. Локоть ставится по длинам плеча и предплечья (закон косинусов) в сторону «полюса»,
## плечо поворачивается целиком — вместе с плоскостью сгиба из клипа, поэтому локоть гнётся только как шарнир,
## а не вбок. Потом предплечье сгибается в этой плоскости до цели; ладонь (если задана) — поворотом предплечья и кисти.
func _update_reach(delta: float) -> void:
	_reach_w = move_toward(_reach_w, 1.0 if not reach.is_empty() else 0.0, delta * 2.0)
	if _snap > 0:   # расстановка на склейке: руки сразу на месте, без подплывания
		_reach_w = 1.0 if not reach.is_empty() else 0.0
	if _reach_w < 0.001 or reach.is_empty():
		return
	var e := ease(_reach_w, -1.8)   # плавно в начале и в конце движения
	var sgt := skel.global_transform
	var gb := sgt.basis.orthonormalized()
	for side in reach:
		var bu := _bi("upperarm" + side)
		var bl := _bi("lowerarm" + side)
		var bh := _bi("hand" + side)
		if bu < 0 or bl < 0 or bh < 0:
			continue
		var tgt: Vector3 = reach[side]
		var s := (sgt * skel.get_bone_global_pose(bu)).origin
		var el := (sgt * skel.get_bone_global_pose(bl)).origin
		var hd := (sgt * skel.get_bone_global_pose(bh)).origin
		var la := s.distance_to(el)
		var lb := el.distance_to(hd)
		var to := tgt - s
		if to.length() < 0.02 or la < 0.02 or lb < 0.02:
			continue
		var dir := to.normalized()
		var d := clampf(to.length(), absf(la - lb) + 0.02, (la + lb) * 0.995)   # дальше длины руки — рука прямая
		var out := gb.x if side == "_l" else -gb.x
		var pole: Vector3 = reach_pole.get(side, -gb.y + out * 0.5 - gb.z * 0.2)
		var pp := pole - dir * pole.dot(dir)
		if pp.length() < 0.001:
			pp = -gb.y - dir * (-gb.y).dot(dir)
		pp = pp.normalized()
		var ca := clampf((la * la + d * d - lb * lb) / (2.0 * la * d), -1.0, 1.0)
		var elbow := s + dir * (la * ca) + pp * (la * sqrt(maxf(0.0, 1.0 - ca * ca)))
		var hand_at := s + dir * d
		# 1) плечо: кость — к новому локтю, плоскость сгиба из клипа — в плоскость «плечо — локоть — цель»
		var u0 := (el - s).normalized()
		var u1 := (elbow - s).normalized()
		# плоскость сгиба: у почти прямой руки вектор сгиба мал и шумит (поворот плеча «переворачивается») —
		# тогда плавно берём её из полюса: кисть по другую сторону линии плечо—локоть, чем полюс
		var n0 := _bend_dir(u0, (hd - el), pp, lb)
		var n1 := _bend_dir(u1, (hand_at - elbow), pp, lb)
		if n0.length() > 0.0005 and n1.length() > 0.0005:
			var f0 := Basis(u0, n0, u0.cross(n0))
			var f1 := Basis(u1, n1, u1.cross(n1))
			var q := Quaternion((f1 * f0.transposed()).orthonormalized())
			if q.get_angle() > 0.0001:
				_rot_now(bu, q.get_axis(), q.get_angle() * e)
		else:
			_aim_bone("upperarm" + side, "lowerarm" + side, u1, e)
		# 2) локоть: предплечье — в цель (поворот в плоскости сгиба)
		var el2 := (sgt * skel.get_bone_global_pose(bl)).origin
		_aim_bone("lowerarm" + side, "hand" + side, (hand_at - el2).normalized(), e)
		# 3) ладонь: поворот вокруг оси предплечья (60% — предплечье, 40% — кисть), не больше 100°
		if reach_palm.has(side):
			_turn_palm(side, (reach_palm[side] as Vector3).normalized(), e)


## Направление сгиба руки (единичное, поперёк плеча u): куда от линии плеча уходит предплечье f. У почти прямой руки —
## плавно к «против полюса» pp, чтобы плоскость сгиба не прыгала от кадра к кадру.
func _bend_dir(u: Vector3, f: Vector3, pp: Vector3, lb: float) -> Vector3:
	var n := f - u * f.dot(u)
	var fb := -(pp - u * pp.dot(u))
	fb = fb.normalized() if fb.length() > 1e-4 else Vector3.ZERO
	var k := smoothstep(0.03, 0.12, n.length() / maxf(lb, 0.01))
	var r := fb.lerp(n.normalized() if n.length() > 1e-5 else fb, k)
	return r.normalized() if r.length() > 1e-4 else fb


## Поворот кости вокруг мировой оси по свежей позе (а не по снимку начала кадра): для IK, где кости уже сдвинуты.
func _rot_now(b: int, axis_w: Vector3, ang: float) -> void:
	if b < 0 or absf(ang) < 1e-6:
		return
	var inv := (skel.global_transform * skel.get_bone_global_pose(b)).basis.orthonormalized().inverse()
	skel.set_bone_pose_rotation(b, skel.get_bone_pose_rotation(b) * Quaternion((inv * axis_w).normalized(), ang))


## Нормаль ладони по костям пальцев (наружу из ладони): правая — указательный × мизинец, левая — наоборот.
func palm_normal(side: String) -> Vector3:
	var sgt := skel.global_transform
	var h := (sgt * skel.get_bone_global_pose(_bi("hand" + side))).origin
	var ix := (sgt * skel.get_bone_global_pose(_bi("index_01" + side))).origin - h
	var pk := (sgt * skel.get_bone_global_pose(_bi("pinky_01" + side))).origin - h
	var n := ix.cross(pk) if side == "_r" else pk.cross(ix)
	return n.normalized() if n.length() > 1e-6 else Vector3.DOWN


func _turn_palm(side: String, want: Vector3, w: float) -> void:
	var bl := _bi("lowerarm" + side)
	var bh := _bi("hand" + side)
	if _bi("index_01" + side) < 0 or _bi("pinky_01" + side) < 0:
		return
	var sgt := skel.global_transform
	var axis := ((sgt * skel.get_bone_global_pose(bh)).origin - (sgt * skel.get_bone_global_pose(bl)).origin).normalized()
	var cur := palm_normal(side)
	var a := cur - axis * cur.dot(axis)
	var b := want - axis * want.dot(axis)
	if a.length() < 0.05 or b.length() < 0.05:
		return
	# угол выбирается ближайшим к прошлому кадру: около 180° он иначе прыгал бы с +100° на −100° (рывок предплечья)
	var ang := a.signed_angle_to(b, axis)
	var prev := float(_palm_tw.get(side, 0.0))
	if ang - prev > PI:
		ang -= TAU
	elif prev - ang > PI:
		ang += TAU
	ang = clampf(ang, deg_to_rad(-100.0), deg_to_rad(100.0))
	_palm_tw[side] = ang
	_rot_now(bl, axis, ang * 0.6 * w)
	_rot_now(bh, axis, ang * 0.4 * w)


var _grip_cd := 0.0


## Что в руках: предметы сцены крепятся к кисти (BoneAttachment3D на hand_r / hand_l) — пальцы обхватывают их сами.
func _grip_scan(dt: float) -> void:
	_grip_cd -= dt
	if _grip_cd > 0.0:
		return
	_grip_cd = 0.4
	var held := {"_r": 0.0, "_l": 0.0}
	for c in skel.get_children():
		if c is BoneAttachment3D and (c as BoneAttachment3D).bone_name in ["hand_r", "hand_l"]:
			var vis := false
			for cc in c.get_children():
				if cc is Node3D and (cc as Node3D).visible:
					vis = true
			if vis:
				held["_r" if (c as BoneAttachment3D).bone_name == "hand_r" else "_l"] = 0.6   # пальцы ложатся на предмет, не сквозь него
	for side in held:
		if not grip.has(side) or bool(get_meta("auto_grip", true)):
			grip[side] = held[side]


## Пальцы обхватывают предмет (рация, пистолет колонки, топор, фонарь): сгиб фаланг, большой палец навстречу.
func _update_grip(delta: float) -> void:
	for side in ["_r", "_l"]:
		var want := float(grip.get(side, 0.0))
		_grip_w[side] = move_toward(float(_grip_w[side]), want, delta * 4.0)
		var w := float(_grip_w[side])
		if w < 0.001:
			continue
		var hb := _bi("hand" + side)
		if hb < 0:
			continue
		var hg := skel.global_transform * skel.get_bone_global_pose(hb)
		var curl := hg.basis.x.normalized() * (1.0 if side == "_l" else -1.0)
		for f in ["index", "middle", "ring", "pinky"]:
			for j in [["_01", 50.0], ["_02", 65.0], ["_03", 40.0]]:
				_rot(_bi(f + String(j[0]) + side), curl, deg_to_rad(float(j[1])) * w)
		for j in [["thumb_02", 25.0], ["thumb_03", 20.0]]:
			_rot(_bi(String(j[0]) + side), curl, deg_to_rad(float(j[1])) * w * 0.6)


## Повернуть кость так, чтобы она смотрела (от себя к дочерней) по направлению dir (мир); w — доля поворота.
func _aim_bone(bone: String, child: String, dir: Vector3, w: float) -> void:
	var b := _bi(bone)
	var c := _bi(child)
	if b < 0 or c < 0:
		return
	var bg := skel.global_transform * skel.get_bone_global_pose(b)
	var cg := skel.global_transform * skel.get_bone_global_pose(c)
	var cur := (cg.origin - bg.origin).normalized()
	var axis := cur.cross(dir)
	if axis.length() < 0.0001:
		return
	var local_axis := (bg.basis.orthonormalized().inverse() * axis.normalized()).normalized()
	skel.set_bone_pose_rotation(b, skel.get_bone_pose_rotation(b) * Quaternion(local_axis, cur.angle_to(dir) * w))


# ---------------------------------------------------------------------------------------------------------------- лицо

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


func _face_update(dt: float) -> void:
	var W := {}
	# выражение: плавно к цели (expr задают сцены) + лицо в покое по характеру
	var base_face: Dictionary = {} if mood == "dead" else _m.get("face", {})
	var tgt: Dictionary = base_face
	if not expr.is_empty():
		tgt = base_face.duplicate()
		for k in expr:
			tgt[k] = float(tgt.get(k, 0.0)) * 0.3 + float(expr[k])
	var ek := 1.0 - exp(-dt * 6.0)
	for k in tgt:
		_face[k] = lerpf(float(_face.get(k, 0.0)), float(tgt[k]), ek)
	for k in _face.keys():
		if not tgt.has(k):
			_face[k] = lerpf(float(_face[k]), 0.0, ek)
			if float(_face[k]) < 0.002:
				_face.erase(k)
	for k in _face:
		W[k] = float(_face[k])
	# мелькающая улыбка (Зоя): появляется и уходит
	var sm := float(_m.get("smile", 0.0))
	if sm > 0.0 and mood == "" and not expr.has("mouthFrownLeft"):
		_smile_cd -= dt
		if _smile_cd <= 0.0 and _smile_t <= 0.0:
			_smile_cd = randf_range(4.0, 10.0)
			if randf() < sm:
				_smile_t = randf_range(1.0, 2.4)
		if _smile_t > 0.0:
			_smile_t -= dt
			var sv := sm * smoothstep(0.0, 0.4, _smile_t) * minf(1.0, (2.4 - _smile_t) / 0.4) * 0.6
			W["mouthSmileLeft"] = float(W.get("mouthSmileLeft", 0.0)) + sv
			W["mouthSmileRight"] = float(W.get("mouthSmileRight", 0.0)) + sv * 0.8
			W["cheekSquintLeft"] = float(W.get("cheekSquintLeft", 0.0)) + sv * 0.35
	# моргание: быстро закрыть, чуть задержать, медленнее открыть; пауза по характеру, в страхе — чаще; иногда дважды
	blink_t -= dt
	if blink_t < 0.0:
		var bi: Array = _m.get("blink", [2.5, 5.5])
		var mul := float((Manner.MOOD.get(mood, {}) as Dictionary).get("blink", 1.0))
		blink_t = randf_range(float(bi[0]), float(bi[1])) * mul
		if randf() < 0.12:
			blink_t = 0.35
	var bl := 0.0
	if blink_t < 0.2:
		var x := 0.2 - blink_t
		bl = smoothstep(0.0, 0.06, x) if x < 0.09 else 1.0 - smoothstep(0.09, 0.2, x)
	if mood == "sleep":
		bl = 1.0
	W["eyeBlinkLeft"] = maxf(float(W.get("eyeBlinkLeft", 0.0)), bl)
	W["eyeBlinkRight"] = maxf(float(W.get("eyeBlinkRight", 0.0)), bl)
	# глаза: формы взгляда по направлению (веки идут за глазами — те же формы есть у век)
	if mood != "dead":
		var ex := _eye + Vector2(float(W.get("_eye_x", 0.0)), float(W.get("_eye_y", 0.0)))
		ex.x = clampf(ex.x, -1.0, 1.0)
		ex.y = clampf(ex.y, -1.0, 1.0)
		var er := "eyeLookInLeft" if ex.x > 0.0 else "eyeLookOutLeft"
		W[er] = float(W.get(er, 0.0)) + absf(ex.x) * 0.55
		er = "eyeLookOutRight" if ex.x > 0.0 else "eyeLookInRight"
		W[er] = float(W.get(er, 0.0)) + absf(ex.x) * 0.55
		for side in ["Left", "Right"]:
			er = ("eyeLookUp" if ex.y > 0.0 else "eyeLookDown") + side
			W[er] = float(W.get(er, 0.0)) + absf(ex.y) * 0.55
	# губы: буква реплики → форма рта; веса плавно (без «щёлканья»), челюсть — по громкости голоса
	var vt := ""
	var open := 0.0
	if talk_t >= 0.0:
		talk_t += dt
		var f := talk_t / talk_dur
		if f >= 1.0:
			talk_t = -1.0
		else:
			var ch := _talk_char(talk_t)
			vt = VIS.get(ch, "")
			open = 0.0 if ch in [" ", ".", ",", "!", "?", "…", "-", "—"] else 0.35 + 0.25 * sin(talk_t * 21.0)
			if talk_env != "":
				var ei := int(talk_t / 0.04)
				open = (float(talk_env.unicode_at(ei) - 48) / 9.0) * 0.75 if ei < talk_env.length() else 0.0
				if ch == " ":
					vt = ""
			if vt in ["viseme_PP", "viseme_FF"]:
				open *= 0.5
	var vk_on := 1.0 - exp(-dt * 28.0)
	var vk_off := 1.0 - exp(-dt * 16.0)
	for v in VIS_ALL:
		var want := 0.7 if v == vt else 0.0
		var cur := float(_vis.get(v, 0.0))
		cur = lerpf(cur, want, vk_on if want > cur else vk_off)
		_vis[v] = cur
		if cur > 0.002:
			W[v] = float(W.get(v, 0.0)) + cur
	_jaw = lerpf(_jaw, open, 1.0 - exp(-dt * 20.0))
	if _jaw > 0.002:
		W["jawOpen"] = float(W.get("jawOpen", 0.0)) + _jaw
	# брови на ударных слогах: всплеск громкости — брови чуть вверх
	var bw := float(_m.get("brows", 0.5))
	var target_emph := 0.0
	if talk_t >= 0.0 and talk_env != "":
		var ei2 := int(talk_t / 0.04)
		if ei2 < talk_env.length() and talk_env.unicode_at(ei2) - 48 >= 7:
			target_emph = 1.0
	_emph = lerpf(_emph, target_emph, 1.0 - exp(-dt * (10.0 if target_emph > _emph else 3.0)))
	if _emph > 0.01 and bw > 0.0:
		W["browInnerUp"] = float(W.get("browInnerUp", 0.0)) + 0.18 * bw * _emph
		W["browOuterUpLeft"] = float(W.get("browOuterUpLeft", 0.0)) + 0.1 * bw * _emph
		W["browOuterUpRight"] = float(W.get("browOuterUpRight", 0.0)) + 0.1 * bw * _emph
	# в сетки: только изменившиеся веса, и только формы, что есть у этого героя (ненулевые сейчас или в прошлом кадре)
	for bn in W:
		if not _shape_to.has(bn):
			continue
		var v := clampf(float(W[bn]), 0.0, 1.0)
		if absf(v - float(_shape_last.get(bn, 0.0))) > 0.002:
			for tg in (_shape_to[bn] as Array):
				(tg[0] as MeshInstance3D).set_blend_shape_value(int(tg[1]), v)
			_shape_last[bn] = v
	for bn in _shape_last.keys():
		if not W.has(bn):
			for tg in (_shape_to[bn] as Array):
				(tg[0] as MeshInstance3D).set_blend_shape_value(int(tg[1]), 0.0)
			_shape_last.erase(bn)


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
		return skel.global_transform * skel.get_bone_global_pose(_bi(n)).origin
	var hl0: Vector3 = g.call("hand_l")
	var hr0: Vector3 = g.call("hand_r")
	var hl1: Vector3 = hl0 + (g.call("middle_02_l") - hl0) * 1.05
	var hr1: Vector3 = hr0 + (g.call("middle_02_r") - hr0) * 1.05
	for sm in _blood_mats:
		sm.set_shader_parameter("hl0", hl0)
		sm.set_shader_parameter("hl1", hl1)
		sm.set_shader_parameter("hr0", hr0)
		sm.set_shader_parameter("hr1", hr1)


## Ветер и толчки — в симулятор прядей: порывы (медленные волны силы), а не дрожь; толчок затухает за ~0,3 с.
func _update_hair(dt: float) -> void:
	if hair_sim == null and skirt_sim == null:
		return
	var gust := 0.55 + 0.45 * (0.5 + 0.5 * sin(_t * 0.9 + _seed)) * (0.6 + 0.4 * sin(_t * 0.37 + _seed * 2.0))
	var wind := wind_dir.normalized() * breeze * 2.2 * gust
	_kick = _kick.lerp(Vector3.ZERO, 1.0 - exp(-dt * 6.0))
	if hair_sim:
		hair_sim.external_force = wind + _kick
	if skirt_sim:   # тяжёлая ткань подола — ветер слабее, чем волосам
		skirt_sim.external_force = wind * 0.35


## Толчок прядям (рывок машины, удар, резкий поворот головы): м/с.
func hair_kick(v: Vector3) -> void:
	_kick += v * 2.0


## Для стенда: смещение кончика первой цепочки прядей от положения покоя относительно головы (м).
func hair_probe() -> Array:
	if hair_sim == null:
		return [0.0, 0.0, 0.0]
	var tip := -1
	for i in skel.get_bone_count():
		if skel.get_bone_name(i).begins_with("hair_0_"):
			tip = i
	if tip < 0:
		return [0.0, 0.0, 0.0]
	var h := _bi("head")
	var hg := skel.get_bone_global_pose(h)
	var tg := skel.get_bone_global_pose(tip)
	var rest := skel.get_bone_global_rest(h).affine_inverse() * skel.get_bone_global_rest(tip)
	var d := tg.origin - (hg * rest).origin
	return [d.x, d.y, d.z]
