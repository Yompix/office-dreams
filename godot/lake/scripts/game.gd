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
var _qte_n := 10              # сколько нажатий нужно (действия mash, press и rock)
var _qte_gap := 0.7           # rock: не чаще одного нажатия за столько секунд (укачать — медленно, в ритм)
var _qte_got := 0             # сколько нажатий уже засчитано (для постановки: качать младенца в такт)
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
const SAVE := "user://lake_save.json"   # контрольная точка: сцена (с "save": true), флаги, язык
var _menu_mode := false
var _brake_in := false
var _brake_a := 0.0           # торможение, м/с²
var _pitch := 0.0             # клевок кузова при торможении (рад) и его скорость — пружина
var _pitch_v := 0.0
const BUMPER := -2.94         # передний бампер машины (z в осях машины)
var sfx: Sfx                  # звук: фоны мест, петли, разовые (scripts/audio.gd)
var _scene_id := ""
var _underwater := false      # пролог: под водой
var _blackout := false        # провал: всё глохнет
var _blizzard := false        # конец главы: буран
var _radio := false           # радио включено: под голосом диктора шипит эфир
var _ending := false
var _skid_cd := 0.0
var _vo := {}                 # озвучка языка: ключ реплики → {dur, w (слова со временем), e (громкость), g}
var _vo_lang := ""
var _chapters := {}           # номер главы → {start, ready, title, map}
var c4 := {}                  # состояние главы 4 (scripts/chapter4.gd): буханка, остановка, сосна, лес, озеро
var c3 := {}                  # состояние главы 3 (scripts/chapter3.gd): деревня, дом Агнес, роды, ночь
var c2 := {}                  # состояние главы 2 (scripts/chapter2.gd): двойник в зеркале, пост, зеркало туалета…
const IN_CAR := ["pov", "hands", "noah", "iris", "two", "notebook", "radio", "glovebox", "road", "cluster", "pills", "back",
	"deer_eye", "mirror", "mirror_back"]    # планы из салона: снаружи слышно глухо
const SHOP := ["st_counter", "st_tv", "st_roman", "st_hand", "st_noah_face"]   # планы в магазине заправки
const FLOOR := 0.38           # пол магазина и крыльцо заправки
## выражения лица (формы ARKit): страх, шок, улыбка, грусть, боль, «мёртвый взгляд»
const EXPR := {"fear": {"browInnerUp": 0.8, "eyeWideLeft": 0.7, "eyeWideRight": 0.7, "mouthStretchLeft": 0.25, "mouthStretchRight": 0.25, "jawOpen": 0.08},
	"shock": {"browInnerUp": 0.6, "browOuterUpLeft": 0.5, "browOuterUpRight": 0.5, "eyeWideLeft": 0.8, "eyeWideRight": 0.8, "jawOpen": 0.18},
	"smile": {"mouthSmileLeft": 0.35, "mouthSmileRight": 0.28, "cheekSquintLeft": 0.15, "cheekSquintRight": 0.12},
	"sad": {"browInnerUp": 0.55, "mouthFrownLeft": 0.4, "mouthFrownRight": 0.4},
	"pain": {"browDownLeft": 0.6, "browDownRight": 0.6, "eyeSquintLeft": 0.6, "eyeSquintRight": 0.6, "mouthStretchLeft": 0.4, "mouthStretchRight": 0.4, "jawOpen": 0.12},
	"dead": {"jawOpen": 0.16, "eyeLookUpLeft": 0.2, "eyeLookUpRight": 0.2},
	"wince": {"eyeBlinkLeft": 0.95, "eyeBlinkRight": 0.95, "browDownLeft": 0.6, "browDownRight": 0.6, "noseSneerLeft": 0.35,
		"noseSneerRight": 0.35, "mouthStretchLeft": 0.35, "mouthStretchRight": 0.35}, "none": {},
	"sleep": {"eyeBlinkLeft": 1.0, "eyeBlinkRight": 1.0, "mouthClose": 0.1}}


func _ready() -> void:
	process_priority = 10   # режиссёр — после мира: дорога и герои уже сдвинулись за кадр, камера ставится без отставания
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
	sfx = Sfx.new()
	add_child(sfx)
	data = JSON.parse_string(FileAccess.get_file_as_string("res://data/ch1.json"))
	data["chapter"] = 1
	_chapters[1] = {"start": String(data.start), "ready": true}
	for n in range(2, 6):   # главы 2–5: сцены, имена, флаги — в общий сценарий; карта и начало — по главам
		var path := "res://data/ch%d.json" % n
		if not FileAccess.file_exists(path):
			continue
		var ch = JSON.parse_string(FileAccess.get_file_as_string(path))
		if not (ch is Dictionary):
			continue
		_chapters[n] = {"start": String(ch.start), "ready": bool(ch.get("ready", false)), "title": ch.get("title", {}), "map": ch.get("map", [])}
		for k in ch.scenes:
			data.scenes[k] = ch.scenes[k]
		for k in ch.get("names", {}):
			data.names[k] = ch.names[k]
		for k in ch.get("flags", {}):
			data.flags[k] = ch.flags[k]
	for sid in data.scenes:   # ключ озвучки каждой реплики: <сцена>_<номер реплики в сцене> (как tools/lake/voice.py)
		var n := 0
		for b in data.scenes[sid].beats:
			if (b as Dictionary).has("say"):
				n += 1
				b["_vo"] = "%s_%d" % [sid, n]
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
	if autoplay or "--scene" in args or "--menu-skip" in args:
		_run(start)
	else:
		_menu()


## Главное меню поверх ночной трассы: новая игра, продолжить с контрольной точки, язык, выход.
func _menu() -> void:
	_menu_mode = true
	_shot = "menu"
	hud.fade.color = Color(0, 0, 0, 1)
	create_tween().tween_property(hud.fade, "color:a", 0.0, 2.0)
	while true:
		hud.lang = lang
		hud.show_menu(FileAccess.file_exists(SAVE))
		var id: String = await hud.menu_pick
		sfx.play("ui_click", -4.0)
		if id == "lang":
			lang = "en" if lang == "ru" else "ru"
			continue
		hud.hide_menu()
		_menu_mode = false
		if id == "quit":
			get_tree().quit()
		elif id == "cont" and _load_save():
			return
		else:
			flags = (data.flags as Dictionary).duplicate()
			_run(String(data.start))
		return


func _save(id: String) -> void:
	var f := FileAccess.open(SAVE, FileAccess.WRITE)
	if f:
		f.store_string(JSON.stringify({"scene": id, "flags": flags, "lang": lang}))


## Продолжить: флаги и язык из сохранения; что видно (фара, Дэнни сзади) — по флагам; сцена — с начала.
func _load_save() -> bool:
	var j = JSON.parse_string(FileAccess.get_file_as_string(SAVE)) if FileAccess.file_exists(SAVE) else null
	if not (j is Dictionary) or not data.scenes.has(String(j.get("scene", ""))):
		return false
	flags = (data.flags as Dictionary).duplicate()
	for k in (j.flags as Dictionary):
		flags[k] = j.flags[k]
	lang = String(j.get("lang", lang))
	hud.lang = lang
	var id := String(j.scene)
	if float(flags.get("lamp", 0)) > 0.0:
		scene.car.break_lamp()
	if float(flags.get("danny", 0)) > 0.0 and (id in ["s7", "s8"] or id.begins_with("c")) and not actors.has("danny"):
		var d := Actor.make("danny", "danny")
		scene.car.add_child(d)
		actors.danny = d
		_seat_in_car("danny", "back")
	if id.begins_with("c2"):
		Ch2.restore(self, id)   # глава 2: кто в машине (Зоя, Ева), время суток
	elif id.begins_with("c3"):
		Ch3.restore(self, id)   # глава 3: дорога, деревня или дом Агнес
	elif id.begins_with("c4"):
		Ch4.restore(self, id)   # глава 4: двор Агнес, буханка, лес, озеро
	_run(id)
	return true


## Ной за рулём, Айрис рядом — свои герои (tools/lake/chars.json → assets/chars).
func _cast() -> void:
	var noah := Actor.make("noah", "noah")
	for mi in noah.find_children("*", "MeshInstance3D", true, false):
		(mi as MeshInstance3D).layers = Car.DRIVER_LAYER   # Ной не попадает в зеркало заднего вида, когда оно смотрит назад
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
	_scene_id = id
	if sc.get("save", false) and not autoplay:
		_save(id)
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
		var vo: Dictionary = _voice_line(String(b.get("_vo", "")))
		hud.line(who, b.get("name", data.names.get(who, "")), b)   # "name" в реплике — своё имя (голос Хейла в главе 2 — «ГОЛОС»)
		var lips := String(b.get("lips", who))   # чьи губы двигаются (говорит «чужой голос» — губы Ноя)
		if not vo.is_empty():   # озвучка: длина реплики — по голосу, губы — по словам и громкости
			dur = maxf(float(vo.dur) + 0.35, 1.0)
			if actors.has(lips):
				(actors[lips] as Actor).say(text, float(vo.dur), vo.get("w", []), String(vo.get("e", "")))
		elif actors.has(lips):
			(actors[lips] as Actor).say(text, dur * 0.8)
		await _wait(dur)
		sfx.voice_stop()
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
		var opts: Array = (c.opts as Array).filter(func(o): return not (o as Dictionary).has("if") or _cond(o["if"]))
		hud.choice(opts, float(c.get("t", 6)))
		if autoplay:
			var want: int = (_forced.pop_front() if not _forced.is_empty() else _rng.randi() % opts.size())
			get_tree().create_timer(1.0).timeout.connect(func(): if hud.choosing(): hud.pick(clampi(want, 0, opts.size() - 1)))
		var i: int = await hud.chosen
		sfx.play("ui_click", -4.0)
		_log("выбор %d: %s" % [i, "молчание" if opts[i].get("silent", false) else hud.T(opts[i])])
		return String(opts[i].get("go", ""))
	if b.has("peek"):   # можно посмотреть (в зеркало): нажал за t секунд — ветка «yes», нет — «no»
		var seen: bool = await _do_peek(float(b.get("t", 5.0)), b.get("hint"))
		_log("посмотрел: %s" % ("да" if seen else "нет"))
		return String(b.yes if seen else b.no)
	if b.has("qte"):
		_qte_n = int(b.get("n", 10))
		_qte_gap = float(b.get("gap", 0.7))
		var ok: bool = await _do_qte(String(b.qte), float(b.get("t", 3.0)), b.get("hint"))
		_log("действие %s: %s" % [b.qte, "удалось" if ok else "нет"])
		if not ok and b.has("fail"):
			return String(b.fail)
	if b.has("go"):
		return String(b.go)
	if b.has("end") and b.end:
		var n := int(b.end) if (b.end is float or b.end is int) else 1
		await _end(n)
		var nx: Dictionary = _chapters.get(n + 1, {})
		if not nx.is_empty() and nx.ready:
			hud.title.text = ""
			return String(nx.start)
		await _to_be_continued()
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
		left -= _dt()


## Время кадра для пауз и действий: на паузе — 0 (сюжет стоит).
func _dt() -> float:
	return 0.0 if get_tree().paused else get_process_delta_time()


func _cut(shot: String) -> void:
	_shot = shot
	scene.car.mirror(shot in ["mirror", "mirror_back", "mirror_self", "mirror_zoe"], {"mirror_back": "back", "mirror_zoe": "back", "mirror_self": "self"}.get(shot, "road"))
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
	elif Ch2.cam(self, _shot) or Ch3.cam(self, _shot) or Ch4.cam(self, _shot):   # планы глав 2–4, которые зависят от того, где герои
		pass
	elif _shot in ["mirror", "mirror_back", "mirror_self", "mirror_zoe"]:   # глазами Ноя — в зеркало заднего вида
		var noah: Actor = actors.noah
		var hb := noah.skel.global_transform * noah.skel.get_bone_global_pose(noah.skel.find_bone("head"))
		var eye := hb.origin + hb.basis.y.normalized() * 0.09 + hb.basis.z.normalized() * 0.1
		cam.fov = 13.0
		cam.look_at_from_position(eye, scene.car.to_global(Car.MIRROR))
	elif _shot == "menu":   # меню: машина идёт на камеру в снегу, камера медленно плывёт сбоку
		cam.fov = 34.0
		var a := _t * 0.05
		cam.look_at_from_position(scene.car.to_global(Vector3(-2.6 + sin(a) * 0.8, 0.95 + sin(a * 0.7) * 0.15, -8.5)), scene.car.to_global(Vector3(1.7, 0.9, -0.5)))
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
	var s: Vector3 = {"driver": Car.DRIVER, "passenger": Car.PASSENGER, "back": Car.BACK, "back_l": Car.BACK_L, "back_m": Car.BACK_M}[seat]
	a.position = s + Vector3(0, 0.15, 0)
	a.rotation_degrees = Vector3(0, 180, 0)
	a.play("Driving" if seat == "driver" else "Rk_SitIdle", 0.5)
	for k in 2:   # дважды: если герой сел сразу после ходьбы или стоя, первая мерка таза бывает по старой позе
		for f in 3:
			await get_tree().process_frame
		if not is_instance_valid(a) or a.get_parent() != scene.car:
			return
		if seat.begins_with("back"):
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
	if Ch2.fx(self, k) or Ch3.fx(self, k) or Ch4.fx(self, k):   # события глав 2–4 (scripts/chapter2.gd, chapter3.gd, chapter4.gd)
		return
	_fx_sound(k)
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
			scene.car.radio_display("FM 102.4")
		"radio_off":
			scene.car.radio_display("")
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


## Озвучка реплики: играет её голос и отдаёт тайминг ({} — голоса нет, реплика идёт по тексту).
func _voice_line(key: String) -> Dictionary:
	if _vo_lang != lang:
		_vo_lang = lang
		_vo = {}
		var tf := "res://snd/vo/%s/timing.json" % lang
		if FileAccess.file_exists(tf):
			var j = JSON.parse_string(FileAccess.get_file_as_string(tf))
			if j is Dictionary:
				_vo = j
	if key == "" or not _vo.has(key) or _fast:
		return {}
	var v: Dictionary = _vo[key]
	if not sfx.voice("res://snd/vo/%s/%s.ogg" % [lang, key], float(v.get("g", 1.0))):
		return {}
	_log("голос " + key)
	return v


## Разовые звуки к событиям сценария (fx).
func _fx_sound(k: String) -> void:
	var outside := (actors.noah as Actor).get_parent() != scene.car
	match k:
		"underwater":
			_underwater = true
		"lullaby":
			sfx.play("lullaby", -1.0)
		"lullaby_stop":
			sfx.stop("lullaby", 0.8)
		"flash_dock":
			sfx.play("splash", -3.0)
			sfx.play("boom", -10.0)
		"horn":
			_underwater = false
			sfx.play("truck_horn", 0.0)
		"wake":
			_blackout = false
			if is_instance_valid(scene.station_root):
				sfx.play("nozzle", -2.0)
			else:
				sfx.play("heartbeat", -6.0)
		"crash":
			sfx.play("ditch_crash", 0.0)
		"u_turn":
			sfx.play("skid", -6.0)
		"skid":
			sfx.play("skid", -2.0)
		"radio_on":
			_radio = true
			sfx.play("radio_on", -6.0)
		"radio_off":
			if _radio:
				sfx.play("radio_off", -6.0)
			_radio = false
			c2.erase("song")
		"pencil_break":
			sfx.play("pencil_snap", -2.0)
		"notebook_dash":
			sfx.play("pencil_scribble", -6.0)
		"lullaby_iris":
			sfx.play("lullaby_girl", -4.0)
		"deer_look":
			sfx.play("deer_snort", -4.0, 1.0, 0.5)
		"noah_to_deer":
			sfx.play("car_door", -4.0)
		"trunk":
			sfx.play("trunk", -3.0)
		"thud":
			sfx.play("blow", 0.0)
		"deer_quiet":
			if _scene_id == "s3_freeze":
				sfx.play("deer_scream", -5.0)
		"drive_on":
			if outside:
				sfx.play("car_door", -5.0)
			if _scene_id == "s3_leave":   # крик оленя тянется за машиной
				sfx.play("deer_scream", -9.0, 0.97, 0.6)
		"fuel_light":
			sfx.play("fuel_chime", -6.0)
		"ring":
			sfx.play("heartbeat", -3.0)
		"glove_open":
			sfx.play("glovebox", -4.0)
		"glove_close":
			sfx.play("glovebox", -9.0, 1.2)
		"pills_take":
			sfx.play("pills", -4.0)
		"pills_throw":
			sfx.play("window_down", -4.0)
			sfx.play("pills", -10.0, 1.0, 0.7)
		"money":
			sfx.play("bills", -4.0)
		"phone_rip":
			sfx.play("phone_rip", -2.0)
		"lapse":   # провал: белый шум, всё глохнет; в темноте — стекло и крик мальчика
			_blackout = true
			sfx.play("static_burst", -6.0, 1.0, 0.1)
			sfx.play("boom", -8.0)
			sfx.play("glass_shatter", -5.0, 1.0, 1.9)
			sfx.play("child_scream", -12.0, 1.0, 2.5)
		"danny_in_car", "danny_stays":
			sfx.play("car_door", -6.0, 1.0, 0.6)
		"leave_station":
			sfx.play("engine_start", -6.0, 1.0, 0.2)
		"blackcar_lights":
			sfx.play("engine_start", -18.0, 0.92)
		"snow_heavy":
			_blizzard = true
			sfx.play("wipers", -12.0)
		"diary":
			sfx.play("page_turn", -4.0)
		"diary_off":
			sfx.play("page_turn", -10.0, 1.1)


## Звук места — каждый кадр: что слышно там, где камера. В салоне — мотор (выше и громче на скорости), снаружи глухо;
## снаружи — ветер в лесу; на заправке — гул неона, в магазине — холодильники и телевизор; буран; гул в голове Ноя;
## дыхание оленя (тише, когда далеко и когда он слабеет), шаги по снегу.
func _sound(delta: float) -> void:
	var off := Sfx.OFF
	var inside := _shot in IN_CAR
	var station := is_instance_valid(scene.station_root) or is_instance_valid(scene.cafe_root)
	var k := clampf(scene.road.speed / 25.0, 0.0, 1.0)
	var car := off
	var forest := off
	var neon := off
	var shop := off
	var bliz := off
	var drone := -26.0
	if _underwater:
		drone = -16.0
	elif _blackout:
		drone = -18.0
	elif _menu_mode:
		forest = -12.0
		car = -26.0
		drone = off
	elif _ending:
		drone = -12.0
	elif station:
		if _shot.begins_with("cafe") or _shot.begins_with("wc") or _shot in ["noah_cafe", "pill_label"]:   # в кафе: зал, буран за окном
			shop = -4.0
			bliz = -24.0
		elif is_instance_valid(scene.cafe_root):   # снаружи кафе: буран, ветер
			bliz = -8.0
			forest = -14.0
		elif _shot in SHOP:
			shop = -3.0
			neon = -24.0
		elif inside:
			neon = -18.0
			forest = -24.0
		else:
			neon = -7.0
			forest = -16.0
	else:
		car = lerpf(-16.0, -3.0, k) if inside else lerpf(-30.0, -14.0, k)
		forest = -22.0 if inside else -5.0
		if _blizzard:
			bliz = -9.0 if inside else -3.0
			forest -= 10.0
	sfx.bed("car_interior", car, lerpf(0.7, 1.0, k))
	sfx.bed("wind_forest", forest)
	sfx.bed("station_hum", neon)
	sfx.bed("shop_room", shop)
	sfx.bed("blizzard", bliz)
	sfx.bed("underwater", -3.0 if _underwater else off)
	sfx.bed("lake_theme", -5.0 if _menu_mode else off)   # меню: мамина колыбельная (tools/lake/music.py)
	sfx.bed("drone", drone + hud.hum * 10.0)
	sfx.bed("hum_ring", lerpf(-34.0, -4.0, hud.hum) if hud.hum > 0.03 else off)
	var song: bool = c2.get("song", false)
	sfx.bed("radio_hiss", (-20.0 if inside else -34.0) if _radio and not _blackout and not song else off)
	sfx.bed("radio_song", (-14.0 if inside else -30.0) if _radio and song and not _blackout else off)
	var br := off
	var dsteps := off
	var life := 1.0
	if is_instance_valid(deer) and not _menu_mode and not _ending:
		life = deer.life
		var near := clampf((cam.global_position.distance_to(deer.head_pos()) - 1.5) / 14.0, 0.0, 1.0)
		if deer.mode == "lie":
			br = lerpf(-3.0, -22.0, near) - (9.0 if inside else 0.0) - (1.0 - life) * 14.0
		elif deer.mode == "walk":
			dsteps = lerpf(-8.0, -26.0, near) - (8.0 if inside else 0.0)
	sfx.bed("deer_breath", br, lerpf(0.8, 1.0, life))
	sfx.bed("deer_steps", dsteps, 0.8)
	var steps := off
	for who in _walks:
		if (actors[who] as Actor).get_parent() == scene:
			steps = -18.0 if inside else -7.0
	sfx.bed("snow_steps", steps)
	_skid_cd = maxf(0.0, _skid_cd - delta)


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
		left -= _dt()
		if get_tree().paused:
			continue
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
	var hint0: String = hud.T(hint_d) if hint_d != null else ""
	hud.hint.text = hint0
	_qte = kind
	_qte_ok = kind not in ["mash", "press", "rock"]   # жать: удалось, только если набрали нужное число нажатий
	_qte_got = 0
	_qte_drift = 0.0
	var left := t
	var got := 0
	var last_press := -1.0
	var auto_t := 0.0
	var held_prev := true   # нажатие, которое было до начала, не считаем
	while left > 0.0:
		await get_tree().process_frame
		var dt := _dt()
		left -= dt
		if kind == "rock":   # укачать: медленно, в ритм — нажал раньше, чем через _qte_gap, — младенец снова хнычет (−1)
			var held_r := Input.is_key_pressed(KEY_E) or Input.is_key_pressed(KEY_SPACE) or Input.is_action_pressed("ui_accept") \
				or Input.is_mouse_button_pressed(MOUSE_BUTTON_LEFT)
			var hit_r := held_r and not held_prev
			held_prev = held_r
			if autoplay:
				auto_t += dt
				hit_r = auto_t >= (0.24 if kind in _miss else _qte_gap + 0.15)
				if hit_r:
					auto_t = 0.0
			if hit_r:
				var now := t - left
				if last_press < 0.0 or now - last_press >= _qte_gap:
					got += 1
					sfx.play("snow_steps", -24.0, 0.7)
				else:
					got = maxi(0, got - 1)
					sfx.play("baby_cry", -20.0, 1.1)
				last_press = now
				_qte_got = got
			var calm := "" if last_press < 0.0 or t - left - last_press >= _qte_gap else ("   …тише" if lang == "ru" else "   …easy")
			hud.hint.text = "%s   %s%s" % [hint0, "■".repeat(mini(got, _qte_n)) + "□".repeat(maxi(0, _qte_n - got)), calm]
			if got >= _qte_n:
				_qte_ok = true
				break
			continue
		if kind == "mash" or kind == "press":   # mash — быстро n раз; press — в ритм (чаще 8 раз в секунду не засчитывается)
			var held := Input.is_key_pressed(KEY_E) or Input.is_key_pressed(KEY_SPACE) or Input.is_action_pressed("ui_accept") \
				or Input.is_mouse_button_pressed(MOUSE_BUTTON_LEFT)
			var hit := held and not held_prev
			held_prev = held
			if autoplay:
				auto_t += dt
				hit = auto_t >= 0.24 and not (kind in _miss)
				if hit:
					auto_t = 0.0
			if hit and (kind == "mash" or t - left - last_press >= 0.12):
				got += 1
				last_press = t - left
				_shake = maxf(_shake, 0.12)
				_qte_got = got
				sfx.play("blow" if kind == "press" else "snow_steps", -22.0 if kind == "press" else -14.0, randf_range(0.9, 1.15))
			hud.hint.text = "%s   %s" % [hint0, "■".repeat(mini(got, _qte_n)) + "□".repeat(maxi(0, _qte_n - got))]
			if got >= _qte_n:
				_qte_ok = true
				break
			continue
		if kind == "steer":   # машину ведёт к обочине: игрок держит её на полосе
			var push := sin(_t * 1.3) * 0.9 + 0.6   # снос вправо (+), руль влево (−) его гасит
			var inp := _steer_in if not autoplay else clampf(-_qte_drift * 3.0 - push / 2.2, -1.0, 1.0)
			_qte_drift = clampf(_qte_drift + (push + inp * 2.2) * dt, -1.2, 1.2)
			if absf(_qte_drift) > 0.8 and _skid_cd <= 0.0:   # колёса срывает — визг шин
				sfx.play("skid", -9.0)
				_skid_cd = 2.0
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
				sfx.play("brake_squeal", -1.0)
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
	sfx.play("deer_impact", 0.0)
	sfx.play("boom", -7.0)
	sfx.play("deer_scream", -3.0, 1.0, 1.5)
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


func _end(n := 1) -> void:
	hud.clear_text()
	create_tween().tween_property(hud.fade, "color:a", 1.0, 1.5)
	await _wait(1.6)
	_ending = true
	sfx.play("boom", -5.0)
	sfx.play("lake_end", -3.0, 1.0, 0.8)
	if n == 1:
		_chapter_map()
	else:
		_chapter_map_n(n)
	await _wait(10.0)
	if _shotlog != "":
		get_viewport().get_texture().get_image().save_png("%s/zz_map%d.png" % [_shotlog, n])
	hud.hide_map()
	_ending = false


func _to_be_continued() -> void:
	_ending = true
	var t := "Продолжение следует" if lang == "ru" else "To be continued"
	hud.title.text = t + "\n\n" + ("Доверие Айрис: %d" % flags.trust if lang == "ru" else "Iris's trust: %d" % flags.trust)
	_log("конец отрывка, флаги " + JSON.stringify(flags))
	await _wait(3.0)
	if autoplay:
		get_tree().quit()


## Карта главы 2–5 из её данных: у варианта — условие "if" (первый подходящий — выбор игрока в этом прохождении).
func _chapter_map_n(n: int) -> void:
	var ch: Dictionary = _chapters.get(n, {})
	var prog := {}
	if FileAccess.file_exists("user://lake_progress.json"):
		var j = JSON.parse_string(FileAccess.get_file_as_string("user://lake_progress.json"))
		if j is Dictionary:
			prog = j
	var rows := []
	var opened := 0
	var total := 0
	for r in ch.get("map", []):
		var opts: Array = r.opts
		var pick := -1
		for i in opts.size():
			if _cond(opts[i].get("if", {})):
				pick = i
				break
		if pick >= 0:
			prog["ch%d:%s:%s" % [n, r.key, opts[pick].id]] = true
		var seen := []
		var names := []
		for o in opts:
			var sn := prog.has("ch%d:%s:%s" % [n, r.key, o.id])
			seen.append(sn)
			names.append(String(o.get(lang, o.ru)))
			opened += 1 if sn else 0
			total += 1
		rows.append({"name": r.get(lang, r.ru), "opts": names, "pick": pick, "seen": seen})
	var f := FileAccess.open("user://lake_progress.json", FileAccess.WRITE)
	if f:
		f.store_string(JSON.stringify(prog))
	var title: Dictionary = ch.get("title", {})
	hud.chapter_map(String(title.get(lang, title.get("ru", ""))), rows, opened, total, int(flags.get("trust", 0)))
	_log("карта главы %d: открыто %d из %d" % [n, opened, total])


func _log(s: String) -> void:
	print(s)
	log_lines.append(s)


func _unhandled_input(e: InputEvent) -> void:
	if e is InputEventKey and e.pressed and not e.echo and (e as InputEventKey).keycode == KEY_ESCAPE and not _menu_mode:
		_pause()
		return
	if e is InputEventKey and e.pressed and not e.echo:
		var k := (e as InputEventKey).keycode
		if hud.choosing() and k >= KEY_1 and k <= KEY_3:
			hud.pick_number(k - KEY_0)
		elif k == KEY_SPACE or k == KEY_ENTER:
			_skip = true
	elif e is InputEventMouseButton and e.pressed and not hud.choosing():
		_skip = true


## Пауза: всё стоит (герои, машина, таймер выбора), меню поверх.
func _pause() -> void:
	if get_tree().paused:
		return
	get_tree().paused = true
	sfx.duck(true)
	hud.show_pause()
	var id: String = await hud.menu_pick
	sfx.play("ui_click", -4.0)
	hud.hide_menu()
	sfx.duck(false)
	get_tree().paused = false
	if id == "menu":
		get_tree().reload_current_scene()
	elif id == "quit":
		get_tree().quit()


func _process(delta: float) -> void:
	_t += delta
	_sound(delta)
	if not c2.is_empty():
		Ch2.process(self)
		Ch2.flash(self, _t)
	if not c3.is_empty():
		Ch3.process(self, delta)
	if not c4.is_empty():
		Ch4.process(self, delta)
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
