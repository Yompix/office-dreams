class_name HairRig
extends RefCounted
## Кости волос и вторичная динамика.
## У моделей MakeHuman нет костей волос: сетка волос привязана к голове и шее, а у длинных причёсок — ещё к ключицам,
## плечам и даже тазу (пряди тянулись за руками). Здесь при создании героя:
##   1) веса волос пересчитываются: у кожи головы — только голова, ниже — своя цепочка костей (за руками больше не тянутся);
##   2) цепочки (2–4 кости) идут вниз от затылка и висков до кончиков — по форме самой причёски;
##   3) SpringBoneSimulator3D (Godot 4.4+) даёт инерцию: пряди отстают на поворотах, качаются при шаге, успокаиваются,
##      реагируют на ветер (external_force); столкновения — голова (сфера), шея, плечи и спина — пряди не входят в тело.
## Короткие стрижки остаются жёсткими (держатся за голову): у них нечему качаться.

## причёска: sectors — где идут цепочки (градусы вокруг головы: 0 — затылок, +90 — левый висок героя, −90 — правый),
## joints — костей в цепочке, stiff/drag/grav — жёсткость, затухание, тяжесть; free — свободная длина от затылка
const STYLE := {
	"iris": {"sectors": [0.0, 80.0, -80.0], "joints": 2, "stiff": 2.4, "drag": 0.55, "grav": 0.25},
	"anna": {"sectors": [0.0, 80.0, -80.0], "joints": 2, "stiff": 2.2, "drag": 0.55, "grav": 0.25},
	"nina": {"sectors": [0.0, 80.0, -80.0], "joints": 2, "stiff": 2.2, "drag": 0.55, "grav": 0.25},
	"zoe": {"sectors": [0.0, 70.0, -70.0], "joints": 3, "stiff": 1.7, "drag": 0.5, "grav": 0.35},
	"eva": {"sectors": [0.0, 55.0, -55.0], "joints": 3, "stiff": 1.3, "drag": 0.5, "grav": 0.5},
	"agnes": {"sectors": [0.0], "joints": 4, "stiff": 0.9, "drag": 0.45, "grav": 0.9},   # коса по спине
}


## Построить кости и симулятор для героя (если причёска длинная). Вернёт SpringBoneSimulator3D или null.
static func build(a: Actor, hair: MeshInstance3D) -> SpringBoneSimulator3D:
	var st: Dictionary = STYLE.get(a.key.trim_suffix("_double"), {})
	if st.is_empty() or hair.skin == null:
		return null
	var sk := a.skel
	var head := sk.find_bone("head")
	var neck := sk.find_bone("neck_01")
	if head < 0:
		return null
	sk.force_update_all_bone_transforms()
	var g_head := sk.get_bone_global_rest(head)
	var hb := -1                                   # индекс привязки «голова» в Skin волос
	for j in hair.skin.get_bind_count():
		var bb := hair.skin.get_bind_bone(j)
		if bb == head or (bb < 0 and String(hair.skin.get_bind_name(j)) == "head"):
			hb = j
	if hb < 0:
		return null
	var mesh_to_sk := g_head * hair.skin.get_bind_pose(hb)   # сетка → скелет (в покое у всех костей одно и то же)
	var up := g_head.basis.y.normalized()
	var fwd := Vector3(0, 0, 1)                    # герой смотрит к +Z (пространство скелета)
	var left := up.cross(fwd).normalized() * -1.0
	if left.dot(Vector3(1, 0, 0)) < 0.0:
		left = -left
	fwd = left.cross(up).normalized()
	if fwd.z < 0.0:
		fwd = -fwd
	var root_y := g_head.origin.dot(up)            # линия «кожа головы → свободные пряди»: уровень основания черепа
	# вершины всех поверхностей волос (в покое, пространство скелета)
	var surf: Array = []
	for s in hair.mesh.get_surface_count():
		surf.append(hair.mesh.surface_get_arrays(s))
	var sectors: Array = st.sectors
	var nj: int = st.joints
	# по секторам: самая низкая точка и средняя точка у кончиков; радиус причёски на уровне корня
	var tip_y: Array = []
	var tip_p: Array = []
	var root_p: Array = []
	for si in sectors.size():
		tip_y.append(root_y)
		tip_p.append(Vector3.ZERO)
		root_p.append(Vector3.ZERO)
	var cnt_root: Array = []
	cnt_root.resize(sectors.size())
	cnt_root.fill(0)
	for arr in surf:
		for v in (arr[Mesh.ARRAY_VERTEX] as PackedVector3Array):
			var p := mesh_to_sk * v
			var si := _nearest_sector(p - g_head.origin, up, fwd, left, sectors)
			var h := p.dot(up)
			if h < float(tip_y[si]):
				tip_y[si] = h
			if absf(h - root_y) < 0.02:
				root_p[si] = (root_p[si] as Vector3) + p
				cnt_root[si] = int(cnt_root[si]) + 1
	for si in sectors.size():
		if int(cnt_root[si]) > 0:
			root_p[si] = (root_p[si] as Vector3) / float(cnt_root[si])
		else:
			root_p[si] = g_head.origin + _dir(float(sectors[si]), fwd, left) * 0.08
	# кончики: средняя точка нижних 2 см сектора
	var tip_n: Array = []
	tip_n.resize(sectors.size())
	tip_n.fill(0)
	for arr in surf:
		for v in (arr[Mesh.ARRAY_VERTEX] as PackedVector3Array):
			var p := mesh_to_sk * v
			var si := _nearest_sector(p - g_head.origin, up, fwd, left, sectors)
			if p.dot(up) < float(tip_y[si]) + 0.02:
				tip_p[si] = (tip_p[si] as Vector3) + p
				tip_n[si] = int(tip_n[si]) + 1
	# цепочки костей: от корня к кончику, ровными отрезками; +Y кости — вниз по пряди
	var chains: Array = []      # [[индексы костей], длина отрезка]
	var base := sk.get_bone_count()
	for si in sectors.size():
		var free_len: float = root_y - float(tip_y[si])
		if free_len < 0.03 or int(tip_n[si]) == 0:
			chains.append([[], 0.0])
			continue
		var r0: Vector3 = root_p[si]
		var r1: Vector3 = (tip_p[si] as Vector3) / float(tip_n[si])
		var seg := r0.distance_to(r1) / float(nj)
		var ids: Array = []
		var parent := head
		var parent_g := g_head
		for j in nj:
			var p0 := r0.lerp(r1, float(j) / float(nj))
			var p1 := r0.lerp(r1, float(j + 1) / float(nj))
			var y := (p1 - p0).normalized()
			var x := y.cross(fwd).normalized()
			if x.length() < 0.5:
				x = left
			var z := x.cross(y).normalized()
			var g := Transform3D(Basis(x, y, z), p0)
			var nm := "hair_%d_%d" % [si, j]
			var bi := sk.find_bone(nm)
			if bi < 0:
				bi = sk.add_bone(nm)
			sk.set_bone_parent(bi, parent)
			sk.set_bone_rest(bi, parent_g.affine_inverse() * g)
			sk.reset_bone_pose(bi)
			ids.append(bi)
			parent = bi
			parent_g = g
		chains.append([ids, seg, r0, r1])
	if chains.all(func(c): return (c[0] as Array).is_empty()):
		return null
	# привязки новых костей к коже волос
	var skin := hair.skin.duplicate() as Skin
	var bind_of := {}
	for c in chains:
		for bi in (c[0] as Array):
			var g := sk.get_bone_global_rest(int(bi))
			skin.add_bind(int(bi), g.affine_inverse() * mesh_to_sk)
			bind_of[int(bi)] = skin.get_bind_count() - 1
	# веса: у кожи головы и спереди (чёлка у лица) — голова; ниже основания черепа — вдоль цепочки своего сектора
	var nm2 := ArrayMesh.new()
	var src := hair.mesh as ArrayMesh
	if src:
		nm2.blend_shape_mode = src.blend_shape_mode
		for i in src.get_blend_shape_count():
			nm2.add_blend_shape(src.get_blend_shape_name(i))
	for s in surf.size():
		var arr: Array = surf[s]
		var vs: PackedVector3Array = arr[Mesh.ARRAY_VERTEX]
		var n := vs.size()
		var per := (arr[Mesh.ARRAY_BONES] as PackedInt32Array).size() / maxi(n, 1)
		var bones := PackedInt32Array()
		var weights := PackedFloat32Array()
		bones.resize(n * per)
		weights.resize(n * per)
		for vi in n:
			var p := mesh_to_sk * vs[vi]
			var ws := _weights(p, g_head.origin, up, fwd, left, root_y, sectors, chains, hb, bind_of)
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
					bones[vi * per + k] = hb
					weights[vi * per + k] = 0.0
		arr[Mesh.ARRAY_BONES] = bones
		arr[Mesh.ARRAY_WEIGHTS] = weights
		var bsa: Array = src.surface_get_blend_shape_arrays(s) if src else []
		var flags := 0
		if per == 8:
			flags = Mesh.ARRAY_FLAG_USE_8_BONE_WEIGHTS
		nm2.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arr, bsa, {}, flags)
		nm2.surface_set_material(s, hair.mesh.surface_get_material(s))
	var overrides: Array = []
	for s in hair.mesh.get_surface_count():
		overrides.append(hair.get_surface_override_material(s))
	hair.mesh = nm2
	hair.skin = skin
	for s in overrides.size():
		if overrides[s]:
			hair.set_surface_override_material(s, overrides[s])
	# симулятор: по цепочке на сектор
	var sim := SpringBoneSimulator3D.new()
	sim.name = "HairSpring"
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
		sim.set_radius(i, 0.012)
		sim.set_enable_all_child_collisions(i, true)
	# столкновения: голова (сфера по коже головы), шея, плечи, верх спины
	var hs := _head_sphere(a, g_head)
	_sphere(sim, "head", hs[0], hs[1])
	if neck >= 0:
		_capsule(sim, "neck_01", Vector3(0, 0.04, 0.0), 0.055, 0.16)
	for side in ["_l", "_r"]:
		if sk.find_bone("clavicle" + side) >= 0:
			_capsule(sim, "clavicle" + side, Vector3(0, 0.08, 0), 0.06, 0.22)
	if sk.find_bone("spine_03") >= 0:
		_sphere(sim, "spine_03", Vector3(0, 0.12, -0.06), 0.13)
	return sim


static func _dir(deg: float, fwd: Vector3, left: Vector3) -> Vector3:
	var r := deg_to_rad(deg)
	return (-fwd * cos(r) + left * sin(r)).normalized()


static func _angle(d: Vector3, up: Vector3, fwd: Vector3, left: Vector3) -> float:
	var h := d - up * d.dot(up)
	return rad_to_deg(atan2(h.dot(left), -h.dot(fwd)))


static func _nearest_sector(d: Vector3, up: Vector3, fwd: Vector3, left: Vector3, sectors: Array) -> int:
	var ang := _angle(d, up, fwd, left)
	var best := 0
	var bd := 1e9
	for si in sectors.size():
		var dd := absf(wrapf(ang - float(sectors[si]), -180.0, 180.0))
		if dd < bd:
			bd = dd
			best = si
	return best


## Веса вершины: голова у кожи и спереди; ниже — вдоль цепочки(ек) ближайших секторов (плавно между соседними).
static func _weights(p: Vector3, hc: Vector3, up: Vector3, fwd: Vector3, left: Vector3, root_y: float, sectors: Array,
		chains: Array, hb: int, bind_of: Dictionary) -> Dictionary:
	var ws := {}
	var d := p - hc
	var h := p.dot(up)
	var depth := root_y - h                             # насколько ниже основания черепа
	var front := d.dot(fwd)
	var face_guard := clampf(1.0 - (front - 0.0) / 0.05, 0.0, 1.0)   # чёлка и пряди у лица — почти неподвижны
	if depth <= 0.0 or face_guard <= 0.0:
		ws[hb] = 1.0
		return ws
	var ang := _angle(d, up, fwd, left)
	# два ближайших сектора и доли между ними
	var order: Array = range(sectors.size())
	order.sort_custom(func(sa, sb): return absf(wrapf(ang - float(sectors[sa]), -180.0, 180.0)) < absf(wrapf(ang - float(sectors[sb]), -180.0, 180.0)))
	var picks: Array = [[order[0], 1.0]]
	if sectors.size() > 1:
		var da := absf(wrapf(ang - float(sectors[order[0]]), -180.0, 180.0))
		var db := absf(wrapf(ang - float(sectors[order[1]]), -180.0, 180.0))
		var wa := db / maxf(da + db, 1e-3)
		picks = [[order[0], wa], [order[1], 1.0 - wa]]
	var head_w := 0.0
	for pk in picks:
		var si: int = pk[0]
		var sw: float = pk[1]
		var c: Array = chains[si]
		var ids: Array = c[0]
		if ids.is_empty():
			head_w += sw
			continue
		var r0: Vector3 = c[2]
		var r1: Vector3 = c[3]
		var axis := r1 - r0
		var u := clampf((p - r0).dot(axis) / maxf(axis.length_squared(), 1e-6), 0.0, 1.0) * float(ids.size())
		# плавно: 0.25 отрезка у корня — ещё за головой; дальше доля между соседними костями
		var follow := smoothstep(0.0, 0.5, depth / 0.03) * face_guard
		head_w += sw * (1.0 - follow)
		var k := clampi(int(floor(u)), 0, ids.size() - 1)
		var f := u - float(k)
		var wk := 1.0
		if f < 0.3 and k > 0:
			var t := 0.5 + f / 0.6
			wk = t
			var prev := int(bind_of[int(ids[k - 1])])
			ws[prev] = float(ws.get(prev, 0.0)) + sw * follow * (1.0 - t)
		var bk := int(bind_of[int(ids[k])])
		ws[bk] = float(ws.get(bk, 0.0)) + sw * follow * wk
	if head_w > 0.0:
		ws[hb] = float(ws.get(hb, 0.0)) + head_w
	return ws


## Сфера головы по коже: вершины кожи, что держатся за голову, — центр и радиус (85% точек внутри).
static func _head_sphere(a: Actor, g_head: Transform3D) -> Array:
	for mi in a.model.find_children("*", "MeshInstance3D", true, false):
		var m := mi as MeshInstance3D
		if String(m.name).to_lower() != "skin" or m.skin == null:
			continue
		var hb := -1
		for j in m.skin.get_bind_count():
			if m.skin.get_bind_bone(j) == a.skel.find_bone("head") or String(m.skin.get_bind_name(j)) == "head":
				hb = j
		if hb < 0:
			break
		var to_sk := g_head * m.skin.get_bind_pose(hb)
		var arr := m.mesh.surface_get_arrays(0)
		var vs: PackedVector3Array = arr[Mesh.ARRAY_VERTEX]
		var bi: PackedInt32Array = arr[Mesh.ARRAY_BONES]
		var bw: PackedFloat32Array = arr[Mesh.ARRAY_WEIGHTS]
		var per := bi.size() / maxi(vs.size(), 1)
		var pts: Array = []
		var c := Vector3.ZERO
		for vi in vs.size():
			for k in per:
				if bi[vi * per + k] == hb and bw[vi * per + k] > 0.9:
					var p := to_sk * vs[vi]
					pts.append(p)
					c += p
					break
		if pts.size() < 50:
			break
		c /= float(pts.size())
		var ds: Array = pts.map(func(q): return (q as Vector3).distance_to(c))
		ds.sort()
		var r: float = ds[int(ds.size() * 0.85)]
		# в пространство кости головы
		return [g_head.affine_inverse() * c, r * 0.95]
	return [Vector3(0, 0.08, 0.02), 0.1]


static func _sphere(sim: SpringBoneSimulator3D, bone: String, off: Vector3, r: float) -> void:
	var s := SpringBoneCollisionSphere3D.new()
	s.bone_name = bone
	s.position_offset = off
	s.radius = r
	sim.add_child(s)


static func _capsule(sim: SpringBoneSimulator3D, bone: String, off: Vector3, r: float, h: float) -> void:
	var s := SpringBoneCollisionCapsule3D.new()
	s.bone_name = bone
	s.position_offset = off
	s.radius = r
	s.height = h
	sim.add_child(s)
