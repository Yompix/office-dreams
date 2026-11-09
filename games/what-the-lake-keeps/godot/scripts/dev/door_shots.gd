extends SceneTree
## Двери прогулок кадрами (#193): на заправке (--station) открываем дверь магазина так же, как прогулка (Explore), и
## снимаем: закрыта, открывается, открыта и Ной в проёме — снаружи и изнутри.
##   xvfb-run -a -s "-screen 0 1280x720x24" $GODOT --rendering-driver vulkan --path godot \
##     --script res://scripts/dev/door_shots.gd -- --station --snapdir DIR
var t := 0.0
var step := 0
var dir := "user://"
var cam: Camera3D
var ex
var door: Dictionary


func _init() -> void:
	var a := OS.get_cmdline_user_args()
	if "--snapdir" in a:
		dir = a[a.find("--snapdir") + 1]
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))


func _shot(name: String, from: Vector3, to: Vector3, fov := 50.0) -> void:
	cam.fov = fov
	cam.look_at_from_position(from, to)


func _save(name: String) -> void:
	get_root().get_texture().get_image().save_png("%s/%s.png" % [dir, name])


func _process(d: float) -> bool:
	var g = current_scene
	if g == null or not ("actors" in g) or not g.actors.has("noah"):
		return false
	t += d
	match step:
		0:
			if t < 2.5:
				return false
			if g.hud:
				g.hud.visible = false
			cam = Camera3D.new()
			g.add_child(cam)
			cam.current = true
			Actor.view_cam = cam
			var def: Dictionary = g.data.explore["st24"]
			ex = Explore.new(g, def)
			ex.a = g.actors.noah
			door = (ex.d.doors[0] as Dictionary).duplicate()
			g._place({"who": "noah", "at": [20.05, 0.38, -1.95], "face": 90, "clip": "Idle"})
			_shot("a", Vector3(18.3, 1.7, 0.9), Vector3(21.1, 1.3, -2.0), 48.0)
			step = 1
			t = 0.0
		1:
			if t > 0.4:
				_save("door_0_closed")
				ex._open_door(door)
				step = 2
				t = 0.0
		2:
			if t > 0.35:
				_save("door_1_opening")
				step = 3
				t = 0.0
		3:
			if t > 1.0:
				_save("door_2_open")
				(g.actors.noah as Actor).walk_to(Vector3(22.3, 0.38, -2.0), 1.1, "Rk_Walk", "Idle")
				step = 4
				t = 0.0
		4:
			if t > 1.1:
				_save("door_3_through")
				_shot("b", Vector3(24.6, 1.75, -4.6), Vector3(21.2, 1.2, -2.0), 52.0)
				step = 5
				t = 0.0
		5:
			if t > 0.3:
				_save("door_4_inside")
				print("blocks: ", (ex.d.block as Array).size(), " free inside: ", ex._free(24.5, -6.7), " free counter: ", ex._free(26.4, -7.35), " free doorway: ", ex._free(21.15, -2.0))
				quit()
				return true
	return false
