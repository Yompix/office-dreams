extends Node3D
## Гардероб: одежда героя крупно — для проверки ткани, складок, посадки и проникновений. В сборку не входит (scenes/dev/*).
##   gd_pc --path godot res://scenes/dev/wardrobe.tscn -- --who noah --out ПАПКА [--light studio|night|warm] [--poses Idle,Walk,…]
## Кадры: <поза>_<камера>.png; камеры: close (грудь и руки), front (в рост, 3/4), back (в рост сзади), side (сбоку).

const POSES := {"Idle": 0.6, "Walk": 0.35, "Jog_Fwd": 0.25, "Rk_SitIdle": 3.0, "Crouch_Idle": 0.8, "PickUp_Table": 0.45, "Idle_FoldArms": 0.8}
var CAMS: Array = ["close", "front", "back", "side"]   # --cams: ещё neckb / neckf / neck3 (шея и ворот крупно)
var who := "noah"
var out := "/tmp/wardrobe"
var poses: Array = []
var a: Actor
var step := -1
var wait := 0
var cam: Camera3D
var light := "studio"


func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	var arg := func(k: String, d: String) -> String:
		return args[args.find(k) + 1] if k in args else d
	who = arg.call("--who", "noah")
	var file: String = arg.call("--file", who)   # модель для проверки: assets/chars/<file>.glb (по умолчанию — герой)
	out = arg.call("--out", "/tmp/wardrobe")
	light = arg.call("--light", "studio")
	poses = Array(String(arg.call("--poses", ",".join(POSES.keys()))).split(","))
	CAMS = Array(String(arg.call("--cams", ",".join(CAMS))).split(","))
	DirAccess.make_dir_recursive_absolute(out)
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.tonemap_mode = Environment.TONE_MAPPER_ACES
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ssao_enabled = true
	match light:
		"night":
			env.background_color = Color(0.03, 0.035, 0.05)
			env.ambient_light_color = Color(0.25, 0.3, 0.42)
			env.ambient_light_energy = 0.35
		"warm":
			env.background_color = Color(0.09, 0.07, 0.05)
			env.ambient_light_color = Color(0.6, 0.45, 0.32)
			env.ambient_light_energy = 0.4
		_:
			env.background_color = Color(0.32, 0.33, 0.35)
			env.ambient_light_color = Color(0.62, 0.64, 0.68)
			env.ambient_light_energy = 0.55
	var we := WorldEnvironment.new()
	we.environment = env
	add_child(we)
	var key := DirectionalLight3D.new()
	key.shadow_enabled = true
	key.rotation_degrees = Vector3(-38, 35, 0)
	key.light_energy = {"night": 0.5, "warm": 0.2}.get(light, 1.6)
	key.light_color = Color(0.7, 0.8, 1.0) if light == "night" else Color(1, 0.98, 0.94)
	add_child(key)
	var fill := OmniLight3D.new()   # тёплый практический свет (лампа, печь) — сбоку
	fill.position = Vector3(-1.6, 1.6, 1.4)
	fill.omni_range = 6.0
	fill.light_energy = {"night": 0.8, "warm": 2.2}.get(light, 0.6)
	fill.light_color = Color(1.0, 0.72, 0.45) if light != "studio" else Color(0.85, 0.9, 1.0)
	fill.shadow_enabled = true
	add_child(fill)
	var floor_mi := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(20, 20)
	floor_mi.mesh = pm
	var fm := StandardMaterial3D.new()
	fm.albedo_color = Color(0.42, 0.42, 0.44)
	fm.roughness = 0.9
	floor_mi.material_override = fm
	add_child(floor_mi)
	var seat := MeshInstance3D.new()   # табурет для позы «сидя»
	var bm := BoxMesh.new()
	bm.size = Vector3(0.45, 0.46, 0.42)
	seat.mesh = bm
	seat.position = Vector3(0, 0.23, -0.12)
	seat.name = "Seat"
	add_child(seat)
	cam = Camera3D.new()
	add_child(cam)
	cam.make_current()
	a = Actor.make(who, file)
	add_child(a)
	a.play("Idle", 0.0, 0.0)
	if "--plain" in args:   # одна серая матовая ткань без текстур: видна только форма (бугор — сетка, пятно — текстура)
		var plain_m := StandardMaterial3D.new()
		plain_m.albedo_color = Color(0.7, 0.7, 0.72)
		plain_m.roughness = 0.6
		for mi in a.find_children("*", "MeshInstance3D", true, false):
			(mi as MeshInstance3D).material_override = plain_m
	if "--ids" in args:   # каждая сетка — своим плоским цветом: видно, что именно проступает (кожа, рубашка, куртка)
		var pal := [Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.MAGENTA, Color.CYAN, Color.ORANGE, Color.WHITE, Color.PURPLE, Color.BROWN, Color.PINK, Color.LIME_GREEN]
		var k := 0
		for mi in a.find_children("*", "MeshInstance3D", true, false):
			var m := StandardMaterial3D.new()
			m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
			m.albedo_color = pal[k % pal.size()]
			m.cull_mode = BaseMaterial3D.CULL_DISABLED
			(mi as MeshInstance3D).material_override = m
			print("IDS ", mi.name, " = ", pal[k % pal.size()].to_html(false))
			k += 1


func _process(_d: float) -> void:
	if wait > 0:
		wait -= 1
		if wait == 12 and (get_node("Seat") as Node3D).visible:
			_fit_seat()
		if wait == 0:
			_shoot()
		return
	step += 1
	var n := poses.size() * CAMS.size()
	if step >= n:
		get_tree().quit()
		return
	var pi := step / CAMS.size()
	var ci := step % CAMS.size()
	var pose: String = poses[pi]
	if ci == 0:
		(get_node("Seat") as Node3D).visible = pose == "Rk_SitIdle"
		a.play(pose, float(POSES.get(pose, 0.5)), 0.0)
		a.ap.pause()
		a.placed()
		wait = 24   # волосы, ткань и слои успевают встать
	else:
		wait = 3
	_aim(CAMS[ci])


func _fit_seat() -> void:   # табурет под рост героя: верх — под тазом сидящего (у Дэнни и Айрис ниже, чем у Хейла)
	var sk := a.skel
	var pel: Vector3 = sk.global_transform * sk.get_bone_global_pose(sk.find_bone("pelvis")).origin
	var th: Vector3 = sk.global_transform * sk.get_bone_global_pose(sk.find_bone("thigh_l")).origin
	var top: float = minf(pel.y, th.y) - 0.1 * pel.y / 0.55   # от сустава бедра до поверхности сиденья ≈ 10 см у взрослого
	top = clampf(top, 0.2, 0.7)
	var seat := get_node("Seat") as MeshInstance3D
	(seat.mesh as BoxMesh).size = Vector3(0.45, top, 0.42)
	seat.position = Vector3(pel.x, top * 0.5, pel.z - 0.1)


func _aim(c: String) -> void:
	var sk := a.skel
	var bp := func(b: String) -> Vector3:
		return sk.global_transform * sk.get_bone_global_pose(sk.find_bone(b)).origin
	var chest: Vector3 = bp.call("spine_03")
	var hip: Vector3 = bp.call("pelvis")
	var mid: Vector3 = (chest + hip) * 0.5 + Vector3(0, -0.15, 0)
	match c:
		"neckb", "neckf", "neck3":   # шея и ворот крупно: сзади, спереди, сбоку-сзади (камера прогулки)
			var nk: Vector3 = bp.call("neck_01")
			var off: Vector3 = {"neckb": Vector3(0.0, 0.12, -0.75), "neckf": Vector3(0.0, 0.05, 0.75), "neck3": Vector3(-0.5, 0.18, -0.55)}[c]
			cam.fov = 30.0
			cam.look_at_from_position(nk + a.global_transform.basis * off, nk + Vector3(0, -0.04, 0))
		"close":
			cam.fov = 30.0
			cam.look_at_from_position(chest + Vector3(0.35, 0.05, 1.15), chest + Vector3(0, -0.12, 0))
		"front":
			cam.fov = 34.0
			cam.look_at_from_position(mid + Vector3(1.3, 0.25, 2.6), mid)
		"back":
			cam.fov = 34.0
			cam.look_at_from_position(mid + Vector3(-1.0, 0.3, -2.8), mid)
		"side":
			cam.fov = 34.0
			cam.look_at_from_position(mid + Vector3(2.9, 0.2, 0.2), mid)


func _shoot() -> void:
	var pi := step / CAMS.size()
	var ci := step % CAMS.size()
	get_viewport().get_texture().get_image().save_png("%s/%s_%s.png" % [out, poses[pi], CAMS[ci]])
