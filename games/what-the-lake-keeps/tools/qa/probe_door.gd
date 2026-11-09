extends SceneTree
## Дверь магазина «24» глазами игрока: Ной открывает дверь (как по E), потом «клавишей W» (камера поворачивается
## к следующей точке) идёт по маршрутам — внутрь, в обход створки, прямо в открытую створку, наискось к петле, наружу.
## Каждый кадр — расстояние от оси тела до полотна створки (тело — цилиндр 0,22 м): лог DOOR и итог по маршрутам.
var stage := 0
var t := 0.0
var g
var ex
var key := InputEventKey.new()
var routes := [
	["внутрь", [Vector2(21.9, -1.9), Vector2(22.62, -3.4), Vector2(22.4, -6.0)]],
	["ждать, пока закроется", [], 5.0],
	["изнутри к выходу", [Vector2(22.55, -4.0), Vector2(22.3, -2.3), Vector2(21.9, -2.0), Vector2(19.4, -2.0)]],
	["отойти от двери", [Vector2(17.4, -1.4)]],
	["ждать", [], 5.0],
	["снова внутрь", [Vector2(19.6, -2.0), Vector2(21.9, -1.95)]],
	["в открытую створку", [Vector2(21.55, -3.6)]],
	["наружу", [Vector2(21.9, -1.95), Vector2(17.4, -1.4)]],
	["назад в закрывающуюся", [Vector2(23.5, -2.0)]],
]
var ri := 0
var wi := 0
var rt := 0.0
var stats := {}
var shots := 0

func _init() -> void:
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))
	key.physical_keycode = KEY_W
	key.keycode = KEY_W

func _press(on: bool) -> void:
	key.pressed = on
	Input.parse_input_event(key)

func _process(d: float) -> bool:
	g = current_scene
	if g == null or not ("data" in g) or g.data == null:
		return false
	if stage == 0:
		if g.data.has("explore") and g.data.explore.has("st24"):
			g.data.explore["st24"]["intro"] = []   # без заправки (действие «держать») — сразу к ходьбе
			g.autoplay = false   # сцена до прогулки — сама (--autoplay --realtime пропускает меню), прогулка — «руками»
			stage = 1
		return false
	if stage == 1:
		if g._ex == null:
			return false
		ex = g._ex
		Engine.time_scale = 1.0   # вступление сцены — ускоренно (автопрогон), сама проверка — в обычном темпе
		t += d
		if t < 2.5:
			return false
		stage = 2
		_open.call_deferred()
		return false
	if stage == 3:
		_drive(d)
	return false

func _open() -> void:
	var a = g.actors.noah
	a.stop_walk()
	a.position = Vector3(20.05, 0.0, -1.95)
	a.rotation = Vector3(0, deg_to_rad(90.0), 0)
	for dr in ex.d.get("doors", []):
		if String(dr.id) == "shop_door":
			var ds: Dictionary = (dr as Dictionary).duplicate()
			ds["door"] = true
			await ex._use(ds)
	print("DOOR opened leaf=", ex._leaf.has("shop_door"), " closer=", ex._closer.has("shop_door"), " frame=", Engine.get_frames_drawn())
	stage = 3

func _drive(d: float) -> void:
	var a = g.actors.noah
	var p := Vector2(a.position.x, a.position.z)
	var ang: float = ex._closer["shop_door"].piv.rotation.y if ex._closer.has("shop_door") else 0.0
	var ld: float = ex._leaf_dist("shop_door", p) if ex._leaf.has("shop_door") else 9.0
	if ri >= routes.size():
		_press(false)
		print("DOOR SUMMARY")
		for k in stats:
			print("DOOR route %s: min %.3f m, кадров ближе 0,22: %d, дошёл: %s" % [k, stats[k].min, stats[k].pen, stats[k].ok])
		quit()
		return
	var r: Array = routes[ri]
	var nm := "%d %s" % [ri, r[0]]
	if not stats.has(nm):
		stats[nm] = {"min": 9.0, "pen": 0, "ok": true}
	stats[nm].min = minf(stats[nm].min, ld)
	if ld < Explore.RAD:
		stats[nm].pen += 1
	rt += d
	print("DOOR %s t=%.2f p=(%.2f,%.2f) leaf=%.1f° dist=%.3f" % [nm, rt, p.x, p.y, rad_to_deg(ang), ld])
	if shots < 40 and int(rt * 4.0) != int((rt - d) * 4.0):   # 4 кадра в секунду
		shots += 1
		root.get_texture().get_image().save_png("%s/door_%02d.png" % [OS.get_cmdline_user_args()[OS.get_cmdline_user_args().find("--snapdir") + 1], shots])
	var pts: Array = r[1]
	if pts.is_empty():
		_press(false)
		if rt > float(r[2]):
			ri += 1; wi = 0; rt = 0.0
		return
	var w: Vector2 = pts[wi]
	var to := w - p
	if to.length() < 0.12 or rt > 6.0:
		if rt > 6.0:
			stats[nm].ok = false   # упёрся (створка или стена не пускает)
		wi += 1
		rt = 0.0 if wi >= pts.size() else rt
		if wi >= pts.size():
			ri += 1; wi = 0; rt = 0.0
			_press(false)
		return
	ex.yaw = atan2(to.x, to.y)
	ex._orbit_t = 0.0
	_press(true)
