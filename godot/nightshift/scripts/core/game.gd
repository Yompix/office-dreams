extends Node
## Движок интерактивного кино — перенос engine.js старой игры (по частям). Кадр (beat): запись постановки (ShotPlayer),
## камера по планам cams, голос по таймингу, субтитры с подсветкой звучащего слова, переходы сцен и выбор с таймером.
## Проверка без экрана: godot … -- --scene intro --beat 2 --at 3.0 --shot out.png  (кадр в момент, снимок, выход)

const SPK := {"ИТАН": "#cfd3da", "СМЕНЩИК": "#ffd27a", "ЛИЛИ": "#ffb36b", "ФРЭНК": "#8fd0ff", "ГАС": "#9fd8a8",
	"???": "#ff3030", "КАМЕРА": "#7fe0a0", "ТЕЛЕФОН": "#9aa3b5", "ВЕРА": "#7fd4ff", "ГОЛОС": "#c8b4ff"}
const SPK_SUB := {"ВЕРА": "пульт «Щит»"}

var player: ShotPlayer
var ui: CanvasLayer
var dlg: PanelContainer
var who_lbl: Label
var text_lbl: RichTextLabel
var hint_lbl: Label
var black: ColorRect
var choice_box: VBoxContainer
var timer_bar: ProgressBar
var music: AudioStreamPlayer
var voices: Array[AudioStreamPlayer] = []
var sfx_pool: Array[AudioStreamPlayer] = []
var amb_static: AudioStreamPlayer
var phone: PhoneScreen
var post: PostFX
var hud: CctvHud
var clock_t0 := 0.0        # с какого момента идут секунды часов ночи (gameClock)
var sec_base := 7
var T := 0.0               # время игры (для мигания, зерна, ламп)
var sfx_q: Array = []      # звуки кадра по времени: [{at, n, op, done}]

var scene_id := ""
var scene: Dictionary = {}
var bi := -1
var beat: Dictionary = {}
var bt := 0.0              # время кадра (с начала beat)
var bdur := 1.2
var shot_off := 0.0        # тот же кадр подряд — время постановки продолжается
var wait := 0.0            # кадр кончился и ждёт нажатия: время постановки стоит, герои живут
var holding := false
var sched: Array = []      # [{t, k, d, started}]
var typed := {}            # текст без озвучки печатается: { t0, d, s, who }
var talk_v := 0.0          # громкость голоса (сглаженная, как talkV старой игры)
var flags: Dictionary = {}
var choice_t := -1.0
var choice_opts: Array = []
var choice_def := 0
var auto := false
var test := {}             # режим проверки из командной строки
var mode := "play"         # play — кадры сцены; choice — выбор; explore / talk — обход и разговор
var last_time := "02:55"   # часы ночи (scene.time / beat.time)
var act := {}              # обход или разговор: { th, kind, t }
var act_view: ActView
var shift: ShiftGame       # смена идёт
var mg: MiniGames
var ambs := {}             # фоновые петли: heart, static…
var gore_idx := {}         # смерти: bake/gore/index.json (вид, основа, сценарий по секундам, файлы записи)
var death := {}            # смерть этой сцены: { g, gd } — бросок в объектив, потом финал
const JUMP_D := 0.75
const GORE_D := 3.4
var ends: EndScreens
var run := {}              # прохождение (для журнала дежурств): решения, время, часы смены, записи, пропуски
var save_scene := ""       # откуда «Попробовать снова» (@save): последняя сцена, не смерть
static var endings_got := {}   # найденные концовки и смерти (сохранение — позже)
static var overs_got := {}
var shift_ui: ShiftUI
var autoplay := false      # автопрогон для проверки: кадры сами, выбор — по умолчанию, обход — по порядку (--autoplay)


func _ready() -> void:
	Save.load_all()
	Save.ensure_buses()
	var lang := _arg("lang", String(Save.setting("lang", "")))
	if lang == "":   # первый запуск — язык системы, если он есть в игре
		lang = OS.get_locale_language()
	StoryDB.setup(lang)
	Graphics.apply(get_viewport())
	auto = bool(Save.setting("auto", false))
	player = ShotPlayer.new()
	add_child(player)
	_build_ui()
	music = AudioStreamPlayer.new()
	music.volume_db = -6.0
	music.bus = "Music"
	add_child(music)
	for i in 4:
		var v := AudioStreamPlayer.new()
		v.bus = "Voice"
		add_child(v)
		voices.append(v)
	for i in 8:
		var v := AudioStreamPlayer.new()
		v.bus = "SFX"
		add_child(v)
		sfx_pool.append(v)
	amb_static = AudioStreamPlayer.new()
	amb_static.bus = "Amb"
	add_child(amb_static)
	autoplay = OS.get_cmdline_user_args().has("--autoplay")
	auto = auto or autoplay
	if autoplay and _arg("scene", "") != "":
		start_scene(_arg("scene", ""), int(_arg("beat", "0")))
	elif _arg("scene", "") != "":
		test = {"at": float(_arg("at", "1.0")), "shot": _arg("shot", "")}
		for f in _arg("flags", "").split(",", false):   # проверка ветки: какие флаги уже стоят (v:сцена — осмотрено)
			flags[f] = true
		if _arg("then", "") != "":   # сразу то, что после сцены (обход, разговор, выбор)
			scene_id = _arg("scene", "")
			scene = StoryDB.scene(scene_id)
			var th: Dictionary = scene.get("then", {})
			if _arg("then", "") == "fin":   # проверка: сразу журнал дежурств
				th = th.merged({"fin": true})
				run = {"picks": [{"sc": "glass2", "q": "Открыть?", "a": "Не открывать"}], "sec": 3725.0, "rec": 21, "ok": 17, "miss": 2, "hrs": ["S1", "S2", "S3", "S4", "S6"]}
			_run_then(th)
		else:
			start_scene(_arg("scene", ""), int(_arg("beat", "0")))
	elif autoplay:
		start_scene(StoryDB.story.get("start", "intro"))
	else:
		if _arg("shot", "") != "":   # проверка титула: -- --at 3 --shot файл
			test = {"at": float(_arg("at", "3.0")), "shot": _arg("shot", "")}
		go_menu()


func _arg(name: String, def: String) -> String:
	var a := OS.get_cmdline_user_args()
	var i := a.find("--" + name)
	return a[i + 1] if i >= 0 and i + 1 < a.size() else def


# ---------- сцены и кадры ----------
func start_scene(id: String, at_beat := 0) -> void:
	mode = "play"
	scene_id = id
	scene = StoryDB.scene(id)
	if scene.is_empty():
		push_error("нет сцены " + id)
		return
	if autoplay:
		print("сцена ", id)
	if id == "intro":   # новая ночь
		flags.clear()
		last_time = "02:55"
		run = {"picks": [], "sec": 0.0, "rec": 0, "ok": 0, "miss": 0, "hrs": []}
		ShiftGame.shk = {}
	if not id.begins_with("over_"):
		save_scene = id
		if not autoplay and test.is_empty():   # «Продолжить» — с этой сцены
			Save.d.save = id
			Save.d.saveT = last_time
			Save.d.flags = flags
			Save.d.shk = ShiftGame.shk
			Save.d.tutDone = ShiftGame.tut_done
			Save.d.run = run
			Save.write()
	if run.is_empty():
		run = {"picks": [], "sec": 0.0, "rec": 0, "ok": 0, "miss": 0, "hrs": []}
	ends.hide_all()
	if scene.has("time"):
		last_time = String(scene.time)
	clock_t0 = T
	_music(String(scene.get("music", "")))
	bi = at_beat - 1
	beat = {}
	shot_off = 0.0
	choice_box.visible = false
	next_beat()


func next_beat() -> void:
	var prev := beat
	var prev_t := bt
	bi += 1
	var beats: Array = scene.get("beats", [])
	while bi < beats.size() and not _flag_ok(beats[bi].get("if")):
		bi += 1
	for v in voices:
		v.stop()
	if bi >= beats.size():
		_run_then(scene.get("then", {}))
		return
	beat = beats[bi]
	_set_flags(beat.get("set"))
	if beat.has("time"):
		last_time = String(beat.time)
		sec_base = 0
		clock_t0 = T
	player.norms = _props_now()
	var same: bool = not prev.is_empty() and prev.get("img") != null and prev.get("img") == beat.get("img")
	shot_off = shot_off + prev_t if same else 0.0
	bt = 0.0
	wait = 0.0
	holding = false
	hint_lbl.visible = false
	hint_lbl.text = "▼"
	# реплики подряд: первая с sayAt (0,15 с), каждая следующая через 0,3 с после конца предыдущей
	sched.clear()
	var t := float(beat.get("sayAt", 0.15))
	for k in beat.get("say", []):
		sched.append({"t": t, "k": String(k), "d": StoryDB.dur(k), "started": false})
		t += StoryDB.dur(k) + 0.3
	typed = {}
	if String(beat.get("text", "")) != "":
		var t0 := t if beat.has("say") else 0.2
		typed = {"t0": t0, "d": StoryDB.type_dur(String(beat.text)), "s": String(beat.text)}
		t = maxf(t, t0 + typed.d)
	bdur = maxf(t + float(beat.get("pad", 0.3)), 1.2)
	_beat_audio()
	death = {}
	if scene_id.begins_with("over_") and beat.get("black") != null:
		_death_setup()
	var img = beat.get("img")
	phone.visible = false
	if beat.has("phone"):   # экран телефона на весь кадр
		phone.show_phone(beat.phone)
		player.visible = false
		black.visible = false
	elif img != null and ResourceLoader.exists("res://bake/%s.json" % img):
		if player.shot == null or player.shot.id != img:
			player.play(img)
		player.visible = true
		black.visible = false
	else:
		black.visible = true   # чёрная склейка
	_show_line()


func _process(delta: float) -> void:
	if scene.is_empty():
		return
	if not test.is_empty():
		delta = 1.0 / 30.0
		if mode == "end" and ends._t >= float(test.at):
			_snap()
	T += delta
	if mode in ["play", "choice", "mg", "shift", "explore", "talk"] and not run.is_empty():
		run.sec += delta
	if mode == "menu":
		_menu_frame()
		if not test.is_empty() and T - _menu_t0 >= float(test.at):
			_snap()
		return
	if mode == "mg":
		_mg_frame(delta)
		if not test.is_empty() and mg.t >= float(test.at):
			_snap()
		return
	if mode == "shift":
		_shift_frame(delta)
		if not test.is_empty() and shift and shift.T >= float(test.at):
			_snap()
		return
	if mode == "explore" or mode == "talk":
		post.beat_fx("", act.get("t", 0.0), T, 0.0, {}, 1.0)
		post.set_p("cctv", 0.0)
		post.set_p("ir", 0.0)
		hud.visible = false
		_act_frame(delta)
		if autoplay and act.t > 1.0:   # автопрогон: осматриваем по порядку, потом уходим
			var it := _act_items(act.th)
			var lv = act.th.get("leave", act.th.get("bye"))
			if not it.is_empty():
				_act_pick(String(it[0].s.go), it[0].s.get("set"), String(it[0].s.go))
			elif lv != null:
				_act_pick(String(lv.go), lv.get("set"), "")
			return
		if not test.is_empty() and act.t >= float(test.at):
			_snap()
		return
	if choice_t >= 0.0:
		choice_t -= delta
		timer_bar.value = maxf(choice_t, 0.0)
		if choice_t <= 0.0:
			_pick(choice_def)
	if beat.is_empty():
		return
	bt += delta
	if holding:
		wait += delta
		if wait > 2.2 and hint_lbl.text == "▼":   # голос кончился, а игрок ждёт — подсказать, как дальше
			hint_lbl.text = StoryDB.t("Пробел или клик — дальше")
	# голос и звуки по расписанию
	for s in sched:
		if not s.started and bt >= s.t:
			s.started = true
			_say(s.k)
	for s in sfx_q:
		if not s.done and bt >= s.at:
			s.done = true
			_play(s.n, s.op)
	if phone.visible:
		phone.tick(bt)
	_show_line()
	_talk(delta)
	# смерть: после обрыва — бросок в объектив и финал (своя запись, свой звук)
	if not death.is_empty() and bt >= float(beat.black):
		dlg.visible = false
		hud.visible = false
		_death_frame()
		if bt >= bdur and not holding:
			next_beat()
		elif not test.is_empty() and bt >= float(test.at):
			_snap()
		return
	# постановка и камера
	var fx := String(beat.get("fx", ""))
	var cctv := fx == "cctv" or fx == "static" or (fx == "glitch" and beat.has("label"))
	if player.shot and player.visible:
		var st := minf(shot_off + bt, player.shot.dur)
		if cctv:   # запись камеры наблюдения — 12 кадров в секунду
			st = floor(st * 12.0) / 12.0
		_plan()
		player.apply(st, wait)
	post.set_p("cctv", 1.0 if cctv else 0.0)
	post.set_p("ir", 1.0 if (cctv and player.ir) else 0.0)
	post.beat_fx(fx, bt, T, clampf(bt / bdur, 0.0, 1.0), beat, bdur)
	hud.visible = cctv and not phone.visible
	if hud.visible:
		var hd: Dictionary = beat.get("hud", {}) if beat.get("hud") is Dictionary else {}
		hud.T = T
		hud.label = String(beat.get("label", ""))
		hud.ir = player.ir
		hud.tag = String(hd.get("tag", "REC"))
		hud.date = String(hd.get("date", _hud_date()))
		hud.clock = (String(hd.at) + ":%02d" % (int(bt) % 60)) if hd.has("at") else game_clock()
		hud.mic = -1.0
	if bt >= bdur and not holding:
		if auto or beat.get("auto", false) or beat.get("say", []).is_empty() and String(beat.get("text", "")) == "":
			next_beat()
			return
		holding = true
		hint_lbl.visible = true
	if not test.is_empty() and bt >= float(test.at):
		_snap()


## План кадра: cams [[cx, cy, z0, z1], …] делят кадр поровну; зум внутри плана плавный.
func _plan() -> void:
	var cams: Array = beat.get("cams", [[0.5, 0.5, 1.0, 1.0]])
	if cams.is_empty():
		cams = [[0.5, 0.5, 1.0, 1.0]]
	var n := cams.size()
	var seg := bdur / n
	var i := mini(n - 1, int(bt / seg))
	var c: Array = cams[i]
	var u := clampf((bt - i * seg) / seg, 0.0, 1.0)
	u = u * u * (3.0 - 2.0 * u)
	if c[0] is Array:   # точка мира вместо точки кадра — пока центр (TODO: проекция точки)
		player.aim_cx = 0.5
		player.aim_cy = 0.5
	else:
		player.aim_cx = float(c[0])
		player.aim_cy = float(c[1])
	player.aim_z = lerpf(float(c[2]), float(c[3]), u)
	player.aim_dk = 0.0 if String(beat.get("fx", "")) == "cctv" else 0.5


func _input(e: InputEvent) -> void:
	if e.is_action_pressed("ui_cancel") and mode in ["play", "choice", "mg", "shift", "explore", "talk"] and not get_tree().paused:
		get_tree().paused = true
		menu.show_pause()
		get_viewport().set_input_as_handled()
		return
	if mode != "play" and mode != "choice":
		return
	if e.is_action_pressed("ui_accept") or (e is InputEventMouseButton and e.pressed and e.button_index == MOUSE_BUTTON_LEFT and not choice_box.visible):
		if not beat.is_empty() and not choice_box.visible:
			next_beat()
	if e is InputEventKey and e.pressed and choice_box.visible:
		var k := (e as InputEventKey).keycode - KEY_1
		if k >= 0 and k < choice_opts.size():
			_pick(k)


# ---------- что после сцены ----------
func _run_then(th: Dictionary) -> void:
	beat = {}
	if th.has("goto"):
		start_scene(th.goto)
	elif th.has("choice"):
		_choice(th)
	elif th.has("explore"):
		_show_act(th.explore, "explore")
	elif th.has("talk"):
		_show_act(th.talk, "talk")
	elif th.has("back"):
		_back_to(String(th.back))
	elif th.has("shift"):
		_start_shift(th)
	elif th.has("mg"):
		_start_mg(th)
	elif th.has("ending"):
		if th.get("fin", false):
			_end_screen(th)
		else:
			_finale(th)
	elif th.has("over"):
		_over_screen(th)
	else:
		var kind := "конец ветки"
		for k in ["explore", "talk", "mg", "shift", "ending", "over", "back"]:
			if th.has(k):
				kind = k
		_say_text("", "[%s — переносится]" % kind)
		hint_lbl.visible = false
		if autoplay:
			print("стоп: ", kind, " ", JSON.stringify(th).left(120))
			get_tree().quit()


# ---------- обход и разговор ----------
## Точки обхода и темы разговора, которые ещё можно выбрать (осмотренное помнится флагом v:сцена).
func _act_items(th: Dictionary) -> Array:
	var out := []
	var list: Array = th.get("spots", th.get("topics", []))
	for i in list.size():
		var s: Dictionary = list[i]
		if _flag_ok(s.get("if")) and not (s.get("once", true) != false and flags.has("v:" + String(s.go))):
			out.append({"s": s, "i": i})
	return out


## Часы ночи с секундами (gameClock в engine.js).
func game_clock(with_sec := true) -> String:
	var p := last_time.split(":")
	var s := int(p[0]) * 3600 + int(p[1]) * 60 + sec_base + int(floor(maxf(0.0, T - clock_t0)))
	return "%02d:%02d" % [(s / 3600) % 24, (s / 60) % 60] + (":%02d" % (s % 60) if with_sec else "")


## Дата ночи на записях: с 28 на 29 октября (после полуночи — 29-е).
func _hud_date() -> String:
	return "29.10.2026" if int(last_time.substr(0, 2)) < 12 else "28.10.2026"


## Тихие перемены в холле по ходу ночи (propsNow в engine.js).
func _props_now() -> Dictionary:
	var tm := last_time if _to_min(last_time) < _to_min("03:00") else "03:00"   # до трёх часы идут, в три встают
	var P := {"clock": tm, "jacket": 0 if flags.has("opened") else 1, "badge": "scratched" if flags.has("mih") else 1,
		"book": 0 if flags.has("jread") else 1, "fphone": 1 if flags.has("fphone") else 0}
	P.merge(scene.get("props", {}), true)
	P.merge(beat.get("props", {}), true)
	return P


## Минуты ночи: часы до полудня — уже следующие сутки (ночь с 21:00).
static func _to_min(s: String) -> int:
	var p := s.split(":")
	var h := int(p[0])
	return ((h + 24) if h < 12 else h) * 60 + (int(p[1]) if p.size() > 1 else 0)


func _show_act(th: Dictionary, kind: String) -> void:
	var items := _act_items(th)
	var lv = th.get("leave", th.get("bye"))
	var leave_ok := true
	if lv != null:
		for k in lv.get("need", []):
			if not flags.has("v:" + String(k)):
				leave_ok = false
	if kind == "explore" and items.is_empty() and lv != null:   # всё осмотрено — дальше сам
		_act_pick(String(lv.go), lv.get("set"), "")
		return
	mode = kind
	act = {"th": th, "kind": kind, "t": 0.0}
	dlg.visible = false
	hint_lbl.visible = false
	phone.visible = false
	black.visible = false
	var img := String(th.get("img", ""))
	if img != "" and ResourceLoader.exists("res://bake/%s.json" % img):
		if player.shot == null or player.shot.id != img:
			player.play(img)
		player.visible = true
	player.norms = _props_now()
	act_view.open(th, kind, items, leave_ok, dlg.theme)


func _act_frame(delta: float) -> void:
	act.t += delta
	var th: Dictionary = act.th
	var cams: Array = th.get("cams", [[0.5, 0.5, 1.0, 1.03]])
	var c: Array = cams[0]
	var k := 1.0 - pow(1.0 - minf(1.0, act.t / 8.0), 2.0)
	var side := 0.14 if act.kind == "talk" else 0.0   # разговор: собеседник справа, темы слева
	player.aim_cx = float(c[0]) - side
	player.aim_cy = float(c[1])
	player.aim_z = lerpf(float(c[2]), float(c[3]), k)
	player.aim_dk = 0.5
	player.talk = {}
	if player.shot and player.visible:
		var st := float(act.t) + float(th.get("t", 0.0))
		player.apply(minf(st, player.shot.dur), maxf(0.0, st - player.shot.dur))
	act_view.layout(player.camera)


func _act_pick(go: String, set_flags, visit: String) -> void:
	if visit != "":
		flags["v:" + visit] = true
	_set_flags(set_flags)
	act_view.close()
	act = {}
	start_scene(go)


## Сцена точки кончилась — назад к обходу или разговору этой сцены.
func _back_to(id: String) -> void:
	scene_id = id
	scene = StoryDB.scene(id)
	if scene.is_empty():
		return
	_run_then(scene.get("then", {}))


# ---------- меню, пауза, продолжение ----------
var menu: MainMenu
var _menu_t0 := 0.0


## Титул: живой кадр (Лили за стеклом), медленный наезд за 20 с.
func go_menu() -> void:
	get_tree().paused = false
	mode = "menu"
	scene = {"beats": []}
	beat = {}
	for v in voices:
		v.stop()
	for v in sfx_pool:
		v.stop()
	for k in ambs:
		ambs[k].stop()
	amb_static.stop()
	if shift:
		shift.queue_free()
		shift = null
	shift_ui.visible = false
	mg.visible = false
	act_view.close()
	ends.hide_all()
	dlg.visible = false
	hint_lbl.visible = false
	phone.visible = false
	choice_box.visible = false
	hud.visible = false
	black.visible = false
	_menu_t0 = T
	if ResourceLoader.exists("res://bake/door_katya.json"):
		player.play("door_katya")
		player.visible = true
	_music("theme")
	menu.show_title(Save.get_v("save") != null and StoryDB.scene(String(Save.get_v("save", ""))).size() > 0, OS.has_feature("pc"))


func _menu_frame() -> void:
	var tt := T - _menu_t0
	var k := minf(1.0, tt / 20.0)
	player.aim_cx = 0.5
	player.aim_cy = 0.47
	player.aim_z = 1.0 + 0.06 * (k * k * (3.0 - 2.0 * k))
	player.aim_dk = 0.0
	player.talk = {}
	if player.shot:
		player.apply(minf(tt + 3.0, player.shot.dur), maxf(0.0, tt + 3.0 - player.shot.dur))
	post.beat_fx("", tt, T, 0.0, {}, 1.0)
	post.set_p("cctv", 0.0)
	post.set_p("ir", 0.0)
	post.set_p("grain_k", 0.09)


func _continue() -> void:
	menu.close()
	flags = Save.get_v("flags", {}).duplicate()
	ShiftGame.shk = Save.get_v("shk", {}).duplicate(true)
	ShiftGame.tut_done = bool(Save.get_v("tutDone", false))
	run = Save.get_v("run", {}).duplicate(true)
	last_time = String(Save.get_v("saveT", "02:55"))
	start_scene(String(Save.get_v("save", "intro")))


# ---------- концовки и экран смерти ----------
## Концовка: последний кадр идёт дальше ~8,5 с и темнеет, своя музыка, название по буквам; клик — журнал дежурств.
func _finale(th: Dictionary) -> void:
	var last := {}
	for b in scene.get("beats", []):
		if b.get("img") != null:
			last = b
	var img = th.get("bg", last.get("img"))
	if img == null:
		_end_screen(th.merged({"fin": true}))
		return
	var cams: Array = last.get("cams", [[0.5, 0.5, 1.0, 1.1]])
	var c: Array = th.get("bgCam", [0.5, 0.5, 1.0, 1.08]) if th.has("bg") else cams[cams.size() - 1]
	var z: float = float(c[2]) if th.has("bg") else float(c[3])
	var fin := {"img": img, "fx": th.get("bgFx", "") if th.has("bg") else last.get("fx", ""), "cams": [[c[0], c[1], z, float(c[3]) if th.has("bg") else z * 1.08]],
		"auto": true, "fin": true, "pad": 8.5}
	if not th.has("bg"):
		if last.has("label"):
			fin.label = last.label
		if last.has("hud"):
			fin.hud = last.hud
	scene = scene.duplicate()
	scene.beats = scene.get("beats", []) + [fin]
	scene.then = th.merged({"fin": true})
	_music("end_" + String(th.ending) if ResourceLoader.exists("res://assets/snd/end_%s.mp3" % th.ending) else "drone")
	_play("boom", {"gain": 0.45})
	bi = scene.beats.size() - 2
	beat = {}
	next_beat()
	ends.show_fin(String(th.get("title", "")), String(th.get("sub", "")))


func _end_screen(th: Dictionary) -> void:
	mode = "end"
	beat = {}
	hud.visible = false
	dlg.visible = false
	endings_got = Save.get_v("endings", {})
	var fresh := not endings_got.has(th.ending)
	endings_got[th.ending] = true
	Save.d.endings = endings_got
	Save.d.erase("save")
	Save.write()
	if not th.get("fin", false):
		_play("boom")
	var mins := maxi(1, int(round(float(run.get("sec", 0.0)) / 60.0)))
	var dur := (StoryDB.t("{h} ч {m} мин").replace("{h}", str(mins / 60)).replace("{m}", str(mins % 60))) if mins >= 60 else StoryDB.t("{m} мин").replace("{m}", str(mins))
	var rows := [["Время в игре", dur], ["Часов смены отстоял", run.get("hrs", []).size()], ["Странностей замечено", run.get("ok", 0)],
		["Записей в журнале", run.get("rec", 0)], ["Пропущено", run.get("miss", 0)]]
	var picks := []
	for p in run.get("picks", []):
		picks.append(StoryDB.t(String(p.a)))
	var fork := {}
	for p in run.get("picks", []):
		fork = p
	var gal := []
	for k in StoryDB.story.endings:
		gal.append([StoryDB.t(String(StoryDB.story.endings[k])), endings_got.has(k)])
	ends.show_journal(rows, picks, game_clock(false) + " — " + String(th.get("title", "")), String(th.get("sub", "")),
		StoryDB.t(String(StoryDB.story.endings.get(th.ending, th.ending))), fresh, gal, StoryDB.t(String(fork.get("q", ""))) if not fork.is_empty() else "")
	if autoplay:
		print("концовка ", th.ending)
		get_tree().quit()


func _over_screen(th: Dictionary) -> void:
	mode = "end"
	beat = {}
	hud.visible = false
	dlg.visible = false
	overs_got = Save.get_v("overs", {})
	overs_got[String(th.over)] = true
	Save.set_v("overs", overs_got)
	_music("")
	ends.show_over(String(th.over), String(th.get("sub", "")), overs_got.size(), StoryDB.story.overs.size())
	ends.set_meta("retry", String(th.get("retry", "@save")))
	if autoplay:
		print("смерть ", th.over)
		get_tree().quit()


## «Попробовать снова» — с места решения (выбор, мини-игра, обход), а не с начала сцены.
func _retry() -> void:
	var r := String(ends.get_meta("retry", "@save"))
	if r == "@save":
		r = save_scene if save_scene != "" else String(StoryDB.story.get("start", "intro"))
	var sc := StoryDB.scene(r)
	if sc.is_empty():
		r = String(StoryDB.story.get("start", "intro"))
		sc = StoryDB.scene(r)
	var t2: Dictionary = sc.get("then", {})
	var dec: bool = (t2.has("choice") or t2.has("mg") or t2.has("explore") or t2.has("talk")) and sc.get("beats", []).size() > 1
	start_scene(r, sc.beats.size() - 1 if dec else 0)


# ---------- смерти ----------
## Смерть (over_*): в миг обрыва — бросок в объектив, крик в три слоя и удар по низам; потом финал «как оно убивает» по сценарию
## (свои звуки на кадрах касаний, вспышка, брызги на объектив), в конце — в черноту. Как beatAudio / frame в engine.js.
func _death_setup() -> void:
	if gore_idx.is_empty() and FileAccess.file_exists("res://bake/gore/index.json"):
		gore_idx = JSON.parse_string(FileAccess.get_file_as_string("res://bake/gore/index.json"))
	var g: Dictionary = gore_idx.get("overs", {}).get(scene_id, {})
	var tl = g.get("tl")
	var gd: float = float(tl.d) if tl is Dictionary else GORE_D
	death = {"g": g, "gd": gd if not g.is_empty() else 0.0}
	var bl := float(beat.black)
	bdur = maxf(bdur, bl + JUMP_D + float(death.gd) + 0.3)
	for e in [["jumpscare", 0.0, {"gain": 1.0}], ["jumpscare", 0.01, {"gain": 0.55, "rate": 0.55}], ["jumpscare", 0.03, {"gain": 0.3, "rate": 1.45}],
			["boom", 0.0, {"gain": 0.9}], ["static", 0.0, {"gain": 0.45, "cut": 0.5}]]:
		sfx_q.append({"at": bl + e[1], "n": e[0], "op": e[2], "done": false})
	if g.is_empty():
		return
	var g0 := bl + JUMP_D
	var gk := String(g.kind).trim_suffix("L").trim_suffix("D").trim_suffix("F")
	var S := []
	if tl is Dictionary:
		for x in tl.get("sfx", []):
			S.append([x[0], x[1], x[2] if x.size() > 2 else 1.0, x[3] if x.size() > 3 and x[3] is Dictionary else {}])
	elif gk in ["eat", "limb", "heart"]:
		S = [["breath_close", 0.15, 0.7], ["skin_stretch", 0.5, 1.0], ["eat_wet", 0.6, 1.0], ["skin_stretch", 1.15, 0.9, {"rate": 0.85}], ["eat_wet", 1.22, 1.0, {"rate": 0.9}], ["eat_wet", 2.3, 0.9], ["eat_wet", 2.85, 0.8, {"rate": 1.1}]]
	elif gk == "drag":
		S = [["skin_stretch", 0.1, 0.8], ["breath_close", 0.3, 0.7], ["eat_wet", 0.9, 0.5, {"rate": 0.7}], ["skin_stretch", 1.8, 0.7, {"rate": 0.8}]]
	elif gk == "chest":
		S = [["breath_close", 0.1, 0.7], ["boom", 0.5, 0.7], ["skin_stretch", 0.5, 1.0], ["eat_wet", 0.6, 1.0], ["eat_wet", 1.25, 1.0, {"rate": 0.85}], ["skin_stretch", 1.3, 0.8], ["eat_wet", 2.2, 0.7]]
	elif gk == "neck" or gk == "hang":
		S = [["breath_close", 0.2, 0.8], ["skin_stretch", 0.55, 0.7], ["neck_crack", 1.25, 1.0], ["neck_crack", 1.32, 0.75, {"rate": 0.8}], ["skin_stretch", 1.3, 0.8]]
	else:
		S = [["breath_close", 0.15, 0.7], ["skin_stretch", 0.5, 1.0], ["eat_wet", 0.55, 1.0], ["eat_wet", 1.1, 0.9, {"rate": 0.9}], ["breath_close", 1.6, 0.6, {"rate": 0.7}], ["eat_wet", 2.0, 0.8]]
	for x in S:
		var op: Dictionary = (x[3] as Dictionary).duplicate() if x.size() > 3 else {}
		op.gain = x[2]
		sfx_q.append({"at": g0 + float(x[1]), "n": String(x[0]), "op": op, "done": false})


func _death_frame() -> void:
	var js: float = bt - float(beat.black)
	var g: Dictionary = death.g
	var gd: float = death.gd
	post.beat_fx("", bt, T, 0.0, {}, bdur)
	player.visible = true
	player.talk = {}
	player.aim_cx = 0.5
	player.aim_cy = 0.5
	player.aim_dk = 0.0
	if js < JUMP_D:   # бросок в объектив: пасть, зум, тряска, красное
		if player.shot == null or player.shot.id != "death_scream":
			player.play("death_scream")
		player.aim_z = 1.05 + 0.65 * minf(1.0, js / 0.4)
		player.apply(2.2 + js)
		post.set_p("scare", 1.0 - js / JUMP_D)
		post.set_p("tint", Vector3(1.0, (60.0 + 150.0 * js / JUMP_D) / 255.0, 40.0 / 255.0))
		post.set_p("invert", 1.0 if js < 0.05 else 0.0)
		post.set_p("grain_k", 0.3)
	elif not g.is_empty() and js < JUMP_D + gd:   # как оно убивает
		var gt := js - JUMP_D
		var tl = g.get("tl")
		var files: Array = g.get("files", [])
		var f := String(files[0])
		if not (tl is Dictionary) and not String(g.kind).begins_with("drag") and gt > 0.55 and gt < 2.15 and files.size() > 1:
			f = String(files[1])   # общий план → крупно рана → общий
		if player.shot == null or player.shot.id != f:
			player.play(f)
		player.aim_z = 1.0 if tl is Dictionary else 1.0 + 0.04 * gt
		player.apply(gt)
		var hit: bool = (gt >= float(tl.hit) and gt < float(tl.hit) + 0.09) if tl is Dictionary else gt < 0.12
		if hit:
			post.set_p("tint", Vector3(1.0, 0.19, 0.19))   # вспышка — на касании
		var sp = tl.get("splat") if tl is Dictionary else (0.55 if RegEx.create_from_string("^(face|throat|eyes|chest|hang)").search(String(g.kind)) else null)
		if sp != null and gt > float(sp):
			post.set_p("splat_k", minf(1.0, (gt - float(sp)) * 3.0))
			post.set_p("splat_seed", float(scene_id.length() * 7 + String(g.kind).length()))
		post.set_p("grain_k", 0.25)
		if gt > gd - 0.35:
			post.set_p("black_k", (gt - gd + 0.35) / 0.35)
	else:
		post.set_p("black_k", 1.0)


# ---------- мини-игры ----------
func _start_mg(th: Dictionary) -> void:
	mode = "mg"
	beat = {}
	dlg.visible = false
	hint_lbl.visible = false
	phone.visible = false
	choice_box.visible = false
	if autoplay:
		print("мини-игра ", th.mg)
	if not mg.finished.is_connected(_mg_done):
		mg.finished.connect(_mg_done)
	mg.start(th, self)


func _mg_done(next: String) -> void:
	player.torch = null
	post.set_p("tear", 0.0)
	post.set_p("run_side", 0.0)
	hud.visible = false
	start_scene(next)


## Кадр под мини-игрой: запись, план с зумом (k²), время (перемотка / с начала), фонарь, тёмная сторона, надписи камеры.
func _mg_frame(_delta: float) -> void:
	var v: Dictionary = mg.view
	if v.is_empty() or not v.has("img"):   # «держать»: прошлый кадр, затемнённый
		post.beat_fx("", mg.t, T, 0.0, {}, 1.0)
		post.set_p("black_k", 0.45)
		hud.visible = false
		return
	var img := String(v.img)
	if ResourceLoader.exists("res://bake/%s.json" % img) and (player.shot == null or player.shot.id != img):
		player.play(img)
	player.visible = true
	black.visible = false
	var k := minf(1.0, mg.t / float(v.get("dur", 3.0)))
	var c: Array = v.c
	player.aim_cx = float(c[0])
	player.aim_cy = float(c[1])
	player.aim_z = float(c[2]) + (float(c[3]) - float(c[2])) * k * k
	player.aim_dk = 0.0
	player.talk = {}
	player.torch = v.get("torch")
	var st: float = float(v.tAt) if v.has("tAt") else (float(v.st) + mg.t if v.has("st") else mg.t)
	if player.shot:
		player.apply(minf(maxf(st, 0.0), player.shot.dur), maxf(0.0, st - player.shot.dur))
	var cctv := v.has("label")
	post.set_p("cctv", 1.0 if cctv else 0.0)
	post.set_p("ir", 1.0 if (cctv and player.ir) else 0.0)
	post.beat_fx("cctv" if cctv else "", mg.t, T, 0.0, {}, 1.0)
	post.set_p("grain_k", (0.2 if v.has("tAt") else 0.25 + k * 0.5) if cctv and mg.t > 0.3 else 0.07)
	post.set_p("tear", float(v.get("rw", 0.0)))
	post.set_p("run_side", (-1.0 if v.run == "L" else 1.0) if v.has("run") else 0.0)
	hud.visible = cctv
	if cctv:
		hud.T = T
		hud.label = String(v.label)
		hud.ir = player.ir
		hud.tag = String(v.get("rw_tag", "REC"))
		hud.date = _hud_date()
		hud.clock = String(v.get("rw_clock", game_clock()))
		hud.mic = -1.0


## Фоновая петля звука (сердце, помехи): включить с громкостью или выключить.
func _amb(n: String, on: bool, gain := 1.0) -> void:
	if not ambs.has(n):
		var a := AudioStreamPlayer.new()
		a.bus = "Amb"
		add_child(a)
		ambs[n] = a
	var p: AudioStreamPlayer = ambs[n]
	if on:
		if not p.playing and ResourceLoader.exists("res://assets/snd/%s.mp3" % n):
			p.stream = _loop_stream(n)
			p.play()
		p.volume_db = linear_to_db(gain)
	else:
		p.stop()


# ---------- смена ----------
func _start_shift(th: Dictionary) -> void:
	mode = "shift"
	beat = {}
	dlg.visible = false
	hint_lbl.visible = false
	phone.visible = false
	black.visible = false
	choice_box.visible = false
	if shift:
		shift.queue_free()
	shift = ShiftGame.new()
	add_child(shift)
	shift.ended.connect(func(n): _shift_done(n))
	shift.died.connect(func(n): _shift_done(n))
	shift.start(th, self, player, shift_ui)


func _shift_done(next: String) -> void:
	if shift:
		shift.queue_free()
		shift = null
	hud.visible = false
	if autoplay:
		print("смена → ", next)
	start_scene(next)


## Кадр смены: камера по слоям (12 кадров в секунду, как запись камеры), надписи камеры, микрофон, субтитры реплик.
func _shift_frame(_delta: float) -> void:
	if shift == null or shift.shf.is_empty():
		return
	var c := String(shift.shf.cam)
	var ids := shift.view_ids(c)
	if ids.is_empty():
		return
	player.visible = true
	player.play_layers(ids)
	var P := _props_now()
	if c == "03":   # часы холла идут по времени смены (или крутятся назад)
		var v: float = shift.shf.M - (shift.T * 40.0 if shift.props().get("clockSpin") else 0.0)
		P.clock = ShiftGame.min_str(fposmod(v, 1440.0))
	else:
		P.clock = null
	player.norms = P
	player.aim_cx = 0.5
	player.aim_cy = 0.5
	player.aim_z = 1.0
	player.aim_dk = 0.0
	player.talk = {}
	player.apply(floor(shift.T * 12.0) / 12.0, 0.0, true)
	post.set_p("cctv", 1.0)
	post.set_p("ir", 1.0 if player.ir else 0.0)
	post.beat_fx("cctv", shift.T, T, 0.0, {}, 1.0)
	hud.visible = true
	hud.T = T
	hud.label = ShiftUI.cam_label(c)
	hud.ir = player.ir
	hud.tag = "REC"
	hud.date = _hud_date()
	hud.clock = game_clock()
	hud.mic = float(shift.shf.mic)
	# реплики смены
	var cur := shift.cur_line()
	if cur.is_empty():
		dlg.visible = false
		return
	var k := String(cur.k)
	var lt: float = shift.T - float(cur.t0)
	if StoryDB.line(k).is_empty():   # без озвучки — печатается
		var tx := StoryDB.t(String(StoryDB.story.shift.get("lines", {}).get(k, "")))
		_say_text(String(StoryDB.story.shift.get("who", {}).get(k, "ИТАН")), tx.substr(0, int(minf(1.0, lt / maxf(0.5, float(cur.d) - 0.9)) * tx.length())))
		return
	var w: Array = StoryDB.line(k).get("w", [])
	var on := 0
	for x in w:
		if lt >= float(x[0]) - 0.02:
			on += 1
	_say_words(StoryDB.who_of(k), StoryDB.chunks(k), on if lt < float(cur.d) + 0.1 else w.size())


func _choice(th: Dictionary) -> void:
	for c in choice_box.get_children():
		if c != timer_bar:
			c.queue_free()
	var head := Label.new()
	head.text = String(th.choice)
	head.add_theme_font_size_override("font_size", 34)
	head.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	choice_box.add_child(head)
	choice_opts.clear()
	for o in th.get("opts", []):
		if not _flag_ok(o.get("if")):
			continue
		choice_opts.append(o)
		var b := Button.new()
		b.text = "%d. %s" % [choice_opts.size(), o.t]
		b.add_theme_font_size_override("font_size", 32)
		b.alignment = HORIZONTAL_ALIGNMENT_LEFT
		var idx := choice_opts.size() - 1
		b.pressed.connect(func(): _pick(idx))
		choice_box.add_child(b)
	choice_box.move_child(timer_bar, -1)
	choice_def = clampi(int(th.get("def", 0)), 0, maxi(0, choice_opts.size() - 1))
	timer_bar.max_value = float(th.get("timeout", 10))
	choice_t = timer_bar.max_value
	choice_box.visible = true
	dlg.visible = false


func _pick(i: int) -> void:
	if choice_opts.is_empty():
		return
	var o: Dictionary = choice_opts[clampi(i, 0, choice_opts.size() - 1)]
	choice_t = -1.0
	choice_box.visible = false
	_set_flags(o.get("set"))
	if not String(o.go).begins_with("over_") and not run.is_empty():   # решения ночи — в журнал дежурств
		run.picks.append({"sc": scene_id, "q": String(scene.get("then", {}).get("choice", "")), "a": String(o.t)})
	start_scene(String(o.go))


# ---------- флаги ----------
func _flag_ok(c) -> bool:
	if c == null:
		return true
	if c is Array:
		for x in c:
			if not _flag_ok(x):
				return false
		return true
	var s := String(c)
	if s.begins_with("!"):
		return not flags.has(s.substr(1))
	return flags.has(s)


func _set_flags(s) -> void:
	if s == null:
		return
	if s is Array:
		for x in s:
			flags[String(x)] = true
	elif s is Dictionary:
		for k in s:
			flags[k] = s[k]
	else:
		flags[String(s)] = true


# ---------- звук кадра ----------
## Звуки кадра: [звук, секунда, {gain, cut, off, rate}] (как beatAudio в engine.js); «static» без once/cut — помехи фоном на весь кадр.
func _beat_audio() -> void:
	sfx_q.clear()
	var st := false
	for e in beat.get("sfx", []):
		var op: Dictionary = e[2] if e.size() > 2 and e[2] is Dictionary else {}
		if String(e[0]) == "static" and not (op.has("once") or op.has("cut")):
			st = true
			continue
		sfx_q.append({"at": float(e[1]) if e.size() > 1 else 0.0, "n": String(e[0]), "op": op, "done": false})
	st = st or String(beat.get("fx", "")) == "static"
	if st and not amb_static.playing:
		amb_static.stream = _loop_stream("static")
		amb_static.volume_db = linear_to_db(0.25)
		amb_static.play()
	elif not st:
		amb_static.stop()


func _play(n: String, op := {}) -> void:
	var p := "res://assets/snd/%s.mp3" % n
	if not ResourceLoader.exists(p):
		return
	for v in sfx_pool:
		if not v.playing:
			v.stream = load(p)
			v.volume_db = linear_to_db(float(op.get("gain", 1.0)))
			v.pitch_scale = float(op.get("rate", 1.0))
			v.play(float(op.get("off", 0.0)))
			if op.has("cut"):
				get_tree().create_timer(float(op.cut)).timeout.connect(v.stop)
			return


func _loop_stream(n: String) -> AudioStreamMP3:
	var st: AudioStreamMP3 = load("res://assets/snd/%s.mp3" % n).duplicate()
	st.loop = true
	return st


# ---------- голос и субтитры ----------
func _say(k: String) -> void:
	if not Save.setting("voice", true):   # без озвучки остаются субтитры
		return
	var p := StoryDB.voice_path(k)
	if not ResourceLoader.exists(p):
		return
	for v in voices:
		if not v.playing:
			v.stream = load(p)
			v.volume_db = linear_to_db(float(StoryDB.line(k).get("g", 1.0)))   # реплики выровнены по громкости (voice.py loud)
			v.play()
			return


## Кто сейчас говорит вслух и насколько громко — от этого двигается рот героя в кадре (engine.js frame: talk).
func _talk(delta: float) -> void:
	var cur := _cur_line()
	talk_v += ((StoryDB.loud(cur.k, bt - cur.t) if not cur.is_empty() else 0.0) - talk_v) * (1.0 - pow(0.5, delta * 60.0))
	var tk := {}
	if not cur.is_empty() and bt < cur.t + cur.d:
		if StoryDB.aloud_of(cur.k):
			tk = {"who": StoryDB.who_of(cur.k), "v": talk_v, "vis": LipSync.viseme_at(cur.k, bt - cur.t)}
	elif not typed.is_empty() and bt >= typed.t0 and bt < typed.t0 + typed.d - 0.9:
		var w := String(typed.get("who", beat.get("who", "ИТАН")))
		if w != "ИТАН" or String(typed.s).strip_edges().begins_with("—"):
			tk = {"who": w, "v": 0.25 + 0.55 * absf(sin(bt * 11.0))}
	player.talk = tk


func _cur_line() -> Dictionary:
	var cur: Dictionary = {}
	for s in sched:
		if bt >= s.t:
			cur = s
	return cur


func _show_line() -> void:
	if not typed.is_empty() and bt >= typed.t0:   # текст без озвучки печатается по буквам
		var tx := String(typed.s)
		var n := int(floor(minf(1.0, (bt - typed.t0) / (typed.d - 0.9)) * tx.length()))
		_say_text(String(typed.get("who", beat.get("who", "ИТАН"))), tx.substr(0, n))
		return
	var cur := _cur_line()
	if cur.is_empty():
		dlg.visible = false
		return
	# озвученная реплика целиком, произнесённые слова ярче (sayWords в engine.js)
	var w: Array = StoryDB.line(cur.k).get("w", [])
	var on := 0
	for x in w:
		if bt - float(cur.t) >= float(x[0]) - 0.02:
			on += 1
	_say_words(StoryDB.who_of(cur.k), StoryDB.chunks(cur.k), on if bt < float(cur.t) + float(cur.d) + 0.1 else w.size())


func _say_words(who: String, words: PackedStringArray, on: int) -> void:
	var sep := "" if StoryDB.lang == "zh" or StoryDB.lang == "ja" else " "
	var a := sep.join(_nonempty(words.slice(0, on)))
	var b := sep.join(_nonempty(words.slice(on)))
	_say_text(who, a + ("" if b == "" else ("" if a == "" else sep) + "[color=#ffffff48]" + b + "[/color]"))


static func _nonempty(a: PackedStringArray) -> PackedStringArray:
	var out := PackedStringArray()
	for x in a:
		if x != "":
			out.append(x)
	return out


## Субтитры: имя говорящего цветом (у «Веры» — подпись), текст с подсветкой (bbcode).
func _say_text(who: String, txt: String) -> void:
	dlg.visible = txt != ""
	who_lbl.text = (StoryDB.t(who) + ("  ·  " + StoryDB.t(SPK_SUB[who]) if SPK_SUB.has(who) else "")) if who != "" else ""
	who_lbl.visible = who != ""
	who_lbl.add_theme_color_override("font_color", Color.html(SPK.get(who, "#cfd3da")))
	text_lbl.text = txt


func _music(name: String) -> void:
	if name == "":
		return
	var p := "res://assets/snd/%s.mp3" % name
	if not ResourceLoader.exists(p):
		return
	if music.stream and music.stream.resource_path == p and music.playing:
		return
	var st: AudioStreamMP3 = load(p)
	st.loop = true
	music.stream = st
	music.play()


# ---------- интерфейс ----------
func _build_ui() -> void:
	post = PostFX.new()
	add_child(post)
	ui = CanvasLayer.new()
	ui.layer = 1
	add_child(ui)
	phone = PhoneScreen.new()
	ui.add_child(phone)
	hud = CctvHud.new()
	ui.add_child(hud)
	act_view = ActView.new()
	act_view.picked.connect(_act_pick)
	shift_ui = ShiftUI.new()
	mg = MiniGames.new()
	menu = MainMenu.new()
	menu.theme = dlg.theme
	menu.new_game.connect(func():
		menu.close()
		start_scene("intro"))
	menu.continue_game.connect(_continue)
	menu.resume.connect(func():
		menu.close()
		get_tree().paused = false)
	menu.to_menu.connect(go_menu)
	menu.quit_game.connect(func(): get_tree().quit())
	ends = EndScreens.new()
	ends.next_pressed.connect(func():
		_play("click")
		next_beat())
	ends.retry_pressed.connect(_retry)
	ends.restart_pressed.connect(func(): start_scene("intro"))
	ends.menu_pressed.connect(go_menu)
	ends.back_pressed.connect(func():
		var f: Dictionary = run.picks[run.picks.size() - 1] if not run.get("picks", []).is_empty() else {}
		if not f.is_empty():
			var sc := StoryDB.scene(String(f.sc))
			start_scene(String(f.sc), maxi(0, sc.get("beats", []).size() - 1)))
	var font: FontFile = load("res://assets/fonts/ns-ui.woff") if ResourceLoader.exists("res://assets/fonts/ns-ui.woff") else null
	var theme := Theme.new()
	if font:
		font = font.duplicate()
		font.fallbacks = PhoneScreen.cjk_fallbacks()   # китайский, японский, корейский: на сайте системных шрифтов нет
		theme.default_font = font
	theme.default_font_size = 30
	black = ColorRect.new()
	black.color = Color.BLACK
	black.set_anchors_preset(Control.PRESET_FULL_RECT)
	black.visible = false
	black.mouse_filter = Control.MOUSE_FILTER_IGNORE
	ui.add_child(black)
	dlg = PanelContainer.new()
	dlg.theme = theme
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0.03, 0.035, 0.05, 0.78)
	sb.set_corner_radius_all(6)
	sb.content_margin_left = 28
	sb.content_margin_right = 28
	sb.content_margin_top = 16
	sb.content_margin_bottom = 18
	dlg.add_theme_stylebox_override("panel", sb)
	dlg.anchor_left = 0.18
	dlg.anchor_right = 0.82
	dlg.anchor_top = 1.0
	dlg.anchor_bottom = 1.0
	dlg.offset_top = -210
	dlg.offset_bottom = -40
	dlg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var vb := VBoxContainer.new()
	dlg.add_child(vb)
	who_lbl = Label.new()
	who_lbl.add_theme_font_size_override("font_size", 24)
	vb.add_child(who_lbl)
	text_lbl = RichTextLabel.new()
	text_lbl.bbcode_enabled = true
	text_lbl.fit_content = true
	text_lbl.scroll_active = false
	text_lbl.add_theme_font_size_override("normal_font_size", 34)
	text_lbl.mouse_filter = Control.MOUSE_FILTER_IGNORE
	vb.add_child(text_lbl)
	ui.add_child(dlg)
	hint_lbl = Label.new()
	hint_lbl.theme = theme
	hint_lbl.text = "▼"
	hint_lbl.anchor_left = 0.8
	hint_lbl.anchor_right = 0.82
	hint_lbl.anchor_top = 1.0
	hint_lbl.anchor_bottom = 1.0
	hint_lbl.offset_top = -80
	hint_lbl.visible = false
	ui.add_child(hint_lbl)
	choice_box = VBoxContainer.new()
	choice_box.theme = theme
	choice_box.anchor_left = 0.25
	choice_box.anchor_right = 0.75
	choice_box.anchor_top = 0.45
	choice_box.anchor_bottom = 0.9
	choice_box.add_theme_constant_override("separation", 14)
	choice_box.visible = false
	timer_bar = ProgressBar.new()
	timer_bar.show_percentage = false
	timer_bar.custom_minimum_size = Vector2(0, 8)
	var fill := StyleBoxFlat.new()
	fill.bg_color = Color(0.85, 0.12, 0.12)
	timer_bar.add_theme_stylebox_override("fill", fill)
	choice_box.add_child(timer_bar)
	ui.add_child(choice_box)
	ui.add_child(act_view)
	ui.add_child(shift_ui)
	ui.add_child(mg)
	ui.add_child(ends)
	ui.add_child(menu)
	ui.move_child(dlg, -1)   # субтитры поверх смены


func _snap() -> void:
	var p := String(test.shot)
	test = {}
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png(p)
	print("снимок ", p, " · ", scene_id, ":", bi, " · ", RenderingServer.get_current_rendering_method())
	get_tree().quit()
