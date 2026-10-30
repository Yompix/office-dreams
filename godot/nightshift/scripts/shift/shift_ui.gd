class_name ShiftUI
extends Control
## Интерфейс смены — перенос shiftUI / shiftPop / shiftToast / shiftTask / tutorial (engine.js, стили .sh в index.html):
## камеры внизу, справа «Записать», «Пульт», «Журнал», «?»; сверху задача и подсказка; посередине окно (запись, пульт, журнал,
## звонок, СМС, «держи»); карточка диспетчера Веры с кольцом вокруг того, что нажать. Единица u — 0,68 % высоты кадра.

signal cam_pressed(c: String)
signal log_pressed
signal panel_pressed
signal book_pressed
signal help_pressed
signal tut_skip
signal tut_go

const GOLD := Color("#f2c21b")
const TXT := Color("#ece8dc")
const CAM_NAMES := {"01": "ВХОД", "02": "ХОЛЛ", "03": "ЛИФТЫ", "04": "ЛЕСТНИЦА", "07": "ЭТАЖ 7", "10": "ЭТАЖ 10", "m2": "ПОДВАЛ", "13": ""}

var u := 7.3
var cam_box: HBoxContainer
var act_box: VBoxContainer
var task_lbl: RichTextLabel
var toast_lbl: Label
var pop: PanelContainer
var pop_box: VBoxContainer
var tut_card: PanelContainer
var tut_do: Label
var tut_go_btn: Button
var ring: Panel
var cam_btns := {}
var _toast_t := 0.0
var _ring_target = null
var _theme: Theme


func _init() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	visible = false


static func cam_no(c: String) -> String:
	return "−2" if c == "m2" else c


static func cam_label(c: String) -> String:
	var n := String(CAM_NAMES.get(c, ""))
	return "CAM " + cam_no(c) + ("  " + StoryDB.t(n) if n != "" else "")


func build(cams: Array, cur: String, theme_: Theme) -> void:
	for ch in get_children():
		ch.queue_free()
	cam_btns.clear()
	_theme = theme_
	theme = theme_
	u = get_viewport_rect().size.y * 0.0068
	# камеры внизу по центру
	cam_box = HBoxContainer.new()
	cam_box.add_theme_constant_override("separation", int(1.4 * u))
	cam_box.anchor_left = 0.5
	cam_box.anchor_right = 0.5
	cam_box.anchor_top = 1.0
	cam_box.anchor_bottom = 1.0
	cam_box.grow_horizontal = Control.GROW_DIRECTION_BOTH
	cam_box.grow_vertical = Control.GROW_DIRECTION_BEGIN
	cam_box.offset_bottom = -3 * u
	for c in cams:
		var b := btn("%s\n%s" % [cam_no(c), StoryDB.t(CAM_NAMES.get(c, "???")).to_upper()], 2.6, Color(0.92, 0.94, 0.92, 0.28))
		b.custom_minimum_size = Vector2(13 * u, 0)
		if c == "13":
			b.add_theme_color_override("font_color", Color("#e8262e"))
		var cc := String(c)
		b.pressed.connect(func(): cam_pressed.emit(cc))
		cam_btns[cc] = b
		cam_box.add_child(b)
	add_child(cam_box)
	set_cam(cur)
	# действия справа
	act_box = VBoxContainer.new()
	act_box.add_theme_constant_override("separation", int(2 * u))
	act_box.anchor_left = 1.0
	act_box.anchor_right = 1.0
	act_box.anchor_top = 0.5
	act_box.anchor_bottom = 0.5
	act_box.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	act_box.grow_vertical = Control.GROW_DIRECTION_BOTH
	act_box.offset_right = -3 * u
	for a in [["Записать", log_pressed, Color(GOLD, 0.7), "shLog"], ["Пульт", panel_pressed, Color(0.92, 0.94, 0.92, 0.28), "shPanel"],
			["Журнал", book_pressed, Color(0.92, 0.94, 0.92, 0.28), "shBook"]]:
		var b := btn(StoryDB.t(a[0]).to_upper(), 3.6, a[2])
		b.custom_minimum_size = Vector2(24 * u, 10 * u)
		b.name = a[3]
		var sig: Signal = a[1]
		b.pressed.connect(func(): sig.emit())
		act_box.add_child(b)
	var q := btn("?", 4.6, Color(0.92, 0.94, 0.92, 0.28))
	q.custom_minimum_size = Vector2(10 * u, 10 * u)
	q.size_flags_horizontal = Control.SIZE_SHRINK_END
	q.pressed.connect(func(): help_pressed.emit())
	act_box.add_child(q)
	add_child(act_box)
	# задача и подсказка
	task_lbl = RichTextLabel.new()
	task_lbl.bbcode_enabled = true
	task_lbl.fit_content = true
	task_lbl.autowrap_mode = TextServer.AUTOWRAP_OFF
	task_lbl.anchor_left = 0.5
	task_lbl.anchor_right = 0.5
	task_lbl.grow_horizontal = Control.GROW_DIRECTION_BOTH
	task_lbl.offset_top = 4 * u
	task_lbl.add_theme_font_size_override("normal_font_size", int(3.2 * u))
	task_lbl.add_theme_stylebox_override("normal", _box(Color(0.04, 0.05, 0.055, 0.75), Color(0, 0, 0, 0), 0, 0, [4, 1.6, 4, 1.6]))
	task_lbl.mouse_filter = Control.MOUSE_FILTER_IGNORE
	task_lbl.visible = false
	add_child(task_lbl)
	toast_lbl = Label.new()
	toast_lbl.anchor_left = 0.5
	toast_lbl.anchor_right = 0.5
	toast_lbl.grow_horizontal = Control.GROW_DIRECTION_BOTH
	toast_lbl.offset_top = 13 * u
	toast_lbl.add_theme_font_size_override("font_size", int(3.4 * u))
	toast_lbl.add_theme_color_override("font_color", TXT)
	toast_lbl.add_theme_color_override("font_shadow_color", Color.BLACK)
	toast_lbl.add_theme_constant_override("shadow_outline_size", int(2 * u))
	toast_lbl.modulate.a = 0.0
	add_child(toast_lbl)
	# окно посередине
	pop = PanelContainer.new()
	pop.add_theme_stylebox_override("panel", _box(Color(0.03, 0.035, 0.043, 0.94), Color(0.92, 0.94, 0.92, 0.25), 0.3, 2.4, [4, 4, 4, 4]))
	pop.anchor_left = 0.5
	pop.anchor_right = 0.5
	pop.anchor_top = 0.46
	pop.anchor_bottom = 0.46
	pop.grow_horizontal = Control.GROW_DIRECTION_BOTH
	pop.grow_vertical = Control.GROW_DIRECTION_BOTH
	pop.custom_minimum_size = Vector2(92 * u, 0)
	pop_box = VBoxContainer.new()
	pop_box.add_theme_constant_override("separation", int(1.6 * u))
	pop.add_child(pop_box)
	pop.visible = false
	add_child(pop)
	# Вера
	tut_card = PanelContainer.new()
	tut_card.add_theme_stylebox_override("panel", _box(Color(0.024, 0.055, 0.078, 0.9), Color(127 / 255.0, 212 / 255.0, 1, 0.55), 0.3, 2.2, [3.4, 2.4, 3.4, 2.4]))
	tut_card.anchor_left = 0.5
	tut_card.anchor_right = 0.5
	tut_card.grow_horizontal = Control.GROW_DIRECTION_BOTH
	tut_card.offset_top = 3 * u
	tut_card.custom_minimum_size = Vector2(110 * u, 0)
	var tv := VBoxContainer.new()
	var hd := Label.new()
	hd.text = StoryDB.t("ПУЛЬТ «ЩИТ» · ДИСПЕТЧЕР ВЕРА")
	hd.add_theme_font_size_override("font_size", int(2.4 * u))
	hd.add_theme_color_override("font_color", Color("#7fd4ff"))
	tv.add_child(hd)
	tut_do = Label.new()
	tut_do.add_theme_font_size_override("font_size", int(3.4 * u))
	tut_do.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	tv.add_child(tut_do)
	var row := HBoxContainer.new()
	row.alignment = BoxContainer.ALIGNMENT_CENTER
	tut_go_btn = btn(StoryDB.t("Продолжить"), 3.2, GOLD)
	tut_go_btn.add_theme_stylebox_override("normal", _box(GOLD, GOLD, 0, 1.4, [6, 1.3, 6, 1.3]))
	tut_go_btn.add_theme_color_override("font_color", Color("#101010"))
	tut_go_btn.visible = false
	tut_go_btn.pressed.connect(func(): tut_go.emit())
	row.add_child(tut_go_btn)
	var sk := btn(StoryDB.t("Пропустить обучение"), 2.4, Color(0.92, 0.94, 0.92, 0.28))
	sk.modulate.a = 0.75
	sk.pressed.connect(func(): tut_skip.emit())
	row.add_child(sk)
	tv.add_child(row)
	tut_card.add_child(tv)
	tut_card.visible = false
	add_child(tut_card)
	ring = Panel.new()
	ring.add_theme_stylebox_override("panel", _box(Color(0, 0, 0, 0), GOLD, 0.6, 2, [0, 0, 0, 0]))
	ring.mouse_filter = Control.MOUSE_FILTER_IGNORE
	ring.visible = false
	add_child(ring)
	visible = true


func set_cam(c: String) -> void:
	for k in cam_btns:
		var b: Button = cam_btns[k]
		var on: bool = k == c
		b.add_theme_stylebox_override("normal", _box(Color(40 / 255.0, 32 / 255.0, 6 / 255.0, 0.8) if on else Color(10 / 255.0, 12 / 255.0, 14 / 255.0, 0.72),
			GOLD if on else (Color(0.9, 0.16, 0.2, 0.6) if k == "13" else Color(0.92, 0.94, 0.92, 0.28)), 0.3, 1.6, [0, 1.6, 0, 1.6]))
		if k != "13":
			b.add_theme_color_override("font_color", GOLD if on else TXT)


func _process(delta: float) -> void:
	if _toast_t > 0.0:
		_toast_t -= delta
		toast_lbl.modulate.a = minf(1.0, toast_lbl.modulate.a + delta / 0.4) if _toast_t > 0.4 else maxf(0.0, _toast_t / 0.4)
	if ring and ring.visible:
		_place_ring()
		ring.modulate.a = 0.6 + 0.4 * absf(sin(Time.get_ticks_msec() / 1000.0 * PI / 1.1))


func toast(s: String) -> void:
	if s == "":
		return
	toast_lbl.text = StoryDB.t(s)
	_toast_t = 4.2


func task(text: String) -> void:
	task_lbl.visible = text != ""
	if text != "":
		task_lbl.text = "[color=#f2c21b]%s[/color]  %s" % [StoryDB.t("ЗАДАЧА"), StoryDB.t(text)]


## Окно посередине: заголовок, текст и содержимое (кнопки строит вызывающий через add_*). no_x — без «Закрыть».
func pop_open(title: String, text := "", no_x := false) -> void:
	for ch in pop_box.get_children():
		ch.queue_free()
	var h := Label.new()
	h.text = StoryDB.t(title).to_upper()
	h.add_theme_font_size_override("font_size", int(4.6 * u))
	pop_box.add_child(h)
	if text != "":
		var p := Label.new()
		p.text = text
		p.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		p.add_theme_font_size_override("font_size", int(3.2 * u))
		p.modulate.a = 0.8
		pop_box.add_child(p)
	pop.visible = true
	if not no_x:
		call_deferred("_add_close")


func _add_close() -> void:
	var x := btn(StoryDB.t("Закрыть").to_upper(), 3.0, Color(0.92, 0.94, 0.92, 0.28))
	x.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	x.custom_minimum_size = Vector2(30 * u, 0)
	x.pressed.connect(pop_close)
	pop_box.add_child(x)


func pop_close() -> void:
	pop.visible = false
	for ch in pop_box.get_children():
		ch.queue_free()


## Сетка кнопок в окне (cols в ряд); каждая — (текст, функция).
func pop_grid(items: Array, cols := 4, sz := 3.2) -> GridContainer:
	var g := GridContainer.new()
	g.columns = cols
	g.add_theme_constant_override("h_separation", int(1.6 * u))
	g.add_theme_constant_override("v_separation", int(1.6 * u))
	for it in items:
		var b := btn(String(it[0]).to_upper(), sz, Color(0.92, 0.94, 0.92, 0.28))
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		b.custom_minimum_size = Vector2(0, 10 * u)
		var f: Callable = it[1]
		b.pressed.connect(f)
		if it.size() > 2 and it[2]:
			b.add_theme_stylebox_override("normal", _box(Color(GOLD, 0.85), GOLD, 0.3, 1.6, [1, 2.6, 1, 2.6]))
			b.add_theme_color_override("font_color", Color("#111111"))
		g.add_child(b)
	pop_box.add_child(g)
	return g


func pop_list(lines: Array) -> void:
	var l := RichTextLabel.new()
	l.bbcode_enabled = true
	l.fit_content = true
	l.add_theme_font_size_override("normal_font_size", int(3.3 * u))
	l.add_theme_color_override("default_color", Color("#e6dfca"))
	l.custom_minimum_size = Vector2(80 * u, 0)
	var s := ""
	for x in lines:
		s += "•  " + String(x) + "\n"
	l.text = s
	var sc := ScrollContainer.new()
	sc.custom_minimum_size = Vector2(0, mini(int(lines.size() * 6 * u) + 20, int(get_viewport_rect().size.y * 0.5)))
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	sc.add_child(l)
	pop_box.add_child(sc)


func btn(text: String, sz: float, border: Color) -> Button:
	var b := Button.new()
	b.text = text
	b.add_theme_font_size_override("font_size", int(sz * u))
	b.add_theme_color_override("font_color", TXT)
	b.add_theme_stylebox_override("normal", _box(Color(10 / 255.0, 12 / 255.0, 14 / 255.0, 0.72), border, 0.3, 1.6, [1, 1.6, 1, 1.6]))
	b.add_theme_stylebox_override("hover", _box(Color(30 / 255.0, 32 / 255.0, 36 / 255.0, 0.85), Color("#ffd84a"), 0.3, 1.6, [1, 1.6, 1, 1.6]))
	b.add_theme_stylebox_override("pressed", _box(Color(224 / 255.0, 48 / 255.0, 58 / 255.0, 0.35), border, 0.3, 1.6, [1, 1.6, 1, 1.6]))
	b.add_theme_stylebox_override("focus", StyleBoxEmpty.new())
	return b


func _box(bg: Color, border: Color, bw: float, rad: float, pad: Array) -> StyleBoxFlat:
	var sb := StyleBoxFlat.new()
	sb.bg_color = bg
	sb.border_color = border
	sb.set_border_width_all(maxi(1, int(bw * u)) if bw > 0 else 0)
	sb.set_corner_radius_all(int(rad * u))
	sb.content_margin_left = pad[0] * u
	sb.content_margin_top = pad[1] * u
	sb.content_margin_right = pad[2] * u
	sb.content_margin_bottom = pad[3] * u
	return sb


## Карточка Веры: что сделать; Продолжить — когда договорила (go), разговор идёт (talk).
func tut_show(on: bool, do_text := "", go := false) -> void:
	tut_card.visible = on
	tut_do.text = StoryDB.t(do_text) if do_text != "" else ""
	tut_go_btn.visible = go


## Кольцо вокруг того, что нажать: имя кнопки (shLog, shPanel, shBook, cam:03, go) или прямоугольник надписей камеры [x, y, w, h].
func ring_at(target) -> void:
	_ring_target = target
	ring.visible = target != null


func _place_ring() -> void:
	var r := Rect2()
	var t = _ring_target
	if t is Array:
		var us := get_viewport_rect().size.y / 1920.0
		r = Rect2(t[0] * us, t[1] * us, t[2] * us, t[3] * us)
	elif t is String:
		var n: Control = null
		if t.begins_with("cam:"):
			n = cam_btns.get(t.substr(4))
		elif t == "go":
			n = tut_go_btn
		else:
			n = act_box.get_node_or_null(t)
		if n == null or not n.is_visible_in_tree():
			ring.visible = false
			return
		r = n.get_global_rect()
	ring.visible = true
	ring.position = r.position - Vector2(6, 6)
	ring.size = r.size + Vector2(12, 12)
