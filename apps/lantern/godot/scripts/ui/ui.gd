class_name UI
extends RefCounted
## Дизайн-система LANTERN: цвета, отступы, шрифты, радиусы и готовые элементы.
## Все размеры — в dp: на телефоне окно масштабируется так, что 1 единица = 1 dp (см. main.gd),
## поэтому 48 здесь — это 48 dp, удобная для пальца высота кнопки на любом экране.
## Анимации — в fx.gd (FX).

# ---------------------------------------------------------------- цвета 1.7: «ночной лагерь, освещённый фонарём»
# Фон — живое ночное небо (backdrop.gd), поверх него «стекло»: полупрозрачные поверхности со светлой кромкой сверху,
# мягкой тенью и тёплым свечением у главного элемента. Один акцент (янтарь) + цвета режимов игры (cover.gd).
const BG := Color("080a14")                    # самый низ неба
const BG_TOP := Color("171a3d")                # индиго вверху
const PANEL := Color("13162c")                 # плотная поверхность (то, что должно закрывать фон)
const CARD := Color(0.72, 0.76, 1.0, 0.075)    # стекло 1: карточки
const CARD_HI := Color(0.76, 0.80, 1.0, 0.125) # стекло 2: кнопки, выделение
const SURFACE_4 := Color(0.80, 0.84, 1.0, 0.19)
const LINE := Color(1, 1, 1, 0.10)             # тонкие разделители и кромки
const FIELD := Color(0.02, 0.03, 0.09, 0.42)   # поля ввода — «углубление» в стекле
const TEXT := Color("f5f3ee")                  # тёплый белый
const MUTED := Color("b6b8cc")                 # второстепенный текст (≈9:1 на стекле)
const DIM := Color("8d90ab")                   # подписи, подсказки (≥4.5:1 на стекле)
const RED := Color("ff6b6b")
const YELLOW := Color("ffc857")
const GREEN := Color("5fd38a")
const JAVA := Color("5fd38a")
const BEDROCK := Color("6aaeff")
const AMBER := Color("ffb648")
const ORANGE := Color("ff8a3d")
const ON_ACCENT := Color("1a1206")   # текст на акцентной кнопке

# ---------------------------------------------------------------- отступы (сетка 4/8 dp)
const S1 := 4
const S2 := 8
const S3 := 12
const S4 := 16
const S5 := 24
const S6 := 32

# ---------------------------------------------------------------- шрифты (sp)
const T_HERO := 38      # большие уверенные заголовки экранов (34–40 sp, плотная разрядка)
const T_DISPLAY := 30
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
const R_2XL := 28
const R_BTN := 16       # кнопки и поля

static var last_scroll_ms := -1    # когда последний раз начали листать пальцем (нажатие после этого не считается)
static var font_regular: Font = preload("res://assets/fonts/Montserrat-Medium.ttf")
static var font_bold: Font = preload("res://assets/fonts/Montserrat-Bold.ttf")
static var font_black: Font = preload("res://assets/fonts/Montserrat-ExtraBold.ttf")
static var font_mono: Font = preload("res://assets/fonts/JetBrainsMono-Regular.ttf")
static var font_caps: FontVariation
static var font_title: FontVariation      # крупные заголовки: ExtraBold с плотной разрядкой
static var font_num: FontVariation        # номера комнат, счётчики: табличные цифры одной ширины

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
	font_caps.set_spacing(TextServer.SPACING_GLYPH, 2)
	font_title = FontVariation.new()
	font_title.base_font = font_black
	font_title.set_spacing(TextServer.SPACING_GLYPH, -1)
	font_num = FontVariation.new()
	font_num.base_font = font_bold
	font_num.opentype_features = {TextServerManager.get_primary_interface().name_to_tag("tnum"): 1}
	font_num.set_spacing(TextServer.SPACING_GLYPH, 1)


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


## Поверхность с мягкой тенью (плавающие элементы простой формы: круги, «таблетки»).
static func elevated(color: Color, radius := R_L, pad := 0) -> StyleBoxFlat:
	var s := box(color, radius, pad)
	s.shadow_color = Color(0, 0, 0, 0.45)
	s.shadow_size = 18
	s.shadow_offset = Vector2(0, 6)
	return s


# ---------------------------------------------------------------- стекло (9-patch из tools/make_ui.py)

const GLASS_M := 20     # поле под тень вокруг панели в текстуре (dp)
const TEX_K := 3.0      # текстуры нарисованы в 3×
static var _tex := {}


## Текстура интерфейса из assets/ui с «логическим» размером (в dp): на плотных экранах остаётся чёткой.
static func ui_tex(name: String) -> Texture2D:
	if not _tex.has(name):
		var src: Texture2D = load("res://assets/ui/%s.png" % name)
		var img := src.get_image()
		if img.is_compressed():
			img.decompress()
		var t := ImageTexture.create_from_image(img)
		t.set_size_override(Vector2i(Vector2(img.get_size()) / TEX_K))
		_tex[name] = t
	return _tex[name]


static func _nine(name: String, radius: int, pad: int, expand := true) -> StyleBoxTexture:
	var s := StyleBoxTexture.new()
	s.texture = ui_tex(name)
	var m := GLASS_M + radius + 1
	s.set_texture_margin_all(m)
	if expand:
		s.set_expand_margin_all(GLASS_M)
	s.set_content_margin_all(pad)
	return s


## Стекло: полупрозрачная поверхность со светлой кромкой сверху, внутренним бликом и большой мягкой тенью.
## level 1..3 — высота (карточка в списке · выделенная карточка · плавающая панель); tint — подкрасить (режим, акцент).
static func glass(level := 1, radius := R_XL, pad := 0, tint := Color(0, 0, 0, 0)) -> StyleBoxTexture:
	var r := 24 if radius >= 20 else 16
	var s := _nine("glass_%d_%d" % [r, clampi(level, 1, 3)], r, pad)
	if tint.a > 0.0:
		s.modulate_color = glass_tint(tint)
	return s


## Цвет для подкраски стекла: светлая версия оттенка (заливка у стекла почти белая — умножение даёт нужный тон).
static func glass_tint(c: Color) -> Color:
	var t := Color.from_hsv(c.h, clampf(c.s * 0.75, 0.0, 0.7), 1.0)
	return Color(1, 1, 1).lerp(t, 0.85)


## Плотное тёмное стекло: листы, диалоги, всплывашки, окно поверх игры — то, под чем лежит другой контент.
static func solid(radius := R_XL, pad := 0) -> StyleBoxTexture:
	var r := 24 if radius >= 20 else 16
	return _nine("solid_%d" % r, r, pad)


## Тёплое свечение вокруг элемента (активная карточка, главная кнопка). Добавляется ребёнком позади содержимого.
static func halo(c: Control, color: Color, radius := R_XL, strength := 0.9, breathe := false) -> Panel:
	var r := 24 if radius >= 20 else 16
	var h := Panel.new()
	var st := _nine("halo_%d" % r, r, 0)
	st.modulate_color = Color(color, strength)
	h.add_theme_stylebox_override("panel", st)
	h.mouse_filter = Control.MOUSE_FILTER_IGNORE
	# через Node2D: контейнер (PanelContainer) не раскладывает свечение как содержимое и не сдвигает его на отступы
	var holder := Node2D.new()
	holder.show_behind_parent = true
	c.add_child(holder)
	holder.add_child(h)
	h.size = c.size
	c.resized.connect(func(): if is_instance_valid(h): h.size = c.size)
	if breathe and FX.on():
		var tw := h.create_tween().set_loops(3).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
		tw.tween_property(h, "modulate:a", 0.45, 1.1)
		tw.tween_property(h, "modulate:a", 1.0, 1.1)
	return h


static func _hpad(s: StyleBox, h: int, v: int) -> StyleBox:
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
	t.set_stylebox("normal", "Button", _hpad(box(CARD_HI, R_BTN, 0, LINE, 1), 16, 10))
	t.set_stylebox("hover", "Button", _hpad(box(SURFACE_4, R_BTN, 0, Color(1, 1, 1, 0.16), 1), 16, 10))
	t.set_stylebox("pressed", "Button", _hpad(box(Color(CARD_HI, 0.08), R_BTN, 0, LINE, 1), 16, 10))
	t.set_stylebox("hover_pressed", "Button", _hpad(box(Color(CARD_HI, 0.08), R_BTN, 0, LINE, 1), 16, 10))
	t.set_stylebox("disabled", "Button", _hpad(box(Color(CARD_HI, 0.05), R_BTN, 0, Color(1, 1, 1, 0.05), 1), 16, 10))
	t.set_stylebox("focus", "Button", box(Color(0, 0, 0, 0), R_M, 0, Color(a, 0.6) if not Cfg.mobile() else Color(0, 0, 0, 0), 2))
	if Cfg.mobile():
		# на телефоне нет «наведения»: после касания кнопка не остаётся подсвеченной
		t.set_stylebox("hover", "Button", _hpad(box(CARD_HI, R_BTN, 0, LINE, 1), 16, 10))
		t.set_color("font_hover_color", "Button", TEXT)
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
		t.set_stylebox("normal", cls, _hpad(box(FIELD, R_BTN, 0, Color(1, 1, 1, 0.13), 1), 16, 10))
		t.set_stylebox("focus", cls, _hpad(box(FIELD, R_BTN, 0, a, 2), 16, 10))
		t.set_stylebox("read_only", cls, _hpad(box(Color(FIELD, 0.25), R_BTN, 0, LINE, 1), 16, 10))
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
	t.set_stylebox("normal", "OptionButton", _hpad(box(FIELD, R_BTN, 0, Color(1, 1, 1, 0.13), 1), 16, 10))
	t.set_stylebox("hover", "OptionButton", _hpad(box(Color(FIELD, 0.3), R_BTN, 0, Color(1, 1, 1, 0.2), 1), 16, 10))
	t.set_stylebox("pressed", "OptionButton", _hpad(box(FIELD, R_BTN, 0, a, 1), 16, 10))
	t.set_stylebox("disabled", "OptionButton", _hpad(box(Color(FIELD, 0.2), R_BTN, 0, LINE, 1), 16, 10))
	t.set_stylebox("focus", "OptionButton", box(Color(0, 0, 0, 0), R_BTN, 0, a, 2))
	t.set_font("font", "OptionButton", font_regular)
	t.set_color("font_color", "OptionButton", TEXT)
	t.set_stylebox("panel", "PopupMenu", box(Color("1a1d38"), R_L, 8, Color(1, 1, 1, 0.14), 1))
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
	t.set_stylebox("panel", "TooltipPanel", _hpad(box(Color("23274a"), R_S, 0, Color(1, 1, 1, 0.14), 1), 10, 6))
	t.set_stylebox("panel", "PanelContainer", glass(1, R_XL, 0))
	t.set_stylebox("slider", "HSlider", box(Color(1, 1, 1, 0.08), 4, 3))
	t.set_stylebox("grabber_area", "HSlider", box(a, 4, 3))
	t.set_stylebox("grabber_area_highlight", "HSlider", box(a.lightened(0.1), 4, 3))
	t.set_icon("grabber", "HSlider", _dot(11, Color.WHITE))
	t.set_icon("grabber_highlight", "HSlider", _dot(12, Color.WHITE))
	t.set_stylebox("panel", "Window", box(PANEL, R_L, 18, LINE, 1))
	t.set_stylebox("embedded_border", "Window", box(PANEL, R_L, 18, LINE, 1))
	t.set_color("font_color", "Label", TEXT)
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
	var track := ("#%s" % a.to_html(false)) if on else "#3b3f63"
	var knob := '<circle cx="36" cy="16" r="12" fill="#fffaf0"/>' if on else '<circle cx="16" cy="16" r="9" fill="#b6b8cc"/>'
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
	l.custom_minimum_size.x = 72 if compact() else 120     # 1.8.2: узкий телефон (320 dp) — абзац рядом с кнопкой не распирает строку
	return l


static func h1(text: String) -> Label:
	_fallbacks()
	var l := label(text, T_DISPLAY, TEXT, font_title)
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.custom_minimum_size.x = 120
	l.add_theme_constant_override("line_spacing", -4)
	return l


## Главный заголовок экрана: огромный, уверенный, с плотной разрядкой.
static func hero(text: String, size := T_HERO) -> Label:
	_fallbacks()
	var l := label(text, size, TEXT, font_title)
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.custom_minimum_size.x = 120
	l.add_theme_constant_override("line_spacing", -6)
	return l


## Число табличными цифрами (номер комнаты, «3/10»).
static func num(text: String, size := T_LABEL, color := MUTED) -> Label:
	_fallbacks()
	return label(text, size, color, font_num)


static func h2(text: String) -> Label:
	_fallbacks()
	var l := label(text, T_TITLE if compact() else T_TITLE + 2, TEXT, font_title)
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART     # 1.8.2: длинное название (сезон на испанском) — перенос, а не шире экрана
	return l


static func h3(text: String) -> Label:
	return label(text, T_HEAD, TEXT, font_bold)


## Подпись раздела: «ДРУЗЬЯ · 3» (капс с разрядкой).
static func caption(text: String, color := DIM) -> Label:
	_fallbacks()
	return label(text.to_upper(), T_CAPTION, color, font_caps)


static func section(text: String, action := "", cb := Callable()) -> Control:
	var r := hbox(8)
	var c := caption(text)
	c.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART      # 1.8.2: «CÓMO ABRIR UN MUNDO A TUS AMIGOS» на 320 dp
	c.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	c.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	r.add_child(c)
	if action != "":
		var b := button(action, "link", cb)
		b.custom_minimum_size.y = 32
		r.add_child(b)
	var m := MarginContainer.new()
	m.add_theme_constant_override("margin_top", 10)
	m.add_theme_constant_override("margin_left", 6)
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
			# градиент янтарь → оранжевый с бликом сверху и тёплым свечением (текстура умножается на акцент)
			for st in [["normal", "primary_16", a], ["hover", "primary_16", a.lightened(0.12)], ["pressed", "primary_16_pressed", a], ["hover_pressed", "primary_16_pressed", a]]:
				var sb := _nine(st[1], 16, 0)
				sb.modulate_color = st[2]
				_hpad(sb, 20, 10)
				b.add_theme_stylebox_override(st[0], sb)
			for k in ["font_color", "font_hover_color", "font_pressed_color", "font_hover_pressed_color", "font_focus_color"]:
				b.add_theme_color_override(k, ON_ACCENT)
			b.add_theme_color_override("font_disabled_color", DIM)
			b.add_theme_stylebox_override("disabled", _hpad(box(Color(CARD_HI, 0.07), R_BTN, 0, LINE, 1), 20, 10))
			b.add_theme_font_override("font", font_black)
			b.add_theme_font_size_override("font_size", T_BODY + 1)
			b.custom_minimum_size.y = touch_h() + 4
			fc = ON_ACCENT
			FX.sheen(b)
		"tonal":
			fc = a.lightened(0.25)
			_btn_colors(b, Color(a, 0.15), Color(a, 0.22), Color(a, 0.3), fc, Color(fc, 0.4))
		"danger":
			fc = Color("ff9d9d")
			_btn_colors(b, Color(RED, 0.14), Color(RED, 0.22), Color(RED, 0.32), fc, Color(fc, 0.4))
		"outline":
			for st in [["normal", 0.0], ["hover", 0.05], ["pressed", 0.1], ["hover_pressed", 0.1], ["disabled", 0.0]]:
				b.add_theme_stylebox_override(st[0], _hpad(box(Color(1, 1, 1, st[1]), R_BTN, 0, Color(1, 1, 1, 0.2), 1), 16, 10))
		"ghost", "link":
			fc = MUTED if kind == "ghost" else a
			for st in [["normal", 0.0], ["hover", 0.06], ["pressed", 0.1], ["hover_pressed", 0.1], ["disabled", 0.0]]:
				b.add_theme_stylebox_override(st[0], _hpad(box(Color(1, 1, 1, st[1]), R_BTN), 12, 8))
			b.add_theme_color_override("font_color", fc)
			b.add_theme_color_override("font_hover_color", TEXT if kind == "ghost" else a.lightened(0.15))
			b.add_theme_color_override("font_pressed_color", TEXT if kind == "ghost" else a)
	if Cfg.mobile() and b.has_theme_stylebox_override("normal"):
		# телефон: без «наведения» — после касания кнопка не остаётся подсвеченной
		b.add_theme_stylebox_override("hover", b.get_theme_stylebox("normal"))
		b.add_theme_color_override("font_hover_color", b.get_theme_color("font_color"))
	if icon_name != "":
		b.icon = icon_tex(icon_name, 20, fc)
	if cb.is_valid():
		b.pressed.connect(cb)
	FX.press(b)
	return b


static func _btn_colors(b: Button, n: Color, h: Color, p: Color, fc: Color, dis: Color) -> void:
	var bc := Color(fc, 0.22)
	b.add_theme_stylebox_override("normal", _hpad(box(n, R_BTN, 0, bc, 1), 18, 10))
	b.add_theme_stylebox_override("hover", _hpad(box(h, R_BTN, 0, bc, 1), 18, 10))
	b.add_theme_stylebox_override("pressed", _hpad(box(p, R_BTN, 0, bc, 1), 18, 10))
	b.add_theme_stylebox_override("hover_pressed", _hpad(box(p, R_BTN, 0, bc, 1), 18, 10))
	b.add_theme_stylebox_override("disabled", _hpad(box(Color(n, n.a * 0.4) if n.a < 1 else Color(n, 0.3), R_BTN), 18, 10))
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
	b.custom_minimum_size = Vector2(44, 44) if compact() else Vector2(0, 36)     # 1.8.2: палец — не меньше 44 dp
	b.add_theme_font_size_override("font_size", 14)
	chip_set(b, on)
	if cb.is_valid():
		b.pressed.connect(cb)
	FX.press(b)
	return b


static func chip_set(b: Button, on: bool) -> void:
	var a := accent()
	var n := box(Color(a, 0.20) if on else Color(1, 1, 1, 0.06), 99, 0, Color(a, 0.7) if on else Color(1, 1, 1, 0.12), 1)
	if on:
		n.shadow_color = Color(a, 0.22)
		n.shadow_size = 8
	_hpad(n, 16, 6)
	b.add_theme_stylebox_override("normal", n)
	var h: StyleBoxFlat = n.duplicate()
	h.bg_color = Color(a, 0.28) if on else Color(1, 1, 1, 0.11)
	b.add_theme_stylebox_override("hover", h)
	b.add_theme_stylebox_override("pressed", h)
	b.add_theme_stylebox_override("hover_pressed", h)
	b.add_theme_stylebox_override("focus", box(Color(0, 0, 0, 0), 99))
	for k in ["font_color", "font_hover_color", "font_pressed_color", "font_hover_pressed_color", "font_focus_color"]:
		b.add_theme_color_override(k, a.lightened(0.35) if on else MUTED)
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
	p.add_theme_stylebox_override("panel", box(FIELD, R_BTN + 2, 4, Color(1, 1, 1, 0.12), 1))
	var holder := Control.new()
	holder.custom_minimum_size.y = 44 if compact() else 42
	p.add_child(holder)
	var pill_bg := Panel.new()
	var pst := box(Color(accent(), 0.22), R_BTN - 2, 0, Color(accent(), 0.6), 1)
	pst.shadow_color = Color(accent(), 0.2)
	pst.shadow_size = 8
	pill_bg.add_theme_stylebox_override("panel", pst)
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
		b.clip_text = true        # узкий экран (360 dp): длинная подпись обрезается, а не распирает экран
		b.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		for st in ["normal", "hover", "pressed", "hover_pressed", "focus"]:
			b.add_theme_stylebox_override(st, box(Color(0, 0, 0, 0), R_M - 2))
		fit_font(b, 14 if compact() else T_BODY, 10)
		# подсветка переезжает сама: раньше часть экранов забывала segmented_select — выбор менялся, а выглядело, будто кнопка не работает
		b.pressed.connect(func(): segmented_select(p, it[0]); cb.call(it[0]))
		d[it[0]] = b
		row.add_child(b)
	p.set_meta("tabs", d)
	p.set_meta("pill", pill_bg)
	p.set_meta("key", current)
	holder.resized.connect(func(): segmented_select(p, p.get_meta("key"), false))
	segmented_select(p, current, false)
	return p


## 1.8.2: подпись кнопки/вкладки с clip_text уменьшается (max → min), чтобы влезть целиком, а не «Досягнен…»;
## не влезла и мелким шрифтом — убираем иконку кнопки.
static func fit_font(b: Button, max_fs: int, min_fs: int) -> void:
	b.set_meta("fit_fs", Vector2i(max_fs, min_fs))
	b.add_theme_font_size_override("font_size", max_fs)
	b.resized.connect(_fit_font_now.bind(b))


static func _fit_font_now(b: Button) -> void:
	if not is_instance_valid(b) or b.size.x < 2.0:
		return
	var fs: Vector2i = b.get_meta("fit_fs")
	var font := b.get_theme_font("font")
	var txt := b.atr(b.text)
	var sb := b.get_theme_stylebox("normal")
	var pad := sb.get_minimum_size().x if sb else 0.0
	var ic := (b.icon.get_width() + b.get_theme_constant("h_separation")) if b.icon else 0.0
	var k := fs.x
	while k > fs.y and font.get_string_size(txt, HORIZONTAL_ALIGNMENT_LEFT, -1, k).x + pad + ic > b.size.x - 1.0:
		k -= 1
	if b.icon and font.get_string_size(txt, HORIZONTAL_ALIGNMENT_LEFT, -1, k).x + pad + ic > b.size.x - 1.0:
		b.icon = null
	if b.get_theme_font_size("font_size") != k:
		b.add_theme_font_size_override("font_size", k)


static func segmented_select(p: Control, key: String, animate := true) -> void:
	var d: Dictionary = p.get_meta("tabs", {})
	p.set_meta("key", key)
	for k in d:
		var on: bool = k == key
		var b: Button = d[k]
		for c in ["font_color", "font_hover_color", "font_pressed_color", "font_hover_pressed_color"]:
			b.add_theme_color_override(c, accent().lightened(0.45) if on else MUTED)
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
static func panel(pad := 20, color := CARD, radius := R_XL) -> PanelContainer:
	pad = mini(pad, 16) if compact() else mini(pad, 24)
	var p := PanelContainer.new()
	if color == CARD or color == PANEL:
		p.add_theme_stylebox_override("panel", glass(1, radius, pad))
	elif color.a < 0.2:
		# едва заметная подложка (заметки, подсказки) — тихое стекло без тени
		p.add_theme_stylebox_override("panel", box(Color(1, 1, 1, 0.04), radius, pad, Color(1, 1, 1, 0.07), 1))
	else:
		# «цветная» карточка старого кода (зелёная — всё хорошо, янтарная — внимание): стекло с оттенком
		p.add_theme_stylebox_override("panel", glass(2, radius, pad, color))
	return p


## Карточка-стекло: level 1..3, tint — оттенок (акцент, цвет режима).
static func glass_panel(level := 1, pad := S4, tint := Color(0, 0, 0, 0), radius := R_XL) -> PanelContainer:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", glass(level, radius, pad, tint))
	return p


static func card(pad := S4, color := CARD, radius := R_L) -> PanelContainer:
	return panel(pad, color, radius)


## Строка списка: [иконка] заголовок / подзаголовок [справа]. Нажимается целиком, если передан cb.
static func list_row(lead: Control, title: String, sub := "", trail: Control = null, cb := Callable(), color := CARD) -> PanelContainer:
	var p := PanelContainer.new()
	var st: StyleBox
	if color.a <= 0.0:
		st = box(color, R_L, 0)
	elif color == CARD:
		st = glass(1, R_XL, 0)
	else:
		st = glass(2, R_XL, 0, color)
	_hpad(st, 14, 12)
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
	# 1.8.2: заголовок строки — до 2 строк («Умови використання» рядом с кнопкой на 360 dp), а не «Умови вико…»
	tl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	tl.max_lines_visible = 2
	tl.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	v.add_child(tl)
	if sub != "":
		var sl := label(sub, T_LABEL, MUTED)
		# 1.8.2: подпись переносится (до 3 строк), а не обрезается: «Надіслати журнал розробникові — отримаєте номер…»
		sl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		sl.max_lines_visible = 3
		sl.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		v.add_child(sl)
	row.add_child(v)
	p.set_meta("title", tl)
	p.set_meta("text", v)
	p.set_meta("row", row)
	if trail:
		trail.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		row.add_child(trail)
	p.custom_minimum_size.y = 68
	if cb.is_valid():
		tappable(p, cb, st)
	return p


static func mark_scroll() -> void:
	last_scroll_ms = Time.get_ticks_msec()


## Палец листал после нажатия — это прокрутка, а не нажатие.
static func scrolled_since(ms: int) -> bool:
	return last_scroll_ms >= ms


## Делает панель нажимаемой (подсветка при наведении и нажатии; прокрутку пальцем не считает нажатием).
static func tappable(p: Control, cb: Callable, st: StyleBox = null) -> void:
	# PASS: палец, начавший на карточке, листает список (на телефоне)
	p.mouse_filter = Control.MOUSE_FILTER_PASS if Cfg.mobile() else Control.MOUSE_FILTER_STOP
	p.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	var flat := st as StyleBoxFlat
	var tex := st as StyleBoxTexture
	var base := flat.bg_color if flat else (tex.modulate_color if tex else Color())
	# стекло при наведении и нажатии «подсвечивается»: заливка ярче (у текстуры — множитель цвета)
	var lit := func(k: float):
		if flat:
			flat.bg_color = Color(base.r, base.g, base.b, minf(1.0, base.a + k)) if base.a < 1.0 else base.lightened(k)
		elif tex:
			tex.modulate_color = Color(base.r * (1.0 + k * 3.0), base.g * (1.0 + k * 3.0), base.b * (1.0 + k * 3.0), base.a)
	p.set_meta("tap", cb)      # автотесты нажимают карточку через это
	var down := [Vector2.ZERO, 0]
	if not Cfg.mobile():
		p.mouse_entered.connect(func(): lit.call(0.05))
		p.mouse_exited.connect(func(): lit.call(0.0))
	p.gui_input.connect(func(e):
		if e is InputEventMouseButton and e.button_index == MOUSE_BUTTON_LEFT:
			if e.pressed:
				down[0] = e.position
				down[1] = Time.get_ticks_msec()
				lit.call(0.10)
				FX.ripple(p, e.position)
			else:
				lit.call(0.0)
				if e.position.distance_to(down[0]) < 12 and Rect2(Vector2.ZERO, p.size).has_point(e.position) and not scrolled_since(down[1]):
					FX.hero_mark(p)
					Sfx.play("tap")
					cb.call())
	FX.press(p, 0.98)


static func icon_tile(name: String, color: Color, px := 44, ic := 22) -> PanelContainer:
	var t := PanelContainer.new()
	t.custom_minimum_size = Vector2(px, px)
	t.add_theme_stylebox_override("panel", box(Color(color, 0.16), int(px * 0.32), 0, Color(color, 0.32), 1))
	var i := UIcon.make(name, ic, color.lightened(0.25))
	i.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	i.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	t.add_child(i)
	t.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return t


## Пустое состояние: картинка + одна строка + действие.
static func empty(icon_name: String, text: String, action := "", cb := Callable(), sub := "") -> Control:
	var v := vbox(12)
	v.alignment = BoxContainer.ALIGNMENT_CENTER
	# иллюстрация: маленький ночной пейзаж в нашем стиле + значок в светящемся круге
	var art := Control.new()
	art.custom_minimum_size = Vector2(220, 132)
	art.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	art.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var cov := Cover.make(icon_name + text, "other", R_XL)
	cov.set_anchors_preset(Control.PRESET_FULL_RECT)
	art.add_child(cov)
	var ring := PanelContainer.new()
	var rst := box(Color(0.03, 0.04, 0.10, 0.55), 99, 0, Color(accent(), 0.8), 2)
	rst.shadow_color = Color(accent(), 0.35)
	rst.shadow_size = 16
	ring.add_theme_stylebox_override("panel", rst)
	ring.custom_minimum_size = Vector2(64, 64)
	ring.position = Vector2(110 - 32, 66 - 32)
	ring.size = Vector2(64, 64)
	var ic := UIcon.make(icon_name, 30, accent().lightened(0.3))
	ic.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	ic.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	ring.add_child(ic)
	art.add_child(ring)
	v.add_child(gap(8))
	v.add_child(art)
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
	t.add_child(caption(L.t("Шаг %d из %d") % [cur, total], accent()))
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
			fill.create_tween().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT).tween_property(fill, "anchor_right", 1.0 if full else 0.0, 0.2)
		bars.add_child(track)
	v.add_child(bars)
	return v


## Большая карточка выбора (мастера): иконка, заголовок, пояснение, отметка выбора.
static func choice(icon_name: String, color: Color, title: String, desc: String, on: bool, cb: Callable) -> PanelContainer:
	var p := PanelContainer.new()
	var st: StyleBox = glass(2 if on else 1, R_XL, 0, color if on else Color(0, 0, 0, 0))
	_hpad(st, 16, 16)
	p.add_theme_stylebox_override("panel", st)
	if on:
		halo(p, color, R_XL, 0.8)
	var row := hbox(14)
	p.add_child(row)
	row.add_child(icon_tile(icon_name, color, 52, 26))
	var v := vbox(4)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var ctl := para(title, T_HEAD, TEXT)
	ctl.add_theme_font_override("font", font_bold)
	v.add_child(ctl)
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
	p.add_theme_stylebox_override("panel", _hpad(box(Color(color, 0.16), 99, 0, Color(color, 0.3), 1), 9, 2))
	var l := label(text, T_CAPTION - 1, color.lightened(0.4), font_bold)
	p.add_child(l)
	p.set_meta("label", l)
	return p


## Счётчик (непрочитанные, заявки).
static func badge(n: int) -> PanelContainer:
	var p := PanelContainer.new()
	var bst := box(RED, 99)
	bst.shadow_color = Color(RED, 0.5)
	bst.shadow_size = 6
	p.add_theme_stylebox_override("panel", _hpad(bst, 6, 1))
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
	b.custom_minimum_size.y = 44 if compact() else 36     # 1.8.2: палец — 44 dp
	b.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	b.add_theme_stylebox_override("normal", _hpad(box(Color(AMBER, 0.14), 99, 0, Color(AMBER, 0.35), 1), 12, 4))
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
	var v := vbox(8)
	var cl := caption(caption_text, MUTED)
	var cm := MarginContainer.new()
	cm.add_theme_constant_override("margin_left", 6)
	cm.add_child(cl)
	v.add_child(cm)
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
	# вся строка нажимается, не только сам переключатель (без срабатывания при прокрутке пальцем)
	v.mouse_filter = Control.MOUSE_FILTER_PASS
	v.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	var st := {"at": Vector2.INF}
	v.gui_input.connect(func(e: InputEvent):
		if e is InputEventMouseButton and e.button_index == MOUSE_BUTTON_LEFT:
			if e.pressed:
				st.at = e.global_position
			elif st.at != Vector2.INF and st.at.distance_to(e.global_position) < 12.0 and not sw.disabled:
				sw.button_pressed = not sw.button_pressed
				st.at = Vector2.INF)
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


static var copy_count := 0
static var open_log: Array = []     # что открывали в системе (автотесты: вместо настоящего открытия)


static func copy(text: String) -> void:
	copy_count += 1
	DisplayServer.clipboard_set(text)


## Открыть ссылку или папку в системе. В автотестах ничего не открываем — только записываем.
static func open(what: String) -> Error:
	open_log.append(what)
	if Cfg.autotest != "":
		return OK
	return OS.shell_open(what)


## Связь тремя столбиками (как сигнал сети): great — 3, good — 2, poor — 1; неизвестно — тусклые.
class Bars extends Control:
	var level := 0
	var color := Color.WHITE

	func _draw() -> void:
		for i in 3:
			var h := 5.0 + i * 4.5
			var r := Rect2(i * 6.0, size.y - h - 1.0, 4.0, h)
			var sb := StyleBoxFlat.new()
			sb.bg_color = color if i < level else Color(1, 1, 1, 0.16)
			sb.set_corner_radius_all(2)
			sb.anti_aliasing = true
			draw_style_box(sb, r)


static func signal_bars(quality: String) -> Control:
	var b := Bars.new()
	b.level = {"great": 3, "good": 2, "poor": 1}.get(quality, 0)
	b.color = {"great": GREEN, "good": YELLOW, "poor": RED}.get(quality, DIM)
	b.custom_minimum_size = Vector2(16, 16)
	b.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	b.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return b


## Маленькая цветная точка + подпись («● Выживание») — компактные метки в строке списка.
static func dot_label(text: String, color: Color, size := T_CAPTION) -> HBoxContainer:
	var h := hbox(5)
	h.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var d := Panel.new()
	d.custom_minimum_size = Vector2(7, 7)
	d.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	d.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var st := box(color, 99)
	st.shadow_color = Color(color, 0.6)
	st.shadow_size = 4
	d.add_theme_stylebox_override("panel", st)
	h.add_child(d)
	h.add_child(label(text, size, MUTED, font_bold))
	return h


## Стопка аватаров игроков + «3/10».
static func avatar_stack(names: Array, total: int, cap: int, full := false) -> HBoxContainer:
	var h := hbox(6)
	h.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var st := Control.new()
	var n := mini(names.size(), 3)
	st.custom_minimum_size = Vector2(22 + maxi(0, n - 1) * 14, 22)
	st.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	st.mouse_filter = Control.MOUSE_FILTER_IGNORE
	for i in n:
		var a := Avatar.make(String(names[i]), 22)
		a.position = Vector2(i * 14, 0)
		a.size = Vector2(22, 22)
		a.rim = Color(0.07, 0.08, 0.16)
		st.add_child(a)
	if n > 0:
		h.add_child(st)
	var l := num("%d/%d" % [total, cap], T_LABEL, RED.lightened(0.2) if full else TEXT)
	l.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	h.add_child(l)
	return h
