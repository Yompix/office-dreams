extends Node3D
## Проба героя: Итан с клипом из anims.glb, свет и туман как в будке охраны. Снимок в $SHOT и выход.
var frames := 0
var ap: AnimationPlayer

func _ready():
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.02, 0.025, 0.035)
	env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.25, 0.3, 0.4)
	env.ambient_light_energy = 0.4
	if RenderingServer.get_current_rendering_method() == "forward_plus":
		env.ssao_enabled = true
		env.volumetric_fog_enabled = true
		env.volumetric_fog_density = 0.02
		env.glow_enabled = true
	var we := WorldEnvironment.new(); we.environment = env; add_child(we)
	var floor := MeshInstance3D.new(); var pm := PlaneMesh.new(); pm.size = Vector2(8, 8); floor.mesh = pm
	var fm := StandardMaterial3D.new(); fm.albedo_color = Color(0.25, 0.24, 0.22); fm.roughness = 0.6; floor.material_override = fm; add_child(floor)
	var who: String = OS.get_environment("WHO") if OS.get_environment("WHO") != "" else "ethan"
	var hero: Node3D = (load("res://assets/models/%s.glb" % who) as PackedScene).instantiate()
	add_child(hero)
	var model: Node = hero.get_child(0)
	ap = AnimationPlayer.new()
	model.add_child(ap)
	ap.add_animation_library("", AnimLib.library())
	var clip: String = OS.get_environment("CLIP") if OS.get_environment("CLIP") != "" else "Idle_Talking"
	ap.play(clip)
	ap.seek(1.0, true)
	var key := SpotLight3D.new(); key.light_color = Color(1.0, 0.82, 0.55); key.light_energy = 6; key.spot_range = 8; key.spot_angle = 40; key.shadow_enabled = true
	add_child(key); key.look_at_from_position(Vector3(1.2, 2.6, 1.6), Vector3(0, 1.4, 0))
	var mon := OmniLight3D.new(); mon.light_color = Color(0.45, 0.85, 0.75); mon.light_energy = 1.5; mon.omni_range = 3; mon.position = Vector3(-0.8, 1.3, 0.9); add_child(mon)
	var cam := Camera3D.new(); cam.fov = 35; add_child(cam)
	cam.look_at_from_position(Vector3(0.9, 1.6, 2.4), Vector3(0, 1.35, 0))

func _process(_d):
	frames += 1
	if frames == 8:
		get_viewport().get_texture().get_image().save_png(OS.get_environment("SHOT"))
		print("saved, ", RenderingServer.get_current_rendering_method())
		get_tree().quit()
