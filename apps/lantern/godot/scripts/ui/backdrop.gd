extends Control
## Фон: мягкое свечение акцента и редкие «пиксельные» квадраты.

var _blocks := []


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	var rng := RandomNumberGenerator.new()
	rng.seed = 7
	for i in 26:
		_blocks.append([Vector2(rng.randf(), rng.randf()), rng.randf_range(10, 30), rng.randf_range(0.02, 0.06)])
	resized.connect(queue_redraw)
	Cfg.accent_changed.connect(func(_c): queue_redraw())


func _draw() -> void:
	var a := Cfg.accent()
	var c := Vector2(size.x * 0.82, -size.y * 0.1)
	for i in 18:
		var r := size.x * (0.65 - i * 0.03)
		draw_circle(c, r, Color(a, 0.004 + i * 0.0007))
	var c2 := Vector2(-size.x * 0.05, size.y * 1.05)
	for i in 14:
		draw_circle(c2, size.x * (0.4 - i * 0.025), Color(UI.BEDROCK, 0.005 + i * 0.001))
	for b in _blocks:
		var p: Vector2 = b[0] * size
		var s: float = b[1]
		draw_rect(Rect2(p, Vector2(s, s)), Color(1, 1, 1, b[2] * (0.22 if UI.compact() else 0.35)))
