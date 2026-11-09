extends SceneTree
func _init():
	for f in ["res://assets/models/anims.glb", "res://assets/models/anims2.glb"]:
		var sc: Node = (load(f) as PackedScene).instantiate()
		for ap in sc.find_children("*", "AnimationPlayer", true, false):
			print(f.get_file(), ": ", ", ".join((ap as AnimationPlayer).get_animation_list()))
	quit()
