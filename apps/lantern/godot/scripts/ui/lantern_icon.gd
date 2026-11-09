class_name LanternIcon
extends Control
## Аватар-фонарь: форма и цвет пламени из косметики, яркость — от уровня «Фонаря» (1..9).

const FLAMES := {
	"flame_amber": [Color("ffb648"), Color("ffe08a")], "flame_orange": [Color("ff7a2e"), Color("ffc07a")],
	"flame_rose": [Color("ff5f8f"), Color("ffc2d4")], "flame_violet": [Color("9d6bff"), Color("dcc9ff")],
	"flame_ice": [Color("4ec9ff"), Color("d6f4ff")], "flame_emerald": [Color("2fd47c"), Color("c4ffd9")],
	"flame_rainbow": [Color("ff6fae"), Color("fff27a")], "flame_s1": [Color("ff9a3d"), Color("fff0c2")],
	"flame_s1p": [Color("ff4f4f"), Color("ffd27a")], "flame_s1legend": [Color("f6d365"), Color("ffffff")],
}
## Цвет пламени по уровню, если косметика не выбрана
const LEVEL_FLAMES := ["ffb648", "ffa040", "ff8a3a", "ff6f61", "ff5f8f", "c56bff", "6b8cff", "4ec9ff", "fff2b0"]

var level := 1
var flame := ""
var shape := "shape_cube"
var seed_hue := 0.0
var animate := true
var _t := 0.0


func setup(lvl: int, equip: Variant = null, size_px := 48) -> LanternIcon:
	level = clampi(lvl, 1, 9)
	var e: Dictionary = {}
	if equip is Dictionary:
		e = equip
	elif equip is String and equip != "":
		var p: Variant = JSON.parse_string(equip)
		if p is Dictionary:
			e = p
	flame = e.get("flame", "")
	shape = e.get("shape", "shape_cube")
	custom_minimum_size = Vector2(size_px, size_px)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	return self


func _process(d: float) -> void:
	if animate and is_visible_in_tree():
		_t += d
		queue_redraw()


func colors() -> Array:
	if FLAMES.has(flame):
		return FLAMES[flame]
	var c := Color(LEVEL_FLAMES[level - 1])
	return [c, c.lightened(0.5)]


func _draw() -> void:
	var s := minf(size.x, size.y)
	var c := Vector2(size.x / 2, size.y * 0.56)
	var cols := colors()
	var fc: Color = cols[0]
	var fl: Color = cols[1]
	if flame == "flame_rainbow":
		fc = Color.from_hsv(fmod(_t * 0.15, 1.0), 0.7, 1.0)
	var flick := 0.92 + 0.08 * sin(_t * 7.3 + seed_hue * 10) * sin(_t * 3.1)
	var bright := (0.35 + level * 0.07) * flick
	# свечение
	for i in 10:
		var r := s * (0.25 + i * 0.03 + level * 0.012)
		draw_circle(c, r, Color(fc, bright * 0.05))
	var w := s * 0.40
	var h := s * 0.44
	match shape:
		"shape_tall":
			w = s * 0.32
			h = s * 0.54
		"shape_round", "shape_s1":
			w = s * 0.44
			h = s * 0.40
	var body := Rect2(c.x - w / 2, c.y - h / 2, w, h)
	var frame := Color("3a3330")
	# стекло с пламенем
	if shape in ["shape_round", "shape_s1"]:
		draw_circle(c, w * 0.55, frame)
		draw_circle(c, w * 0.45, fc.lerp(Color.WHITE, 0.1))
		draw_circle(c + Vector2(0, h * 0.05), w * 0.25, fl)
	else:
		draw_rect(body, frame)
		var glass := body.grow(-s * 0.045)
		draw_rect(glass, fc)
		draw_rect(Rect2(glass.position + Vector2(glass.size.x * 0.25, glass.size.y * 0.3), glass.size * Vector2(0.5, 0.55)), fl)
		draw_line(Vector2(c.x, glass.position.y), Vector2(c.x, glass.end.y), frame, maxf(1.0, s * 0.03))
		draw_line(Vector2(glass.position.x, c.y - h * 0.1), Vector2(glass.end.x, c.y - h * 0.1), frame, maxf(1.0, s * 0.025))
	if shape == "shape_star":
		for k in 4:
			var a := k * PI / 2 + _t * 0.5
			draw_line(c + Vector2.from_angle(a) * w * 0.7, c + Vector2.from_angle(a) * w * 0.95, Color(fl, 0.8), maxf(1.0, s * 0.03))
	# крышка и ручка
	var cap := Rect2(c.x - w * 0.32, body.position.y - s * 0.09, w * 0.64, s * 0.09)
	draw_rect(cap, Color("2e2826"))
	draw_rect(Rect2(c.x - w * 0.55, body.end.y - s * 0.01, w * 1.1, s * 0.06), Color("2e2826"))
	draw_arc(Vector2(c.x, cap.position.y), s * 0.09, PI, TAU, 12, Color("5c524a"), maxf(1.5, s * 0.035))
