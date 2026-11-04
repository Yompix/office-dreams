extends SceneTree
## Посадка по всей игре: автопилот маршрута (ключи как у story_tests.py), раз в 0,25 с для каждого сидящего героя (не в
## машине) — насколько низ тела ниже верха сиденья под ним (проваливается, «−») или выше (висит, «+»); сиденья под
## тазом нет — отдельной строкой. Итог — худшее по каждой сцене и герою.
##   $GODOT --headless --path godot/lake --script res://scripts/dev/seat_audit.gd -- --autoplay --fast --pick 0,1,…
##   одна сцена: … -- --autoplay --scene c3_s4 --audit-secs 60
var t := 0.0
var worst := {}   # "сцена/герой/клип" → [мин, макс, сколько замеров]
var none := {}    # "сцена/герой/клип" → сколько замеров без сиденья
var info := {}    # "сцена/герой/клип" → подробности первого замера
var dbg := {}     # "сцена/герой/клип" → точки замера, когда «провал»


func _init() -> void:
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))


var total := 0.0
var limit := -1.0   # --audit-secs N: выйти через N с (проверка одной сцены: --scene X)


func _process(d: float) -> bool:
	if limit < 0.0:
		var args := OS.get_cmdline_user_args()
		limit = float(args[args.find("--audit-secs") + 1]) if "--audit-secs" in args else 1e9
		Seats.debug = "--settle-log" in args
	total += d
	if total > limit:
		quit()
		return true
	var g = current_scene
	if g == null or not ("actors" in g):
		return false
	t += d
	if t < 0.25:
		return false
	t = 0.0
	var sid := String(g.get("_scene_id")) if g.get("_scene_id") != null else "?"
	for k in g.actors:
		var a = g.actors[k]
		if not (a is Actor) or not is_instance_valid(a) or not a.is_inside_tree() or not a.is_visible_in_tree():
			continue
		if not (a.clip in Actor.SEAT) or a.clip in ["Crouch_Idle", "Fixing_Kneeling", "Driving"] or a._settle_t >= 0.0 or a.has_meta("sit_pending"):
			continue
		var p = a.get_parent()
		var in_car := false
		while p != null:
			if p is Car or p is Actor:   # в машине или у другого на спине
				in_car = true
				break
			p = p.get_parent()
		if in_car:
			continue
		var sk: Skeleton3D = a.skel
		var sg := sk.global_transform
		var pel: Vector3 = sg * sk.get_bone_global_pose(sk.find_bone("pelvis")).origin
		var bs: Basis = a.global_transform.basis.orthonormalized()
		var hit: Array = Seats._top(g, pel, pel.y - 0.6, pel.y + 0.12, bs.x, -bs.z)
		var top := float(hit[0])
		var key := "%s/%s/%s" % [sid, k, a.clip]
		if is_nan(top):
			none[key] = int(none.get(key, 0)) + 1
			continue
		var gap: float = (pel.y - a.seat_depth()) - top
		if gap < -0.03 and not dbg.has(key):
			var l: Array = Seats.last
			var parts := []
			for j in 4:
				var m = l[1][j]
				parts.append("%s %.2f «%s» (%.2f,%.2f)" % [["таз", "бок+", "бок−", "зад"][j], l[0][j], String(m.name) if m else "-", l[2][j].x, l[2][j].z])
			dbg[key] = " | ".join(parts) + " | корень (%.2f,%.2f) взгляд (%.2f,%.2f)" % [a.global_position.x, a.global_position.z, bs.z.x, bs.z.z]
		var w: Array = worst.get(key, [gap, gap, 0])
		worst[key] = [minf(w[0], gap), maxf(w[1], gap), int(w[2]) + 1]
		if not info.has(key):
			var feet := minf((sg * sk.get_bone_global_pose(sk.find_bone("foot_l")).origin).y, (sg * sk.get_bone_global_pose(sk.find_bone("foot_r")).origin).y)
			var mi: Node = hit[1]
			info[key] = "таз %.2f стопы %.2f глубина %.2f, сиденье «%s» %.2f%s; таз xz (%.2f,%.2f) корень (%.2f,%.2f)" % [pel.y, feet, a.seat_depth(), (String(mi.get_parent().name) + "/" + String(mi.name)) if mi else "?", top, " (свой табурет)" if is_instance_valid(a.stool) else "", pel.x, pel.z, a.global_position.x, a.global_position.z]
	return false


func _finalize() -> void:
	var keys := worst.keys()
	keys.sort()
	for k in keys:
		var w: Array = worst[k]
		var flag := "ПРОВАЛ" if w[0] < -0.03 else ("ВИСИТ" if w[1] > 0.05 else "ок")
		print("SEAT %-7s %-45s низ−сиденье: %+.3f…%+.3f м (%d)  %s" % [flag, k, w[0], w[1], w[2], info.get(k, "")])
	for k in dbg:
		print("SEATDBG %s: %s" % [k, dbg[k]])
	for k in none:
		print("SEAT НЕТСИД %-45s замеров без сиденья под тазом: %d" % [k, none[k]])
