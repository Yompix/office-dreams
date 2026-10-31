class_name Van
extends Node3D
## Буханка деда (глава 4): модель tools/lake/van.py. Фары и стопы, колёса крутятся по скорости, кузов качается на дороге,
## руль, дым из выхлопа (кашляет чёрным, когда заводят), плафон и свет из окон в салоне, шапка снега на крыше (стояла в сарае).
## Нос — к −Z (как у машины героев). Места в салоне — SEATS (оси буханки): точка на подушке и куда лицом (градусы).

const SEATS := {
	"driver": [Vector3(-0.5, 1.06, -1.2), 180.0],
	"front": [Vector3(0.5, 1.06, -1.2), 180.0],
	"l1": [Vector3(-0.63, 1.05, -0.42), 90.0],
	"l2": [Vector3(-0.63, 1.05, 0.38), 90.0],
	"l3": [Vector3(-0.63, 1.05, 1.18), 90.0],
	"r1": [Vector3(0.63, 1.05, 0.68), -90.0],
	"r2": [Vector3(0.63, 1.05, 1.42), -90.0],
}
const WHEEL_C := Vector3(-0.5, 1.33, -1.66)       # центр руля
const WHEEL_AXIS := Vector3(0.0, 0.574, -0.819)   # ось руля (обод почти лежит)
const R := 0.38                                   # радиус колеса

var speed := 0.0
var steer := 0.0
var ride := true
var wheels: Array[MeshInstance3D] = []
var wheel_node: MeshInstance3D
var lamps: Array[MeshInstance3D] = []
var tails: Array[MeshInstance3D] = []
var beams: Array[SpotLight3D] = []
var dome: OmniLight3D
var fill: OmniLight3D
var exhaust: CPUParticles3D
var snow_cap: MeshInstance3D
var model: Node3D
var _t := 0.0
var _smoke := 0.0
var _lamp_on: StandardMaterial3D
var _lamp_off: StandardMaterial3D
var _tail_on: StandardMaterial3D
var _tail_off: StandardMaterial3D
var _wheel_b0 := Basis.IDENTITY


func _ready() -> void:
	model = (load("res://assets/models/van.glb") as PackedScene).instantiate() as Node3D
	add_child(model)
	_lamp_off = _m(Color(0.75, 0.75, 0.7), 0.0)
	_lamp_on = _m(Color(1.0, 0.97, 0.88), 4.0)
	_tail_off = _m(Color(0.25, 0.01, 0.01), 0.0)
	_tail_on = _m(Color(0.9, 0.04, 0.02), 2.0)
	for n in model.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		var nm := String(mi.name)
		if nm.begins_with("lamp_") and not nm.begins_with("lamp_rim"):
			lamps.append(mi)
		elif nm.begins_with("tail_"):
			tails.append(mi)
		elif nm.begins_with("wheel_"):
			wheels.append(mi)
		elif nm == "steer":
			wheel_node = mi
			_wheel_b0 = mi.basis
		elif nm == "glass":
			mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	for x in [-0.66, 0.66]:
		var sl := SpotLight3D.new()
		sl.position = Vector3(x, 0.88, -2.26)
		sl.light_color = Color(1.0, 0.9, 0.72)   # старые лампы — желтее
		sl.light_energy = 9.0
		sl.spot_range = 45.0
		sl.spot_angle = 28.0
		sl.spot_attenuation = 0.7
		sl.rotation_degrees = Vector3(-4.0, 0, 0)
		add_child(sl)
		beams.append(sl)
	dome = OmniLight3D.new()   # плафон: вечером в салоне
	dome.position = Vector3(0.0, 1.9, 0.4)
	dome.light_color = Color(1.0, 0.86, 0.62)
	dome.light_energy = 0.0
	dome.omni_range = 3.2
	add_child(dome)
	fill = OmniLight3D.new()   # днём: свет из окон в салон (лица на лавках)
	fill.position = Vector3(0.0, 1.65, 0.1)
	fill.light_color = Color(0.85, 0.9, 1.0)
	fill.light_energy = 0.0
	fill.omni_range = 3.4
	fill.shadow_enabled = false
	add_child(fill)
	exhaust = CPUParticles3D.new()   # выхлоп: слева под задним бампером, назад
	exhaust.position = Vector3(-0.62, 0.42, 2.25)
	exhaust.amount = 70
	exhaust.lifetime = 2.2
	exhaust.direction = Vector3(0, 0.25, 1)
	exhaust.spread = 18.0
	exhaust.initial_velocity_min = 0.6
	exhaust.initial_velocity_max = 1.3
	exhaust.gravity = Vector3(0, 0.25, 0)
	exhaust.scale_amount_min = 0.6
	exhaust.scale_amount_max = 1.4
	var sc := Curve.new()
	sc.add_point(Vector2(0, 0.4))
	sc.add_point(Vector2(1, 2.6))
	exhaust.scale_amount_curve = sc
	var q := QuadMesh.new()
	q.size = Vector2(0.35, 0.35)
	var qm := StandardMaterial3D.new()
	qm.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
	qm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	qm.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	qm.vertex_color_use_as_albedo = true
	var gr := Gradient.new()
	gr.set_color(0, Color(1, 1, 1, 1))
	gr.set_color(1, Color(1, 1, 1, 0))
	var gt := GradientTexture2D.new()
	gt.gradient = gr
	gt.fill = GradientTexture2D.FILL_RADIAL
	gt.fill_from = Vector2(0.5, 0.5)
	gt.fill_to = Vector2(0.5, 0.0)
	qm.albedo_texture = gt
	q.material = qm
	exhaust.mesh = q
	exhaust.color = Color(0.75, 0.75, 0.75, 0.25)
	exhaust.emitting = false
	add_child(exhaust)
	lights(false)


func _m(c: Color, e: float) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	m.roughness = 0.2
	if e > 0.0:
		m.emission_enabled = true
		m.emission = c
		m.emission_energy_multiplier = e
	return m


## Место в салоне: [точка в мире, куда лицом — градусы в мире].
func seat(k: String) -> Array:
	var s: Array = SEATS[k]
	return [to_global(s[0]), float(s[1]) + rotation_degrees.y]


func lights(on: bool) -> void:
	for mi in lamps:
		mi.material_override = _lamp_on if on else _lamp_off
	for mi in tails:
		mi.material_override = _tail_on if on else _tail_off
	for sl in beams:
		sl.visible = on


## Мотор: заглох / работает (серый дымок); cough — схватил и кашляет чёрным.
func engine(on: bool, cough := false) -> void:
	exhaust.emitting = on
	if cough:
		_smoke = 1.0


## Шапка снега на крыше (стояла в сарае под навесом — нет; во дворе — да).
func snow(on: bool) -> void:
	if on and snow_cap == null:
		snow_cap = MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(1.7, 0.16, 3.9)
		snow_cap.mesh = bm
		var sm := StandardMaterial3D.new()
		sm.albedo_color = Color(0.9, 0.92, 0.96)
		sm.roughness = 0.95
		snow_cap.material_override = sm
		snow_cap.position = Vector3(0.0, 2.1, 0.1)
		add_child(snow_cap)
	if snow_cap:
		snow_cap.visible = on


func _process(delta: float) -> void:
	_t += delta
	for w in wheels:   # колёса катятся (нос к −Z: вперёд — вращение вокруг −X)
		w.rotate_object_local(Vector3.RIGHT, -speed / R * delta)
	if wheel_node:
		wheel_node.basis = Basis(WHEEL_AXIS.normalized(), -steer * 2.5) * _wheel_b0
	if model:
		var k := clampf(speed / 12.0, 0.0, 1.0) if ride else 0.0
		model.position.y = (sin(_t * 11.0) * 0.006 + sin(_t * 23.7) * 0.003) * k
		model.rotation.z = sin(_t * 1.7) * 0.004 * k
	if exhaust.emitting:   # кашель: чёрный густой дым, потом — обычный серый
		_smoke = maxf(0.0, _smoke - delta * 0.45)
		var c := Color(0.75, 0.75, 0.75, 0.22).lerp(Color(0.05, 0.05, 0.05, 0.85), _smoke)
		exhaust.color = c
		exhaust.initial_velocity_max = 1.3 + 2.0 * _smoke
