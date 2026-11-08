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
		reach_basis = {}
		if v.is_empty():
			reach_lean = 0.0    # руки свободны — корпус больше не тянется
var reach_lean := 0.0           # цель дальше длины руки: корпус наклоняется к ней, не больше стольких градусов (0 — стоит как в клипе)
var reach_pole := {}            # куда смотрит локоть (мир): {"_r": Vector3}; по умолчанию — вниз, наружу и чуть назад
var reach_palm := {}            # куда повёрнута ладонь (мир): {"_r": Vector3}; по умолчанию — как в клипе
var reach_basis := {}           # кисть целиком (мир): {"_r": Basis} — предмет в руке встаёт точно (пистолет носиком в горловину)
var _reach_w := 0.0
var _palm_tw := {}              # поворот ладони прошлого кадра (рад) — для непрерывности
var grip := {}                  # держит предмет: {"_r": 0…1, "_l": 0…1} — пальцы обхватывают
var point := {}                 # указательный вытянут, остальные поджаты: {"_l": 0…1} (кнопки таксофона, угол плаката)
var _grip_w := {"_r": 0.0, "_l": 0.0}
var walking := false
var _hair: Array[ShaderMaterial] = []
## слой «отбрасывает солнечную тень»: солнце днём рисует тени только от героев (и кузова машины), а не от домов и леса —
## герои стоят на снегу, а цена теней — десятки вызовов отрисовки, а не тысячи (замер: двор гл. 4 — 2988 → см. PERFORMANCE_AUDIT)
const SUN_LAYER := 1 << 11
## Части лица без тени: глаза, зубы и язык — внутри головы (их тень падает внутрь черепа и рта), брови и ресницы лежат
## на коже тоньше пикселя карты теней. Каждая сетка с тенью — отдельный вызов отрисовки в каждом проходе теней (солнце —
## 2 каскада, лампа — до 6 граней куба), а видно эту тень не было. Свет и тени на самих этих частях — как раньше.
const NO_SHADOW := ["eyes", "teeth", "tongue", "brows", "lashes"]
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
const STRIDE := {"Rk_Walk": 1.35, "Walk": 1.10, "Walk_Formal": 1.12, "Walk_Carry": 0.73, "Crouch_Fwd": 0.96, "Jog_Fwd": 2.7,
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
static var foot_lock_on := true   # стопы не едут по земле на ходу (8 ноя); стенд сравнения: false
var _fl_hold := 0.0
var _fl_yaw := 0.0
var _fl := {}   # сторона → {"on": стоит, "p": точка на земле, "w": вес}
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
		var path := "res://assets/chars/%s.glb" % look_file(String(k))
		if ResourceLoader.exists(path) and not ResourceLoader.has_cached(path):
			ResourceLoader.load_threaded_request(path)


static func _load_model(path: String) -> PackedScene:
	if ResourceLoader.load_threaded_get_status(path) != ResourceLoader.THREAD_LOAD_INVALID_RESOURCE:
		var r := ResourceLoader.load_threaded_get(path)
		if r is PackedScene:
			return r
	return load(path) as PackedScene


## Новые версии героев заправки (порядок редактора от 5 ноя, п. 6): отдельные файлы рядом со старыми — старые не
## перезаписываются до утверждения эталона. Вернуть прежний облик: запуск с --looks1 (или LOOKS_V2 = false).
const LOOKS := {"iris": "iris_v3", "danny": "danny_v2", "orderly": "orderly_v2", "noah_kid": "noah_kid_v2", "victor": "victor_v2", "hale": "hale_v2"}   # 8 ноя: санитар 2,00 м, Ной-ребёнок 1,30, Виктор 1,87 с проседью, Хейл седой; iris_v3 — чёлка выше на 1,8 см (решение редактора 6 ноя); iris_v2 рядом
static var LOOKS_V2 := true


static func look_file(file: String) -> String:
	if not LOOKS_V2 or "--looks1" in OS.get_cmdline_user_args():
		return file
	var f2: String = LOOKS.get(file, "")
	return f2 if f2 != "" and ResourceLoader.exists("res://assets/chars/%s.glb" % f2) else file


static func make(k: String, file: String) -> Actor:
	file = look_file(file)
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
	for gi in a.model.find_children("*", "GeometryInstance3D", true, false):
		(gi as GeometryInstance3D).layers |= SUN_LAYER
	var hair_mi: MeshInstance3D = null
	for mi in a.model.find_children("*", "MeshInstance3D", true, false):
		var m := mi as MeshInstance3D
		var nm := String(m.name).to_lower()
		if nm in NO_SHADOW:
			m.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		for i in m.mesh.get_surface_count():
			var src := m.get_active_material(i) as BaseMaterial3D
			if src == null:
				continue
			if nm.contains("hair"):   # волосы: свой материал — вырезка по альфе, блик вдоль прядей; движение — кости (HairRig)
				var hm := ShaderMaterial.new()
				hm.shader = _hair_shader()
				hm.set_shader_parameter("albedo_tex", src.albedo_texture)
				hm.set_shader_parameter("albedo", src.albedo_color)
				if src.albedo_texture and src.albedo_texture.resource_path.contains("short_messy"):
					hm.set_shader_parameter("cut", 0.09)   # стрижка прядями (Айрис, Дэнни): тонкие пряди — порог ниже, иначе «ниточки»
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
				if Actor.sss_ok():   # свет чуть проходит под кожу — только Forward+ (ПК); телефон и сайт его не умеют
					mm.subsurf_scatter_enabled = true
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
			Fabric.tune(mm, nm, String(src.resource_name))   # ткань по имени сетки: шерсть, трикотаж, кожа, нейлон, дождевик… (Fabric)
			m.set_surface_override_material(i, mm)
	a._leather = Leather.apply(a, a.skel)   # куртка Ноя: зерно кожи, складки на сгибах (лист «24», п. 6 редактора)
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
	# сел — когда поза встанет (после смешивания), тело ложится на сиденье, а не в него (Seats.settle)
	_settle_t = (b + 0.08) if (c in SEAT and not c in ["Crouch_Idle", "Fixing_Kneeling", "Driving"]) else -1.0


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


## Жесты по подписям сцены («Ной прячет руки в карманы», «Дэнни кивает» — теперь и движением), см. do_gesture()
var gesture := ""
var _gest_t := 0.0
var _gest_dur := 1.0
var _gest_to: Actor = null
var _gest_bone := "head"
var _gest_look = null
var _gest_reach0 := {}
var _gest_palm0 := {}
var _gest_pole0 := {}
var _nod_t := -1.0
var _shake_t := -1.0
var _trem_t := -1.0
var _wave_end := -1.0
var _gest_side := 1.0
var _seat_depth := {}   # клип → глубина «таз → низ тела» сидя (м), см. seat_depth()
var _settle_t := -1.0   # через сколько секунд положить тело на сиденье (сел; −1 — не нужно)
var stool: Node3D = null   # свой табурет (Seats.settle), если посадили туда, где мебели нет


## Сидя: на сколько кость таза выше самой низкой точки тела под ней (ягодицы, бёдра, штаны) в текущей позе — на столько
## таз должен быть выше верха сиденья, чтобы тело лежало на подушке, а не уходило в неё (Game._settle_seat).
## Считается один раз на клип: кожа и одежда скиннингом на процессоре, только вершины таза и бёдер рядом с тазом.
func seat_depth() -> float:
	if _seat_depth.has(clip):
		return float(_seat_depth[clip])
	skel.force_update_all_bone_transforms()
	var sg := skel.global_transform
	var pel: Vector3 = sg * skel.get_bone_global_pose(skel.find_bone("pelvis")).origin
	var hips := {}
	for nm in ["pelvis", "thigh_l", "thigh_r"]:
		var bi := skel.find_bone(nm)
		if bi >= 0:
			hips[bi] = true
	var low := INF
	for n in model.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		var nm := String(mi.name).to_lower()
		if mi.mesh == null or mi.skin == null or not (nm == "skin" or nm.begins_with("cloth_")) or nm.contains("shoe") or nm.contains("boot"):
			continue
		var sr: Skin = mi.skin
		var bones: Array = []
		var xf: Array = []
		for b in sr.get_bind_count():
			var bi := sr.get_bind_bone(b)
			if bi < 0:
				bi = skel.find_bone(sr.get_bind_name(b))
			bones.append(bi)
			xf.append(skel.get_bone_global_pose(bi) * sr.get_bind_pose(b) if bi >= 0 else Transform3D.IDENTITY)
		for si in mi.mesh.get_surface_count():
			var arr := mi.mesh.surface_get_arrays(si)
			var vv: PackedVector3Array = arr[Mesh.ARRAY_VERTEX]
			var bo: PackedInt32Array = arr[Mesh.ARRAY_BONES] if arr[Mesh.ARRAY_BONES] != null else PackedInt32Array()
			var we: PackedFloat32Array = arr[Mesh.ARRAY_WEIGHTS] if arr[Mesh.ARRAY_WEIGHTS] != null else PackedFloat32Array()
			if vv.is_empty() or bo.is_empty():
				continue
			var st := bo.size() / vv.size()
			for i in vv.size():
				var hw := 0.0
				for j in st:
					var b := bo[i * st + j]
					if b < bones.size() and hips.has(bones[b]):
						hw += we[i * st + j]
				if hw < 0.5:
					continue
				var q := Vector3.ZERO
				for j in st:
					var w := we[i * st + j]
					if w > 0.0:
						q += ((xf[bo[i * st + j]] as Transform3D) * vv[i]) * w
				q = sg * q
				if Vector2(q.x - pel.x, q.z - pel.z).length() < 0.22 and q.y < low:
					low = q.y
	var d := clampf(pel.y - low, 0.03, 0.25) if low < INF else 0.1
	_seat_depth[clip] = d
	return d


## Закончить жест раньше времени (Роман узнал Ноя — руки расходятся): руки и взгляд — как были до жеста.
func end_gesture() -> void:
	if gesture != "":
		_gest_t = 0.0


## Руки свободны: цели IK, снимок рук до жеста и хват сброшены — жест, начатый в этом же шаге сценария, по окончании
## не вернёт руки к старым целям (ведро: «руки из воды» и осмотр ладоней в одном шаге).
func clear_hands() -> void:
	reach = {}
	point = {}
	_gest_reach0 = {}
	_gest_palm0 = {}
	_gest_pole0 = {}
	if gesture != "check_hands":   # осмотр ладоней сам держит пальцы разжатыми
		grip = {}
		set_meta("auto_grip", true)


## Жест на dur секунд. Руки — к точкам у своего тела каждый кадр (идут вместе с героем): pockets — в карманы,
## look_hands — ладони перед собой и взгляд на них, check_hands — то же, потом перевернуть (ногти, костяшки),
## collar — поднять воротник, warm_hands — дышать в ладони, behind — за спину, give — протянуть руку, palm_to — ладонь на
## кость другого героя (голова, плечо), take_hand — взять за руку, sleeve — взгляд на свой рукав.
## Головой: nod — кивок, shake — «нет»; tremble — дрожь. После — руки и взгляд как были.
func do_gesture(kind: String, dur := 2.0, other: Actor = null, bone := "head") -> void:
	match kind:
		"nod":
			_nod_t = 0.0
		"shake":
			_shake_t = 0.0
		"tremble":
			_trem_t = dur
		"wave":
			wave = 1.0
			_wave_end = dur
		_:
			if gesture == "":
				_gest_reach0 = reach.duplicate()
				_gest_palm0 = reach_palm.duplicate()
				_gest_pole0 = reach_pole.duplicate()
				_gest_look = look_target
			gesture = kind
			_gest_t = dur
			_gest_dur = maxf(dur, 0.1)
			if kind == "check_hands":
				set_meta("auto_grip", false)   # пальцы разжаты жестом, авто-хват их не трогает
			_gest_to = other
			_gest_bone = bone
			_gest_side = -1.0 if bone == "l" else 1.0


func _update_gesture(dt: float) -> void:
	if _wave_end >= 0.0:
		_wave_end -= dt
		if _wave_end < 0.0:
			wave = 0.0
	if gesture == "":
		return
	_gest_t -= dt
	if _gest_t <= 0.0:
		if gesture == "check_hands":   # пальцы — снова как в клипе (или по предмету в руке)
			grip = {}
			set_meta("auto_grip", true)
		gesture = ""
		reach = _gest_reach0
		reach_palm = _gest_palm0
		reach_pole = _gest_pole0
		look_target = _gest_look
		return
	var sg := skel.global_transform
	var bp := func(n: String) -> Vector3:
		return sg * skel.get_bone_global_pose(_bi(n)).origin
	var pv: Vector3 = bp.call("pelvis")
	var ch: Vector3 = bp.call("spine_03")
	var right: Vector3 = bp.call("thigh_r") - bp.call("thigh_l")
	right.y = 0.0
	right = right.normalized() if right.length() > 1e-4 else global_transform.basis.x
	var fwd := global_transform.basis.z
	fwd.y = 0.0
	fwd = fwd.normalized()
	var up := Vector3.UP
	var r := {}
	var palm := {}
	match gesture:
		"pockets":
			r = {"_r": pv + fwd * 0.09 + right * 0.17 - up * 0.03, "_l": pv + fwd * 0.09 - right * 0.17 - up * 0.03}
			palm = {"_r": -right, "_l": right}
		"look_hands":
			var c := ch + fwd * 0.32 - up * 0.22
			r = {"_r": c + right * 0.09, "_l": c - right * 0.09}
			palm = {"_r": up, "_l": up}
			look_target = c
		"check_hands":   # осмотреть руки: ладонями вверх, потом перевернуть тыльной стороной — ногти, костяшки.
			# Кисти — у груди (локти согнуты, предплечья почти горизонтально), пальцы разжаты: видно ладони и ногти
			var c := ch + fwd * 0.28
			r = {"_r": c + right * 0.1, "_l": c - right * 0.1}
			var k := smoothstep(0.42, 0.62, 1.0 - _gest_t / _gest_dur) * PI   # ладонь вверх → внутрь → вниз (поворот предплечья)
			var g := lerpf(-0.45, -1.0, k / PI)   # тыльной стороной — пальцы прямее: видно ногти
			grip = {"_r": g, "_l": g}
			palm = {"_r": up * cos(k) - right * sin(k), "_l": up * cos(k) + right * sin(k)}
			look_target = c
		"collar":   # поднять воротник ребром ладони: кисти к бокам шеи, потом чуть вверх (ёжится на холоде)
			var hd: Vector3 = bp.call("head")
			var nk := hd - up * 0.13
			var lift := smoothstep(0.35, 0.8, 1.0 - _gest_t / _gest_dur) * 0.05
			r = {"_r": nk + right * 0.085 + fwd * 0.03 + up * lift, "_l": nk - right * 0.085 + fwd * 0.03 + up * lift}
			palm = {"_r": -right, "_l": right}
		"arms_crossed":   # руки скрещены на животе (Роман на табурете): кисти — к противоположным локтям, ладонями к себе
			var c := pv + up * 0.3 + fwd * 0.17
			r = {"_r": c - right * 0.12, "_l": c + right * 0.13 + up * 0.03}
			palm = {"_r": -fwd, "_l": -fwd}
		"palms_out":   # пустые ладони вперёд, на уровне груди: «не надо» / отшатнулся
			var c := ch + fwd * 0.3 - up * 0.05
			r = {"_r": c + right * 0.16, "_l": c - right * 0.16}
			palm = {"_r": fwd, "_l": fwd}
		"warm_hands":   # дышит в сложенные лодочкой ладони: кисти у рта, ладони друг к другу
			var hd: Vector3 = bp.call("head")
			var m := hd + fwd * 0.12 - up * 0.07
			r = {"_r": m + right * 0.025, "_l": m - right * 0.025}
			palm = {"_r": -right + up * 0.4, "_l": right + up * 0.4}
			look_target = m + fwd * 0.6 - up * 0.2
		"behind":
			r = {"_r": pv - fwd * 0.17 + right * 0.06 + up * 0.04, "_l": pv - fwd * 0.17 - right * 0.06 + up * 0.04}
		"shade_eyes":   # прикрыть глаза ладонью козырьком (у витрины — от света навеса)
			var he: Vector3 = bp.call("head")
			r = {"_r": he + fwd * 0.13 + up * 0.07 + right * 0.03}
			palm = {"_r": -up * 0.7 - fwd * 0.3}
		"shade_eyes_l":   # то же левой рукой (витрина: камера справа — правое плечо свободно, отражение не заслонено)
			var hl: Vector3 = bp.call("head")
			r = {"_l": hl + fwd * 0.13 + up * 0.07 - right * 0.03}
			palm = {"_l": -up * 0.7 - fwd * 0.3}
		"walkie_talk":   # рацию — к губам, большим пальцем на кнопку (Дэнни: «Мама. Приём…»)
			var hw: Vector3 = bp.call("head")
			r = {"_r": hw + fwd * 0.11 - up * 0.1 + right * 0.03}
			palm = {"_r": -right * 0.6 - fwd * 0.4}
			look_target = hw + fwd * 0.8 - up * 0.3
		"sleeve":
			look_target = bp.call("lowerarm_r")
		"chest":   # прижать к груди (спрятать под куртку): на уровне груди, а не живота
			r = {"_r": ch + fwd * 0.16 + up * 0.03 + right * 0.04, "_l": ch + fwd * 0.16 + up * 0.03 - right * 0.04}
			palm = {"_r": -fwd, "_l": -fwd}
		"look_away":   # отвернуться (к окну): взгляд в сторону — bone "l" или "r"
			look_target = head_pos() + right * (2.0 * _gest_side) + fwd * 0.6
		"give":
			r = {"_r": ch + fwd * 0.45 - up * 0.12 + right * 0.08}
			palm = {"_r": up}
		"palm_to", "take_hand":
			if is_instance_valid(_gest_to) and _gest_to.skel != null:
				var ob := _gest_to.skel.find_bone(_gest_bone)
				if ob >= 0:
					var tp: Vector3 = _gest_to.skel.global_transform * _gest_to.skel.get_bone_global_pose(ob).origin
					if gesture == "palm_to":
						tp += up * 0.07
						palm = {"_r": -up}
					else:
						palm = {"_r": (tp - ch).normalized()}
					r = {"_r": tp}
	if not r.is_empty():
		reach = r
		reach_palm = palm
	elif reach != _gest_reach0:   # жест одной головой (отвернуться, на рукав) после жеста руками: руки — как были до жестов
		reach = _gest_reach0
		reach_palm = _gest_palm0
		reach_pole = _gest_pole0


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


## Прогулка: скорость (м/с, с учётом стен) задаёт explore.gd и сам двигает героя; здесь — поворот к ходу,
## шаг клипа под скорость (стопы не едут), «Idle», когда встал.
func drive(v: Vector3, delta: float, face_dir := Vector3.ZERO) -> void:
	walking = false
	var sp := Vector2(v.x, v.z).length()
	var look := face_dir if face_dir.length() > 0.01 else v
	if Vector2(look.x, look.z).length() > 0.05:
		_turn_to = null
		rotation.y = lerp_angle(rotation.y, atan2(look.x, look.z), 1.0 - exp(-delta * 9.0))
	if sp > 0.08:
		_walk_clip = "Rk_Walk"
		if clip != "Rk_Walk":
			play("Rk_Walk")
		ap.speed_scale = clampf(sp / (float(STRIDE.get("Rk_Walk", 1.35)) * leg), 0.35, 1.45)
	elif clip == "Rk_Walk":
		ap.speed_scale = 1.0
		play("Idle")
	_v = sp


## Плавно повернуться на месте (рад, как rotation.y).
func face_to(yaw: float) -> void:
	if walking:
		stop_walk()
		play("Idle")
	_turn_to = yaw


func speed() -> float:
	return _v


## Кровь на руках (три состояния по листу постановки «24»): 1 — засохшая на ладонях и пальцах (как после дома);
## 0 — отмыл в ведре: ладони чистые, под ногтями тёмное (set_nails); разбитые костяшки после провала — set_knuckles.
func set_blood(v: float) -> void:
	for sm in _blood_mats:
		sm.set_shader_parameter("blood", v)


## Тёмное под ногтями: 1 — есть (с самого начала и после мытья), 0 — нет.
func set_nails(v: float) -> void:
	for sm in _blood_mats:
		sm.set_shader_parameter("nails", v)


## Разбитые костяшки (после провала на заправке): 1 — свежие ссадины и кровь с тыльной стороны кисти.
func set_knuckles(v: float) -> void:
	for sm in _blood_mats:
		sm.set_shader_parameter("knuckles", v)


func _process(delta: float) -> void:
	var t0 := Time.get_ticks_usec()
	_tick(delta)
	if _settle_t >= 0.0:
		_settle_t -= delta
		if _settle_t < 0.0:
			if has_meta("sit_pending"):   # Ch2.sit ещё ставит таз в точку — посадить, когда поставит
				_settle_t = 0.03
			else:
				Seats.settle(self)
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
		# лёжа — без пружин: сила тяжести мира тянула полы пальто к матрасу, бёдра выталкивали их вверх, и подол вставал
		# шатром (гл. 3, роды: «тёмный ком»); без пружин полы лежат вдоль ног, как у стоящей
		var lying := clip in LIE
		if skirt_sim.active == lying:
			skirt_sim.active = not lying
			if not lying:
				skirt_sim.reset()
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
	_update_gesture(dt)
	_update_reach(dt)
	_update_grip(dt)
	_foot_lock(dt)
	var q3 := Time.get_ticks_usec()
	_face_update(dt)
	var q4 := Time.get_ticks_usec()
	_update_blood()
	Leather.update(_leather, self, skel)
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
		ap.speed_scale = clampf(_v / nat, 0.35, 1.45)


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


## Оси тела по костям: X — влево, Y — от таза к шее, Z — куда смотрит грудь (Basis() — если костей нет).
func _body_basis() -> Basis:
	var p := _bi("pelvis")
	var n := _bi("neck_01")
	var ul := _bi("upperarm_l")
	var ur := _bi("upperarm_r")
	if p < 0 or n < 0 or ul < 0 or ur < 0:
		return Basis()
	var sgt := skel.global_transform
	var up := ((sgt * skel.get_bone_global_pose(n)).origin - (sgt * skel.get_bone_global_pose(p)).origin).normalized()
	var left := (sgt * skel.get_bone_global_pose(ul)).origin - (sgt * skel.get_bone_global_pose(ur)).origin
	left = left - up * left.dot(up)
	if left.length() < 1e-4:
		return Basis()
	left = left.normalized()
	return Basis(left, up, left.cross(up))


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
	if clip in LIE:
		# лёжа: «вверх» — вдоль позвоночника к голове, «вперёд» — куда смотрит грудь. По осям корня (вертикаль мира)
		# поворот к собеседнику гнул шею вбок по подушке — голова съезжала к краю кровати (гл. 3, роды)
		var bb := _body_basis()
		if bb != Basis():
			basis = bb
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
	# сидя голову к собеседнику не выкручивают — остальное добирают глаза: на лавке, ступеньке, стуле — до ~60°, в машине —
	# до ~45°. Раньше Айрис на пассажирском сиденье поворачивалась к Ною на 100° (его голова — на 15 см позади её), и на её
	# плане «в лицо» камера видела затылок: каре закрывало лицо (гл. 2, буран, 3 ноя). С пределом 60° в машине голова
	# уходила на 12° дальше камеры у лобового стекла — каре закрывало пол-лица; на ступеньке 45° мало — к Ною не повернуться
	var yl := 1.45
	if clip in SEAT and clip != "Crouch_Idle" and clip != "Fixing_Kneeling":
		yl = 0.79 if bool(get_meta("in_car", false)) else 1.05
	ty = clampf(ty, -yl, yl)
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
	# жесты головой (do_gesture): кивок, «нет», дрожь
	if _nod_t >= 0.0:
		_nod_t += dt
		var nk := _nod_t / 0.9
		if nk >= 1.0:
			_nod_t = -1.0
		else:
			_rot(h, pitch_axis, sin(nk * TAU * 2.0) * 0.17 * sin(nk * PI))
			_rot(_bi("neck_01"), pitch_axis, sin(nk * TAU * 2.0) * 0.07 * sin(nk * PI))
	if _shake_t >= 0.0:
		_shake_t += dt
		var sn := _shake_t / 1.1
		if sn >= 1.0:
			_shake_t = -1.0
		else:
			_rot(h, up, sin(sn * TAU * 2.0) * 0.24 * sin(sn * PI))
	if _trem_t > 0.0:
		_trem_t -= dt
		_rot(_bi("spine_03"), global_transform.basis.x.normalized(), sin(_t * 41.0) * 0.012 + sin(_t * 27.0) * 0.008)
		_rot(h, up, sin(_t * 37.0) * 0.012)
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
	if reach_lean > 0.0:
		_lean_to_reach(e)
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
		# 3б) кисть целиком — в заданный базис (запястье уже в цели, поворот его не сдвигает)
		if reach_basis.has(side) and skel.get_bone_parent(bh) >= 0:
			var ws: Basis = sgt.basis.orthonormalized().inverse() * (reach_basis[side] as Basis).orthonormalized()
			var pg := skel.get_bone_global_pose(skel.get_bone_parent(bh)).basis.orthonormalized()
			var tq := Quaternion((pg.inverse() * ws).orthonormalized())
			skel.set_bone_pose_rotation(bh, skel.get_bone_pose_rotation(bh).slerp(tq, e))
		# 4) осмотр рук: кисть — в линию с предплечьем (в клипе она свисает), иначе пальцы смотрят в пол
		if gesture in ["check_hands", "look_hands"] and _bi("middle_01" + side) >= 0:
			var fa := ((sgt * skel.get_bone_global_pose(bh)).origin - (sgt * skel.get_bone_global_pose(bl)).origin).normalized()
			_aim_bone("hand" + side, "middle_01" + side, fa, e * 0.85)


## Цель дальше длины руки (Ной присел у ведра и тянется к воде): корпус наклоняется к цели — поясница, середина
## и верх спины вместе (40/35/25 %), не больше reach_lean градусов; плечо подходит так, что рука остаётся чуть согнутой.
## Угол — по повороту плеча вокруг середины спины (spine_02): так же сдвигает плечо, как этот наклон тремя позвонками.
func _lean_to_reach(e: float) -> void:
	var sgt := skel.global_transform
	var sp := [_bi("spine_01"), _bi("spine_02"), _bi("spine_03")]
	if sp.has(-1):
		return
	var p := (sgt * skel.get_bone_global_pose(sp[1])).origin
	var lim := deg_to_rad(reach_lean)
	var need := 0.0
	var axis := Vector3.ZERO
	for side in reach:
		var bu := _bi("upperarm" + side)
		var bl := _bi("lowerarm" + side)
		var bh := _bi("hand" + side)
		if bu < 0 or bl < 0 or bh < 0 or not reach[side] is Vector3:
			continue
		var s := (sgt * skel.get_bone_global_pose(bu)).origin
		var el := (sgt * skel.get_bone_global_pose(bl)).origin
		var arm := (s.distance_to(el) + el.distance_to((sgt * skel.get_bone_global_pose(bh)).origin)) * 0.9
		var tgt: Vector3 = reach[side]
		if s.distance_to(tgt) <= arm:
			continue
		var ax := (s - p).cross(tgt - p)
		if ax.length() < 1e-5:
			continue
		ax = ax.normalized()
		var a := 0.0
		while a < lim and (p + (s - p).rotated(ax, a)).distance_to(tgt) > arm:
			a += 0.0175
		if a > need:
			need = a
			axis = ax
	if need <= 0.0:
		return
	var parts := [0.4, 0.35, 0.25]
	for k in 3:
		_rot_now(sp[k], axis, need * float(parts[k]) * e)


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
		if absf(w) < 0.001:   # w < 0 — разжать: пальцы распрямляются из позы клипа (осмотр ладоней)
			continue
		var hb := _bi("hand" + side)
		if hb < 0:
			continue
		var hg := skel.global_transform * skel.get_bone_global_pose(hb)
		var curl := hg.basis.x.normalized() * (1.0 if side == "_l" else -1.0)
		# кисть сдвинута IK (reach, поворот ладони) — снимок поз начала кадра устарел: пальцы гнём по свежей позе,
		# иначе ось сгиба уходит вбок и кулак выходит растопыренной ладонью (ведро: удар по льду)
		var fresh := reach.has(side)
		# разжать (w < 0): в клипе сильнее всего согнуты концевые фаланги — их и распрямляем больше
		var joints := [["_01", 50.0], ["_02", 65.0], ["_03", 40.0]] if w > 0.0 else [["_01", 25.0], ["_02", 50.0], ["_03", 70.0]]
		for f in ["index", "middle", "ring", "pinky"]:
			for j in joints:
				if fresh:
					_rot_now(_bi(f + String(j[0]) + side), curl, deg_to_rad(float(j[1])) * w)
				else:
					_rot(_bi(f + String(j[0]) + side), curl, deg_to_rad(float(j[1])) * w)
		for j in [["thumb_02", 25.0], ["thumb_03", 20.0]]:
			if fresh:
				_rot_now(_bi(String(j[0]) + side), curl, deg_to_rad(float(j[1])) * w * 0.6)
			else:
				_rot(_bi(String(j[0]) + side), curl, deg_to_rad(float(j[1])) * w * 0.6)
	for side in point:   # указательный вытянут (жмёт кнопку, расправляет угол), средний, безымянный и мизинец поджаты
		var pw := clampf(float(point[side]), 0.0, 1.0)
		var ph := _bi("hand" + side)
		if pw < 0.001 or ph < 0:
			continue
		var pc := (skel.global_transform * skel.get_bone_global_pose(ph)).basis.x.normalized() * (1.0 if side == "_l" else -1.0)
		for f in ["middle", "ring", "pinky"]:
			for j in [["_01", 85.0], ["_02", 95.0], ["_03", 60.0]]:
				_rot_now(_bi(f + String(j[0]) + side), pc, deg_to_rad(float(j[1])) * pw)
		for j in ["_01", "_02", "_03"]:   # в клипах покоя указательный согнут почти в кулак — выпрямить по позе скелета
			var ib := _bi("index" + j + side)
			if ib >= 0:
				var rq := skel.get_bone_rest(ib).basis.get_rotation_quaternion()
				skel.set_bone_pose_rotation(ib, skel.get_bone_pose_rotation(ib).slerp(rq, pw))
		for j in [["thumb_02", 30.0], ["thumb_03", 25.0]]:   # большой — прижат к среднему
			_rot_now(_bi(String(j[0]) + side), pc, deg_to_rad(float(j[1])) * pw * 0.6)


## Повернуть кость так, чтобы она смотрела (от себя к дочерней) по направлению dir (мир); w — доля поворота.
## Стопы на ходу (8 ноя): опорная стопа «прибита» к месту, где встала, пока не оторвётся от земли; бедро и голень
## доворачиваются двухзвенной IK (колено — вперёд по ходу), стопа сохраняет свой поворот из клипа. Клип сам ставит
## опорную ногу неровно (мокап), а скорость героя задаёт код — без этого стопы ехали по земле ~0,25 м/с.
func _foot_lock(dt: float) -> void:
	var moving := (walking or _v > 0.05) and clip in STRIDE
	var yaw_rate := absf(wrapf(rotation.y - _fl_yaw, -PI, PI)) / maxf(dt, 1e-4)
	_fl_yaw = rotation.y
	var turning := not moving and (_turn_to != null or yaw_rate > 0.6) and clip in STAND   # поворот на месте
	_fl_hold = 0.4 if (moving or turning) else maxf(_fl_hold - dt, 0.0)   # встал: ещё 0,4 с держим стопы, пока клип перетекает в «Idle»
	if not foot_lock_on or _fl_hold <= 0.0 or _bi("thigh_l") < 0 or clip in SEAT or clip in LIE:
		_fl.clear()
		return
	var sgt := skel.global_transform
	var gb := global_transform.basis.orthonormalized()
	var feet := {}
	for side in ["_l", "_r"]:
		feet[side] = (sgt * skel.get_bone_global_pose(_bi("foot" + side))).origin
	# разворот на месте: стопы стоят и не крутятся с телом; отстала больше чем на 28° — переставляем (по одной)
	var stepping := false
	for side in ["_l", "_r"]:
		var st0: Dictionary = _fl.get(side, {})
		if not st0.is_empty() and st0.get("step", false):
			stepping = true
	for side in ["_l", "_r"]:
		var f: Vector3 = feet[side]
		var other: Vector3 = feet["_r" if side == "_l" else "_l"]
		var st: Dictionary = _fl.get(side, {"on": false, "p": f, "w": 0.0, "yaw": rotation.y, "step": false})
		if turning:
			if st.get("step", false):
				if float(st.w) < 0.05:   # переставили: снова встала
					st.step = false
					st.on = true
					st.p = f
					st.yaw = rotation.y
			elif not st.on:
				st.on = true
				st.p = f
				st.yaw = rotation.y
			elif absf(wrapf(rotation.y - float(st.yaw), -PI, PI)) > 0.49 and not stepping:
				st.on = false
				st.step = true
				stepping = true
		else:
			var down := f.y <= other.y + 0.012   # ниже (или вровень с) другой стопы — опорная
			if down and not st.on:
				st.on = true
				st.p = f
				st.yaw = rotation.y
			elif not down and st.on:
				st.on = false
			if not moving:   # остановка: стопы отпускаем плавно к позе «Idle»
				st.on = false
		if st.on and Vector2(f.x - st.p.x, f.z - st.p.z).length() > 0.3:   # слишком далеко (рывок) — отпустить
			st.p = f
			st.yaw = rotation.y
		var rate := 12.0 if st.on else (10.0 if st.get("step", false) else (3.0 if not moving else 8.0))
		st.w = move_toward(float(st.w), 1.0 if st.on else 0.0, dt * rate)
		_fl[side] = st
		if float(st.w) < 0.01:
			continue
		var tgt: Vector3 = Vector3(st.p.x, f.y, st.p.z).lerp(f, 1.0 - float(st.w))   # высоту ведёт клип: перекат с пятки на носок
		var yaw_fix := 0.0
		if turning:
			yaw_fix = wrapf(float(st.yaw) - rotation.y, -PI, PI) * float(st.w)
		_leg_ik(side, tgt, gb.z, yaw_fix)


func _leg_ik(side: String, tgt: Vector3, fwd: Vector3, yaw_fix := 0.0) -> void:
	var bt := _bi("thigh" + side)
	var bc := _bi("calf" + side)
	var bf := _bi("foot" + side)
	if bt < 0 or bc < 0 or bf < 0:
		return
	var sgt := skel.global_transform
	var foot_b := (sgt * skel.get_bone_global_pose(bf)).basis   # поворот стопы из клипа — вернуть после IK
	if absf(yaw_fix) > 0.0001:   # на развороте стопа остаётся смотреть, куда стояла
		foot_b = Basis(Vector3.UP, yaw_fix) * foot_b
	var h := (sgt * skel.get_bone_global_pose(bt)).origin
	var k := (sgt * skel.get_bone_global_pose(bc)).origin
	var a0 := (sgt * skel.get_bone_global_pose(bf)).origin
	var la := h.distance_to(k)
	var lb := k.distance_to(a0)
	var to := tgt - h
	if to.length() < 0.05 or la < 0.05 or lb < 0.05:
		return
	var dir := to.normalized()
	var d := clampf(to.length(), absf(la - lb) + 0.02, (la + lb) * 0.999)
	var pp := fwd - dir * fwd.dot(dir)
	if pp.length() < 0.001:
		return
	pp = pp.normalized()
	var ca := clampf((la * la + d * d - lb * lb) / (2.0 * la * d), -1.0, 1.0)
	var knee := h + dir * (la * ca) + pp * (la * sqrt(maxf(0.0, 1.0 - ca * ca)))
	_aim_bone("thigh" + side, "calf" + side, (knee - h).normalized(), 1.0)
	var k2 := (sgt * skel.get_bone_global_pose(bc)).origin
	_aim_bone("calf" + side, "foot" + side, (h + dir * d - k2).normalized(), 1.0)
	var par := skel.get_bone_parent(bf)
	if par >= 0:
		var pg := (sgt * skel.get_bone_global_pose(par)).basis.orthonormalized()
		skel.set_bone_pose_rotation(bf, Quaternion((pg.inverse() * foot_b.orthonormalized()).orthonormalized()))



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


## Свет под кожей (subsurface scattering) есть только в Forward+; в Compatibility (телефон, сайт, совместимый режим ПК)
## движок на каждую кожу пишет предупреждение — там его не включаем (выглядит так же: эффект там всё равно не рисуется).
static func sss_ok() -> bool:
	return RenderingServer.get_current_rendering_method() == "forward_plus"


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
uniform float nails = 0.0;      // тёмное под ногтями (остаётся после мытья)
uniform float knuckles = 0.0;   // разбитые костяшки — свежая кровь с тыльной стороны (после провала)
uniform vec3 tips[10];          // кончики пальцев: 0–4 левая, 5–9 правая
uniform vec3 kn[8];             // костяшки (основания пальцев): 0–3 левая, 4–7 правая
uniform vec3 dors_l = vec3(0.0, 1.0, 0.0);   // тыльная сторона кисти (мир)
uniform vec3 dors_r = vec3(0.0, 1.0, 0.0);
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
	vec3 wN = normalize((INV_VIEW_MATRIX * vec4(NORMAL, 0.0)).xyz);
	// под ногтями: тёмное на ногтях и самых кончиках пальцев (с тыльной стороны и по краю), подушечки и ладонь — чистые
	float dt = 1.0;
	for (int i = 0; i < 10; i++) { dt = min(dt, distance(vW, tips[i])); }
	vec3 dors = distance(vW, tips[2]) < distance(vW, tips[7]) ? dors_l : dors_r;
	float mn = nails * (1.0 - smoothstep(0.009, 0.019, dt)) * smoothstep(-0.35, 0.25, dot(wN, dors));
	c = mix(c, vec3(0.06, 0.02, 0.015), mn * 0.85);
	// разбитые костяшки: свежая кровь и ссадины по линии оснований пальцев, только тыльная сторона
	float dk = 1.0;
	for (int i = 0; i < 8; i++) { dk = min(dk, distance(vW, kn[i])); }
	vec3 dors2 = distance(vW, kn[1]) < distance(vW, kn[5]) ? dors_l : dors_r;
	float nk = n3(vW * 120.0);
	float mk = knuckles * (1.0 - smoothstep(0.01, 0.021, dk + (nk - 0.5) * 0.01)) * smoothstep(0.1, 0.5, dot(wN, dors2));
	c = mix(c, mix(vec3(0.32, 0.03, 0.025), vec3(0.12, 0.01, 0.01), step(0.62, nk)), mk);   // ссадина — алая, трещины — тёмные
	ALBEDO = c;
	ROUGHNESS = mix(mix(rough, mix(0.68, 0.32, fresh), m), 0.25, mk);   // свежая кровь блестит
	SPECULAR = 0.5;
	SSS_STRENGTH = 0.35 * (1.0 - m);
}
"""
var _blood_mats: Array[ShaderMaterial] = []
var _cuff_mats: Array[ShaderMaterial] = []
var _leather: Array[ShaderMaterial] = []   # куртка Ноя (Leather): суставы рук и таз — каждый кадр

## Манжеты куртки в засохшей крови (лист «24»: «под ногтями и на манжете осталось бурое» — водой не отходит):
## второй проход поверх материала куртки — пятно у запястья, сам материал куртки не меняется.
const BLOOD_CUFF := """
shader_type spatial;
render_mode blend_mix, depth_draw_never, cull_back;
uniform vec3 hl0; uniform vec3 hr0;   // запястья (мир)
uniform vec3 el; uniform vec3 er;     // локти: пятно — по краю рукава, а не по всему предплечью
uniform float amount = 1.0;
varying vec3 vW;
float h3(vec3 p) { return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453); }
float n3(vec3 p) { vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
	return mix(mix(mix(h3(i), h3(i + vec3(1.0, 0.0, 0.0)), f.x), mix(h3(i + vec3(0.0, 1.0, 0.0)), h3(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
		mix(mix(h3(i + vec3(0.0, 0.0, 1.0)), h3(i + vec3(1.0, 0.0, 1.0)), f.x), mix(h3(i + vec3(0.0, 1.0, 1.0)), h3(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z); }
void vertex() { vW = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz; }
void fragment() {
	// расстояние вдоль предплечья от запястья к локтю: пятно — последние ~9 см рукава
	vec3 al = normalize(el - hl0); vec3 ar = normalize(er - hr0);
	float tl = dot(vW - hl0, al); float tr = dot(vW - hr0, ar);
	float dl = length(vW - hl0 - al * tl); float dr = length(vW - hr0 - ar * tr);
	float t = dl < dr ? tl : tr;
	float rad = min(dl, dr);
	float n = n3(vW * 55.0) * 0.6 + n3(vW * 17.0) * 0.4;
	// только сам рукав: от запястья к локтю (не за кистью) и не дальше его радиуса — бок куртки у опущенной руки чистый
	float m = amount * smoothstep(-0.03, 0.0, t) * (1.0 - smoothstep(0.05, 0.11, t + (n - 0.5) * 0.05)) * (1.0 - smoothstep(0.065, 0.085, rad));
	m *= smoothstep(0.42, 0.62, n3(vW * 38.0 + 2.3) * 0.6 + n * 0.7);
	ALBEDO = mix(vec3(0.13, 0.026, 0.018), vec3(0.24, 0.04, 0.028), n3(vW * 29.0));
	ROUGHNESS = 0.55;
	ALPHA = clamp(m, 0.0, 1.0) * 0.8;
}
"""


func enable_blood() -> void:
	var sh := Shader.new()
	sh.code = BLOOD_SKIN if Actor.sss_ok() else BLOOD_SKIN.replace("render_mode sss_mode_skin;", "").replace("SSS_STRENGTH = 0.35 * (1.0 - m);", "")
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
			sm.set_shader_parameter("nails", 1.0)   # кровь под ногтями — с самого начала (отмыть её в ведре не выйдет)
			_blood_mats.append(sm)
	if _cuff_mats.is_empty():   # манжеты куртки — вторым проходом поверх её материала
		var csh := Shader.new()
		csh.code = BLOOD_CUFF
		for n in find_children("cloth_jacket", "MeshInstance3D", true, false):   # куртки нет в meshes (там — меши с формами лица)
			var mi := n as MeshInstance3D
			var jm := mi.get_active_material(0)
			if jm == null:
				continue
			var cm := ShaderMaterial.new()
			cm.shader = csh
			_cuff_mats.append(cm)
			if jm is ShaderMaterial:   # кожа куртки (Leather) — своя у героя: второй проход прямо на неё (копия не получала бы кости)
				jm.next_pass = cm
				continue
			var jd := jm.duplicate() as Material
			jd.next_pass = cm
			mi.set_surface_override_material(0, jd)


func _update_blood() -> void:
	if _blood_mats.is_empty():
		return
	var g := func(n: String) -> Vector3:
		return skel.global_transform * skel.get_bone_global_pose(_bi(n)).origin
	var hl0: Vector3 = g.call("hand_l")
	var hr0: Vector3 = g.call("hand_r")
	var hl1: Vector3 = hl0 + (g.call("middle_02_l") - hl0) * 1.05
	var hr1: Vector3 = hr0 + (g.call("middle_02_r") - hr0) * 1.05
	var tips := PackedVector3Array()   # кончики: продолжение последней фаланги на её длину × 0,55 (у края ногтя)
	var kn := PackedVector3Array()
	for sd in ["_l", "_r"]:
		for f in ["thumb", "index", "middle", "ring", "pinky"]:
			var a: Vector3 = g.call(f + "_02" + sd)
			var b: Vector3 = g.call(f + "_03" + sd)
			tips.append(b + (b - a) * 0.55)
		for f in ["index", "middle", "ring", "pinky"]:
			kn.append(g.call(f + "_01" + sd))
	# тыльная сторона кисти: нормаль к плоскости «запястье — указательный — мизинец», наружу от ладони
	var dl: Vector3 = (kn[0] - hl0).cross(kn[3] - hl0).normalized()
	var dr: Vector3 = (kn[7] - hr0).cross(kn[4] - hr0).normalized()
	if not _cuff_mats.is_empty():
		var el: Vector3 = g.call("lowerarm_l")
		var er: Vector3 = g.call("lowerarm_r")
		for cm in _cuff_mats:
			cm.set_shader_parameter("hl0", hl0)
			cm.set_shader_parameter("hr0", hr0)
			cm.set_shader_parameter("el", el)
			cm.set_shader_parameter("er", er)
	for sm in _blood_mats:
		sm.set_shader_parameter("hl0", hl0)
		sm.set_shader_parameter("hl1", hl1)
		sm.set_shader_parameter("hr0", hr0)
		sm.set_shader_parameter("hr1", hr1)
		sm.set_shader_parameter("tips", tips)
		sm.set_shader_parameter("kn", kn)
		sm.set_shader_parameter("dors_l", dl)
		sm.set_shader_parameter("dors_r", dr)


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
