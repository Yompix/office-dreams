extends SceneTree
## Имена сеток модели героя и их размер: godot --headless --script res://scripts/dev/mesh_names.gd -- файл…
func _init() -> void:
	var args := OS.get_cmdline_user_args()
	for f in args:
		var a := Actor.make(f.get_file().get_basename().split("_")[0], f)
		root.add_child(a)
		var out := []
		for mi in a.model.find_children("*", "MeshInstance3D", true, false):
			var m := mi as MeshInstance3D
			var n := 0
			for s in m.mesh.get_surface_count():
				n += (m.mesh.surface_get_arrays(s)[Mesh.ARRAY_VERTEX] as PackedVector3Array).size()
			out.append("%s(%d)" % [m.name, n])
		print("MESHES ", f, ": ", ", ".join(out))
	quit()
