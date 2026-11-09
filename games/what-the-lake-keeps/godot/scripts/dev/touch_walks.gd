extends SceneTree
## Ходьба пальцем по каждой прогулке (телефон): реплики пропускаются, на выборе — первый вариант; как только прогулка
## началась — палец слева ведём вперёд, вправо, назад, влево (по 1,5 с) и смотрим, сдвинулся ли Ной; если нет — почему.
##   xvfb-run -a -s "-screen 0 960x540x24" $GODOT --rendering-method gl_compatibility --resolution 960x540 --path godot \
##     --script res://scripts/dev/touch_walks.gd -- --scene c4_s3 --touch [--flags …]
var t := 0.0
var total := 0.0
var step := 0
var p0 := Vector3.ZERO
var best := 0.0
var dir_i := 0
var why := ""
const DIRS := [Vector2(0, -110), Vector2(110, 0), Vector2(0, 110), Vector2(-110, 0)]


func _init() -> void:
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))


func _w(p: Vector2) -> Vector2:
	var ws := Vector2(DisplayServer.window_get_size())
	var vs: Vector2 = root.get_visible_rect().size
	return p * (ws / vs)


func _touch(i: int, p: Vector2, on: bool) -> void:
	var e := InputEventScreenTouch.new()
	e.index = i
	e.position = _w(p)
	e.pressed = on
	Input.parse_input_event(e)


func _drag(i: int, p: Vector2) -> void:
	var e := InputEventScreenDrag.new()
	e.index = i
	e.position = _w(p)
	Input.parse_input_event(e)


func _done(ok: bool, msg: String) -> void:
	var args := OS.get_cmdline_user_args()
	var sc: String = args[args.find("--scene") + 1] if "--scene" in args else "?"
	print("ХОДЬБА %s %s: %s" % ["ОК " if ok else "НЕТ", sc, msg])
	quit()


func _process(d: float) -> bool:
	var g = current_scene
	if g == null or not ("_ex" in g) or g.hud == null:
		return false
	t += d
	total += d
	if total > 240.0:
		_done(false, "прогулка не началась за 4 мин (шаг %d)" % step)
		return true
	var vs: Vector2 = root.get_visible_rect().size
	var home := Vector2(250, vs.y - 250)
	match step:
		0:   # ждём прогулку: реплики — дальше, на выборе — первый вариант
			if g._ex != null and not g._ex.busy and not g.hud.choosing() and t > 1.0:
				p0 = (g.actors.noah as Node3D).global_position
				step = 1
				t = 0.0
				_touch(0, home, true)
			elif g.hud.choosing():
				g.hud.pick(0)
			else:
				g._skip = true
		1:   # тянем палец в сторону DIRS[dir_i]
			var k := minf(1.0, t * 3.0)
			_drag(0, home + DIRS[dir_i] * k)
			if t > 0.5 and why == "":
				if not TouchCtl.enabled:
					why = "TouchCtl выключен"
				elif not g.hud.choosing() and g._ex != null and g._ex.busy:
					why = "прогулка занята (busy)"
				elif TouchCtl.move.length() < 0.2:
					why = "джойстик не принял палец (move=%.2f)" % TouchCtl.move.length()
			if t > 1.5:
				_touch(0, home + DIRS[dir_i], false)
				var nd: Node3D = g.actors.noah
				best = maxf(best, nd.global_position.distance_to(p0))
				p0 = nd.global_position
				dir_i += 1
				t = 0.0
				if dir_i >= DIRS.size() or best > 0.8:
					_done(best > 0.8, "сдвиг %.2f м%s" % [best, ("" if best > 0.8 or why == "" else " — " + why)])
					return true
				_touch(0, home, true)
	return false
