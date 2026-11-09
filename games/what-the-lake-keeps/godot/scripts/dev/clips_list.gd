extends SceneTree
func _initialize():
	for f in ["res://assets/models/anims.glb", "res://assets/models/anims2.glb"]:
		var n := (load(f) as PackedScene).instantiate()
		var ap := n.find_children("*", "AnimationPlayer", true, false)[0] as AnimationPlayer
		print(f, ": ", ", ".join(ap.get_animation_list()))
	quit()
