extends SceneTree
## Куда уходит видеопамять: игра идёт сама (автопилот), через --after секунд — список текстур по размеру
## у всех материалов сцены (по размеру и формату, с мип-картами), итоги памяти, видеокарта и качество; пишет JSON в --vramout.
##   xvfb-run -a -s "-screen 0 1280x720x24" $GODOT --path godot/lake --script res://scripts/dev/vram_probe.gd -- \
##     --autoplay --fast --quality 0 --scene c2_s4 --restore --after 60 --vramout файл.json
var out := "/tmp/lake_vram.json"
var after := 60.0
var t0 := 0
var done := false


func _init() -> void:
	var args := OS.get_cmdline_user_args()
	if "--vramout" in args:
		out = args[args.find("--vramout") + 1]
	if "--after" in args:
		after = float(args[args.find("--after") + 1])
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))
	t0 = Time.get_ticks_msec()


func _process(_d: float) -> bool:
	if done or float(Time.get_ticks_msec() - t0) / 1000.0 < after:
		return false
	done = true
	var seen := {}
	var tex := []
	_walk(root, seen, tex)
	tex.sort_custom(func(a, b): return int(a.bytes) > int(b.bytes))
	var top := []
	var by_dir := {}
	var total := 0
	for t in tex:
		total += int(t.bytes)
		var p := String(t.path)
		var d := p.get_base_dir() if p != "" else "(без пути)"
		if p.contains("::"):
			d = p.get_slice("::", 0)
		by_dir[d] = int(by_dir.get(d, 0)) + int(t.bytes)
		if top.size() < 60:
			top.append({"path": p, "w": t.w, "h": t.h, "d": 1, "format": t.format, "mb": snappedf(float(t.bytes) / 1048576.0, 0.01)})
	var dirs := []
	for k in by_dir:
		dirs.append([k, snappedf(float(by_dir[k]) / 1048576.0, 0.1)])
	dirs.sort_custom(func(a, b): return a[1] > b[1])
	var g := current_scene
	var res := {
		"scene": String(g.get("_scene_id")) if g != null and "_scene_id" in g else "",
		"gpu": RenderingServer.get_video_adapter_name(),
		"method": RenderingServer.get_current_rendering_method(),
		"textures": tex.size(),
		"textures_mb": snappedf(float(total) / 1048576.0, 0.1),
		"texture_mem_mb": snappedf(float(RenderingServer.get_rendering_info(RenderingServer.RENDERING_INFO_TEXTURE_MEM_USED)) / 1048576.0, 0.1),
		"buffer_mem_mb": snappedf(float(RenderingServer.get_rendering_info(RenderingServer.RENDERING_INFO_BUFFER_MEM_USED)) / 1048576.0, 0.1),
		"video_mem_mb": snappedf(float(RenderingServer.get_rendering_info(RenderingServer.RENDERING_INFO_VIDEO_MEM_USED)) / 1048576.0, 0.1),
		"static_mem_mb": snappedf(float(OS.get_static_memory_usage()) / 1048576.0, 0.1),
		"window": DisplayServer.window_get_size(),
		"by_dir": dirs.slice(0, 25),
		"top": top,
	}
	var f := FileAccess.open(out, FileAccess.WRITE)
	f.store_string(JSON.stringify(res, " ", false))
	f.close()
	print("VRAM записано ", out)
	quit()
	return true


## Бит на пиксель по формату изображения (с мип-картами ×4/3).
static func _bpp(fmt: int) -> float:
	match fmt:
		Image.FORMAT_L8, Image.FORMAT_R8:
			return 8.0
		Image.FORMAT_LA8, Image.FORMAT_RG8:
			return 16.0
		Image.FORMAT_RGB8, Image.FORMAT_RGBA8:
			return 32.0   # RGB8 на видеокарте — тоже 4 байта
		Image.FORMAT_RGBAH, Image.FORMAT_RGBH:
			return 64.0
		Image.FORMAT_RGBAF:
			return 128.0
		Image.FORMAT_DXT1, Image.FORMAT_ETC2_RGB8, Image.FORMAT_RGTC_R, Image.FORMAT_ETC:
			return 4.0
		Image.FORMAT_DXT3, Image.FORMAT_DXT5, Image.FORMAT_BPTC_RGBA, Image.FORMAT_ETC2_RGBA8, Image.FORMAT_RGTC_RG, Image.FORMAT_ASTC_4x4:
			return 8.0
	return 32.0


func _add_tex(t: Texture, where: String, seen: Dictionary, tex: Array) -> void:
	if t == null or not (t is Texture2D):
		return
	var rid := t.get_rid()
	if seen.has(rid):
		return
	seen[rid] = true
	var fmt := -1
	if t.has_method("get_format"):
		fmt = int(t.call("get_format"))
	var w := (t as Texture2D).get_width()
	var h := (t as Texture2D).get_height()
	var mip := 4.0 / 3.0
	var path := t.resource_path if t.resource_path != "" else where
	tex.append({"path": path, "w": w, "h": h, "format": fmt, "bytes": int(float(w * h) * _bpp(fmt) / 8.0 * mip)})


func _mat(m: Material, where: String, seen: Dictionary, tex: Array) -> void:
	if m == null:
		return
	if m is BaseMaterial3D:
		for i in BaseMaterial3D.TEXTURE_MAX:
			_add_tex((m as BaseMaterial3D).get_texture(i), where, seen, tex)
	elif m is ShaderMaterial and (m as ShaderMaterial).shader:
		for u in (m as ShaderMaterial).shader.get_shader_uniform_list():
			var v = (m as ShaderMaterial).get_shader_parameter(u.name)
			if v is Texture:
				_add_tex(v, where, seen, tex)
	if m.next_pass:
		_mat(m.next_pass, where, seen, tex)


func _walk(n: Node, seen: Dictionary, tex: Array) -> void:
	var where := String(n.get_path())
	if n is GeometryInstance3D:
		var gi := n as GeometryInstance3D
		_mat(gi.material_override, where, seen, tex)
		_mat(gi.material_overlay, where, seen, tex)
		if n is MeshInstance3D and (n as MeshInstance3D).mesh:
			var mi := n as MeshInstance3D
			for i in mi.mesh.get_surface_count():
				_mat(mi.get_active_material(i), where, seen, tex)
		elif n is MultiMeshInstance3D and (n as MultiMeshInstance3D).multimesh and (n as MultiMeshInstance3D).multimesh.mesh:
			var mm := (n as MultiMeshInstance3D).multimesh.mesh
			for i in mm.get_surface_count():
				_mat(mm.surface_get_material(i), where, seen, tex)
	if n is Sprite3D:
		_add_tex((n as Sprite3D).texture, where, seen, tex)
	if n is SubViewport:
		var vp := n as SubViewport
		tex.append({"path": where + " (SubViewport)", "w": vp.size.x, "h": vp.size.y, "format": Image.FORMAT_RGBA8, "bytes": vp.size.x * vp.size.y * 8})
	for c in n.get_children():
		_walk(c, seen, tex)
