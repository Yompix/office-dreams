extends Control
## Живой фон 1.7: ночное небо (assets/shaders/sky.gdshader), слабая лента сияния, тёплый свет «фонаря» слева сверху
## и до 22 медленных светлячков (одна мягкая текстура, одна пачка).
## Бюджет: небо считается в маленьком SubViewport (1/4 стороны экрана — в 16 раз меньше пикселей; картинка гладкая,
## растягивается без потерь) и только когда меняется — 30 раз в секунду; на экран идёт обычная текстура.
## Через 3 с без касаний всё замирает (приложение на простое не перерисовывает экран); «Меньше анимаций» и
## автотесты — неподвижная картинка.

const FPS := 30.0
const IDLE_SEC := 3.0
const FLIES := 22
const SKY_DIV := 4.0             # небо рисуется в 1/4 стороны экрана

var warm := 1.0                 # сила света фонаря (экран знакомства — ярче)
var _sky: ColorRect
var _vp: SubViewport
var _view: TextureRect
var _mat: ShaderMaterial
var _t := 0.0
var _acc := 0.0
var _idle := 0.0
var _flies: Array = []
var _dot: Texture2D
var _shift := Vector2.ZERO
var _tilt := Vector2.ZERO
var scroll_src: Callable         # () -> float: прокрутка текущего экрана (для параллакса)
static var frozen_t := -1.0      # автотесты: зафиксировать время (скриншоты одинаковые)


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	_vp = SubViewport.new()
	_vp.disable_3d = true
	_vp.transparent_bg = false
	_vp.render_target_update_mode = SubViewport.UPDATE_ONCE
	_vp.size = Vector2i(64, 128)
	add_child(_vp)
	_sky = ColorRect.new()
	_sky.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_mat = ShaderMaterial.new()
	_mat.shader = load("res://assets/shaders/sky.gdshader")
	_mat.set_shader_parameter("accent", Cfg.accent())
	_mat.set_shader_parameter("warm", warm)
	_sky.material = _mat
	_vp.add_child(_sky)
	_view = TextureRect.new()
	_view.texture = _vp.get_texture()
	_view.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_view.stretch_mode = TextureRect.STRETCH_SCALE
	_view.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	_view.set_anchors_preset(Control.PRESET_FULL_RECT)
	_view.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_view.show_behind_parent = true
	add_child(_view)
	_dot = UI.ui_tex("dot")
	var rng := RandomNumberGenerator.new()
	rng.seed = 7
	for i in FLIES:
		_flies.append({"p": Vector2(rng.randf(), rng.randf()), "r": rng.randf_range(2.0, 4.5), "ph": rng.randf() * TAU,
			"sp": rng.randf_range(0.25, 0.8), "k": rng.randf_range(0.6, 1.4), "warm": rng.randf() < 0.75})
	resized.connect(_fit)
	Cfg.accent_changed.connect(func(c): _mat.set_shader_parameter("accent", c))
	_t = 12.0
	_fit()
	_apply()


func _fit() -> void:
	if size.x > 0.0 and size.y > 0.0:
		_mat.set_shader_parameter("px", size)
		# размер в настоящих пикселях экрана / 4
		var k := get_viewport_transform().get_scale().x if is_inside_tree() else 1.0
		_vp.size = Vector2i(maxi(16, int(ceil(size.x * k / SKY_DIV))), maxi(16, int(ceil(size.y * k / SKY_DIV))))
		_sky.size = Vector2(_vp.size)
		_vp.render_target_update_mode = SubViewport.UPDATE_ONCE
	queue_redraw()


func _input(e: InputEvent) -> void:
	if e is InputEventMouseButton or e is InputEventScreenTouch or e is InputEventScreenDrag or e is InputEventKey \
			or (e is InputEventMouseMotion and (e as InputEventMouseMotion).button_mask != 0):
		_idle = 0.0


## Что-то произошло на экране (переход, событие) — оживить фон ещё на пару секунд.
func wake() -> void:
	_idle = 0.0


func _process(d: float) -> void:
	if not FX.on() or not is_visible_in_tree():
		return
	_idle += d
	if _idle > IDLE_SEC:
		return      # замерли: ничего не меняем — экран не перерисовывается
	_acc += d
	if _acc < 1.0 / FPS:
		return
	_t += _acc
	_acc = 0.0
	var sc := 0.0
	if scroll_src.is_valid():
		sc = float(scroll_src.call())
	if OS.has_feature("mobile"):
		var a := Input.get_accelerometer()
		if a.length() > 1.0:
			_tilt = _tilt.lerp(Vector2(clampf(a.x / 9.8, -1, 1), clampf(a.y / 9.8 - 0.6, -1, 1)), 0.08)
	_shift = _shift.lerp(Vector2(_tilt.x * 0.25, -sc * 0.00012 + _tilt.y * 0.02), 0.2)
	_apply()


func _apply() -> void:
	var t := frozen_t if frozen_t >= 0.0 else _t
	_mat.set_shader_parameter("t", t)
	_mat.set_shader_parameter("shift", _shift)
	_vp.render_target_update_mode = SubViewport.UPDATE_ONCE     # небо пересчитывается только на этом кадре
	queue_redraw()


func _draw() -> void:
	var t := frozen_t if frozen_t >= 0.0 else _t
	var a := Cfg.accent()
	for f in _flies:
		var p: Vector2 = f.p
		var x := fposmod(p.x + sin(t * 0.11 * f.k + f.ph) * 0.06 + _shift.x * 0.2 * f.k, 1.0) * size.x
		var y := fposmod(p.y - t * 0.006 * f.sp + cos(t * 0.09 * f.k + f.ph * 1.7) * 0.04 + _shift.y * 2.0 * f.k, 1.0) * size.y
		var tw := 0.35 + 0.65 * (0.5 + 0.5 * sin(t * f.sp * 1.6 + f.ph * 3.0))
		var col: Color = (a.lightened(0.25) if f.warm else Color(0.62, 0.92, 0.86))
		var r: float = f.r
		draw_texture_rect(_dot, Rect2(x - r * 3.0, y - r * 3.0, r * 6.0, r * 6.0), false, Color(col, 0.07 * tw))
		draw_texture_rect(_dot, Rect2(x - r * 0.7, y - r * 0.7, r * 1.4, r * 1.4), false, Color(col.lightened(0.2), 0.42 * tw))
