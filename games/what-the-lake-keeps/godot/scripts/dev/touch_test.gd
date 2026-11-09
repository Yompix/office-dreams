extends SceneTree
## Проверка управления касанием (телефон) в облаке: прогулка — джойстик двигает Ноя, палец справа поворачивает камеру,
## «Осмотреть» доходит до прогулки, кнопка паузы ставит паузу; снимок экрана с джойстиком.
##   xvfb-run -a -s "-screen 0 1280x720x24" $GODOT --rendering-method gl_compatibility --path godot \
##     --script res://scripts/dev/touch_test.gd -- --scene c2_s4_porch --restore --touch --snapdir папка
var t := 0.0
var step := 0
var p0 := Vector3.ZERO
var res: Array = []
var dir := "user://"
var drag_t := 0.0
var yaw0 := 0.0
var look_x := 0.0


func _init() -> void:
	var args := OS.get_cmdline_user_args()
	if "--snapdir" in args:
		dir = args[args.find("--snapdir") + 1]
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))


func _w(p: Vector2) -> Vector2:   # точка холста 1920×1080 → точка окна
	var ws := Vector2(DisplayServer.window_get_size())
	var vs: Vector2 = root.get_visible_rect().size
	return p * (ws / vs)


func _touch(i: int, p: Vector2, on: bool) -> void:
	var e := InputEventScreenTouch.new()
	e.index = i
	e.position = _w(p)
	e.pressed = on
	Input.parse_input_event(e)


func _drag(i: int, p: Vector2, rel := Vector2.ZERO) -> void:
	var e := InputEventScreenDrag.new()
	e.index = i
	e.position = _w(p)
	e.relative = _w(rel)
	Input.parse_input_event(e)


func check(name: String, ok: bool) -> void:
	res.append(("ОК   " if ok else "НЕТ  ") + name)
	print(res[-1])


func _process(d: float) -> bool:
	var g = current_scene
	if g == null or not ("_ex" in g):
		return false
	t += d
	var vs: Vector2 = root.get_visible_rect().size
	match step:
		0:
			if g._ex != null and not g._ex.busy and t > 2.0:
				check("джойстик включён (TouchCtl)", TouchCtl.enabled)
				p0 = (g.actors.noah as Node3D).global_position
				_touch(0, Vector2(260, vs.y - 260), true)
				step = 1
				t = 0.0
			elif t > 120.0:
				check("прогулка не началась", false)
				step = 9
		1:   # тянем джойстик вверх — Ной идёт вперёд (2,5 с)
			drag_t += d
			_drag(0, Vector2(260, vs.y - 260 - minf(1.0, drag_t * 3.0) * 110.0))
			if t > 0.6 and t < 0.7:
				root.get_texture().get_image().save_png("%s/touch_walk.png" % dir)
			if t > 2.5:
				_touch(0, Vector2(260, vs.y - 370), false)
				var moved := (g.actors.noah as Node3D).global_position.distance_to(p0)
				check("джойстик двигает Ноя (%.2f м за 2,5 с)" % moved, moved > 0.8)
				step = 2
				t = 0.0
		2:
			if t > 0.8:
				check("джойстик отпущен — стоит (TouchCtl.move = 0)", TouchCtl.move.length() < 0.01)
				yaw0 = float(g._ex.yaw)
				_touch(3, Vector2(vs.x * 0.7, vs.y * 0.4), true)
				step = 21
				t = 0.0
		21:   # палец справа ведём влево на 300 точек — камера поворачивается (сдвиг — сколько прошёл палец с прошлого кадра)
			var lx := vs.x * 0.7 - minf(1.0, t * 2.0) * 300.0
			_drag(3, Vector2(lx, vs.y * 0.4), Vector2(lx - look_x, 0) if look_x > 0.0 else Vector2.ZERO)
			look_x = lx
			if t > 0.6:
				_touch(3, Vector2(vs.x * 0.7 - 300.0, vs.y * 0.4), false)
				var dy := absf(angle_difference(yaw0, float(g._ex.yaw)))
				check("палец справа поворачивает камеру (%.0f°)" % rad_to_deg(dy), dy > 0.3)
				_touch(1, Vector2(vs.x - 190, vs.y - 190), true)
				_touch(1, Vector2(vs.x - 190, vs.y - 190), false)
				step = 3
				t = 0.0
		3:
			if t > 0.3:
				check("«Осмотреть» дошло до прогулки (нажатие забрано)", not TouchCtl._use)
				_touch(2, Vector2(vs.x - 90, 84), true)
				_touch(2, Vector2(vs.x - 90, 84), false)
				step = 4
				t = 0.0
		4:
			if t > 0.5:
				check("кнопка паузы ставит паузу", paused)
				root.get_texture().get_image().save_png("%s/touch_pause.png" % dir)
				step = 9
		9:
			print("ИТОГ ПРОВЕРКИ КАСАНИЙ: %d из %d" % [res.filter(func(r): return r.begins_with("ОК")).size(), res.size()])
			quit()
			return true
	return false
