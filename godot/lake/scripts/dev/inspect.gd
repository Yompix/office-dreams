extends SceneTree
## Список клипов движений и форм лица героя (для подбора поз): godot --headless --path godot/lake --script res://scripts/dev/inspect.gd
func _init():
	for f in ["res://assets/models/anims.glb", "res://assets/models/anims2.glb"]:
		var sc: Node = (load(f) as PackedScene).instantiate()
		var ap: AnimationPlayer = sc.find_child("AnimationPlayer", true, false)
		print(f.get_file(), ": ", ", ".join(ap.get_animation_list()))
		sc.free()
	for who in ["ethan", "lily"]:
		var m: Node = (load("res://assets/models/%s.glb" % who) as PackedScene).instantiate()
		for mi in m.find_children("*", "MeshInstance3D", true, false):
			var mesh: Mesh = (mi as MeshInstance3D).mesh
			var shapes := []
			if mesh is ArrayMesh:
				for i in (mesh as ArrayMesh).get_blend_shape_count():
					shapes.append((mesh as ArrayMesh).get_blend_shape_name(i))
			print(who, " ", mi.name, " shapes=", shapes.size(), " ", ", ".join(shapes.slice(0, 12)))
		var sk: Skeleton3D = m.find_child("*", true, false) if false else m.find_children("*", "Skeleton3D", true, false).front()
		var names := []
		for i in sk.get_bone_count():
			names.append(sk.get_bone_name(i))
		print(who, " bones ", sk.get_bone_count(), ": ", ", ".join(names.slice(0, 60)))
		m.free()
	quit()
