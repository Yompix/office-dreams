extends SceneTree
## Клипы: таз (смещение по ходу — «корневое движение»), совпадение начала и конца (шов петли), скорость шага по стопам.
##   godot --headless --path godot --script res://scripts/dev/clip_probe.gd
var _a: Actor
var _f := 0


func _init() -> void:
	_a = Actor.make("noah", "noah")
	get_root().add_child(_a)


func _process(_d: float) -> bool:
	_f += 1
	if _f < 3:
		return false
	var a := _a
	var sk := a.skel
	var pel := sk.find_bone("pelvis")
	var fl := sk.find_bone("ball_l")
	var fr := sk.find_bone("ball_r")
	for c in a.ap.get_animation_list():
		var an := a.ap.get_animation(c)
		a.ap.play(c)
		var p0 := Vector3.ZERO
		var pmin := Vector3(1e9, 1e9, 1e9)
		var pmax := -pmin
		var steps := 0
		var first := []
		var last := []
		var n := int(an.length / 0.02)
		var foot_min_y := 1e9
		var hs: Array = []   # [высота, скорость] по кадрам для левой стопы
		var foot_travel := 0.0
		var prev_l := Vector3.ZERO
		var prev_r := Vector3.ZERO
		for i in n + 1:
			if i == 0:
				a.ap.seek(0.0, true)
				a.ap.advance(0.0)
			else:
				a.ap.advance(0.02)
			sk.force_update_all_bone_transforms()
			var pp := sk.get_bone_global_pose(pel).origin
			pmin = pmin.min(pp)
			pmax = pmax.max(pp)
			var l := sk.get_bone_global_pose(fl).origin
			var r := sk.get_bone_global_pose(fr).origin
			if i > 0:
				hs.append([l.y, Vector2(l.x - prev_l.x, l.z - prev_l.z).length() / 0.02])
			if i > 0:   # путь стоп относительно таза (по земле): скорость шага клипа
				foot_travel += Vector2(l.x - prev_l.x, l.z - prev_l.z).length() + Vector2(r.x - prev_r.x, r.z - prev_r.z).length()
			prev_l = l
			prev_r = r
			if i == 0:
				p0 = pp
				for b in sk.get_bone_count():
					first.append(sk.get_bone_pose_rotation(b))
			if i == n:
				for b in sk.get_bone_count():
					last.append(sk.get_bone_pose_rotation(b))
		var ymin := 1e9
		for e in hs:
			ymin = minf(ymin, e[0])
		var st: Array = []
		for e in hs:
			if e[0] < ymin + 0.015:
				st.append(e[1])
		st.sort()
		var v_st: float = st[st.size() / 2] if st.size() > 0 else 0.0
		var seam := 0.0
		for b in sk.get_bone_count():
			seam = maxf(seam, rad_to_deg((first[b] as Quaternion).angle_to(last[b])))
		var drift := pmax - pmin
		print("%-20s %5.2fс  таз ход X %.2f Y %.2f Z %.2f  шов петли %5.1f°  опора %.2f м/с" % [c, an.length, drift.x, drift.y, drift.z, seam, v_st])
	quit()
	return true
