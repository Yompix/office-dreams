extends Node3D
## Проба записи кадра: $SHOT_ID в момент $AT (секунды), снимок в $SHOT и выход. С обработкой кадра, как в игре ($POST=0 — без неё).
## Для сверки света: SOLO=<лампа> — горит одна лампа места; BARE=glow,ssao,vfog,fog,probe,beams — без этих эффектов;
## DBG_LIGHTS=1 / DBG_MATS=1 — свет и материалы места в журнал. Сравнение со старой игрой — tools/godot/compare_light.py.
var frames := 0
var player: ShotPlayer
var post: PostFX

func _at() -> float:
	return float(OS.get_environment("AT")) if OS.get_environment("AT") != "" else 2.0

func _ready():
	player = ShotPlayer.new()
	add_child(player)
	player.play(OS.get_environment("SHOT_ID") if OS.get_environment("SHOT_ID") != "" else "pro_two")
	player.apply(_at())
	if OS.get_environment("POST") != "0":
		post = PostFX.new()
		add_child(post)

func _process(_d):
	frames += 1
	player.apply(_at())   # мониторы и лица живут каждый кадр
	if post:
		post.beat_fx("", _at(), _at(), 0.0, {}, 1.0)
		post.set_p("ir", 1.0 if player.ir else 0.0)
	if OS.get_environment("BARE") != "":   # проверка: без эффектов окружения (свечение, SSAO, туманы, проба, лучи, пыль)
		var L1: Location = player.locations[player.shot.loc]
		var off := OS.get_environment("BARE").split(",")
		if "glow" in off: L1.env.glow_enabled = false
		if "ssao" in off: L1.env.ssao_enabled = false
		if "vfog" in off: L1.env.volumetric_fog_enabled = false
		if "fog" in off: L1.env.fog_enabled = false
		if "probe" in off and L1.probe: L1.probe.visible = false
		if "beams" in off: L1.beams.visible = false
	if OS.get_environment("SOLO") != "":   # только одна лампа места (проверка, что она светит)
		var L0: Location = player.locations[player.shot.loc]
		for k in L0.lights:
			L0.lights[k].visible = k == OS.get_environment("SOLO")
		L0.env.ambient_light_energy = 0.0
		player.fill.visible = false
		if L0.probe:
			L0.probe.visible = false
	if frames == 11 and OS.get_environment("DBG_MATS") != "":
		var seen := {}
		for mi in player.locations[player.shot.loc].find_children("*", "MeshInstance3D", true, false):
			var m3 := mi as MeshInstance3D
			if m3.mesh == null or not m3.visible:
				continue
			for si in m3.mesh.get_surface_count():
				var mat := m3.get_active_material(si) as BaseMaterial3D
				if mat == null or seen.has(mat) or mat.albedo_texture == null:
					continue
				seen[mat] = true
				var img := mat.albedo_texture.get_image()
				print("mat ", m3.name, " col=", mat.albedo_color, " fmt=", img.get_format() if img else -1, " size=", img.get_size() if img else Vector2i.ZERO,
					" rough=", mat.roughness, " metal=", mat.metallic, " spec=", mat.metallic_specular, " shading=", mat.shading_mode, " avg=", img.get_pixel(int(img.get_width() / 2), int(img.get_height() / 2)) if img and not img.is_compressed() else "comp")
				if seen.size() > 6:
					break
	if frames == 11 and OS.get_environment("DBG_LIGHTS") != "":
		var L: Location = player.locations[player.shot.loc]
		for li in L.find_children("*", "Light3D", true, false):
			var l := li as Light3D
			print("%s vis=%s e=%.3f c=%s pos=%s layers=%d mask=%d sh=%s rng=%s" % [l.name, l.is_visible_in_tree(), l.light_energy, l.light_color,
				l.global_position, l.layers, l.light_cull_mask, l.shadow_enabled, (l as OmniLight3D).omni_range if l is OmniLight3D else "-"])
		for k in L.lights:
			print("key ", k, " -> ", L.lights[k].name)
		print("amb ", L.env.ambient_light_color, " e=", L.env.ambient_light_energy)
		for bm in L.beams.list:
			print("beam ", (bm.l as Node).name, " uI=", bm.mat.get_shader_parameter("uI"), " uK=", bm.mat.get_shader_parameter("uK"), " vis=", (bm.l as Light3D).visible)
		for c in L.beams.get_children():
			print("beamnode ", c.name, " vis=", (c as Node3D).is_visible_in_tree(), " layers=", (c as VisualInstance3D).layers, " pos=", (c as Node3D).global_position)
	if frames == 12:
		get_viewport().get_texture().get_image().save_png(OS.get_environment("SHOT"))
		print("saved ", RenderingServer.get_current_rendering_method())
		get_tree().quit()
