class_name FX
extends RefCounted
## Анимации LANTERN: переходы, нажатия, появление списков, счётчики, искры, «скелетоны».
## Все длительности 150–250 мс, одна кривая (CUBIC, EASE_OUT), без «пружин»; только Tween (дёшево и на слабых телефонах).
## «Меньше анимаций» в настройках и автотесты (без --anim=1) — всё мгновенно.

static func on() -> bool:
	if Cfg.autotest != "" and not Cfg.autotest_args.has("anim"):
		return false
	return not bool(Cfg.get_value("app", "reduce_motion", false))


static func d(t: float) -> float:
	return t if on() else 0.0


## Нажатие: сжатие до 0.96 и пружинистый возврат + «волна» от пальца.
static func press(c: Control, k := 0.96) -> void:
	c.resized.connect(func(): c.pivot_offset = c.size / 2)
	c.gui_input.connect(func(e):
		if not on():
			return
		if e is InputEventMouseButton and e.button_index == MOUSE_BUTTON_LEFT:
			var tw := c.create_tween()
			if e.pressed:
				tw.set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT).tween_property(c, "scale", Vector2.ONE * k, 0.09)
				if c is BaseButton and not (c as BaseButton).disabled:
					ripple(c, e.position)
			else:
				tw.set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT).tween_property(c, "scale", Vector2.ONE, 0.16))


class Ripple extends Control:
	var center := Vector2.ZERO
	var r := 0.0
	var a := 0.16
	var col := Color.WHITE

	func _draw() -> void:
		draw_circle(center, r, Color(col, a), true, -1, true)


static func ripple(c: Control, at: Vector2) -> void:
	if not on() or c.size.x < 8:
		return
	# обрезаем саму волну, а не элемент: у стекла и главной кнопки тень и свечение выходят за края
	var clip := Control.new()
	clip.clip_contents = true
	clip.mouse_filter = Control.MOUSE_FILTER_IGNORE
	clip.set_anchors_preset(Control.PRESET_FULL_RECT)
	c.add_child(clip)
	var rp := Ripple.new()
	rp.mouse_filter = Control.MOUSE_FILTER_IGNORE
	rp.set_anchors_preset(Control.PRESET_FULL_RECT)
	rp.center = at
	clip.add_child(rp)
	rp.tree_exited.connect(clip.queue_free)
	var maxr := Vector2(maxf(at.x, c.size.x - at.x), maxf(at.y, c.size.y - at.y)).length()
	var tw := rp.create_tween().set_parallel().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
	tw.tween_method(func(v): rp.r = v; rp.queue_redraw(), 6.0, maxr, 0.28)
	tw.tween_method(func(v): rp.a = v; rp.queue_redraw(), 0.16, 0.0, 0.3).set_delay(0.06)
	tw.chain().tween_callback(rp.queue_free)


## Появление: проявление + лёгкое «всплытие» (масштаб от точки сбоку/снизу).
## Позицию не трогаем: ею управляют контейнеры, и сдвиг ломал бы раскладку.
static func enter(c: Control, from := Vector2(0, 12), delay := 0.0, dur := 0.22) -> void:
	if not on():
		return
	c.modulate.a = 0.0
	var k := 0.94 if from.x != 0 else 0.97
	c.scale = Vector2(k, k)
	var tw := c.create_tween().set_parallel().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
	tw.tween_callback(func():
		# точка опоры с той стороны, откуда «приезжает» элемент
		var px := c.size.x * (0.5 if from.x == 0 else (0.0 if from.x < 0 else 1.0) * 1.0)
		var py := c.size.y * (1.0 if from.y > 0 else (0.0 if from.y < 0 else 0.5))
		if from.x > 0:
			px = -c.size.x * 0.4
		elif from.x < 0:
			px = c.size.x * 1.4
		c.pivot_offset = Vector2(px, py)).set_delay(delay)
	tw.tween_property(c, "modulate:a", 1.0, dur * 0.8).set_delay(delay)
	tw.tween_property(c, "scale", Vector2.ONE, dur).set_delay(delay)


## Элементы списка появляются по очереди (шаг до 25 мс, не дольше 8 шагов — весь список виден за ~0,4 с).
static func stagger(box: Container, step := 0.035, from := Vector2(0, 14), max_items := 8) -> void:
	step = minf(step, 0.025)
	if not on():
		return
	var i := 0
	for ch in box.get_children():
		if ch is Control and (ch as Control).visible:
			enter(ch, from, minf(i, max_items) * step, 0.22)
			i += 1


## Счётчик «досчитывает» до числа (искры, друзья в мире).
static func count(l: Label, from: int, to: int, fmt := "%d", dur := 0.6) -> void:
	if not on() or from == to:
		l.text = fmt % to
		return
	l.create_tween().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT).tween_method(func(v): l.text = fmt % int(round(v)), float(from), float(to), dur)


## Мягкая «пульсация» (подключение, ожидание).
static func pulse(c: Control, k := 1.08, period := 1.2) -> void:
	c.resized.connect(func(): c.pivot_offset = c.size / 2)
	if not on():
		return
	var tw := c.create_tween().set_loops().set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
	tw.tween_property(c, "scale", Vector2.ONE * k, period / 2)
	tw.tween_property(c, "scale", Vector2.ONE, period / 2)


## Код комнаты: символы «переворачиваются» по одному с тёплым свечением.
static func flip_in(labels: Array, step := 0.07) -> void:
	for i in labels.size():
		var l: Control = labels[i]
		if not on():
			continue
		l.pivot_offset = l.size / 2 if l.size != Vector2.ZERO else Vector2(14, 24)
		l.scale = Vector2(1, 0.05)
		l.modulate = Color(1.6, 1.3, 0.8, 0.0)
		var tw := l.create_tween().set_parallel()
		tw.tween_property(l, "scale", Vector2.ONE, 0.32).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT).set_delay(i * step)
		tw.tween_property(l, "modulate", Color(1.5, 1.25, 0.9, 1.0), 0.16).set_delay(i * step)
		tw.chain().tween_property(l, "modulate", Color.WHITE, 0.45)


## Тёплые искры-угольки (друг зашёл в мир, покупка, награда).
class Embers extends Control:
	var parts := []
	var t := 0.0

	func _ready() -> void:
		mouse_filter = Control.MOUSE_FILTER_IGNORE
		set_anchors_preset(Control.PRESET_FULL_RECT)

	func burst(at: Vector2, n := 26) -> void:
		var rng := RandomNumberGenerator.new()
		rng.randomize()
		for i in n:
			var a := rng.randf_range(-PI, 0) if rng.randf() < 0.8 else rng.randf_range(0, PI)
			var sp := rng.randf_range(80, 260)
			parts.append({"p": at, "v": Vector2(cos(a), sin(a)) * sp, "life": rng.randf_range(0.6, 1.2), "age": 0.0,
				"r": rng.randf_range(1.5, 3.5), "c": [Color("ffd27a"), Color("ff9a3d"), Color("ffb648"), Color("fff0c2")][i % 4]})
		set_process(true)

	func _process(dt: float) -> void:
		for p in parts:
			p.age += dt
			p.v.y += 160 * dt
			p.v *= 0.985
			p.p += p.v * dt
		parts = parts.filter(func(p): return p.age < p.life)
		queue_redraw()
		if parts.is_empty():
			set_process(false)

	func _draw() -> void:
		for p in parts:
			var k: float = 1.0 - p.age / p.life
			draw_circle(p.p, p.r * 2.6, Color(p.c, 0.12 * k), true, -1, true)
			draw_circle(p.p, p.r, Color(p.c, k), true, -1, true)


static func burst(host: Control, at: Vector2, n := 26) -> void:
	if not on():
		return
	var e := Embers.new()
	host.add_child(e)
	e.burst(at, n)
	host.get_tree().create_timer(1.6).timeout.connect(e.queue_free)


## «Скелетон» с бегущим бликом — пока данные грузятся.
class Skeleton extends Control:
	var t := 0.0
	var radius := 14.0

	func _process(dt: float) -> void:
		t = fmod(t + dt, 1.4)
		queue_redraw()

	func _draw() -> void:
		var sb := StyleBoxFlat.new()
		sb.bg_color = Color(1, 1, 1, 0.05)
		sb.set_corner_radius_all(int(radius))
		draw_style_box(sb, Rect2(Vector2.ZERO, size))
		var x := (t / 1.4) * (size.x + 240) - 120
		for i in 12:
			var w := 120.0 - i * 10
			var a := 0.012 + i * 0.002
			var r := Rect2(Vector2(maxf(0, x - w / 2), 0), Vector2(clampf(minf(w, size.x - (x - w / 2)), 0, size.x), size.y))
			if r.size.x > 0 and r.position.x < size.x:
				sb.bg_color = Color(1, 1, 1, a)
				draw_style_box(sb, r)


static func skeleton(h := 64.0, r := 14.0) -> Control:
	var s := Skeleton.new()
	s.custom_minimum_size.y = h
	s.radius = r
	s.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	s.mouse_filter = Control.MOUSE_FILTER_IGNORE
	s.set_process(on())
	return s


## Блик, пробегающий по главной кнопке, когда она появляется (один раз).
static func sheen(b: Control) -> void:
	b.ready.connect(func():
		if not on():
			return
		var clip := Control.new()
		clip.clip_contents = true
		clip.mouse_filter = Control.MOUSE_FILTER_IGNORE
		clip.set_anchors_preset(Control.PRESET_FULL_RECT)
		b.add_child(clip)
		var t := TextureRect.new()
		t.texture = UI.ui_tex("sheen")
		t.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		t.stretch_mode = TextureRect.STRETCH_SCALE
		t.mouse_filter = Control.MOUSE_FILTER_IGNORE
		t.modulate.a = 0.0
		clip.add_child(t)
		var tw := t.create_tween()
		tw.tween_interval(0.35)
		tw.tween_callback(func():
			var w := maxf(b.size.x, 40.0)
			t.size = Vector2(w * 0.7, b.size.y)
			t.position = Vector2(-w * 0.7, 0)
			t.modulate.a = 1.0)
		tw.tween_property(t, "position:x", maxf(b.size.x, 40.0) * 1.2 + 200.0, 0.75).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
		tw.tween_callback(clip.queue_free), CONNECT_ONE_SHOT)


## Лёгкая вибрация (Android): переключение вкладок, успех.
static func haptic(ms := 10) -> void:
	if OS.get_name() == "Android" and on():
		Input.vibrate_handheld(ms)
