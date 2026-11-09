extends SceneTree
func _initialize():
	var m := (load("res://assets/models/station.glb") as PackedScene).instantiate() as Node3D
	root.add_child(m)
	for n in m.find_children("*", "MeshInstance3D", true, false):
		var nm := String(n.name)
		if nm.contains("door") or nm.contains("glass") or nm.contains("band"):
			print(nm, " parent=", n.get_parent().name, " pos=", (n as Node3D).global_position)
	quit()
