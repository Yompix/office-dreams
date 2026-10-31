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
var station_root: Node3D   # всё, что ставит стоянка (постройки, свет, реквизит) — уезжаем, и оно исчезает
var road_items: Node3D     # что стоит на дороге (олень, кровь, указатель, чужая машина): едет назад вместе с ней
const LOT := Rect2(4.2, -28.0, 33.6, 46.0)   # площадка заправки (x, z) справа от дороги
const FLOOR := 0.38                          # пол магазина и крыльцо
var cafe_root: Node3D      # кафе «У трёх сосен» (глава 2): постройка, свет, фуры, машины — уезжаем, и оно исчезает
var cafe: Node3D
var trucks: Array = []
var eva_car: BlackCar
const CAFE_LOT := Rect2(4.0, -44.0, 36.0, 68.0)
const CF := 0.45                             # пол кафе и крыльцо
const CAFE_BOOTHS := [-13.4, -11.1, -2.6, -0.3]
## планы кафе (глава 2): [позиция камеры, куда смотрит, угол обзора]
const CAFE_SHOTS := {
	"cafe_ext": [Vector3(5.0, 2.3, 5.0), Vector3(23.0, 2.4, -7.5), 46.0],
	"cafe_wide": [Vector3(22.9, CF + 1.9, -6.2), Vector3(31.0, CF + 1.0, -9.5), 64.0],
	"cafe_eva": [Vector3(23.7, CF + 1.3, -12.3), Vector3(23.0, CF + 1.12, -14.15), 40.0],
	"cafe_truckers": [Vector3(28.6, CF + 1.6, -6.0), Vector3(30.1, CF + 1.15, -10.4), 46.0],
	"eva_car": [Vector3(23.0, CF + 1.55, -12.2), Vector3(18.9, 0.5, -16.4), 42.0],
	"cafe_iris": [Vector3(23.3, CF + 1.22, -3.1), Vector3(22.75, CF + 1.12, -1.82), 38.0],
	"noah_cafe": [Vector3(23.1, CF + 1.25, -2.15), Vector3(22.9, CF + 1.15, -3.38), 38.0],
	"cafe_danny": [Vector3(22.8, CF + 1.15, -2.85), Vector3(23.42, CF + 0.98, -1.82), 38.0],
	"cafe_iris_eva": [Vector3(24.0, CF + 1.45, -12.2), Vector3(23.0, CF + 1.0, -14.0), 46.0],
	"cafe_zoe": [Vector3(28.2, CF + 1.45, -4.4), Vector3(30.1, CF + 1.25, -5.4), 40.0],
	"cafe_zoe_table": [Vector3(22.85, CF + 1.22, -2.1), Vector3(23.42, CF + 1.12, -3.38), 40.0],
	"wc_sink": [Vector3(32.75, CF + 1.75, -13.15), Vector3(33.45, CF + 0.95, -13.8), 46.0],
	"wc_mirror": [Vector3(32.35, CF + 1.66, -13.55), Vector3(33.74, CF + 1.6, -13.8), 34.0],
	"wc_pills": [Vector3(32.85, CF + 1.35, -13.25), Vector3(33.15, CF + 1.0, -13.7), 30.0],
	"cafe_window_porch": [Vector3(23.1, CF + 1.5, -5.2), Vector3(21.0, CF + 1.25, -9.0), 46.0],
	"porch": [Vector3(19.5, CF + 1.5, -7.4), Vector3(21.0, CF + 1.35, -8.9), 42.0],
	"porch_zoe": [Vector3(21.3, CF + 1.5, -8.2), Vector3(20.85, CF + 1.45, -9.25), 36.0],
	"iris_window": [Vector3(20.6, CF + 1.35, -2.4), Vector3(22.75, CF + 1.1, -1.82), 32.0],
	"cafe_window": [Vector3(23.2, CF + 1.45, -3.6), Vector3(8.0, 0.9, -36.0), 40.0],
	"cafe_back": [Vector3(37.0, 1.9, 2.5), Vector3(34.0, 1.3, -1.0), 50.0],
	"sedan_walk": [Vector3(18.6, 2.1, -14.0), Vector3(8.6, 0.8, -34.0), 40.0],
	"bracelet_snow": [Vector3(18.2, 0.55, -11.0), Vector3(18.5, 0.02, -11.7), 30.0],
	"eva_porch": [Vector3(18.4, 1.6, -5.2), Vector3(21.0, CF + 1.3, -8.0), 40.0],
	"noah_out": [Vector3(20.5, 1.65, -9.2), Vector3(18.4, 1.62, -11.0), 38.0],
}
const ROAD_EDGE := 3.5                       # край асфальта (полдороги)

## планы на заправке — в координатах мира: [позиция, куда смотрит, угол обзора]
const STATION_SHOTS := {
	"st_wide": [Vector3(1.0, 3.4, 14.0), Vector3(16.0, 1.8, -6.0), 52.0],
	"st_sign": [Vector3(-1.5, 5.6, 10.0), Vector3(5.6, 6.4, 6.0), 34.0],
	"st_arrive": [Vector3(9.0, 1.5, -16.0), Vector3(14.0, 1.1, -4.0), 46.0],
	"st_danny": [Vector3(18.7, 1.1, -2.4), Vector3(20.3, 1.05, -3.3), 34.0],
	"st_two": [Vector3(17.4, 1.25, -2.3), Vector3(20.1, 0.85, -3.7), 40.0],
	"st_pump": [Vector3(15.9, 1.65, -0.8), Vector3(13.2, 1.25, -3.3), 40.0],
	"st_door": [Vector3(17.2, 1.75, 1.2), Vector3(21.0, 1.5, -2.0), 46.0],
	"st_counter": [Vector3(27.7, 2.25, -6.5), Vector3(26.25, 1.75, -9.6), 44.0],
	"st_tv": [Vector3(26.25, 2.45, -10.0), Vector3(26.4, 2.65, -11.45), 30.0],
	"st_roman": [Vector3(26.9, 1.85, -7.4), Vector3(26.4, 1.85, -9.3), 34.0],
	"st_hand": [Vector3(28.05, 1.38, -9.75), Vector3(26.45, 1.02, -8.95), 40.0],
	"st_noah_face": [Vector3(26.7, 1.85, -9.3), Vector3(26.4, 1.85, -7.5), 34.0],
	"st_knuckles": [Vector3(13.5, 1.6, -4.85), Vector3(12.95, 0.88, -4.3), 36.0],
	"st_window": [Vector3(20.15, 2.45, -5.1), Vector3(23.1, 0.55, -3.65), 42.0],
	"st_crack": [Vector3(19.55, 1.55, -0.85), Vector3(21.1, 1.45, -2.05), 40.0],
	"st_danny_stare": [Vector3(19.1, 1.05, -4.2), Vector3(20.25, 0.98, -3.3), 32.0],
	"st_kids": [Vector3(16.3, 1.65, -0.8), Vector3(19.65, 1.0, -3.4), 40.0],
	"st_noah_out": [Vector3(19.2, 1.45, -3.25), Vector3(17.0, 1.62, -2.5), 36.0],
	"st_blackcar": [Vector3(12.6, 1.6, -12.5), Vector3(9.2, 1.1, -24.0), 34.0],
}

## планы на трассе снаружи машины (сцена 3, олень) — в координатах мира: машина стоит в начале координат, нос к −Z
const ROAD_SHOTS := {
	"deer_face": [Vector3(-2.5, 1.25, -2.65), Vector3(-0.95, 1.18, -4.3), 34.0],
	"deer_stop": [Vector3(3.2, 1.2, -8.0), Vector3(-0.6, 0.9, -3.5), 42.0],
	"deer_walk": [Vector3(1.5, 1.5, -1.6), Vector3(-6.0, 0.9, -5.5), 48.0],
	"deer_lie": [Vector3(0.3, 1.6, -5.8), Vector3(-1.9, 0.15, -9.6), 40.0],
	"lamp": [Vector3(-0.3, 0.92, -5.2), Vector3(-0.2, 0.66, -2.85), 40.0],
	"deer_walk_in": [Vector3(-2.6, 1.6, 1.8), Vector3(-1.4, 0.6, -8.5), 46.0],
	"deer_noah": [Vector3(1.4, 1.3, -6.6), Vector3(-1.2, 0.6, -9.6), 44.0],
	"iris_glass": [Vector3(0.95, 1.3, -3.9), Vector3(0.38, 1.12, 0.12), 16.0],
	"deer_side": [Vector3(-1.5, 1.4, -8.3), Vector3(-3.9, 0.35, -11.5), 40.0],
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
	"pills": [Vector3(0.22, 0.98, -0.02), Vector3(0.39, 0.64, -0.3), 26.0],
	"back": [Vector3(0.05, 1.25, -0.25), Vector3(0.32, 0.92, 1.4), 52.0],
	"deer_eye": [Vector3(-0.36, 1.17, 0.12), Vector3(-0.3, 1.05, -4.3), 40.0],
}


func _process(delta: float) -> void:
	if road == null or road_items == null:
		return
	for c in road_items.get_children():   # всё на дороге едет назад вместе с ней; далеко позади — убрать
		(c as Node3D).position.z += road.speed * delta
		if (c as Node3D).position.z > 90.0:
			c.queue_free()


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
	road_items = Node3D.new()
	add_child(road_items)


## Заправка «24»: постройки (tools/lake/station.py), площадка, свет навеса, магазина и вывески, реквизит Poly Haven;
## машина — у колонки, стоит.
func park_at_station(lang := "ru") -> void:
	road.park(LOT)
	station_root = Node3D.new()
	add_child(station_root)
	station = (load("res://assets/models/station.glb") as PackedScene).instantiate() as Node3D
	station_root.add_child(station)
	for n in station.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		var nm := String(mi.name)
		if nm.begins_with("window") or nm.contains("glass") or nm.begins_with("fridge_glow"):
			mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		if nm.begins_with("shop_band"):              # световой короб над витриной: тусклее (не белое пятно) и с надписью
			var bm := mi.get_active_material(0)
			if bm is StandardMaterial3D:
				bm = (bm as StandardMaterial3D).duplicate()
				(bm as StandardMaterial3D).emission_energy_multiplier = 0.28
				(bm as StandardMaterial3D).albedo_color = Color(0.7, 0.68, 0.62)
				mi.set_surface_override_material(0, bm)
			var lb := Label3D.new()
			lb.text = "ПРОДУКТЫ  ·  КОФЕ  ·  24 ЧАСА" if lang == "ru" else "FOOD  ·  COFFEE  ·  OPEN 24 HRS"
			lb.font_size = 120
			lb.pixel_size = 0.0034
			lb.modulate = Color(0.62, 0.06, 0.04)
			lb.outline_size = 0
			lb.shaded = false
			lb.double_sided = false
			lb.position = Vector3(20.935, FLOOR + 3.13, -5.5)
			lb.rotation_degrees = Vector3(0, -90, 0)
			station_root.add_child(lb)
	var lot := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = LOT.size
	pm.subdivide_width = 16
	pm.subdivide_depth = 16
	lot.mesh = pm
	lot.material_override = road.lot_material()
	lot.position = Vector3(LOT.get_center().x, 0.004, LOT.get_center().y)
	station_root.add_child(lot)
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
			station_root.add_child(sl)
	for x in [23.6, 27.2]:
		for z in [-9.5, -5.5, -1.5]:
			var ol := OmniLight3D.new()
			ol.position = Vector3(x, FLOOR + 3.1, z)
			ol.light_color = Color(0.95, 1.0, 0.97)
			ol.light_energy = 0.9
			ol.omni_range = 5.5
			ol.light_volumetric_fog_energy = 0.0
			station_root.add_child(ol)
	var spill := SpotLight3D.new()                     # свет витрины на крыльцо и снег
	spill.position = Vector3(21.6, 2.7, -5.0)
	spill.rotation_degrees = Vector3(-25, 90, 0)
	spill.light_color = Color(1.0, 0.94, 0.84)
	spill.light_energy = 1.4
	spill.spot_range = 12.0
	spill.spot_angle = 70.0
	station_root.add_child(spill)
	var sign := OmniLight3D.new()
	sign.position = Vector3(5.6, 6.2, 6.0)
	sign.light_color = Color(1.0, 0.4, 0.28)
	sign.light_energy = 2.2
	sign.omni_range = 10.0
	station_root.add_child(sign)
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
		station_root.add_child(n)
		n.position = it[1]
		n.rotation_degrees.y = it[2]
		if not props.has(it[0]):
			props[it[0]] = n
	if props.has("Television_01"):                    # новости с фото Ноя
		var tv := NewsTV.new()
		station_root.add_child(tv)
		tv.build(props["Television_01"], lang)
		props["news"] = tv
	car.speed = 0.0
	car.ride = false
	car.position = Vector3(14.3, 0.0, -5.0)   # ближе к правому островку: слева у колонки место для Ноя


## Кафе «У трёх сосен» (глава 2): дом у трассы, свет ламп и окон, вывеска на языке игры, три сосны, фуры у входа,
## машина Евы в кювете у крыльца. Машина героев — у крыльца.
func park_at_cafe(lang := "ru") -> void:
	road.park(CAFE_LOT)
	cafe_root = Node3D.new()
	add_child(cafe_root)
	cafe = (load("res://assets/models/cafe.glb") as PackedScene).instantiate() as Node3D
	cafe_root.add_child(cafe)
	for n in cafe.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		var nm := String(mi.name)
		if nm.begins_with("window") or nm.contains("glass") or nm.begins_with("pend") or nm.begins_with("ceiling_lamp"):
			mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	var lb := Label3D.new()                           # вывеска над крыльцом
	lb.text = "У ТРЁХ СОСЕН" if lang == "ru" else "THREE PINES"
	lb.font_size = 150
	lb.pixel_size = 0.0055
	lb.modulate = Color(0.98, 0.86, 0.6)
	lb.outline_size = 0
	lb.shaded = false
	lb.position = Vector3(21.86, CF + 3.45, -7.0)
	lb.rotation_degrees = Vector3(0, -90, 0)
	cafe_root.add_child(lb)
	var warm := Color(1.0, 0.76, 0.46)
	for zc in CAFE_BOOTHS:                            # лампы над столами
		var ol := OmniLight3D.new()
		ol.position = Vector3(23.05, CF + 2.0, zc)
		ol.light_color = warm
		ol.light_energy = 1.3
		ol.omni_range = 4.2
		ol.shadow_enabled = pc and zc == -2.6
		ol.light_volumetric_fog_energy = 0.0
		cafe_root.add_child(ol)
	for z in [-11.0, -7.5, -4.0]:                     # над стойкой
		var ol := OmniLight3D.new()
		ol.position = Vector3(30.8, CF + 2.95, z)
		ol.light_color = Color(1.0, 0.85, 0.62)
		ol.light_energy = 1.0
		ol.omni_range = 5.0
		ol.light_volumetric_fog_energy = 0.0
		cafe_root.add_child(ol)
	for at in [[Vector3(27.0, CF + 2.95, -7.0), Color(1.0, 0.85, 0.62), 0.8, 6.5], [Vector3(32.0, CF + 2.9, -13.8), Color(0.92, 0.96, 1.0), 0.9, 3.4],
			[Vector3(21.6, CF + 2.2, -8.1), warm, 1.4, 6.0], [Vector3(21.3, CF + 4.2, -7.0), Color(1.0, 0.82, 0.55), 1.2, 4.0]]:
		var ol := OmniLight3D.new()
		ol.position = at[0]
		ol.light_color = at[1]
		ol.light_energy = at[2]
		ol.omni_range = at[3]
		ol.light_volumetric_fog_energy = 0.4
		cafe_root.add_child(ol)
	for z in [-11.5, -2.0]:                           # тёплые окна на снегу
		var sp := SpotLight3D.new()
		sp.position = Vector3(23.4, 2.4, z)
		sp.rotation_degrees = Vector3(-22, 90, 0)
		sp.light_color = warm
		sp.light_energy = 1.6
		sp.spot_range = 13.0
		sp.spot_angle = 60.0
		sp.light_volumetric_fog_energy = 0.8
		cafe_root.add_child(sp)
	var items := [["Television_01", Vector3(33.35, CF + 2.3, 0.4), -135.0], ["CashRegister_01", Vector3(31.0, CF + 1.05, -9.8), 90.0]]
	for it in items:
		var path := "res://assets/ph/%s/%s.gltf" % [it[0], it[0]]
		if not ResourceLoader.exists(path):
			continue
		var n := (load(path) as PackedScene).instantiate() as Node3D
		cafe_root.add_child(n)
		n.position = it[1]
		n.rotation_degrees.y = it[2]
		props[it[0]] = n
	if props.has("Television_01"):
		var tv := NewsTV.new()
		cafe_root.add_child(tv)
		tv.build(props["Television_01"], lang)
		props["news"] = tv
	for t in [[Vector3(14.5, 0.0, 9.0), 1.7, 3], [Vector3(10.8, 0.0, 13.5), 1.9, 5], [Vector3(17.2, 0.0, 15.0), 1.6, 8]]:   # три сосны
		var tr := MeshInstance3D.new()
		tr.mesh = road._fir_mesh2(int(t[2]))
		tr.position = t[0]
		tr.scale = Vector3.ONE * float(t[1])
		tr.rotation.y = float(t[2]) * 1.3
		cafe_root.add_child(tr)
	trucks.clear()
	for tk in [[Vector3(11.0, 0.0, -24.0), 8.0, Color(0.5, 0.06, 0.04)], [Vector3(15.0, 0.0, -30.0), 4.0, Color(0.78, 0.78, 0.76)],
			[Vector3(28.0, 0.0, 12.0), 95.0, Color(0.1, 0.2, 0.45)]]:
		var tru := Truck.new().setup(tk[2])
		cafe_root.add_child(tru)
		tru.position = tk[0]
		tru.rotation_degrees.y = tk[1]
		trucks.append(tru)
	eva_car = BlackCar.new()                          # машина Евы — в кювете у входа, нос в снегу
	eva_car.paint_color = Color(0.2, 0.3, 0.26)
	eva_car.tinted = false
	eva_car.has_driver = false
	cafe_root.add_child(eva_car)
	eva_car.position = Vector3(19.0, -0.35, -16.6)
	eva_car.rotation_degrees = Vector3(9.0, 160.0, -11.0)
	car.speed = 0.0
	car.ride = false
	car.position = Vector3(17.6, 0.0, -11.8)


func leave_cafe() -> void:
	if is_instance_valid(cafe_root):
		cafe_root.queue_free()
	cafe_root = null
	cafe = null
	trucks.clear()
	props.clear()
	road.unpark()
	car.position = Vector3.ZERO
	car.rotation = Vector3.ZERO
	car.ride = true
	car.speed = road.speed


## Трещины на стеклянной двери магазина (после провала): паутина от точки удара — текстура кодом.
func crack_door() -> void:
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
			for o in [Vector2(0, 0), Vector2(1, 0), Vector2(0, 1), Vector2(-1, 0), Vector2(0, -1)]:
				var q: Vector2 = (p + o).clamp(Vector2.ZERO, Vector2(n - 1, n - 1))
				img.set_pixelv(q, Color(0.92, 0.95, 1.0, 0.9 if o == Vector2.ZERO else 0.55))
	for r in [18.0, 42.0, 80.0]:                    # кольца трещин
		for j in 360:
			var a := deg_to_rad(j)
			var q: Vector2 = c + Vector2(cos(a), sin(a)) * (float(r) + rng.randf_range(-3, 3))
			if q.x > 1 and q.y > 1 and q.x < n - 2 and q.y < n - 2 and rng.randf() < 0.75:
				img.set_pixelv(q, Color(0.9, 0.93, 1.0, 0.85))
				img.set_pixelv(q + Vector2(1, 0), Color(0.9, 0.93, 1.0, 0.5))
	img.generate_mipmaps()
	var m := StandardMaterial3D.new()
	m.albedo_texture = ImageTexture.create_from_image(img)
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.roughness = 0.1
	m.cull_mode = BaseMaterial3D.CULL_DISABLED
	m.emission_enabled = true                          # трещины ловят свет магазина
	m.emission_texture = m.albedo_texture
	m.emission = Color(0.35, 0.37, 0.4)
	m.emission_energy_multiplier = 0.6
	var q := MeshInstance3D.new()                      # своя плоскость поверх стекла двери (у стекла нет развёртки)
	var qm := QuadMesh.new()
	qm.size = Vector2(0.98, 1.92)
	q.mesh = qm
	q.material_override = m
	q.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	q.position = Vector3(21.095, FLOOR + 1.12, -2.0)
	q.rotation_degrees = Vector3(0, -90, 0)
	station_root.add_child(q)


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
		station_root.add_child(b)


## Телефон на прилавке с вырванным проводом (провод висит).
func rip_phone() -> void:
	var ph := MeshInstance3D.new()
	var pm := BoxMesh.new()
	pm.size = Vector3(0.2, 0.08, 0.16)
	ph.mesh = pm
	ph.material_override = _dark()
	ph.position = Vector3(27.35, FLOOR + 1.09, -8.6)
	ph.rotation_degrees.y = 25.0
	station_root.add_child(ph)
	var cord := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = 0.004
	cm.bottom_radius = 0.004
	cm.height = 0.9
	cord.mesh = cm
	cord.material_override = _dark()
	cord.position = Vector3(27.5, FLOOR + 0.62, -8.15)
	cord.rotation_degrees = Vector3(12, 0, 8)
	station_root.add_child(cord)


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
	m.albedo_color = Color(0.2, 0.012, 0.01)
	m.roughness = 0.12
	m.metallic_specular = 0.7
	d.material_override = m
	d.position = p
	d.scale = Vector3(0.2, 1.0, 0.16)
	holder().add_child(d)
	create_tween().tween_property(d, "scale", Vector3(1.0, 1.0, 0.75), 6.0).set_ease(Tween.EASE_OUT)


## Кровавый след по снегу от a к b (оттащили оленя с дороги).
func blood_trail(a: Vector3, b: Vector3) -> void:
	var d := MeshInstance3D.new()
	var bm := BoxMesh.new()
	var l := Vector2(b.x - a.x, b.z - a.z).length()
	bm.size = Vector3(0.2, 0.002, l)
	d.mesh = bm
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.32, 0.02, 0.015, 0.5)
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.roughness = 0.3
	d.material_override = m
	d.position = (a + b) * 0.5
	d.rotation.y = atan2(b.x - a.x, b.z - a.z)
	holder().add_child(d)


## Куда ставить предметы: на стоянке — к заправке, в пути — на дорогу (едут назад вместе с ней).
func holder() -> Node3D:
	return station_root if is_instance_valid(station_root) else road_items


## Уезжаем с заправки: постройки и свет исчезают, ели и снег — как в пути, машина снова на трассе.
func leave_station() -> void:
	if is_instance_valid(station_root):
		station_root.queue_free()
	station_root = null
	station = null
	props.clear()
	road.unpark()
	car.position = Vector3.ZERO
	car.rotation = Vector3.ZERO
	car.ride = true
	car.speed = road.speed


## Огни заправки позади (в зеркале): вывеска «24», лампы навеса, витрина — светящиеся пятна за деревьями.
func station_glow(z0: float) -> void:
	var g := Node3D.new()
	g.position = Vector3(0, 0, z0)
	road_items.add_child(g)
	var spots := [[Vector3(18.0, 4.6, 6.0), Vector2(2.4, 0.25), Color(0.85, 0.92, 1.0), 6.0], [Vector3(18.0, 4.6, 12.0), Vector2(2.4, 0.25), Color(0.85, 0.92, 1.0), 6.0],
		[Vector3(24.0, 2.0, 10.0), Vector2(5.0, 1.6), Color(1.0, 0.86, 0.62), 2.2], [Vector3(6.5, 6.4, 3.0), Vector2(1.6, 1.2), Color(1.0, 0.12, 0.08), 3.0]]
	for sp in spots:
		var q := MeshInstance3D.new()
		var qm := QuadMesh.new()
		qm.size = sp[1]
		q.mesh = qm
		var m := StandardMaterial3D.new()
		m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		m.albedo_color = sp[2]
		m.billboard_mode = BaseMaterial3D.BILLBOARD_FIXED_Y
		m.emission_enabled = true
		m.emission = sp[2]
		m.emission_energy_multiplier = sp[3]
		q.material_override = m
		q.position = sp[0]
		g.add_child(q)
	var ol := OmniLight3D.new()                       # свет на снег у заправки
	ol.position = Vector3(16.0, 4.0, 8.0)
	ol.light_color = Color(0.9, 0.93, 1.0)
	ol.light_energy = 2.0
	ol.omni_range = 16.0
	ol.light_volumetric_fog_energy = 0.4
	g.add_child(ol)


## Дорожный указатель справа на обочине: зелёный щит с белыми буквами.
func signpost(z: float, text: String) -> Node3D:
	var n := Node3D.new()
	n.position = Vector3(ROAD_EDGE + 1.4, 0.05, z)
	road_items.add_child(n)
	var steel := StandardMaterial3D.new()
	steel.albedo_color = Color(0.45, 0.46, 0.48)
	steel.metallic = 0.7
	steel.roughness = 0.45
	for x in [-0.8, 0.8]:
		var post := MeshInstance3D.new()
		var cm := CylinderMesh.new()
		cm.top_radius = 0.045
		cm.bottom_radius = 0.045
		cm.height = 3.0
		post.mesh = cm
		post.material_override = steel
		post.position = Vector3(x, 1.5, 0)
		n.add_child(post)
	var board := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = Vector3(2.4, 1.15, 0.05)
	board.mesh = bm
	var gm := StandardMaterial3D.new()
	gm.albedo_color = Color(0.03, 0.3, 0.16)
	gm.roughness = 0.35
	gm.metallic_specular = 0.8
	board.material_override = gm
	board.position = Vector3(0, 2.55, 0)
	n.add_child(board)
	var lb := Label3D.new()
	lb.text = text
	lb.font_size = 110
	lb.pixel_size = 0.004
	lb.modulate = Color(0.96, 0.97, 0.95)
	lb.outline_size = 0
	lb.shaded = true
	lb.position = Vector3(0, 2.55, 0.03)
	n.add_child(lb)
	var rim := MeshInstance3D.new()                    # белая кайма щита
	var rm := BoxMesh.new()
	rm.size = Vector3(2.5, 1.25, 0.04)
	rim.mesh = rm
	var wm := StandardMaterial3D.new()
	wm.albedo_color = Color(0.9, 0.92, 0.9)
	rim.material_override = wm
	rim.position = Vector3(0, 2.55, -0.01)
	n.add_child(rim)
	return n


## Камера на план (в координатах машины; планы заправки — в координатах мира).
func put_camera(cam: Camera3D, shot: String) -> void:
	if STATION_SHOTS.has(shot) or ROAD_SHOTS.has(shot) or CAFE_SHOTS.has(shot):
		var w: Array = STATION_SHOTS.get(shot, ROAD_SHOTS.get(shot, CAFE_SHOTS.get(shot)))
		cam.fov = w[2]
		cam.look_at_from_position(w[0], w[1])
		return
	var s: Array = SHOTS.get(shot, SHOTS.pov)
	cam.fov = s[2]
	cam.look_at_from_position(car.to_global(s[0]), car.to_global(s[1]))
