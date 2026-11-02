extends Node3D
## Младенец — свёрток-«конверт» на руках: шерстяное одеяльце (к ножкам уже), капюшон, кружево вокруг лица, лицо
## (глаза закрыты — спит, носик, ротик, румянец), лента с бантом. Раньше — белая капсула с розовым шаром на конце:
## младенца в ней не узнавали (владелец, 2 ноя).
## Держат обеими руками у груди: свёрток привязан к кости груди держащего, кисти — двухзвенной IK героя в точки под
## свёртком, ладонями вверх (каждый кадр, за дыханием и шагом). Если руками героя распорядилась сцена (свой reach),
## свёрток рук не трогает, пока сцена их не отпустит.
## Оси свёртка: X — вдоль (голова на +X, у левого локтя держащего), Y — вверх, Z — от груди держащего.

const KNIT := "res://assets/ph/knitted_fleece/knitted_fleece_nor_gl_1k.jpg"   # Poly Haven CC0, уже в проекте (одежда)
## точки запястий в осях свёртка: под серединой (левая) и под ножками (правая); у лежащей — те же точки
## («вниз» свёртка у неё — к животу)
const HAND_L := Vector3(0.02, -0.1, -0.01)
const HAND_R := Vector3(-0.17, -0.086, 0.0)
## насколько свёрток впереди кости груди (м): пальто и пуховики толще свитера
const FWD := {"eva": 0.3, "iris": 0.27, "agnes": 0.25}
const FWD_LYING := 0.25

var holder: Actor
## сетка свёртка одна на всю игру: строится один раз (и не удаляется вместе со свёртком — иначе свёрток, снятый в том же
## кадре, в котором его надели, оставлял движку обращения к уже удалённой сетке)
static var _mesh: ArrayMesh = null


## Свёрток на руках героя: перед грудью (у лежащей — на груди). Возвращает узел-привязку к кости груди.
static func attach(a: Actor) -> Node3D:
	var sk := a.skel
	var bi := sk.find_bone("spine_03")
	var ba := BoneAttachment3D.new()
	ba.bone_name = "spine_03"
	sk.add_child(ba)
	var b: Node3D = load("res://scripts/world/baby.gd").new()
	b.name = "Baby"
	b.holder = a
	b.add_child(_bundle())
	ba.add_child(b)
	# место — по осям тела (а не корня героя): у лежащей «вперёд» — вверх от груди, «вверх» — к голове
	var bb := body_basis(a)
	var chest := sk.global_transform * sk.get_bone_global_pose(bi).origin
	var lying := a.clip in Actor.LIE
	var fwd: float = FWD_LYING if lying else float(FWD.get(String(a.name), 0.26))
	var tilt := Basis(bb.z, deg_to_rad(22.0))   # голова — у локтя, выше ножек
	var want := Transform3D(tilt * bb, chest + bb.z * fwd - bb.y * 0.13 + bb.x * 0.03)
	var bone_g := sk.global_transform * sk.get_bone_global_pose(bi)
	b.transform = bone_g.affine_inverse() * want
	return ba


## Оси тела героя по костям: X — влево, Y — вдоль позвоночника к голове, Z — куда смотрит грудь.
static func body_basis(a: Actor) -> Basis:
	var sk := a.skel
	var gp := func(n: String) -> Vector3:
		return sk.global_transform * sk.get_bone_global_pose(sk.find_bone(n)).origin
	var up: Vector3 = (gp.call("neck_01") - gp.call("pelvis")).normalized()
	var left: Vector3 = gp.call("upperarm_l") - gp.call("upperarm_r")
	left = (left - up * left.dot(up)).normalized()
	return Basis(left, up, left.cross(up))


func _process(_d: float) -> void:
	if holder == null or not is_instance_valid(holder):
		return
	var last: Dictionary = holder.get_meta("baby_reach", {})
	if not holder.reach.is_empty() and holder.reach != last:
		holder.set_meta("baby_reach", {})   # руки заняты сценой — не спорим
		return
	var t := global_transform
	var bx := t.basis.x.normalized()
	var by := t.basis.y.normalized()
	var want := {"_l": t * HAND_L, "_r": t * HAND_R}
	holder.reach = want   # сбрасывает локоть и ладонь — задаём заново
	holder.reach_palm = {"_l": by, "_r": by}
	holder.reach_pole = {"_l": bx - by * 0.6, "_r": -bx - by * 0.6}   # локти — вниз и в стороны
	holder.set_meta("baby_reach", want)


func _exit_tree() -> void:
	if holder != null and is_instance_valid(holder):
		var last: Dictionary = holder.get_meta("baby_reach", {})
		if not last.is_empty() and holder.reach == last:
			holder.reach = {}
		holder.set_meta("baby_reach", {})


# ------------------------------------------------------------------------------------------------------- сетка свёртка

static func _mat(c: Color, rough := 0.95, knit := false) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	m.roughness = rough
	m.metallic_specular = 0.25
	if knit:
		m.rim_enabled = true   # ворс шерсти: свет «стелется» по контуру
		m.rim = 0.3
		m.rim_tint = 0.8
		if ResourceLoader.exists(KNIT):
			m.normal_enabled = true
			m.normal_texture = load(KNIT)
			m.normal_scale = 1.2
			m.uv1_scale = Vector3(4.0, 2.0, 1.0)

	return m


## Тело свёртка — тело вращения вдоль X: от закруглённых ножек (x −0,25) к плечам под капюшоном, сечение чуть
## сплюснуто сверху.
static func _body() -> ArrayMesh:
	var prof := [[-0.25, 0.0], [-0.247, 0.022], [-0.239, 0.036], [-0.225, 0.046], [-0.2, 0.054], [-0.15, 0.06],
		[-0.08, 0.067], [-0.01, 0.073], [0.05, 0.077], [0.1, 0.078], [0.14, 0.07]]
	var seg := 28
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	for i in prof.size():
		var x: float = prof[i][0]
		var r: float = prof[i][1]
		for j in seg + 1:
			var a := TAU * j / seg
			st.set_uv(Vector2(float(j) / seg, float(i) / (prof.size() - 1)))
			st.add_vertex(Vector3(x, cos(a) * r * 0.92, sin(a) * r))
	for i in prof.size() - 1:
		for j in seg:
			var k0 := i * (seg + 1) + j
			var k1 := k0 + seg + 1
			st.add_index(k0)   # обход — наружу (лицевая сторона снаружи)
			st.add_index(k1)
			st.add_index(k0 + 1)
			st.add_index(k0 + 1)
			st.add_index(k1)
			st.add_index(k1 + 1)
	st.generate_normals()
	st.generate_tangents()
	return st.commit()


## Узел свёртка с общей сеткой.
static func _bundle() -> MeshInstance3D:
	if _mesh == null:
		_mesh = _build_mesh()
	var mi := MeshInstance3D.new()
	mi.name = "Bundle"
	mi.mesh = _mesh
	return mi


## Одна сетка, по поверхности на материал (8 вызовов отрисовки).
static func _build_mesh() -> ArrayMesh:
	var parts := {}   # материал → [[сетка, преобразование], …]
	var add := func(key: String, mesh: Mesh, xf: Transform3D) -> void:
		if not parts.has(key):
			parts[key] = []
		(parts[key] as Array).append([mesh, xf])
	var along := Basis(Vector3(0, 0, 1), -PI / 2)   # ось Y примитива → X свёртка
	add.call("wool", _body(), Transform3D())
	# капюшон — круглый конец у головы
	var hood := SphereMesh.new()
	hood.radius = 0.085
	hood.height = 0.17
	hood.radial_segments = 24
	hood.rings = 12
	var hc := Vector3(0.12, 0.008, 0.0)
	add.call("wool", hood, Transform3D(Basis(), hc))
	# лицо повёрнуто к тому, кто стоит перед держащим (и чуть вверх), макушка — вверх и к локтю: так лицо читается с
	# обычной камеры спереди; лицом строго вверх, макушкой вдоль свёртка, оно в кадре лежало боком
	var f := Vector3(0.22, 0.42, 0.88).normalized()
	var t0 := Vector3(0.55, 0.83, 0.0)
	var tdir := (t0 - f * t0.dot(f)).normalized()   # к макушке
	var sdir := tdir.cross(f).normalized()          # поперёк лица
	var head_c := hc + f * 0.05
	var hr := 0.056
	var head := SphereMesh.new()
	head.radius = hr
	head.height = hr * 2.0
	head.radial_segments = 24
	head.rings = 12
	add.call("skin", head, Transform3D(Basis(sdir, tdir, f), head_c))
	var on_face := func(d: Vector3, sink := 0.0) -> Vector3:
		return head_c + d.normalized() * (hr - sink)
	# кружево вокруг лица — по линии, где лицо выходит из капюшона
	var rim := TorusMesh.new()
	rim.inner_radius = 0.043
	rim.outer_radius = 0.068
	rim.rings = 32
	rim.ring_segments = 8
	add.call("lace", rim, Transform3D(Basis(sdir, f, sdir.cross(f)), hc + f * 0.064))   # ось тора — из лица
	# закрытые глаза — тёмные дуги поперёк лица, внешние уголки чуть ниже
	var eye := CapsuleMesh.new()
	eye.radius = 0.0034
	eye.height = 0.022
	eye.radial_segments = 6
	eye.rings = 2
	for s in [-1.0, 1.0]:
		var d: Vector3 = (f * 0.93 + tdir * 0.17 + sdir * (0.33 * s)).normalized()
		var ax := (sdir - d * sdir.dot(d)).normalized().rotated(d, deg_to_rad(-10.0) * s)
		add.call("dark", eye, Transform3D(Basis(ax.cross(d), ax, d), on_face.call(d, 0.0012)))
	# румянец — чуть розовее кожи, плоские пятна на щеках
	var cheek := SphereMesh.new()
	cheek.radius = 0.0105
	cheek.height = 0.028
	cheek.radial_segments = 10
	cheek.rings = 5
	for s in [-1.0, 1.0]:
		var d: Vector3 = (f * 0.88 - tdir * 0.12 + sdir * (0.44 * s)).normalized()
		var cb := Basis((sdir - d * sdir.dot(d)).normalized(), d.cross((sdir - d * sdir.dot(d)).normalized()), d)
		add.call("cheek", cheek, Transform3D(cb * Basis.from_scale(Vector3(1.0, 1.0, 0.25)), on_face.call(d, 0.0028)))
	var nose := SphereMesh.new()
	nose.radius = 0.0058
	nose.height = 0.012
	nose.radial_segments = 8
	nose.rings = 4
	add.call("nose", nose, Transform3D(Basis(), on_face.call(f - tdir * 0.05, 0.001)))
	var mouth := CapsuleMesh.new()
	mouth.radius = 0.0036
	mouth.height = 0.013
	mouth.radial_segments = 6
	mouth.rings = 2
	var md: Vector3 = (f * 0.95 - tdir * 0.3).normalized()
	var max_ := (sdir - md * sdir.dot(md)).normalized()
	add.call("lips", mouth, Transform3D(Basis(max_.cross(md), max_, md), on_face.call(md, 0.0012)))
	# лента с бантом поперёк свёртка
	var band := TorusMesh.new()
	band.inner_radius = 0.064
	band.outer_radius = 0.076
	band.rings = 24
	band.ring_segments = 6
	add.call("ribbon", band, Transform3D(along * Basis.from_scale(Vector3(0.93, 1.0, 1.0)), Vector3(-0.08, 0.0, 0.0)))
	var loop := TorusMesh.new()
	loop.inner_radius = 0.009
	loop.outer_radius = 0.02
	loop.rings = 12
	loop.ring_segments = 6
	var knot_p := Vector3(-0.08, 0.063, 0.031)
	for s in [-1.0, 1.0]:
		var lb := Basis(Vector3(0, 0, 1), deg_to_rad(25.0 * s)) * Basis(Vector3(1, 0, 0), PI / 2)   # петли — плоскостью к зрителю
		add.call("ribbon", loop, Transform3D(lb, knot_p + Vector3(0.022 * s, 0.006, 0.0)))
	var knot := SphereMesh.new()
	knot.radius = 0.011
	knot.height = 0.02
	knot.radial_segments = 8
	knot.rings = 4
	add.call("ribbon", knot, Transform3D(Basis(), knot_p + Vector3(0, 0.004, 0)))
	var mats := {
		"wool": _mat(Color(0.86, 0.84, 0.86), 1.0, true),
		"lace": _mat(Color(0.96, 0.95, 0.93), 0.85),
		"skin": _mat(Color(0.96, 0.81, 0.74), 0.6),
		"cheek": _mat(Color(0.96, 0.73, 0.69), 0.6),
		"nose": _mat(Color(0.93, 0.76, 0.69), 0.6),
		"dark": _mat(Color(0.24, 0.13, 0.11), 0.7),
		"lips": _mat(Color(0.76, 0.42, 0.43), 0.55),
		"ribbon": _mat(Color(0.8, 0.45, 0.52), 0.5),
	}
	var am := ArrayMesh.new()
	for key in parts:
		var st := SurfaceTool.new()
		st.begin(Mesh.PRIMITIVE_TRIANGLES)
		for pr in parts[key]:
			st.append_from(pr[0], 0, pr[1])
		st.commit(am)
		am.surface_set_material(am.get_surface_count() - 1, mats[key])
	return am
