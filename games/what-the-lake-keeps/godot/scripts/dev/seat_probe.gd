extends SceneTree
## Поза «сидя» у героя: где колени и стопы относительно таза (в системе героя: +Z вперёд, +Y вверх).
##   godot --headless --path godot --script res://scripts/dev/seat_probe.gd -- герой [клип]
var _a: Actor
var _f := 0
var _clip := "Sitting_Idle"


func _init() -> void:
	var args := Array(OS.get_cmdline_user_args())
	var who: String = args[0] if args.size() > 0 else "agnes"
	if args.size() > 1:
		_clip = args[1]
	_a = Actor.make(who, who)
	get_root().add_child(_a)


func _process(_d: float) -> bool:
	_f += 1
	if _f < 3:
		return false
	Ch3.hold(_a, _clip, 0.0)
	var sk := _a.skel
	sk.force_update_all_bone_transforms()
	var s_in := _a.global_transform.affine_inverse() * sk.global_transform
	for k in ["pelvis", "thigh_l", "calf_l", "foot_l", "thigh_r", "calf_r", "foot_r", "spine_03", "head"]:
		print("%-9s %s" % [k, (s_in * sk.get_bone_global_pose(sk.find_bone(k)).origin).snapped(Vector3.ONE * 0.01)])
	quit()
	return true
