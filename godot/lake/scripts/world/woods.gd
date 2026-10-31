class_name Woods
extends Node3D
## Зимний лес у дороги (глава 4, погоня): ели густо, снег по колено (ровный слой снега выше ступней — герои по колено
## в снегу), охотничья избушка из досок со щелями: сквозь них в темноте пляшет луч фонаря (внутри — свой туман, лучи видно).
## Местные координаты: избушка в (0, 0, 0), дверь — к +Z; тропа от дороги — с +X.

const DEEP := 0.34                 # снег по колено
const FLOOR := DEEP + 0.1          # пол избушки (на венце из брёвен)
const HW := 1.7                    # полуширина избушки (x)
const HD := 1.9                    # полуглубина (z)
const WALL := 2.0
var deep: MeshInstance3D
var hut: Node3D
var plank_m: StandardMaterial3D


func build(road, pc: bool) -> void:
	var sm: Material = Lake.snow_mat(road)
	var ground := MeshInstance3D.new()
	var gb := BoxMesh.new()
	gb.size = Vector3(180.0, 1.0, 180.0)
	ground.mesh = gb
	ground.material_override = sm
	ground.position = Vector3(0.0, -0.5, 0.0)
	add_child(ground)
	deep = MeshInstance3D.new()   # снег по колено: ровный слой выше ступней (в избушке — пол выше него)
	var pm := PlaneMesh.new()
	pm.size = Vector2(180.0, 180.0)
	deep.mesh = pm
	deep.material_override = sm
	deep.position = Vector3(0.0, DEEP, 0.0)
	deep.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(deep)
	var rng := RandomNumberGenerator.new()
	rng.seed = 51
	var pts: Array = []
	for i in 640:
		var x := rng.randf_range(-85.0, 85.0)
		var z := rng.randf_range(-85.0, 85.0)
		if Vector2(x, z).length() < 6.5:   # поляна у избушки
			continue
		if absf(z - 1.0) < 2.4 and x > 2.0:   # тропа от дороги
			continue
		if absf(x - 6.0) < 2.2 and z > 2.0 and z < 30.0:   # тропа дальше (куда бегут)
			continue
		pts.append([x, z, rng.randf_range(0.8, 1.5)])
	Lake.firs(road, self, pts, 61, pc)
	_hut(pc)


func _hut(pc: bool) -> void:
	hut = Node3D.new()
	add_child(hut)
	plank_m = StandardMaterial3D.new()
	var tx := "res://assets/ph/weathered_plank_siding/weathered_plank_siding_"
	plank_m.albedo_texture = load(tx + "diff_1k.jpg")
	plank_m.albedo_color = Color(0.62, 0.55, 0.48)
	plank_m.normal_enabled = true
	plank_m.normal_texture = load(tx + "nor_gl_1k.jpg")
	plank_m.roughness = 0.92
	plank_m.uv1_scale = Vector3(0.6, 1.6, 1.0)
	var log_m := plank_m.duplicate() as StandardMaterial3D
	log_m.albedo_color = Color(0.42, 0.34, 0.27)
	_box(Vector3(HW * 2 + 0.3, 0.3, HD * 2 + 0.3), Vector3(0, DEEP - 0.05, 0), log_m)   # венец
	_box(Vector3(HW * 2, 0.06, HD * 2), Vector3(0, FLOOR - 0.03, 0), log_m)              # пол
	var step := 0.19
	for side in [-1.0, 1.0]:   # стены по x: доски вдоль z, щели между ними
		var n := int(HD * 2 / step)
		for k in n:
			var z := -HD + step * 0.5 + k * step
			_box(Vector3(0.05, WALL, step - 0.042), Vector3(side * HW, FLOOR + WALL / 2, z), plank_m)
	var nz := int(HW * 2 / step)
	for k in nz:   # задняя стена (−z) и передняя с дверным проёмом (+z)
		var x := -HW + step * 0.5 + k * step
		_box(Vector3(step - 0.028, WALL, 0.05), Vector3(x, FLOOR + WALL / 2, -HD), plank_m)
		if absf(x - 0.3) > 0.45:
			_box(Vector3(step - 0.028, WALL, 0.05), Vector3(x, FLOOR + WALL / 2, HD), plank_m)
		else:
			_box(Vector3(step - 0.028, 0.32, 0.05), Vector3(x, FLOOR + WALL - 0.16, HD), plank_m)
	var door := _box(Vector3(0.9, 1.66, 0.05), Vector3(0.3, FLOOR + 0.83, HD), plank_m)   # дверь приоткрыта внутрь
	door.position += Vector3(-0.32, 0, -0.34)
	door.rotation_degrees.y = 55.0
	var roof_m := StandardMaterial3D.new()
	roof_m.albedo_color = Color(0.82, 0.85, 0.9)
	roof_m.roughness = 0.9
	for sx in [-1.0, 1.0]:   # крыша под снегом, скатами к ±x
		var r := _box(Vector3(HW * 1.3, 0.16, HD * 2 + 0.6), Vector3(sx * HW * 0.55, FLOOR + WALL + 0.45, 0), roof_m)
		r.rotation_degrees.z = -sx * 32.0
	for sz in [-1.0, 1.0]:   # фронтоны
		for k in 6:
			var w := HW * 2 * (1.0 - float(k) / 6.0)
			_box(Vector3(w, 0.16, 0.05), Vector3(0, FLOOR + WALL + 0.08 + k * 0.16, sz * HD), plank_m)
	_box(Vector3(0.4, 0.06, 1.6), Vector3(-HW + 0.3, FLOOR + 0.45, -0.3), log_m)   # лавка
	for z in [-0.9, 0.3]:
		_box(Vector3(0.06, 0.44, 0.3), Vector3(-HW + 0.3, FLOOR + 0.22, z), log_m)
	var stove := _box(Vector3(0.45, 0.6, 0.45), Vector3(HW - 0.5, FLOOR + 0.3, -HD + 0.5), StandardMaterial3D.new())   # холодная буржуйка
	(stove.material_override as StandardMaterial3D).albedo_color = Color(0.08, 0.08, 0.085)
	(stove.material_override as StandardMaterial3D).metallic = 0.5
	var moon := OmniLight3D.new()   # в избушке — холодный свет снега сквозь щели (лица читаются)
	moon.light_color = Color(0.6, 0.7, 1.0)
	moon.light_energy = 0.35
	moon.omni_range = 3.4
	moon.position = Vector3(0.3, FLOOR + 1.6, 0.5)
	hut.add_child(moon)
	if pc:   # внутри — свой туман: луч фонаря сквозь щели виден полосами
		var fv := FogVolume.new()
		fv.size = Vector3(HW * 2 - 0.1, WALL, HD * 2 - 0.1)
		fv.position = Vector3(0, FLOOR + WALL / 2, 0)
		var fm := FogMaterial.new()
		fm.density = 0.45
		fm.albedo = Color(0.8, 0.84, 0.92)
		fv.material = fm
		hut.add_child(fv)


func _box(size: Vector3, pos: Vector3, m: Material) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	mi.mesh = bm
	mi.material_override = m
	mi.position = pos
	hut.add_child(mi)
	return mi
