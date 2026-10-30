class_name HumanRig
extends RefCounted
## «Переодевание» скелета героя MakeHuman под скелет клипов, как makeHuman() в старой игре (chars2.js):
## каждая кость получает поворот покоя кости Quaternius (в пространстве модели), суставы остаются на месте,
## привязка кожи пересчитывается — сетка в покое не меняется. После этого клипы Quaternius встают как есть.

const QW_NAME := {"Root": "root", "head": "Head"}   # имя кости героя → имя кости скелета клипов


## Возвращает { skel, k } — скелет и масштаб высоты таза для библиотеки клипов.
static func rebase(model: Node3D) -> Dictionary:
	var skel: Skeleton3D = model.find_child("Skeleton3D", true, false)
	var to_model := _xform_to(skel, model)            # скелет → пространство модели
	var n := skel.get_bone_count()
	var old_g: Array[Transform3D] = []
	for i in n:
		old_g.append(skel.get_bone_global_rest(i))
	var order := range(n)
	order.sort_custom(func(a, b): return _depth(skel, a) < _depth(skel, b))
	var new_g: Array[Transform3D] = []
	new_g.resize(n)
	for i in order:
		var nm := skel.get_bone_name(i)
		var qw = AnimLib.rest_world(QW_NAME.get(nm, nm))
		var p := skel.get_bone_parent(i)
		var basis: Basis
		if qw != null:
			basis = to_model.basis.inverse() * Basis(qw as Quaternion)
		else:
			var parent_new: Basis = new_g[p].basis if p >= 0 else Basis.IDENTITY
			basis = parent_new * skel.get_bone_rest(i).basis
		new_g[i] = Transform3D(basis.orthonormalized(), old_g[i].origin)   # сустав на прежнем месте
	for i in n:
		var p := skel.get_bone_parent(i)
		var local: Transform3D = (new_g[p].affine_inverse() * new_g[i]) if p >= 0 else new_g[i]
		skel.set_bone_rest(i, local)
	skel.reset_bone_poses()
	# привязка кожи: кость_новая × привязка_новая = кость_старая × привязка_старая
	for mi in model.find_children("*", "MeshInstance3D", true, false):
		var m := mi as MeshInstance3D
		if m.skin == null:
			continue
		var skin: Skin = m.skin.duplicate()
		for j in skin.get_bind_count():
			var bi := skin.get_bind_bone(j)
			if bi < 0:
				bi = skel.find_bone(skin.get_bind_name(j))
			if bi < 0:
				continue
			skin.set_bind_pose(j, new_g[bi].affine_inverse() * old_g[bi] * skin.get_bind_pose(j))
		m.skin = skin
	var pel := skel.find_bone("pelvis")
	var k := skel.get_bone_rest(pel).origin.z / AnimLib.pelvis_z()
	return {"skel": skel, "k": k}


static func _xform_to(node: Node3D, top: Node3D) -> Transform3D:
	var t := Transform3D.IDENTITY
	var n: Node = node
	while n != null and n != top:
		if n is Node3D:
			t = (n as Node3D).transform * t
		n = n.get_parent()
	return t


static func _depth(skel: Skeleton3D, i: int) -> int:
	var d := 0
	var p := skel.get_bone_parent(i)
	while p >= 0:
		d += 1
		p = skel.get_bone_parent(p)
	return d
