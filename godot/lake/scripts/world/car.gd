class_name Car
extends Node3D
## Машина отца изнутри (старый седан): торпедо, приборы с часами 03:12, руль, магнитола, бардачок, зеркало, кресла, стёкла,
## дворники, капот в лобовом; фары — свет на дорогу. Своя система координат: вперёд −Z, водитель слева (x < 0), пол — y = 0.
## Свет салона — от приборов (тёплый) и отражённый от снега в фарах (холодный снизу спереди) — так в кадре видно лица.

const DRIVER := Vector3(-0.36, 0.0, 0.3)     # точка посадки водителя (таз — выше)
const PASSENGER := Vector3(0.36, 0.0, 0.3)
var wheel: Node3D
var wipers: Array[Node3D] = []
var glovebox: Node3D
var clock: Label3D
var radio_lbl: Label3D
var dash_light: OmniLight3D
var snow_bounce: OmniLight3D
var headlights: Array[SpotLight3D] = []
var t := 0.0
var wiper_speed := 1.0
var steer := 0.0                               # поворот руля (рад), руки героя — за ним
var wheel_basis0 := Basis.IDENTITY
var column: MeshInstance3D
var notebook: MeshInstance3D
var glove_open := 0.0
var ride := true                               # кузов качается на дороге (на стоянке — нет)
var _y0 = null

var m_plastic: StandardMaterial3D
var m_plastic2: StandardMaterial3D
var m_fabric: StandardMaterial3D
var m_roof: StandardMaterial3D
var m_glass: StandardMaterial3D
var m_paint: StandardMaterial3D
var m_chrome: StandardMaterial3D


func setup(pc: bool) -> void:
	m_plastic = _m(Color(0.045, 0.045, 0.05), 0.55)
	m_plastic2 = _m(Color(0.07, 0.068, 0.07), 0.7)
	m_fabric = _m(Color(0.09, 0.085, 0.08), 0.95)
	m_roof = _m(Color(0.12, 0.115, 0.11), 0.95)
	m_paint = _m(Color(0.12, 0.13, 0.15), 0.35)
	m_paint.metallic = 0.4
	m_chrome = _m(Color(0.6, 0.6, 0.62), 0.25)
	m_chrome.metallic = 0.9
	m_glass = StandardMaterial3D.new()
	m_glass.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m_glass.albedo_color = Color(0.5, 0.6, 0.7, 0.06)
	m_glass.roughness = 0.05
	m_glass.metallic_specular = 0.9
	m_glass.cull_mode = BaseMaterial3D.CULL_DISABLED
	# пол, торпедо, панель приборов
	_box(Vector3(1.6, 0.05, 2.6), Vector3(0, 0.12, 0.2), m_plastic2)
	_box(Vector3(1.56, 0.3, 0.5), Vector3(0, 0.76, -0.62), m_plastic, Vector3(-12, 0, 0))          # торпедо
	_box(Vector3(1.5, 0.06, 0.62), Vector3(0, 0.92, -0.7), m_plastic, Vector3(-6, 0, 0))            # верх торпедо
	_box(Vector3(0.46, 0.12, 0.12), Vector3(-0.36, 0.96, -0.46), m_plastic2, Vector3(-15, 0, 0))      # козырёк приборов
	_box(Vector3(0.3, 0.5, 0.42), Vector3(0, 0.52, -0.3), m_plastic2)                                # центральная консоль
	glovebox = _box(Vector3(0.5, 0.2, 0.04), Vector3(0.36, 0.72, -0.36), m_plastic2)                 # бардачок (крышка)
	# приборы: две шкалы и подсветка
	for x in [-0.45, -0.27]:
		var g := MeshInstance3D.new()
		var cm := CylinderMesh.new()
		cm.top_radius = 0.065
		cm.bottom_radius = 0.065
		cm.height = 0.01
		g.mesh = cm
		var gm := _m(Color(0.02, 0.02, 0.02), 0.4)
		gm.emission_enabled = true
		gm.emission = Color(1.0, 0.55, 0.2)
		gm.emission_energy_multiplier = 0.25
		g.material_override = gm
		g.position = Vector3(x, 0.905, -0.44)
		g.rotation_degrees = Vector3(70, 0, 0)
		add_child(g)
	clock = _label("03:12", Vector3(-0.36, 0.875, -0.43), 0.03, Color(1.0, 0.62, 0.25))
	radio_lbl = _label("", Vector3(0.0, 0.75, -0.44), 0.02, Color(0.55, 0.9, 1.0))
	# руль: обод, спицы, колонка
	wheel = Node3D.new()
	wheel.position = Vector3(-0.36, 0.93, -0.2)
	wheel.rotation_degrees = Vector3(-62, 0, 0)
	add_child(wheel)
	var rim := MeshInstance3D.new()
	var tm := TorusMesh.new()
	tm.inner_radius = 0.165
	tm.outer_radius = 0.19
	tm.rings = 32
	rim.mesh = tm
	rim.material_override = m_plastic
	wheel.add_child(rim)
	for a in [0.0, 120.0, 240.0]:
		var sp := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(0.025, 0.012, 0.17)
		sp.mesh = bm
		sp.material_override = m_plastic
		sp.rotation_degrees = Vector3(0, a, 0)
		sp.position = Vector3(sin(deg_to_rad(a)), 0, cos(deg_to_rad(a))) * 0.085
		wheel.add_child(sp)
	column = _box(Vector3(0.07, 0.07, 0.35), Vector3(-0.36, 0.86, -0.38), m_plastic2, Vector3(-28, 0, 0))   # колонка
	wheel_basis0 = wheel.transform.basis
	# кресла: подушка и спинка с подголовником
	for s in [DRIVER, PASSENGER]:
		_box(Vector3(0.52, 0.14, 0.52), s + Vector3(0, 0.42, 0.05), m_fabric, Vector3(6, 0, 0))
		_box(Vector3(0.52, 0.66, 0.14), s + Vector3(0, 0.82, 0.36), m_fabric, Vector3(-14, 0, 0))
		_box(Vector3(0.26, 0.2, 0.12), s + Vector3(0, 1.28, 0.46), m_fabric, Vector3(-10, 0, 0))
	_box(Vector3(1.5, 0.5, 0.6), Vector3(0, 0.55, 1.3), m_fabric)                                    # заднее сиденье
	notebook = _box(Vector3(0.21, 0.012, 0.28), PASSENGER + Vector3(0.0, 0.66, -0.12), _m(Color(0.1, 0.12, 0.16), 0.8), Vector3(-25, 8, 0))
	var pages := MeshInstance3D.new()
	var pm := BoxMesh.new()
	pm.size = Vector3(0.19, 0.004, 0.26)
	pages.mesh = pm
	pages.material_override = _m(Color(0.86, 0.84, 0.78), 0.9)
	pages.position = Vector3(0, 0.008, 0)
	notebook.add_child(pages)
	# крыша, стойки, двери, лобовое и боковые стёкла
	_box(Vector3(1.56, 0.04, 1.52), Vector3(0, 1.57, 0.64), m_roof)   # крыша — по верхний край лобового (высокому Ною хватает места)
	for sx in [-1.0, 1.0]:
		_box(Vector3(0.07, 0.06, 0.82), Vector3(0.74 * sx, 1.22, -0.37), m_plastic, Vector3(-52, 0, 0))   # стойки
		_box(Vector3(0.06, 0.62, 2.1), Vector3(0.79 * sx, 0.62, 0.35), m_plastic2)                         # двери
		_box(Vector3(0.02, 0.6, 1.7), Vector3(0.8 * sx, 1.23, 0.4), m_glass)                               # боковые стёкла
	var ws := _box(Vector3(1.46, 0.02, 0.86), Vector3(0, 1.22, -0.38), m_glass, Vector3(-52, 0, 0))       # лобовое
	ws.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	_box(Vector3(0.2, 0.06, 0.02), Vector3(0, 1.44, -0.1), m_plastic)                                    # зеркало
	var mir := _box(Vector3(0.18, 0.05, 0.005), Vector3(0, 1.44, -0.088), m_chrome)
	mir.rotation_degrees = Vector3(0, 8, 0)
	# капот и крылья — видны в лобовом
	_box(Vector3(1.7, 0.08, 1.9), Vector3(0, 0.83, -1.8), m_paint, Vector3(4, 0, 0))
	# дворники
	for x in [-0.55, 0.05]:
		var piv := Node3D.new()
		piv.position = Vector3(x, 0.93, -0.82)
		add_child(piv)
		var arm := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(0.52, 0.012, 0.012)
		arm.mesh = bm
		arm.material_override = m_plastic
		arm.position = Vector3(0.26, 0, 0)
		piv.add_child(arm)
		piv.rotation_degrees = Vector3(-52, 0, 0)
		wipers.append(piv)
	# фары
	for x in [-0.62, 0.62]:
		var sl := SpotLight3D.new()
		sl.position = Vector3(x, 0.7, -2.4)
		sl.light_color = Color(1.0, 0.93, 0.8)
		sl.light_energy = 16.0
		sl.spot_range = 75.0
		sl.spot_angle = 24.0
		sl.spot_attenuation = 0.6
		sl.spot_angle_attenuation = 1.6
		sl.shadow_enabled = pc
		sl.light_volumetric_fog_energy = 1.2
		sl.rotation_degrees = Vector3(-3.0, 0, 0)
		add_child(sl)
		headlights.append(sl)
	# свет салона: приборы и снег в фарах
	dash_light = OmniLight3D.new()
	dash_light.position = Vector3(-0.2, 0.98, -0.42)
	dash_light.light_color = Color(1.0, 0.6, 0.3)
	dash_light.light_energy = 0.35
	dash_light.omni_range = 1.1
	dash_light.omni_attenuation = 1.2
	add_child(dash_light)
	snow_bounce = OmniLight3D.new()
	snow_bounce.position = Vector3(0, 0.95, -1.5)
	snow_bounce.light_color = Color(0.72, 0.8, 0.95)
	snow_bounce.light_energy = 0.9
	snow_bounce.omni_range = 3.2
	snow_bounce.omni_attenuation = 1.0
	snow_bounce.shadow_enabled = pc
	add_child(snow_bounce)


func _m(c: Color, rough: float) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	m.roughness = rough
	return m


func _box(size: Vector3, pos: Vector3, mat: Material, rot := Vector3.ZERO) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	mi.mesh = bm
	mi.material_override = mat
	mi.position = pos
	mi.rotation_degrees = rot
	add_child(mi)
	return mi


func _label(txt: String, pos: Vector3, size: float, col: Color) -> Label3D:
	var l := Label3D.new()
	l.text = txt
	l.font = load("res://assets/fonts/IBMPlexMono-Bold.woff2")
	l.font_size = 64
	l.pixel_size = size / 64.0
	l.modulate = col
	l.position = pos
	l.rotation_degrees = Vector3(-20, 0, 0)
	l.shaded = false
	l.double_sided = false
	add_child(l)
	return l


func _process(delta: float) -> void:
	t += delta
	# дворники: ход 0…95°, пауза внизу
	var ph := fmod(t * 0.8 * wiper_speed, 1.6)
	var a := 0.0 if ph > 1.0 else sin(ph * PI) * 95.0
	for i in wipers.size():
		wipers[i].rotation_degrees = Vector3(-52, 0, 0)
		wipers[i].rotate_object_local(Vector3.UP, deg_to_rad(a))
	wheel.transform.basis = wheel_basis0 * Basis(Vector3.UP, steer)
	glovebox.rotation_degrees = Vector3(-glove_open * 70.0, 0, 0)
	# лёгкая тряска дороги: свет приборов дышит вместе с машиной
	dash_light.light_energy = 0.35 + 0.02 * sin(t * 13.0)
	# кузов на неровной зимней дороге: чуть подпрыгивает и кренится в поворотах (головы и волосы отзываются сами)
	if ride:
		if _y0 == null:
			_y0 = position.y
		position.y = _y0 + sin(t * 2.3) * 0.003 + sin(t * 5.7 + 1.0) * 0.0018 + sin(t * 11.3) * 0.0007
		rotation.z = -steer * 0.035 + sin(t * 1.3) * 0.002


## Посадка: герой ставится так, чтобы таз лёг на подушку кресла у спинки (у высоких и маленьких клипы сидят по-разному).
func seat(a: Actor, s: Vector3) -> void:
	var sk := a.skel
	var p := to_local(sk.global_transform * sk.get_bone_global_pose(sk.find_bone("pelvis")).origin)
	a.position += s + Vector3(0, 0.59, 0.17) - p


## Руль — в руки водителя: центр между ладонями, обод по их размаху, плоскость — к груди; колонка — от руля к торпедо.
func fit_wheel(a: Actor) -> void:
	var sk := a.skel
	var gp := func(n: String) -> Vector3:
		return to_local(sk.global_transform * sk.get_bone_global_pose(sk.find_bone(n)).origin)
	var pl: Vector3 = (gp.call("hand_l") + gp.call("middle_01_l")) * 0.5
	var pr: Vector3 = (gp.call("hand_r") + gp.call("middle_01_r")) * 0.5
	var chest: Vector3 = gp.call("spine_03")
	var c := (pl + pr) * 0.5
	var nrm := (chest - c).normalized()
	var side := (pr - pl).normalized()
	var r := pl.distance_to(pr) * 0.5
	var k := r / 0.178
	wheel.position = c
	wheel_basis0 = Basis(side * k, nrm * k, side.cross(nrm).normalized() * k)   # ось обода (Y тора) — к груди
	wheel.transform.basis = wheel_basis0
	var dash := Vector3(c.x, 0.84, -0.52)   # колонка уходит в торпедо под приборами
	column.position = (c + dash) * 0.5
	column.look_at(to_global(dash), Vector3.UP)
	column.scale = Vector3(0.8, 0.8, c.distance_to(dash) / 0.35)
