extends SceneTree
## Седан сам по себе, вид сверху-сзади: есть ли крыша (без кода игры). Снимок — args[0] (png).
func _initialize():
	var m := (load("res://assets/models/sedan.glb") as PackedScene).instantiate() as Node3D
	root.add_child(m)
	var only := OS.get_cmdline_user_args()[1] if OS.get_cmdline_user_args().size() > 1 else ""
	if only != "":   # показать только эти сетки (через запятую): что именно — крыша
		for n in m.find_children("*", "MeshInstance3D", true, false):
			(n as MeshInstance3D).visible = String(n.name) in only.split(",")
	var cam := Camera3D.new()
	root.add_child(cam)
	cam.position = Vector3(1.6, 3.0, 4.2)
	cam.look_at(Vector3(0, 1.0, 0.3), Vector3.UP)
	cam.current = true
	var l := DirectionalLight3D.new()
	root.add_child(l)
	l.rotation_degrees = Vector3(-55, 30, 0)
	var env := WorldEnvironment.new()
	env.environment = Environment.new()
	env.environment.background_mode = Environment.BG_COLOR
	env.environment.background_color = Color(0.5, 0.55, 0.6)
	env.environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.environment.ambient_light_color = Color(0.6, 0.6, 0.6)
	root.add_child(env)
	for i in 8:
		await process_frame
	var out := OS.get_cmdline_user_args()[0] if OS.get_cmdline_user_args().size() > 0 else "user://car_top.png"
	root.get_viewport().get_texture().get_image().save_png(out)
	quit()
