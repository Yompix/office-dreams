class_name Orb
extends Control
## Кнопка «Создать» в нижнем меню: светящийся шар-фонарь с «дышащим» ореолом (янтарь → оранжевый, блик сверху, плюс).

var _t := 0.0
var _acc := 0.0
var _live := 0.0
var pressed_k := 1.0


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	set_process(FX.on())


func _notification(what: int) -> void:
	if what == NOTIFICATION_VISIBILITY_CHANGED and is_visible_in_tree():
		_live = 0.0
		set_process(FX.on())


func _process(d: float) -> void:
	_t += d
	_acc += d
	_live += d
	if _live > 6.0:      # подышал — и замер: на простое экран не перерисовывается
		set_process(false)
	if _acc >= 1.0 / 30.0:
		_acc = 0.0
		queue_redraw()


func wake() -> void:
	_live = 0.0
	set_process(FX.on())


func _draw() -> void:
	var c := size / 2
	var r := minf(size.x, size.y) / 2 - 6.0
	var a := Cfg.accent()
	var breath := 0.5 + 0.5 * sin(_t * 2.1)
	var dot := UI.ui_tex("dot")
	var hr := r * (2.25 + 0.25 * breath)
	draw_texture_rect(dot, Rect2(c - Vector2(hr, hr), Vector2(hr, hr) * 2), false, Color(a, 0.30 + 0.14 * breath))
	draw_texture_rect(dot, Rect2(c - Vector2(r, r) * 1.45, Vector2(r, r) * 2.9), false, Color(UI.ORANGE.lerp(a, 0.5), 0.45))
	var pts := PackedVector2Array()
	var vc := PackedColorArray()
	var top := a.lightened(0.38)
	var bot := a.lerp(UI.ORANGE, 0.75) if a == UI.AMBER else a.darkened(0.18)
	for i in 48:
		var ang := float(i) / 48.0 * TAU
		var d := Vector2(cos(ang), sin(ang))
		pts.append(c + d * r)
		vc.append(top.lerp(bot, clampf(0.5 + (d.y * 0.8 + d.x * 0.35) * 0.5, 0.0, 1.0)))
	draw_polygon(pts, vc)
	draw_arc(c, r, 0, TAU, 56, top.lerp(bot, 0.5), 1.0, true)
	draw_arc(c + Vector2(0, 1), r - 1.5, PI * 1.12, PI * 1.88, 28, Color(1, 1, 1, 0.6), 1.5, true)
	var k := r * 0.36
	var sb := StyleBoxFlat.new()
	sb.bg_color = UI.ON_ACCENT
	sb.set_corner_radius_all(2)
	sb.anti_aliasing = true
	draw_style_box(sb, Rect2(c.x - k, c.y - 1.6, k * 2, 3.2))
	draw_style_box(sb, Rect2(c.x - 1.6, c.y - k, 3.2, k * 2))
