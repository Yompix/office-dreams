extends Node3D
## WHAT THE LAKE KEEPS — режиссёр главы: читает сценарий (data/ch1.json), ставит планы камеры, говорит репликами героев,
## даёт выбор на время и действия (руль, тормоз), запоминает выборы (доверие Айрис, «Тень», таблетки, память).
## Проверка без человека: godot --path godot/lake -- --autoplay (выборы случайные, действия удаются), --lang en, --scene s2;
## --pick 0,2,1 — какие варианты выбрать; --miss brake — какое действие провалить; --fast — паузы по 0,7 с (лист кадров быстро);
## --stop s4 — закончить на этой сцене; --flags late=1,trust=2 — флаги на старте.

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
var _shot_n := 0
var _forced: Array = []       # --pick 0,2,1: какие варианты выбирает автопрогон (по порядку выборов)
var _miss: Array = []         # --miss brake,steer: какие действия автопрогон проваливает
var _stop := ""              # --stop s4: на этой сцене прогон заканчивается
var _fast := false            # --fast: каждая пауза не дольше 0,7 с, ходьба — сразу на место (быстрый лист кадров)
var deer: Deer                # олень на трассе (сцена 3): едет назад вместе с дорогой
var blackcar: BlackCar        # чёрная машина Хейла (с конца главы 1)
var _brake_in := false
var _brake_a := 0.0           # торможение, м/с²
var _pitch := 0.0             # клевок кузова при торможении (рад) и его скорость — пружина
var _pitch_v := 0.0
const BUMPER := -2.94         # передний бампер машины (z в осях машины)
const FLOOR := 0.38           # пол магазина и крыльцо заправки
## выражения лица (формы ARKit): страх, шок, улыбка, грусть, боль, «мёртвый взгляд»
const EXPR := {"fear": {"browInnerUp": 0.8, "eyeWideLeft": 0.7, "eyeWideRight": 0.7, "mouthStretchLeft": 0.25, "mouthStretchRight": 0.25, "jawOpen": 0.08},
	"shock": {"browInnerUp": 0.6, "browOuterUpLeft": 0.5, "browOuterUpRight": 0.5, "eyeWideLeft": 0.8, "eyeWideRight": 0.8, "jawOpen": 0.18},
	"smile": {"mouthSmileLeft": 0.35, "mouthSmileRight": 0.28, "cheekSquintLeft": 0.15, "cheekSquintRight": 0.12},
	"sad": {"browInnerUp": 0.55, "mouthFrownLeft": 0.4, "mouthFrownRight": 0.4},
	"pain": {"browDownLeft": 0.6, "browDownRight": 0.6, "eyeSquintLeft": 0.6, "eyeSquintRight": 0.6, "mouthStretchLeft": 0.4, "mouthStretchRight": 0.4, "jawOpen": 0.12},
	"dead": {"jawOpen": 0.16, "eyeLookUpLeft": 0.2, "eyeLookUpRight": 0.2},
	"wince": {"eyeBlinkLeft": 0.95, "eyeBlinkRight": 0.95, "browDownLeft": 0.6, "browDownRight": 0.6, "noseSneerLeft": 0.35,
		"noseSneerRight": 0.35, "mouthStretchLeft": 0.35, "mouthStretchRight": 0.35}, "none": {}}


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
	if autoplay and not ("--snaps" in args) and not ("--shotlog" in args):
		Engine.time_scale = 4.0
	if "--pick" in args:
		_forced = Array(String(args[args.find("--pick") + 1]).split(",")).map(func(x): return int(x))
	if "--miss" in args:
		_miss = Array(String(args[args.find("--miss") + 1]).split(","))
	_fast = "--fast" in args
	if "--flags" in args:   # проверка ветки: --flags late=1,trust=2,danny=1
		for kv in String(args[args.find("--flags") + 1]).split(","):
			var p := kv.split("=")
			if p.size() == 2:
				flags[p[0]] = float(p[1])
	if "--stop" in args:   # проверка одной сцены: выйти, когда дошли до этой
		_stop = args[args.find("--stop") + 1]
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
	if id == _stop:
		get_tree().quit()
		return
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
	if b.has("sit"):   # сесть на крыльцо/ящик: таз — в точку (после того как встал клип сидя)
		await get_tree().process_frame
		await get_tree().process_frame
		var st: Array = b.sit.at
		_sit(String(b.sit.who), Vector3(st[0], st[1], st[2]))
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
			var want: int = (_forced.pop_front() if not _forced.is_empty() else _rng.randi() % opts.size())
			get_tree().create_timer(1.0).timeout.connect(func(): if hud.choosing(): hud.pick(clampi(want, 0, opts.size() - 1)))
		var i: int = await hud.chosen
		_log("выбор %d: %s" % [i, "молчание" if opts[i].get("silent", false) else hud.T(opts[i])])
		return String(opts[i].get("go", ""))
	if b.has("peek"):   # можно посмотреть (в зеркало): нажал за t секунд — ветка «yes», нет — «no»
		var seen: bool = await _do_peek(float(b.get("t", 5.0)), b.get("hint"))
		_log("посмотрел: %s" % ("да" if seen else "нет"))
		return String(b.yes if seen else b.no)
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
	var left := minf(t, 0.7) if _fast else t
	_skip = false
	while left > 0.0 and not _skip:
		await get_tree().process_frame
		left -= get_process_delta_time()


func _cut(shot: String) -> void:
	_shot = shot
	scene.car.mirror(shot in ["mirror", "mirror_back"], "back" if shot == "mirror_back" else "road")
	_place_cam()
	if _shotlog != "":
		_shot_n += 1
		var n := _shot_n
		get_tree().create_timer(0.6).timeout.connect(func():
			get_viewport().get_texture().get_image().save_png("%s/%03d_%s.png" % [_shotlog, n, shot]))


## План: из таблицы машины; «руки» — крупно на руль, где бы он ни встал под руки героя.
func _place_cam() -> void:
	if _shot == "pov":   # глазами Ноя: камера у глаз (чуть перед лицом), вперёд по машине и немного вниз — руль и руки в кадре
		var noah: Actor = actors.noah
		var hb := noah.skel.global_transform * noah.skel.get_bone_global_pose(noah.skel.find_bone("head"))
		var eye := hb.origin + hb.basis.y.normalized() * 0.09 + hb.basis.z.normalized() * 0.13
		cam.fov = 72.0
		cam.look_at_from_position(eye, eye - scene.car.global_transform.basis.z * 10.0 + Vector3(0, -3.0, 0))
	elif _shot in ["mirror", "mirror_back"]:   # глазами Ноя — в зеркало заднего вида
		var noah: Actor = actors.noah
		var hb := noah.skel.global_transform * noah.skel.get_bone_global_pose(noah.skel.find_bone("head"))
		var eye := hb.origin + hb.basis.y.normalized() * 0.09 + hb.basis.z.normalized() * 0.1
		cam.fov = 13.0
		cam.look_at_from_position(eye, scene.car.to_global(Car.MIRROR))
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
		"noah_hands":   # кровь на руках
			var n: Actor = actors.noah
			return (n.skel.global_transform * n.skel.get_bone_global_pose(n.skel.find_bone("hand_r")).origin
				+ n.skel.global_transform * n.skel.get_bone_global_pose(n.skel.find_bone("hand_l")).origin) * 0.5
		"deer":
			if is_instance_valid(deer):
				return deer.head_pos()
		"windshield":
			return scene.car.to_global(Vector3(-0.3, 1.2, -0.75))
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
	if _fast:   # быстрый лист кадров: сразу на месте
		a.position = dst
		a.play(String(w.get("then", "Idle")), 0.0)
		if w.get("face") != null:
			a.rotation.y = deg_to_rad(float(w.face))
		_walks.erase(who)


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
	if float(flags.get("late", 0)) > 0.0:   # сидели с оленем — чёрная машина уже здесь: у края площадки, фары выключены
		blackcar = BlackCar.new()
		scene.station_root.add_child(blackcar)
		blackcar.position = Vector3(9.2, 0.0, -24.0)
		blackcar.rotation_degrees = Vector3(0, 200, 0)


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


## Заправочный пистолет в правой руке Ноя (после провала): ручка, носик, шланг вниз.
func _attach_nozzle(a: Actor) -> void:
	var ba := BoneAttachment3D.new()
	ba.bone_name = "hand_r"
	a.skel.add_child(ba)
	var dark := StandardMaterial3D.new()
	dark.albedo_color = Color(0.03, 0.03, 0.035)
	dark.roughness = 0.5
	var steel := StandardMaterial3D.new()
	steel.albedo_color = Color(0.55, 0.56, 0.58)
	steel.metallic = 0.9
	steel.roughness = 0.3
	var h := MeshInstance3D.new()
	var hb := BoxMesh.new()
	hb.size = Vector3(0.035, 0.13, 0.05)
	h.mesh = hb
	h.material_override = dark
	h.position = Vector3(0.0, 0.07, 0.02)
	ba.add_child(h)
	var sp := MeshInstance3D.new()
	var sc := CylinderMesh.new()
	sc.top_radius = 0.009
	sc.bottom_radius = 0.011
	sc.height = 0.17
	sp.mesh = sc
	sp.material_override = steel
	sp.position = Vector3(0.0, 0.11, 0.1)
	sp.rotation_degrees = Vector3(75, 0, 0)
	ba.add_child(sp)
	var hose := MeshInstance3D.new()
	var hc := CylinderMesh.new()
	hc.top_radius = 0.016
	hc.bottom_radius = 0.016
	hc.height = 0.6
	hose.mesh = hc
	hose.material_override = dark
	hose.position = Vector3(0.0, -0.25, -0.01)
	ba.add_child(hose)


## Провал: Ной у колонки с пистолетом, дверь треснула, кассир на полу (жив — стонет; «Тень» ≥ 1 — нет), мальчик застыл, Айрис в машине.
func _lapse_after() -> void:
	_place({"who": "noah", "at": [13.0, 0.0, -4.1], "face": -100, "clip": "Idle"})
	_attach_nozzle(actors.noah)
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
	(actors.noah as Actor).look_target = _target("iris")


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
		"leave_station":   # отъезжаем: заправка позади, трасса; чёрная машина у края площадки (фары выключены), огни заправки — в зеркале
			scene.leave_station()
			for who in ["roman", "danny"]:
				if actors.has(who) and (actors[who] as Actor).get_parent() != scene.car:
					(actors[who] as Actor).queue_free()
					actors.erase(who)
			_place({"who": "noah", "car": "driver"})
			_place({"who": "iris", "car": "passenger"})
			scene.road.speed = 7.0
			var tl := create_tween().set_parallel()
			tl.tween_property(scene.road, "speed", 25.0, 14.0).set_ease(Tween.EASE_IN).set_trans(Tween.TRANS_SINE)
			tl.tween_property(scene.car, "speed", 25.0, 14.0).set_ease(Tween.EASE_IN).set_trans(Tween.TRANS_SINE)
			scene.car.speed = 7.0
			scene.station_glow(14.0)
			blackcar = BlackCar.new()
			scene.road_items.add_child(blackcar)
			blackcar.position = Vector3(4.4, 0.0, -26.0)    # у выезда с площадки, впереди: они проезжают мимо — и он позади, в зеркале
			blackcar.rotation_degrees = Vector3(0, 35, 0)    # носом к дороге — ждёт
		"blackcar_lights":
			if is_instance_valid(blackcar):
				blackcar.lights(true)
		"blackcar_turn":   # не едет за ними — разворачивается к заправке
			if is_instance_valid(blackcar):
				var tb := create_tween()
				tb.tween_property(blackcar, "rotation_degrees:y", 180.0, 2.2).set_ease(Tween.EASE_IN_OUT)
				tb.parallel().tween_property(blackcar, "speed", 5.0, 2.2)
		"signpost":
			scene.signpost(-95.0, "СЕВЕР  640" if lang == "ru" else "NORTH  640")
		"snow_heavy":   # впереди буран: снега больше, летит гуще
			scene.road.snow.amount = 5200 if scene.pc else 2400
			scene.env.fog_density = 0.02
		"diary":
			hud.show_page("Если со мной что-то случится —\nНой всё видел.\n\n                                      А." if lang == "ru"
				else "If anything happens to me —\nNoah saw everything.\n\n                                      A.")
		"diary_off":
			hud.hide_page()
		"deer_road":   # олень посреди дороги в 70 м: боком, голова к машине, глаза светятся в фарах
			if not is_instance_valid(deer):
				deer = Deer.new()
				scene.road_items.add_child(deer)
			deer.mode = "stand"
			deer.fall = 0.0
			deer.life = 1.0
			deer.place_body(Vector3(-0.3, 0.0, BUMPER - 0.22 - 70.0), -90.0)
			deer.look_target = scene.car.to_global(Vector3(0, 0.9, BUMPER))
			deer.shine_from = scene.car.to_global(Vector3(0, 0.67, -2.85))
			_brake_a = 0.0
		"deer_look":   # встали в метре: олень смотрит в лобовое
			if is_instance_valid(deer):
				deer.look_target = _target("windshield")
		"deer_walk_off":   # уходит в лес — через встречную полосу
			if is_instance_valid(deer):
				deer.look_target = null
				deer.speed = 1.25
				deer.mode = "walk"
		"noah_to_deer":   # Ной выходит из машины и идёт к оленю: присаживается у его головы, со стороны машины
			var hp := deer.head_pos() if is_instance_valid(deer) else Vector3(-1.5, 0.0, -9.5)
			var dst := Vector3(hp.x + 1.1, 0.0, hp.z + 0.6)
			_place({"who": "noah", "at": [-1.35, 0.0, 0.25], "face": 180, "clip": "Idle"})
			_walk({"who": "noah", "to": [dst.x, 0.0, dst.z], "then": "Crouch_Idle", "face": rad_to_deg(atan2(hp.x - dst.x, hp.z - dst.z))})
		"deer_drag":   # оттащил с дороги: олень на обочине (в краю света фары), кровавый след; Ной на коленях у его головы
			if is_instance_valid(deer):
				var from := deer.head_pos()
				deer.place_body(Vector3(-4.3, 0.05, -11.6), 90.0)
				var hp := deer.head_pos()
				scene.blood_trail(Vector3(from.x, 0.003, from.z), Vector3(hp.x, 0.053, hp.z))
				var kn := Vector3(hp.x + 0.62, 0.05, hp.z + 0.3)
				_place({"who": "noah", "at": [kn.x, kn.y, kn.z], "face": rad_to_deg(atan2(hp.x - kn.x, hp.z - kn.z)), "clip": "Fixing_Kneeling"})
			var fill := OmniLight3D.new()                  # холодный свет «от снега и луны» на обочине — иначе там темно
			fill.light_color = Color(0.55, 0.62, 0.82)
			fill.light_energy = 0.7
			fill.omni_range = 5.5
			fill.light_volumetric_fog_energy = 0.0
			fill.position = Vector3(-2.6, 2.4, -10.2)
			scene.road_items.add_child(fill)
			get_tree().create_timer(30.0).timeout.connect(fill.queue_free)
		"deer_quiet":   # затихает: дыхание реже и слабее
			if is_instance_valid(deer):
				deer.die(4.5)
		"deer_dead":
			if is_instance_valid(deer):
				deer.mode = "dead"
		"thud":   # удар монтировкой — за кадром: кадр вздрагивает
			_shake = 0.45
			_jolt(0.12)
		"drive_on":   # Ной за руль, поехали; олень остаётся позади
			if (actors.noah as Actor).get_parent() != scene.car:
				_place({"who": "noah", "car": "driver"})
			(actors.iris as Actor).look_target = null
			_expr({"who": "iris", "set": "none"})
			var tw := create_tween().set_parallel()
			tw.tween_property(scene.road, "speed", 25.0, 4.0).set_ease(Tween.EASE_IN).set_trans(Tween.TRANS_SINE)
			tw.tween_property(scene.car, "speed", 25.0, 4.0).set_ease(Tween.EASE_IN).set_trans(Tween.TRANS_SINE)


## Рывок машины: героев (и их волосы) встряхивает.
func _jolt(k: float) -> void:
	for a in actors.values():
		(a as Actor).hair_kick(Vector3(randf_range(-1.0, 1.0) * k, k * 0.4, randf_range(-0.5, 0.5) * k))


func _do_peek(t: float, hint_d) -> bool:
	hud.hint.text = hud.T(hint_d) if hint_d != null else ""
	var left := t
	var seen := false
	while left > 0.0:
		await get_tree().process_frame
		left -= get_process_delta_time()
		if autoplay:
			seen = not ("peek" in _miss) and t - left > 0.8
		elif Input.is_key_pressed(KEY_Q) or Input.is_key_pressed(KEY_UP) or Input.is_key_pressed(KEY_TAB) \
				or Input.is_action_pressed("ui_up") or Input.is_mouse_button_pressed(MOUSE_BUTTON_RIGHT):
			seen = true
		if seen:
			break
	hud.hint.text = ""
	return seen


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
		elif kind == "brake":   # олень впереди: тормоз вовремя — машина встаёт в метре от него; поздно или не нажал — удар
			if not is_instance_valid(deer):
				break
			var v: float = scene.road.speed
			var gap := BUMPER - deer.body_center().z - 0.22
			var press := _brake_in
			if autoplay:
				press = not ("brake" in _miss) and t - left > 0.6
			if press and _brake_a == 0.0:   # сколько нужно, чтобы встать в метре; больше 11 м/с² не выйдет — удар
				_brake_a = minf(v * v / (2.0 * maxf(gap - 1.1, 0.05)), 11.0) if gap > 1.15 else 11.0
				_shake = 0.35
				hud.hint.text = ""
			if _brake_a > 0.0:
				v = maxf(0.0, v - _brake_a * dt)
				scene.road.speed = v
				scene.car.speed = v
			if gap <= 0.03 and v > 0.2:
				_qte_ok = false
				_deer_hit()
				break
			if _brake_a > 0.0 and v <= 0.0:
				break
	hud.hint.text = ""
	_qte = ""
	scene.car.steer = 0.0
	return _qte_ok


## Удар: темнота, рывок, машина встала, левая фара разбита; оленя отбросило на встречную полосу — лежит, бьётся, кровь.
func _deer_hit() -> void:
	hud.fade.color = Color(0, 0, 0, 1)
	_shake = 1.6
	_jolt(1.0)
	_pitch_v = -0.4
	scene.road.speed = 0.0
	scene.car.speed = 0.0
	scene.car.break_lamp()
	flags["lamp"] = 1
	deer.mode = "lie"
	deer.fall = 1.0
	deer.look_target = null
	deer.shine_from = null
	deer.place_body(Vector3(-2.3, 0.0, -9.6), 90.0)
	get_tree().create_timer(0.15).timeout.connect(func():   # лужа — под головой (поза лёжа уже встала)
		var h := deer.head_pos()
		scene.blood_pool(Vector3(h.x, 0.004, h.z)))


## Карта главы 1: что решили (ярко), что видели в прошлых прохождениях (серым), что ещё не открыто («???»).
## Открытые ветки копятся между прохождениями — user://lake_progress.json.
const MAP1 := [
	{"key": "deer", "ru": "Олень", "en": "The deer", "ids": ["1", "2", "3", "4", "5"],
		"o_ru": ["успел", "добил", "уехал", "посидел с ним", "не смог шевельнуться"], "o_en": ["stopped in time", "finished it", "drove away", "stayed with it", "froze"]},
	{"key": "pills", "ru": "Таблетка", "en": "The pill", "ids": ["take", "no", "throw"],
		"o_ru": ["принял", "не принял", "выбросил"], "o_en": ["took one", "refused", "threw them out"]},
	{"key": "roman", "ru": "Кассир Роман", "en": "Roman, the cashier", "ids": ["pay", "cord", "ally", "hurt", "dead"],
		"o_ru": ["заплатил и ушёл", "вырвал провод", "союзник", "ранен", "мёртв"], "o_en": ["paid and left", "ripped the cord", "ally", "hurt", "dead"]},
	{"key": "danny", "ru": "Дэнни", "en": "Danny", "ids": ["with", "left"], "o_ru": ["с вами", "остался"], "o_en": ["came along", "left behind"]},
	{"key": "mirror", "ru": "Зеркало", "en": "The mirror", "ids": ["1", "0"], "o_ru": ["увидел чёрную машину", "не посмотрел"],
		"o_en": ["saw the black car", "didn't look"]},
]


func _map_pick(key: String) -> String:
	match key:
		"deer":
			return str(int(flags.get("deer", 1)))
		"pills":
			return "take" if float(flags.get("pills", 0)) > 0.0 else ("throw" if float(flags.get("threw", 0)) > 0.0 else "no")
		"roman":
			if float(flags.get("roman_ally", 0)) > 0.0:
				return "ally"
			if float(flags.get("roman_dead", 0)) > 0.0:
				return "dead"
			if float(flags.get("roman_hurt", 0)) > 0.0:
				return "hurt"
			return "cord" if float(flags.get("police_late", 0)) > 0.0 else "pay"
		"danny":
			return "with" if float(flags.get("danny", 0)) > 0.0 else "left"
		"mirror":
			return str(int(flags.get("mirror", 0)))
	return ""


func _chapter_map() -> void:
	var prog := {}
	if FileAccess.file_exists("user://lake_progress.json"):
		var j = JSON.parse_string(FileAccess.get_file_as_string("user://lake_progress.json"))
		if j is Dictionary:
			prog = j
	var rows := []
	var opened := 0
	var total := 0
	for r in MAP1:
		var pick := (r.ids as Array).find(_map_pick(String(r.key)))
		if pick >= 0:
			prog["ch1:%s:%s" % [r.key, r.ids[pick]]] = true
		var seen := []
		for id in r.ids:
			var sn := prog.has("ch1:%s:%s" % [r.key, id])
			seen.append(sn)
			opened += 1 if sn else 0
			total += 1
		rows.append({"name": r.get(lang, r.ru), "opts": r.get("o_" + lang, r.o_ru), "pick": pick, "seen": seen})
	var f := FileAccess.open("user://lake_progress.json", FileAccess.WRITE)
	if f:
		f.store_string(JSON.stringify(prog))
	hud.chapter_map("ГЛАВА 1 · КРОВЬ НА РУКАХ" if lang == "ru" else "CHAPTER 1 · BLOOD ON HIS HANDS", rows, opened, total, int(flags.get("trust", 0)))
	_log("карта главы: открыто %d из %d" % [opened, total])


func _end() -> void:
	hud.clear_text()
	create_tween().tween_property(hud.fade, "color:a", 1.0, 1.5)
	await _wait(1.6)
	_chapter_map()
	await _wait(10.0)
	if _shotlog != "":
		get_viewport().get_texture().get_image().save_png("%s/zz_map.png" % _shotlog)
	hud.hide_map()
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
	_brake_in = Input.is_key_pressed(KEY_S) or Input.is_key_pressed(KEY_DOWN) or Input.is_key_pressed(KEY_SPACE) \
		or Input.is_action_pressed("ui_down") or Input.is_action_pressed("ui_accept") or Input.is_mouse_button_pressed(MOUSE_BUTTON_LEFT)
	# клевок кузова: тормозит — нос вниз, встал — качнулся назад (пружина)
	var want := -0.022 * (_brake_a / 11.0) if (_brake_a > 0.0 and scene.road.speed > 0.0) else 0.0
	_pitch_v += ((want - _pitch) * 60.0 - _pitch_v * 7.0) * delta
	_pitch += _pitch_v * delta
	scene.car.rotation.x = _pitch
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
