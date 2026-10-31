extends SceneTree
var n := 0
var cam: Camera3D
var deer: Deer
var shots := [["stand", Vector3(2.6, 1.3, 2.2), 0.5], ["look", Vector3(2.6, 1.3, 2.2), 2.0], ["walk", Vector3(3.2, 1.0, 0.0), 0.3],
	["walk", Vector3(3.2, 1.0, 0.0), 0.6], ["lie", Vector3(2.4, 1.4, 1.6), 2.5], ["dead", Vector3(0.6, 2.4, 2.2), 3.0]]
var si := 0
var tt := 0.0
func _initialize():
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.32, 0.35, 0.4)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.6, 0.62, 0.66)
	env.ambient_light_energy = 0.7
	env.tonemap_mode = Environment.TONE_MAPPER_ACES
	var we := WorldEnvironment.new(); we.environment = env; root.add_child(we)
	var l := DirectionalLight3D.new(); l.rotation_degrees = Vector3(-40, 30, 0); l.light_energy = 1.6; l.shadow_enabled = true; root.add_child(l)
	var g := MeshInstance3D.new(); var pm := PlaneMesh.new(); pm.size = Vector2(12, 12); g.mesh = pm
	var gm := StandardMaterial3D.new(); gm.albedo_color = Color(0.85, 0.87, 0.9); g.material_override = gm; root.add_child(g)
	deer = Deer.new(); root.add_child(deer)
	cam = Camera3D.new(); cam.fov = 40; root.add_child(cam); cam.make_current()
func _process(d):
	if si >= shots.size():
		quit(); return false
	var s: Array = shots[si]
	deer.mode = "stand" if s[0] == "look" else s[0]
	deer.look_target = cam.global_position if s[0] == "look" else null
	if s[0] == "walk": deer.position = Vector3.ZERO
	cam.look_at_from_position(s[1], Vector3(0, 0.75, 0.1))
	tt += d
	if tt >= s[2]:
		root.get_viewport().get_texture().get_image().save_png("res://export/deer/test_%d_%s.png" % [si, s[0]])
		si += 1; tt = 0.0
	return false
