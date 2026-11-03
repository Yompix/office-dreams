extends Node
## Проверка прогулки «как игрок»: клавиши WASD и E подаются в игру настоящими событиями (Input.parse_input_event),
## Ной идёт к каждой точке сам — по пути в обход препятствий (точки "nav"), у точки жмём E, в разговоре — 1.
## Каждый кадр: Ной не внутри препятствия и не за краем, камера не в постройке. В сборку не входит (scripts/dev/*).
##   godot --path godot/lake -- --walktest --scene s5 [--fast] [--flags late=1,pills=1] [--shotlog папка]
## Итог — строка «прогулка-тест: …» в журнале и код выхода 0 / 1.
var g
var ex: Explore
var held := {}            # клавиша → нажата
var plan: Array = []      # [точка, путь] — куда идём
var path: Array = []
var target: Dictionary = {}
var visited: Array = []
var bad_pos := 0          # кадров, где Ной внутри препятствия
var bad_cam := 0          # кадров, где камера в постройке
var frames := 0
var prompts := {}         # подписи, которые показывались
var t := 0.0
var stuck_t := 0.0
var last_p := Vector3.ZERO
var started := false
var done := false
var log_lines: Array[String] = []
var _up: Array = []       # клавиши, которые отпустить в следующем кадре (E держим кадр: игра опрашивает её состояние)
var side_t := 0.0         # обход угла: столько секунд шагаем вбок
var side_sign := 1.0


func _ready() -> void:
	g = get_parent()
	process_mode = Node.PROCESS_MODE_ALWAYS
	process_priority = 20   # после режиссёра (10): он уже поправил шаг героя у стены — проверяем итог кадра
	Engine.time_scale = 2.0


func _process(delta: float) -> void:
	if done:
		return
	for k in _up:
		_hold(k, false)
	_up.clear()
	t += delta
	if g._ex != null and not started:
		started = true
		ex = g._ex
		for s in ex._spots():
			if ex._avail(s):
				plan.append(s)
		print("прогулка-тест: точек ", plan.size())
	if not started:
		if t > 120.0:
			_finish("не дошли до прогулки")
		return
	if g._ex == null:   # прогулка кончилась (вошли в дверь)
		_finish("")
		return
	if t > 900.0:
		_finish("время вышло")
		return
	var a: Actor = ex.a
	frames += 1
	if not ex.busy:
		if not ex._free(a.position.x, a.position.z):
			bad_pos += 1
			if bad_pos <= 12 or bad_pos % 100 == 0:   # где именно: какое препятствие или край
				var why := "край"
				for i in ex.d.get("block", []).size():
					var bb: Array = ex.d.block[i]
					var R := Explore.RAD
					if a.position.x > float(bb[0]) - R and a.position.x < float(bb[2]) + R and a.position.z > float(bb[1]) - R and a.position.z < float(bb[3]) + R:
						why = "препятствие %d %s" % [i, str(bb)]
				print("прогулка-тест: внутри у ", a.position, " (", why, ") шаг=", a.walking, " v=", snappedf(a.speed(), 0.01), " цель ", target.get("id", ""))
		var cp: Vector3 = g.cam.global_position
		for b in ex.d.get("cam_block", []):
			if (float(b[2]) - float(b[0])) * (float(b[3]) - float(b[1])) < 4.0:   # столбы и колонки — тонкие: камера
				continue                                                          # может скользнуть сквозь край, это не «в постройке»
			if cp.x > float(b[0]) + 0.05 and cp.x < float(b[2]) - 0.05 and cp.z > float(b[1]) + 0.05 and cp.z < float(b[3]) - 0.05 and cp.y < float(b[4]):
				bad_cam += 1
				if bad_cam <= 5:
					print("прогулка-тест: камера в постройке ", cp, " Ной ", a.position, " yaw ", snappedf(rad_to_deg(ex.yaw), 1))
	var pt: String = g.hud.prompt_lbl.text if g.hud.prompt_lbl.get_meta("on", false) else ""
	if pt != "":
		prompts[pt] = true
	if ex.busy or g.hud.choosing():
		_release_all()
		if g.hud.choosing():
			_tap(KEY_1)
		return
	if target.is_empty():
		_next_target()
		return
	# дошли: точка подсвечена — E
	if not ex._near.is_empty() and String(ex._near.id) == String(target.id):
		_release_all()
		_hold(KEY_E, true)
		_up.append(KEY_E)
		visited.append(String(target.id))
		target = {}
		return
	# идём по пути: клавиши — относительно камеры
	var w: Vector2 = ex._where(target)
	var me := Vector2(a.position.x, a.position.z)
	while not path.is_empty() and me.distance_to(path[0]) < 0.45:
		path.pop_front()
	var goal: Vector2 = path[0] if not path.is_empty() else w
	var dir := (goal - me).normalized()
	if side_t > 0.0:   # упёрлись в угол — полсекунды вбок (по очереди вправо и влево), потом снова к цели
		side_t -= delta
		dir = Vector2(-dir.y, dir.x) * side_sign
	var f := Vector2(sin(ex.yaw), cos(ex.yaw))
	var r3 := Vector3(f.x, 0, f.y).cross(Vector3.UP)
	var r := Vector2(r3.x, r3.z)
	var fy := dir.dot(f)
	var rx := dir.dot(r)
	_hold(KEY_W, fy > 0.38)
	_hold(KEY_S, fy < -0.38)
	_hold(KEY_D, rx > 0.38)
	_hold(KEY_A, rx < -0.38)
	# застряли — новый путь от текущего места
	if a.position.distance_to(last_p) < 0.004:
		stuck_t += delta
	else:
		stuck_t = maxf(0.0, stuck_t - delta * 0.5)
	last_p = a.position
	if stuck_t > 0.6 and side_t <= 0.0:
		side_t = 0.7
		side_sign = -side_sign
	if stuck_t > 6.0:
		print("прогулка-тест: застрял у ", a.position, " → ", target.id)
		stuck_t = 0.0
		path = ex._path(me, w)
		if path.size() <= 1:   # путь не нашли — точку пропускаем (это ошибка)
			log_lines.append("не дошёл до " + String(target.id))
			target = {}


func _next_target() -> void:
	while not plan.is_empty():
		var s: Dictionary = plan.pop_front()
		if s.get("exit", false) and not plan.is_empty():   # выход — последним
			plan.append(s)
			continue
		if not ex._avail(s):
			continue
		target = s
		var a: Actor = ex.a
		path = ex._path(Vector2(a.position.x, a.position.z), ex._where(s))
		print("прогулка-тест: иду к ", s.id, " (", path.size(), " отрезков)")
		return
	_finish("план кончился, а прогулка — нет")


func _hold(k: Key, on: bool) -> void:
	if bool(held.get(k, false)) == on:
		return
	held[k] = on
	var e := InputEventKey.new()
	e.keycode = k
	e.physical_keycode = k
	e.pressed = on
	Input.parse_input_event(e)


func _release_all() -> void:
	for k in held.keys():
		_hold(k, false)


func _tap(k: Key) -> void:
	for on in [true, false]:
		var e := InputEventKey.new()
		e.keycode = k
		e.physical_keycode = k
		e.pressed = on
		Input.parse_input_event(e)


func _finish(err: String) -> void:
	done = true
	_release_all()
	var ok := err == "" and bad_pos == 0 and bad_cam == 0 and log_lines.is_empty()
	print("прогулка-тест: %s; точки %s; кадров %d, Ной в препятствии %d, камера в постройке %d; подсказки: %s; %s%s" % [
		"ОК" if ok else "ОШИБКА", ",".join(visited), frames, bad_pos, bad_cam, "; ".join(prompts.keys()),
		err, (" " + "; ".join(log_lines)) if not log_lines.is_empty() else ""])
	get_tree().quit(0 if ok else 1)
