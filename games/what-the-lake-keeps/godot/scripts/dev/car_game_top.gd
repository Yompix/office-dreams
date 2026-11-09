extends SceneTree
## Машина игры (Car.setup — материалы, слои, салон), вид сверху: пропадает ли крыша из-за кода машины. args[0] — png.
func _initialize():
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.5, 0.55, 0.6)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.7, 0.7, 0.7)
	var we := WorldEnvironment.new(); we.environment = env; root.add_child(we)
	var l := DirectionalLight3D.new(); l.rotation_degrees = Vector3(-50, 30, 0); root.add_child(l)
	var car := Car.new()
	root.add_child(car)
	car.setup(true)
	var cam := Camera3D.new(); root.add_child(cam); cam.make_current()
	cam.look_at_from_position(Vector3(2.2, 3.4, 4.8), Vector3(0, 1.0, 0))
	var ua := OS.get_cmdline_user_args()
	if ua.size() > 1:   # порог LOD сеток (как settings.gd по качеству) и дальняя камера сзади-сверху, как общий план заправки
		root.mesh_lod_threshold = float(ua[1])
		cam.look_at_from_position(Vector3(0.3, 4.6, 8.6), Vector3(0, 0.9, -0.5))
	if ua.size() > 3:   # своя камера: "x,y,z" "x,y,z" [fov]
		var cp := ua[2].split_floats(","); var ct := ua[3].split_floats(",")
		cam.look_at_from_position(Vector3(cp[0], cp[1], cp[2]), Vector3(ct[0], ct[1], ct[2]))
		if ua.size() > 4:
			cam.fov = float(ua[4])
	for i in 10:
		await process_frame
	for n in car.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		if String(mi.name) in ["body", "glass", "headliner"]:
			print(mi.name, " visible=", mi.is_visible_in_tree(), " layers=", mi.layers, " mat=", mi.get_active_material(0))
	print("cam mask=", cam.cull_mask)
	root.get_texture().get_image().save_png(OS.get_cmdline_user_args()[0])
	quit()
