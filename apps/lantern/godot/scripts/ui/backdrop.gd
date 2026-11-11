extends Control
## Фон: два мягких свечения (акцент сверху справа, синее снизу слева).
## Каждое — одна радиальная текстура (один слой заливки), а не десятки полупрозрачных кругов: меньше перерисовки на слабых телефонах.

var _warm: GradientTexture2D
var _cool: GradientTexture2D


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	_cool = _glow(UI.BEDROCK, 0.08)
	_rebuild()
	resized.connect(queue_redraw)
	Cfg.accent_changed.connect(func(_c): _rebuild())


func _rebuild() -> void:
	_warm = _glow(Cfg.accent(), 0.10)
	queue_redraw()


static func _glow(c: Color, a: float) -> GradientTexture2D:
	var g := Gradient.new()
	g.set_color(0, Color(c, a))
	g.set_color(1, Color(c, 0.0))
	g.add_point(0.5, Color(c, a * 0.35))
	var t := GradientTexture2D.new()
	t.gradient = g
	t.fill = GradientTexture2D.FILL_RADIAL
	t.fill_from = Vector2(0.5, 0.5)
	t.fill_to = Vector2(1.0, 0.5)
	t.width = 128
	t.height = 128
	return t


func _draw() -> void:
	var r := size.x * 0.65
	draw_texture_rect(_warm, Rect2(Vector2(size.x * 0.82, -size.y * 0.1) - Vector2(r, r), Vector2(r, r) * 2), false)
	var r2 := size.x * 0.4
	draw_texture_rect(_cool, Rect2(Vector2(-size.x * 0.05, size.y * 1.05) - Vector2(r2, r2), Vector2(r2, r2) * 2), false)
