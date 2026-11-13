class_name UIcon
extends Control
## Векторные иконки LANTERN: линии 2 dp на сетке 24×24 — чёткие при любой плотности экрана.
## UIcon.make("home", 24, цвет) — узел; UIcon.texture("home", 20, цвет) — текстура для Button.icon (через SVG).

var icon := "home"
var color := Color.WHITE
var stroke := 2.0
var _svg: Variant = null     # PackedStringArray в режиме SVG


static func make(name: String, px := 24, c := Color.WHITE) -> UIcon:
	var i := UIcon.new()
	i.icon = name
	i.color = c
	i.custom_minimum_size = Vector2(px, px)
	i.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return i


static func texture(name: String, px := 20, c := Color.WHITE) -> Texture2D:
	var i := UIcon.new()
	i.icon = name
	i.color = c
	i.size = Vector2(24, 24)
	i._svg = PackedStringArray()
	i._paint()
	var svg := '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#%s" stroke-width="%.1f" stroke-linecap="round" stroke-linejoin="round">%s</svg>' % [c.to_html(false), i.stroke, "".join(i._svg)]
	i.free()
	var img := Image.new()
	img.load_svg_from_string(svg, px / 24.0 * 2.0)
	img.resize(px * 2, px * 2, Image.INTERPOLATE_LANCZOS)
	var t := ImageTexture.create_from_image(img)
	t.set_size_override(Vector2i(px, px))
	return t


func set_color(c: Color) -> void:
	color = c
	queue_redraw()


func _k() -> float:
	return 1.0 if _svg != null else minf(size.x, size.y) / 24.0


func _o() -> Vector2:
	if _svg != null:
		return Vector2.ZERO
	var k := _k()
	return (size - Vector2(24, 24) * k) / 2


func _p(x: float, y: float) -> Vector2:
	return _o() + Vector2(x, y) * _k()


func _f(v: float) -> String:
	return "%.2f" % v


func _line(pts: Array, closed := false) -> void:
	if _svg != null:
		var s := PackedStringArray()
		for p in pts:
			s.append(_f(p[0]) + "," + _f(p[1]))
		_svg.append('<%s points="%s"/>' % ["polygon" if closed else "polyline", " ".join(s)])
		return
	var arr := PackedVector2Array()
	for p in pts:
		arr.append(_p(p[0], p[1]))
	if closed:
		arr.append(arr[0])
	draw_polyline(arr, color, stroke * _k(), true)


func _circle(x: float, y: float, r: float, filled := false) -> void:
	if _svg != null:
		_svg.append('<circle cx="%s" cy="%s" r="%s"%s/>' % [_f(x), _f(y), _f(r), (' fill="#%s" stroke="none"' % color.to_html(false)) if filled else ""])
		return
	if filled:
		draw_circle(_p(x, y), r * _k(), color, true, -1, true)
	else:
		draw_arc(_p(x, y), r * _k(), 0, TAU, 40, color, stroke * _k(), true)


func _arc(x: float, y: float, r: float, a0: float, a1: float) -> void:
	if _svg != null:
		var p0 := Vector2(x, y) + Vector2.from_angle(deg_to_rad(a0)) * r
		var p1 := Vector2(x, y) + Vector2.from_angle(deg_to_rad(a1)) * r
		_svg.append('<path d="M%s %s A%s %s 0 %d 1 %s %s"/>' % [_f(p0.x), _f(p0.y), _f(r), _f(r), 1 if a1 - a0 > 180 else 0, _f(p1.x), _f(p1.y)])
		return
	draw_arc(_p(x, y), r * _k(), deg_to_rad(a0), deg_to_rad(a1), 32, color, stroke * _k(), true)


func _rrect(x: float, y: float, w: float, h: float, r: float) -> void:
	if _svg != null:
		_svg.append('<rect x="%s" y="%s" width="%s" height="%s" rx="%s"/>' % [_f(x), _f(y), _f(w), _f(h), _f(r)])
		return
	var pts := PackedVector2Array()
	var corners := [[x + w - r, y + r, -90], [x + w - r, y + h - r, 0], [x + r, y + h - r, 90], [x + r, y + r, 180]]
	for c in corners:
		for i in 7:
			var a := deg_to_rad(c[2] + i * 15.0)
			pts.append(_p(c[0] + cos(a) * r, c[1] + sin(a) * r))
	pts.append(pts[0])
	draw_polyline(pts, color, stroke * _k(), true)


func _poly(pts: Array) -> void:
	if _svg != null:
		var s := PackedStringArray()
		for p in pts:
			s.append(_f(p[0]) + "," + _f(p[1]))
		_svg.append('<polygon points="%s" fill="#%s" stroke="none"/>' % [" ".join(s), color.to_html(false)])
		return
	var arr := PackedVector2Array()
	for p in pts:
		arr.append(_p(p[0], p[1]))
	draw_colored_polygon(arr, color)


func _draw() -> void:
	_paint()


func _paint() -> void:
	match icon:
		"home":
			_line([[3.5, 11], [12, 4], [20.5, 11]])
			_line([[5.5, 9.5], [5.5, 20], [18.5, 20], [18.5, 9.5]])
			_line([[10, 20], [10, 14.5], [14, 14.5], [14, 20]])
		"friends":
			_circle(9, 8.5, 3.5)
			_arc(9, 21, 6.5, 200, 340)
			_arc(16.5, 8.5, 3, -100, 110)
			_line([[17.5, 14.6], [20.5, 18]])
		"user":
			_circle(12, 8.5, 4)
			_arc(12, 22, 8, 205, 335)
		"shop":
			_line([[5, 8], [19, 8], [18, 20.5], [6, 20.5]], true)
			_arc(12, 8, 3.5, 180, 360)
		"more":
			_circle(5.5, 12, 1.6, true)
			_circle(12, 12, 1.6, true)
			_circle(18.5, 12, 1.6, true)
		"dots_v":
			_circle(12, 5.5, 1.6, true)
			_circle(12, 12, 1.6, true)
			_circle(12, 18.5, 1.6, true)
		"shield":
			_line([[12, 3.5], [19.5, 6.5], [19.5, 12], [17.5, 17], [12, 20.5], [6.5, 17], [4.5, 12], [4.5, 6.5]], true)
			_line([[8.8, 12], [11.2, 14.4], [15.4, 9.6]])
		"bolt":
			_line([[13.5, 3], [6, 13.2], [11.5, 13.2], [10.5, 21], [18, 10.8], [12.5, 10.8]], true)
		"plus":
			_line([[12, 5], [12, 19]])
			_line([[5, 12], [19, 12]])
		"play":
			_line([[8, 5.5], [18.5, 12], [8, 18.5]], true)
		"back":
			_line([[19, 12], [5.5, 12]])
			_line([[11, 6], [5, 12], [11, 18]])
		"chevron":
			_line([[9.5, 6], [15.5, 12], [9.5, 18]])
		"chevron_down":
			_line([[6, 9.5], [12, 15.5], [18, 9.5]])
		"close":
			_line([[6, 6], [18, 18]])
			_line([[18, 6], [6, 18]])
		"check":
			_line([[5, 12.5], [10, 17.5], [19, 7]])
		"copy":
			_rrect(8.5, 8.5, 11, 11, 2.5)
			_line([[15.5, 5.5], [15.5, 5], [6.5, 5], [5, 5], [5, 15.5]])
		"share":
			_circle(17.5, 6, 2.6)
			_circle(6.5, 12, 2.6)
			_circle(17.5, 18, 2.6)
			_line([[8.8, 10.8], [15.2, 7.2]])
			_line([[8.8, 13.2], [15.2, 16.8]])
		"link":
			_arc(8, 16, 3.5, 45, 315)
			_arc(16, 8, 3.5, 225, 495)
			_line([[9.5, 14.5], [14.5, 9.5]])
		"paste":
			_rrect(5.5, 5, 13, 16, 2.5)
			_line([[9, 5], [9, 3.5], [15, 3.5], [15, 5]])
			_line([[9, 11], [15, 11]])
			_line([[9, 15], [13.5, 15]])
		"qr":
			for c in [[4, 4], [14, 4], [4, 14]]:
				_rrect(c[0], c[1], 6, 6, 1.2)
			_poly([[14, 14], [16.5, 14], [16.5, 16.5], [14, 16.5]])
			_poly([[17.5, 17.5], [20, 17.5], [20, 20], [17.5, 20]])
			_poly([[17.5, 14], [20, 14], [20, 15.5], [17.5, 15.5]])
			_poly([[14, 18], [15.5, 18], [15.5, 20], [14, 20]])
		"search":
			_circle(10.5, 10.5, 6)
			_line([[15, 15], [20, 20]])
		"settings":
			_circle(12, 12, 3)
			for i in 8:
				var a := i * TAU / 8.0
				_line([[12 + cos(a) * 6.2, 12 + sin(a) * 6.2], [12 + cos(a) * 8.6, 12 + sin(a) * 8.6]])
			_circle(12, 12, 6.2)
		"quests":
			_circle(12, 12, 8)
			_circle(12, 12, 4)
			_circle(12, 12, 1.2, true)
		"fire":
			_line([[12, 3.5], [16.5, 9], [17.5, 13.5], [16, 17.5], [12, 20], [8, 17.5], [6.5, 13.5], [8, 10], [9.5, 12], [10.5, 8], [12, 3.5]])
		"campfire":
			_line([[12, 3.5], [15.5, 8.5], [15.5, 12], [12, 14.5], [8.5, 12], [8.5, 9], [10, 10.5], [12, 3.5]])
			_line([[4.5, 20.5], [19.5, 16.5]])
			_line([[4.5, 16.5], [19.5, 20.5]])
		"chat":
			_line([[4.5, 6.5], [4.5, 15.5], [8, 15.5], [8, 19.5], [12.5, 15.5], [19.5, 15.5], [19.5, 4.5], [4.5, 4.5], [4.5, 6.5]])
		"send":
			_line([[4, 11.5], [20, 4], [14, 20], [11, 13], [4, 11.5]])
			_line([[11, 13], [20, 4]])
		"globe":
			_circle(12, 12, 8.5)
			_line([[3.5, 12], [20.5, 12]])
			# меридиан — эллипс
			var pts := []
			for i in 40:
				var a := i * TAU / 40.0
				pts.append([12 + cos(a) * 3.8, 12 + sin(a) * 8.5])
			_line(pts, true)
		"cloud":
			_line([[7, 18.5], [17.5, 18.5]])
			_arc(7, 14.5, 4, 90, 250)
			_arc(11.5, 10.5, 5, 195, 340)
			_arc(17.5, 14, 4.5, -60, 90)
		"server":
			_rrect(4, 4.5, 16, 6.5, 2)
			_rrect(4, 13, 16, 6.5, 2)
			_circle(7.5, 7.8, 1, true)
			_circle(7.5, 16.3, 1, true)
		"info":
			_circle(12, 12, 8.5)
			_line([[12, 11], [12, 16.5]])
			_circle(12, 7.8, 1.2, true)
		"wifi":
			_arc(12, 18, 12, 225, 315)
			_arc(12, 18, 7.5, 225, 315)
			_circle(12, 18, 1.6, true)
		"lock":
			_rrect(5.5, 10.5, 13, 10, 2.5)
			_arc(12, 10.5, 4, 180, 360)
		"spark":
			_poly([[12, 2.5], [14.6, 9.4], [21.5, 12], [14.6, 14.6], [12, 21.5], [9.4, 14.6], [2.5, 12], [9.4, 9.4]])
		"star":
			var s := []
			for i in 10:
				var a := -PI / 2 + i * PI / 5
				var r := 8.5 if i % 2 == 0 else 3.8
				s.append([12 + cos(a) * r, 12.5 + sin(a) * r])
			_line(s, true)
		"gift":
			_rrect(4.5, 9, 15, 11.5, 2)
			_line([[3.5, 9], [20.5, 9]])
			_line([[12, 9], [12, 20.5]])
			_arc(9.5, 7, 2.5, 90, 330)
			_arc(14.5, 7, 2.5, 210, 450)
		"heart":
			var pts2 := []
			for i in 60:
				var t := i * TAU / 60.0
				var x := 16 * pow(sin(t), 3)
				var y := 13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)
				pts2.append([12 + x * 0.5, 12.5 - y * 0.5])
			_line(pts2, true)
		"java":
			# куб (компьютер)
			_line([[12, 3.5], [19.5, 7.5], [19.5, 16.5], [12, 20.5], [4.5, 16.5], [4.5, 7.5]], true)
			_line([[4.5, 7.5], [12, 11.5], [19.5, 7.5]])
			_line([[12, 11.5], [12, 20.5]])
		"desktop":
			_rrect(3.5, 4.5, 17, 12, 2)
			_line([[9, 20], [15, 20]])
			_line([[12, 16.5], [12, 20]])
		"phone":
			_rrect(7, 3, 10, 18, 2.5)
			_line([[11, 18], [13, 18]])
		"download":
			_line([[12, 4], [12, 15]])
			_line([[7.5, 10.5], [12, 15], [16.5, 10.5]])
			_line([[5, 19.5], [19, 19.5]])
		"upload":
			_line([[12, 15], [12, 4]])
			_line([[7.5, 8.5], [12, 4], [16.5, 8.5]])
			_line([[5, 19.5], [19, 19.5]])
		"trash":
			_line([[4.5, 7], [19.5, 7]])
			_line([[9.5, 7], [9.5, 4.5], [14.5, 4.5], [14.5, 7]])
			_line([[6.5, 7], [7.5, 20], [16.5, 20], [17.5, 7]])
		"refresh":
			_arc(12, 12, 7.5, -60, 230)
			_line([[16, 4], [16, 7.6], [19.6, 7.6]])
		"stop":
			_rrect(6, 6, 12, 12, 2)
		"folder":
			_line([[3.5, 6.5], [9.5, 6.5], [11.5, 8.5], [20.5, 8.5], [20.5, 18.5], [3.5, 18.5]], true)
		"camera":
			_rrect(3.5, 7, 17, 12.5, 2.5)
			_line([[8.5, 7], [10, 4.5], [14, 4.5], [15.5, 7]])
			_circle(12, 13, 3.5)
		"lantern":
			_line([[9, 4.5], [15, 4.5]])
			_rrect(6.5, 6.5, 11, 13, 2.5)
			_line([[8, 21], [16, 21]])
			_line([[12, 10], [14, 13], [12, 16.5], [10, 13]], true)
		_:
			_circle(12, 12, 8)
