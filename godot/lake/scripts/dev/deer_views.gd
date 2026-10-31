extends SceneTree
var n := 0
var cam: Camera3D
var deer: Deer
var views := [[Vector3(0.08, 3.2, 0.2), Vector3(0.08, 1.0, 0.21), "top"], [Vector3(0.08, 1.25, 3.0), Vector3(0.08, 1.15, 0.4), "front"], [Vector3(3.0, 1.2, 0.4), Vector3(0.08, 1.0, 0.4), "side"]]
var vi := 0
func _initialize():
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.25, 0.28, 0.32)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.6, 0.6, 0.62)
	var we := WorldEnvironment.new(); we.environment = env; root.add_child(we)
	var l := DirectionalLight3D.new(); l.rotation_degrees = Vector3(-50, 20, 0); root.add_child(l)
	deer = Deer.new(); root.add_child(deer)
	cam = Camera3D.new(); cam.fov = 40; root.add_child(cam); cam.make_current()
func _process(d):
	n += 1
	var v: Array = views[vi]
	cam.look_at_from_position(v[0], v[1], Vector3.UP if vi != 0 else Vector3(0, 0, -1))
	if n == 8:
		root.get_viewport().get_texture().get_image().save_png("res://export/deer/view_%s.png" % v[2])
		n = 0
		vi += 1
		if vi >= views.size():
			quit()
	return false
