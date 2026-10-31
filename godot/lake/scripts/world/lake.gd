class_name Lake
extends Node3D
## Озеро (главы 4–5): модель tools/lake/lake.py (дача, лодочный сарай со щелью, мостки, лодка, поленница) + земля в снегу,
## берег, лёд со снежными пятнами, ели вокруг и на дальнем берегу. Местные координаты: x — к озеру, кромка воды — x = EDGE.
## Узел ставят далеко в стороне от дороги (Ch4.LAKE): своя земля, свой лес.

const EDGE := -8.0
const FAR := 110.0               # дальний берег (x)
const ICE_Y := -0.4
var model: Node3D
var ice: MeshInstance3D
var snow_parts: Array[MeshInstance3D] = []   # снег на крышах и сугробы — летом (в воспоминании) их нет


static var _snow: StandardMaterial3D


## Снег на земле — неподвижный (у дороги снег «едет» вместе с ней, тут — нет): snow_02 Poly Haven.
static func snow_mat(_road = null) -> Material:
	if _snow == null:
		_snow = StandardMaterial3D.new()
		var tx := "res://assets/ph/snow_02/snow_02_"
		_snow.albedo_texture = load(tx + "diff_1k.jpg")
		_snow.albedo_color = Color(0.93, 0.95, 1.0)
		_snow.normal_enabled = true
		_snow.normal_texture = load(tx + "nor_gl_1k.jpg")
		_snow.roughness = 0.82
		_snow.uv1_triplanar = true
		_snow.uv1_scale = Vector3(0.35, 0.35, 0.35)
	return _snow


## Ели (те же, что вдоль дороги) — по списку точек [x, z, масштаб].
static func firs(road, parent: Node3D, pts: Array, seed_i: int, pc: bool) -> MultiMeshInstance3D:
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	mm.mesh = road._fir_mesh2(seed_i)
	mm.instance_count = pts.size()
	var rng := RandomNumberGenerator.new()
	rng.seed = seed_i
	for i in pts.size():
		var p: Array = pts[i]
		var s: float = p[2]
		mm.set_instance_transform(i, Transform3D(Basis(Vector3.UP, rng.randf() * TAU).scaled(Vector3(s, s * rng.randf_range(0.9, 1.15), s)), Vector3(p[0], 0.05, p[1])))
	var mi := MultiMeshInstance3D.new()
	mi.multimesh = mm
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON if pc else GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	parent.add_child(mi)
	return mi


func build(road, pc: bool) -> void:
	model = (load("res://assets/models/lake.glb") as PackedScene).instantiate() as Node3D
	add_child(model)
	for n in model.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		var nm := String(mi.name)
		if nm.contains("snow"):
			snow_parts.append(mi)
		if nm.contains("window") or nm.contains("glass"):
			mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	var sm := snow_mat(road)
	var land := MeshInstance3D.new()   # земля под снегом: от леса до кромки
	var lb := BoxMesh.new()
	lb.size = Vector3(220.0, 1.0, 420.0)
	land.mesh = lb
	land.material_override = sm
	land.position = Vector3(EDGE - 110.0 - 1.0, -0.5, 0.0)
	add_child(land)
	var bank := MeshInstance3D.new()   # берег — скос к льду
	var bb := BoxMesh.new()
	bb.size = Vector3(2.4, 0.4, 420.0)
	bank.mesh = bb
	bank.material_override = sm
	bank.position = Vector3(EDGE - 0.2, -0.32, 0.0)
	bank.rotation_degrees = Vector3(0, 0, -12.0)
	add_child(bank)
	var far := MeshInstance3D.new()   # дальний берег
	far.mesh = lb
	far.material_override = sm
	far.position = Vector3(FAR + 110.0, -0.3, 0.0)
	add_child(far)
	ice = MeshInstance3D.new()   # лёд: голубоватый, местами под снегом, блестит
	var pm := PlaneMesh.new()
	pm.size = Vector2(FAR - EDGE + 4.0, 420.0)
	pm.subdivide_width = 2
	ice.mesh = pm
	var im := StandardMaterial3D.new()
	var nt := NoiseTexture2D.new()
	var fn := FastNoiseLite.new()
	fn.frequency = 0.012
	fn.fractal_octaves = 4
	nt.noise = fn
	nt.width = 1024
	nt.height = 1024
	nt.seamless = true
	var cr := Gradient.new()
	cr.set_color(0, Color(0.56, 0.64, 0.72))
	cr.set_color(1, Color(0.9, 0.92, 0.96))
	cr.add_point(0.45, Color(0.66, 0.73, 0.8))
	cr.add_point(0.62, Color(0.86, 0.89, 0.94))
	nt.color_ramp = cr
	im.albedo_texture = nt
	im.uv1_scale = Vector3(6, 6, 6)
	im.roughness = 0.22
	im.metallic_specular = 0.7
	ice.material_override = im
	ice.position = Vector3((FAR + EDGE) / 2.0, ICE_Y, 0.0)
	add_child(ice)
	# лес: ближний берег (за дачей, вдоль берега, тропа от леса — на юге), дальний берег — стеной
	var rng := RandomNumberGenerator.new()
	rng.seed = 77
	var near: Array = []
	for i in 520:
		var x := rng.randf_range(-120.0, EDGE - 4.0)
		var z := rng.randf_range(-170.0, 170.0)
		if x > -30.0 and z > -10.0 and z < 26.0:   # дача, сарай, мостки — поляна
			continue
		if x > -40.0 and z > 26.0 and z < 50.0 and absf(x + 14.0) < 6.0:   # тропа из леса
			continue
		near.append([x, z, rng.randf_range(0.9, 1.6)])
	firs(road, self, near, 31, pc)
	var farp: Array = []
	for i in 420:
		farp.append([rng.randf_range(FAR + 4.0, FAR + 70.0), rng.randf_range(-200.0, 200.0), rng.randf_range(1.1, 1.9)])
	for i in 160:   # по бокам озера
		var sz := -1.0 if i % 2 == 0 else 1.0
		farp.append([rng.randf_range(EDGE, FAR), sz * rng.randf_range(150.0, 200.0), rng.randf_range(1.0, 1.7)])
	firs(road, self, farp, 37, pc)


## Лето (воспоминание в главе 5): снега нет.
func summer(on: bool) -> void:
	for mi in snow_parts:
		mi.visible = not on
