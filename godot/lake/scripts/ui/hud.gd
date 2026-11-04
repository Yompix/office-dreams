class_name Hud
extends CanvasLayer
## Экран поверх кадра: субтитры (имя и реплика), выбор на время (полоса времени, 1–3, «молчание» — дождаться конца),
## подсказка «Айрис это запомнит», подсказки действий (руль, тормоз), затемнение, надпись главы.

signal chosen(i: int)
signal menu_pick(id: String)     # главное меню и пауза: "new", "cont", "settings", "quit", "resume", "menu"
signal settings_changed(key: String, value: Variant)   # экран настроек: что поменяли (игра применяет сразу)
signal settings_closed
signal tree_closed(cp: String)   # дерево выборов закрыто: "" — назад, иначе — переиграть с этой контрольной точки
var lang := "ru"
var sub_name: Label
var sub_text: Label
var note: Label
var choice_box: VBoxContainer
var timer_bar: ColorRect
var timer_bg: ColorRect
var silent_lbl: Label
var toast: Label
var hint: Label
var prompt_lbl: Label        # прогулка: «E   Поговорить с Дэнни» внизу кадра
var scrim: TextureRect      # мягкое затемнение низа кадра под субтитрами и текстом рассказчика
var fade: ColorRect
var title: Label
var _choice_t := 0.0
var _choice_len := 0.0
var _opts: Array = []
var _toast_t := 0.0
var font: Font
var font_b: Font
var font_hand: Font
var root: Control
var page: Control           # страница дневника крупно (почерк Анны)
var map_panel: Control      # карта главы
var menu_panel: Control     # главное меню / пауза
var settings_panel: Control # экран настроек (поверх меню или паузы)
var tree_panel: Control     # дерево выборов
var _tree_data: Array = []
var _tree_body: ScrollContainer
var _tree_tabs: HBoxContainer
var _menu_kind := ""        # "main" / "pause"
## Телефон (Android или ключ --touch): свой интерфейс, а не ПК-шный с кнопками поверх — всё крупнее (масштаб холста),
## кнопки меню и ответы — плашки под палец, без «Выхода» и подсказок про клавиатуру, настройки — одним списком
## с прокруткой (без экрана, V-Sync и видеорежима), отступы от выреза камеры.
var phone := false
const PHONE_SCALE := 1.3

const NAME_COL := {"noah": Color(0.85, 0.9, 1.0), "iris": Color(1.0, 0.78, 0.6), "radio": Color(0.6, 0.85, 1.0),
	"danny": Color(0.75, 1.0, 0.75), "roman": Color(1.0, 0.92, 0.6)}
var hum := 0.0              # «гул» в голове Ноя (начало провала): края кадра темнеют и пульсируют, 0…1
var hum_to := 0.0
var noise := 0.0            # белый шум провала, 0…1
var vignette: ColorRect
var static_rect: ColorRect
var _ht := 0.0

const VIGNETTE := """
shader_type canvas_item;
uniform float amount = 0.0;
uniform float t = 0.0;
void fragment() {
	vec2 d = UV - 0.5;
	float r = length(d * vec2(1.6, 1.0));
	float pulse = 0.55 + 0.45 * sin(t * 5.5) * sin(t * 1.7 + 1.0);
	float edge = smoothstep(0.35 - amount * 0.12, 0.95, r) * amount * (0.6 + 0.4 * pulse);
	COLOR = vec4(0.12, 0.0, 0.0, clamp(edge * 1.4, 0.0, 0.92));
}
"""
const STATIC := """
shader_type canvas_item;
uniform float amount = 0.0;
uniform float t = 0.0;
float h(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
void fragment() {
	vec2 g = floor(UV * vec2(320.0, 180.0));
	float n = h(g + floor(t * 30.0) * 17.0);
	float band = smoothstep(0.0, 0.08, abs(fract(UV.y * 3.0 - t * 1.3) - 0.5));
	COLOR = vec4(vec3(n * (0.6 + 0.4 * band)), amount);
}
"""


## «Сквозь щель»: тёмные доски по краям кадра, просвет между ними с рваными краями (кадр снят от щели — края дорисованы).
const PEEK := """
shader_type canvas_item;
uniform float amount = 0.0;
uniform float aspect = 1.7778;
float h(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
float n1(float x) { float i = floor(x); float f = fract(x); return mix(h(vec2(i, 1.0)), h(vec2(i + 1.0, 1.0)), smoothstep(0.0, 1.0, f)); }
void fragment() {
	vec2 uv = UV;
	float hw = 0.16 / aspect * 1.7778;
	float l = 0.5 - hw + 0.022 * n1(uv.y * 9.0) + 0.01 * n1(uv.y * 31.0);
	float r = 0.5 + hw - 0.022 * n1(uv.y * 8.0 + 5.0) - 0.01 * n1(uv.y * 29.0 + 3.0);
	float t = 0.07 + 0.035 * n1(uv.x * 7.0);
	float b = 0.93 - 0.035 * n1(uv.x * 6.0 + 2.0);
	float e = 0.012;
	float inside = smoothstep(l - e, l + e, uv.x) * (1.0 - smoothstep(r - e, r + e, uv.x))
		* smoothstep(t - e, t + e, uv.y) * (1.0 - smoothstep(b - e, b + e, uv.y));
	float grain = 0.5 + 0.5 * sin(uv.x * 260.0 + n1(uv.y * 14.0) * 9.0 + n1(uv.x * 40.0) * 3.0);
	vec3 wood = vec3(0.11, 0.08, 0.055) * (0.55 + 0.45 * grain);
	float d = min(min(abs(uv.x - l), abs(uv.x - r)), min(abs(uv.y - t), abs(uv.y - b)));
	vec3 col = wood * (0.18 + 1.4 * exp(-d * 45.0));
	COLOR = vec4(col, (1.0 - inside) * amount);
}
"""
var peek: ColorRect
var page_bg: ColorRect          # «осмотр»: лист блокнота поверх кадра (рисунки Айрис), под субтитрами
var page_rect: TextureRect

func _ready() -> void:
	layer = 10
	process_mode = Node.PROCESS_MODE_ALWAYS   # меню паузы работает, когда игра стоит
	font = load("res://assets/fonts/Inter-500.woff2")
	font_b = load("res://assets/fonts/Inter-700.woff2")
	font_hand = load("res://assets/fonts/Pangolin-Regular.woff2")
	root = Control.new()
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(root)
	fade = ColorRect.new()
	fade.color = Color(0, 0, 0, 0)
	fade.set_anchors_preset(Control.PRESET_FULL_RECT)
	fade.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.add_child(fade)
	vignette = ColorRect.new()
	vignette.set_anchors_preset(Control.PRESET_FULL_RECT)
	vignette.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var vm := ShaderMaterial.new()
	vm.shader = Shader.new()
	vm.shader.code = VIGNETTE
	vignette.material = vm
	root.add_child(vignette)
	root.move_child(vignette, 0)
	peek = ColorRect.new()
	peek.set_anchors_preset(Control.PRESET_FULL_RECT)
	peek.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var pkm := ShaderMaterial.new()
	pkm.shader = Shader.new()
	pkm.shader.code = PEEK
	peek.material = pkm
	peek.visible = false
	root.add_child(peek)
	root.move_child(peek, 1)
	page_bg = ColorRect.new()
	page_bg.color = Color(0.02, 0.02, 0.03, 0.78)
	page_bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	page_bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	page_bg.visible = false
	root.add_child(page_bg)
	root.move_child(page_bg, 2)
	page_rect = TextureRect.new()
	page_rect.set_anchors_preset(Control.PRESET_FULL_RECT)
	page_rect.anchor_top = 0.05
	page_rect.anchor_bottom = 0.8
	page_rect.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	page_rect.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	page_rect.mouse_filter = Control.MOUSE_FILTER_IGNORE
	page_rect.visible = false
	root.add_child(page_rect)
	root.move_child(page_rect, 3)
	static_rect = ColorRect.new()
	static_rect.set_anchors_preset(Control.PRESET_FULL_RECT)
	static_rect.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var sm := ShaderMaterial.new()
	sm.shader = Shader.new()
	sm.shader.code = STATIC
	static_rect.material = sm
	root.add_child(static_rect)
	scrim = TextureRect.new()   # низ кадра чуть темнее, пока есть текст: субтитры читаются и на белом снегу
	var gt := GradientTexture2D.new()
	var gr := Gradient.new()
	gr.set_color(0, Color(0, 0, 0, 0.0))
	gr.set_color(1, Color(0, 0, 0, 0.55))
	gr.add_point(0.5, Color(0, 0, 0, 0.32))
	gt.gradient = gr
	gt.fill_from = Vector2(0, 0)
	gt.fill_to = Vector2(0, 1)
	gt.width = 4
	gt.height = 128
	scrim.texture = gt
	scrim.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	scrim.stretch_mode = TextureRect.STRETCH_SCALE
	scrim.anchor_right = 1.0
	scrim.anchor_top = 0.64
	scrim.anchor_bottom = 1.0
	scrim.mouse_filter = Control.MOUSE_FILTER_IGNORE
	scrim.modulate.a = 0.0
	root.add_child(scrim)
	sub_name = _label(root, 30, font_b, Color.WHITE)
	sub_text = _label(root, 40, font, Color.WHITE)
	var fi := FontVariation.new()   # текст рассказчика — курсивом (наклон шрифта), внизу, как титры: не путается с репликами
	fi.base_font = font
	fi.variation_transform = Transform2D(Vector2(1.0, 0.18), Vector2(0.0, 1.0), Vector2.ZERO)
	note = _label(root, 34, fi, Color(0.93, 0.91, 0.87))
	for l in [sub_name, sub_text, note]:
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		l.anchor_left = 0.12
		l.anchor_right = 0.88
	sub_name.anchor_top = 0.76
	sub_name.anchor_bottom = 0.79
	sub_text.anchor_top = 0.79
	sub_text.anchor_bottom = 0.95
	note.anchor_left = 0.15
	note.anchor_right = 0.85
	note.anchor_top = 0.78
	note.anchor_bottom = 0.95
	note.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	choice_box = VBoxContainer.new()
	choice_box.anchor_left = 0.25
	choice_box.anchor_right = 0.75
	choice_box.anchor_top = 0.62
	choice_box.anchor_bottom = 0.9
	choice_box.add_theme_constant_override("separation", 14)
	root.add_child(choice_box)
	timer_bg = ColorRect.new()
	timer_bg.color = Color(1, 1, 1, 0.12)
	timer_bg.anchor_left = 0.3
	timer_bg.anchor_right = 0.7
	timer_bg.anchor_top = 0.6
	timer_bg.anchor_bottom = 0.6
	timer_bg.offset_bottom = 4
	root.add_child(timer_bg)
	timer_bar = ColorRect.new()
	timer_bar.color = Color(1, 1, 1, 0.85)
	timer_bar.anchor_top = 0.6
	timer_bar.anchor_bottom = 0.6
	timer_bar.offset_bottom = 4
	root.add_child(timer_bar)
	silent_lbl = _label(root, 24, font, Color(0.75, 0.75, 0.78, 0.8))
	silent_lbl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	silent_lbl.anchor_left = 0.3
	silent_lbl.anchor_right = 0.7
	silent_lbl.anchor_top = 0.9
	silent_lbl.anchor_bottom = 0.94
	toast = _label(root, 28, font, Color(1.0, 0.9, 0.75))
	toast.anchor_left = 0.04
	toast.anchor_top = 0.05
	toast.anchor_right = 0.6
	toast.anchor_bottom = 0.1
	toast.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART   # длинная плашка (BootGuard в меню) — в несколько строк, не за край
	hint = _label(root, 34, font_b, Color.WHITE)
	hint.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	hint.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART   # длинная подсказка (24 квадрата) — в две строки, а не за край
	hint.anchor_left = 0.1
	hint.anchor_right = 0.9
	hint.anchor_top = 0.1
	hint.anchor_bottom = 0.24
	prompt_lbl = _label(root, 30, font_b, Color(1.0, 0.97, 0.92))
	prompt_lbl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	prompt_lbl.anchor_left = 0.2
	prompt_lbl.anchor_right = 0.8
	prompt_lbl.anchor_top = 0.885
	prompt_lbl.anchor_bottom = 0.94
	prompt_lbl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART   # длинная подсказка — в две строки, а не за край экрана
	prompt_lbl.grow_vertical = Control.GROW_DIRECTION_BEGIN
	prompt_lbl.modulate.a = 0.0
	title = _label(root, 64, font_b, Color.WHITE)
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	title.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	title.set_anchors_preset(Control.PRESET_FULL_RECT)
	clear_choice()


## Включить телефонный интерфейс (game.gd — один раз при старте).
func set_phone(on: bool) -> void:
	phone = on
	if not on:
		return
	get_tree().root.content_scale_factor = PHONE_SCALE   # весь интерфейс на 30 % крупнее; раскладка — по долям экрана
	toast.anchor_left = 0.08   # подальше от выреза камеры
	choice_box.anchor_left = 0.08
	choice_box.anchor_right = 0.92
	prompt_lbl.anchor_left = 0.24
	prompt_lbl.anchor_right = 0.76   # между джойстиком и кнопкой «Осмотреть»
	prompt_lbl.anchor_top = 0.80
	prompt_lbl.anchor_bottom = 0.86


## Плашка под палец (телефон): заметная и без наведения мышью.
func _phone_box(alpha := 0.42) -> StyleBoxFlat:
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0.04, 0.05, 0.07, alpha)
	sb.border_color = Color(1, 1, 1, 0.16)
	sb.set_border_width_all(2)
	sb.set_corner_radius_all(14)
	sb.content_margin_left = 30
	sb.content_margin_right = 30
	sb.content_margin_top = 16
	sb.content_margin_bottom = 16
	return sb


func _label(parent: Control, size: int, f: Font, col: Color) -> Label:
	var l := Label.new()
	l.add_theme_font_override("font", f)
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", col)
	l.add_theme_color_override("font_outline_color", Color(0, 0, 0, 0.85))
	l.add_theme_constant_override("outline_size", 8)
	l.mouse_filter = Control.MOUSE_FILTER_IGNORE
	parent.add_child(l)
	return l


func T(d) -> String:
	if d is Dictionary:
		return String(d.get(lang, d.get("ru", "")))
	return String(d) if d != null else ""


func line(who: String, name_d, text_d) -> void:
	sub_name.text = T(name_d)
	sub_name.add_theme_color_override("font_color", NAME_COL.get(who, Color.WHITE))
	sub_text.text = T(text_d)
	note.text = ""
	_fit_subs()


## Высота реплики по числу строк: текст прижат к низу (0,95 экрана) и растёт вверх, имя — сразу над ним
## (крупные субтитры: длинная реплика в 4 строки уходила за нижний край, имя налезало на текст).
func _fit_subs() -> void:
	var vs := get_viewport().get_visible_rect().size
	if vs.y <= 0.0 or sub_text.text == "":
		return
	var fs := sub_text.get_theme_font_size("font_size")
	var w := vs.x * (sub_text.anchor_right - sub_text.anchor_left)
	var h := font.get_multiline_string_size(sub_text.text, HORIZONTAL_ALIGNMENT_CENTER, w, fs).y + 8.0
	var top := clampf(0.95 - h / vs.y, 0.5, 0.79)
	sub_text.anchor_top = top
	sub_text.anchor_bottom = 0.95
	sub_text.vertical_alignment = VERTICAL_ALIGNMENT_BOTTOM
	var nh := float(sub_name.get_theme_font_size("font_size")) * 1.25
	sub_name.anchor_bottom = top
	sub_name.anchor_top = top - nh / vs.y


## Текст рассказчика: поверх кадра — внизу курсивом, как титры; на чёрном экране (пересказ «Ранее…») — посередине.
func show_note(text_d, center := false) -> void:
	note.text = T(text_d)
	sub_name.text = ""
	sub_text.text = ""
	if page_rect != null and page_rect.visible:
		return   # под листом блокнота — место задаёт page_view
	note.anchor_top = 0.4 if center else 0.78
	note.anchor_bottom = 0.6 if center else 0.95


## Страница дневника крупно: бумага в линейку, почерк от руки (чернила), чуть наискосок.
func show_page(text: String) -> void:
	hide_page()
	page = Control.new()
	page.set_anchors_preset(Control.PRESET_FULL_RECT)
	page.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.add_child(page)
	root.move_child(page, root.get_child_count() - 1)
	var w := 960
	var h := 640
	var img := Image.create(w, h, false, Image.FORMAT_RGBA8)
	var rng := RandomNumberGenerator.new()
	rng.seed = 3
	for y in h:
		for x in w:
			var e := minf(minf(x, w - 1 - x), minf(y, h - 1 - y)) / 60.0
			var age := 0.82 + 0.18 * clampf(e, 0.0, 1.0)
			var c := Color(0.93, 0.9, 0.82) * age + Color(rng.randf_range(-0.015, 0.015), rng.randf_range(-0.015, 0.015), 0.0, 0.0)
			if y > 70 and (y - 70) % 46 == 0:
				c = c.lerp(Color(0.5, 0.62, 0.8), 0.45)
			if x == 110 or x == 112:
				c = c.lerp(Color(0.85, 0.35, 0.35), 0.35)
			c.a = 1.0
			img.set_pixel(x, y, c)
	var tr := TextureRect.new()
	tr.texture = ImageTexture.create_from_image(img)
	tr.size = Vector2(w, h)
	tr.pivot_offset = Vector2(w, h) * 0.5
	tr.rotation = deg_to_rad(-2.0)
	var vs := get_viewport().get_visible_rect().size
	tr.position = (vs - Vector2(w, h)) * 0.5 - Vector2(0, 40)
	tr.mouse_filter = Control.MOUSE_FILTER_IGNORE
	page.add_child(tr)
	var l := Label.new()
	l.text = text
	l.add_theme_font_override("font", font_hand)
	l.add_theme_font_size_override("font_size", 52)
	l.add_theme_color_override("font_color", Color(0.1, 0.14, 0.36))
	l.add_theme_constant_override("line_spacing", 6)
	l.position = Vector2(150, 105)
	l.size = Vector2(w - 220, h - 160)
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.mouse_filter = Control.MOUSE_FILTER_IGNORE
	tr.add_child(l)
	page.modulate.a = 0.0
	create_tween().tween_property(page, "modulate:a", 1.0, 0.6)


func hide_page() -> void:
	if is_instance_valid(page):
		page.queue_free()
	page = null


## Карта главы: строки «что решили» — выбранное ярко, виденное раньше — серым, ещё не открытое — «???»;
## внизу — сколько веток открыто и доверие Айрис. rows: [{"name": …, "opts": [текст…], "pick": i, "seen": [bool…]}].
func chapter_map(head: String, rows: Array, opened: int, total: int, trust: int) -> void:
	hide_map()
	map_panel = ColorRect.new()
	(map_panel as ColorRect).color = Color(0.01, 0.012, 0.018, 0.96)
	map_panel.set_anchors_preset(Control.PRESET_FULL_RECT)
	map_panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.add_child(map_panel)
	var t := _label(map_panel, 46, font_b, Color(0.92, 0.94, 1.0))
	t.text = head
	t.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	t.anchor_left = 0.05
	t.anchor_right = 0.95
	t.anchor_top = 0.07
	t.anchor_bottom = 0.15
	var box := VBoxContainer.new()
	box.anchor_left = 0.12
	box.anchor_right = 0.92
	box.anchor_top = 0.22
	box.anchor_bottom = 0.85
	box.add_theme_constant_override("separation", 34)
	map_panel.add_child(box)
	for r in rows:
		var row := HBoxContainer.new()
		row.add_theme_constant_override("separation", 26)
		box.add_child(row)
		var n := _label(row, 36, font_b, Color(0.62, 0.66, 0.74))
		n.text = String(r.name)
		n.custom_minimum_size = Vector2(360, 0)
		var opts: Array = r.opts
		for i in opts.size():
			var picked := i == int(r.pick)
			var seen: bool = (r.seen as Array)[i]
			var o := _label(row, 34, font_b if picked else font, Color(1.0, 0.92, 0.72) if picked else (Color(0.6, 0.62, 0.66) if seen else Color(0.32, 0.33, 0.36)))
			o.text = ("● " + String(opts[i])) if picked else (String(opts[i]) if seen else "???")
	var f := _label(map_panel, 28, font, Color(0.75, 0.78, 0.84))
	f.text = ("Открыто веток: %d из %d        Доверие Айрис: %+d" if lang == "ru" else "Branches discovered: %d of %d        Iris's trust: %+d") % [opened, total, trust]
	f.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	f.anchor_left = 0.05
	f.anchor_right = 0.95
	f.anchor_top = 0.87
	f.anchor_bottom = 0.93
	map_panel.modulate.a = 0.0
	create_tween().tween_property(map_panel, "modulate:a", 1.0, 0.8)


## Главное меню: название слева на фоне ночной трассы, кнопки. has_save — есть ли что продолжить.
func show_menu(has_save: bool) -> void:
	hide_menu()
	var ru := lang == "ru"
	menu_panel = Control.new()
	menu_panel.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.add_child(menu_panel)
	var shade := TextureRect.new()                     # затемнение слева, под названием и кнопками
	var g := Gradient.new()
	g.set_color(0, Color(0, 0, 0, 0.88))
	g.set_color(1, Color(0, 0, 0, 0.0))
	var gt := GradientTexture2D.new()
	gt.gradient = g
	gt.fill_from = Vector2(0, 0)
	gt.fill_to = Vector2(1, 0)
	shade.texture = gt
	shade.set_anchors_preset(Control.PRESET_FULL_RECT)
	shade.anchor_right = 0.75
	shade.stretch_mode = TextureRect.STRETCH_SCALE
	shade.mouse_filter = Control.MOUSE_FILTER_IGNORE
	menu_panel.add_child(shade)
	var t := _label(menu_panel, 72 if phone else 88, font_b, Color(0.93, 0.95, 1.0))
	t.text = "WHAT THE\nLAKE KEEPS"
	t.anchor_left = 0.07
	t.anchor_top = 0.06 if phone else 0.16
	t.anchor_right = 0.6
	t.anchor_bottom = 0.30 if phone else 0.42
	var st := _label(menu_panel, 30 if phone else 34, font, Color(0.62, 0.68, 0.78))
	st.text = "Что хранит озеро" if ru else "A story in five chapters"
	st.anchor_left = 0.075
	st.anchor_top = 0.30 if phone else 0.42
	st.anchor_right = 0.6
	st.anchor_bottom = 0.35 if phone else 0.47
	var items := []
	if has_save:
		items.append(["cont", "Продолжить" if ru else "Continue"])
	items.append(["new", "Новая игра" if ru else "New game"])
	items.append(["tree", "Дерево выборов" if ru else "Choice tree"])
	items.append(["settings", "Настройки" if ru else "Settings"])
	if not phone:   # на телефоне выходят кнопкой «Домой» — «Выход» не нужен
		items.append(["quit", "Выход" if ru else "Quit"])
	_menu_buttons(items, 0.07, 0.39 if phone else 0.52)
	_menu_kind = "main"


## Пауза (Esc): продолжить, в главное меню, выход.
func show_pause() -> void:
	hide_menu()
	for b in choice_box.get_children():   # варианты выбора под паузой — без фокуса
		(b as Control).focus_mode = Control.FOCUS_NONE
		(b as Control).release_focus()
	var ru := lang == "ru"
	menu_panel = ColorRect.new()
	(menu_panel as ColorRect).color = Color(0, 0, 0, 0.72)
	menu_panel.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.add_child(menu_panel)
	var t := _label(menu_panel, 60, font_b, Color.WHITE)
	t.text = "Пауза" if ru else "Paused"
	t.anchor_left = 0.07
	t.anchor_top = 0.3
	t.anchor_right = 0.6
	t.anchor_bottom = 0.4
	var items := [["resume", "Продолжить" if ru else "Resume"], ["settings", "Настройки" if ru else "Settings"],
		["menu", "Главное меню" if ru else "Main menu"]]
	if not phone:
		items.append(["quit", "Выход" if ru else "Quit"])
	_menu_buttons(items, 0.07, 0.42 if phone else 0.44)
	_menu_kind = "pause"


## Вопрос поверх главного меню (сохранение не прочиталось, начать заново): текст и кнопки; ответ — menu_pick (id кнопки).
## focus — какая кнопка выбрана сразу (клавиатура, геймпад): безопасная, чтобы двойное нажатие ничего не стёрло.
func show_confirm(text: String, items: Array, focus := 0) -> void:
	hide_menu()
	menu_panel = ColorRect.new()
	(menu_panel as ColorRect).color = Color(0, 0, 0, 0.82)
	menu_panel.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.add_child(menu_panel)
	var t := _label(menu_panel, 34 if phone else 36, font, Color(0.9, 0.92, 0.96))
	t.text = text
	t.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	t.anchor_left = 0.07
	t.anchor_top = 0.22 if phone else 0.26
	t.anchor_right = 0.62 if not phone else 0.9
	t.anchor_bottom = 0.42
	_menu_buttons(items, 0.07, 0.46, focus)
	_menu_kind = "confirm"


func _menu_buttons(items: Array, x: float, y: float, focus := 0) -> void:
	var box := VBoxContainer.new()
	box.anchor_left = x
	box.anchor_right = x + 0.26
	box.anchor_top = y
	box.anchor_bottom = y + 0.4
	box.add_theme_constant_override("separation", 16 if phone else 12)
	if phone:
		box.anchor_right = x + 0.34
		box.anchor_bottom = y + 0.55
	menu_panel.add_child(box)
	for it in items:
		var b := Button.new()
		b.text = String(it[1])
		b.add_theme_font_override("font", font_b)
		b.add_theme_font_size_override("font_size", 38)
		b.alignment = HORIZONTAL_ALIGNMENT_LEFT
		var sb := StyleBoxFlat.new()
		sb.bg_color = Color(0, 0, 0, 0.0)
		sb.content_margin_left = 18
		sb.content_margin_top = 8
		sb.content_margin_bottom = 8
		if phone:
			sb = _phone_box()
			b.custom_minimum_size = Vector2(0, 80)
		b.add_theme_stylebox_override("normal", sb)
		var sbh := sb.duplicate() as StyleBoxFlat
		sbh.bg_color = Color(1, 1, 1, 0.12)
		sbh.border_color = Color(1, 0.88, 0.7, 0.9)
		sbh.border_width_left = 4
		b.add_theme_stylebox_override("hover", sbh)
		b.add_theme_stylebox_override("pressed", sbh)
		b.add_theme_stylebox_override("focus", sbh)
		b.add_theme_color_override("font_color", Color(0.86, 0.88, 0.92))
		b.add_theme_color_override("font_hover_color", Color(1.0, 0.92, 0.78))
		b.add_theme_color_override("font_focus_color", Color(1.0, 0.92, 0.78))
		var id := String(it[0])
		b.pressed.connect(func(): menu_pick.emit(id))
		box.add_child(b)
	if box.get_child_count() > 0 and not phone:   # на телефоне подсветка «выбрано» без касания только путает
		(box.get_child(clampi(focus, 0, box.get_child_count() - 1)) as Button).grab_focus.call_deferred()   # клавиатура и геймпад: стрелки + Enter


func hide_menu() -> void:
	for b in choice_box.get_children():
		(b as Control).focus_mode = Control.FOCUS_ALL
	if is_instance_valid(menu_panel):
		menu_panel.queue_free()
	menu_panel = null
	_menu_kind = ""


func menu_open() -> bool:
	return is_instance_valid(menu_panel)


func hide_map() -> void:
	if is_instance_valid(map_panel):
		map_panel.queue_free()
	map_panel = null


func clear_text() -> void:
	sub_name.text = ""
	sub_text.text = ""
	note.text = ""


## Выбор: варианты (у «молчания» нет кнопки — оно случится, если не выбрать), время на ответ.
func choice(opts: Array, t: float) -> void:
	clear_choice()
	_opts = opts
	_choice_len = t
	_choice_t = t
	var shown := opts.filter(func(o): return not (o as Dictionary).get("silent", false)).size()
	var fs := 32 if shown <= 4 else 27   # 5–6 вариантов (глава 5, дача) — мельче, иначе нижние уходили за край
	if phone:
		fs = 30 if shown <= 4 else 25   # × масштаб 1,3 — крупнее, чем на ПК
	var n := 0
	for i in opts.size():
		var o: Dictionary = opts[i]
		if o.get("silent", false):
			silent_lbl.text = "…промолчать — просто подожди" if lang == "ru" else "…stay silent — just wait"
			continue
		n += 1
		var b := Button.new()
		b.text = T(o) if phone else "%d   %s" % [n, T(o)]   # номера — для клавиш 1–9, на телефоне не нужны
		b.add_theme_font_override("font", font)
		b.add_theme_font_size_override("font_size", fs)
		b.alignment = HORIZONTAL_ALIGNMENT_LEFT
		b.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART   # длинный вариант (английский) — в две строки, а не за край
		b.focus_mode = Control.FOCUS_ALL   # стрелки / крестовина геймпада выделяют, Enter / A выбирает
		var sb := StyleBoxFlat.new()
		sb.bg_color = Color(0, 0, 0, 0.55)
		sb.border_color = Color(1, 1, 1, 0.25)
		sb.set_border_width_all(1)
		sb.content_margin_left = 22
		sb.content_margin_top = 10
		sb.content_margin_bottom = 10
		if phone:
			sb = _phone_box(0.6)
			b.custom_minimum_size = Vector2(0, 84 if shown <= 4 else 70)
		b.add_theme_stylebox_override("normal", sb)
		var sbh := sb.duplicate() as StyleBoxFlat
		sbh.bg_color = Color(1, 1, 1, 0.18)
		b.add_theme_stylebox_override("hover", sbh)
		b.add_theme_stylebox_override("pressed", sbh)
		var sbf := sbh.duplicate() as StyleBoxFlat
		sbf.border_color = Color(1, 0.88, 0.7, 0.95)
		sbf.border_width_left = 5
		b.add_theme_stylebox_override("focus", sbf)
		b.pressed.connect(func(): pick(i))
		if o.get("shadow", false):   # «Тень» (глава 5, [улыбнуться]): вариант дрожит красным
			b.add_theme_color_override("font_color", Color(0.85, 0.12, 0.1))
			b.add_theme_color_override("font_hover_color", Color(1.0, 0.25, 0.2))
			b.set_meta("shake", true)
		choice_box.add_child(b)
	# место под варианты: сколько их и сколько строк — блок поднимается, чтобы все и подпись «промолчать» влезли
	var vs := get_viewport().get_visible_rect().size
	var need := 0.0
	var wbox := vs.x * (choice_box.anchor_right - choice_box.anchor_left) - 40.0
	for o in opts:
		if (o as Dictionary).get("silent", false):
			continue
		need += maxf(font.get_multiline_string_size("0   " + T(o), HORIZONTAL_ALIGNMENT_LEFT, wbox, fs).y + 22.0, 84.0 if phone else 0.0) + 14.0 + (12.0 if phone else 0.0)
	var top := clampf(0.89 - need / maxf(vs.y, 1.0), 0.22 if phone else 0.3, 0.62)
	choice_box.anchor_top = top
	timer_bg.anchor_top = top - 0.02
	timer_bg.anchor_bottom = top - 0.02
	timer_bar.anchor_top = top - 0.02
	timer_bar.anchor_bottom = top - 0.02
	timer_bg.visible = t > 0.0   # выбор без таймера (разговор на прогулке, темы в дороге) — без полоски времени
	timer_bar.visible = t > 0.0


func clear_choice() -> void:
	for c in choice_box.get_children():
		c.queue_free()
	_opts = []
	_choice_t = 0.0
	timer_bg.visible = false
	timer_bar.visible = false
	silent_lbl.text = ""


func choosing() -> bool:
	return not _opts.is_empty()


func pick(i: int) -> void:
	if _opts.is_empty() or get_tree().paused:   # под меню паузы выбор не делается (стрелки/Tab доходили до вариантов)
		return
	clear_choice()
	chosen.emit(i)


## Стрелки / крестовина во время выбора: выделить первый вариант (дальше фокус ходит сам, Enter / A — выбрать).
func focus_choice() -> void:
	for b in choice_box.get_children():
		if (b as Control).has_focus():
			return
	if choice_box.get_child_count() > 0:
		(choice_box.get_child(0) as Button).grab_focus()


func pick_number(n: int) -> void:   # клавиши 1–9: номер среди видимых вариантов
	var k := 0
	for i in _opts.size():
		if not _opts[i].get("silent", false):
			k += 1
			if k == n:
				pick(i)
				return


## Подсказка прогулки внизу кадра («E   Осмотреть»); пустая строка — убрать (тает).
func prompt(text: String) -> void:
	if text != "":
		prompt_lbl.text = text
	prompt_lbl.set_meta("on", text != "")


func show_toast(text: String, dur := 3.0) -> void:
	toast.text = text
	_toast_t = dur
	toast.move_to_front()   # поверх меню (плашка BootGuard в главном меню иначе под его затемнением)


func _process(delta: float) -> void:
	_ht += delta
	scrim.modulate.a = move_toward(scrim.modulate.a, 1.0 if (sub_text.text != "" or note.text != "") else 0.0, delta * 2.5)
	hum = move_toward(hum, hum_to, delta * 0.35)
	(vignette.material as ShaderMaterial).set_shader_parameter("amount", hum)
	(vignette.material as ShaderMaterial).set_shader_parameter("t", _ht)
	(static_rect.material as ShaderMaterial).set_shader_parameter("amount", noise)
	(static_rect.material as ShaderMaterial).set_shader_parameter("t", _ht)
	if not _opts.is_empty() and not get_tree().paused:
		for b in choice_box.get_children():   # «Тень» дрожит
			if b.has_meta("shake"):
				(b as Control).position.x = randf_range(-3.0, 3.0)
		_choice_t -= delta if _choice_len > 0.0 else 0.0
		var f := clampf(_choice_t / maxf(_choice_len, 0.01), 0.0, 1.0)
		var w := get_viewport().get_visible_rect().size.x * 0.4
		timer_bar.offset_left = get_viewport().get_visible_rect().size.x * 0.3 + w * (1.0 - f) * 0.5
		timer_bar.offset_right = get_viewport().get_visible_rect().size.x * 0.3 + w * (1.0 - (1.0 - f) * 0.5)
		timer_bar.anchor_left = 0.0
		timer_bar.anchor_right = 0.0
		if _choice_len > 0.0 and _choice_t <= 0.0:   # время вышло: молчание, если оно есть, иначе — первый вариант
			var idx := 0
			for i in _opts.size():
				if _opts[i].get("silent", false):
					idx = i
			pick(idx)
	var pw := 1.0 if (prompt_lbl.get_meta("on", false) and sub_text.text == "" and note.text == "" and _opts.is_empty()) else 0.0
	prompt_lbl.modulate.a = move_toward(prompt_lbl.modulate.a, pw, delta * 5.0)
	if _toast_t > 0.0 and not get_tree().paused:   # плашка «запомнит» не тает под паузой
		_toast_t -= delta
		toast.modulate.a = clampf(_toast_t, 0.0, 1.0)
		if _toast_t <= 0.0:
			toast.text = ""


## Кадр «сквозь щель в досках» (мальчик в сарае): маска поверх картинки, под субтитрами.
func peek_on(on: bool) -> void:
	if peek == null:
		return
	peek.visible = on
	var sz := get_viewport().get_visible_rect().size
	(peek.material as ShaderMaterial).set_shader_parameter("amount", 1.0 if on else 0.0)
	(peek.material as ShaderMaterial).set_shader_parameter("aspect", sz.x / maxf(sz.y, 1.0))


## Лист блокнота во весь экран (осмотр рисунка): null — убрать.
func page_view(tex: Texture2D) -> void:
	if page_rect == null:
		return
	page_rect.texture = tex
	page_rect.visible = tex != null
	page_bg.visible = tex != null
	note.anchor_top = 0.82 if tex != null else 0.78   # текст рассказчика — под листом, а не поверх рисунка
	note.anchor_bottom = 0.97 if tex != null else 0.95


## Крупные субтитры (настройки): реплики, имя и текст рассказчика крупнее.
func set_subs_big(on: bool) -> void:
	sub_name.add_theme_font_size_override("font_size", 38 if on else 30)
	sub_text.add_theme_font_size_override("font_size", 52 if on else 40)
	note.add_theme_font_size_override("font_size", 44 if on else 34)
	_fit_subs()


func settings_open() -> bool:
	return is_instance_valid(settings_panel)


func hide_settings() -> void:
	if is_instance_valid(settings_panel):
		settings_panel.queue_free()
	settings_panel = null


## Экран настроек: слева — графика, экран, звук, субтитры, язык; справа — управление. Всё меняется сразу (settings_changed),
## «Назад» / Esc / B — закрыть (settings_closed). Мышь, клавиатура (стрелки, Enter) и геймпад.
func show_settings(st: Settings) -> void:
	hide_settings()
	var ru := lang == "ru"
	var p := ColorRect.new()
	p.color = Color(0.015, 0.02, 0.03, 0.95)
	p.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.add_child(p)
	settings_panel = p
	var t := _label(p, 60, font_b, Color.WHITE)
	t.text = "Настройки" if ru else "Settings"
	t.anchor_left = 0.06
	t.anchor_top = 0.05
	t.anchor_right = 0.5
	t.anchor_bottom = 0.13
	var grid := GridContainer.new()
	grid.columns = 2
	grid.anchor_left = 0.06
	grid.anchor_right = 0.56
	grid.anchor_top = 0.15
	grid.anchor_bottom = 0.92
	grid.add_theme_constant_override("h_separation", 36)
	grid.add_theme_constant_override("v_separation", 10)
	if phone:   # телефон: две колонки строк во всю ширину (экран широкий) — без прокрутки, строки под палец
		var sc := ScrollContainer.new()   # прокрутка — на всякий случай (узкий экран, крупный шрифт системы)
		sc.anchor_left = 0.04
		sc.anchor_right = 0.96
		sc.anchor_top = 0.16
		sc.anchor_bottom = 0.85
		sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
		p.add_child(sc)
		grid.columns = 4
		grid.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		grid.add_theme_constant_override("h_separation", 26)
		grid.add_theme_constant_override("v_separation", 16)
		sc.add_child(grid)
	else:
		p.add_child(grid)
	var on_off := func(v: bool) -> String: return (("Вкл" if v else "Выкл") if ru else ("On" if v else "Off"))
	var first: Control = null
	var rows := [
		["quality", "Качество графики" if ru else "Graphics quality", "cycle", mini(st.quality, 2) if phone else st.quality, 3 if phone else 4,
			func(v: int) -> String: return Settings.quality_name(v, lang)],
	]
	if not phone:   # окно, полный экран и V-Sync — только у ПК
		rows.append(["fullscreen", "Экран" if ru else "Display", "cycle", 1 if st.fullscreen else 0, 2,
			func(v: int) -> String: return (("Полный экран" if v == 1 else "Окно") if ru else ("Fullscreen" if v == 1 else "Windowed"))])
		rows.append(["vsync", "Вертикальная синхронизация" if ru else "V-Sync", "cycle", 1 if st.vsync else 0, 2,
			func(v: int) -> String: return on_off.call(v == 1)])
	rows.append_array([
		["brightness", "Яркость" if ru else "Brightness", "slider", st.brightness, 0.6, 1.5],
		["master", "Громкость: общая" if ru else "Volume: master", "slider", float(st.vol.master), 0.0, 1.0],
		["music", "Музыка" if ru else "Music", "slider", float(st.vol.music), 0.0, 1.0],
		["sfx", "Звуки" if ru else "Sound effects", "slider", float(st.vol.sfx), 0.0, 1.0],
		["voice", "Голоса" if ru else "Voices", "slider", float(st.vol.voice), 0.0, 1.0],
		["subs_big", "Крупные субтитры" if ru else "Large subtitles", "cycle", 1 if st.subs_big else 0, 2,
			func(v: int) -> String: return on_off.call(v == 1)],
		["choice_long", "Больше времени на выбор" if ru else "More time to choose", "cycle", 1 if st.choice_long else 0, 2,
			func(v: int) -> String: return on_off.call(v == 1)],
		["lang", "Язык" if ru else "Language", "cycle", 0 if lang == "ru" else 1, 2,
			func(v: int) -> String: return "Русский" if v == 0 else "English"],
	])
	if OS.has_feature("pc") and not phone:   # видеорежим: после вылетов включается сам; вернуть — здесь (игра перезапустится)
		rows.append(["renderer", "Видеорежим" if ru else "Renderer", "cycle", 1 if BootGuard.compat_on() else 0, 2,
			func(v: int) -> String: return (("Совместимый" if v == 1 else "Обычный") if ru else ("Compatible" if v == 1 else "Standard"))])
	for r in rows:
		var key := String(r[0])
		var l := Label.new()
		l.text = String(r[1])
		l.add_theme_font_override("font", font)
		l.add_theme_font_size_override("font_size", 30)
		l.add_theme_color_override("font_color", Color(0.78, 0.81, 0.86))
		l.custom_minimum_size = Vector2(460, 50)
		l.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
		if phone:
			l.custom_minimum_size = Vector2(330, 84)
			l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		grid.add_child(l)
		var c: Control
		if String(r[2]) == "cycle":
			var b := _settings_button()
			var n: int = r[4]
			var fmt: Callable = r[5]
			b.set_meta("v", int(r[3]))
			b.text = "‹  %s  ›" % fmt.call(int(r[3]))
			b.pressed.connect(func():
				var v: int = (int(b.get_meta("v")) + 1) % n
				b.set_meta("v", v)
				b.text = "‹  %s  ›" % fmt.call(v)
				settings_changed.emit(key, v))
			b.gui_input.connect(func(ev: InputEvent):   # влево / вправо — листать значения назад и вперёд
				if ev.is_action_pressed("ui_left") or ev.is_action_pressed("ui_right"):
					var d := -1 if ev.is_action_pressed("ui_left") else 1
					var v: int = posmod(int(b.get_meta("v")) + d, n)
					b.set_meta("v", v)
					b.text = "‹  %s  ›" % fmt.call(v)
					settings_changed.emit(key, v)
					b.accept_event())
			c = b
		else:
			var sl := HSlider.new()
			sl.min_value = float(r[4])
			sl.max_value = float(r[5])
			sl.step = 0.05
			sl.value = float(r[3])
			sl.custom_minimum_size = Vector2(420, 50)
			sl.focus_mode = Control.FOCUS_ALL
			var gs := StyleBoxFlat.new()
			gs.bg_color = Color(1, 1, 1, 0.16)
			gs.content_margin_top = 4
			gs.content_margin_bottom = 4
			if phone:   # дорожка толще, тянуть пальцем по всей длине (касание ставит значение в точку)
				sl.custom_minimum_size = Vector2(440, 84)
				sl.focus_mode = Control.FOCUS_NONE
				gs.content_margin_top = 10
				gs.content_margin_bottom = 10
				gs.set_corner_radius_all(10)
			sl.add_theme_stylebox_override("slider", gs)
			var ga := StyleBoxFlat.new()
			ga.bg_color = Color(1.0, 0.86, 0.66, 0.85)
			ga.content_margin_top = 4
			ga.content_margin_bottom = 4
			if phone:
				ga.content_margin_top = 10
				ga.content_margin_bottom = 10
				ga.set_corner_radius_all(10)
			sl.add_theme_stylebox_override("grabber_area", ga)
			sl.add_theme_stylebox_override("grabber_area_highlight", ga)
			var fs := StyleBoxFlat.new()
			fs.bg_color = Color(0, 0, 0, 0)
			fs.border_color = Color(1, 0.88, 0.7, 0.9)
			fs.set_border_width_all(2)
			sl.add_theme_stylebox_override("focus", fs)
			sl.value_changed.connect(func(v: float): settings_changed.emit(key, v))
			c = sl
		grid.add_child(c)
		c.focus_entered.connect(func(): l.add_theme_color_override("font_color", Color(1.0, 0.9, 0.72)))   # где фокус — видно и у ползунков
		c.focus_exited.connect(func(): l.add_theme_color_override("font_color", Color(0.78, 0.81, 0.86)))
		if first == null:
			first = c
	var back := _settings_button()
	back.text = "‹  " + ("Назад" if ru else "Back")
	back.pressed.connect(func(): close_settings())
	grid.add_child(Control.new())
	grid.add_child(back)
	var help := _label(p, 26, font, Color(0.72, 0.76, 0.82))   # управление
	help.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
	help.vertical_alignment = VERTICAL_ALIGNMENT_TOP
	help.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	help.anchor_left = 0.6
	help.anchor_right = 0.96
	help.anchor_top = 0.15
	help.anchor_bottom = 0.95
	if phone:   # управление касанием — коротко внизу, вместо клавиатуры и геймпада
		help.anchor_left = 0.06
		help.anchor_right = 0.94
		help.anchor_top = 0.86
		help.anchor_bottom = 0.98
		help.add_theme_font_size_override("font_size", 22)
		help.text = ("Касание — дальше и выбрать ответ  ·  джойстик слева — идти  ·  «Осмотреть» справа  ·  ‖ — пауза  ·  «Назад» телефона — пауза"
			if ru else "Tap — continue, pick an answer  ·  left joystick — walk  ·  «Look» on the right  ·  ‖ — pause  ·  phone Back — pause")
		if first and not phone:
			first.grab_focus.call_deferred()
		return
	help.text = ("УПРАВЛЕНИЕ\n\nКлавиатура и мышь\n1–9 или клик — выбрать ответ\n↑ ↓ — выделить ответ, Enter — выбрать\n"
		+ "Пробел, Enter, клик — дальше\nE, пробел, клик — действие (жать, тянуть, укачивать)\nS, ↓ — тормоз\n"
		+ "↑, Q, правая кнопка — посмотреть (в зеркало)\nEsc или P — пауза\nF11 или Alt+Enter — полный экран\n\n"
		+ "Геймпад\nКрестовина или стик — выделить ответ\nA — выбрать, дальше, действие\n↓ — тормоз, ↑ — посмотреть\n"
		+ "Start — пауза, B — назад") if ru else ("CONTROLS\n\nKeyboard & mouse\n1–9 or click — pick an answer\n"
		+ "↑ ↓ — highlight an answer, Enter — pick\nSpace, Enter, click — continue\nE, Space, click — action (mash, pull, rock)\n"
		+ "S, ↓ — brake\n↑, Q, right mouse — look (in the mirror)\nEsc or P — pause\nF11 or Alt+Enter — fullscreen\n\n"
		+ "Gamepad\nD-pad or stick — highlight an answer\nA — pick, continue, action\n↓ — brake, ↑ — look\nStart — pause, B — back")
	if first:
		first.grab_focus.call_deferred()


func close_settings() -> void:
	if not settings_open():
		return
	hide_settings()
	settings_closed.emit()


func _settings_button() -> Button:
	var b := Button.new()
	b.add_theme_font_override("font", font_b)
	b.add_theme_font_size_override("font_size", 30)
	b.alignment = HORIZONTAL_ALIGNMENT_LEFT
	b.custom_minimum_size = Vector2(420, 50)
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(1, 1, 1, 0.06)
	sb.content_margin_left = 16
	if phone:
		b.custom_minimum_size = Vector2(440, 84)
		b.alignment = HORIZONTAL_ALIGNMENT_CENTER
		sb.set_corner_radius_all(12)
		sb.bg_color = Color(1, 1, 1, 0.09)
	b.add_theme_stylebox_override("normal", sb)
	var sbh := sb.duplicate() as StyleBoxFlat
	sbh.bg_color = Color(1, 1, 1, 0.14)
	sbh.border_color = Color(1, 0.88, 0.7, 0.9)
	sbh.border_width_left = 4
	b.add_theme_stylebox_override("hover", sbh)
	b.add_theme_stylebox_override("pressed", sbh)
	b.add_theme_stylebox_override("focus", sbh)
	b.add_theme_color_override("font_color", Color(0.9, 0.92, 0.95))
	b.add_theme_color_override("font_focus_color", Color(1.0, 0.92, 0.78))
	b.add_theme_color_override("font_hover_color", Color(1.0, 0.92, 0.78))
	return b


## Esc / B: закрыть настройки или дерево, продолжить из паузы (в паузе — ещё P и Start: та же кнопка, что поставила паузу).
func _unhandled_input(e: InputEvent) -> void:
	if _menu_kind == "pause" and not settings_open() and not e.is_echo() and (
			(e is InputEventKey and e.pressed and (e as InputEventKey).keycode == KEY_P)
			or (e is InputEventJoypadButton and e.pressed and (e as InputEventJoypadButton).button_index == JOY_BUTTON_START)):
		menu_pick.emit("resume")
		get_viewport().set_input_as_handled()
		return
	if tree_open() and not e.is_echo():   # дерево: ← → (если рядом нечего выделить) и LB / RB — соседняя глава
		var jb := e as InputEventJoypadButton
		var d := 0
		if e.is_action_pressed("ui_left") or (jb and jb.pressed and jb.button_index == JOY_BUTTON_LEFT_SHOULDER):
			d = -1
		elif e.is_action_pressed("ui_right") or (jb and jb.pressed and jb.button_index == JOY_BUTTON_RIGHT_SHOULDER):
			d = 1
		if d != 0 and not _tree_data.is_empty():
			var i := posmod(int(_tree_body.get_meta("ch", 0)) + d, _tree_data.size())
			var on_tab := _tree_tabs.is_ancestor_of(get_viewport().gui_get_focus_owner()) if get_viewport().gui_get_focus_owner() else false
			_tree_chapter(i, not on_tab)
			if on_tab:
				(_tree_tabs.get_child(i) as Control).grab_focus()
			get_viewport().set_input_as_handled()
			return
	if not e.is_action_pressed("ui_cancel"):
		return
	if tree_open():
		close_tree("")
		get_viewport().set_input_as_handled()
	elif settings_open():
		close_settings()
		get_viewport().set_input_as_handled()
	elif _menu_kind == "pause":
		menu_pick.emit("resume")
		get_viewport().set_input_as_handled()
	elif _menu_kind == "confirm":   # Esc / B — «Назад»
		menu_pick.emit("back")
		get_viewport().set_input_as_handled()



func tree_open() -> bool:
	return is_instance_valid(tree_panel)


func close_tree(cp: String) -> void:
	if not tree_open():
		return
	tree_panel.queue_free()
	tree_panel = null
	tree_closed.emit(cp)


## Дерево выборов: вкладки глав сверху; ствол — контрольные точки по порядку сюжета (пройденные — «переиграть отсюда»,
## нажать дважды), между ними — развилки: варианты расходятся веткой и сходятся обратно. Открытые — подписаны, путь прошлого
## прохождения — светлый с рамкой, неоткрытые — «?». Внизу главы 5 — пять концовок.
func show_tree(chapters: Array, cur: int) -> void:
	if tree_open():
		tree_panel.queue_free()
	_tree_data = chapters
	var ru := lang == "ru"
	var p := ColorRect.new()
	p.color = Color(0.012, 0.016, 0.024, 0.97)
	p.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.add_child(p)
	tree_panel = p
	var t := _label(p, 52, font_b, Color.WHITE)
	t.text = "Дерево выборов" if ru else "Choice tree"
	t.anchor_left = 0.04
	t.anchor_top = 0.03
	t.anchor_right = 0.6
	t.anchor_bottom = 0.1
	var tabs := HBoxContainer.new()
	tabs.anchor_left = 0.04
	tabs.anchor_right = 0.96
	tabs.anchor_top = 0.105
	tabs.anchor_bottom = 0.16
	tabs.add_theme_constant_override("separation", 10)
	p.add_child(tabs)
	_tree_tabs = tabs
	for i in chapters.size():
		var tb := _settings_button()
		tb.custom_minimum_size = Vector2(0, 48)
		tb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		tb.add_theme_font_size_override("font_size", 24)
		tb.text = ("Глава %d" if ru else "Chapter %d") % int(chapters[i].n)
		tb.set_meta("i", i)
		tb.pressed.connect(func(): _tree_chapter(int(tb.get_meta("i")), true))   # A / Enter / клик — к контрольным точкам главы
		tb.focus_entered.connect(func(): _tree_chapter(int(tb.get_meta("i")), false))
		tabs.add_child(tb)
	_tree_body = ScrollContainer.new()
	_tree_body.anchor_left = 0.04
	_tree_body.anchor_right = 0.96
	_tree_body.anchor_top = 0.18
	_tree_body.anchor_bottom = 0.9
	_tree_body.follow_focus = true
	_tree_body.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	p.add_child(_tree_body)
	var foot := _label(p, 24, font, Color(0.62, 0.66, 0.74))
	foot.name = "foot"
	foot.anchor_left = 0.04
	foot.anchor_right = 0.96
	foot.anchor_top = 0.91
	foot.anchor_bottom = 0.98
	var back := _settings_button()
	back.text = "‹  " + ("Назад" if ru else "Back")
	back.custom_minimum_size = Vector2(220, 48)
	back.anchor_left = 0.82
	back.anchor_right = 0.96
	back.anchor_top = 0.03
	back.anchor_bottom = 0.08
	back.pressed.connect(func(): close_tree(""))
	p.add_child(back)
	_tree_chapter(cur)
	if tabs.get_child_count() > cur:
		(tabs.get_child(cur) as Button).grab_focus.call_deferred()


func _tree_chapter(i: int, focus_body := false) -> void:
	if not tree_open() or i < 0 or i >= _tree_data.size():
		return
	if _tree_body.has_meta("ch") and int(_tree_body.get_meta("ch")) == i and not focus_body:
		return
	_tree_body.set_meta("ch", i)
	for c in _tree_body.get_children():
		c.queue_free()
	for k in _tree_tabs.get_child_count():   # открытая глава — вкладка подсвечена, даже когда фокус внизу, в дереве
		var tb := _tree_tabs.get_child(k) as Button
		var sb := StyleBoxFlat.new()
		sb.content_margin_left = 16
		sb.bg_color = Color(1, 1, 1, 0.14 if k == i else 0.06)
		if k == i:
			sb.border_color = Color(1, 0.88, 0.7, 0.9)
			sb.border_width_bottom = 3
		tb.add_theme_stylebox_override("normal", sb)
		tb.add_theme_color_override("font_color", Color(1.0, 0.92, 0.78) if k == i else Color(0.9, 0.92, 0.95))
	var ch: Dictionary = _tree_data[i]
	var ru := lang == "ru"
	var canvas := Control.new()
	_tree_body.add_child(canvas)
	var W := get_viewport().get_visible_rect().size.x * 0.92 - 24.0
	var cx := W * 0.5
	var y := 24.0
	var segs: Array = []   # линии ствола и веток: [от, до, открыта ли]
	var prev := Vector2(cx, y)
	var first_cp: Button = null
	var cap := _label(canvas, 30, font_b, Color(0.86, 0.88, 0.93))
	cap.text = String(ch.title)
	cap.position = Vector2(0, y)
	cap.size = Vector2(W, 40)
	y += 62.0
	prev = Vector2(cx, y)
	for sec in ch.sections:
		var reached: bool = sec.reached
		var b := _settings_button()
		b.custom_minimum_size = Vector2(520, 60)
		b.size = Vector2(520, 60)
		b.position = Vector2(cx - 260.0, y)
		b.alignment = HORIZONTAL_ALIGNMENT_CENTER
		b.add_theme_font_size_override("font_size", 26)
		var label := "◆   " + String(sec.title) if reached else "◆   ?  ?  ?"
		b.text = label
		if reached:
			var cp := String(sec.cp)
			b.set_meta("armed", false)
			b.pressed.connect(func():   # дважды: «Начать отсюда?» → начать
				if bool(b.get_meta("armed")):
					close_tree(cp)
				else:
					b.set_meta("armed", true)
					b.text = "▶   " + ("Начать отсюда? Нажми ещё раз" if ru else "Start from here? Press again"))
			b.focus_exited.connect(func():
				b.set_meta("armed", false)
				b.text = label)
			if first_cp == null:
				first_cp = b
		else:
			b.disabled = true
			b.focus_mode = Control.FOCUS_NONE
			b.add_theme_color_override("font_disabled_color", Color(0.4, 0.43, 0.48))
		canvas.add_child(b)
		segs.append([prev, Vector2(cx, y), reached])
		y += 60.0
		prev = Vector2(cx, y)
		y += 30.0
		for row in sec.rows:
			y = _tree_row(canvas, row, cx, W, y, segs, prev, 220.0)
			prev = Vector2(cx, y - 16.0)
	if ch.end != null:   # концовки — в самом низу, крупнее
		var el := _label(canvas, 28, font_b, Color(1.0, 0.86, 0.66))
		el.text = "Концовки" if ru else "Endings"
		el.position = Vector2(0, y)
		el.size = Vector2(W, 36)
		y += 44.0
		y = _tree_row(canvas, ch.end, cx, W, y, segs, prev, 280.0, true)
	canvas.custom_minimum_size = Vector2(W, y + 30.0)
	canvas.draw.connect(func():
		for sg in segs:
			canvas.draw_line(sg[0], sg[1], Color(1.0, 0.86, 0.66, 0.75) if sg[2] else Color(1, 1, 1, 0.14), 3.0 if sg[2] else 2.0, true))
	canvas.queue_redraw()
	var foot := tree_panel.get_node_or_null("foot") as Label
	if foot:
		if phone:   # без клавиш: назад — кнопкой вверху справа или «Назад» телефона
			foot.text = (("Открыто вариантов: %d из %d   ·   ◆ — контрольная точка: коснись дважды, чтобы переиграть с неё" if ru
				else "Branches found: %d of %d   ·   ◆ — checkpoint: tap twice to replay from it") % [int(ch.opened), int(ch.total)])
		else:
			foot.text = (("Открыто вариантов: %d из %d   ·   ◆ — контрольная точка: нажми дважды, чтобы переиграть с неё   ·   Esc / B — назад" if ru
				else "Branches found: %d of %d   ·   ◆ — checkpoint: press twice to replay from it   ·   Esc / B — back") % [int(ch.opened), int(ch.total)])
	if focus_body:   # к первой пройденной контрольной точке; если в главе ещё ничего не пройдено — на её вкладку
		(first_cp if first_cp else _tree_tabs.get_child(i) as Control).grab_focus.call_deferred()


## Развилка: название, варианты в ряд (ветки расходятся от ствола и сходятся обратно). Возвращает новый y.
func _tree_row(canvas: Control, row: Dictionary, cx: float, W: float, y: float, segs: Array, prev: Vector2, wmax: float, big := false) -> float:
	var nm := _label(canvas, 22, font, Color(0.6, 0.64, 0.72))
	nm.text = String(row.name)
	nm.position = Vector2(0, y)
	nm.size = Vector2(W, 28)
	y += 32.0
	var opts: Array = row.opts
	var m := opts.size()
	var gap := 16.0
	var w := minf(wmax, (W - 40.0 - gap * (m - 1)) / maxf(1.0, m))
	var h := 64.0 if big else 54.0
	var x0 := cx - (w * m + gap * (m - 1)) * 0.5
	for i in m:
		var o: Dictionary = opts[i]
		var box := PanelContainer.new()
		var sb := StyleBoxFlat.new()
		sb.set_corner_radius_all(8)
		sb.content_margin_left = 8
		sb.content_margin_right = 8
		if o.last:
			sb.bg_color = Color(1.0, 0.86, 0.66, 0.2)
			sb.border_color = Color(1.0, 0.86, 0.66, 0.95)
			sb.set_border_width_all(2)
		elif o.seen:
			sb.bg_color = Color(1, 1, 1, 0.1)
			sb.border_color = Color(1, 1, 1, 0.3)
			sb.set_border_width_all(1)
		else:
			sb.bg_color = Color(1, 1, 1, 0.03)
			sb.border_color = Color(1, 1, 1, 0.1)
			sb.set_border_width_all(1)
		box.add_theme_stylebox_override("panel", sb)
		box.position = Vector2(x0 + i * (w + gap), y)
		box.size = Vector2(w, h)
		box.custom_minimum_size = Vector2(w, h)
		box.mouse_filter = Control.MOUSE_FILTER_IGNORE
		var l := Label.new()
		l.text = String(o.text) if o.seen else "?"
		l.add_theme_font_override("font", font_b if o.last else font)
		l.add_theme_font_size_override("font_size", 24 if big else 20)
		l.add_theme_color_override("font_color", Color(1.0, 0.94, 0.84) if o.last else (Color(0.82, 0.85, 0.9) if o.seen else Color(0.36, 0.39, 0.44)))
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		l.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
		l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		box.add_child(l)
		canvas.add_child(box)
		var top := Vector2(box.position.x + w * 0.5, y)
		segs.append([prev, top, bool(o.last)])
		segs.append([top + Vector2(0, h), Vector2(cx, y + h + 28.0), bool(o.last)])
	return y + h + 44.0
