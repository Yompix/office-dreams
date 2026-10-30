class_name MiniGames
extends Control
## Мини-игры второго акта — перенос MG (engine.js 787–945): держать (не дышать / держать Фрэнка), бег (сторона, где не гаснет свет),
## выдернуть шнур (CAM 13), протереть запотевшее стекло, фонарь (луч ведёт рука), перемотка записи камеры. Кадр под мини-игрой
## (view) рисует движок: запись кадра, план, зум, время (tAt — перемотка, st — с начала), фонарь, туман на стекле, тёмная сторона.

signal finished(next: String)

const RED := Color("#e03a3a")
var game
var kind := ""
var th := {}
var t := 0.0                 # секунд с начала мини-игры
var view := {}               # { img, c, dur, tAt, st, label, torch: Vector2, run: "L"/"R", rw, rw_tag, rw_clock }
var u := 7.3
var _end := false
var _st := {}                # состояние текущей игры
var head: Label
var sub: Label
var _sub_line: Label
var _fog_img: Image
var _fog_tex: ImageTexture
var _fog_rect: TextureRect
var _rng := RandomNumberGenerator.new()


func _init() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	visible = false


func start(p_th: Dictionary, p_game) -> void:
	game = p_game
	th = p_th
	kind = String(th.mg)
	t = 0.0
	_end = false
	_st = {}
	view = {}
	for c in get_children():
		c.queue_free()
	u = get_viewport_rect().size.y * 0.0068
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	visible = true
	game._music("tension")
	_head(String(th.get("title", "")), String(th.get("hint", "")), {"low": 0.15, "cam": 0.12}.get(_head_kind(), 0.06))
	call("_start_" + kind)


func _head_kind() -> String:
	return "cam" if kind == "tape" else ("low" if kind == "cord" else "")


func _head(title: String, hint: String, top: float) -> void:
	var box := VBoxContainer.new()
	box.anchor_right = 1.0
	box.anchor_top = top
	box.anchor_bottom = top
	box.mouse_filter = Control.MOUSE_FILTER_IGNORE
	head = Label.new()
	head.text = title
	head.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	head.add_theme_font_size_override("font_size", int(5.4 * u))
	head.add_theme_color_override("font_shadow_color", Color.BLACK)
	head.add_theme_constant_override("shadow_outline_size", int(2 * u))
	box.add_child(head)
	sub = Label.new()
	sub.text = hint
	sub.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	sub.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	sub.add_theme_font_size_override("font_size", int(3.6 * u))
	sub.modulate.a = 0.75
	sub.add_theme_color_override("font_shadow_color", Color.BLACK)
	sub.add_theme_constant_override("shadow_outline_size", int(2 * u))
	box.add_child(sub)
	add_child(box)


func _meter(bottom: float) -> ProgressBar:
	var m := ProgressBar.new()
	m.show_percentage = false
	m.max_value = 1.0
	m.anchor_left = 0.07
	m.anchor_right = 0.93
	m.anchor_top = 1.0 - bottom
	m.anchor_bottom = 1.0 - bottom
	m.custom_minimum_size = Vector2(0, 4 * u)
	var bg := StyleBoxFlat.new()
	bg.bg_color = Color(0, 0, 0, 0.6)
	bg.border_color = Color(1, 1, 1, 0.4)
	bg.set_border_width_all(maxi(2, int(0.5 * u)))
	bg.set_corner_radius_all(int(3 * u))
	var fg := StyleBoxFlat.new()
	fg.bg_color = RED
	fg.set_corner_radius_all(int(3 * u))
	m.add_theme_stylebox_override("background", bg)
	m.add_theme_stylebox_override("fill", fg)
	m.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(m)
	return m


func _fin(next: String) -> void:
	if _end:
		return
	_end = true
	visible = false
	view = {}
	finished.emit(next)


# ---------- держать: не дышать, пока шаги не стихнут (или держать Фрэнка, пока зовёт голос матери) ----------
func _start_hold() -> void:
	_st = {"held": false, "a": 1.0, "safe": false, "steps": 0}
	_st.meter = _meter(0.72)
	var ml := Label.new()
	ml.text = StoryDB.t(String(th.get("meter", "воздух")))
	ml.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	ml.anchor_right = 1.0
	ml.anchor_top = 0.3
	ml.anchor_bottom = 0.3
	ml.add_theme_font_size_override("font_size", int(3.4 * u))
	ml.modulate.a = 0.7
	add_child(ml)
	if th.has("calls"):   # что зовёт голос — и словами
		_sub_line = Label.new()
		_sub_line.anchor_left = 0.07
		_sub_line.anchor_right = 0.93
		_sub_line.anchor_top = 0.36
		_sub_line.anchor_bottom = 0.36
		_sub_line.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		_sub_line.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		_sub_line.add_theme_font_size_override("font_size", int(3.8 * u))
		_sub_line.add_theme_color_override("font_color", Color("#ff6a6a"))
		add_child(_sub_line)
	var b := Button.new()
	b.text = StoryDB.t(String(th.get("btn", "ДЕРЖИ")))
	b.add_theme_font_size_override("font_size", int(5 * u))
	b.custom_minimum_size = Vector2(44, 44) * u
	b.anchor_left = 0.5
	b.anchor_right = 0.5
	b.anchor_top = 1.0
	b.anchor_bottom = 1.0
	b.offset_left = -22 * u
	b.offset_right = 22 * u
	b.offset_top = -(0.18 * get_viewport_rect().size.y + 44 * u)
	b.offset_bottom = -0.18 * get_viewport_rect().size.y
	for s in ["normal", "hover", "pressed", "focus"]:
		var sb := StyleBoxFlat.new()
		sb.bg_color = Color(0.47, 0.04, 0.06, 0.9) if s != "pressed" else Color(0.78, 0.08, 0.12, 0.95)
		sb.border_color = Color(1, 0.24, 0.27, 0.8)
		sb.set_border_width_all(int(u))
		sb.set_corner_radius_all(int(22 * u))
		b.add_theme_stylebox_override(s, sb)
	b.button_down.connect(func(): _st.held = true)
	b.button_up.connect(func(): _st.held = false)
	_st.btn = b
	add_child(b)
	game._amb("heart", true, 0.7)


func _tick_hold(dt: float) -> void:
	var dur := float(th.get("dur", 9))
	var held: bool = _st.held or Input.is_action_pressed("ui_accept") or game.autoplay and not _st.safe
	_st.btn.text = StoryDB.t(String(th.get("btnOn", "НЕ ДЫШИ") if held else th.get("btn", "ДЕРЖИ")))
	if th.has("calls") and t > 1.0 and floor(t / 2.6) > _st.steps and t < dur:   # голос из темноты зовёт
		_st.steps += 1
		var k: String = th.calls[_st.steps % th.calls.size()]
		game._say(k)
		if _sub_line and not StoryDB.line(k).is_empty():
			_sub_line.text = String(StoryDB.line(k).t)
	elif not th.has("calls") and t > 0.8 and floor(t / 1.1) > _st.steps and t < dur:   # босые шаги кружат вокруг будки
		_st.steps += 1
		game._play("steps_barefoot", {"off": _rng.randf() * 4, "cut": 1, "gain": 0.6 + 0.4 * sin(t)})
		if _st.steps % 3 == 0:
			game._play("breath_close", {"gain": 0.45})
	_st.a = clampf(_st.a + (-minf(0.085, 0.8 / dur) if held else 0.25) * dt, 0.0, 1.0)
	_st.meter.value = _st.a
	if t > 2.5 and t < dur and not held:   # выдохнул — услышала
		game._amb("heart", false)
		_fin(String(th.lose))
		return
	if _st.a <= 0.0:
		game._amb("heart", false)
		_fin(String(th.lose))
		return
	if t >= dur and not _st.safe:
		_st.safe = true
		head.text = StoryDB.t(String(th.get("safe", "ТИХО…")))
		sub.text = StoryDB.t(String(th.get("safeHint", "Шаги ушли. Можно дышать.")))
	if _st.safe and not held:
		game._amb("heart", false)
		_fin(String(th.win))


# ---------- бег: свет гаснет с одной стороны — беги в другую ----------
func _start_run() -> void:
	_st = {"k": 0, "n": int(th.get("n", 5))}
	view = {"img": "run_lit", "c": [0.5, 0.5, 1.0, 1.04], "dur": 1e9}
	var row := HBoxContainer.new()
	row.anchor_left = 0.06
	row.anchor_right = 0.94
	row.anchor_top = 1.0
	row.anchor_bottom = 1.0
	row.offset_top = -(0.16 * get_viewport_rect().size.y + 26 * u)
	row.offset_bottom = -0.16 * get_viewport_rect().size.y
	row.add_theme_constant_override("separation", int(4 * u))
	for d in ["L", "R"]:
		var b := Button.new()
		b.text = "←" if d == "L" else "→"
		b.add_theme_font_size_override("font_size", int(12 * u))
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var sb := StyleBoxFlat.new()
		sb.bg_color = Color(0, 0, 0, 0.6)
		sb.border_color = Color(1, 1, 1, 0.35)
		sb.set_border_width_all(int(0.6 * u))
		sb.set_corner_radius_all(int(4 * u))
		b.add_theme_stylebox_override("normal", sb)
		var dd: String = d
		b.pressed.connect(func(): _run_pick(dd))
		row.add_child(b)
	add_child(row)
	_st.meter = _meter(0.72)
	_run_step()


func _run_step() -> void:
	if _st.k >= _st.n:
		_fin(String(th.win))
		return
	_st.bad = "L" if _rng.randf() < 0.5 else "R"
	_st.t0 = t
	_st.lim = maxf(1.3, 2.6 - _st.k * 0.2)
	view.run = _st.bad
	sub.text = StoryDB.t("Свет гаснет СЛЕВА — беги ВПРАВО →" if _st.bad == "L" else "Свет гаснет СПРАВА — беги ← ВЛЕВО") + " (%d/%d)" % [_st.k + 1, _st.n]
	game._play("steps", {"gain": 0.8})


func _run_pick(d: String) -> void:
	if _end:
		return
	if d == _st.bad:
		_fin(String(th.lose))
		return
	game._play("click")
	_st.k += 1
	_run_step()


func _tick_run(_dt: float) -> void:
	var l: float = _st.lim - (t - _st.t0)
	_st.meter.value = maxf(0.0, l / _st.lim)
	if game.autoplay and t - _st.t0 > 0.5:
		_run_pick("R" if _st.bad == "L" else "L")
		return
	if Input.is_action_just_pressed("ui_left"):
		_run_pick("L")
	elif Input.is_action_just_pressed("ui_right"):
		_run_pick("R")
	elif l <= 0.0:
		_fin(String(th.lose))


# ---------- выдернуть шнур монитора, пока оно не подошло к камере ----------
func _start_cord() -> void:
	var dur := float(th.get("dur", 3.2))
	view = {"img": String(th.get("img", "cam13_monster")), "c": th.get("cam", [0.54, 0.34, 1.2, 3.4]), "dur": dur, "label": String(th.get("label", "CAM 13"))}
	_st = {"dur": dur, "y0": -1.0, "d": 0.0}
	game._amb("static", true, 0.45)
	game._say("ch_monster")
	var box := Control.new()
	box.anchor_left = 0.82
	box.anchor_right = 0.82
	box.anchor_top = 0.26
	box.anchor_bottom = 0.82
	box.offset_left = -13 * u
	box.offset_right = 13 * u
	box.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var wire := ColorRect.new()
	wire.color = Color("#1a1a1a")
	wire.anchor_left = 0.5
	wire.anchor_right = 0.5
	wire.anchor_bottom = 1.0
	wire.offset_left = -0.7 * u
	wire.offset_right = 0.7 * u
	wire.mouse_filter = Control.MOUSE_FILTER_IGNORE
	box.add_child(wire)
	var plug := Button.new()
	plug.text = "🔌"
	plug.add_theme_font_size_override("font_size", int(11 * u))
	plug.custom_minimum_size = Vector2(22, 22) * u
	plug.position = Vector2(2 * u, 0)
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color("#dddddd")
	sb.set_corner_radius_all(int(4 * u))
	for s in ["normal", "hover", "pressed", "focus"]:
		plug.add_theme_stylebox_override(s, sb)
	plug.gui_input.connect(func(e: InputEvent):
		if e is InputEventMouseButton:
			_st.y0 = (e as InputEventMouseButton).global_position.y if e.pressed else -1.0
			if not e.pressed:
				plug.position.y = 0
		elif e is InputEventMouseMotion and _st.y0 >= 0.0:
			var d := maxf(0.0, (e as InputEventMouseMotion).global_position.y - _st.y0)
			plug.position.y = d
			if d > box.size.y * 0.45:
				_cord_win())
	box.add_child(plug)
	add_child(box)
	_st.meter = _meter(0.08)
	_st.meter.value = 1.0


func _cord_win() -> void:
	game._amb("static", false)
	game._play("click")
	game._play("boom")
	_fin(String(th.win))


func _tick_cord(_dt: float) -> void:
	var l: float = _st.dur - t
	_st.meter.value = maxf(0.0, l / _st.dur)
	if game.autoplay and t > 1.0:
		_cord_win()
		return
	if l <= 0.0:
		game._amb("static", false)
		_fin(String(th.lose))


# ---------- запотевшее стекло: протереть; видно больше половины — в стекло бьёт ладонь ----------
func _start_wipe() -> void:
	view = {"img": String(th.get("img", "katya_slam")), "c": th.get("cam", [0.5, 0.47, 1.22, 1.22]), "dur": 1e9, "tAt": 0.1}
	var FW := 192
	var FH := 108
	_fog_img = Image.create(FW, FH, false, Image.FORMAT_RGBA8)
	for y in FH:   # пелена гуще к краям, в ней капельки
		for x in FW:
			var r := Vector2(x - FW / 2.0, y - FH * 0.45).length() / (FH * 0.62)
			_fog_img.set_pixel(x, y, Color(lerpf(150, 120, clampf(r, 0, 1)) / 255.0, lerpf(162, 130, clampf(r, 0, 1)) / 255.0, lerpf(172, 140, clampf(r, 0, 1)) / 255.0, lerpf(0.93, 0.985, clampf(r, 0, 1))))
	for i in 260:
		var p := Vector2i(_rng.randi() % FW, _rng.randi() % FH)
		var c := _fog_img.get_pixelv(p)
		_fog_img.set_pixelv(p, c.lerp(Color(0.85, 0.9, 0.9, c.a), 0.4))
	for i in 7:   # подтёки — по ним чуть видно, что за стеклом
		var x := _rng.randf() * FW
		var y := _rng.randf() * 60.0
		for j in int(30 + _rng.randf() * 60):
			x += _rng.randf() * 0.8 - 0.4
			y += 1.3
			if x >= 0 and x < FW and y < FH:
				var c := _fog_img.get_pixel(int(x), int(y))
				c.a *= 0.78
				_fog_img.set_pixel(int(x), int(y), c)
	_fog_tex = ImageTexture.create_from_image(_fog_img)
	_fog_rect = TextureRect.new()
	_fog_rect.texture = _fog_tex
	_fog_rect.set_anchors_preset(Control.PRESET_FULL_RECT)
	_fog_rect.stretch_mode = TextureRect.STRETCH_SCALE
	_fog_rect.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	_fog_rect.mouse_filter = Control.MOUSE_FILTER_STOP
	_fog_rect.gui_input.connect(_wipe_input)
	add_child(_fog_rect)
	move_child(_fog_rect, 0)
	_st = {"last": null, "slam": -1.0, "chk": 0.0, "clear": 0.0, "sq": 0.0}
	game._amb("heart", true, 0.35)


func _wipe_input(e: InputEvent) -> void:
	if _st.slam >= 0.0:
		return
	var sz := get_viewport_rect().size
	if e is InputEventMouseButton:
		_st.last = (e.position / sz * Vector2(192, 108)) if e.pressed else null
		if e.pressed:
			_rub(_st.last, _st.last)
	elif e is InputEventMouseMotion and _st.last != null:
		var p: Vector2 = e.position / sz * Vector2(192, 108)
		_rub(_st.last, p)
		if p.distance_to(_st.last) > 5 and t - _st.sq > 0.16:
			_st.sq = t
			game._play("glass_tap", {"gain": 0.12, "rate": 1.7 + _rng.randf() * 0.4, "cut": 0.12})
		_st.last = p


func _rub(a: Vector2, b: Vector2) -> void:
	var n := maxi(1, int(a.distance_to(b) / 3.0))
	for i in n + 1:
		var c := a.lerp(b, float(i) / n)
		for y in range(int(c.y) - 10, int(c.y) + 11):
			for x in range(int(c.x) - 10, int(c.x) + 11):
				if x >= 0 and y >= 0 and x < 192 and y < 108 and Vector2(x, y).distance_to(c) <= 9.5:
					var px := _fog_img.get_pixel(x, y)
					px.a *= 0.2
					_fog_img.set_pixel(x, y, px)
	_fog_tex.update(_fog_img)


func _tick_wipe(dt: float) -> void:
	if _st.slam < 0.0:
		if game.autoplay:
			_rub(Vector2(fmod(t * 300.0, 192.0), 54 + sin(t * 3) * 40), Vector2(fmod(t * 300.0 + 20, 192.0), 54 + sin(t * 3 + 0.3) * 40))
		if t - _st.chk > 0.25:   # стекло снова потеет от дыхания с той стороны; сколько уже видно
			_st.chk = t
			var n := 0
			var tot := 0
			for y in range(0, 108, 2):
				for x in range(0, 192, 2):
					var px := _fog_img.get_pixel(x, y)
					px.a = minf(1.0, px.a + 0.006 * 15.0 * dt * 60.0 / 15.0)
					_fog_img.set_pixel(x, y, px)
					tot += 1
					if px.a < 90.0 / 255.0:
						n += 1
			_fog_tex.update(_fog_img)
			_st.clear = float(n) / tot
		if _st.clear > float(th.get("need", 0.45)) or t > float(th.get("dur", 15)):   # увидел пустую улицу — и тут же удар в стекло
			_st.slam = t
			view.erase("tAt")
			view.st = 0.1 - t
			head.visible = false
			sub.visible = false
			await get_tree().create_timer(0.14).timeout
			if th.has("slam") and not _end:
				view.img = String(th.slam)
				view.st = -t + 0.02
			await get_tree().create_timer(0.01).timeout
			game._play("glass_slam", {"gain": 1.1})
			game._play("breath_close", {"gain": 0.7})
	elif t - _st.slam > float(th.get("after", 1.7)):
		game._amb("heart", false)
		_fin(String(th.win))


# ---------- фонарь: луч ведёт рука; найти дверь ----------
func _start_torch() -> void:
	view = {"img": String(th.img), "c": th.get("cam", [0.5, 0.5, 1.0, 1.0]), "dur": 1e9, "torch": Vector2(0.3, 0.9)}
	_st = {"tgt": Vector2(0.3, 0.9), "seen": -1.0 if th.has("mon") else 0.0, "on_mon": 0.0, "on_goal": 0.0, "gone": not th.has("mon"), "found": -1.0}
	mouse_filter = Control.MOUSE_FILTER_STOP
	game._play("click")
	game._amb("heart", true, 0.3)


func _gui_input(e: InputEvent) -> void:
	if kind == "torch" and _st.get("found", 0.0) < 0.0 and (e is InputEventMouseButton or (e is InputEventMouseMotion and (e.button_mask != 0))):
		var sz := get_viewport_rect().size
		_st.tgt = Vector2(clampf(e.position.x / sz.x, 0, 1), clampf(e.position.y / sz.y - 0.07, 0, 1))   # луч чуть выше пальца


func _near(p: Array, r: float) -> bool:
	var sz := get_viewport_rect().size
	var L: Vector2 = view.torch
	return Vector2((L.x - float(p[0])) * sz.x / sz.y, L.y - float(p[1])).length() < r


func _tick_torch(dt: float) -> void:
	if game.autoplay:
		_st.tgt = Vector2(float(th.goal[0]), float(th.goal[1]))
	view.torch = (view.torch as Vector2).lerp(_st.tgt, minf(1.0, dt * 9.0))   # фонарь тяжёлый — луч догоняет палец
	if _st.seen < 0.0:
		_st.on_mon = _st.on_mon + dt if _near(th.mon, 0.06) else 0.0
		if _st.on_mon > 0.25:
			_st.seen = t
			game._play("breath_close", {"gain": 0.9})
			game._play("whispers", {"gain": 0.35, "cut": 1.4})
	elif not _st.gone and t - _st.seen > 0.75:
		_st.gone = true
		view.img = String(th.img2)
		game._play("fluor_flicker", {"gain": 0.4, "cut": 0.3})
		sub.text = String(th.get("hint2", ""))
	if _st.gone and _st.found < 0.0:
		_st.on_goal = _st.on_goal + dt if _near(th.goal, 0.05) else 0.0
		if _st.on_goal > 0.7:
			_st.found = t
			game._play("click")
			sub.text = String(th.get("found", ""))
	elif _st.found >= 0.0 and t - _st.found > 0.8:
		game._amb("heart", false)
		_fin(String(th.win))


# ---------- перемотка записи: ползунок влево — запись идёт назад; нашёл, где он ещё жив, — запись сама играет дальше ----------
func _start_tape() -> void:
	var len_ := float(th.get("len", 5.6))
	view = {"img": String(th.img), "c": th.get("cam", [0.47, 0.56, 1.9, 1.9]), "dur": 1e9, "tAt": len_, "label": String(th.get("label", "")),
		"rw": 0.0, "rw_tag": StoryDB.t("◀◀ ЗАПИСЬ"), "rw_clock": _clock_at(len_)}
	_st = {"len": len_, "drag": false, "last_tick": len_, "found": -1.0, "pl": -1.0}
	var box := Control.new()
	box.anchor_left = 0.09
	box.anchor_right = 0.91
	box.anchor_top = 1.0
	box.anchor_bottom = 1.0
	box.offset_top = -(0.12 * get_viewport_rect().size.y + 18 * u)
	box.offset_bottom = -0.12 * get_viewport_rect().size.y
	var bar := ProgressBar.new()
	bar.show_percentage = false
	bar.max_value = 1.0
	bar.value = 1.0
	bar.anchor_right = 1.0
	bar.anchor_top = 0.5
	bar.anchor_bottom = 0.5
	bar.offset_top = -1.2 * u
	bar.offset_bottom = 1.2 * u
	bar.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var fg := StyleBoxFlat.new()
	fg.bg_color = RED
	bar.add_theme_stylebox_override("fill", fg)
	box.add_child(bar)
	var knob := Panel.new()
	var ks := StyleBoxFlat.new()
	ks.bg_color = Color("#dddddd")
	ks.set_corner_radius_all(int(8 * u))
	knob.add_theme_stylebox_override("panel", ks)
	knob.size = Vector2(16, 16) * u
	knob.mouse_filter = Control.MOUSE_FILTER_IGNORE
	box.add_child(knob)
	box.gui_input.connect(func(e: InputEvent):
		if _st.found >= 0.0:
			return
		if e is InputEventMouseButton:
			_st.drag = e.pressed
		if (e is InputEventMouseButton and e.pressed) or (e is InputEventMouseMotion and _st.drag):
			_tape_set(e.position.x / box.size.x))
	add_child(box)
	_st.bar = bar
	_st.knob = knob
	_st.box = box
	game._amb("static", true, 0.22)
	game._play("click")
	_tape_set(1.0)


func _clock_at(tt: float) -> String:
	var b: Array = th.get("clock0", [3, 0, 0])
	var x := int(b[0]) * 3600 + int(b[1]) * 60 + int(b[2]) + int(floor(tt))
	return "%02d:%02d:%02d" % [x / 3600, (x / 60) % 60, x % 60]


func _tape_set(k: float) -> void:
	k = clampf(k, 0.0, 1.0)
	var tt: float = k * _st.len
	view.rw = minf(1.0, float(view.rw) + absf(tt - float(view.tAt)) * 1.5)
	view.tAt = tt
	view.rw_clock = _clock_at(tt)
	_st.bar.value = k
	_st.knob.position = Vector2(k * _st.box.size.x - 8 * u, _st.box.size.y / 2.0 - 8 * u)
	if absf(tt - _st.last_tick) > 0.18:
		_st.last_tick = tt
		game._play("tick", {"gain": 0.16, "rate": 1.5})
	if _st.found < 0.0 and tt < float(th.goal):
		_st.found = t
		_st.drag = false
		game._play("click")
		sub.text = String(th.get("found", ""))
		view.rw_tag = StoryDB.t("▶ ЗАПИСЬ")


func _tick_tape(_dt: float) -> void:
	view.rw = float(view.rw) * 0.88
	if game.autoplay and _st.found < 0.0:
		_tape_set(maxf(0.0, (float(view.tAt) - 0.08) / _st.len))
	if _st.found >= 0.0:   # сама играет вперёд с того места, где он ещё жив, — до конца рывка
		var e: float = t - _st.found
		if e > 0.9:
			if _st.pl < 0.0:
				_st.pl = float(view.tAt)
				for s in th.get("sfx", []):
					var at: float = float(s[1]) - _st.pl
					var op: Dictionary = s[2] if s.size() > 2 else {}
					if at <= 0.0:
						game._play(String(s[0]), op)
					else:
						get_tree().create_timer(at).timeout.connect(func(): game._play(String(s[0]), op))
			view.tAt = _st.pl + (e - 0.9)
			view.rw_clock = _clock_at(float(view.tAt))
			if float(view.tAt) > float(th.stop):
				game._amb("static", false)
				_fin(String(th.win))


func _process(delta: float) -> void:
	if not visible or _end:
		return
	var dt := minf(0.1, delta)
	if game and not game.test.is_empty():
		dt = 1.0 / 30.0
	t += dt
	call("_tick_" + kind, dt)
