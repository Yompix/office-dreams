class_name UI
extends RefCounted
## Стиль LANTERN: тёмная тема, скруглённые карточки, акцентный цвет из настроек.

const BG := Color("10131a")
const PANEL := Color("171b25")
const CARD := Color("1d2230")
const CARD_HI := Color("252b3c")
const LINE := Color("2c3345")
const TEXT := Color("eef1f7")
const MUTED := Color("9aa3b8")
const DIM := Color("6b7489")
const RED := Color("ff5d6c")
const YELLOW := Color("ffcc4d")
const JAVA := Color("4cc96e")
const BEDROCK := Color("4aa8ff")

static var font_regular: Font = preload("res://assets/fonts/Montserrat-Medium.ttf")
static var font_bold: Font = preload("res://assets/fonts/Montserrat-Bold.ttf")
static var font_black: Font = preload("res://assets/fonts/Montserrat-ExtraBold.ttf")
static var font_mono: Font = preload("res://assets/fonts/JetBrainsMono-Regular.ttf")


static var _fallback_done := false


## Символы (✓ ✦ ➤ ♥ ★ …) берём из Noto Sans Symbols 2, если их нет в основном шрифте.
static func _fallbacks() -> void:
	if _fallback_done:
		return
	_fallback_done = true
	var sym: Font = load("res://assets/fonts/NotoSansSymbols2.ttf")
	for f in [font_regular, font_bold, font_black, font_mono]:
		f.fallbacks = [sym]


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
	return s


static func make_theme() -> Theme:
	_fallbacks()
	var t := Theme.new()
	var a := accent()
	t.default_font = font_regular
	t.default_font_size = 16
	t.set_color("font_color", "Label", TEXT)
	# кнопки
	t.set_stylebox("normal", "Button", box(CARD_HI, 12, 12))
	t.set_stylebox("hover", "Button", box(CARD_HI.lightened(0.08), 12, 12))
	t.set_stylebox("pressed", "Button", box(CARD_HI.darkened(0.15), 12, 12))
	t.set_stylebox("disabled", "Button", box(CARD.darkened(0.1), 12, 12))
	t.set_stylebox("focus", "Button", box(Color(0, 0, 0, 0), 12, 12, a, 2))
	t.set_font("font", "Button", font_bold)
	t.set_font_size("font_size", "Button", 16)
	t.set_color("font_color", "Button", TEXT)
	t.set_color("font_hover_color", "Button", Color.WHITE)
	t.set_color("font_pressed_color", "Button", Color.WHITE)
	t.set_color("font_disabled_color", "Button", DIM)
	t.set_constant("h_separation", "Button", 10)
	# поля ввода
	for cls in ["LineEdit", "TextEdit", "SpinBox"]:
		t.set_stylebox("normal", cls, box(Color("0d1017"), 10, 12, LINE, 1))
		t.set_stylebox("focus", cls, box(Color("0d1017"), 10, 12, a, 2))
		t.set_stylebox("read_only", cls, box(Color("0d1017"), 10, 12, LINE, 1))
		t.set_color("font_color", cls, TEXT)
		t.set_color("font_placeholder_color", cls, DIM)
		t.set_color("caret_color", cls, a)
		t.set_color("selection_color", cls, Color(a, 0.35))
	t.set_font("font", "TextEdit", font_mono)
	t.set_font_size("font_size", "TextEdit", 13)
	t.set_font("normal_font", "RichTextLabel", font_mono)
	t.set_font_size("normal_font_size", "RichTextLabel", 13)
	t.set_color("default_color", "RichTextLabel", Color("c9d1e3"))
	# выпадающий список
	t.set_stylebox("normal", "OptionButton", box(Color("0d1017"), 10, 12, LINE, 1))
	t.set_stylebox("hover", "OptionButton", box(Color("121621"), 10, 12, LINE.lightened(0.2), 1))
	t.set_stylebox("pressed", "OptionButton", box(Color("0d1017"), 10, 12, a, 1))
	t.set_stylebox("focus", "OptionButton", box(Color(0, 0, 0, 0), 10, 12, a, 2))
	t.set_font("font", "OptionButton", font_regular)
	t.set_stylebox("panel", "PopupMenu", box(CARD_HI, 10, 6, LINE, 1))
	t.set_stylebox("hover", "PopupMenu", box(Color(a, 0.25), 8, 6))
	t.set_color("font_color", "PopupMenu", TEXT)
	t.set_color("font_hover_color", "PopupMenu", Color.WHITE)
	t.set_font("font", "PopupMenu", font_regular)
	# флажки
	t.set_color("font_color", "CheckBox", TEXT)
	t.set_color("font_hover_color", "CheckBox", Color.WHITE)
	t.set_color("font_pressed_color", "CheckBox", TEXT)
	t.set_color("font_hover_pressed_color", "CheckBox", Color.WHITE)
	t.set_color("font_focus_color", "CheckBox", TEXT)
	for st in ["normal", "hover", "pressed", "hover_pressed", "focus"]:
		t.set_stylebox(st, "CheckBox", box(Color(0, 0, 0, 0), 6, 4))
		t.set_stylebox(st, "CheckButton", box(Color(0, 0, 0, 0), 6, 4))
	t.set_icon("checked", "CheckBox", _check_icon(true, a))
	t.set_icon("unchecked", "CheckBox", _check_icon(false, a))
	t.set_icon("checked", "CheckButton", _switch_icon(true, a))
	t.set_icon("unchecked", "CheckButton", _switch_icon(false, a))
	t.set_color("font_color", "CheckButton", TEXT)
	t.set_color("font_hover_color", "CheckButton", Color.WHITE)
	t.set_color("font_pressed_color", "CheckButton", TEXT)
	t.set_color("font_hover_pressed_color", "CheckButton", Color.WHITE)
	# прогресс
	t.set_stylebox("background", "ProgressBar", box(Color("0d1017"), 8))
	t.set_stylebox("fill", "ProgressBar", box(a, 8))
	t.set_font_size("font_size", "ProgressBar", 12)
	t.set_color("font_color", "ProgressBar", TEXT)
	# полосы прокрутки
	t.set_stylebox("scroll", "VScrollBar", box(Color(0, 0, 0, 0), 4))
	t.set_stylebox("grabber", "VScrollBar", box(LINE, 4))
	t.set_stylebox("grabber_highlight", "VScrollBar", box(LINE.lightened(0.2), 4))
	t.set_stylebox("grabber_pressed", "VScrollBar", box(a, 4))
	t.set_stylebox("panel", "TooltipPanel", box(CARD_HI, 8, 8, LINE, 1))
	t.set_stylebox("panel", "PanelContainer", box(PANEL, 18, 0))
	t.set_stylebox("slider", "HSlider", box(Color("0d1017"), 4, 3))
	t.set_stylebox("grabber_area", "HSlider", box(a, 4, 3))
	t.set_stylebox("grabber_area_highlight", "HSlider", box(a.lightened(0.1), 4, 3))
	t.set_icon("grabber", "HSlider", _dot(10, Color.WHITE))
	t.set_icon("grabber_highlight", "HSlider", _dot(11, Color.WHITE))
	t.set_stylebox("panel", "Window", box(PANEL, 16, 18, LINE, 1))
	t.set_stylebox("embedded_border", "Window", box(PANEL, 16, 18, LINE, 1))
	return t


static func _dot(r: int, c: Color) -> ImageTexture:
	var img := Image.create(r * 2, r * 2, false, Image.FORMAT_RGBA8)
	for y in r * 2:
		for x in r * 2:
			var d := Vector2(x + 0.5 - r, y + 0.5 - r).length()
			img.set_pixel(x, y, Color(c, clampf(r - d, 0, 1)))
	return ImageTexture.create_from_image(img)


static func _check_icon(on: bool, a: Color) -> ImageTexture:
	var s := 22
	var img := Image.create(s, s, false, Image.FORMAT_RGBA8)
	for y in s:
		for x in s:
			var inside := _rrect(x, y, s, s, 6)
			var edge := inside and not _rrect(x - 2, y - 2, s - 4, s - 4, 4)
			var c := Color(0, 0, 0, 0)
			if inside:
				c = a if on else Color("0d1017")
				if edge and not on:
					c = LINE.lightened(0.25)
			img.set_pixel(x, y, c)
	if on:
		for p in [Vector2i(6, 11), Vector2i(7, 12), Vector2i(8, 13), Vector2i(9, 14), Vector2i(10, 13), Vector2i(11, 12), Vector2i(12, 11), Vector2i(13, 10), Vector2i(14, 9), Vector2i(15, 8), Vector2i(16, 7)]:
			for d in [Vector2i(0, 0), Vector2i(0, -1), Vector2i(1, 0)]:
				img.set_pixelv(p + d, Color("0d1017"))
	return ImageTexture.create_from_image(img)


static func _rrect(x: int, y: int, w: int, h: int, r: int) -> bool:
	if x < 0 or y < 0 or x >= w or y >= h:
		return false
	var cx := clampi(x, r, w - 1 - r)
	var cy := clampi(y, r, h - 1 - r)
	return Vector2(x - cx, y - cy).length() <= r + 0.3


static func _switch_icon(on: bool, a: Color) -> ImageTexture:
	var w := 44
	var h := 24
	var img := Image.create(w, h, false, Image.FORMAT_RGBA8)
	var knob := Vector2(w - 12, 12) if on else Vector2(12, 12)
	for y in h:
		for x in w:
			var c := Color(0, 0, 0, 0)
			if _rrect(x, y, w, h, 11):
				c = a if on else Color("2c3345")
			var d := Vector2(x + 0.5, y + 0.5).distance_to(knob)
			if d < 8.5:
				c = c.blend(Color(1, 1, 1, clampf(8.5 - d, 0, 1)))
			img.set_pixel(x, y, c)
	return ImageTexture.create_from_image(img)


# ---------------------------------------------------------------- фабрики

static func label(text: String, size := 16, color := TEXT, font: Font = null) -> Label:
	var l := Label.new()
	l.text = text
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", color)
	if font:
		l.add_theme_font_override("font", font)
	return l


static func para(text: String, size := 15, color := MUTED) -> Label:
	var l := label(text, size, color)
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.custom_minimum_size.x = 120
	return l


static func h1(text: String) -> Label:
	return label(text, 34, TEXT, font_black)


static func h2(text: String) -> Label:
	return label(text, 22, TEXT, font_bold)


static func h3(text: String) -> Label:
	return label(text, 17, TEXT, font_bold)


static func button(text: String, kind := "secondary", cb := Callable()) -> Button:
	var b := Button.new()
	b.text = text
	b.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	b.custom_minimum_size.y = 46
	var a := accent()
	match kind:
		"primary":
			b.add_theme_stylebox_override("normal", box(a, 12, 14))
			b.add_theme_stylebox_override("hover", box(a.lightened(0.12), 12, 14))
			b.add_theme_stylebox_override("pressed", box(a.darkened(0.15), 12, 14))
			b.add_theme_color_override("font_color", Color("0b0e14"))
			b.add_theme_color_override("font_hover_color", Color("0b0e14"))
			b.add_theme_color_override("font_pressed_color", Color("0b0e14"))
			b.add_theme_color_override("font_focus_color", Color("0b0e14"))
		"danger":
			b.add_theme_stylebox_override("normal", box(Color(RED, 0.16), 12, 14, Color(RED, 0.5), 1))
			b.add_theme_stylebox_override("hover", box(Color(RED, 0.28), 12, 14, RED, 1))
			b.add_theme_stylebox_override("pressed", box(Color(RED, 0.4), 12, 14, RED, 1))
			b.add_theme_color_override("font_color", Color("ffb3ba"))
		"ghost":
			b.add_theme_stylebox_override("normal", box(Color(0, 0, 0, 0), 10, 10))
			b.add_theme_stylebox_override("hover", box(Color(1, 1, 1, 0.06), 10, 10))
			b.add_theme_stylebox_override("pressed", box(Color(1, 1, 1, 0.1), 10, 10))
			b.add_theme_color_override("font_color", MUTED)
	if cb.is_valid():
		b.pressed.connect(cb)
	return b


static func panel(pad := 22, color := PANEL, radius := 18) -> PanelContainer:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", box(color, radius, pad, LINE, 1))
	return p


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
	if h:
		c.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	else:
		c.size_flags_vertical = Control.SIZE_EXPAND_FILL
	return c


static func gap(px: int) -> Control:
	var c := Control.new()
	c.custom_minimum_size = Vector2(px, px)
	return c


static func pill(text: String, color: Color) -> PanelContainer:
	var p := PanelContainer.new()
	p.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	var s := box(Color(color, 0.16), 99, 0, Color(color, 0.45), 1)
	s.content_margin_left = 12
	s.content_margin_right = 12
	s.content_margin_top = 4
	s.content_margin_bottom = 4
	p.add_theme_stylebox_override("panel", s)
	var l := label(text, 13, color.lightened(0.25), font_bold)
	p.add_child(l)
	p.set_meta("label", l)
	return p


static func texture(path: String, w := 0, h := 0) -> TextureRect:
	var t := TextureRect.new()
	t.texture = load(path)
	t.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	t.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	t.custom_minimum_size = Vector2(w, h)
	return t


static func field(caption: String, ctrl: Control, hint := "") -> VBoxContainer:
	var v := vbox(6)
	v.add_child(label(caption, 14, MUTED, font_bold))
	v.add_child(ctrl)
	if hint != "":
		v.add_child(para(hint, 13, DIM))
	return v


static func option(items: Array, selected := 0) -> OptionButton:
	var o := OptionButton.new()
	for it in items:
		o.add_item(str(it))
	o.select(selected)
	o.custom_minimum_size.y = 44
	o.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	return o


static func line_edit(text := "", placeholder := "") -> LineEdit:
	var e := LineEdit.new()
	e.text = text
	e.placeholder_text = placeholder
	e.custom_minimum_size.y = 46
	return e


## Мягкое увеличение при наведении (карточки, большие кнопки).
static func hover_scale(c: Control, k := 1.03) -> void:
	c.resized.connect(func(): c.pivot_offset = c.size / 2)
	c.mouse_entered.connect(func():
		var tw := c.create_tween().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
		tw.tween_property(c, "scale", Vector2.ONE * k, 0.18))
	c.mouse_exited.connect(func():
		var tw := c.create_tween().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
		tw.tween_property(c, "scale", Vector2.ONE, 0.22))


static func copy(text: String) -> void:
	DisplayServer.clipboard_set(text)
