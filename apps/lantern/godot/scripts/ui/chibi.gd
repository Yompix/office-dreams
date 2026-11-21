class_name Chibi
extends Control
## 2.0: талисман Yompix Games — чиби-силуэт с растрёпанными волосами, короной и белыми глазами-щёлками.
## Покачивается, моргает; вокруг — кольцо-орбита с искрой. Геометрия — как в stream-kit/brand.js (viewBox 200×240).
## Используется: заставка (собирается по частям), загрузки и пустые экраны, знакомство, «Любые игры».
## «Меньше анимаций» — стоит неподвижно. Рисуется линиями и многоугольниками (никаких текстур) — дёшево на любом телефоне.

var orbit := true           # кольцо с искрой
var animate := true
var assemble := 1.0         # 0..1: заставка «собирает» фигуру (тело → голова → глаза → корона → кольцо)
var line := Color("f4f4f2")
var fill := Color("0b0b0b")
var _t := 0.0
var _head: PackedVector2Array

const LENS := [30, 8, 34, 10, 26, 6, 38, 12, 22, 4, 16, 0, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 6, 0, 14, 4, 24, 8, 30, 10]


func _init() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	var n := LENS.size()
	for i in n:
		var a := -PI / 2 - 0.35 + i * TAU / n
		var r: float = (48.0 if i % 2 else 52.0) + float(LENS[i])
		if sin(a) > 0.5:
			r = 49.0
		_head.append(Vector2(100 + r * cos(a), 120 + r * sin(a)))


func _process(d: float) -> void:
	if not animate or not FX.on() or not is_visible_in_tree():
		return
	_t += d
	queue_redraw()


func _draw() -> void:
	# вписываем рисунок 200×240 (+ орбита по бокам) в размер узла
	var w := 260.0 if orbit else 200.0
	var k := minf(size.x / w, size.y / 250.0)
	var ox := (size.x - 200.0 * k) / 2.0
	var oy := (size.y - 240.0 * k) / 2.0 + 6.0 * k
	var bob := sin(_t * TAU / 3.2) * 5.0 if animate and FX.on() else 0.0
	var xf := Transform2D(0.0, Vector2(k, k), 0.0, Vector2(ox, oy + bob * k))
	var lw := maxf(2.0, 5.0 * k)
	var ring_a := clampf((assemble - 0.8) / 0.2, 0.0, 1.0)
	if orbit and ring_a > 0.0:
		_ring(xf, k, lw, ring_a, true)
	# тело
	var body_a := clampf(assemble / 0.25, 0.0, 1.0)
	if body_a > 0.0:
		var bp := PackedVector2Array()
		for i in 25:
			var u := float(i) / 24.0
			var x := lerpf(24.0, 176.0, u)
			var y := 240.0 - sin(u * PI) * 68.0 * (0.85 + 0.15 * sin(u * PI))
			bp.append(Vector2(x, y))
		var tb := xf * bp
		draw_colored_polygon(tb, Color(fill, body_a))
		tb.append(tb[0])
		draw_polyline(tb, Color(line, body_a), lw, true)
	# голова
	var head_a := clampf((assemble - 0.2) / 0.25, 0.0, 1.0)
	if head_a > 0.0:
		var th := xf * _head
		draw_colored_polygon(th, Color(fill, head_a))
		var cl := th.duplicate()
		cl.append(th[0])
		draw_polyline(cl, Color(line, head_a), lw, true)
	# глаза — белые щёлки, моргают
	var eye_a := clampf((assemble - 0.45) / 0.15, 0.0, 1.0)
	if eye_a > 0.0:
		var ph := fmod(_t, 4.6) / 4.6
		var sy := 0.08 if animate and FX.on() and ph > 0.92 and ph < 0.95 else 1.0
		for ex in [80.0, 110.0]:
			var r := Rect2(xf * Vector2(ex, 118.0 - 14.0 * sy), Vector2(10.0, 28.0 * sy) * k)
			draw_rect(r, Color(1, 1, 1, eye_a))
	# корона
	var crown_a := clampf((assemble - 0.6) / 0.2, 0.0, 1.0)
	if crown_a > 0.0:
		var tilt := sin(_t * TAU / 3.2) * 0.05 if animate and FX.on() else 0.0
		var pts := PackedVector2Array([Vector2(70, 44), Vector2(66, 16), Vector2(82, 30), Vector2(96, 10), Vector2(109, 30), Vector2(126, 16), Vector2(122, 44), Vector2(70, 44)])
		var cx := Transform2D(tilt, Vector2(96, 44)) * Transform2D(0.0, Vector2(-96, -44))
		draw_polyline(xf * (cx * pts), Color(line, crown_a), lw, true)
	if orbit and ring_a > 0.0:
		_ring(xf, k, lw, ring_a, false)


## Орбита: эллипс с разрывами, задняя половина за фигурой (тусклее), передняя — перед ней, искра бежит по кольцу.
func _ring(xf: Transform2D, k: float, lw: float, a: float, back: bool) -> void:
	var c := Vector2(100, 150)
	var rx := 128.0
	var ry := 30.0
	var tilt := -0.18
	var spin := _t * 0.9 if animate and FX.on() else 0.0
	var seg := PackedVector2Array()
	var n := 64
	for i in n + 1:
		var u := float(i) / n * TAU
		var front := sin(u) > 0.0
		var gap := fmod(u + spin, TAU) > 4.6 and fmod(u + spin, TAU) < 5.0
		if front == back or gap:
			if seg.size() > 1:
				draw_polyline(xf * seg, Color(line, a * (0.5 if back else 1.0)), lw * 0.9, true)
			seg = PackedVector2Array()
			continue
		var p := Vector2(cos(u) * rx, sin(u) * ry).rotated(tilt) + c
		seg.append(p)
	if seg.size() > 1:
		draw_polyline(xf * seg, Color(line, a * (0.5 if back else 1.0)), lw * 0.9, true)
	if not back:
		# искра-звезда на правом конце кольца
		var u := 0.25 + spin * 0.15
		var sp := xf * (Vector2(cos(u) * rx, sin(u) * ry).rotated(tilt) + c)
		var s := 9.0 * k * (0.8 + 0.2 * sin(_t * 4.0))
		var star := PackedVector2Array()
		for i in 8:
			var ang := i * PI / 4.0 + _t * 0.6
			star.append(sp + Vector2(cos(ang), sin(ang)) * (s if i % 2 == 0 else s * 0.28))
		draw_colored_polygon(star, Color(1, 1, 1, a))
