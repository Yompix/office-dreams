extends SceneTree
var n := 0
var cam: Camera3D
func _initialize():
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.2, 0.21, 0.23)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.6, 0.6, 0.62)
	env.ambient_light_energy = 0.6
	env.tonemap_mode = Environment.TONE_MAPPER_ACES
	var we := WorldEnvironment.new()
	we.environment = env
	root.add_child(we)
	var l := DirectionalLight3D.new()
	l.rotation_degrees = Vector3(-35, 30, 0)
	l.light_energy = 1.5
	root.add_child(l)
	var d: Node3D = (load("res://assets/models/deer_raw.glb") as PackedScene).instantiate()
	root.add_child(d)
	cam = Camera3D.new()
	cam.fov = 40
	root.add_child(cam)
	cam.make_current()
func _process(_d):
	n += 1
	var views := [Vector3(0, 0.3, 4.2), Vector3(4.2, 0.3, 0), Vector3(2.8, 1.6, 2.8)]
	if n % 6 == 1 and n / 6 < views.size():
		cam.look_at_from_position(views[n / 6], Vector3(0, 0, 0))
	if n % 6 == 5:
		root.get_viewport().get_texture().get_image().save_png("res://export/deer/view_%d.png" % (n / 6))
		if n / 6 >= views.size() - 1:
			quit()
	return false
