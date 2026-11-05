extends SceneTree
## Ной в заданной точке прогулки (--pose x:z:face[:clip]) и снимки с заданных камер (--cams x:y:z:tx:ty:tz:fov,...) → snapdir/pose_N.png
var t := -1.0
var done := false

func _init() -> void:
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))

func _arg(k: String, def: String) -> String:
	var a := OS.get_cmdline_user_args()
	return a[a.find(k) + 1] if k in a else def

func _process(d: float) -> bool:
	var g = current_scene
	if g == null or not ("_ex" in g) or g._ex == null or done:
		return false
	if t < 0.0:
		t = 0.0
	t += d
	if t < 5.0:
		return false
	done = true
	_shoot(g)
	return false

func _shoot(g) -> void:
	var p := _arg("--pose", "0:0:0").split(":")
	var noah = g.actors.noah
	var pos := Vector3(float(p[0]), 0.0, float(p[1]))
	if g._ex.has_method("_floor"):
		pos.y = g._ex._floor(pos.x, pos.z)
	noah.stop_walk()
	noah.position = pos
	noah.rotation = Vector3(0, deg_to_rad(float(p[2])), 0)
	if p.size() > 3:
		noah.play(p[3], 0.0, 0.0)
	var cam := Camera3D.new()
	g.add_child(cam)
	var dir := _arg("--snapdir", "/tmp")
	var i := 0
	for cs in _arg("--cams", "").split(","):
		var c := cs.split(":")
		if c.size() < 7:
			continue
		for k in 20:
			noah.position = pos
			cam.fov = float(c[6])
			cam.look_at_from_position(Vector3(float(c[0]), float(c[1]), float(c[2])), Vector3(float(c[3]), float(c[4]), float(c[5])))
			cam.current = true
			await process_frame
		i += 1
		root.get_texture().get_image().save_png("%s/pose_%d.png" % [dir, i])
	print("POSE done %d" % i)
	quit()
