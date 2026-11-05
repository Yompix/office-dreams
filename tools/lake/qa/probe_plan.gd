extends SceneTree
## Проверка помещений: когда начнётся прогулка — план сверху (срез на высоте 1,95 м над полом) по всей площадке ходьбы,
## картинка + данные (ходьба, препятствия, двери, точки) — в snapdir/plan_<id>.png/json. Наложение — plan_overlay.py.
var t := -1.0
var done := false
var vp: SubViewport

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
	if t < 5.0:   # локация догружается (кафе главы 2 ставится не сразу)
		return false
	var want := _arg("--planid", "")
	if want != "" and _id(g) != want:   # не та прогулка (в сцене их несколько) — ждём свою
		t = -1.0
		return false
	done = true
	_shoot(g)
	return false

func _id(g) -> String:
	var dd: Dictionary = g._ex.d
	for ek in g.data.explore:
		if JSON.stringify(g.data.explore[ek].get("walk", [])) == JSON.stringify(dd.get("walk", [])) \
				and JSON.stringify(g.data.explore[ek].get("spots", []).map(func(s): return s.id)) == JSON.stringify(dd.get("spots", []).map(func(s): return s.id)):
			return String(ek)
	return "x"

func _shoot(g) -> void:
	var ex = g._ex
	var dd: Dictionary = ex.d
	var x0 := 1e9
	var z0 := 1e9
	var x1 := -1e9
	var z1 := -1e9
	for w in dd.get("walk", []):
		x0 = minf(x0, float(w[0])); z0 = minf(z0, float(w[1])); x1 = maxf(x1, float(w[2])); z1 = maxf(z1, float(w[3]))
	for dr in dd.get("doors", []):
		for w in dr.get("walk", []) + dr.get("floors", []):
			x0 = minf(x0, float(w[0])); z0 = minf(z0, float(w[1])); x1 = maxf(x1, float(w[2])); z1 = maxf(z1, float(w[3]))
	var fy := 0.0
	for f in dd.get("floors", []):
		fy = maxf(fy, float(f[4]))
	var cx := (x0 + x1) * 0.5
	var cz := (z0 + z1) * 0.5
	var ext := maxf(x1 - x0, z1 - z0) + 1.0
	var N := 1400
	vp = SubViewport.new()
	vp.size = Vector2i(N, N)
	vp.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	g.add_child(vp)
	var cam := Camera3D.new()
	cam.projection = Camera3D.PROJECTION_ORTHOGONAL
	cam.size = ext
	cam.keep_aspect = Camera3D.KEEP_HEIGHT
	cam.near = 0.05
	cam.far = 60.0
	vp.add_child(cam)
	var cy := fy + 1.95
	cam.look_at_from_position(Vector3(cx, cy, cz), Vector3(cx, cy - 10.0, cz), Vector3(0, 0, -1))
	cam.current = true
	var sun := DirectionalLight3D.new()   # план читается и ночью: свет сверху на время снимка
	sun.light_energy = 1.6
	sun.rotation_degrees = Vector3(-90, 0, 0)
	g.add_child(sun)
	for i in 6:
		await process_frame
	var dir := _arg("--snapdir", "/tmp")
	var id := _id(g)
	vp.get_texture().get_image().save_png("%s/plan_%s.png" % [dir, id])
	# срез на высоте тела: что твёрдое между полом + 0,3 м и полом + 1,7 м (герои и снег спрятаны) — маска по прозрачности
	var fmin := 0.0
	for f in dd.get("floors", []):
		fmin = minf(fmin, float(f[4]))
	var hid: Array = []
	for k in g.actors:
		var ac = g.actors[k]
		if is_instance_valid(ac) and ac.visible:
			ac.visible = false
			hid.append(ac)
	for n in g.get_tree().root.find_children("*", "GPUParticles3D", true, false) + g.get_tree().root.find_children("*", "CPUParticles3D", true, false) \
			+ g.get_tree().root.find_children("*", "Label3D", true, false) + g.get_tree().root.find_children("*", "Sprite3D", true, false):
		if (n as Node3D).visible:
			(n as Node3D).visible = false
			hid.append(n)
	var vp2 := SubViewport.new()
	vp2.size = Vector2i(N, N)
	vp2.transparent_bg = true
	vp2.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	g.add_child(vp2)
	var cam2 := Camera3D.new()
	cam2.projection = Camera3D.PROJECTION_ORTHOGONAL
	cam2.size = ext
	cam2.keep_aspect = Camera3D.KEEP_HEIGHT
	var top := fmin + 1.7
	var bot := fy + float(_arg("--slicebot", "0.45"))   # улица: снег по колено не считаем (0,45); комнаты — от 0,25 м (лавки, сундуки)
	cam2.near = 0.01
	cam2.far = maxf(0.2, top - bot)
	var env := Environment.new()   # без тумана и неба — пустые места остаются прозрачными
	env.background_mode = Environment.BG_CLEAR_COLOR
	cam2.environment = env
	vp2.add_child(cam2)
	cam2.look_at_from_position(Vector3(cx, top, cz), Vector3(cx, top - 10.0, cz), Vector3(0, 0, -1))
	cam2.current = true
	for i in 6:
		await process_frame
	vp2.get_texture().get_image().save_png("%s/mask_%s.png" % [dir, id])
	for n in hid:
		if is_instance_valid(n):
			n.visible = true
	var info := {"id": id, "cx": cx, "cz": cz, "ext": ext, "N": N, "floor": fy, "cut": cy, "slice": [bot, top], "walk": dd.get("walk", []), "block": dd.get("block", []),
		"doors": dd.get("doors", []).map(func(dr): return {"id": dr.id, "hinge": dr.get("hinge", []), "open": dr.get("open", 90), "unblock": dr.get("unblock", []), "block": dr.get("block", []), "walk": dr.get("walk", [])}),
		"spots": dd.get("spots", []).map(func(s): return {"id": s.id, "stand": s.get("stand", []), "at": s.get("at", [])}),
		"rad": Explore.RAD, "actors": {}}
	for k in g.actors:
		var ac = g.actors[k]
		if is_instance_valid(ac):
			info.actors[k] = [ac.global_position.x, ac.global_position.z]
	var f := FileAccess.open("%s/plan_%s.json" % [dir, id], FileAccess.WRITE)
	f.store_string(JSON.stringify(info))
	f.close()
	print("PLAN %s saved ext %.1f cut %.2f" % [id, ext, cy])
	quit()
