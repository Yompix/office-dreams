extends SceneTree
func _initialize():
	var m := (load("res://assets/models/sedan.glb") as PackedScene).instantiate() as Node3D
	root.add_child(m)
	for n in m.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		var nm := String(mi.name)
		if nm.begins_with("body") or nm.begins_with("glass") or nm.begins_with("roof"):
			var mat := mi.get_active_material(0) as StandardMaterial3D
			print(nm, " surf=", mi.mesh.get_surface_count(), " mat=", mat.resource_name if mat else "-", " transp=", mat.transparency if mat else -1, " alpha=", mat.albedo_color.a if mat else -1, " cull=", mat.cull_mode if mat else -1, " aabb=", mi.get_aabb())
	quit()
