class_name Road
extends Node3D
## Ночная лесная трасса «бесконечной лентой»: машина стоит на месте, мир едет навстречу. Асфальт со снегом и обочины —
## текстуры Poly Haven (CC0) со сдвигом по пройденному пути, разметка — в шейдере; ели — кодом (ярусы конусов с хвоей
## Poly Haven), три полосы леса с разным шагом, чтобы глаз не ловил повтор; столбы ЛЭП изредка; снег летит в фары.
## Фары, луна и туман — здесь же: на ПК объёмный туман даёт лучи фар в снегу.

const ROAD_W := 7.0          # ширина асфальта
const LEN := 260.0           # сколько дороги видно впереди
var speed := 25.0            # м/с (90 км/ч)
var dist := 0.0              # пройденный путь
var road_mat: ShaderMaterial
var bank_mat: ShaderMaterial
var forests: Array = []      # [MultiMeshInstance3D, шаг повтора]
var poles: Node3D
var snow: CPUParticles3D
var pc := true

const ROAD_SHADER := """
shader_type spatial;
render_mode cull_disabled;
uniform sampler2D tex_diff : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D tex_nor : hint_normal, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D tex_arm : filter_linear_mipmap_anisotropic, repeat_enable;
uniform float dist = 0.0;
uniform float tile = 4.0;
uniform float marks = 1.0;      // разметка (у обочин — 0)
uniform float half_w = 3.5;
varying vec3 vW;
void vertex() { vW = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz; }
void fragment() {
	vec2 uv = vec2(vW.x, vW.z - dist) / tile;
	vec3 c = texture(tex_diff, uv).rgb;
	vec3 arm = texture(tex_arm, uv).rgb;
	// разметка: прерывистая по оси, сплошные по краям — потёртые, присыпанные снегом
	float z = vW.z - dist;
	float dash = step(fract(z / 12.0), 0.5) * (1.0 - smoothstep(0.06, 0.09, abs(vW.x)));
	float edge = 1.0 - smoothstep(0.06, 0.09, abs(abs(vW.x) - (half_w - 0.35)));
	float wear = smoothstep(0.25, 0.75, texture(tex_diff, uv * 0.37 + 0.2).g);
	c = mix(c, vec3(0.82, 0.8, 0.72), (dash + edge) * marks * (0.55 + 0.45 * wear));
	// колеи: в середине полосы снега меньше, асфальт темнее и мокрый
	float lane = abs(abs(vW.x) - 1.75);
	float rut = (1.0 - smoothstep(0.25, 0.75, lane)) * marks;
	c *= mix(1.0, 0.72, rut);
	ALBEDO = c;
	ROUGHNESS = mix(arm.g, 0.35, rut * 0.8);
	AO = arm.r;
	NORMAL_MAP = texture(tex_nor, uv).rgb;
}
"""


func setup(is_pc: bool) -> void:
	pc = is_pc
	var sh := Shader.new()
	sh.code = ROAD_SHADER
	road_mat = _mat(sh, "asphalt_snow", 4.0, 1.0)
	bank_mat = _mat(sh, "snow_02", 3.0, 0.0)
	_strip(road_mat, -ROAD_W / 2.0, ROAD_W / 2.0, 0.0)
	_strip(bank_mat, ROAD_W / 2.0, ROAD_W / 2.0 + 9.0, 0.05)        # обочина справа, чуть выше дороги
	_strip(bank_mat, -ROAD_W / 2.0 - 9.0, -ROAD_W / 2.0, 0.05)
	_strip(bank_mat, ROAD_W / 2.0 + 9.0, 70.0, 0.15)                 # снег в лесу
	_strip(bank_mat, -70.0, -ROAD_W / 2.0 - 9.0, 0.15)
	forests.append([_forest(7, 8.5, 23.0, 1.0, 37.0), 37.0])
	forests.append([_forest(11, 12.0, 40.0, 1.25, 53.0), 53.0])
	forests.append([_forest(23, 25.0, 65.0, 1.5, 71.0), 71.0])
	_poles()
	_snow()


func _mat(sh: Shader, name: String, tile: float, marks: float) -> ShaderMaterial:
	var m := ShaderMaterial.new()
	m.shader = sh
	var base := "res://assets/ph/%s/%s" % [name, name]
	m.set_shader_parameter("tex_diff", load(base + "_diff_1k.jpg"))
	m.set_shader_parameter("tex_nor", load(base + "_nor_gl_1k.jpg"))
	m.set_shader_parameter("tex_arm", load(base + "_arm_1k.jpg"))
	m.set_shader_parameter("tile", tile)
	m.set_shader_parameter("marks", marks)
	m.set_shader_parameter("half_w", ROAD_W / 2.0)
	return m


func _strip(m: Material, x0: float, x1: float, y: float) -> void:
	var pm := PlaneMesh.new()
	pm.size = Vector2(x1 - x0, LEN + 40.0)
	pm.subdivide_depth = 8
	var mi := MeshInstance3D.new()
	mi.mesh = pm
	mi.material_override = m
	mi.position = Vector3((x0 + x1) / 2.0, y, -LEN / 2.0 + 20.0)
	add_child(mi)


## Ель: ствол и 6–8 ярусов «юбок» из конусов, хвоя — текстура веток Poly Haven (fir_tree_01), снег на верхушках ярусов.
func _fir_mesh(snowy: float) -> ArrayMesh:
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	var tiers := 7
	for t in tiers:
		var f := float(t) / tiers
		var y0 := 1.2 + f * 8.6
		var r := lerpf(2.6, 0.35, f)
		var h := lerpf(2.4, 1.3, f)
		var seg := 14
		for i in seg:
			var a0 := TAU * i / seg + f * 1.7
			var a1 := TAU * (i + 1) / seg + f * 1.7
			var droop := 0.35 + 0.25 * sin(a0 * 3.0 + t)   # ветки свисают неровно
			var p_top := Vector3(0, y0 + h, 0)
			var p0 := Vector3(cos(a0) * r, y0 - droop * 0.4, sin(a0) * r)
			var p1 := Vector3(cos(a1) * r, y0 - droop * 0.4, sin(a1) * r)
			var n := (p1 - p_top).cross(p0 - p_top).normalized()
			st.set_normal(n)
			st.set_color(Color(snowy, 0, 0))
			st.set_uv(Vector2(0.5, 0.0))
			st.add_vertex(p_top)
			st.set_uv(Vector2(float(i) / seg * 2.0, 1.0))
			st.add_vertex(p0)
			st.set_uv(Vector2(float(i + 1) / seg * 2.0, 1.0))
			st.add_vertex(p1)
	# ствол
	var seg2 := 8
	for i in seg2:
		var a0 := TAU * i / seg2
		var a1 := TAU * (i + 1) / seg2
		var b0 := Vector3(cos(a0) * 0.22, 0, sin(a0) * 0.22)
		var b1 := Vector3(cos(a1) * 0.22, 0, sin(a1) * 0.22)
		var t0 := Vector3(cos(a0) * 0.08, 6.0, sin(a0) * 0.08)
		var t1 := Vector3(cos(a1) * 0.08, 6.0, sin(a1) * 0.08)
		for v in [[b0, Vector2(0, 0)], [t0, Vector2(0, 3)], [b1, Vector2(1, 0)], [b1, Vector2(1, 0)], [t0, Vector2(0, 3)], [t1, Vector2(1, 3)]]:
			st.set_normal(Vector3((v[0] as Vector3).x, 0, (v[0] as Vector3).z).normalized())
			st.set_color(Color(0, 1, 0))
			st.set_uv(v[1])
			st.add_vertex(v[0])
	st.generate_tangents()
	return st.commit()


const FIR_SHADER := """
shader_type spatial;
render_mode cull_disabled;
uniform sampler2D twig : source_color, filter_linear_mipmap, repeat_enable;
uniform sampler2D twig_n : hint_normal, filter_linear_mipmap, repeat_enable;
uniform sampler2D bark : source_color, filter_linear_mipmap, repeat_enable;
varying float vSnow;
varying float vTrunk;
void vertex() { vSnow = COLOR.r; vTrunk = COLOR.g; }
void fragment() {
	vec3 nW = (INV_VIEW_MATRIX * vec4(NORMAL, 0.0)).xyz;
	vec3 tw = texture(twig, UV * vec2(3.0, 2.0)).rgb * vec3(0.35, 0.45, 0.4);
	float s = vSnow * smoothstep(0.2, 0.75, nW.y) * smoothstep(0.35, 0.65, texture(twig, UV * 5.0).g + 0.3);
	vec3 c = mix(tw, vec3(0.62, 0.66, 0.72), s * 0.85);
	c = mix(c, texture(bark, UV).rgb * 0.6, vTrunk);
	ALBEDO = c;
	ROUGHNESS = mix(0.85, 0.6, s);
	NORMAL_MAP = texture(twig_n, UV * vec2(3.0, 2.0)).rgb;
	NORMAL_MAP_DEPTH = 0.6;
}
"""
static var _fir_m: ShaderMaterial


func _forest(seed_i: int, x_near: float, x_far: float, scale: float, period: float) -> MultiMeshInstance3D:
	if _fir_m == null:
		var sh := Shader.new()
		sh.code = FIR_SHADER
		_fir_m = ShaderMaterial.new()
		_fir_m.shader = sh
		var tx := "res://assets/ph/fir_tree_01/textures/fir_tree_01_"
		_fir_m.set_shader_parameter("twig", load(tx + "twig_diff_1k.jpg"))
		_fir_m.set_shader_parameter("twig_n", load(tx + "twig_nor_gl_1k.jpg"))
		_fir_m.set_shader_parameter("bark", load(tx + "bark_diff_1k.jpg"))
	var rng := RandomNumberGenerator.new()
	rng.seed = seed_i
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	mm.mesh = _fir_mesh(1.0)
	var items: Array[Transform3D] = []
	var span := LEN + period * 2.0
	var n_per := int(period / (2.2 * scale))   # деревьев на шаг повтора с каждой стороны
	var reps := int(ceil(span / period))
	var base: Array = []
	for i in n_per * 2:
		var side := -1.0 if i % 2 == 0 else 1.0
		base.append([side * rng.randf_range(x_near, x_far), rng.randf() * period, rng.randf_range(0.7, 1.35) * scale, rng.randf() * TAU])
	for r in reps:
		for b in base:
			var s: float = b[2]
			var xf := Transform3D(Basis(Vector3.UP, b[3]).scaled(Vector3(s, s * rng.randf_range(0.9, 1.15), s)), Vector3(b[0], 0.1, -(b[1] + r * period) + period))
			items.append(xf)
	mm.instance_count = items.size()
	for i in items.size():
		mm.set_instance_transform(i, items[i])
	var mi := MultiMeshInstance3D.new()
	mi.multimesh = mm
	mi.material_override = _fir_m
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON if pc else GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(mi)
	return mi


func _poles() -> void:
	poles = Node3D.new()
	add_child(poles)
	var wood := StandardMaterial3D.new()
	wood.albedo_color = Color(0.23, 0.19, 0.15)
	wood.roughness = 0.9
	for i in 6:
		var c := CylinderMesh.new()
		c.top_radius = 0.11
		c.bottom_radius = 0.14
		c.height = 9.0
		var p := MeshInstance3D.new()
		p.mesh = c
		p.material_override = wood
		p.position = Vector3(ROAD_W / 2.0 + 3.2, 4.5, -i * 50.0)
		var bar := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(1.6, 0.12, 0.12)
		bar.mesh = bm
		bar.material_override = wood
		bar.position = Vector3(0, 3.9, 0)
		p.add_child(bar)
		poles.add_child(p)


func _snow() -> void:
	snow = CPUParticles3D.new()
	snow.amount = 2600 if pc else 1200
	snow.lifetime = 2.2
	snow.preprocess = 2.2
	snow.emission_shape = CPUParticles3D.EMISSION_SHAPE_BOX
	snow.emission_box_extents = Vector3(9.0, 4.0, 2.0)
	snow.position = Vector3(0, 3.2, -38.0)
	snow.direction = Vector3(0, -0.15, 1)
	snow.spread = 6.0
	snow.gravity = Vector3(0, -1.2, 0)
	snow.initial_velocity_min = 16.0
	snow.initial_velocity_max = 21.0
	snow.scale_amount_min = 0.6
	snow.scale_amount_max = 1.4
	var qm := QuadMesh.new()
	qm.size = Vector2(0.028, 0.028)
	var sm := StandardMaterial3D.new()
	sm.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
	sm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	sm.albedo_color = Color(0.92, 0.95, 1.0, 0.85)
	sm.roughness = 1.0
	qm.material = sm
	snow.mesh = qm
	add_child(snow)


func _process(delta: float) -> void:
	dist += speed * delta
	road_mat.set_shader_parameter("dist", dist)
	bank_mat.set_shader_parameter("dist", dist)
	for f in forests:
		(f[0] as Node3D).position.z = fmod(dist, f[1])
	poles.position.z = fmod(dist, 50.0)
	snow.initial_velocity_min = 4.0 + speed * 0.55
	snow.initial_velocity_max = 6.0 + speed * 0.75
