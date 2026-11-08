extends SceneTree
## Стандарт героев (8 ноя): «было / стало» рядом с Ноем (1,89 м) — рост в полный рост и головы крупно.
##   xvfb-run -a godot --path godot/lake --rendering-driver opengl3 --script res://scripts/dev/cast_lineup.gd -- --out ПАПКА [--pairs orderly,noah_kid,victor,hale]
## Слева направо: Ной, герой «было», герой «стало» (<имя>_v2). Жёлтые линии — 1 м, 1,5 м, 2 м. В сборку не входит.
var out := "/tmp/cast_lineup"
var pairs: Array = ["orderly", "noah_kid", "victor", "hale"]
var cam: Camera3D
var world: Node3D
var actors: Array = []
var jobs: Array = []   # [пара, вид]
var wait := 0


func _initialize() -> void:
	var args := OS.get_cmdline_user_args()
	if "--out" in args:
		out = args[args.find("--out") + 1]
	if "--pairs" in args:
		pairs = Array(String(args[args.find("--pairs") + 1]).split(","))
	DirAccess.make_dir_recursive_absolute(out)
	world = Node3D.new()
	root.add_child(world)
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.16, 0.17, 0.19)
	env.tonemap_mode = Environment.TONE_MAPPER_ACES
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.75, 0.78, 0.85)
	env.ambient_light_energy = 0.6
	var we := WorldEnvironment.new()
	we.environment = env
	world.add_child(we)
	var key := DirectionalLight3D.new()
	key.rotation_degrees = Vector3(-35, 25, 0)
	key.light_energy = 1.1
	key.shadow_enabled = true
	world.add_child(key)
	var fill := DirectionalLight3D.new()
	fill.rotation_degrees = Vector3(-20, -140, 0)
	fill.light_energy = 0.35
	world.add_child(fill)
	var floor_mi := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(20, 20)
	floor_mi.mesh = pm
	var fm := StandardMaterial3D.new()
	fm.albedo_color = Color(0.3, 0.3, 0.32)
	floor_mi.material_override = fm
	world.add_child(floor_mi)
	for h in [1.0, 1.5, 2.0]:
		var mi := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(3.6, 0.006, 0.006)
		mi.mesh = bm
		var m := StandardMaterial3D.new()
		m.albedo_color = Color(0.9, 0.75, 0.2)
		m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		mi.material_override = m
		mi.position = Vector3(0, h, -0.3)
		world.add_child(mi)
	cam = Camera3D.new()
	world.add_child(cam)
	cam.make_current()
	for p in pairs:
		jobs.append([p, "full"])
		jobs.append([p, "head"])


func _cast(p: String) -> void:
	for a in actors:
		a.queue_free()
	actors.clear()
	var list := [["noah", "noah", -1.0], [p, "res://assets/chars/%s.glb" % p, 0.0], [p + "_v2", "%s_v2" % p, 1.0]]
	for it in list:
		var a := Actor.make(it[0], it[1])
		world.add_child(a)
		a.position = Vector3(it[2], 0, 0)
		a.play("Idle", 0.0, 0.0)
		a.placed()
		actors.append(a)


func _head(i: int) -> Vector3:
	var sk: Skeleton3D = actors[i].skel
	return sk.global_transform * sk.get_bone_global_pose(sk.find_bone("head")).origin


func _process(_d: float) -> bool:
	if wait > 0:
		wait -= 1
		if wait == 0:
			var done: Array = jobs.pop_front()
			root.get_viewport().get_texture().get_image().save_png("%s/%s_%s.png" % [out, done[0], done[1]])
		return false
	if jobs.is_empty():
		quit()
		return false
	var j: Array = jobs[0]
	if j[1] == "full":
		_cast(j[0])
		cam.fov = 40.0
		cam.look_at_from_position(Vector3(0, 1.15, 4.6), Vector3(0, 1.05, 0))
		wait = 40
	else:
		var mid: Vector3 = (_head(1) + _head(2)) * 0.5 + Vector3(0, 0.08, 0)
		cam.fov = 30.0
		cam.look_at_from_position(mid + Vector3(0, 0.05, 2.2), mid)
		wait = 4
	return false
