extends Control
## Экран подключения: луч света бежит от «вас» к «хозяину» — тёплые огоньки летят по дуге между двумя кружками.
## Когда вход удался, экран сменяется и вспыхивают искры (FX.burst). Рисуется одной пачкой мягких точек.

var me := "Вы"
var host := "Друг"
var _t := 0.0
var _acc := 0.0


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	set_process(FX.on())


func _process(d: float) -> void:
	_t += d
	_acc += d
	if _acc >= 1.0 / 30.0:
		_acc = 0.0
		queue_redraw()


func _draw() -> void:
	var w := size.x
	var cy := size.y * 0.55
	var r := 30.0
	var a := Vector2(maxf(r + 12.0, w * 0.14), cy)
	var b := Vector2(minf(w - r - 12.0, w * 0.86), cy)
	var acc := Cfg.accent()
	var dot := UI.ui_tex("dot")
	var arc := func(k: float) -> Vector2:
		return a.lerp(b, k) + Vector2(0, -sin(k * PI) * size.y * 0.30)
	# тусклая дорожка
	var pts := PackedVector2Array()
	for i in 33:
		pts.append(arc.call(i / 32.0))
	draw_polyline(pts, Color(1, 1, 1, 0.10), 2.0, true)
	# бегущий свет: голова + хвост из огоньков
	var t := _t if FX.on() else 0.62
	for j in 3:
		var head := fmod(t * 0.55 + j * 0.34, 1.15)
		for i in 14:
			var k := head - i * 0.022
			if k < 0.0 or k > 1.0:
				continue
			var p: Vector2 = arc.call(k)
			var f := 1.0 - i / 14.0
			var rr := 3.0 + 7.0 * f
			draw_texture_rect(dot, Rect2(p - Vector2(rr, rr) * 2.2, Vector2(rr, rr) * 4.4), false, Color(acc, 0.16 * f))
			draw_texture_rect(dot, Rect2(p - Vector2(rr, rr) * 0.7, Vector2(rr, rr) * 1.4), false, Color(acc.lightened(0.5), 0.9 * f))
	# два конца: вы и хозяин
	for e in [[a, me, acc, "Вы"], [b, host, UI.BEDROCK, "Хозяин"]]:
		var c: Vector2 = e[0]
		var col: Color = e[2]
		var pulse := 0.5 + 0.5 * sin(t * 2.4 + (0.0 if e[3] == "Вы" else 1.6))
		draw_texture_rect(dot, Rect2(c - Vector2(r, r) * (2.0 + 0.3 * pulse), Vector2(r, r) * (4.0 + 0.6 * pulse)), false, Color(col, 0.22 + 0.10 * pulse))
		draw_circle(c, r, Color(0.07, 0.08, 0.17), true, -1, true)
		draw_arc(c, r, 0, TAU, 48, col, 2.0, true)
		var ch := String(e[1]).substr(0, 1).to_upper()
		var fs := 24
		var ts := UI.font_black.get_string_size(ch, HORIZONTAL_ALIGNMENT_LEFT, -1, fs)
		draw_string(UI.font_black, Vector2(c.x - ts.x / 2, c.y + fs * 0.36), ch, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, col.lightened(0.5))
		var ls := UI.font_bold.get_string_size(String(e[3]), HORIZONTAL_ALIGNMENT_LEFT, -1, 12)
		draw_string(UI.font_bold, Vector2(c.x - ls.x / 2, c.y + r + 18), String(e[3]), HORIZONTAL_ALIGNMENT_LEFT, -1, 12, UI.MUTED)
