class_name MainMenu
extends Control
## Титул, настройки, концовки, пауза — перенос menu / settings / pauseMenu (engine.js). Титул: живой кадр (Лили за стеклом),
## медленный наезд, сверху и снизу темнее, логотип HUSH TILL SIX мигает, как больная лампа; кнопки; предупреждения (наушники, 18+).

signal new_game
signal continue_game
signal resume
signal to_menu
signal lang_changed
signal quit_game

const GOLD := Color("#f2c21b")
var u := 7.3
var page := ""             # title | settings | gallery | pause
var from := "title"
var _t := 0.0
var _logo: Label
var _box: Control


func _init() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	visible = false
	process_mode = Node.PROCESS_MODE_ALWAYS


func _clear() -> void:
	for c in get_children():
		c.queue_free()
	u = get_viewport_rect().size.y * 0.0068
	visible = true
	mouse_filter = Control.MOUSE_FILTER_STOP


func _process(delta: float) -> void:
	_t += delta
	if _logo and page == "title":   # мигание логотипа — больная лампа
		var k := fmod(_t + 1.0, 7.3)
		_logo.modulate.a = 0.25 if (k > 2.38 and k < 2.46) or (k > 2.55 and k < 2.6) else 1.0


func _shade(top: bool, a0: float, a1: float, frac: float) -> void:
	var g := TextureRect.new()
	var gt := GradientTexture2D.new()
	var gr := Gradient.new()
	gr.set_color(0, Color(0, 0, 0, a0))
	gr.set_color(1, Color(0, 0, 0, a1))
	gt.gradient = gr
	gt.fill_from = Vector2(0, 0)
	gt.fill_to = Vector2(0, 1)
	gt.width = 4
	gt.height = 128
	g.texture = gt
	g.stretch_mode = TextureRect.STRETCH_SCALE
	g.anchor_right = 1.0
	g.anchor_top = 0.0 if top else 1.0 - frac
	g.anchor_bottom = frac if top else 1.0
	g.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(g)


func _btn(text: String, cb: Callable, big := true) -> Button:
	var b := Button.new()
	b.text = text
	b.add_theme_font_size_override("font_size", int((4.6 if big else 3.6) * u))
	b.add_theme_color_override("font_color", Color("#ece8dc"))
	b.add_theme_color_override("font_hover_color", GOLD)
	b.add_theme_color_override("font_focus_color", GOLD)
	b.flat = true
	b.focus_mode = Control.FOCUS_ALL
	b.pressed.connect(cb)
	return b


func _label(text: String, sz: float, col := Color("#ece8dc")) -> Label:
	var l := Label.new()
	l.text = text
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.add_theme_font_size_override("font_size", int(sz * u))
	l.add_theme_color_override("font_color", col)
	l.add_theme_color_override("font_shadow_color", Color.BLACK)
	l.add_theme_constant_override("shadow_outline_size", int(1.2 * u))
	return l


func show_title(has_save: bool, desktop: bool) -> void:
	_clear()
	page = "title"
	_t = 0.0
	_shade(true, 0.88, 0.0, 0.5)
	_shade(false, 0.0, 0.9, 0.45)
	var lf: Font = load("res://assets/fonts/ns-logo.woff") if ResourceLoader.exists("res://assets/fonts/ns-logo.woff") else null
	_logo = Label.new()
	_logo.text = "HUSH\nTILL SIX"
	_logo.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	if lf:
		_logo.add_theme_font_override("font", lf)
	_logo.add_theme_font_size_override("font_size", int(15 * u))
	_logo.add_theme_constant_override("line_spacing", int(-4 * u))
	_logo.add_theme_color_override("font_color", Color("#e8e2d2"))
	_logo.add_theme_color_override("font_shadow_color", Color(0.7, 0.05, 0.05, 0.6))
	_logo.add_theme_constant_override("shadow_outline_size", int(3 * u))
	_logo.anchor_right = 1.0
	_logo.anchor_top = 0.06
	add_child(_logo)
	var tag := _label(StoryDB.t("Не отзывайся · Не открывай · Дождись шести"), 3.2, Color(1, 1, 1, 0.75))
	tag.anchor_right = 1.0
	tag.anchor_top = 0.38
	add_child(tag)
	var nav := VBoxContainer.new()
	nav.anchor_left = 0.5
	nav.anchor_right = 0.5
	nav.anchor_top = 1.0
	nav.anchor_bottom = 1.0
	nav.grow_horizontal = Control.GROW_DIRECTION_BOTH
	nav.grow_vertical = Control.GROW_DIRECTION_BEGIN
	nav.offset_bottom = -0.16 * get_viewport_rect().size.y
	nav.add_theme_constant_override("separation", int(0.6 * u))
	var first: Button = null
	if has_save:
		first = _btn(StoryDB.t("Продолжить"), func(): continue_game.emit())
		nav.add_child(first)
	var nb := _btn(StoryDB.t("Начать заново" if has_save else "Начать смену"), func(): new_game.emit())
	nav.add_child(nb)
	if first == null:
		first = nb
	nav.add_child(_btn(StoryDB.t("Концовки"), show_gallery))
	nav.add_child(_btn(StoryDB.t("Настройки"), func(): show_settings("title")))
	if desktop:
		nav.add_child(_btn(StoryDB.t("Выйти из игры"), func(): quit_game.emit()))
	add_child(nav)
	var notes := VBoxContainer.new()
	notes.anchor_right = 1.0
	notes.anchor_top = 1.0
	notes.anchor_bottom = 1.0
	notes.grow_vertical = Control.GROW_DIRECTION_BEGIN
	notes.offset_bottom = -2 * u
	notes.add_child(_label("🎧  " + StoryDB.t("Лучше в наушниках · в игре есть вспышки света"), 2.4, Color(1, 1, 1, 0.6)))
	notes.add_child(_label("18+  " + StoryDB.t("Жестокость, кровь и сцены насилия"), 2.4, Color(1, 0.4, 0.4, 0.75)))
	add_child(notes)
	first.grab_focus.call_deferred()


func show_gallery() -> void:
	_clear()
	page = "gallery"
	var bg := ColorRect.new()
	bg.color = Color(0, 0, 0, 0.82)
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(bg)
	var box := VBoxContainer.new()
	box.set_anchors_preset(Control.PRESET_CENTER)
	box.grow_horizontal = Control.GROW_DIRECTION_BOTH
	box.grow_vertical = Control.GROW_DIRECTION_BOTH
	box.add_theme_constant_override("separation", int(1.6 * u))
	box.add_child(_label(StoryDB.t("КОНЦОВКИ"), 7))
	var got: Dictionary = Save.get_v("endings", {})
	for k in StoryDB.story.endings:
		box.add_child(_label(("✓  " + StoryDB.t(String(StoryDB.story.endings[k]))) if got.has(k) else "·  ???", 4, Color("#ece8dc") if got.has(k) else Color(1, 1, 1, 0.4)))
	var ov: Dictionary = Save.get_v("overs", {})
	box.add_child(_label(StoryDB.t("Смертей найдено: {c} из {d}").replace("{c}", str(ov.size())).replace("{d}", str(StoryDB.story.overs.size())), 3, Color(1, 1, 1, 0.6)))
	var b := _btn(StoryDB.t("Назад"), func(): to_menu.emit())
	box.add_child(b)
	add_child(box)
	b.grab_focus.call_deferred()


## Настройки: язык, громкости, озвучка, «меньше вспышек», «реплики сами», графика, весь экран.
func show_settings(p_from: String) -> void:
	_clear()
	page = "settings"
	from = p_from
	var bg := ColorRect.new()
	bg.color = Color(0, 0, 0, 0.85)
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(bg)
	var box := VBoxContainer.new()
	box.set_anchors_preset(Control.PRESET_CENTER)
	box.grow_horizontal = Control.GROW_DIRECTION_BOTH
	box.grow_vertical = Control.GROW_DIRECTION_BOTH
	box.custom_minimum_size = Vector2(120 * u, 0)
	box.add_theme_constant_override("separation", int(1.2 * u))
	box.add_child(_label(StoryDB.t("НАСТРОЙКИ"), 6))
	var names: Dictionary = StoryDB.i18n.get("names", {})
	var langs: Array = StoryDB.i18n.langs
	var cur := StoryDB.lang
	var lb := _btn(StoryDB.t("Язык") + ":  " + String(names.get(cur, cur)).to_upper(), func():
		var i := (langs.find(cur) + 1) % langs.size()
		Save.set_setting("lang", langs[i])
		StoryDB.setup(langs[i])
		lang_changed.emit()
		show_settings(from), false)
	box.add_child(lb)
	for v in [["all", "Громкость"], ["mus", "Музыка"], ["voice", "Голоса"], ["sfx", "Звуки"], ["amb", "Дождь и фон"]]:
		var row := HBoxContainer.new()
		var l := _label(StoryDB.t(v[1]), 3.2)
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
		l.custom_minimum_size = Vector2(40 * u, 0)
		row.add_child(l)
		var s := HSlider.new()
		s.min_value = 0
		s.max_value = 100
		s.step = 5
		s.value = float(Save.setting("vol").get(v[0], 1.0)) * 100.0
		s.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		s.custom_minimum_size = Vector2(60 * u, 4 * u)
		var key: String = v[0]
		s.value_changed.connect(func(x):
			var vol: Dictionary = Save.setting("vol")
			vol[key] = x / 100.0
			Save.set_setting("vol", vol)
			Save.apply_volumes())
		row.add_child(s)
		box.add_child(row)
	for t in [["voice", "Озвучка"], ["calm", "Меньше вспышек"], ["auto", "Реплики сами"], ["full", "Во весь экран"]]:
		var key: String = t[0]
		var nm: String = t[1]
		box.add_child(_btn(StoryDB.t(nm) + ":  " + StoryDB.t("ВКЛ" if Save.setting(key) else "ВЫКЛ"), func():
			Save.set_setting(key, not Save.setting(key))
			if key == "full":
				DisplayServer.window_set_mode(DisplayServer.WINDOW_MODE_FULLSCREEN if Save.setting("full") else DisplayServer.WINDOW_MODE_WINDOWED)
			show_settings(from), false))
	var gfx := String(Save.setting("gfx", "high"))
	box.add_child(_btn(StoryDB.t("Графика") + ":  " + StoryDB.t({"high": "ВЫСОКАЯ", "mid": "СРЕДНЯЯ", "low": "НИЗКАЯ"}.get(gfx, "ВЫСОКАЯ")), func():
		Save.set_setting("gfx", {"high": "mid", "mid": "low", "low": "high"}[gfx])
		Graphics.apply(get_viewport())
		show_settings(from), false))
	var back := _btn(StoryDB.t("Назад"), func():
		if from == "pause":
			show_pause()
		else:
			to_menu.emit())
	box.add_child(back)
	add_child(box)
	back.grab_focus.call_deferred()


func show_pause() -> void:
	_clear()
	page = "pause"
	var bg := ColorRect.new()
	bg.color = Color(0, 0, 0, 0.7)
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(bg)
	var box := VBoxContainer.new()
	box.set_anchors_preset(Control.PRESET_CENTER)
	box.grow_horizontal = Control.GROW_DIRECTION_BOTH
	box.grow_vertical = Control.GROW_DIRECTION_BOTH
	box.add_theme_constant_override("separation", int(1.2 * u))
	box.add_child(_label(StoryDB.t("ПАУЗА"), 7))
	var r := _btn(StoryDB.t("Продолжить"), func(): resume.emit())
	box.add_child(r)
	box.add_child(_btn(StoryDB.t("Настройки"), func(): show_settings("pause")))
	box.add_child(_btn(StoryDB.t("Меню"), func(): to_menu.emit()))
	add_child(box)
	r.grab_focus.call_deferred()


func close() -> void:
	for c in get_children():
		c.queue_free()
	visible = false
	page = ""
	mouse_filter = Control.MOUSE_FILTER_IGNORE
