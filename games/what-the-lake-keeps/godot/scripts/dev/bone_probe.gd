extends SceneTree
## Где кости героя на планах нужной сцены (мир) — для проверки посадки, рук на руле, ноши:
##   godot --headless --fixed-fps 30 --path godot --script res://scripts/dev/bone_probe.gd -- --autoplay --fast \
##     --pick … --stop s6 --probe s5 --who danny [--bones pelvis,calf_l,foot_l] [--delay 20]
## У водителя в машине дополнительно: ладони в координатах руля (радиус от оси и смещение от плоскости обода).
var want := ""
var who := ""
var bones := ["pelvis", "thigh_l", "calf_l", "foot_l", "ball_l", "calf_r", "foot_r", "ball_r", "hand_l", "hand_r"]
var delay := 20
var _key := ""
var _wait := 0
var _done := {}


func _init() -> void:
	var args := OS.get_cmdline_user_args()
	if "--probe" in args:
		want = args[args.find("--probe") + 1]
	if "--who" in args:
		who = args[args.find("--who") + 1]
	if "--bones" in args:
		bones = Array(String(args[args.find("--bones") + 1]).split(","))
	if "--delay" in args:
		delay = int(args[args.find("--delay") + 1])
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))


func _process(_d: float) -> bool:
	var g = current_scene
	if g == null or not ("_scene_id" in g):
		return false
	var sid := String(g.get("_scene_id"))
	if not sid.begins_with(want):
		return false
	var key := sid + "/" + String(g.get("_shot"))
	if key != _key:
		_key = key
		_wait = delay
		return false
	if _done.has(key):
		return false
	_wait -= 1
	if _wait > 0:
		return false
	_done[key] = true
	if not g.actors.has(who) or not is_instance_valid(g.actors[who]):
		print("BONES %s нет героя %s" % [key, who])
		return false
	var a: Actor = g.actors[who]
	var sk := a.skel
	var org := Vector3.ZERO
	if "--house" in OS.get_cmdline_user_args():   # координаты дома Агнес (как в Ch3.ag: x, y от пола дома, z)
		org = Village.ag(0, 0, 0)
	var cam: Camera3D = g.cam
	var out := "BONES %s %s корень %s клип %s камера %s -> %s" % [key, who, (a.global_position - org).snapped(Vector3.ONE * 0.01), a.ap.current_animation,
		(cam.global_position - org).snapped(Vector3.ONE * 0.01), (-cam.global_transform.basis.z).snapped(Vector3.ONE * 0.01)]
	for bn in bones:
		var bi := sk.find_bone(bn)
		if bi < 0:
			continue
		out += " | %s %s" % [bn, (sk.global_transform * sk.get_bone_global_pose(bi).origin - org).snapped(Vector3.ONE * 0.01)]
	var car = g.scene.car if g.scene != null and "car" in g.scene else null
	if car != null and is_instance_valid(car) and a.get_parent() == car and "wheel" in car:
		var wh: Node3D = car.wheel
		for s in ["_l", "_r"]:
			var h := sk.global_transform * sk.get_bone_global_pose(sk.find_bone("hand" + s)).origin
			var m := sk.global_transform * sk.get_bone_global_pose(sk.find_bone("middle_01" + s)).origin
			var lp := wh.to_local((h + m) * 0.5)
			var lw := wh.to_local(h)
			var lh := wh.to_local((h + m) * 0.5 + a.palm_normal(s) * 0.025)   # пустота кулака
			out += " || ладонь%s в руле %s (радиус %.3f, от плоскости %.3f) кулак (радиус %.3f, от плоскости %.3f) запястье (радиус %.3f)" % [s, lp.snapped(Vector3.ONE * 0.001), Vector2(lp.x, lp.z).length(), lp.y, Vector2(lh.x, lh.z).length(), lh.y, Vector2(lw.x, lw.z).length()]
		out += " || масштаб руля %.3f" % wh.transform.basis.get_scale().x
	print(out)
	return false
