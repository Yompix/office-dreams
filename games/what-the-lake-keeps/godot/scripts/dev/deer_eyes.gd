extends SceneTree
var n := 0
var cam: Camera3D
var deer: Deer
func _initialize():
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.05, 0.06, 0.08)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.5, 0.5, 0.55)
	env.ambient_light_energy = 0.6
	env.glow_enabled = true
	var we := WorldEnvironment.new(); we.environment = env; root.add_child(we)
	var l := DirectionalLight3D.new(); l.rotation_degrees = Vector3(-30, 20, 0); l.light_energy = 1.2; root.add_child(l)
	deer = Deer.new(); root.add_child(deer)
	cam = Camera3D.new(); cam.fov = 30; root.add_child(cam); cam.make_current()
func _process(d):
	n += 1
	var hp := deer.head_pos()
	var cp := hp + Vector3(0.25, 0.05, 1.6)
	cam.look_at_from_position(cp, hp + Vector3(0, -0.05, 0.2))
	deer.shine_from = cp
	if n == 10:
		root.get_viewport().get_texture().get_image().save_png("res://export/deer/eyes.png")
		print("HEAD ", hp)
		quit()
	return false
