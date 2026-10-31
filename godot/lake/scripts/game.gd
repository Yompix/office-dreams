extends Node3D
## WHAT THE LAKE KEEPS — режиссёр главы: читает сценарий (data/ch1.json), ставит планы камеры, говорит репликами героев,
## даёт выбор на время и действия (руль, тормоз), запоминает выборы (доверие Айрис, «Тень», таблетки, память).
## Проверка без человека: godot --path godot/lake -- --autoplay (выборы случайные, действия удаются), --lang en, --scene s2.

var scene: DriveScene
var cam: Camera3D
var hud: Hud
var actors := {}
var data: Dictionary
var flags := {}
var lang := "ru"
var autoplay := false
var _shot := "pov"
var _shake := 0.0
var _t := 0.0
var _skip := false
var _steer_in := 0.0
var _qte := ""
var _qte_ok := true
var _qte_drift := 0.0
var _rng := RandomNumberGenerator.new()
var log_lines: Array[String] = []
var _snaps: Array = []
var _snapdir := ""
var _walks := {}
var _shotlog := ""            # --shotlog папка: снимок через 0,6 с после каждой склейки (лист кадров всей сцены)
var _shot_n := 0              # кто идёт: [куда, скорость, клип по приходу, поворот по приходу]
const FLOOR := 0.38           # пол магазина и крыльцо заправки
## выражения лица (формы ARKit): страх, шок, улыбка, грусть, боль, «мёртвый взгляд»
const EXPR := {"fear": {"browInnerUp": 0.8, "eyeWideLeft": 0.7, "eyeWideRight": 0.7, "mouthStretchLeft": 0.25, "mouthStretchRight": 0.25, "jawOpen": 0.08},
	"shock": {"browInnerUp": 0.6, "browOuterUpLeft": 0.5, "browOuterUpRight": 0.5, "eyeWideLeft": 0.8, "eyeWideRight": 0.8, "jawOpen": 0.18},
	"smile": {"mouthSmileLeft": 0.35, "mouthSmileRight": 0.28, "cheekSquintLeft": 0.15, "cheekSquintRight": 0.12},
	"sad": {"browInnerUp": 0.55, "mouthFrownLeft": 0.4, "mouthFrownRight": 0.4},
	"pain": {"browDownLeft": 0.6, "browDownRight": 0.6, "eyeSquintLeft": 0.6, "eyeSquintRight": 0.6, "mouthStretchLeft": 0.4, "mouthStretchRight": 0.4, "jawOpen": 0.12},
	"dead": {"jawOpen": 0.16, "eyeLookUpLeft": 0.2, "eyeLookUpRight": 0.2}, "none": {}}


func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	autoplay = "--autoplay" in args
	if "--lang" in args:
		lang = args[args.find("--lang") + 1]
	_rng.seed = 7 if not ("--seed" in args) else int(args[args.find("--seed") + 1])
	var pc := RenderingServer.get_current_rendering_method() == "forward_plus"
	scene = DriveScene.new()
	add_child(scene)
	scene.setup(pc)
	cam = Camera3D.new()
	cam.near = 0.02
	add_child(cam)
	cam.make_current()
	_cast()
	hud = Hud.new()
	hud.lang = lang
	add_child(hud)
	data = JSON.parse_string(FileAccess.get_file_as_string("res://data/ch1.json"))
	flags = (data.flags as Dictionary).duplicate()
	var start: String = data.start
	if "--scene" in args:
		start = args[args.find("--scene") + 1]
	if autoplay and not ("--snaps" in args):
		Engine.time_scale = 4.0
	if "--snaps" in args:   # снимки кадра в секунды игры: --snaps 3,7.5,12 --snapdir папка
		_snaps = Array(String(args[args.find("--snaps") + 1]).split(",")).map(func(x): return float(x))
		_snapdir = args[args.find("--snapdir") + 1] if "--snapdir" in args else "user://"
	if "--shotlog" in args:
		_shotlog = args[args.find("--shotlog") + 1]
	if "--station" in args:   # проверка заправки: --station --shots st_wide,st_counter
		scene.park_at_station(lang)
	if "--shots" in args:   # проверка планов: каждый план — снимок, без сценария: --shots pov,hands,iris --snapdir папка
		_shots(String(args[args.find("--shots") + 1]).split(","), args[args.find("--snapdir") + 1] if "--snapdir" in args else "user://")
		return
	_run(start)


## Ной за рулём, Айрис рядом — свои герои (tools/lake/chars.json → assets/chars).
func _cast() -> void:
	var noah := Actor.make("noah", "noah")
	scene.car.add_child(noah)
	noah.position = Car.DRIVER + Vector3(0, 0.15, 0)
	noah.rotation_degrees = Vector3(0, 180, 0)
	noah.play("Driving", 0.5)
	noah.enable_blood()
	actors.noah = noah
	var iris := Actor.make("iris", "iris")
	scene.car.add_child(iris)
	iris.position = Car.PASSENGER + Vector3(0, 0.15, 0)
	iris.rotation_degrees = Vector3(0, 180, 0)
	iris.play("Rk_SitIdle", 1.0)
	actors.iris = iris
	await get_tree().process_frame
	await get_tree().process_frame
	scene.car.seat(noah, Car.DRIVER)
	scene.car.seat(iris, Car.PASSENGER)
	await get_tree().process_frame
	scene.car.fit_wheel(noah)


func _run(id: String) -> void:
	var sc: Dictionary = data.scenes.get(id, {})
	if sc.is_empty():
		push_error("нет сцены " + id)
		return
	_log("сцена " + id)
	if sc.has("time"):
		scene.car.clock.text = sc.time
	for b in sc.beats:
		var go = await _beat(b)
		if go is String and go != "":
			_run(go)
			return


## Один шаг сценария. Возвращает имя сцены, если дальше — переход.
func _beat(b: Dictionary):
	if b.has("if") and not _cond(b["if"]):   # условие не выполнено: шаг пропускается или переход в «else»
		return String(b.get("else", ""))
	if b.get("station", false):
		await _station()
	if b.has("place"):
		_place(b.place)
	if b.has("walk"):
		_walk(b.walk)
	if b.has("anim"):
		var an: Dictionary = b.anim
		(actors[String(an.who)] as Actor).play(String(an.clip), float(an.get("at", 0.0)))
	if b.has("expr"):
		_expr(b.expr)
	if b.has("fx"):
		_fx(String(b.fx))
	if b.has("set"):
		for k in b.set:
			flags[k] = flags.get(k, 0) + int(b.set[k])
		_log("флаги " + JSON.stringify(flags))
	if b.has("toast"):
		hud.show_toast("Айрис это запомнит." if lang == "ru" else "Iris will remember that.")
	if b.has("shot"):
		_cut(String(b.shot))
	if b.has("look"):
		_look(b.look)
	if b.has("say"):
		var who := String(b.say)
		var text := hud.T(b)
		var dur := clampf(0.9 + text.length() / 15.0, 1.4, 9.0)
		hud.line(who, data.names.get(who, ""), b)
		if actors.has(who):
			(actors[who] as Actor).say(text, dur * 0.8)
		await _wait(dur)
		hud.clear_text()
	elif b.has("text"):
		hud.show_note(b.text)
		await _wait(float(b.get("dur", 2.5)))
		hud.clear_text()
	elif b.has("note") and b.note is Dictionary:
		hud.show_note(b.note)
		await _wait(float(b.get("dur", 2.0)))
		hud.clear_text()
	elif b.has("dur"):
		await _wait(float(b.dur))
	if b.has("choice"):
		var c: Dictionary = b.choice
		if c.has("shot"):
			_cut(String(c.shot))
		var opts: Array = c.opts
		hud.choice(opts, float(c.get("t", 6)))
		if autoplay:
			get_tree().create_timer(1.0).timeout.connect(func(): if hud.choosing(): hud.pick(_rng.randi() % opts.size()))
		var i: int = await hud.chosen
		_log("выбор %d: %s" % [i, "молчание" if opts[i].get("silent", false) else hud.T(opts[i])])
		return String(opts[i].get("go", ""))
	if b.has("qte"):
		var ok: bool = await _do_qte(String(b.qte), float(b.get("t", 3.0)), b.get("hint"))
		_log("действие %s: %s" % [b.qte, "удалось" if ok else "нет"])
		if not ok and b.has("fail"):
			return String(b.fail)
	if b.has("go"):
		return String(b.go)
	if b.get("end", false):
		await _end()
	return ""


func _shots(list: Array, dir: String) -> void:
	hud.fade.color.a = 0.0
	await _wait(1.0)
	for sh in list:
		_cut(String(sh))
		await _wait(0.6)
		get_viewport().get_texture().get_image().save_png("%s/shot_%s.png" % [dir, sh])
		if sh == "st_tv" and scene.props.has("news"):   # кадр новостей целиком — проверить надписи
			(scene.props["news"] as NewsTV).news.get_texture().get_image().save_png("%s/news_screen.png" % dir)
	get_tree().quit()


func _wait(t: float) -> void:
	var left := t
	_skip = false
	while left > 0.0 and not _skip:
		await get_tree().process_frame
		left -= get_process_delta_time()


func _cut(shot: String) -> void:
	_shot = shot
	_place_cam()
	if _shotlog != "":
		_shot_n += 1
		var n := _shot_n
		get_tree().create_timer(0.6, true, false, true).timeout.connect(func():
			get_viewport().get_texture().get_image().save_png("%s/%03d_%s.png" % [_shotlog, n, shot]))


## План: из таблицы машины; «руки» — крупно на руль, где бы он ни встал под руки героя.
func _place_cam() -> void:
	if _shot == "pov":   # глазами Ноя: камера у глаз (чуть перед лицом), вперёд по машине и немного вниз — руль и руки в кадре
		var noah: Actor = actors.noah
		var hb := noah.skel.global_transform * noah.skel.get_bone_global_pose(noah.skel.find_bone("head"))
		var eye := hb.origin + hb.basis.y.normalized() * 0.09 + hb.basis.z.normalized() * 0.13
		cam.fov = 72.0
		cam.look_at_from_position(eye, eye - scene.car.global_transform.basis.z * 10.0 + Vector3(0, -3.0, 0))
	elif _shot == "hands":
		cam.fov = 36.0
		cam.look_at_from_position(scene.car.to_global(Vector3(0.12, 1.08, -0.42)), scene.car.wheel.global_position + Vector3(0, -0.02, 0))
	else:
		scene.put_camera(cam, _shot)


## Взгляд: "noah" (Айрис смотрит на Ноя) или {"who": "roman", "at": "tv"}; цель — герой, предмет или точка [x, y, z].
func _look(what) -> void:
	var who := "iris"
	var at = what
	if what is Dictionary:
		who = String(what.get("who", "iris"))
		at = what.get("at")
	var a: Actor = actors.get(who)
	if a:
		a.look_target = _target(at)


func _target(at):
	if at == null:
		return null
	if at is Array:
		return Vector3(at[0], at[1], at[2])
	var s := String(at)
	if actors.has(s):
		var o: Actor = actors[s]
		return o.skel.global_transform * o.skel.get_bone_global_pose(o.skel.find_bone("head")).origin + Vector3(0, 0.07, 0)
	match s:
		"notebook":
			return scene.car.to_global(Vector3(0.36, 0.55, 0.05))
		"window":
			return scene.car.to_global(Vector3(3.0, 1.1, 0.2))
		"glovebox":
			return scene.car.to_global(Vector3(0.38, 0.68, -0.45))
		"tv":
			if scene.props.has("news"):
				return (scene.props["news"] as NewsTV).screen.global_position
	return null


## Условие сценария: {"pills": ">0", "shadow": ">=1"} — выполняются все.
func _cond(c: Dictionary) -> bool:
	for k in c:
		var v := float(flags.get(k, 0))
		var e := String(c[k]).strip_edges()
		var op := ""
		for o in [">=", "<=", "==", "!=", ">", "<"]:
			if e.begins_with(o):
				op = o
				break
		var n := float(e.substr(op.length()))
		var ok := v == n
		match op:
			">=":
				ok = v >= n
			"<=":
				ok = v <= n
			"!=":
				ok = v != n
			">":
				ok = v > n
			"<":
				ok = v < n
		if not ok:
			return false
	return true


func _expr(e: Dictionary) -> void:
	var a: Actor = actors.get(String(e.who))
	if a:
		a.expr = (EXPR.get(String(e.set), {}) as Dictionary).duplicate()


## Герой — на место: {"who", "at": [x, y, z], "face": градусы, "clip"}; {"who", "car": "driver|passenger|back"} — в машину.
func _place(p: Dictionary) -> void:
	var who := String(p.who)
	var a: Actor = actors[who]
	_walks.erase(who)
	if p.has("car"):
		_seat_in_car(who, String(p.car))
		return
	if a.get_parent() != scene:
		a.reparent(scene, false)
	var at: Array = p.at
	a.position = Vector3(at[0], at[1], at[2])
	a.rotation = Vector3(0, deg_to_rad(float(p.get("face", 0.0))), 0)
	if p.has("clip"):
		a.play(String(p.clip), float(p.get("t", 0.3)))


func _seat_in_car(who: String, seat: String) -> void:
	var a: Actor = actors[who]
	if a.get_parent() != scene.car:
		a.reparent(scene.car, false)
	var s: Vector3 = {"driver": Car.DRIVER, "passenger": Car.PASSENGER, "back": Car.BACK}[seat]
	a.position = s + Vector3(0, 0.15, 0)
	a.rotation_degrees = Vector3(0, 180, 0)
	a.play("Driving" if seat == "driver" else "Rk_SitIdle", 0.5)
	await get_tree().process_frame
	await get_tree().process_frame
	if seat == "back":
		scene.car.seat(a, s, 0.53, 0.16)
	else:
		scene.car.seat(a, s)
	if seat == "driver":
		await get_tree().process_frame
		scene.car.fit_wheel(a)


## Пройти к точке: {"who", "to": [x, y, z], "speed", "clip" (шаг), "then" (клип по приходу), "face" (поворот по приходу)}.
func _walk(w: Dictionary) -> void:
	var who := String(w.who)
	var a: Actor = actors[who]
	if a.get_parent() != scene:
		var g := a.global_transform
		a.reparent(scene, false)
		a.global_transform = g
		a.rotation = Vector3(0, a.rotation.y, 0)
	var to: Array = w.to
	var dst := Vector3(to[0], to[1], to[2])
	var d := dst - a.position
	a.rotation.y = atan2(d.x, d.z)
	a.play(String(w.get("clip", "Rk_Walk")), 0.0)
	_walks[who] = [dst, float(w.get("speed", 1.15)), String(w.get("then", "Idle")), w.get("face")]


## Сесть: таз героя — в точку p (на крыльцо, на ящик).
func _sit(who: String, p: Vector3) -> void:
	var a: Actor = actors[who]
	var sk := a.skel
	var cur := sk.global_transform * sk.get_bone_global_pose(sk.find_bone("pelvis")).origin
	a.global_position += p - cur


## Заправка «24»: машина у колонки, Ной у колонки, Айрис рядом с машиной, мальчик на крыльце с рацией, кассир за прилавком.
func _station() -> void:
	scene.park_at_station(lang)
	for k in ["danny", "roman"]:
		if not actors.has(k):
			var a := Actor.make(k, k)
			scene.add_child(a)
			actors[k] = a
	_place({"who": "roman", "at": [26.4, FLOOR, -9.25], "face": 0, "clip": "Idle"})
	_place({"who": "noah", "at": [13.0, 0.0, -4.0], "face": -110, "clip": "Idle"})
	_place({"who": "iris", "at": [15.0, 0.0, -2.2], "face": 70, "clip": "Idle"})
	_place({"who": "danny", "at": [20.3, FLOOR, -3.3], "face": -90, "clip": "Rk_SitIdle"})
	(actors.noah as Actor).look_target = null
	(actors.iris as Actor).look_target = null
	await get_tree().process_frame
	await get_tree().process_frame
	_sit("danny", Vector3(20.2, FLOOR + 0.1, -3.3))
	_attach_walkie(actors.danny)


## Детская рация в руках у Дэнни.
func _attach_walkie(a: Actor) -> void:
	var ba := BoneAttachment3D.new()
	ba.bone_name = "hand_r"
	a.skel.add_child(ba)
	var w := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = Vector3(0.055, 0.13, 0.035)
	w.mesh = bm
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.85, 0.55, 0.08)
	m.roughness = 0.45
	w.material_override = m
	w.position = Vector3(0.0, 0.08, 0.03)
	ba.add_child(w)
	var ant := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = 0.005
	cm.bottom_radius = 0.006
	cm.height = 0.09
	ant.mesh = cm
	var am := StandardMaterial3D.new()
	am.albedo_color = Color(0.05, 0.05, 0.05)
	ant.material_override = am
	ant.position = Vector3(0.015, 0.11, 0.0)
	w.add_child(ant)


## Провал: Ной у колонки с пистолетом, дверь треснула, кассир на полу (жив — стонет; «Тень» ≥ 1 — нет), мальчик застыл, Айрис в машине.
func _lapse_after() -> void:
	_place({"who": "noah", "at": [13.0, 0.0, -4.1], "face": -100, "clip": "Idle"})
	scene.crack_door()
	var dead := float(flags.get("shadow", 0)) >= 1.0
	var r: Actor = actors.roman
	_place({"who": "roman", "at": [22.9, FLOOR, -3.7], "face": 80, "clip": "Death01"})
	r.ap.seek(maxf(0.0, r.ap.current_animation_length - 0.05), true)
	r.ap.pause()
	r.look_target = null
	_expr({"who": "roman", "set": "dead" if dead else "pain"})
	if dead:
		r.blink_t = 1e9
		scene.blood_pool(Vector3(22.0, FLOOR + 0.004, -3.75))
	_expr({"who": "danny", "set": "fear"})
	(actors.danny as Actor).look_target = _target("noah")
	_place({"who": "iris", "car": "passenger"})
	hud.hum_to = 0.0
	hud.hum = 0.0


## Сцена 6: Айрис с мальчиком у крыльца, Ной напротив.
func _kids_by_car() -> void:
	var lapse := float(flags.get("lapse1", 0)) > 0.0
	_place({"who": "danny", "at": [19.6, 0.0, -3.7], "face": -100, "clip": "Idle"})
	_place({"who": "iris", "at": [19.7, 0.0, -3.05], "face": -105, "clip": "Idle"})
	_place({"who": "noah", "at": [17.0, 0.0, -2.5], "face": 80, "clip": "Idle"})
	_expr({"who": "danny", "set": "fear" if lapse else "sad"})
	(actors.danny as Actor).look_target = _target("noah")
	(actors.iris as Actor).look_target = _target("noah")
	(actors.noah as Actor).look_target = _target("danny")


func _danny_in_car() -> void:
	_place({"who": "danny", "car": "back"})
	_place({"who": "iris", "car": "passenger"})
	_place({"who": "noah", "car": "driver"})
	(actors.danny as Actor).look_target = null
	_expr({"who": "danny", "set": "sad"})


func _danny_stays() -> void:
	_place({"who": "danny", "at": [20.3, FLOOR, -3.3], "face": -90, "clip": "Rk_SitIdle"})
	_place({"who": "iris", "car": "passenger"})
	_place({"who": "noah", "car": "driver"})
	await get_tree().process_frame
	await get_tree().process_frame
	_sit("danny", Vector3(20.2, FLOOR + 0.1, -3.3))
	_expr({"who": "danny", "set": "sad"})


func _lapse_black() -> void:
	hud.noise = 0.0
	hud.fade.color = Color(0, 0, 0, 1)
	hud.hum_to = 0.0
	hud.hum = 0.0


func _fx(k: String) -> void:
	var fade := hud.fade
	match k:
		"black":
			fade.color.a = 1.0
		"wake":
			fade.color.a = 1.0
			create_tween().tween_property(fade, "color:a", 0.0, 1.8).set_ease(Tween.EASE_OUT)
			_shake = 0.6
		"flash_dock":
			fade.color = Color(0.85, 0.9, 1.0, 0.9)
			create_tween().tween_property(fade, "color", Color(0, 0, 0, 1.0), 0.5)
		"horn":
			fade.color = Color(1.0, 0.97, 0.9, 1.0)
			_shake = 1.0
		"crash":
			_shake = 1.5
			_jolt(0.8)
			create_tween().tween_property(fade, "color:a", 1.0, 0.6)
		"retry":
			hud.show_note({"ru": "Попробуй ещё раз.", "en": "Try again."})
		"skid", "u_turn", "grab_wheel":
			_shake = 0.8
			_jolt(0.35)
			var tw := create_tween()
			tw.tween_property(scene.car, "steer", -0.9, 0.35)
			tw.tween_property(scene.car, "steer", 0.6, 0.4)
			tw.tween_property(scene.car, "steer", 0.0, 0.5)
		"radio_on":
			scene.car.radio_lbl.text = "FM 102.4"
		"radio_off":
			scene.car.radio_lbl.text = ""
		"iris_stop_drawing", "notebook_dash", "pencil_break", "lullaby_iris":
			(actors.iris as Actor).play("Rk_SitTalk", 0.0)
		"blood_hands":
			pass   # кровь на руках Ноя видна всегда (enable_blood); здесь — план крупно
		"fade_out":
			create_tween().tween_property(fade, "color:a", 1.0, 1.2)
		"fade_in":
			fade.color = Color(0, 0, 0, 1)
			create_tween().tween_property(fade, "color:a", 0.0, 1.6)
		"fuel_light":
			scene.car.fuel_blink = true
		"hum_up":
			hud.hum_to = 0.6
		"hum_down":
			hud.hum_to = 0.0
		"ring":
			hud.hum_to = 1.0
			_shake = 0.5
		"glove_open":
			scene.car.show_pills(true, "НОЙ\nД-р С. Хейл\n1 таб. при головной боли" if lang == "ru" else "NOAH\nDr. S. Hale\n1 tablet for headaches")
			create_tween().tween_property(scene.car, "glove_open", 1.0, 0.7).set_ease(Tween.EASE_OUT)
		"glove_close":
			var tg := create_tween()
			tg.tween_property(scene.car, "glove_open", 0.0, 0.5)
			tg.tween_callback(scene.car.show_pills.bind(false))
		"pills_take", "pills_throw":
			scene.car.show_pills(false)
		"money":
			scene.show_money()
		"phone_rip":
			scene.rip_phone()
			_shake = 0.4
		"lapse":                                     # провал: белая вспышка → белый шум → чернота
			fade.color = Color(1, 1, 1, 1)
			var tl := create_tween()
			tl.tween_property(fade, "color", Color(1, 1, 1, 0.0), 0.12)
			tl.tween_callback(func(): hud.noise = 0.9)
			tl.tween_interval(0.9)
			tl.tween_callback(_lapse_black)
		"lapse_after":
			_lapse_after()
		"kids_by_car":
			_kids_by_car()
		"danny_in_car":
			_danny_in_car()
		"danny_stays":
			_danny_stays()


## Рывок машины: героев (и их волосы) встряхивает.
func _jolt(k: float) -> void:
	for a in actors.values():
		(a as Actor).hair_kick(Vector3(randf_range(-1.0, 1.0) * k, k * 0.4, randf_range(-0.5, 0.5) * k))


func _do_qte(kind: String, t: float, hint_d) -> bool:
	hud.hint.text = hud.T(hint_d) if hint_d != null else ""
	_qte = kind
	_qte_ok = true
	_qte_drift = 0.0
	var left := t
	while left > 0.0:
		await get_tree().process_frame
		var dt := get_process_delta_time()
		left -= dt
		if kind == "steer":   # машину ведёт к обочине: игрок держит её на полосе
			var push := sin(_t * 1.3) * 0.9 + 0.6   # снос вправо (+), руль влево (−) его гасит
			var inp := _steer_in if not autoplay else clampf(-_qte_drift * 3.0 - push / 2.2, -1.0, 1.0)
			_qte_drift = clampf(_qte_drift + (push + inp * 2.2) * dt, -1.2, 1.2)
			scene.car.steer = -_qte_drift * 0.8
			if absf(_qte_drift) >= 1.2:
				_qte_ok = false
				break
	hud.hint.text = ""
	_qte = ""
	scene.car.steer = 0.0
	return _qte_ok


func _end() -> void:
	hud.clear_text()
	create_tween().tween_property(hud.fade, "color:a", 1.0, 1.5)
	await _wait(1.6)
	var t := "Продолжение следует" if lang == "ru" else "To be continued"
	hud.title.text = t + "\n\n" + ("Доверие Айрис: %d" % flags.trust if lang == "ru" else "Iris's trust: %d" % flags.trust)
	_log("конец отрывка, флаги " + JSON.stringify(flags))
	await _wait(3.0)
	if autoplay:
		get_tree().quit()


func _log(s: String) -> void:
	print(s)
	log_lines.append(s)


func _unhandled_input(e: InputEvent) -> void:
	if e is InputEventKey and e.pressed and not e.echo:
		var k := (e as InputEventKey).keycode
		if hud.choosing() and k >= KEY_1 and k <= KEY_3:
			hud.pick_number(k - KEY_0)
		elif k == KEY_SPACE or k == KEY_ENTER:
			_skip = true
	elif e is InputEventMouseButton and e.pressed and not hud.choosing():
		_skip = true


func _process(delta: float) -> void:
	_t += delta
	for who in _walks.keys():
		var wk: Array = _walks[who]
		var a: Actor = actors[who]
		var dst: Vector3 = wk[0]
		var d := Vector3(dst.x - a.position.x, 0.0, dst.z - a.position.z)
		var step := float(wk[1]) * delta
		if d.length() <= step:
			a.position = dst
			a.play(String(wk[2]), 0.0)
			if wk[3] != null:
				a.rotation.y = deg_to_rad(float(wk[3]))
			_walks.erase(who)
		else:
			a.position += d.normalized() * step
			a.position.y = move_toward(a.position.y, dst.y, delta * 0.8)
	if not _snaps.is_empty() and _t >= float(_snaps[0]):
		get_viewport().get_texture().get_image().save_png("%s/snap_%05.1f.png" % [_snapdir, float(_snaps[0])])
		_snaps.pop_front()
		if _snaps.is_empty():
			get_tree().quit()
	_steer_in = Input.get_axis("ui_left", "ui_right")
	if Input.is_key_pressed(KEY_A):
		_steer_in = -1.0
	elif Input.is_key_pressed(KEY_D):
		_steer_in = 1.0
	# ручная камера: лёгкое дыхание кадра, тряска после рывков; дорога потряхивает вид из машины
	_shake = maxf(0.0, _shake - delta * 0.8)
	_place_cam()
	var road := 0.004 if _shot in ["pov", "road", "hands"] else 0.002
	cam.rotate_object_local(Vector3.RIGHT, sin(_t * 1.7) * 0.004 + sin(_t * 23.0) * road + (randf() - 0.5) * 0.03 * _shake)
	cam.rotate_object_local(Vector3.UP, sin(_t * 1.1) * 0.005 + (randf() - 0.5) * 0.03 * _shake + _qte_drift * 0.12)
