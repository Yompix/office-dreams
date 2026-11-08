extends SceneTree
## Лента кадров шага сбоку (8 ноя): герой идёт walk_to, камера сбоку едет рядом; 12 кадров через 0,1 с.
##   xvfb-run -a godot --path godot/lake --rendering-driver opengl3 --script res://scripts/dev/walk_strip.gd -- --who noah --out ПАПКА [--nolock]
var out := "/tmp/walk_strip"
var who := "noah"
var world: Node3D
var cam: Camera3D
var a: Actor
var t := 0.0
var n := 0
const DT := 1.0 / 60.0


func _initialize() -> void:
	var args := OS.get_cmdline_user_args()
	if "--out" in args:
		out = args[args.find("--out") + 1]
	if "--who" in args:
		who = args[args.find("--who") + 1]
	if "--nolock" in args:
		Actor.foot_lock_on = false
	DirAccess.make_dir_recursive_absolute(out)
	world = Node3D.new()
	root.add_child(world)
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.2, 0.21, 0.23)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.8, 0.8, 0.85)
	env.ambient_light_energy = 0.7
	var we := WorldEnvironment.new()
	we.environment = env
	world.add_child(we)
	var key := DirectionalLight3D.new()
	key.rotation_degrees = Vector3(-40, 60, 0)
	key.shadow_enabled = true
	world.add_child(key)
	var fl := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(4, 30)
	fl.mesh = pm
	var m := StandardMaterial3D.new()
	m.albedo_texture = _checker()
	m.uv1_scale = Vector3(4, 30, 1)
	fl.material_override = m
	world.add_child(fl)
	cam = Camera3D.new()
	world.add_child(cam)
	cam.make_current()
	a = Actor.make(who, who)
	world.add_child(a)
	a.play("Idle", 0.0, 0.0)
	a.placed()


func _checker() -> ImageTexture:
	var im := Image.create(2, 2, false, Image.FORMAT_RGB8)
	im.set_pixel(0, 0, Color(0.55, 0.55, 0.55))
	im.set_pixel(1, 1, Color(0.55, 0.55, 0.55))
	im.set_pixel(1, 0, Color(0.35, 0.35, 0.35))
	im.set_pixel(0, 1, Color(0.35, 0.35, 0.35))
	return ImageTexture.create_from_image(im)


func _process(_d: float) -> bool:
	t += DT
	if t > 0.3 and t < 0.33:
		a.walk_to(Vector3(0, 0, 20), 1.15, "Rk_Walk", "Idle")
	var z := a.position.z
	cam.fov = 32.0
	cam.look_at_from_position(Vector3(3.2, 0.55, z + 0.2), Vector3(0, 0.45, z))
	if t > 2.5 and fmod(t - 2.5, 0.1) < DT and n < 12:
		root.get_viewport().get_texture().get_image().save_png("%s/%s_%02d.png" % [out, who, n])
		n += 1
	if n >= 12:
		quit()
	return false
