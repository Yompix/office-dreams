extends SceneTree
## Детектор проникновений по ходу игры (без окна, автопилот): тело героя — капсулы по костям (голова, шея, грудь,
## живот, плечи, предплечья, бёдра, голени). Каждый кадр:
##   cam  — камера внутри тела видимого героя (глубина = насколько камера внутри капсулы);
##   pair — два героя друг в друге: голова/торс одного в голове/торсе другого глубже 6 см (кроме ноши: кто привязан к
##          скелету другого — на руках, на спине);
##   hand — кисть внутри своего торса глубже 4 см;
##   foot — стопа в полу: пятка (сустав стопы) или носок (подушка стопы) ниже своей высоты в позе покоя больше 3 см
##          (в покое подошва стоит на полу; корень героя — на полу его точки).
## По сценам и планам — сколько кадров с нарушением и самая большая глубина.
##   $GODOT --headless --fixed-fps 30 --path godot --script res://scripts/dev/clip_check.gd -- --autoplay --fast \
##     --pick 0,1,… --clipout файл.json
var out := "/tmp/lake_clip.json"
var rows := {}        # "сцена|план|вид|кто" → [кадров, наибольшая глубина]
var frames := 0
var dumped := false
var rest_h := {}      # "герой:кость" → высота кости над корнем героя в позе покоя, м

const SEG := [   # [кость, кость-конец или "", радиус (взрослый), часть тела]
	["head", "", 0.105, "head"], ["neck_01", "head", 0.06, "neck"],
	["pelvis", "spine_02", 0.14, "torso"], ["spine_02", "neck_01", 0.15, "torso"],
	["upperarm_l", "lowerarm_l", 0.055, "arm"], ["upperarm_r", "lowerarm_r", 0.055, "arm"],
	["lowerarm_l", "hand_l", 0.045, "arm"], ["lowerarm_r", "hand_r", 0.045, "arm"],
	["thigh_l", "calf_l", 0.08, "leg"], ["thigh_r", "calf_r", 0.08, "leg"],
	["calf_l", "foot_l", 0.06, "leg"], ["calf_r", "foot_r", 0.06, "leg"],
]


func _init() -> void:
	var args := OS.get_cmdline_user_args()
	if "--clipout" in args:
		out = args[args.find("--clipout") + 1]
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))


func _caps(a: Actor) -> Array:
	var sk := a.skel
	var res: Array = []
	var gp := func(b: String) -> Vector3:
		var i := sk.find_bone(b)
		return sk.global_transform * sk.get_bone_global_pose(i).origin if i >= 0 else Vector3.INF
	var head: Vector3 = gp.call("head")
	var foot: Vector3 = gp.call("foot_l")
	var k := 1.0
	if head != Vector3.INF and foot != Vector3.INF:
		k = clampf(head.distance_to(foot) / 1.55, 0.5, 1.3)   # ребёнок тоньше (по росту в текущей позе — грубо)
	for s in SEG:
		var p0: Vector3 = gp.call(String(s[0]))
		if p0 == Vector3.INF:
			continue
		var p1: Vector3 = p0 + Vector3(0, 0.08 * k, 0) if String(s[1]) == "" else gp.call(String(s[1]))
		if String(s[1]) == "":
			p0 = p1   # голова — шар над суставом
		if p1 == Vector3.INF:
			continue
		res.append([p0, p1, float(s[2]) * k, String(s[3])])
	return res


static func _seg_pt(p: Vector3, a: Vector3, b: Vector3) -> float:
	var ab := b - a
	var t := clampf((p - a).dot(ab) / maxf(ab.length_squared(), 1e-8), 0.0, 1.0)
	return p.distance_to(a + ab * t)


static func _seg_seg(a0: Vector3, a1: Vector3, b0: Vector3, b1: Vector3) -> float:
	var best := 1e9
	for i in 9:   # грубо: точки вдоль первого отрезка до второго
		var p := a0.lerp(a1, float(i) / 8.0)
		best = minf(best, _seg_pt(p, b0, b1))
	return best


func _hit(key: String, depth: float) -> void:
	var r: Array = rows.get(key, [0, 0.0])
	r[0] = int(r[0]) + 1
	r[1] = maxf(float(r[1]), depth)
	rows[key] = r


func _process(_d: float) -> bool:
	var g := current_scene
	if g == null or not ("actors" in g) or not ("cam" in g):
		return false
	var sid := String(g.get("_scene_id"))
	if sid == "":
		return false
	frames += 1
	var shot := String(g.get("_shot"))
	var cam: Camera3D = g.get("cam")
	var acts: Dictionary = g.get("actors")
	var vis: Array = []
	for k in acts:
		var a = acts[k]
		if a is Actor and is_instance_valid(a) and (a as Actor).is_visible_in_tree():
			vis.append([String(k), a, _caps(a)])
	var cp := cam.global_position
	for v in vis:
		for c in (v[2] as Array):
			var d := _seg_pt(cp, c[0], c[1])
			if d < float(c[2]) - 0.01:
				_hit("%s|%s|cam|%s:%s" % [sid, shot, v[0], c[3]], float(c[2]) - d)
		# стопы в полу: герой стоит на своей точке (корень на полу); пятка (сустав стопы) или носок (подушка) ниже своей
		# высоты в позе покоя больше 3 см = подошва в полу (кроме ноши — её корень на несущем, и сцен, где герой нарочно
		# в воде или ползёт: полынья)
		var act: Actor = v[1]
		var clipn := String(act.ap.current_animation)   # сидя и лёжа корень героя не на полу — не проверяем
		if act.get_parent() == g.get("scene") and not shot.begins_with("hole") and not ("water" in shot) and not ("crawl" in shot) \
				and not (clipn in Actor.SEAT) and not (clipn in Actor.LIE) and clipn != "":
			var sk2: Skeleton3D = act.skel
			var inv := act.global_transform.affine_inverse()
			for fb in ["foot_l", "foot_r", "ball_l", "ball_r"]:
				var fi := sk2.find_bone(fb)
				if fi < 0:
					continue
				var key := "%d:%d" % [act.get_instance_id(), fi]
				if not rest_h.has(key):
					rest_h[key] = (inv * (sk2.global_transform * sk2.get_bone_global_rest(fi).origin)).y
				var now_h := (inv * (sk2.global_transform * sk2.get_bone_global_pose(fi).origin)).y
				var under := (float(rest_h[key]) - now_h) * act.global_transform.basis.get_scale().y
				if under > 0.03:
					_hit("%s|%s|foot|%s" % [sid, shot, v[0]], under)
		# кисть в своём торсе
		var sk: Skeleton3D = (v[1] as Actor).skel
		for hb in ["hand_l", "hand_r"]:
			var hi := sk.find_bone(hb)
			if hi < 0:
				continue
			var hp := sk.global_transform * sk.get_bone_global_pose(hi).origin
			for c in (v[2] as Array):
				if String(c[3]) != "torso":
					continue
				var d2 := _seg_pt(hp, c[0], c[1])
				if d2 < float(c[2]) - 0.04:
					_hit("%s|%s|hand|%s" % [sid, shot, v[0]], float(c[2]) - d2)
	for i in vis.size():
		for j in range(i + 1, vis.size()):
			var A: Actor = vis[i][1]
			var B: Actor = vis[j][1]
			if A.is_ancestor_of(B) or B.is_ancestor_of(A) or A.skel.is_ancestor_of(B) or B.skel.is_ancestor_of(A):
				continue   # ноша: привязан к скелету другого
			if A.global_position.distance_to(B.global_position) > 2.5:
				continue
			for ca in (vis[i][2] as Array):
				if not (String(ca[3]) in ["head", "torso"]):
					continue
				for cb in (vis[j][2] as Array):
					if not (String(cb[3]) in ["head", "torso"]):
						continue
					var dd := _seg_seg(ca[0], ca[1], cb[0], cb[1])
					var depth := float(ca[2]) + float(cb[2]) - dd
					if depth > 0.06:
						_hit("%s|%s|pair|%s+%s:%s/%s" % [sid, shot, vis[i][0], vis[j][0], ca[3], cb[3]], depth)
	return false


func _finalize() -> void:
	if dumped:
		return
	dumped = true
	var res := []
	for k in rows:
		var p := String(k).split("|")
		res.append({"scene": p[0], "shot": p[1], "kind": p[2], "who": p[3], "frames": rows[k][0], "depth_cm": snappedf(float(rows[k][1]) * 100.0, 0.1)})
	res.sort_custom(func(x, y): return float(x.depth_cm) * int(x.frames) > float(y.depth_cm) * int(y.frames))
	var f := FileAccess.open(out, FileAccess.WRITE)
	if f:
		f.store_string(JSON.stringify({"frames": frames, "hits": res}, " "))
	print("CLIP записано ", out, " нарушений ", res.size(), " кадров ", frames)
