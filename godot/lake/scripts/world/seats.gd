class_name Seats
extends RefCounted
## Где у сиденья верх: вертикальный луч вниз по сеткам сцены (физики в мире нет — постановка по точкам).
## Скамьи, табуреты, кровати, ступеньки, бревна — любые сетки под тазом, кроме самих героев; в счёт идут только
## «лицом вверх» грани (верх подушки, а не бок спинки) в окне высот [lo, hi]. Владелец, 4 ноя: «персонажи не сидят,
## а через текстуры проваливаются» — к сиденью ставилась кость таза, а низ тела на 8–12 см ниже неё уходил в подушку.

static var _faces := {}   # id сетки → PackedVector3Array треугольников (локально)
static var last := []    # для проверок: высоты и сетки под каждой точкой последнего замера
static var debug := false   # для проверок: печатать каждую посадку


## Самый высокий верх сиденья под точкой p (окно по высоте lo…hi, мир), по нескольким точкам вокруг (под ягодицами).
## Нет ничего — NAN.
static func top_under(root: Node, p: Vector3, lo: float, hi: float, side: Vector3, back: Vector3) -> float:
	return float(_top(root, p, lo, hi, side, back)[0])


## То же, что top_under, но вместе с сеткой, которой принадлежит этот верх: [высота или NAN, MeshInstance3D или null].
## Точки: под тазом и по бокам — главные; точка позади таза (под крестцом) — только если под главными пусто: иначе за
## верх сиденья принималась спинка или планка на стене за спиной (буханка: спинка лавки на 5 см выше подушки).
static func _top(root: Node, p: Vector3, lo: float, hi: float, side: Vector3, back: Vector3) -> Array:
	var ys := [NAN, NAN, NAN, NAN]
	var ws: Array = [null, null, null, null]
	var pts := [p, p + side * 0.08, p - side * 0.08, p + back * 0.06]
	for n in root.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		if mi == null or mi.mesh == null or not mi.is_visible_in_tree() or _in_actor(mi):
			continue
		var box: AABB = mi.global_transform * mi.get_aabb()
		if box.position.y > hi or box.end.y < lo:
			continue
		var near := false
		for q in pts:
			if q.x >= box.position.x - 0.02 and q.x <= box.end.x + 0.02 and q.z >= box.position.z - 0.02 and q.z <= box.end.z + 0.02:
				near = true
				break
		if not near:
			continue
		var f := _faces_of(mi.mesh)
		var xf := mi.global_transform
		var flip := -1.0 if xf.basis.determinant() < 0.0 else 1.0   # отражённая сетка — обход вершин наоборот
		var i := 0
		while i + 2 < f.size():
			var a: Vector3 = xf * f[i]
			var b: Vector3 = xf * f[i + 1]
			var c: Vector3 = xf * f[i + 2]
			i += 3
			var nrm := (b - a).cross(c - a)
			if nrm.length_squared() < 1e-12:
				continue
			nrm = nrm.normalized() * flip
			# только грани лицом вверх (верх сиденья), не спинка, не бока и не низ (у Godot (b−a)×(c−a) смотрит внутрь:
			# у верхней грани y < 0). Раньше годился и низ: в буханке низ спинки лавки (на 5 см выше подушки) считался сиденьем
			if nrm.y > -0.6:
				continue
			for j in pts.size():
				var y := _y_on(a, b, c, pts[j].x, pts[j].z)
				if not is_nan(y) and y >= lo and y <= hi and (is_nan(ys[j]) or y > ys[j]):
					ys[j] = y
					ws[j] = mi
	last = [ys, ws, pts]
	var best := NAN
	var who: MeshInstance3D = null
	for j in 3:
		if not is_nan(ys[j]) and (is_nan(best) or ys[j] > best):
			best = ys[j]
			who = ws[j]
	if is_nan(best):
		return [ys[3], ws[3]]
	return [best, who]


static func _in_actor(n: Node) -> bool:
	var p := n.get_parent()
	while p != null:
		if p is Actor:
			return true
		p = p.get_parent()
	return false


static func _faces_of(m: Mesh) -> PackedVector3Array:
	var k := m.get_instance_id()
	if not _faces.has(k):
		_faces[k] = m.get_faces()
	return _faces[k]


## Высота треугольника abc над точкой (x, z), если точка внутри его проекции на землю; иначе NAN.
static func _y_on(a: Vector3, b: Vector3, c: Vector3, x: float, z: float) -> float:
	var d := (b.z - c.z) * (a.x - c.x) + (c.x - b.x) * (a.z - c.z)
	if absf(d) < 1e-10:
		return NAN
	var l1 := ((b.z - c.z) * (x - c.x) + (c.x - b.x) * (z - c.z)) / d
	var l2 := ((c.z - a.z) * (x - c.x) + (a.x - c.x) * (z - c.z)) / d
	var l3 := 1.0 - l1 - l2
	if l1 < -1e-4 or l2 < -1e-4 or l3 < -1e-4:
		return NAN
	return l1 * a.y + l2 * b.y + l3 * c.y


## Тело — на подушку, а не в неё: верх сиденья под тазом + глубина «таз → низ тела» в этой позе (Actor.seat_depth).
## В машине — своя посадка по креслу (Car.seat), там не трогаем. Окно высот: от ~42 см ниже таза (но не ниже стоп + 12 см —
## иначе «сиденьем» стал бы пол) до 12 см выше (таз поставили ниже подушки).
static func settle(a: Actor) -> void:
	if not is_instance_valid(a) or not a.is_inside_tree() or not (a.clip in Actor.SEAT) or a.clip in ["Crouch_Idle", "Fixing_Kneeling", "Driving"]:
		return
	var p := a.get_parent()
	while p != null:
		if p is Car or p is Actor:   # в машине — своя посадка; у другого на руках или на спине (Агнес у Ноя) — не сидит
			return
		p = p.get_parent()
	var root: Node = a.get_tree().current_scene
	if root == null:
		return
	var sk := a.skel
	sk.force_update_all_bone_transforms()
	var sg := sk.global_transform
	var pel: Vector3 = sg * sk.get_bone_global_pose(sk.find_bone("pelvis")).origin
	var fl_l: Vector3 = sg * sk.get_bone_global_pose(sk.find_bone("foot_l")).origin
	var fl_r: Vector3 = sg * sk.get_bone_global_pose(sk.find_bone("foot_r")).origin
	var feet := minf(fl_l.y, fl_r.y)
	var bs := a.global_transform.basis.orthonormalized()
	# окно: до 42 см ниже таза и до 12 см выше (точку посадки часто ставят по верху подушки, а она выше таза: кафе —
	# подушка дивана на 7 см выше). Спинки и планки на стене за спиной сюда не попадают: в счёт только грани лицом
	# вверх и только под тазом и по бокам (см. _top)
	var hit: Array = _top(root, pel, pel.y - 0.42, pel.y + 0.12, bs.x, -bs.z)
	var top := float(hit[0])
	if debug:
		print("SETTLE %s %s таз %.2f стопы %.2f → %s %.2f (кадр %d)" % [a.key, a.clip, pel.y, feet, String((hit[1] as Node).name) if hit[1] else "-", top, Engine.get_process_frames()])
	if not is_nan(top) and top < feet + 0.12:
		# низко: пол, на котором стоят ноги (не сиденье), или край, с которого ноги свешены (мостки, лавка у обрыва)?
		# Пол — если он не выше щиколоток (стопы на нём, а не под ним) и та же высота есть и под стопами
		if top <= feet + 0.03 and not is_nan(top_under(root, (fl_l + fl_r) * 0.5, top - 0.03, top + 0.03, bs.x, bs.z)):
			hit = _top(root, pel, feet + 0.12, pel.y + 0.12, bs.x, -bs.z)
			top = float(hit[0])
	if is_instance_valid(a.stool) and hit[1] != null and (hit[1] as Node).get_parent() != a.stool:   # пересел на настоящее
		a.stool.queue_free()
		a.stool = null
	if is_nan(top):
		if a.has_meta("sit_pending"):   # Ch2.sit ещё не поставил таз в точку посадки — табурет ставить рано
			return
		# сиденья нет (постановка «посадила» у печи, у стены, где мебели не построили) — тело висело бы в воздухе:
		# ставим под него табурет по высоте позы (ноги на полу, низ тела на сиденье). Сидит на земле (таз у самых
		# стоп) — табурет не нужен.
		if not a.clip.begins_with("Rk_"):   # Sitting_* — сидит на земле, на снегу: табурет там не к месту
			return
		var h := pel.y - a.seat_depth() - 0.005
		var fl: Array = _top(root, pel, feet - 0.45, h - 0.1, bs.x, -bs.z)
		var floor_y: float = float(fl[0]) if not is_nan(float(fl[0])) else feet - 0.08
		if h - floor_y < 0.18 or h - floor_y > 0.75:
			return
		var host: Node = (fl[1] as Node).get_parent() if fl[1] != null else a.get_parent()
		_stool(a, pel - bs.z * 0.05, h, floor_y, host, a.global_rotation.y)
		return
	a.global_position.y += top + a.seat_depth() + 0.005 - pel.y


## Повернуть сидящего на месте (вокруг таза, а не вокруг корня: иначе таз уезжал с сиденья — Агнес «отворачивается» у
## печи и повисала в воздухе рядом с табуретом). Свой табурет поворачивается вместе с ним.
static func turn(a: Actor, deg: float) -> void:
	var sk := a.skel
	sk.force_update_all_bone_transforms()
	var pel: Vector3 = sk.global_transform * sk.get_bone_global_pose(sk.find_bone("pelvis")).origin
	var r := deg_to_rad(deg)
	var off := a.global_position - pel
	a.global_position = pel + off.rotated(Vector3.UP, r)
	a.rotation.y += r
	if is_instance_valid(a.stool):
		var so := a.stool.global_position - pel
		a.stool.global_position = pel + so.rotated(Vector3.UP, r)
		a.stool.rotation.y += r


static var _wood: StandardMaterial3D = null


## Деревянный табурет: сиденье и четыре ножки с царгами (перекладины на разной высоте, как у настоящих). Верх — top,
## низ ножек — floor_y; живёт в том же узле, что и пол под ним (ушли из дома — ушёл и табурет). Свой у каждого героя:
## пересел — табурет переезжает под него; героя переставили в другое место (Actor.placed) — табурет убирается.
static func _stool(a: Actor, at: Vector3, top: float, floor_y: float, host: Node, yaw: float) -> void:
	if _wood == null:
		_wood = StandardMaterial3D.new()
		var base := "res://assets/ph/wood_table_worn/wood_table_worn_"
		if ResourceLoader.exists(base + "diff_1k.jpg"):
			_wood.albedo_texture = load(base + "diff_1k.jpg")
			if ResourceLoader.exists(base + "nor_gl_1k.jpg"):
				_wood.normal_enabled = true
				_wood.normal_texture = load(base + "nor_gl_1k.jpg")
		_wood.albedo_color = Color(0.62, 0.48, 0.34)
		_wood.roughness = 0.82
		_wood.uv1_triplanar = true
		_wood.uv1_world_triplanar = true
		_wood.uv1_scale = Vector3(1.6, 1.6, 1.6)
	var st: Node3D = a.stool if is_instance_valid(a.stool) else null
	if st == null or st.get_parent() != host:
		if st != null:
			st.queue_free()
		st = Node3D.new()
		st.name = "stool_" + a.key
		host.add_child(st)
		a.stool = st
	for c in st.get_children():
		c.free()
	var h := top - floor_y
	var parts := [[Vector3(0.38, 0.036, 0.32), Vector3(0, h - 0.018, 0)]]
	for sx in [-1.0, 1.0]:
		for sz in [-1.0, 1.0]:
			parts.append([Vector3(0.036, h - 0.036, 0.036), Vector3(sx * 0.15, (h - 0.036) * 0.5, sz * 0.12)])
	for sz in [-1.0, 1.0]:
		parts.append([Vector3(0.27, 0.022, 0.02), Vector3(0, h * 0.28, sz * 0.12)])
	for sx in [-1.0, 1.0]:
		parts.append([Vector3(0.02, 0.022, 0.21), Vector3(sx * 0.15, h * 0.4, 0)])
	for pr in parts:
		var m := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = pr[0]
		m.mesh = bm
		m.material_override = _wood
		m.position = pr[1]
		st.add_child(m)
	st.global_position = Vector3(at.x, floor_y, at.z)
	st.global_rotation = Vector3(0, yaw, 0)
