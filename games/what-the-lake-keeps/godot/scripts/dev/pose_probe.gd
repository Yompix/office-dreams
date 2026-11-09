extends SceneTree
## Наклон корпуса и взгляд головы в клипах (в системе героя): + корпус вперёд, + голова вниз. Градусы.
##   godot --headless --path godot --script res://scripts/dev/pose_probe.gd -- [герой] [клип …]
var _a: Actor
var _f := 0
var _clips: Array = []


func _init() -> void:
	var args := Array(OS.get_cmdline_user_args())
	var who: String = args[0] if args.size() > 0 else "noah"
	_clips = args.slice(1) if args.size() > 1 else ["Idle", "Walk", "Rk_Walk", "Walk_Carry"]
	_a = Actor.make(who, who)
	get_root().add_child(_a)


func _process(_d: float) -> bool:
	_f += 1
	if _f < 3:
		return false
	var sk := _a.skel
	var head := sk.find_bone("head")
	sk.reset_bone_poses()
	sk.force_update_all_bone_transforms()
	var rest_head := sk.get_bone_global_pose(head).basis.orthonormalized()
	var fwd_local := rest_head.inverse() * Vector3(0, 0, 1)   # какая ось головы смотрит вперёд в покое
	var rp := func(n: String) -> Vector3:
		return sk.get_bone_global_pose(sk.find_bone(n)).origin
	print("покой: таз ", rp.call("pelvis"), " грудь ", rp.call("spine_03"), " голова ", rp.call("head"), " стопа ", rp.call("foot_r"), " носок ", rp.call("ball_r"), " кисть ", rp.call("hand_r"))
	print("скелет в герое: ", _a.skel.transform, "  модель: ", _a.model.transform if _a.model else Transform3D())
	for c in _clips:
		if not _a.ap.has_animation(c):
			print(c, ": нет клипа")
			continue
		var an := _a.ap.get_animation(c)
		var leans := []
		var pitches := []
		var hands := []
		for i in 8:
			var tt := an.length * float(i) / 8.0
			_a.ap.play(c)
			_a.ap.seek(tt, true)
			_a.ap.advance(0.0)
			sk.force_update_all_bone_transforms()
			var pel := sk.get_bone_global_pose(sk.find_bone("pelvis")).origin
			var sp3 := sk.get_bone_global_pose(sk.find_bone("spine_03")).origin
			var nk := sk.get_bone_global_pose(sk.find_bone("neck_01")).origin
			var d := (sp3 - pel)
			leans.append(rad_to_deg(atan2(d.z, d.y)))
			var hf := sk.get_bone_global_pose(head).basis.orthonormalized() * fwd_local
			pitches.append(rad_to_deg(atan2(-hf.y, Vector2(hf.x, hf.z).length())))
			var hr := sk.get_bone_global_pose(sk.find_bone("hand_r")).origin
			hands.append(hr)
		var avg := func(a: Array) -> float:
			var s := 0.0
			for v in a:
				s += float(v)
			return s / maxf(a.size(), 1)
		print("%-12s корпус %+5.1f°  голова %+5.1f° (вниз +)  кисть пр. y=%.2f z=%.2f" % [c, avg.call(leans), avg.call(pitches), (hands[0] as Vector3).y, (hands[0] as Vector3).z])
	quit()
	return true
