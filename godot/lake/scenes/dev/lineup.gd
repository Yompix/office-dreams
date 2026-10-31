extends Node3D
## Проба героев: все в ряд (стоя, клип Idle) в ровном свете, снимок $SHOT; CLOSE=1 — крупно лица.
var frames := 0

func _ready():
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.18, 0.19, 0.21)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.5, 0.52, 0.56)
	env.ambient_light_energy = 0.5
	env.tonemap_mode = Environment.TONE_MAPPER_ACES
	var we := WorldEnvironment.new()
	we.environment = env
	add_child(we)
	var key := DirectionalLight3D.new()
	key.rotation_degrees = Vector3(-30, 25, 0)
	key.light_energy = 1.6
	key.shadow_enabled = true
	add_child(key)
	var names: Array = (OS.get_environment("WHO") if OS.get_environment("WHO") != "" else "noah,iris,danny,roman").split(",")
	for i in names.size():
		var a := Actor.make(names[i], names[i])
		add_child(a)
		a.position = Vector3((i - (names.size() - 1) / 2.0) * 0.75, 0, 0)
		a.play("Idle", 0.5)
		if OS.get_environment("ONLY") != "":   # проверка: только части с этим словом в имени
			for mi in a.find_children("*", "MeshInstance3D", true, false):
				(mi as MeshInstance3D).visible = String(mi.name).contains(OS.get_environment("ONLY"))
	var cam := Camera3D.new()
	add_child(cam)
	if OS.get_environment("CLOSE") != "":
		cam.fov = 18
		cam.look_at_from_position(Vector3(0, 1.45, 4.2), Vector3(0, 1.38, 0))
	else:
		cam.fov = 34
		cam.look_at_from_position(Vector3(0, 1.1, 4.6), Vector3(0, 0.9, 0))
	cam.make_current()

func _process(_d):
	frames += 1
	if frames == 20:
		get_viewport().get_texture().get_image().save_png(OS.get_environment("SHOT"))
		get_tree().quit()
