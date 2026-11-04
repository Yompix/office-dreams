extends SceneTree
## Крыша седана: треугольники кузова выше 1,45 м — куда смотрит лицевая сторона (вверх — видна сверху, вниз — отсечена).
func _initialize():
	var m := (load("res://assets/models/sedan.glb") as PackedScene).instantiate() as Node3D
	root.add_child(m)
	for n in m.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		var nm := String(mi.name)
		var arr := mi.mesh.surface_get_arrays(0)
		var v: PackedVector3Array = arr[Mesh.ARRAY_VERTEX]
		var idx: PackedInt32Array = arr[Mesh.ARRAY_INDEX] if arr[Mesh.ARRAY_INDEX] != null else PackedInt32Array()
		var up := 0
		var down := 0
		var cnt := idx.size() / 3 if idx.size() > 0 else v.size() / 3
		for t in cnt:
			var a: Vector3 = v[idx[t * 3]] if idx.size() > 0 else v[t * 3]
			var b: Vector3 = v[idx[t * 3 + 1]] if idx.size() > 0 else v[t * 3 + 1]
			var c: Vector3 = v[idx[t * 3 + 2]] if idx.size() > 0 else v[t * 3 + 2]
			var y := (a.y + b.y + c.y) / 3.0
			if y < 1.45:
				continue
			var nrm := (b - a).cross(c - a)
			if nrm.y < -1e-6:
				up += 1   # Godot: по часовой — лицом к нам (лицевая сторона — где обход по часовой)
			elif nrm.y > 1e-6:
				down += 1
		if up + down > 0:
			print(nm, " выше 1.45: ", up + down, " треуг.; нормаль обхода вниз (лицом вверх в Godot): ", up, ", вверх: ", down)
	quit()
