extends Control
## «Костёр»: друзья в сети — фонарики вокруг огня. Нажмите на фонарик, чтобы зайти к другу или написать ему.

var app
var arg

var _t := 0.0
var _stars := []
var _spots := []      # [{rect, friend}]
var _hover := -1
var _deco := "camp_logs"
var _hint: Label


func _ready() -> void:
	size_flags_vertical = Control.SIZE_EXPAND_FILL
	mouse_filter = Control.MOUSE_FILTER_STOP
	var rng := RandomNumberGenerator.new()
	rng.seed = 42
	for i in 90:
		_stars.append([Vector2(rng.randf(), rng.randf() * 0.55), rng.randf_range(0.6, 2.0), rng.randf() * TAU])
	# подсказка — «таблетка» сверху по центру
	var head := PanelContainer.new()
	head.add_theme_stylebox_override("panel", UI._hpad(UI.box(Color(0, 0, 0, 0.35), 99), 14, 6))
	head.set_anchors_preset(Control.PRESET_CENTER_TOP)
	head.grow_horizontal = Control.GROW_DIRECTION_BOTH
	head.position.y = 12
	_hint = UI.label("", 13, Color(1, 1, 1, 0.8), UI.font_bold)
	head.add_child(_hint)
	head.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(head)
	clip_contents = true
	Social.changed.connect(func(w): if w in ["friends", "conn", "eco"]: _rebuild())
	_rebuild()


func _rebuild() -> void:
	_deco = Social.eco.get("equip", {}).get("campfire", "camp_logs") if Social.eco.get("equip") is Dictionary else "camp_logs"
	for c in get_children():
		if c is LanternIcon or c.has_meta("spot"):
			c.queue_free()
	_spots.clear()
	var online := Social.friends.filter(func(f): return f.get("online", false))
	_hint.text = ("У костра друзей: %d · нажмите на фонарик" % online.size()) if Social.online() else "Нет связи с сервером LANTERN"
	if app:
		app.set_title("Костёр", "Друзья в сети — зайти в мир или написать")
	var people := [{"me": true, "nick": Social.me.get("nick", "Вы"), "lantern": Social.eco.get("lantern", {"level": 1}), "equip": Social.eco.get("equip", {})}]
	people.append_array(online)
	for i in people.size():
		var f: Dictionary = people[i]
		var li := LanternIcon.new().setup(int(f.get("lantern", {}).get("level", 1)), f.get("equip", ""), 64)
		li.seed_hue = i * 0.37
		add_child(li)
		var l := UI.label(f.get("nick", "") + ("" if not f.get("me", false) else " (вы)"), 14, UI.TEXT, UI.font_bold)
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		l.set_meta("spot", true)
		l.clip_text = true
		l.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		l.size.x = 160
		add_child(l)
		var st := UI.label("" if f.get("me", false) else _short(f), 12, Color("ffb648"))
		st.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		st.set_meta("spot", true)
		st.clip_text = true
		st.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		st.size.x = 160
		add_child(st)
		_spots.append({"f": f, "icon": li, "label": l, "status": st})
	_layout()


static func _short(f: Dictionary) -> String:
	var a: Variant = f.get("activity")
	if a is Dictionary and a.get("code", "") != "":
		return "в мире — зайти"
	return "у костра"


func _notification(what: int) -> void:
	if what == NOTIFICATION_RESIZED:
		_layout()


func _layout() -> void:
	var c := Vector2(size.x / 2, size.y * 0.66)
	var n := _spots.size()
	for i in n:
		var a: float = PI / 2 + (i * TAU / max(n, 1)) + (0.0 if n > 1 else 0.0)
		var p := c + Vector2(cos(a) * size.x * 0.30, sin(a) * size.y * 0.20)
		if n == 1:
			p = c + Vector2(0, size.y * 0.2)
		var s: Dictionary = _spots[i]
		s.icon.position = p - Vector2(32, 52)
		s.icon.size = Vector2(64, 64)
		s.label.position = p + Vector2(-80, 12)
		s.status.position = p + Vector2(-80, 32)
		s["rect"] = Rect2(p - Vector2(50, 56), Vector2(100, 110))


func _process(d: float) -> void:
	_t += d
	queue_redraw()


func _gui_input(e: InputEvent) -> void:
	if e is InputEventMouseMotion:
		var h := -1
		for i in _spots.size():
			if _spots[i].get("rect", Rect2()).has_point(e.position):
				h = i
		if h != _hover:
			_hover = h
			mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND if h > 0 else Control.CURSOR_ARROW
	elif e is InputEventMouseButton and e.pressed and e.button_index == MOUSE_BUTTON_LEFT:
		# телефон: касание без движения мыши — ищем фонарик по месту нажатия
		_hover = -1
		for i in _spots.size():
			if _spots[i].get("rect", Rect2()).has_point(e.position):
				_hover = i
		if _hover <= 0:
			return
		var f: Dictionary = _spots[_hover].f
		var a: Variant = f.get("activity")
		if a is Dictionary and a.get("code", "") != "":
			app.go("join", true, {"input": a.code, "edition": "both"})
		else:
			app.go("friends", true, {"chat": int(f.id)})


func _draw() -> void:
	var w := size.x
	var h := size.y
	# небо
	var sky := [Color("070a14"), Color("101a33"), Color("1c2340")]
	draw_rect(Rect2(0, 0, w, h), sky[0])
	for i in 24:
		draw_rect(Rect2(0, h * i / 24.0, w, h / 24.0 + 1), sky[0].lerp(sky[2], i / 24.0 * 0.8))
	for s in _stars:
		var tw: float = 0.4 + 0.6 * abs(sin(_t * 0.8 + s[2]))
		draw_circle(s[0] * Vector2(w, h), s[1], Color(1, 1, 1, 0.5 * tw))
	if _deco in ["camp_stars", "camp_s1b"]:
		for i in 5:
			var y := h * (0.12 + i * 0.035)
			draw_line(Vector2(0, y + sin(_t * 0.3 + i) * 10), Vector2(w, y + cos(_t * 0.25 + i) * 14), Color(0.3, 1.0, 0.7, 0.05 + i * 0.01), 12)
	# лес на горизонте
	var pts := PackedVector2Array([Vector2(0, h)])
	for i in 41:
		var x := w * i / 40.0
		pts.append(Vector2(x, h * 0.5 - (abs(sin(i * 1.7)) * 40 + (i % 3) * 14)))
	pts.append(Vector2(w, h))
	draw_colored_polygon(pts, Color("0a1020"))
	# поляна
	var c := Vector2(w / 2, h * 0.66)
	var ground := Color("15233a") if _deco != "camp_snow" else Color("c9d6ea")
	draw_set_transform(c, 0, Vector2(1, 0.36))
	draw_circle(Vector2.ZERO, w * 0.42, ground)
	draw_circle(Vector2.ZERO, w * 0.30, ground.lightened(0.05))
	# свет от огня на земле
	var flick := 0.85 + 0.15 * sin(_t * 9.0) * sin(_t * 3.7)
	for i in 16:
		draw_circle(Vector2.ZERO, w * (0.36 - i * 0.02), Color(1.0, 0.55, 0.15, 0.025 * flick))
	draw_set_transform(Vector2.ZERO, 0, Vector2.ONE)
	if _deco in ["camp_tent", "camp_s1"]:
		var tp := c + Vector2(-w * 0.32, -h * 0.06)
		draw_colored_polygon(PackedVector2Array([tp + Vector2(-70, 60), tp + Vector2(0, -50), tp + Vector2(70, 60)]), Color("7a4a2a"))
		draw_colored_polygon(PackedVector2Array([tp + Vector2(-14, 60), tp + Vector2(0, 10), tp + Vector2(14, 60)]), Color("2a1a10"))
	# дрова
	draw_line(c + Vector2(-46, 18), c + Vector2(46, -2), Color("4a2e1c"), 14)
	draw_line(c + Vector2(-46, -2), c + Vector2(46, 18), Color("5a3822"), 14)
	# пламя
	for layer in 3:
		var col: Color = [Color("ff5a1f"), Color("ff9a2e"), Color("ffe08a")][layer]
		var hh: float = (110 - layer * 30) * flick
		var ww: float = 46 - layer * 12
		var poly := PackedVector2Array()
		for k in 13:
			var a := PI + k * PI / 12.0
			var r := ww * (0.8 + 0.2 * sin(_t * 11 + k + layer))
			poly.append(c + Vector2(cos(a) * r, 4))
		poly.reverse()
		var tip := c + Vector2(sin(_t * 4.0 + layer) * 8, -hh)
		var fl := PackedVector2Array([c + Vector2(-ww, 4)])
		for k in 6:
			var tt := k / 6.0
			fl.append(c.lerp(tip, tt) + Vector2(-ww * (1 - tt) + sin(_t * 8 + k + layer) * 4, 0))
		fl.append(tip)
		for k in range(5, -1, -1):
			var tt := k / 6.0
			fl.append(c.lerp(tip, tt) + Vector2(ww * (1 - tt) + cos(_t * 7 + k + layer) * 4, 0))
		fl.append(c + Vector2(ww, 4))
		draw_colored_polygon(fl, col)
	# искры / светлячки
	var nsp := 18 if _deco in ["camp_fireflies"] else 8
	for i in nsp:
		var ph := fmod(_t * 0.5 + i * 0.37, 1.0)
		var p := c + Vector2(sin(i * 3.1 + _t) * 30 * (1 + ph), -40 - ph * 200)
		if _deco == "camp_fireflies" and i >= 8:
			p = Vector2(w * fmod(i * 0.173, 1.0) + sin(_t + i) * 30, h * (0.45 + fmod(i * 0.29, 0.3)) + cos(_t * 1.3 + i) * 20)
			draw_circle(p, 3, Color(0.8, 1.0, 0.4, 0.6 + 0.4 * sin(_t * 3 + i)))
		else:
			draw_circle(p, 2, Color(1.0, 0.75, 0.3, 1.0 - ph))
	if _deco == "camp_snow":
		for i in 60:
			var p := Vector2(fmod(i * 97.3 + sin(_t + i) * 20, w), fmod(i * 53.1 + _t * 40, h))
			draw_circle(p, 2, Color(1, 1, 1, 0.7))
	if _hover > 0 and _hover < _spots.size():
		var r: Rect2 = _spots[_hover].get("rect", Rect2())
		draw_rect(r, Color(1, 0.8, 0.4, 0.12), true)
