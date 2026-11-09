class_name PlanarMirror
extends Node3D
## Плоское зеркало (туалет кафе): своя камера — отражение главной камеры относительно плоскости зеркала,
## картинка — на прямоугольник поверх стекла (лево и право меняются местами). Видит всё, кроме слоя 3
## (сам Ной, когда в зеркале его «двойник» — слой 2, см. Ch2.reflection).

var vp: SubViewport
var cam: Camera3D
var quad: MeshInstance3D
var _main: Camera3D
var _p := Vector3.ZERO
var _n := Vector3(-1, 0, 0)
var _size := Vector2.ONE
var _up := Vector3.UP          # «верх» картинки: у лежачего зеркала (вода, глава 5) — вдоль −Z
var _res := -1                 # уровень качества, под который выставлено разрешение отражения
var _frame := 0


func build(p: Vector3, n: Vector3, size: Vector2, main: Camera3D) -> void:
	_p = p
	_n = n.normalized()
	_main = main
	_size = size
	_up = Vector3.FORWARD if absf(_n.dot(Vector3.UP)) > 0.95 else Vector3.UP
	vp = SubViewport.new()
	vp.size = Vector2i(int(512 * size.x / size.y), 512)
	vp.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	add_child(vp)
	cam = Camera3D.new()
	cam.near = 0.05
	vp.add_child(cam)
	quad = MeshInstance3D.new()
	var q := QuadMesh.new()
	q.size = size
	quad.mesh = q
	var m := StandardMaterial3D.new()
	m.albedo_texture = vp.get_texture()
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	m.uv1_scale = Vector3(-1, 1, 1)
	m.uv1_offset = Vector3(1, 0, 0)
	quad.material_override = m
	quad.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(quad)
	quad.global_position = p + _n * 0.002   # чуть перед стеклом модели (иначе её блестящее стекло перекрывает картинку)
	quad.look_at(quad.global_position - _n, _up)   # −Z — в стену, лицевая сторона квада (+Z) — в комнату


## Отражение рисуется заново (вторая отрисовка всей сцены!), только когда зеркало видно в кадре и ближе 30 м; на «Низком» —
## через кадр и вдвое меньше (256 точек по высоте), на «Среднем» — 384. Раньше рисовалось каждый кадр всегда: боковое
## зеркало буханки удваивало отрисовку леса всю дорогу в главе 4 — на встроенной видеокарте это половина кадров в секунду.
func _visible_now() -> bool:
	if not quad.is_visible_in_tree() or _main.global_position.distance_to(_p) > 30.0:
		return false
	var b := quad.global_transform.basis
	var hx := b.x.normalized() * _size.x * 0.5
	var hy := b.y.normalized() * _size.y * 0.5
	for c in [_p, _p + hx + hy, _p + hx - hy, _p - hx + hy, _p - hx - hy]:
		if _main.is_position_in_frustum(c):
			return true
	return false


func _process(_d: float) -> void:
	if _main == null or cam == null:
		return
	var q := clampi(Settings.fx, 0, 3)
	if q != _res:
		_res = q
		var h: int = [256, 384, 512, 512][q]
		vp.size = Vector2i(maxi(16, int(h * _size.x / _size.y)), h)
	_frame += 1
	var vis := _visible_now()
	vp.render_target_update_mode = SubViewport.UPDATE_DISABLED if not vis \
		else (SubViewport.UPDATE_ONCE if q == 0 and _frame % 2 == 0 else (SubViewport.UPDATE_DISABLED if q == 0 else SubViewport.UPDATE_ALWAYS))
	if not vis:
		return
	# камера зеркала — отражение глаза главной камеры за плоскость стекла; смотрит перпендикулярно стеклу в комнату,
	# пирамида видимости — ровно через прямоугольник зеркала (косая проекция): ближняя плоскость = плоскость стекла,
	# поэтому стена за зеркалом и улица не попадают в кадр, а картинка совпадает с тем, что видно в настоящем зеркале
	var e := _main.global_position
	var r := e - 2.0 * (e - _p).dot(_n) * _n
	var d := (_p - r).dot(_n)
	if d < 0.02 or d > 59.0:   # камера дальше 30 м от зеркала: ближняя плоскость за дальней (ошибка Godot «p_far <= p_near»)
		return
	var b := Basis.looking_at(_n, _up)
	cam.global_transform = Transform3D(b, r)
	var loc := b.inverse() * (_p - r)
	cam.projection = Camera3D.PROJECTION_FRUSTUM
	cam.keep_aspect = Camera3D.KEEP_HEIGHT
	cam.near = d
	cam.far = 60.0
	cam.size = _size.y
	cam.frustum_offset = Vector2(loc.x, loc.y)
