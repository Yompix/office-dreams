class_name Car
extends Node3D
## Машина отца (старый седан 90-х): модель tools/lake/car.py — кузов, стёкла, колёса, салон (торпедо, руль, бардачок,
## кресла, двери, потолок); здесь — приборы с часами 03:12, магнитола, дворники, свет фар на дорогу. Своя система координат: вперёд −Z, водитель слева (x < 0), пол — y = 0.
## Свет салона — от приборов (тёплый) и отражённый от снега в фарах (холодный снизу спереди) — так в кадре видно лица.

const DRIVER := Vector3(-0.36, 0.0, 0.3)     # точка посадки водителя (таз — выше)
const INTERIOR := ["dash", "binnacle", "cluster", "stack", "radio", "vent", "glove", "steer", "seat", "door", "headliner", "roofrail",
	"pillar", "visor", "shelf", "carpet", "tunnel", "toeboard", "console", "gear", "knob", "handbrake", "mirror_in", "mirror_glass"]
const PASSENGER := Vector3(0.36, 0.0, 0.3)
const BACK := Vector3(0.38, 0.0, 1.26)         # заднее сиденье справа (Дэнни)
const BACK_L := Vector3(-0.38, 0.0, 1.26)      # заднее слева (Зоя, Айрис, Ева)
const BACK_M := Vector3(0.0, 0.0, 1.26)        # заднее посередине (когда сзади трое)
var wheel: Node3D
var wipers: Array[Node3D] = []
var glovebox: Node3D
var clock: Label3D
var radio_lbl: Label3D
var radio_glow: MeshInstance3D                    # подсветка табло магнитолы (горит, когда играет)
var dash_light: OmniLight3D
var snow_bounce: OmniLight3D
var headlights: Array[SpotLight3D] = []
var lamps: Array[MeshInstance3D] = []           # стёкла фар (светятся)
var lamp_broken := false                         # сбили оленя: левая фара разбита до конца главы 2
var mirror_glass: MeshInstance3D                 # зеркало заднего вида: стекло
var mirror_vp: SubViewport                       # что видно в зеркале (своя камера смотрит назад)
var mirror_cam: Camera3D
var mirror_quad: MeshInstance3D
var mirror_aim := Vector3(0, 1.0, 8.0)           # куда смотрит камера зеркала (оси машины): дорога позади или заднее сиденье
var mirror_aim_name := "road"
var mirror_hide_driver := false                  # «Тень»: в зеркале вместо Ноя его двойник (слой 2), сам Ной (слой 3) скрыт
const DRIVER_LAYER := 4                          # Ной — на своём слое: камера зеркала назад его не видит (не торчит ухо)
const MIRROR := Vector3(0.0, 1.445, -0.068)      # середина зеркала (оси машины)
var t := 0.0
var wiper_speed := 1.0
var steer := 0.0                               # поворот руля (рад), руки героя — за ним
var wheel_basis0 := Basis.IDENTITY
var grip_l := Vector3.INF                        # где кисти водителя держат обод — в координатах руля (ставит fit_wheel)
var grip_r := Vector3.INF
var column: MeshInstance3D
var notebook: MeshInstance3D
var back_fill: OmniLight3D                        # днём: свет из окон на заднее сиденье (глава 2 — там сидят герои)
var glove_open := 0.0
var glove_base := 0.0
var fuel_blink := false                         # мигает лампа бензина
var fuel_lamp: MeshInstance3D
var pills: Node3D                               # пузырёк таблеток в бардачке
var pills_label: Label3D
var wheels: Array[MeshInstance3D] = []          # колёса снаружи: крутятся по скорости
var speed := 25.0                               # м/с, как у дороги; на стоянке — 0
var ride := true                               # кузов качается на дороге (на стоянке — нет)
var _y0 = null

var m_plastic: StandardMaterial3D
var m_plastic2: StandardMaterial3D
var m_fabric: StandardMaterial3D
var m_roof: StandardMaterial3D
var m_glass: ShaderMaterial   # стёкла: снаружи тонировка и Френель, изнутри вуаль (car_glass.gdshader)
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
	# стекло: снаружи — тонировка и отражение под острым углом (сзади-сверху машина не «кабриолет»), заднее темнее
	# лобового; изнутри — прежняя лёгкая вуаль (Color(0.45, 0.52, 0.6, 0.16)), кадры в салоне те же
	m_glass = ShaderMaterial.new()
	m_glass.shader = preload("res://scripts/world/car_glass.gdshader")
	# машина целиком — модель tools/lake/car.py (кузов, стёкла, колёса, салон); детали — по именам
	var model := Prefetch.scene("res://assets/models/sedan.glb").instantiate() as Node3D
	add_child(model)
	var steer_mi: MeshInstance3D
	var handle: Node3D
	for n in model.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		var nm := String(mi.name)
		var inside := false
		for pre in INTERIOR:
			if nm.begins_with(pre):
				inside = true
		if nm.begins_with("mirror_stem") and absf(mi.position.x) < 0.5:
			inside = true
		if not inside:
			mi.layers = 2      # кузов снаружи: подсветка салона его не трогает (не было пятна на капоте)
		if nm.begins_with("lamp") and not nm.begins_with("lamp_rim"):
			mi.material_override = _lamp_mat(Color(1.0, 0.95, 0.85), 3.0)          # фары горят
			lamps.append(mi)
		elif nm.begins_with("tail"):
			mi.material_override = _lamp_mat(Color(1.0, 0.06, 0.03), 0.8)
		elif nm == "body":                                                       # лак: блики фонарей и снега на кузове
			var pm := mi.get_active_material(0) as StandardMaterial3D
			if pm:
				pm = pm.duplicate() as StandardMaterial3D
				pm.clearcoat_enabled = true
				pm.clearcoat = 0.8
				pm.clearcoat_roughness = 0.08
				mi.set_surface_override_material(0, pm)
		elif nm == "glass":
			mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
			for i in mi.mesh.get_surface_count():
				mi.set_surface_override_material(i, m_glass)
		elif nm.begins_with("wheel_"):
			wheels.append(mi)
		elif nm == "steer":
			steer_mi = mi
		elif nm == "glovebox":
			glovebox = mi
		elif nm == "glove_handle":
			handle = mi
		elif nm == "mirror_glass":
			mirror_glass = mi
	if handle and glovebox:
		handle.reparent(glovebox)                                                # ручка открывается вместе с крышкой
	glove_base = glovebox.rotation_degrees.x if glovebox else 0.0
	# приборы: спидометр и тахометр — шкалы с подсветкой и стрелками (90 км/ч, 2600 об/мин)
	for g in [[-0.46, 90.0 / 200.0], [-0.26, 2.6 / 7.0]]:
		var q := MeshInstance3D.new()
		var pm := PlaneMesh.new()
		pm.size = Vector2(0.12, 0.12)
		q.mesh = pm
		var gm := StandardMaterial3D.new()
		gm.albedo_color = Color(0.02, 0.02, 0.02)
		gm.emission_enabled = true
		gm.emission_texture = _dial_tex(g[1])
		gm.emission_operator = BaseMaterial3D.EMISSION_OP_MULTIPLY
		gm.emission = Color(1, 1, 1)
		gm.emission_energy_multiplier = 0.6
		gm.roughness = 0.3
		q.material_override = gm
		q.position = Vector3(g[0], 0.884, -0.404)
		q.rotation_degrees = Vector3(72, 0, 0)
		add_child(q)
	fuel_lamp = MeshInstance3D.new()                  # лампа бензина на щитке (мигает в сцене 4)
	var fq := QuadMesh.new()
	fq.size = Vector2(0.022, 0.014)
	fuel_lamp.mesh = fq
	var fm := StandardMaterial3D.new()
	fm.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	fm.albedo_color = Color(1.0, 0.55, 0.05)
	fuel_lamp.material_override = fm
	fuel_lamp.position = Vector3(-0.36, 0.912, -0.398)
	fuel_lamp.rotation_degrees = Vector3(-18, 0, 0)
	fuel_lamp.visible = false
	add_child(fuel_lamp)
	pills = Node3D.new()                              # пузырёк: янтарный, белая крышка, этикетка
	pills.position = Vector3(0.36, 0.665, -0.46)
	pills.rotation_degrees = Vector3(0, -25, 0)
	add_child(pills)
	var bottle := MeshInstance3D.new()
	var bc := CylinderMesh.new()
	bc.top_radius = 0.021
	bc.bottom_radius = 0.021
	bc.height = 0.075
	bottle.mesh = bc
	var bmat := StandardMaterial3D.new()
	bmat.albedo_color = Color(0.55, 0.28, 0.05, 0.85)
	bmat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	bmat.roughness = 0.15
	bottle.material_override = bmat
	bottle.position = Vector3(0, 0.0375, 0)
	pills.add_child(bottle)
	var cap := MeshInstance3D.new()
	var cc := CylinderMesh.new()
	cc.top_radius = 0.023
	cc.bottom_radius = 0.023
	cc.height = 0.02
	cap.mesh = cc
	cap.material_override = _m(Color(0.92, 0.92, 0.9), 0.5)
	cap.position = Vector3(0, 0.085, 0)
	pills.add_child(cap)
	var lab := MeshInstance3D.new()
	var lc := CylinderMesh.new()
	lc.top_radius = 0.0215
	lc.bottom_radius = 0.0215
	lc.height = 0.04
	lab.mesh = lc
	lab.material_override = _m(Color(0.9, 0.88, 0.82), 0.7)
	lab.position = Vector3(0, 0.036, 0)
	pills.add_child(lab)
	pills_label = Label3D.new()
	pills_label.font = load("res://assets/fonts/Inter-700.woff2")
	pills_label.font_size = 48
	pills_label.pixel_size = 0.00012
	pills_label.modulate = Color(0.08, 0.08, 0.1)
	pills_label.outline_size = 0
	pills_label.position = Vector3(0, 0.036, 0.0222)
	pills_label.shaded = false
	pills.add_child(pills_label)
	pills.visible = false
	if glovebox:                                      # пузырёк — на откинутой крышке бардачка (ниши в торпедо нет)
		pills.reparent(glovebox, false)
		pills.position = Vector3(0.02, 0.07, -0.03)
		pills.rotation_degrees = Vector3(-(glove_base + 75.0), -25, 0)   # когда крышка открыта — стоит прямо
	clock = _label("03:12", Vector3(-0.36, 0.853, -0.4), 0.026, Color(1.0, 0.62, 0.25))
	clock.rotation_degrees = Vector3(-18, 0, 0)
	clock.position += clock.transform.basis.z * 0.0018   # над стеклом табло, а не вровень с ним (не мерцает)
	radio_lbl = _label("", Vector3(0.0, 0.762, -0.362), 0.018, Color(0.55, 0.9, 1.0))
	radio_lbl.rotation_degrees = Vector3(-8, 0, 0)
	_radio_face()
	# руль: модель руля в узле wheel (его ставит fit_wheel в руки водителя), колонка — к торпедо
	wheel = Node3D.new()
	wheel.position = Vector3(-0.36, 0.93, -0.2)
	wheel.rotation_degrees = Vector3(-62, 0, 0)
	add_child(wheel)
	if steer_mi:
		steer_mi.reparent(wheel, false)
		steer_mi.transform = Transform3D.IDENTITY
	column = MeshInstance3D.new()
	var colm := CylinderMesh.new()
	colm.top_radius = 0.02
	colm.bottom_radius = 0.025
	colm.height = 0.35
	colm.radial_segments = 16
	column.mesh = colm
	column.material_override = m_plastic
	add_child(column)
	column.position = Vector3(-0.36, 0.86, -0.38)
	wheel_basis0 = wheel.transform.basis
	notebook = _box(Vector3(0.21, 0.012, 0.28), PASSENGER + Vector3(0.0, 0.66, -0.12), _m(Color(0.1, 0.12, 0.16), 0.8), Vector3(-25, 8, 0))
	var pages := MeshInstance3D.new()
	var pm2 := BoxMesh.new()
	pm2.size = Vector3(0.19, 0.004, 0.26)
	pages.mesh = pm2
	pages.material_override = _m(Color(0.86, 0.84, 0.78), 0.9)
	pages.position = Vector3(0, 0.008, 0)
	notebook.add_child(pages)
	# дворники
	for x in [-0.55, 0.05]:
		var piv := Node3D.new()
		piv.position = Vector3(x, 0.95, -0.7)
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
		sl.position = Vector3(x, 0.665, -2.9)
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
	dash_light.light_volumetric_fog_energy = 0.0
	dash_light.light_specular = 0.15             # без яркой точки-отражения в лобовом
	dash_light.light_cull_mask = 1 | 2 | 4          # салон, люди и их «двойники» в зеркале
	add_child(dash_light)
	snow_bounce = OmniLight3D.new()
	snow_bounce.position = Vector3(0, 1.3, -1.35)   # над капотом, перед лобовым: свет снега в фарах — снизу спереди
	snow_bounce.light_color = Color(0.72, 0.8, 0.95)
	snow_bounce.light_energy = 0.9
	snow_bounce.omni_range = 3.2
	snow_bounce.omni_attenuation = 1.0
	snow_bounce.light_volumetric_fog_energy = 0.0   # подсветка-«отражение от снега»: в тумане не светится шаром
	snow_bounce.light_cull_mask = 1 | 2 | 4         # только салон и люди
	snow_bounce.shadow_enabled = pc
	add_child(snow_bounce)
	back_fill = OmniLight3D.new()
	back_fill.position = Vector3(0.0, 1.42, 1.0)
	back_fill.light_color = Color(0.74, 0.8, 0.92)
	back_fill.light_energy = 0.4
	back_fill.omni_range = 1.5
	back_fill.omni_attenuation = 1.3
	back_fill.light_volumetric_fog_energy = 0.0
	back_fill.light_specular = 0.1
	back_fill.light_cull_mask = 1 | 2 | 4
	back_fill.visible = false
	add_child(back_fill)


func _m(c: Color, rough: float) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	m.roughness = rough
	return m


## Шкала прибора: деления по дуге 240°, крупные — через пять, стрелка на доле f.
static func _dial_tex(f: float) -> ImageTexture:
	var n := 256
	var img := Image.create(n, n, false, Image.FORMAT_RGBA8)
	img.fill(Color(0, 0, 0, 1))
	var c := Vector2(n, n) * 0.5
	var tick := Color(1.0, 0.62, 0.28)
	for i in 41:
		var a := deg_to_rad(150.0 + i * 6.0)
		var big := i % 5 == 0
		var u := Vector2(cos(a), sin(a))
		for k in 30:
			var p := c + u * lerpf(n * (0.33 if big else 0.39), n * 0.45, k / 29.0)
			for w in ([-1, 0, 1, 2] if big else [0, 1]):
				img.set_pixelv((p + Vector2(-u.y, u.x) * w).clamp(Vector2.ZERO, Vector2(n - 1, n - 1)), tick)
	var na := deg_to_rad(150.0 + 240.0 * f)
	var nu := Vector2(cos(na), sin(na))
	for k in 110:
		var p := c + nu * lerpf(-n * 0.05, n * 0.4, k / 109.0)
		for w in [-2, -1, 0, 1, 2]:
			img.set_pixelv((p + Vector2(-nu.y, nu.x) * w * 0.6).clamp(Vector2.ZERO, Vector2(n - 1, n - 1)), Color(1.0, 0.25, 0.1))
	for y in range(-9, 10):
		for x in range(-9, 10):
			if x * x + y * y < 80:
				img.set_pixel(int(c.x) + x, int(c.y) + y, Color(0.15, 0.1, 0.08))
	img.generate_mipmaps()
	return ImageTexture.create_from_image(img)


func _lamp_mat(c: Color, e: float) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = c * 0.6
	m.emission_enabled = true
	m.emission = c
	m.emission_energy_multiplier = e
	m.roughness = 0.1
	return m


## Лицевая панель магнитолы: табло (светится, когда играет), две ручки и кнопки станций.
func _radio_face() -> void:
	var face := Node3D.new()
	face.position = Vector3(0.0, 0.762, -0.3645)
	face.rotation_degrees = Vector3(-8, 0, 0)
	add_child(face)
	radio_glow = MeshInstance3D.new()
	var q := QuadMesh.new()
	q.size = Vector2(0.078, 0.022)
	radio_glow.mesh = q
	var gm := StandardMaterial3D.new()
	gm.albedo_color = Color(0.015, 0.03, 0.04)
	gm.roughness = 0.2
	gm.emission_enabled = true
	gm.emission = Color(0.08, 0.3, 0.42)
	gm.emission_energy_multiplier = 0.0
	radio_glow.material_override = gm
	radio_glow.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	face.add_child(radio_glow)
	var km := _m(Color(0.045, 0.045, 0.05), 0.3)
	var cap := _m(Color(0.5, 0.5, 0.52), 0.25)
	for x in [-0.069, 0.069]:
		var k := MeshInstance3D.new()
		var cm := CylinderMesh.new()
		cm.top_radius = 0.0085
		cm.bottom_radius = 0.0098
		cm.height = 0.013
		k.mesh = cm
		k.material_override = km
		k.rotation_degrees = Vector3(90, 0, 0)
		k.position = Vector3(x, 0.0, 0.0065)
		face.add_child(k)
		var c := _box(Vector3(0.0022, 0.009, 0.002), Vector3.ZERO, cap)   # риска на ручке
		c.reparent(face, false)
		c.position = Vector3(x, 0.004, 0.0132)
	for i in 5:
		var b := _box(Vector3(0.0105, 0.0055, 0.004), Vector3.ZERO, km)
		b.reparent(face, false)
		b.position = Vector3(-0.026 + i * 0.013, -0.0175, 0.002)


## Табло магнитолы: частота (пусто — выключена).
func radio_display(t: String) -> void:
	radio_lbl.text = t
	if radio_glow:
		(radio_glow.material_override as StandardMaterial3D).emission_energy_multiplier = 0.7 if t != "" else 0.0


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
	if glovebox:
		glovebox.rotation_degrees.x = glove_base + glove_open * 75.0   # крышка откидывается вниз, к пассажиру
	for w in wheels:
		w.rotate_object_local(Vector3.RIGHT, -speed / 0.325 * delta)
	if fuel_lamp:
		fuel_lamp.visible = fuel_blink and fmod(t, 1.1) < 0.6
	_mirror_follow()
	# лёгкая тряска дороги: свет приборов дышит вместе с машиной
	dash_light.light_energy = 0.35 + 0.02 * sin(t * 13.0)
	# кузов на неровной зимней дороге: чуть подпрыгивает и кренится в поворотах (головы и волосы отзываются сами)
	if ride:
		if _y0 == null:
			_y0 = position.y
		position.y = _y0 + sin(t * 2.3) * 0.003 + sin(t * 5.7 + 1.0) * 0.0018 + sin(t * 11.3) * 0.0007
		rotation.z = -steer * 0.035 + sin(t * 1.3) * 0.002


## Зеркало заднего вида: включить (на крупных планах зеркала) — своя камера смотрит назад, картинка зеркально на стекле.
## aim: "road" — дорога позади (через заднее стекло), "back" — заднее сиденье.
func mirror(on: bool, aim := "road") -> void:
	if mirror_glass == null:
		return
	if mirror_vp == null:
		mirror_vp = SubViewport.new()
		mirror_vp.size = Vector2i(1024, 240) if Settings.fx >= 1 else Vector2i(512, 120)   # «Низкое» — вдвое меньше
		mirror_vp.render_target_update_mode = SubViewport.UPDATE_DISABLED
		add_child(mirror_vp)
		mirror_cam = Camera3D.new()
		mirror_cam.fov = 17.0
		mirror_cam.near = 0.05
		mirror_vp.add_child(mirror_cam)
		mirror_quad = MeshInstance3D.new()
		var q := QuadMesh.new()
		q.size = Vector2(0.226, 0.053)
		mirror_quad.mesh = q
		var m := StandardMaterial3D.new()
		m.albedo_texture = mirror_vp.get_texture()
		m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		m.uv1_scale = Vector3(-1, 1, 1)               # в зеркале лево и право меняются местами
		m.uv1_offset = Vector3(1, 0, 0)
		mirror_quad.material_override = m
		mirror_quad.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		mirror_quad.position = Vector3(0, 0, 0.0028)
		mirror_glass.add_child(mirror_quad)
	mirror_aim = {"back": Vector3(0.0, 0.95, 1.35), "self": Vector3(-0.36, 1.27, 0.22)}.get(aim, Vector3(0.0, 1.0, 8.0))
	mirror_aim_name = aim
	mirror_cam.fov = 26.0 if aim == "self" else 17.0
	mirror_mask()
	mirror_vp.render_target_update_mode = SubViewport.UPDATE_ALWAYS if on else SubViewport.UPDATE_DISABLED
	mirror_quad.visible = on
	_mirror_follow()


## Кого видит зеркало: назад — без Ноя и его двойника; на себя — Ноя, а при «Тени» — двойника вместо него.
## У Ноя кроме своего слоя есть слой солнечных теней (Actor.SUN_LAYER) — его тоже убираем из маски, иначе Ной виден
## через него (у остальных героев есть слой 1, они остаются в зеркале).
func mirror_mask() -> void:
	if mirror_cam == null:
		return
	if mirror_aim_name != "self":
		mirror_cam.cull_mask = 0xFFFFF & ~DRIVER_LAYER & ~2 & ~Actor.SUN_LAYER
	elif mirror_hide_driver:
		mirror_cam.cull_mask = 0xFFFFF & ~DRIVER_LAYER & ~Actor.SUN_LAYER
	else:
		mirror_cam.cull_mask = 0xFFFFF


func _mirror_follow() -> void:
	if mirror_cam and mirror_quad and mirror_quad.visible:
		mirror_cam.global_transform = global_transform * Transform3D(Basis(), MIRROR + Vector3(0, -0.01, 0.06))
		mirror_cam.look_at(to_global(mirror_aim))


## Разбитая фара (сбили оленя): левая гаснет — стекло тёмное, в трещинах, луч выключен; дальше светит одна.
func break_lamp() -> void:
	lamp_broken = true
	for sl in headlights:
		if sl.position.x < 0.0:
			sl.visible = false
	for mi in lamps:
		if to_local(mi.global_transform * mi.get_aabb().get_center()).x < 0.0:
			var m := StandardMaterial3D.new()
			m.roughness = 0.35
			m.metallic_specular = 0.9
			m.albedo_texture = _crack_tex()
			mi.material_override = m


## Фары: включить или погасить (разбитая левая так и не светит).
func lights(on: bool) -> void:
	for sl in headlights:
		sl.visible = on and not (lamp_broken and sl.position.x < 0.0)


## Трещины на стекле фары (белёсые лучи от удара).
func _crack_tex() -> ImageTexture:
	var n := 128
	var img := Image.create(n, n, false, Image.FORMAT_RGBA8)
	img.fill(Color(0.05, 0.05, 0.055, 1))
	var rng := RandomNumberGenerator.new()
	rng.seed = 5
	var c := Vector2(n * 0.4, n * 0.55)
	for i in 14:
		var a := TAU * i / 14.0 + rng.randf() * 0.3
		var p := c
		for k in rng.randi_range(25, 70):
			a += rng.randf_range(-0.15, 0.15)
			p += Vector2(cos(a), sin(a))
			if p.x < 1 or p.y < 1 or p.x > n - 2 or p.y > n - 2:
				break
			img.set_pixelv(p, Color(0.55, 0.57, 0.6, 1))
	img.generate_mipmaps()
	return ImageTexture.create_from_image(img)


## Посадка: герой ставится так, чтобы таз лёг на подушку кресла у спинки (у высоких и маленьких клипы сидят по-разному).
func seat(a: Actor, s: Vector3, h := 0.59, back := 0.17) -> void:
	var sk := a.skel
	var p := to_local(sk.global_transform * sk.get_bone_global_pose(sk.find_bone("pelvis")).origin)
	a.position += s + Vector3(0, h, back) - p


## Пузырёк в бардачке: показать (с надписью на этикетке) или убрать.
func show_pills(v: bool, label := "") -> void:
	pills.visible = v
	if label != "":
		pills_label.text = label


## Запястье (оси машины) → в осях руля, со сдвигом: пустота кулака (hollow) ложится на обод — радиус 0,178 в плоскости
## обода. Руль не меньше 0,85 своего размера, и кулаки в клипе бывают ближе друг к другу, чем обод: тогда кисти чуть
## расходятся к ободу, а не обод сжимается до игрушечного.
func _on_rim(wrist: Vector3, hollow: Vector3) -> Vector3:
	var w := wheel.to_local(to_global(wrist))
	var h := wheel.to_local(to_global(hollow))
	var r := Vector2(h.x, h.z)
	if r.length() < 0.01:
		return w
	var on := r.normalized() * 0.178
	return w + Vector3(on.x - r.x, -h.y, on.y - r.y)


## Руль — в руки водителя: центр между кулаками, обод по их размаху, плоскость — к груди; колонка — от руля к торпедо.
func fit_wheel(a: Actor) -> void:
	var sk := a.skel
	var gp := func(n: String) -> Vector3:
		return to_local(sk.global_transform * sk.get_bone_global_pose(sk.find_bone(n)).origin)
	# обод — через пустоту сжатого кулака: на 2,5 см перед серединой ладони (по нормали ладони). Раньше — через саму
	# середину ладони (линию костей): обод шёл сквозь мякоть, а пальцы сжимались рядом с ним (кадр владельца, 2 ноя)
	var inv := global_transform.basis.orthonormalized().inverse()
	var pl: Vector3 = (gp.call("hand_l") + gp.call("middle_01_l")) * 0.5 + (inv * a.palm_normal("_l")).normalized() * 0.025
	var pr: Vector3 = (gp.call("hand_r") + gp.call("middle_01_r")) * 0.5 + (inv * a.palm_normal("_r")).normalized() * 0.025
	var chest: Vector3 = gp.call("spine_03")
	var c := (pl + pr) * 0.5
	var nrm := (chest - c).normalized()
	var side := (pr - pl).normalized()
	var r := pl.distance_to(pr) * 0.5
	var k := clampf(r / 0.178, 0.85, 1.15)   # руль не раздувается и не сжимается, если кисти в клипе разошлись или сошлись
	wheel.position = c
	wheel_basis0 = Basis(side * k, nrm * k, side.cross(nrm).normalized() * k)   # ось обода (Y тора) — к груди
	wheel.transform.basis = wheel_basis0 * Basis(Vector3.UP, steer)
	# кисти водителя держат обод там, где легли: точки — в координатах руля, поворачиваются вместе с ним (Game._hold_wheel)
	grip_l = _on_rim(gp.call("hand_l"), pl)
	grip_r = _on_rim(gp.call("hand_r"), pr)
	# колонка — тонкая, от ступицы прямо вперёд и чуть вниз, к щитку приборов (раньше — толстая труба к точке под щитком,
	# через колени водителя)
	var end := Vector3(c.x, c.y - 0.18, -0.42)
	column.position = (c + end) * 0.5
	column.look_at(to_global(end), Vector3.UP)
	column.rotate_object_local(Vector3.RIGHT, PI / 2)   # цилиндр стоит по Y — кладём вдоль направления на щиток
	column.scale = Vector3(1.0, c.distance_to(end) / 0.35, 1.0)


## Дверь — передняя водителя ("l") и пассажира ("r"), задние ("rl", "rr"). Кузов, стёкла и обивка в модели — по одной сетке на машину,
## поэтому дверь вырезается из них по шву: передний край — у стойки A (z −0,60), задний — у стойки B (z 0,44), снизу —
## порог, сверху — крыша; треугольники двери переходят в отдельную сетку на петле у переднего края. Закрытая дверь
## выглядит как раньше (те же треугольники на тех же местах). Вырезается один раз; door_open(side, 0…1) — открыть.
const DOOR_Z := Vector2(-0.6, 0.44)     # передняя дверь: от стойки A до стойки B
const DOOR_ZR := Vector2(0.47, 1.5)     # задняя ("rl" / "rr"): от стойки B до заднего крыла
func door(side: String) -> Node3D:
	var nm := "door_pivot_" + side
	var pv := get_node_or_null(nm) as Node3D
	if pv:
		return pv
	var sx := -1.0 if side.ends_with("l") else 1.0
	var zr := DOOR_ZR if side.length() == 2 else DOOR_Z
	pv = Node3D.new()
	pv.name = nm
	add_child(pv)
	pv.position = Vector3(sx * 0.86, 0.0, zr.x)
	var inv := global_transform.affine_inverse()
	for part in [["body", 0.22, 1.0], ["glass", 0.9, 1.52], ["door_" + side.right(1), -1.0, 3.0]]:
		var src := find_child(String(part[0]), true, false) as MeshInstance3D
		if src == null or src.mesh == null:
			continue
		var xf := inv * src.global_transform   # вершины сетки → оси машины
		var keep := ArrayMesh.new()
		var cut := ArrayMesh.new()
		for s in src.mesh.get_surface_count():
			var arr := src.mesh.surface_get_arrays(s)
			var vs: PackedVector3Array = arr[Mesh.ARRAY_VERTEX]
			var idx: PackedInt32Array = arr[Mesh.ARRAY_INDEX] if arr[Mesh.ARRAY_INDEX] != null else PackedInt32Array()
			if idx.is_empty():
				idx.resize(vs.size())
				for i in vs.size():
					idx[i] = i
			var ik := PackedInt32Array()
			var ic := PackedInt32Array()
			for t in range(0, idx.size() - 2, 3):
				var c := xf * ((vs[idx[t]] + vs[idx[t + 1]] + vs[idx[t + 2]]) / 3.0)
				var on := c.x * sx > 0.5 and c.z > zr.x and c.z < zr.y and c.y > float(part[1]) and c.y < float(part[2])
				var dst := ic if on else ik
				dst.append(idx[t])
				dst.append(idx[t + 1])
				dst.append(idx[t + 2])
			var mat := src.get_active_material(s)
			for pair in [[keep, ik], [cut, ic]]:
				var am: ArrayMesh = pair[0]
				var ii: PackedInt32Array = pair[1]
				if ii.is_empty():
					continue
				var a2 := arr.duplicate()
				a2[Mesh.ARRAY_INDEX] = ii
				am.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, a2)
				if mat:
					am.surface_set_material(am.get_surface_count() - 1, mat)
		if cut.get_surface_count() == 0:
			continue
		for s in src.mesh.get_surface_count():   # переопределённые материалы — теперь в самих сетках
			src.set_surface_override_material(s, null)
		src.mesh = keep
		var dm := MeshInstance3D.new()
		dm.name = String(part[0]) + "_door_" + side
		dm.mesh = cut
		dm.layers = src.layers
		dm.cast_shadow = src.cast_shadow
		pv.add_child(dm)
		dm.transform = pv.transform.affine_inverse() * xf
	return pv


## Открыть / закрыть дверь ("l", "r" — передние, "rl", "rr" — задние): k — доля (1 — настежь, 62°); dur — секунды.
func door_open(side: String, k: float, dur := 0.6) -> Tween:
	var pv := door(side)
	var tw := pv.create_tween()
	tw.tween_property(pv, "rotation_degrees:y", (-62.0 if side.ends_with("l") else 62.0) * k, dur).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_OUT)
	return tw


## Иней на стёклах (гл. 2): k 0…1 — доля, t — секунды (≤ 0 — сразу). С обеих сторон одинаково.
func set_frost(k: float, t := -1.0) -> void:
	if m_glass == null:
		return
	if t <= 0.0:
		m_glass.set_shader_parameter("frost", k)
		return
	var k0 := float(m_glass.get_shader_parameter("frost")) if m_glass.get_shader_parameter("frost") != null else 0.0
	create_tween().tween_method(func(v: float) -> void: m_glass.set_shader_parameter("frost", v), k0, k, t)


## Мотор заглушён ключом: подсветка приборов, шкалы, магнитола и лампа бензина гаснут за dur секунд; фары — сразу.
func dash_off(dur := 0.6) -> void:
	lights(false)
	fuel_blink = false
	if fuel_lamp:
		fuel_lamp.visible = false
	var tw := create_tween().set_parallel()
	if dash_light:
		tw.tween_property(dash_light, "light_energy", 0.0, dur)
	if radio_glow:
		radio_glow.visible = false
	for n in get_children():   # шкалы спидометра и тахометра — светящиеся квадраты у щитка
		if n is MeshInstance3D and (n as MeshInstance3D).material_override is StandardMaterial3D:
			var m := (n as MeshInstance3D).material_override as StandardMaterial3D
			if m.emission_enabled and m.emission_texture != null:
				tw.tween_property(m, "emission_energy_multiplier", 0.0, dur)
	if clock:
		tw.tween_property(clock, "modulate:a", 0.0, dur)
	for l in lamps:   # стёкла фар больше не светятся
		var lm := l.material_override as StandardMaterial3D
		if lm:
			lm = lm.duplicate() as StandardMaterial3D
			l.material_override = lm
			tw.tween_property(lm, "emission_energy_multiplier", 0.0, dur * 0.5)


## Мотор заведён (отъезд): фары, подсветка приборов и шкал снова горят.
func dash_on(dur := 0.4) -> void:
	lights(true)
	var tw := create_tween().set_parallel()
	if dash_light:
		tw.tween_property(dash_light, "light_energy", 0.35, dur)
	for n in get_children():
		if n is MeshInstance3D and (n as MeshInstance3D).material_override is StandardMaterial3D:
			var m := (n as MeshInstance3D).material_override as StandardMaterial3D
			if m.emission_enabled and m.emission_texture != null:
				tw.tween_property(m, "emission_energy_multiplier", 0.6, dur)
	if clock:
		tw.tween_property(clock, "modulate:a", 1.0, dur)
	for l in lamps:
		var lm := l.material_override as StandardMaterial3D
		if lm:
			tw.tween_property(lm, "emission_energy_multiplier", 3.0, dur * 0.5)

