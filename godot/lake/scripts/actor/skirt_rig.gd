class_name SkirtRig
extends RefCounted
## Кости подола и вторичная динамика: пальто, плащ, длинная юбка, платье.
## У моделей нет костей ткани: низ пальто и юбки был привязан к тазу и бёдрам, поэтому при шаге пола шла за коленом
## жёсткой доской, сидя — раскрывалась шатром, присев — юбка обтягивала колени.
## Здесь при создании героя:
##   1) по самой длинной ткани (пальто → юбка → платье) вокруг бёдер ставятся цепочки костей (2–3 сустава) от линии
##      тазобедренных суставов вниз до края подола — по форме самой ткани;
##   2) вершины ниже линии бёдер у ВСЕХ тканей подола героя (пальто, его пуговицы, планка, карманы, платье под пальто)
##      плавно переходят на эти цепочки — слои двигаются вместе и не протыкают друг друга;
##   3) SpringBoneSimulator3D даёт инерцию и вес: пола отстаёт на шаге и поворотах, качается и успокаивается;
##      столкновения — бёдра, голени, таз (капсулы и сфера): ноги толкают ткань, а не проходят сквозь неё.
## Одна система на подол: ClothFit.ride для таких сеток не нужен (юбка висит на пружинах).

## ткань: какая сетка задаёт форму цепочек и как она движется (жёсткость, затухание, тяжесть)
const STYLE := {
	"coat": {"stiff": 1.6, "drag": 0.6, "grav": 0.7},      # шерсть и плотный плащ: тяжёлая, медленная
	"skirt": {"stiff": 0.9, "drag": 0.5, "grav": 0.8},     # длинная широкая юбка: свободнее
	"dress": {"stiff": 2.2, "drag": 0.6, "grav": 0.5},     # платье по фигуре: почти за телом
}
const SECTORS := [0.0, 45.0, 90.0, 135.0, 180.0, -135.0, -90.0, -45.0]   # 0 — сзади, 90 — слева, 180 — спереди
## за какую кость держится цепочка сектора: косые передние и задние — за «поводок» бедра своей стороны, середина
## спереди и сзади и бока — за таз (висят между ног). Поводок (skirt_drv_l/r) — кость у тазобедренного сустава,
## которая каждый кадр повторяет поворот бедра не целиком (Actor.skirt_drive): в шаге наполовину — пола идёт за коленом
## с отставанием пружины, а не задирается доской; сидя и присев — целиком, пола ложится на бёдра и колени
const ROOT_OF := {45.0: "_l", 135.0: "_l", -45.0: "_r", -135.0: "_r"}


static func _kind(nm: String) -> String:
	nm = nm.to_lower()
	if nm.contains("coat"):
		return "coat"
	if nm.contains("skirt"):
		return "skirt"
	if nm.contains("dress"):
		return "dress"
	return ""


## Построить цепочки и симулятор (если у героя есть подол ниже бёдер). Вернёт SpringBoneSimulator3D или null.
static func build(a: Actor) -> SpringBoneSimulator3D:
	var sk := a.skel
	var pel := sk.find_bone("pelvis")
	var thl := sk.find_bone("thigh_l")
	var thr := sk.find_bone("thigh_r")
	if pel < 0 or thl < 0 or thr < 0:
		return null
	var meshes: Array = []
	for mi in a.model.find_children("*", "MeshInstance3D", true, false):
		var m := mi as MeshInstance3D
		if m.skin != null and m.mesh is ArrayMesh and _kind(String(m.name)) != "":
			meshes.append(m)
	# узкое платье без пальто — за ногами, как было: редкие цепочки на облегающей ткани пропускают колено сквозь неё;
	# под пальто платье идёт на тех же цепочках, что и пальто (слои двигаются вместе)
	if not meshes.any(func(m): return _kind(String((m as MeshInstance3D).name)) in ["coat", "skirt"]):
		return null
	sk.force_update_all_bone_transforms()
	var hip := (sk.get_bone_global_rest(thl).origin + sk.get_bone_global_rest(thr).origin) * 0.5
	var top_y := hip.y + 0.02                      # выше — ткань держится за таз и бёдра, как была
	# ведущая сетка: пальто (самое длинное и внешнее), иначе юбка, иначе платье; у пальто — сама ткань, не пуговицы
	var lead: MeshInstance3D = null
	var lead_kind := ""
	for want in ["coat", "skirt", "dress"]:
		for m in meshes:
			var nm := String((m as MeshInstance3D).name).to_lower()
			if _kind(nm) == want and not (nm.contains("button") or nm.contains("placket") or nm.contains("pocket")):
				if lead == null or _count(m) > _count(lead):
					lead = m
		if lead:
			lead_kind = want
			break
	if lead == null:
		return null
	var l2a := _mesh_to_sk(sk, lead)
	if l2a.is_empty():
		return null
	var l2s: Transform3D = l2a[0]
	# по секторам: линия корня (у бёдер) и край подола
	var n := SECTORS.size()
	var root_p: Array = []
	var root_c: Array = []
	var low_y: Array = []
	for si in n:
		root_p.append(Vector3.ZERO)
		root_c.append(0)
		low_y.append(top_y)
	var pts: Array = []
	for s in lead.mesh.get_surface_count():
		for v in (lead.mesh.surface_get_arrays(s)[Mesh.ARRAY_VERTEX] as PackedVector3Array):
			var p: Vector3 = l2s * v
			if p.y > top_y + 0.03:
				continue
			pts.append(p)
			var si := _sector(p - hip)
			if absf(p.y - top_y) < 0.025:
				root_p[si] = (root_p[si] as Vector3) + p
				root_c[si] = int(root_c[si]) + 1
			if p.y < float(low_y[si]):
				low_y[si] = p.y
	var tip_p: Array = []
	var tip_c: Array = []
	for si in n:
		tip_p.append(Vector3.ZERO)
		tip_c.append(0)
	for p in pts:
		var si := _sector((p as Vector3) - hip)
		if (p as Vector3).y < float(low_y[si]) + 0.02:
			tip_p[si] = (tip_p[si] as Vector3) + p
			tip_c[si] = int(tip_c[si]) + 1
	var drop := 0.0
	for si in n:
		drop = maxf(drop, top_y - float(low_y[si]))
	if drop < 0.12:                               # подол у бёдер (куртка, короткая парка) — качаться нечему
		return null
	var nj := 3 if drop > 0.35 else 2
	var drivers: Array = []                       # [[поводок, бедро]]
	var drv := {}
	for side in ["_l", "_r"]:
		var th: int = sk.find_bone("thigh" + side)
		var nm: String = "skirt_drv" + side
		var di: int = sk.find_bone(nm)
		if di < 0:
			di = sk.add_bone(nm)
		sk.set_bone_parent(di, pel)
		sk.set_bone_rest(di, sk.get_bone_global_rest(pel).affine_inverse() * sk.get_bone_global_rest(th))
		sk.reset_bone_pose(di)
		drv[side] = di
		drivers.append([di, th])
	var chains: Array = []                        # [[кости], длина отрезка, корень, кончик]
	for si in n:
		if int(root_c[si]) == 0 or int(tip_c[si]) == 0 or top_y - float(low_y[si]) < 0.1:
			chains.append([[], 0.0, Vector3.ZERO, Vector3.ZERO])
			continue
		var r0: Vector3 = (root_p[si] as Vector3) / float(root_c[si])
		var r1: Vector3 = (tip_p[si] as Vector3) / float(tip_c[si])
		var ids: Array = []
		var parent := pel
		if ROOT_OF.has(float(SECTORS[si])):
			parent = int(drv[ROOT_OF[float(SECTORS[si])]])
		var parent_g := sk.get_bone_global_rest(parent)
		for j in nj:
			var p0 := r0.lerp(r1, float(j) / float(nj))
			var p1 := r0.lerp(r1, float(j + 1) / float(nj))
			var y := (p1 - p0).normalized()
			var out := Vector3(p0.x - hip.x, 0.0, p0.z - hip.z).normalized()
			var x := y.cross(out).normalized()
			if x.length() < 0.5:
				x = Vector3.RIGHT
			var z := x.cross(y).normalized()
			var g := Transform3D(Basis(x, y, z), p0)
			var nm := "skirt_%d_%d" % [si, j]
			var bi := sk.find_bone(nm)
			if bi < 0:
				bi = sk.add_bone(nm)
			sk.set_bone_parent(bi, parent)
			sk.set_bone_rest(bi, parent_g.affine_inverse() * g)
			sk.reset_bone_pose(bi)
			ids.append(bi)
			parent = bi
			parent_g = g
		chains.append([ids, r0.distance_to(r1) / float(nj), r0, r1])
	if chains.all(func(c): return (c[0] as Array).is_empty()):
		return null
	var rigged := 0
	for m in meshes:
		if _rig_mesh(sk, m as MeshInstance3D, chains, hip, top_y):
			(m as MeshInstance3D).set_meta("skirt_rig", true)
			rigged += 1
	if rigged == 0:
		return null
	var st: Dictionary = STYLE[lead_kind]
	var sim := SpringBoneSimulator3D.new()
	sim.name = "SkirtSpring"
	sim.set_meta("drivers", drivers)
	sk.add_child(sim)
	var live: Array = chains.filter(func(c): return not (c[0] as Array).is_empty())
	sim.setting_count = live.size()
	for i in live.size():
		var ids: Array = live[i][0]
		sim.set_root_bone(i, int(ids[0]))
		sim.set_end_bone(i, int(ids[ids.size() - 1]))
		sim.set_extend_end_bone(i, true)
		sim.set_end_bone_direction(i, SpringBoneSimulator3D.BONE_DIRECTION_PLUS_Y)
		sim.set_end_bone_length(i, float(live[i][1]))
		sim.set_stiffness(i, float(st.stiff))
		sim.set_drag(i, float(st.drag))
		sim.set_gravity(i, float(st.grav))
		sim.set_gravity_direction(i, Vector3.DOWN)
		sim.set_radius(i, 0.04)   # сустав «толще» точки: держит на расстоянии и ткань между суставами
		sim.set_enable_all_child_collisions(i, true)
	# столкновения: бёдра и голени — капсулы по костям (толщина ноги + ткань под подолом), таз — сфера
	var leg := hip.y                              # длина ноги ~ высота тазобедренного сустава: у Дэнни тоньше, у Хейла толще
	var k := clampf(leg / 0.9, 0.6, 1.3)
	for side in ["_l", "_r"]:
		_bone_capsule(sim, sk, "thigh" + side, "calf" + side, 0.095 * k)
		_bone_capsule(sim, sk, "calf" + side, "foot" + side, 0.07 * k)
	var ps := SpringBoneCollisionSphere3D.new()
	ps.bone_name = "pelvis"
	ps.position_offset = sk.get_bone_global_rest(pel).affine_inverse() * (hip + Vector3(0, 0.03, 0)) - Vector3.ZERO
	ps.radius = 0.13 * k
	sim.add_child(ps)
	return sim


static func _count(m: MeshInstance3D) -> int:
	var c := 0
	for s in m.mesh.get_surface_count():
		c += (m.mesh.surface_get_arrays(s)[Mesh.ARRAY_VERTEX] as PackedVector3Array).size()
	return c


## сетка → скелет в покое (по любой привязке: у всех костей одно и то же); [] — если привязок нет
static func _mesh_to_sk(sk: Skeleton3D, m: MeshInstance3D) -> Array:
	for j in m.skin.get_bind_count():
		var b := m.skin.get_bind_bone(j)
		if b < 0:
			b = sk.find_bone(m.skin.get_bind_name(j))
		if b >= 0:
			return [sk.get_bone_global_rest(b) * m.skin.get_bind_pose(j)]
	return []


static func _angle(d: Vector3) -> float:   # 0 — сзади (−Z), 90 — слева (+X), 180 — спереди (+Z)
	return rad_to_deg(atan2(d.x, -d.z))


static func _sector(d: Vector3) -> int:
	var ang := _angle(d)
	var best := 0
	var bd := 1e9
	for si in SECTORS.size():
		var dd := absf(wrapf(ang - float(SECTORS[si]), -180.0, 180.0))
		if dd < bd:
			bd = dd
			best = si
	return best


## Перевесить сетку: ниже линии бёдер — плавно на цепочки двух ближайших секторов. false — если менять нечего.
static func _rig_mesh(sk: Skeleton3D, m: MeshInstance3D, chains: Array, hip: Vector3, top_y: float) -> bool:
	var m2a := _mesh_to_sk(sk, m)
	if m2a.is_empty():
		return false
	var m2s: Transform3D = m2a[0]
	var src := m.mesh as ArrayMesh
	var skin := m.skin.duplicate() as Skin
	var bind_of := {}
	for c in chains:
		for bi in (c[0] as Array):
			skin.add_bind(int(bi), sk.get_bone_global_rest(int(bi)).affine_inverse() * m2s)
			bind_of[int(bi)] = skin.get_bind_count() - 1
	var nm2 := ArrayMesh.new()
	nm2.blend_shape_mode = src.blend_shape_mode
	for i in src.get_blend_shape_count():
		nm2.add_blend_shape(src.get_blend_shape_name(i))
	var changed := 0
	for s in src.get_surface_count():
		var arr := src.surface_get_arrays(s)
		var vs: PackedVector3Array = arr[Mesh.ARRAY_VERTEX]
		var bones: PackedInt32Array = arr[Mesh.ARRAY_BONES]
		var weights: PackedFloat32Array = arr[Mesh.ARRAY_WEIGHTS]
		var nv := vs.size()
		var per := bones.size() / maxi(nv, 1)
		if per == 0:
			return false
		for vi in nv:
			var p: Vector3 = m2s * vs[vi]
			var depth := top_y - p.y
			if depth <= 0.0:
				continue
			var follow := smoothstep(0.0, 0.12, depth)
			var cw := _chain_weights(p, hip, chains, bind_of)
			if cw.is_empty():
				continue
			var ws := {}
			for k in per:
				var w := weights[vi * per + k]
				if w > 0.0:
					ws[bones[vi * per + k]] = float(ws.get(bones[vi * per + k], 0.0)) + w * (1.0 - follow)
			for b in cw:
				ws[b] = float(ws.get(b, 0.0)) + float(cw[b]) * follow
			var keys := ws.keys()
			keys.sort_custom(func(ka, kb): return float(ws[ka]) > float(ws[kb]))
			var tot := 0.0
			for k in mini(per, keys.size()):
				tot += float(ws[keys[k]])
			for k in per:
				if k < keys.size():
					bones[vi * per + k] = int(keys[k])
					weights[vi * per + k] = float(ws[keys[k]]) / maxf(tot, 1e-6)
				else:
					weights[vi * per + k] = 0.0
			changed += 1
		arr[Mesh.ARRAY_BONES] = bones
		arr[Mesh.ARRAY_WEIGHTS] = weights
		var flags := Mesh.ARRAY_FLAG_USE_8_BONE_WEIGHTS if per == 8 else 0
		nm2.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arr, src.surface_get_blend_shape_arrays(s), {}, flags)
		nm2.surface_set_material(s, src.surface_get_material(s))
		nm2.surface_set_name(s, src.surface_get_name(s))
	if changed == 0:
		return false
	var overrides: Array = []
	for s in src.get_surface_count():
		overrides.append(m.get_surface_override_material(s))
	m.mesh = nm2
	m.skin = skin
	for s in overrides.size():
		if overrides[s]:
			m.set_surface_override_material(s, overrides[s])
	return true


## веса цепочек для точки: два ближайших сектора (доли по углу), вдоль цепочки — между соседними костями
static func _chain_weights(p: Vector3, hip: Vector3, chains: Array, bind_of: Dictionary) -> Dictionary:
	var ws := {}
	var ang := _angle(p - hip)
	var order: Array = range(SECTORS.size()).filter(func(si): return not (chains[si][0] as Array).is_empty())
	if order.is_empty():
		return ws
	order.sort_custom(func(sa, sb): return absf(wrapf(ang - float(SECTORS[sa]), -180.0, 180.0)) < absf(wrapf(ang - float(SECTORS[sb]), -180.0, 180.0)))
	var picks: Array = [[order[0], 1.0]]
	if order.size() > 1:
		var da := absf(wrapf(ang - float(SECTORS[order[0]]), -180.0, 180.0))
		var db := absf(wrapf(ang - float(SECTORS[order[1]]), -180.0, 180.0))
		if db < 100.0:                             # соседний сектор (через разрез спереди не тянем)
			var wa := db / maxf(da + db, 1e-3)
			picks = [[order[0], wa], [order[1], 1.0 - wa]]
	for pk in picks:
		var c: Array = chains[int(pk[0])]
		var ids: Array = c[0]
		var r0: Vector3 = c[2]
		var r1: Vector3 = c[3]
		var axis := r1 - r0
		var u := clampf((p - r0).dot(axis) / maxf(axis.length_squared(), 1e-6), 0.0, 1.0) * float(ids.size())
		var k := clampi(int(floor(u)), 0, ids.size() - 1)
		var f := u - float(k)
		var wk := 1.0
		if f < 0.3 and k > 0:                      # у сустава — поровну с костью выше, без излома
			var t := 0.5 + f / 0.6
			wk = t
			var prev := int(bind_of[int(ids[k - 1])])
			ws[prev] = float(ws.get(prev, 0.0)) + float(pk[1]) * (1.0 - t)
		var bk := int(bind_of[int(ids[k])])
		ws[bk] = float(ws.get(bk, 0.0)) + float(pk[1]) * wk
	return ws


## капсула вдоль кости (от её сустава до сустава ребёнка) — независимо от того, куда смотрят оси кости
static func _bone_capsule(sim: SpringBoneSimulator3D, sk: Skeleton3D, bone: String, child: String, r: float) -> void:
	var b := sk.find_bone(bone)
	var c := sk.find_bone(child)
	if b < 0 or c < 0:
		return
	var gb := sk.get_bone_global_rest(b)
	var d := gb.basis.inverse() * (sk.get_bone_global_rest(c).origin - gb.origin)
	var cap := SpringBoneCollisionCapsule3D.new()
	cap.bone_name = bone
	cap.position_offset = d * 0.5
	cap.rotation_offset = Quaternion(Vector3.UP, d.normalized())
	cap.radius = r
	cap.height = d.length() + r * 2.0
	sim.add_child(cap)


## Поводки бёдер — каждый кадр после клипа: доля поворота бедра (в шаге ~0,55, сидя и присев — 1).
static func drive(sk: Skeleton3D, sim: SpringBoneSimulator3D) -> void:
	for d in (sim.get_meta("drivers", []) as Array):
		var di: int = d[0]
		var th: int = d[1]
		var rest_q := sk.get_bone_rest(th).basis.get_rotation_quaternion()
		var delta := rest_q.inverse() * sk.get_bone_pose_rotation(th)
		var ang := delta.get_angle()
		var f := lerpf(0.55, 1.0, smoothstep(deg_to_rad(28.0), deg_to_rad(62.0), ang))
		var dq := sk.get_bone_rest(di).basis.get_rotation_quaternion()
		sk.set_bone_pose_rotation(di, dq * Quaternion.IDENTITY.slerp(delta, f))
