extends SceneTree
## Детали sedan.glb: имя, видимость, AABB в осях машины, число поверхностей и треугольников (что над салоном, y > 1,4).
func _initialize():
	var model := (load("res://assets/models/sedan.glb") as PackedScene).instantiate() as Node3D
	root.add_child(model)
	await process_frame
	for n in model.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		var ab := mi.global_transform * mi.get_aabb()
		var tris := 0
		for s in mi.mesh.get_surface_count():
			var arr := mi.mesh.surface_get_arrays(s)
			var idx: PackedInt32Array = arr[Mesh.ARRAY_INDEX]
			tris += (idx.size() if idx.size() > 0 else (arr[Mesh.ARRAY_VERTEX] as PackedVector3Array).size()) / 3
		if ab.end.y > 1.3:
			print(mi.name, " y=", snappedf(ab.position.y, 0.01), "..", snappedf(ab.end.y, 0.01), " z=", snappedf(ab.position.z, 0.01), "..", snappedf(ab.end.z, 0.01),
				" x=", snappedf(ab.position.x, 0.01), "..", snappedf(ab.end.x, 0.01), " surf=", mi.mesh.get_surface_count(), " tris=", tris,
				" lods=", (mi.mesh as ArrayMesh).surface_get_format(0) if mi.mesh is ArrayMesh else -1)
	quit()
