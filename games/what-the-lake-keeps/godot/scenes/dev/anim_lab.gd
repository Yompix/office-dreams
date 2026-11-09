extends Node3D
## Стенд анимации героев: замеры и кадры «до/после». В сборку не входит (scenes/dev/*).
##   godot --headless --path godot res://scenes/dev/anim_lab.tscn -- --lab ТЕСТ [--who iris] [--out /tmp/lab.json]
##   кадры (ПК-графика): gd_pc --path godot res://scenes/dev/anim_lab.tscn -- --lab ТЕСТ --shots ПАПКА [--every 6]
## Тесты:
##   idle   — герой стоит 12 с: доля кадров, когда скелет неподвижен (замер «манекена»), дыхание, моргание
##   walk   — идёт 6 м и останавливается: скольжение опорной стопы, рывки на старте/остановке, поворот
##   trans  — цепочка клипов (стоя → шаг → стоя → сидя → стоя → присел → стоя): самый большой скачок кости за кадр
##   turn   — резкие повороты головы влево/вправо: отставание и успокоение волос, тряска в покое
##   wind   — стоит на ветру 8 с: движение волос (порывы, а не дрожь)
##   perf   — 8 героев сразу 10 с: время обработки героев за кадр (CPU)
##   face   — разговор двоих: моргание, взгляд на собеседника, слушающий
##   pause  — стоит; на 3-й секунде пауза игры на 1,5 с: поза должна замереть, после — продолжиться без рывка
##   carry  — Ной несёт Еву (как в главе 3: Ch3.carry + Ch3.process): ладони под спиной и коленями, [--view side|front|back]

const CAST := ["noah", "iris", "zoe", "eva", "danny", "agnes", "hale", "orderly"]
var test := "idle"
var who := "iris"
var out_path := "/tmp/lab.json"
var shots := ""
var every := 6
var t := 0.0
var frame := 0
var actors: Array = []
var prev_rot := {}          # актёр → [Quaternion] прошлого кадра (локальные повороты костей)
var res := {}
var cam: Camera3D
var phase := 0
var walk_dst := Vector3.ZERO
var walking := false
var feet := {}              # стопа → [прошлое положение (мир)]
var slide_sum := 0.0
var slides: Array = []
var stance_foot := ""
var stance_p0 := Vector3.ZERO
var stance_t0 := 0.0
var stance_walk := false
var stance_slides: Array = []
var slide_n := 0
var max_jump := 0.0
var jump_at := ""
var still_frames := 0
var counted := 0
var sec_pose := {}          # поза секунду назад
var sec_t := 0.0
var sec_still := 0
var sec_n := 0
var proc_us := 0
var proc_frames := 0
var hair_log: Array = []    # [t, смещение волос (м)]
var _hair_now: Array = []
var g_fake: LabGame         # для теста carry: то, что Ch3 берёт у игры
var pause_pose := []        # pause: поза в начале паузы
var pause_drift := 0.0      # pause: насколько поза сдвинулась за паузу (°)
var after_jump := 0.0       # pause: самый большой скачок кости за кадр в первые 0,5 с после паузы (°)
var hand_err: Array = []    # carry: расстояние ладони до точки опоры, м


## То, что глава 3 читает у game.gd: герои, своё состояние, флаги, сцена.
class LabGame:
	extends RefCounted
	var actors := {}
	var c3 := {}
	var flags := {}
	var _scene_id := "c3_s1_carry"


func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	var arg := func(k: String, d: String) -> String:
		return args[args.find(k) + 1] if k in args else d
	test = arg.call("--lab", "idle")
	process_mode = Node.PROCESS_MODE_ALWAYS   # стенд считает и на паузе игры (тест pause), герои — нет
	Actor.prof_sections = "--sections" in args
	if "--stride" in args:
		Actor.stride_override = float(args[args.find("--stride") + 1])
	who = arg.call("--who", "iris")
	out_path = arg.call("--out", "/tmp/lab_%s.json" % test)
	shots = arg.call("--shots", "")
	every = int(arg.call("--every", "6"))
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.16, 0.17, 0.19)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.55, 0.57, 0.6)
	env.ambient_light_energy = 0.6
	env.tonemap_mode = Environment.TONE_MAPPER_ACES
	var we := WorldEnvironment.new()
	we.environment = env
	add_child(we)
	var key := DirectionalLight3D.new()
	key.rotation_degrees = Vector3(-35, 30, 0)
	key.light_energy = 1.5
	key.shadow_enabled = true
	add_child(key)
	var floor_mi := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(30, 30)
	floor_mi.mesh = pm
	add_child(floor_mi)
	cam = Camera3D.new()
	add_child(cam)
	cam.make_current()
	var names: Array = [who]
	match test:
		"perf":
			names = CAST
		"face":
			names = [who, "noah" if who != "noah" else "iris"]
		"carry":
			names = ["noah", "agnes" if "--piggy" in args else "eva"]
	for i in names.size():
		var a := Actor.make(names[i], names[i])
		a.process_mode = Node.PROCESS_MODE_PAUSABLE   # как в игре: на паузе герои стоят (стенд — нет)
		add_child(a)
		a.position = Vector3((i - (names.size() - 1) / 2.0) * 0.9, 0, 0)
		a.play("Idle", 0.0)
		actors.append(a)
	if test == "face":
		(actors[0] as Actor).position = Vector3(-0.55, 0, 0)
		(actors[0] as Actor).rotation_degrees = Vector3(0, 90, 0)
		(actors[1] as Actor).position = Vector3(0.55, 0, 0)
		(actors[1] as Actor).rotation_degrees = Vector3(0, -90, 0)
	if test == "wind":
		(actors[0] as Actor).breeze = 1.0
	if test == "carry":
		g_fake = LabGame.new()
		g_fake.actors = {"noah": actors[0], "eva": actors[1]}
		(actors[0] as Actor).position = Vector3.ZERO
		(actors[0] as Actor).play(_arg("--clip", "Walk"), 0.0, 0.0)
		if "--noeva" in args:   # только Ной с клипом: что даёт сам клип, без Евы и без поправок «несёт»
			(actors[1] as Actor).visible = false
			g_fake.actors.erase("eva")
		elif "--piggy" in args:   # Агнес на спине (глава 4: Ch4.piggyback + Ch4.piggy_update)
			g_fake.actors = {"noah": actors[0], "agnes": actors[1]}
			Ch4.piggyback(actors[0], actors[1])
		else:
			Ch3.carry(g_fake)
	if test == "grip":   # рация в правой руке, как у Дэнни на заправке
		var ba := BoneAttachment3D.new()
		ba.bone_name = "hand_r"
		(actors[0] as Actor).skel.add_child(ba)
		var w := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(0.055, 0.13, 0.035)
		w.mesh = bm
		w.position = Vector3(0.0, 0.08, 0.03)
		ba.add_child(w)
	var a0: Actor = actors[0]
	var hs: Variant = a0.get("hair_sim")
	print("волосы: ", "кости и пружины" if hs != null else "без костей (короткие, нет причёски или старое ядро)")
	if hs != null:   # пряди после симулятора: читаем в момент, когда модификатор отработал
		(hs as SpringBoneSimulator3D).modification_processed.connect(func(): _hair_now = a0.call("hair_probe"))
	_cam_for_test()


func _cam_for_test() -> void:
	match test:
		"perf":
			cam.fov = 40
			cam.look_at_from_position(Vector3(0, 1.3, 6.5), Vector3(0, 1.0, 0))
		"walk":
			cam.fov = 40
			cam.look_at_from_position(Vector3(4.5, 1.2, 3.0), Vector3(0, 0.9, 3.0))
		"face":
			cam.fov = 30
			cam.look_at_from_position(Vector3(0.2, 1.45, 2.3), Vector3(0, 1.35, 0))
		"grip":
			cam.fov = 22
			var hp: Vector3 = (actors[0] as Actor).skel.global_transform * (actors[0] as Actor).skel.get_bone_global_pose((actors[0] as Actor).skel.find_bone("hand_r")).origin
			cam.look_at_from_position(hp + Vector3(-0.5, 0.25, 0.9), hp)
		"carry":
			var view := _arg("--view", "side")
			cam.fov = 34
			var at := Vector3(0, 1.05, 0.35)
			var from: Vector3 = {"side": Vector3(2.4, 1.35, 0.6), "front": Vector3(0.9, 1.4, 2.6), "back": Vector3(-1.2, 1.6, -2.2),
				"left": Vector3(-2.4, 1.3, 0.5), "front3": Vector3(-1.4, 1.5, 2.4)}.get(view, Vector3(2.4, 1.35, 0.6))
			cam.look_at_from_position(from, at)
		"turn", "wind":
			cam.fov = 18
			cam.look_at_from_position(Vector3(0.6, 1.55, 2.6), Vector3(0, 1.45, 0))
		_:
			cam.fov = 30
			cam.look_at_from_position(Vector3(0, 1.2, 4.0), Vector3(0, 1.0, 0))


func _process(delta: float) -> void:
	t += delta
	frame += 1
	_script_step(delta)
	_measure()
	if shots != "" and frame % every == 0 and frame > 2:
		DirAccess.make_dir_recursive_absolute(shots)
		get_viewport().get_texture().get_image().save_png("%s/%s_%05d.png" % [shots, test, frame])
	if t > _length():
		_finish()


func _length() -> float:
	return {"pause": 7.0, "carry": 4.0, "grip": 3.0, "idle": 12.0, "walk": 10.0, "trans": 16.0, "turn": 9.0, "wind": 8.0, "perf": 10.0, "face": 10.0}.get(test, 10.0)


## Сценарий теста: что делает герой в момент t.
func _script_step(delta: float) -> void:
	var a: Actor = actors[0]
	match test:
		"walk":
			if phase == 0 and t > 1.0:
				phase = 1
				_walk_start(a, Vector3(0, 0, 6.0))
			if walking:
				_walk_update(a, delta)
		"trans":
			var seq := [[0.0, "Idle"], [2.0, "Walk"], [4.0, "Idle"], [6.0, "Rk_SitIdle"], [9.0, "Idle"], [11.0, "Crouch_Idle"], [13.0, "Idle"]]
			for s in seq:
				if phase < seq.find(s) + 1 and t >= float(s[0]):
					phase = seq.find(s) + 1
					a.play(String(s[1]), 0.0)
		"carry":
			if g_fake.actors.has("agnes"):
				Ch4.piggy_update(a, actors[1])
				if OS.get_environment("LABDBG") != "" and frame % 30 == 0:
					var ag: Actor = actors[1]
					var ks := ag.skel
					for k in ["thigh_l", "calf_l", "foot_l", "thigh_r", "calf_r", "spine_03", "head"]:
						print("    агнес %-8s %s" % [k, a.to_local(ks.global_transform * ks.get_bone_global_pose(ks.find_bone(k)).origin).snapped(Vector3.ONE * 0.01)])
			else:
				Ch3.process(g_fake, delta)
			if t > 1.0:   # где ладони относительно точек опоры (после того как пальцы и руки встали)
				var sk := a.skel
				var tg: Dictionary = a.reach
				for side in ["_r", "_l"]:
					if tg.has(side):
						var h := sk.global_transform * sk.get_bone_global_pose(sk.find_bone("hand" + side)).origin
						hand_err.append(h.distance_to(tg[side]))
						if OS.get_environment("LABDBG") != "" and frame % 15 == 0:
							var sh := sk.global_transform * sk.get_bone_global_pose(sk.find_bone("upperarm" + side)).origin
							var el := sk.global_transform * sk.get_bone_global_pose(sk.find_bone("lowerarm" + side)).origin
							print("  %s цель %s кисть %s локоть %s плечо %s (в системе Ноя)" % [side, a.to_local(tg[side]).snapped(Vector3.ONE * 0.01),
								a.to_local(h).snapped(Vector3.ONE * 0.01), a.to_local(el).snapped(Vector3.ONE * 0.01), a.to_local(sh).snapped(Vector3.ONE * 0.01)])
		"pause":
			var sk := a.skel
			if phase == 0 and t >= 3.0:
				phase = 1
				pause_pose = _pose(sk)
				get_tree().paused = true
			elif phase == 1:
				var cur := _pose(sk)
				var dd := _pose_diff(pause_pose, cur)
				if dd > pause_drift and OS.get_environment("LABDBG") != "":
					for i in cur.size():
						if rad_to_deg((pause_pose[i] as Quaternion).angle_to(cur[i])) > dd - 0.001:
							print("  пауза: t=%.2f сдвиг %.2f° кость %s" % [t, dd, sk.get_bone_name(i)])
				pause_drift = maxf(pause_drift, dd)
				if t >= 4.5:
					phase = 2
					get_tree().paused = false
		"turn":
			# резкие повороты головы: влево, вправо, прямо — потом покой
			var tgt := Vector3(0, 1.5, 3.0)
			if t > 1.0 and t < 2.5:
				tgt = Vector3(-3.0, 1.5, 1.0)
			elif t >= 2.5 and t < 4.0:
				tgt = Vector3(3.0, 1.5, 1.0)
			a.look_target = tgt
		"face":
			var b: Actor = actors[1]
			if phase == 0:
				phase = 1
				a.look_target = _head(b)
				a.say("я не знаю, что со мной. я правда не помню, как мы сюда доехали", 4.0)
			if phase == 1 and t > 4.5:
				phase = 2
				b.look_target = _head(a)
				b.say("тогда езжай. просто езжай на север и не спрашивай", 3.5)


func _pose(sk: Skeleton3D) -> Array:
	var r := []
	for b in sk.get_bone_count():
		r.append(sk.get_bone_pose_rotation(b))
	return r


func _pose_diff(a: Array, b: Array) -> float:
	var mx := 0.0
	for i in mini(a.size(), b.size()):
		mx = maxf(mx, rad_to_deg((a[i] as Quaternion).angle_to(b[i])))
	return mx


func _arg(k: String, d: String) -> String:
	var args := OS.get_cmdline_user_args()
	return args[args.find(k) + 1] if k in args else d


func _head(o: Actor) -> Vector3:
	return o.skel.global_transform * o.skel.get_bone_global_pose(o.skel.find_bone("head")).origin + Vector3(0, 0.07, 0)


## Ходьба — так же, как game.gd::_walk (если у героя есть свой шаг — им).
func _walk_start(a: Actor, dst: Vector3) -> void:
	walk_dst = dst
	walking = true
	if a.has_method("walk_to"):
		a.call("walk_to", dst, float(_arg("--speed", "1.15")), _arg("--clip", "Rk_Walk"), "Idle", 0.0)
		return
	var d := dst - a.position
	a.rotation.y = atan2(d.x, d.z)
	a.play("Rk_Walk", 0.0)


func _walk_update(a: Actor, delta: float) -> void:
	if a.has_method("walk_to"):
		walking = a.get("walking")
		return
	var d := Vector3(walk_dst.x - a.position.x, 0.0, walk_dst.z - a.position.z)
	var step := 1.15 * delta
	if d.length() <= step:
		a.position = walk_dst
		a.play("Idle", 0.0)
		walking = false
	else:
		a.position += d.normalized() * step


func _measure() -> void:
	for a in actors:
		var sk: Skeleton3D = (a as Actor).skel
		var cur := []
		for b in sk.get_bone_count():
			cur.append(sk.get_bone_pose_rotation(b))
		if prev_rot.has(a):
			var mx := 0.0
			var mb := -1
			var p: Array = prev_rot[a]
			for b in mini(p.size(), cur.size()):
				var ang := rad_to_deg((p[b] as Quaternion).angle_to(cur[b]))
				if ang > mx:
					mx = ang
					mb = b
			if OS.get_environment("LABDBG") != "" and t > 3.0 and frame % 30 == 0:
				print("  t=%.2f mx=%.4f кость %s" % [t, mx, sk.get_bone_name(mb) if mb >= 0 else "-"])
			if OS.get_environment("LABDBG") != "" and mx > 5.0 and frame > 3:
				print("  скачок %.1f° t=%.2f %s кость %s" % [mx, t, (a as Actor).key, sk.get_bone_name(mb)])
			if frame > 3:
				if test == "pause" and phase == 2 and t < 5.0:
					after_jump = maxf(after_jump, mx)
				if mx > max_jump:
					max_jump = mx
					jump_at = "%.2f с, %s" % [t, sk.get_bone_name(mb) if mb >= 0 else "-"]
				if t > 3.0 and a == actors[0]:
					counted += 1
					if mx < 0.1:   # меньше 0,1° за кадр глаз не видит
						still_frames += 1
		prev_rot[a] = cur
	# живость: насколько поза изменилась за последнюю секунду (меньше 0,2° — стоит как манекен)
	if t - sec_t >= 1.0:
		sec_t = t
		var sk1: Skeleton3D = (actors[0] as Actor).skel
		var cur1 := []
		for b in sk1.get_bone_count():
			cur1.append(sk1.get_bone_pose_rotation(b))
		if not sec_pose.is_empty() and t > 3.0:
			var mx1 := 0.0
			for b in mini((sec_pose.p as Array).size(), cur1.size()):
				mx1 = maxf(mx1, rad_to_deg(((sec_pose.p as Array)[b] as Quaternion).angle_to(cur1[b])))
			sec_n += 1
			if mx1 < 0.2:
				sec_still += 1
		sec_pose = {"p": cur1}
	# стопы: скольжение опорной (нижней) стопы по земле, м/с
	var a0: Actor = actors[0]
	var sk0 := a0.skel
	var lo := 1e9
	var lo_name := ""
	for fn in ["ball_l", "ball_r"]:
		var p := sk0.global_transform * sk0.get_bone_global_pose(sk0.find_bone(fn)).origin
		if p.y < lo:
			lo = p.y
			lo_name = fn
	# опора целиком: где стопа встала и где оторвалась — смещение по земле за время опоры (м/с); перекат стопы не в счёт
	var walking_now: bool = a0.get("walking") if a0.get("walking") != null else walking
	if test == "walk" and t > 1.2:
		var plo := sk0.global_transform * sk0.get_bone_global_pose(sk0.find_bone(lo_name)).origin
		if lo_name != stance_foot:
			if stance_foot != "" and t - stance_t0 > 0.15 and stance_walk:
				var pend: Vector3 = feet.get(stance_foot, plo)
				stance_slides.append(Vector2(pend.x - stance_p0.x, pend.z - stance_p0.z).length() / (t - stance_t0))
			stance_foot = lo_name
			stance_p0 = plo
			stance_t0 = t
			stance_walk = walking_now
		elif not walking_now:
			stance_walk = false
	for fn in ["ball_l", "ball_r"]:
		var p := sk0.global_transform * sk0.get_bone_global_pose(sk0.find_bone(fn)).origin
		if feet.has(fn) and fn == lo_name and test == "walk" and t > 1.2:
			var spd := Vector2(p.x - (feet[fn] as Vector3).x, p.z - (feet[fn] as Vector3).z).length() / maxf(get_process_delta_time(), 1e-4)
			if (a0.get("walking") if a0.get("walking") != null else walking) or t < 8.0:
				slide_sum += spd
				slide_n += 1
				slides.append(spd)
			if OS.get_environment("LABDBG") != "" and frame % 8 == 0:
				print("  t=%.2f v=%.2f скор.клипа=%.2f стопа %s %.2f м/с" % [t, float(a0.get("_v")), a0.ap.speed_scale, fn, spd])
		feet[fn] = p
	# волосы: смещение прядей (шейдер — sway; кости волос — кончик относительно головы)
	if a0.has_method("hair_probe") and a0.get("hair_sim") != null:
		hair_log.append([snappedf(t, 0.01), _hair_now])
	elif a0._hair.size() > 0:
		var swv: Variant = a0._hair[0].get_shader_parameter("sway")
		if swv is Vector3:
			hair_log.append([snappedf(t, 0.01), [swv.x, swv.y, swv.z]])
	if Actor.prof_frames > 0:
		proc_us = Actor.prof_us
		proc_frames = Actor.prof_frames


func _median(a: Array) -> float:
	if a.is_empty():
		return 0.0
	var b := a.duplicate()
	b.sort()
	return float(b[b.size() / 2])


func _finish() -> void:
	var a0: Actor = actors[0]
	print("клип ", a0.clip, " играет=", a0.ap.is_playing(), " позиция=", a0.ap.current_animation_position, " назначен=", a0.ap.assigned_animation)
	res = {"test": test, "who": who, "max_bone_jump_deg": snappedf(max_jump, 0.01), "jump_at": jump_at,
		"still_ratio": snappedf(float(still_frames) / maxf(counted, 1), 0.001),
		"still_seconds_ratio": snappedf(float(sec_still) / maxf(sec_n, 1), 0.001),
		"foot_slide_mps": snappedf(slide_sum / maxf(slide_n, 1), 0.001),
		"foot_slide_median": snappedf(_median(slides), 0.001),
		"stance_slide_mps": snappedf(_median(stance_slides), 0.001), "stances": stance_slides.size(),
		"actor_us_per_frame": snappedf(float(proc_us) / maxf(proc_frames, 1), 0.1), "actors": actors.size(),
		"frames": frame, "hair": hair_log, "hand_err_m": snappedf(_median(hand_err), 0.001),
		"hand_err_max_m": snappedf(hand_err.max() if not hand_err.is_empty() else 0.0, 0.001)}
	if test == "pause":
		print("ЛАБ pause: за паузу поза сдвинулась на %.3f°, после паузы самый большой скачок %.2f°" % [pause_drift, after_jump])
	if test == "carry":
		print("ЛАБ carry: ладони от точек опоры — медиана %.1f см, макс %.1f см" % [100.0 * _median(hand_err), 100.0 * (hand_err.max() if not hand_err.is_empty() else 0.0)])
	var f := FileAccess.open(out_path, FileAccess.WRITE)
	f.store_string(JSON.stringify(res))
	f.close()
	print("ЛАБ %s %s: скачок %.1f° (%s), манекен %.0f%% секунд, скольжение стопы %.2f м/с (медиана %.2f, опора целиком %.3f м/с по %d шагам), герои %.0f мкс/кадр (%d)" % [test, who, max_jump, jump_at,
		100.0 * float(sec_still) / maxf(sec_n, 1), slide_sum / maxf(slide_n, 1), _median(slides), _median(stance_slides), stance_slides.size(), float(proc_us) / maxf(proc_frames, 1), actors.size()])
	if Actor.prof_sections:
		var fr := maxf(proc_frames, 1)
		var parts := []
		for k2 in Actor.sec_us:
			parts.append("%s %.0f" % [k2, float(Actor.sec_us[k2]) / fr])
		print("  по частям (мкс/кадр на всех): ", ", ".join(parts))
	get_tree().quit()
