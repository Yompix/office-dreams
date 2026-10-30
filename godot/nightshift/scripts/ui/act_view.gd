class_name ActView
extends Control
## Обход и разговор — перенос showAct / actSpots (engine.js). Обход (explore): кольца на предметах кадра (мировая точка →
## место на экране каждый кадр, камера дышит), подсказка сверху, «выйти» снизу. Разговор (talk): темы списком (в горизонтали —
## слева, собеседник справа). Каждая точка или тема ведёт в свою короткую сцену, та кончается then: { back: 'сцена' }.

signal picked(go: String, set_flags, visit: String)

const GOLD := Color("#f2c21b")
var kind := "explore"
var th := {}
var items: Array = []      # [{s, i}]
var spots: Array = []      # [{node, ring, label, at}]
var _u := 7.3              # единица интерфейса старой игры: 0,68 % высоты кадра
var _laid := false
var _t := 0.0


func _init() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	visible = false


func open(p_th: Dictionary, p_kind: String, p_items: Array, leave_ok: bool, theme_: Theme) -> void:
	for c in get_children():
		c.queue_free()
	spots.clear()
	th = p_th
	kind = p_kind
	items = p_items
	theme = theme_
	_laid = false
	_u = get_viewport_rect().size.y * 0.0068
	var lv = th.get("leave", th.get("bye"))
	if kind == "talk":
		_talk_list(lv, leave_ok)
	else:
		var hint := Label.new()
		hint.text = String(th.get("hint", StoryDB.t("Осмотрись. Нажми на то, что хочешь проверить."))).to_upper()
		hint.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		hint.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		hint.anchor_left = 0.06
		hint.anchor_right = 0.94
		hint.offset_top = 16 * _u
		hint.add_theme_font_size_override("font_size", int(3.8 * _u))
		hint.add_theme_color_override("font_color", Color(236 / 255.0, 232 / 255.0, 220 / 255.0, 0.85))
		hint.add_theme_color_override("font_shadow_color", Color.BLACK)
		hint.add_theme_constant_override("shadow_outline_size", int(2 * _u))
		hint.name = "hint"
		add_child(hint)
		for it in items:
			_spot(it)
		if lv != null and leave_ok:
			var b := _button(String(lv.t), Color(1, 1, 1, 0.25))
			b.anchor_left = 0.3
			b.anchor_right = 0.7
			b.anchor_top = 0.91
			b.anchor_bottom = 0.91
			b.grow_vertical = Control.GROW_DIRECTION_BEGIN
			b.name = "leave"
			b.pressed.connect(func(): picked.emit(String(lv.go), lv.get("set"), ""))
			add_child(b)
	visible = true
	_t = 0.0


func close() -> void:
	visible = false
	for c in get_children():
		c.queue_free()
	spots.clear()


func _spot(it: Dictionary) -> void:
	var s: Dictionary = it.s
	var node := Control.new()
	node.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var ring := _Ring.new()
	ring.r = 4.5 * _u
	ring.w = 0.6 * _u
	ring.custom_minimum_size = Vector2(9, 9) * _u
	ring.size = ring.custom_minimum_size
	ring.position = -ring.size / 2.0
	ring.mouse_filter = Control.MOUSE_FILTER_STOP
	ring.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	node.add_child(ring)
	var lb := Label.new()
	lb.text = String(s.t)
	lb.add_theme_font_size_override("font_size", int(4 * _u))
	lb.add_theme_color_override("font_color", Color("#ece8dc"))
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0, 0, 0, 0.55)
	sb.set_corner_radius_all(int(1.5 * _u))
	sb.content_margin_left = 2 * _u
	sb.content_margin_right = 2 * _u
	sb.content_margin_top = 1 * _u
	sb.content_margin_bottom = 1 * _u
	lb.add_theme_stylebox_override("normal", sb)
	lb.mouse_filter = Control.MOUSE_FILTER_STOP
	lb.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	node.add_child(lb)
	add_child(node)
	var go := String(s.go)
	var cb := func(e: InputEvent):
		if e is InputEventMouseButton and e.pressed and e.button_index == MOUSE_BUTTON_LEFT:
			picked.emit(go, s.get("set"), go)
	ring.gui_input.connect(cb)
	lb.gui_input.connect(cb)
	ring.mouse_entered.connect(func(): ring.hover = true)
	ring.mouse_exited.connect(func(): ring.hover = false)
	spots.append({"node": node, "ring": ring, "label": lb, "at": s.get("at"), "xy": s.get("xy", [0.5, 0.5]), "up": false})


func _talk_list(lv, leave_ok: bool) -> void:
	var land := true
	# собеседник справа, темы слева — на затемнении
	var shade := TextureRect.new()
	var gt := GradientTexture2D.new()
	var gr := Gradient.new()
	gr.set_color(0, Color(0, 0, 0, 0.85))
	gr.set_color(1, Color(0, 0, 0, 0))
	gt.gradient = gr
	gt.width = 256
	gt.height = 4
	shade.texture = gt
	shade.stretch_mode = TextureRect.STRETCH_SCALE
	shade.anchor_right = 0.55
	shade.anchor_bottom = 1.0
	shade.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(shade)
	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", int(2.4 * _u))
	box.anchor_left = 0.05 if land else 0.07
	box.anchor_right = 0.45 if land else 0.93
	box.anchor_top = 1.0
	box.anchor_bottom = 1.0
	box.grow_vertical = Control.GROW_DIRECTION_BEGIN
	box.offset_bottom = -0.12 * get_viewport_rect().size.y
	if th.has("title"):
		var h := Label.new()
		h.text = String(th.title).to_upper()
		h.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		h.add_theme_font_size_override("font_size", int(5.2 * _u))
		h.add_theme_color_override("font_color", Color("#8fd0ff"))
		box.add_child(h)
	var n := 0
	for it in items:
		n += 1
		var s: Dictionary = it.s
		var b := _button("%d   %s" % [n, s.t], Color(143 / 255.0, 208 / 255.0, 1, 0.55))
		var go := String(s.go)
		b.pressed.connect(func(): picked.emit(go, s.get("set"), go))
		box.add_child(b)
	if lv != null and leave_ok:
		var b := _button("%d   %s" % [n + 1, lv.t], Color(1, 1, 1, 0.25))
		b.pressed.connect(func(): picked.emit(String(lv.go), lv.get("set"), ""))
		box.add_child(b)
	add_child(box)


func _button(text: String, border: Color) -> Button:
	var b := Button.new()
	b.text = text
	b.alignment = HORIZONTAL_ALIGNMENT_LEFT
	b.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	b.add_theme_font_size_override("font_size", int(4 * _u))
	for st in ["normal", "hover", "pressed", "focus"]:
		var sb := StyleBoxFlat.new()
		sb.bg_color = Color(6 / 255.0, 12 / 255.0, 20 / 255.0, 0.82 if st == "normal" else 0.95)
		sb.border_color = border if st == "normal" else Color("#ffd84a")
		sb.set_border_width_all(maxi(2, int(0.4 * _u)))
		sb.set_corner_radius_all(int(3 * _u))
		sb.content_margin_left = 3 * _u
		sb.content_margin_right = 3 * _u
		sb.content_margin_top = 3 * _u
		sb.content_margin_bottom = 3 * _u
		b.add_theme_stylebox_override(st, sb)
	return b


## Каждый кадр: кольца — на предметах (мировая точка → экран), подписи разложены один раз.
func layout(cam: Camera3D) -> void:
	if not visible or kind != "explore":
		return
	_t += get_process_delta_time()
	var vp := get_viewport_rect().size
	for sp in spots:
		var p: Vector2
		var at = sp.at
		if at is Array and at.size() == 3 and not cam.is_position_behind(Vector3(at[0], at[1], at[2])):
			p = cam.unproject_position(Vector3(at[0], at[1], at[2]))
		else:
			p = Vector2(float(sp.xy[0]), float(sp.xy[1])) * vp
		sp.node.position = p
		var lb: Label = sp.label
		var ls := lb.get_combined_minimum_size()
		lb.size = ls
		lb.position = Vector2(-ls.x / 2.0, (-4.5 * _u - 1.6 * _u - ls.y) if sp.up else 4.5 * _u + 1.6 * _u)
		sp.ring.phase = _t
	if not _laid and spots.size() > 0:
		_laid = true
		_lay_labels(vp)


## Подписи под кольцом или над ним — где меньше наезжает на чужие кольца, подписи, подсказку и кнопку (перебор всех раскладок).
func _lay_labels(vp: Vector2) -> void:
	var busy: Array[Rect2] = []
	for n in ["hint", "leave"]:
		var c := get_node_or_null(n) as Control
		if c:
			busy.append(c.get_global_rect())
	var best := 0
	var bc := INF
	var n := mini(spots.size(), 6)
	for m in 1 << n:
		var rects: Array[Rect2] = []
		for i in spots.size():
			var up := i < n and (m >> i & 1) == 1
			var ls: Vector2 = spots[i].label.size
			var p: Vector2 = spots[i].node.position
			rects.append(Rect2(p + Vector2(-ls.x / 2.0, (-6.1 * _u - ls.y) if up else 6.1 * _u), ls))
		var cost := 0.0
		for i in rects.size():
			var a := rects[i]
			if a.position.y < 0 or a.end.y > vp.y:
				cost += 1e6
			for j in spots.size():
				if j != i:
					cost += _ov(a, Rect2(spots[j].node.position - Vector2(4.5, 4.5) * _u, Vector2(9, 9) * _u))
			for b in busy:
				cost += _ov(a, b)
			for j in range(i + 1, rects.size()):
				cost += _ov(a, rects[j])
		if cost < bc - 0.5:
			bc = cost
			best = m
	for i in n:
		spots[i].up = (best >> i & 1) == 1


static func _ov(a: Rect2, b: Rect2) -> float:
	var r := a.intersection(b)
	return r.get_area() if a.intersects(b) else 0.0


## Кольцо точки обхода: золотое, светится, «дышит» (как @keyframes spot), под курсором — ярче и крупнее.
class _Ring:
	extends Control
	var r := 30.0
	var w := 4.0
	var phase := 0.0
	var hover := false

	func _process(_d: float) -> void:
		queue_redraw()

	func _draw() -> void:
		var c := size / 2.0
		var k := 1.18 if hover else lerpf(0.82, 1.08, 0.5 - 0.5 * cos(phase / 1.6 * TAU))
		var a := 1.0 if hover else lerpf(0.75, 1.0, 0.5 - 0.5 * cos(phase / 1.6 * TAU))
		var col := Color("#ffd84a") if hover else GOLD
		for i in 4:   # свечение
			draw_arc(c, r * k, 0, TAU, 48, Color(col, a * 0.12 * (4 - i)), w + i * w * 0.9, true)
		draw_arc(c, r * k - w / 2.0, 0, TAU, 48, Color(col, a), w, true)
