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
	_run(start)


## Ной за рулём, Айрис рядом (модели пока из Hush Till Six: Итан и Лили — заменим своими).
func _cast() -> void:
	var noah := Actor.make("noah", "ethan")
	scene.car.add_child(noah)
	noah.position = Car.DRIVER + Vector3(0, 0.15, 0)
	noah.rotation_degrees = Vector3(0, 180, 0)
	noah.play("Driving", 0.5)
	noah.enable_blood()
	actors.noah = noah
	var iris := Actor.make("iris", "lily")
	scene.car.add_child(iris)
	iris.position = Car.PASSENGER + Vector3(0, 0.15, 0)
	iris.rotation_degrees = Vector3(0, 180, 0)
	iris.play("Rk_SitIdle", 1.0)
	actors.iris = iris
	await get_tree().process_frame
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


func _wait(t: float) -> void:
	var left := t
	_skip = false
	while left > 0.0 and not _skip:
		await get_tree().process_frame
		left -= get_process_delta_time()


func _cut(shot: String) -> void:
	_shot = shot
	_place_cam()


## План: из таблицы машины; «руки» — крупно на руль, где бы он ни встал под руки героя.
func _place_cam() -> void:
	if _shot == "hands":
		cam.fov = 36.0
		cam.look_at_from_position(scene.car.to_global(Vector3(0.12, 1.08, -0.42)), scene.car.wheel.global_position + Vector3(0, -0.02, 0))
	else:
		scene.put_camera(cam, _shot)


func _look(what) -> void:
	var iris: Actor = actors.iris
	var noah: Actor = actors.noah
	match String(what):
		"noah":
			iris.look_target = noah.skel.global_transform * noah.skel.get_bone_global_pose(noah.skel.find_bone("head")).origin
		"notebook":
			iris.look_target = scene.car.to_global(Vector3(0.36, 0.55, 0.05))
		"window":
			iris.look_target = scene.car.to_global(Vector3(3.0, 1.1, 0.2))
		_:
			iris.look_target = null


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
			create_tween().tween_property(fade, "color:a", 1.0, 0.6)
		"retry":
			hud.show_note({"ru": "Попробуй ещё раз.", "en": "Try again."})
		"skid", "u_turn", "grab_wheel":
			_shake = 0.8
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
