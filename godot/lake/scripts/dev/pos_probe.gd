extends SceneTree
## Где стоят герои в сцене: игра идёт сама до сцены --at, через --wait кадров печатает положение героев (таз),
## клип, родителя. godot --headless --fixed-fps 30 --path godot/lake --script res://scripts/dev/pos_probe.gd -- --autoplay --fast --pick … --at c3_s4
var at := ""
var wait := 20
var n := -1

func _init() -> void:
	var args := OS.get_cmdline_user_args()
	at = args[args.find("--at") + 1]
	if "--wait" in args:
		wait = int(args[args.find("--wait") + 1])
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))

func _process(_d: float) -> bool:
	var g := current_scene
	if g == null or not ("_scene_id" in g):
		return false
	if String(g.get("_scene_id")) != at:
		return false
	n += 1
	if n < wait:
		return false
	print("POS сцена ", at, " план ", g.get("_shot"))
	var acts: Dictionary = g.get("actors")
	for k in acts:
		var a = acts[k]
		if not (a is Actor) or not is_instance_valid(a):
			continue
		var sk: Skeleton3D = (a as Actor).skel
		var pel := sk.global_transform * sk.get_bone_global_pose(sk.find_bone("pelvis")).origin
		var head := sk.global_transform * sk.get_bone_global_pose(sk.find_bone("head")).origin
		print("POS %-8s вид %s таз (%.2f %.2f %.2f) голова (%.2f %.2f %.2f) клип %s родитель %s" % [k, (a as Actor).is_visible_in_tree(),
			pel.x, pel.y, pel.z, head.x, head.y, head.z, (a as Actor).ap.current_animation, (a as Actor).get_parent().name])
	return true
