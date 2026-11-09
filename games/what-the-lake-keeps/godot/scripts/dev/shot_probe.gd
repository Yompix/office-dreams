extends SceneTree
## Где стоят головы героев и камера в координатах машины на нужных планах:
##   godot --headless --path godot --script res://scripts/dev/shot_probe.gd -- --autoplay --fast --pick … --probe c2_s6_road
var want := ""
var seen := {}


func _init() -> void:
	var args := OS.get_cmdline_user_args()
	if "--probe" in args:
		want = args[args.find("--probe") + 1]
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))


func _process(_d: float) -> bool:
	var g = current_scene
	if g == null or not ("_scene_id" in g):
		return false
	var sid := String(g.get("_scene_id"))
	var shot := String(g.get("_shot"))
	if sid != want or seen.has(shot):
		return false
	seen[shot] = true
	if want.begins_with("c3") and g.actors.has("eva"):   # дом Агнес: где у Евы корень, голова и стопы (координаты дома)
		var ev: Actor = g.actors.eva
		var esk := ev.skel
		var hp := esk.global_transform * esk.get_bone_global_pose(esk.find_bone("head")).origin
		var fl := esk.global_transform * esk.get_bone_global_pose(esk.find_bone("foot_l")).origin
		var o := Village.ag(0, 0, 0)
		print("PROBE %s %s ева корень %s голова %s стопа %s клип %s" % [sid, shot, (ev.global_position - o).snapped(Vector3.ONE * 0.01), (hp - o).snapped(Vector3.ONE * 0.01), (fl - o).snapped(Vector3.ONE * 0.01), ev.ap.current_animation])
		return false
	var car: Node3D = g.scene.car
	var out := "PROBE %s %s cam %s" % [sid, shot, car.to_local((g.cam as Camera3D).global_position)]
	for k in g.actors:
		var a = g.actors[k]
		if a is Actor and is_instance_valid(a) and (a as Actor).get_parent() == car:
			var sk: Skeleton3D = (a as Actor).skel
			var hb := sk.global_transform * sk.get_bone_global_pose(sk.find_bone("head"))
			var fwd := car.global_transform.basis.inverse() * hb.basis.z
			var la = (a as Actor).look_actor
			out += " | %s голова %s взгляд %s клип %s поворот %s смотрит_на %s точка %s" % [k, car.to_local(hb.origin).snapped(Vector3.ONE * 0.01), fwd.normalized().snapped(Vector3.ONE * 0.01), (a as Actor).ap.current_animation, (a as Actor).rotation_degrees.snapped(Vector3.ONE), la.name if la != null and is_instance_valid(la) else "-", (a as Actor).look_target]
	# что между камерой и головой героя плана: расстояние от линии камера→голова до голов других (м)
	if g.actors.has(shot):
		var cp: Vector3 = (g.cam as Camera3D).global_position
		var tsk: Skeleton3D = (g.actors[shot] as Actor).skel
		var th := tsk.global_transform * tsk.get_bone_global_pose(tsk.find_bone("head")).origin
		for k in g.actors:
			var a2 = g.actors[k]
			if k == shot or not (a2 is Actor) or not is_instance_valid(a2):
				continue
			var sk2: Skeleton3D = (a2 as Actor).skel
			for bn in ["head", "spine_03", "hand_l", "hand_r"]:
				var bi := sk2.find_bone(bn)
				if bi < 0:
					continue
				var bp := sk2.global_transform * sk2.get_bone_global_pose(bi).origin
				var ab := th - cp
				var t := clampf((bp - cp).dot(ab) / ab.length_squared(), 0.0, 1.0)
				var d := bp.distance_to(cp + ab * t)
				if d < 0.2 and t > 0.02 and t < 0.98:
					out += " || ЗАСЛОН %s.%s в %.2f м от луча (доля %.2f)" % [k, bn, d, t]
	# какие сетки (по габариту) пересекают луч камера → голова героя плана
	if g.actors.has(shot):
		var cp2: Vector3 = (g.cam as Camera3D).global_position
		var tsk2: Skeleton3D = (g.actors[shot] as Actor).skel
		var th2 := tsk2.global_transform * tsk2.get_bone_global_pose(tsk2.find_bone("head")).origin
		for n in root.find_children("*", "MeshInstance3D", true, false):
			var mi := n as MeshInstance3D
			if not mi.is_visible_in_tree() or mi.mesh == null or (mi.layers & (g.cam as Camera3D).cull_mask) == 0:
				continue
			var inv := mi.global_transform.affine_inverse()
			var a0 := inv * cp2
			var a1 := inv * th2
			var bb := mi.get_aabb()
			var d := a1 - a0
			var t0 := 0.0
			var t1 := 0.97
			var ok := true
			for ax in 3:
				if absf(d[ax]) < 1e-6:
					if a0[ax] < bb.position[ax] or a0[ax] > bb.end[ax]:
						ok = false
				else:
					var ta := (bb.position[ax] - a0[ax]) / d[ax]
					var tb := (bb.end[ax] - a0[ax]) / d[ax]
					t0 = maxf(t0, minf(ta, tb))
					t1 = minf(t1, maxf(ta, tb))
			if ok and t0 <= t1 and t1 > 0.03:
				var owner_name := String(mi.get_parent().name) if mi.get_parent() else ""
				out += " || СЕТКА %s/%s (с %.2f до %.2f луча)" % [owner_name, mi.name, t0, t1]
	print(out)
	return false
