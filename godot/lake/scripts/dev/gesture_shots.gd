extends SceneTree
## Жесты кадрами (#189): на заправке (--station) герою по очереди даём жест и через t секунд снимаем его спереди и сбоку.
##   xvfb-run -a -s "-screen 0 1280x720x24" $GODOT --rendering-driver vulkan --path godot/lake --script \
##     res://scripts/dev/gesture_shots.gd -- --station --who noah --list look_hands,pockets,nod,sleeve,chest,give,behind \
##     --snapdir DIR [--at 1.1]
var t := 0.0
var step := -1
var list: PackedStringArray = []
var who := "noah"
var at := 1.1
var dir := "user://"
var cam: Camera3D
var k := 0
var phase := 0


func _init() -> void:
	var a := OS.get_cmdline_user_args()
	if "--list" in a:
		list = String(a[a.find("--list") + 1]).split(",")
	if "--who" in a:
		who = a[a.find("--who") + 1]
	if "--at" in a:
		at = float(a[a.find("--at") + 1])
	if "--snapdir" in a:
		dir = a[a.find("--snapdir") + 1]
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))


func _process(d: float) -> bool:
	var g = current_scene
	if g == null or not ("actors" in g) or not g.actors.has(who):
		return false
	t += d
	var a: Actor = g.actors[who]
	if step == -1:
		if t < 2.5:
			return false
		if g.hud:
			g.hud.visible = false
		cam = Camera3D.new()
		g.add_child(cam)
		cam.current = true
		Actor.view_cam = cam   # герои считают «в кадре ли я» по этой камере (иначе — по камере игры, и жесты не считаются)
		var car: Node3D = g.scene.car
		if car:   # на открытое место: в 4,5 м сбоку от машины, лицом от неё
			var cx: Vector3 = car.global_transform.basis.x
			a.global_position = car.global_position - cx * 4.5
			a.global_position.y = car.global_position.y
			a.look_at(a.global_position - cx, Vector3.UP, true)
			a.placed()
		a.play("Idle", 0.0, 0.0)
		a.look_target = null
		step = 0
		t = 0.0
		_start(g, a)
		return false
	if k >= list.size():
		quit()
		return true
	var hp: Vector3 = a.head_pos()
	var fwd := a.global_transform.basis.z
	fwd.y = 0.0
	fwd = fwd.normalized()
	var side := fwd.cross(Vector3.UP).normalized()
	if phase == 0 and t > at:
		cam.fov = 40.0
		cam.look_at_from_position(hp + fwd * 1.9 + side * 0.5 + Vector3(0, -0.1, 0), hp + Vector3(0, -0.35, 0))
		phase = 1
	elif phase == 1 and t > at + 0.12:
		get_root().get_texture().get_image().save_png("%s/%02d_%s_front.png" % [dir, k, list[k]])
		cam.look_at_from_position(hp + side * 1.9 + fwd * 0.6 + Vector3(0, -0.1, 0), hp + Vector3(0, -0.35, 0))
		phase = 2
	elif phase == 2 and t > at + 0.24:
		get_root().get_texture().get_image().save_png("%s/%02d_%s_side.png" % [dir, k, list[k]])
		k += 1
		phase = 0
		t = 0.0
		if k < list.size():
			_start(g, a)
	return false


func _start(g, a: Actor) -> void:
	var kind := String(list[k])
	var other: Actor = null
	for key in ["danny", "roman", "iris"]:
		if key != who and g.actors.has(key) and is_instance_valid(g.actors[key]):
			other = g.actors[key]
			break
	if kind in ["palm_to", "take_hand"] and other:   # второй герой — перед ним, на шаг
		var fwd := a.global_transform.basis.z
		fwd = Vector3(fwd.x, 0.0, fwd.z).normalized()
		if other.get_parent() != g.scene:
			other.reparent(g.scene, false)
		other.global_position = a.global_position + fwd * 0.6
		other.look_at(a.global_position, Vector3.UP, true)
		other.rotation = Vector3(0, other.rotation.y, 0)
		other.visible = true
		other.placed()
		other.play("Idle", 0.0, 0.0)
	a.do_gesture(kind, at + 1.0, other, "head" if kind == "palm_to" else "hand_l")
