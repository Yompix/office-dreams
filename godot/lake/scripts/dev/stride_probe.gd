extends SceneTree
## Естественная скорость шага клипа (8 ноя): герой на месте, клип со скоростью 1; опорная стопа (ниже другой) уезжает
## назад со скоростью, с которой герой должен идти, чтобы стопа стояла. Печатает м/с и то же в «длинах ноги».
##   godot --headless --fixed-fps 60 --path godot/lake --script res://scripts/dev/stride_probe.gd -- [--who noah,iris] [--clip Rk_Walk]
var who: Array = ["noah", "iris", "danny", "zoe", "eva", "agnes", "hale", "roman", "orderly", "noah_kid", "victor", "anna", "nina"]
var clips: Array = ["Rk_Walk", "Walk"]
var world: Node3D
var a: Actor
var jobs: Array = []
var t := 0.0
var prev := {}
var vs: Array = []
const DT := 1.0 / 60.0


func _initialize() -> void:
	var args := OS.get_cmdline_user_args()
	if "--who" in args:
		who = Array(String(args[args.find("--who") + 1]).split(","))
	if "--clip" in args:
		clips = Array(String(args[args.find("--clip") + 1]).split(","))
	for w in who:
		for c in clips:
			jobs.append([w, c])
	world = Node3D.new()
	root.add_child(world)
	_next()


func _next() -> void:
	if a and is_instance_valid(a):
		a.queue_free()
	if jobs.is_empty():
		quit()
		return
	var j: Array = jobs[0]
	a = Actor.make(j[0], j[0])
	world.add_child(a)
	a.play(j[1], 0.0, 0.0)
	a.ap.speed_scale = 1.0
	t = 0.0
	prev = {}
	vs = []


func _foot(side: String) -> Vector3:
	var sk := a.skel
	return sk.global_transform * sk.get_bone_global_pose(sk.find_bone("foot_" + side)).origin


func _process(_d: float) -> bool:
	if a == null:
		return false
	t += DT
	var fl := _foot("l")
	var fr := _foot("r")
	if t > 0.5 and prev.size() == 2:
		var plant := "l" if fl.y < fr.y else "r"
		var p: Vector3 = fl if plant == "l" else fr
		var pp: Vector3 = prev[plant]
		vs.append(-(p.z - pp.z) / DT)   # назад по ходу (+z — вперёд у героя)
	prev = {"l": fl, "r": fr}
	if t > 4.5:
		vs.sort()
		var med: float = vs[vs.size() / 2]
		var j: Array = jobs.pop_front()
		print("STRIDE %-9s %-8s нат %.2f м/с  нога %.2f м  → %.2f ног/с  (в коде %.2f)" % [j[0], j[1], med, a.leg, med / a.leg, float(Actor.STRIDE.get(j[1], 0.0))])
		_next()
	return false
