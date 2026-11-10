class_name UI
extends RefCounted
## Дизайн-система LANTERN: цвета, отступы, шрифты, радиусы и готовые элементы.
## Все размеры — в dp: на телефоне окно масштабируется так, что 1 единица = 1 dp (см. main.gd),
## поэтому 48 здесь — это 48 dp, удобная для пальца высота кнопки на любом экране.
## Анимации — в fx.gd (FX).

# ---------------------------------------------------------------- цвета (тёплая тёмная тема «фонарь»)
const BG := Color("0e1015")          # фон приложения
const PANEL := Color("161920")       # поверхность 1: панели, нижнее меню, боковая панель
const CARD := Color("1c2029")        # поверхность 2: карточки
const CARD_HI := Color("252a35")     # поверхность 3: кнопки, поля, выделение
const SURFACE_4 := Color("2e3441")
const LINE := Color("2a2f3a")        # тонкие разделители
const FIELD := Color("12151b")       # поля ввода
const TEXT := Color("f2f3f6")
const MUTED := Color("a4aab8")       # второстепенный текст
const DIM := Color("6c7384")         # подписи, подсказки
const RED := Color("ff6b6b")
const YELLOW := Color("ffc857")
const GREEN := Color("5ccb7a")
const JAVA := Color("5ccb7a")
const BEDROCK := Color("5aa9ff")
const AMBER := Color("ffb648")
const ON_ACCENT := Color("1a1206")   # текст на акцентной кнопке

# ---------------------------------------------------------------- отступы (сетка 4/8 dp)
const S1 := 4
const S2 := 8
const S3 := 12
const S4 := 16
const S5 := 24
const S6 := 32

# ---------------------------------------------------------------- шрифты (sp)
const T_DISPLAY := 28
const T_TITLE := 22
const T_HEAD := 17
const T_BODY := 15
const T_LABEL := 13
const T_CAPTION := 12

# ---------------------------------------------------------------- радиусы
const R_S := 8
const R_M := 12
const R_L := 16
const R_XL := 24

static var font_regular: Font = preload("res://assets/fonts/Montserrat-Medium.ttf")
static var font_bold: Font = preload("res://assets/fonts/Montserrat-Bold.ttf")
static var font_black: Font = preload("res://assets/fonts/Montserrat-ExtraBold.ttf")
static var font_mono: Font = preload("res://assets/fonts/JetBrainsMono-Regular.ttf")
static var font_caps: FontVariation

static var _fallback_done := false


## Символы (✓ ✦ ➤ ♥ ★ …) берём из Noto Sans Symbols 2, если их нет в основном шрифте.
static func _fallbacks() -> void:
	if _fallback_done:
		return
	_fallback_done = true
	var sym: Font = load("res://assets/fonts/NotoSansSymbols2.ttf")
	for f in [font_regular, font_bold, font_black, font_mono]:
		f.fallbacks = [sym]
	font_caps = FontVariation.new()
	font_caps.base_font = font_bold
	font_caps.set_spacing(TextServer.SPACING_GLYPH, 1)


static func accent() -> Color:
	return Cfg.accent()


static func box(color: Color, radius := 16, pad := 0, border := Color(0, 0, 0, 0), bw := 0) -> StyleBoxFlat:
	var s := StyleBoxFlat.new()
	s.bg_color = color
	s.set_corner_radius_all(radius)
	s.set_content_margin_all(pad)
	if bw > 0:
		s.border_color = border
		s.set_border_width_all(bw)
	s.anti_aliasing = true
	s.corner_detail = 10
	return s


## Поверхность с мягкой тенью (листы, всплывашки, плавающие элементы).
static func elevated(color: Color, radius := R_L, pad := 0) -> StyleBoxFlat:
	var s := box(color, radius, pad)
	s.shadow_color = Color(0, 0, 0, 0.45)
	s.shadow_size = 18
	s.shadow_offset = Vector2(0, 6)
	return s


static func _hpad(s: StyleBoxFlat, h: int, v: int) -> StyleBoxFlat:
	s.content_margin_left = h
	s.content_margin_right = h
	s.content_margin_top = v
	s.content_margin_bottom = v
	return s


static func make_theme() -> Theme:
	_fallbacks()
	var t := Theme.new()
	var a := accent()
	var phone := compact()
	t.default_font = font_regular
	t.default_font_size = T_BODY
	t.set_color("font_color", "Label", TEXT)
	t.set_constant("line_spacing", "Label", 0)
	# кнопки (по умолчанию — тональные серые)
	t.set_stylebox("normal", "Button", _hpad(box(CARD_HI, R_M), 16, 10))
	t.set_stylebox("hover", "Button", _hpad(box(SURFACE_4, R_M), 16, 10))
	t.set_stylebox("pressed", "Button", _hpad(box(CARD_HI.darkened(0.2), R_M), 16, 10))
	t.set_stylebox("hover_pressed", "Button", _hpad(box(CARD_HI.darkened(0.2), R_M), 16, 10))
	t.set_stylebox("disabled", "Button", _hpad(box(Color(CARD_HI, 0.5), R_M), 16, 10))
	t.set_stylebox("focus", "Button", box(Color(0, 0, 0, 0), R_M, 0, Color(a, 0.6), 2))
	t.set_font("font", "Button", font_bold)
	t.set_font_size("font_size", "Button", T_BODY)
	t.set_color("font_color", "Button", TEXT)
	t.set_color("font_hover_color", "Button", Color.WHITE)
	t.set_color("font_pressed_color", "Button", Color.WHITE)
	t.set_color("font_hover_pressed_color", "Button", Color.WHITE)
	t.set_color("font_focus_color", "Button", TEXT)
	t.set_color("font_disabled_color", "Button", DIM)
	t.set_constant("h_separation", "Button", 8)
	# MenuButton («•••»)
	for st in ["normal", "hover", "pressed", "hover_pressed", "focus", "disabled"]:
		t.set_stylebox(st, "MenuButton", _hpad(box(Color(1, 1, 1, 0.06 if st in ["hover", "pressed"] else 0.0), R_M), 10, 6))
	t.set_color("font_color", "MenuButton", MUTED)
	t.set_font("font", "MenuButton", font_bold)
	# поля ввода
	for cls in ["LineEdit", "TextEdit", "SpinBox"]:
		t.set_stylebox("normal", cls, _hpad(box(FIELD, R_M, 0, LINE, 1), 14, 10))
		t.set_stylebox("focus", cls, _hpad(box(FIELD, R_M, 0, a, 2), 14, 10))
		t.set_stylebox("read_only", cls, _hpad(box(Color(FIELD, 0.6), R_M, 0, LINE, 1), 14, 10))
		t.set_color("font_color", cls, TEXT)
		t.set_color("font_placeholder_color", cls, DIM)
		t.set_color("caret_color", cls, a)
		t.set_color("selection_color", cls, Color(a, 0.35))
		t.set_constant("caret_width", cls, 2)
	t.set_font_size("font_size", "LineEdit", 16 if phone else T_BODY)
	t.set_font("font", "TextEdit", font_mono)
	t.set_font_size("font_size", "TextEdit", 13)
	t.set_font("normal_font", "RichTextLabel", font_mono)
	t.set_font_size("normal_font_size", "RichTextLabel", 13)
	t.set_color("default_color", "RichTextLabel", Color("c9cfdc"))
	# выпадающий список
	t.set_stylebox("normal", "OptionButton", _hpad(box(FIELD, R_M, 0, LINE, 1), 14, 10))
	t.set_stylebox("hover", "OptionButton", _hpad(box(Color("161a22"), R_M, 0, LINE.lightened(0.2), 1), 14, 10))
	t.set_stylebox("pressed", "OptionButton", _hpad(box(FIELD, R_M, 0, a, 1), 14, 10))
	t.set_stylebox("disabled", "OptionButton", _hpad(box(Color(FIELD, 0.5), R_M, 0, LINE, 1), 14, 10))
	t.set_stylebox("focus", "OptionButton", box(Color(0, 0, 0, 0), R_M, 0, a, 2))
	t.set_font("font", "OptionButton", font_regular)
	t.set_color("font_color", "OptionButton", TEXT)
	t.set_stylebox("panel", "PopupMenu", elevated(Color("232835"), R_M, 8))
	t.set_stylebox("hover", "PopupMenu", box(Color(a, 0.18), R_S, 0))
	t.set_color("font_color", "PopupMenu", TEXT)
	t.set_color("font_hover_color", "PopupMenu", Color.WHITE)
	t.set_font("font", "PopupMenu", font_regular)
	t.set_font_size("font_size", "PopupMenu", 16 if phone else T_BODY)
	t.set_constant("v_separation", "PopupMenu", 18 if phone else 12)
	t.set_constant("item_start_padding", "PopupMenu", 14)
	t.set_constant("item_end_padding", "PopupMenu", 18)
	# флажки и переключатели
	for cls in ["CheckBox", "CheckButton"]:
		t.set_color("font_color", cls, TEXT)
		t.set_color("font_hover_color", cls, Color.WHITE)
		t.set_color("font_pressed_color", cls, TEXT)
		t.set_color("font_hover_pressed_color", cls, Color.WHITE)
		t.set_color("font_focus_color", cls, TEXT)
		t.set_font("font", cls, font_bold)
		t.set_constant("h_separation", cls, 12)
		for st in ["normal", "hover", "pressed", "hover_pressed", "focus", "disabled"]:
			t.set_stylebox(st, cls, box(Color(0, 0, 0, 0), 6, 6))
	t.set_icon("checked", "CheckBox", _check_icon(true, a))
	t.set_icon("unchecked", "CheckBox", _check_icon(false, a))
	t.set_icon("checked", "CheckButton", _switch_icon(true, a))
	t.set_icon("unchecked", "CheckButton", _switch_icon(false, a))
	# прогресс
	t.set_stylebox("background", "ProgressBar", box(Color(1, 1, 1, 0.07), 99))
	t.set_stylebox("fill", "ProgressBar", box(a, 99))
	t.set_font_size("font_size", "ProgressBar", 12)
	t.set_color("font_color", "ProgressBar", TEXT)
	# полосы прокрутки — тонкие; на телефоне их не видно совсем (main.gd)
	for cls in ["VScrollBar", "HScrollBar"]:
		var sc := box(Color(0, 0, 0, 0), 4)
		sc.set_content_margin_all(2)
		t.set_stylebox("scroll", cls, sc)
		t.set_stylebox("scroll_focus", cls, sc)
		t.set_stylebox("grabber", cls, box(Color(1, 1, 1, 0.12), 4))
		t.set_stylebox("grabber_highlight", cls, box(Color(1, 1, 1, 0.22), 4))
		t.set_stylebox("grabber_pressed", cls, box(Color(a, 0.7), 4))
	t.set_stylebox("panel", "TooltipPanel", _hpad(elevated(SURFACE_4, R_S), 10, 6))
	t.set_stylebox("panel", "PanelContainer", box(PANEL, R_L, 0))
	t.set_stylebox("slider", "HSlider", box(Color(1, 1, 1, 0.08), 4, 3))
	t.set_stylebox("grabber_area", "HSlider", box(a, 4, 3))
	t.set_stylebox("grabber_area_highlight", "HSlider", box(a.lightened(0.1), 4, 3))
	t.set_icon("grabber", "HSlider", _dot(11, Color.WHITE))
	t.set_icon("grabber_highlight", "HSlider", _dot(12, Color.WHITE))
	t.set_stylebox("panel", "Window", box(PANEL, R_L, 18, LINE, 1))
	t.set_stylebox("embedded_border", "Window", box(PANEL, R_L, 18, LINE, 1))
	return t


static func _dot(r: int, c: Color) -> ImageTexture:
	var img := Image.create(r * 2, r * 2, false, Image.FORMAT_RGBA8)
	for y in r * 2:
		for x in r * 2:
			var d := Vector2(x + 0.5 - r, y + 0.5 - r).length()
			img.set_pixel(x, y, Color(c, clampf(r - d, 0, 1)))
	return ImageTexture.create_from_image(img)


static func _check_icon(on: bool, a: Color) -> Texture2D:
	var fill := ("#%s" % a.to_html(false)) if on else "none"
	var stroke := "none" if on else "#59616f"
	var mark := '<polyline points="6,11.5 9.5,15 16,7.5" fill="none" stroke="#%s" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/>' % ON_ACCENT.to_html(false) if on else ""
	return _svg_tex('<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22"><rect x="1" y="1" width="20" height="20" rx="6" fill="%s" stroke="%s" stroke-width="2"/>%s</svg>' % [fill, stroke, mark], 22, 22)


static func _switch_icon(on: bool, a: Color) -> Texture2D:
	var track := ("#%s" % a.to_html(false)) if on else "#3a4150"
	var knob := ('<circle cx="36" cy="16" r="11" fill="#%s"/>' % ON_ACCENT.to_html(false)) if on else '<circle cx="16" cy="16" r="8" fill="#aab1c0"/>'
	return _svg_tex('<svg xmlns="http://www.w3.org/2000/svg" width="52" height="32"><rect x="0" y="0" width="52" height="32" rx="16" fill="%s"/>%s</svg>' % [track, knob], 52, 32)


static func _svg_tex(svg: String, w: int, h: int) -> Texture2D:
	var img := Image.new()
	img.load_svg_from_string(svg, 2.0)
	var t := ImageTexture.create_from_image(img)
	t.set_size_override(Vector2i(w, h))
	return t


# ---------------------------------------------------------------- текст

static func label(text: String, size := T_BODY, color := TEXT, font: Font = null) -> Label:
	var l := Label.new()
	l.text = text
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", color)
	if font:
		l.add_theme_font_override("font", font)
	return l


static func para(text: String, size := T_BODY, color := MUTED) -> Label:
	var l := label(text, size, color)
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.custom_minimum_size.x = 120
	return l


static func h1(text: String) -> Label:
	var l := label(text, T_DISPLAY, TEXT, font_black)
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.custom_minimum_size.x = 120
	return l


static func h2(text: String) -> Label:
	return label(text, T_TITLE - 2 if compact() else T_TITLE, TEXT, font_black)


static func h3(text: String) -> Label:
	return label(text, T_HEAD, TEXT, font_bold)


## Подпись раздела: «ДРУЗЬЯ · 3» (капс с разрядкой).
static func caption(text: String, color := DIM) -> Label:
	_fallbacks()
	return label(text.to_upper(), T_CAPTION, color, font_caps)


static func section(text: String, action := "", cb := Callable()) -> Control:
	var r := hbox(8)
	var c := caption(text)
	c.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	c.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	r.add_child(c)
	if action != "":
		var b := button(action, "link", cb)
		b.custom_minimum_size.y = 32
		r.add_child(b)
	var m := MarginContainer.new()
	m.add_theme_constant_override("margin_top", 8)
	m.add_theme_constant_override("margin_left", 4)
	m.add_child(r)
	return m


# ---------------------------------------------------------------- кнопки

static func touch_h() -> int:
	return 48 if compact() else 44


## kind: primary (заливка акцентом), secondary (тональная серая), tonal (тон акцента),
## ghost (без фона), link (текст акцентом), danger, outline.
static func button(text: String, kind := "secondary", cb := Callable(), icon_name := "") -> Button:
	var b := Button.new()
	b.text = text
	b.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	b.custom_minimum_size.y = touch_h()
	b.focus_mode = Control.FOCUS_NONE if compact() else Control.FOCUS_ALL
	var a := accent()
	var fc := TEXT
	match kind:
		"primary":
			_btn_colors(b, a, a.lightened(0.1), a.darkened(0.12), ON_ACCENT, DIM)
			b.add_theme_stylebox_override("disabled", _hpad(box(CARD_HI, R_M), 18, 10))
			fc = ON_ACCENT
		"tonal":
			fc = a.lightened(0.25)
			_btn_colors(b, Color(a, 0.15), Color(a, 0.22), Color(a, 0.3), fc, Color(fc, 0.4))
		"danger":
			fc = Color("ff9d9d")
			_btn_colors(b, Color(RED, 0.14), Color(RED, 0.22), Color(RED, 0.32), fc, Color(fc, 0.4))
		"outline":
			for st in [["normal", 0.0], ["hover", 0.05], ["pressed", 0.1], ["hover_pressed", 0.1], ["disabled", 0.0]]:
				b.add_theme_stylebox_override(st[0], _hpad(box(Color(1, 1, 1, st[1]), R_M, 0, Color(1, 1, 1, 0.16), 1), 16, 10))
		"ghost", "link":
			fc = MUTED if kind == "ghost" else a
			for st in [["normal", 0.0], ["hover", 0.06], ["pressed", 0.1], ["hover_pressed", 0.1], ["disabled", 0.0]]:
				b.add_theme_stylebox_override(st[0], _hpad(box(Color(1, 1, 1, st[1]), R_M), 12, 8))
			b.add_theme_color_override("font_color", fc)
			b.add_theme_color_override("font_hover_color", TEXT if kind == "ghost" else a.lightened(0.15))
			b.add_theme_color_override("font_pressed_color", TEXT if kind == "ghost" else a)
	if icon_name != "":
		b.icon = icon_tex(icon_name, 20, fc)
	if cb.is_valid():
		b.pressed.connect(cb)
	FX.press(b)
	return b


static func _btn_colors(b: Button, n: Color, h: Color, p: Color, fc: Color, dis: Color) -> void:
	b.add_theme_stylebox_override("normal", _hpad(box(n, R_M), 18, 10))
	b.add_theme_stylebox_override("hover", _hpad(box(h, R_M), 18, 10))
	b.add_theme_stylebox_override("pressed", _hpad(box(p, R_M), 18, 10))
	b.add_theme_stylebox_override("hover_pressed", _hpad(box(p, R_M), 18, 10))
	b.add_theme_stylebox_override("disabled", _hpad(box(Color(n, n.a * 0.4) if n.a < 1 else Color(n, 0.3), R_M), 18, 10))
	for k in ["font_color", "font_hover_color", "font_pressed_color", "font_hover_pressed_color", "font_focus_color"]:
		b.add_theme_color_override(k, fc)
	b.add_theme_color_override("font_disabled_color", dis)


static var _icon_cache := {}


## Иконка-текстура для Button.icon (рисуется из того же векторного набора через SVG).
static func icon_tex(name: String, px := 20, c := TEXT) -> Texture2D:
	var key := "%s|%d|%s" % [name, px, c.to_html()]
	if not _icon_cache.has(key):
		_icon_cache[key] = UIcon.texture(name, px, c)
	return _icon_cache[key]


static func icon(name: String, px := 24, c := TEXT) -> UIcon:
	return UIcon.make(name, px, c)


## Круглая кнопка-иконка 48×48 (верхняя панель, чат, «•••»).
static func icon_button(name: String, cb := Callable(), tip := "", c := TEXT, filled := Color(0, 0, 0, 0)) -> Button:
	var b := Button.new()
	b.custom_minimum_size = Vector2(48, 48) if compact() else Vector2(44, 44)
	b.tooltip_text = tip
	b.focus_mode = Control.FOCUS_NONE
	b.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	b.add_theme_stylebox_override("normal", box(filled, 99))
	b.add_theme_stylebox_override("hover", box(filled.lightened(0.1) if filled.a > 0 else Color(1, 1, 1, 0.07), 99))
	b.add_theme_stylebox_override("pressed", box(filled.darkened(0.15) if filled.a > 0 else Color(1, 1, 1, 0.12), 99))
	b.add_theme_stylebox_override("hover_pressed", box(filled.darkened(0.15) if filled.a > 0 else Color(1, 1, 1, 0.12), 99))
	b.add_theme_stylebox_override("disabled", box(Color(filled, filled.a * 0.4), 99))
	b.add_theme_stylebox_override("focus", box(Color(0, 0, 0, 0), 99))
	var ic := UIcon.make(name, 22, c)
	ic.set_anchors_preset(Control.PRESET_CENTER)
	ic.offset_left = -11
	ic.offset_top = -11
	ic.offset_right = 11
	ic.offset_bottom = 11
	b.add_child(ic)
	b.set_meta("icon", ic)
	if cb.is_valid():
		b.pressed.connect(cb)
	FX.press(b)
	return b


## Кнопка-чип (фильтры, вкладки, быстрые ответы).
static func chip(text: String, on := false, cb := Callable()) -> Button:
	var b := Button.new()
	b.text = text
	b.focus_mode = Control.FOCUS_NONE
	b.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	b.custom_minimum_size.y = 40 if compact() else 36
	b.add_theme_font_size_override("font_size", 14)
	chip_set(b, on)
	if cb.is_valid():
		b.pressed.connect(cb)
	FX.press(b)
	return b


static func chip_set(b: Button, on: bool) -> void:
	var a := accent()
	var n := box(Color(a, 0.16) if on else Color(1, 1, 1, 0.05), 99, 0, Color(a, 0.55) if on else Color(1, 1, 1, 0.08), 1)
	_hpad(n, 16, 6)
	b.add_theme_stylebox_override("normal", n)
	var h: StyleBoxFlat = n.duplicate()
	h.bg_color = Color(a, 0.22) if on else Color(1, 1, 1, 0.09)
	b.add_theme_stylebox_override("hover", h)
	b.add_theme_stylebox_override("pressed", h)
	b.add_theme_stylebox_override("hover_pressed", h)
	b.add_theme_stylebox_override("focus", box(Color(0, 0, 0, 0), 99))
	for k in ["font_color", "font_hover_color", "font_pressed_color", "font_hover_pressed_color", "font_focus_color"]:
		b.add_theme_color_override(k, a.lightened(0.3) if on else MUTED)
	b.add_theme_font_override("font", font_bold)


## Вкладки-чипы в одну строку с горизонтальной прокруткой (без переноса).
## Кнопки — в meta "tabs" {key: Button}.
static func tabs(items: Array, current: String, cb: Callable) -> ScrollContainer:
	var sc := ScrollContainer.new()
	sc.vertical_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_SHOW_NEVER
	sc.custom_minimum_size.y = 44 if compact() else 40
	var row := hbox(8)
	sc.add_child(row)
	var d := {}
	for it in items:
		var b := chip(it[1], it[0] == current, cb.bind(it[0]))
		d[it[0]] = b
		row.add_child(b)
	sc.set_meta("tabs", d)
	return sc


static func tabs_select(sc: Control, key: String) -> void:
	var d: Dictionary = sc.get_meta("tabs", {})
	for k in d:
		chip_set(d[k], k == key)
	if sc is ScrollContainer and d.has(key):
		(sc as ScrollContainer).ensure_control_visible.call_deferred(d[key])


## Сегменты одинаковой ширины со скользящей подложкой (Java / Bedrock / Оба).
static func segmented(items: Array, current: String, cb: Callable) -> PanelContainer:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", box(FIELD, R_M + 2, 4, LINE, 1))
	var holder := Control.new()
	holder.custom_minimum_size.y = 42
	p.add_child(holder)
	var pill_bg := Panel.new()
	pill_bg.add_theme_stylebox_override("panel", box(CARD_HI.lightened(0.04), R_M - 2))
	pill_bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	holder.add_child(pill_bg)
	var row := hbox(0)
	row.set_anchors_preset(Control.PRESET_FULL_RECT)
	holder.add_child(row)
	var d := {}
	for it in items:
		var b := Button.new()
		b.text = it[1]
		b.focus_mode = Control.FOCUS_NONE
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		b.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
		for st in ["normal", "hover", "pressed", "hover_pressed", "focus"]:
			b.add_theme_stylebox_override(st, box(Color(0, 0, 0, 0), R_M - 2))
		b.pressed.connect(cb.bind(it[0]))
		d[it[0]] = b
		row.add_child(b)
	p.set_meta("tabs", d)
	p.set_meta("pill", pill_bg)
	p.set_meta("key", current)
	holder.resized.connect(func(): segmented_select(p, p.get_meta("key"), false))
	segmented_select(p, current, false)
	return p


static func segmented_select(p: Control, key: String, animate := true) -> void:
	var d: Dictionary = p.get_meta("tabs", {})
	p.set_meta("key", key)
	for k in d:
		var on: bool = k == key
		var b: Button = d[k]
		for c in ["font_color", "font_hover_color", "font_pressed_color", "font_hover_pressed_color"]:
			b.add_theme_color_override(c, TEXT if on else MUTED)
	var pl: Panel = p.get_meta("pill")
	if not d.has(key):
		pl.visible = false
		return
	pl.visible = true
	var b: Button = d[key]
	var n := d.size()
	var w: float = pl.get_parent().size.x / max(n, 1)
	var target := Rect2(Vector2(w * b.get_index(), 0), Vector2(w, (pl.get_parent() as Control).size.y))
	if animate and FX.on():
		var tw := pl.create_tween().set_parallel().set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
		tw.tween_property(pl, "position", target.position, 0.28)
		tw.tween_property(pl, "size", target.size, 0.28)
	else:
		pl.position = target.position
		pl.size = target.size


# ---------------------------------------------------------------- контейнеры

## Карточка: поверхность 2 без заметной рамки. Подпись совместима со старым кодом: panel(pad, цвет, радиус).
static func panel(pad := 20, color := CARD, radius := R_L) -> PanelContainer:
	if color == PANEL:
		color = CARD
	pad = mini(pad, 16) if compact() else mini(pad, 24)
	var p := PanelContainer.new()
	var s := box(color, radius, pad)
	s.border_color = Color(1, 1, 1, 0.045)
	s.set_border_width_all(1)
	p.add_theme_stylebox_override("panel", s)
	return p


static func card(pad := S4, color := CARD, radius := R_L) -> PanelContainer:
	return panel(pad, color, radius)


## Строка списка: [иконка] заголовок / подзаголовок [справа]. Нажимается целиком, если передан cb.
static func list_row(lead: Control, title: String, sub := "", trail: Control = null, cb := Callable(), color := CARD) -> PanelContainer:
	var p := PanelContainer.new()
	var st := box(color, R_L, 0)
	_hpad(st, 14, 10)
	p.add_theme_stylebox_override("panel", st)
	var row := hbox(14)
	p.add_child(row)
	if lead:
		lead.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		row.add_child(lead)
	var v := vbox(2)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	var tl := label(title, T_BODY + 1, TEXT, font_bold)
	tl.clip_text = true
	tl.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	v.add_child(tl)
	if sub != "":
		var sl := label(sub, T_LABEL, MUTED)
		sl.clip_text = true
		sl.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		v.add_child(sl)
	row.add_child(v)
	p.set_meta("title", tl)
	p.set_meta("text", v)
	p.set_meta("row", row)
	if trail:
		trail.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		row.add_child(trail)
	p.custom_minimum_size.y = 64
	if cb.is_valid():
		tappable(p, cb, st)
	return p


## Делает панель нажимаемой (подсветка при наведении и нажатии; прокрутку пальцем не считает нажатием).
static func tappable(p: Control, cb: Callable, st: StyleBoxFlat = null) -> void:
	p.mouse_filter = Control.MOUSE_FILTER_STOP
	p.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	var base := st.bg_color if st else Color()
	var down := [Vector2.ZERO]
	p.mouse_entered.connect(func(): if st: st.bg_color = base.lightened(0.04))
	p.mouse_exited.connect(func(): if st: st.bg_color = base)
	p.gui_input.connect(func(e):
		if e is InputEventMouseButton and e.button_index == MOUSE_BUTTON_LEFT:
			if e.pressed:
				down[0] = e.position
				if st:
					st.bg_color = base.lightened(0.08)
				FX.ripple(p, e.position)
			else:
				if st:
					st.bg_color = base
				if e.position.distance_to(down[0]) < 12 and Rect2(Vector2.ZERO, p.size).has_point(e.position):
					cb.call())
	FX.press(p, 0.985)


static func icon_tile(name: String, color: Color, px := 44, ic := 22) -> PanelContainer:
	var t := PanelContainer.new()
	t.custom_minimum_size = Vector2(px, px)
	t.add_theme_stylebox_override("panel", box(Color(color, 0.16), R_M))
	var i := UIcon.make(name, ic, color.lightened(0.15))
	i.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	i.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	t.add_child(i)
	t.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return t


## Пустое состояние: картинка + одна строка + действие.
static func empty(icon_name: String, text: String, action := "", cb := Callable(), sub := "") -> Control:
	var v := vbox(12)
	v.alignment = BoxContainer.ALIGNMENT_CENTER
	var ring := PanelContainer.new()
	ring.add_theme_stylebox_override("panel", box(Color(accent(), 0.10), 99))
	ring.custom_minimum_size = Vector2(88, 88)
	ring.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	var ic := UIcon.make(icon_name, 40, accent())
	ic.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	ic.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	ring.add_child(ic)
	v.add_child(gap(8))
	v.add_child(ring)
	var t := para(text, T_HEAD, TEXT)
	t.add_theme_font_override("font", font_bold)
	t.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	v.add_child(t)
	if sub != "":
		var s := para(sub, T_LABEL + 1, MUTED)
		s.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		v.add_child(s)
	if action != "":
		var b := button(action, "primary", cb)
		b.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
		b.custom_minimum_size.x = 200
		v.add_child(b)
	v.add_child(gap(8))
	return v


## Шаги мастера: «Шаг 1 из 3 · Название» + полоски (заполнение анимируется).
static func steps(cur: int, total: int, names: Array = [], prev := -1) -> Control:
	var v := vbox(8)
	var t := hbox(8)
	t.add_child(caption("Шаг %d из %d" % [cur, total], accent()))
	if cur - 1 < names.size():
		t.add_child(label(String(names[cur - 1]), T_CAPTION, MUTED, font_bold))
	v.add_child(t)
	var bars := hbox(6)
	for i in total:
		var track := Panel.new()
		track.custom_minimum_size.y = 4
		track.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		track.add_theme_stylebox_override("panel", box(Color(1, 1, 1, 0.1), 99))
		var fill := Panel.new()
		fill.add_theme_stylebox_override("panel", box(accent(), 99))
		fill.set_anchors_preset(Control.PRESET_FULL_RECT)
		fill.mouse_filter = Control.MOUSE_FILTER_IGNORE
		track.add_child(fill)
		var full := i < cur
		var was := i < (prev if prev >= 0 else cur)
		fill.anchor_right = 1.0 if (was if FX.on() else full) else 0.0
		if FX.on() and full != was:
			fill.create_tween().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT).tween_property(fill, "anchor_right", 1.0 if full else 0.0, 0.32)
		bars.add_child(track)
	v.add_child(bars)
	return v


## Большая карточка выбора (мастера): иконка, заголовок, пояснение, отметка выбора.
static func choice(icon_name: String, color: Color, title: String, desc: String, on: bool, cb: Callable) -> PanelContainer:
	var p := PanelContainer.new()
	var st := box(Color(color, 0.10) if on else CARD, R_L, 0, color if on else Color(1, 1, 1, 0.06), 2 if on else 1)
	_hpad(st, 16, 16)
	p.add_theme_stylebox_override("panel", st)
	var row := hbox(14)
	p.add_child(row)
	row.add_child(icon_tile(icon_name, color, 52, 26))
	var v := vbox(4)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.add_child(label(title, T_HEAD, TEXT, font_bold))
	v.add_child(para(desc, T_LABEL + 1, MUTED))
	row.add_child(v)
	var mark := PanelContainer.new()
	mark.custom_minimum_size = Vector2(26, 26)
	mark.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	mark.add_theme_stylebox_override("panel", box(color if on else Color(0, 0, 0, 0), 99, 0, color if on else Color(1, 1, 1, 0.25), 2))
	if on:
		var ck := UIcon.make("check", 16, ON_ACCENT)
		ck.stroke = 3.0
		ck.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
		ck.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		mark.add_child(ck)
	row.add_child(mark)
	tappable(p, cb, st)
	return p


## Узкий экран (телефон в портрете, телефон лёжа или маленькое окно): одна колонка, нижнее меню, крупные кнопки.
static func compact() -> bool:
	var tree := Engine.get_main_loop() as SceneTree
	if tree == null or tree.root == null:
		return false
	var s := tree.root.get_visible_rect().size
	return s.x < 760 or s.y < 560


## Колонки рядом на ПК и друг под другом на телефоне.
static func cols(sep := 20) -> BoxContainer:
	var b: BoxContainer = VBoxContainer.new() if compact() else HBoxContainer.new()
	b.add_theme_constant_override("separation", mini(sep, 16) if compact() else sep)
	return b


## Ряд кнопок, который переносится на следующую строку, если не помещается.
static func flow(sep := 8) -> HFlowContainer:
	var f := HFlowContainer.new()
	f.add_theme_constant_override("h_separation", sep)
	f.add_theme_constant_override("v_separation", sep)
	return f


## Ширина окна-диалога: не шире экрана.
static func dialog_width(w: float) -> float:
	var tree := Engine.get_main_loop() as SceneTree
	return minf(w, tree.root.get_visible_rect().size.x - 32) if tree and tree.root else w


static func vbox(sep := 12) -> VBoxContainer:
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", sep)
	return v


static func hbox(sep := 12) -> HBoxContainer:
	var h := HBoxContainer.new()
	h.add_theme_constant_override("separation", sep)
	return h


static func spacer(h := true) -> Control:
	var c := Control.new()
	c.mouse_filter = Control.MOUSE_FILTER_IGNORE
	if h:
		c.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	else:
		c.size_flags_vertical = Control.SIZE_EXPAND_FILL
	return c


static func gap(px: int) -> Control:
	var c := Control.new()
	c.mouse_filter = Control.MOUSE_FILTER_IGNORE
	c.custom_minimum_size = Vector2(px, px)
	return c


static func divider() -> Control:
	var c := ColorRect.new()
	c.color = Color(1, 1, 1, 0.06)
	c.custom_minimum_size.y = 1
	c.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return c


static func pill(text: String, color: Color) -> PanelContainer:
	var p := PanelContainer.new()
	p.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	p.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	p.add_theme_stylebox_override("panel", _hpad(box(Color(color, 0.15), 99), 10, 3))
	var l := label(text, T_CAPTION, color.lightened(0.3), font_bold)
	p.add_child(l)
	p.set_meta("label", l)
	return p


## Счётчик (непрочитанные, заявки).
static func badge(n: int) -> PanelContainer:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", _hpad(box(RED, 99), 6, 1))
	p.custom_minimum_size = Vector2(20, 20)
	p.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	p.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	var l := label(str(n) if n < 100 else "99+", 11, Color.WHITE, font_bold)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	l.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	p.add_child(l)
	p.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return p


## Баланс искр — маленький чип (верхняя панель, магазин).
static func sparks_chip(n: int, cb := Callable()) -> Button:
	var b := Button.new()
	b.text = "%d" % n
	b.icon = icon_tex("spark", 16, AMBER)
	b.focus_mode = Control.FOCUS_NONE
	b.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	b.custom_minimum_size.y = 36
	b.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	b.add_theme_stylebox_override("normal", _hpad(box(Color(AMBER, 0.12), 99), 12, 4))
	b.add_theme_stylebox_override("hover", _hpad(box(Color(AMBER, 0.2), 99), 12, 4))
	b.add_theme_stylebox_override("pressed", _hpad(box(Color(AMBER, 0.26), 99), 12, 4))
	b.add_theme_stylebox_override("hover_pressed", _hpad(box(Color(AMBER, 0.26), 99), 12, 4))
	b.add_theme_stylebox_override("focus", box(Color(0, 0, 0, 0), 99))
	for k in ["font_color", "font_hover_color", "font_pressed_color", "font_hover_pressed_color"]:
		b.add_theme_color_override(k, AMBER.lightened(0.2))
	b.add_theme_font_size_override("font_size", 14)
	b.add_theme_constant_override("h_separation", 6)
	if cb.is_valid():
		b.pressed.connect(cb)
	FX.press(b)
	return b


static func texture(path: String, w := 0, h := 0) -> TextureRect:
	var t := TextureRect.new()
	t.texture = load(path)
	t.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	t.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	t.custom_minimum_size = Vector2(w, h)
	t.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return t


static func field(caption_text: String, ctrl: Control, hint := "") -> VBoxContainer:
	var v := vbox(6)
	v.add_child(label(caption_text, T_LABEL, MUTED, font_bold))
	v.add_child(ctrl)
	if hint != "":
		v.add_child(para(hint, T_CAPTION, DIM))
	return v


static func option(items: Array, selected := 0) -> OptionButton:
	var o := OptionButton.new()
	for it in items:
		o.add_item(str(it))
	o.select(selected)
	o.custom_minimum_size.y = touch_h()
	o.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	return o


static func line_edit(text := "", placeholder := "") -> LineEdit:
	var e := LineEdit.new()
	e.text = text
	e.placeholder_text = placeholder
	e.custom_minimum_size.y = touch_h() + (4 if compact() else 0)
	return e


## Строка настроек с тумблером: заголовок + пояснение слева, переключатель справа.
static func switch_row(title: String, desc: String, on: bool, cb := Callable()) -> Dictionary:
	var row := hbox(12)
	var v := vbox(2)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.add_child(label(title, T_BODY, TEXT, font_bold))
	if desc != "":
		v.add_child(para(desc, T_LABEL, MUTED))
	row.add_child(v)
	var sw := CheckButton.new()
	sw.button_pressed = on
	sw.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	sw.focus_mode = Control.FOCUS_NONE
	sw.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	if cb.is_valid():
		sw.toggled.connect(cb)
	row.add_child(sw)
	return {"row": row, "switch": sw}


## Мягкое увеличение при наведении (только мышь, на ПК).
static func hover_scale(c: Control, k := 1.02) -> void:
	if compact() or not FX.on():
		return
	c.resized.connect(func(): c.pivot_offset = c.size / 2)
	c.mouse_entered.connect(func():
		c.create_tween().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT).tween_property(c, "scale", Vector2.ONE * k, 0.18))
	c.mouse_exited.connect(func():
		c.create_tween().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT).tween_property(c, "scale", Vector2.ONE, 0.22))


static func copy(text: String) -> void:
	DisplayServer.clipboard_set(text)
