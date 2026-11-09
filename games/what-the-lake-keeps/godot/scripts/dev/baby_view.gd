extends SceneTree
## Стенд: младенец-свёрток на руках (стоя, на ходу, лёжа на кровати дома Агнес) — кадры для проверки глазами.
##   xvfb-run godot --path godot --script res://scripts/dev/baby_view.gd -- --out ПАПКА [--who agnes,eva,iris]
const BABY := preload("res://scripts/world/baby.gd")
var out := "/tmp/baby_view"
var who: Array = ["agnes", "eva", "iris"]
var cam: Camera3D
var world: Node3D
var a: Actor
var step := 0
var wait := 0
var jobs: Array = []   # [герой, клип, лежит?, камеры]


func _initialize() -> void:
	var args := OS.get_cmdline_user_args()
	if "--out" in args:
		out = args[args.find("--out") + 1]
	if "--who" in args:
		who = Array(String(args[args.find("--who") + 1]).split(","))
	DirAccess.make_dir_recursive_absolute(out)
	world = Node3D.new()
	root.add_child(world)
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.09, 0.07, 0.05)
	env.tonemap_mode = Environment.TONE_MAPPER_ACES
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.6, 0.47, 0.36)
	env.ambient_light_energy = 0.55
	env.ssao_enabled = true
	var we := WorldEnvironment.new()
	we.environment = env
	world.add_child(we)
	var lamp := OmniLight3D.new()   # как в доме: тёплая лампа под потолком и отсвет печи
	lamp.position = Vector3(0.6, 2.3, 1.2)
	lamp.omni_range = 7.0
	lamp.light_energy = 1.6
	lamp.light_color = Color(1.0, 0.8, 0.58)
	lamp.shadow_enabled = true
	world.add_child(lamp)
	var key := DirectionalLight3D.new()
	key.rotation_degrees = Vector3(-50, 30, 0)
	key.light_energy = 0.35
	key.shadow_enabled = true
	world.add_child(key)
	_box(Vector3(12, 0.02, 12), Vector3(0, -0.01, 0), Color(0.36, 0.27, 0.2))
	# кровать как в доме Агнес (village.py), пол — y 0: рама, матрас, одеяло у стены, подушка у комнаты
	_box(Vector3(1.9, 0.45, 0.95), Vector3(-2.2, 0.23, 1.3), Color(0.35, 0.22, 0.14))
	_box(Vector3(1.85, 0.12, 0.92), Vector3(-2.2, 0.5, 1.3), Color(0.92, 0.9, 0.86))
	_box(Vector3(1.2, 0.08, 0.96), Vector3(-2.45, 0.58, 1.3), Color(0.75, 0.16, 0.12))
	_box(Vector3(0.4, 0.08, 0.6), Vector3(-1.45, 0.58, 1.45), Color(0.95, 0.94, 0.92))
	cam = Camera3D.new()
	world.add_child(cam)
	cam.make_current()
	for w in who:
		jobs.append([w, "Idle", false, ["front", "close", "side"]])
	jobs.append([who[0], "Walk", false, ["front"]])
	if "eva" in who:
		jobs.append(["eva", "LayToIdle", true, ["bed_top", "bed_side"]])


func _box(size: Vector3, pos: Vector3, c: Color) -> void:
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	mi.mesh = bm
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	m.roughness = 0.9
	mi.material_override = m
	mi.position = pos
	world.add_child(mi)


func _process(_d: float) -> bool:
	if wait > 0:
		wait -= 1
		if wait == 0:
			_shoot()
		return false
	if jobs.is_empty():
		quit()
		return false
	var j: Array = jobs[0]
	var cams: Array = j[3]
	if step == 0:
		if a and is_instance_valid(a):
			a.queue_free()
		a = Actor.make(j[0], j[0])
		world.add_child(a)
		if j[2]:   # лёжа на кровати, как Ch3.lie у Евы (Ch3.EVA_BED)
			a.position = Vector3(-2.4, 0.66, 1.3)
			a.rotation = Vector3(0, deg_to_rad(-90.0), 0)
			a.play("LayToIdle", 0.0, 0.0)
			a.ap.pause()
		else:
			a.position = Vector3(0.8, 0.0, 0.0)
			a.rotation = Vector3(0, deg_to_rad(20.0), 0)
			a.play(j[1], 0.3, 0.0)
		a.placed()
		BABY.attach(a)
		wait = 45   # руки подходят к свёртку, волосы и ткань встают
	else:
		wait = 3
	_aim(cams[step])
	return false


func _aim(c: String) -> void:
	var sk := a.skel
	var chest: Vector3 = sk.global_transform * sk.get_bone_global_pose(sk.find_bone("spine_03")).origin
	var f := a.global_transform.basis.z
	var l := a.global_transform.basis.x
	match c:
		"front":
			cam.fov = 34.0
			cam.look_at_from_position(chest + f * 1.6 + l * 0.5 + Vector3(0, -0.05, 0), chest + Vector3(0, -0.25, 0))
		"close":
			cam.fov = 30.0
			cam.look_at_from_position(chest + f * 0.75 + l * 0.15 + Vector3(0, 0.12, 0), chest + f * 0.2 + Vector3(0, -0.15, 0))
		"side":
			cam.fov = 32.0
			cam.look_at_from_position(chest + l * 1.3 + f * 0.4 + Vector3(0, 0.05, 0), chest + f * 0.15 + Vector3(0, -0.2, 0))
		"bed_top":
			cam.fov = 40.0
			cam.look_at_from_position(chest + Vector3(0.9, 1.0, -0.7), chest + Vector3(0.1, 0.0, 0.0))
		"bed_side":
			cam.fov = 44.0
			cam.look_at_from_position(Vector3(-1.6, 1.45, -0.35), Vector3(-2.2, 0.6, 1.3))


func _shoot() -> void:
	var j: Array = jobs[0]
	var cams: Array = j[3]
	root.get_viewport().get_texture().get_image().save_png("%s/%s_%s_%s.png" % [out, j[0], j[1], cams[step]])
	step += 1
	if step >= cams.size():
		step = 0
		jobs.pop_front()
