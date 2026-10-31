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


func build(p: Vector3, n: Vector3, size: Vector2, main: Camera3D) -> void:
	_p = p
	_n = n.normalized()
	_main = main
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
	quad.global_position = p + _n * 0.004
	quad.look_at(quad.global_position - _n, Vector3.UP)   # лицевой стороной — от стены (квад смотрит по +Z)
	quad.rotate_object_local(Vector3.UP, PI)


func _process(_d: float) -> void:
	if _main == null or cam == null:
		return
	# камера зеркала — отражение главной: позиция и направление через плоскость
	var e := _main.global_position
	var r := e - 2.0 * (e - _p).dot(_n) * _n
	cam.fov = clampf(_main.fov * 1.4, 30.0, 80.0)
	cam.look_at_from_position(r, _p, Vector3.UP)
