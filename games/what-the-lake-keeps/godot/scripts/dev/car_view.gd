extends SceneTree
var n := 0
func _initialize():
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.3, 0.35, 0.4)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.7, 0.7, 0.7)
	var we := WorldEnvironment.new(); we.environment = env; root.add_child(we)
	var l := DirectionalLight3D.new(); l.rotation_degrees = Vector3(-50, 30, 0); root.add_child(l)
	var m := (load("res://assets/models/sedan.glb") as PackedScene).instantiate() as Node3D
	root.add_child(m)
	var cam := Camera3D.new(); root.add_child(cam); cam.make_current()
	cam.look_at_from_position(Vector3(2.2, 3.2, 4.5), Vector3(0, 1.0, 0))
func _process(d):
	n += 1
	if n == 8:
		root.get_viewport().get_texture().get_image().save_png("res://export/car_view.png")
		quit()
	return false
