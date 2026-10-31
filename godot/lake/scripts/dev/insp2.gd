extends SceneTree
func _init():
	var sc: Node = (load("res://assets/models/station.glb") as PackedScene).instantiate()
	for mi in sc.find_children("*", "MeshInstance3D", true, false):
		var m := mi as MeshInstance3D
		var n := String(m.name)
		if n.contains("24") or n.contains("ice_word") or n.contains("sign_face"):
			var ab := m.transform * m.get_aabb()
			print(n, " pos=", m.position, " aabb=", ab.position, " size=", ab.size, " mat=", m.get_active_material(0).resource_name if m.get_active_material(0) else "none")
	quit()
