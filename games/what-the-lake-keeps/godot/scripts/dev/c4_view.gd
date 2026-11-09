extends SceneTree
## Просмотр моделей главы 4: буханка (снаружи и салон), доктор Хейл, санитар. Кадры — в /tmp/prev.
var n := 0
var cam: Camera3D
var shots := []
var k := 0
var actors := []
func _initialize():
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.55, 0.62, 0.72)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.75, 0.78, 0.82)
	env.ambient_light_energy = 0.8
	env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	var we := WorldEnvironment.new(); we.environment = env; root.add_child(we)
	var l := DirectionalLight3D.new(); l.rotation_degrees = Vector3(-35, 40, 0); l.light_energy = 1.4; l.shadow_enabled = true; root.add_child(l)
	var fl := MeshInstance3D.new(); var pm := PlaneMesh.new(); pm.size = Vector2(40, 40); fl.mesh = pm
	var fm := StandardMaterial3D.new(); fm.albedo_color = Color(0.85, 0.87, 0.9); fl.material_override = fm; root.add_child(fl)
	var m := (load("res://assets/models/van.glb") as PackedScene).instantiate() as Node3D
	root.add_child(m)
	for nm in ["hale", "orderly"]:
		var a := (load("res://assets/chars/%s.glb" % nm) as PackedScene).instantiate() as Node3D
		a.position = Vector3(3.2 if nm == "hale" else 4.4, 0, 0.5)
		a.rotation_degrees = Vector3(0, 20, 0)
		root.add_child(a)
	cam = Camera3D.new(); root.add_child(cam); cam.make_current()
	shots = [
		["van_front", Vector3(3.2, 1.7, -5.2), Vector3(0, 1.1, 0), 45.0],
		["van_back", Vector3(-3.0, 1.9, 5.2), Vector3(0, 1.1, 0), 45.0],
		["van_side", Vector3(-6.0, 1.3, 0.0), Vector3(0, 1.1, 0), 45.0],
		["van_in_back", Vector3(0.0, 1.55, 1.9), Vector3(0, 1.1, -1.5), 70.0],
		["van_in_front", Vector3(0.35, 1.5, -0.6), Vector3(-0.5, 1.2, -2.0), 70.0],
		["chars", Vector3(3.8, 1.5, 3.6), Vector3(3.8, 1.1, 0.5), 45.0],
		["hale_face", Vector3(3.45, 1.72, 1.25), Vector3(3.2, 1.65, 0.5), 30.0],
		["orderly_face", Vector3(4.7, 1.95, 1.35), Vector3(4.4, 1.85, 0.5), 30.0],
	]
func _process(d):
	n += 1
	if n % 6 == 1 and k < shots.size():
		var s = shots[k]
		cam.fov = s[3]
		cam.look_at_from_position(s[1], s[2])
	if n % 6 == 5 and k < shots.size():
		root.get_viewport().get_texture().get_image().save_png("/tmp/prev/%s.png" % shots[k][0])
		k += 1
	if k >= shots.size():
		quit()
	return false
