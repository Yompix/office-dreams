extends Node
## Движок интерактивного кино — перенос engine.js старой игры (по частям). Кадр (beat): запись постановки (ShotPlayer),
## камера по планам cams, голос по таймингу, субтитры с подсветкой звучащего слова, переходы сцен и выбор с таймером.
## Проверка без экрана: godot … -- --scene intro --beat 2 --at 3.0 --shot out.png  (кадр в момент, снимок, выход)

const SPK := {"ИТАН": "#cfd3da", "СМЕНЩИК": "#ffd27a", "ЛИЛИ": "#ffb36b", "ФРЭНК": "#8fd0ff", "ГАС": "#9fd8a8",
	"???": "#ff3030", "КАМЕРА": "#7fe0a0", "ТЕЛЕФОН": "#9aa3b5", "ВЕРА": "#7fd4ff", "ГОЛОС": "#c8b4ff"}

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

var scene_id := ""
var scene: Dictionary = {}
var bi := -1
var beat: Dictionary = {}
var bt := 0.0              # время кадра (с начала beat)
var bdur := 1.2
var shot_off := 0.0        # тот же кадр подряд — время постановки продолжается
var wait := 0.0            # кадр кончился и ждёт нажатия: время постановки стоит, герои живут
var waiting := false
var sched: Array = []      # [{t, k, d, started}]
var flags: Dictionary = {}
var choice_t := -1.0
var choice_opts: Array = []
var choice_def := 0
var auto := false
var test := {}             # режим проверки из командной строки


func _ready() -> void:
	StoryDB.setup(_arg("lang", "ru"))
	player = ShotPlayer.new()
	add_child(player)
	_build_ui()
	music = AudioStreamPlayer.new()
	music.volume_db = -6.0
	add_child(music)
	for i in 4:
		var v := AudioStreamPlayer.new()
		add_child(v)
		voices.append(v)
	if _arg("scene", "") != "":
		test = {"at": float(_arg("at", "1.0")), "shot": _arg("shot", "")}
		start_scene(_arg("scene", ""), int(_arg("beat", "0")))
	else:
		start_scene(StoryDB.story.get("start", "intro"))


func _arg(name: String, def: String) -> String:
	var a := OS.get_cmdline_user_args()
	var i := a.find("--" + name)
	return a[i + 1] if i >= 0 and i + 1 < a.size() else def


# ---------- сцены и кадры ----------
func start_scene(id: String, at_beat := 0) -> void:
	scene_id = id
	scene = StoryDB.scene(id)
	if scene.is_empty():
		push_error("нет сцены " + id)
		return
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
	var same: bool = not prev.is_empty() and prev.get("img") != null and prev.get("img") == beat.get("img")
	shot_off = shot_off + prev_t if same else 0.0
	bt = 0.0
	wait = 0.0
	waiting = false
	hint_lbl.visible = false
	# реплики подряд: первая с sayAt (0,15 с), каждая следующая через 0,3 с после конца предыдущей
	sched.clear()
	var t := float(beat.get("sayAt", 0.15))
	for k in beat.get("say", []):
		sched.append({"t": t, "k": String(k), "d": StoryDB.dur(k), "started": false})
		t += StoryDB.dur(k) + 0.3
	if String(beat.get("text", "")) != "":
		t = maxf(t, 0.2 + maxf(2.2, String(beat.text).length() / 14.0 + 1.3))
	bdur = maxf(t + float(beat.get("pad", 0.3)), 1.2)
	var img = beat.get("img")
	if img != null and ResourceLoader.exists("res://bake/%s.json" % img):
		if player.shot == null or player.shot.id != img:
			player.play(img)
		player.visible = true
		black.visible = false
	else:
		black.visible = true   # телефон, чёрная склейка (пока без экрана телефона)
	_show_line()


func _process(delta: float) -> void:
	if scene.is_empty():
		return
	if not test.is_empty():
		delta = 1.0 / 30.0
	if choice_t >= 0.0:
		choice_t -= delta
		timer_bar.value = maxf(choice_t, 0.0)
		if choice_t <= 0.0:
			_pick(choice_def)
	if beat.is_empty():
		return
	bt += delta
	if waiting:
		wait += delta
	# голос по расписанию
	for s in sched:
		if not s.started and bt >= s.t:
			s.started = true
			_say(s.k)
	_show_line()
	# постановка и камера
	if player.shot and player.visible:
		var st := minf(shot_off + bt, player.shot.dur)
		_plan()
		player.apply(st, wait)
	if bt >= bdur and not waiting:
		if auto or beat.get("say", []).is_empty() and String(beat.get("text", "")) == "":
			next_beat()
			return
		waiting = true
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
	else:
		var kind := "конец ветки"
		for k in ["explore", "talk", "mg", "shift", "ending", "over", "back"]:
			if th.has(k):
				kind = k
		_say_text("", "[%s — переносится]" % kind)
		hint_lbl.visible = false


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


# ---------- голос и субтитры ----------
func _say(k: String) -> void:
	var p := StoryDB.voice_path(k)
	if not ResourceLoader.exists(p):
		return
	for v in voices:
		if not v.playing:
			v.stream = load(p)
			v.play()
			return


func _show_line() -> void:
	var cur: Dictionary = {}
	for s in sched:
		if bt >= s.t:
			cur = s
	if cur.is_empty():
		if String(beat.get("text", "")) != "":
			_say_text("", String(beat.text))
		else:
			dlg.visible = false
		return
	var ln := StoryDB.line(cur.k)
	var who := String(ln.get("who", ""))
	var words: Array = ln.get("w", [])
	var txt := String(ln.get("t", ""))
	var lt := bt - float(cur.t)
	var shown := 0
	for w in words:
		if lt >= float(w[0]):
			shown += 1
	_say_text(who, txt, shown, words.size())


## Субтитры: имя говорящего цветом, слова, ещё не прозвучавшие, бледнее (как «караоке» старой игры).
func _say_text(who: String, txt: String, shown := -1, total := 0) -> void:
	dlg.visible = txt != ""
	who_lbl.text = StoryDB.t(who) if who != "" else ""
	who_lbl.visible = who != ""
	who_lbl.add_theme_color_override("font_color", Color.html(SPK.get(who, "#cfd3da")))
	if shown < 0 or total == 0:
		text_lbl.text = txt
		return
	var parts := txt.split(" ")
	var k := int(round(float(shown) / total * parts.size())) if shown < total else parts.size()
	var a := " ".join(parts.slice(0, k))
	var b := " ".join(parts.slice(k))
	text_lbl.text = a + ("" if b == "" else " [color=#ffffff48]" + b + "[/color]")


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
	ui = CanvasLayer.new()
	add_child(ui)
	var font: FontFile = load("res://assets/fonts/ns-ui.woff") if ResourceLoader.exists("res://assets/fonts/ns-ui.woff") else null
	var theme := Theme.new()
	if font:
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


func _snap() -> void:
	var p := String(test.shot)
	test = {}
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png(p)
	print("снимок ", p, " · ", scene_id, ":", bi, " · ", RenderingServer.get_current_rendering_method())
	get_tree().quit()
