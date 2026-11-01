extends SceneTree
## Волосы героев: где лежат вершины относительно головы (покой) — длина свободной части, спереди/сзади/по бокам.
var _f := 0
var _list := ["noah", "iris", "zoe", "eva", "agnes", "hale", "anna", "nina", "roman", "victor", "orderly", "noah_kid", "hale_young"]
func _process(_d: float) -> bool:
	_f += 1
	if _f < 2:
		return false
	for k in _list:
		var a := Actor.make(k, k)
		get_root().add_child(a)
		var sk := a.skel
		sk.force_update_all_bone_transforms()
		var hb := sk.find_bone("head")
		var hg := sk.get_bone_global_pose(hb)        # голова в пространстве скелета
		var nb := sk.find_bone("neck_01")
		var ng := sk.get_bone_global_pose(nb)
		for mi in a.model.find_children("*", "MeshInstance3D", true, false):
			var m := mi as MeshInstance3D
			if not String(m.name).to_lower().contains("hair"):
				continue
			# сетка → скелет: вершины сетки в покое лежат в пространстве модели; скелет — тоже (у MakeHuman без смещений)
			var to_sk := sk.global_transform.affine_inverse() * m.global_transform
			var arr := m.mesh.surface_get_arrays(0)
			var vs: PackedVector3Array = arr[Mesh.ARRAY_VERTEX]
			var ymin := 1e9
			var ymax := -1e9
			var back := 0
			var front := 0
			var side := 0
			var below_neck := 0
			var hc := hg.origin + Vector3(0, 0.08, 0)
			for v in vs:
				var p := to_sk * v
				ymin = minf(ymin, p.y)
				ymax = maxf(ymax, p.y)
				if p.y < ng.origin.y:
					below_neck += 1
				var d := p - hc
				if d.y < -0.06:
					if d.z < -0.03:
						back += 1
					elif d.z > 0.03:
						front += 1
					else:
						side += 1
			print("%-11s вершин %5d  верх %.2f низ %.2f (голова %.2f, шея %.2f)  ниже шеи %4d  ниже висков: зад %d бок %d перёд %d" % [k, vs.size(), ymax, ymin, hg.origin.y, ng.origin.y, below_neck, back, side, front])
		a.free()
	quit()
	return true
