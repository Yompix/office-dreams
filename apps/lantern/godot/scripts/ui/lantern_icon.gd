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
var _acc := 0.0
var _live := 0.0


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
	# Мерцание — только у крупных фонарей и не чаще 20 раз в секунду: мелкие аватары рисуются один раз
	# (иначе экран со списком друзей перерисовывается каждый кадр и не даёт телефону «уснуть»).
	if not animate or size.x < 56.0 or not is_visible_in_tree() or not FX.on():
		set_process(false)
		return
	_t += d
	_acc += d
	_live += d
	if _live > 2.5:   # помигал после появления — и замер: на простое приложение не перерисовывает экран (батарея)
		set_process(false)
	if _acc >= 0.05:
		_acc = 0.0
		queue_redraw()


func _notification(what: int) -> void:
	if what == NOTIFICATION_VISIBILITY_CHANGED and is_visible_in_tree():
		_live = 0.0
		set_process(animate and size.x >= 56.0)


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
	var metal := Color("2e2826")
	# стекло с пламенем: скруглённое стекло и капля-огонёк (свой силуэт, как на иконке LANTERN)
	if shape in ["shape_round", "shape_s1"]:
		draw_circle(c, w * 0.55, frame)
		draw_circle(c, w * 0.45, fc.lerp(Color.WHITE, 0.1))
	else:
		var sb := StyleBoxFlat.new()
		sb.bg_color = fc
		sb.border_color = frame
		sb.set_border_width_all(int(maxf(1.0, s * 0.04)))
		sb.set_corner_radius_all(int(w * 0.22))
		sb.anti_aliasing = true
		draw_style_box(sb, body)
	_flame(c + Vector2(0, h * 0.08), minf(w, h) * 0.62, fl)
	if shape == "shape_star":
		for k in 4:
			var a := k * PI / 2 + _t * 0.5
			draw_line(c + Vector2.from_angle(a) * w * 0.7, c + Vector2.from_angle(a) * w * 0.95, Color(fl, 0.8), maxf(1.0, s * 0.03))
	# крышка-трапеция, основание и ручка
	var top := body.position.y
	draw_colored_polygon(PackedVector2Array([Vector2(c.x - w * 0.22, top - s * 0.09), Vector2(c.x + w * 0.22, top - s * 0.09),
		Vector2(c.x + w * 0.6, top + s * 0.01), Vector2(c.x - w * 0.6, top + s * 0.01)]), metal)
	draw_rect(Rect2(c.x - w * 0.58, body.end.y - s * 0.01, w * 1.16, s * 0.06), metal)
	draw_arc(Vector2(c.x, top - s * 0.09), s * 0.08, PI, TAU, 16, Color("5c524a"), maxf(1.5, s * 0.035), true)


## Огонёк-капля высотой fh с центром низа в p
func _flame(p: Vector2, fh: float, col: Color) -> void:
	var pts := PackedVector2Array()
	var r := fh * 0.3
	for i in 24:
		var a := float(i) / 24.0 * TAU
		var x := sin(a) * r
		var y := cos(a) * r * 0.9
		var k := clampf(1.0 - absf(a - PI) / 1.2, 0.0, 1.0)
		y -= fh * 0.45 * pow(k, 1.3)
		x *= 1.0 - 0.8 * k
		pts.append(p + Vector2(x, y))
	draw_colored_polygon(pts, col)
