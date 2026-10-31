class_name DriveScene
extends Node3D
## Ночная трасса с машиной: окружение (небо Poly Haven едва светит, туман, на ПК — объёмный туман для лучей фар), луна,
## дорога, машина. Планы камеры — в координатах машины (SHOTS): «как в кино» — руки на руле, лицо Айрис, дорога глазами Ноя.

var road: Road
var car: Car
var env: Environment
var moon: DirectionalLight3D
var pc := true
var station: Node3D
var props := {}            # реквизит заправки по именам (касса, телевизор…)
const LOT := Rect2(4.2, -28.0, 33.6, 46.0)   # площадка заправки (x, z) справа от дороги
const FLOOR := 0.38                          # пол магазина и крыльцо

## планы на заправке — в координатах мира: [позиция, куда смотрит, угол обзора]
const STATION_SHOTS := {
	"st_wide": [Vector3(1.0, 3.4, 14.0), Vector3(16.0, 1.8, -6.0), 52.0],
	"st_sign": [Vector3(-1.5, 5.6, 10.0), Vector3(5.6, 6.4, 6.0), 34.0],
	"st_arrive": [Vector3(9.0, 1.5, -16.0), Vector3(14.0, 1.1, -4.0), 46.0],
	"st_danny": [Vector3(18.7, 1.1, -2.4), Vector3(20.3, 1.05, -3.3), 34.0],
	"st_two": [Vector3(18.6, 1.3, -5.6), Vector3(19.9, 0.95, -3.3), 44.0],
	"st_pump": [Vector3(15.9, 1.65, -0.8), Vector3(13.2, 1.25, -3.3), 40.0],
	"st_door": [Vector3(17.2, 1.75, 1.2), Vector3(21.0, 1.5, -2.0), 46.0],
	"st_counter": [Vector3(25.9, 2.0, -6.3), Vector3(26.4, 1.75, -9.3), 44.0],
	"st_tv": [Vector3(26.25, 2.45, -10.0), Vector3(26.4, 2.65, -11.45), 30.0],
	"st_roman": [Vector3(26.9, 1.85, -7.4), Vector3(26.4, 1.85, -9.3), 34.0],
	"st_hand": [Vector3(25.2, 1.35, -9.3), Vector3(26.2, 1.0, -8.9), 42.0],
	"st_noah_face": [Vector3(26.7, 1.85, -9.3), Vector3(26.4, 1.85, -7.5), 34.0],
	"st_knuckles": [Vector3(13.6, 1.3, -3.2), Vector3(13.05, 1.0, -4.05), 32.0],
	"st_window": [Vector3(19.4, 1.55, -5.2), Vector3(22.4, 0.6, -3.7), 40.0],
	"st_danny_stare": [Vector3(19.1, 1.05, -4.2), Vector3(20.25, 0.98, -3.3), 32.0],
	"st_kids": [Vector3(16.4, 1.62, -2.0), Vector3(19.65, 1.05, -3.4), 40.0],
	"st_noah_out": [Vector3(19.2, 1.45, -3.25), Vector3(17.0, 1.62, -2.5), 36.0],
}

## план → [позиция камеры, куда смотрит, угол обзора] (машина: вперёд −Z, водитель слева)
const SHOTS := {
	"pov": [Vector3(-0.36, 1.2, 0.26), Vector3(-0.2, 0.75, -25.0), 64.0],
	"hands": [Vector3(0.06, 1.06, -0.33), Vector3(-0.36, 0.93, -0.16), 38.0],
	"noah": [Vector3(0.28, 1.12, -0.18), Vector3(-0.36, 1.18, 0.3), 40.0],
	"iris": [Vector3(-0.26, 1.14, -0.12), Vector3(0.36, 1.1, 0.32), 40.0],
	"two": [Vector3(0.0, 1.22, -0.6), Vector3(0.0, 1.05, 0.45), 58.0],
	"notebook": [Vector3(0.22, 1.18, -0.02), Vector3(0.38, 0.62, 0.2), 42.0],
	"radio": [Vector3(-0.08, 0.98, -0.25), Vector3(0.0, 0.8, -0.45), 34.0],
	"glovebox": [Vector3(0.05, 1.05, 0.05), Vector3(0.36, 0.72, -0.42), 44.0],
	"front": [Vector3(0.6, 1.45, -5.5), Vector3(0.0, 1.0, 0.0), 40.0],
	"road": [Vector3(-0.36, 1.17, 0.24), Vector3(-0.36, 0.6, -40.0), 50.0],
	"cluster": [Vector3(-0.3, 1.05, -0.16), Vector3(-0.36, 0.89, -0.41), 30.0],
	"pills": [Vector3(0.3, 0.86, -0.2), Vector3(0.36, 0.7, -0.46), 30.0],
	"back": [Vector3(0.05, 1.25, -0.25), Vector3(0.32, 0.92, 1.4), 52.0],
}


func setup(is_pc: bool) -> void:
	pc = is_pc
	env = Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.008, 0.01, 0.016)
	var sky := Sky.new()
	var psm := PanoramaSkyMaterial.new()
	psm.panorama = load("res://assets/ph/narrow_moonlit_road_1k.hdr")
	sky.sky_material = psm
	sky.radiance_size = Sky.RADIANCE_SIZE_64
	env.sky = sky
	env.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
	env.ambient_light_energy = 0.018
	env.reflected_light_source = Environment.REFLECTION_SOURCE_SKY
	env.tonemap_mode = Environment.TONE_MAPPER_ACES
	env.tonemap_exposure = 1.0
	env.tonemap_white = 1.6
	env.fog_enabled = true
	env.fog_light_color = Color(0.03, 0.035, 0.05)
	env.fog_density = 0.012
	env.glow_enabled = true
	env.glow_bloom = 0.0
	env.glow_hdr_threshold = 0.9
	env.glow_intensity = 0.7
	env.glow_blend_mode = Environment.GLOW_BLEND_MODE_ADDITIVE
	if pc:
		env.volumetric_fog_enabled = true
		env.volumetric_fog_density = 0.007
		env.volumetric_fog_albedo = Color(0.85, 0.88, 0.95)
		env.volumetric_fog_length = 80.0
		env.ssao_enabled = true
	var we := WorldEnvironment.new()
	we.environment = env
	add_child(we)
	moon = DirectionalLight3D.new()
	moon.light_color = Color(0.55, 0.65, 0.9)
	moon.light_energy = 0.025
	moon.rotation_degrees = Vector3(-35, 140, 0)
	moon.shadow_enabled = false
	add_child(moon)
	road = Road.new()
	add_child(road)
	road.setup(pc)
	car = Car.new()
	add_child(car)
	car.setup(pc)


## Заправка «24»: постройки (tools/lake/station.py), площадка, свет навеса, магазина и вывески, реквизит Poly Haven;
## машина — у колонки, стоит.
func park_at_station(lang := "ru") -> void:
	road.park(LOT)
	station = (load("res://assets/models/station.glb") as PackedScene).instantiate() as Node3D
	add_child(station)
	for n in station.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		var nm := String(mi.name)
		if nm.begins_with("window") or nm.contains("glass") or nm.begins_with("fridge_glow"):
			mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	var lot := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = LOT.size
	pm.subdivide_width = 16
	pm.subdivide_depth = 16
	lot.mesh = pm
	lot.material_override = road.lot_material()
	lot.position = Vector3(LOT.get_center().x, 0.004, LOT.get_center().y)
	add_child(lot)
	# свет: лампы навеса (холодные, конусы в снегу), лампы в магазине, витрина на снег, красная вывеска
	for x in [12.0, 16.0]:
		for z in [-9.0, -5.0, -1.0]:
			var sl := SpotLight3D.new()
			sl.position = Vector3(x, 4.66, z)
			sl.rotation_degrees = Vector3(-90, 0, 0)
			sl.light_color = Color(0.9, 0.95, 1.0)
			sl.light_energy = 2.6
			sl.spot_range = 9.0
			sl.spot_angle = 64.0
			sl.spot_attenuation = 0.9
			sl.shadow_enabled = pc and z == -5.0
			sl.light_volumetric_fog_energy = 0.6
			add_child(sl)
	for x in [23.6, 27.2]:
		for z in [-9.5, -5.5, -1.5]:
			var ol := OmniLight3D.new()
			ol.position = Vector3(x, FLOOR + 3.1, z)
			ol.light_color = Color(0.95, 1.0, 0.97)
			ol.light_energy = 0.9
			ol.omni_range = 5.5
			ol.light_volumetric_fog_energy = 0.0
			add_child(ol)
	var spill := SpotLight3D.new()                     # свет витрины на крыльцо и снег
	spill.position = Vector3(21.6, 2.7, -5.0)
	spill.rotation_degrees = Vector3(-25, 90, 0)
	spill.light_color = Color(1.0, 0.94, 0.84)
	spill.light_energy = 1.4
	spill.spot_range = 12.0
	spill.spot_angle = 70.0
	add_child(spill)
	var sign := OmniLight3D.new()
	sign.position = Vector3(5.6, 6.2, 6.0)
	sign.light_color = Color(1.0, 0.4, 0.28)
	sign.light_energy = 2.2
	sign.omni_range = 10.0
	add_child(sign)
	# реквизит: [модель, позиция, поворот вокруг вертикали]
	var items := [["CashRegister_01", Vector3(25.75, FLOOR + 1.05, -8.42), 180.0], ["Television_01", Vector3(26.4, FLOOR + 2.145, -11.5), 0.0],
		["long_life_food", Vector3(27.3, FLOOR + 1.05, -8.35), 160.0], ["WetFloorSign_01", Vector3(21.9, FLOOR, -0.6), 30.0],
		["cardboard_box_01", Vector3(29.8, FLOOR, -11.0), 10.0], ["cardboard_box_01", Vector3(29.3, FLOOR, -10.9), -15.0],
		["metal_jerrycan", Vector3(20.6, 0.0, 2.75), 70.0], ["metal_jerrycan", Vector3(20.9, 0.0, 3.05), 100.0],
		["old_tyre", Vector3(19.9, 0.0, 3.4), 0.0], ["old_tyre", Vector3(19.9, 0.2, 3.4), 30.0], ["old_tyre", Vector3(19.95, 0.4, 3.35), 60.0]]
	for it in items:
		var path := "res://assets/ph/%s/%s.gltf" % [it[0], it[0]]
		if not ResourceLoader.exists(path):
			continue
		var n := (load(path) as PackedScene).instantiate() as Node3D
		add_child(n)
		n.position = it[1]
		n.rotation_degrees.y = it[2]
		if not props.has(it[0]):
			props[it[0]] = n
	if props.has("Television_01"):                    # новости с фото Ноя
		var tv := NewsTV.new()
		add_child(tv)
		tv.build(props["Television_01"], lang)
		props["news"] = tv
	car.speed = 0.0
	car.ride = false
	car.position = Vector3(14.3, 0.0, -5.0)   # ближе к правому островку: слева у колонки место для Ноя


## Трещины на стеклянной двери магазина (после провала): паутина от точки удара — текстура кодом.
func crack_door() -> void:
	var g := station.find_child("shop_door_glass", true, false) as MeshInstance3D
	if g == null:
		return
	var n := 512
	var img := Image.create(n, n, false, Image.FORMAT_RGBA8)
	img.fill(Color(0.1, 0.12, 0.13, 0.2))
	var rng := RandomNumberGenerator.new()
	rng.seed = 13
	var c := Vector2(n * 0.42, n * 0.48)
	for i in 22:                                    # лучи от точки удара
		var a := TAU * i / 22.0 + rng.randf() * 0.2
		var p := c
		var len := rng.randf_range(n * 0.25, n * 0.7)
		var k := 0.0
		while k < len:
			a += rng.randf_range(-0.12, 0.12)
			p += Vector2(cos(a), sin(a)) * 2.0
			k += 2.0
			if p.x < 1 or p.y < 1 or p.x > n - 2 or p.y > n - 2:
				break
			img.set_pixelv(p, Color(0.92, 0.95, 1.0, 0.9))
			img.set_pixelv(p + Vector2(1, 0), Color(0.8, 0.85, 0.9, 0.6))
	for r in [18.0, 42.0, 80.0]:                    # кольца трещин
		for j in 360:
			var a := deg_to_rad(j)
			var q: Vector2 = c + Vector2(cos(a), sin(a)) * (float(r) + rng.randf_range(-3, 3))
			if q.x > 0 and q.y > 0 and q.x < n and q.y < n and rng.randf() < 0.75:
				img.set_pixelv(q, Color(0.9, 0.93, 1.0, 0.85))
	img.generate_mipmaps()
	var m := StandardMaterial3D.new()
	m.albedo_texture = ImageTexture.create_from_image(img)
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.roughness = 0.1
	m.cull_mode = BaseMaterial3D.CULL_DISABLED
	g.material_override = m


## Деньги на прилавке.
func show_money() -> void:
	for i in 3:
		var b := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(0.155, 0.002, 0.068)
		b.mesh = bm
		var m := StandardMaterial3D.new()
		m.albedo_color = Color(0.55, 0.62, 0.5)
		m.roughness = 0.8
		b.material_override = m
		b.position = Vector3(26.25 + i * 0.03, FLOOR + 1.052 + i * 0.002, -8.15 + i * 0.02)
		b.rotation_degrees.y = 10.0 + i * 9.0
		add_child(b)


## Телефон на прилавке с вырванным проводом (провод висит).
func rip_phone() -> void:
	var ph := MeshInstance3D.new()
	var pm := BoxMesh.new()
	pm.size = Vector3(0.2, 0.08, 0.16)
	ph.mesh = pm
	ph.material_override = _dark()
	ph.position = Vector3(27.35, FLOOR + 1.09, -8.6)
	ph.rotation_degrees.y = 25.0
	add_child(ph)
	var cord := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = 0.004
	cm.bottom_radius = 0.004
	cm.height = 0.9
	cord.mesh = cm
	cord.material_override = _dark()
	cord.position = Vector3(27.5, FLOOR + 0.62, -8.15)
	cord.rotation_degrees = Vector3(12, 0, 8)
	add_child(cord)


func _dark() -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.03, 0.03, 0.03)
	m.roughness = 0.5
	return m


## Тёмное пятно под кассиром — растекается.
func blood_pool(p: Vector3) -> void:
	var d := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = 0.5
	cm.bottom_radius = 0.5
	cm.height = 0.002
	cm.radial_segments = 40
	d.mesh = cm
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.09, 0.006, 0.006)
	m.roughness = 0.12
	m.metallic_specular = 0.7
	d.material_override = m
	d.position = p
	d.scale = Vector3(0.2, 1.0, 0.16)
	add_child(d)
	create_tween().tween_property(d, "scale", Vector3(1.0, 1.0, 0.75), 6.0).set_ease(Tween.EASE_OUT)


## Камера на план (в координатах машины; планы заправки — в координатах мира).
func put_camera(cam: Camera3D, shot: String) -> void:
	if STATION_SHOTS.has(shot):
		var w: Array = STATION_SHOTS[shot]
		cam.fov = w[2]
		cam.look_at_from_position(w[0], w[1])
		return
	var s: Array = SHOTS.get(shot, SHOTS.pov)
	cam.fov = s[2]
	cam.look_at_from_position(car.to_global(s[0]), car.to_global(s[1]))
