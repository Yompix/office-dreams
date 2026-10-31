class_name Hud
extends CanvasLayer
## Экран поверх кадра: субтитры (имя и реплика), выбор на время (полоса времени, 1–3, «молчание» — дождаться конца),
## подсказка «Айрис это запомнит», подсказки действий (руль, тормоз), затемнение, надпись главы.

signal chosen(i: int)
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


func _ready() -> void:
	layer = 10
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
	static_rect = ColorRect.new()
	static_rect.set_anchors_preset(Control.PRESET_FULL_RECT)
	static_rect.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var sm := ShaderMaterial.new()
	sm.shader = Shader.new()
	sm.shader.code = STATIC
	static_rect.material = sm
	root.add_child(static_rect)
	sub_name = _label(root, 30, font_b, Color.WHITE)
	sub_text = _label(root, 40, font, Color.WHITE)
	note = _label(root, 34, font, Color(0.8, 0.82, 0.86))
	note.add_theme_font_override("font", font)
	for l in [sub_name, sub_text, note]:
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		l.anchor_left = 0.12
		l.anchor_right = 0.88
	sub_name.anchor_top = 0.76
	sub_name.anchor_bottom = 0.79
	sub_text.anchor_top = 0.79
	sub_text.anchor_bottom = 0.95
	note.anchor_top = 0.45
	note.anchor_bottom = 0.6
	choice_box = VBoxContainer.new()
	choice_box.anchor_left = 0.3
	choice_box.anchor_right = 0.7
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
	toast.anchor_right = 0.5
	toast.anchor_bottom = 0.1
	hint = _label(root, 34, font_b, Color.WHITE)
	hint.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	hint.anchor_left = 0.2
	hint.anchor_right = 0.8
	hint.anchor_top = 0.12
	hint.anchor_bottom = 0.2
	title = _label(root, 64, font_b, Color.WHITE)
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	title.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	title.set_anchors_preset(Control.PRESET_FULL_RECT)
	clear_choice()


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


func show_note(text_d) -> void:
	note.text = T(text_d)
	sub_name.text = ""
	sub_text.text = ""


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
	var n := 0
	for i in opts.size():
		var o: Dictionary = opts[i]
		if o.get("silent", false):
			silent_lbl.text = "…промолчать — просто подожди" if lang == "ru" else "…stay silent — just wait"
			continue
		n += 1
		var b := Button.new()
		b.text = "%d   %s" % [n, T(o)]
		b.add_theme_font_override("font", font)
		b.add_theme_font_size_override("font_size", 32)
		b.alignment = HORIZONTAL_ALIGNMENT_LEFT
		b.focus_mode = Control.FOCUS_NONE
		var sb := StyleBoxFlat.new()
		sb.bg_color = Color(0, 0, 0, 0.55)
		sb.border_color = Color(1, 1, 1, 0.25)
		sb.set_border_width_all(1)
		sb.content_margin_left = 22
		sb.content_margin_top = 10
		sb.content_margin_bottom = 10
		b.add_theme_stylebox_override("normal", sb)
		var sbh := sb.duplicate() as StyleBoxFlat
		sbh.bg_color = Color(1, 1, 1, 0.18)
		b.add_theme_stylebox_override("hover", sbh)
		b.pressed.connect(func(): pick(i))
		choice_box.add_child(b)
	timer_bg.visible = true
	timer_bar.visible = true


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
	if _opts.is_empty():
		return
	clear_choice()
	chosen.emit(i)


func pick_number(n: int) -> void:   # клавиши 1–3: номер среди видимых вариантов
	var k := 0
	for i in _opts.size():
		if not _opts[i].get("silent", false):
			k += 1
			if k == n:
				pick(i)
				return


func show_toast(text: String) -> void:
	toast.text = text
	_toast_t = 3.0


func _process(delta: float) -> void:
	_ht += delta
	hum = move_toward(hum, hum_to, delta * 0.35)
	(vignette.material as ShaderMaterial).set_shader_parameter("amount", hum)
	(vignette.material as ShaderMaterial).set_shader_parameter("t", _ht)
	(static_rect.material as ShaderMaterial).set_shader_parameter("amount", noise)
	(static_rect.material as ShaderMaterial).set_shader_parameter("t", _ht)
	if not _opts.is_empty():
		_choice_t -= delta
		var f := clampf(_choice_t / maxf(_choice_len, 0.01), 0.0, 1.0)
		var w := get_viewport().get_visible_rect().size.x * 0.4
		timer_bar.offset_left = get_viewport().get_visible_rect().size.x * 0.3 + w * (1.0 - f) * 0.5
		timer_bar.offset_right = get_viewport().get_visible_rect().size.x * 0.3 + w * (1.0 - (1.0 - f) * 0.5)
		timer_bar.anchor_left = 0.0
		timer_bar.anchor_right = 0.0
		if _choice_t <= 0.0:   # время вышло: молчание, если оно есть, иначе — первый вариант
			var idx := 0
			for i in _opts.size():
				if _opts[i].get("silent", false):
					idx = i
			pick(idx)
	if _toast_t > 0.0:
		_toast_t -= delta
		toast.modulate.a = clampf(_toast_t, 0.0, 1.0)
		if _toast_t <= 0.0:
			toast.text = ""
