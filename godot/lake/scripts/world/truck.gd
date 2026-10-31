class_name Truck
extends Node3D
## Фура (tools/lake/truck.py): тягач и полуприцеп. Краска кабины — своя у каждой; фары, стопы и оранжевые габариты
## загораются по lights(true); аварийка мигает по hazard = true. Полуприцеп — узел `trailer`, поворачивается вокруг
## сцепки (fold — «сложилась поперёк дороги»). Нос — к −Z.

var trailer: Node3D
var _lamps: Array[MeshInstance3D] = []
var _tails: Array[MeshInstance3D] = []
var _markers: Array[MeshInstance3D] = []
var _beams: Array[SpotLight3D] = []
var _on := {}
var _off := {}
var lit := false
var hazard := false
var _t := 0.0


func setup(paint: Color) -> Truck:
	var model := (load("res://assets/models/truck.glb") as PackedScene).instantiate() as Node3D
	add_child(model)
	_on = {"lamp": _m(Color(1.0, 0.97, 0.9), 5.0, Color(1.0, 0.96, 0.88)), "tail": _m(Color(0.9, 0.04, 0.02), 2.5, Color(1.0, 0.06, 0.02)),
		"marker": _m(Color(1.0, 0.55, 0.05), 3.0, Color(1.0, 0.5, 0.05))}
	_off = {"lamp": _m(Color(0.6, 0.6, 0.58), 0.0, Color.BLACK), "tail": _m(Color(0.25, 0.02, 0.02), 0.0, Color.BLACK),
		"marker": _m(Color(0.5, 0.28, 0.04), 0.0, Color.BLACK)}
	var cab := StandardMaterial3D.new()
	cab.albedo_color = paint
	cab.roughness = 0.35
	cab.clearcoat_enabled = true
	cab.clearcoat = 0.6
	for n in model.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		var nm := String(mi.name)
		if nm.begins_with("cab"):
			mi.material_override = cab
		elif nm.begins_with("lamp"):
			_lamps.append(mi)
		elif nm.begins_with("tail"):
			_tails.append(mi)
		elif nm.begins_with("marker"):
			_markers.append(mi)
		elif nm.begins_with("glass"):
			mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	for n in model.find_children("trailer*", "Node3D", true, false):
		if String(n.name) == "trailer":
			trailer = n
	for x in [-0.95, 0.95]:
		var sl := SpotLight3D.new()
		sl.position = Vector3(x, 0.95, -4.9)
		sl.light_color = Color(1.0, 0.95, 0.85)
		sl.light_energy = 10.0
		sl.spot_range = 45.0
		sl.spot_angle = 28.0
		sl.light_volumetric_fog_energy = 1.2
		sl.rotation_degrees = Vector3(-3.0, 0, 0)
		add_child(sl)
		_beams.append(sl)
	lights(false)
	return self


func _m(c: Color, e: float, ec: Color) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	m.roughness = 0.25
	if e > 0.0:
		m.emission_enabled = true
		m.emission = ec
		m.emission_energy_multiplier = e
	return m


func lights(on: bool) -> void:
	lit = on
	for mi in _lamps:
		mi.material_override = _on.lamp if on else _off.lamp
	for mi in _tails:
		mi.material_override = _on.tail if on else _off.tail
	for mi in _markers:
		mi.material_override = _on.marker if on else _off.marker
	for sl in _beams:
		sl.visible = on


## Полуприцеп поперёк: угол складывания вокруг сцепки (градусы).
func fold(deg: float) -> void:
	if trailer:
		trailer.rotation_degrees.y = deg


func _process(delta: float) -> void:
	if not hazard:
		return
	_t += delta
	var on := fmod(_t, 0.9) < 0.45   # аварийка: габариты и стопы мигают
	for mi in _markers:
		mi.material_override = _on.marker if on else _off.marker
	for mi in _tails:
		mi.material_override = _on.tail if on else _off.tail
