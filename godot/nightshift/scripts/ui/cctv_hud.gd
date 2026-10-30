class_name CctvHud
extends Control
## Надписи камеры наблюдения поверх кадра — перенос cctvHud (engine.js): уголки кадра, мигающий REC (или «▶ ЗАПИСЬ» у записи),
## IR, когда камера в ИК, название камеры, дата (как пишут в стране игрока) и время, в смене — микрофон камеры (столбики).
## Координаты — как в старой игре: вертикальный холст 1080×1920, масштаб по высоте кадра.

var label := ""
var tag := "REC"
var ir := false
var date := "28.10.2026"
var clock := "21:00:00"
var mic := -1.0           # уровень микрофона (смена); < 0 — не показывать
var T := 0.0
var _mic_s := 0.0
static var _font: Font


func _init() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	visible = false


func _process(_d: float) -> void:
	if visible:
		queue_redraw()


static func font() -> Font:
	if _font == null:
		var f: FontFile = (load("res://ui/fonts/IBMPlexMono-Bold.woff2") as FontFile).duplicate()
		f.fallbacks = PhoneScreen.cjk_fallbacks()
		_font = f
	return _font


## Дата на записи — как пишут в стране игрока: США 10-29-2026, Азия 2026/10/29, Корея 2026.10.29, юг Европы 29/10/2026, остальные 29.10.2026.
static func loc_date(d: String) -> String:
	var p := d.split(".")
	if p.size() < 3:
		return d
	match StoryDB.lang:
		"en":
			return "%s-%s-%s" % [p[1], p[0], p[2]]
		"ja", "zh":
			return "%s/%s/%s" % [p[2], p[1], p[0]]
		"ko":
			return "%s.%s.%s" % [p[2], p[1], p[0]]
		"es", "pt", "fr", "it":
			return "%s/%s/%s" % [p[0], p[1], p[2]]
	return d


func _draw() -> void:
	var us := size.y / 1920.0
	var W := size.x
	var H := size.y
	var m := 46.0 * us
	var L := 64.0 * us
	var line_c := Color(235 / 255.0, 240 / 255.0, 235 / 255.0, 0.55)
	for c in [[m, m, 1, 1], [W - m, m, -1, 1], [m, H - m, 1, -1], [W - m, H - m, -1, -1]]:
		draw_polyline(PackedVector2Array([Vector2(c[0] + c[2] * L, c[1]), Vector2(c[0], c[1]), Vector2(c[0], c[1] + c[3] * L)]), line_c, 4.0 * us, true)
	draw_set_transform(Vector2.ZERO, 0.0, Vector2(us, us))
	var f := font()
	var txt := Color(235 / 255.0, 240 / 255.0, 235 / 255.0, 0.9)
	var sh := Color(0, 0, 0, 0.9)
	if fmod(T, 1.0) < 0.5:
		draw_circle(Vector2(98, 222), 13, Color("#e8262e"))
	_t(f, Vector2(124, 204), tag, 40, txt, sh)
	if ir and tag == "REC":
		_t(f, Vector2(250, 204), "IR", 40, Color(txt, 0.75), sh)
	if label != "":
		_t(f, Vector2(84, 258), label, 40, txt, sh)
	_t(f, Vector2(84, 310), loc_date(date) + "  " + clock, 34, txt, sh)
	if mic >= 0.0:   # микрофон камеры: что-то слышно — столбики прыгают
		_mic_s = maxf(_mic_s * 0.94, mic)
		var lv := minf(1.0, 0.1 + randf() * 0.1 + _mic_s * (0.75 + randf() * 0.25))
		_t(f, Vector2(84, 356), "MIC", 26, txt, sh)
		for i in 12:
			var on := float(i) / 12.0 < lv
			var col := (Color("#e8262e") if i > 8 else (Color("#e8c22e") if i > 5 else Color(150 / 255.0, 235 / 255.0, 160 / 255.0, 0.9))) if on else Color(235 / 255.0, 240 / 255.0, 235 / 255.0, 0.18)
			draw_rect(Rect2(150 + i * 15, 360, 10, 18), col)
	draw_set_transform(Vector2.ZERO)


## Текст сверху вниз (textBaseline top) с тенью.
func _t(f: Font, at: Vector2, s: String, sz: int, c: Color, sh: Color) -> void:
	var base := at + Vector2(0, f.get_ascent(sz))
	draw_string(f, base + Vector2(2, 2), s, HORIZONTAL_ALIGNMENT_LEFT, -1, sz, sh)
	draw_string(f, base, s, HORIZONTAL_ALIGNMENT_LEFT, -1, sz, c)
