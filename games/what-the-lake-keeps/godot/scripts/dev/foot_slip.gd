extends SceneTree
## Замер скольжения стоп при ходьбе (8 ноя): герой идёт по прямой walk_to (разгон, ход, торможение) и drive (как на
## прогулке); пока стопа опорная (ниже всех 2 см от минимума и почти не поднимается), её скорость по земле должна быть ≈0.
## Печатает: средняя и 95-я перцентиль скорости опорной стопы (м/с), доля кадров «едет» (> 0,15 м/с).
##   godot --headless --fixed-fps 60 --path godot --script res://scripts/dev/foot_slip.gd -- [--who noah,iris,...] [--clip Rk_Walk] [--speed 1.15]
## --fixed-fps 60 обязательно: шаг теста 1/60 с, без него кадры headless короче и drive врёт. В сборку не входит.
var who: Array = ["noah", "iris", "danny", "zoe", "eva", "agnes", "hale", "roman", "orderly", "noah_kid", "victor", "anna", "nina"]
var clip := "Rk_Walk"
var speed := 1.15
var mode := "walk"   # walk — walk_to; drive — как на прогулке (explore.gd)
var world: Node3D
var a: Actor
var i := 0
var t := 0.0
var prev := {}
var samples := {}
const DT := 1.0 / 60.0


func _initialize() -> void:
	var args := OS.get_cmdline_user_args()
	if "--who" in args:
		who = Array(String(args[args.find("--who") + 1]).split(","))
	if "--clip" in args:
		clip = args[args.find("--clip") + 1]
	if "--speed" in args:
		speed = float(args[args.find("--speed") + 1])
	if "--mode" in args:
		mode = args[args.find("--mode") + 1]
	if "--nolock" in args:
		Actor.foot_lock_on = false
	world = Node3D.new()
	root.add_child(world)
	_next()


func _next() -> void:
	if a and is_instance_valid(a):
		a.queue_free()
	if i >= who.size():
		quit()
		return
	a = Actor.make(who[i], who[i])
	world.add_child(a)
	a.position = Vector3.ZERO
	a.play("Idle", 0.0, 0.0)
	a.placed()
	t = 0.0
	prev = {}
	samples = {"start": [], "cruise": [], "stop": []}


func _foot(side: String) -> Vector3:
	var sk := a.skel
	return sk.global_transform * sk.get_bone_global_pose(sk.find_bone("foot_" + side)).origin


func _process(_d: float) -> bool:
	if a == null:
		return false
	t += DT
	if t < 0.5:
		return false
	if t < 0.52:
		if mode == "walk":
			a.walk_to(Vector3(0, 0, 8.0), speed, clip, "Idle")
	var phase := "start" if t < 1.6 else ("cruise" if a.position.z < 6.5 else "stop")
	if mode == "drive":
		phase = "start" if t < 1.2 else ("cruise" if t < 6.0 else "stop")
	var fl := _foot("l")
	var fr := _foot("r")
	if prev.size() == 2:   # в каждый момент ходьбы одна стопа стоит: скольжение = меньшая из двух скоростей по земле
		var vl := Vector2(fl.x - prev.l.x, fl.z - prev.l.z).length() / DT
		var vr := Vector2(fr.x - prev.r.x, fr.z - prev.r.z).length() / DT
		samples[phase].append(minf(vl, vr))
		if OS.get_environment("SLIP_DUMP") != "" and phase == "stop":
			print("D %.3f v=%.2f vl=%.2f vr=%.2f clip=%s on=%s" % [t, a.speed(), vl, vr, a.clip, str(a._fl.get("_l", {}).get("on", "-")) + "/" + str(a._fl.get("_r", {}).get("on", "-"))])
	prev = {"l": fl, "r": fr}
	if mode == "drive":   # двигаем ПОСЛЕ замера стоп: иначе корень уже сдвинут, а поза ещё прошлого кадра
		var v := 0.0
		if t < 6.0:
			v = minf(speed, (t - 0.5) * 2.5)
		else:
			v = maxf(0.0, speed - (t - 6.0) * 3.5)
		a.position += Vector3(0, 0, v * DT)
		a.drive(Vector3(0, 0, v), DT)
	var done := (mode == "walk" and not a.walking and t > 1.0) or (mode == "drive" and t > 7.5) or t > 14.0
	if done:
		var line := "%-9s" % who[i]
		for ph in ["start", "cruise", "stop"]:
			var s: Array = samples[ph]
			if s.is_empty():
				line += "  %s —" % ph
				continue
			s.sort()
			var mean := 0.0
			var bad := 0
			for x in s:
				mean += x
				if x > 0.1:
					bad += 1
			line += "  %s ср %.2f p95 %.2f едет %d%%" % [ph, mean / s.size(), s[int(s.size() * 0.95)], int(100.0 * bad / s.size())]
		print("SLIP ", line)
		i += 1
		_next()
	return false
