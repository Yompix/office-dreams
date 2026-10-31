class_name BlackCar
extends Node3D
## Чёрная машина (Хейл): тот же седан, что у героев (tools/lake/car.py), но чёрный лак, тонированные стёкла,
## силуэт водителя за рулём. Фары выключены, пока не скажут lights(true) — тогда загораются фары и задние огни.
## Нос машины — к −Z (как у машины героев).

var lamps: Array[MeshInstance3D] = []
var tails: Array[MeshInstance3D] = []
var beams: Array[SpotLight3D] = []
var _lamp_on: StandardMaterial3D
var _lamp_off: StandardMaterial3D
var _tail_on: StandardMaterial3D
var _tail_off: StandardMaterial3D
var lit := false
var speed := 0.0            # своя скорость вперёд (носом), м/с — поверх того, что едет вместе с дорогой
var paint_color := Color(0.012, 0.012, 0.014)   # другие машины на дороге: свой цвет, обычные стёкла, без водителя
var tinted := true
var has_driver := true


func _ready() -> void:
	var model := (load("res://assets/models/sedan.glb") as PackedScene).instantiate() as Node3D
	add_child(model)
	var paint := StandardMaterial3D.new()
	paint.albedo_color = paint_color
	paint.metallic = 0.5 if tinted else 0.25
	paint.roughness = 0.28 if tinted else 0.4
	paint.clearcoat_enabled = true
	paint.clearcoat = 1.0
	paint.clearcoat_roughness = 0.05
	var glass := StandardMaterial3D.new()
	glass.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	glass.albedo_color = Color(0.02, 0.022, 0.025, 0.88) if tinted else Color(0.08, 0.1, 0.12, 0.35)   # тонировка: внутри почти ничего не видно
	glass.roughness = 0.04
	glass.metallic_specular = 1.0
	_lamp_off = _m(Color(0.25, 0.25, 0.24), 0.0, Color.BLACK)
	_lamp_on = _m(Color(1.0, 0.97, 0.9), 4.0, Color(1.0, 0.96, 0.88))
	_tail_off = _m(Color(0.12, 0.0, 0.0), 0.0, Color.BLACK)
	_tail_on = _m(Color(0.9, 0.04, 0.02), 2.0, Color(1.0, 0.05, 0.02))
	for n in model.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		var nm := String(mi.name)
		if nm == "body":
			mi.material_override = paint
		elif nm == "glass":
			mi.material_override = glass
			mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		elif nm.begins_with("lamp") and not nm.begins_with("lamp_rim"):
			lamps.append(mi)
		elif nm.begins_with("tail"):
			tails.append(mi)
	for x in [-0.62, 0.62]:
		var sl := SpotLight3D.new()
		sl.position = Vector3(x, 0.665, -2.9)
		sl.light_color = Color(1.0, 0.95, 0.85)
		sl.light_energy = 14.0
		sl.spot_range = 60.0
		sl.spot_angle = 26.0
		sl.spot_attenuation = 0.6
		sl.light_volumetric_fog_energy = 1.4
		sl.rotation_degrees = Vector3(-3.0, 0, 0)
		add_child(sl)
		beams.append(sl)
	if not has_driver:
		lights(false)
		return
	# водитель — тёмный силуэт за рулём
	var dark := StandardMaterial3D.new()
	dark.albedo_color = Color(0.008, 0.008, 0.01)
	dark.roughness = 1.0
	var head := MeshInstance3D.new()
	var sm := SphereMesh.new()
	sm.radius = 0.105
	sm.height = 0.25
	head.mesh = sm
	head.material_override = dark
	head.position = Vector3(-0.36, 1.33, 0.18)
	add_child(head)
	var body := MeshInstance3D.new()
	var bm := CapsuleMesh.new()
	bm.radius = 0.2
	bm.height = 0.7
	body.mesh = bm
	body.material_override = dark
	body.position = Vector3(-0.36, 0.98, 0.26)
	body.scale = Vector3(1.15, 1.0, 0.7)
	add_child(body)
	lights(false)


func _process(delta: float) -> void:
	if speed != 0.0:
		position += -basis.z * speed * delta


func _m(c: Color, e: float, ec: Color) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	m.roughness = 0.2
	if e > 0.0:
		m.emission_enabled = true
		m.emission = ec
		m.emission_energy_multiplier = e
	return m


## Заднее правое стекло опущено «на два пальца»: тёмная щель над стеклом.
var gap_side := 1.0           # какое заднее окно опускается: 1 — правое (+X), −1 — левое


func window_gap(on: bool) -> void:
	var g := get_node_or_null("gap")
	if on and g == null:
		var m := MeshInstance3D.new()
		m.name = "gap"
		var bm := BoxMesh.new()
		bm.size = Vector3(0.02, 0.05, 0.62)
		m.mesh = bm
		var mat := StandardMaterial3D.new()
		mat.albedo_color = Color(0.0, 0.0, 0.0)
		mat.roughness = 1.0
		m.material_override = mat
		m.position = Vector3(0.79 * gap_side, 1.27, 0.55)
		add_child(m)
	elif not on and g:
		g.queue_free()


## Фары и задние огни.
func lights(on: bool) -> void:
	lit = on
	for mi in lamps:
		mi.material_override = _lamp_on if on else _lamp_off
	for mi in tails:
		mi.material_override = _tail_on if on else _tail_off
	for sl in beams:
		sl.visible = on
