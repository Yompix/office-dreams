class_name EndScreens
extends Control
## Экраны конца — перенос finale / endScreen / overScreen (engine.js 1456–1530):
## название концовки по буквам поверх последнего кадра; журнал дежурств (время, часы смены, записи, решения, штамп концовки,
## найденные концовки; «к выбору», «заново», «меню»); экран смерти («Ты не дожил до утра», «Попробовать снова», сколько смертей найдено).

signal next_pressed
signal retry_pressed
signal restart_pressed
signal menu_pressed
signal back_pressed

const RED := Color("#a3141c")
var u := 7.3
var _title := ""
var _t := 0.0
var _mode := ""
var _title_lbl: Label
var _sub_lbl: Label
var _tap_lbl: Label
var _fade: ColorRect


func _init() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	visible = false


func _clear() -> void:
	for c in get_children():
		c.queue_free()
	u = get_viewport_rect().size.y * 0.0068
	_t = 0.0
	visible = true


func hide_all() -> void:
	_clear()
	visible = false
	_mode = ""


func _process(delta: float) -> void:
	if not visible:
		return
	_t += delta
	if _mode == "fin":   # буквы названия проявляются по одной, потом строка и «нажми»
		_title_lbl.visible_characters = int(clampf((_t - 0.5) / 0.08, 0.0, float(_title.length())))
		_sub_lbl.modulate.a = clampf((_t - 2.4) / 1.6, 0.0, 1.0)
		_tap_lbl.modulate.a = clampf((_t - 4.5) / 0.6, 0.0, 1.0) * (0.55 + 0.45 * sin(_t * 2.6))
	elif _mode == "over" and _fade:
		_fade.modulate.a = 1.0 - clampf((_t - 0.8) / 1.0, 0.0, 1.0)


func _label(text: String, sz: float, col := Color("#ece8dc")) -> Label:
	var l := Label.new()
	l.text = text
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.add_theme_font_size_override("font_size", int(sz * u))
	l.add_theme_color_override("font_color", col)
	l.add_theme_color_override("font_shadow_color", Color.BLACK)
	l.add_theme_constant_override("shadow_outline_size", int(1.5 * u))
	return l


func _btn(text: String, main := true) -> Button:
	var b := Button.new()
	b.text = text
	b.add_theme_font_size_override("font_size", int(4 * u))
	b.custom_minimum_size = Vector2(70 * u, 11 * u)
	for s in ["normal", "hover", "pressed", "focus"]:
		var sb := StyleBoxFlat.new()
		sb.bg_color = (Color(0.62, 0.06, 0.08, 0.9) if main else Color(0.05, 0.05, 0.06, 0.7)) if s == "normal" else (Color(0.8, 0.1, 0.12, 0.95) if main else Color(0.15, 0.15, 0.17, 0.85))
		sb.border_color = Color(1, 1, 1, 0.25)
		sb.set_border_width_all(maxi(1, int(0.3 * u)))
		sb.set_corner_radius_all(int(2 * u))
		b.add_theme_stylebox_override(s, sb)
	return b


## Название концовки по буквам внизу кадра (кадр идёт дальше и темнеет); клик — дальше.
func show_fin(title: String, sub: String) -> void:
	_clear()
	_mode = "fin"
	_title = title
	mouse_filter = Control.MOUSE_FILTER_STOP
	var g := TextureRect.new()   # снизу темнее — под название
	var gt := GradientTexture2D.new()
	var gr := Gradient.new()
	gr.offsets = PackedFloat32Array([0.0, 0.38, 0.7, 1.0])
	gr.colors = PackedColorArray([Color(0, 0, 0, 0), Color(0, 0, 0, 0), Color(0, 0, 0, 0.55), Color(0, 0, 0, 0.9)])
	gt.gradient = gr
	gt.fill_from = Vector2(0, 0)
	gt.fill_to = Vector2(0, 1)
	gt.width = 4
	gt.height = 256
	g.texture = gt
	g.stretch_mode = TextureRect.STRETCH_SCALE
	g.set_anchors_preset(Control.PRESET_FULL_RECT)
	g.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(g)
	var box := VBoxContainer.new()
	box.anchor_left = 0.1
	box.anchor_right = 0.9
	box.anchor_top = 1.0
	box.anchor_bottom = 1.0
	box.grow_vertical = Control.GROW_DIRECTION_BEGIN
	box.offset_bottom = -0.09 * get_viewport_rect().size.y
	box.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_title_lbl = _label(title, 13)
	_title_lbl.visible_characters = 0
	box.add_child(_title_lbl)
	_sub_lbl = _label(sub, 3.6)
	_sub_lbl.modulate.a = 0.0
	box.add_child(_sub_lbl)
	_tap_lbl = _label(StoryDB.t("Нажми, чтобы продолжить").to_upper(), 2.6)
	_tap_lbl.modulate.a = 0.0
	box.add_child(_tap_lbl)
	add_child(box)


func _gui_input(e: InputEvent) -> void:
	if _mode == "fin" and e is InputEventMouseButton and e.pressed and _t > 1.0:
		next_pressed.emit()


func _unhandled_input(e: InputEvent) -> void:
	if _mode == "fin" and e.is_action_pressed("ui_accept") and _t > 1.0:
		next_pressed.emit()
	elif _mode == "over" and _t > 0.8:
		if e.is_action_pressed("ui_accept"):
			retry_pressed.emit()
		elif e.is_action_pressed("ui_cancel"):
			menu_pressed.emit()


## Экран смерти: обрыв в черноту, секунда темноты, потом название; за ним проступает застывший последний кадр.
func show_over(title: String, sub: String, found: int, total: int) -> void:
	_clear()
	_mode = "over"
	mouse_filter = Control.MOUSE_FILTER_STOP
	var bg := ColorRect.new()
	bg.color = Color(0.08, 0, 0.01, 0.6)
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(bg)
	var box := VBoxContainer.new()
	box.set_anchors_preset(Control.PRESET_CENTER)
	box.grow_horizontal = Control.GROW_DIRECTION_BOTH
	box.grow_vertical = Control.GROW_DIRECTION_BOTH
	box.add_theme_constant_override("separation", int(2.4 * u))
	box.custom_minimum_size = Vector2(120 * u, 0)
	box.add_child(_label(title, 9, Color("#e8262e")))
	box.add_child(_label(sub + "\n" + StoryDB.t("Ты не дожил до утра."), 3.6))
	var r := _btn(StoryDB.t("Попробовать снова"))
	r.pressed.connect(func(): retry_pressed.emit())
	r.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	box.add_child(r)
	var m := _btn(StoryDB.t("Меню"), false)
	m.pressed.connect(func(): menu_pressed.emit())
	m.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	box.add_child(m)
	var c := _label(StoryDB.t("Смертей найдено: {c} из {d}").replace("{c}", str(found)).replace("{d}", str(total)), 2.6)
	c.modulate.a = 0.6
	box.add_child(c)
	add_child(box)
	_fade = ColorRect.new()   # сначала чернота
	_fade.color = Color.BLACK
	_fade.set_anchors_preset(Control.PRESET_FULL_RECT)
	_fade.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_fade)
	r.grab_focus.call_deferred()


## Журнал дежурств: страница журнала охраны (строки ложатся по очереди), штамп концовки, найденные концовки, кнопки.
func show_journal(rows: Array, picks: Array, end_line: String, end_sub: String, stamp: String, fresh: bool, gallery: Array, fork_q: String) -> void:
	_clear()
	_mode = "jr"
	mouse_filter = Control.MOUSE_FILTER_STOP
	var bg := ColorRect.new()
	bg.color = Color(0.02, 0.02, 0.025, 0.92)
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(bg)
	var row := HBoxContainer.new()
	row.set_anchors_preset(Control.PRESET_CENTER)
	row.grow_horizontal = Control.GROW_DIRECTION_BOTH
	row.grow_vertical = Control.GROW_DIRECTION_BOTH
	row.add_theme_constant_override("separation", int(6 * u))
	# страница
	var page := PanelContainer.new()
	var ps := StyleBoxFlat.new()
	ps.bg_color = Color("#e9e2cf")
	ps.set_corner_radius_all(int(1 * u))
	ps.content_margin_left = 10 * u
	ps.content_margin_right = 5 * u
	ps.content_margin_top = 5 * u
	ps.content_margin_bottom = 8 * u
	page.add_theme_stylebox_override("panel", ps)
	page.custom_minimum_size = Vector2(96 * u, 0)
	var pv := VBoxContainer.new()
	pv.add_theme_constant_override("separation", int(1.2 * u))
	var ink := Color("#1f2633")
	var hd := _label(StoryDB.t("ЖУРНАЛ ДЕЖУРСТВ"), 4.8, ink)
	hd.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
	hd.add_theme_constant_override("shadow_outline_size", 0)
	pv.add_child(hd)
	var hs := _label(StoryDB.t("БЦ «Горизонт» · пост охраны · ночь 28→29 октября"), 3.0, Color(ink, 0.7))
	hs.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
	hs.add_theme_constant_override("shadow_outline_size", 0)
	pv.add_child(hs)
	for r in rows:
		var h := HBoxContainer.new()
		var a := _label(StoryDB.t(String(r[0])), 3.3, ink)
		a.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
		a.add_theme_constant_override("shadow_outline_size", 0)
		a.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		h.add_child(a)
		var b := _label(str(r[1]), 3.5, ink)
		b.autowrap_mode = TextServer.AUTOWRAP_OFF
		b.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
		b.add_theme_constant_override("shadow_outline_size", 0)
		b.add_theme_font_override("font", CctvHud.font())
		h.add_child(b)
		pv.add_child(h)
	if not picks.is_empty():
		var ps2 := _label(StoryDB.t("Решения за ночь"), 3.2, Color(ink, 0.8))
		ps2.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
		ps2.add_theme_constant_override("shadow_outline_size", 0)
		pv.add_child(ps2)
		for i in picks.size():
			var pl := _label("%d. %s" % [i + 1, picks[i]], 3.0, ink)
			pl.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
			pl.add_theme_constant_override("shadow_outline_size", 0)
			pv.add_child(pl)
	var el := _label(end_line, 3.8, RED)
	el.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
	el.add_theme_constant_override("shadow_outline_size", 0)
	pv.add_child(el)
	var es := _label(end_sub, 3.0, Color(RED, 0.85))
	es.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
	es.add_theme_constant_override("shadow_outline_size", 0)
	pv.add_child(es)
	var st := _label(stamp.to_upper() + ("\n" + StoryDB.t("новая концовка") if fresh else ""), 5.0, RED)
	st.add_theme_constant_override("shadow_outline_size", 0)
	st.rotation_degrees = -8
	pv.add_child(st)
	page.add_child(pv)
	row.add_child(page)
	# сбоку: найденные концовки и кнопки
	var side := VBoxContainer.new()
	side.add_theme_constant_override("separation", int(2 * u))
	side.add_child(_label(StoryDB.t("Концовки"), 4.0))
	for gl in gallery:
		var l := _label(("✓ " if gl[1] else "· ") + (String(gl[0]) if gl[1] else "???"), 3.2, Color("#ece8dc") if gl[1] else Color(1, 1, 1, 0.4))
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
		side.add_child(l)
	if fork_q != "":
		var bb := _btn(StoryDB.t("Вернуться к выбору") + "\n" + fork_q)
		bb.pressed.connect(func(): back_pressed.emit())
		side.add_child(bb)
	var again := _btn(StoryDB.t("Начать заново"), fork_q == "")
	again.pressed.connect(func(): restart_pressed.emit())
	side.add_child(again)
	var menu := _btn(StoryDB.t("Меню"), false)
	menu.pressed.connect(func(): menu_pressed.emit())
	side.add_child(menu)
	row.add_child(side)
	add_child(row)
